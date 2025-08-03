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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.companion.ICompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.ServiceManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_0022 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 298635078)
    @Test
    public void testPocCVE_2024_0022() {
        try {
            ICompanionDeviceManager service =
                    ICompanionDeviceManager.Stub.asInterface(
                            ServiceManager.getServiceOrThrow(Context.COMPANION_DEVICE_SERVICE));

            Context context = getApplicationContext();

            // Call requestNotificationAccess with non current user id.
            // With fix, a SecurityException will be thrown.
            // Without fix, an IllegalStateException will be thrown
            try {
                service.requestNotificationAccess(
                        new ComponentName(context.getPackageName(), "CVE_2024_0022"),
                        context.getUserId() + 1);
            } catch (Exception e) {
                if (e instanceof SecurityException) {
                    if (!e.getMessage().contains("does not match")) {
                        throw e;
                    }
                }

                if (e instanceof IllegalStateException) {
                    if (e.getMessage()
                            .contains("App must have an association before calling this API")) {
                        assertWithMessage(
                                        "Device is vulnerable to b/298635078, notification access"
                                            + " can be requested and set on behalf of another user"
                                            + " profile via requestNotificationAccess() of"
                                            + " CompanionDeviceManagerService.")
                                .fail();
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
