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

package com.android.cts.verifier.capturecontentfornotes;

import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminTestReceiver.DEVICE_OWNER_PKG;
import static com.android.cts.verifier.capturecontentfornotes.DeviceAdminTestReceiver.RECEIVER_COMPONENT_NAME;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.cts.verifier.R;

/** Helper activity to execute device admin policy commands. */
public class DeviceAdminCommandExecutorActivity extends Activity {

    private static final String TAG = DeviceAdminTestReceiver.class.getSimpleName();

    static final String ACTION =
            "com.android.cts.verifier.capturecontentfornotes.action.EXECUTE_COMMAND";
    static final String EXTRA_COMMAND = "EXTRA_COMMAND";
    static final String COMMAND_DISABLE_SCREEN_CAPTURE = "COMMAND_DISABLE_SCREEN_CAPTURE";
    static final String COMMAND_ENABLE_SCREEN_CAPTURE = "COMMAND_ENABLE_SCREEN_CAPTURE";
    static final String COMMAND_CLEAR_DEVICE_OWNER = "COMMAND_CLEAR_DEVICE_OWNER";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        int toastMsgId = R.string.no_command_run_toast;

        try {
            DevicePolicyManager dpm =
                    TestAppSystemServiceFactory.getDevicePolicyManager(this,
                            DeviceAdminTestReceiver.class, true);

            String command = intent.getStringExtra(EXTRA_COMMAND);
            switch (command) {
                case COMMAND_DISABLE_SCREEN_CAPTURE:
                    dpm.setScreenCaptureDisabled(RECEIVER_COMPONENT_NAME, true);
                    toastMsgId = R.string.disable_screenshot_toast;
                    break;
                case COMMAND_ENABLE_SCREEN_CAPTURE:
                    dpm.setScreenCaptureDisabled(RECEIVER_COMPONENT_NAME, false);
                    toastMsgId = R.string.enable_screenshot_toast;
                    break;
                case COMMAND_CLEAR_DEVICE_OWNER:
                    dpm.clearDeviceOwnerApp(DEVICE_OWNER_PKG);
                    toastMsgId = R.string.clear_device_owner_toast;
                    break;
                default:
                    //Do nothing, all cases covered.
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute command: " + intent, e);
            toastMsgId = R.string.admin_policy_setup_failed;
        } finally {
            Toast.makeText(this, toastMsgId, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
