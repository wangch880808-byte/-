package com.ruoyi.education.group3.support;

/** 第三组业务字典常量，避免发布来源与处理状态在代码中散落为魔法字符串。 */
public final class Group3Constants
{
    private Group3Constants()
    {
    }

    /** 发布来源：课程建设。 */
    public static final String RELEASE_STUDIO = "STUDIO";
    /** 发布来源：课前准备。 */
    public static final String RELEASE_PREPARE = "PREPARE";
    /** 发布来源：课程建设与课前准备均已发布。 */
    public static final String RELEASE_BOTH = "BOTH";
    /** 上传参数：仅保存资料，不自动对学生发布。 */
    public static final String RELEASE_NONE = "NONE";

    public static final String PROCESSING_PENDING = "PENDING";
    public static final String PROCESSING_READY = "READY";
    public static final String PROCESSING_FAILED = "FAILED";
}
