/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tv.feedbackconsent;

/**
 * This interface defines callback functions for the
 * {@link ITvDiagnosticInformationManager#getDiagnosticInformation} request.
 *
 * {@hide}
 */

interface ITvDiagnosticInformationManagerCallback {

    /**
    * Called when system logs are generated successfully,
    * user consents to sharing system logs, and the logs are available in the
    * respective URI provided by the calling application.
    */
    oneway void onSystemLogsFinished();

    /* Options specified are invalid or incompatible */
    const int SYSTEM_LOGS_ERROR_INVALID_INPUT = 1;

    /* System Logs generation encountered a runtime error */
    const int SYSTEM_LOGS_ERROR_RUNTIME = 2;

    /* User denied consent to share the system logs with the specified app */
    const int SYSTEM_LOGS_ERROR_USER_CONSENT_DENIED = 3;

    /* The request to get user consent timed out */
    const int SYSTEM_LOGS_ERROR_USER_CONSENT_TIMED_OUT = 4;

    /* There is currently a consent request in progress. The caller should try again later. */
    const int SYSTEM_LOGS_ERROR_ANOTHER_REQUEST_IN_PROGRESS = 5;

    /* There is no system logs to retrieve for the given caller. */
    const int SYSTEM_LOGS_ERROR_NO_SYSTEM_LOGS_TO_RETRIEVE = 6;

    /* Failed to write system logs to the FD provided by the caller. */
    const int SYSTEM_LOGS_ERROR_WRITE_FAILED = 7;

    /**
    * Called when user denies consent to sharing system logs or system logs
    * generation results in failure.
    *
    * @param systemLogsErrorCode One of the SYSTEM_LOGS error codes listed above.
    */

    oneway void onSystemLogsError(int systemLogsErrorCode);

    /**
    * Called when user consents to sharing bugreport,
    * bugreport is generated successfully and is available in the
    * respective URI provided by the calling application.
    */
    oneway void onBugreportFinished();

    /**
    * Called when user denies consent to sharing the bugreport or bugreport
    * generation results in failure.
    *
    * @param bugreportErrorCode One of the BUGREPORT error codes listed in IDumpstateListener.aidl.
    */
    oneway void onBugreportError(int bugreportErrorCode);
}