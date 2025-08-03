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

package android.location.cts.privileged;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.util.HexDump;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for SMS/WAP NI SUPL message injection in framework. */
@RunWith(AndroidJUnit4.class)
public class SmsNiSuplTest {
    private static final String MOCK_WAP_PUSH_SUPL_INIT_PDU =
            "07804180551512F2440B804180551512F27DF522016031654390550605040B8423F001063B6170706C696"
            + "36174696F6E2F766E642E6F6D616C6F632D7375706C2D696E697400AF782D6F6D612D6170706C696361"
            + "74696F6E3A756C702E75610000100200004000000046054001180010";
    private static final String MOCK_MT_SMS_SUPL_INIT_PDU =
            "07804180551512F2440B804180551512F27DF522016041502390170605041C6B1C6B00100200004000000"
            + "046054001180010";

    private Context mContext;
    private LocationManager mLocationManager;
    private TelephonyManager mTelephonyManager;
    private SmsManager mSmsManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mLocationManager = mContext.getSystemService(LocationManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSmsManager = mContext.getSystemService(SmsManager.class);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        assertNotNull(mTelephonyManager);
        assertNotNull(mLocationManager);
    }

    /**
     * This test sends the mock NI SUPL messages to trigger the code path in GnssLocationProvider
     * for code test coverage. It passes if there is no crash or no exception thrown during the
     * test.
     */
    @Test
    public void testNiSuplMessage() {
        if (isSimCardPresent()) {
            sendSmsWithPdu(MOCK_WAP_PUSH_SUPL_INIT_PDU);
            sendSmsWithPdu(MOCK_MT_SMS_SUPL_INIT_PDU);
        }
    }

    private void sendSmsWithPdu(String pdu) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mSmsManager,
                (sm) ->
                        sm.injectSmsPdu(
                                HexDump.hexStringToByteArray(pdu), SmsMessage.FORMAT_3GPP, null),
                android.Manifest.permission.MODIFY_PHONE_STATE);
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE
                && mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }
}
