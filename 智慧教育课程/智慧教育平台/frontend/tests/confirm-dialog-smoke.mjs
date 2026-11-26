import { createApp, h } from 'vue'
import ConfirmDialog from '@/components/education/ConfirmDialog.vue'

/**
 * 公共弹窗隔离冒烟入口。
 *
 * 同时挂载两个 ConfirmDialog：
 * 1. warnings + confirmDisabled：只禁用确认按钮，取消和关闭保持可用；
 * 2. busy：确认、取消和关闭全部禁用，保持原有忙碌态语义。
 *
 * 该入口只在 Vite 开发服务器下加载，不进入生产构建。
 */
const Root = {
  render() {
    return h('div', { class: 'smoke-root' }, [
      h('section', { id: 'disabled-case' }, [
        h(ConfirmDialog, {
          title: '禁用确认按钮',
          text: '确认按钮单独禁用',
          warnings: ['警告一：保留新 main 的 warnings 展示。', '警告二：confirmDisabled 不得影响取消。'],
          confirmDisabled: true,
          onClose: () => {},
          onConfirm: () => {}
        })
      ]),
      h('section', { id: 'busy-case' }, [
        h(ConfirmDialog, {
          title: '忙碌态',
          text: '忙碌态关闭和取消语义不变',
          busy: true,
          onClose: () => {},
          onConfirm: () => {}
        })
      ])
    ])
  }
}

createApp(Root).mount('#app')
