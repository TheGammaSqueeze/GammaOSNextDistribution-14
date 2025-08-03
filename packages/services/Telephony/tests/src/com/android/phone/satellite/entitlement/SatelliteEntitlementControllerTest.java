/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.phone.satellite.entitlement;

import static com.android.phone.satellite.entitlement.SatelliteEntitlementResult.SATELLITE_ENTITLEMENT_STATUS_ENABLED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.testing.TestableLooper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.ExponentialBackoff;
import com.android.internal.telephony.satellite.SatelliteController;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class SatelliteEntitlementControllerTest extends TelephonyTestBase {
    private static final String TAG = "SatelliteEntitlementControllerTest";
    private static final int SUB_ID = 0;
    private static final int SUB_ID_2 = 1;
    private static final int[] ACTIVE_SUB_ID = {SUB_ID};
    private static final int DEFAULT_QUERY_REFRESH_DAY = 30;
    private static final List<String> PLMN_ALLOWED_LIST = Arrays.asList("31026", "302820");
    @Mock
    CarrierConfigManager mCarrierConfigManager;
    @Mock
    ConnectivityManager mConnectivityManager;
    @Mock Network mNetwork;
    @Mock TelephonyManager mTelephonyManager;
    @Mock SubscriptionManagerService mMockSubscriptionManagerService;
    @Mock SatelliteEntitlementApi mSatelliteEntitlementApi;
    @Mock SatelliteEntitlementResult mSatelliteEntitlementResult;
    @Mock SatelliteController mSatelliteController;
    @Mock ExponentialBackoff mExponentialBackoff;
    private PersistableBundle mCarrierConfigBundle;
    private TestSatelliteEntitlementController mSatelliteEntitlementController;
    private Handler mHandler;
    private TestableLooper mTestableLooper;
    private List<Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener>>
            mCarrierConfigChangedListenerList = new ArrayList<>();
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        replaceInstance(SubscriptionManagerService.class, "sInstance", null,
                mMockSubscriptionManagerService);
        replaceInstance(SatelliteController.class, "sInstance", null, mSatelliteController);

        HandlerThread handlerThread = new HandlerThread("SatelliteEntitlementController");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
            }
        };
        mTestableLooper = new TestableLooper(mHandler.getLooper());
        doReturn(Context.TELEPHONY_SERVICE).when(mContext).getSystemServiceName(
                TelephonyManager.class);
        doReturn(mTelephonyManager).when(mContext).getSystemService(Context.TELEPHONY_SERVICE);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(Context.CARRIER_CONFIG_SERVICE).when(mContext).getSystemServiceName(
                CarrierConfigManager.class);
        doReturn(mCarrierConfigManager).when(mContext).getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(0);
            CarrierConfigManager.CarrierConfigChangeListener listener = invocation.getArgument(1);
            mCarrierConfigChangedListenerList.add(new Pair<>(executor, listener));
            return null;
        }).when(mCarrierConfigManager).registerCarrierConfigChangeListener(
                any(Executor.class),
                any(CarrierConfigManager.CarrierConfigChangeListener.class));
        mCarrierConfigBundle = new PersistableBundle();
        mCarrierConfigBundle.putInt(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_STATUS_REFRESH_DAYS_INT,
                DEFAULT_QUERY_REFRESH_DAY);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        doReturn(mCarrierConfigBundle)
                .when(mCarrierConfigManager).getConfigForSubId(anyInt(), anyVararg());
        doReturn(Context.CONNECTIVITY_SERVICE).when(mContext).getSystemServiceName(
                ConnectivityManager.class);
        doReturn(mConnectivityManager).when(mContext).getSystemService(
                Context.CONNECTIVITY_SERVICE);
        doReturn(mNetwork).when(mConnectivityManager).getActiveNetwork();
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        mSatelliteEntitlementController = new TestSatelliteEntitlementController(mContext,
                mHandler.getLooper(), mSatelliteEntitlementApi);
        mSatelliteEntitlementController = spy(mSatelliteEntitlementController);
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testIsQueryAvailable() throws Exception {
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);

        // Verify don't start the query when KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL is false.
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, false);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());

        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        // Verify don't start the query when ExponentialBackoff is in progressed.
        replaceInstance(SatelliteEntitlementController.class, "mExponentialBackoffPerSub",
                mSatelliteEntitlementController, Map.of(SUB_ID, mExponentialBackoff));
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());

        replaceInstance(SatelliteEntitlementController.class, "mExponentialBackoffPerSub",
                mSatelliteEntitlementController, new HashMap<>());
        // Verify don't start the query when Internet is disconnected.
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        setInternetConnected(false);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());

        setInternetConnected(true);
        // Verify don't start the query when last query refresh time is not expired.
        setLastQueryTime(System.currentTimeMillis());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());

        // Verify start the query when isQueryAvailable return true
        setLastQueryTime(0L);
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());
    }

    @Test
    public void testCheckSatelliteEntitlementStatus() throws Exception {
        setIsQueryAvailableTrue();
        // Verify don't call the checkSatelliteEntitlementStatus when getActiveSubIdList is empty.
        doReturn(new int[]{}).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi, never()).checkEntitlementStatus();
        // Verify don't call the updateSatelliteEntitlementStatus.
        verify(mSatelliteController, never()).onSatelliteEntitlementStatusUpdated(anyInt(),
                anyBoolean(), anyList(), any());

        // Verify call the checkSatelliteEntitlementStatus with invalid response.
        setIsQueryAvailableTrue();
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        replaceInstance(SatelliteEntitlementController.class,
                "mSatelliteEntitlementResultPerSub", mSatelliteEntitlementController,
                new HashMap<>());
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is disabled
        // and empty PLMNAllowed
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID),
                eq(false), eq(new ArrayList<>()), any());

        // Verify call the checkSatelliteEntitlementStatus with the subscribed result.
        clearInvocationsForMock();
        setIsQueryAvailableTrue();
        doReturn(mSatelliteEntitlementResult).when(
                mSatelliteEntitlementApi).checkEntitlementStatus();
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is enable and
        // availablePLMNAllowedList
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), any());

        // Change subId and verify call the updateSatelliteEntitlementStatus with  satellite
        // service is enable and availablePLMNAllowedList
        clearInvocationsForMock();
        doReturn(new int[]{SUB_ID_2}).when(mMockSubscriptionManagerService).getActiveSubIdList(
                true);
        mSatelliteEntitlementController.handleCmdStartQueryEntitlement();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID_2), eq(true),
                eq(PLMN_ALLOWED_LIST), any());
    }

    @Test
    public void testCheckSatelliteEntitlementStatusWhenInternetConnected() throws Exception {
        Field fieldNetworkCallback = SatelliteEntitlementController.class.getDeclaredField(
                "mNetworkCallback");
        fieldNetworkCallback.setAccessible(true);
        ConnectivityManager.NetworkCallback networkCallback =
                (ConnectivityManager.NetworkCallback) fieldNetworkCallback.get(
                        mSatelliteEntitlementController);
        Network mockNetwork = mock(Network.class);

        // Verify the called the checkSatelliteEntitlementStatus when Internet is connected.
        setInternetConnected(true);
        setLastQueryTime(0L);
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST);

        networkCallback.onAvailable(mockNetwork);
        mTestableLooper.processAllMessages();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is available.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), any());
    }

    @Test
    public void testCheckSatelliteEntitlementStatusWhenCarrierConfigChanged() throws Exception {
        // Verify the called the checkSatelliteEntitlementStatus when CarrierConfigChanged
        // occurred and Internet is connected.
        setInternetConnected(true);
        setLastQueryTime(0L);
        setSatelliteEntitlementResult(SATELLITE_ENTITLEMENT_STATUS_ENABLED, PLMN_ALLOWED_LIST);
        triggerCarrierConfigChanged();

        verify(mSatelliteEntitlementApi).checkEntitlementStatus();
        // Verify call the updateSatelliteEntitlementStatus with satellite service is available.
        verify(mSatelliteController).onSatelliteEntitlementStatusUpdated(eq(SUB_ID), eq(true),
                eq(PLMN_ALLOWED_LIST), any());
    }

    private void triggerCarrierConfigChanged() {
        for (Pair<Executor, CarrierConfigManager.CarrierConfigChangeListener> pair
                : mCarrierConfigChangedListenerList) {
            pair.first.execute(() -> pair.second.onCarrierConfigChanged(
                    /*slotIndex*/ 0, /*subId*/ SUB_ID, /*carrierId*/ 0, /*specificCarrierId*/ 0)
            );
        }
        mTestableLooper.processAllMessages();
    }

    private void clearInvocationsForMock() {
        clearInvocations(mSatelliteEntitlementApi);
        clearInvocations(mSatelliteController);
    }

    private void setIsQueryAvailableTrue() throws Exception {
        doReturn(ACTIVE_SUB_ID).when(mMockSubscriptionManagerService).getActiveSubIdList(true);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        replaceInstance(SatelliteEntitlementController.class, "mRetryCountPerSub",
                mSatelliteEntitlementController, new HashMap<>());
        setInternetConnected(true);
        setLastQueryTime(0L);
        replaceInstance(SatelliteEntitlementController.class,
                "mSatelliteEntitlementResultPerSub", mSatelliteEntitlementController,
                new HashMap<>());
    }

    private void setInternetConnected(boolean connected) {
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder().build();

        if (connected) {
            networkCapabilities = new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setTransportInfo(mock(WifiInfo.class))
                    .build();
        }
        doReturn(networkCapabilities).when(mConnectivityManager).getNetworkCapabilities(mNetwork);
    }

    private void setSatelliteEntitlementResult(int entitlementStatus,
            List<String> plmnAllowedList) {
        doReturn(entitlementStatus).when(mSatelliteEntitlementResult).getEntitlementStatus();
        doReturn(plmnAllowedList).when(mSatelliteEntitlementResult).getAllowedPLMNList();
    }

    private void setLastQueryTime(Long lastQueryTime) throws Exception {
        Map<Integer, Long> lastQueryTimePerSub = new HashMap<>();
        replaceInstance(SatelliteEntitlementController.class, "mLastQueryTimePerSub",
                mSatelliteEntitlementController, lastQueryTimePerSub);
        lastQueryTimePerSub.put(SUB_ID, lastQueryTime);
    }

    public static class TestSatelliteEntitlementController extends SatelliteEntitlementController {
        private SatelliteEntitlementApi mInjectSatelliteEntitlementApi;

        TestSatelliteEntitlementController(@NonNull Context context, @NonNull Looper looper,
                SatelliteEntitlementApi api) {
            super(context, looper);
            mInjectSatelliteEntitlementApi = api;
        }

        @Override
        public SatelliteEntitlementApi getSatelliteEntitlementApi(int subId) {
            logd("getSatelliteEntitlementApi");
            return mInjectSatelliteEntitlementApi;
        }
    }

    private static void logd(String log) {
        Log.d(TAG, log);
    }
}
