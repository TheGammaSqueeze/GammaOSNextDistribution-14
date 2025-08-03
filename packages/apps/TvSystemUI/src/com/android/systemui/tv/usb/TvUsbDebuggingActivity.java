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

package com.android.systemui.tv.usb;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.debug.IAdbManager;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Space;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.broadcast.BroadcastDispatcher;

import com.android.systemui.tv.TvBottomSheetActivity;
import com.android.systemui.tv.res.R;

import javax.inject.Inject;

public class TvUsbDebuggingActivity extends TvBottomSheetActivity implements View.OnClickListener {
    private static final String TAG = TvUsbDebuggingActivity.class.getSimpleName();

    private UsbDisconnectedReceiver mDisconnectedReceiver;
    private final BroadcastDispatcher mBroadcastDispatcher;

    private String mKey;
    private String mFingerprints;
    private boolean mServiceNotified;

    @Inject
    public TvUsbDebuggingActivity(BroadcastDispatcher broadcastDispatcher) {
        super();
        mBroadcastDispatcher = broadcastDispatcher;
    }

    @Override
    public final void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().addPrivateFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        // Emulator does not support reseating the usb cable to reshow the dialog.
        if (SystemProperties.getInt("service.adb.tcp.port", 0) == 0 && !Build.IS_EMULATOR) {
            mDisconnectedReceiver = new UsbDisconnectedReceiver(this);
            IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_STATE);
            mBroadcastDispatcher.registerReceiver(mDisconnectedReceiver, filter);
        }

        Intent intent = getIntent();

        mFingerprints = intent.getStringExtra("fingerprints");
        mKey = intent.getStringExtra("key");

        if (mFingerprints == null || mKey == null) {
            finish();
        }
    }

    private class UsbDisconnectedReceiver extends BroadcastReceiver {
        private final Activity mActivity;
        UsbDisconnectedReceiver(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (!UsbManager.ACTION_USB_STATE.equals(action)) {
                return;
            }
            boolean connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
            if (!connected) {
                Log.d(TAG, "USB disconnected, notifying service");
                notifyService(false);
                mActivity.finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mDisconnectedReceiver != null) {
            mBroadcastDispatcher.unregisterReceiver(mDisconnectedReceiver);
        }

        if (isFinishing()) {
            if (!mServiceNotified) {
                notifyService(false);
            }
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        TextView titleTextView = findViewById(R.id.bottom_sheet_title);
        TextView contentTextView = findViewById(R.id.bottom_sheet_body);
        ImageView icon = findViewById(R.id.bottom_sheet_icon);
        ImageView secondIcon = findViewById(R.id.bottom_sheet_second_icon);
        Button okButton = findViewById(R.id.bottom_sheet_positive_button);
        Button cancelButton = findViewById(R.id.bottom_sheet_negative_button);
        Button alwaysButton = findViewById(R.id.bottom_sheet_always_button);
        Space alwaysSpace = findViewById(R.id.bottom_sheet_always_space);

        String content = getString(com.android.systemui.res.R.string
                .usb_debugging_message, mFingerprints);

        titleTextView.setText(com.android.systemui.res.R.string.usb_debugging_title);
        contentTextView.setText(content);

        icon.setImageResource(com.android.internal.R.drawable.ic_usb_48dp);
        secondIcon.setVisibility(View.GONE);
        okButton.setText(com.android.systemui.res.R.string.usb_debugging_allow);
        okButton.setOnClickListener(this);

        alwaysSpace.setVisibility(View.VISIBLE);
        alwaysButton.setVisibility(View.VISIBLE);
        alwaysButton.setText(com.android.systemui.res.R.string.usb_debugging_always);
        alwaysButton.setOnClickListener(this);

        cancelButton.setText(android.R.string.cancel);
        cancelButton.setOnClickListener(this);
        cancelButton.requestFocus();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bottom_sheet_positive_button) {
            notifyService(true);
        } else if (v.getId() == R.id.bottom_sheet_always_button) {
            notifyService(true, true);
        } else {
            notifyService(false);
        }
        finish();
    }

    private void notifyService(boolean allow) {
        notifyService(allow, false);
    }

    private void notifyService(boolean allow, boolean alwaysAllow) {
        try {
            IBinder b = ServiceManager.getService(ADB_SERVICE);
            IAdbManager service = IAdbManager.Stub.asInterface(b);
            if (allow) {
                service.allowDebugging(alwaysAllow, mKey);
            } else {
                service.denyDebugging();
            }
            mServiceNotified = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to notify Usb service", e);
        }
    }
}
