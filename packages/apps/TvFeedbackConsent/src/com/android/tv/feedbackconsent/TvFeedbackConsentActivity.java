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

import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TvFeedbackConsentActivity extends Activity implements
        TvFeedbackConsentDataCollector.TvFeedbackConsentDataCollectorCallback {

    private static final String TAG = TvFeedbackConsentActivity.class.getSimpleName();

    private static final String DATE_PATTERN = "MM/dd/yyyy, hh:mm a";
    private static ResultReceiver resultReceiver;
    private boolean systemLogRequested;
    private boolean bugreportRequested;
    private boolean sendLogs;
    private boolean sendBugreport;

    private static final List<String> systemLogsLoadingText = Collections.singletonList(
            "Loading, please wait.....");
    TvFeedbackConsentInformationDialog viewLogsDialog;
    private TvFeedbackConsentDataCollector tvFeedbackDataCollector;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        systemLogRequested = intent.getBooleanExtra(SYSTEM_LOGS_REQUESTED, false);
        bugreportRequested = intent.getBooleanExtra(BUGREPORT_REQUESTED, false);

        if (!systemLogRequested && !bugreportRequested) {
            Log.e(TAG, "Consent screen requested without requesting any data.");
            this.onStop();
        }
        setContentView(R.layout.tv_feedback_consent);
        onViewCreated();
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = getIntent();
        resultReceiver = intent.getParcelableExtra(CONSENT_RECEIVER, ResultReceiver.class);
    }

    private void onViewCreated() {
        View nextButton = requireViewById(R.id.next_button);
        nextButton.setOnClickListener(this::onNextButtonClicked);
        nextButton.requestFocus();

        if (systemLogRequested) {
            View systemLogsRow = requireViewById(R.id.system_logs_row);
            systemLogsRow.setVisibility(View.VISIBLE);
            View systemLogsSwitch = requireViewById(R.id.system_logs_switch);
            systemLogsSwitch.setOnFocusChangeListener(
                    (v, focused) -> systemLogsRow.setSelected(focused));
            prepareSystemLogs();
        }

        if (bugreportRequested) {
            View bugreportRow = requireViewById(R.id.bugreport_row);
            bugreportRow.setVisibility(View.VISIBLE);

            String dateTime = LocalDateTime.now(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.US));
            String formattedBugreportLegalText = getString(
                R.string.feedback_bugreport_legal_display_text, dateTime);
            TextView bugreportLegalTextView = requireViewById(R.id.bugreport_legal_text);
            bugreportLegalTextView.setText(formattedBugreportLegalText);

            View bugreportSwitch = requireViewById(R.id.bugreport_switch);
            bugreportSwitch.setOnFocusChangeListener(
                (v, focused) -> bugreportRow.setSelected(focused));
        }
    }

    private void prepareSystemLogs() {
        viewLogsDialog = new TvFeedbackConsentInformationDialog(
                TvFeedbackConsentActivity.this,
                R.style.ViewLogsDialogTheme,
                R.layout.view_system_logs_dialog,
                systemLogsLoadingText);
        viewLogsDialog.create();

        View viewLogsButton = requireViewById(R.id.view_logs_button);
        viewLogsButton.setOnClickListener((v) -> viewLogsDialog.show());
        viewLogsButton.setVisibility(View.VISIBLE);

        tvFeedbackDataCollector = new TvFeedbackConsentDataCollector(this);
        tvFeedbackDataCollector.collectSystemLogs(/* numLines= */ 10000);
    }

    @Override
    public void onSystemLogsReady() {
        if (!systemLogRequested || tvFeedbackDataCollector == null || viewLogsDialog == null) {
            return;
        }
        List<String> systemLogs = tvFeedbackDataCollector.getSystemLogs();
        viewLogsDialog.updateRecyclerView(systemLogs);
    }

    private void onNextButtonClicked(View view) {
        sendLogs = ((Switch) requireViewById(R.id.system_logs_switch)).isChecked();
        sendBugreport = ((Switch) requireViewById(R.id.bugreport_switch)).isChecked();
        finish();
    }

    private void sendResult() {
        if (resultReceiver == null) {
            Log.w(TAG, "Activity intent does not contain a result receiver");
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putSerializable(SYSTEM_LOGS_CONSENT, sendLogs);
        bundle.putSerializable(BUGREPORT_CONSENT, sendBugreport);
        if (systemLogRequested && sendLogs) {
            bundle.putStringArrayList(SYSTEM_LOGS_KEY,
                    new ArrayList<>(tvFeedbackDataCollector.getSystemLogs()));
        }

        try {
            resultReceiver.send(RESULT_CODE_OK, bundle);
        } catch (Exception e) {
            Log.e(TAG, "Exception in sending result: ", e);
        }
    }

    @Override
    public void onStop() {
        sendResult();
        super.onStop();
    }
}