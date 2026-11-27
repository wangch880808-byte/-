"""教师/学生公告通知链路真实 HTTP 联调验证脚本。

前置条件：一次性 MySQL/Redis、完整 ruoyi-admin、fixture.sql 已加载；
密码由 TEST_PASSWORD 环境变量提供，脚本不写入任何 Token、Cookie 或密码。
所有 HTTP 请求（含 /captchaImage 和 /login）都纳入请求统计。

负例自检：`python verify.py --self-test <case>` 使用受控输入触发一个失败断言，
写出报告并以非零退出码结束；真实链路默认无参数运行，全部断言通过才返回 0。
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:18080")
PASSWORD = os.environ.get("TEST_PASSWORD", "")
MYSQL = os.environ.get("MYSQL_BIN", "D:/develop/MySQL/MySQL Server 8.0/bin/mysql.exe")
MYSQL_HOST = os.environ.get("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = os.environ.get("MYSQL_PORT", "56353")
MYSQL_DB = os.environ.get("MYSQL_DB", "smart_edu_it")
REDIS_CLI = os.environ.get("REDIS_CLI", "F:/Redis-x64-5.0.14.1/redis-cli.exe")
REDIS_HOST = os.environ.get("REDIS_HOST", "127.0.0.1")
REDIS_PORT = os.environ.get("REDIS_PORT", "6380")
REDIS_DB = os.environ.get("REDIS_DB", "0")

BINDING_A = "910000000001"
BINDING_SUCCESS = "910000000003"
STUDENT_C = 930000000301
STUDENT_C2 = 930000000302

CHECKS = []
OBSERVATIONS = []
HTTP_LOG = []
captcha_checked = False


class RequestFailure(RuntimeError):
    pass


def http(method, path, token=None, payload=None, transport=None):
    entry = {"method": method, "path": path}
    HTTP_LOG.append(entry)
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    try:
        if transport is not None:
            return transport(method, BASE + path, data, headers)
        request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read().decode("utf-8", "replace")
                status = response.status
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", "replace")
            status = error.code
        try:
            body = json.loads(raw)
        except Exception:
            body = {"_raw": raw[:200]}
        entry["httpStatus"] = status
        entry["businessCode"] = body.get("code") if isinstance(body, dict) else None
        return {"status": status, "body": body,
                "code": body.get("code") if isinstance(body, dict) else None,
                "msg": body.get("msg") if isinstance(body, dict) else None}
    except RequestFailure:
        raise
    except Exception as error:
        entry["exception"] = type(error).__name__
        raise RequestFailure(f"{method} {path}: {error}") from error


def record_check(name, ok, http_status=None, business_code=None, detail=None):
    CHECKS.append({
        "name": name,
        "pass": bool(ok),
        "httpStatus": http_status,
        "businessCode": business_code,
        "detail": detail or {},
    })
    print(f"{'PASS' if ok else 'FAIL'} {name}")
    return bool(ok)


def check_response(name, response, expected_http=200, expected_code=200):
    return record_check(name,
                        response["status"] == expected_http
                        and response["code"] == expected_code,
                        response["status"], response["code"],
                        {"expectedHttp": expected_http,
                         "expectedBusinessCode": expected_code,
                         "message": response["msg"]})


def check_values(name, pairs, http_status=None, business_code=None):
    mismatches = []
    for label, actual, expected in pairs:
        if actual != expected:
            mismatches.append({"field": label, "actual": actual, "expected": expected})
    return record_check(name, not mismatches, http_status, business_code,
                        {"mismatches": mismatches})


def observe(name, detail):
    OBSERVATIONS.append({"name": name, "detail": detail})
    print(f"OBSERVE {name}")


def sql(query):
    output = subprocess.check_output([
        MYSQL, "--default-character-set=utf8mb4", "-h", MYSQL_HOST, "-P", MYSQL_PORT,
        "-uroot", "--batch", "--raw", "--skip-column-names", "-e", query, MYSQL_DB,
    ], text=True, encoding="utf-8")
    return [line.split("\t") for line in output.splitlines() if line != ""]


def redis_get(key):
    output = subprocess.check_output([
        REDIS_CLI, "-h", REDIS_HOST, "-p", REDIS_PORT, "-n", REDIS_DB, "get", key,
    ], text=True).strip()
    if output.startswith('"') and output.endswith('"'):
        output = output[1:-1]
    return output


def normalize_db_time(value):
    if value == "NULL":
        return None
    parsed = datetime.fromisoformat(value)
    return parsed.strftime("%Y-%m-%dT%H:%M:%S.") + f"{parsed.microsecond // 1000:03d}Z"


def normalize_json_time(value):
    if value is None:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.") + \
        f"{parsed.microsecond // 1000:03d}Z"


def login(username):
    global captcha_checked
    captcha = http("GET", "/captchaImage")
    check_response(f"验证码接口 {username}", captcha)
    enabled = captcha["body"].get("captchaEnabled") if isinstance(captcha["body"], dict) else None
    if not captcha_checked:
        check_values("验证码确实启用", [("captchaEnabled", enabled, True)],
                     captcha["status"], captcha["code"])
        captcha_checked = True
    uuid = captcha["body"].get("uuid")
    code = ""
    if enabled:
        key = "captcha_codes:" + str(uuid)
        for _ in range(20):
            code = redis_get(key)
            if code:
                break
            time.sleep(0.1)
        if not code:
            raise RuntimeError(f"captcha answer missing for {username}")
    response = http("POST", "/login", payload={
        "username": username, "password": PASSWORD, "code": code, "uuid": uuid,
    })
    check_response(f"登录 {username}", response)
    token = response["body"].get("token") if isinstance(response["body"], dict) else None
    if response["code"] != 200 or not token:
        raise RuntimeError(f"login failed for {username}")
    return token


def notice_id(recipient, source_id):
    rows = sql("SELECT id FROM edu_user_notice WHERE recipient_user_id = "
               f"{int(recipient)} AND notice_kind = 'ANNOUNCEMENT' AND source_id = {int(source_id)}")
    return rows[0][0] if rows else None


def notice_count(source_id):
    return int(sql("SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'ANNOUNCEMENT' "
                   f"AND source_id = {int(source_id)}")[0][0])


def match_audit(expected, rows, baseline):
    """按 (oper_name, request_method, oper_url, title, business_type, status) 做多重集匹配。"""
    new_rows = [row for row in rows if int(row[0]) > baseline]
    remaining = [tuple(row[1:]) for row in new_rows]
    missing = []
    for wanted in expected:
        found = False
        for index, row in enumerate(remaining):
            if row == wanted:
                remaining.pop(index)
                found = True
                break
        if not found:
            missing.append(wanted)
    return missing, remaining, new_rows


def run_real():
    # 未登录访问
    check_response("未登录访问教师公告",
                   http("GET", f"/education/teacher/announcements?bindingId={BINDING_SUCCESS}"),
                   200, 401)
    check_response("未登录访问学生通知",
                   http("GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}"),
                   200, 401)

    tokens = {}
    for user in ["teacher", "student", "student2", "student_c", "student_c2", "student4",
                 "teacher_limited", "teacher_other", "teacher_team", "teacher_nonclass",
                 "assistant", "admin"]:
        tokens[user] = login(user)

    info = http("GET", "/getInfo", tokens["teacher"])
    check_response("教师 getInfo", info)
    body = info["body"] if isinstance(info["body"], dict) else {}
    check_values("教师角色存在", [("roles contains teacher", "teacher" in body.get("roles", []), True)])
    check_values("教师发布权限存在",
                 [("permissions contains publish",
                   "education:announcement:publish" in body.get("permissions", []), True)])

    baseline_announcements = int(sql("SELECT COUNT(*) FROM edu_course_announcement")[0][0])
    baseline_notices = int(sql("SELECT COUNT(*) FROM edu_user_notice")[0][0])
    baseline_audit = int(sql("SELECT COALESCE(MAX(oper_id), 0) FROM sys_oper_log")[0][0])
    observe("数据库基线", {"announcements": baseline_announcements,
                           "notices": baseline_notices,
                           "auditMaxId": baseline_audit})

    create = http("POST", "/education/teacher/announcements", tokens["teacher"], {
        "bindingId": BINDING_SUCCESS, "title": "全链路联调公告一",
        "body": "真实 HTTP 公告正文", "opensAt": None, "closesAt": None,
    })
    check_response("教师创建公告草稿", create)
    ann1 = int(create["body"]["data"]["id"])
    check_values("创建响应状态", [("status", create["body"]["data"].get("status"), "DRAFT")])
    check_values("草稿数据库状态",
                 [("status", sql(f"SELECT status FROM edu_course_announcement WHERE id={ann1}")[0][0],
                   "DRAFT")])

    edit = http("PUT", f"/education/teacher/announcements/{ann1}", tokens["teacher"], {
        "title": "全链路联调公告一（已编辑）", "body": "真实 HTTP 公告正文已编辑",
        "opensAt": None, "closesAt": None,
    })
    check_response("教师编辑公告草稿", edit)
    check_values("编辑后标题",
                 [("title", sql(f"SELECT title FROM edu_course_announcement WHERE id={ann1}")[0][0],
                   "全链路联调公告一（已编辑）")])

    publish = http("POST", f"/education/teacher/announcements/{ann1}/publish", tokens["teacher"])
    check_response("教师发布公告", publish)
    check_values("发布响应状态", [("status", publish["body"]["data"].get("status"), "PUBLISHED")])
    check_values("发布后数据库状态",
                 [("status", sql(f"SELECT status FROM edu_course_announcement WHERE id={ann1}")[0][0],
                   "PUBLISHED")])
    check_values("联调教学班 C 通知人数", [("count", notice_count(ann1), 2)])
    recipients = ",".join(row[0] for row in sql(
        f"SELECT recipient_user_id FROM edu_user_notice WHERE source_id={ann1} "
        "ORDER BY recipient_user_id"))
    check_values("教学班 C 接收人", [("recipients", recipients, "930000000301,930000000302")])

    repeat_publish = http("POST", f"/education/teacher/announcements/{ann1}/publish",
                          tokens["teacher"])
    check_response("重复发布幂等", repeat_publish)
    check_values("重复发布后通知数不增", [("count", notice_count(ann1), 2)])

    create2 = http("POST", "/education/teacher/announcements", tokens["teacher"], {
        "bindingId": BINDING_SUCCESS, "title": "全链路联调公告二", "body": "第二篇公告",
        "opensAt": None, "closesAt": None,
    })
    check_response("教师创建第二篇公告", create2)
    ann2 = int(create2["body"]["data"]["id"])
    publish2 = http("POST", f"/education/teacher/announcements/{ann2}/publish", tokens["teacher"])
    check_response("教师发布第二篇公告", publish2)

    notices = http("GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}",
                   tokens["student_c"])
    check_response("学生通知列表", notices)
    check_values("学生通知列表=2", [("total", notices["body"].get("total"), 2)])
    rows = notices["body"].get("rows", [])
    check_values("学生通知标题集合",
                 [("titles", sorted(row.get("title") for row in rows),
                   ["全链路联调公告一（已编辑）", "全链路联调公告二"])])
    unread = http("GET", f"/education/student/notices/unread-count?bindingId={BINDING_SUCCESS}",
                  tokens["student_c"])
    check_response("学生未读数接口", unread)
    check_values("学生未读数=2",
                 [("unreadCount", unread["body"].get("data", {}).get("unreadCount"), 2)])

    n1 = notice_id(STUDENT_C, ann1)
    n2 = notice_id(STUDENT_C, ann2)
    detail = http("GET", f"/education/student/notices/{n1}", tokens["student_c"])
    check_response("学生查看通知详情", detail)
    check_values("通知详情标题",
                 [("title", detail["body"].get("data", {}).get("title"),
                   "全链路联调公告一（已编辑）")])

    read1 = http("POST", f"/education/student/notices/{n1}/read", tokens["student_c"])
    check_response("学生单条已读", read1)
    read1_data = read1["body"].get("data", {})
    check_values("首次已读 alreadyRead=false", [("alreadyRead", read1_data.get("alreadyRead"), False)])
    read_at = sql(f"SELECT read_at FROM edu_user_notice WHERE id={int(n1)}")[0][0]
    check_values("数据库 read_at 已写入", [("readAt", read_at != "NULL", True)])
    check_values("首次已读 readAt 与数据库一致",
                 [("readAt", normalize_json_time(read1_data.get("readAt")),
                   normalize_db_time(read_at))])

    read1_again = http("POST", f"/education/student/notices/{n1}/read", tokens["student_c"])
    check_response("重复已读幂等", read1_again)
    read1_again_data = read1_again["body"].get("data", {})
    check_values("重复已读 alreadyRead=true",
                 [("alreadyRead", read1_again_data.get("alreadyRead"), True)])
    check_values("重复已读 readAt 与数据库一致",
                 [("readAt", normalize_json_time(read1_again_data.get("readAt")),
                   normalize_db_time(read_at))])

    unread_before_read_all = int(sql(
        f"SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id={STUDENT_C} "
        "AND read_at IS NULL")[0][0])
    read_all = http("PUT", "/education/student/notices/read-all", tokens["student_c"])
    check_response("学生全部已读", read_all)
    read_all_data = read_all["body"].get("data", {})
    check_values("全部已读 markedCount", [("markedCount", read_all_data.get("markedCount"),
                                            unread_before_read_all)])
    check_values("全部已读后未读数",
                 [("unreadCount", read_all_data.get("unreadCount"), 0)])
    check_values("全部已读后数据库未读数", [("count", int(sql(
        f"SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id={STUDENT_C} "
        "AND read_at IS NULL")[0][0]), 0)])

    archive = http("POST", f"/education/teacher/announcements/{ann1}/archive", tokens["teacher"])
    check_response("教师归档公告", archive)
    check_values("归档响应状态", [("status", archive["body"]["data"].get("status"), "ARCHIVED")])
    check_values("归档后数据库状态",
                 [("status", sql(f"SELECT status FROM edu_course_announcement WHERE id={ann1}")[0][0],
                   "ARCHIVED")])
    student_announcements = http(
        "GET", f"/education/student/announcements?bindingId={BINDING_SUCCESS}", tokens["student_c"])
    check_response("归档后学生公告列表", student_announcements)
    check_values("归档后学生公告列表=1", [("total", student_announcements["body"].get("total"), 1)])
    student_notices_after = http(
        "GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}", tokens["student_c"])
    check_response("归档后学生通知列表", student_notices_after)
    check_values("归档后学生通知列表=1", [("total", student_notices_after["body"].get("total"), 1)])
    check_response("归档公告学生详情=404",
                   http("GET", f"/education/student/announcements/{ann1}", tokens["student_c"]),
                   200, 404)
    check_response("归档后通知详情=404",
                   http("GET", f"/education/student/notices/{n1}", tokens["student_c"]), 200, 404)
    check_response("归档后重复发布=409",
                   http("POST", f"/education/teacher/announcements/{ann1}/publish",
                        tokens["teacher"]), 200, 409)
    teacher_get_archived = http("GET", f"/education/teacher/announcements/{ann1}",
                                tokens["teacher"])
    check_response("教师仍可查看归档公告", teacher_get_archived)
    check_values("教师查看归档状态",
                 [("status", teacher_get_archived["body"].get("data", {}).get("status"),
                   "ARCHIVED")])

    # 拒绝场景
    check_response("无公告按钮权限助理创建=403",
                   http("POST", "/education/teacher/announcements", tokens["assistant"], {
                       "bindingId": BINDING_SUCCESS, "title": "助理越权", "body": "x"}),
                   200, 403)
    check_response("学生调用教师接口=403",
                   http("POST", "/education/teacher/announcements", tokens["student"], {
                       "bindingId": BINDING_SUCCESS, "title": "学生越权", "body": "x"}),
                   200, 403)
    check_response("非任课教师创建=403",
                   http("POST", "/education/teacher/announcements", tokens["teacher_nonclass"], {
                       "bindingId": BINDING_A, "title": "非任课教师", "body": "x"}),
                   200, 403)
    check_response("受限教师创建=403",
                   http("POST", "/education/teacher/announcements", tokens["teacher_limited"], {
                       "bindingId": BINDING_A, "title": "受限教师", "body": "x"}),
                   200, 403)
    check_response("团队 fallback 创建=403",
                   http("POST", "/education/teacher/announcements", tokens["teacher_team"], {
                       "bindingId": BINDING_A, "title": "团队教师", "body": "x"}),
                   200, 403)

    draft = http("POST", "/education/teacher/announcements", tokens["teacher"], {
        "bindingId": BINDING_A, "title": "拒绝场景草稿", "body": "draft"})
    check_response("教师创建拒绝场景草稿", draft)
    draft_id = int(draft["body"]["data"]["id"])
    for user in ["teacher_limited", "teacher_team", "teacher_nonclass"]:
        check_response(f"{user} 发布他人草稿=403",
                       http("POST", f"/education/teacher/announcements/{draft_id}/publish",
                            tokens[user]), 200, 403)
        check_values(f"{user} 发布失败后状态仍 DRAFT",
                     [("status", sql(
                         f"SELECT status FROM edu_course_announcement WHERE id={draft_id}")[0][0],
                       "DRAFT")])
        check_values(f"{user} 发布失败后无通知", [("count", notice_count(draft_id), 0)])

    check_response("跨班教师创建=403",
                   http("POST", "/education/teacher/announcements", tokens["teacher_other"], {
                       "bindingId": BINDING_SUCCESS, "title": "跨班创建", "body": "x"}),
                   200, 403)
    check_response("跨班教师列表=403",
                   http("GET", f"/education/teacher/announcements?bindingId={BINDING_SUCCESS}",
                        tokens["teacher_other"]), 200, 403)
    check_response("跨班教师查看公告=404",
                   http("GET", f"/education/teacher/announcements/{ann2}",
                        tokens["teacher_other"]), 200, 404)
    check_response("跨班教师发布=403",
                   http("POST", f"/education/teacher/announcements/{ann2}/publish",
                        tokens["teacher_other"]), 200, 403)
    check_response("跨班学生访问通知=403",
                   http("GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}",
                        tokens["student4"]), 200, 403)

    other_notice = notice_id(STUDENT_C2, ann2)
    other_before = sql(f"SELECT read_at FROM edu_user_notice WHERE id={int(other_notice)}")[0][0]
    check_response("学生甲标记学生乙通知=404",
                   http("POST", f"/education/student/notices/{other_notice}/read",
                        tokens["student_c"]), 200, 404)
    check_values("跨用户已读未写入", [("readAt",
                                       sql(f"SELECT read_at FROM edu_user_notice "
                                           f"WHERE id={int(other_notice)}")[0][0],
                                       other_before)])

    check_response("教师访问学生通知=403",
                   http("GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}",
                        tokens["teacher"]), 200, 403)
    check_response("管理员访问学生通知=403",
                   http("GET", f"/education/student/notices?bindingId={BINDING_SUCCESS}",
                        tokens["admin"]), 200, 403)

    # 最终数据库数量：本轮新增 3 条公告、4 条通知；拒绝请求不得新增写入。
    final_announcements = int(sql("SELECT COUNT(*) FROM edu_course_announcement")[0][0])
    final_notices = int(sql("SELECT COUNT(*) FROM edu_user_notice")[0][0])
    check_values("最终公告数=基线+3", [("count", final_announcements,
                                        baseline_announcements + 3)])
    check_values("最终通知数=基线+4", [("count", final_notices, baseline_notices + 4)])
    check_values("拒绝场景草稿无通知", [("count", notice_count(draft_id), 0)])

    # 审计基线匹配（异步落库，最多等待 20 秒；一条日志只能匹配一个预期）
    expected_audit = [
        ("teacher", "POST", "/education/teacher/announcements", "教师公告", "1", "0"),
        ("teacher", "PUT", f"/education/teacher/announcements/{ann1}", "教师公告", "2", "0"),
        ("teacher", "POST", f"/education/teacher/announcements/{ann1}/publish", "教师公告", "2", "0"),
        ("teacher", "POST", f"/education/teacher/announcements/{ann1}/publish", "教师公告", "2", "0"),
        ("teacher", "POST", "/education/teacher/announcements", "教师公告", "1", "0"),
        ("teacher", "POST", f"/education/teacher/announcements/{ann2}/publish", "教师公告", "2", "0"),
        ("student_c", "POST", f"/education/student/notices/{n1}/read", "学生通知", "2", "0"),
        ("student_c", "POST", f"/education/student/notices/{n1}/read", "学生通知", "2", "0"),
        ("student_c", "PUT", "/education/student/notices/read-all", "学生通知", "2", "0"),
        ("teacher", "POST", f"/education/teacher/announcements/{ann1}/archive", "教师公告", "2", "0"),
        ("teacher", "POST", "/education/teacher/announcements", "教师公告", "1", "0"),
        ("teacher", "POST", f"/education/teacher/announcements/{ann1}/publish", "教师公告", "2", "1"),
        ("teacher_nonclass", "POST", "/education/teacher/announcements", "教师公告", "1", "1"),
        ("teacher_limited", "POST", "/education/teacher/announcements", "教师公告", "1", "1"),
        ("teacher_team", "POST", "/education/teacher/announcements", "教师公告", "1", "1"),
        ("teacher_limited", "POST", f"/education/teacher/announcements/{draft_id}/publish",
         "教师公告", "2", "1"),
        ("teacher_team", "POST", f"/education/teacher/announcements/{draft_id}/publish",
         "教师公告", "2", "1"),
        ("teacher_nonclass", "POST", f"/education/teacher/announcements/{draft_id}/publish",
         "教师公告", "2", "1"),
        ("teacher_other", "POST", "/education/teacher/announcements", "教师公告", "1", "1"),
        ("teacher_other", "POST", f"/education/teacher/announcements/{ann2}/publish",
         "教师公告", "2", "1"),
        ("student_c", "POST", f"/education/student/notices/{other_notice}/read",
         "学生通知", "2", "1"),
    ]
    deadline = time.time() + 20
    rows = []
    while time.time() < deadline:
        rows = sql("SELECT oper_id,oper_name,request_method,oper_url,title,business_type,status "
                   "FROM sys_oper_log WHERE oper_id > " + str(baseline_audit) +
                   " AND title IN ('教师公告','学生通知') ORDER BY oper_id")
        if len(rows) >= len(expected_audit):
            time.sleep(2)
            rows = sql("SELECT oper_id,oper_name,request_method,oper_url,title,business_type,status "
                       "FROM sys_oper_log WHERE oper_id > " + str(baseline_audit) +
                       " AND title IN ('教师公告','学生通知') ORDER BY oper_id")
            break
        time.sleep(1)
    missing, unexpected, new_rows = match_audit(expected_audit, rows, baseline_audit)
    check_values("审计记录数量与本轮操作一致", [("count", len(new_rows), len(expected_audit))])
    check_values("审计记录无缺失", [("missing", len(missing), 0)])
    check_values("审计记录无多余", [("unexpected", len(unexpected), 0)])
    observe("审计记录", [{"operId": row[0], "operName": row[1], "method": row[2],
                          "url": row[3], "title": row[4], "businessType": row[5],
                          "status": row[6]} for row in new_rows])
    observe("审计缺失预期", missing)


def run_self_test(case):
    if case == "missing-publish":
        check_values("自检：缺少发布权限", [("hasPublish", False, True)])
    elif case == "already-read":
        check_values("自检：alreadyRead 错误", [("alreadyRead", True, False)])
    elif case == "db-count":
        check_values("自检：数据库数量错误", [("notices", 99, 2)])
    elif case == "audit-history":
        expected = [("teacher", "POST", "/education/teacher/announcements", "教师公告", "1", "0")]
        rows = [["1", "teacher", "POST", "/education/teacher/announcements",
                 "教师公告", "1", "0"]]
        missing, unexpected, new_rows = match_audit(expected, rows, 5)
        check_values("自检：只有历史审计应失败",
                     [("newRows", len(new_rows), 0), ("missing", len(missing), 0)])
    elif case == "request-exception":
        def failing_transport(method, url, data, headers):
            raise RuntimeError("controlled request failure")
        try:
            http("GET", "/self-test", transport=failing_transport)
            record_check("自检：请求异常未被捕获", False)
        except RequestFailure:
            record_check("自检：请求异常已捕获（预期失败退出）", False)
    else:
        record_check("自检：未知用例", False)


def finish():
    passed = sum(1 for item in CHECKS if item["pass"])
    failed = len(CHECKS) - passed
    report = {
        "base": BASE,
        "checkCount": len(CHECKS),
        "passed": passed,
        "failed": failed,
        "checks": CHECKS,
        "observations": OBSERVATIONS,
        "httpRequestCount": len(HTTP_LOG),
        "httpRequests": HTTP_LOG,
    }
    report_path = os.environ.get("REPORT_PATH", "full-auth-report.json")
    with open(report_path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(f"REPORT={report_path} checks={len(CHECKS)} passed={passed} failed={failed} "
          f"httpRequests={len(HTTP_LOG)}")
    return 0 if failed == 0 else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", choices=[
        "missing-publish", "already-read", "db-count", "audit-history", "request-exception",
    ])
    args = parser.parse_args()
    if args.self_test:
        try:
            run_self_test(args.self_test)
        except Exception as error:
            record_check("自检异常", False, detail={"error": str(error)})
        return finish()
    try:
        if not PASSWORD:
            raise RuntimeError("TEST_PASSWORD is required")
        run_real()
    except Exception as error:
        record_check("真实链路脚本异常", False, detail={"error": str(error)})
    return finish()


if __name__ == "__main__":
    sys.exit(main())
