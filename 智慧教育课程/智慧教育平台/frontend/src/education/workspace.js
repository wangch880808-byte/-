import { context, contexts, prepareContext as prepare, resetEducation } from '@/education/context'
import { educationRuntime, resetView as reset } from '@/education/runtime'
import { cancelRequests } from '@/utils/request'
import useUserStore from '@/store/modules/user'
export { context, contexts }
export const workspace=educationRuntime
workspace.tabs=[];workspace.notifications=[]
export function resetView(message=''){cancelRequests('page');cancelRequests('header');reset(message);workspace.notifications=[]}
export function forbidView(message){contexts.suspend();resetView(message)}
export function prepareContext(force=false,selection){return prepare(useUserStore(),force,selection)}
export function suspendWorkspace(){resetView();resetEducation();workspace.tabs=[]}
