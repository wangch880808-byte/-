package com.ruoyi.education.course.api;

/**
 * 教学班只读快照。跨领域（resource / prepare / classroom / activity / assignment / learning / analysis）
 * 只允许通过本契约取得教学班归属，不允许直接查 edu_teaching_class 或 join 它。
 *
 * <p>字段一旦被其他领域落库引用（例如任务的 class_id 快照），修改必须走契约变更流程。</p>
 *
 * @param classId        教学班 ID（edu_teaching_class.id），跨领域唯一引用键
 * @param name           教学班名称
 * @param code           教学班编码（同一学期内唯一）
 * @param termId         所属学期 ID
 * @param termName       学期名称（展示用，可为空）
 * @param organizationId 所属组织 ID
 * @param status         DRAFT / ACTIVE / ARCHIVED
 * @param classKind      FORMAL / TRIAL
 */
public record EduClassRef(
        Long classId,
        String name,
        String code,
        Long termId,
        String termName,
        Long organizationId,
        String status,
        String classKind) {

    /** 只有 ACTIVE 的教学班才对课堂、作业、资源发布开放。 */
    public boolean open() {
        return "ACTIVE".equals(status);
    }
}
