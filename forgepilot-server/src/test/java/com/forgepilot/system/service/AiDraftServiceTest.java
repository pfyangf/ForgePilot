package com.forgepilot.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forgepilot.execution.persistence.CodingTaskMapper;
import com.forgepilot.project.domain.Project;
import com.forgepilot.project.domain.Requirement;
import com.forgepilot.project.persistence.AnalysisDraftMapper;
import com.forgepilot.project.persistence.DefectMapper;
import com.forgepilot.project.persistence.ProjectMapper;
import com.forgepilot.project.persistence.RequirementDocumentMapper;
import com.forgepilot.project.persistence.RequirementMapper;
import com.forgepilot.project.persistence.ReviewMapper;
import com.forgepilot.project.service.ProjectAccess;
import com.forgepilot.system.auth.AuthUser;
import com.forgepilot.system.port.ModelProviderPort;
import com.forgepilot.system.port.ModelProviderPort.PlatformModelRequest;
import com.forgepilot.system.web.GenerateDraftRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class AiDraftServiceTest {

    private final ProjectAccess projectAccess = mock(ProjectAccess.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final AnalysisDraftMapper analysisDraftMapper = mock(AnalysisDraftMapper.class);
    private final RequirementDocumentMapper documentMapper = mock(RequirementDocumentMapper.class);
    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final DefectMapper defectMapper = mock(DefectMapper.class);
    private final CodingTaskMapper codingTaskMapper = mock(CodingTaskMapper.class);
    private final ModelProviderPort modelProviderPort = mock(ModelProviderPort.class);
    private AiDraftService service;

    @BeforeEach
    void setUp() {
        service = new AiDraftService(
                projectAccess,
                projectMapper,
                requirementMapper,
                analysisDraftMapper,
                documentMapper,
                reviewMapper,
                defectMapper,
                codingTaskMapper,
                modelProviderPort);
        Project project = new Project();
        project.setId("p1");
        project.setName("示例项目");
        project.setDefaultModelProviderId("prov");
        project.setDefaultModelId("gpt-5");
        when(projectMapper.selectById("p1")).thenReturn(project);
        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setProjectId("p1");
        requirement.setCode("REQ-PAY-1");
        requirement.setTitle("支付");
        requirement.setDescription("支持支付");
        when(requirementMapper.selectById("r1")).thenReturn(requirement);
        when(analysisDraftMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void generateUsesProviderAndReturnsEditableText() {
        when(modelProviderPort.complete(any())).thenReturn("验收清单草稿");
        var draft = service.generate(
                user(),
                new GenerateDraftRequest("ACCEPTANCE", "p1", "r1", null, null, null, null, null));
        assertThat(draft.text()).isEqualTo("验收清单草稿");
        assertThat(draft.scene()).isEqualTo("ACCEPTANCE");
        ArgumentCaptor<PlatformModelRequest> captor = ArgumentCaptor.forClass(PlatformModelRequest.class);
        verify(modelProviderPort).complete(captor.capture());
        assertThat(captor.getValue().providerId()).isEqualTo("prov");
        assertThat(captor.getValue().modelId()).isEqualTo("gpt-5");
        assertThat(captor.getValue().prompt()).contains("REQ-PAY-1").contains("写出验收清单");
    }

    @Test
    void generateFailsWhenModelIsNotConfigured() {
        Project project = new Project();
        project.setId("p1");
        project.setName("示例项目");
        when(projectMapper.selectById("p1")).thenReturn(project);
        assertThatThrownBy(() -> service.generate(
                        user(),
                        new GenerateDraftRequest("TASK_BRIEF", "p1", "r1", null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("未配置模型");
    }

    private static AuthUser user() {
        return new AuthUser("u1", "demo", "演示", "tenant", List.of("MEMBER"), List.of("projects"));
    }
}
