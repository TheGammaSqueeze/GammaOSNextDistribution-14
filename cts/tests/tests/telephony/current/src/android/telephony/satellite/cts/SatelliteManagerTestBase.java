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

package android.telephony.satellite.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.TelephonyManagerTest.ServiceStateRadioStateListener;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.NtnSignalStrengthCallback;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteCapabilitiesCallback;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.text.TextUtils;
import android.util.Log;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SatelliteManagerTestBase {
    protected static String TAG = "SatelliteManagerTestBase";

    protected static final String TOKEN = "TEST_TOKEN";
    protected static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    /**
     * Since SST sets waiting time up to 10 seconds for the power off radio, the timer waiting for
     * radio power state change should be greater than 10 seconds.
     */
    protected static final long EXTERNAL_DEPENDENT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    protected static SatelliteManager sSatelliteManager;
    protected static TelephonyManager sTelephonyManager = null;

    protected UwbManager mUwbManager = null;
    protected NfcAdapter mNfcAdapter = null;
    protected BluetoothAdapter mBluetoothAdapter = null;
    protected WifiManager mWifiManager = null;

    protected static void beforeAllTestsBase() {
        sSatelliteManager = getContext().getSystemService(SatelliteManager.class);
        sTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        turnRadioOn();
    }

    protected static void afterAllTestsBase() {
        sSatelliteManager = null;
        sTelephonyManager = null;
    }

    protected static boolean shouldTestSatellite() {
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE)) {
            logd("Skipping tests because FEATURE_TELEPHONY_SATELLITE is not available");
            return false;
        }
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            logd("Skipping tests because Telephony service is null, exception=" + e);
            return false;
        }
        return true;
    }

    protected static boolean shouldTestSatelliteWithMockService() {
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY)) {
            logd("Skipping tests because FEATURE_TELEPHONY is not available");
            return false;
        }
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            logd("Skipping tests because Telephony service is null, exception=" + e);
            return false;
        }
        return true;
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    protected static void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    protected static void revokeSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected static class SatelliteTransmissionUpdateCallbackTest implements
            SatelliteTransmissionUpdateCallback {

        protected static final class DatagramStateChangeArgument {
            protected int state;
            protected int pendingCount;
            protected int errorCode;

            DatagramStateChangeArgument(int state, int pendingCount, int errorCode) {
                this.state = state;
                this.pendingCount = pendingCount;
                this.errorCode = errorCode;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (other == null || getClass() != other.getClass()) return false;
                DatagramStateChangeArgument that = (DatagramStateChangeArgument) other;
                return state == that.state
                        && pendingCount  == that.pendingCount
                        && errorCode == that.errorCode;
            }

            @Override
            public String toString() {
                return ("state: " + state + " pendingCount: " + pendingCount
                        + " errorCode: " + errorCode);
            }
        }

        public PointingInfo mPointingInfo;
        private final Semaphore mPositionChangeSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mSendDatagramStateChanges = new ArrayList<>();
        private final Object mSendDatagramStateChangesLock = new Object();
        private final Semaphore mSendSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mReceiveDatagramStateChanges = new ArrayList<>();
        private final Object mReceiveDatagramStateChangesLock = new Object();
        private final Semaphore mReceiveSemaphore = new Semaphore(0);

        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            logd("onSatellitePositionChanged: pointingInfo=" + pointingInfo);
            mPointingInfo = pointingInfo;

            try {
                mPositionChangeSemaphore.release();
            } catch (Exception e) {
                loge("onSatellitePositionChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            logd("onSendDatagramStateChanged: state=" + state + ", sendPendingCount="
                    + sendPendingCount + ", errorCode=" + errorCode);
            synchronized (mSendDatagramStateChangesLock) {
                mSendDatagramStateChanges.add(new DatagramStateChangeArgument(state,
                        sendPendingCount, errorCode));
            }

            try {
                mSendSemaphore.release();
            } catch (Exception e) {
                loge("onSendDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onReceiveDatagramStateChanged(
                int state, int receivePendingCount, int errorCode) {
            logd("onReceiveDatagramStateChanged: state=" + state + ", "
                    + "receivePendingCount=" + receivePendingCount + ", errorCode=" + errorCode);
            synchronized (mReceiveDatagramStateChangesLock) {
                mReceiveDatagramStateChanges.add(new DatagramStateChangeArgument(state,
                        receivePendingCount, errorCode));
            }

            try {
                mReceiveSemaphore.release();
            } catch (Exception e) {
                loge("onReceiveDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilOnSatellitePositionChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mPositionChangeSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatellitePositionChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSatellitePositionChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnSendDatagramStateChanged(int expectedNumberOfEvents) {
            logd("waitUntilOnSendDatagramStateChanged expectedNumberOfEvents:" + expectedNumberOfEvents);
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSendSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSendDatagramStateChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSendDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnReceiveDatagramStateChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mReceiveSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onReceiveDatagramStateChanged()");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnReceiveDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearPointingInfo() {
            mPointingInfo = null;
            mPositionChangeSemaphore.drainPermits();
        }

        public void clearSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("clearSendDatagramStateChanges");
                mSendDatagramStateChanges.clear();
                mSendSemaphore.drainPermits();
            }
        }

        public void clearReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                logd("clearReceiveDatagramStateChanges");
                mReceiveDatagramStateChanges.clear();
                mReceiveSemaphore.drainPermits();
            }
        }

        @Nullable
        public DatagramStateChangeArgument getSendDatagramStateChange(int index) {
            synchronized (mSendDatagramStateChangesLock) {
                if (index < mSendDatagramStateChanges.size()) {
                    return mSendDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getSendDatagramStateChange: invalid index= " + index
                            + " mSendDatagramStateChanges.size= "
                            + mSendDatagramStateChanges.size());
                    return null;
                }
            }
        }

        @Nullable
        public DatagramStateChangeArgument getReceiveDatagramStateChange(int index) {
            synchronized (mReceiveDatagramStateChangesLock) {
                if (index < mReceiveDatagramStateChanges.size()) {
                    return mReceiveDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getReceiveDatagramStateChange: invalid index= " + index
                            + " mReceiveDatagramStateChanges.size= "
                            + mReceiveDatagramStateChanges.size());
                    return null;
                }
            }
        }

        public int getNumOfSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("getNumOfSendDatagramStateChanges size:" + mSendDatagramStateChanges.size());
                return mSendDatagramStateChanges.size();
            }
        }

        public int getNumOfReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                return mReceiveDatagramStateChanges.size();
            }
        }
    }

    protected static class SatelliteProvisionStateCallbackTest implements
            SatelliteProvisionStateCallback {
        public boolean isProvisioned = false;
        private List<Boolean> mProvisionedStates = new ArrayList<>();
        private final Object mProvisionedStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
            isProvisioned = provisioned;
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.add(provisioned);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                loge("onSatelliteProvisionStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteProvisionStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteProvisionStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.clear();
                mSemaphore.drainPermits();
            }
        }

        public int getTotalCountOfProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                return mProvisionedStates.size();
            }
        }

        public boolean getProvisionedState(int index) {
            synchronized (mProvisionedStatesLock) {
                if (index < mProvisionedStates.size()) {
                    return mProvisionedStates.get(index);
                }
            }
            loge("getProvisionedState: invalid index=" + index);
            return false;
        }
    }

    protected static class SatelliteModemStateCallbackTest implements SatelliteModemStateCallback {
        public int modemState = SatelliteManager.SATELLITE_MODEM_STATE_OFF;
        private List<Integer> mModemStates = new ArrayList<>();
        private final Object mModemStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Semaphore mModemOffSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteModemStateChanged(int state) {
            Log.d(TAG, "onSatelliteModemStateChanged: state=" + state);
            modemState = state;
            synchronized (mModemStatesLock) {
                mModemStates.add(state);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.e(TAG, "onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }

            if (state == SatelliteManager.SATELLITE_MODEM_STATE_OFF) {
                try {
                    mModemOffSemaphore.release();
                } catch (Exception ex) {
                    Log.e(TAG, "onSatelliteModemStateChanged: Got exception in "
                            + "releasing mModemOffSemaphore, ex=" + ex);
                }
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        Log.e(TAG, "Timeout to receive onSatelliteModemStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "onSatelliteModemStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilModemOff() {
            try {
                if (!mModemOffSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive satellite modem off event");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "Waiting for satellite modem off event: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilModemOff(long timeoutMillis) {
            try {
                if (!mModemOffSemaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive satellite modem off event");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "Waiting for satellite modem off event: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void clearModemStates() {
            synchronized (mModemStatesLock) {
                Log.d(TAG, "onSatelliteModemStateChanged: clearModemStates");
                mModemStates.clear();
                mSemaphore.drainPermits();
                mModemOffSemaphore.drainPermits();
            }
        }

        public int getModemState(int index) {
            synchronized (mModemStatesLock) {
                if (index < mModemStates.size()) {
                    return mModemStates.get(index);
                } else {
                    Log.e(TAG, "getModemState: invalid index=" + index
                            + ", mModemStates.size=" + mModemStates.size());
                    return -1;
                }
            }
        }

        public int getTotalCountOfModemStates() {
            synchronized (mModemStatesLock) {
                return mModemStates.size();
            }
        }
    }

    protected static class SatelliteDatagramCallbackTest implements SatelliteDatagramCallback {
        public SatelliteDatagram mDatagram;
        public long mDatagramId;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, Consumer<Void> callback) {
            logd("onSatelliteDatagramReceived: datagramId=" + datagramId + ", datagram="
                    + datagram + ", pendingCount=" + pendingCount);
            mDatagram = datagram;
            mDatagramId = datagramId;
            if (callback != null) {
                logd("onSatelliteDatagramReceived: callback.accept() datagramId=" + datagramId);
                callback.accept(null);
            } else {
                logd("onSatelliteDatagramReceived: callback is null datagramId=" + datagramId);
            }

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteDatagramReceived: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteDatagramReceived");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteDatagramReceived: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class NtnSignalStrengthCallbackTest implements NtnSignalStrengthCallback {
        public NtnSignalStrength mNtnSignalStrength;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onNtnSignalStrengthChanged(@NonNull NtnSignalStrength ntnSignalStrength) {
            logd("onNtnSignalStrengthChanged: ntnSignalStrength=" + ntnSignalStrength);
            mNtnSignalStrength = new NtnSignalStrength(ntnSignalStrength);

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onNtnSignalStrengthChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onNtnSignalStrengthChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onNtnSignalStrengthChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class SatelliteCapabilitiesCallbackTest implements
            SatelliteCapabilitiesCallback {
        public SatelliteCapabilities mSatelliteCapabilities;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteCapabilitiesChanged(
                @NonNull SatelliteCapabilities satelliteCapabilities) {
            logd("onSatelliteCapabilitiesChanged: satelliteCapabilities=" + satelliteCapabilities);
            mSatelliteCapabilities = satelliteCapabilities;

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteCapabilitiesChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteCapabilitiesChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteCapabilitiesChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class SatelliteModeRadiosUpdater extends ContentObserver implements
            AutoCloseable {
        private final Context mContext;
        private final Semaphore mSemaphore = new Semaphore(0);
        private String mExpectedSatelliteModeRadios = "";
        private final Object mLock = new Object();

        public SatelliteModeRadiosUpdater(Context context) {
            super(new Handler(Looper.getMainLooper()));
            mContext = context;
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.SATELLITE_MODE_RADIOS), false, this);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                            Manifest.permission.WRITE_SECURE_SETTINGS,
                            Manifest.permission.NETWORK_SETTINGS,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                            Manifest.permission.UWB_PRIVILEGED);
        }

        @Override
        public void onChange(boolean selfChange) {
            String newSatelliteModeRadios = Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
            synchronized (mLock) {
                if (TextUtils.equals(mExpectedSatelliteModeRadios, newSatelliteModeRadios)) {
                    logd("SatelliteModeRadiosUpdater: onChange, newSatelliteModeRadios="
                            + newSatelliteModeRadios);
                    try {
                        mSemaphore.release();
                    } catch (Exception ex) {
                        loge("SatelliteModeRadiosUpdater: onChange, ex=" + ex);
                    }
                }
            }
        }

        @Override
        public void close() throws Exception {
            mContext.getContentResolver().unregisterContentObserver(this);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        public boolean setSatelliteModeRadios(String expectedSatelliteModeRadios) {
            logd("setSatelliteModeRadios: expectedSatelliteModeRadios="
                    + expectedSatelliteModeRadios);
            String originalSatelliteModeRadios =  Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
            if (TextUtils.equals(expectedSatelliteModeRadios, originalSatelliteModeRadios)) {
                logd("setSatelliteModeRadios: satellite radios mode is already as expected");
                return true;
            }

            setExpectedSatelliteModeRadios(expectedSatelliteModeRadios);
            clearSemaphorePermits();
            Settings.Global.putString(mContext.getContentResolver(),
                    Settings.Global.SATELLITE_MODE_RADIOS, expectedSatelliteModeRadios);
            return waitForModeChanged();
        }

        private void clearSemaphorePermits() {
            mSemaphore.drainPermits();
        }

        private boolean waitForModeChanged() {
            logd("SatelliteModeRadiosUpdater: waitForModeChanged start");
            try {
                if (!mSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("SatelliteModeRadiosUpdater: Timeout to wait for mode changed");
                    return false;
                }
            } catch (InterruptedException e) {
                loge("SatelliteModeRadiosUpdater: waitForModeChanged, e=" + e);
                return false;
            }
            return true;
        }

        private void setExpectedSatelliteModeRadios(String expectedSatelliteModeRadios) {
            synchronized (mLock) {
                mExpectedSatelliteModeRadios = expectedSatelliteModeRadios;
            }
            logd("SatelliteModeRadiosUpdater: mExpectedSatelliteModeRadios="
                    + mExpectedSatelliteModeRadios);
        }
    }

    protected static boolean provisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();

        sSatelliteManager.provisionService(
                TOKEN, testProvisionData, null, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("provisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            loge("provisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean deprovisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        sSatelliteManager.deprovisionService(
                TOKEN, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("deprovisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            loge("deprovisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean isSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsProvisioned(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteProvisioned ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isProvisioned = provisioned.get();
        if (error == null) {
            assertNotNull(isProvisioned);
            return isProvisioned;
        } else {
            assertNull(isProvisioned);
            logd("isSatelliteProvisioned error=" + error);
            return false;
        }
    }

    protected static boolean isSatelliteEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };


        sSatelliteManager.requestIsEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            logd("isSatelliteEnabled error=" + error);
            return false;
        }
    }

    protected static boolean isSatelliteDemoModeEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsDemoModeEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteDemoModeEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            logd("isSatelliteEnabled error=" + error);
            return false;
        }
    }

    protected static void requestSatelliteEnabled(boolean enabled) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static void requestSatelliteEnabled(boolean enabled, long timeoutMillis) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static int requestSatelliteEnabledWithResult(boolean enabled, long timeoutMillis) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode = null;
        try {
            errorCode = error.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
        }
        assertNotNull(errorCode);
        return errorCode;
    }

    protected static void requestSatelliteEnabledForDemoMode(boolean enabled) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(enabled).setDemoMode(true).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static void requestSatelliteEnabled(boolean enabled, boolean demoEnabled,
            int expectedError) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(enabled).setDemoMode(demoEnabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(expectedError, (long) errorCode);
    }

    protected static boolean isSatelliteSupported() {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSupported(getContext().getMainExecutor(),
                receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteSupported ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            logd("isSatelliteSupported isSupported=" + isSupported);
            return isSupported;
        } else {
            assertNull(isSupported);
            logd("isSatelliteSupported error=" + error);
            return false;
        }
    }

    protected static void turnRadioOff() {
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.requestRadioPowerOffForReason(TelephonyManager.RADIO_POWER_REASON_USER),
                android.Manifest.permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
    }

    protected static void turnRadioOn() {
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.clearRadioPowerOffForReason(TelephonyManager.RADIO_POWER_REASON_USER),
                android.Manifest.permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
    }

    protected class UwbAdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final Semaphore mUwbSemaphore = new Semaphore(0);
        private final Object mUwbExpectedStateLock = new Object();
        private boolean mUwbExpectedState = false;

        public String toString(int state) {
            switch (state) {
                case UwbManager.AdapterStateCallback.STATE_DISABLED:
                    return "Disabled";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE:
                    return "Inactive";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE:
                    return "Active";

                default:
                    return "";
            }
        }

        @Override
        public void onStateChanged(int state, int reason) {
            logd("UwbAdapterStateCallback onStateChanged() called, state = " + toString(state));
            logd("Adapter state changed reason " + String.valueOf(reason));

            synchronized (mUwbExpectedStateLock) {
                if (mUwbExpectedState == mUwbManager.isUwbEnabled()) {
                    try {
                        mUwbSemaphore.release();
                    } catch (Exception e) {
                        loge("UwbAdapterStateCallback onStateChanged(): Got exception, ex=" + e);
                    }
                }
            }
        }

        public boolean waitUntilOnUwbStateChanged() {
            synchronized (mUwbExpectedStateLock) {
                if (mUwbExpectedState == mUwbManager.isUwbEnabled()) {
                    return true;
                }
            }

            try {
                if (!mUwbSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("UwbAdapterStateCallback Timeout to receive "
                            + "onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("UwbAdapterStateCallback waitUntilOnUwbStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void setUwbExpectedState(boolean expectedState) {
            synchronized (mUwbExpectedStateLock) {
                mUwbExpectedState = expectedState;
                mUwbSemaphore.drainPermits();
            }
        }
    }

    protected class BTWifiNFCStateReceiver extends BroadcastReceiver {
        private final Semaphore mBTSemaphore = new Semaphore(0);
        private final Object mBTExpectedStateLock = new Object();
        private boolean mBTExpectedState = false;

        private final Semaphore mNfcSemaphore = new Semaphore(0);
        private final Object mNfcExpectedStateLock = new Object();
        private boolean mNfcExpectedState = false;

        private final Semaphore mWifiSemaphore = new Semaphore(0);
        private final Object mWifiExpectedStateLock = new Object();
        private boolean mWifiExpectedState = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                logd("BTWifiNFCStateReceiver NULL action for intent " + intent);
                return;
            }
            logd("BTWifiNFCStateReceiver onReceive: action = " + action);

            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    logd("Bluetooth state updated to " + btState);

                    synchronized (mBTExpectedStateLock) {
                        if (mBTExpectedState == mBluetoothAdapter.isEnabled()) {
                            try {
                                mBTSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;

                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    int nfcState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1);
                    logd("Nfc state updated to " + nfcState);

                    synchronized (mNfcExpectedStateLock) {
                        if (mNfcExpectedState == mNfcAdapter.isEnabled()) {
                            try {
                                mNfcSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;

                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN);
                    logd("Wifi state updated to " + wifiState);

                    synchronized (mWifiExpectedStateLock) {
                        if (mWifiExpectedState == mWifiManager.isWifiEnabled()) {
                            try {
                                mWifiSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        public boolean waitUntilOnBTStateChanged() {
            logd("waitUntilOnBTStateChanged");
            synchronized (mBTExpectedStateLock) {
                if (mBTExpectedState == mBluetoothAdapter.isEnabled()) {
                    return true;
                }
            }

            try {
                if (!mBTSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnBTStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnBTStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilOnNfcStateChanged() {
            synchronized (mNfcExpectedStateLock) {
                if (mNfcExpectedState == mNfcAdapter.isEnabled()) {
                    return true;
                }
            }

            try {
                if (!mNfcSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnNfcStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnNfcStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilOnWifiStateChanged() {
            synchronized (mWifiExpectedStateLock) {
                if (mWifiExpectedState == mWifiManager.isWifiEnabled()) {
                    return true;
                }
            }

            try {
                if (!mWifiSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnWifiStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnWifiStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void setBTExpectedState(boolean expectedState) {
            synchronized (mBTExpectedStateLock) {
                mBTExpectedState = expectedState;
                mBTSemaphore.drainPermits();
            }
        }

        public void setWifiExpectedState(boolean expectedState) {
            synchronized (mWifiExpectedStateLock) {
                mWifiExpectedState = expectedState;
                mWifiSemaphore.drainPermits();
            }
        }

        public void setNfcExpectedState(boolean expectedState) {
            synchronized (mNfcExpectedStateLock) {
                mNfcExpectedState = expectedState;
                mNfcSemaphore.drainPermits();
            }
        }
    }

    protected static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    protected static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    protected static void assertSatelliteEnabledInSettings(boolean enabled) {
        int satelliteModeEnabled = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.SATELLITE_MODE_ENABLED, 0);
        if (enabled) {
            assertEquals(satelliteModeEnabled, 1);
        } else {
            assertEquals(satelliteModeEnabled, 0);
        }
        logd("requestSatelliteEnabled: " + enabled
                + " : satelliteModeEnabled from settings: " + satelliteModeEnabled);
    }

    protected static void waitFor(long timeoutMillis) {
        Object delayTimeout = new Object();
        synchronized (delayTimeout) {
            try {
                delayTimeout.wait(timeoutMillis);
            } catch (InterruptedException ex) {
                // Ignore the exception
                logd("waitFor: delayTimeout ex=" + ex);
            }
        }
    }

    // Get default active subscription ID.
    protected int getActiveSubIDForCarrierSatelliteTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> infos = ShellIdentityUtils.invokeMethodWithShellPermissions(sm,
                SubscriptionManager::getActiveSubscriptionInfoList);

        int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        // Couldn't resolve a default. We can try to resolve a default using the active
        // subscriptions.
        if (!infos.isEmpty()) {
            return infos.get(0).getSubscriptionId();
        }
        // There must be at least one active subscription.
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static boolean isSubIdInInfoList(List<SubscriptionInfo> infos, int subId) {
        return infos.stream().anyMatch(info -> info.getSubscriptionId() == subId);
    }
}
