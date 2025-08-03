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

package android.telephony.cts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.telephony.Annotation.DisconnectCauses;
import android.telephony.BarringInfo;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.EmergencyScanType;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.DomainSelector;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A Test DomainSelectionService that will verify DomainSelectionService functionality.
 */
public class TestDomainSelectionService extends Service {

    private static final String TAG = "CtsTestDomainSelectionService";

    public static final int TEST_TIMEOUT_MS = 70 * 1000; // 70 seconds

    private DomainSelectionService mTestDomainSelectionService;
    private Executor mExecutor = Runnable::run;
    protected boolean mIsTelephonyBound = false;
    protected final Object mLock = new Object();

    private DomainSelectorUT mDomainSelector;
    private TransportSelectorCallback mTransportSelectorCallback;
    private WwanSelectorCallback mWwanSelectorCallback;
    private SelectionAttributes mSelectionAttributes;
    private EmergencyRegistrationResult mEmergencyRegResult;
    private CancellationSignal mCancelSignal;

    public static final int LATCH_ON_BIND = 0;
    public static final int LATCH_ON_UNBIND = 1;
    public static final int LATCH_ON_DOMAIN_SELECTION = 2;
    public static final int LATCH_ON_SERVICE_STATE_UPDATED = 3;
    public static final int LATCH_ON_BARRING_INFO_UPDATED = 4;
    public static final int LATCH_ON_WWAN_SELECTOR_CALLBACK = 5;
    public static final int LATCH_ON_EMERGENCY_REG_RESULT = 6;
    public static final int LATCH_ON_RESELECTION = 7;
    public static final int LATCH_ON_FINISH_SELECTION = 8;
    private static final int LATCH_MAX = 9;

    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];

    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    private class DomainSelectorUT implements DomainSelector {
        private boolean mDisposed = false;

        @Override
        public void reselectDomain(@NonNull SelectionAttributes attr) {
            synchronized (mLock) {
                Log.i(TAG, "reselectDomain disposed=" + mDisposed);
                if (mDisposed) return;
                mSelectionAttributes = attr;
                countDownLatch(LATCH_ON_RESELECTION);
            }
        }

        @Override
        public void finishSelection() {
            synchronized (mLock) {
                Log.i(TAG, "finishSelection disposed=" + mDisposed);
                if (mDisposed) return;
                mTransportSelectorCallback = null;
                resetWwanSelectorCallback();
                countDownLatch(LATCH_ON_FINISH_SELECTION);
            }
        }

        void dispose() {
            synchronized (mLock) {
                mDisposed = true;
            }
        }
    }

    private class DomainSelectionServiceUT extends DomainSelectionService {

        DomainSelectionServiceUT(Context context) {
            // DomainSelectionServiceUT is created in order to get around classloader restrictions.
            // Attach the base context from the wrapper DomainSelectionService.
            if (getBaseContext() == null) {
                attachBaseContext(context);
            }
        }

        @Override
        public void onDomainSelection(@NonNull SelectionAttributes attr,
                @NonNull TransportSelectorCallback callback) {
            synchronized (mLock) {
                Log.i(TAG, "onDomainSelection");
                mSelectionAttributes = attr;
                mTransportSelectorCallback = callback;
                resetWwanSelectorCallback();
                countDownLatch(LATCH_ON_DOMAIN_SELECTION);
            }
        }

        @Override
        public void onServiceStateUpdated(int slotId, int subId,
                @NonNull ServiceState serviceState) {
            synchronized (mLock) {
                Log.i(TAG, "onServiceStateUpdated");
                super.onServiceStateUpdated(slotId, subId, serviceState);
                countDownLatch(LATCH_ON_SERVICE_STATE_UPDATED);
            }
        }

        @Override
        public void onBarringInfoUpdated(int slotId, int subId, @NonNull BarringInfo barringInfo) {
            synchronized (mLock) {
                Log.i(TAG, "onBarringInfoUpdated");
                super.onBarringInfoUpdated(slotId, subId, barringInfo);
                countDownLatch(LATCH_ON_BARRING_INFO_UPDATED);
            }
        }

        @Override
        public @NonNull Executor getCreateExecutor() {
            mExecutor = super.getCreateExecutor();
            return mExecutor;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    // For local access of this Service.
    class LocalBinder extends Binder {
        TestDomainSelectionService getService() {
            return TestDomainSelectionService.this;
        }
    }

    private DomainSelectionService getDomainSelectionService() {
        synchronized (mLock) {
            if (mTestDomainSelectionService != null) {
                return mTestDomainSelectionService;
            }
            mTestDomainSelectionService = new DomainSelectionServiceUT(this);
            return mTestDomainSelectionService;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        synchronized (mLock) {
            if ("android.telephony.DomainSelectionService".equals(intent.getAction())) {
                mIsTelephonyBound = true;
                Log.i(TAG, "onBind-Remote");
                countDownLatch(LATCH_ON_BIND);
                return getDomainSelectionService().onBind(intent);
            }
            Log.i(TAG, "onBind-Local");
            return mBinder;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        synchronized (mLock) {
            if ("android.telephony.DomainSelectionService".equals(intent.getAction())) {
                Log.i(TAG, "onUnbind-Remote");
                mIsTelephonyBound = false;
                countDownLatch(LATCH_ON_UNBIND);
            } else {
                Log.i(TAG, "onUnbind-Local");
            }
            // return false so that onBind is called next time.
            return false;
        }
    }

    /** Resets the state. */
    public void resetState() {
        synchronized (mLock) {
            Log.i(TAG, "resetState");
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }

            if (mDomainSelector != null) {
                mDomainSelector.dispose();
                mDomainSelector = null;
            }
            mSelectionAttributes = null;
            mTransportSelectorCallback = null;
            resetWwanSelectorCallback();
        }
    }

    /** Indicates whether telephony framework is bound. */
    public boolean isTelephonyBound() {
        return mIsTelephonyBound;
    }

    /**
     * Waits until the latch counts down to zero unless the thread is interrupted,
     * or the default waiting time elapses.
     * @return true if the count reached zero. Otherwise, false.
     */
    public boolean waitForLatchCountdown(int latchIndex) {
        return waitForLatchCountdown(latchIndex, TEST_TIMEOUT_MS);
    }

    /**
     * Waits until the latch counts down to zero unless the thread is interrupted,
     * or the specified waiting time elapses.
     * @return true if the count reached zero. Otherwise, false.
     */
    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Latch " + latchIndex + " took "
                    + (System.currentTimeMillis() - startTime) + " ms to count down.");
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /** Decrements the count of the latch specified. */
    private void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    /** Returns the {@link TransportSelectorCallback} instance. */
    public TransportSelectorCallback getTransportSelectorCallback() {
        return mTransportSelectorCallback;
    }

    /** Returns the {@link WwanSelectorCallback} instance. */
    public WwanSelectorCallback getWwanSelectorCallback() {
        return mWwanSelectorCallback;
    }

    /** Returns the {@link SelectionAttributes} instance. */
    public SelectionAttributes getSelectionAttributes() {
        return mSelectionAttributes;
    }

    /** Returns the {@link EmergencyRegistrationResult} instance. */
    public EmergencyRegistrationResult getEmergencyRegResult() {
        return mEmergencyRegResult;
    }

    /** Methods for TransportSelectorCallback */

    /**
     * Notify the {@link DomainSelector} instance has been created to the telephony framework.
     */
    public boolean onCreated() {
        synchronized (mLock) {
            Log.i(TAG, "onCreated");
            if (mTransportSelectorCallback != null) {
                mDomainSelector = new DomainSelectorUT();
                mTransportSelectorCallback.onCreated(mDomainSelector);
                return true;
            }
        }
        return false;
    }

    /**
     * Notify that WLAN transport has been selected.
     */
    public boolean onWlanSelected() {
        synchronized (mLock) {
            Log.i(TAG, "onWlanSelected");
            if (mTransportSelectorCallback != null) {
                resetWwanSelectorCallback();
                mTransportSelectorCallback.onWlanSelected(true);
                return true;
            }
        }
        return false;
    }

    /**
     * Notify that WWAN transport has been selected.
     */
    public boolean onWwanSelected() {
        synchronized (mLock) {
            Log.i(TAG, "onWwanSelected");
            if (mTransportSelectorCallback != null) {
                resetWwanSelectorCallback();
                mTransportSelectorCallback.onWwanSelected((callback) -> {
                    synchronized (mLock) {
                        Log.i(TAG, "selectWwan-onComplete");
                        mWwanSelectorCallback = callback;
                        countDownLatch(LATCH_ON_WWAN_SELECTOR_CALLBACK);
                    }
                });
                return true;
            }
        }
        return false;
    }

    private void resetWwanSelectorCallback() {
        mWwanSelectorCallback = null;
        mEmergencyRegResult = null;
        mCancelSignal = null;
    }

    /**
     * Notify that selection has terminated.
     *
     * @param cause indicates the reason.
     */
    public boolean terminateSelection(@DisconnectCauses int cause) {
        synchronized (mLock) {
            Log.i(TAG, "terminateSelection cause=" + cause);
            if (mTransportSelectorCallback != null) {
                mTransportSelectorCallback.onSelectionTerminated(cause);
                return true;
            }
        }
        return false;
    }

    /** Methods for WwanSelectorCallback */

    /**
     * Notify the framework that the {@link DomainSelectionService} has requested an emergency
     * network scan as part of selection.
     *
     * @param preferredNetworks the ordered list of preferred networks to scan.
     * @param scanType indicates the scan preference, such as full service or limited service.
     */
    public boolean requestEmergencyNetworkScan(@NonNull List<Integer> preferredNetworks,
            @EmergencyScanType int scanType) {
        synchronized (mLock) {
            Log.i(TAG, "requestEmergencyNetworkScan list=" + preferredNetworks
                    + ", type=" + scanType);
            if (mWwanSelectorCallback != null) {
                mEmergencyRegResult = null;
                mCancelSignal = new CancellationSignal();
                mWwanSelectorCallback.onRequestEmergencyNetworkScan(
                        preferredNetworks, scanType, false, mCancelSignal,
                        (regResult) -> {
                            synchronized (mLock) {
                                Log.i(TAG, "requestScan-onComplete");
                                mEmergencyRegResult = regResult;
                                mCancelSignal = null;
                                countDownLatch(LATCH_ON_EMERGENCY_REG_RESULT);
                            }
                        });
                return true;
            }
        }
        return false;
    }

    /**
     * Cancel the emergency network scan requested.
     */
    public boolean cancelEmergencyNetworkScan() {
        synchronized (mLock) {
            Log.i(TAG, "cancelEmergencyNetworkScan");
            if (mCancelSignal != null) {
                mCancelSignal.cancel();
                mCancelSignal = null;
                return true;
            }
        }
        return false;
    }

    /**
     * Notify the domain has been selected.
     *
     * @param domain The selected domain.
     */
    public boolean onDomainSelected(@NetworkRegistrationInfo.Domain int domain) {
        synchronized (mLock) {
            Log.i(TAG, "onDomainSelected domain=" + domain);
            if (mWwanSelectorCallback != null) {
                mWwanSelectorCallback.onDomainSelected(domain, true);
                return true;
            }
        }
        return false;
    }
}
