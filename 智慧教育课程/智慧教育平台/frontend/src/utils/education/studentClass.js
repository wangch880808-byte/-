export function isCollegeDept(dept) {
  return dept.deptType === 'COLLEGE'
    || (!dept.deptType && /(?:学院|系)$/.test(dept.deptName.trim()))
}

export function studentClassOption(dept, byId) {
  if (dept.deptType !== 'CLASS' || String(dept.status) !== '0') return null
  const ancestorIds = String(dept.ancestors || '').split(',').slice(1)
  if ((ancestorIds.length !== 2 && ancestorIds.length !== 3)
    || ancestorIds.at(-1) !== String(dept.parentId)) return null

  // A scoped list can omit ancestors. The server validates the complete branch.
  const ancestors = ancestorIds.map(id => byId.get(id))
  const [school, college, major] = ancestors
  if (ancestors.some(parent => parent && String(parent.status) !== '0')
    || (school && (String(school.parentId) !== '0' || (school.deptType && school.deptType !== 'SCHOOL')))
    || (college && (!isCollegeDept(college) || String(college.parentId) !== ancestorIds[0]))
    || (major && (major.deptType !== 'MAJOR' || String(major.parentId) !== ancestorIds[1]))) return null

  return {
    value: String(dept.deptId),
    name: dept.deptName,
    path: [...ancestors.filter(Boolean), dept].map(node => node.deptName).join(' / '),
    schoolId: ancestorIds[0],
    schoolName: school?.deptName || '',
    collegeId: ancestorIds[1],
    collegeName: college?.deptName || '',
    majorName: major?.deptName || ''
  }
}
