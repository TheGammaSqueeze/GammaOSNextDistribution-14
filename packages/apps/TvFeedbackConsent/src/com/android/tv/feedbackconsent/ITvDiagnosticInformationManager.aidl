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

import android.net.Uri;
import com.android.tv.feedbackconsent.ITvDiagnosticInformationManagerCallback;

/**
 * Binder interface for getting Diagnostic Information on TV
 * {@hide}
 */

interface ITvDiagnosticInformationManager {

    /**
    * Asks for user consent and shares diagnostic data with the calling application.
    *
    * <p> Shows the user a consent screen for sharing diagnostic information such as the bugreport,
    * system logs, etc with the calling application.
    * Also allows the user to view the system logs being shared.

    * <p> System logs will be generated in the background and the user will be given the
    * option to view the logs.
    * If the user consents to sharing system logs, the logs will be copied over to the
    * Uri provided by the calling application and
    * {@link ITvDiagnosticInformationManagerCallback#onSystemLogsFinished} will be executed.
    * If log generation results in a failure or the user denies consent to sharing the system logs,
    * {@link ITvDiagnosticInformationManagerCallback#onSystemLogsError} will be called
    * along with information related to the error.
    *
    * <p>If the user consents to sharing the bugreport, {@link BugreportManager#startBugreport}
    * will be called to generate a bugreport in the background.
    * On successful bugreport generation, BugreportManager will automatically copy the
    * bugreport to the Uri provided by the calling application and
    * {@link ITvDiagnosticInformationManagerCallback#onBugreportFinished} will be executed.
    * If bugreport generation results in a failure or the user denies consent to sharing the bugreport,
    * {@link ITvDiagnosticInformationManagerCallback#onBugreportError} will be called
    * along with information related to the error.
    *
    * bugreportUri and systemLogsUri are nullable parameters and are independent of each other.
    * However, passing both these parameters as null implies no information has been requested, and
    * result in an error.
    *
    * @param bugreportUri the optional identifier for the file to which the zipped bugreport
    * should be written
    * @param SystemLogsUri the optional identifier for the file to which the system logs
    * should be written
    * @param listener callback for updates;
    */
    void getDiagnosticInformation(
        in Uri bugreportUri,
        in Uri systemLogsUri,
        in ITvDiagnosticInformationManagerCallback tvDiagnosticInformationManagerCallback);
}