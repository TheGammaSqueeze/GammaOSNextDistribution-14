/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.bluetooth.AlertActivity;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;
import com.android.internal.annotations.VisibleForTesting;

/** This class is designed to ask user to confirm if accept incoming file; */
// Next tag value for ContentProfileErrorReportUtils.report(): 1
public class BluetoothOppIncomingFileConfirmActivity extends AlertActivity {
    private static final String TAG = "BluetoothIncomingFileConfirmActivity";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    @VisibleForTesting
    static final int DISMISS_TIMEOUT_DIALOG = 0;

    @VisibleForTesting
    static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;

    private static final String PREFERENCE_USER_TIMEOUT = "user_timeout";

    private BluetoothOppTransferInfo mTransInfo;

    private Uri mUri;

    private ContentValues mUpdateValues;

    private boolean mTimeout = false;

    private BroadcastReceiver mReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Material_Settings_Floating);
        if (V) {
            Log.d(TAG, "onCreate(): action = " + getIntent().getAction());
        }
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        Intent intent = getIntent();
        mUri = intent.getData();
        mTransInfo = new BluetoothOppTransferInfo();
        mTransInfo = BluetoothOppUtility.queryRecord(this, mUri);
        if (mTransInfo == null) {
            if (V) {
                Log.e(TAG, "Error: Can not get data from db");
            }
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.OPP,
                    BluetoothProtoEnums.BLUETOOTH_OPP_INCOMING_FILE_CONFIRM_ACTIVITY,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    0);
            finish();
            return;
        }

        mAlertBuilder.setTitle(getString(R.string.incoming_file_confirm_content));
        mAlertBuilder.setView(createView());
        mAlertBuilder.setPositiveButton(R.string.incoming_file_confirm_ok,
                (dialog, which) -> onIncomingFileConfirmOk());
        mAlertBuilder.setNegativeButton(R.string.incoming_file_confirm_cancel,
                (dialog, which) -> onIncomingFileConfirmCancel());

        setupAlert();
        if (V) {
            Log.v(TAG, "mTimeout: " + mTimeout);
        }
        if (mTimeout) {
            onTimeout();
        }

        if (V) {
            Log.v(TAG, "BluetoothIncomingFileConfirmActivity: Got uri:" + mUri);
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION.equals(intent.getAction())) {
                    return;
                }
                onTimeout();
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mReceiver, filter);
    }

    private View createView() {
        View view = getLayoutInflater().inflate(R.layout.incoming_dialog, null);

        ((TextView) view.findViewById(R.id.from_content)).setText(mTransInfo.mDeviceName);
        String fileName = mTransInfo.mFileName;
        if (fileName != null) {
            fileName = fileName
                    .replace('\t', ' ')
                    .replace('\n', ' ')
                    .replace('\r', ' ');
        }
        ((TextView) view.findViewById(R.id.filename_content)).setText(fileName);
        ((TextView) view.findViewById(R.id.size_content)).setText(
                Formatter.formatFileSize(this, mTransInfo.mTotalBytes));

        return view;
    }

    private void onIncomingFileConfirmOk() {
        if (!mTimeout) {
            // Update database
            mUpdateValues = new ContentValues();
            mUpdateValues.put(BluetoothShare.USER_CONFIRMATION,
                    BluetoothShare.USER_CONFIRMATION_CONFIRMED);
            BluetoothMethodProxy.getInstance().contentResolverUpdate(this.getContentResolver(),
                    mUri, mUpdateValues, null, null);

            Toast.makeText(this, getString(R.string.bt_toast_1), Toast.LENGTH_SHORT).show();
        }
    }

    private void onIncomingFileConfirmCancel() {
        // Update database
        mUpdateValues = new ContentValues();
        mUpdateValues.put(BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.USER_CONFIRMATION_DENIED);
        BluetoothMethodProxy.getInstance().contentResolverUpdate(this.getContentResolver(),
                mUri, mUpdateValues, null, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (D) {
                Log.d(TAG, "onKeyDown() called; Key: back key");
            }
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTimeout = savedInstanceState.getBoolean(PREFERENCE_USER_TIMEOUT);
        if (V) {
            Log.v(TAG, "onRestoreInstanceState() mTimeout: " + mTimeout);
        }
        if (mTimeout) {
            onTimeout();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (V) {
            Log.v(TAG, "onSaveInstanceState() mTimeout: " + mTimeout);
        }
        outState.putBoolean(PREFERENCE_USER_TIMEOUT, mTimeout);
    }

    private void onTimeout() {
        mTimeout = true;

        changeTitle(getString(
                R.string.incoming_file_confirm_timeout_content,
                mTransInfo.mDeviceName));
        changeButtonVisibility(DialogInterface.BUTTON_NEGATIVE, View.GONE);
        changeButtonText(
                DialogInterface.BUTTON_POSITIVE,
                getString(R.string.incoming_file_confirm_timeout_ok));

        BluetoothMethodProxy.getInstance().handlerSendMessageDelayed(mTimeoutHandler,
                DISMISS_TIMEOUT_DIALOG, DISMISS_TIMEOUT_DIALOG_VALUE);
    }

    private final Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS_TIMEOUT_DIALOG:
                    if (V) {
                        Log.v(TAG, "Received DISMISS_TIMEOUT_DIALOG msg.");
                    }
                    finish();
                    break;
                default:
                    break;
            }
        }
    };
}
