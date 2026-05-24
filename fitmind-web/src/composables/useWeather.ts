import { ref, readonly } from 'vue'
import { fetchWeather, type WeatherLive } from '../api/weather'

/** 天气现象 → emoji 映射 */
const weatherEmojiMap: [string, string][] = [
  ['龙卷风', '🌪️'], ['龙卷', '🌪️'],
  ['沙尘暴', '🌪️'], ['强沙尘暴', '🌪️'],
  ['冰雹', '🌩️'], ['雷电', '⚡'],
  ['强雷阵雨', '⛈️'], ['雷阵雨', '⛈️'],
  ['特大暴雨', '⛈️'], ['大暴雨', '⛈️'], ['暴雨', '⛈️'],
  ['强阵雨', '⛈️'], ['阵雨', '🌦️'],
  ['大雨', '🌧️'], ['中雨', '🌧️'], ['小雨', '🌦️'],
  ['毛毛雨', '🌦️'], ['细雨', '🌦️'],
  ['冻雨', '🧊'],
  ['暴雪', '🌨️'], ['阵雪', '🌨️'],
  ['大雪', '❄️'], ['中雪', '❄️'], ['小雪', '🌨️'],
  ['雨夹雪', '🌨️'], ['雨雪', '🌨️'],
  ['特强浓雾', '🌫️'], ['强浓雾', '🌫️'], ['浓雾', '🌫️'], ['雾', '🌫️'], ['轻雾', '🌫️'],
  ['严重霾', '😷'], ['重度霾', '😷'], ['中度霾', '😷'], ['霾', '😷'],
  ['浮尘', '😷'], ['扬沙', '😷'],
  ['飓风', '🌀'], ['烈风', '🌀'], ['大风', '🌀'],
  ['狂风', '💨'], ['强风', '💨'], ['疾风', '💨'], ['阵风', '💨'],
  ['和风', '🌬️'], ['微风', '🌬️'], ['有风', '🌬️'], ['清风', '🌬️'],
  ['多云', '☁️'], ['阴', '🌫️'],
  ['晴间多云', '⛅'], ['少云', '🌤️'],
  ['晴', '☀️'],
  ['热', '🔥'], ['冷', '🥶']
]

const getWeatherEmoji = (weather: string): string => {
  for (const [key, emoji] of weatherEmojiMap) {
    if (weather.includes(key)) return emoji
  }
  return '🌡️'
}

// ── 单例状态（全局共享，避免多实例重复请求）──────────────────────────────────
const weather = ref<WeatherLive | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let initialized = false
let refreshTimer: ReturnType<typeof setInterval> | null = null

export function useWeather() {
  const doFetch = async () => {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      weather.value = await fetchWeather()
    } catch (e: any) {
      error.value = e?.message || '天气获取失败'
      console.warn('[useWeather]', e)
    } finally {
      loading.value = false
    }
  }

  /** 首次挂载时调用，确保只初始化一次，每 30 分钟自动刷新 */
  const init = () => {
    if (initialized) return
    initialized = true
    doFetch()
    refreshTimer = setInterval(doFetch, 30 * 60 * 1000)
  }

  const dispose = () => {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
      initialized = false
    }
  }

  const weatherEmoji = () => (weather.value ? getWeatherEmoji(weather.value.weather) : '')

  return {
    weather: readonly(weather),
    loading: readonly(loading),
    error: readonly(error),
    weatherEmoji,
    fetchWeather: doFetch,
    init,
    dispose
  }
}
