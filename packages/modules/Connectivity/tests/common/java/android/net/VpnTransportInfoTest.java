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

package android.net;

import static android.net.NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.REDACT_NONE;

import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import androidx.test.filters.SmallTest;

import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
@ConnectivityModuleTest
public class VpnTransportInfoTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    @Test
    public void testParceling() {
        final VpnTransportInfo v = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345");
        assertParcelingIsLossless(v);

        final VpnTransportInfo v2 =
                new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345", true, true);
        assertParcelingIsLossless(v2);
    }

    @Test
    public void testEqualsAndHashCode() {
        String session1 = "12345";
        String session2 = "6789";
        final VpnTransportInfo v11 = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, session1);
        final VpnTransportInfo v12 = new VpnTransportInfo(VpnManager.TYPE_VPN_SERVICE, session1);
        final VpnTransportInfo v13 = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, session1);
        final VpnTransportInfo v14 = new VpnTransportInfo(VpnManager.TYPE_VPN_LEGACY, session1);
        final VpnTransportInfo v15 = new VpnTransportInfo(VpnManager.TYPE_VPN_OEM, session1);
        final VpnTransportInfo v16 = new VpnTransportInfo(
                VpnManager.TYPE_VPN_OEM, session1, true, true);
        final VpnTransportInfo v17 = new VpnTransportInfo(
                VpnManager.TYPE_VPN_OEM, session1, true, true);
        final VpnTransportInfo v21 = new VpnTransportInfo(VpnManager.TYPE_VPN_LEGACY, session2);

        final VpnTransportInfo v31 = v11.makeCopy(REDACT_FOR_NETWORK_SETTINGS);
        final VpnTransportInfo v32 = v13.makeCopy(REDACT_FOR_NETWORK_SETTINGS);
        final VpnTransportInfo v33 = v16.makeCopy(REDACT_FOR_NETWORK_SETTINGS);
        final VpnTransportInfo v34 = v17.makeCopy(REDACT_FOR_NETWORK_SETTINGS);

        assertNotEquals(v11, v12);
        assertNotEquals(v13, v14);
        assertNotEquals(v14, v15);
        assertNotEquals(v14, v21);
        assertNotEquals(v15, v16);

        assertEquals(v11, v13);
        assertEquals(v31, v32);
        assertEquals(v33, v34);
        assertEquals(v11.hashCode(), v13.hashCode());
        assertEquals(v16.hashCode(), v17.hashCode());
        assertEquals(REDACT_FOR_NETWORK_SETTINGS, v32.getApplicableRedactions());
        assertEquals(session1, v15.makeCopy(REDACT_NONE).getSessionId());
    }

    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsBypassable_beforeU() {
        final VpnTransportInfo v = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345");
        assertThrows(UnsupportedOperationException.class, () -> v.isBypassable());
    }

    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testIsBypassable_afterU() {
        final VpnTransportInfo v = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345");
        assertFalse(v.isBypassable());

        final VpnTransportInfo v2 =
                new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345", true, false);
        assertTrue(v2.isBypassable());
    }

    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testShouldLongLivedTcpExcluded() {
        final VpnTransportInfo v = new VpnTransportInfo(VpnManager.TYPE_VPN_PLATFORM, "12345");
        assertFalse(v.areLongLivedTcpConnectionsExpensive());

        final VpnTransportInfo v2 = new VpnTransportInfo(
                VpnManager.TYPE_VPN_PLATFORM, "12345", true, true);
        assertTrue(v2.areLongLivedTcpConnectionsExpensive());
    }
}
