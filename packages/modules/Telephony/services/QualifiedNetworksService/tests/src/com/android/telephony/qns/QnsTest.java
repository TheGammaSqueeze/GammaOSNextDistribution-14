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

package com.android.telephony.qns;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Country;
import android.location.CountryDetector;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.SipDelegateManager;

import androidx.test.core.app.ApplicationProvider;

import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class QnsTest {
    private static final long MAX_WAIT_TIME_MS = 10000;
    @Mock protected static Context sMockContext;

    @Mock protected TelephonyManager mMockTelephonyManager;
    @Mock protected CarrierConfigManager mMockCarrierConfigManager;
    @Mock protected ConnectivityManager mMockConnectivityManager;
    @Mock protected ImsManager mMockImsManager;
    @Mock protected SubscriptionManager mMockSubscriptionManager;
    @Mock protected WifiManager mMockWifiManager;
    @Mock protected CountryDetector mMockCountryDetector;

    @Mock protected ImsMmTelManager mMockImsMmTelManager;
    @Mock protected ImsRcsManager mMockImsRcsManager;
    @Mock protected SipDelegateManager mMockSipDelegateManager;
    @Mock protected SubscriptionInfo mMockSubscriptionInfo;
    @Mock protected WifiInfo mMockWifiInfo;
    @Mock protected Resources mMockResources;

    // qns mocks
    @Mock protected IwlanNetworkStatusTracker mMockIwlanNetworkStatusTracker;
    @Mock protected WifiQualityMonitor mMockWifiQm;
    @Mock protected CellularNetworkStatusTracker mMockCellNetStatusTracker;
    @Mock protected CellularQualityMonitor mMockCellularQm;
    @Mock protected PowerManager mMockPowerManager;
    @Mock protected QnsImsManager mMockQnsImsManager;
    @Mock protected QnsCarrierConfigManager mMockQnsConfigManager;
    @Mock protected QnsEventDispatcher mMockQnsEventDispatcher;
    @Mock protected QnsProvisioningListener mMockQnsProvisioningListener;
    @Mock protected QnsTelephonyListener mMockQnsTelephonyListener;
    @Mock protected QnsCallStatusTracker mMockQnsCallStatusTracker;
    @Mock protected WifiBackhaulMonitor mMockWifiBm;
    @Mock protected QnsTimer mMockQnsTimer;
    @Mock protected QnsMetrics mMockQnsMetrics;

    protected QnsComponents[] mQnsComponents = new QnsComponents[2];

    private boolean mReady = false;
    private final Object mLock = new Object();

    protected void setUp() throws Exception {
        sMockContext = spy(ApplicationProvider.getApplicationContext());
        stubContext();
        stubManagers();
        stubOthers();
        stubQnsComponents();
        addPermissions();
    }

    private void stubQnsComponents() {
        mQnsComponents[0] =
                new QnsComponents(
                        sMockContext,
                        mMockCellNetStatusTracker,
                        mMockCellularQm,
                        mMockIwlanNetworkStatusTracker,
                        mMockQnsImsManager,
                        mMockQnsConfigManager,
                        mMockQnsEventDispatcher,
                        mMockQnsProvisioningListener,
                        mMockQnsTelephonyListener,
                        mMockQnsCallStatusTracker,
                        mMockQnsTimer,
                        mMockWifiBm,
                        mMockWifiQm,
                        mMockQnsMetrics,
                        0);

        mQnsComponents[1] =
                new QnsComponents(
                        sMockContext,
                        mMockCellNetStatusTracker,
                        mMockCellularQm,
                        mMockIwlanNetworkStatusTracker,
                        mMockQnsImsManager,
                        mMockQnsConfigManager,
                        mMockQnsEventDispatcher,
                        mMockQnsProvisioningListener,
                        mMockQnsTelephonyListener,
                        mMockQnsCallStatusTracker,
                        mMockQnsTimer,
                        mMockWifiBm,
                        mMockWifiQm,
                        mMockQnsMetrics,
                        1);
    }

    private void stubContext() {
        when(sMockContext.getSystemService(TelephonyManager.class))
                .thenReturn(mMockTelephonyManager);
        when(sMockContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        when(sMockContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mMockCarrierConfigManager);
        when(sMockContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        when(sMockContext.getSystemService(ImsManager.class)).thenReturn(mMockImsManager);
        when(sMockContext.getSystemService(WifiManager.class)).thenReturn(mMockWifiManager);
        when(sMockContext.getSystemService(CountryDetector.class)).thenReturn(mMockCountryDetector);
        when(sMockContext.getSystemService(PowerManager.class)).thenReturn(mMockPowerManager);
        when(sMockContext.getResources()).thenReturn(mMockResources);
    }

    private void stubManagers() {
        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getSimCarrierId()).thenReturn(0);
        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("ca");
        when(mMockImsManager.getImsMmTelManager(anyInt())).thenReturn(mMockImsMmTelManager);
        when(mMockImsManager.getImsRcsManager(anyInt())).thenReturn(mMockImsRcsManager);
        when(mMockImsManager.getSipDelegateManager(anyInt())).thenReturn(mMockSipDelegateManager);
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(anyInt()))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);

        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);

        when(mMockCountryDetector.detectCountry())
                .thenReturn(new Country("US", Country.COUNTRY_SOURCE_LOCATION));
        when(mMockPowerManager.isDeviceIdleMode()).thenReturn(false);
    }

    private void stubOthers() {
        when(mMockWifiInfo.getRssi()).thenReturn(-65);
    }

    private void addPermissions() {
        when(sMockContext.checkPermission(anyString(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
    }

    protected void waitUntilReady() {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(MAX_WAIT_TIME_MS);
                } catch (InterruptedException e) {
                }
                if (!mReady) {
                    fail("Test is not ready!!");
                }
            }
        }
    }

    /** Wait for up to 2 second for the handler message queue to clear. */
    protected final void waitForLastHandlerAction(Handler h) {
        CountDownLatch lock = new CountDownLatch(1);
        // Allow the handler to start work on stuff.
        h.postDelayed(lock::countDown, 100);
        int timeoutCount = 0;
        while (timeoutCount < 10) {
            try {
                if (lock.await(200, TimeUnit.MILLISECONDS)) {
                    // no messages in queue, stop waiting.
                    if (!h.hasMessagesOrCallbacks()) break;
                    lock = new CountDownLatch(1);
                    // Delay allowing the handler thread to start work on stuff.
                    h.postDelayed(lock::countDown, 100);
                }
            } catch (InterruptedException e) {
                // do nothing
            }
            timeoutCount++;
        }
        assertTrue("Handler was not empty before timeout elapsed", timeoutCount < 10);
    }

    protected final void waitForDelayedHandlerAction(
            Handler h, long delayMillis, long timeoutMillis) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.postDelayed(lock::countDown, delayMillis);
        while (lock.getCount() > 0) {
            try {
                lock.await(delayMillis + timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    protected synchronized void setObject(
            final Class c, final String field, final Object obj, final Object newValue)
            throws Exception {
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        f.set(obj, newValue);
    }
}
