/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts.appthatrequestcustomcamerapermission;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class RequestCameraPermission extends Activity {
    private static final String LOG_TAG = RequestCameraPermission.class.getSimpleName();

    public static final String CUSTOM_PERMISSION = "appthatrequestcustomcamerapermission.CUSTOM";
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            Log.w(LOG_TAG, "Activity was recreated. (Perhaps due to a configuration change?)");
            return;
        }

        boolean cameraGranted =
                checkSelfPermission(CAMERA) == PERMISSION_GRANTED;
        boolean customGranted =
                checkSelfPermission(CUSTOM_PERMISSION) == PERMISSION_GRANTED;

        mHandler = new Handler(getMainLooper());

        if (!cameraGranted && !customGranted) {
            requestPermissions(new String[] {CAMERA}, 0);
        } else {
            Log.e(LOG_TAG, "Test app was opened with cameraGranted=" + cameraGranted
                    + " and customGranted=" + customGranted);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0) {
            if (grantResults[0] != PERMISSION_GRANTED) {
                Log.e(LOG_TAG, "permission wasn't granted, this test should fail,"
                        + " leaving test app open.");
            } else {
                // Delayed request because the immediate request might show the dialog again
                mHandler.postDelayed(() ->
                        requestPermissions(new String[] {CUSTOM_PERMISSION}, 1), 500);
            }
        } else if (requestCode == 1) {
            if (grantResults[0] != PERMISSION_GRANTED) {
                Log.e(LOG_TAG, "permission wasn't granted, this test should fail,"
                        + " leaving test app open.");
            } else {
                // Here camera was granted and custom was autogranted, exit process and let test
                // verify both are revoked.

                // Delayed exit because b/254675301
                mHandler.postDelayed(() -> System.exit(0), 1000);
            }
        }

    }
}
