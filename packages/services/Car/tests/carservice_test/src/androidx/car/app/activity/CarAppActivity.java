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

package androidx.car.app.activity;


import static com.android.car.pm.CarPackageManagerServiceTest.DoActivity.DIALOG_TITLE;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.car.pm.CarPackageManagerServiceTest;

/**
 * An activity to represent a template activity in tests.
 */
public class CarAppActivity extends CarPackageManagerServiceTest.TempActivity {
    public static final String ACTION_SHOW_DIALOG = "SHOW_DIALOG";
    public static final String ACTION_START_SECOND_INSTANCE = "START_SECOND_INSTANCE";
    public static final String SECOND_INSTANCE_TITLE = "Second Instance";
    private static final String BUNDLE_KEY_IS_SECOND_INSTANCE = "is_second_instance";

    private final ShowDialogReceiver mShowDialogReceiver = new ShowDialogReceiver();
    private final StartSecondInstanceReceiver
            mStartSecondInstanceReceiver = new StartSecondInstanceReceiver();

    private class ShowDialogReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            showDialog();
        }
    }

    private class StartSecondInstanceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            startSecondInstance();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(BUNDLE_KEY_IS_SECOND_INSTANCE, false)) {
            getActionBar().setTitle(SECOND_INSTANCE_TITLE);
        }
        this.registerReceiver(mShowDialogReceiver, new IntentFilter(ACTION_SHOW_DIALOG),
                Context.RECEIVER_NOT_EXPORTED);
        this.registerReceiver(mStartSecondInstanceReceiver,
                new IntentFilter(ACTION_START_SECOND_INSTANCE), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mShowDialogReceiver);
        this.unregisterReceiver(mStartSecondInstanceReceiver);
    }

    private void startSecondInstance() {
        Intent intent = new Intent(CarAppActivity.this, CarAppActivity.class);
        intent.putExtra(BUNDLE_KEY_IS_SECOND_INSTANCE, true);
        startActivity(intent);
    }

    private void showDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(DIALOG_TITLE)
                .setMessage("Message")
                .create();
        dialog.show();
    }
}
