package com.example.soar;

import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.worker.LHTaskWorker;
import io.littlehorse.sdk.wfsdk.Workflow;

import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        LHConfig config = new LHConfig();

        System.out.println("[workflows] Connecting to LittleHorse server...");

        // Give LH server a moment to be fully ready on first boot
        Thread.sleep(2000);

        TaskWorkers workers = new TaskWorkers();

        // -------------------------------------------------
        // Register all WfSpecs
        // -------------------------------------------------
        List<Workflow> workflowDefs = WorkflowDefinitions.all();
        for (Workflow wf : workflowDefs) {
            try {
                wf.registerWfSpec(config.getBlockingStub());
                System.out.println("[workflows] Registered WfSpec: " + wf.getName());
            } catch (Exception e) {
                System.err.println("[workflows] Failed to register WfSpec " + wf.getName() + ": " + e.getMessage());
            }
        }

        // -------------------------------------------------
        // Start all LHTaskWorkers (one per TaskDef)
        // -------------------------------------------------
        List<String> taskDefs = List.of(
            "get-account-info",
            "get-fraud-score",
            "freeze-account",
            "unfreeze-account",
            "post-alert",
            "post-log-entry",
            "verify-card",
            "verify-check",
            "send-notification",
            "create-case",
            "close-case"
        );

        for (String taskDef : taskDefs) {
            LHTaskWorker worker = new LHTaskWorker(workers, taskDef, config);
            try {
                worker.registerTaskDef();
                System.out.println("[workflows] Registered TaskDef: " + taskDef);
            } catch (Exception e) {
                System.err.println("[workflows] Failed to register TaskDef " + taskDef + ": " + e.getMessage());
            }
            worker.start();
            System.out.println("[workflows] Started worker for: " + taskDef);
        }

        System.out.println("[workflows] All workers running. Waiting for tasks...");

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            System.out.println("[workflows] Shutting down...")));

        Thread.currentThread().join();
    }
}
