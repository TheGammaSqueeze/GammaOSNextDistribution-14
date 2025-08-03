/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.clearcut;

import com.android.asuite.clearcut.Common;
import com.android.asuite.clearcut.Common.UserType;
import com.android.asuite.clearcut.ExternalUserLog.AtestLogEventExternal;
import com.android.asuite.clearcut.ExternalUserLog.AtestLogEventExternal.AtestExitEvent;
import com.android.asuite.clearcut.ExternalUserLog.AtestLogEventExternal.AtestStartEvent;
import com.android.asuite.clearcut.ExternalUserLog.AtestLogEventExternal.RunTestsFinishEvent;
import com.android.asuite.clearcut.ExternalUserLog.AtestLogEventExternal.RunnerFinishEvent;
import com.android.asuite.clearcut.InternalUserLog.AtestLogEventInternal;

import com.google.protobuf.ByteString;

import java.time.Duration;

/** Utility to help populate the event protos */
public class ClearcutEventHelper {

    private static final String TOOL_NAME = "Tradefed";

    /**
     * Create the start event for Tradefed.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @return a ByteString representation of the even proto.
     */
    public static ByteString createStartEvent(
            String userKey, String runId, UserType userType, String subToolName) {
        if (UserType.GOOGLE.equals(userType)) {
            AtestLogEventInternal.Builder builder =
                    createBaseInternalEventBuilder(userKey, runId, userType, subToolName);
            AtestLogEventInternal.AtestStartEvent.Builder startEventBuilder =
                    AtestLogEventInternal.AtestStartEvent.newBuilder();
            builder.setAtestStartEvent(startEventBuilder.build());
            return builder.build().toByteString();
        }

        AtestLogEventExternal.Builder builder =
                createBaseExternalEventBuilder(userKey, runId, userType, subToolName);
        AtestStartEvent.Builder startBuilder = AtestStartEvent.newBuilder();
        builder.setAtestStartEvent(startBuilder.build());
        return builder.build().toByteString();
    }

    /**
     * Create the end event for Tradefed.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @param sessionDuration The duration of the complete session.
     * @return a ByteString representation of the even proto.
     */
    public static ByteString createFinishedEvent(
            String userKey,
            String runId,
            UserType userType,
            String subToolName,
            Duration sessionDuration) {
        if (UserType.GOOGLE.equals(userType)) {
            AtestLogEventInternal.Builder builder =
                    createBaseInternalEventBuilder(userKey, runId, userType, subToolName);
            AtestLogEventInternal.AtestExitEvent.Builder exitEventBuilder =
                    AtestLogEventInternal.AtestExitEvent.newBuilder();
            Common.Duration duration =
                    Common.Duration.newBuilder()
                            .setSeconds(sessionDuration.getSeconds())
                            .setNanos(sessionDuration.getNano())
                            .build();
            exitEventBuilder.setDuration(duration);
            builder.setAtestExitEvent(exitEventBuilder.build());
            return builder.build().toByteString();
        }

        AtestLogEventExternal.Builder builder =
                createBaseExternalEventBuilder(userKey, runId, userType, subToolName);
        AtestLogEventExternal.AtestExitEvent.Builder startBuilder = AtestExitEvent.newBuilder();
        builder.setAtestExitEvent(startBuilder.build());
        return builder.build().toByteString();
    }

    /**
     * Create the start invocation event for Tradefed.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @return a ByteString representation of the even proto.
     */
    public static ByteString createRunStartEvent(
            String userKey, String runId, UserType userType, String subToolName) {
        if (UserType.GOOGLE.equals(userType)) {
            AtestLogEventInternal.Builder builder =
                    createBaseInternalEventBuilder(userKey, runId, userType, subToolName);
            AtestLogEventInternal.RunnerFinishEvent.Builder startRunEventBuilder =
                    AtestLogEventInternal.RunnerFinishEvent.newBuilder();
            builder.setRunnerFinishEvent(startRunEventBuilder.build());
            return builder.build().toByteString();
        }

        AtestLogEventExternal.Builder builder =
                createBaseExternalEventBuilder(userKey, runId, userType, subToolName);
        RunnerFinishEvent.Builder startBuilder = RunnerFinishEvent.newBuilder();
        builder.setRunnerFinishEvent(startBuilder.build());
        return builder.build().toByteString();
    }

    /**
     * Create the run test finished event for Tradefed.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @param testDuration the duration of the test session.
     * @return a ByteString representation of the even proto.
     */
    public static ByteString creatRunTestFinished(
            String userKey,
            String runId,
            UserType userType,
            String subToolName,
            Duration testDuration) {
        if (UserType.GOOGLE.equals(userType)) {
            AtestLogEventInternal.Builder builder =
                    createBaseInternalEventBuilder(userKey, runId, userType, subToolName);
            AtestLogEventInternal.RunTestsFinishEvent.Builder runTestsFinished =
                    AtestLogEventInternal.RunTestsFinishEvent.newBuilder();
            Common.Duration duration =
                    Common.Duration.newBuilder()
                            .setSeconds(testDuration.getSeconds())
                            .setNanos(testDuration.getNano())
                            .build();
            runTestsFinished.setDuration(duration);
            builder.setRunTestsFinishEvent(runTestsFinished.build());
            return builder.build().toByteString();
        }

        AtestLogEventExternal.Builder builder =
                createBaseExternalEventBuilder(userKey, runId, userType, subToolName);
        RunTestsFinishEvent.Builder startBuilder = RunTestsFinishEvent.newBuilder();
        builder.setRunTestsFinishEvent(startBuilder.build());
        return builder.build().toByteString();
    }

    /**
     * Create the basic event builder with all the common informations.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @return a builder for the event.
     */
    private static AtestLogEventExternal.Builder createBaseExternalEventBuilder(
            String userKey, String runId, UserType userType, String subToolName) {
        AtestLogEventExternal.Builder builder = AtestLogEventExternal.newBuilder();
        builder.setUserKey(userKey);
        builder.setRunId(runId);
        builder.setUserType(userType);
        builder.setToolName(TOOL_NAME);
        builder.setSubToolName(subToolName);
        return builder;
    }

    /**
     * Create the basic event builder with all the common informations.
     *
     * @param userKey The unique id representing the user
     * @param runId The current id for the session.
     * @param userType The type of the user: internal or external.
     * @param subToolName The name of test suite tool.
     * @return a builder for the event.
     */
    private static AtestLogEventInternal.Builder createBaseInternalEventBuilder(
            String userKey, String runId, UserType userType, String subToolName) {
        AtestLogEventInternal.Builder builder = AtestLogEventInternal.newBuilder();
        builder.setUserKey(userKey);
        builder.setRunId(runId);
        builder.setUserType(userType);
        builder.setToolName(TOOL_NAME);
        builder.setSubToolName(subToolName);
        return builder;
    }
}
