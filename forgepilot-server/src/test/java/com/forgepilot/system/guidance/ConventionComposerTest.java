package com.forgepilot.system.guidance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgepilot.system.guidance.ConventionComposer.ConventionItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConventionComposerTest {

    @Test
    void moreSpecificTitleWinsAndUnmarkedDocumentsStayOut() {
        List<ConventionItem> composed = ConventionComposer.forAgent(List.of(
                new ConventionItem("PLATFORM", "提交说明", "平台要求写明动机", true, true),
                new ConventionItem("TENANT", "提交说明", "公司要求带工单号", true, true),
                new ConventionItem("PROJECT", "模块边界", "web 不直接访问支付库", true, true),
                new ConventionItem("PROJECT", "架构笔记", "给人看的长文", true, false),
                new ConventionItem("TENANT", "停用规则", "不要注入", false, true)));

        assertThat(composed).extracting(ConventionItem::title).containsExactly("提交说明", "模块边界");
        assertThat(composed.get(0).body()).isEqualTo("公司要求带工单号");
    }
}
