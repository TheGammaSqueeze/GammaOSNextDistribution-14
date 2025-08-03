/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;


@RunWith(AndroidJUnit4.class)
public class NoLocationPermissionTest {

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetCellLocation() {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertNotNull(telephonyManager);

        try {
            telephonyManager.getCellLocation();
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testGetAllCellInfo() {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertNotNull(telephonyManager);

        try {
            telephonyManager.getAllCellInfo();
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testListenCellLocation() {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertNotNull(telephonyManager);

        try {
            telephonyManager.listen(new PhoneStateListener(Runnable::run),
                    PhoneStateListener.LISTEN_CELL_LOCATION);
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testRequestCellInfoUpdate() {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertNotNull(telephonyManager);

        try {
            telephonyManager.requestCellInfoUpdate(Runnable::run,
                    new TelephonyManager.CellInfoCallback() {
                        @Override
                        public void onCellInfo(List<CellInfo> cellInfos) {
                        }
                    });
            fail("Should throw SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }
}
