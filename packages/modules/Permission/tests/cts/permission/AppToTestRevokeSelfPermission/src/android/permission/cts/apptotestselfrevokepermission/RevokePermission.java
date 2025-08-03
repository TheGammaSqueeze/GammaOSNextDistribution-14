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

package android.permission.cts.apptotestrevokeselfpermission;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class RevokePermission extends Activity {
    private static final String TAG = "RevokePermission";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            Log.w(TAG, "Activity was recreated. (Perhaps due to a configuration change?)");
            return;
        }

        Intent intent = getIntent();
        String[] permissions = intent.getStringArrayExtra("permissions");
        if (permissions == null) {
            return;
        }
        if (permissions.length == 1) {
            getApplicationContext().revokeSelfPermissionOnKill(permissions[0]);
        } else {
            getApplicationContext().revokeSelfPermissionsOnKill(Arrays.asList(permissions));
        }
    }
}
