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

package android.net.wifi.p2p;

import static android.net.wifi.WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SynchronousExecutor;
import android.os.Bundle;
import android.os.test.TestLooper;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import libcore.junit.util.ResourceLeakageDetector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test harness for WifiP2pManager.
 */
@SmallTest
public class WifiP2pManagerTest {
    private WifiP2pManager mDut;
    private TestLooper mTestLooper;

    private static final String PACKAGE_NAME = "some.package.name";

    @Mock
    public Context mContextMock;
    @Mock
    IWifiP2pManager mP2pServiceMock;

    @Rule
    public ResourceLeakageDetector.LeakageDetectorRule leakageDetectorRule =
            ResourceLeakageDetector.getRule();

    ArgumentCaptor<Bundle> mBundleCaptor = ArgumentCaptor.forClass(Bundle.class);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDut = new WifiP2pManager(mP2pServiceMock);
        mTestLooper = new TestLooper();

        when(mContextMock.getOpPackageName()).thenReturn(PACKAGE_NAME);
        if (SdkLevel.isAtLeastS()) {
            AttributionSource attributionSource = mock(AttributionSource.class);
            when(mContextMock.getAttributionSource()).thenReturn(attributionSource);
        }
    }

    /**
     * Validate initialization flow.
     */
    @Test
    public void testInitialize() throws Exception {
        mDut.initialize(mContextMock, mTestLooper.getLooper(), null);
        verify(mP2pServiceMock).getMessenger(any(), eq(PACKAGE_NAME), mBundleCaptor.capture());
        if (SdkLevel.isAtLeastS()) {
            assertEquals(mContextMock.getAttributionSource(),
                    mBundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        } else {
            assertNull(mBundleCaptor.getValue().getParcelable(EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        }
        assertTrue(mBundleCaptor.getValue().containsKey(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
        assertEquals(Display.DEFAULT_DISPLAY,
                mBundleCaptor.getValue().getInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
    }

    /**
     * Validate initialization flow with Display Context.
     */
    @Test
    public void testInitializeWithDisplayContext() throws Exception {
        final int displayId = 1023;

        Display display = mock(Display.class);
        when(display.getDisplayId()).thenReturn(displayId);
        when(mContextMock.getDisplay()).thenReturn(display);
        mDut.initialize(mContextMock, mTestLooper.getLooper(), null);
        verify(mP2pServiceMock).getMessenger(any(), eq(PACKAGE_NAME), mBundleCaptor.capture());
        assertTrue(mBundleCaptor.getValue().containsKey(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
        assertEquals(displayId,
                mBundleCaptor.getValue().getInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
    }

    /**
     * Validate initialization flow with invalid Display Context.
     */
    @Test
    public void testInitializeWithInvalidDisplayContext() throws Exception {
        doThrow(UnsupportedOperationException.class).when(mContextMock).getDisplay();
        mDut.initialize(mContextMock, mTestLooper.getLooper(), null);
        verify(mP2pServiceMock).getMessenger(any(), eq(PACKAGE_NAME), mBundleCaptor.capture());
        assertTrue(mBundleCaptor.getValue().containsKey(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
        assertEquals(Display.DEFAULT_DISPLAY,
                mBundleCaptor.getValue().getInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID));
    }

    /**
     * Validate that on finalize we close the channel and flag a resource leakage.
     */
    @Test
    public void testChannelFinalize() throws Exception {
        try (WifiP2pManager.Channel channel = new WifiP2pManager.Channel(mContextMock,
                mTestLooper.getLooper(), null, null, mDut)) {
            leakageDetectorRule.assertUnreleasedResourceCount(channel, 1);
        }
    }

    /**
     * Validate that when close is called on a channel it frees up resources (i.e. don't
     * get flagged again on finalize).
     */
    @Test
    public void testChannelClose() throws Exception {
        WifiP2pManager.Channel channel = new WifiP2pManager.Channel(mContextMock,
                mTestLooper.getLooper(), null, null, mDut);

        channel.close();
        verify(mP2pServiceMock).close(any());

        leakageDetectorRule.assertUnreleasedResourceCount(channel, 0);
    }

    /**
     * Validate that non vendor-specific information element raises IllegalArgumentException.
     */
    @Test
    public void testSetVendorElementsWithNonVendorSpecificInformationElement() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS);
        WifiP2pManager.Channel channel = mock(WifiP2pManager.Channel.class);
        List<ScanResult.InformationElement> ies = new ArrayList<>();
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[4]));
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_SSID, 0, new byte[4]));

        try {
            mDut.setVendorElements(channel, ies, null);
            fail("expected IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Validate that the length of a VSIE exceeds 255 raises IllegalArgumentException.
     */
    @Test
    public void testSetVendorElementsWithIeSizeOver255Bytes() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS);
        WifiP2pManager.Channel channel = mock(WifiP2pManager.Channel.class);
        List<ScanResult.InformationElement> ies = new ArrayList<>();
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[256]));

        try {
            mDut.setVendorElements(channel, ies, null);
            fail("expected IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Validate that the total length of VSIEs exceeds 512 raises IllegalArgumentException.
     */
    @Test
    public void testSetVendorElementsWithTotalSizeOver512Bytes() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS);
        WifiP2pManager.Channel channel = mock(WifiP2pManager.Channel.class);
        List<ScanResult.InformationElement> ies = new ArrayList<>();
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[256]));
        ies.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[256]));

        try {
            mDut.setVendorElements(channel, ies, null);
            fail("expected IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
        }
    }

    /** Test {@link WifiP2pManager#isSetVendorElementsSupported()} work as expectation. */
    @Test
    public void testIsSetVendorElementsSupported() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(0L);
        assertFalse(mDut.isSetVendorElementsSupported());
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS);
        assertTrue(mDut.isSetVendorElementsSupported());
    }

    /** Test {@link WifiP2pManager#isChannelConstrainedDiscoverySupported()} work as expectation. */
    @Test
    public void testIsFlexibleDiscoverySupported() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(0L);
        assertFalse(mDut.isChannelConstrainedDiscoverySupported());
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_FLEXIBLE_DISCOVERY);
        assertTrue(mDut.isChannelConstrainedDiscoverySupported());
    }

    /** Test {@link WifiP2pManager#isGroupClientRemovalSupported()} work as expectation. */
    @Test
    public void testIsGroupClientRemovalSupported() throws Exception {
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(0L);
        assertFalse(mDut.isGroupClientRemovalSupported());
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL);
        assertTrue(mDut.isGroupClientRemovalSupported());
    }

    /**
     * Test {@link WifiP2pManager#isGroupOwnerIPv6LinkLocalAddressProvided()} works as
     * expected.
     */
    @Test
    public void testIsGroupOwnerIPv6LinkLocalAddressProvided() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(0L);
        assertFalse(mDut.isGroupOwnerIPv6LinkLocalAddressProvided());
        when(mP2pServiceMock.getSupportedFeatures()).thenReturn(
                WifiP2pManager.FEATURE_GROUP_OWNER_IPV6_LINK_LOCAL_ADDRESS_PROVIDED);
        assertTrue(mDut.isGroupOwnerIPv6LinkLocalAddressProvided());
    }

    @Test
    public void testWifiP2pListener() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        SynchronousExecutor executor = mock(SynchronousExecutor.class);
        WifiP2pManager.WifiP2pListener listener = mock(WifiP2pManager.WifiP2pListener.class);
        mDut.initialize(mContextMock, mTestLooper.getLooper(), null);
        mDut.registerWifiP2pListener(executor, listener);
        verify(mP2pServiceMock).registerWifiP2pListener(any(IWifiP2pListener.Stub.class),
                eq(PACKAGE_NAME), any(Bundle.class));
        mDut.unregisterWifiP2pListener(listener);
        verify(mP2pServiceMock).unregisterWifiP2pListener(any(IWifiP2pListener.Stub.class));
    }
}
