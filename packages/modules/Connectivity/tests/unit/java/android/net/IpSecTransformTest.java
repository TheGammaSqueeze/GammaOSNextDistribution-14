/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Build;
import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;

import com.android.server.IpSecService;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;

/** Unit tests for {@link IpSecTransform}. */
@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class IpSecTransformTest {
    @Rule public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_RESOURCE_ID = 0x1234;

    private static final InetAddress SRC_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.200");
    private static final InetAddress DST_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.201");
    private static final InetAddress SRC_ADDRESS_V6 =
            InetAddresses.parseNumericAddress("2001:db8::200");
    private static final InetAddress DST_ADDRESS_V6 =
            InetAddresses.parseNumericAddress("2001:db8::201");

    private MockContext mMockContext;
    private IpSecService mMockIpSecService;
    private IpSecManager mIpSecManager;

    @Before
    public void setUp() throws Exception {
        mMockIpSecService = mock(IpSecService.class);
        mIpSecManager = new IpSecManager(mock(Context.class) /* unused */, mMockIpSecService);

        // Set up mMockContext since IpSecTransform needs an IpSecManager instance and a non-null
        // package name to create transform
        mMockContext =
                new MockContext() {
                    @Override
                    public String getSystemServiceName(Class<?> serviceClass) {
                        if (serviceClass.equals(IpSecManager.class)) {
                            return Context.IPSEC_SERVICE;
                        }
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Object getSystemService(String name) {
                        if (name.equals(Context.IPSEC_SERVICE)) {
                            return mIpSecManager;
                        }
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String getOpPackageName() {
                        return "fooPackage";
                    }
                };

        final IpSecSpiResponse spiResp =
                new IpSecSpiResponse(IpSecManager.Status.OK, TEST_RESOURCE_ID, DROID_SPI);
        when(mMockIpSecService.allocateSecurityParameterIndex(any(), anyInt(), any()))
                .thenReturn(spiResp);

        final IpSecTransformResponse transformResp =
                new IpSecTransformResponse(IpSecManager.Status.OK, TEST_RESOURCE_ID);
        when(mMockIpSecService.createTransform(any(), any(), any())).thenReturn(transformResp);
    }

    @Test
    public void testCreateTransformCopiesConfig() {
        // Create a config with a few parameters to make sure it's not empty
        IpSecConfig config = new IpSecConfig();
        config.setSourceAddress("0.0.0.0");
        config.setDestinationAddress("1.2.3.4");
        config.setSpiResourceId(1984);

        IpSecTransform preModification = new IpSecTransform(null, config);

        config.setSpiResourceId(1985);
        IpSecTransform postModification = new IpSecTransform(null, config);

        assertNotEquals(preModification, postModification);
    }

    @Test
    public void testCreateTransformsWithSameConfigEqual() {
        // Create a config with a few parameters to make sure it's not empty
        IpSecConfig config = new IpSecConfig();
        config.setSourceAddress("0.0.0.0");
        config.setDestinationAddress("1.2.3.4");
        config.setSpiResourceId(1984);

        IpSecTransform config1 = new IpSecTransform(null, config);
        IpSecTransform config2 = new IpSecTransform(null, config);

        assertEquals(config1, config2);
    }

    private IpSecTransform buildTestTransform() throws Exception {
        final IpSecManager.SecurityParameterIndex spi =
                mIpSecManager.allocateSecurityParameterIndex(DST_ADDRESS);
        return new IpSecTransform.Builder(mMockContext).buildTunnelModeTransform(SRC_ADDRESS, spi);
    }

    @Test
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testStartTransformMigration() throws Exception {
        mIpSecManager.startTunnelModeTransformMigration(
                buildTestTransform(), SRC_ADDRESS_V6, DST_ADDRESS_V6);
        verify(mMockIpSecService)
                .migrateTransform(
                        anyInt(),
                        eq(SRC_ADDRESS_V6.getHostAddress()),
                        eq(DST_ADDRESS_V6.getHostAddress()),
                        any());
    }

    @Test
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    public void testStartTransformMigrationOnSdkBeforeU() throws Exception {
        try {
            mIpSecManager.startTunnelModeTransformMigration(
                    buildTestTransform(), SRC_ADDRESS_V6, DST_ADDRESS_V6);
            fail("Expect to fail since migration is not supported before U");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
