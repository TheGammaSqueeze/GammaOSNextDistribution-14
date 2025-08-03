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

package android.telephony.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.android.compatibility.common.util.PropertyUtil.getVendorApiLevel;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for telephony related feature flags defined in {@link android.content.pm.PackageManager}
 */
public final class TelephonyFeatureFlagsTest {

    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        assumeTrue(getVendorApiLevel() > Build.VERSION_CODES.S);
        mPackageManager = getContext().getPackageManager();
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    @Test
    public void testFeatureFlagsValidation() throws Exception {
        boolean hasFeatureTelecom = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELECOM);
        boolean hasFeatureTelephony = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        boolean hasFeatureCalling = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CALLING);
        boolean hasFeatureCarrierLock = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CARRIERLOCK);
        boolean hasFeatureCdma = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CDMA);
        boolean hasFeatureData = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_DATA);
        boolean hasFeatureEuicc = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_EUICC);
        boolean hasFeatureEuiccMep = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_EUICC_MEP);
        boolean hasFeatureGsm = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_GSM);
        boolean hasFeatureIms = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS);
        boolean hasFeatureSingleReg = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION);
        boolean hasFeatureMbms = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MBMS);
        boolean hasFeatureMessaging = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING);
        boolean hasFeatureRadio = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS);
        boolean hasFeatureSubscription = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION);

        if (hasFeatureCalling) {
            assertTrue(hasFeatureTelecom && hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureCarrierLock) {
            assertTrue(hasFeatureSubscription);
        }

        if (hasFeatureCdma) {
            assertTrue(hasFeatureRadio);
        }

        if (hasFeatureData) {
            assertTrue(hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureEuicc) {
            assertTrue(hasFeatureSubscription);
        }

        if (hasFeatureEuiccMep) {
            assertTrue(hasFeatureEuicc);
        }

        if (hasFeatureGsm) {
            assertTrue(hasFeatureRadio);
        }

        if (hasFeatureSingleReg) {
            assertTrue(hasFeatureIms);
        }

        if (hasFeatureMbms) {
            assertTrue(hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureMessaging) {
            assertTrue(hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureRadio) {
            assertTrue(hasFeatureTelephony);
        }

        if (hasFeatureSubscription) {
            assertTrue(hasFeatureTelephony);
        }
    }
}
