/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.settings;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import com.google.android.tv.btservices.BluetoothDeviceService;
import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.SimplifiedConnection;

public class RemoteDfuConfirmationActivity extends Activity implements ResponseFragment.Listener {

    public static final String EXTRA_BT_ADDRESS = "extra_bt_address";
    public static final String EXTRA_BT_NAME= "extra_bt_name";

    private static final String KEY_UPDATE = "update";
    private static final int CONTINUE = R.string.settings_continue;
    private static final int CANCEL = R.string.settings_cancel;
    private static final int[] CONT_CANCEL_ARGS = {CONTINUE, CANCEL};
    private static final String FRAGMENT_TAG = "dfu_confirm";

    private final Handler mHandler = new Handler();
    private boolean mBtDeviceServiceBound;
    private BluetoothDeviceService.LocalBinder mBtDeviceServiceBinder;

    private final ServiceConnection mBtDeviceServiceConnection = new SimplifiedConnection() {

        @Override
        protected void cleanUp() {
            mBtDeviceServiceBound = false;
            mBtDeviceServiceBinder = null;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBtDeviceServiceBinder = (BluetoothDeviceService.LocalBinder) service;
            mBtDeviceServiceBound = true;
            mHandler.post(RemoteDfuConfirmationActivity.this::show);
        }
    };

    private void show() {
        FragmentManager fm = getFragmentManager();
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ResponseFragment();
            Bundle args = new Bundle();
            ResponseFragment.prepareArgs(
                    args,
                    KEY_UPDATE,
                    R.string.settings_bt_update,
                    R.string.settings_bt_update_summary,
                    0,
                    CONT_CANCEL_ARGS,
                    null,
                    ResponseFragment.DEFAULT_CHOICE_UNDEFINED
            );
            fragment.setArguments(args);
            FragmentTransaction transact = fm.beginTransaction();
            transact.add(android.R.id.content, fragment, FRAGMENT_TAG);
            transact.commit();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(this, BluetoothUtils.getBluetoothDeviceServiceClass(this)),
                mBtDeviceServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onChoice(String key, int choice) {
        final Intent origIntent = getIntent();
        final String address = origIntent.getStringExtra(EXTRA_BT_ADDRESS);
        if (choice == CONTINUE) {
            Intent intent = new Intent(this, RemoteDfuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(EXTRA_BT_ADDRESS, address);
            startActivity(intent);
            finish();
        } else if (choice == CANCEL) {
            if (mBtDeviceServiceBinder != null) {
                mBtDeviceServiceBinder.dismissDfuNotification(address);
            }
            finish();
        }
    }

    @Override
    public void onText(String key, String text) {}

    @Override
    public void onDestroy() {
        if (mBtDeviceServiceBound) {
            unbindService(mBtDeviceServiceConnection);
        }
        super.onDestroy();
    }
}
