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

package android.security.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.location.LocationManager;
import android.os.PackageTagsList;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LocationDisabledAppOpsTest extends StsExtraBusinessLogicTestCase {

    private final Context mContext = InstrumentationRegistry.getContext();
    private LocationManager mLm;
    private AppOpsManager mAom;

    @Before
    public void setUp() {
        mLm = mContext.getSystemService(LocationManager.class);
        mAom = mContext.getSystemService(AppOpsManager.class);
    }

    @Test
    @AsbSecurityTest(cveBugId = 231496105)
    public void testLocationAppOpIsIgnoredForAppsWhenLocationIsDisabled() {
        PackageTagsList ignoreList = mLm.getIgnoreSettingsAllowlist();

        UserHandle[] userArr = {UserHandle.SYSTEM};
        runWithShellPermissionIdentity(() -> {
            userArr[0] = UserHandle.of(ActivityManager.getCurrentUser());
        });

        UserHandle user = userArr[0];

        boolean wasEnabled = mLm.isLocationEnabledForUser(user);

        try {
            runWithShellPermissionIdentity(() -> {
                mLm.setLocationEnabledForUser(false, user);
            });
            List<PackageInfo> pkgs =
                    mContext.getPackageManager().getInstalledPackagesAsUser(
                            0, user.getIdentifier());

            eventually(() -> {
                List<String> bypassedNoteOps = new ArrayList<>();
                List<String> bypassedCheckOps = new ArrayList<>();
                for (PackageInfo pi : pkgs) {
                    ApplicationInfo ai = pi.applicationInfo;
                    int appId = UserHandle.getAppId(ai.uid);
                    if (appId != Process.SYSTEM_UID) {
                        final int[] mode = {MODE_ALLOWED};
                        runWithShellPermissionIdentity(() -> {
                            mode[0] = mAom.noteOpNoThrow(
                                    OPSTR_FINE_LOCATION, ai.uid, ai.packageName);
                        });
                        if (mode[0] == MODE_ALLOWED && !ignoreList.containsAll(pi.packageName)) {
                            bypassedNoteOps.add(pi.packageName);
                        }


                        mode[0] = MODE_ALLOWED;
                        runWithShellPermissionIdentity(() -> {
                            mode[0] = mAom
                                    .checkOpNoThrow(OPSTR_FINE_LOCATION, ai.uid, ai.packageName);
                        });
                        if (mode[0] == MODE_ALLOWED && !ignoreList.includes(pi.packageName)) {
                            bypassedCheckOps.add(pi.packageName);
                        }

                    }
                }

                String msg = "";
                if (!bypassedNoteOps.isEmpty()) {
                    msg += "Apps which still have access from noteOp " + bypassedNoteOps;
                }
                if (!bypassedCheckOps.isEmpty()) {
                    msg += (msg.isEmpty() ? "" : "\n\n")
                            +  "Apps which still have access from checkOp " + bypassedCheckOps;
                }
                if (!msg.isEmpty()) {
                    Assert.fail(msg);
                }
            });
        } finally {
            runWithShellPermissionIdentity(() -> {
                mLm.setLocationEnabledForUser(wasEnabled, user);
            });
        }
    }

}

