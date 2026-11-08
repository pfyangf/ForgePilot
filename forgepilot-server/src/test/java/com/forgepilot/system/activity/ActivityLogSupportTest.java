package com.forgepilot.system.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.system.activity.DailyReportBuilder.ActivityEntry;
import com.forgepilot.system.activity.DailyReportBuilder.WaitingItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityLogSupportTest {

    @Test
    void masksSecretFieldsAndKeepsOtherValues() {
        Map<String, Object> masked = ActivityPayloadMasker.mask(Map.of(
                "branch", "fp/task-1",
                "apiKey", "sk-live",
                "nested", Map.of("password", "secret")));
        assertThat(masked.get("branch")).isEqualTo("fp/task-1");
        assertThat(masked.get("apiKey")).isEqualTo("***");
        assertThat(masked.get("nested")).isEqualTo(Map.of("password", "***"));
    }

    @Test
    void dailyReportGroupsTheDayAndKeepsWaitingItems() {
        Instant noon = LocalDate.of(2026, 11, 8).atTime(12, 0).toInstant(ZoneOffset.UTC);
        var report = DailyReportBuilder.build(
                List.of(
                        new ActivityEntry("me", "pay", noon, "REQUIREMENT_CREATED", "SUCCESS"),
                        new ActivityEntry("me", "pay", noon, "TASK_BLOCKED", "SUCCESS"),
                        new ActivityEntry("me", "pay", noon.plusSeconds(86_400), "TASK_COMPLETED", "SUCCESS"),
                        new ActivityEntry("me", "pay", noon, "TASK_APPROVED", "FAILURE")),
                LocalDate.of(2026, 11, 8),
                ZoneOffset.UTC,
                List.of(new WaitingItem("me", "pay", "task-wait", "WAITING_USER")));

        assertThat(report.byUser().get("me").createdRequirements()).isEqualTo(1);
        assertThat(report.byUser().get("me").blocked()).isEqualTo(1);
        assertThat(report.byUser().get("me").completed()).isZero();
        assertThat(report.byUser().get("me").approved()).isZero();
        assertThat(report.byProject().get("pay").createdRequirements()).isEqualTo(1);
        assertThat(report.waitingOnUsers()).hasSize(1);
    }
}
