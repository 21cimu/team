<template>
  <div class="countdown-wrapper" @click="toggle">
    <svg class="countdown-ring" :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <circle
        class="ring-bg"
        :cx="size / 2" :cy="size / 2" :r="radius"
        fill="none"
        :stroke="'rgba(248,250,252,0.08)'"
        :stroke-width="strokeWidth"
      />
      <circle
        class="ring-progress"
        :cx="size / 2" :cy="size / 2" :r="radius"
        fill="none"
        :stroke="ringColor"
        :stroke-width="strokeWidth"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        stroke-linecap="round"
        :class="{ 'ring-glow': isRunning }"
        :style="{ transform: 'rotate(-90deg)', transformOrigin: '50% 50%' }"
      />
    </svg>
    <div class="countdown-center">
      <span class="countdown-value" :class="{ 'value-warning': percent < 0.3 && isRunning }">
        {{ displaySeconds }}
      </span>
      <span class="countdown-unit">s</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'

const props = withDefaults(defineProps<{
  totalSeconds: number
  size?: number
  strokeWidth?: number
}>(), {
  size: 52,
  strokeWidth: 4
})

const emit = defineEmits<{
  (e: 'complete'): void
  (e: 'tick', remaining: number): void
}>()

const remaining = ref(props.totalSeconds)
const isRunning = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const radius = computed(() => (props.size - props.strokeWidth * 2) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const percent = computed(() => remaining.value / props.totalSeconds)
const dashOffset = computed(() => circumference.value * (1 - percent.value))
const displaySeconds = computed(() => Math.ceil(remaining.value))

const ringColor = computed(() => {
  if (!isRunning.value && remaining.value === props.totalSeconds) return 'rgba(248,250,252,0.2)'
  if (percent.value > 0.5) return '#22C55E'
  if (percent.value > 0.25) return '#FBBF24'
  return '#ef4444'
})

const toggle = () => {
  if (isRunning.value) {
    pause()
  } else {
    start()
  }
}

const start = () => {
  if (remaining.value <= 0) {
    remaining.value = props.totalSeconds
  }
  isRunning.value = true
  timer = setInterval(() => {
    remaining.value -= 0.1
    emit('tick', remaining.value)
    if (remaining.value <= 0) {
      remaining.value = 0
      pause()
      emit('complete')
    }
  }, 100)
}

const pause = () => {
  isRunning.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

const reset = () => {
  pause()
  remaining.value = props.totalSeconds
}

watch(() => props.totalSeconds, (newVal) => {
  pause()
  remaining.value = newVal
})

onBeforeUnmount(() => {
  pause()
})

defineExpose({ start, pause, reset, isRunning, remaining })
</script>

<style scoped>
.countdown-wrapper {
  position: relative;
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.countdown-ring {
  display: block;
}

.ring-progress {
  transition: stroke-dashoffset 0.1s linear, stroke 0.3s ease;
}

.ring-glow {
  filter: drop-shadow(0 0 6px currentColor);
}

.countdown-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: baseline;
  gap: 1px;
}

.countdown-value {
  font-family: 'Barlow Condensed', monospace;
  font-size: 16px;
  font-weight: 700;
  color: #F8FAFC;
  line-height: 1;
}

.countdown-unit {
  font-family: 'Barlow Condensed', monospace;
  font-size: 9px;
  color: rgba(248,250,252,0.4);
  line-height: 1;
}

.value-warning {
  animation: blink-warn 0.5s ease infinite alternate;
}

@keyframes blink-warn {
  from { opacity: 1; }
  to { opacity: 0.5; }
}
</style>
