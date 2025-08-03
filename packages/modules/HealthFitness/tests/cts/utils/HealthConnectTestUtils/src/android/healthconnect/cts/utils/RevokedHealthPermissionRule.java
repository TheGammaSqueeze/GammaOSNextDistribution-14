/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.healthconnect.cts.utils;

import static android.healthconnect.cts.utils.PermissionHelper.getGrantedHealthPermissions;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import org.junit.rules.ExternalResource;

/** Revokes permission for the given package for the duration of the test. */
public class RevokedHealthPermissionRule extends ExternalResource {

    private final String mPermission;
    private final String mPackageName;
    private boolean mIsPermissionGranted;

    public RevokedHealthPermissionRule(String packageName, String permission) {
        this.mPermission = permission;
        this.mPackageName = packageName;
    }

    @Override
    protected void before() throws Throwable {
        var grantedPermissions = getGrantedHealthPermissions(mPackageName);

        mIsPermissionGranted = grantedPermissions.contains(mPermission);

        if (mIsPermissionGranted) {
            getInstrumentation()
                    .getUiAutomation()
                    .revokeRuntimePermission(mPackageName, mPermission);
        }
    }

    @Override
    protected void after() {
        if (mIsPermissionGranted) {
            getInstrumentation()
                    .getUiAutomation()
                    .grantRuntimePermission(mPackageName, mPermission);
        }
    }
}
