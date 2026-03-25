package com.example.soar;

import io.littlehorse.sdk.wfsdk.Workflow;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowVerificationTest {

    @Test
    public void testWorkflowDefinitions() {
        // Smoke test: Ensure all workflows can be constructed without error
        List<Workflow> workflows = WorkflowDefinitions.all();
        assertThat(workflows).hasSize(4);
    }
}
