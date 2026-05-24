import type { WeatherContextPayload } from '../api/ai'
import type { WeatherLive } from '../api/weather'

export const toWeatherContextPayload = (weather?: WeatherLive | null): WeatherContextPayload | undefined => {
  if (!weather) return undefined

  return {
    province: weather.province || '',
    city: weather.city || '',
    weather: weather.weather || '',
    temperature: weather.temperature || '',
    windDirection: weather.windDirection || '',
    windPower: weather.windPower || '',
    humidity: weather.humidity || '',
    reportTime: weather.reportTime || ''
  }
}
