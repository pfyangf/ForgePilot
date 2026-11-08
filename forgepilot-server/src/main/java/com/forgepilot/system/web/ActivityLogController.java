package com.forgepilot.system.web;

import com.forgepilot.system.activity.DailyReportBuilder;
import com.forgepilot.system.security.AuthContext;
import com.forgepilot.system.service.ActivityLogQueryService;
import com.forgepilot.system.service.ActivityLogQueryService.ActivityView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ActivityLogController {

    private final ActivityLogQueryService queryService;

    public ActivityLogController(ActivityLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/activity-logs")
    public List<ActivityView> list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return queryService.query(AuthContext.require(), tenantId, projectId, actorUserId, action, from, to);
    }

    @GetMapping("/activity-logs/export")
    public ResponseEntity<?> export(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var user = AuthContext.require();
        if ("csv".equalsIgnoreCase(format)) {
            String csv = queryService.exportCsv(user, tenantId, projectId, actorUserId, action, from, to);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-logs.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv);
        }
        List<ActivityView> rows = queryService.query(user, tenantId, projectId, actorUserId, action, from, to);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/reports/daily")
    public DailyReportBuilder.DailyReport daily(
            @RequestParam(required = false) String day, @RequestParam(required = false) String tenantId) {
        LocalDate date = day == null || day.isBlank() ? LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")) : LocalDate.parse(day);
        return queryService.dailyReport(AuthContext.require(), date, tenantId);
    }

    @GetMapping(value = "/reports/daily.md", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<String> dailyMarkdown(
            @RequestParam(required = false) String day, @RequestParam(required = false) String tenantId) {
        LocalDate date = day == null || day.isBlank() ? LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")) : LocalDate.parse(day);
        String md = queryService.dailyMarkdown(AuthContext.require(), date, tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"daily-report.md\"")
                .body(md);
    }

    /** 操作记录只追加，禁止改删。 */
    @RequestMapping(
            value = {"/activity-logs", "/activity-logs/**"},
            method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.POST})
    public Map<String, String> immutable() {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "操作记录只追加，禁止修改或删除");
    }
}
