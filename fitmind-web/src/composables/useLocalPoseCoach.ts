import { computed, ref } from 'vue'
import { FilesetResolver, PoseLandmarker } from '@mediapipe/tasks-vision'
import type {
  ActionPhaseSegment,
  ActionPrediction,
  ExerciseActionAnalysisResult,
  FormCheck,
  JointAngleMetric
} from '../api/exercise'

type LocalPoseStatus = 'idle' | 'starting' | 'tracking' | 'error'
export type ActionSelectionMode = 'auto' | 'manual'

export type SupportedRealtimeAction =
  | 'bodyweight_squat'
  | 'jumping_jack'
  | 'forward_lunge'
  | 'push_up'
  | 'high_knees'
  | 'standing_knee_raise'
  | 'glute_bridge'
  | 'plank'
  | 'burpee'

type Landmark = {
  x: number
  y: number
  z?: number
  visibility?: number
}

type PoseSnapshot = {
  timestamp: number
  leftKnee: number | null
  rightKnee: number | null
  leftHip: number | null
  rightHip: number | null
  leftElbow: number | null
  rightElbow: number | null
  ankleSpreadRatio: number | null
  wristSpreadRatio: number | null
  armLiftRatio: number | null
  torsoDrift: number | null
  leftKneeLift: number | null
  rightKneeLift: number | null
  hipHeightRatio: number | null
  plankLineOffset: number | null
  wristBelowShoulderRatio: number | null
}

type ActionTracker = {
  repetitions: number
  hasReachedDepth: boolean
  currentPhase: string
  currentToken: string
  phaseTimeline: ActionPhaseSegment[]
  stableFrames: number
  auxCount: number
  lastSide: 'left' | 'right' | null
}

type ActionDescriptor = {
  key: SupportedRealtimeAction
  label: string
  labelZh: string
  tips: string[]
}

const REQUIRED_POSE_FRAMES = 18
const DETECT_INTERVAL_MS = 120
const MAX_SNAPSHOTS = 96
const AUTO_DETECT_MIN_HISTORY = 10
const AUTO_DETECT_MIN_SCORE = 0.34
const AUTO_DETECT_MIN_GAP = 0.05
const AUTO_DETECT_CONFIRM_FRAMES = 4
const AUTO_SWITCH_COOLDOWN_MS = 1800
const MODEL_ASSET_PATH =
  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task'
const WASM_ROOT = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision/wasm'

const LEFT_SHOULDER = 11
const RIGHT_SHOULDER = 12
const LEFT_ELBOW = 13
const RIGHT_ELBOW = 14
const LEFT_WRIST = 15
const RIGHT_WRIST = 16
const LEFT_HIP = 23
const RIGHT_HIP = 24
const LEFT_KNEE = 25
const RIGHT_KNEE = 26
const LEFT_ANKLE = 27
const RIGHT_ANKLE = 28

const ACTIONS: ActionDescriptor[] = [
  {
    key: 'bodyweight_squat',
    label: 'BodyWeightSquats',
    labelZh: '徒手深蹲',
    tips: ['下蹲前先稳定站距。', '到底后再有控制地起身。', '尽量保持膝和髋的轨迹稳定。']
  },
  {
    key: 'jumping_jack',
    label: 'JumpingJack',
    labelZh: '开合跳',
    tips: ['手脚打开和回收的节奏尽量一致。', '双臂尽量越过肩线。', '保持全身完整入镜。']
  },
  {
    key: 'forward_lunge',
    label: 'Lunges',
    labelZh: '弓步',
    tips: ['前后脚拉开后再下沉。', '起身时避免急冲。', '保持躯干稳定。']
  },
  {
    key: 'push_up',
    label: 'PushUps',
    labelZh: '俯卧撑',
    tips: ['下放时保持核心收紧。', '到底后再稳定推起。', '避免塌腰和撅臀。']
  },
  {
    key: 'high_knees',
    label: 'HighKnees',
    labelZh: '高抬腿',
    tips: ['左右腿交替尽量均匀。', '抬膝时别只做半程。', '保持上身不要左右乱晃。']
  },
  {
    key: 'standing_knee_raise',
    label: 'StandingKneeRaise',
    labelZh: '站姿提膝',
    tips: ['抬膝时先稳定核心。', '每次抬膝都做到清晰顶点。', '放下时保持控制。']
  },
  {
    key: 'glute_bridge',
    label: 'GluteBridge',
    labelZh: '臀桥',
    tips: ['顶峰时夹紧臀部。', '上顶后停顿半拍。', '避免只抬腰不抬髋。']
  },
  {
    key: 'plank',
    label: 'Plank',
    labelZh: '平板支撑',
    tips: ['从肩到髋尽量保持一条线。', '持续收紧腹部和臀部。', '避免塌腰或臀部抬太高。']
  },
  {
    key: 'burpee',
    label: 'Burpee',
    labelZh: '波比跳',
    tips: ['下蹲和撑地要连贯。', '进入支撑位后保持身体稳定。', '回到站立时做完整起身。']
  }
]

const ACTION_MAP = Object.fromEntries(ACTIONS.map(item => [item.key, item] as const)) as Record<
  SupportedRealtimeAction,
  ActionDescriptor
>

const POSE_CONNECTIONS: Array<[number, number]> = [
  [11, 12], [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 23], [12, 24], [23, 24], [23, 25], [25, 27],
  [24, 26], [26, 28]
]

let sharedLandmarkerPromise: Promise<PoseLandmarker> | null = null

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

function average(values: Array<number | null>) {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  if (!valid.length) {
    return null
  }
  return valid.reduce((sum, value) => sum + value, 0) / valid.length
}

function minValid(values: Array<number | null>, fallback: number) {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  return valid.length ? Math.min(...valid) : fallback
}

function maxValid(values: Array<number | null>, fallback: number) {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  return valid.length ? Math.max(...valid) : fallback
}

function rangeValid(values: Array<number | null>) {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  if (valid.length < 2) {
    return 0
  }
  return Math.max(...valid) - Math.min(...valid)
}

function getPoint(landmarks: Landmark[], index: number, minVisibility = 0.35) {
  const point = landmarks[index]
  if (!point) return null
  const visibility = point.visibility ?? 1
  if (visibility < minVisibility) return null
  return point
}

function angleAt(a: Landmark | null, b: Landmark | null, c: Landmark | null) {
  if (!a || !b || !c) {
    return null
  }
  const baX = a.x - b.x
  const baY = a.y - b.y
  const bcX = c.x - b.x
  const bcY = c.y - b.y
  const baNorm = Math.hypot(baX, baY)
  const bcNorm = Math.hypot(bcX, bcY)
  if (baNorm < 1e-6 || bcNorm < 1e-6) {
    return null
  }
  const cosine = Math.max(-1, Math.min(1, (baX * bcX + baY * bcY) / (baNorm * bcNorm)))
  return Math.round((Math.acos(cosine) * 180) / Math.PI * 10) / 10
}

function metricFromSeries(key: string, label: string, unit: string, values: Array<number | null>) {
  const valid = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  if (!valid.length) {
    return null
  }
  const current = valid[valid.length - 1]
  const min = Math.min(...valid)
  const max = Math.max(...valid)
  const avg = valid.reduce((sum, value) => sum + value, 0) / valid.length
  return {
    key,
    label,
    current: Math.round(current * 10) / 10,
    average: Math.round(avg * 10) / 10,
    min: Math.round(min * 10) / 10,
    max: Math.round(max * 10) / 10,
    unit
  } satisfies JointAngleMetric
}

function createTracker(): ActionTracker {
  return {
    repetitions: 0,
    hasReachedDepth: false,
    currentPhase: '准备',
    currentToken: 'idle',
    phaseTimeline: [],
    stableFrames: 0,
    auxCount: 0,
    lastSide: null
  }
}

function pushPhase(tracker: ActionTracker, phase: string, frameIndex: number) {
  tracker.currentPhase = phase
  const last = tracker.phaseTimeline[tracker.phaseTimeline.length - 1]
  if (!last || last.phase !== phase) {
    tracker.phaseTimeline.push({
      phase,
      startFrame: frameIndex,
      endFrame: frameIndex,
      frameCount: 1
    })
  } else {
    last.endFrame = frameIndex
    last.frameCount += 1
  }
  if (tracker.phaseTimeline.length > 14) {
    tracker.phaseTimeline.splice(0, tracker.phaseTimeline.length - 14)
  }
}

function buildSnapshot(landmarks: Landmark[], timestamp: number) {
  const leftShoulder = getPoint(landmarks, LEFT_SHOULDER)
  const rightShoulder = getPoint(landmarks, RIGHT_SHOULDER)
  const leftHip = getPoint(landmarks, LEFT_HIP)
  const rightHip = getPoint(landmarks, RIGHT_HIP)
  const leftKnee = getPoint(landmarks, LEFT_KNEE)
  const rightKnee = getPoint(landmarks, RIGHT_KNEE)
  const leftAnkle = getPoint(landmarks, LEFT_ANKLE)
  const rightAnkle = getPoint(landmarks, RIGHT_ANKLE)
  const leftWrist = getPoint(landmarks, LEFT_WRIST)
  const rightWrist = getPoint(landmarks, RIGHT_WRIST)

  const leftKneeAngle = angleAt(leftHip, leftKnee, leftAnkle)
  const rightKneeAngle = angleAt(rightHip, rightKnee, rightAnkle)
  const leftHipAngle = angleAt(leftShoulder, leftHip, leftKnee)
  const rightHipAngle = angleAt(rightShoulder, rightHip, rightKnee)
  const leftElbowAngle = angleAt(leftShoulder, getPoint(landmarks, LEFT_ELBOW), leftWrist)
  const rightElbowAngle = angleAt(rightShoulder, getPoint(landmarks, RIGHT_ELBOW), rightWrist)

  const shoulderWidth =
    leftShoulder && rightShoulder ? Math.max(Math.abs(leftShoulder.x - rightShoulder.x), 1e-4) : null
  const avgShoulderY = average([leftShoulder?.y ?? null, rightShoulder?.y ?? null])
  const avgHipY = average([leftHip?.y ?? null, rightHip?.y ?? null])
  const avgKneeY = average([leftKnee?.y ?? null, rightKnee?.y ?? null])
  const avgAnkleY = average([leftAnkle?.y ?? null, rightAnkle?.y ?? null])
  const avgWristY = average([leftWrist?.y ?? null, rightWrist?.y ?? null])
  const bodyHeight =
    avgShoulderY !== null && avgAnkleY !== null ? Math.max(avgAnkleY - avgShoulderY, 1e-4) : null

  const ankleSpreadRatio =
    leftAnkle && rightAnkle && shoulderWidth ? Math.abs(leftAnkle.x - rightAnkle.x) / shoulderWidth : null
  const wristSpreadRatio =
    leftWrist && rightWrist && shoulderWidth ? Math.abs(leftWrist.x - rightWrist.x) / shoulderWidth : null
  const armLiftRatio = average([
    leftShoulder && leftWrist ? leftShoulder.y - leftWrist.y : null,
    rightShoulder && rightWrist ? rightShoulder.y - rightWrist.y : null
  ])
  const torsoDrift = average([
    leftShoulder && leftHip ? Math.abs(leftShoulder.x - leftHip.x) : null,
    rightShoulder && rightHip ? Math.abs(rightShoulder.x - rightHip.x) : null
  ])
  const leftKneeLift = leftHip && leftKnee ? leftHip.y - leftKnee.y : null
  const rightKneeLift = rightHip && rightKnee ? rightHip.y - rightKnee.y : null
  const hipHeightRatio =
    avgHipY !== null && avgKneeY !== null && bodyHeight ? (avgKneeY - avgHipY) / bodyHeight : null
  const plankLineOffset =
    avgShoulderY !== null && avgHipY !== null && bodyHeight ? Math.abs(avgShoulderY - avgHipY) / bodyHeight : null
  const wristBelowShoulderRatio =
    avgWristY !== null && avgShoulderY !== null && bodyHeight ? (avgWristY - avgShoulderY) / bodyHeight : null

  return {
    timestamp,
    leftKnee: leftKneeAngle,
    rightKnee: rightKneeAngle,
    leftHip: leftHipAngle,
    rightHip: rightHipAngle,
    leftElbow: leftElbowAngle,
    rightElbow: rightElbowAngle,
    ankleSpreadRatio,
    wristSpreadRatio,
    armLiftRatio,
    torsoDrift,
    leftKneeLift,
    rightKneeLift,
    hipHeightRatio,
    plankLineOffset,
    wristBelowShoulderRatio
  } satisfies PoseSnapshot
}

function elbowAverage(snapshot: PoseSnapshot) {
  return average([snapshot.leftElbow, snapshot.rightElbow]) ?? 180
}

function hipAverage(snapshot: PoseSnapshot) {
  return average([snapshot.leftHip, snapshot.rightHip]) ?? 180
}

function kneeAverage(snapshot: PoseSnapshot) {
  return average([snapshot.leftKnee, snapshot.rightKnee]) ?? 180
}

function scoreSquat(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const knee = kneeAverage(snapshot)
  const hip = hipAverage(snapshot)
  const stance = snapshot.ankleSpreadRatio ?? 0.9
  const motionRange = rangeValid(history.map(item => average([item.leftKnee, item.rightKnee])))
  return clamp01(
    0.42 * clamp01((165 - knee) / 70) +
      0.28 * clamp01((165 - hip) / 75) +
      0.15 * (1 - Math.min(1, Math.abs(stance - 1.25) / 0.7)) +
      0.15 * clamp01(motionRange / 65)
  )
}

function scoreJumpingJack(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const armLift = snapshot.armLiftRatio ?? -0.2
  const spread = snapshot.ankleSpreadRatio ?? 0.8
  const spreadRange = rangeValid(history.map(item => item.ankleSpreadRatio))
  return clamp01(
    0.45 * clamp01((armLift + 0.05) / 0.42) +
      0.4 * clamp01((spread - 0.95) / 0.9) +
      0.15 * clamp01(spreadRange / 0.8)
  )
}

function scoreLunge(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const bentKnee = Math.min(snapshot.leftKnee ?? 180, snapshot.rightKnee ?? 180)
  const straightKnee = Math.max(snapshot.leftKnee ?? 180, snapshot.rightKnee ?? 180)
  const spread = snapshot.ankleSpreadRatio ?? 0.8
  const depthRange = rangeValid(history.map(item => Math.min(item.leftKnee ?? 180, item.rightKnee ?? 180)))
  return clamp01(
    0.4 * clamp01((150 - bentKnee) / 65) +
      0.3 * clamp01((straightKnee - 130) / 45) +
      0.15 * clamp01((spread - 1) / 0.75) +
      0.15 * clamp01(depthRange / 55)
  )
}

function scorePushUp(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const elbow = elbowAverage(snapshot)
  const hip = hipAverage(snapshot)
  const elbowRange = rangeValid(history.map(item => average([item.leftElbow, item.rightElbow])))
  const bodyLine = snapshot.plankLineOffset ?? 0.2
  return clamp01(
    0.38 * clamp01((165 - elbow) / 75) +
      0.24 * clamp01(elbowRange / 60) +
      0.22 * clamp01((hip - 140) / 35) +
      0.16 * clamp01((0.15 - bodyLine) / 0.12)
  )
}

function scoreHighKnees(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const kneeLift = Math.max(snapshot.leftKneeLift ?? 0, snapshot.rightKneeLift ?? 0)
  const alternation = history.slice(-20).reduce((count, item, index, arr) => {
    if (index === 0) return 0
    const prevSide = (arr[index - 1].leftKneeLift ?? 0) > (arr[index - 1].rightKneeLift ?? 0) ? 'left' : 'right'
    const currSide = (item.leftKneeLift ?? 0) > (item.rightKneeLift ?? 0) ? 'left' : 'right'
    return currSide !== prevSide ? count + 1 : count
  }, 0)
  const torso = snapshot.torsoDrift ?? 0.12
  return clamp01(
    0.46 * clamp01(kneeLift / 0.18) +
      0.26 * clamp01(alternation / 8) +
      0.16 * clamp01((1.45 - (snapshot.ankleSpreadRatio ?? 1.2)) / 0.9) +
      0.12 * clamp01((0.12 - torso) / 0.09)
  )
}

function scoreStandingKneeRaise(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const kneeLift = Math.max(snapshot.leftKneeLift ?? 0, snapshot.rightKneeLift ?? 0)
  const support = Math.min(snapshot.leftKneeLift ?? 0, snapshot.rightKneeLift ?? 0)
  const torso = snapshot.torsoDrift ?? 0.12
  const liftRange = rangeValid(history.map(item => Math.max(item.leftKneeLift ?? 0, item.rightKneeLift ?? 0)))
  return clamp01(
    0.46 * clamp01(kneeLift / 0.2) +
      0.2 * clamp01((0.08 - Math.max(0, support)) / 0.08) +
      0.18 * clamp01(liftRange / 0.18) +
      0.16 * clamp01((0.12 - torso) / 0.09)
  )
}

function scoreGluteBridge(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const hip = hipAverage(snapshot)
  const line = snapshot.plankLineOffset ?? 0.2
  const height = snapshot.hipHeightRatio ?? 0
  const hipRange = rangeValid(history.map(item => average([item.leftHip, item.rightHip])))
  return clamp01(
    0.38 * clamp01((hip - 130) / 45) +
      0.24 * clamp01(height / 0.45) +
      0.2 * clamp01((0.14 - line) / 0.12) +
      0.18 * clamp01(hipRange / 35)
  )
}

function scorePlank(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const hip = hipAverage(snapshot)
  const line = snapshot.plankLineOffset ?? 0.2
  const drift = snapshot.torsoDrift ?? 0.12
  const lineRange = rangeValid(history.slice(-20).map(item => item.plankLineOffset))
  return clamp01(
    0.38 * clamp01((hip - 145) / 30) +
      0.28 * clamp01((0.12 - line) / 0.1) +
      0.18 * clamp01((0.1 - drift) / 0.08) +
      0.16 * clamp01((0.08 - lineRange) / 0.08)
  )
}

function scoreBurpee(snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  const deepKnee = minValid(history.map(item => average([item.leftKnee, item.rightKnee])), 180)
  const wristDrop = maxValid(history.map(item => item.wristBelowShoulderRatio), 0)
  const line = minValid(history.map(item => item.plankLineOffset), 1)
  const kneeRange = rangeValid(history.map(item => average([item.leftKnee, item.rightKnee])))
  const currentTransition = clamp01(((snapshot.wristBelowShoulderRatio ?? 0) / 0.8) + ((160 - kneeAverage(snapshot)) / 120))
  return clamp01(
    0.3 * clamp01((160 - deepKnee) / 80) +
      0.26 * clamp01(wristDrop / 0.85) +
      0.22 * clamp01((0.18 - line) / 0.14) +
      0.12 * clamp01(kneeRange / 85) +
      0.1 * currentTransition
  )
}

function scoreAction(action: SupportedRealtimeAction, snapshot: PoseSnapshot, history: PoseSnapshot[]) {
  switch (action) {
    case 'bodyweight_squat':
      return scoreSquat(snapshot, history)
    case 'jumping_jack':
      return scoreJumpingJack(snapshot, history)
    case 'forward_lunge':
      return scoreLunge(snapshot, history)
    case 'push_up':
      return scorePushUp(snapshot, history)
    case 'high_knees':
      return scoreHighKnees(snapshot, history)
    case 'standing_knee_raise':
      return scoreStandingKneeRaise(snapshot, history)
    case 'glute_bridge':
      return scoreGluteBridge(snapshot, history)
    case 'plank':
      return scorePlank(snapshot, history)
    case 'burpee':
      return scoreBurpee(snapshot, history)
  }
}

function rankActions(history: PoseSnapshot[]) {
  const current = history[history.length - 1]
  if (!current) {
    return []
  }

  return ACTIONS
    .map(action => ({
      action,
      score: scoreAction(action.key, current, history)
    }))
    .sort((a, b) => b.score - a.score)
}

function buildPredictionList(history: PoseSnapshot[]) {
  const scored = rankActions(history)
  if (!scored.length) {
    return []
  }
  const total = scored.reduce((sum, item) => sum + Math.max(item.score, 0.01), 0)

  return scored
    .map(item => ({
      label: item.action.label,
      labelZh: item.action.labelZh,
      score: Math.round(item.score * 1000) / 1000,
      scorePercent: Math.max(1, Math.min(99, Math.round((Math.max(item.score, 0.01) / total) * 100)))
    }) satisfies ActionPrediction)
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
}

function updateSquatTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const knee = kneeAverage(snapshot)
  const previous = tracker.currentToken
  let token = previous
  let phase = '准备'

  if (knee <= 105) {
    token = 'bottom'
    phase = '底部'
  } else if (knee < 148) {
    token = knee < hipAverage(snapshot) ? 'descending' : 'ascending'
    phase = token === 'descending' ? '下蹲' : '起身'
  } else {
    token = 'up'
  }

  if (token === 'bottom') {
    tracker.hasReachedDepth = true
  }
  if (token === 'up' && tracker.hasReachedDepth && previous !== 'up') {
    tracker.repetitions += 1
    tracker.hasReachedDepth = false
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updateJumpingJackTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const armLift = snapshot.armLiftRatio ?? -0.2
  const spread = snapshot.ankleSpreadRatio ?? 0.8
  const previous = tracker.currentToken
  let token = previous
  let phase = '准备'

  if (armLift > 0.2 && spread > 1.45) {
    token = 'open'
    phase = '张开'
    tracker.hasReachedDepth = true
  } else if (armLift < 0.05 && spread < 1.15) {
    token = 'closed'
    phase = previous === 'open' ? '收回' : '准备'
    if (tracker.hasReachedDepth && previous === 'open') {
      tracker.repetitions += 1
      tracker.hasReachedDepth = false
    }
  } else {
    token = spread >= 1.2 ? 'opening' : 'closing'
    phase = token === 'opening' ? '展开' : '收回'
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updateLungeTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const bentKnee = Math.min(snapshot.leftKnee ?? 180, snapshot.rightKnee ?? 180)
  const straightKnee = Math.max(snapshot.leftKnee ?? 180, snapshot.rightKnee ?? 180)
  const spread = snapshot.ankleSpreadRatio ?? 0.8
  const previous = tracker.currentToken
  let token = previous
  let phase = '准备'

  if (bentKnee < 110 && straightKnee > 135 && spread > 1.2) {
    token = 'bottom'
    phase = '底部'
    tracker.hasReachedDepth = true
  } else if (spread > 1.08 && bentKnee < 145) {
    token = 'descending'
    phase = '下沉'
  } else {
    token = 'up'
    if (tracker.hasReachedDepth && previous !== 'up') {
      tracker.repetitions += 1
      tracker.hasReachedDepth = false
    }
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updatePushUpTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const elbow = elbowAverage(snapshot)
  const previous = tracker.currentToken
  let token = previous
  let phase = '支撑'

  if (elbow < 95) {
    token = 'bottom'
    phase = '底部'
    tracker.hasReachedDepth = true
  } else if (elbow < 145) {
    token = previous === 'bottom' ? 'ascending' : 'descending'
    phase = token === 'descending' ? '下放' : '推起'
  } else {
    token = 'up'
  }

  if (token === 'up' && tracker.hasReachedDepth && previous !== 'up') {
    tracker.repetitions += 1
    tracker.hasReachedDepth = false
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updateHighKneesTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const leftLift = snapshot.leftKneeLift ?? 0
  const rightLift = snapshot.rightKneeLift ?? 0
  const activeSide = leftLift > rightLift ? 'left' : 'right'
  const peakLift = Math.max(leftLift, rightLift)
  let phase = '准备'

  if (peakLift > 0.08) {
    phase = activeSide === 'left' ? '左提膝' : '右提膝'
    if (tracker.lastSide && tracker.lastSide !== activeSide) {
      tracker.repetitions += 1
    }
    tracker.lastSide = activeSide
    tracker.currentToken = activeSide
  } else {
    tracker.currentToken = 'idle'
    tracker.lastSide = null
  }

  pushPhase(tracker, phase, frameIndex)
}

function updateStandingKneeRaiseTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const leftLift = snapshot.leftKneeLift ?? 0
  const rightLift = snapshot.rightKneeLift ?? 0
  const activeLift = Math.max(leftLift, rightLift)
  const supportLift = Math.min(leftLift, rightLift)
  const activeSide = leftLift > rightLift ? 'left' : 'right'
  const previous = tracker.currentToken
  let token = previous
  let phase = '准备'

  if (activeLift > 0.1 && supportLift < 0.045) {
    token = 'top'
    phase = activeSide === 'left' ? '左膝顶点' : '右膝顶点'
    tracker.hasReachedDepth = true
    tracker.lastSide = activeSide
  } else if (activeLift > 0.05) {
    token = 'raising'
    phase = activeSide === 'left' ? '左膝抬起' : '右膝抬起'
  } else {
    token = 'down'
    if (tracker.hasReachedDepth && previous !== 'down') {
      tracker.repetitions += 1
      tracker.hasReachedDepth = false
    }
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updateGluteBridgeTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const hip = hipAverage(snapshot)
  const line = snapshot.plankLineOffset ?? 0.2
  const previous = tracker.currentToken
  let token = previous
  let phase = '准备'

  if (hip > 155 && line < 0.12) {
    token = 'top'
    phase = '桥顶'
    tracker.hasReachedDepth = true
  } else if (hip > 135) {
    token = previous === 'top' ? 'lowering' : 'lifting'
    phase = token === 'lifting' ? '上顶' : '下放'
  } else {
    token = 'down'
    if (tracker.hasReachedDepth && previous !== 'down') {
      tracker.repetitions += 1
      tracker.hasReachedDepth = false
    }
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function updatePlankTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const hip = hipAverage(snapshot)
  const line = snapshot.plankLineOffset ?? 0.2
  const wrists = snapshot.wristBelowShoulderRatio ?? 0
  const isHolding = hip > 150 && line < 0.12 && wrists > 0.2

  if (isHolding) {
    tracker.stableFrames += 1
    tracker.repetitions = Math.floor(tracker.stableFrames / 8)
    tracker.currentToken = 'hold'
    pushPhase(tracker, '保持', frameIndex)
  } else {
    tracker.currentToken = 'idle'
    pushPhase(tracker, '准备', frameIndex)
  }
}

function updateBurpeeTracker(tracker: ActionTracker, snapshot: PoseSnapshot, frameIndex: number) {
  const knee = kneeAverage(snapshot)
  const wrists = snapshot.wristBelowShoulderRatio ?? 0
  const hip = hipAverage(snapshot)
  const previous = tracker.currentToken
  let token = previous
  let phase = '站立'

  if (wrists > 0.5 && hip > 150 && knee > 140) {
    token = 'plank'
    phase = '支撑'
    tracker.auxCount = 1
  } else if (wrists > 0.35 && knee < 125) {
    token = 'squatdown'
    phase = '下蹲撑地'
  } else if (knee < 115) {
    token = 'tuck'
    phase = '收腿'
  } else {
    token = 'stand'
    if (tracker.auxCount === 1 && previous !== 'stand') {
      tracker.repetitions += 1
      tracker.auxCount = 0
    }
  }

  tracker.currentToken = token
  pushPhase(tracker, phase, frameIndex)
}

function buildSquatChecks(history: PoseSnapshot[]) {
  const knees = history.map(item => average([item.leftKnee, item.rightKnee]))
  const spreads = history.map(item => item.ankleSpreadRatio)
  const drifts = history.map(item => item.torsoDrift)
  const depthPass = minValid(knees, 180) < 112
  const stanceAvg = average(spreads) ?? 0.8
  const torsoMax = maxValid(drifts, 0)

  return {
    formChecks: [
      { name: '深度是否到位', passed: depthPass, detail: depthPass ? '下蹲深度已经接近有效区间。' : '下蹲还不够深，建议继续把髋部坐低。' },
      { name: '站距是否稳定', passed: stanceAvg > 0.95 && stanceAvg < 1.8, detail: stanceAvg > 0.95 && stanceAvg < 1.8 ? '站距基本稳定。' : '双脚站距偏窄或偏宽，先把站距固定住。' },
      { name: '躯干是否平稳', passed: torsoMax < 0.11, detail: torsoMax < 0.11 ? '躯干保持得比较稳。' : '躯干有明显侧移，建议放慢节奏。' }
    ] satisfies FormCheck[],
    suggestions: [
      depthPass ? null : '继续增加下蹲幅度，底部停顿半拍再起身。',
      stanceAvg > 0.95 && stanceAvg < 1.8 ? null : '先固定双脚站距，再开始连续深蹲。',
      torsoMax < 0.11 ? null : '收紧核心，减少下蹲过程中的侧移。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildJumpingJackChecks(history: PoseSnapshot[]) {
  const armLiftMax = maxValid(history.map(item => item.armLiftRatio), -0.2)
  const spreadMax = maxValid(history.map(item => item.ankleSpreadRatio), 0.8)
  return {
    formChecks: [
      { name: '双臂是否打开', passed: armLiftMax > 0.22, detail: armLiftMax > 0.22 ? '双臂已经越过肩线。' : '抬臂还不够高，建议把手臂再打开一些。' },
      { name: '双脚是否张开', passed: spreadMax > 1.45, detail: spreadMax > 1.45 ? '双脚张开幅度足够。' : '脚步打开幅度不足，建议加大横向步幅。' },
      { name: '节奏是否连续', passed: history.length >= REQUIRED_POSE_FRAMES, detail: history.length >= REQUIRED_POSE_FRAMES ? '实时节奏连续。' : '节奏还在建立中，保持连续动作。' }
    ] satisfies FormCheck[],
    suggestions: [
      armLiftMax > 0.22 ? null : '双臂张开时尽量抬过肩线。',
      spreadMax > 1.45 ? null : '双脚打开再大一点，动作会更完整。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildLungeChecks(history: PoseSnapshot[]) {
  const spreadMax = maxValid(history.map(item => item.ankleSpreadRatio), 0.8)
  const bentMin = minValid(history.map(item => Math.min(item.leftKnee ?? 180, item.rightKnee ?? 180)), 180)
  const torsoMax = maxValid(history.map(item => item.torsoDrift), 0)
  return {
    formChecks: [
      { name: '步距是否足够', passed: spreadMax > 1.2, detail: spreadMax > 1.2 ? '前后脚距离基本够用。' : '步距偏短，先把前后脚拉开。' },
      { name: '下沉是否充分', passed: bentMin < 115, detail: bentMin < 115 ? '下沉幅度基本有效。' : '下沉深度偏浅，可以再稳定下沉一些。' },
      { name: '躯干是否稳定', passed: torsoMax < 0.11, detail: torsoMax < 0.11 ? '躯干整体比较稳定。' : '躯干晃动明显，建议先减慢节奏。' }
    ] satisfies FormCheck[],
    suggestions: [
      spreadMax > 1.2 ? null : '先把前后脚间距拉开，再开始弓步。',
      bentMin < 115 ? null : '继续向下沉，别只做半程弓步。',
      torsoMax < 0.11 ? null : '保持胸口稳定，起身不要急冲。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildPushUpChecks(history: PoseSnapshot[]) {
  const elbowMin = minValid(history.map(item => average([item.leftElbow, item.rightElbow])), 180)
  const hipMin = minValid(history.map(item => average([item.leftHip, item.rightHip])), 180)
  const lineMax = maxValid(history.map(item => item.plankLineOffset), 1)
  return {
    formChecks: [
      { name: '下放是否充分', passed: elbowMin < 100, detail: elbowMin < 100 ? '下放深度已经接近有效区间。' : '下放还不够深，建议让肘角更清晰。' },
      { name: '核心是否稳定', passed: hipMin > 145, detail: hipMin > 145 ? '身体主线比较稳定。' : '推起时有塌腰风险，建议先收紧核心。' },
      { name: '支撑位是否平整', passed: lineMax < 0.15, detail: lineMax < 0.15 ? '肩到髋的线条比较稳定。' : '身体主线波动较大，先降低节奏。' }
    ] satisfies FormCheck[],
    suggestions: [
      elbowMin < 100 ? null : '再多下放一点，动作会更完整。',
      hipMin > 145 ? null : '全程收紧腹部，避免塌腰。',
      lineMax < 0.15 ? null : '先放慢节奏，再做完整推起。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildHighKneesChecks(history: PoseSnapshot[]) {
  const leftMax = maxValid(history.map(item => item.leftKneeLift), 0)
  const rightMax = maxValid(history.map(item => item.rightKneeLift), 0)
  const torsoMax = maxValid(history.map(item => item.torsoDrift), 0)
  const alternations = history.slice(-20).reduce((count, item, index, arr) => {
    if (index === 0) return 0
    const prevSide = (arr[index - 1].leftKneeLift ?? 0) > (arr[index - 1].rightKneeLift ?? 0) ? 'left' : 'right'
    const currSide = (item.leftKneeLift ?? 0) > (item.rightKneeLift ?? 0) ? 'left' : 'right'
    return currSide !== prevSide ? count + 1 : count
  }, 0)
  return {
    formChecks: [
      { name: '抬膝高度', passed: leftMax > 0.08 && rightMax > 0.08, detail: leftMax > 0.08 && rightMax > 0.08 ? '左右抬膝高度都已经建立。' : '至少一侧抬膝还不够高。' },
      { name: '左右切换', passed: alternations >= 4, detail: alternations >= 4 ? '左右切换节奏已经建立。' : '左右交替还不够连贯。' },
      { name: '躯干稳定', passed: torsoMax < 0.13, detail: torsoMax < 0.13 ? '上身基本稳定。' : '上身晃动偏多，建议先慢一点。' }
    ] satisfies FormCheck[],
    suggestions: [
      leftMax > 0.08 && rightMax > 0.08 ? null : '继续把膝盖提高到更清晰的位置。',
      alternations >= 4 ? null : '保持连续交替，不要频繁停顿。',
      torsoMax < 0.13 ? null : '减少左右晃动，先把节奏放稳。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildStandingKneeRaiseChecks(history: PoseSnapshot[]) {
  const peakLift = maxValid(history.map(item => Math.max(item.leftKneeLift ?? 0, item.rightKneeLift ?? 0)), 0)
  const supportLift = maxValid(history.map(item => Math.min(item.leftKneeLift ?? 0, item.rightKneeLift ?? 0)), 0)
  const torsoMax = maxValid(history.map(item => item.torsoDrift), 0)
  return {
    formChecks: [
      { name: '提膝高度', passed: peakLift > 0.1, detail: peakLift > 0.1 ? '提膝顶点已经比较清晰。' : '提膝高度还不够，建议再抬高一些。' },
      { name: '支撑腿稳定', passed: supportLift < 0.05, detail: supportLift < 0.05 ? '支撑侧比较稳定。' : '支撑腿代偿较多，建议减慢节奏。' },
      { name: '躯干控制', passed: torsoMax < 0.12, detail: torsoMax < 0.12 ? '上身控制较稳。' : '上身摇晃明显，先稳定再提膝。' }
    ] satisfies FormCheck[],
    suggestions: [
      peakLift > 0.1 ? null : '提膝时继续抬高到清晰顶点。',
      supportLift < 0.05 ? null : '减慢放下速度，别让支撑腿乱晃。',
      torsoMax < 0.12 ? null : '收紧核心，减少身体摆动。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildGluteBridgeChecks(history: PoseSnapshot[]) {
  const hipMax = maxValid(history.map(item => average([item.leftHip, item.rightHip])), 0)
  const lineMin = minValid(history.map(item => item.plankLineOffset), 1)
  const hipRange = rangeValid(history.map(item => average([item.leftHip, item.rightHip])))
  return {
    formChecks: [
      { name: '顶峰髋伸展', passed: hipMax > 155, detail: hipMax > 155 ? '桥顶髋部已经抬到较完整位置。' : '上顶还不够高，建议继续抬髋。' },
      { name: '桥顶线条', passed: lineMin < 0.12, detail: lineMin < 0.12 ? '桥顶主线比较平整。' : '桥顶线条还不够平，注意髋部继续上顶。' },
      { name: '动作范围', passed: hipRange > 20, detail: hipRange > 20 ? '上顶和下放的范围已经建立。' : '动作幅度偏小，建议做完整上下程。' }
    ] satisfies FormCheck[],
    suggestions: [
      hipMax > 155 ? null : '桥顶时再把髋部推高一点。',
      lineMin < 0.12 ? null : '顶峰时夹紧臀部，别只靠腰部发力。',
      hipRange > 20 ? null : '继续做完整下放和上顶。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildPlankChecks(history: PoseSnapshot[]) {
  const hipMin = minValid(history.map(item => average([item.leftHip, item.rightHip])), 180)
  const lineMax = maxValid(history.map(item => item.plankLineOffset), 1)
  const lineRange = rangeValid(history.map(item => item.plankLineOffset))
  return {
    formChecks: [
      { name: '身体主线', passed: hipMin > 150 && lineMax < 0.14, detail: hipMin > 150 && lineMax < 0.14 ? '身体主线基本稳定。' : '肩到髋的线条还不够稳定。' },
      { name: '塌腰控制', passed: lineMax < 0.14, detail: lineMax < 0.14 ? '没有明显塌腰。' : '存在塌腰或抬臀偏高的情况。' },
      { name: '保持稳定', passed: lineRange < 0.07, detail: lineRange < 0.07 ? '保持阶段比较稳定。' : '保持时波动较大，建议重新收紧核心。' }
    ] satisfies FormCheck[],
    suggestions: [
      hipMin > 150 && lineMax < 0.14 ? null : '从肩到髋尽量保持一条直线。',
      lineMax < 0.14 ? null : '避免塌腰，必要时缩短保持时长。',
      lineRange < 0.07 ? null : '先缩短每次保持时间，再逐渐延长。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildBurpeeChecks(history: PoseSnapshot[], tracker: ActionTracker) {
  const kneeMin = minValid(history.map(item => average([item.leftKnee, item.rightKnee])), 180)
  const wristMax = maxValid(history.map(item => item.wristBelowShoulderRatio), 0)
  const plankMin = minValid(history.map(item => item.plankLineOffset), 1)
  return {
    formChecks: [
      { name: '是否完成下蹲撑地', passed: kneeMin < 120 && wristMax > 0.35, detail: kneeMin < 120 && wristMax > 0.35 ? '下蹲撑地阶段已经建立。' : '下蹲撑地还不够完整。' },
      { name: '是否进入稳定支撑', passed: plankMin < 0.16, detail: plankMin < 0.16 ? '支撑位已经出现。' : '支撑位还不够稳定，建议放慢过渡。' },
      { name: '是否完成完整循环', passed: tracker.repetitions > 0, detail: tracker.repetitions > 0 ? '已经识别到完整波比跳循环。' : '还没有识别到完整的站立返回。' }
    ] satisfies FormCheck[],
    suggestions: [
      kneeMin < 120 && wristMax > 0.35 ? null : '下蹲时继续把手清晰撑到地面。',
      plankMin < 0.16 ? null : '进入支撑位后先稳定身体主线。',
      tracker.repetitions > 0 ? null : '完成支撑后记得完整回到站立。'
    ].filter((item): item is string => Boolean(item))
  }
}

function buildLiveResult(
  action: SupportedRealtimeAction,
  tracker: ActionTracker,
  history: PoseSnapshot[],
  totalFrames: number,
  poseFrames: number
) {
  const descriptor = ACTION_MAP[action]
  const predictions = buildPredictionList(history)
  const scoreLookup = Object.fromEntries(predictions.map(item => [item.label, item.score])) as Record<string, number>
  const score = scoreLookup[descriptor.label] ?? 0
  const scorePercent = Math.round(score * 100)

  let checks: { formChecks: FormCheck[]; suggestions: string[] }
  let metrics: JointAngleMetric[] = []

  switch (action) {
    case 'bodyweight_squat':
      checks = buildSquatChecks(history)
      metrics = [
        metricFromSeries('knee_flexion', '膝关节角度', 'deg', history.map(item => average([item.leftKnee, item.rightKnee]))),
        metricFromSeries('hip_flexion', '髋关节角度', 'deg', history.map(item => average([item.leftHip, item.rightHip]))),
        metricFromSeries('stance_ratio', '站距比例', 'x', history.map(item => item.ankleSpreadRatio))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'jumping_jack':
      checks = buildJumpingJackChecks(history)
      metrics = [
        metricFromSeries('arm_lift', '抬臂比例', 'x', history.map(item => item.armLiftRatio)),
        metricFromSeries('stance_ratio', '双脚张开比例', 'x', history.map(item => item.ankleSpreadRatio)),
        metricFromSeries('elbow_angle', '肘关节角度', 'deg', history.map(item => average([item.leftElbow, item.rightElbow])))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'forward_lunge':
      checks = buildLungeChecks(history)
      metrics = [
        metricFromSeries('front_knee', '前侧膝角', 'deg', history.map(item => Math.min(item.leftKnee ?? 180, item.rightKnee ?? 180))),
        metricFromSeries('rear_knee', '后侧膝角', 'deg', history.map(item => Math.max(item.leftKnee ?? 180, item.rightKnee ?? 180))),
        metricFromSeries('stride_ratio', '步距比例', 'x', history.map(item => item.ankleSpreadRatio))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'push_up':
      checks = buildPushUpChecks(history)
      metrics = [
        metricFromSeries('elbow_angle', '肘关节角度', 'deg', history.map(item => average([item.leftElbow, item.rightElbow]))),
        metricFromSeries('hip_angle', '髋部角度', 'deg', history.map(item => average([item.leftHip, item.rightHip]))),
        metricFromSeries('body_line', '身体主线偏移', 'x', history.map(item => item.plankLineOffset))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'high_knees':
      checks = buildHighKneesChecks(history)
      metrics = [
        metricFromSeries('left_knee_lift', '左膝抬起比例', 'x', history.map(item => item.leftKneeLift)),
        metricFromSeries('right_knee_lift', '右膝抬起比例', 'x', history.map(item => item.rightKneeLift)),
        metricFromSeries('torso_drift', '躯干侧移', 'x', history.map(item => item.torsoDrift))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'standing_knee_raise':
      checks = buildStandingKneeRaiseChecks(history)
      metrics = [
        metricFromSeries('peak_knee_lift', '最高提膝比例', 'x', history.map(item => Math.max(item.leftKneeLift ?? 0, item.rightKneeLift ?? 0))),
        metricFromSeries('support_leg_motion', '支撑腿扰动', 'x', history.map(item => Math.min(item.leftKneeLift ?? 0, item.rightKneeLift ?? 0))),
        metricFromSeries('torso_drift', '躯干侧移', 'x', history.map(item => item.torsoDrift))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'glute_bridge':
      checks = buildGluteBridgeChecks(history)
      metrics = [
        metricFromSeries('hip_angle', '髋部角度', 'deg', history.map(item => average([item.leftHip, item.rightHip]))),
        metricFromSeries('hip_height', '髋高比例', 'x', history.map(item => item.hipHeightRatio)),
        metricFromSeries('bridge_line', '桥顶线条偏移', 'x', history.map(item => item.plankLineOffset))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'plank':
      checks = buildPlankChecks(history)
      metrics = [
        metricFromSeries('hip_angle', '髋部角度', 'deg', history.map(item => average([item.leftHip, item.rightHip]))),
        metricFromSeries('plank_line', '身体主线偏移', 'x', history.map(item => item.plankLineOffset)),
        metricFromSeries('torso_drift', '躯干侧移', 'x', history.map(item => item.torsoDrift))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
    case 'burpee':
      checks = buildBurpeeChecks(history, tracker)
      metrics = [
        metricFromSeries('knee_motion', '膝角范围', 'deg', history.map(item => average([item.leftKnee, item.rightKnee]))),
        metricFromSeries('wrist_drop', '撑地深度', 'x', history.map(item => item.wristBelowShoulderRatio)),
        metricFromSeries('plank_line', '支撑位偏移', 'x', history.map(item => item.plankLineOffset))
      ].filter((item): item is JointAngleMetric => item !== null)
      break
  }

  const poseRatio = totalFrames > 0 ? poseFrames / totalFrames : 0
  const hint =
    poseRatio < 0.45
      ? '有效姿态帧偏少，请把全身完整放进镜头并保持固定机位。'
      : scorePercent >= 75
        ? '本地识别稳定，当前节奏和动作幅度已经比较完整。'
        : scorePercent >= 60
          ? '本地识别已建立，建议继续放慢节奏并保持动作完整。'
          : '当前动作还不够稳定，建议先放慢节奏并保证完整入镜。'

  const suggestions = [...checks.suggestions, ...descriptor.tips]
    .filter((item, index, array) => array.indexOf(item) === index)
    .slice(0, 4)

  return {
    success: true,
    label: descriptor.label,
    labelZh: descriptor.labelZh,
    score,
    scorePercent,
    standard: scorePercent >= 70,
    hint,
    suggestions,
    topPredictions: predictions,
    poseFrames,
    totalFrames,
    sequenceFrames: REQUIRED_POSE_FRAMES,
    source: 'mediapipe-browser-local',
    repetitions: tracker.repetitions,
    currentPhase: tracker.currentPhase,
    phaseTimeline: [...tracker.phaseTimeline],
    jointAngles: metrics,
    formChecks: checks.formChecks
  } satisfies ExerciseActionAnalysisResult
}

async function ensurePoseLandmarker() {
  if (!sharedLandmarkerPromise) {
    sharedLandmarkerPromise = FilesetResolver.forVisionTasks(WASM_ROOT).then(vision =>
      PoseLandmarker.createFromOptions(vision, {
        baseOptions: {
          modelAssetPath: MODEL_ASSET_PATH,
          delegate: 'GPU'
        },
        runningMode: 'VIDEO',
        numPoses: 1,
        minPoseDetectionConfidence: 0.55,
        minPosePresenceConfidence: 0.55,
        minTrackingConfidence: 0.55
      })
    )
  }
  return sharedLandmarkerPromise
}

export function useLocalPoseCoach() {
  const status = ref<LocalPoseStatus>('idle')
  const statusMessage = ref('')
  const actionSelectionMode = ref<ActionSelectionMode>('auto')
  const manualSelectedAction = ref<SupportedRealtimeAction>('bodyweight_squat')
  const effectiveAction = ref<SupportedRealtimeAction>('bodyweight_squat')
  const autoDetectedAction = ref<SupportedRealtimeAction | null>(null)
  const capturedFrames = ref(0)
  const poseFrames = ref(0)
  const requiredFrames = ref(REQUIRED_POSE_FRAMES)
  const result = ref<ExerciseActionAnalysisResult | null>(null)
  const supportedActions = ACTIONS
  const selectedAction = computed(() => effectiveAction.value)

  let stream: MediaStream | null = null
  let videoEl: HTMLVideoElement | null = null
  let canvasEl: HTMLCanvasElement | null = null
  let landmarker: PoseLandmarker | null = null
  let animationFrameId: number | null = null
  let lastAnalyzedAt = 0
  let snapshots: PoseSnapshot[] = []
  let tracker = createTracker()
  let autoCandidateAction: SupportedRealtimeAction | null = null
  let autoCandidateFrames = 0
  let lastAutoSwitchAt = 0

  const isActive = computed(() => status.value === 'starting' || status.value === 'tracking')

  function resetAnalysisState() {
    capturedFrames.value = 0
    poseFrames.value = 0
    result.value = null
    snapshots = []
    tracker = createTracker()
  }

  function resetAutoDetectionState() {
    autoDetectedAction.value = null
    autoCandidateAction = null
    autoCandidateFrames = 0
    lastAutoSwitchAt = 0
  }

  function drawOverlay(landmarks: Landmark[] | null) {
    const canvas = canvasEl
    const video = videoEl
    if (!canvas || !video) return

    const width = video.videoWidth || 960
    const height = video.videoHeight || 540
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
    }

    const context = canvas.getContext('2d')
    if (!context) return

    context.clearRect(0, 0, width, height)
    if (!landmarks) {
      return
    }

    context.lineWidth = 3
    context.strokeStyle = 'rgba(132, 209, 169, 0.78)'
    context.fillStyle = 'rgba(255, 244, 221, 0.92)'

    for (const [startIndex, endIndex] of POSE_CONNECTIONS) {
      const start = getPoint(landmarks, startIndex, 0.2)
      const end = getPoint(landmarks, endIndex, 0.2)
      if (!start || !end) continue
      context.beginPath()
      context.moveTo(start.x * width, start.y * height)
      context.lineTo(end.x * width, end.y * height)
      context.stroke()
    }

    landmarks.forEach((landmark, index) => {
      if ((landmark.visibility ?? 1) < 0.2) return
      const radius = index >= 23 ? 4.5 : 3.5
      context.beginPath()
      context.arc(landmark.x * width, landmark.y * height, radius, 0, Math.PI * 2)
      context.fill()
    })
  }

  function updateTracker(snapshot: PoseSnapshot) {
    const frameIndex = capturedFrames.value
    switch (effectiveAction.value) {
      case 'bodyweight_squat':
        updateSquatTracker(tracker, snapshot, frameIndex)
        return
      case 'jumping_jack':
        updateJumpingJackTracker(tracker, snapshot, frameIndex)
        return
      case 'forward_lunge':
        updateLungeTracker(tracker, snapshot, frameIndex)
        return
      case 'push_up':
        updatePushUpTracker(tracker, snapshot, frameIndex)
        return
      case 'high_knees':
        updateHighKneesTracker(tracker, snapshot, frameIndex)
        return
      case 'standing_knee_raise':
        updateStandingKneeRaiseTracker(tracker, snapshot, frameIndex)
        return
      case 'glute_bridge':
        updateGluteBridgeTracker(tracker, snapshot, frameIndex)
        return
      case 'plank':
        updatePlankTracker(tracker, snapshot, frameIndex)
        return
      case 'burpee':
        updateBurpeeTracker(tracker, snapshot, frameIndex)
        return
    }
  }

  function maybeAutoSwitchAction(now: number) {
    if (actionSelectionMode.value !== 'auto' || snapshots.length < AUTO_DETECT_MIN_HISTORY) {
      return false
    }

    const ranked = rankActions(snapshots)
    const top = ranked[0]
    if (!top) {
      return false
    }

    autoDetectedAction.value = top.action.key
    const secondScore = ranked[1]?.score ?? 0
    const scoreGap = top.score - secondScore
    const qualifies = top.score >= AUTO_DETECT_MIN_SCORE && scoreGap >= AUTO_DETECT_MIN_GAP

    if (!qualifies) {
      autoCandidateAction = null
      autoCandidateFrames = 0
      return false
    }

    if (autoCandidateAction === top.action.key) {
      autoCandidateFrames += 1
    } else {
      autoCandidateAction = top.action.key
      autoCandidateFrames = 1
    }

    if (effectiveAction.value === top.action.key) {
      return false
    }

    if (now - lastAutoSwitchAt < AUTO_SWITCH_COOLDOWN_MS) {
      return false
    }

    if (autoCandidateFrames < AUTO_DETECT_CONFIRM_FRAMES) {
      return false
    }

    effectiveAction.value = top.action.key
    lastAutoSwitchAt = now
    autoCandidateFrames = 0
    autoCandidateAction = top.action.key
    resetAnalysisState()
    statusMessage.value = `已自动锁定为 ${top.action.labelZh}，正在重新建立动作序列。`
    return true
  }

  async function analyzeFrame() {
    if (!landmarker || !videoEl) return
    const now = performance.now()
    if (now - lastAnalyzedAt < DETECT_INTERVAL_MS) {
      return
    }
    if (videoEl.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      return
    }

    lastAnalyzedAt = now
    capturedFrames.value += 1
    const detection = landmarker.detectForVideo(videoEl, now)
    const landmarks = (detection.landmarks?.[0] ?? null) as Landmark[] | null
    drawOverlay(landmarks)

    if (!landmarks) {
      status.value = 'tracking'
      statusMessage.value = `未检测到稳定姿态，已采样 ${capturedFrames.value} 帧。`
      return
    }

    poseFrames.value += 1
    const snapshot = buildSnapshot(landmarks, now)
    snapshots.push(snapshot)
    if (snapshots.length > MAX_SNAPSHOTS) {
      snapshots = snapshots.slice(-MAX_SNAPSHOTS)
    }

    if (maybeAutoSwitchAction(now)) {
      status.value = 'tracking'
      return
    }

    updateTracker(snapshot)

    if (poseFrames.value < REQUIRED_POSE_FRAMES) {
      status.value = 'tracking'
      statusMessage.value = `本地姿态引擎已就绪，正在建立动作序列 ${poseFrames.value}/${REQUIRED_POSE_FRAMES}。`
      return
    }

    result.value = buildLiveResult(effectiveAction.value, tracker, snapshots, capturedFrames.value, poseFrames.value)
    status.value = 'tracking'
    statusMessage.value = '浏览器端实时识别中，当前结果不依赖实时 WebSocket。'
  }

  function loop() {
    animationFrameId = window.requestAnimationFrame(async () => {
      try {
        await analyzeFrame()
      } catch (error: any) {
        status.value = 'error'
        statusMessage.value = error?.message || '本地姿态分析失败'
      } finally {
        if (isActive.value) {
          loop()
        }
      }
    })
  }

  function stopStream() {
    if (animationFrameId !== null) {
      window.cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
    if (stream) {
      stream.getTracks().forEach(track => track.stop())
      stream = null
    }
    if (videoEl) {
      videoEl.srcObject = null
    }
    drawOverlay(null)
  }

  async function waitForVideoReady(video: HTMLVideoElement) {
    if (video.readyState >= HTMLMediaElement.HAVE_METADATA) {
      return
    }
    await new Promise<void>((resolve, reject) => {
      const handleLoaded = () => {
        cleanup()
        resolve()
      }
      const handleError = () => {
        cleanup()
        reject(new Error('摄像头预览启动失败'))
      }
      const cleanup = () => {
        video.removeEventListener('loadedmetadata', handleLoaded)
        video.removeEventListener('error', handleError)
      }
      video.addEventListener('loadedmetadata', handleLoaded)
      video.addEventListener('error', handleError)
    })
  }

  async function start(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
    stop()
    videoEl = video
    canvasEl = canvas
    effectiveAction.value = actionSelectionMode.value === 'manual' ? manualSelectedAction.value : effectiveAction.value
    resetAnalysisState()
    resetAutoDetectionState()
    status.value = 'starting'
    statusMessage.value = '正在加载本地姿态识别模型和摄像头。'

    if (!navigator.mediaDevices?.getUserMedia) {
      status.value = 'error'
      statusMessage.value = '当前浏览器不支持摄像头采集。'
      return
    }

    try {
      landmarker = landmarker ?? await ensurePoseLandmarker()
      stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 960 },
          height: { ideal: 540 },
          facingMode: 'user'
        },
        audio: false
      })
      video.srcObject = stream
      video.muted = true
      video.playsInline = true
      await waitForVideoReady(video)
      await video.play()

      status.value = 'tracking'
      statusMessage.value = '摄像头已连接，正在采集稳定姿态。'
      loop()
    } catch (error: any) {
      stopStream()
      status.value = 'error'
      statusMessage.value = error?.message || '本地实时识别启动失败'
    }
  }

  function stop() {
    stopStream()
    status.value = 'idle'
    statusMessage.value = ''
    resetAutoDetectionState()
  }

  function setSelectedAction(nextAction: SupportedRealtimeAction) {
    manualSelectedAction.value = nextAction
    actionSelectionMode.value = 'manual'
    if (effectiveAction.value === nextAction) {
      return
    }
    effectiveAction.value = nextAction
    resetAnalysisState()
    resetAutoDetectionState()
    if (status.value === 'tracking') {
      statusMessage.value = '已切换为手动覆盖动作，正在重新建立本地动作序列。'
    }
  }

  function setSelectionMode(nextMode: ActionSelectionMode) {
    if (actionSelectionMode.value === nextMode) {
      return
    }

    actionSelectionMode.value = nextMode
    resetAnalysisState()

    if (nextMode === 'manual') {
      effectiveAction.value = manualSelectedAction.value
      resetAutoDetectionState()
      if (status.value === 'tracking') {
        statusMessage.value = '已切换为手动覆盖模式，正在重新建立本地动作序列。'
      }
      return
    }

    effectiveAction.value = manualSelectedAction.value
    resetAutoDetectionState()
    if (status.value === 'tracking') {
      statusMessage.value = '已切换为自动识别模式，正在判断当前动作。'
    }
  }

  return {
    status,
    statusMessage,
    supportedActions,
    actionSelectionMode,
    selectedAction,
    manualSelectedAction,
    autoDetectedAction,
    capturedFrames,
    poseFrames,
    requiredFrames,
    result,
    isActive,
    start,
    stop,
    setSelectedAction,
    setSelectionMode
  }
}
