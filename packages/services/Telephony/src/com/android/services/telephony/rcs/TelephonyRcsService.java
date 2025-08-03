/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.services.telephony.rcs;

import android.annotation.AnyThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneConfigurationManager;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.ImsStateCallbackController;
import com.android.phone.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Singleton service setup to manage RCS related services that the platform provides such as User
 * Capability Exchange.
 */
@AnyThread
public class TelephonyRcsService {

    private static final String LOG_TAG = "TelephonyRcsService";

    /**
     * Used to inject RcsFeatureController and UceController instances for testing.
     */
    @VisibleForTesting
    public interface FeatureFactory {
        /**
         * @return an {@link RcsFeatureController} associated with the slot specified.
         */
        RcsFeatureController createController(Context context, int slotId, int subId);

        /**
         * @return an instance of {@link UceControllerManager} associated with the slot specified.
         */
        UceControllerManager createUceControllerManager(Context context, int slotId, int subId);

        /**
         * @return an instance of {@link SipTransportController} for the slot and subscription
         * specified.
         */
        SipTransportController createSipTransportController(Context context, int slotId, int subId);
    }

    private FeatureFactory mFeatureFactory = new FeatureFactory() {
        @Override
        public RcsFeatureController createController(Context context, int slotId, int subId) {
            return new RcsFeatureController(context, slotId, subId);
        }

        @Override
        public UceControllerManager createUceControllerManager(Context context, int slotId,
                int subId) {
            return new UceControllerManager(context, slotId, subId);
        }

        @Override
        public SipTransportController createSipTransportController(Context context, int slotId,
                int subId) {
            return new SipTransportController(context, slotId, subId);
        }
    };

    /**
     * Used to inject device resource for testing.
     */
    @VisibleForTesting
    public interface ResourceProxy {
        /**
         * @return an whether the device supports User Capability Exchange.
         */
        boolean getDeviceUceEnabled(Context context);
    }

    private static ResourceProxy sResourceProxy = context -> {
        return context.getResources().getBoolean(
                R.bool.config_rcs_user_capability_exchange_enabled);
    };

    // Notifies this service that there has been a change in available slots.
    private static final int HANDLER_MSIM_CONFIGURATION_CHANGE = 1;

    private final Context mContext;
    private final Object mLock = new Object();
    private int mNumSlots;

    // Maps slot ID -> RcsFeatureController.
    private SparseArray<RcsFeatureController> mFeatureControllers;
    // Maps slotId -> associatedSubIds
    private SparseArray<Integer> mSlotToAssociatedSubIds;

    // Whether the device supports User Capability Exchange
    private boolean mRcsUceEnabled;

    private BroadcastReceiver mCarrierConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle == null) {
                    return;
                }
                int slotId = bundle.getInt(CarrierConfigManager.EXTRA_SLOT_INDEX,
                        SubscriptionManager.INVALID_PHONE_INDEX);
                int subId = bundle.getInt(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                onCarrierConfigChangedForSlot(slotId, subId);
            }
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper(), (msg) -> {
        switch (msg.what) {
            case HANDLER_MSIM_CONFIGURATION_CHANGE: {
                AsyncResult result = (AsyncResult) msg.obj;
                Integer numSlots = (Integer) result.result;
                if (numSlots == null) {
                    Log.w(LOG_TAG, "msim config change with null num slots.");
                    break;
                }
                updateFeatureControllerSize(numSlots);
                break;
            }
            default:
                return false;
        }
        return true;
    });

    public TelephonyRcsService(Context context, int numSlots) {
        mContext = context;
        mNumSlots = numSlots;
        mFeatureControllers = new SparseArray<>(numSlots);
        mSlotToAssociatedSubIds = new SparseArray<>(numSlots);
        mRcsUceEnabled = sResourceProxy.getDeviceUceEnabled(mContext);
        RcsStats.getInstance().registerUceCallback();
    }

    @VisibleForTesting
    public TelephonyRcsService(Context context, int numSlots, ResourceProxy resourceProxy) {
        mContext = context;
        mNumSlots = numSlots;
        mFeatureControllers = new SparseArray<>(numSlots);
        mSlotToAssociatedSubIds = new SparseArray<>(numSlots);
        sResourceProxy = resourceProxy;
        mRcsUceEnabled = sResourceProxy.getDeviceUceEnabled(mContext);
        RcsStats.getInstance().registerUceCallback();
    }

    /**
     * @return the {@link RcsFeatureController} associated with the given slot.
     */
    public RcsFeatureController getFeatureController(int slotId) {
        synchronized (mLock) {
            return mFeatureControllers.get(slotId);
        }
    }

    /**
     * Called after instance creation to initialize internal structures as well as register for
     * system callbacks.
     */
    public void initialize() {
        updateFeatureControllerSize(mNumSlots);

        PhoneConfigurationManager.registerForMultiSimConfigChange(mHandler,
                HANDLER_MSIM_CONFIGURATION_CHANGE, null);
        mContext.registerReceiver(mCarrierConfigChangedReceiver, new IntentFilter(
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
    }

    @VisibleForTesting
    public void setFeatureFactory(FeatureFactory f) {
        mFeatureFactory = f;
    }

    /**
     * Update the number of {@link RcsFeatureController}s that are created based on the number of
     * active slots on the device.
     */
    @VisibleForTesting
    public void updateFeatureControllerSize(int newNumSlots) {
        synchronized (mLock) {
            int oldNumSlots = mFeatureControllers.size();
            if (oldNumSlots == newNumSlots) {
                return;
            }
            Log.i(LOG_TAG, "updateFeatureControllers: oldSlots=" + oldNumSlots + ", newNumSlots="
                    + newNumSlots);
            mNumSlots = newNumSlots;
            if (oldNumSlots < newNumSlots) {
                for (int i = oldNumSlots; i < newNumSlots; i++) {
                    RcsFeatureController c = constructFeatureController(i);
                    // Do not add feature controllers for inactive subscriptions
                    if (c.hasActiveFeatures()) {
                        mFeatureControllers.put(i, c);
                        // Do not change mSlotToAssociatedSubIds, it will be updated upon carrier
                        // config change.
                    }
                }
            } else {
                for (int i = (oldNumSlots - 1); i > (newNumSlots - 1); i--) {
                    RcsFeatureController c = mFeatureControllers.get(i);
                    if (c != null) {
                        mFeatureControllers.remove(i);
                        mSlotToAssociatedSubIds.remove(i);
                        c.destroy();
                    }
                }
            }
        }
    }

    /**
     * Verifies the subId supplied is the active subId for the slotId specified.
     * If we have not processed a CARRIER_CONFIG_CHANGED indication for this subscription yet,
     * either the subscription is not active or we have not finished setting up the feature yet.
     * @param slotId The slotId we are verifying
     * @param subId The subId we are verifying
     * @return true if the subId is the active subId we are tracking for the slotId specified.
     */
    public boolean verifyActiveSubId(int slotId, int subId) {
        synchronized (mLock) {
            int currId = mSlotToAssociatedSubIds.get(slotId,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            return subId == currId;
        }
    }

    /**
     * ACTION_CARRIER_CONFIG_CHANGED was received by this service for a specific slot.
     * @param slotId The slotId associated with the event.
     * @param subId The subId associated with the event. May cause the subId associated with the
     *              RcsFeatureController to change if the subscription itself has changed.
     */
    private void onCarrierConfigChangedForSlot(int slotId, int subId) {
        synchronized (mLock) {
            RcsFeatureController f = mFeatureControllers.get(slotId);
            final int oldSubId = mSlotToAssociatedSubIds.get(slotId,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            mSlotToAssociatedSubIds.put(slotId, subId);
            Log.i(LOG_TAG, "updateFeatureControllerSubscription: slotId=" + slotId
                    + ", oldSubId= " + oldSubId + ", subId=" + subId + ", existing feature="
                    + (f != null));
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                if (f == null) {
                    // A controller doesn't exist for this slot yet.
                    f = mFeatureFactory.createController(mContext, slotId, subId);
                    updateSupportedFeatures(f, slotId, subId);
                    if (f.hasActiveFeatures()) mFeatureControllers.put(slotId, f);
                } else {
                    updateSupportedFeatures(f, slotId, subId);
                    // Do not keep an empty container around.
                    if (!f.hasActiveFeatures()) {
                        f.destroy();
                        mFeatureControllers.remove(slotId);
                    }
                }
            }
            if (f != null) {
                if (oldSubId == subId) {
                    f.onCarrierConfigChangedForSubscription();
                } else {
                    f.updateAssociatedSubscription(subId);
                }
            }
        }
    }

    private RcsFeatureController constructFeatureController(int slotId) {
        int subId = getSubscriptionFromSlot(slotId);
        RcsFeatureController c = mFeatureFactory.createController(mContext, slotId, subId);
        updateSupportedFeatures(c, slotId, subId);
        return c;
    }

    private void updateSupportedFeatures(RcsFeatureController c, int slotId, int subId) {
        if (isDeviceUceEnabled() && doesSubscriptionSupportPresence(subId)) {
            if (c.getFeature(UceControllerManager.class) == null) {
                c.addFeature(mFeatureFactory.createUceControllerManager(mContext, slotId, subId),
                        UceControllerManager.class);
            }
        } else {
            if (c.getFeature(UceControllerManager.class) != null) {
                c.removeFeature(UceControllerManager.class);
            }
        }

        if (doesSubscriptionSupportSingleRegistration(subId)) {
            if (c.getFeature(SipTransportController.class) == null) {
                c.addFeature(mFeatureFactory.createSipTransportController(mContext, slotId, subId),
                        SipTransportController.class);
            }
        } else {
            if (c.getFeature(SipTransportController.class) != null) {
                c.removeFeature(SipTransportController.class);
            }
        }
        // Only start the connection procedure if we have active features.
        if (c.hasActiveFeatures()) c.connect();

        ImsStateCallbackController.getInstance()
                .notifyExternalRcsStateChanged(slotId, false, c.hasActiveFeatures());
    }

    /**
     * Get whether the device supports RCS User Capability Exchange or not.
     */
    public boolean isDeviceUceEnabled() {
        return mRcsUceEnabled;
    }

    /**
     * Set the device supports RCS User Capability Exchange.
     */
    public void setDeviceUceEnabled(boolean isEnabled) {
        mRcsUceEnabled = isEnabled;
    }

    private boolean doesSubscriptionSupportPresence(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return false;
        boolean supportsUce = getConfig(subId,
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL, false /*default*/);
        supportsUce |= getConfig(subId,
                CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false /*default*/);
        return supportsUce;
    }

    private boolean doesSubscriptionSupportSingleRegistration(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) return false;
        return getConfig(subId, CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL,
                false /*defaultValue*/);
    }

    private int getSubscriptionFromSlot(int slotId) {
        return SubscriptionManager.getSubscriptionId(slotId);
    }

    /**
     * @return the boolean result corresponding to a boolean {@link CarrierConfigManager} key.
     */
    private boolean getConfig(int subId, String key, boolean defaultValue) {
        CarrierConfigManager c = mContext.getSystemService(CarrierConfigManager.class);
        if (c == null) return defaultValue;
        PersistableBundle b = c.getConfigForSubId(subId, key);
        return b != null ? b.getBoolean(key, defaultValue) : defaultValue;
    }

    /**
     * Dump this instance into a readable format for dumpsys usage.
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("RcsFeatureControllers:");
        pw.increaseIndent();
        synchronized (mLock) {
            for (int i = 0; i < mNumSlots; i++) {
                RcsFeatureController f = mFeatureControllers.get(i);
                if (f == null) continue;
                pw.increaseIndent();
                f.dump(fd, printWriter, args);
                pw.decreaseIndent();
            }
        }
        pw.decreaseIndent();
    }
}
