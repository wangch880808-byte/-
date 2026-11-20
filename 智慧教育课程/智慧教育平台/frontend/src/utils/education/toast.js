let timer = null

/** 轻提示，2 秒后自动消失 */
export function toast(message, type = 'success') {
  const existing = document.querySelector('.app-toast')
  if (existing) existing.remove()
  clearTimeout(timer)

  const el = document.createElement('div')
  el.className = 'app-toast' + (type === 'error' ? ' error' : '')
  el.textContent = message
  document.body.appendChild(el)

  timer = setTimeout(() => el.remove(), 2200)
}
