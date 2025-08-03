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
package android.carrierapi.cts.targetprep;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.UiccUtil.ApduCommand;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CsimRemover {

    private static final String TAG = "CsimRemover";

    /**
     * APDUs to remove the CSIM record from EF_dir.
     *
     * <p>Given the general move away from CDMA, we do *not* have an equivalent postcondition to
     * restore CSIM, though you can accomplish this by changing the final APDU's data to {@code
     * "61184F10A0000003431002F310FFFF89020000FF50044353494DFFFFFFFFFFFFFF"}.
     */
    private static final List<ApduCommand> REMOVE_CSIM_SCRIPT =
            List.of(
                    // Verify ADM
                    new ApduCommand(0x00, 0x20, 0x00, 0x0A, 0x08, "3535353535353535"),
                    // Select MF
                    new ApduCommand(0x00, 0xA4, 0x00, 0x0C, 0x02, "3F00"),
                    // Select EF_dir
                    new ApduCommand(0x00, 0xA4, 0x00, 0x0C, 0x02, "2F00"),
                    // Overwrite CSIM record with all FF bytes
                    new ApduCommand(0x00, 0xDC, 0x03, 0x04, 0x21, "FF".repeat(0x21)));

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Before
    public void ensurePreconditionsMet() {
        // Bail out if no cellular support.
        assumeTrue("No cellular support, CSIM removal will be skipped", FeatureUtil.hasTelephony());
        // Since this APK is signed with the 2021 CTS SIM's certificate, we assume that if we don't
        // have carrier privileges, we shouldn't be doing anything. This APDU script is not
        // guaranteed to work on the "legacy" CTS SIM since there's no strict spec for its content.
        assumeTrue(
                "No carrier privileges, CSIM removal will be skipped",
                getContext().getSystemService(TelephonyManager.class).hasCarrierPrivileges());
    }

    /** Removes CSIM from the UICC if it's present. */
    @Test
    public void removeCsim() throws Exception {
        int subId = SubscriptionManager.getDefaultSubscriptionId();
        assumeTrue("No CSIM detected, CSIM removal will be skipped", isCsimPresent(subId));

        Log.i(TAG, "Removing CSIM applet record");
        ApduScriptUtil.runApduScript(subId, REMOVE_CSIM_SCRIPT);

        // The script will internally wait for the SIM to power back up.

        // Additionally, TC Should wait until loading CarrierPrivileges successfully
        // in CarrierPrivileagesTracker
        PollingCheck.waitFor(5000, () -> getContext().getSystemService(TelephonyManager.class)
                .hasCarrierPrivileges(),"Timeout when waiting to gain carrier privileges again.");

        assertWithMessage("Carrier privileges not restored after executing CSIM removal script")
                .that(getContext().getSystemService(TelephonyManager.class).hasCarrierPrivileges())
                .isTrue();
        assertWithMessage("CSIM still detected, CSIM removal failed")
                .that(isCsimPresent(subId))
                .isFalse();
    }

    private boolean isCsimPresent(int subId) {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                getContext()
                        .getSystemService(TelephonyManager.class)
                        .createForSubscriptionId(subId),
                tm -> tm.isApplicationOnUicc(TelephonyManager.APPTYPE_CSIM),
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }
}
