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

package android.net.wifi.nl80211.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.wifi.WifiKeystore;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS tests for {@link WifiKeystore}.
 *
 * Note: API calls are expected to fail due to SELinux restrictions on ILegacyKeystore.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiKeyStoreTest {
    private static final String TEST_ALIAS = "some_alias";
    private static final String TEST_PREFIX = "prefix_";
    private static final byte[] TEST_DATA = new byte[]{1, 2, 3, 4, 7};

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // skip tests if Wifi is not supported
        assumeTrue(WifiFeature.isWifiSupported(mContext));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testGet() {
        assertNull(WifiKeystore.get(TEST_ALIAS));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testPut() {
        assertFalse(WifiKeystore.put(TEST_ALIAS, TEST_DATA));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testRemove() {
        assertFalse(WifiKeystore.remove(TEST_ALIAS));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testList() {
        assertEquals(0, WifiKeystore.list(TEST_PREFIX).length);
    }
}
