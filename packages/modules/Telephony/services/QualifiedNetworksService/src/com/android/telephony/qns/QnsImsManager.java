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
package com.android.telephony.qns;

import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipDialogState;
import android.telephony.ims.SipDialogStateCallback;
import android.telephony.ims.feature.ImsFeature;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * QnsImsManager is helper class to get wi-fi calling related items from ImsManager
 *
 * @hide
 */
class QnsImsManager {

    static final String PROP_DBG_WFC_AVAIL_OVERRIDE = "persist.dbg.wfc_avail_ovr";

    private static final int SYS_PROP_NOT_SET = -1;

    private final String mLogTag;
    private final Context mContext;
    private final int mSlotId;
    private final Executor mExecutor;
    private final Handler mHandler;
    private final HandlerThread mHandlerThread;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private final SubscriptionManager mSubscriptionManager;
    private boolean mQnsImsManagerInitialized;
    private CarrierConfigManager mConfigManager;
    private ImsManager mImsManager;
    private ImsMmTelManager mImsMmTelManager;
    private ImsRcsManager mImsRcsManager;
    private SipDelegateManager mSipDelegateManager;
    QnsImsStateCallback mMmTelStateCallback;
    QnsImsStateCallback mRcsStateCallback;
    QnsImsRegistrationCallback mMmtelImsRegistrationCallback;
    QnsImsRegistrationCallback mRcsImsRegistrationCallback;
    QnsSipDialogStateCallback mRcsSipDialogSessionStateCallback;

    final QnsRegistrantList mMmTelImsStateListener;
    final QnsRegistrantList mRcsImsStateListener;
    final QnsRegistrantList mMmTelImsRegistrationListener;
    final QnsRegistrantList mRcsImsRegistrationListener;
    final QnsRegistrantList mRcsSipDialogSessionStateListener;

    @VisibleForTesting
    final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionsChangeListener;

    /** QnsImsManager default constructor */
    QnsImsManager(Context context, int slotId) {
        mSlotId = slotId;
        mLogTag = QnsImsManager.class.getSimpleName() + "_" + mSlotId;
        mContext = context;
        mExecutor = new QnsImsManagerExecutor();

        mHandlerThread = new HandlerThread(mLogTag);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mMmTelImsStateListener = new QnsRegistrantList();
        mRcsImsStateListener = new QnsRegistrantList();
        mMmTelImsRegistrationListener = new QnsRegistrantList();
        mRcsImsRegistrationListener = new QnsRegistrantList();
        mRcsSipDialogSessionStateListener = new QnsRegistrantList();

        initQnsImsManager();

        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mSubscriptionsChangeListener = new QnsSubscriptionsChangedListener();
        if (mSubscriptionManager != null) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(
                    new QnsUtils.QnsExecutor(mHandler), mSubscriptionsChangeListener);
        }
    }

    class QnsSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {

        /**
         * Callback invoked when there is any change to any SubscriptionInfo.
         */
        @Override
        public void onSubscriptionsChanged() {
            int newSubId = QnsUtils.getSubId(mContext, mSlotId);
            if (newSubId != mSubId) {
                mSubId = newSubId;
                if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    clearQnsImsManager();
                } else {
                    clearQnsImsManager();
                    initQnsImsManager();
                }
            }
        }
    }

    @VisibleForTesting
    protected synchronized void initQnsImsManager() {
        if (mQnsImsManagerInitialized) {
            return;
        }
        log("initQnsImsManager.");

        if (mConfigManager == null) {
            mConfigManager = mContext.getSystemService(CarrierConfigManager.class);
            if (mConfigManager == null) {
                loge("initQnsImsManager: couldn't initialize. failed to get CarrierConfigManager.");
                clearQnsImsManager();
                return;
            }
        }

        if (mImsManager == null) {
            mImsManager = mContext.getSystemService(ImsManager.class);
            if (mImsManager == null) {
                loge("initQnsImsManager: couldn't initialize. failed to get ImsManager.");
                clearQnsImsManager();
                return;
            }
        }

        int subId = getSubId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return;
        }

        mImsMmTelManager = mImsManager.getImsMmTelManager(subId);
        if (mImsMmTelManager == null) {
            loge("initQnsImsManager: couldn't initialize. failed to get ImsMmTelManager.");
            clearQnsImsManager();
            return;
        }

        mImsRcsManager = mImsManager.getImsRcsManager(subId);
        if (mImsRcsManager == null) {
            loge("initQnsImsManager: couldn't initialize. failed to get ImsRcsManager.");
            clearQnsImsManager();
            return;
        }

        mSipDelegateManager = mImsManager.getSipDelegateManager(subId);
        if (mSipDelegateManager == null) {
            loge("initQnsImsManager: couldn't initialize. failed to get mSipDelegateManager.");
            clearQnsImsManager();
            return;
        }

        mQnsImsManagerInitialized = true;

        startTrackingImsState(ImsFeature.FEATURE_MMTEL);
        startTrackingImsState(ImsFeature.FEATURE_RCS);
        startTrackingImsRegistration(ImsFeature.FEATURE_MMTEL);
        startTrackingImsRegistration(ImsFeature.FEATURE_RCS);
        startTrackingSipDialogSessionState(ImsFeature.FEATURE_RCS);
    }

    @VisibleForTesting
    protected synchronized void startTrackingImsState(int feature) {
        if (feature == ImsFeature.FEATURE_MMTEL
                && mImsMmTelManager != null
                && mMmTelStateCallback == null) {
            try {
                QnsImsStateCallback imsStateCallback = new QnsImsStateCallback(feature);
                mImsMmTelManager.registerImsStateCallback(mExecutor, imsStateCallback);
                log("startTrackingImsState: registered ImsFeature.MMTEL State Callback.");
                mMmTelStateCallback = imsStateCallback;
            } catch (ImsException e) {
                loge("startTrackingImsState: couldn't register MMTEL state callback, " + e);
            }
        }

        if (feature == ImsFeature.FEATURE_RCS
                && mImsRcsManager != null
                && mRcsStateCallback == null) {
            try {
                QnsImsStateCallback rcsStateCallback = new QnsImsStateCallback(feature);
                mImsRcsManager.registerImsStateCallback(mExecutor, rcsStateCallback);
                log("startTrackingImsState: registered ImsFeature.RCS State Callback.");
                mRcsStateCallback = rcsStateCallback;
            } catch (ImsException e) {
                loge("startTrackingImsState: couldn't register RCS state callback, " + e);
            }
        }
    }

    @VisibleForTesting
    protected synchronized void startTrackingImsRegistration(int feature) {
        if (feature == ImsFeature.FEATURE_MMTEL
                && mImsMmTelManager != null
                && mMmtelImsRegistrationCallback == null) {
            try {
                QnsImsRegistrationCallback imsRegistrationCallback =
                        new QnsImsRegistrationCallback(feature);
                mImsMmTelManager.registerImsRegistrationCallback(
                        mExecutor, imsRegistrationCallback);
                log("startTrackingImsRegistration: registered MMTEL registration callback");
                mMmtelImsRegistrationCallback = imsRegistrationCallback;
            } catch (ImsException e) {
                loge("startTrackingImsRegistration: couldn't register MMTEL callback, " + e);
            }
        }

        if (feature == ImsFeature.FEATURE_RCS
                && mImsRcsManager != null
                && mRcsImsRegistrationCallback == null) {
            try {
                QnsImsRegistrationCallback rcsRegistrationCallback =
                        new QnsImsRegistrationCallback(feature);
                mImsRcsManager.registerImsRegistrationCallback(mExecutor, rcsRegistrationCallback);
                log("startTrackingImsRegistration: registered RCS registration callback");
                mRcsImsRegistrationCallback = rcsRegistrationCallback;
            } catch (ImsException e) {
                loge("startTrackingImsRegistration: couldn't register RCS callback, " + e);
            }
        }
    }

    @VisibleForTesting
    protected synchronized void startTrackingSipDialogSessionState(int feature) {
        if (feature == ImsFeature.FEATURE_RCS
                && mImsRcsManager != null
                && mRcsStateCallback != null
                && mRcsStateCallback.isImsAvailable()
                && mRcsSipDialogSessionStateCallback == null) {
            try {
                QnsSipDialogStateCallback rcsSipDialogStateCallback =
                        new QnsSipDialogStateCallback();
                mSipDelegateManager.registerSipDialogStateCallback(
                        mExecutor, rcsSipDialogStateCallback);
                log("startTrackingSipDialogSessionState: registered SipDialogState callback.");
                mRcsSipDialogSessionStateCallback = rcsSipDialogStateCallback;
            } catch (ImsException e) {
                loge("startTrackingSipDialogSessionState: couldn't register callback, " + e);
            }
        }
    }

    protected synchronized void stopTrackingImsState(int feature) {
        if (feature == ImsFeature.FEATURE_MMTEL
                && mImsMmTelManager != null
                && mMmTelStateCallback != null) {
            try {
                mImsMmTelManager.unregisterImsStateCallback(mMmTelStateCallback);
            } catch (Exception e) {
                // do-nothing
            }
        }
        if (feature == ImsFeature.FEATURE_RCS
                && mImsRcsManager != null
                && mRcsStateCallback != null) {
            try {
                mImsRcsManager.unregisterImsStateCallback(mRcsStateCallback);
            } catch (Exception e) {
                // do-nothing
            }
        }
    }

    protected synchronized void stopTrackingImsRegistration(int feature) {
        if (feature == ImsFeature.FEATURE_MMTEL
                && mImsMmTelManager != null
                && mMmtelImsRegistrationCallback != null) {
            try {
                mImsMmTelManager.unregisterImsRegistrationCallback(mMmtelImsRegistrationCallback);
            } catch (Exception e) {
                // do-nothing
            }
        }
        if (feature == ImsFeature.FEATURE_RCS
                && mImsRcsManager != null
                && mRcsImsRegistrationCallback != null) {
            try {
                mImsRcsManager.unregisterImsRegistrationCallback(mRcsImsRegistrationCallback);
            } catch (Exception e) {
                // do-nothing
            }
        }
    }

    protected synchronized void stopTrackingSipDialogSessionState(int feature) {
        if (feature == ImsFeature.FEATURE_RCS
                && mSipDelegateManager != null
                && mRcsSipDialogSessionStateCallback != null) {
            try {
                mSipDelegateManager.unregisterSipDialogStateCallback(
                        mRcsSipDialogSessionStateCallback);
            } catch (Exception e) {
                // do-nothing
            }
        }
    }

    @VisibleForTesting
    protected synchronized void clearQnsImsManager() {
        log("clearQnsImsManager");

        stopTrackingImsState(ImsFeature.FEATURE_MMTEL);
        stopTrackingImsState(ImsFeature.FEATURE_RCS);
        stopTrackingImsRegistration(ImsFeature.FEATURE_MMTEL);
        stopTrackingImsRegistration(ImsFeature.FEATURE_RCS);
        stopTrackingSipDialogSessionState(ImsFeature.FEATURE_RCS);

        mImsManager = null;
        mImsMmTelManager = null;
        mImsRcsManager = null;
        mSipDelegateManager = null;
        mMmTelStateCallback = null;
        mMmtelImsRegistrationCallback = null;
        mRcsStateCallback = null;
        mRcsImsRegistrationCallback = null;
        mRcsSipDialogSessionStateCallback = null;
        mQnsImsManagerInitialized = false;
    }

    @VisibleForTesting
    protected synchronized void close() {
        if (mSubscriptionManager != null) {
            mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionsChangeListener);
        }
        mHandlerThread.quitSafely();
        clearQnsImsManager();

        mMmTelImsStateListener.removeAll();
        mRcsImsStateListener.removeAll();
        mMmTelImsRegistrationListener.removeAll();
        mRcsImsRegistrationListener.removeAll();
        mRcsSipDialogSessionStateListener.removeAll();
    }

    int getSlotIndex() {
        return mSlotId;
    }

    private synchronized ImsMmTelManager getImsMmTelManagerOrThrowExceptionIfNotReady()
            throws ImsException {
        initQnsImsManager();
        if (mImsManager == null || mImsMmTelManager == null || mMmTelStateCallback == null) {
            throw new ImsException(
                    "IMS service is down.", ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return mImsMmTelManager;
    }

    /**
     * Get the boolean config from carrier config manager.
     *
     * @param key config key defined in CarrierConfigManager
     * @return boolean value of corresponding key.
     */
    private boolean getBooleanCarrierConfig(String key) {
        PersistableBundle b = null;
        if (mConfigManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            b = mConfigManager.getConfigForSubId(getSubId());
        }
        if (b != null) {
            return b.getBoolean(key);
        } else {
            // Return static default defined in CarrierConfigManager.
            return CarrierConfigManager.getDefaultConfig().getBoolean(key);
        }
    }

    /**
     * Get the int config from carrier config manager.
     *
     * @param key config key defined in CarrierConfigManager
     * @return integer value of corresponding key.
     */
    private int getIntCarrierConfig(String key) {
        PersistableBundle b = null;
        if (mConfigManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            b = mConfigManager.getConfigForSubId(getSubId());
        }
        if (b != null) {
            return b.getInt(key);
        } else {
            // Return static default defined in CarrierConfigManager.
            return CarrierConfigManager.getDefaultConfig().getInt(key);
        }
    }

    private boolean isCrossSimCallingEnabledByUser() {
        boolean crossSimCallingEnabled;
        try {
            ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
            crossSimCallingEnabled = mmTelManager.isCrossSimCallingEnabled();
        } catch (Exception e) {
            crossSimCallingEnabled = false;
        }
        log("isCrossSimCallingEnabledByUser:" + crossSimCallingEnabled);
        return crossSimCallingEnabled;
    }

    private boolean isGbaValid() {
        if (getBooleanCarrierConfig(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)) {
            TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
            if (tm == null) {
                loge("isGbaValid: TelephonyManager is null, returning false.");
                return false;
            }
            tm = tm.createForSubscriptionId(getSubId());
            String efIst = tm.getIsimIst();
            if (efIst == null) {
                loge("isGbaValid - ISF is NULL");
                return true;
            }
            boolean result = efIst.length() > 1 && (0x02 & (byte) efIst.charAt(1)) != 0;
            log("isGbaValid - GBA capable=" + result + ", ISF=" + efIst);
            return result;
        }
        return true;
    }

    private boolean isCrossSimEnabledByPlatform() {
        if (isWfcEnabledByPlatform()) {
            return getBooleanCarrierConfig(
                    CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL);
        }
        return false;
    }

    private boolean isVolteProvisionedOnDevice() {
        if (isMmTelProvisioningRequired(REGISTRATION_TECH_LTE)) {
            return isVolteProvisioned();
        }

        return true;
    }

    private boolean isVolteProvisioned() {
        return getImsProvisionedBoolNoException(REGISTRATION_TECH_LTE);
    }

    private boolean isWfcProvisioned() {
        return getImsProvisionedBoolNoException(REGISTRATION_TECH_IWLAN);
    }

    private boolean isMmTelProvisioningRequired(int tech) {
        if (!SubscriptionManager.isValidSubscriptionId(getSubId())) {
            return false;
        }

        boolean required = false;
        try {
            ProvisioningManager p = ProvisioningManager.createForSubscriptionId(getSubId());
            required = p.isProvisioningRequiredForCapability(CAPABILITY_TYPE_VOICE, tech);
        } catch (RuntimeException e) {
            loge("isMmTelProvisioningRequired, tech:" + tech + ". e:" + e);
        }

        log("isMmTelProvisioningRequired " + required + " for tech:" + tech);
        return required;
    }

    private boolean getImsProvisionedBoolNoException(int tech) {
        if (!SubscriptionManager.isValidSubscriptionId(getSubId())) {
            return false;
        }

        boolean status = false;
        try {
            ProvisioningManager p = ProvisioningManager.createForSubscriptionId(getSubId());
            status = p.getProvisioningStatusForCapability(CAPABILITY_TYPE_VOICE, tech);
        } catch (RuntimeException e) {
            loge("getImsProvisionedBoolNoException, tech:" + tech + ". e:" + e);
        }

        log("getImsProvisionedBoolNoException " + status + " for tech:" + tech);
        return status;
    }

    private int getSubId() {
        return QnsUtils.getSubId(mContext, mSlotId);
    }

    private static class QnsImsManagerExecutor implements Executor {
        private Executor mExecutor;

        @Override
        public void execute(Runnable runnable) {
            startExecutorIfNeeded();
            mExecutor.execute(runnable);
        }

        private synchronized void startExecutorIfNeeded() {
            if (mExecutor != null) return;
            mExecutor = Executors.newSingleThreadExecutor();
        }
    }

    private class QnsImsStateCallback extends ImsStateCallback {
        int mImsFeature;
        boolean mImsAvailable;

        public boolean isImsAvailable() {
            return mImsAvailable;
        }

        QnsImsStateCallback(int imsFeature) {
            mImsFeature = imsFeature;
        }

        @Override
        public void onUnavailable(int reason) {
            changeImsState(false);
        }

        @Override
        public void onAvailable() {
            changeImsState(true);
        }

        @Override
        public void onError() {
            changeImsState(false);
        }

        private void changeImsState(boolean imsAvailable) {
            if (mImsAvailable != imsAvailable) {
                mImsAvailable = imsAvailable;
                onImsStateChanged(mImsFeature, imsAvailable);
            }
        }
    }

    /** class for the IMS State. */
    static class ImsState {
        private final boolean mImsAvailable;

        ImsState(boolean imsAvailable) {
            mImsAvailable = imsAvailable;
        }

        boolean isImsAvailable() {
            return mImsAvailable;
        }
    }

    private void onImsStateChanged(int imsFeature, boolean imsAvailable) {
        if (imsFeature == ImsFeature.FEATURE_MMTEL) {
            log("onImsStateChanged ImsFeature.MMTEL:" + imsAvailable);
        }
        if (imsFeature == ImsFeature.FEATURE_RCS) {
            log("onImsStateChanged ImsFeature.RCS:" + imsAvailable);
        }

        if (imsAvailable) {
            startTrackingImsRegistration(imsFeature);
            startTrackingSipDialogSessionState(imsFeature);
        }

        ImsState imsState = new ImsState(imsAvailable);
        notifyImsStateChanged(imsFeature, imsState);
    }

    /**
     * Registers to monitor Ims State
     *
     * @param h Handler to get an event
     * @param what message id.
     */
    void registerImsStateChanged(Handler h, int what) {
        QnsRegistrant r = new QnsRegistrant(h, what, null);
        mMmTelImsStateListener.add(r);
    }

    /**
     * Unregisters ims state for given handler.
     *
     * @param h Handler
     */
    void unregisterImsStateChanged(Handler h) {
        mMmTelImsStateListener.remove(h);
    }

    /**
     * Registers to monitor Rcs State
     *
     * @param h Handler to get an event
     * @param what message id.
     */
    void registerRcsStateChanged(Handler h, int what) {
        QnsRegistrant r = new QnsRegistrant(h, what, null);
        mRcsImsStateListener.add(r);
    }

    /**
     * Unregisters rcs state for given handler.
     *
     * @param h Handler
     */
    void unregisterRcsStateChanged(Handler h) {
        mRcsImsStateListener.remove(h);
    }

    protected void notifyImsStateChanged(int imsFeature, ImsState imsState) {
        if (imsFeature == ImsFeature.FEATURE_MMTEL) {
            mMmTelImsStateListener.notifyResult(imsState);
        } else if (imsFeature == ImsFeature.FEATURE_RCS) {
            mRcsImsStateListener.notifyResult(imsState);
        }
    }

    private static class StateConsumer extends Semaphore implements Consumer<Integer> {
        private static final long TIMEOUT_MILLIS = 2000;

        StateConsumer() {
            super(0);
            mValue = new AtomicInteger();
        }

        private final AtomicInteger mValue;

        int getOrTimeOut() throws InterruptedException {
            if (tryAcquire(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return mValue.get();
            }
            return ImsFeature.STATE_UNAVAILABLE;
        }

        public void accept(Integer value) {
            if (value != null) {
                mValue.set(value);
            }
            release();
        }
    }

    private class QnsImsRegistrationCallback extends RegistrationManager.RegistrationCallback {
        int mImsFeature;
        ImsRegistrationState mImsRegistrationState;

        QnsImsRegistrationCallback(int imsFeature) {
            mImsFeature = imsFeature;
            mImsRegistrationState = null;
        }

        @Override
        public void onRegistered(ImsRegistrationAttributes attribute) {
            int transportType = attribute.getTransportType();
            log("on IMS registered on :" + QnsConstants.transportTypeToString(transportType));
            mImsRegistrationState =
                    new ImsRegistrationState(
                            QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED, transportType, null);
            notifyImsRegistrationChangedEvent(
                    mImsFeature, new ImsRegistrationState(mImsRegistrationState));
        }

        @Override
        public void onTechnologyChangeFailed(int transportType, ImsReasonInfo reason) {
            log(
                    "onTechnologyChangeFailed["
                            + QnsConstants.transportTypeToString(transportType)
                            + "] "
                            + reason.toString());
            mImsRegistrationState =
                    new ImsRegistrationState(
                            QnsConstants.IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED,
                            transportType,
                            reason);
            notifyImsRegistrationChangedEvent(
                    mImsFeature, new ImsRegistrationState(mImsRegistrationState));
        }

        @Override
        public void onUnregistered(ImsReasonInfo reason) {
            log("onUnregistered " + reason.toString());
            mImsRegistrationState =
                    new ImsRegistrationState(
                            QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED,
                            AccessNetworkConstants.TRANSPORT_TYPE_INVALID,
                            reason);
            notifyImsRegistrationChangedEvent(
                    mImsFeature, new ImsRegistrationState(mImsRegistrationState));
        }
    }

    private class QnsSipDialogStateCallback extends SipDialogStateCallback {
        boolean mIsActive;

        public boolean isActive() {
            return mIsActive;
        }

        @Override
        public void onActiveSipDialogsChanged(@NonNull List<SipDialogState> dialogs) {
            for (SipDialogState state : dialogs) {
                if (state.getState() == SipDialogState.STATE_CONFIRMED) {
                    if (!mIsActive) {
                        mIsActive = true;
                        notifySipDialogSessionStateChanged(mIsActive);
                    }
                    return;
                }
            }
            if (mIsActive) {
                mIsActive = false;
                notifySipDialogSessionStateChanged(mIsActive);
            }
        }

        @Override
        public void onError() {
            mIsActive = false;
            notifySipDialogSessionStateChanged(mIsActive);
            // TODO do nothing?
        }
    }

    /** State class for the IMS Registration. */
    static class ImsRegistrationState {
        @QnsConstants.QnsImsRegiEvent private final int mEvent;
        private final int mTransportType;
        private final ImsReasonInfo mReasonInfo;

        ImsRegistrationState(int event, int transportType, ImsReasonInfo reason) {
            mEvent = event;
            mTransportType = transportType;
            mReasonInfo = reason;
        }

        ImsRegistrationState(ImsRegistrationState state) {
            mEvent = state.mEvent;
            mTransportType = state.mTransportType;
            mReasonInfo = state.mReasonInfo;
        }

        int getEvent() {
            return mEvent;
        }

        int getTransportType() {
            return mTransportType;
        }

        ImsReasonInfo getReasonInfo() {
            return mReasonInfo;
        }

        @Override
        public String toString() {
            String reason = getReasonInfo() == null ? "null" : mReasonInfo.toString();
            String event = Integer.toString(mEvent);
            switch (mEvent) {
                case QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED:
                    event = "IMS_REGISTERED";
                    break;
                case QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED:
                    event = "IMS_UNREGISTERED";
                    break;
                case QnsConstants.IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED:
                    event = "IMS_ACCESS_NETWORK_CHANGE_FAILED";
                    break;
            }
            return "ImsRegistrationState["
                    + QnsConstants.transportTypeToString(mTransportType)
                    + "] "
                    + "Event:"
                    + event
                    + " reason:"
                    + reason;
        }
    }

    /**
     * Get the status of whether the IMS is registered or not for given transport type
     *
     * @param transportType Transport Type
     * @return true when ims is registered.
     */
    boolean isImsRegistered(int transportType) {
        if (mMmtelImsRegistrationCallback == null) {
            return false;
        }
        ImsRegistrationState state = mMmtelImsRegistrationCallback.mImsRegistrationState;
        return state != null
                && state.getTransportType() == transportType
                && state.getEvent() == QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED;
    }

    /**
     * Get the status of whether the Rcs is registered or not for given transport type
     *
     * @param transportType Transport Type
     * @return true when ims is registered.
     */
    boolean isRcsRegistered(int transportType) {
        if (mRcsImsRegistrationCallback == null) {
            return false;
        }
        ImsRegistrationState state = mRcsImsRegistrationCallback.mImsRegistrationState;
        return state != null
                && state.getTransportType() == transportType
                && state.getEvent() == QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED;
    }

    /**
     * Registers to monitor Ims registration status
     *
     * @param h Handler to get an event
     * @param what message id.
     */
    void registerImsRegistrationStatusChanged(Handler h, int what) {
        QnsRegistrant r = new QnsRegistrant(h, what, null);
        mMmTelImsRegistrationListener.add(r);
    }

    /**
     * Unregisters ims registration status for given handler.
     *
     * @param h Handler
     */
    void unregisterImsRegistrationStatusChanged(Handler h) {
        mMmTelImsRegistrationListener.remove(h);
    }

    /**
     * Registers to monitor Ims registration status
     *
     * @param h Handler to get an event
     * @param what message id.
     */
    void registerRcsRegistrationStatusChanged(Handler h, int what) {
        QnsRegistrant r = new QnsRegistrant(h, what, null);
        mRcsImsRegistrationListener.add(r);
    }

    /**
     * Unregisters ims registration status for given handler.
     *
     * @param h Handler
     */
    void unregisterRcsRegistrationStatusChanged(Handler h) {
        mRcsImsRegistrationListener.remove(h);
    }

    @VisibleForTesting
    protected void notifyImsRegistrationChangedEvent(int imsFeature, ImsRegistrationState state) {
        if (imsFeature == ImsFeature.FEATURE_MMTEL) {
            mMmTelImsRegistrationListener.notifyResult(state);
        } else if (imsFeature == ImsFeature.FEATURE_RCS) {
            mRcsImsRegistrationListener.notifyResult(state);
        }
    }

    /**
     * Get the active status of SipDialogState
     *
     * @return true when one of Sip Dialogs is active.
     */
    boolean isSipDialogSessionActive() {
        return mRcsSipDialogSessionStateCallback != null
                && mRcsSipDialogSessionStateCallback.isActive();
    }

    /**
     * Registers to monitor SipDialogSession State
     *
     * @param h Handler to get an event
     * @param what message id.
     */
    void registerSipDialogSessionStateChanged(Handler h, int what) {
        QnsRegistrant r = new QnsRegistrant(h, what, null);
        mRcsSipDialogSessionStateListener.add(r);
    }

    /**
     * Unregisters SipDialogState status for given handler.
     *
     * @param h Handler
     */
    void unregisterSipDialogSessionStateChanged(Handler h) {
        mRcsSipDialogSessionStateListener.remove(h);
    }

    @VisibleForTesting
    protected void notifySipDialogSessionStateChanged(boolean isActive) {
        mRcsSipDialogSessionStateListener.notifyResult(isActive);
    }

    private static boolean isImsSupportedOnDevice(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
    }

    /**
     * Get the status of the MmTel Feature corresponding to this subscription.
     *
     * <p>This function is a blocking function and there may be a timeout of up to 2 seconds.
     *
     * @return MmTel Feature Status. Returns one of the following: {@link
     *     ImsFeature#STATE_UNAVAILABLE}, {@link ImsFeature#STATE_INITIALIZING}, {@link
     *     ImsFeature#STATE_READY}.
     * @throws ImsException if the IMS service associated with this subscription is not available or
     *     the IMS service is not available.
     * @throws InterruptedException if the thread to get value is timed out. (max 2000ms)
     */
    int getImsServiceState() throws ImsException, InterruptedException {
        if (!isImsSupportedOnDevice(mContext)) {
            throw new ImsException(
                    "IMS not supported on device.",
                    ImsReasonInfo.CODE_LOCAL_IMS_NOT_SUPPORTED_ON_DEVICE);
        }
        ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
        final StateConsumer stateConsumer = new StateConsumer();
        mmTelManager.getFeatureState(mExecutor, stateConsumer);
        int state = stateConsumer.getOrTimeOut(); // ImsFeature.STATE_READY
        log("getImsServiceState state:" + state);
        return state;
    }

    /**
     * Returns whether wi-fi calling feature is enabled by platform.
     *
     * <p>This function is a blocking function and there may be a timeout of up to 2 seconds.
     *
     * @return true, if wi-fi calling feature is enabled by platform.
     */
    boolean isWfcEnabledByPlatform() {
        // We first read the per slot value. If it doesn't exist, we read the general value.
        // If still doesn't exist, we use the hardcoded default value.
        if (SystemProperties.getInt(PROP_DBG_WFC_AVAIL_OVERRIDE + mSlotId, SYS_PROP_NOT_SET) == 1
                || SystemProperties.getInt(PROP_DBG_WFC_AVAIL_OVERRIDE, SYS_PROP_NOT_SET) == 1) {
            return true;
        }

        return mContext.getResources()
                        .getBoolean(com.android.internal.R.bool.config_device_wfc_ims_available)
                && getBooleanCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)
                && isGbaValid();
    }

    /**
     * Returns whether wi-fi calling setting is enabled by user.
     *
     * @return true, if wi-fi calling setting is enabled by user.
     */
    boolean isWfcEnabledByUser() {
        boolean wfcEnabled;
        try {
            ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
            wfcEnabled = mmTelManager.isVoWiFiSettingEnabled();
        } catch (Exception e) {
            wfcEnabled =
                    getBooleanCarrierConfig(
                            CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL);
        }
        log("isWfcEnabledByUser:" + wfcEnabled);
        return wfcEnabled;
    }

    /**
     * Returns whether wi-fi calling roaming setting is enabled by user.
     *
     * @return true, if wi-fi calling roaming setting is enabled by user.
     */
    boolean isWfcRoamingEnabledByUser() {
        boolean wfcRoamingEnabled;
        try {
            ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
            wfcRoamingEnabled = mmTelManager.isVoWiFiRoamingSettingEnabled();
        } catch (Exception e) {
            wfcRoamingEnabled =
                    getBooleanCarrierConfig(
                            CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL);
        }
        log("isWfcRoamingEnabledByUser:" + wfcRoamingEnabled);
        return wfcRoamingEnabled;
    }

    /**
     * Returns whether cross sim wi-fi calling is enabled.
     *
     * @return true, if cross sim wi-fi calling is enabled.
     */
    boolean isCrossSimCallingEnabled() {
        boolean userEnabled = isCrossSimCallingEnabledByUser();
        boolean platformEnabled = isCrossSimEnabledByPlatform();
        boolean isProvisioned = isWfcProvisionedOnDevice();

        log(
                "isCrossSimCallingEnabled: platformEnabled = "
                        + platformEnabled
                        + ", provisioned = "
                        + isProvisioned
                        + ", userEnabled = "
                        + userEnabled);
        return userEnabled && platformEnabled && isProvisioned;
    }

    /**
     * Returns Voice over Wi-Fi mode preference
     *
     * @param roaming false:mode pref for home, true:mode pref for roaming
     * @return voice over Wi-Fi mode preference, which can be one of the following: {@link
     *     ImsMmTelManager#WIFI_MODE_WIFI_ONLY}, {@link
     *     ImsMmTelManager#WIFI_MODE_CELLULAR_PREFERRED}, {@link
     *     ImsMmTelManager#WIFI_MODE_WIFI_PREFERRED}
     */
    int getWfcMode(boolean roaming) {
        if (!roaming) {
            int wfcMode;
            try {
                ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
                wfcMode = mmTelManager.getVoWiFiModeSetting();
            } catch (Exception e) {
                wfcMode =
                        getIntCarrierConfig(
                                CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT);
            }
            log("getWfcMode:" + wfcMode);
            return wfcMode;
        } else {
            int wfcRoamingMode;
            try {
                ImsMmTelManager mmTelManager = getImsMmTelManagerOrThrowExceptionIfNotReady();
                wfcRoamingMode = mmTelManager.getVoWiFiRoamingModeSetting();
            } catch (Exception e) {
                wfcRoamingMode =
                        getIntCarrierConfig(
                                CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT);
            }
            log("getWfcMode(roaming):" + wfcRoamingMode);
            return wfcRoamingMode;
        }
    }

    /**
     * Indicates whether VoWifi is provisioned on slot.
     *
     * <p>When CarrierConfig KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL is true, and VoLTE is
     * not provisioned on device, this method returns false.
     */
    boolean isWfcProvisionedOnDevice() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)) {
            if (!isVolteProvisionedOnDevice()) {
                return false;
            }
        }

        if (isMmTelProvisioningRequired(REGISTRATION_TECH_IWLAN)) {
            return isWfcProvisioned();
        }

        return true;
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    protected void loge(String s) {
        Log.e(mLogTag, s);
    }
}
