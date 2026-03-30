package com.example.soar;

import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.*;
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
            "get-alert-details", "get-user-logs",
            "check-global-failure", "find-open-case", "add-case-note", "fail-workflow"
        );

        // 1. Register all TaskDefs
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
            if (!registered) {
                throw new RuntimeException("Failed to register " + taskDef);
            }
        }

        // 2. Register ExternalEventDefs
        for (int i = 0; i < 5; i++) {
            try {
                PutExternalEventDefRequest eventReq = PutExternalEventDefRequest.newBuilder()
                    .setName("analyst-decision")
                    .build();
                stub.putExternalEventDef(eventReq);
                System.out.println("[workflows] Registered ExternalEventDef: analyst-decision");
                break;
            } catch (Exception e) {
                System.err.println("[workflows] Retry " + (i+1) + " register ExternalEventDef analyst-decision: " + e.getMessage());
                Thread.sleep(2000);
            }
        }

        // 3. Register all WfSpecs
        for (Workflow wf : WorkflowDefinitions.all()) {
            for (int i = 0; i < 5; i++) {
                try {
                    wf.registerWfSpec(stub);
                    System.out.println("[workflows] Registered WfSpec: " + wf.getName());
                    break;
                } catch (Exception e) {
                    System.err.println("[workflows] Retry " + (i+1) + " register WfSpec " + wf.getName() + ": " + e.getMessage());
                    Thread.sleep(2000);
                }
            }
        }

        // 4. Start all LHTaskWorkers in background threads
        for (String taskDef : taskDefs) {
            new Thread(() -> {
                try {
                    LHTaskWorker worker = new LHTaskWorker(workers, taskDef, config);
                    worker.start();
                    System.out.println("[workflows] Started background worker for: " + taskDef);
                } catch (Exception e) {
                    System.err.println("[workflows] Error in worker " + taskDef + ": " + e.getMessage());
                }
            }).start();
        }

        System.out.println("[workflows] All workers running. Waiting for tasks...");

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            System.out.println("[workflows] Shutting down...")));

        Thread.currentThread().join();
    }
}
