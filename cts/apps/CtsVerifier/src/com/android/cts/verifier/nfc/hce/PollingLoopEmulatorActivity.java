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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

import java.util.HexFormat;
import java.util.List;

public class PollingLoopEmulatorActivity extends BaseEmulatorActivity {

    int mNfcTech = 0;
    int mNfcACount = 0;
    int mNfcBCount = 0;
    int mNfcOnCount = 0;
    int mNfcOffCount = 0;
    boolean mAllowedTransaction = false;
    PendingIntent mPendingIntent;
    String mCustomFrame = null;

    private static final String NFC_TECH_KEY = "NFC_TECH";
    private static final String NFC_CUSTOM_FRAME = "NFC_CUSTOM_FRAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(true);
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE_UNAUDITED);
        IntentFilter filter = new IntentFilter(PollingLoopService.POLLING_FRAME_ACTION);
        registerReceiver(mFieldStateReceiver, filter, RECEIVER_EXPORTED);
    }

    private static String getReaderString(Context context, int nfcTech) {
        if (nfcTech == NfcAdapter.FLAG_READER_NFC_A) {
            return context.getString(R.string.nfc_polling_loop_a_reader);
        } else if (nfcTech == NfcAdapter.FLAG_READER_NFC_B) {
            return context.getString(R.string.nfc_polling_loop_b_reader);
        } else if (nfcTech == (NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B)) {
            return context.getString(R.string.nfc_polling_loop_ab_reader);
        }
        return "error";
    }

    private static String getEmulatorString(Context context, int nfcTech, String customFrame) {
        if (customFrame != null) {
            return context.getString(R.string.nfc_polling_loop_custom_emulator);
        }
        if (nfcTech == NfcAdapter.FLAG_READER_NFC_A) {
            return context.getString(R.string.nfc_polling_loop_a_emulator);
        } else if (nfcTech == NfcAdapter.FLAG_READER_NFC_B) {
            return context.getString(R.string.nfc_polling_loop_b_emulator);
        } else if (nfcTech == (NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B)) {
            return context.getString(R.string.nfc_polling_loop_ab_emulator);
        }
        return "error";
    }

    private static String getEmulatorHelpString(Context context, int nfcTech, String customFrame) {
        if (customFrame != null) {
            return context.getString(R.string.nfc_polling_loop_custom_emulator_help);
        }
        if (nfcTech == NfcAdapter.FLAG_READER_NFC_A) {
            return context.getString(R.string.nfc_polling_loop_a_emulator_help);
        } else if (nfcTech == NfcAdapter.FLAG_READER_NFC_B) {
            return context.getString(R.string.nfc_polling_loop_a_emulator_help);
        } else if (nfcTech == (NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B)) {
            return context.getString(R.string.nfc_polling_loop_a_emulator_help);
        }
        return "error";
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNfcTech = getIntent().getIntExtra(NFC_TECH_KEY, NfcAdapter.FLAG_READER_NFC_A);
        mCustomFrame = getIntent().getStringExtra(NFC_CUSTOM_FRAME);
        NfcDialogs.createHceTapReaderDialog(this,
                    getEmulatorHelpString(this, mNfcTech, mCustomFrame)).show();
        setTitle(getEmulatorString(this, mNfcTech, mCustomFrame));
        mAdapter.setObserveModeEnabled(true);
        getPassButton().setEnabled(false);
        setupServices(this, PollingLoopService.COMPONENT);
        mCardEmulation.setPreferredService(this,
                new ComponentName(this.getApplicationContext(),
                        PollingLoopService.class));
        mNfcACount = 0;
        mNfcBCount = 0;
        mNfcOnCount = 0;
        mNfcOffCount = 0;
        mAllowedTransaction = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    void onServicesSetup(boolean result) {
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (mAllowedTransaction) {
            getPassButton().setEnabled(true);
        } else {
            getPassButton().setEnabled(false);
        }
    }

    final BroadcastReceiver mFieldStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PollingLoopService.POLLING_FRAME_ACTION)) {
                processPollingFrames(
                        intent.getParcelableArrayListExtra(PollingLoopService.POLLING_FRAME_EXTRA,
                                PollingFrame.class));
            }
        }
    };

    static Intent buildEmulatorIntent(Context context, int nfcTech, String customFrame) {
        Intent intent = buildEmulatorIntent(context, nfcTech);
        intent.putExtra(NFC_CUSTOM_FRAME, customFrame);
        return intent;
    }

    static Intent buildEmulatorIntent(Context context, int nfcTech) {
        Intent intent = new Intent(context, PollingLoopEmulatorActivity.class);
        intent.putExtra(NFC_TECH_KEY, nfcTech);
        return intent;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mNfcTech = intent.getIntExtra(NFC_TECH_KEY, NfcAdapter.FLAG_READER_NFC_A);
        setIntent(intent);
        setupServices(this, PollingLoopService.COMPONENT);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        mNfcTech = intent.getIntExtra(NFC_TECH_KEY, NfcAdapter.FLAG_READER_NFC_A);
        setupServices(this, PollingLoopService.COMPONENT);
    }

    void processPollingFrames(List<PollingFrame> frames) {
        for (PollingFrame frame : frames) {
            processPollingFrame(frame);
        }
        if (seenCorrectPollingLoop() && !mAllowedTransaction && mCustomFrame == null) {
            if (mAdapter.setObserveModeEnabled(false)) {
                mAllowedTransaction = true;
            }
        }
    }

    private boolean seenCorrectPollingLoop() {
        if (mNfcTech == NfcAdapter.FLAG_READER_NFC_A) {
            if (mNfcACount >= 3 && mNfcBCount == 0 && mNfcOnCount >= 3
                    && mNfcOffCount >= 3 && Math.abs(mNfcOffCount - mNfcOnCount) <= 1) {
                return true;
            }
        } else if (mNfcTech == NfcAdapter.FLAG_READER_NFC_B) {
            if (mNfcBCount >= 3 && mNfcACount == 0 && mNfcOnCount >= 3
                    && mNfcOffCount >= 3 && Math.abs(mNfcOffCount - mNfcOnCount) <= 1) {
                return true;
            }
        } else if (mNfcTech == (NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B)) {
            if (mNfcACount >= 3 && mNfcBCount >= 3 && mNfcOnCount >= 3
                    && mNfcOffCount >= 3 && Math.abs(mNfcOffCount - mNfcOnCount) <= 1) {
                return true;
            }
        }
        return false;
    }

    void processPollingFrame(PollingFrame frame) {
        int type = frame.getType();
        if (type == PollingFrame.POLLING_LOOP_TYPE_A) {
            mNfcACount++;
        } else if (type == PollingFrame.POLLING_LOOP_TYPE_B) {
            mNfcBCount++;
        } else if (type == PollingFrame.POLLING_LOOP_TYPE_ON) {
            mNfcOnCount++;
        } else if (type == PollingFrame.POLLING_LOOP_TYPE_OFF) {
            mNfcOffCount++;
        } else if (type == PollingFrame.POLLING_LOOP_TYPE_UNKNOWN) {
            if (mCustomFrame != null && !mAllowedTransaction) {
                byte[] passedData = frame.getData();
                if (mCustomFrame.equals(HexFormat.of().formatHex(passedData))) {
                    getPassButton().setEnabled(true);
                }
            }
        }
    }

    static Intent buildReaderIntent(Context context, int nfcTech) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_NFC_TECH, nfcTech);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PollingLoopService.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PollingLoopService.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL, getReaderString(context, nfcTech));
        return readerIntent;
    }
}
