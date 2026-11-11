import { shallowReactive } from 'vue'
export const pages=shallowReactive([])
export function registerBusinessPage(page){
 if(!page||!/^[a-z][a-z0-9-]*$/.test(page.id||'')||!/^[a-z][a-z0-9-]*$/.test(page.path||'')||!page.title||!page.permission||!page.serviceRoute||typeof page.component!=='function'||!Array.isArray(page.roles)||!page.roles.length||page.roles.some(r=>!['admin','teacher','student'].includes(r)))throw new Error('业务页面注册信息不完整')
 if(pages.some(p=>p.id===page.id||p.path===page.path))throw new Error('业务页面位置重复')
 if(page.requiresContext&&!['course','class'].includes(page.requiresContext))throw new Error('教学上下文类型无效')
 if(page.headerEntry&&(!page.headerEntry.label||(page.headerEntry.getUnreadCount&&typeof page.headerEntry.getUnreadCount!=='function')))throw new Error('顶部入口信息无效')
 pages.push(Object.freeze({...page}));window.dispatchEvent(new CustomEvent('edu:page-registered',{detail:page}))
}
