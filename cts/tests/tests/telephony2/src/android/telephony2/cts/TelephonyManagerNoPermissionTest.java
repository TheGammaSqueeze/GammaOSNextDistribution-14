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

package android.telephony2.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;

import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test APIs when the package does not have READ_PHONE_STATE.
 */
public class TelephonyManagerNoPermissionTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE2,
                TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
    }

    @Test
    public void testGetCallState_redirectToTelecom() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE2,
                TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
        try {
            mTelephonyManager.getCallState();
            fail("TelephonyManager#getCallState must require READ_PHONE_STATE if "
                    + "TelecomManager#ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION is enabled.");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testGetCallStateForSubscription() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE2,
                TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
        try {
            mTelephonyManager.getCallStateForSubscription();
            fail("TelephonyManager#getCallStateForSubscription must require READ_PHONE_STATE "
                    + "if TelecomManager#ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION is "
                    + "enabled.");
        } catch (SecurityException e) {
            // expected
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    public void getCarrierRestrictionRules_SecurityException() {
        try {
            assumeNotNull(mTelephonyManager.getCarrierRestrictionRules());
            mTelephonyManager.getCarrierRestrictionRules();
            fail();
        } catch (SecurityException se) {
            // expected
        }
    }
}
