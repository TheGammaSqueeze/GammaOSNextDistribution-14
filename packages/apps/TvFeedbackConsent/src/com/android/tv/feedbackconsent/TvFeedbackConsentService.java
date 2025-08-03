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

import static com.android.tv.feedbackconsent.TvFeedbackConstants.BUGREPORT_CONSENT;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.BUGREPORT_REQUESTED;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.CONSENT_RECEIVER;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.RESULT_CODE_OK;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOGS_CONSENT;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOGS_KEY;
import static com.android.tv.feedbackconsent.TvFeedbackConstants.SYSTEM_LOGS_REQUESTED;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.IBinder;
import android.os.RemoteException;
import android.net.Uri;
import android.util.Log;
import android.annotation.RequiresPermission;
import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.core.util.Preconditions;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TvFeedbackConsentService extends Service {

    private static final String TAG = TvFeedbackConsentService.class.getSimpleName();

    final TvDiagnosticInformationManagerBinder tvDiagnosticInformationBinder =
        new TvDiagnosticInformationManagerBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return tvDiagnosticInformationBinder;
    }

    private final class TvDiagnosticInformationManagerBinder extends
        ITvDiagnosticInformationManager.Stub {

        private boolean mSystemLogsRequested;
        private boolean mBugreportRequested;
        private boolean mSystemLogsConsented;
        private ITvDiagnosticInformationManagerCallback mTvFeedbackConsentCallback;
        private Uri mSystemLogsUri;
        private Uri mBugreportUri;

        @RequiresPermission(allOf = {Manifest.permission.DUMP, Manifest.permission.READ_LOGS})
        @Override
        public void getDiagnosticInformation(
            @Nullable Uri bugreportUri,
            @Nullable Uri systemLogsUri,
            @NonNull ITvDiagnosticInformationManagerCallback tvFeedbackConsentCallback) {
            Preconditions.checkNotNull(tvFeedbackConsentCallback);
            mTvFeedbackConsentCallback = tvFeedbackConsentCallback;
            mBugreportRequested = false;
            mSystemLogsRequested = false;
            if (bugreportUri != null) {
                mBugreportRequested = true;
                mBugreportUri = bugreportUri;
            }
            if (systemLogsUri != null) {
                mSystemLogsRequested = true;
                mSystemLogsUri = systemLogsUri;
            }
            Preconditions.checkArgument(mBugreportRequested || mSystemLogsRequested,
                "No Diagnostic information requested: " +
                    "Both bugreportUri and systemLogsUri cannot be null");

            ResultReceiver resultReceiver = createResultReceiver();
            displayConsentScreen(resultReceiver);
        }

        private ResultReceiver createResultReceiver() {
            return new ResultReceiver(
                new Handler(Looper.getMainLooper())) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (mTvFeedbackConsentCallback == null) {
                        Log.w(TAG, "Diagnostic information requested without a callback");
                        return;
                    }

                    if (mBugreportRequested) {
                        TvFeedbackBugreportHelper bugreportHelper =
                            new TvFeedbackBugreportHelper(
                                /* context= */ TvFeedbackConsentService.this);
                        bugreportHelper.startBugreport(
                            resultData.getBoolean(BUGREPORT_CONSENT, false),
                            mTvFeedbackConsentCallback,
                            mBugreportUri);

                    }
                    if (mSystemLogsRequested) {
                        mSystemLogsConsented = resultData.getBoolean(SYSTEM_LOGS_CONSENT,
                            false);
                        List<String> systemLogs = resultData.getStringArrayList(
                            SYSTEM_LOGS_KEY);

                        processSystemLogs(systemLogs);
                    }
                }
            };
        }

        private void processSystemLogs(List<String> systemLogs) {
            int systemLogsResultCode;
            if (!mSystemLogsConsented) {
                systemLogsResultCode =
                    mTvFeedbackConsentCallback.SYSTEM_LOGS_ERROR_USER_CONSENT_DENIED;
                Log.d(TAG, "User denied consent to share system logs");
            } else if (systemLogs == null || systemLogs.isEmpty()) {
                systemLogsResultCode = mTvFeedbackConsentCallback.SYSTEM_LOGS_ERROR_RUNTIME;
                Log.d(TAG, "Error generating system logs");
            } else {
                systemLogsResultCode = copySystemLogsToUri(systemLogs);
            }

            try {
                if (systemLogsResultCode == RESULT_CODE_OK) {
                    mTvFeedbackConsentCallback.onSystemLogsFinished();
                    Log.d(TAG, "System logs generated and returned successfully.");
                } else {
                    mTvFeedbackConsentCallback.onSystemLogsError(systemLogsResultCode);
                }

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        private int copySystemLogsToUri(List<String> systemLogs) {
            try (
                FileOutputStream out = (FileOutputStream) TvFeedbackConsentService.this
                    .getContentResolver().openOutputStream(mSystemLogsUri, "w");
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                for (String str : systemLogs) {
                    writer.write(str);
                    writer.write(System.lineSeparator());
                }
                return RESULT_CODE_OK;
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Uri file not found", e);
                return mTvFeedbackConsentCallback.SYSTEM_LOGS_ERROR_INVALID_INPUT;
            } catch (IOException e) {
                Log.e(TAG, "Error copying system logs", e);
                return mTvFeedbackConsentCallback.SYSTEM_LOGS_ERROR_WRITE_FAILED;
            }
        }

        private void displayConsentScreen(ResultReceiver resultReceiver) {
            Intent consentIntent = new Intent(
                TvFeedbackConsentService.this, TvFeedbackConsentActivity.class);
            consentIntent.putExtra(CONSENT_RECEIVER, resultReceiver);
            consentIntent.putExtra(BUGREPORT_REQUESTED, mBugreportRequested);
            consentIntent.putExtra(SYSTEM_LOGS_REQUESTED, mSystemLogsRequested);
            consentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                TvFeedbackConsentService.this.startActivity(consentIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error starting activity", e);
            }
        }
    }
}