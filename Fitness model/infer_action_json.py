import argparse
import contextlib
import io
import json
import os
import sys
from collections import Counter, deque

import cv2
import mediapipe as mp
import numpy as np
import torch

with contextlib.redirect_stdout(io.StringIO()):
    from visualize_action_scores import (
        ACTION_NAME_ZH,
        LOW_SCORE_HINT_THRESHOLD,
        apply_benchpress_highjump_heuristic,
        apply_benchpress_stillrings_heuristic,
        apply_ropeclimbing_lunges_heuristic,
        apply_upper_body_climb_heuristic,
        build_display_names,
        extract_pose_sequence,
        load_class_names,
        load_model,
        predict_scores,
        prepare_model_input,
    )

LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28

ACTION_ADVICE = {
    "BenchPress": [
        "下放时保持手腕尽量叠在肘部上方，减少手腕折叠。",
        "推起前先稳定肩胛，避免耸肩抢力。",
        "尽量采用侧面或斜侧面机位，让手肘轨迹更清晰。",
    ],
    "BodyWeightSquats": [
        "先向后坐髋，再同步屈膝下蹲。",
        "下蹲时保持膝盖方向和脚尖基本一致。",
        "使用侧前方视角，便于判断深度和躯干角度。",
    ],
    "JumpingJack": [
        "手臂完全打开到头顶附近，减少半程动作。",
        "双脚打开与回收节奏尽量一致。",
        "拍摄时给头顶和脚下都留出足够空间。",
    ],
    "JumpRope": [
        "手肘贴近躯干，减少大幅甩肩。",
        "保持前脚掌轻落地，节奏尽量连续稳定。",
        "确保下肢完整入镜，便于识别弹跳节奏。",
    ],
    "Lunges": [
        "保持前后脚距离足够，躯干尽量竖直。",
        "下落时垂直下沉，不要明显向前顶膝。",
        "采用侧面机位更容易识别弓步深度。",
    ],
    "PullUps": [
        "先稳定悬垂，再主动向下收肘发力。",
        "减少摆腿和借力，尽量让核心保持收紧。",
        "保持单杠、头部和髋部都在画面中。",
    ],
    "PushUps": [
        "核心收紧，避免塌腰或撅臀。",
        "下放到明确屈肘后再稳定推起。",
        "斜侧面视角更利于判断肘角和躯干直线。",
    ],
    "WallPushups": [
        "从肩到脚跟尽量保持一条直线。",
        "靠近墙面时控制下放速度，不要反弹。",
        "人物与墙面接触位置都需要完整入镜。",
    ],
}


def parse_args():
    parser = argparse.ArgumentParser(description="Infer fitness action from a local video file.")
    parser.add_argument("--source", required=True, help="Local video path")
    parser.add_argument("--model-path", default="./trained_models/best_model.pth")
    parser.add_argument("--class-mapping", default="./processed_data/class_mapping.json")
    parser.add_argument("--sequence-length", type=int, default=100)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--min-frames", type=int, default=20)
    parser.add_argument("--smooth-factor", type=float, default=0.7)
    return parser.parse_args()


def resolve_local_path(path_value):
    candidate = os.path.expanduser(path_value)
    if os.path.isabs(candidate):
        return candidate
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(script_dir, candidate))


def round_num(value, digits=1):
    return round(float(value), digits)


def unique_keep_order(items):
    seen = set()
    result = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return result


def point_visible(frame, index, min_vis=0.35):
    return frame[index, 3] >= min_vis


def safe_point(frame, index, min_vis=0.35):
    if not point_visible(frame, index, min_vis):
        return None
    return frame[index, :3]


def calculate_angle(a, b, c):
    if a is None or b is None or c is None:
        return None
    ba = a[:2] - b[:2]
    bc = c[:2] - b[:2]
    norm_ba = np.linalg.norm(ba)
    norm_bc = np.linalg.norm(bc)
    if norm_ba < 1e-6 or norm_bc < 1e-6:
        return None
    cosine = np.dot(ba, bc) / (norm_ba * norm_bc)
    cosine = np.clip(cosine, -1.0, 1.0)
    return float(np.degrees(np.arccos(cosine)))


def angle_for_triplet(frame, a_idx, b_idx, c_idx, min_vis=0.35):
    return calculate_angle(
        safe_point(frame, a_idx, min_vis),
        safe_point(frame, b_idx, min_vis),
        safe_point(frame, c_idx, min_vis),
    )


def bilateral_angle(frame, left_triplet, right_triplet, min_vis=0.35):
    values = []
    left_value = angle_for_triplet(frame, *left_triplet, min_vis=min_vis)
    right_value = angle_for_triplet(frame, *right_triplet, min_vis=min_vis)
    if left_value is not None:
        values.append(left_value)
    if right_value is not None:
        values.append(right_value)
    if not values:
        return None
    return float(np.mean(values))


def active_side_angle(frame, left_triplet, right_triplet, prefer_smaller=True, min_vis=0.35):
    left_value = angle_for_triplet(frame, *left_triplet, min_vis=min_vis)
    right_value = angle_for_triplet(frame, *right_triplet, min_vis=min_vis)
    values = [value for value in [left_value, right_value] if value is not None]
    if not values:
        return None
    return min(values) if prefer_smaller else max(values)


def normalized_ankle_spread(frame):
    left_ankle = safe_point(frame, LEFT_ANKLE)
    right_ankle = safe_point(frame, RIGHT_ANKLE)
    left_shoulder = safe_point(frame, LEFT_SHOULDER)
    right_shoulder = safe_point(frame, RIGHT_SHOULDER)
    if left_ankle is None or right_ankle is None or left_shoulder is None or right_shoulder is None:
        return None
    shoulder_width = np.linalg.norm(left_shoulder[:2] - right_shoulder[:2])
    if shoulder_width < 1e-6:
        return None
    return float(abs(left_ankle[0] - right_ankle[0]) / shoulder_width)


def summarize_series(key, label, values):
    valid = [float(value) for value in values if value is not None]
    if not valid:
        return None
    return {
        "key": key,
        "label": label,
        "current": round_num(valid[-1]),
        "average": round_num(np.mean(valid)),
        "min": round_num(np.min(valid)),
        "max": round_num(np.max(valid)),
        "unit": "deg",
    }


def smooth_phase_sequence(phases, window=5):
    if not phases:
        return phases
    smoothed = []
    half_window = max(1, window // 2)
    for idx in range(len(phases)):
        left = max(0, idx - half_window)
        right = min(len(phases), idx + half_window + 1)
        bucket = [phase for phase in phases[left:right] if phase != "未识别"]
        if not bucket:
            smoothed.append(phases[idx])
            continue
        smoothed.append(Counter(bucket).most_common(1)[0][0])
    return smoothed


def build_phase_segments(phases, frame_indices):
    if not phases:
        return []
    segments = []
    start = 0
    for idx in range(1, len(phases) + 1):
        if idx == len(phases) or phases[idx] != phases[start]:
            if phases[start] != "未识别":
                start_frame = frame_indices[start]
                end_frame = frame_indices[idx - 1]
                segments.append(
                    {
                        "phase": phases[start],
                        "startFrame": int(start_frame),
                        "endFrame": int(end_frame),
                        "frameCount": int(end_frame - start_frame + 1),
                    }
                )
            start = idx
    return segments[-8:]


def count_cycles(phases, low_phase, high_phase):
    reps = 0
    state = "seek_high"
    for phase in phases:
        if phase == high_phase:
            if state == "seen_low":
                reps += 1
            state = "seen_high"
        elif phase == low_phase:
            if state == "seen_high":
                state = "seen_low"
    return reps


def build_form_check(name, passed, detail, suggestion):
    return {
        "name": name,
        "passed": bool(passed),
        "detail": detail,
        "suggestion": suggestion,
    }


def finalize_analysis(label, joint_angles, form_checks, phases, frame_indices, reps, base_advice):
    valid_joint_angles = [item for item in joint_angles if item is not None]
    smoothed_phases = smooth_phase_sequence(phases)
    current_phase = smoothed_phases[-1] if smoothed_phases else "未识别"
    phase_timeline = build_phase_segments(smoothed_phases, frame_indices)
    form_checks_clean = [
        {
            "name": check["name"],
            "passed": check["passed"],
            "detail": check["detail"],
        }
        for check in form_checks
    ]
    failed_suggestions = [check["suggestion"] for check in form_checks if not check["passed"]]
    advice = unique_keep_order(failed_suggestions + base_advice)[:5]

    return {
        "repetitions": int(reps),
        "currentPhase": current_phase,
        "phaseTimeline": phase_timeline,
        "jointAngles": valid_joint_angles,
        "formChecks": form_checks_clean,
        "advice": advice,
    }


def analyze_squat(pose_frames, frame_indices):
    knee_series = []
    hip_series = []
    phases = []

    prev_knee = None
    for frame in pose_frames:
        knee_angle = bilateral_angle(
            frame,
            (LEFT_HIP, LEFT_KNEE, LEFT_ANKLE),
            (RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE),
        )
        hip_angle = bilateral_angle(
            frame,
            (LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE),
            (RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE),
        )
        knee_series.append(knee_angle)
        hip_series.append(hip_angle)

        if knee_angle is None:
            phases.append("未识别")
        elif knee_angle <= 100:
            phases.append("底部")
        elif knee_angle >= 155:
            phases.append("顶部")
        elif prev_knee is not None and knee_angle < prev_knee - 2:
            phases.append("下降")
        elif prev_knee is not None and knee_angle > prev_knee + 2:
            phases.append("上升")
        else:
            phases.append("过渡")
        prev_knee = knee_angle if knee_angle is not None else prev_knee

    joint_angles = [
        summarize_series("kneeFlexion", "膝关节角", knee_series),
        summarize_series("hipFlexion", "髋关节角", hip_series),
    ]

    valid_knees = [value for value in knee_series if value is not None]
    valid_hips = [value for value in hip_series if value is not None]
    min_knee = min(valid_knees) if valid_knees else 180.0
    max_knee = max(valid_knees) if valid_knees else 0.0
    min_hip = min(valid_hips) if valid_hips else 180.0

    form_checks = [
        build_form_check(
            "下蹲深度",
            min_knee <= 105,
            f"最低膝角约 {round_num(min_knee)}°",
            "继续下蹲，让大腿更接近平行地面。",
        ),
        build_form_check(
            "站起伸展",
            max_knee >= 160,
            f"最高膝角约 {round_num(max_knee)}°",
            "站起时把膝和髋完全伸开，避免只做半程。",
        ),
        build_form_check(
            "躯干控制",
            min_hip >= 45,
            f"最低髋角约 {round_num(min_hip)}°",
            "下蹲时抬胸收紧核心，减少明显前倾。",
        ),
    ]
    reps = count_cycles(smooth_phase_sequence(phases), "底部", "顶部")
    return finalize_analysis("BodyWeightSquats", joint_angles, form_checks, phases, frame_indices, reps, ACTION_ADVICE["BodyWeightSquats"])


def analyze_lunges(pose_frames, frame_indices):
    knee_series = []
    hip_series = []
    phases = []
    prev_knee = None

    for frame in pose_frames:
        left_knee = angle_for_triplet(frame, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        right_knee = angle_for_triplet(frame, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        left_hip = angle_for_triplet(frame, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE)
        right_hip = angle_for_triplet(frame, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE)

        active_knee = active_side_angle(
            frame,
            (LEFT_HIP, LEFT_KNEE, LEFT_ANKLE),
            (RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE),
        )
        if active_knee is None:
            active_hip = None
        elif left_knee is None:
            active_hip = right_hip
        elif right_knee is None:
            active_hip = left_hip
        else:
            active_hip = left_hip if left_knee <= right_knee else right_hip

        knee_series.append(active_knee)
        hip_series.append(active_hip)

        if active_knee is None:
            phases.append("未识别")
        elif active_knee <= 105:
            phases.append("底部")
        elif active_knee >= 150:
            phases.append("顶部")
        elif prev_knee is not None and active_knee < prev_knee - 2:
            phases.append("下降")
        elif prev_knee is not None and active_knee > prev_knee + 2:
            phases.append("上升")
        else:
            phases.append("过渡")
        prev_knee = active_knee if active_knee is not None else prev_knee

    joint_angles = [
        summarize_series("leadKneeFlexion", "主力膝角", knee_series),
        summarize_series("leadHipFlexion", "主力髋角", hip_series),
    ]
    valid_knees = [value for value in knee_series if value is not None]
    valid_hips = [value for value in hip_series if value is not None]
    min_knee = min(valid_knees) if valid_knees else 180.0
    max_knee = max(valid_knees) if valid_knees else 0.0
    min_hip = min(valid_hips) if valid_hips else 180.0

    form_checks = [
        build_form_check(
            "弓步深度",
            min_knee <= 110,
            f"最低主力膝角约 {round_num(min_knee)}°",
            "继续下沉到底部，形成更明确的弓步深度。",
        ),
        build_form_check(
            "回到起始位",
            max_knee >= 155,
            f"最高主力膝角约 {round_num(max_knee)}°",
            "起身回位时把前后腿更完整地伸展。",
        ),
        build_form_check(
            "躯干竖直",
            min_hip >= 55,
            f"最低主力髋角约 {round_num(min_hip)}°",
            "保持胸口向上，减少弓步时过度前倾。",
        ),
    ]
    reps = count_cycles(smooth_phase_sequence(phases), "底部", "顶部")
    return finalize_analysis("Lunges", joint_angles, form_checks, phases, frame_indices, reps, ACTION_ADVICE["Lunges"])


def analyze_press_pattern(label, pose_frames, frame_indices):
    elbow_series = []
    body_line_series = []
    phases = []
    prev_elbow = None

    for frame in pose_frames:
        elbow_angle = bilateral_angle(
            frame,
            (LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST),
            (RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST),
        )
        body_line = bilateral_angle(
            frame,
            (LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE),
            (RIGHT_SHOULDER, RIGHT_HIP, RIGHT_ANKLE),
        )
        elbow_series.append(elbow_angle)
        body_line_series.append(body_line)

        if elbow_angle is None:
            phases.append("未识别")
        elif elbow_angle <= 95:
            phases.append("底部")
        elif elbow_angle >= 155:
            phases.append("顶部")
        elif prev_elbow is not None and elbow_angle < prev_elbow - 2:
            phases.append("下降")
        elif prev_elbow is not None and elbow_angle > prev_elbow + 2:
            phases.append("上升")
        else:
            phases.append("过渡")
        prev_elbow = elbow_angle if elbow_angle is not None else prev_elbow

    joint_angles = [
        summarize_series("elbowFlexion", "肘关节角", elbow_series),
        summarize_series("bodyLine", "身体直线角", body_line_series),
    ]
    valid_elbows = [value for value in elbow_series if value is not None]
    valid_body = [value for value in body_line_series if value is not None]
    min_elbow = min(valid_elbows) if valid_elbows else 180.0
    max_elbow = max(valid_elbows) if valid_elbows else 0.0
    avg_body = float(np.mean(valid_body)) if valid_body else 0.0

    form_checks = [
        build_form_check(
            "下放幅度",
            min_elbow <= 100,
            f"最低肘角约 {round_num(min_elbow)}°",
            "继续下放到更明显的屈肘位置，不要只做半程。",
        ),
        build_form_check(
            "推起锁定",
            max_elbow >= 155,
            f"最高肘角约 {round_num(max_elbow)}°",
            "推起时把手臂更完整地伸开，形成清晰顶端。",
        ),
        build_form_check(
            "身体稳定",
            avg_body >= 150 or label == "BenchPress",
            f"平均身体直线角约 {round_num(avg_body)}°",
            "收紧核心，减少塌腰或髋部下沉。",
        ),
    ]
    reps = count_cycles(smooth_phase_sequence(phases), "底部", "顶部")
    return finalize_analysis(label, joint_angles, form_checks, phases, frame_indices, reps, ACTION_ADVICE.get(label, []))


def analyze_pullup(pose_frames, frame_indices):
    elbow_series = []
    body_line_series = []
    phases = []
    prev_elbow = None

    for frame in pose_frames:
        elbow_angle = bilateral_angle(
            frame,
            (LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST),
            (RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST),
        )
        body_line = bilateral_angle(
            frame,
            (LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE),
            (RIGHT_SHOULDER, RIGHT_HIP, RIGHT_ANKLE),
        )
        elbow_series.append(elbow_angle)
        body_line_series.append(body_line)

        if elbow_angle is None:
            phases.append("未识别")
        elif elbow_angle <= 85:
            phases.append("顶端")
        elif elbow_angle >= 150:
            phases.append("悬垂")
        elif prev_elbow is not None and elbow_angle < prev_elbow - 2:
            phases.append("拉起")
        elif prev_elbow is not None and elbow_angle > prev_elbow + 2:
            phases.append("下放")
        else:
            phases.append("过渡")
        prev_elbow = elbow_angle if elbow_angle is not None else prev_elbow

    joint_angles = [
        summarize_series("elbowFlexion", "肘关节角", elbow_series),
        summarize_series("bodyLine", "身体直线角", body_line_series),
    ]
    valid_elbows = [value for value in elbow_series if value is not None]
    valid_body = [value for value in body_line_series if value is not None]
    min_elbow = min(valid_elbows) if valid_elbows else 180.0
    max_elbow = max(valid_elbows) if valid_elbows else 0.0
    avg_body = float(np.mean(valid_body)) if valid_body else 0.0

    form_checks = [
        build_form_check(
            "底部伸展",
            max_elbow >= 150,
            f"最大肘角约 {round_num(max_elbow)}°",
            "到底部时把手臂更完整伸开，形成清晰悬垂。",
        ),
        build_form_check(
            "拉起高度",
            min_elbow <= 90,
            f"最小肘角约 {round_num(min_elbow)}°",
            "向上拉起时继续收肘，减少半程引体。",
        ),
        build_form_check(
            "核心稳定",
            avg_body >= 145,
            f"平均身体直线角约 {round_num(avg_body)}°",
            "减少摆腿和躯干折叠，保持核心收紧。",
        ),
    ]
    reps = count_cycles(smooth_phase_sequence(phases), "顶端", "悬垂")
    return finalize_analysis("PullUps", joint_angles, form_checks, phases, frame_indices, reps, ACTION_ADVICE["PullUps"])


def analyze_jumping_jack(pose_frames, frame_indices):
    arm_series = []
    spread_series = []
    phases = []

    for frame in pose_frames:
        arm_angle = bilateral_angle(
            frame,
            (LEFT_ELBOW, LEFT_SHOULDER, LEFT_HIP),
            (RIGHT_ELBOW, RIGHT_SHOULDER, RIGHT_HIP),
        )
        ankle_spread = normalized_ankle_spread(frame)
        arm_series.append(arm_angle)
        spread_series.append(ankle_spread)

        if arm_angle is None or ankle_spread is None:
            phases.append("未识别")
        elif arm_angle >= 130 and ankle_spread >= 1.4:
            phases.append("展开")
        elif arm_angle <= 40 and ankle_spread <= 0.8:
            phases.append("合拢")
        elif len(phases) > 0 and phases[-1] == "合拢":
            phases.append("打开中")
        else:
            phases.append("回收中")

    arm_metric = summarize_series("shoulderOpenAngle", "肩部打开角", arm_series)
    valid_spread = [value for value in spread_series if value is not None]
    spread_metric = None
    if valid_spread:
        spread_metric = {
            "key": "stanceWidthRatio",
            "label": "下肢打开比例",
            "current": round_num(valid_spread[-1]),
            "average": round_num(np.mean(valid_spread)),
            "min": round_num(np.min(valid_spread)),
            "max": round_num(np.max(valid_spread)),
            "unit": "x",
        }

    max_arm = max([value for value in arm_series if value is not None], default=0.0)
    max_spread = max(valid_spread, default=0.0)
    form_checks = [
        build_form_check(
            "手臂打开",
            max_arm >= 145,
            f"最大肩部打开角约 {round_num(max_arm)}°",
            "手臂继续向头顶方向打开，避免停在半程。",
        ),
        build_form_check(
            "步幅打开",
            max_spread >= 1.5,
            f"最大下肢打开比例约 {round_num(max_spread)}x",
            "双脚再主动打开一些，形成更清晰的开合幅度。",
        ),
    ]
    reps = count_cycles(smooth_phase_sequence(phases), "展开", "合拢")
    return finalize_analysis("JumpingJack", [arm_metric, spread_metric], form_checks, phases, frame_indices, reps, ACTION_ADVICE["JumpingJack"])


def analyze_generic(label, pose_frames, frame_indices):
    knee_series = []
    elbow_series = []
    for frame in pose_frames:
        knee_series.append(
            bilateral_angle(
                frame,
                (LEFT_HIP, LEFT_KNEE, LEFT_ANKLE),
                (RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE),
            )
        )
        elbow_series.append(
            bilateral_angle(
                frame,
                (LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST),
                (RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST),
            )
        )
    joint_angles = [
        summarize_series("kneeFlexion", "膝关节角", knee_series),
        summarize_series("elbowFlexion", "肘关节角", elbow_series),
    ]
    form_checks = [
        build_form_check(
            "画面完整度",
            len(pose_frames) >= 20,
            f"有效姿态帧 {len(pose_frames)}",
            "录制更长的连续动作片段，避免只截取单个姿势。",
        )
    ]
    return finalize_analysis(label, joint_angles, form_checks, ["未识别"] * len(frame_indices), frame_indices, 0, ACTION_ADVICE.get(label, []))


def build_structured_analysis(label, pose_frames, frame_indices):
    if not pose_frames:
        return {
            "repetitions": 0,
            "currentPhase": "未识别",
            "phaseTimeline": [],
            "jointAngles": [],
            "formChecks": [],
            "advice": [],
        }

    if label == "BodyWeightSquats":
        return analyze_squat(pose_frames, frame_indices)
    if label == "Lunges":
        return analyze_lunges(pose_frames, frame_indices)
    if label in {"PushUps", "WallPushups", "BenchPress"}:
        return analyze_press_pattern(label, pose_frames, frame_indices)
    if label == "PullUps":
        return analyze_pullup(pose_frames, frame_indices)
    if label == "JumpingJack":
        return analyze_jumping_jack(pose_frames, frame_indices)
    return analyze_generic(label, pose_frames, frame_indices)


def build_hint(score, pose_frames, total_frames, analysis):
    coverage = 0.0 if total_frames <= 0 else pose_frames / total_frames
    reps = analysis.get("repetitions", 0)
    phase = analysis.get("currentPhase", "未识别")

    if coverage < 0.35:
        return "有效骨架帧过少，建议先调整光线、距离和机位，再重新上传。"
    if score >= 0.75:
        if reps > 0:
            return f"动作识别稳定，已识别 {reps} 次完整重复，当前处于“{phase}”阶段。"
        return f"动作识别稳定，当前处于“{phase}”阶段，可以继续优化动作控制。"
    if score >= LOW_SCORE_HINT_THRESHOLD:
        if reps > 0:
            return f"动作已识别，但稳定性一般。当前识别到 {reps} 次重复，建议放慢节奏并保持关键关节清晰。"
        return "动作已识别，但稳定性一般。建议放慢节奏，并保持全身完整入镜。"
    return "当前置信度偏低，可能是动作幅度不足，或视频角度没有覆盖关键关节。"


def serialize_predictions(class_names, display_names, scores, top_k):
    ranked = np.argsort(scores)[::-1][:top_k]
    predictions = []
    for idx in ranked:
        predictions.append(
            {
                "label": class_names[idx],
                "labelZh": display_names[idx],
                "score": round(float(scores[idx]), 6),
                "scorePercent": int(round(float(scores[idx]) * 100)),
            }
        )
    return predictions


def run_inference(args):
    source_path = args.source if os.path.isabs(args.source) else os.path.abspath(args.source)
    if not os.path.exists(source_path):
        raise FileNotFoundError(f"Video file does not exist: {source_path}")

    model_path = resolve_local_path(args.model_path)
    class_mapping_path = resolve_local_path(args.class_mapping)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    class_names = load_class_names(class_mapping_path)
    display_names = build_display_names(class_names)
    model, config = load_model(model_path, device)
    use_velocity = config.get("use_velocity", True)
    smooth_factor = float(np.clip(args.smooth_factor, 0.0, 0.99))
    sequence_length = max(1, int(args.sequence_length))
    top_k = max(1, min(int(args.top_k), len(class_names)))

    capture = cv2.VideoCapture(source_path)
    if not capture.isOpened():
        raise RuntimeError(f"Cannot open video: {source_path}")

    if not hasattr(mp, "solutions") or not hasattr(mp.solutions, "pose"):
        raise RuntimeError("Current Python environment has incompatible mediapipe. Please install mediapipe==0.10.14 in the configured interpreter.")

    mp_pose = mp.solutions.pose
    sequence_buffer = deque(maxlen=sequence_length)
    smoothed_scores = None
    score_history = []
    total_frames = 0
    pose_frames = 0
    pose_sequences = []
    pose_frame_indices = []

    with mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as pose:
        while True:
            success, frame = capture.read()
            if not success:
                break

            total_frames += 1
            pose_frame, _ = extract_pose_sequence(frame, pose)
            if pose_frame is not None:
                pose_frames += 1
                sequence_buffer.append(pose_frame)
                pose_sequences.append(pose_frame)
                pose_frame_indices.append(total_frames)

            if len(sequence_buffer) < max(1, args.min_frames):
                continue

            model_input = prepare_model_input(sequence_buffer, sequence_length, use_velocity)
            current_scores = predict_scores(model, model_input, device)
            current_scores = apply_benchpress_highjump_heuristic(current_scores, class_names, sequence_buffer)
            current_scores = apply_benchpress_stillrings_heuristic(current_scores, class_names, sequence_buffer)
            current_scores = apply_ropeclimbing_lunges_heuristic(current_scores, class_names, sequence_buffer)
            current_scores = apply_upper_body_climb_heuristic(current_scores, class_names, sequence_buffer)

            if smoothed_scores is None:
                smoothed_scores = current_scores
            else:
                smoothed_scores = smooth_factor * smoothed_scores + (1.0 - smooth_factor) * current_scores

            score_history.append(smoothed_scores.copy())

    capture.release()

    if not score_history:
        raise RuntimeError("No stable action prediction was produced. Please upload a clearer fitness video.")

    history_window = min(12, len(score_history))
    final_scores = np.mean(score_history[-history_window:], axis=0)
    final_scores = final_scores / np.clip(np.sum(final_scores), 1e-6, None)
    best_idx = int(np.argmax(final_scores))
    best_label = class_names[best_idx]
    best_score = float(final_scores[best_idx])
    structured_analysis = build_structured_analysis(best_label, pose_sequences, pose_frame_indices)

    return {
        "success": True,
        "label": best_label,
        "labelZh": ACTION_NAME_ZH.get(best_label, display_names[best_idx]),
        "score": round(best_score, 6),
        "scorePercent": int(round(best_score * 100)),
        "standard": best_score >= LOW_SCORE_HINT_THRESHOLD,
        "hint": build_hint(best_score, pose_frames, total_frames, structured_analysis),
        "advice": structured_analysis["advice"],
        "topK": serialize_predictions(class_names, display_names, final_scores, top_k),
        "poseFrames": pose_frames,
        "totalFrames": total_frames,
        "sequenceFrames": sequence_length,
        "source": "mediapipe-pytorch",
        "repetitions": structured_analysis["repetitions"],
        "currentPhase": structured_analysis["currentPhase"],
        "phaseTimeline": structured_analysis["phaseTimeline"],
        "jointAngles": structured_analysis["jointAngles"],
        "formChecks": structured_analysis["formChecks"],
    }


def main():
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    args = parse_args()
    result = run_inference(args)
    sys.stdout.write(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
