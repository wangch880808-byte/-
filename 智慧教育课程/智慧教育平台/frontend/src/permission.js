import router from '@/router'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import { bootstrap, canOpen, session } from '@/education/session'
import { prepareContext, resetView, workspace } from '@/education/workspace'

let navigation = 0
const versions = new WeakMap()

function finishNavigation(route) {
  workspace.pending = false
  if (route.query.denied) resetView('该页面未开放或没有访问权限')
}

router.beforeEach(async (to, from) => {
  const run = ++navigation
  versions.set(to, run)
  const token = getToken()
  const current = () => run === navigation && token === getToken()
  const redirect = target => {
    // Returning to the current URL is a duplicate navigation and skips this guard.
    if (router.resolve(target).fullPath !== from.fullPath) return target
    finishNavigation(from)
    return false
  }

  workspace.pending = true
  resetView()
  if (!token) return to.meta.public ? true : redirect({ path: '/login', query: { redirect: to.fullPath } })

  try {
    await bootstrap()
    if (!current()) return

    const home = '/' + session.role
    if (to.meta.public) return redirect(home)
    if ((to.meta.workspaceRole || to.params.role) !== session.role) return redirect(home)
    if (to.name === 'admin-forbidden') return true
    if (to.meta.adminPlaceholder && session.role !== 'admin') return redirect(home)

    const adminHome = to.name === 'home' && session.role === 'admin'
    const page = adminHome ? router.resolve('/admin/overview').meta.page : to.meta.page
    if (page && !canOpen(page)) {
      return session.role === 'admin'
        ? redirect({ name: 'admin-forbidden', replace: true })
        : redirect({ path: home, query: { denied: '1' } })
    }
    if (adminHome) return redirect('/admin/overview')
    if (to.name === 'course-detail' && String(to.query.courseSpaceId || '') !== String(to.params.courseId)) {
      return redirect({ path: to.path, query: { courseSpaceId: to.params.courseId } })
    }

    await prepareContext(false, to.query)
    if (current()) return true
  } catch (error) {
    if (run !== navigation || (getToken() && token !== getToken())) return
    if (error.cancelled) return false
    useUserStore().clearIdentity()
    return to.meta.public ? true : redirect({ path: '/login', query: { logout: 'expired' } })
  }
})

router.afterEach(to => {
  if (versions.get(to) === navigation) finishNavigation(to)
})
router.onError((_error, to) => {
  if (versions.get(to) === navigation) workspace.pending = false
})
