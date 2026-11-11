package com.forgepilot.execution.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkingBranchNamesTest {

    @Test
    void defaultsToRequirementCode() {
        assertThat(WorkingBranchNames.fromRequirementCode("REQ-DEMO99-3")).isEqualTo("fp/REQ-DEMO99-3");
        assertThat(WorkingBranchNames.defaultName("REQ-DEMO99-3", "TASK-DEMO99-7", Set.of()))
                .isEqualTo("fp/REQ-DEMO99-3");
    }

    @Test
    void suffixesTaskCodeOnCollision() {
        assertThat(WorkingBranchNames.defaultName(
                        "REQ-DEMO99-3", "TASK-DEMO99-7", Set.of("fp/REQ-DEMO99-3")))
                .isEqualTo("fp/REQ-DEMO99-3-TASK-DEMO99-7");
    }

    @Test
    void incrementsWhenSuffixAlsoTaken() {
        assertThat(WorkingBranchNames.defaultName(
                        "REQ-DEMO99-3",
                        "TASK-DEMO99-7",
                        Set.of("fp/REQ-DEMO99-3", "fp/REQ-DEMO99-3-TASK-DEMO99-7")))
                .isEqualTo("fp/REQ-DEMO99-3-TASK-DEMO99-7-2");
    }

    @Test
    void sanitizesUnsafeCharacters() {
        assertThat(WorkingBranchNames.fromRequirementCode("REQ 99/foo")).isEqualTo("fp/REQ-99-foo");
        assertThat(WorkingBranchNames.sanitizeFull(" fp/REQ-DEMO99-3 ")).isEqualTo("fp/REQ-DEMO99-3");
        assertThat(WorkingBranchNames.sanitizeFull("feature/ok_name")).isEqualTo("feature/ok_name");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> WorkingBranchNames.sanitizeFull("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }
}
