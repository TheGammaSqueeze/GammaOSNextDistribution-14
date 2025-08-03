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

package android.telecom.cts;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

/**
 * CTS tests to ensure that a ConnectionService which returns a null binding will be automatically
 * unbound.
 */

public class NullBindingTest extends BaseTelecomTestWithMockServices {
    private static final PhoneAccountHandle TEST_NULL_BINDING_HANDLE =
            new PhoneAccountHandle(new ComponentName("android.telecom.cts",
                    "android.telecom.cts.NullBindingConnectionService"),
                    "1");

    public static final PhoneAccount TEST_NULL_BINDING_ACCOUNT = PhoneAccount.builder(
                    TEST_NULL_BINDING_HANDLE, "Null")
            .setAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .build();

    private static final Uri TEST_ADDRESS_1 = Uri.fromParts("sip", "call1@test.com", null);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (mShouldTestTelecom) {
            mTelecomManager.registerPhoneAccount(TEST_NULL_BINDING_ACCOUNT);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            mTelecomManager.unregisterPhoneAccount(TEST_NULL_BINDING_HANDLE);
        }
        super.tearDown();
    }

    /**
     * Ensures that when we bind to a ConnectionService which returns a null binding that the
     * ConnectionService is unbound automatically.
     */
    public void testNullBinding() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Place a call using the null binding connection service.
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_NULL_BINDING_HANDLE);
        mTelecomManager.placeCall(TEST_ADDRESS_1, extras);

        // Ensure it bound and then unbound.
        assertTrue(TestUtils.waitForLatchCountDown(NullBindingConnectionService.sBindLatch));
        assertTrue(TestUtils.waitForLatchCountDown(NullBindingConnectionService.sUnbindLatch));

        // Ensure there is no call present in Telecom
        assertFalse(mTelecomManager.isInCall());
    }
}
