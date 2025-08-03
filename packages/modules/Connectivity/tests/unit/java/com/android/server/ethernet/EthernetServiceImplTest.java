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

package com.android.server.ethernet;

import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.EthernetNetworkSpecifier;
import android.net.EthernetNetworkUpdateRequest;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.IpConfiguration;
import android.net.NetworkCapabilities;
import android.net.StringNetworkSpecifier;
import android.os.Build;
import android.os.Handler;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class EthernetServiceImplTest {
    private static final String TEST_IFACE = "test123";
    private static final NetworkCapabilities DEFAULT_CAPS = new NetworkCapabilities.Builder()
            .addTransportType(TRANSPORT_ETHERNET)
            .setNetworkSpecifier(new EthernetNetworkSpecifier(TEST_IFACE))
            .build();
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST =
            new EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(new IpConfiguration())
                    .setNetworkCapabilities(DEFAULT_CAPS)
                    .build();
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST_WITHOUT_CAPABILITIES =
            new EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(new IpConfiguration())
                    .build();
    private static final EthernetNetworkUpdateRequest UPDATE_REQUEST_WITHOUT_IP_CONFIG =
            new EthernetNetworkUpdateRequest.Builder()
                    .setNetworkCapabilities(DEFAULT_CAPS)
                    .build();
    private static final INetworkInterfaceOutcomeReceiver NULL_LISTENER = null;
    private EthernetServiceImpl mEthernetServiceImpl;
    private Context mContext;
    private Handler mHandler;
    private EthernetTracker mEthernetTracker;
    private PackageManager mPackageManager;

    @Before
    public void setup() {
        mContext = mock(Context.class);
        mHandler = mock(Handler.class);
        mEthernetTracker = mock(EthernetTracker.class);
        mPackageManager = mock(PackageManager.class);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        mEthernetServiceImpl = new EthernetServiceImpl(mContext, mHandler, mEthernetTracker);
        mEthernetServiceImpl.mStarted.set(true);
        toggleAutomotiveFeature(true);
        shouldTrackIface(TEST_IFACE, true);
    }

    private void toggleAutomotiveFeature(final boolean isEnabled) {
        doReturn(isEnabled)
                .when(mPackageManager).hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void shouldTrackIface(@NonNull final String iface, final boolean shouldTrack) {
        doReturn(shouldTrack).when(mEthernetTracker).isTrackingInterface(iface);
    }

    @Test
    public void testSetConfigurationRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.setConfiguration("" /* iface */, new IpConfiguration());
        });
    }

    @Test
    public void testUpdateConfigurationRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(
                    "" /* iface */, UPDATE_REQUEST, null /* listener */);
        });
    }

    @Test
    public void testEnableInterfaceRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.enableInterface("" /* iface */, null /* listener */);
        });
    }

    @Test
    public void testDisableInterfaceRejectsWhenEthNotStarted() {
        mEthernetServiceImpl.mStarted.set(false);
        assertThrows(IllegalStateException.class, () -> {
            mEthernetServiceImpl.disableInterface("" /* iface */, null /* listener */);
        });
    }

    @Test
    public void testUpdateConfigurationRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(null, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testEnableInterfaceRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.enableInterface(null /* iface */, NULL_LISTENER);
        });
    }

    @Test
    public void testDisableInterfaceRejectsNullIface() {
        assertThrows(NullPointerException.class, () -> {
            mEthernetServiceImpl.disableInterface(null /* iface */, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfigurationWithCapabilitiesRejectsWithoutAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        assertThrows(UnsupportedOperationException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfigurationRejectsWithInvalidSpecifierType() {
        final StringNetworkSpecifier invalidSpecifierType = new StringNetworkSpecifier("123");
        final EthernetNetworkUpdateRequest request =
                new EthernetNetworkUpdateRequest.Builder()
                        .setNetworkCapabilities(
                                new NetworkCapabilities.Builder()
                                        .addTransportType(TRANSPORT_ETHERNET)
                                        .setNetworkSpecifier(invalidSpecifierType)
                                        .build()
                        ).build();
        assertThrows(IllegalArgumentException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(
                    "" /* iface */, request, null /* listener */);
        });
    }

    @Test
    public void testUpdateConfigurationRejectsWithInvalidSpecifierName() {
        final String ifaceToUpdate = "eth0";
        final String ifaceOnSpecifier = "wlan0";
        EthernetNetworkUpdateRequest request =
                new EthernetNetworkUpdateRequest.Builder()
                        .setNetworkCapabilities(
                                new NetworkCapabilities.Builder()
                                        .addTransportType(TRANSPORT_ETHERNET)
                                        .setNetworkSpecifier(
                                                new EthernetNetworkSpecifier(ifaceOnSpecifier))
                                        .build()
                        ).build();
        assertThrows(IllegalArgumentException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(ifaceToUpdate, request, null /* listener */);
        });
    }

    @Test
    public void testUpdateConfigurationWithCapabilitiesWithAutomotiveFeature() {
        toggleAutomotiveFeature(false);
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST_WITHOUT_CAPABILITIES,
                NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(UPDATE_REQUEST_WITHOUT_CAPABILITIES.getIpConfiguration()),
                eq(UPDATE_REQUEST_WITHOUT_CAPABILITIES.getNetworkCapabilities()),
                any(EthernetCallback.class));
    }

    private void denyManageEthPermission() {
        doThrow(new SecurityException("")).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_ETHERNET_NETWORKS), anyString());
    }

    private void denyManageTestNetworksPermission() {
        doThrow(new SecurityException("")).when(mContext)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MANAGE_TEST_NETWORKS), anyString());
    }

    @Test
    public void testUpdateConfigurationRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testEnableInterfaceRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.enableInterface(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testDisableInterfaceRejectsWithoutManageEthPermission() {
        denyManageEthPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.disableInterface(TEST_IFACE, NULL_LISTENER);
        });
    }

    private void enableTestInterface() {
        when(mEthernetTracker.isValidTestInterface(eq(TEST_IFACE))).thenReturn(true);
    }

    @Test
    public void testUpdateConfigurationRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    @Test
    public void testEnableInterfaceRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.enableInterface(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testDisableInterfaceRejectsTestRequestWithoutTestPermission() {
        enableTestInterface();
        denyManageTestNetworksPermission();
        assertThrows(SecurityException.class, () -> {
            mEthernetServiceImpl.disableInterface(TEST_IFACE, NULL_LISTENER);
        });
    }

    @Test
    public void testUpdateConfiguration() {
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(
                eq(TEST_IFACE),
                eq(UPDATE_REQUEST.getIpConfiguration()),
                eq(UPDATE_REQUEST.getNetworkCapabilities()),
                any(EthernetCallback.class));
    }

    @Test
    public void testUpdateConfigurationAddsSpecifierWhenNotSet() {
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_ETHERNET).build();
        final EthernetNetworkUpdateRequest requestSansSpecifier =
                new EthernetNetworkUpdateRequest.Builder()
                        .setNetworkCapabilities(nc)
                        .build();
        final NetworkCapabilities ncWithSpecifier = new NetworkCapabilities(nc)
                .setNetworkSpecifier(new EthernetNetworkSpecifier(TEST_IFACE));

        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, requestSansSpecifier, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(
                eq(TEST_IFACE),
                isNull(),
                eq(ncWithSpecifier), any(EthernetCallback.class));
    }

    @Test
    public void testEnableInterface() {
        mEthernetServiceImpl.enableInterface(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).setInterfaceEnabled(eq(TEST_IFACE), eq(true),
                any(EthernetCallback.class));
    }

    @Test
    public void testDisableInterface() {
        mEthernetServiceImpl.disableInterface(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).setInterfaceEnabled(eq(TEST_IFACE), eq(false),
                any(EthernetCallback.class));
    }

    @Test
    public void testUpdateConfigurationAcceptsTestRequestWithNullCapabilities() {
        enableTestInterface();
        final EthernetNetworkUpdateRequest request =
                new EthernetNetworkUpdateRequest
                        .Builder()
                        .setIpConfiguration(new IpConfiguration()).build();
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, request, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(request.getIpConfiguration()),
                eq(request.getNetworkCapabilities()), any(EthernetCallback.class));
    }

    @Test
    public void testUpdateConfigurationAcceptsRequestWithNullIpConfiguration() {
        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST_WITHOUT_IP_CONFIG,
                NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(eq(TEST_IFACE),
                eq(UPDATE_REQUEST_WITHOUT_IP_CONFIG.getIpConfiguration()),
                eq(UPDATE_REQUEST_WITHOUT_IP_CONFIG.getNetworkCapabilities()),
                any(EthernetCallback.class));
    }

    @Test
    public void testUpdateConfigurationRejectsInvalidTestRequest() {
        enableTestInterface();
        assertThrows(IllegalArgumentException.class, () -> {
            mEthernetServiceImpl.updateConfiguration(TEST_IFACE, UPDATE_REQUEST, NULL_LISTENER);
        });
    }

    private EthernetNetworkUpdateRequest createTestNetworkUpdateRequest() {
        final NetworkCapabilities nc =  new NetworkCapabilities
                .Builder(UPDATE_REQUEST.getNetworkCapabilities())
                .addTransportType(TRANSPORT_TEST).build();

        return new EthernetNetworkUpdateRequest
                .Builder(UPDATE_REQUEST)
                .setNetworkCapabilities(nc).build();
    }

    @Test
    public void testUpdateConfigurationForTestRequestDoesNotRequireAutoOrEthernetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();
        final EthernetNetworkUpdateRequest request = createTestNetworkUpdateRequest();

        mEthernetServiceImpl.updateConfiguration(TEST_IFACE, request, NULL_LISTENER);
        verify(mEthernetTracker).updateConfiguration(
                eq(TEST_IFACE),
                eq(request.getIpConfiguration()),
                eq(request.getNetworkCapabilities()), any(EthernetCallback.class));
    }

    @Test
    public void testEnableInterfaceForTestRequestDoesNotRequireNetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();

        mEthernetServiceImpl.enableInterface(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).setInterfaceEnabled(eq(TEST_IFACE), eq(true),
                any(EthernetCallback.class));
    }

    @Test
    public void testDisableInterfaceForTestRequestDoesNotRequireAutoOrNetPermission() {
        enableTestInterface();
        toggleAutomotiveFeature(false);
        denyManageEthPermission();

        mEthernetServiceImpl.disableInterface(TEST_IFACE, NULL_LISTENER);
        verify(mEthernetTracker).setInterfaceEnabled(eq(TEST_IFACE), eq(false),
                any(EthernetCallback.class));
    }

    private void denyPermissions(String... permissions) {
        for (String permission: permissions) {
            doReturn(PackageManager.PERMISSION_DENIED).when(mContext)
                    .checkCallingOrSelfPermission(eq(permission));
        }
    }

    @Test
    public void testSetEthernetEnabled() {
        denyPermissions(android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
        mEthernetServiceImpl.setEthernetEnabled(true);
        verify(mEthernetTracker).setEthernetEnabled(true);
        reset(mEthernetTracker);

        denyPermissions(Manifest.permission.NETWORK_STACK);
        mEthernetServiceImpl.setEthernetEnabled(false);
        verify(mEthernetTracker).setEthernetEnabled(false);
        reset(mEthernetTracker);

        denyPermissions(Manifest.permission.NETWORK_SETTINGS);
        try {
            mEthernetServiceImpl.setEthernetEnabled(true);
            fail("Should get SecurityException");
        } catch (SecurityException e) { }
        verify(mEthernetTracker, never()).setEthernetEnabled(false);
    }
}
