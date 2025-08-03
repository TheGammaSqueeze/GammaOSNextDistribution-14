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


import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;

import android.annotation.ArrayRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.cts.externalpointingui.ExternalMockPointingUi;
import android.telephony.cts.externalsatellitegatewayservice.ExternalMockSatelliteGatewayService;
import android.telephony.cts.externalsatellitegatewayservice.IExternalMockSatelliteGatewayService;
import android.telephony.cts.externalsatellitegatewayservice.IExternalSatelliteGatewayListener;
import android.telephony.cts.externalsatelliteservice.ExternalMockSatelliteService;
import android.telephony.cts.externalsatelliteservice.IExternalMockSatelliteService;
import android.telephony.cts.externalsatelliteservice.IExternalSatelliteListener;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.satellite.stub.PointingInfo;
import android.telephony.satellite.stub.SatelliteDatagram;
import android.text.TextUtils;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for connecting Telephony framework and CTS to the MockSatelliteService.
 */
class MockSatelliteServiceManager {
    private static final String TAG = "MockSatelliteServiceManager";
    private static final String PACKAGE = "android.telephony.cts";
    private static final String EXTERNAL_SATELLITE_GATEWAY_PACKAGE =
            ExternalMockSatelliteGatewayService.class.getPackage().getName();
    private static final String EXTERNAL_SATELLITE_PACKAGE =
            ExternalMockSatelliteService.class.getPackage().getName();
    private static final String EXTERNAL_POINTING_UI_PACKAGE =
            ExternalMockPointingUi.class.getPackage().getName();
    private static final String SET_SATELLITE_SERVICE_PACKAGE_NAME_CMD =
            "cmd phone set-satellite-service-package-name -s ";
    private static final String SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME_CMD =
            "cmd phone set-satellite-gateway-service-package-name -s ";
    private static final String SET_SATELLITE_LISTENING_TIMEOUT_DURATION_CMD =
            "cmd phone set-satellite-listening-timeout-duration -t ";
    private static final String SET_SATELLITE_POINTING_UI_CLASS_NAME_CMD =
            "cmd phone set-satellite-pointing-ui-class-name";
    private static final String SET_DATAGRAM_CONTROLLER_TIMEOUT_DURATION_CMD =
            "cmd phone set-datagram-controller-timeout-duration ";

    private static final String SET_SATELLITE_CONTROLLER_TIMEOUT_DURATION_CMD =
            "cmd phone set-satellite-controller-timeout-duration ";
    private static final String SET_SHOULD_SEND_DATAGRAM_TO_MODEM_IN_DEMO_MODE =
            "cmd phone set-should-send-datagram-to-modem-in-demo-mode ";
    private static final String SET_COUNTRY_CODES = "cmd phone set-country-codes";
    private static final String SET_SATELLITE_ACCESS_CONTROL_OVERLAY_CONFIGS =
            "cmd phone set-satellite-access-control-overlay-configs";
    private static final long TIMEOUT = 5000;
    @NonNull private ActivityManager mActivityManager;
    @NonNull private UidImportanceListener mUidImportanceListener = new UidImportanceListener();

    private MockSatelliteService mSatelliteService;
    private TestSatelliteServiceConnection mSatelliteServiceConn;
    private IExternalMockSatelliteService mExternalSatelliteService;
    private ExternalSatelliteServiceConnection mExternalSatelliteServiceConn;
    private final Semaphore mRemoteServiceConnectedSemaphore = new Semaphore(0);
    private final Semaphore mExternalServiceDisconnectedSemaphore = new Semaphore(0);
    private MockSatelliteGatewayService mSatelliteGatewayService;
    private TestSatelliteGatewayServiceConnection mSatelliteGatewayServiceConn;
    private IExternalMockSatelliteGatewayService mExternalSatelliteGatewayService;
    private ExternalSatelliteGatewayServiceConnection mExternalSatelliteGatewayServiceConn;
    private final Semaphore mRemoteGatewayServiceConnectedSemaphore = new Semaphore(0);
    private final Semaphore mRemoteGatewayServiceDisconnectedSemaphore = new Semaphore(0);
    private final Semaphore mExternalGatewayServiceDisconnectedSemaphore = new Semaphore(0);
    private Instrumentation mInstrumentation;
    private final Semaphore mStartSendingPointingInfoSemaphore = new Semaphore(0);
    private final Semaphore mStopSendingPointingInfoSemaphore = new Semaphore(0);
    private final Semaphore mPollPendingDatagramsSemaphore = new Semaphore(0);
    private final Semaphore mStopPointingUiSemaphore = new Semaphore(0);
    private final Semaphore mSendDatagramsSemaphore = new Semaphore(0);
    private final Semaphore mRequestSatelliteEnabledSemaphore = new Semaphore(0);
    private final Object mRequestSatelliteEnabledLock = new Object();
    private List<SatelliteDatagram> mSentSatelliteDatagrams = new ArrayList<>();
    private List<Boolean> mSentIsEmergencyList = new ArrayList<>();
    private final Object mSendDatagramLock = new Object();
    private final Semaphore mListeningEnabledSemaphore = new Semaphore(0);
    private List<Boolean> mListeningEnabledList = new ArrayList<>();
    private final Object mListeningEnabledLock = new Object();
    private final Semaphore mMockPointingUiActivitySemaphore = new Semaphore(0);
    private final MockPointingUiActivityStatusReceiver mMockPointingUiActivityStatusReceiver =
            new MockPointingUiActivityStatusReceiver(mMockPointingUiActivitySemaphore);
    private final boolean mIsSatelliteServicePackageConfigured;
    boolean mIsPointingUiOverridden = false;
    private final Semaphore mSetSatellitePlmnSemaphore = new Semaphore(0);

    @NonNull
    private final ILocalSatelliteListener mSatelliteListener =
            new ILocalSatelliteListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("onRemoteServiceConnected");
                    mRemoteServiceConnectedSemaphore.release();
                }

                @Override
                public void onStartSendingSatellitePointingInfo() {
                    logd("onStartSendingSatellitePointingInfo");
                    try {
                        mStartSendingPointingInfoSemaphore.release();
                    } catch (Exception ex) {
                        logd("onStartSendingSatellitePointingInfo: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onStopSendingSatellitePointingInfo() {
                    logd("onStopSendingSatellitePointingInfo");
                    try {
                        mStopSendingPointingInfoSemaphore.release();
                    } catch (Exception ex) {
                        logd("onStopSendingSatellitePointingInfo: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onPollPendingSatelliteDatagrams() {
                    logd("onPollPendingSatelliteDatagrams");
                    try {
                        mPollPendingDatagramsSemaphore.release();
                    } catch (Exception ex) {
                        logd("onPollPendingSatelliteDatagrams: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onSendSatelliteDatagram(
                        SatelliteDatagram datagram, boolean isEmergency) {
                    logd("onSendSatelliteDatagram");
                    synchronized (mSendDatagramLock) {
                        mSentSatelliteDatagrams.add(datagram);
                        mSentIsEmergencyList.add(isEmergency);
                    }
                    try {
                        mSendDatagramsSemaphore.release();
                    } catch (Exception ex) {
                        logd("onSendSatelliteDatagram: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onSatelliteListeningEnabled(boolean enabled) {
                    logd("onSatelliteListeningEnabled: enabled=" + enabled);
                    synchronized (mListeningEnabledLock) {
                        mListeningEnabledList.add(enabled);
                    }
                    try {
                        mListeningEnabledSemaphore.release();
                    } catch (Exception ex) {
                        logd("onSatelliteListeningEnabled: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onSetSatellitePlmn() {
                    logd("onSetSatellitePlmn()");
                    try {
                        mSetSatellitePlmnSemaphore.release();
                    } catch (Exception ex) {
                        logd("onSetSatellitePlmn: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onRequestSatelliteEnabled(boolean enableSatellite) {
                    logd("onRequestSatelliteEnabled: enableSatellite=" + enableSatellite);
                    try {
                        mRequestSatelliteEnabledSemaphore.release();
                    } catch (Exception ex) {
                        logd("onRequestSatelliteEnabled: Got exception, ex=" + ex);
                    }
                }
            };

    @NonNull
    private final ILocalSatelliteGatewayListener mSatelliteGatewayListener =
            new ILocalSatelliteGatewayListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("ILocalSatelliteGatewayListener: onRemoteServiceConnected");
                    mRemoteGatewayServiceConnectedSemaphore.release();
                }

                @Override
                public void onRemoteServiceDisconnected() {
                    logd("ILocalSatelliteGatewayListener: onRemoteServiceDisconnected");
                    mRemoteGatewayServiceDisconnectedSemaphore.release();
                }
            };

    private class TestSatelliteServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        TestSatelliteServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("onServiceConnected");
            mSatelliteService = ((MockSatelliteService.LocalBinder) service).getService();
            mSatelliteService.setLocalSatelliteListener(mSatelliteListener);
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("onServiceDisconnected");
            mSatelliteService = null;
        }
    }

    private class ExternalSatelliteServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        ExternalSatelliteServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("ExternalSatelliteService: onServiceConnected");
            mExternalSatelliteService =
                    IExternalMockSatelliteService.Stub.asInterface(service);
            try {
                mExternalSatelliteService.setExternalSatelliteListener(
                        mExternalSatelliteListener);
                mLatch.countDown();
            } catch (RemoteException ex) {
                loge("ExternalSatelliteService: setExternalSatelliteListener ex=" + ex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("ExternalSatelliteService: onServiceDisconnected");
            mExternalSatelliteService = null;
            try {
                mExternalServiceDisconnectedSemaphore.release();
            } catch (Exception ex) {
                loge("ExternalSatelliteService: ex=" + ex);
            }
        }
    }

    private class UidImportanceListener implements ActivityManager.OnUidImportanceListener {
        @Override
        public void onUidImportance(int uid, int importance) {
            if (importance != IMPORTANCE_GONE) return;
            final PackageManager pm = mInstrumentation.getContext().getPackageManager();
            final String[] callerPackages = pm.getPackagesForUid(uid);
            if (callerPackages != null) {
                if (Arrays.stream(callerPackages)
                        .anyMatch(EXTERNAL_POINTING_UI_PACKAGE::contains)) {
                    mStopPointingUiSemaphore.release();
                }
            }
        }
    }

    @NonNull
    private final IExternalSatelliteListener mExternalSatelliteListener =
            new IExternalSatelliteListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("IExternalSatelliteListener: onRemoteServiceConnected");
                    mRemoteServiceConnectedSemaphore.release();
                }
            };

    private class TestSatelliteGatewayServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        TestSatelliteGatewayServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("GatewayService: onServiceConnected");
            mSatelliteGatewayService =
                    ((MockSatelliteGatewayService.LocalBinder) service).getService();
            mSatelliteGatewayService.setLocalSatelliteListener(mSatelliteGatewayListener);
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("GatewayService: onServiceDisconnected");
            mSatelliteGatewayService = null;
        }
    }

    @NonNull
    private final IExternalSatelliteGatewayListener mExternalSatelliteGatewayListener =
            new IExternalSatelliteGatewayListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("IExternalSatelliteGatewayListener: onRemoteServiceConnected");
                    mRemoteGatewayServiceConnectedSemaphore.release();
                }
            };

    private class ExternalSatelliteGatewayServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        ExternalSatelliteGatewayServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("ExternalGatewayService: onServiceConnected");
            mExternalSatelliteGatewayService =
                    IExternalMockSatelliteGatewayService.Stub.asInterface(service);
            try {
                mExternalSatelliteGatewayService.setExternalSatelliteGatewayListener(
                        mExternalSatelliteGatewayListener);
                mLatch.countDown();
            } catch (RemoteException ex) {
                loge("ExternalGatewayService: setExternalSatelliteGatewayListener ex=" + ex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("ExternalGatewayService: onServiceDisconnected");
            mExternalSatelliteGatewayService = null;
            try {
                mExternalGatewayServiceDisconnectedSemaphore.release();
            } catch (Exception ex) {
                loge("ExternalGatewayService: ex=" + ex);
            }
        }
    }

    MockSatelliteServiceManager(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mIsSatelliteServicePackageConfigured = !TextUtils.isEmpty(getSatelliteServicePackageName());
    }

    boolean connectSatelliteService() {
        logd("connectSatelliteService starting ...");
        if (!setupLocalSatelliteService()) {
            loge("Failed to set up local satellite service");
            return false;
        }

        try {
            if (!setSatelliteServicePackageName(PACKAGE)) {
                loge("Failed to set satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectSatelliteService: Got exception with setSatelliteServicePackageName "
                    + "ex=" + ex);
            return false;
        }

        // Wait for SatelliteModemInterface connecting to MockSatelliteService.
        return waitForRemoteSatelliteServiceConnected(1);
    }

    boolean connectExternalSatelliteService() {
        logd("connectExternalSatelliteService starting ...");
        if (!setupExternalSatelliteService()) {
            loge("Failed to set up external satellite service");
            return false;
        }

        try {
            if (!setSatelliteServicePackageName(EXTERNAL_SATELLITE_PACKAGE)) {
                loge("Failed to set satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectExternalSatelliteService: Got exception with "
                    + "setSatelliteServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean setupExternalSatelliteService() {
        logd("setupExternalSatelliteService start");
        if (mExternalSatelliteService != null) {
            logd("setupExternalSatelliteService: external service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mExternalSatelliteServiceConn = new ExternalSatelliteServiceConnection(latch);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(EXTERNAL_SATELLITE_PACKAGE,
                ExternalMockSatelliteService.class.getName()));
        mInstrumentation.getContext().bindService(intent, mExternalSatelliteServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupExternalSatelliteService: Got InterruptedException e=" + e);
            return false;
        }
    }

    boolean connectSatelliteGatewayService() {
        logd("connectSatelliteGatewayService starting ...");
        if (!setupLocalSatelliteGatewayService()) {
            loge("Failed to set up local satellite gateway service");
            return false;
        }

        try {
            if (!setSatelliteGatewayServicePackageName(PACKAGE)) {
                loge("Failed to set satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectSatelliteGatewayService: Got exception with "
                    + "setSatelliteGatewayServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean connectExternalSatelliteGatewayService() {
        logd("connectExternalSatelliteGatewayService starting ...");
        if (!setupExternalSatelliteGatewayService()) {
            loge("Failed to set up external satellite gateway service");
            return false;
        }

        try {
            if (!setSatelliteGatewayServicePackageName(EXTERNAL_SATELLITE_GATEWAY_PACKAGE)) {
                loge("Failed to set satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectExternalSatelliteGatewayService: Got exception with "
                    + "setSatelliteGatewayServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean setupExternalSatelliteGatewayService() {
        logd("setupExternalSatelliteGatewayService start");
        if (mExternalSatelliteGatewayService != null) {
            logd("setupExternalSatelliteGatewayService: external service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mExternalSatelliteGatewayServiceConn = new ExternalSatelliteGatewayServiceConnection(latch);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(EXTERNAL_SATELLITE_GATEWAY_PACKAGE,
                ExternalMockSatelliteGatewayService.class.getName()));
        mInstrumentation.getContext().bindService(intent, mExternalSatelliteGatewayServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupExternalSatelliteGatewayService: Got InterruptedException e=" + e);
            return false;
        }
    }

    boolean restoreSatelliteServicePackageName() {
        logd("restoreSatelliteServicePackageName");
        try {
            if (!setSatelliteServicePackageName(null)) {
                loge("Failed to restore satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatelliteServicePackageName: Got exception with "
                    + "setSatelliteServicePackageName ex=" + ex);
            return false;
        }
        return true;
    }

    boolean restoreSatelliteGatewayServicePackageName() {
        logd("restoreSatelliteGatewayServicePackageName");
        if (mSatelliteGatewayServiceConn != null) {
            mInstrumentation.getContext().unbindService(mSatelliteGatewayServiceConn);
        }
        mSatelliteGatewayServiceConn = null;
        mSatelliteGatewayService = null;
        try {
            if (!setSatelliteGatewayServicePackageName(null)) {
                loge("Failed to restore satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatelliteGatewayServicePackageName: Got exception with "
                    + "setSatelliteGatewayServicePackageName ex=" + ex);
            return false;
        }
        return true;
    }

    boolean overrideSatellitePointingUiClassName() {
        logd("overrideSatellitePointingUiClassName");
        try {
            if (!setSatellitePointingUiClassName(
                    PACKAGE, MockPointingUiActivity.class.getName())) {
                loge("Failed to override satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("overrideSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        registerForMockPointingUiActivityStatus();
        mIsPointingUiOverridden = true;
        return true;
    }

    boolean overrideExternalSatellitePointingUiClassName() {
        logd("overrideExternalSatellitePointingUiClassName");
        try {
            mActivityManager = mInstrumentation.getContext()
                    .getSystemService(ActivityManager.class);
            mActivityManager.addOnUidImportanceListener(mUidImportanceListener, IMPORTANCE_GONE);
            if (!setSatellitePointingUiClassName(
                    EXTERNAL_POINTING_UI_PACKAGE, ExternalMockPointingUi.class.getName())) {
                loge("Failed to override external satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("overrideExternalSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        registerForExternalMockPointingUiActivityStatus();
        mIsPointingUiOverridden = true;
        return true;
    }
    boolean restoreSatellitePointingUiClassName() {
        logd("restoreSatellitePointingUiClassName");
        if (!mIsPointingUiOverridden) {
            return true;
        }
        try {
            if (!setSatellitePointingUiClassName(null, null)) {
                loge("Failed to restore satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        unregisterForMockPointingUiActivityStatus();
        mIsPointingUiOverridden = false;
        return true;
    }

    boolean isSatelliteServicePackageConfigured() {
        return mIsSatelliteServicePackageConfigured;
    }

    Boolean getSentIsEmergency(int index) {
        synchronized (mSendDatagramLock) {
            if (index >= mSentIsEmergencyList.size()) return null;
            return mSentIsEmergencyList.get(index);
        }
    }

    SatelliteDatagram getSentSatelliteDatagram(int index) {
        synchronized (mSendDatagramLock) {
            if (index >= mSentSatelliteDatagrams.size()) return null;
            return mSentSatelliteDatagrams.get(index);
        }
    }

    void clearSentSatelliteDatagramInfo() {
        synchronized (mSendDatagramLock) {
            mSentSatelliteDatagrams.clear();
            mSentIsEmergencyList.clear();
            mSendDatagramsSemaphore.drainPermits();
        }
    }

    void clearRequestSatelliteEnabledInfo() {
        synchronized (mRequestSatelliteEnabledLock) {
            mRequestSatelliteEnabledSemaphore.drainPermits();
        }
    }

    int getTotalCountOfSentSatelliteDatagrams() {
        synchronized (mSendDatagramLock) {
            return mSentSatelliteDatagrams.size();
        }
    }

    Boolean getListeningEnabled(int index) {
        synchronized (mListeningEnabledLock) {
            if (index >= mListeningEnabledList.size()) return null;
            return mListeningEnabledList.get(index);
        }
    }

    int getTotalCountOfListeningEnabledList() {
        synchronized (mListeningEnabledLock) {
            return mListeningEnabledList.size();
        }
    }

    void clearListeningEnabledList() {
        synchronized (mListeningEnabledLock) {
            mListeningEnabledList.clear();
            mListeningEnabledSemaphore.drainPermits();
        }
    }

    void clearMockPointingUiActivityStatusChanges() {
        mMockPointingUiActivitySemaphore.drainPermits();
    }

    void clearRemoteGatewayServiceConnectedStatusChanges() {
        mRemoteGatewayServiceConnectedSemaphore.drainPermits();
    }

    void clearRemoteGatewayServiceDisconnectedStatusChanges() {
        mRemoteGatewayServiceDisconnectedSemaphore.drainPermits();
    }

    void clearStopPointingUiActivity() {
        mStopPointingUiSemaphore.drainPermits();
    }

    boolean waitForEventOnStartSendingSatellitePointingInfo(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStartSendingPointingInfoSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onStartSendingSatellitePointingInfo");
                    return false;
                }
            } catch (Exception ex) {
                loge("onStartSendingSatellitePointingInfo: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnStopSendingSatellitePointingInfo(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStopSendingPointingInfoSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onStopSendingSatellitePointingInfo");
                    return false;
                }
            } catch (Exception ex) {
                loge("onStopSendingSatellitePointingInfo: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnPollPendingSatelliteDatagrams(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mPollPendingDatagramsSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onPollPendingSatelliteDatagrams");
                    return false;
                }
            } catch (Exception ex) {
                loge("onPollPendingSatelliteDatagrams: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    void clearPollPendingDatagramPermits() {
        mPollPendingDatagramsSemaphore.drainPermits();
    }

    boolean waitForEventOnSendSatelliteDatagram(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSendDatagramsSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onSendSatelliteDatagram");
                    return false;
                }
            } catch (Exception ex) {
                loge("onSendSatelliteDatagram: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnRequestSatelliteEnabled(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRequestSatelliteEnabledSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive OnRequestSatelliteEnabled");
                    return false;
                }
            } catch (Exception ex) {
                loge("OnRequestSatelliteEnabled: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnSatelliteListeningEnabled(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mListeningEnabledSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onSatelliteListeningEnabled");
                    return false;
                }
            } catch (Exception ex) {
                loge("onSatelliteListeningEnabled: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteServiceConnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteServiceConnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteSatelliteServiceConnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteSatelliteServiceConnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteGatewayServiceConnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteGatewayServiceConnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteSatelliteGatewayServiceConnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteSatelliteGatewayServiceConnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteGatewayServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteGatewayServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteGatewayServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteGatewayServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForExternalSatelliteServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mExternalServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive ExternalServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("ExternalServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForExternalSatelliteGatewayServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mExternalGatewayServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive ExternalGatewayServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("ExternalGatewayServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventMockPointingUiActivityStarted(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mMockPointingUiActivitySemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive MockPointingUiActivityStarted");
                    return false;
                }
            } catch (Exception ex) {
                loge("MockPointingUiActivityStarted: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventMockPointingUiActivityStopped(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStopPointingUiSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive StopPointingUiSemaphore");
                    return false;
                }
            } catch (Exception ex) {
                loge("StopPointingUiSemaphore: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnSetSatellitePlmn(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSetSatellitePlmnSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onSetSatellitePlmn");
                    return false;
                }
            } catch (Exception ex) {
                loge("onSetSatellitePlmn: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    void setErrorCode(int errorCode) {
        logd("setErrorCode: errorCode=" + errorCode);
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.setErrorCode(errorCode);
    }

    void setEnableCellularScanningErrorCode(int errorCode) {
        logd("setEnableCellularScanningErrorCode: errorCode=" + errorCode);
        if (mSatelliteService == null) {
            loge("setEnableCellularScanningErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.setEnableCellularScanningErrorCode(errorCode);
    }

    void setSupportedRadioTechnologies(@NonNull int[] supportedRadioTechnologies) {
        logd("setSupportedRadioTechnologies: " + supportedRadioTechnologies[0]);
        if (mSatelliteService == null) {
            loge("setSupportedRadioTechnologies: mSatelliteService is null");
            return;
        }
        mSatelliteService.setSupportedRadioTechnologies(supportedRadioTechnologies);
    }

    void setSatelliteSupport(boolean supported) {
        logd("setSatelliteSupport: supported=" + supported);
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.setSatelliteSupport(supported);
    }

    public void setShouldRespondTelephony(boolean shouldRespondTelephony) {
        logd("setShouldRespondTelephony: shouldRespondTelephony=" + shouldRespondTelephony);
        if (mSatelliteService == null) {
            loge("setShouldRespondTelephony: mSatelliteService is null");
            return;
        }
        mSatelliteService.setShouldRespondTelephony(shouldRespondTelephony);
    }

    void setNtnSignalStrength(
            android.telephony.satellite.stub.NtnSignalStrength ntnSignalStrength) {
        if (mSatelliteService == null) {
            loge("setNtnSignalStrength: mSatelliteService is null");
            return;
        }
        mSatelliteService.setNtnSignalStrength(ntnSignalStrength);
    }

    void setSatelliteCommunicationAllowed(boolean allowed) {
        logd("setSatelliteCommunicationAllowed: allowed=" + allowed);
        if (mSatelliteService == null) {
            loge("setSatelliteCommunicationAllowed: mSatelliteService is null");
            return;
        }
        mSatelliteService.setSatelliteCommunicationAllowed(allowed);
    }

    void sendOnSatelliteDatagramReceived(SatelliteDatagram datagram, int pendingCount) {
        logd("sendOnSatelliteDatagramReceived");
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnSatelliteDatagramReceived(datagram, pendingCount);
    }

    void sendOnPendingDatagrams() {
        logd("sendOnPendingDatagrams");
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnPendingDatagrams();
    }

    void sendOnSatellitePositionChanged(PointingInfo pointingInfo) {
        logd("sendOnSatellitePositionChanged");
        mSatelliteService.sendOnSatellitePositionChanged(pointingInfo);
    }

    void sendOnSatelliteModemStateChanged(int modemState) {
        logd("sendOnSatelliteModemStateChanged: " + modemState);
        if (mSatelliteService == null) {
            loge("sendOnSatelliteModemStateChanged: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnSatelliteModemStateChanged(modemState);
    }

    void sendOnNtnSignalStrengthChanged(
            android.telephony.satellite.stub.NtnSignalStrength ntnSignalStrength) {
        logd("sendOnNtnSignalStrengthChanged: " + ntnSignalStrength.signalStrengthLevel);
        if (mSatelliteService == null) {
            loge("sendOnNtnSignalStrengthChanged: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnNtnSignalStrengthChanged(ntnSignalStrength);
    }

    void sendOnSatelliteCapabilitiesChanged(
            android.telephony.satellite.stub.SatelliteCapabilities satelliteCapabilities) {
        logd("sendOnSatelliteCapabilitiesChanged: " + satelliteCapabilities);
        if (mSatelliteService == null) {
            loge("sendOnSatelliteCapabilitiesChanged: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnSatelliteCapabilitiesChanged(satelliteCapabilities);
    }

    boolean setSatelliteListeningTimeoutDuration(long timeoutMillis) {
        try {
            String result =
                    TelephonyUtils.executeShellCommand(mInstrumentation,
                            SET_SATELLITE_LISTENING_TIMEOUT_DURATION_CMD + timeoutMillis);
            logd("setSatelliteListeningTimeoutDuration: result = " + result);
            return "true".equals(result);
        } catch (Exception e) {
            loge("setSatelliteListeningTimeoutDuration: e=" + e);
            return false;
        }
    }

    boolean setDatagramControllerTimeoutDuration(
            boolean reset, int timeoutType, long timeoutMillis) {
        StringBuilder command = new StringBuilder();
        command.append(SET_DATAGRAM_CONTROLLER_TIMEOUT_DURATION_CMD);
        if (reset) {
            command.append("-r");
        }
        command.append(" -t " + timeoutType);
        command.append(" -d " + timeoutMillis);

        try {
            String result =
                    TelephonyUtils.executeShellCommand(mInstrumentation, command.toString());
            logd("setDatagramControllerTimeoutDuration: result = " + result);
            return "true".equals(result);
        } catch (Exception e) {
            loge("setDatagramControllerTimeoutDuration: e=" + e);
            return false;
        }
    }

    boolean setSatelliteControllerTimeoutDuration(
            boolean reset, int timeoutType, long timeoutMillis) {
        StringBuilder command = new StringBuilder();
        command.append(SET_SATELLITE_CONTROLLER_TIMEOUT_DURATION_CMD);
        if (reset) {
            command.append("-r");
        }
        command.append(" -t " + timeoutType);
        command.append(" -d " + timeoutMillis);

        try {
            String result =
                    TelephonyUtils.executeShellCommand(mInstrumentation, command.toString());
            logd("setSatelliteControllerTimeoutDuration: result = " + result);
            return "true".equals(result);
        } catch (Exception e) {
            loge("setSatelliteControllerTimeoutDuration: e=" + e);
            return false;
        }
    }

    boolean setShouldSendDatagramToModemInDemoMode(boolean shouldSendToDemoMode) {
        try {
            String result = TelephonyUtils.executeShellCommand(mInstrumentation,
                    SET_SHOULD_SEND_DATAGRAM_TO_MODEM_IN_DEMO_MODE
                            + (shouldSendToDemoMode ? "true" : "false"));
            logd("setShouldSendDatagramToModemInDemoMode(" + shouldSendToDemoMode + "): result = "
                    + result);
            return true;
        } catch (Exception e) {
            loge("setShouldSendDatagramToModemInDemoMode: e=" + e);
            return false;
        }
    }

    void setWaitToSend(boolean wait) {
        logd("setWaitToSend: wait= " + wait);
        if (mSatelliteService == null) {
            loge("setWaitToSend: mSatelliteService is null");
            return;
        }
        mSatelliteService.setWaitToSend(wait);
    }

    boolean sendSavedDatagram() {
        logd("sendSavedDatagram");
        if (mSatelliteService == null) {
            loge("sendSavedDatagram: mSatelliteService is null");
            return false;
        }
        return mSatelliteService.sendSavedDatagram();
    }

    boolean respondToRequestSatelliteEnabled(boolean isEnabled) {
        logd("respondToRequestSatelliteEnabled, isEnabled=" + isEnabled);
        if (mSatelliteService == null) {
            loge("respondToRequestSatelliteEnabled: mSatelliteService is null");
            return false;
        }
        return mSatelliteService.respondToRequestSatelliteEnabled(isEnabled);
    }

    boolean stopExternalSatelliteService() {
        logd("stopExternalSatelliteService");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_SATELLITE_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalSatelliteService: ex=" + ex);
            return false;
        }
    }

    boolean stopExternalSatelliteGatewayService() {
        logd("stopExternalSatelliteGatewayService");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_SATELLITE_GATEWAY_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalSatelliteGatewayService: ex=" + ex);
            return false;
        }
    }

    void resetSatelliteService() {
        mSatelliteService = null;
    }

    boolean stopExternalMockPointingUi() {
        logd("stopExternalMockPointingUi");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_POINTING_UI_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalMockPointingUi: ex=" + ex);
            return false;
        }
    }

    @Nullable List<String> getCarrierPlmnList() {
        if (mSatelliteService == null) {
            loge("getCarrierPlmnList: mSatelliteService is null");
            return null;
        }
        return mSatelliteService.getCarrierPlmnList();
    }

    @Nullable List<String> getAllSatellitePlmnList() {
        if (mSatelliteService == null) {
            loge("getAllSatellitePlmnList: mSatelliteService is null");
            return null;
        }
        return mSatelliteService.getAllSatellitePlmnList();
    }

    @Nullable Boolean getIsSatelliteEnabledForCarrier() {
        if (mSatelliteService == null) {
            loge("getIsSatelliteEnabledForCarrier: mSatelliteService is null");
            return null;
        }
        return mSatelliteService.getIsSatelliteEnabledForCarrier();
    }

    void clearSatelliteEnabledForCarrier() {
        if (mSatelliteService == null) {
            loge("clearSatelliteEnabledForCarrier: mSatelliteService is null");
            return;
        }
        mSatelliteService.clearSatelliteEnabledForCarrier();
    }

    /**
     * Set whether provisioning API should be supported
     */
    void setProvisioningApiSupported(boolean provisioningApiSupported) {
        if (mSatelliteService == null) {
            loge("setProvisioningApiSupported: mSatelliteService is null");
            return;
        }
        mSatelliteService.setProvisioningApiSupported(provisioningApiSupported);
    }

    @NonNull List<String> getPlmnListFromOverlayConfig() {
        String[] plmnArr = readStringArrayFromOverlayConfig(
                R.array.config_satellite_providers);
        return Arrays.stream(plmnArr).toList();
    }

    /** Set telephony country codes */
    boolean setCountryCodes(boolean reset, @Nullable String currentNetworkCountryCodes,
            @Nullable String cachedNetworkCountryCodes, @Nullable String locationCountryCode,
            long locationCountryCodeTimestampNanos) {
        logd("setCountryCodes: reset= " + reset + ", currentNetworkCountryCodes="
                + currentNetworkCountryCodes + ", cachedNetworkCountryCodes="
                + cachedNetworkCountryCodes + ", locationCountryCode=" + locationCountryCode
                + ", locationCountryCodeTimestampNanos=" + locationCountryCodeTimestampNanos);
        try {
            StringBuilder command = new StringBuilder();
            command.append(SET_COUNTRY_CODES);
            if (reset) {
                command.append(" -r");
            }
            if (!TextUtils.isEmpty(currentNetworkCountryCodes)) {
                command.append(" -n ");
                command.append(currentNetworkCountryCodes);
            }
            if (!TextUtils.isEmpty(cachedNetworkCountryCodes)) {
                command.append(" -c ");
                command.append(cachedNetworkCountryCodes);
            }
            if (!TextUtils.isEmpty(locationCountryCode)) {
                command.append(" -l ");
                command.append(locationCountryCode);
                command.append(" -t ");
                command.append(locationCountryCodeTimestampNanos);
            }
            TelephonyUtils.executeShellCommand(mInstrumentation, command.toString());
            return true;
        } catch (Exception ex) {
            loge("setCountryCodes: ex= " + ex);
            return false;
        }
    }

    /** Set overlay configs for satellite access controller */
    boolean setSatelliteAccessControlOverlayConfigs(boolean reset, boolean isAllowed,
            @Nullable String s2CellFile, long locationFreshDurationNanos,
            @Nullable String satelliteCountryCodes) {
        logd("setSatelliteAccessControlOverlayConfigs");
        try {
            StringBuilder command = new StringBuilder();
            command.append(SET_SATELLITE_ACCESS_CONTROL_OVERLAY_CONFIGS);
            if (reset) {
                command.append(" -r");
            } else {
                if (isAllowed) {
                    command.append(" -a");
                }
                if (!TextUtils.isEmpty(s2CellFile)) {
                    command.append(" -f ");
                    command.append(s2CellFile);
                }
                command.append(" -d ");
                command.append(locationFreshDurationNanos);
                if (!TextUtils.isEmpty(satelliteCountryCodes)) {
                    command.append(" -c ");
                    command.append(satelliteCountryCodes);
                }
            }
            logd("command=" + command);
            TelephonyUtils.executeShellCommand(mInstrumentation, command.toString());
            return true;
        } catch (Exception ex) {
            loge("setSatelliteAccessControlOverlayConfigs: ex= " + ex);
            return false;
        }
    }

    @NonNull private String[] readStringArrayFromOverlayConfig(@ArrayRes int id) {
        String[] strArray = null;
        try {
            strArray = mInstrumentation.getContext().getResources().getStringArray(id);
        } catch (Resources.NotFoundException ex) {
            loge("readStringArrayFromOverlayConfig: id= " + id + ", ex=" + ex);
        }
        if (strArray == null) {
            strArray = new String[0];
        }
        return strArray;
    }

    private boolean setupLocalSatelliteService() {
        if (mSatelliteService != null) {
            logd("setupLocalSatelliteService: local service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mSatelliteServiceConn = new TestSatelliteServiceConnection(latch);
        mInstrumentation.getContext().bindService(new Intent(mInstrumentation.getContext(),
                MockSatelliteService.class), mSatelliteServiceConn, Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupLocalSatelliteService: Got InterruptedException e=" + e);
            return false;
        }
    }

    private boolean setupLocalSatelliteGatewayService() {
        if (mSatelliteGatewayService != null) {
            logd("setupLocalSatelliteGatewayService: local service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mSatelliteGatewayServiceConn = new TestSatelliteGatewayServiceConnection(latch);
        mInstrumentation.getContext().bindService(new Intent(mInstrumentation.getContext(),
                MockSatelliteGatewayService.class), mSatelliteGatewayServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupLocalSatelliteGatewayService: Got InterruptedException e=" + e);
            return false;
        }
    }

    private boolean setSatelliteServicePackageName(@Nullable String packageName) {
        try {
            TelephonyUtils.executeShellCommand(
                    mInstrumentation, SET_SATELLITE_SERVICE_PACKAGE_NAME_CMD + packageName);
            return true;
        } catch (Exception ex) {
            loge("setSatelliteServicePackageName: ex= " + ex);
            return false;
        }
    }

    private boolean setSatelliteGatewayServicePackageName(
            @Nullable String packageName) throws Exception {
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME_CMD + packageName);
            return true;
        } catch (Exception ex) {
            loge("setSatelliteGatewayServicePackageName: ex=" + ex);
            return false;
        }
    }

    private boolean setSatellitePointingUiClassName(
            @Nullable String packageName, @Nullable String className)  {
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    SET_SATELLITE_POINTING_UI_CLASS_NAME_CMD + " -p " + packageName
                            + " -c " + className);
            return true;
        } catch (Exception ex) {
            loge("setSatellitePointingUiClassName: ex = " + ex);
            return false;
        }
    }

    private void registerForMockPointingUiActivityStatus() {
        IntentFilter intentFilter = new IntentFilter(
                MockPointingUiActivity.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED);
        mInstrumentation.getContext().registerReceiver(
                mMockPointingUiActivityStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    private void registerForExternalMockPointingUiActivityStatus() {
        logd("registerForExternalMockPointingUiActivityStatus");
        IntentFilter intentFilter = new IntentFilter(
                ExternalMockPointingUi.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED);
        mInstrumentation.getContext().registerReceiver(
                mMockPointingUiActivityStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    private void unregisterForMockPointingUiActivityStatus() {
        mInstrumentation.getContext().unregisterReceiver(mMockPointingUiActivityStatusReceiver);
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    private static class MockPointingUiActivityStatusReceiver extends BroadcastReceiver {
        private Semaphore mSemaphore;

        MockPointingUiActivityStatusReceiver(Semaphore semaphore) {
            mSemaphore = semaphore;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            logd("MockPointingUiActivityStatusReceiver: onReceive " + intent.getAction());
            if ((MockPointingUiActivity.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED.equals(
                    intent.getAction()))
                    || (ExternalMockPointingUi.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED.equals(
                    intent.getAction()))) {
                logd("MockPointingUiActivityStatusReceiver: onReceive");
                try {
                    mSemaphore.release();
                } catch (Exception ex) {
                    loge("MockPointingUiActivityStatusReceiver: Got exception, ex=" + ex);
                }
            }
        }
    }

    private String getSatelliteServicePackageName() {
        return TextUtils.emptyIfNull(mInstrumentation.getContext().getResources().getString(
                R.string.config_satellite_service_package));
    }
}
