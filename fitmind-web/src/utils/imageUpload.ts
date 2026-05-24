export interface OptimizeImageOptions {
  maxWidth?: number
  maxHeight?: number
  quality?: number
  mimeType?: string
}

export const readImageAsOptimizedDataUrl = (
  file: File,
  options: OptimizeImageOptions = {}
): Promise<string> => {
  const {
    maxWidth = 1600,
    maxHeight = 1600,
    quality = 0.82,
    mimeType = 'image/jpeg'
  } = options

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.onload = () => {
      const src = reader.result
      if (typeof src !== 'string') {
        reject(new Error('图片数据无效'))
        return
      }

      const image = new Image()
      image.onerror = () => reject(new Error('解析图片失败'))
      image.onload = () => {
        let { width, height } = image
        const scale = Math.min(1, maxWidth / width, maxHeight / height)
        width = Math.max(1, Math.round(width * scale))
        height = Math.max(1, Math.round(height * scale))

        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('无法处理图片'))
          return
        }

        ctx.drawImage(image, 0, 0, width, height)
        resolve(canvas.toDataURL(mimeType, quality))
      }
      image.src = src
    }
    reader.readAsDataURL(file)
  })
}
