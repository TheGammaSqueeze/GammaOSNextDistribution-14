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

package android.security.cts.CVE_2022_20129;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.telecom.PhoneAccount.CAPABILITY_CALL_PROVIDER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.platform.test.annotations.AsbSecurityTest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20129 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 217934478)
    @Test
    public void testPocCVE_2022_20129() {
        // maxPhoneAccounts = 10 * MAX_PHONE_ACCOUNT_REGISTRATIONS to accommodate code customization
        final int maxPhoneAccounts = 100;
        boolean isVulnerable = false;
        int phoneAccounts = 0;
        ArrayList<PhoneAccountHandle> phoneAccountHandleList = new ArrayList<PhoneAccountHandle>();
        Context context = null;
        TelecomManager telecomManager = null;
        UiAutomation uiAutomation = null;
        try {
            Instrumentation instrumentation = getInstrumentation();
            context = instrumentation.getContext();
            telecomManager = context.getSystemService(TelecomManager.class);
            uiAutomation = instrumentation.getUiAutomation();

            // Store the number of phoneaccounts present before test run
            uiAutomation.adoptShellPermissionIdentity(MODIFY_PHONE_STATE);
            phoneAccounts = telecomManager.getAllPhoneAccountHandles().size();

            // Create and register 'maxPhoneAccounts' phoneAccounts
            String packageName = context.getPackageName();
            for (int i = 0; i < maxPhoneAccounts; ++i) {
                PhoneAccountHandle handle =
                        new PhoneAccountHandle(
                                new ComponentName(packageName, PocService.class.getName()),
                                packageName + i);
                PhoneAccount account =
                        new PhoneAccount.Builder(handle, packageName + i)
                                .setCapabilities(CAPABILITY_CALL_PROVIDER)
                                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                                .build();
                phoneAccountHandleList.add(handle);
                try {
                    telecomManager.registerPhoneAccount(account);
                    isVulnerable = true;

                    // Enable phoneAccount after registering so that it can be retrieved later using
                    // getAllPhoneAccountHandles, making the test reliable
                    telecomManager.enablePhoneAccount(
                            account.getAccountHandle(), true /* isEnabled */);
                } catch (IllegalArgumentException expected) {
                    // This exception is expected with fix. Exception message isn't checked to
                    // accommodate code customizations
                    isVulnerable = false;
                    break;
                }
            }
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Retrieve the total number of phoneAccounts added by test
                phoneAccounts = telecomManager.getAllPhoneAccountHandles().size() - phoneAccounts;
                boolean allAccountsAdded = maxPhoneAccounts == phoneAccounts;

                // Unregister all phoneaccounts used in test
                for (PhoneAccountHandle handle : phoneAccountHandleList) {
                    telecomManager.unregisterPhoneAccount(handle);
                }
                uiAutomation.dropShellPermissionIdentity();

                // Test fails only if 'maxPhoneAccounts' phoneAccounts were added successfully in
                // test and no exception occurred
                assertFalse(
                        "Device is vulnerable to b/217934478!!", isVulnerable && allAccountsAdded);
            } catch (Exception ignored) {
                // Ignore exceptions here
            }
        }
    }
}
