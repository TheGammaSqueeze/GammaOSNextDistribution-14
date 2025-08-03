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

package com.android.services.telephony.domainselection;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.BarringInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.ImsStateCallback.DisconnectedReason;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.Keep;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;

/**
 * A class for tracking the IMS related information like IMS registration state, MMTEL capabilities.
 * And, it also tracks the {@link ServiceState} and {@link BarringInfo} to identify the current
 * network state to which the device is attached.
 */
@Keep
public class ImsStateTracker {
    /**
     * A listener used to be notified of the {@link ServiceState} change.
     */
    public interface ServiceStateListener {
        /**
         * Called when the {@link ServiceState} is updated.
         */
        void onServiceStateUpdated(ServiceState serviceState);
    }

    /**
     * A listener used to be notified of the {@link BarringInfo} change.
     */
    public interface BarringInfoListener {
        /**
         * Called when the {@link BarringInfo} is updated.
         */
        void onBarringInfoUpdated(BarringInfo barringInfo);
    }

    /**
     * A listener used to be notified of the change for MMTEL connection state, IMS registration
     * state, and MMTEL capabilities.
     */
    public interface ImsStateListener {
        /**
         * Called when MMTEL feature connection state is changed.
         */
        void onImsMmTelFeatureAvailableChanged();

        /**
         * Called when IMS registration state is changed.
         */
        void onImsRegistrationStateChanged();

        /**
         * Called when MMTEL capability is changed - IMS is registered
         * and the service is currently available over IMS.
         */
        void onImsMmTelCapabilitiesChanged();
    }

    private static final String TAG = ImsStateTracker.class.getSimpleName();
    /**
     * When MMTEL feature connection is unavailable temporarily,
     * the IMS state will be set to unavailable after waiting for this time.
     */
    @VisibleForTesting
    protected static final long MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS = 1000; // 1 seconds

    // Persistent Logging
    private final LocalLog mEventLog = new LocalLog(30);
    private final Context mContext;
    private final int mSlotId;
    private final Handler mHandler;
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    /** For tracking the ServiceState and its related listeners. */
    private ServiceState mServiceState;
    private final Set<ServiceStateListener> mServiceStateListeners = new ArraySet<>(2);

    /** For tracking the BarringInfo and its related listeners. */
    private BarringInfo mBarringInfo;
    private final Set<BarringInfoListener> mBarringInfoListeners = new ArraySet<>(2);

    /** For tracking IMS states and callbacks. */
    private final Set<ImsStateListener> mImsStateListeners = new ArraySet<>(5);
    private ImsMmTelManager mMmTelManager;
    private ImsStateCallback mImsStateCallback;
    private RegistrationManager.RegistrationCallback mImsRegistrationCallback;
    private ImsMmTelManager.CapabilityCallback mMmTelCapabilityCallback;
    /** The availability of MmTelFeature. */
    private Boolean mMmTelFeatureAvailable;
    /** The IMS registration state and the network type that performed IMS registration. */
    private Boolean mImsRegistered;
    private @RadioAccessNetworkType int mImsAccessNetworkType = AccessNetworkType.UNKNOWN;
    private Boolean mImsRegisteredOverCrossSim;
    /** The MMTEL capabilities - Voice, Video, SMS, and Ut. */
    private MmTelCapabilities mMmTelCapabilities;
    private final Runnable mMmTelFeatureUnavailableRunnable = new Runnable() {
        @Override
        public void run() {
            setImsStateAsUnavailable();
            notifyImsMmTelFeatureAvailableChanged();
        }
    };

    public ImsStateTracker(@NonNull Context context, int slotId, @NonNull Looper looper) {
        mContext = context;
        mSlotId = slotId;
        mHandler = new Handler(looper);
    }

    /**
     * Destroys this tracker.
     */
    public void destroy() {
        stopListeningForImsState();
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Returns the slot index for this tracker.
     */
    public int getSlotId() {
        return mSlotId;
    }

    /**
     * Returns the current subscription index for this tracker.
     */
    public int getSubId() {
        return mSubId;
    }

    /**
     * Returns the Handler instance of this tracker.
     */
    @VisibleForTesting
    public @NonNull Handler getHandler() {
        return mHandler;
    }

    /**
     * Starts monitoring the IMS states with the specified subscription.
     * This method will be called whenever the subscription index for this tracker is changed.
     * If the subscription index for this tracker is same as previously set, it will be ignored.
     *
     * @param subId The subscription index to be started.
     */
    public void start(int subId) {
        if (mSubId == subId) {
            if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
                setImsStateAsUnavailable();
                return;
            } else if (mImsStateCallback != null) {
                // If start() is called with the same subscription index and the ImsStateCallback
                // was already registered, we don't need to unregister and register this callback
                // again. So, this request should be ignored if the subscription index is same.
                logd("start: ignored for same subscription(" + mSubId + ")");
                return;
            }
        } else {
            logi("start: subscription changed from " + mSubId + " to " + subId);
            mSubId = subId;
        }

        stopListeningForImsState();
        startListeningForImsState();
    }

    /**
     * Updates the service state of the network to which the device is currently attached.
     * This method should be run on the same thread as the Handler.
     *
     * @param serviceState The {@link ServiceState} to be updated.
     */
    public void updateServiceState(ServiceState serviceState) {
        mServiceState = serviceState;

        for (ServiceStateListener listener : mServiceStateListeners) {
            listener.onServiceStateUpdated(serviceState);
        }
    }

    /**
     * Adds a listener to be notified of the {@link ServiceState} change.
     * The newly added listener is notified if the current {@link ServiceState} is present.
     *
     * @param listener The listener to be added.
     */
    public void addServiceStateListener(@NonNull ServiceStateListener listener) {
        mServiceStateListeners.add(listener);

        final ServiceState serviceState = mServiceState;
        if (serviceState != null) {
            mHandler.post(() -> notifyServiceStateUpdated(listener, serviceState));
        }
    }

    /**
     * Removes a listener to be notified of the {@link ServiceState} change.
     *
     * @param listener The listener to be removed.
     */
    public void removeServiceStateListener(@NonNull ServiceStateListener listener) {
        mServiceStateListeners.remove(listener);
    }

    /**
     * Notifies the specified listener of a change to {@link ServiceState}.
     *
     * @param listener The listener to be notified.
     * @param serviceState The {@link ServiceState} to be reported.
     */
    private void notifyServiceStateUpdated(ServiceStateListener listener,
            ServiceState serviceState) {
        if (!mServiceStateListeners.contains(listener)) {
            return;
        }
        listener.onServiceStateUpdated(serviceState);
    }

    /**
     * Updates the barring information received from the network to which the device is currently
     * attached.
     * This method should be run on the same thread as the Handler.
     *
     * @param barringInfo The {@link BarringInfo} to be updated.
     */
    public void updateBarringInfo(BarringInfo barringInfo) {
        mBarringInfo = barringInfo;

        for (BarringInfoListener listener : mBarringInfoListeners) {
            listener.onBarringInfoUpdated(barringInfo);
        }
    }

    /**
     * Adds a listener to be notified of the {@link BarringInfo} change.
     * The newly added listener is notified if the current {@link BarringInfo} is present.
     *
     * @param listener The listener to be added.
     */
    public void addBarringInfoListener(@NonNull BarringInfoListener listener) {
        mBarringInfoListeners.add(listener);

        final BarringInfo barringInfo = mBarringInfo;
        if (barringInfo != null) {
            mHandler.post(() -> notifyBarringInfoUpdated(listener, barringInfo));
        }
    }

    /**
     * Removes a listener to be notified of the {@link BarringInfo} change.
     *
     * @param listener The listener to be removed.
     */
    public void removeBarringInfoListener(@NonNull BarringInfoListener listener) {
        mBarringInfoListeners.remove(listener);
    }

    /**
     * Notifies the specified listener of a change to {@link BarringInfo}.
     *
     * @param listener The listener to be notified.
     * @param barringInfo The {@link BarringInfo} to be reported.
     */
    private void notifyBarringInfoUpdated(BarringInfoListener listener, BarringInfo barringInfo) {
        if (!mBarringInfoListeners.contains(listener)) {
            return;
        }
        listener.onBarringInfoUpdated(barringInfo);
    }

    /**
     * Adds a listener to be notified of the IMS state change.
     * If each state was already received from the IMS service, the newly added listener
     * is notified once.
     *
     * @param listener The listener to be added.
     */
    public void addImsStateListener(@NonNull ImsStateListener listener) {
        mImsStateListeners.add(listener);
        mHandler.post(() -> notifyImsStateChangeIfValid(listener));
    }

    /**
     * Removes a listener to be notified of the IMS state change.
     *
     * @param listener The listener to be removed.
     */
    public void removeImsStateListener(@NonNull ImsStateListener listener) {
        mImsStateListeners.remove(listener);
    }

    /**
     * Returns {@code true} if all IMS states are ready, {@code false} otherwise.
     */
    @VisibleForTesting
    public boolean isImsStateReady() {
        return mMmTelFeatureAvailable != null
                && mImsRegistered != null
                && mMmTelCapabilities != null;
    }

    /**
     * Returns {@code true} if MMTEL feature connection is available, {@code false} otherwise.
     */
    public boolean isMmTelFeatureAvailable() {
        return mMmTelFeatureAvailable != null && mMmTelFeatureAvailable;
    }

    /**
     * Returns {@code true} if IMS is registered, {@code false} otherwise.
     */
    public boolean isImsRegistered() {
        return mImsRegistered != null && mImsRegistered;
    }

    /**
     * Returns {@code true} if IMS is registered over Wi-Fi (IWLAN), {@code false} otherwise.
     */
    public boolean isImsRegisteredOverWlan() {
        return mImsAccessNetworkType == AccessNetworkType.IWLAN;
    }

    /**
     * Returns {@code true} if IMS is registered over the mobile data of another subscription.
     */
    public boolean isImsRegisteredOverCrossSim() {
        return mImsRegisteredOverCrossSim != null && mImsRegisteredOverCrossSim;
    }

    /**
     * Returns {@code true} if IMS voice call is capable, {@code false} otherwise.
     */
    public boolean isImsVoiceCapable() {
        return mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
    }

    /**
     * Returns {@code true} if IMS video call is capable, {@code false} otherwise.
     */
    public boolean isImsVideoCapable() {
        return mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
    }

    /**
     * Returns {@code true} if IMS SMS is capable, {@code false} otherwise.
     */
    public boolean isImsSmsCapable() {
        return mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_SMS);
    }

    /**
     * Returns {@code true} if IMS UT is capable, {@code false} otherwise.
     */
    public boolean isImsUtCapable() {
        return mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_UT);
    }

    /**
     * Returns the access network type to which IMS is registered.
     */
    public @RadioAccessNetworkType int getImsAccessNetworkType() {
        return mImsAccessNetworkType;
    }

    /**
     * Sets the IMS states to the initial values.
     */
    private void initImsState() {
        mMmTelFeatureAvailable = null;
        mImsRegistered = null;
        mImsAccessNetworkType = AccessNetworkType.UNKNOWN;
        mImsRegisteredOverCrossSim = null;
        mMmTelCapabilities = null;
    }

    /**
     * Sets the IMS states to unavailable to notify the readiness of the IMS state
     * when the subscription is not valid.
     */
    private void setImsStateAsUnavailable() {
        logd("setImsStateAsUnavailable");
        setMmTelFeatureAvailable(false);
        setImsRegistered(false);
        setImsAccessNetworkType(AccessNetworkType.UNKNOWN);
        setImsRegisteredOverCrossSim(false);
        setMmTelCapabilities(new MmTelCapabilities());
    }

    private void setMmTelFeatureAvailable(boolean available) {
        if (!Objects.equals(mMmTelFeatureAvailable, Boolean.valueOf(available))) {
            logi("setMmTelFeatureAvailable: " + mMmTelFeatureAvailable + " >> " + available);
            mMmTelFeatureAvailable = Boolean.valueOf(available);
        }
    }

    private void setImsRegistered(boolean registered) {
        if (!Objects.equals(mImsRegistered, Boolean.valueOf(registered))) {
            logi("setImsRegistered: " + mImsRegistered + " >> " + registered);
            mImsRegistered = Boolean.valueOf(registered);
        }
    }

    private void setImsAccessNetworkType(int accessNetworkType) {
        if (mImsAccessNetworkType != accessNetworkType) {
            logi("setImsAccessNetworkType: " + accessNetworkTypeToString(mImsAccessNetworkType)
                    + " >> " + accessNetworkTypeToString(accessNetworkType));
            mImsAccessNetworkType = accessNetworkType;
        }
    }

    private void setMmTelCapabilities(@NonNull MmTelCapabilities capabilities) {
        if (!Objects.equals(mMmTelCapabilities, capabilities)) {
            logi("MMTEL capabilities: " + mMmTelCapabilities + " >> " + capabilities);
            mMmTelCapabilities = capabilities;
        }
    }

    private void setImsRegisteredOverCrossSim(boolean crossSim) {
        if (!Objects.equals(mImsRegisteredOverCrossSim, Boolean.valueOf(crossSim))) {
            logi("setImsRegisteredOverCrossSim: " + mImsRegisteredOverCrossSim + " >> " + crossSim);
            mImsRegisteredOverCrossSim = Boolean.valueOf(crossSim);
        }
    }

    /**
     * Notifies the specified listener of the current IMS state if it's valid.
     *
     * @param listener The {@link ImsStateListener} to be notified.
     */
    private void notifyImsStateChangeIfValid(@NonNull ImsStateListener listener) {
        if (!mImsStateListeners.contains(listener)) {
            return;
        }

        if (mMmTelFeatureAvailable != null) {
            listener.onImsMmTelFeatureAvailableChanged();
        }

        if (mImsRegistered != null) {
            listener.onImsRegistrationStateChanged();
        }

        if (mMmTelCapabilities != null) {
            listener.onImsMmTelCapabilitiesChanged();
        }
    }

    /**
     * Notifies the application that MMTEL feature connection state is changed.
     */
    private void notifyImsMmTelFeatureAvailableChanged() {
        for (ImsStateListener l : mImsStateListeners) {
            l.onImsMmTelFeatureAvailableChanged();
        }
    }

    /**
     * Notifies the application that IMS registration state is changed.
     */
    private void notifyImsRegistrationStateChanged() {
        logi("ImsState: " + imsStateToString());
        for (ImsStateListener l : mImsStateListeners) {
            l.onImsRegistrationStateChanged();
        }
    }

    /**
     * Notifies the application that MMTEL capabilities is changed.
     */
    private void notifyImsMmTelCapabilitiesChanged() {
        logi("ImsState: " + imsStateToString());
        for (ImsStateListener l : mImsStateListeners) {
            l.onImsMmTelCapabilitiesChanged();
        }
    }

    /**
     * Called when MMTEL feature connection state is available.
     */
    private void onMmTelFeatureAvailable() {
        logd("onMmTelFeatureAvailable");
        mHandler.removeCallbacks(mMmTelFeatureUnavailableRunnable);
        setMmTelFeatureAvailable(true);
        registerImsRegistrationCallback();
        registerMmTelCapabilityCallback();
        notifyImsMmTelFeatureAvailableChanged();
    }

    /**
     * Called when MMTEL feature connection state is unavailable.
     */
    private void onMmTelFeatureUnavailable(@DisconnectedReason int reason) {
        logd("onMmTelFeatureUnavailable: reason=" + disconnectedCauseToString(reason));

        if (reason == ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR
                || reason == ImsStateCallback.REASON_IMS_SERVICE_NOT_READY) {
            // Wait for onAvailable for some times and
            // if it's not available, the IMS state will be set to unavailable.
            initImsState();
            setMmTelFeatureAvailable(false);
            mHandler.postDelayed(mMmTelFeatureUnavailableRunnable,
                    MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS);
        } else if (reason == ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR
                || reason == ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED) {
            // Permanently blocked for this subscription.
            setImsStateAsUnavailable();
            notifyImsMmTelFeatureAvailableChanged();
        } else if (reason == ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED) {
            // Wait for onAvailable for some times and
            // if it's not available, the IMS state will be set to unavailable.
            initImsState();
            setMmTelFeatureAvailable(false);
            unregisterImsRegistrationCallback();
            unregisterMmTelCapabilityCallback();
            mHandler.postDelayed(mMmTelFeatureUnavailableRunnable,
                    MMTEL_FEATURE_AVAILABLE_WAIT_TIME_MILLIS);
        } else if (reason == ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE) {
            // The {@link TelephonyDomainSelectionService} will call ImsStateTracker#start
            // when the subscription changes to register new callbacks.
            setImsStateAsUnavailable();
            unregisterImsRegistrationCallback();
            unregisterMmTelCapabilityCallback();
            // ImsStateCallback has already been removed after calling onUnavailable.
            mImsStateCallback = null;
            notifyImsMmTelFeatureAvailableChanged();
        } else {
            logw("onMmTelFeatureUnavailable: unexpected reason=" + reason);
        }
    }

    /**
     * Called when IMS is registered to the IMS network.
     */
    private void onImsRegistered(@NonNull ImsRegistrationAttributes attributes) {
        logd("onImsRegistered: " + attributes);

        setImsRegistered(true);
        setImsAccessNetworkType(
                imsRegTechToAccessNetworkType(attributes.getRegistrationTechnology()));
        setImsRegisteredOverCrossSim(attributes.getRegistrationTechnology()
                == ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM);
        notifyImsRegistrationStateChanged();
    }

    /**
     * Called when IMS is unregistered from the IMS network.
     */
    private void onImsUnregistered(@NonNull ImsReasonInfo info) {
        logd("onImsUnregistered: " + info);
        setImsRegistered(false);
        setImsAccessNetworkType(AccessNetworkType.UNKNOWN);
        setImsRegisteredOverCrossSim(false);
        setMmTelCapabilities(new MmTelCapabilities());
        notifyImsRegistrationStateChanged();
    }

    /**
     * Called when MMTEL capability is changed - IMS is registered
     * and the service is currently available over IMS.
     */
    private void onMmTelCapabilitiesChanged(@NonNull MmTelCapabilities capabilities) {
        logd("onMmTelCapabilitiesChanged: " + capabilities);
        setMmTelCapabilities(capabilities);
        notifyImsMmTelCapabilitiesChanged();
    }

    /**
     * Starts listening to monitor the IMS states -
     * connection state, IMS registration state, and MMTEL capabilities.
     */
    private void startListeningForImsState() {
        if (!SubscriptionManager.isValidSubscriptionId(getSubId())) {
            setImsStateAsUnavailable();
            return;
        }

        ImsManager imsMngr = mContext.getSystemService(ImsManager.class);
        mMmTelManager = imsMngr.getImsMmTelManager(getSubId());
        initImsState();
        registerImsStateCallback();
    }

    /**
     * Stops listening to monitor the IMS states -
     * connection state, IMS registration state, and MMTEL capabilities.
     */
    private void stopListeningForImsState() {
        mHandler.removeCallbacks(mMmTelFeatureUnavailableRunnable);

        if (mMmTelManager != null) {
            unregisterMmTelCapabilityCallback();
            unregisterImsRegistrationCallback();
            unregisterImsStateCallback();
            mMmTelManager = null;
        }
    }

    private void registerImsStateCallback() {
        if (mImsStateCallback != null) {
            loge("ImsStateCallback is already registered for sub-" + getSubId());
            return;
        }
        /**
         * Listens to the IMS connection state change.
         */
        mImsStateCallback = new ImsStateCallback() {
            @Override
            public void onUnavailable(@DisconnectedReason int reason) {
                onMmTelFeatureUnavailable(reason);
            }

            @Override
            public void onAvailable() {
                onMmTelFeatureAvailable();
            }

            @Override
            public void onError() {
                // This case will not be happened because this domain selection service
                // is running on the Telephony service.
            }
        };

        try {
            mMmTelManager.registerImsStateCallback(mHandler::post, mImsStateCallback);
        } catch (ImsException e) {
            loge("Exception when registering ImsStateCallback: " + e);
            mImsStateCallback = null;
        }
    }

    private void unregisterImsStateCallback() {
        if (mImsStateCallback != null) {
            try {
                mMmTelManager.unregisterImsStateCallback(mImsStateCallback);
            }  catch (Exception ignored) {
                // Ignore the runtime exception while unregistering callback.
                logd("Exception when unregistering ImsStateCallback: " + ignored);
            }
            mImsStateCallback = null;
        }
    }

    private void registerImsRegistrationCallback() {
        if (mImsRegistrationCallback != null) {
            logd("RegistrationCallback is already registered for sub-" + getSubId());
            return;
        }
        /**
         * Listens to the IMS registration state change.
         */
        mImsRegistrationCallback = new RegistrationManager.RegistrationCallback() {
            @Override
            public void onRegistered(@NonNull ImsRegistrationAttributes attributes) {
                onImsRegistered(attributes);
            }

            @Override
            public void onUnregistered(@NonNull ImsReasonInfo info) {
                onImsUnregistered(info);
            }
        };

        try {
            mMmTelManager.registerImsRegistrationCallback(mHandler::post, mImsRegistrationCallback);
        } catch (ImsException e) {
            loge("Exception when registering RegistrationCallback: " + e);
            mImsRegistrationCallback = null;
        }
    }

    private void unregisterImsRegistrationCallback() {
        if (mImsRegistrationCallback != null) {
            try {
                mMmTelManager.unregisterImsRegistrationCallback(mImsRegistrationCallback);
            }  catch (Exception ignored) {
                // Ignore the runtime exception while unregistering callback.
                logd("Exception when unregistering RegistrationCallback: " + ignored);
            }
            mImsRegistrationCallback = null;
        }
    }

    private void registerMmTelCapabilityCallback() {
        if (mMmTelCapabilityCallback != null) {
            logd("CapabilityCallback is already registered for sub-" + getSubId());
            return;
        }
        /**
         * Listens to the MmTel feature capabilities change.
         */
        mMmTelCapabilityCallback = new ImsMmTelManager.CapabilityCallback() {
            @Override
            public void onCapabilitiesStatusChanged(@NonNull MmTelCapabilities capabilities) {
                onMmTelCapabilitiesChanged(capabilities);
            }
        };

        try {
            mMmTelManager.registerMmTelCapabilityCallback(mHandler::post, mMmTelCapabilityCallback);
        } catch (ImsException e) {
            loge("Exception when registering CapabilityCallback: " + e);
            mMmTelCapabilityCallback = null;
        }
    }

    private void unregisterMmTelCapabilityCallback() {
        if (mMmTelCapabilityCallback != null) {
            try {
                mMmTelManager.unregisterMmTelCapabilityCallback(mMmTelCapabilityCallback);
            } catch (Exception ignored) {
                // Ignore the runtime exception while unregistering callback.
                logd("Exception when unregistering CapabilityCallback: " + ignored);
            }
            mMmTelCapabilityCallback = null;
        }
    }

    /** Returns a string representation of IMS states. */
    public String imsStateToString() {
        StringBuilder sb = new StringBuilder("{ ");
        sb.append("MMTEL: featureAvailable=").append(booleanToString(mMmTelFeatureAvailable));
        sb.append(", registered=").append(booleanToString(mImsRegistered));
        sb.append(", accessNetworkType=").append(accessNetworkTypeToString(mImsAccessNetworkType));
        sb.append(", capabilities=").append(mmTelCapabilitiesToString(mMmTelCapabilities));
        sb.append(" }");
        return sb.toString();
    }

    protected static String accessNetworkTypeToString(
            @RadioAccessNetworkType int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkType.UNKNOWN: return "UNKNOWN";
            case AccessNetworkType.GERAN: return "GERAN";
            case AccessNetworkType.UTRAN: return "UTRAN";
            case AccessNetworkType.EUTRAN: return "EUTRAN";
            case AccessNetworkType.CDMA2000: return "CDMA2000";
            case AccessNetworkType.IWLAN: return "IWLAN";
            case AccessNetworkType.NGRAN: return "NGRAN";
            default: return Integer.toString(accessNetworkType);
        }
    }

    /** Converts the IMS registration technology to the access network type. */
    private static @RadioAccessNetworkType int imsRegTechToAccessNetworkType(
            @ImsRegistrationImplBase.ImsRegistrationTech int imsRegTech) {
        switch (imsRegTech) {
            case ImsRegistrationImplBase.REGISTRATION_TECH_LTE:
                return AccessNetworkType.EUTRAN;
            case ImsRegistrationImplBase.REGISTRATION_TECH_NR:
                return AccessNetworkType.NGRAN;
            case ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN:
            case ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM:
                return AccessNetworkType.IWLAN;
            default:
                return AccessNetworkType.UNKNOWN;
        }
    }

    private static String booleanToString(Boolean b) {
        return b == null ? "null" : b.toString();
    }

    private static String mmTelCapabilitiesToString(MmTelCapabilities c) {
        if (c == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        sb.append("voice=").append(c.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE));
        sb.append(", video=").append(c.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO));
        sb.append(", ut=").append(c.isCapable(MmTelCapabilities.CAPABILITY_TYPE_UT));
        sb.append(", sms=").append(c.isCapable(MmTelCapabilities.CAPABILITY_TYPE_SMS));
        sb.append("]");
        return sb.toString();
    }

    private static String disconnectedCauseToString(@DisconnectedReason int reason) {
        switch (reason) {
            case ImsStateCallback.REASON_UNKNOWN_TEMPORARY_ERROR:
                return "UNKNOWN_TEMPORARY_ERROR";
            case ImsStateCallback.REASON_UNKNOWN_PERMANENT_ERROR:
                return "UNKNOWN_PERMANENT_ERROR";
            case ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED:
                return "IMS_SERVICE_DISCONNECTED";
            case ImsStateCallback.REASON_NO_IMS_SERVICE_CONFIGURED:
                return "NO_IMS_SERVICE_CONFIGURED";
            case ImsStateCallback.REASON_SUBSCRIPTION_INACTIVE:
                return "SUBSCRIPTION_INACTIVE";
            case ImsStateCallback.REASON_IMS_SERVICE_NOT_READY:
                return "IMS_SERVICE_NOT_READY";
            default:
                return Integer.toString(reason);
        }
    }

    /**
     * Dumps this instance into a readable format for dumpsys usage.
     */
    public void dump(@NonNull PrintWriter pw) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("ImsStateTracker:");
        ipw.increaseIndent();
        ipw.println("SlotId: " + getSlotId());
        ipw.println("SubId: " + getSubId());
        ipw.println("ServiceState: " + mServiceState);
        ipw.println("BarringInfo: " + mBarringInfo);
        ipw.println("ImsState: " + imsStateToString());
        ipw.println("Event Log:");
        ipw.increaseIndent();
        mEventLog.dump(ipw);
        ipw.decreaseIndent();
        ipw.decreaseIndent();
    }

    private void logd(String s) {
        Log.d(TAG, "[" + getSlotId() + "|" + getSubId() + "] " + s);
    }

    private void logi(String s) {
        Log.i(TAG, "[" + getSlotId() + "|" + getSubId() + "] " + s);
        mEventLog.log("[" + getSlotId() + "|" + getSubId() + "] " + s);
    }

    private void loge(String s) {
        Log.e(TAG, "[" + getSlotId() + "|" + getSubId() + "] " + s);
        mEventLog.log("[" + getSlotId() + "|" + getSubId() + "] " + s);
    }

    private void logw(String s) {
        Log.w(TAG, "[" + getSlotId() + "|" + getSubId() + "] " + s);
        mEventLog.log("[" + getSlotId() + "|" + getSubId() + "] " + s);
    }
}
