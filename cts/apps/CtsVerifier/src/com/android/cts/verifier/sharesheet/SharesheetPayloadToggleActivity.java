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

import static com.android.cts.verifier.sharesheet.TestContract.LogTags.TAG;

import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.service.chooser.Flags;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.sharesheet.TestContract.Keys;
import com.android.cts.verifier.sharesheet.TestContract.UriParams;
import com.android.cts.verifier.sharesheet.TestContract.Uris;

import java.util.ArrayList;

public class SharesheetPayloadToggleActivity  extends PassFailButtons.Activity {
    private final String[] mMimeTypes = new String[] { "image/png", "image/jpg" };
    private final ArrayList<Uri> mUris;
    private int mLaunchId = 1;

    public SharesheetPayloadToggleActivity() {
        mUris = new ArrayList<>(3);
        for (int i = 1; i <= 3; i++) {
            mUris.add(
                    Uris.ImageBaseUri.buildUpon()
                            .appendQueryParameter(UriParams.Name, Integer.toString(i))
                            .appendQueryParameter(UriParams.Type, mMimeTypes[i % mMimeTypes.length])
                            .build());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Flags.chooserPayloadToggling()) {
            // If the API isn't enabled, immediately let the test pass.
            Toast.makeText(this, R.string.sharesheet_skipping_for_flag, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(true);
            return;
        }

        setContentView(R.layout.sharesheet_payload_toggle_activity);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.sharesheet_payload_toggle_test,
                R.string.sharesheet_payload_toggle_test_info,
                -1);

        Button shareBtn = findViewById(R.id.share);

        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.GONE);

        shareBtn.setOnClickListener(v -> {
            share();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mLaunchId != intent.getIntExtra(Keys.LaunchId, -1)) {
            Log.w(TAG, "Unexpected launch id");
            return;
        }

        if (!Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            Log.d(TAG, "Wrong action type: " + intent.getAction()
                    + ", expected: ACTION_SEND_MULTIPLE");
            Toast.makeText(this, R.string.sharesheet_wrong_intent_action, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(false);
            return;
        }
        if (!"image/*".equals(intent.getType())) {
            Log.d(TAG, "Wrong mime type: " + intent.getType() + ", expected: 'image/*'");
            Toast.makeText(
                    this, R.string.sharesheet_wrong_intent_mime_type, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(false);
            return;
        }
        ArrayList<Uri> sharedUris = intent.getParcelableArrayListExtra(
                Intent.EXTRA_STREAM, Uri.class);
        if (sharedUris == null || sharedUris.size() != mUris.size()
                || !sharedUris.containsAll(mUris)) {
            Log.d(TAG, "Wrong shared items: " + sharedUris);
            Toast.makeText(this, R.string.sharesheet_wrong_shared_items, Toast.LENGTH_LONG).show();
            setTestResultAndFinish(false);
            return;
        }
        Toast.makeText(this, R.string.result_success, Toast.LENGTH_LONG).show();
        setTestResultAndFinish(true);
    }

    private void share() {
        final int focusedIdx = 1;
        String mimeType = mMimeTypes[focusedIdx % mMimeTypes.length];
        Uri uri = mUris.get(focusedIdx);

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.setType(mimeType);
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.setClipData(
                new ClipData("", new String[] { mimeType }, new Item(uri)));
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        mLaunchId++;
        sendIntent.putExtra(Keys.LaunchId, mLaunchId);
        //TODO: investigate why Chooser does not show this activity when launched
        //sendIntent.setClass(this, getClass());
        String category =
                "android.cts.intent.category.MANUAL_TEST.SharesheetPayloadToggleActivity";
        sendIntent.addCategory(category);

        Intent shareIntent = Intent.createChooser(sendIntent, null);

        // Since we're specifying a target component, don't auto-launch it.
        shareIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI, Uris.ExtraContentUri);
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, 0);
        ClipData clipData = new ClipData("", new String[0], new Item(Uris.ExtraContentUri));
        clipData.addItem(new Item(uri));
        shareIntent.setClipData(clipData);
        shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // test extras
        shareIntent.putExtra(Keys.AdditionalContent, mUris);
        shareIntent.putExtra(Keys.CursorStartPos, focusedIdx);
        startActivity(shareIntent);
    }
}
