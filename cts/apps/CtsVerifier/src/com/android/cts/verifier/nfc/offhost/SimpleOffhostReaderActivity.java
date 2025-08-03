/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.nfc.offhost;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.util.Arrays;

import com.android.cts.verifier.nfc.hce.HceUtils;
import com.android.cts.verifier.nfc.hce.CommandApdu;

public class SimpleOffhostReaderActivity extends PassFailButtons.Activity implements ReaderCallback,
        OnItemSelectedListener {
    public static final String PREFS_NAME = "OffhostTypePrefs";

    public static final String TAG = "SimpleOffhostReaderActivity";
    public static final String EXTRA_APDUS = "apdus";
    public static final String EXTRA_RESPONSES = "responses";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_DESELECT = "deselect";

    NfcAdapter mAdapter;
    CommandApdu[] mApdus;
    String[] mResponses;
    String mLabel;
    boolean mDeselect;
    boolean mIsTypeB;

    TextView mTextView;
    Spinner mSpinner;
    SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nfc_hce_reader);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mLabel = getIntent().getStringExtra(EXTRA_LABEL);
        mDeselect = getIntent().getBooleanExtra(EXTRA_DESELECT, false);
        setTitle(mLabel);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(12.0f);
        mTextView.setText(R.string.nfc_offhost_uicc_type_selection);

        Spinner spinner = (Spinner) findViewById(R.id.type_ab_selection);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.nfc_types_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        mPrefs = getSharedPreferences(PREFS_NAME, 0);
        mIsTypeB = mPrefs.getBoolean("typeB", false);
        if (mIsTypeB) {
            spinner.setSelection(1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_BARCODE | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        Intent intent = getIntent();
        Parcelable[] apdus = intent.getParcelableArrayExtra(EXTRA_APDUS);
        if (apdus != null) {
            mApdus = new CommandApdu[apdus.length];
            for (int i = 0; i < apdus.length; i++) {
                mApdus[i] = (CommandApdu) apdus[i];
            }
        } else {
            mApdus = null;
        }
        mResponses = intent.getStringArrayExtra(EXTRA_RESPONSES);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        final StringBuilder sb = new StringBuilder();
        long startTime = 0;
        boolean success = true;
        int count = 0;

        try {
            if(mDeselect) {
                mIsTypeB = mPrefs.getBoolean("typeB", false);
                // Use FrameRF for deselect test case
                if(mIsTypeB) {
                    NfcB nfcb = NfcB.get(tag);
                    if (nfcb == null) {
                        // TODO dialog box
                        return;
                    }
                    byte[] tagId = tag.getId();
                    String tagIdString = HceUtils.getHexBytes("", tagId);
                    nfcb.connect();
                    startTime = System.currentTimeMillis();

                    //ATTRIB
                    int tagIdLen = tagId.length;
                    if (tagIdLen != 4) {
                        // NFCID0 should be 4 bytes
                        return;
                    }
                    byte[] attrib = new byte[tagIdLen + 5];
                    attrib[0] = (byte)0x1d;
                    for(int i = 0; i < tagIdLen; i ++) {
                        attrib[1+i] = tagId[i];
                    }
                    attrib[tagIdLen+1] = 0x00;
                    attrib[tagIdLen+2] = 0x08;
                    attrib[tagIdLen+3] = 0x01;
                    attrib[tagIdLen+4] = 0x00;
                    nfcb.transceive(attrib);

                    count = 0;
                    for (CommandApdu apdu: mApdus) {
                        sb.append("Request APDU:\n");
                        sb.append(apdu.getApdu() + "\n\n");
                        long apduStartTime = System.currentTimeMillis();
                        byte[] response =
                                nfcb.transceive(HceUtils.hexStringToBytes(apdu.getApdu()));
                        long apduEndTime = System.currentTimeMillis();
                        if (!responseCheck(sb, response, count, apduStartTime, apduEndTime)) {
                            success = false;
                            break;
                        }
                        count++;
                    }
                    nfcb.transceive(HceUtils.hexStringToBytes("C2"));
                } else {
                    NfcA nfca = NfcA.get(tag);
                    if (nfca == null) {
                        // TODO dialog box
                        return;
                    }
                    nfca.connect();
                    nfca.setTimeout(5000);
                    startTime = System.currentTimeMillis();
                    // RATS
                    nfca.transceive(HceUtils.hexStringToBytes("E080"));

                    count = 0;
                    for (CommandApdu apdu: mApdus) {
                        sb.append("Request APDU:\n");
                        sb.append(apdu.getApdu() + "\n\n");
                        long apduStartTime = System.currentTimeMillis();
                        byte[] response =
                                nfca.transceive(HceUtils.hexStringToBytes(apdu.getApdu()));
                        long apduEndTime = System.currentTimeMillis();
                        if (!responseCheck(sb, response, count, apduStartTime, apduEndTime)) {
                            success = false;
                            break;
                        }
                        count++;
                    }
                    // S-block DESELECT
                    nfca.transceive(HceUtils.hexStringToBytes("C2"));
                }
                mAdapter.disableReaderMode(this);
            } else {
                IsoDep isoDep = IsoDep.get(tag);
                if (isoDep == null) {
                    // TODO dialog box
                    return;
                }
                isoDep.connect();
                isoDep.setTimeout(5000);
                startTime = System.currentTimeMillis();

                count = 0;
                for (CommandApdu apdu: mApdus) {
                    sb.append("Request APDU:\n");
                    sb.append(apdu.getApdu() + "\n\n");
                    long apduStartTime = System.currentTimeMillis();
                    byte[] response = isoDep.transceive(HceUtils.hexStringToBytes(apdu.getApdu()));
                    long apduEndTime = System.currentTimeMillis();
                    if (!responseCheck(sb, response, count, apduStartTime, apduEndTime)) {
                        success = false;
                        break;
                    }
                    count++;
                }
            }

            if (success) {
                sb.insert(0, "Total APDU exchange time: " +
                        Long.toString(System.currentTimeMillis() - startTime) + " ms.\n\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(sb.toString());
                        getPassButton().setEnabled(true);
                    }
                });
            } else {
                sb.insert(0, "FAIL. Total APDU exchange time: " +
                        Long.toString(System.currentTimeMillis() - startTime) + " ms.\n\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(sb.toString());
                        AlertDialog.Builder builder = new AlertDialog.Builder(SimpleOffhostReaderActivity.this);
                        builder.setTitle("Test failed");
                        builder.setMessage("An unexpected response APDU was received, or no APDUs were received at all.");
                        builder.setPositiveButton("OK", null);
                        builder.show();
                    }
                });
            }
        } catch (IOException e) {
            sb.insert(0, "Error while reading: (did you keep the devices in range?)\nPlease try again\n.");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView.setText(sb.toString());
                }
            });
        } finally {
        }
    }

    private boolean responseCheck(StringBuilder sb, byte[] response, int count,
            long apduStartTime, long apduEndTime) {
        sb.append("Response APDU (in " + Long.toString(apduEndTime - apduStartTime) +
                " ms):\n");
        sb.append(HceUtils.getHexBytes(null, response));
        sb.append("\n\n\n");
        boolean wildCard = "*".equals(mResponses[count]);
        byte[] expectedResponse = HceUtils.hexStringToBytes(mResponses[count]);
        Log.d(TAG, HceUtils.getHexBytes("APDU response: ", response));

        if (response.length > expectedResponse.length) {
            response = Arrays.copyOfRange(response,
                    response.length - expectedResponse.length, response.length);
        }

        if (!wildCard && !Arrays.equals(response, expectedResponse)) {
            Log.d(TAG, "Unexpected APDU response: " +
                                HceUtils.getHexBytes("", response));
            return false;
        }
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
            long id) {
        if (position == 0) {
            // Type-A
            mAdapter.disableReaderMode(this);
            mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_BARCODE | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean("typeB", false);
            editor.commit();
        } else {
            // Type-B
            mAdapter.disableReaderMode(this);
            mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_B |
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean("typeB", true);
            editor.commit();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public String getTestId() {
        return mLabel;
    }
}
