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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccount;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/**
 * This class tests cases related to handling calls across multiple PhoneAccounts in the same app at
 * the same time and ensuring the operations are sequenced in the correct order as well as respect
 * any calling restrictions placed on the PhoneAccounts.
 */
@RunWith(JUnit4.class)
public class SimultaneousCallingTest extends BaseAppVerifier {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * This case sets up two PhoneAccounts registered by the same application with a
     * calling restriction (see {@link PhoneAccount#getSimultaneousCallingRestriction()}). This
     * restriction does not allow calls with another PhoneAccount registered by the same application
     * when this PhoneAccount is active with a call.
     * <p>
     * Test that when there is a call on one PhoneAccount, Telecom ensures that other PhoneAccounts
     * registered by the same application become ineligible for new outgoing calls. Instead, the
     * call will be placed using the default active PhoneAccount.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SIMULTANEOUS_CALLING_INDICATIONS)
    public void simultaneousCalling_MtMo_OneActiveAccountRestriction() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes incomingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                MANAGED_HANDLE_1, false /*isOutgoing*/);
        CallAttributes outgoingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                MANAGED_HANDLE_2, true /*isOutgoing*/);
        AppControlWrapper managedApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            // Temporarily set the default PhoneAccount override so that we do not hit
            // SELECT_PHONE_ACCOUNT and stall when MANAGED_HANDLE_2 becomes unavailable
            setUserDefaultPhoneAccountOverride(MANAGED_HANDLE_1);
            updateManagedPhoneAccountWithRestriction(MANAGED_HANDLE_1, Collections.emptySet());
            updateManagedPhoneAccountWithRestriction(MANAGED_HANDLE_2, Collections.emptySet());

            String mt = addCallAndVerify(managedApp, incomingAttributes);
            setCallStateAndVerify(managedApp, mt, STATE_ACTIVE);
            verifyCallPhoneAccount(mt, MANAGED_HANDLE_1);

            String mo = addCallAndVerify(managedApp, outgoingAttributes);
            // Verify that the call was placed over MANAGED_HANDLE_1 because MANAGED_HANDLE_2
            // is not available due to the current calling restriction.
            verifyCallPhoneAccount(mo, MANAGED_HANDLE_1);

            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
        }
    }
}
