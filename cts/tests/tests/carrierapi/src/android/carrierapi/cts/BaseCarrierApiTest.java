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

package android.carrierapi.cts;

import static com.android.compatibility.common.util.UiccUtil.UiccCertificate.CTS_UICC_LEGACY;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.UiccUtil;

import org.junit.Before;

/**
 * Common test base to ensure uniform preconditions checking. This class will check for:
 *
 * <ol>
 *   <li>{@link android.content.pm.PackageManager#FEATURE_TELEPHONY}
 *   <li>A SIM that grants us carrier privileges is currently active in the device
 * </ol>
 *
 * Just inherit from this class when writing your test, then you are able to assume in the subclass
 * {@code Before} method that preconditions have all passed. The setup and test methods will not be
 * executed if preconditions are not met.
 */
public abstract class BaseCarrierApiTest {
    protected static final String NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE =
            "This test requires a SIM card with carrier privilege rules on it.\n"
                    + "Visit https://source.android.com/devices/tech/config/uicc.html";
    // More specific message when the test suite detects an outdated legacy SIM.
    private static final String DEPRECATED_TEST_SIM_FAILURE_MESSAGE =
            "This test requires a 2021-compliant SIM card with carrier privilege rules on it.\n"
                + "The current SIM card appears to be outdated and is not compliant with the 2021"
                + " CTS SIM specification published with Android 12 (\"S\").\n"
                + "As of Android 13 (\"T\"), you must use a 2021-compliant SIM card to pass this"
                + " suite. The 2021-compliant SIM is backward compatible with the legacy"
                + " specification, so it may also be used to run this suite on older Android"
                + " releases.\n"
                + "2021-compliant SIMs received directly from Google have \"2021 CTS\" printed on"
                + " them.\n"
                + "Visit https://source.android.com/devices/tech/config/uicc#prepare_uicc";

    protected Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private boolean mPreconditionsSatisfied = false;

    protected boolean werePreconditionsSatisfied() {
        return mPreconditionsSatisfied;
    }

    /**
     * Subclasses do NOT need to explicitly call or override this method. Per the JUnit docs, a
     * superclass {@code Before} method always executes before a subclass {@code Before} method.
     *
     * <p>If preconditions fail, neither the subclass {@code Before} method(s) nor the actual {@code
     * Test} method will execute, but {@code After} methods will still execute. If a subclass does
     * work in an {@code After} method, then it should first check {@link
     * #werePreconditionsSatisfied} and return early without doing any work if it's {@code false}.
     */
    @Before
    public void ensurePreconditionsMet() {
        mPreconditionsSatisfied = false;
        // Bail out if no cellular support.
        assumeTrue(
                "No cellular support, CarrierAPI."
                        + getClass().getSimpleName()
                        + " cases will be skipped",
                FeatureUtil.hasTelephony());

        // Wait for the broadcast queue to be handled completely to make sure
        // CarrierPrivilegesTracker has received the ACTION_PACKAGE_ADDED for this test package and
        // updated the carrier privileges in advance to avoid flakiness.
        AmUtils.waitForBroadcastBarrier();

        // We must run with carrier privileges. As of 2022, all devices must run CTS with a SIM
        // compliant with the 2021 spec, which has a new certificate. To make results very clear, we
        // still explicitly check for the legacy certificate, and if we don't have carrier
        // privileges but detect the legacy cert, we tell the tester they must upgrade to pass this
        // test suite.
        assertWithMessage(
                        UiccUtil.uiccHasCertificate(CTS_UICC_LEGACY)
                                ? DEPRECATED_TEST_SIM_FAILURE_MESSAGE
                                : NO_CARRIER_PRIVILEGES_FAILURE_MESSAGE)
                .that(getContext().getSystemService(TelephonyManager.class).hasCarrierPrivileges())
                .isTrue();
        mPreconditionsSatisfied = true;
    }
}
