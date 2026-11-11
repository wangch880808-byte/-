import { api } from '@/api/education/client.js'
export function validateExcel(file) {
  if (!file) return '请选择 Excel 文件'
  if (!/\.xlsx?$/i.test(file.name)) return '仅支持 .xls 或 .xlsx 文件'
  if (!file.size || file.size > 10 * 1024 * 1024) return '文件不能为空，且不能超过 10MB'
  return ''
}
export function plainImportMessage(value) {
  return String(value || '').replace(/<br\s*\/?\s*>/gi, '\n')
}
export async function fetchExcel(resource, action, filters, signal) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters || {})) if (value !== '' && value != null) params.set(key, String(value))
  return api.post('/system/' + resource + '/' + action, params,
    { responseType: 'blob', signal, timeout: 120000 })
}
export function saveExcel(blob, name) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = name
  document.body.appendChild(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
