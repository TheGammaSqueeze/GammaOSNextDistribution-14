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
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.BarringInfo;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.TransportSelectorCallback;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.flags.Flags;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Implements the telephony domain selection for various telephony features.
 */
public class TelephonyDomainSelectionService extends DomainSelectionService {
    /**
     * Testing interface for injecting mock ImsStateTracker.
     */
    @VisibleForTesting
    public interface ImsStateTrackerFactory {
        /**
         * @return The {@link ImsStateTracker} created for the specified slot.
         */
        ImsStateTracker create(Context context, int slotId, @NonNull Looper looper);
    }

    /**
     * Testing interface for injecting mock DomainSelector.
     */
    @VisibleForTesting
    public interface DomainSelectorFactory {
        /**
         * @return The {@link DomainSelectorBase} created using the specified arguments.
         */
        DomainSelectorBase create(Context context, int slotId, int subId,
                @SelectorType int selectorType, boolean isEmergency, @NonNull Looper looper,
                @NonNull ImsStateTracker imsStateTracker,
                @NonNull DomainSelectorBase.DestroyListener listener,
                @NonNull CrossSimRedialingController crossSimRedialingController,
                @NonNull CarrierConfigHelper carrierConfigHelper,
                @NonNull EmergencyCallbackModeHelper emergencyCallbackModeHelper);
    }

    private static final class DefaultDomainSelectorFactory implements DomainSelectorFactory {
        @Override
        public DomainSelectorBase create(Context context, int slotId, int subId,
                @SelectorType int selectorType, boolean isEmergency, @NonNull Looper looper,
                @NonNull ImsStateTracker imsStateTracker,
                @NonNull DomainSelectorBase.DestroyListener listener,
                @NonNull CrossSimRedialingController crossSimRedialingController,
                @NonNull CarrierConfigHelper carrierConfigHelper,
                @NonNull EmergencyCallbackModeHelper emergencyCallbackModeHelper) {
            DomainSelectorBase selector = null;

            logi("create-DomainSelector: slotId=" + slotId + ", subId=" + subId
                    + ", selectorType=" + selectorTypeToString(selectorType)
                    + ", emergency=" + isEmergency);

            switch (selectorType) {
                case SELECTOR_TYPE_CALLING:
                    if (isEmergency) {
                        selector = new EmergencyCallDomainSelector(context, slotId, subId, looper,
                                imsStateTracker, listener, crossSimRedialingController,
                                carrierConfigHelper, emergencyCallbackModeHelper);
                    } else {
                        selector = new NormalCallDomainSelector(context, slotId, subId, looper,
                                imsStateTracker, listener);
                    }
                    break;
                case SELECTOR_TYPE_SMS:
                    if (isEmergency) {
                        selector = new EmergencySmsDomainSelector(context, slotId, subId, looper,
                                imsStateTracker, listener);
                    } else {
                        selector = new SmsDomainSelector(context, slotId, subId, looper,
                                imsStateTracker, listener);
                    }
                    break;
                default:
                    // Not reachable.
                    break;
            }

            return selector;
        }
    };

    /**
     * A container class to manage the domain selector per a slot and selector type.
     * If the domain selector is not null and reusable, the same domain selector will be used
     * for the specific slot.
     */
    private static final class DomainSelectorContainer {
        private final int mSlotId;
        private final @SelectorType int mSelectorType;
        private final boolean mIsEmergency;
        private final @NonNull DomainSelectorBase mSelector;

        DomainSelectorContainer(int slotId, @SelectorType int selectorType, boolean isEmergency,
                @NonNull DomainSelectorBase selector) {
            mSlotId = slotId;
            mSelectorType = selectorType;
            mIsEmergency = isEmergency;
            mSelector = selector;
        }

        public int getSlotId() {
            return mSlotId;
        }

        public @SelectorType int getSelectorType() {
            return mSelectorType;
        }

        public DomainSelectorBase getDomainSelector() {
            return mSelector;
        }

        public boolean isEmergency() {
            return mIsEmergency;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("{ ")
                    .append("slotId=").append(mSlotId)
                    .append(", selectorType=").append(selectorTypeToString(mSelectorType))
                    .append(", isEmergency=").append(mIsEmergency)
                    .append(", selector=").append(mSelector)
                    .append(" }").toString();
        }
    }

    private final DomainSelectorBase.DestroyListener mDestroyListener =
            new DomainSelectorBase.DestroyListener() {
        @Override
        public void onDomainSelectorDestroyed(DomainSelectorBase selector) {
            logd("DomainSelector destroyed: " + selector);
            removeDomainSelector(selector);
        }
    };

    /**
     * A class to listen for the subscription change for starting {@link ImsStateTracker}
     * to monitor the IMS states.
     */
    private final OnSubscriptionsChangedListener mSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    handleSubscriptionsChanged();
                }
            };

    private static final String TAG = TelephonyDomainSelectionService.class.getSimpleName();

    // Persistent Logging
    private static final LocalLog sEventLog = new LocalLog(20);
    private Context mContext;
    // Map of slotId -> ImsStateTracker
    private final SparseArray<ImsStateTracker> mImsStateTrackers = new SparseArray<>(2);
    private final List<DomainSelectorContainer> mDomainSelectorContainers = new ArrayList<>();
    private final ImsStateTrackerFactory mImsStateTrackerFactory;
    private final DomainSelectorFactory mDomainSelectorFactory;
    private Handler mServiceHandler;
    private CrossSimRedialingController mCrossSimRedialingController;
    private CarrierConfigHelper mCarrierConfigHelper;
    private EmergencyCallbackModeHelper mEmergencyCallbackModeHelper;

    /** Default constructor. */
    public TelephonyDomainSelectionService() {
        this(ImsStateTracker::new, new DefaultDomainSelectorFactory(), null, null);
    }

    @VisibleForTesting
    protected TelephonyDomainSelectionService(
            @NonNull ImsStateTrackerFactory imsStateTrackerFactory,
            @NonNull DomainSelectorFactory domainSelectorFactory,
            @Nullable CarrierConfigHelper carrierConfigHelper,
            @Nullable EmergencyCallbackModeHelper ecbmHelper) {
        mImsStateTrackerFactory = imsStateTrackerFactory;
        mDomainSelectorFactory = domainSelectorFactory;
        mCarrierConfigHelper = carrierConfigHelper;
    }

    @Override
    public void onCreate() {
        logd("onCreate");
        mContext = getApplicationContext();

        // Create a worker thread for this domain selection service.
        getCreateExecutor();

        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        int activeModemCount = (tm != null) ? tm.getActiveModemCount() : 1;
        for (int i = 0; i < activeModemCount; ++i) {
            mImsStateTrackers.put(i, mImsStateTrackerFactory.create(mContext, i, getLooper()));
        }

        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (sm != null) {
            sm.addOnSubscriptionsChangedListener(getExecutor(), mSubscriptionsChangedListener);
        } else {
            loge("Adding OnSubscriptionChangedListener failed");
        }

        mCrossSimRedialingController = new CrossSimRedialingController(mContext, getLooper());
        if (mCarrierConfigHelper == null) {
            mCarrierConfigHelper = new CarrierConfigHelper(mContext, getLooper());
        }
        if (mEmergencyCallbackModeHelper == null) {
            mEmergencyCallbackModeHelper = new EmergencyCallbackModeHelper(mContext, getLooper());
        }

        logi("TelephonyDomainSelectionService created");
    }

    @Override
    public void onDestroy() {
        logd("onDestroy");

        List<DomainSelectorContainer> domainSelectorContainers;

        synchronized (mDomainSelectorContainers) {
            domainSelectorContainers = new ArrayList<>(mDomainSelectorContainers);
            mDomainSelectorContainers.clear();
        }

        for (DomainSelectorContainer dsc : domainSelectorContainers) {
            DomainSelectorBase selector = dsc.getDomainSelector();
            if (selector != null) {
                selector.destroy();
            }
        }
        domainSelectorContainers.clear();

        synchronized (mImsStateTrackers) {
            for (int i = 0; i < mImsStateTrackers.size(); ++i) {
                ImsStateTracker ist = mImsStateTrackers.get(i);
                if (ist != null) {
                    ist.destroy();
                }
            }
            mImsStateTrackers.clear();
        }

        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (sm != null) {
            sm.removeOnSubscriptionsChangedListener(mSubscriptionsChangedListener);
        }

        if (mCrossSimRedialingController != null) {
            mCrossSimRedialingController.destroy();
            mCrossSimRedialingController = null;
        }

        if (mCarrierConfigHelper != null) {
            mCarrierConfigHelper.destroy();
            mCarrierConfigHelper = null;
        }

        if (mEmergencyCallbackModeHelper != null) {
            mEmergencyCallbackModeHelper.destroy();
            mEmergencyCallbackModeHelper = null;
        }

        if (mServiceHandler != null) {
            mServiceHandler.getLooper().quit();
            mServiceHandler = null;
        }
    }

    /**
     * Selects a domain for the given attributes and callback.
     *
     * @param attr required to determine the domain.
     * @param callback the callback instance being registered.
     */
    @Override
    public void onDomainSelection(@NonNull SelectionAttributes attr,
            @NonNull TransportSelectorCallback callback) {
        final int slotId = attr.getSlotIndex();
        final int subId = attr.getSubscriptionId();
        final int selectorType = attr.getSelectorType();
        final boolean isEmergency = attr.isEmergency();
        ImsStateTracker ist = getImsStateTracker(slotId);
        DomainSelectorBase selector = mDomainSelectorFactory.create(mContext, slotId, subId,
                selectorType, isEmergency, getLooper(), ist, mDestroyListener,
                mCrossSimRedialingController, mCarrierConfigHelper, mEmergencyCallbackModeHelper);

        if (selector != null) {
            // Ensures that ImsStateTracker is started before selecting the domain if not started
            // for the specified subscription index.
            ist.start(subId);
            addDomainSelector(slotId, selectorType, isEmergency, selector);
        } else {
            loge("No proper domain selector: " + selectorTypeToString(selectorType));
            // Executed through the service handler to ensure that the callbacks are not called
            // directly in this execution flow.
            mServiceHandler.post(() ->
                    callback.onSelectionTerminated(DisconnectCause.ERROR_UNSPECIFIED));
            return;
        }

        // Executed through the service handler to ensure that the callbacks are not called
        // directly in this execution flow.
        mServiceHandler.post(() ->  {
            // Notify the caller that the domain selector is created.
            callback.onCreated(selector);
            // Performs the domain selection.
            selector.selectDomain(attr, callback);
        });
    }

    /**
     * Called when the {@link ServiceState} needs to be updated for the specified slot and
     * subcription index.
     *
     * @param slotId for which the service state changed.
     * @param subId The current subscription for a specified slot.
     * @param serviceState The {@link ServiceState} to be updated.
     */
    @Override
    public void onServiceStateUpdated(int slotId, int subId, @NonNull ServiceState serviceState) {
        ImsStateTracker ist = getImsStateTracker(slotId);
        if (ist != null) {
            ist.updateServiceState(serviceState);
        }
    }

    /**
     * Called when the {@link BarringInfo} needs to be updated for the specified slot and
     * subscription index.
     *
     * @param slotId The slot the BarringInfo is updated for.
     * @param subId The current subscription for a specified slot.
     * @param barringInfo The {@link BarringInfo} to be updated.
     */
    @Override
    public void onBarringInfoUpdated(int slotId, int subId, @NonNull BarringInfo barringInfo) {
        ImsStateTracker ist = getImsStateTracker(slotId);
        if (ist != null) {
            ist.updateBarringInfo(barringInfo);
        }
    }

    /**
     *  Returns an Executor used to execute methods called remotely by the framework.
     */
    @Override
    public @NonNull Executor getCreateExecutor() {
        return getExecutor();
    }

    /**
     *  Returns an Executor used to execute methods called remotely by the framework.
     */
    @VisibleForTesting
    public @NonNull Executor getExecutor() {
        if (mServiceHandler == null) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            mServiceHandler = new Handler(handlerThread.getLooper());
        }

        return mServiceHandler::post;
    }

    /**
     * Returns a Looper instance.
     */
    @VisibleForTesting
    public Looper getLooper() {
        getExecutor();
        return mServiceHandler.getLooper();
    }

    /**
     * Handles the subscriptions change.
     */
    private void handleSubscriptionsChanged() {
        SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        if (Flags.workProfileApiSplit()) {
            sm = sm.createForAllUserProfiles();
        }
        List<SubscriptionInfo> subsInfoList =
                (sm != null) ? sm.getActiveSubscriptionInfoList() : null;

        if (subsInfoList == null || subsInfoList.isEmpty()) {
            logd("handleSubscriptionsChanged: No valid SubscriptionInfo");
            return;
        }

        for (int i = 0; i < subsInfoList.size(); ++i) {
            SubscriptionInfo subsInfo = subsInfoList.get(i);
            int slotId = subsInfo.getSimSlotIndex();

            if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                logd("handleSubscriptionsChanged: slotId=" + slotId);
                ImsStateTracker ist = getImsStateTracker(slotId);
                ist.start(subsInfo.getSubscriptionId());
            }
        }
    }

    /**
     * Adds the {@link DomainSelectorBase} to the list of domain selector container.
     */
    private void addDomainSelector(int slotId, @SelectorType int selectorType,
            boolean isEmergency, @NonNull DomainSelectorBase selector) {
        synchronized (mDomainSelectorContainers) {
            // If the domain selector already exists, remove the previous one first.
            for (int i = 0; i < mDomainSelectorContainers.size(); ++i) {
                DomainSelectorContainer dsc = mDomainSelectorContainers.get(i);

                if (dsc.getSlotId() == slotId
                        && dsc.getSelectorType() == selectorType
                        && dsc.isEmergency() == isEmergency) {
                    mDomainSelectorContainers.remove(i);
                    DomainSelectorBase oldSelector = dsc.getDomainSelector();
                    if (oldSelector != null) {
                        logw("DomainSelector destroyed by new domain selection request: " + dsc);
                        oldSelector.destroy();
                    }
                    break;
                }
            }

            DomainSelectorContainer dsc =
                    new DomainSelectorContainer(slotId, selectorType, isEmergency, selector);
            mDomainSelectorContainers.add(dsc);

            logi("DomainSelector added: " + dsc + ", count=" + mDomainSelectorContainers.size());
        }
    }

    /**
     * Removes the domain selector container that matches with the specified
     * {@link DomainSelectorBase}.
     */
    private void removeDomainSelector(@NonNull DomainSelectorBase selector) {
        synchronized (mDomainSelectorContainers) {
            for (int i = 0; i < mDomainSelectorContainers.size(); ++i) {
                DomainSelectorContainer dsc = mDomainSelectorContainers.get(i);

                if (dsc.getDomainSelector() == selector) {
                    mDomainSelectorContainers.remove(i);
                    logi("DomainSelector removed: " + dsc
                            + ", count=" + mDomainSelectorContainers.size());
                    break;
                }
            }
        }
    }

    /**
     * Returns the {@link ImsStateTracker} instance for the specified slot.
     * If the {@link ImsStateTracker} does not exist for the slot, it creates new instance
     * and returns.
     */
    private ImsStateTracker getImsStateTracker(int slotId) {
        synchronized (mImsStateTrackers) {
            ImsStateTracker ist = mImsStateTrackers.get(slotId);

            if (ist == null) {
                ist = mImsStateTrackerFactory.create(mContext, slotId, getLooper());
                mImsStateTrackers.put(slotId, ist);
            }

            return ist;
        }
    }

    private static String selectorTypeToString(@SelectorType int selectorType) {
        switch (selectorType) {
            case SELECTOR_TYPE_CALLING: return "CALLING";
            case SELECTOR_TYPE_SMS: return "SMS";
            default: return Integer.toString(selectorType);
        }
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void logi(String s) {
        Log.i(TAG, s);
        sEventLog.log(s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
        sEventLog.log(s);
    }

    private static void logw(String s) {
        Log.w(TAG, s);
        sEventLog.log(s);
    }

    /**
     * Dumps this instance into a readable format for dumpsys usage.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("TelephonyDomainSelectionService:");
        ipw.increaseIndent();
        ipw.println("ImsStateTrackers:");
        synchronized (mImsStateTrackers) {
            for (int i = 0; i < mImsStateTrackers.size(); ++i) {
                ImsStateTracker ist = mImsStateTrackers.valueAt(i);
                ist.dump(ipw);
            }
        }
        ipw.decreaseIndent();
        ipw.increaseIndent();
        synchronized (mDomainSelectorContainers) {
            for (int i = 0; i < mDomainSelectorContainers.size(); ++i) {
                DomainSelectorContainer dsc = mDomainSelectorContainers.get(i);
                ipw.println("DomainSelector: " + dsc.toString());
                ipw.increaseIndent();
                DomainSelectorBase selector = dsc.getDomainSelector();
                if (selector != null) {
                    selector.dump(ipw);
                }
                ipw.decreaseIndent();
            }
        }
        ipw.decreaseIndent();
        ipw.increaseIndent();
        ipw.println("Event Log:");
        ipw.increaseIndent();
        sEventLog.dump(ipw);
        ipw.decreaseIndent();
        ipw.decreaseIndent();
        ipw.println("________________________________");
    }
}
