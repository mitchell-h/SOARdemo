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
        // Give LH server plenty of time to be fully ready (Kafka rebalance)
        Thread.sleep(15000);

        TaskWorkers workers = new TaskWorkers();
        var stub = config.getBlockingStub();

        // -------------------------------------------------
        // 1. Start all LHTaskWorkers (one per TaskDef)
        // -------------------------------------------------
        List<String> taskDefs = List.of(
            "get-account-info", "get-fraud-score", "freeze-account", "unfreeze-account",
            "post-alert", "post-log-entry", "verify-card", "verify-check",
            "send-notification", "create-case", "close-case",
            "get-alert-details", "get-user-logs"
        );

        for (String taskDef : taskDefs) {
            LHTaskWorker worker = new LHTaskWorker(workers, taskDef, config);
            boolean registered = false;
            for (int i = 0; i < 5; i++) {
                try {
                    worker.registerTaskDef();
                    System.out.println("[workflows] Registered TaskDef: " + taskDef);
                    registered = true;
                    break;
                } catch (Exception e) {
                    System.err.println("[workflows] Retry " + (i+1) + " register TaskDef " + taskDef + ": " + e.getMessage());
                    Thread.sleep(2000);
                }
            }
            if (registered) {
                worker.start();
                System.out.println("[workflows] Started worker for: " + taskDef);
            }
        }

        // -------------------------------------------------
        // 2. Register all WfSpecs (after TaskDefs are ready)
        // -------------------------------------------------
        List<Workflow> workflowDefs = WorkflowDefinitions.all();
        for (Workflow wf : workflowDefs) {
            boolean registered = false;
            for (int i = 0; i < 5; i++) {
                try {
                    wf.registerWfSpec(stub);
                    System.out.println("[workflows] Registered WfSpec: " + wf.getName());
                    registered = true;
                    break;
                } catch (Exception e) {
                    System.err.println("[workflows] Retry " + (i+1) + " register WfSpec " + wf.getName() + ": " + e.getMessage());
                    Thread.sleep(2000);
                }
            }
            if (!registered) System.err.println("[workflows] PERMANENT FAILURE: " + wf.getName());
        }

        System.out.println("[workflows] All workers running. Waiting for tasks...");

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            System.out.println("[workflows] Shutting down...")));

        Thread.currentThread().join();
    }
}
