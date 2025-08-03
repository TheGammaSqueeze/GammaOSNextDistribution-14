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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED;

import static com.android.server.connectivity.ConnectivityFlags.CARRIER_SERVICE_CHANGED_USE_CALLBACK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.os.Build;
import android.os.HandlerThread;
import android.telephony.TelephonyManager;

import com.android.net.module.util.CollectionUtils;
import com.android.networkstack.apishim.TelephonyManagerShimImpl;
import com.android.networkstack.apishim.common.TelephonyManagerShim.CarrierPrivilegesListenerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.server.connectivity.CarrierPrivilegeAuthenticator.Dependencies;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Tests for CarrierPrivilegeAuthenticatorTest.
 *
 * Build, install and run with:
 *  atest FrameworksNetTests:CarrierPrivilegeAuthenticatorTest
 */
@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class CarrierPrivilegeAuthenticatorTest {
    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private static final int SUBSCRIPTION_COUNT = 2;
    private static final int TEST_SUBSCRIPTION_ID = 1;

    @NonNull private final Context mContext;
    @NonNull private final TelephonyManager mTelephonyManager;
    @NonNull private final TelephonyManagerShimImpl mTelephonyManagerShim;
    @NonNull private final PackageManager mPackageManager;
    @NonNull private TestCarrierPrivilegeAuthenticator mCarrierPrivilegeAuthenticator;
    @NonNull private final BiConsumer<Integer, Integer> mListener;
    private final int mCarrierConfigPkgUid = 12345;
    private final boolean mUseCallbacks;
    private final String mTestPkg = "com.android.server.connectivity.test";
    private final BroadcastReceiver mMultiSimBroadcastReceiver;
    @NonNull private final HandlerThread mHandlerThread;

    public class TestCarrierPrivilegeAuthenticator extends CarrierPrivilegeAuthenticator {
        TestCarrierPrivilegeAuthenticator(@NonNull final Context c,
                @NonNull final Dependencies deps,
                @NonNull final TelephonyManager t) {
            super(c, deps, t, mTelephonyManagerShim, true /* requestRestrictedWifiEnabled */,
                    mListener);
        }
        @Override
        protected int getSubId(int slotIndex) {
            return TEST_SUBSCRIPTION_ID;
        }
    }

    @After
    public void tearDown() {
        mHandlerThread.quit();
    }

    /** Parameters to test both using callbacks or the old broadcast */
    @Parameterized.Parameters
    public static Collection<Boolean> shouldUseCallbacks() {
        return Arrays.asList(true, false);
    }

    public CarrierPrivilegeAuthenticatorTest(final boolean useCallbacks) throws Exception {
        mContext = mock(Context.class);
        mTelephonyManager = mock(TelephonyManager.class);
        mTelephonyManagerShim = mock(TelephonyManagerShimImpl.class);
        mPackageManager = mock(PackageManager.class);
        mListener = mock(BiConsumer.class);
        mHandlerThread = new HandlerThread(CarrierPrivilegeAuthenticatorTest.class.getSimpleName());
        mUseCallbacks = useCallbacks;
        final Dependencies deps = mock(Dependencies.class);
        doReturn(useCallbacks).when(deps).isFeatureEnabled(any() /* context */,
                eq(CARRIER_SERVICE_CHANGED_USE_CALLBACK));
        doReturn(mHandlerThread).when(deps).makeHandlerThread();
        doReturn(SUBSCRIPTION_COUNT).when(mTelephonyManager).getActiveModemCount();
        doReturn(mTestPkg).when(mTelephonyManagerShim)
                .getCarrierServicePackageNameForLogicalSlot(anyInt());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = mCarrierConfigPkgUid;
        doReturn(applicationInfo).when(mPackageManager).getApplicationInfo(eq(mTestPkg), anyInt());
        mCarrierPrivilegeAuthenticator =
                new TestCarrierPrivilegeAuthenticator(mContext, deps, mTelephonyManager);
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), argThat(filter ->
                filter.getAction(0).equals(ACTION_MULTI_SIM_CONFIG_CHANGED)
        ), any() /* broadcast permissions */, any() /* handler */);
        mMultiSimBroadcastReceiver = receiverCaptor.getValue();
    }

    private Map<Integer, CarrierPrivilegesListenerShim> getCarrierPrivilegesListeners() {
        final ArgumentCaptor<Integer> slotCaptor = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<CarrierPrivilegesListenerShim> listenerCaptor =
                ArgumentCaptor.forClass(CarrierPrivilegesListenerShim.class);
        try {
            verify(mTelephonyManagerShim, atLeastOnce()).addCarrierPrivilegesListener(
                    slotCaptor.capture(), any(), listenerCaptor.capture());
        } catch (UnsupportedApiLevelException e) {
        }
        final Map<Integer, CarrierPrivilegesListenerShim> result =
                CollectionUtils.assoc(slotCaptor.getAllValues(), listenerCaptor.getAllValues());
        clearInvocations(mTelephonyManagerShim);
        return result;
    }

    private Intent buildTestMultiSimConfigBroadcastIntent() {
        return new Intent(ACTION_MULTI_SIM_CONFIG_CHANGED);
    }
    @Test
    public void testConstructor() throws Exception {
        // Two listeners originally registered, one for slot 0 and one for slot 1
        final Map<Integer, CarrierPrivilegesListenerShim> initialListeners =
                getCarrierPrivilegesListeners();
        assertNotNull(initialListeners.get(0));
        assertNotNull(initialListeners.get(1));
        assertEquals(2, initialListeners.size());

        initialListeners.get(0).onCarrierServiceChanged(null, mCarrierConfigPkgUid);

        final NetworkCapabilities.Builder ncBuilder = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(TEST_SUBSCRIPTION_ID));

        assertTrue(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));
        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, ncBuilder.build()));
    }

    @Test
    public void testMultiSimConfigChanged() throws Exception {
        // Two listeners originally registered, one for slot 0 and one for slot 1
        final Map<Integer, CarrierPrivilegesListenerShim> initialListeners =
                getCarrierPrivilegesListeners();
        assertNotNull(initialListeners.get(0));
        assertNotNull(initialListeners.get(1));
        assertEquals(2, initialListeners.size());

        doReturn(1).when(mTelephonyManager).getActiveModemCount();

        // This is a little bit cavalier in that the call to onReceive is not on the handler
        // thread that was specified in registerReceiver.
        // TODO : capture the handler and call this on it if this causes flakiness.
        mMultiSimBroadcastReceiver.onReceive(mContext, buildTestMultiSimConfigBroadcastIntent());
        // Check all listeners have been removed
        for (CarrierPrivilegesListenerShim listener : initialListeners.values()) {
            verify(mTelephonyManagerShim).removeCarrierPrivilegesListener(eq(listener));
        }

        // Expect a new CarrierPrivilegesListener to have been registered for slot 0, and none other
        final Map<Integer, CarrierPrivilegesListenerShim> newListeners =
                getCarrierPrivilegesListeners();
        assertNotNull(newListeners.get(0));
        assertEquals(1, newListeners.size());

        newListeners.get(0).onCarrierServiceChanged(null, mCarrierConfigPkgUid);

        final TelephonyNetworkSpecifier specifier =
                new TelephonyNetworkSpecifier(TEST_SUBSCRIPTION_ID);
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .setNetworkSpecifier(specifier)
                .build();
        assertTrue(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, nc));
        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, nc));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testCarrierPrivilegesLostDueToCarrierServiceUpdate() throws Exception {
        final CarrierPrivilegesListenerShim l = getCarrierPrivilegesListeners().get(0);

        l.onCarrierServiceChanged(null, mCarrierConfigPkgUid);
        l.onCarrierServiceChanged(null, mCarrierConfigPkgUid + 1);
        if (mUseCallbacks) {
            verify(mListener).accept(eq(mCarrierConfigPkgUid), eq(TEST_SUBSCRIPTION_ID));
        }
        l.onCarrierServiceChanged(null, mCarrierConfigPkgUid + 2);
        if (mUseCallbacks) {
            verify(mListener).accept(eq(mCarrierConfigPkgUid + 1), eq(TEST_SUBSCRIPTION_ID));
        }
    }

    @Test
    public void testOnCarrierPrivilegesChanged() throws Exception {
        final CarrierPrivilegesListenerShim listener = getCarrierPrivilegesListeners().get(0);

        final TelephonyNetworkSpecifier specifier =
                new TelephonyNetworkSpecifier(TEST_SUBSCRIPTION_ID);
        final NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .setNetworkSpecifier(specifier)
                .build();

        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = mCarrierConfigPkgUid + 1;
        doReturn(applicationInfo).when(mPackageManager).getApplicationInfo(eq(mTestPkg), anyInt());
        listener.onCarrierPrivilegesChanged(Collections.emptyList(), new int[] {});
        listener.onCarrierServiceChanged(null, applicationInfo.uid);

        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, nc));
        assertTrue(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid + 1, nc));
    }

    @Test
    public void testDefaultSubscription() throws Exception {
        final CarrierPrivilegesListenerShim listener = getCarrierPrivilegesListeners().get(0);
        listener.onCarrierServiceChanged(null, mCarrierConfigPkgUid);

        final NetworkCapabilities.Builder ncBuilder = new NetworkCapabilities.Builder();
        ncBuilder.addTransportType(TRANSPORT_CELLULAR);
        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));

        ncBuilder.setNetworkSpecifier(new TelephonyNetworkSpecifier(TEST_SUBSCRIPTION_ID));
        assertTrue(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));

        // The builder for NetworkCapabilities doesn't allow removing the transport as long as a
        // specifier is set, so unset it first. TODO : fix the builder
        ncBuilder.setNetworkSpecifier(null);
        ncBuilder.removeTransportType(TRANSPORT_CELLULAR);
        ncBuilder.addTransportType(TRANSPORT_WIFI);
        ncBuilder.setNetworkSpecifier(new TelephonyNetworkSpecifier(TEST_SUBSCRIPTION_ID));
        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testNetworkCapabilitiesContainOneSubId() throws Exception {
        final CarrierPrivilegesListenerShim listener = getCarrierPrivilegesListeners().get(0);
        listener.onCarrierServiceChanged(null, mCarrierConfigPkgUid);

        final NetworkCapabilities.Builder ncBuilder = new NetworkCapabilities.Builder();
        ncBuilder.addTransportType(TRANSPORT_WIFI);
        ncBuilder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        ncBuilder.setSubscriptionIds(Set.of(TEST_SUBSCRIPTION_ID));
        assertTrue(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));
    }

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    public void testNetworkCapabilitiesContainTwoSubIds() throws Exception {
        final CarrierPrivilegesListenerShim listener = getCarrierPrivilegesListeners().get(0);
        listener.onCarrierServiceChanged(null, mCarrierConfigPkgUid);

        final NetworkCapabilities.Builder ncBuilder = new NetworkCapabilities.Builder();
        ncBuilder.addTransportType(TRANSPORT_WIFI);
        ncBuilder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        ncBuilder.setSubscriptionIds(Set.of(0, 1));
        assertFalse(mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                mCarrierConfigPkgUid, ncBuilder.build()));
    }
}
