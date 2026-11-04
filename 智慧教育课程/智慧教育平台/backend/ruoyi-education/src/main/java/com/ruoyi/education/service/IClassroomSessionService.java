package com.ruoyi.education.service;

import java.util.List;
import com.ruoyi.education.dto.classroom.ChangePageRequest;
import com.ruoyi.education.dto.classroom.SessionDetailResponse;
import com.ruoyi.education.dto.classroom.SessionEventItem;
import com.ruoyi.education.dto.classroom.SessionSnapshotResponse;
import com.ruoyi.education.dto.classroom.StartSessionRequest;
import com.ruoyi.education.dto.classroom.StartSessionResponse;
import com.ruoyi.education.enums.SnapshotView;

/**
 * 课堂会话服务。
 */
public interface IClassroomSessionService
{
    StartSessionResponse startSession(StartSessionRequest request, Long userId);

    SessionDetailResponse getSession(Long sessionId, Long userId);

    SessionSnapshotResponse getSnapshot(Long sessionId, SnapshotView view, Long userId);

    List<SessionEventItem> listEvents(Long sessionId, Long afterSeq, SnapshotView view, Long userId);

    SessionSnapshotResponse joinSession(Long sessionId, Long userId);

    void pauseSession(Long sessionId, Long userId);

    void resumeSession(Long sessionId, Long userId);

    SessionSnapshotResponse endSession(Long sessionId, Long userId);

    SessionSnapshotResponse changePage(Long sessionId, ChangePageRequest request, Long userId);

    SessionSnapshotResponse getActiveSession(Long bindingId, Long userId);
}
