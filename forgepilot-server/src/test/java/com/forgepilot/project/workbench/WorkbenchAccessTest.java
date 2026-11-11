package com.forgepilot.project.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkbenchAccessTest {

    @Test
    void tenantIsolationBlocksOtherCompany() {
        assertThat(WorkbenchAccess.sameTenant("t1", "t2")).isFalse();
        assertThat(WorkbenchAccess.canSeeProject("t1", "t2", Set.of("p1"), "p1")).isFalse();
        assertThat(WorkbenchAccess.canSeeProject("t1", "t1", Set.of("p1"), "p1")).isTrue();
        assertThat(WorkbenchAccess.canSeeProject("t1", "t1", Set.of("p2"), "p1")).isFalse();
    }

    @Test
    void mineAndWaitingFilters() {
        assertThat(WorkbenchAccess.isMyRequirement("me", "me", "other")).isTrue();
        assertThat(WorkbenchAccess.isMyRequirement("me", "other", "other")).isFalse();
        assertThat(WorkbenchAccess.isMyTask("me", "other", "other", "me", "x")).isTrue();
        assertThat(WorkbenchAccess.isWaitingOnMe("WAITING_APPROVAL", "SUCCEEDED")).isTrue();
        assertThat(WorkbenchAccess.isWaitingOnMe("RUNNING", "WAITING_INPUT")).isTrue();
        assertThat(WorkbenchAccess.isWaitingOnMe("RUNNING", "RUNNING")).isFalse();
        assertThat(WorkbenchAccess.isRequirementWaitingOnMe("待需求评审")).isTrue();
        assertThat(WorkbenchAccess.isRequirementWaitingOnMe("设计中")).isTrue();
        assertThat(WorkbenchAccess.isRequirementWaitingOnMe("待验收")).isTrue();
        assertThat(WorkbenchAccess.isRequirementWaitingOnMe("开发中")).isFalse();
        assertThat(WorkbenchAccess.isRequirementWaitingOnMe("已发布")).isFalse();
    }
}
