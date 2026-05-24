/**
 * 高德地图天气 API 封装（基于 JS API v1.4，适配「Web端JS API」Key）
 * 申请地址：https://console.amap.com/dev/key/app → 类型选「Web端(JS API)」
 */
const AMAP_KEY = import.meta.env.VITE_AMAP_KEY || ''
const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE || ''

declare global {
  interface Window {
    AMap: any
    _AMapSecurityConfig: { securityJsCode: string }
  }
}

export interface WeatherLive {
  province: string
  city: string
  weather: string
  temperature: string
  windDirection: string
  windPower: string
  humidity: string
  reportTime: string
}

// ── SDK 动态加载 ─────────────────────────────────────────────────────────────
let sdkReady: Promise<void> | null = null

function loadAmapSdk(): Promise<void> {
  if (sdkReady) return sdkReady

  sdkReady = new Promise<void>((resolve, reject) => {
    // 已经加载好了
    if (window.AMap?.Weather) {
      resolve()
      return
    }

    // ✅ 全局超时计时器放在 Promise 顶层，不依赖 onload 是否触发
    const timer = setTimeout(() => {
      sdkReady = null
      reject(new Error('AMap SDK 加载超时，请检查网络或高德 Key 是否正确'))
    }, 12000)

    const script = document.createElement('script')
    // 使用稳定的 1.4.15 版本，Weather/CitySearch 插件通过 URL 参数预加载
    script.src = `https://webapi.amap.com/maps?v=1.4.15&key=${AMAP_KEY}&plugin=AMap.Weather,AMap.CitySearch`
    script.async = true

    // ✅ 必须在 SDK 加载前注入安全密钥（2021年底后新建的Key强制要求）
    if (AMAP_SECURITY_CODE) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE }
    }

    script.onload = () => {
      if (!window.AMap) {
        clearTimeout(timer)
        sdkReady = null
        reject(new Error('AMap 对象不存在，Key 类型可能不是「Web端JS API」'))
        return
      }
      // 通过 plugin 回调确保插件已就绪（比轮询 window.AMap.Weather 更可靠）
      window.AMap.plugin(['AMap.Weather', 'AMap.CitySearch'], () => {
        clearTimeout(timer)
        console.debug('[Weather] AMap SDK + 插件加载完成')
        resolve()
      })
    }

    script.onerror = () => {
      clearTimeout(timer)
      sdkReady = null
      reject(new Error('AMap SDK 脚本加载失败，请检查网络连接'))
    }

    document.head.appendChild(script)
  })

  return sdkReady
}

/**
 * 查询实况天气，自动 IP 定位城市
 * @param adcodeOrCity 可选，指定城市 adcode 或名称；不传则自动定位
 */
export async function fetchWeather(adcodeOrCity?: string): Promise<WeatherLive> {
  await loadAmapSdk()

  return new Promise<WeatherLive>((resolve, reject) => {
    const weatherPlugin = new window.AMap.Weather()

    const doQuery = (target: string) => {
      console.debug('[Weather] 查询城市:', target)
      weatherPlugin.getLive(target, (err: any, data: any) => {
        if (err) {
          // 打印完整错误对象，便于诊断
          const detail = typeof err === 'object'
            ? (err?.info || err?.message || JSON.stringify(err))
            : String(err)
          console.warn('[Weather] getLive 失败 →', detail, err)
          reject(new Error(detail || '天气查询失败'))
          return
        }
        console.debug('[Weather] 天气数据:', data)
        resolve({
          province: data.province || '',
          city: data.city || target,
          weather: data.weather || '',
          temperature: String(data.temperature ?? ''),
          windDirection: data.windDirection || '',
          windPower: data.windPower || '',
          humidity: String(data.humidity ?? ''),
          reportTime: data.reportTime || ''
        })
      })
    }

    if (adcodeOrCity) {
      doQuery(adcodeOrCity)
    } else {
      const citySearch = new window.AMap.CitySearch()
      citySearch.getLocalCity((status: string, result: any) => {
        console.debug('[Weather] IP 定位:', status, result)
        if (status === 'complete' && result?.adcode) {
          doQuery(result.adcode)
        } else if (status === 'complete' && result?.city) {
          doQuery(result.city)
        } else {
          console.warn('[Weather] IP 定位失败，回退北京')
          doQuery('110000')
        }
      })
    }
  })
}
