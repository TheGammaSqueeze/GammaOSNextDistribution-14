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
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.DomainSelector;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.Keep;

import java.io.PrintWriter;

/**
 * An abstract base class to implement domain selector for a specific use case.
 */
@Keep
public abstract class DomainSelectorBase extends Handler implements DomainSelector {
    /**
     * A listener used to inform the DomainSelectorService that this DomainSelector has been
     * destroyed.
     */
    public interface DestroyListener {
        /**
         * Called when the specified domain selector is being destroyed.
         * This MUST be called when this domain selector is no longer available after
         * {@link DomainSelector#finishSelection} called.
         */
        void onDomainSelectorDestroyed(DomainSelectorBase selector);
    }

    // Persistent Logging
    protected final LocalLog mEventLog = new LocalLog(30);
    protected final Context mContext;
    protected final ImsStateTracker mImsStateTracker;
    protected SelectionAttributes mSelectionAttributes;
    protected TransportSelectorCallback mTransportSelectorCallback;
    protected WwanSelectorCallback mWwanSelectorCallback;
    private final int mSlotId;
    private final int mSubId;
    private final DestroyListener mDestroyListener;
    private final String mLogTag;

    public DomainSelectorBase(Context context, int slotId, int subId, @NonNull Looper looper,
            @NonNull ImsStateTracker imsStateTracker, @NonNull DestroyListener destroyListener,
            String logTag) {
        super(looper);
        mContext = context;
        mImsStateTracker = imsStateTracker;
        mSlotId = slotId;
        mSubId = subId;
        mDestroyListener = destroyListener;
        mLogTag = logTag;
    }

    /**
     * Selects a domain for the specified attributes and callback.
     *
     * @param attr The attributes required to determine the domain.
     * @param callback The callback called when the transport selection is completed.
     */
    public abstract void selectDomain(SelectionAttributes attr, TransportSelectorCallback callback);

    /**
     * Destroys this domain selector.
     */
    protected void destroy() {
        removeCallbacksAndMessages(null);
        notifyDomainSelectorDestroyed();
    }

    /**
     * Notifies the application that this domain selector is being destroyed.
     */
    protected void notifyDomainSelectorDestroyed() {
        if (mDestroyListener != null) {
            mDestroyListener.onDomainSelectorDestroyed(this);
        }
    }

    /**
     * Returns the slot index for this domain selector.
     */
    protected int getSlotId() {
        return mSlotId;
    }

    /**
     * Returns the subscription index for this domain selector.
     */
    protected int getSubId() {
        return mSubId;
    }

    /**
     * Dumps this instance into a readable format for dumpsys usage.
     */
    protected void dump(@NonNull PrintWriter pw) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println(mLogTag + ":");
        ipw.increaseIndent();
        ipw.println("SlotId: " + getSlotId());
        ipw.println("SubId: " + getSubId());
        mEventLog.dump(ipw);
        ipw.decreaseIndent();
    }

    protected void logd(String s) {
        Log.d(mLogTag, "[" + getSlotId() + "|" + getSubId() + "] " + s);
    }

    protected void logi(String s) {
        Log.i(mLogTag, "[" + getSlotId() + "|" + getSubId() + "] " + s);
        mEventLog.log("[" + getSlotId() + "|" + getSubId() + "] " + s);
    }

    protected void loge(String s) {
        Log.e(mLogTag, "[" + getSlotId() + "|" + getSubId() + "] " + s);
        mEventLog.log("[" + getSlotId() + "|" + getSubId() + "] " + s);
    }
}
