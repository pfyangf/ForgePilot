package com.forgepilot.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CoreSchemaMigrationTest {

    @Test
    void schemaMatchesV12Baseline() throws IOException {
        String sql = new String(new ClassPathResource("db/migration/V1__core_schema.sql").getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(sql).contains("create table project_repository");
        assertThat(sql).contains("unique (project_id, local_directory_name)");
        assertThat(sql).contains("create table workspace_checkout");
        assertThat(sql).contains("create table repository_mirror");
        assertThat(sql).contains("uk_merge_request_open_task_repo");
        assertThat(sql).contains("on merge_request (task_id, project_repository_id)");
        assertThat(sql).contains("upstream_wire_api");
        assertThat(sql).contains("translation_gateway");
        assertThat(sql).contains("agent_home_path");
        assertThat(sql).contains("uk_agent_session_one_active");
        assertThat(sql).contains("create table tenant");
        assertThat(sql).contains("create table guidance_item");
        assertThat(sql).contains("create table task_repository");
        assertThat(sql).contains("create table activity_log");
        assertThat(sql).contains("unique (task_id, project_repository_id)");
        assertThat(sql).doesNotContain("create table audit_event");
        assertThat(sql).doesNotContain("api_key");
        assertThat(sql).doesNotContain("gitlab_project");
        assertThat(sql).doesNotContain("create table defect");
        assertThat(sql).doesNotContain("primary_repository");

        String project = table(sql, "project", "project_member");
        assertThat(project).contains("default_model_provider_id");
        assertThat(project).doesNotContain("repository_id");

        String task = table(sql, "task", "task_state_history");
        assertThat(task).contains("model_provider_id");
        assertThat(task).contains("branch_name");
        assertThat(task).doesNotContain("repository_id");
    }

    @Test
    void lifecycleMigrationAddsReviewDocumentDefectAndRelease() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/V10__requirement_lifecycle.sql").getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(sql).contains("create table review");
        assertThat(sql).contains("create table requirement_document");
        assertThat(sql).contains("create table defect");
        assertThat(sql).contains("create table requirement_release");
        assertThat(sql).contains("'待需求评审'");
        assertThat(sql).contains("'待设计评审'");
        assertThat(sql).contains("'已发布'");
        assertThat(sql).contains("alter table task add column defect_id");
    }

    private static String table(String sql, String name, String next) {
        String start = "create table " + name + " (";
        int from = sql.indexOf(start);
        int to = sql.indexOf("create table " + next);
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return sql.substring(from, to);
    }
}
