/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permission.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.OemNetworkPreferences;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
* Test that protected android.net.ConnectivityManager methods cannot be called without
* permissions
*/
@RunWith(AndroidJUnit4.class)
public class ConnectivityManagerPermissionTest {

    private ConnectivityManager mConnectivityManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        assertNotNull(mConnectivityManager);
    }

    /**
     * Verify that calling {@link ConnectivityManager#getNetworkInfo(int))}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#ACCESS_NETWORK_STATE}.
     */
    @Test
    public void testGetNetworkInfo() {
        try {
            mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            fail("Was able to call getNetworkInfo");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link ConnectivityManager#setOemNetworkPreference(OemNetworkPreferences,
     * Executor, ConnectivityManager.OnSetOemNetworkPreferenceListener)}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#CONTROL_OEM_PAID_NETWORK_PREFERENCE}.
     */
    @Test
    public void testSetOemNetworkPreference() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        try {
            final String testPackage = "does.not.matter.com";
            final int testPref = OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
            final OemNetworkPreferences preferences =
                    new OemNetworkPreferences.Builder()
                    .addNetworkPreference(testPackage, testPref)
                    .build();
            mConnectivityManager.setOemNetworkPreference(preferences, null, null);
            fail("Was able to call setOemNetworkPreference");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that calling {@link ConnectivityManager#setOemNetworkPreference(OemNetworkPreferences,
     * Executor, ConnectivityManager.OnSetOemNetworkPreferenceListener)}
     * requires automotive feature.
     * <p>Tests Feature:
     *   {@link PackageManager#FEATURE_AUTOMOTIVE}.
     */
    @Test
    public void testSetOemNetworkPreferenceHasAutomotiveFeature() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        try {
            final String testPackage = "does.not.matter.com";
            final int testPref = OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
            final OemNetworkPreferences preferences =
                    new OemNetworkPreferences.Builder()
                            .addNetworkPreference(testPackage, testPref)
                            .build();
            mConnectivityManager.setOemNetworkPreference(preferences, null, null);
            fail("Was able to call setOemNetworkPreference");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
