import { ElMessage, ElMessageBox } from 'element-plus'
export default {
  confirm: message => ElMessageBox.confirm(message, '系统提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }),
  msgSuccess: message => ElMessage.success(message),
  msgWarning: message => ElMessage.warning(message),
  msgError: message => ElMessage.error(message)
}
