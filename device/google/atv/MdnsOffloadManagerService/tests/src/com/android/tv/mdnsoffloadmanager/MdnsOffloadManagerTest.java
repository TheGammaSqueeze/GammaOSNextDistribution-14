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

package com.android.tv.mdnsoffloadmanager;

import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.tv.mdnsoffloadmanager.TestHelpers.SERVICE_AIRPLAY;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.SERVICE_ATV;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.SERVICE_GOOGLECAST;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.SERVICE_GTV;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.makeIntent;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.makeLinkProperties;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.makeLowPowerStandbyPolicy;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.verifyOffloadedServices;
import static com.android.tv.mdnsoffloadmanager.TestHelpers.verifyPassthroughQNames;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.tv.mdnsoffloadmanager.MdnsOffloadManagerService.Injector;
import com.android.tv.mdnsoffloadmanager.util.WakeLockWrapper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import device.google.atv.mdns_offload.IMdnsOffload.MdnsProtocolData;
import device.google.atv.mdns_offload.IMdnsOffload.MdnsProtocolData.MatchCriteria;
import device.google.atv.mdns_offload.IMdnsOffload.PassthroughBehavior;
import device.google.atv.mdns_offload.IMdnsOffloadManager;

@SmallTest
public class MdnsOffloadManagerTest {

    private static final String TAG = MdnsOffloadManagerTest.class.getSimpleName();
    private static final ComponentName VENDOR_SERVICE_COMPONENT =
            ComponentName.unflattenFromString("test.vendor.offloadservice/.TestOffloadService");
    private static final String[] PRIORITY_LIST = {
            "_googlecast._tcp.local.",
            "_some._other._svc.local."
    };
    private static final String IFC_0 = "imaginaryif0";
    private static final String IFC_1 = "imaginaryif1";
    private static final int APP_UID_0 = 1234;
    private static final int SECONDARY_USER_APP_UID_0 = 101234;
    private static final int APP_UID_1 = 1235;
    private static final String APP_PACKAGE_0 = "first.app.package";
    private static final String APP_PACKAGE_1 = "some.other.package";

    @Mock
    Resources mResources;
    @Mock
    IBinder mClientBinder0;
    @Mock
    IBinder mClientBinder1;
    @Mock
    ConnectivityManager mConnectivityManager;
    @Mock
    Network mNetwork0;
    @Mock
    Network mNetwork1;
    @Mock
    PackageManager mPackageManager;
    @Mock
    WakeLockWrapper mWakeLock;
    @Spy
    FakeMdnsOffloadService mVendorService = new FakeMdnsOffloadService();
    @Captor
    ArgumentCaptor<NetworkCallback> mNetworkCallbackCaptor;
    @Captor
    ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor;

    TestLooper mTestLooper;
    ServiceConnection mCapturedVendorServiceConnection;
    BroadcastReceiver mCapturedScreenBroadcastReceiver;
    BroadcastReceiver mCapturedLowPowerStandbyPolicyReceiver;
    MdnsOffloadManagerService mOffloadManagerService;
    IMdnsOffloadManager mOffloadManagerBinder;
    boolean mIsInteractive;
    int mCallingUid;
    PowerManager.LowPowerStandbyPolicy mLowPowerStandbyPolicy;

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        mTestLooper = new TestLooper();
        MockitoAnnotations.initMocks(this);
        when(mResources.getString(eq(R.string.config_mdnsOffloadVendorServiceComponent)))
                .thenReturn(VENDOR_SERVICE_COMPONENT.flattenToShortString());
        when(mResources.getStringArray(eq(R.array.config_mdnsOffloadPriorityQnames)))
                .thenReturn(PRIORITY_LIST);
        when(mPackageManager.getPackageUid(eq(APP_PACKAGE_0), anyInt())).thenReturn(APP_UID_0);
        when(mPackageManager.getPackageUid(eq(APP_PACKAGE_1), anyInt())).thenReturn(APP_UID_1);
        mLowPowerStandbyPolicy = makeLowPowerStandbyPolicy(APP_PACKAGE_0);
        mCallingUid = APP_UID_0;
        mIsInteractive = true;
    }

    private void createOffloadManager() {
        mOffloadManagerService = new MdnsOffloadManagerService(new Injector() {
            @Override
            Resources getResources() {
                return mResources;
            }

            @Override
            synchronized Looper getLooper() {
                return mTestLooper.getLooper();
            }

            @Override
            boolean isInteractive() {
                return mIsInteractive;
            }

            @Override
            int getCallingUid() {
                return mCallingUid;
            }

            @Override
            ConnectivityManager getConnectivityManager() {
                return mConnectivityManager;
            }

            @Override
            PowerManager.LowPowerStandbyPolicy getLowPowerStandbyPolicy() {
                return mLowPowerStandbyPolicy;
            }

            @Override
            WakeLockWrapper newWakeLock() {
                return mWakeLock;
            }

            @Override
            PackageManager getPackageManager() {
                return mPackageManager;
            }

            @Override
            boolean bindService(Intent intent, ServiceConnection connection, int flags) {
                if (!VENDOR_SERVICE_COMPONENT.equals(intent.getComponent())) {
                    fail("MDNS offload manager is expected to bind to the component provided in " +
                            "the resources.");
                }
                if (flags != Context.BIND_AUTO_CREATE) {
                    fail("MDNS offload manager is expected to set BIND_AUTO_CREATE flag when " +
                            "binding to vendor service.");
                }
                mCapturedVendorServiceConnection = connection;
                return true;
            }

            @Override
            void registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
                if (filter.countActions() == 2 &&
                        filter.hasAction(Intent.ACTION_SCREEN_ON) &&
                        filter.hasAction(Intent.ACTION_SCREEN_OFF)) {
                    mCapturedScreenBroadcastReceiver = receiver;
                    return;
                } else if (filter.countActions() == 1 &&
                        filter.hasAction(PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED)) {
                    mCapturedLowPowerStandbyPolicyReceiver = receiver;
                    return;
                }
                fail("Unexpected broadcast receiver registered.");
            }
        });

        mOffloadManagerService.onCreate();
        mOffloadManagerBinder = (IMdnsOffloadManager) mOffloadManagerService.onBind(null);
        verify(mConnectivityManager).registerNetworkCallback(
                argThat(request ->
                        Arrays.stream(request.getTransportTypes())
                                .boxed()
                                .toList()
                                .containsAll(List.of(TRANSPORT_ETHERNET, TRANSPORT_WIFI))),
                mNetworkCallbackCaptor.capture());
    }

    private void bindVendorService() {
        mCapturedVendorServiceConnection.onServiceConnected(
                VENDOR_SERVICE_COMPONENT, mVendorService);
        mTestLooper.dispatchAll();
    }

    private void unbindVendorService() {
        mCapturedVendorServiceConnection.onServiceDisconnected(VENDOR_SERVICE_COMPONENT);
        mTestLooper.dispatchAll();
    }

    private void registerNetwork(Network network, String networkInterface) {
        mNetworkCallbackCaptor.getValue().onLinkPropertiesChanged(network,
                makeLinkProperties(networkInterface));
        mTestLooper.dispatchAll();
    }

    private void unregisterNetwork(Network network) {
        mNetworkCallbackCaptor.getValue().onLost(network);
        mTestLooper.dispatchAll();
    }

    private void setupDefaultOffloadManager() {
        createOffloadManager();
        bindVendorService();
        registerNetwork(mNetwork0, IFC_0);
    }

    @Test
    public void whenCreated_setsListenersAndBindsService() throws RemoteException {
        setupDefaultOffloadManager();

        assertNotNull(mCapturedVendorServiceConnection);
        assertNotNull(mCapturedScreenBroadcastReceiver);
        verify(mConnectivityManager).registerNetworkCallback(any(), (NetworkCallback) notNull());
        // Vendor offload is reset on binding.
        verify(mVendorService).resetAll();
        assertFalse(mVendorService.mOffloadState);
        assertEquals(0, mVendorService.getOffloadData(IFC_0).offloadedRecords.size());
    }


    @Test
    public void whenOffloadingRecord_propagatesToVendorService() throws RemoteException {
        setupDefaultOffloadManager();

        int recordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_ATV, mClientBinder0);
        mTestLooper.dispatchAll();

        assertTrue("Expected a valid record key", recordKey > 0);
        FakeMdnsOffloadService.OffloadData offloadData = mVendorService.getOffloadData(IFC_0);
        assertEquals(1, offloadData.offloadedRecords.size());
        MdnsProtocolData protocolData = offloadData.offloadedRecords.get(0);
        assertEquals(SERVICE_ATV.rawOffloadPacket, protocolData.rawOffloadPacket);
        assertEquals(1, protocolData.matchCriteriaList.size());
        MatchCriteria matchCriteria = protocolData.matchCriteriaList.get(0);
        assertEquals(0x01, matchCriteria.type); // Type A response
        assertEquals(12, matchCriteria.nameOffset);
    }

    /**
     * Multiple records must be offloaded in the correct order, as memory for offloaded records is
     * limited and the first records must take precedence over the subsequent ones.
     */
    @Test
    public void offloadingOrderIsMaintained() throws RemoteException {
        setupDefaultOffloadManager();

        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GTV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_AIRPLAY, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);
    }

    /**
     * When removing a record, offload of the rest of the records is re-applied in the same order
     * as before.
     */
    @Test
    public void removingOffloadedRecord_maintainsOrder() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        int gtvRecordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_GTV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_AIRPLAY, mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);

        mOffloadManagerBinder.removeProtocolResponses(gtvRecordKey, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_AIRPLAY);
    }

    @Test
    public void removingInvalidRecordKey_doesNothing() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GTV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_AIRPLAY, mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);

        mOffloadManagerBinder.removeProtocolResponses(1337, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);
    }

    @Test
    public void removingRecordHoldingInvalidClientBinder_doesNothing() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        int recordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_GTV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_AIRPLAY, mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);

        mOffloadManagerBinder.removeProtocolResponses(recordKey, mClientBinder1);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV, SERVICE_AIRPLAY);
    }

    @Test
    public void recordsFromPriorityListAreOffloadedFirst() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GTV, mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV, SERVICE_GTV);

        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GOOGLECAST, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_GOOGLECAST, SERVICE_ATV,
                SERVICE_GTV);
    }

    @Test
    public void whenOutOfMemoryCapacity_priorityListRecordsAreNotEvicted() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GTV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_AIRPLAY, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GOOGLECAST, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(
                mVendorService, IFC_0, SERVICE_GOOGLECAST, SERVICE_ATV, SERVICE_GTV);
    }

    @Test
    public void priorityListNamesAreCanonicalized() throws RemoteException {
        when(mResources.getStringArray(eq(R.array.config_mdnsOffloadPriorityQnames)))
                .thenReturn(new String[]{"_googlecast._tcp.local"}); // Trailing dot is missing.
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GOOGLECAST, mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_GOOGLECAST, SERVICE_ATV);
    }

    @Test
    public void addingToPassthroughList_setsPassthroughBehaviorAndPropagatesToVendorService()
            throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();

        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv");
    }

    /**
     * Order of passthrough QNames must be maintained, as the vendor service will drop passthrough
     * QNames if the chip runs out of memory.
     */
    @Test
    public void passthroughOrderIsMaintained() throws RemoteException {
        setupDefaultOffloadManager();

        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "airplay", mClientBinder0);
        mTestLooper.dispatchAll();

        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv", "gtv", "airplay");
    }

    @Test
    public void removingPassthroughQName_maintainsOrder() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "airplay", mClientBinder0);
        mTestLooper.dispatchAll();

        mOffloadManagerBinder.removeFromPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();

        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv", "airplay");
    }

    @Test
    public void removingNonexistentPassthroughQName_doesNothing() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "airplay", mClientBinder0);
        mTestLooper.dispatchAll();
        reset(mVendorService); // Forget previous calls related to passthrough.

        mOffloadManagerBinder.removeFromPassthroughList(
                IFC_0, "otherservice", mClientBinder0);
        mTestLooper.dispatchAll();

        verify(mVendorService, never()).setPassthroughBehavior(eq(IFC_0), anyByte());
        verify(mVendorService, never()).removeFromPassthroughList(eq(IFC_0), anyString());
        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv", "gtv", "airplay");
    }

    @Test
    public void removingPassthroughQNameHoldingInvalidClientBinder_doesNothing()
            throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "airplay", mClientBinder0);
        mTestLooper.dispatchAll();

        mOffloadManagerBinder.removeFromPassthroughList(IFC_0, "gtv", mClientBinder1);
        mTestLooper.dispatchAll();

        verifyPassthroughQNames(mVendorService, IFC_0, "atv", "gtv", "airplay");
    }

    @Test
    public void removingAllEntriesFromPassthroughList_disablesPassthroughBehavior()
            throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);

        mOffloadManagerBinder.removeFromPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.removeFromPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();

        assertEquals(
                PassthroughBehavior.DROP_ALL,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
    }

    @Test
    public void passthroughQNamesFromPriorityListTakePrecedence() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyPassthroughQNames(mVendorService, IFC_0, "atv", "gtv");

        mOffloadManagerBinder.addToPassthroughList(
                IFC_0, "_googlecast._tcp.local", mClientBinder0);
        mTestLooper.dispatchAll();

        verifyPassthroughQNames(mVendorService, IFC_0, "_googlecast._tcp.local", "atv", "gtv");
    }

    /**
     * TODO(b/271353749#comment43) Remove this requirement once vendor implementations support
     * case-insensitive string comparisons.
     */
    @Test
    public void passthroughPreservesCase() throws RemoteException {
        setupDefaultOffloadManager();

        mOffloadManagerBinder.addToPassthroughList(IFC_0, "_SERVICE012._gtv.local", mClientBinder0);
        mTestLooper.dispatchAll();

        verifyPassthroughQNames(mVendorService, IFC_0, "_SERVICE012._gtv.local");
    }

    @Test
    public void whenOutOfMemoryCapacity_priorityListQNamesAreNotEvicted() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "another", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "service", mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(
                IFC_0, "_googlecast._tcp.local", mClientBinder0);
        mTestLooper.dispatchAll();

        verifyPassthroughQNames(
                mVendorService, IFC_0, "_googlecast._tcp.local", "atv", "gtv", "another");
    }

    @Test
    public void whenVendorServiceBindsLate_offloadsData() throws RemoteException {
        createOffloadManager();
        registerNetwork(mNetwork0, IFC_0);
        int recordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_ATV, mClientBinder0);
        assertTrue("Expected a valid record key", recordKey > 0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();

        bindVendorService();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
    }

    @Test
    public void whenVendorServiceReconnects_restoresOffloadData() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");

        unbindVendorService();
        mVendorService = new FakeMdnsOffloadService();
        bindVendorService();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
    }

    @Test
    public void whenClientDies_cleansUpOffloadData() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_GOOGLECAST, mClientBinder1);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "airplay", mClientBinder1);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_GOOGLECAST, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv", "airplay");

        verify(mClientBinder1, atLeastOnce())
                .linkToDeath(mDeathRecipientCaptor.capture(), eq(0));
        mDeathRecipientCaptor.getAllValues().forEach(IBinder.DeathRecipient::binderDied);
        mTestLooper.dispatchAll();

        // Offload data owned by other clients is left untouched.
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
    }

    @Test
    public void whenNonInteractiveMode_enablesOffload() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();

        mCapturedScreenBroadcastReceiver.onReceive(
                mock(Context.class), makeIntent(Intent.ACTION_SCREEN_OFF));
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        assertTrue(mVendorService.mOffloadState);
    }

    @Test
    public void whenInteractiveMode_disablesOffloadAndRetrievesMetrics() throws RemoteException {
        setupDefaultOffloadManager();
        int recordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        mCapturedScreenBroadcastReceiver.onReceive(
                mock(Context.class), makeIntent(Intent.ACTION_SCREEN_OFF));
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        assertTrue(mVendorService.mOffloadState);
        reset(mVendorService); // Forget previous calls to hit/miss counter methods.

        mCapturedScreenBroadcastReceiver.onReceive(
                mock(Context.class), makeIntent(Intent.ACTION_SCREEN_ON));
        mTestLooper.dispatchAll();

        assertFalse(mVendorService.mOffloadState);
        verify(mVendorService).getAndResetHitCounter(eq(recordKey));
        verify(mVendorService).getAndResetMissCounter();
        // Offloaded records are untouched.
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
    }

    @Test
    public void whenNetworkNotAvailable_noOffloadOrPassthrough() throws RemoteException {
        createOffloadManager();
        bindVendorService();

        int recordKey = mOffloadManagerBinder.addProtocolResponses(
                IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();

        assertTrue("Expected a valid record key", recordKey > 0);
        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);
        assertEquals(
                PassthroughBehavior.DROP_ALL,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
    }

    @Test
    public void whenNetworkBecomesAvailableLate_offloadsData() throws RemoteException {
        createOffloadManager();
        bindVendorService();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();

        registerNetwork(mNetwork0, IFC_0);

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
    }

    @Test
    public void whenNetworkLost_removesOffloadData() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");

        unregisterNetwork(mNetwork0);

        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);
        assertEquals(
                PassthroughBehavior.DROP_ALL,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
    }

    @Test
    public void whenNetworkLost_maintainsOffloadDataOnOtherInterfaces() throws RemoteException {
        setupDefaultOffloadManager();
        registerNetwork(mNetwork1, IFC_1);
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mOffloadManagerBinder.addProtocolResponses(IFC_1, SERVICE_GOOGLECAST, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_1, "airplay", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        verifyOffloadedServices(mVendorService, IFC_1, SERVICE_GOOGLECAST);
        verifyPassthroughQNames(mVendorService, IFC_1, "airplay");

        unregisterNetwork(mNetwork1);

        // Offload data on IFC_0 is left untouched.
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
        verifyOffloadedServices(mVendorService, IFC_1);
        verifyPassthroughQNames(mVendorService, IFC_1);
        assertEquals(
                PassthroughBehavior.DROP_ALL,
                mVendorService.getOffloadData(IFC_1).passthroughBehavior);
    }

    @Test
    public void whenNetworkRecovers_restoresOffloadData() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "gtv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        unregisterNetwork(mNetwork0);
        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);

        registerNetwork(mNetwork0, IFC_0);

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "gtv");
        assertEquals(
                PassthroughBehavior.PASSTHROUGH_LIST,
                mVendorService.getOffloadData(IFC_0).passthroughBehavior);
    }

    @Test
    public void callingPackageNotOnLowPowerExemptedList_dataNotOffloaded() throws RemoteException {
        setupDefaultOffloadManager();
        mCallingUid = APP_UID_1;

        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);
    }

    @Test
    public void packageRemovedFromLowPowerExemptedList_correspondingDataIsCleared()
            throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv");

        mLowPowerStandbyPolicy = makeLowPowerStandbyPolicy(APP_PACKAGE_1);
        mCapturedLowPowerStandbyPolicyReceiver.onReceive(
                mock(Context.class),
                makeIntent(PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED));
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);
    }

    @Test
    public void packageAddedToLowPowerExemptedList_dataIsOffloaded() throws RemoteException {
        setupDefaultOffloadManager();
        mCallingUid = APP_UID_1;
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();
        verifyOffloadedServices(mVendorService, IFC_0);
        verifyPassthroughQNames(mVendorService, IFC_0);

        mLowPowerStandbyPolicy = makeLowPowerStandbyPolicy(APP_PACKAGE_0, APP_PACKAGE_1);
        mCapturedLowPowerStandbyPolicyReceiver.onReceive(
                mock(Context.class),
                makeIntent(PowerManager.ACTION_LOW_POWER_STANDBY_POLICY_CHANGED));
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv");
    }

    /**
     * Ensure package allowlist is maintained by app ID, not UID (which is assigned per app & user
     * combination).
     */
    @Test
    public void secondaryUser_packageIsAllowlisted() throws RemoteException {
        setupDefaultOffloadManager();
        mCallingUid = SECONDARY_USER_APP_UID_0;

        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(IFC_0, "atv", mClientBinder0);
        mTestLooper.dispatchAll();

        verifyOffloadedServices(mVendorService, IFC_0, SERVICE_ATV);
        verifyPassthroughQNames(mVendorService, IFC_0, "atv");
    }

    @Test
    public void serviceDump_containsDebuggingInfo() throws RemoteException {
        setupDefaultOffloadManager();
        mOffloadManagerBinder.addProtocolResponses(IFC_0, SERVICE_ATV, mClientBinder0);
        mOffloadManagerBinder.addToPassthroughList(
                IFC_0, "atv", mClientBinder0);
        mCallingUid = APP_UID_1;
        mOffloadManagerBinder.addProtocolResponses(IFC_1, SERVICE_GOOGLECAST, mClientBinder1);
        mOffloadManagerBinder.addToPassthroughList(
                IFC_1, "gtv", mClientBinder1);
        mTestLooper.dispatchAll();

        StringWriter resultWriter = new StringWriter();
        mOffloadManagerService.dump(null, new PrintWriter(resultWriter), null);
        mTestLooper.dispatchAll();
        String result = resultWriter.getBuffer().toString();

        Log.d(TAG, "Service dump:\n" + result);
        assertTrue(result.contains("""
                OffloadIntentStore:
                offload intents:
                * OffloadIntent{mNetworkInterface='imaginaryif0', mRecordKey=1, mPriority=1, mOwnerAppId=1234}
                * OffloadIntent{mNetworkInterface='imaginaryif1', mRecordKey=2, mPriority=-2, mOwnerAppId=1235}
                passthrough intents:
                * PassthroughIntent{mNetworkInterface='imaginaryif0', mOriginalQName='atv', mCanonicalQName='ATV.', mPriority=0, mOwnerAppId=1234}
                * PassthroughIntent{mNetworkInterface='imaginaryif1', mOriginalQName='gtv', mCanonicalQName='GTV.', mPriority=0, mOwnerAppId=1235}

                """));
        assertTrue(result.contains("""
                InterfaceOffloadManager[imaginaryif0]:
                mIsNetworkAvailable=true
                current offload keys:
                * 0
                current passthrough qnames:
                * atv

                """));
        assertTrue(result.contains("""
                InterfaceOffloadManager[imaginaryif1]:
                mIsNetworkAvailable=false
                current offload keys:
                current passthrough qnames:

                """));
        assertTrue(result.contains("""
                OffloadWriter:
                mOffloadState=false
                isVendorServiceConnected=true

                """));
        assertTrue(result.contains("""
                mRecordKey=1
                match criteria:
                * MatchCriteria{type=1, nameOffset=12}
                raw offload packet:
                000000:  00 00 00 00 00 00 00 01 00 00 00 00 03 61 74 76   |  .............atv
                000016:  00 00 01 80 01 00 00 00 05 00 04 64 50 28 14      |  ...........dP(.
                """));
        assertTrue(result.contains("""
                mRecordKey=2
                match criteria:
                * MatchCriteria{type=12, nameOffset=12}
                * MatchCriteria{type=1, nameOffset=55}
                raw offload packet:
                000000:  00 00 00 00 00 00 00 02 00 00 00 00 0b 5f 67 6f   |  ............._go
                000016:  6f 67 6c 65 63 61 73 74 04 5f 74 63 70 05 6c 6f   |  oglecast._tcp.lo
                000032:  63 61 6c 00 00 0c 80 01 00 00 00 05 00 09 06 74   |  cal............t
                000048:  76 2d 61 62 63 c0 1d c0 2e 00 01 80 01 00 00 00   |  v-abc...........
                000064:  05 00 04 64 50 28 14                              |  ...dP(.
                """));
    }
}