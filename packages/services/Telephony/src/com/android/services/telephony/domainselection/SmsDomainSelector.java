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
import android.os.Looper;
import android.os.Message;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TransportSelectorCallback;

/**
 * Implements SMS domain selector for sending MO SMS.
 */
public class SmsDomainSelector extends DomainSelectorBase implements
        ImsStateTracker.ImsStateListener {
    protected static final int EVENT_SELECT_DOMAIN = 101;

    protected boolean mDestroyed = false;
    private boolean mDomainSelectionRequested = false;

    public SmsDomainSelector(Context context, int slotId, int subId, @NonNull Looper looper,
            @NonNull ImsStateTracker imsStateTracker, @NonNull DestroyListener listener) {
        this(context, slotId, subId, looper, imsStateTracker, listener, "DomainSelector-SMS");
    }

    protected SmsDomainSelector(Context context, int slotId, int subId, @NonNull Looper looper,
            @NonNull ImsStateTracker imsStateTracker, @NonNull DestroyListener listener,
            String logTag) {
        super(context, slotId, subId, looper, imsStateTracker, listener, logTag);
    }

    @Override
    public void destroy() {
        if (mDestroyed) {
            return;
        }
        logd("destroy");
        mDestroyed = true;
        mImsStateTracker.removeImsStateListener(this);
        super.destroy();
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_SELECT_DOMAIN:
                selectDomain();
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    @Override
    public void reselectDomain(@NonNull SelectionAttributes attr) {
        if (isDomainSelectionRequested()) {
            // The domain selection is already requested,
            // so we don't need to request it again before completing the previous task.
            logi("Domain selection is already running.");
            return;
        }

        logi("reselectDomain");
        mSelectionAttributes = attr;
        setDomainSelectionRequested(true);
        obtainMessage(EVENT_SELECT_DOMAIN).sendToTarget();
    }

    @Override
    public void finishSelection() {
        logi("finishSelection");
        setDomainSelectionRequested(false);
        mSelectionAttributes = null;
        mTransportSelectorCallback = null;
        mWwanSelectorCallback = null;
        destroy();
    }

    @Override
    public void selectDomain(SelectionAttributes attr, TransportSelectorCallback callback) {
        if (isDomainSelectionRequested()) {
            // The domain selection is already requested,
            // so we don't need to request it again before completing the previous task.
            logi("Domain selection is already running.");
            return;
        }
        mSelectionAttributes = attr;
        mTransportSelectorCallback = callback;
        setDomainSelectionRequested(true);
        mImsStateTracker.addImsStateListener(this);
        obtainMessage(EVENT_SELECT_DOMAIN).sendToTarget();
    }

    @Override
    public void onImsMmTelFeatureAvailableChanged() {
        sendMessageForDomainSelection();
    }

    @Override
    public void onImsRegistrationStateChanged() {
        sendMessageForDomainSelection();
    }

    @Override
    public void onImsMmTelCapabilitiesChanged() {
        sendMessageForDomainSelection();
    }

    protected boolean isSmsOverImsAvailable() {
        return mImsStateTracker.isImsSmsCapable()
                && mImsStateTracker.isImsRegistered()
                && mImsStateTracker.isMmTelFeatureAvailable();
    }

    protected void selectDomain() {
        if (!isDomainSelectionRequested()) {
            logi("Domain selection is not requested!");
            return;
        }

        logi("selectDomain: " + mImsStateTracker.imsStateToString());

        if (isSmsOverImsAvailable()) {
            if (mImsStateTracker.isImsRegisteredOverWlan()) {
                notifyWlanSelected(false);
                return;
            }
            notifyWwanSelected(NetworkRegistrationInfo.DOMAIN_PS, false);
        } else {
            notifyWwanSelected(NetworkRegistrationInfo.DOMAIN_CS, false);
        }
    }

    protected void sendMessageForDomainSelection() {
        // If the event is already queued to this handler,
        // it will be removed first to avoid the duplicate operation.
        removeMessages(EVENT_SELECT_DOMAIN);
        // Since the IMS state may have already been posted,
        // proceed with the domain selection after processing all pending messages.
        obtainMessage(EVENT_SELECT_DOMAIN).sendToTarget();
    }

    protected boolean isDomainSelectionRequested() {
        return mDomainSelectionRequested;
    }

    protected void setDomainSelectionRequested(boolean requested) {
        if (mDomainSelectionRequested != requested) {
            logd("DomainSelectionRequested: " + mDomainSelectionRequested + " >> " + requested);
            mDomainSelectionRequested = requested;
        }
    }

    protected void notifyWlanSelected(boolean useEmergencyPdn) {
        logi("DomainSelected: WLAN, E-PDN=" + useEmergencyPdn);
        mTransportSelectorCallback.onWlanSelected(useEmergencyPdn);
        setDomainSelectionRequested(false);
    }

    protected void notifyWwanSelected(@NetworkRegistrationInfo.Domain int domain,
            boolean useEmergencyPdn) {
        if (mWwanSelectorCallback == null) {
            mTransportSelectorCallback.onWwanSelected((callback) -> {
                mWwanSelectorCallback = callback;
                notifyWwanSelectedInternal(domain, useEmergencyPdn);
            });
        } else {
            notifyWwanSelectedInternal(domain, useEmergencyPdn);
        }

        setDomainSelectionRequested(false);
    }

    protected void notifyWwanSelectedInternal(@NetworkRegistrationInfo.Domain int domain,
            boolean useEmergencyPdn) {
        logi("DomainSelected: WWAN/" + DomainSelectionService.getDomainName(domain)
                + ", E-PDN=" + useEmergencyPdn);

        if (mWwanSelectorCallback != null) {
            mWwanSelectorCallback.onDomainSelected(domain, useEmergencyPdn);
        } else {
            mTransportSelectorCallback.onSelectionTerminated(DisconnectCause.LOCAL);
        }
    }
}
