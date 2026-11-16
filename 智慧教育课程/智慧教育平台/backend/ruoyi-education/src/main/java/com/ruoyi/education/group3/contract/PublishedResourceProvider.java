package com.ruoyi.education.group3.contract;

import java.util.List;
import java.util.Map;

/**
 * 已发布资料读取契约，供课堂、作业、AI 等下游组消费。
 * <p>
 * 实现方必须基于当前调用者（教师/学生）的对象范围做权限过滤，
 * 学生只能看到已对其开放（处于生效发布窗口内）的资料版本。
 * </p>
 */
public interface PublishedResourceProvider
{
    /**
     * 列出当前调用者在指定教学班下可见的已发布资料。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return 资料列表，每个 map 包含字段：
     *         <ul>
     *           <li>{@code bindingId} — 教学班绑定 ID</li>
     *           <li>{@code resourceId} — 资料 ID</li>
     *           <li>{@code versionId} — 资料版本 ID</li>
     *           <li>{@code title} — 资料标题</li>
     *           <li>{@code versionNo} — 版本号</li>
     *           <li>{@code mimeType} — MIME 类型</li>
     *           <li>{@code mediaKind} — 媒体类型（PDF/IMAGE/LINK 等）</li>
     *           <li>{@code pageCount} — 预览页数</li>
     *           <li>{@code pages} — 预览页列表（含 pageNo、url、widthPx、heightPx）</li>
     *           <li>{@code releaseId} — 发布记录 ID</li>
     *           <li>{@code opensAt} — 开放时间（ISO-8601 UTC）</li>
     *           <li>{@code closesAt} — 截止时间（ISO-8601 UTC）</li>
     *           <li>{@code downloadUrl} — 下载地址（相对路径）</li>
     *           <li>{@code serverTime} — 服务端当前时间（ISO-8601 UTC）</li>
     *         </ul>
     */
    List<Map<String, Object>> publishedResources(Long bindingId);

    /**
     * 获取单个已发布资料版本的详情。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @param versionId 资料版本 ID
     * @return 资料版本详情，字段同 {@link #publishedResources(Long)} 中单个元素
     */
    Map<String, Object> resourceVersion(Long bindingId, Long versionId);
}
