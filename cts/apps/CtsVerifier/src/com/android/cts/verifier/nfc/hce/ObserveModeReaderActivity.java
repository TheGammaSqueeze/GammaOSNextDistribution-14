/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

@TargetApi(19)
public class ObserveModeReaderActivity extends PassFailButtons.Activity implements ReaderCallback {
    public static final String TAG = "ObserveModeReaderActivity";

    NfcAdapter mAdapter;

    TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();

        setTitle(R.string.nfc_observe_mode_reader);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(12.0f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B, null);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        getPassButton().setEnabled(false);

    }
}
