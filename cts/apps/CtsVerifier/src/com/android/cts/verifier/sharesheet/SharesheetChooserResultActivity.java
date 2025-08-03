/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.verifier.sharesheet;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.chooser.ChooserResult;
import android.service.chooser.Flags;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class SharesheetChooserResultActivity extends PassFailButtons.Activity {
    private static final String CHOOSER_RESULT =
            "com.android.cts.verifier.sharesheet.CHOOSER_RESULT";

    private final ChooserResult mExpectedCopy =
            new ChooserResult(ChooserResult.CHOOSER_RESULT_COPY, null, false);

    BroadcastReceiver mChooserCallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            evaluateAndSetResult(intent.getParcelableExtra(
                    Intent.EXTRA_CHOOSER_RESULT,
                    ChooserResult.class));
        }
    };

    private void evaluateAndSetResult(ChooserResult result) {
        if (mExpectedCopy.equals(result)) {
            Toast.makeText(SharesheetChooserResultActivity.this,
                    R.string.sharesheet_result_test_passed,
                    Toast.LENGTH_SHORT).show();
            setTestResultAndFinish(true);
            return;
        }
        setTestResultAndFinish(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Flags.enableChooserResult()) {
            // If the API isn't enabled, immediately let the test pass.
            Toast.makeText(this, R.string.sharesheet_skipping_for_flag, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(true);
            return;
        }

        setContentView(R.layout.sharesheet_chooser_result_activity);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.sharesheet_result_test,
                R.string.sharesheet_result_test_info,
                -1);

        final TextView instructionText = requireViewById(R.id.instructions);
        final Button shareButton = requireViewById(R.id.sharesheet_share_button);
        final View afterShareSection =
                requireViewById(R.id.sharesheet_result_test_instructions_after_share);
        final Button copyNotFound = requireViewById(R.id.sharesheet_result_test_not_found);
        final Button copyPressed = requireViewById(R.id.sharesheet_result_test_pressed);

        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.GONE);

        shareButton.setOnClickListener(v -> {
            shareText();
            instructionText.setText(R.string.sharesheet_result_test_instructions_after_share);
            afterShareSection.setVisibility(View.VISIBLE);
            shareButton.setText(R.string.sharesheet_result_test_try_again);
        });

        copyPressed.setOnClickListener(v -> {
            // Pressed copy but not callback was received, fail.
            Toast.makeText(this,
                    R.string.sharesheet_result_test_no_result_message,
                    Toast.LENGTH_LONG).show();
            setTestResultAndFinish(false);
        });

        // If there's no copy button, then the test is passed.
        copyNotFound.setOnClickListener(v -> {
            Toast.makeText(this,
                    R.string.sharesheet_result_test_no_button,
                    Toast.LENGTH_LONG).show();
            setTestResultAndFinish(true);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mChooserCallbackReceiver, new IntentFilter(CHOOSER_RESULT),
                RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mChooserCallbackReceiver);
    }

    private void shareText() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Testing");
        sendIntent.setType("text/plain");

        Intent resultIntent = new Intent(CHOOSER_RESULT).setPackage(getPackageName());
        PendingIntent shareResultIntent = PendingIntent.getBroadcast(
                /* context= */ this,
                /* flags= */ 0,
                /* intent= */ resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Intent shareIntent = Intent.createChooser(
                /* target= */ sendIntent,
                /* title= */ null,
                /* sender= */ shareResultIntent.getIntentSender()
        );
        startActivity(shareIntent);
    }
}
