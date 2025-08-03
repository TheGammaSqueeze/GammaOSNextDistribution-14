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

import android.content.Intent;
import android.os.Bundle;
import android.service.chooser.Flags;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test the album subtype hint (must be honored for sharesheets that show a headline like
 * "Sharing text").
 *
 * The flow is broken up into three steps:
 * 1. Ask the user to invoke the sharesheet and see if there is a headline text.
 * 2. After the sharesheet has been invoked, confirm whether there was a headline.
 * 3. If there was a headline, ask the user to invoke again, this time using the API to indicate
 *    that an album is being shared, and let the user pass or fail the test based upon whether the
 *    album designation is honored.
 */
public class SharesheetAlbumActivity extends PassFailButtons.Activity {
    // True iff the operator has confirmed that the sharesheet displayed a headline (meaning we are
    // in step 3 described above).
    private boolean mHeadlineConfirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Flags.chooserAlbumText()) {
            // If the API isn't enabled, immediately let the test pass.
            Toast.makeText(this, R.string.sharesheet_skipping_for_flag, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(true);
            return;
        }

        setContentView(R.layout.sharesheet_album_activity);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.sharesheet_album_test, R.string.sharesheet_album_test_info, -1);

        Button shareBtn = (Button) findViewById(R.id.share);
        TextView instructionText = (TextView) findViewById(R.id.instructions);

        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.GONE);

        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mHeadlineConfirmed) {
                    // Show the UI for step two and open the sharesheet.
                    instructionText.setText(R.string.sharesheet_album_test_step_2);
                    findViewById(R.id.step_two_buttons).setVisibility(View.VISIBLE);
                    shareText(false);
                } else {
                    // Show the sharesheet with the album hint, let the user pass the test after
                    // it's shown.
                    getPassButton().setVisibility(View.VISIBLE);
                    shareText(true);
                }
            }
        });

        findViewById(R.id.title_yes).setOnClickListener(v -> {
            // There is a title, so move to the next step to see if the album subtype is honored.
            instructionText.setText(R.string.sharesheet_album_test_step_3);
            findViewById(R.id.step_two_buttons).setVisibility(View.GONE);
            mHeadlineConfirmed = true;
        });

        // If there's no title, then the test is passed.
        findViewById(R.id.title_no).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sharesheet_no_title_message, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(true);
        });
    }

    private void shareText(boolean album) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Testing");
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        if (album) {
            shareIntent.putExtra(Intent.EXTRA_CHOOSER_CONTENT_TYPE_HINT,
                    Intent.CHOOSER_CONTENT_TYPE_ALBUM);
        }
        startActivity(shareIntent);
    }
}
