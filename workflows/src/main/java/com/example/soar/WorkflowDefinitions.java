package com.example.soar;

import io.littlehorse.sdk.common.proto.Comparator;
import io.littlehorse.sdk.common.proto.VariableMutationType;
import io.littlehorse.sdk.common.proto.VariableType;
import io.littlehorse.sdk.wfsdk.NodeOutput;
import io.littlehorse.sdk.wfsdk.TaskNodeOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.Workflow;

import java.util.List;

/**
 * Defines all LittleHorse WfSpecs for the SOAR demo.
 *
 * Workflows:
 *   1. fraud-alert-workflow        - Run fraud check, conditionally freeze account
 *   2. alert-investigation-workflow - Escalate alert, wait for analyst ExternalEvent decision
 *   3. transaction-verification-workflow - Verify card or check payment instrument
 *   4. account-freeze-workflow     - Freeze account and post alert (callable standalone)
 */
public class WorkflowDefinitions {

    public static List<Workflow> all() {
        return List.of(
            fraudAlertWorkflow(),
            alertInvestigationWorkflow(),
            transactionVerificationWorkflow(),
            accountFreezeWorkflow()
        );
    }

    // -------------------------------------------------------------------------
    // 1. Fraud Alert Workflow
    // Input variables: username, transactionJson, ipAddress, amount, country
    // Flow:
    //   a) get-account-info(username)  -> accountJson
    //   b) get-fraud-score(transactionJson, accountJson) -> fraudScore
    //   c) post-log-entry(username, "fraud-check", fraudScore)
    //   d) if fraudScore > 0.60:
    //        freeze-account(username)
    //        post-alert(username, fraudScore, transactionJson, "CRITICAL")
    //        send-notification(username, "ACCOUNT_FROZEN_HIGH_FRAUD", fraudScore)
    //      else if fraudScore > 0.35:
    //        post-alert (MEDIUM alert, no freeze)
    //        send-notification
    // -------------------------------------------------------------------------
    private static Workflow fraudAlertWorkflow() {
        return Workflow.newWorkflow("fraud-alert-workflow", wf -> {
            WfRunVariable username = wf.addVariable("username", VariableType.STR).required();
            WfRunVariable txJson   = wf.addVariable("transactionJson", VariableType.STR).required();
            // ipAddress, amount, country stored for scoring context
            wf.addVariable("ipAddress", VariableType.STR).withDefault("");
            wf.addVariable("amount", VariableType.DOUBLE).withDefault(0.0);
            wf.addVariable("country", VariableType.STR).withDefault("");

            // Step 1: get account info (with 2 retries)
            TaskNodeOutput accountJson = wf.execute("get-account-info", username).withRetries(2);

            // Step 2: get fraud score (with 2 retries)
            TaskNodeOutput fraudScore  = wf.execute("get-fraud-score", txJson, accountJson).withRetries(2);

            // Step 3: always log the check
            wf.execute("post-log-entry", username, "fraud-check-complete", fraudScore);

            // Step 4: if score > 0.60 -> freeze + critical alert
            wf.doIf(
                wf.condition(fraudScore, Comparator.GREATER_THAN, 0.60),
                ifBlock -> {
                    ifBlock.execute("freeze-account", username).withRetries(3);
                    ifBlock.execute("create-case", username, "HIGH_FRAUD_SCORE", "SYSTEM", "Frozen due to fraud score > 0.60");
                    ifBlock.execute("post-alert", username, fraudScore, txJson, "CRITICAL");
                    ifBlock.execute("send-notification", username, "ACCOUNT_FROZEN_HIGH_FRAUD", fraudScore);
                }
            );

            // Step 5: else if score is MEDIUM (0.35-0.60) -> alert only
            wf.doIf(
                wf.condition(fraudScore, Comparator.GREATER_THAN_EQ, 0.35),
                medBlock -> {
                    medBlock.doIf(
                        medBlock.condition(fraudScore, Comparator.LESS_THAN, 0.60),
                        alertBlock -> {
                            alertBlock.execute("post-alert", username, fraudScore, txJson, "MEDIUM");
                            alertBlock.execute("send-notification", username, "SUSPICIOUS_ACTIVITY", fraudScore);
                        }
                    );
                }
            );
        });
    }

    // -------------------------------------------------------------------------
    // 2. Alert Investigation Workflow
    // Demonstrates LittleHorse ExternalEvents - the workflow pauses and waits
    // for an analyst to send the ANALYST_DECISION event ("FREEZE" or "CLOSE").
    // Input variables: alertId, username, fraudScore
    // -------------------------------------------------------------------------
    private static Workflow alertInvestigationWorkflow() {
        return Workflow.newWorkflow("alert-investigation-workflow", wf -> {
            WfRunVariable alertId    = wf.addVariable("alertId", VariableType.STR).required();
            WfRunVariable username   = wf.addVariable("username", VariableType.STR).required();
            WfRunVariable fraudScore = wf.addVariable("fraudScore", VariableType.DOUBLE).withDefault(0.0);

            // Notify analyst and log
            wf.execute("send-notification", username, "ESCALATED_FOR_INVESTIGATION", alertId);
            wf.execute("post-log-entry", username, "alert-investigation-started", alertId);

            // PAUSE: wait for ANALYST_DECISION external event
            NodeOutput decision = wf.waitForEvent("ANALYST_DECISION");

            // Branch: FREEZE
            wf.doIf(
                wf.condition(decision, Comparator.EQUALS, "FREEZE"),
                freezeBlock -> {
                    freezeBlock.execute("freeze-account", username).withRetries(3);
                    freezeBlock.execute("create-case", username, "ANALYST_FREEZE", "ANALYST", alertId);
                    freezeBlock.execute("post-log-entry", username, "investigation-resulted-in-freeze", alertId);
                    freezeBlock.execute("send-notification", username, "ACCOUNT_FROZEN_BY_ANALYST", alertId);
                }
            );

            // Branch: CLOSE
            wf.doIf(
                wf.condition(decision, Comparator.EQUALS, "CLOSE"),
                closeBlock -> {
                    closeBlock.execute("post-log-entry", username, "investigation-closed-no-action", alertId);
                    // Close the case if one exists (actually alert-investigation might not have a case yet if it was just an alert)
                    // But in SOAR, typically an alert investigation *is* part of a case.
                    // For now let's just log it.
                    closeBlock.execute("send-notification", username, "ALERT_CLOSED_NO_ACTION", alertId);
                }
            );
        });
    }

    // -------------------------------------------------------------------------
    // 3. Transaction Verification Workflow
    // Input variables: username, verifyType (CARD | CHECK), paymentData (JSON)
    // -------------------------------------------------------------------------
    private static Workflow transactionVerificationWorkflow() {
        return Workflow.newWorkflow("transaction-verification-workflow", wf -> {
            WfRunVariable username    = wf.addVariable("username", VariableType.STR).required();
            WfRunVariable verifyType  = wf.addVariable("verifyType", VariableType.STR).required();
            WfRunVariable paymentData = wf.addVariable("paymentData", VariableType.STR).withDefault("{}");
            WfRunVariable verifyResult = wf.addVariable("verifyResult", VariableType.STR);

            // Conditional branch based on verifyType
            wf.doIf(
                wf.condition(verifyType, Comparator.EQUALS, "CARD"),
                cardBlock -> {
                    NodeOutput cardResult = cardBlock.execute("verify-card", username, paymentData);
                    cardBlock.mutate(verifyResult, VariableMutationType.ASSIGN, cardResult);
                }
            );

            wf.doIf(
                wf.condition(verifyType, Comparator.EQUALS, "CHECK"),
                checkBlock -> {
                    NodeOutput checkResult = checkBlock.execute("verify-check", username, paymentData);
                    checkBlock.mutate(verifyResult, VariableMutationType.ASSIGN, checkResult);
                }
            );

            // Always log the verification attempt
            wf.execute("post-log-entry", username, "payment-instrument-verified", verifyType);
        });
    }

    // -------------------------------------------------------------------------
    // 4. Account Freeze Workflow  (standalone, e.g. triggered by compliance)
    // Input variables: username, reason
    // -------------------------------------------------------------------------
    private static Workflow accountFreezeWorkflow() {
        return Workflow.newWorkflow("account-freeze-workflow", wf -> {
            WfRunVariable username = wf.addVariable("username", VariableType.STR).required();
            WfRunVariable reason   = wf.addVariable("reason", VariableType.STR).withDefault("COMPLIANCE");

            // Freeze with 3 retries, then alert and notify
            wf.execute("freeze-account", username).withRetries(3);
            wf.execute("post-alert", username, 0.0, "{}", "HIGH");
            wf.execute("post-log-entry", username, "account-frozen-by-workflow", reason);
            wf.execute("send-notification", username, "ACCOUNT_FROZEN_COMPLIANCE", reason);
        });
    }
}
