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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.IBooleanConsumer;
import android.telephony.IIntegerConsumer;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.INtnSignalStrengthConsumer;
import android.telephony.satellite.stub.ISatelliteCapabilitiesConsumer;
import android.telephony.satellite.stub.ISatelliteListener;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.telephony.satellite.stub.NtnSignalStrength;
import android.telephony.satellite.stub.PointingInfo;
import android.telephony.satellite.stub.SatelliteCapabilities;
import android.telephony.satellite.stub.SatelliteDatagram;
import android.telephony.satellite.stub.SatelliteImplBase;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;
import android.telephony.satellite.stub.SatelliteService;
import android.util.Log;

import com.android.internal.util.FunctionalUtils;
import com.android.telephony.Rlog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockSatelliteService extends SatelliteImplBase {
    private static final String TAG = "MockSatelliteService";

    // Hardcoded values below
    private static final int SATELLITE_ALWAYS_VISIBLE = 0;
    /** SatelliteCapabilities constant indicating that the radio technology is proprietary. */
    private static final int[] SUPPORTED_RADIO_TECHNOLOGIES =
            new int[]{NTRadioTechnology.PROPRIETARY};
    /** SatelliteCapabilities constant indicating that pointing to satellite is required. */
    private static final boolean POINTING_TO_SATELLITE_REQUIRED = true;
    /** SatelliteCapabilities constant indicating the maximum number of characters per datagram. */
    private static final int MAX_BYTES_PER_DATAGRAM = 339;
    /** SatelliteCapabilities constant keys which are used to fill mAntennaPositionMap. */
    private static final int[] ANTENNA_POSITION_KEYS = new int[]{
            SatelliteManager.DISPLAY_MODE_OPENED, SatelliteManager.DISPLAY_MODE_CLOSED};
    /** SatelliteCapabilities constant values which are used to fill mAntennaPositionMap. */
    private static final AntennaPosition[] ANTENNA_POSITION_VALUES = new AntennaPosition[] {
            new AntennaPosition(new AntennaDirection(1,1,1),
                    SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT),
            new AntennaPosition(new AntennaDirection(2,2,2),
                    SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT)
    };

    @NonNull
    private final Map<IBinder, ISatelliteListener> mRemoteListeners = new HashMap<>();
    @Nullable private ILocalSatelliteListener mLocalListener;
    private final LocalBinder mBinder = new LocalBinder();
    @SatelliteResult
    private int mErrorCode = SatelliteResult.SATELLITE_RESULT_SUCCESS;

    @SatelliteResult
    private int mEnableCellularScanningErrorCode = SatelliteResult.SATELLITE_RESULT_SUCCESS;
    // For local access of this Service.
    class LocalBinder extends Binder {
        MockSatelliteService getService() {
            return MockSatelliteService.this;
        }
    }

    private boolean mIsCommunicationAllowedInLocation;
    private boolean mIsEnabled;
    private boolean mIsProvisioned;
    private boolean mIsSupported;
    private int mModemState;
    private final AtomicBoolean mWaitToSend = new AtomicBoolean(false);
    private SatelliteDatagram mDatagramToBeSent;
    private boolean mIsEmergencyDatagram;
    private IIntegerConsumer mSendDatagramErrorCallback;
    private Object mSendDatagramWithDelayLock = new Object();
    private static final long TIMEOUT = 1000;
    private final AtomicBoolean mShouldRespondTelephony = new AtomicBoolean(true);
    private final AtomicBoolean mShouldNotifyRemoteServiceConnected =
            new AtomicBoolean(false);
    @Nullable private List<String> mCarrierPlmnList;
    @Nullable private List<String> mAllSatellitePlmnList;
    private boolean mIsSatelliteEnabledForCarrier;
    private android.telephony.satellite.stub.NtnSignalStrength mNtnSignalStrength;

    private int[] mSupportedRadioTechnologies;
    private boolean mIsProvisioningApiSupported = true;
    private IIntegerConsumer mRequestSatelliteEnabledErrorCallback;
    private IIntegerConsumer mRequestSatelliteDisabledErrorCallback;
    private final Object mRequestSatelliteEnabledLock = new Object();

    /**
     * Create MockSatelliteService using the Executor specified for methods being called from
     * the framework.
     *
     * @param executor The executor for the framework to use when executing satellite methods.
     */
    public MockSatelliteService(@NonNull Executor executor) {
        super(executor);
        mIsCommunicationAllowedInLocation = true;
        mIsEnabled = false;
        mIsProvisioned = false;
        mIsSupported = true;
        mModemState = SatelliteModemState.SATELLITE_MODEM_STATE_OFF;
        mSupportedRadioTechnologies = SUPPORTED_RADIO_TECHNOLOGIES;
    }

    /**
     * Zero-argument constructor to prevent service binding exception.
     */
    public MockSatelliteService() {
        this(Runnable::run);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (SatelliteService.SERVICE_INTERFACE.equals(intent.getAction())) {
            logd("Remote service bound");
            return getBinder();
        }
        logd("Local service bound");
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logd("onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logd("onDestroy");
    }

    @Override
    public void setSatelliteListener(@NonNull ISatelliteListener listener) {
        logd("setSatelliteListener");
        mRemoteListeners.put(listener.asBinder(), listener);
        notifyRemoteServiceConnected();
    }

    @Override
    public void requestSatelliteListeningEnabled(boolean enabled, int timeout,
            @NonNull IIntegerConsumer errorCallback) {
        logd("requestSatelliteListeningEnabled: mErrorCode=" + mErrorCode);

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onSatelliteListeningEnabled(enabled));
        } else {
            loge("requestSatelliteListeningEnabled: mLocalListener is null");
        }

        if (!verifySatelliteModemState(errorCallback)) {
            return;
        }
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }

        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
        }
        if (enabled) {
            updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_LISTENING);
        } else {
            updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_IDLE);
        }
    }

    @Override
    public void requestSatelliteEnabled(boolean enableSatellite, boolean enableDemoMode,
            @NonNull IIntegerConsumer errorCallback) {
        logd("requestSatelliteEnabled: mErrorCode=" + mErrorCode
                + ", enableSatellite=" + enableSatellite
                + ", enableDemoMode=" + enableDemoMode
                + ", mShouldRespondTelephony=" + mShouldRespondTelephony.get());
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }

        if (enableSatellite) {
            enableSatellite(errorCallback);
        } else {
            disableSatellite(errorCallback);
        }

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onRequestSatelliteEnabled(enableSatellite));
        } else {
            loge("requestSatelliteEnabled: mLocalListener is null");
        }
    }

    private void enableSatellite(@NonNull IIntegerConsumer errorCallback) {
        if (mShouldRespondTelephony.get()) {
            mIsEnabled = true;
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
            updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_IDLE);
        }
        synchronized (mRequestSatelliteEnabledLock) {
            mRequestSatelliteEnabledErrorCallback = errorCallback;
        }
    }

    private void disableSatellite(@NonNull IIntegerConsumer errorCallback) {
        if (mShouldRespondTelephony.get()) {
            mIsEnabled = false;
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
            updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_OFF);
        }
        synchronized (mRequestSatelliteEnabledLock) {
            mRequestSatelliteDisabledErrorCallback = errorCallback;
        }
    }

    @Override
    public void requestIsSatelliteEnabled(@NonNull IIntegerConsumer errorCallback,
            @NonNull IBooleanConsumer callback) {
        logd("requestIsSatelliteEnabled: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mIsEnabled));
        }
    }

    @Override
    public void requestIsSatelliteSupported(@NonNull IIntegerConsumer errorCallback,
            @NonNull IBooleanConsumer callback) {
        logd("requestIsSatelliteSupported");
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mIsSupported));
        }
    }

    @Override
    public void requestSatelliteCapabilities(@NonNull IIntegerConsumer errorCallback,
            @NonNull ISatelliteCapabilitiesConsumer callback) {
        logd("requestSatelliteCapabilities: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }

        if (mShouldRespondTelephony.get()) {
            SatelliteCapabilities capabilities = new SatelliteCapabilities();
            capabilities.supportedRadioTechnologies = mSupportedRadioTechnologies;
            capabilities.isPointingRequired = POINTING_TO_SATELLITE_REQUIRED;
            capabilities.maxBytesPerOutgoingDatagram = MAX_BYTES_PER_DATAGRAM;
            capabilities.antennaPositionKeys = ANTENNA_POSITION_KEYS;
            capabilities.antennaPositionValues = ANTENNA_POSITION_VALUES;
            runWithExecutor(() -> callback.accept(capabilities));
        }
    }

    @Override
    public void enableCellularModemWhileSatelliteModeIsOn(
            boolean enabled, @NonNull IIntegerConsumer errorCallback) {
        logd("enableCellularModemWhileSatelliteModeIsOn: enabled=" + enabled);
        runWithExecutor(() -> errorCallback.accept(mEnableCellularScanningErrorCode));
    }

    @Override
    public void startSendingSatellitePointingInfo(@NonNull IIntegerConsumer errorCallback) {
        logd("startSendingSatellitePointingInfo: mErrorCode=" + mErrorCode);
        if (!verifySatelliteModemState(errorCallback)) {
            if (mLocalListener != null) {
                runWithExecutor(() -> mLocalListener.onStartSendingSatellitePointingInfo());
            } else {
                loge("startSendingSatellitePointingInfo: mLocalListener is null");
            }
            return;
        }

        if (mShouldRespondTelephony.get()) {
            if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            } else {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_SUCCESS));
            }
        }

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onStartSendingSatellitePointingInfo());
        } else {
            loge("startSendingSatellitePointingInfo: mLocalListener is null");
        }
    }

    @Override
    public void stopSendingSatellitePointingInfo(@NonNull IIntegerConsumer errorCallback) {
        logd("stopSendingSatellitePointingInfo: mErrorCode=" + mErrorCode);
        if (mShouldRespondTelephony.get()) {
            if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            } else {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_SUCCESS));
            }
        }

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onStopSendingSatellitePointingInfo());
        } else {
            loge("stopSendingSatellitePointingInfo: mLocalListener is null");
        }
    }

    @Override
    public void provisionSatelliteService(@NonNull String token, @NonNull byte[] provisionData,
            @NonNull IIntegerConsumer errorCallback) {
        logd("provisionSatelliteService: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (!mIsProvisioningApiSupported) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
        }
        updateSatelliteProvisionState(true);
    }

    @Override
    public void deprovisionSatelliteService(@NonNull String token,
            @NonNull IIntegerConsumer errorCallback) {
        logd("deprovisionSatelliteService: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (!mIsProvisioningApiSupported) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
        }
        updateSatelliteProvisionState(false);
    }

    @Override
    public void requestIsSatelliteProvisioned(@NonNull IIntegerConsumer errorCallback,
            @NonNull IBooleanConsumer callback) {
        logd("requestIsSatelliteProvisioned: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (!mIsProvisioningApiSupported) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mIsProvisioned));
        }
    }

    @Override
    public void pollPendingSatelliteDatagrams(@NonNull IIntegerConsumer errorCallback) {
        logd("pollPendingSatelliteDatagrams: mErrorCode=" + mErrorCode);
        updateSatelliteModemState(
                SatelliteModemState.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        if (mShouldRespondTelephony.get()) {
            if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            } else {
                runWithExecutor(() -> errorCallback.accept(
                        SatelliteResult.SATELLITE_RESULT_SUCCESS));
            }
        }

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onPollPendingSatelliteDatagrams());
        } else {
            loge("pollPendingSatelliteDatagrams: mLocalListener is null");
        }
    }

    @Override
    public void sendSatelliteDatagram(@NonNull SatelliteDatagram datagram, boolean isEmergency,
            @NonNull IIntegerConsumer errorCallback) {
        logd("sendDatagram: mErrorCode=" + mErrorCode);

        if (mWaitToSend.get()) {
            synchronized (mSendDatagramWithDelayLock) {
                // Save the datagram
                mDatagramToBeSent = datagram;
                mIsEmergencyDatagram = isEmergency;
                mSendDatagramErrorCallback = errorCallback;
            }

            if (mLocalListener != null) {
                runWithExecutor(() -> mLocalListener.onSendSatelliteDatagram(
                        datagram, isEmergency));
            } else {
                loge("sendDatagram: mLocalListener is null");
            }
        } else {
            updateSatelliteModemState(
                    SatelliteModemState.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
            if (mShouldRespondTelephony.get()) {
                if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                    runWithExecutor(() -> errorCallback.accept(mErrorCode));
                } else {
                    runWithExecutor(() -> errorCallback.accept(
                            SatelliteResult.SATELLITE_RESULT_SUCCESS));
                }
            }

            if (mLocalListener != null) {
                runWithExecutor(() -> mLocalListener.onSendSatelliteDatagram(
                        datagram, isEmergency));
            } else {
                loge("sendDatagram: mLocalListener is null");
            }
            updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_IDLE);
        }
    }

    @Override
    public void requestSatelliteModemState(@NonNull IIntegerConsumer errorCallback,
            @NonNull IIntegerConsumer callback) {
        logd("requestSatelliteModemState: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mModemState));
        }
    }

    @Override
    public void requestIsSatelliteCommunicationAllowedForCurrentLocation(
            @NonNull IIntegerConsumer errorCallback, @NonNull IBooleanConsumer callback) {
        logd("requestIsCommunicationAllowedForCurrentLocation: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            runWithExecutor(() -> errorCallback.accept(mErrorCode));
            return;
        }
        runWithExecutor(() -> callback.accept(mIsCommunicationAllowedInLocation));
    }

    @Override
    public void requestTimeForNextSatelliteVisibility(@NonNull IIntegerConsumer errorCallback,
            @NonNull IIntegerConsumer callback) {
        logd("requestTimeForNextSatelliteVisibility: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(SATELLITE_ALWAYS_VISIBLE));
        }
    }

    @Override
    public void setSatellitePlmn(@NonNull int simLogicalSlotIndex,
            @NonNull List<String> carrierPlmnList,
            @NonNull List<String> allSatellitePlmnList,
            @NonNull IIntegerConsumer errorCallback) {
        logd("setSatellitePlmn: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> errorCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> errorCallback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
        }

        mCarrierPlmnList = carrierPlmnList;
        mAllSatellitePlmnList = allSatellitePlmnList;

        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onSetSatellitePlmn());
        } else {
            loge("setSatellitePlmn: mLocalListener is null");
        }
    }

    @Override
    public void setSatelliteEnabledForCarrier(@NonNull int simLogicalSlotIndex,
            @NonNull boolean satelliteEnabled,
            @NonNull IIntegerConsumer callback) {
        logd("setSatelliteEnabledForCarrier: mErrorCode=" + mErrorCode
                + ", satelliteEnabled=" + satelliteEnabled
                + ", mShouldRespondTelephony=" + mShouldRespondTelephony.get());
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> callback.accept(mErrorCode));
            }
            return;
        }

        mIsSatelliteEnabledForCarrier = satelliteEnabled;
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(SatelliteResult.SATELLITE_RESULT_SUCCESS));
        }
    }

    @Override
    public void requestIsSatelliteEnabledForCarrier(@NonNull int simLogicalSlotIndex,
            @NonNull IIntegerConsumer resultCallback, @NonNull IBooleanConsumer callback) {
        logd("requestIsSatelliteEnabledForCarrier: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> resultCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mIsSatelliteEnabledForCarrier));
        }
    }

    @Override
    public void requestSignalStrength(@NonNull IIntegerConsumer resultCallback,
            INtnSignalStrengthConsumer callback) {
        logd("requestSignalStrength: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> resultCallback.accept(mErrorCode));
            }
            return;
        }
        if (mShouldRespondTelephony.get()) {
            runWithExecutor(() -> callback.accept(mNtnSignalStrength));
        }
    }

    @Override
    public void startSendingNtnSignalStrength(@NonNull IIntegerConsumer resultCallback) {
        logd("startSendingNtnSignalStrength: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> resultCallback.accept(mErrorCode));
            }
        }
    }

    @Override
    public void stopSendingNtnSignalStrength(@NonNull IIntegerConsumer resultCallback) {
        logd("stopSendingNtnSignalStrength: mErrorCode=" + mErrorCode);
        if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
            if (mShouldRespondTelephony.get()) {
                runWithExecutor(() -> resultCallback.accept(mErrorCode));
            }
        }
    }

    public void setLocalSatelliteListener(@NonNull ILocalSatelliteListener listener) {
        logd("setLocalSatelliteListener: listener=" + listener);
        mLocalListener = listener;
        if (mShouldNotifyRemoteServiceConnected.get()) {
            notifyRemoteServiceConnected();
        }
    }

    public void setErrorCode(@SatelliteResult int errorCode) {
        logd("setErrorCode: errorCode=" + errorCode);
        mErrorCode = errorCode;
    }

    /**
     * This API is used by CTS test cases to update current NTN signal strength of the mock
     * satellite modem.
     */
    public void setNtnSignalStrength(
            android.telephony.satellite.stub.NtnSignalStrength ntnSignalStrength) {
        logd("setNtnSignalStrength: ntnSignalStrengthLevel="
                + ntnSignalStrength.signalStrengthLevel);
        mNtnSignalStrength = ntnSignalStrength;
    }

    /**
     * This API is used by CTS test cases to set the error code returned when the mock satellite
     * modem receives requests to enable or disable cellular scanning mode.
     */
    public void setEnableCellularScanningErrorCode(@SatelliteResult int errorCode) {
        logd("setEnableCellularScanningErrorCode: errorCode=" + errorCode);
        mEnableCellularScanningErrorCode = errorCode;
    }

    /**
     * This API is used by CTS test cases to update supported NTN radio technologies of the
     * mock satellite modem.
     */
    public void setSupportedRadioTechnologies(@NonNull int[] supportedRadioTechnologies) {
        logd("setSupportedRadioTechnologies: supportedRadioTechnologies="
                + supportedRadioTechnologies[0]);
        mSupportedRadioTechnologies = supportedRadioTechnologies;
    }

    public void setSatelliteSupport(boolean supported) {
        logd("setSatelliteSupport: supported=" + supported);
        mIsSupported = supported;
    }

    public void setShouldRespondTelephony(boolean shouldRespondTelephony) {
        mShouldRespondTelephony.set(shouldRespondTelephony);
    }

    /**
     * Set whether satellite communication should be allowed.
     */
    public void setSatelliteCommunicationAllowed(boolean allowed) {
        logd("setSatelliteCommunicationAllowed: allowed=" + allowed);
        mIsCommunicationAllowedInLocation = allowed;
    }

    public void sendOnSatelliteDatagramReceived(SatelliteDatagram datagram, int pendingCount) {
        logd("sendOnSatelliteDatagramReceived");
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onSatelliteDatagramReceived(datagram, pendingCount)));
        updateSatelliteModemState(SatelliteModemState.SATELLITE_MODEM_STATE_IDLE);
    }

    public void sendOnPendingDatagrams() {
        logd("sendOnPendingDatagrams");
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onPendingDatagrams()));
    }

    public void sendOnSatellitePositionChanged(PointingInfo pointingInfo) {
        logd("sendOnSatellitePositionChanged");
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onSatellitePositionChanged(pointingInfo)));
    }

    /**
     * This API is used by CTS test cases to update satellite modem state.
     */
    public void sendOnSatelliteModemStateChanged(int modemState) {
        updateSatelliteModemState(modemState);
    }

    /**
     * Called when NTN signal strength changes.
     * This API is used by CTS test cases to update NTN signal strength.
     * @param ntnSignalStrength The new NTN signal strength.
     */
    public void sendOnNtnSignalStrengthChanged(NtnSignalStrength ntnSignalStrength) {
        logd("sendOnNtnSignalStrengthChanged");
        mNtnSignalStrength = ntnSignalStrength;
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onNtnSignalStrengthChanged(ntnSignalStrength)));
    }

    /**
     * Called when satellite capabilities has changed.
     * This API is used by CTS test cases to update satellite capabilities.
     * @param satelliteCapabilities The new satellite capabilities.
     */
    public void sendOnSatelliteCapabilitiesChanged(SatelliteCapabilities satelliteCapabilities) {
        logd("sendOnSatelliteCapabilitiesChanged");
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onSatelliteCapabilitiesChanged(satelliteCapabilities)));
    }

    public void setWaitToSend(boolean wait) {
        mWaitToSend.set(wait);
    }

    public boolean sendSavedDatagram() {
        synchronized (mSendDatagramWithDelayLock) {
            logd("sendSavedDatagram");
            if (mSendDatagramErrorCallback == null) {
                return false;
            }

            if (mShouldRespondTelephony.get()) {
                if (mErrorCode != SatelliteResult.SATELLITE_RESULT_SUCCESS) {
                    runWithExecutor(() -> mSendDatagramErrorCallback.accept(mErrorCode));
                } else {
                    runWithExecutor(() -> mSendDatagramErrorCallback.accept(
                            SatelliteResult.SATELLITE_RESULT_SUCCESS));
                }
            }
            mSendDatagramErrorCallback = null;
            return true;
        }
    }

    /**
     * Respond to the previous enable/disable request.
     */
    public boolean respondToRequestSatelliteEnabled(boolean isEnabled) {
        synchronized (mRequestSatelliteEnabledLock) {
            if (isEnabled) {
                logd("respondToRequestSatelliteEnabled: isEnabled=" + isEnabled + ", "
                        + (mRequestSatelliteEnabledErrorCallback != null));
                if (mRequestSatelliteEnabledErrorCallback == null) {
                    return false;
                }
                runWithExecutor(() -> mRequestSatelliteEnabledErrorCallback.accept(mErrorCode));
                mRequestSatelliteEnabledErrorCallback = null;
                return true;
            } else {
                logd("respondToRequestSatelliteEnabled: isEnabled=" + isEnabled + ", "
                        + (mRequestSatelliteDisabledErrorCallback != null));
                if (mRequestSatelliteDisabledErrorCallback == null) {
                    return false;
                }
                runWithExecutor(() -> mRequestSatelliteDisabledErrorCallback.accept(mErrorCode));
                mRequestSatelliteDisabledErrorCallback = null;
                return true;
            }
        }
    }

    /**
     * Get the configured PLMN list supported by carrier.
     */
    public List<String> getCarrierPlmnList() {
        logd("getCarrierPlmnList");
        return mCarrierPlmnList;
    }

    /**
     * Get the configured all satellite PLMN list.
     */
    public List<String> getAllSatellitePlmnList() {
        logd("getAllSatellitePlmnList");
        return mAllSatellitePlmnList;
    }

    public boolean getIsSatelliteEnabledForCarrier() {
        logd("getIsSatelliteEnabledForCarrier");
        return mIsSatelliteEnabledForCarrier;
    }

    public void clearSatelliteEnabledForCarrier() {
        mIsSatelliteEnabledForCarrier = false;
    }

    /**
     * Set whether provisioning API should be supported
     */
    public void setProvisioningApiSupported(boolean provisioningApiSupported) {
        mIsProvisioningApiSupported = provisioningApiSupported;
    }

    /**
     * Helper method to verify that the satellite modem is properly configured to receive
     * requests.
     *
     * @param errorCallback The callback to notify of any errors preventing satellite requests.
     * @return {@code true} if the satellite modem is configured to receive requests and
     * {@code false} if it is not.
     */
    private boolean verifySatelliteModemState(@NonNull IIntegerConsumer errorCallback) {
        if (!mIsSupported) {
            runWithExecutor(() -> errorCallback.accept(
                    SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED));
            return false;
        }
        if (!mIsProvisioned) {
            runWithExecutor(() -> errorCallback.accept(
                    SatelliteResult.SATELLITE_RESULT_SERVICE_NOT_PROVISIONED));
            return false;
        }
        if (!mIsEnabled) {
            runWithExecutor(() -> errorCallback.accept(
                    SatelliteResult.SATELLITE_RESULT_INVALID_MODEM_STATE));
            return false;
        }
        return true;
    }

    /**
     * Update the satellite modem state and notify listeners if it changed.
     *
     * @param modemState The {@link SatelliteModemState} to update.
     */
    private void updateSatelliteModemState(int modemState) {
        logd("updateSatelliteModemState modemState=" + modemState);
        mRemoteListeners.values().forEach(listener -> runWithExecutor(() ->
                listener.onSatelliteModemStateChanged(modemState)));
        mModemState = modemState;
    }

    /**
     * Update the satellite provision state.
     *
     * @param isProvisioned {@code true} if the satellite is currently provisioned and
     *                      {@code false} if it is not.
     */
    private void updateSatelliteProvisionState(boolean isProvisioned) {
        logd("updateSatelliteProvisionState: isProvisioned=" + isProvisioned
                + ", mIsProvisioned=" + mIsProvisioned);
        mIsProvisioned = isProvisioned;
    }

    /**
     * Execute the given runnable using the executor that this service was created with.
     *
     * @param r A runnable that can throw an exception.
     */
    private void runWithExecutor(@NonNull FunctionalUtils.ThrowingRunnable r) {
        mExecutor.execute(() -> Binder.withCleanCallingIdentity(r));
    }

    private void notifyRemoteServiceConnected() {
        logd("notifyRemoteServiceConnected");
        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onRemoteServiceConnected());
            mShouldNotifyRemoteServiceConnected.set(false);
        } else {
            mShouldNotifyRemoteServiceConnected.set(true);
        }
    }

    /**
     * Log the message to the radio buffer with {@code DEBUG} priority.
     *
     * @param log The message to log.
     */
    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    /**
     * Log with error attribute
     *
     * @param s is string log
     */
    protected void loge(@NonNull String s) {
        Log.e(TAG, s);
    }
}
