/* Copyright (c) 2016, 2017, 2019-2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */
package org.codeaurora.ims;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.ims.feature.ImsFeature;

import org.codeaurora.ims.internal.ICrsCrbtController;
import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.internal.IQtiImsExtListener;
import org.codeaurora.ims.internal.IImsArController;
import org.codeaurora.ims.internal.IImsMultiIdentityInterface;
import org.codeaurora.ims.internal.IImsScreenShareController;
import org.codeaurora.ims.utils.QtiImsExtUtils;
import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.VosActionInfo;

import java.util.concurrent.Executor;

/**
 * Base implementation for IQtiImsExt.
 * Introduce Executor pattern where API(s) will be called on the executor thread.
 * Except for non-oneway API(s) which call into ImsConfigImpl as setConfig/getConfig
 * are blocking AIDL calls.
 */
public abstract class QtiImsExtBase {

    /*
     * Implement the methods of the IQtiImsExt interface in this stub
     */
    public final class QtiImsExtBinder extends IQtiImsExt.Stub {

        @Override
        public void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute,
                int endHour, int endMinute, int action, int condition, int serviceClass,
                String number, IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() -> QtiImsExtBase.this.onSetCallForwardUncondTimer(
                    phoneId, startHour, startMinute, endHour, endMinute, action,
                    condition, serviceClass, number, listener),
                    "setCallForwardUncondTimer", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onGetCallForwardUncondTimer(phoneId, reason,
                    serviceClass, listener), "getCallForwardUncondTimer", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void resumePendingCall(int phoneId, int videoState) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onResumePendingCall(phoneId, videoState),
                    "resumePendingCall", mExecutor, QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSendCancelModifyCall(phoneId, listener),
                    "sendCancelModifyCall", mExecutor, QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void queryVopsStatus(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onQueryVopsStatus(phoneId, listener),
                    "queryVopsStatus", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void querySsacStatus(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onQuerySsacStatus(phoneId, listener),
                    "querySsacStatus", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onRegisterForParticipantStatusInfo(phoneId, listener),
                    "registerForParticipantStatusInfo", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void updateVoltePreference(int phoneId, int preference,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onUpdateVoltePreference(phoneId, preference, listener),
                    "updateVoltePreference", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void queryVoltePreference(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onQueryVoltePreference(phoneId, listener),
                    "queryVoltePreference", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void getHandoverConfig(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onGetHandoverConfig(phoneId, listener),
                    "getHandoverConfig", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void setHandoverConfig(int phoneId, int hoConfig,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSetHandoverConfig(phoneId, hoConfig, listener),
                    "setHandoverConfig", mExecutor, QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public void setUssdInfoListener(int phoneId, IQtiImsExtListener listener)
                throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSetUssdInfoListener(phoneId, listener),
                    "setUssdInfoListener", mExecutor, QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public int setRcsAppConfig(int phoneId, int defaultSmsApp) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onSetRcsAppConfig(phoneId, defaultSmsApp),
                    "setRcsAppConfig", getBinderExecutor(), QtiImsExtUtils.MODIFY_PHONE_STATE,
                    mContext);
        }

        @Override
        public void setDataChannelCapabilityListener(int phoneId,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSetDataChannelCapabilityListener(
                    phoneId, listener), "setDataChannelCapabilityListener", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public int getRcsAppConfig(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetRcsAppConfig(phoneId),
                    "getRcsAppConfig", getBinderExecutor(),
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public int setVvmAppConfig(int phoneId, int defaultVvmApp) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onSetVvmAppConfig(phoneId, defaultVvmApp),
                    "setVvmAppConfig", getBinderExecutor(),
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public int getVvmAppConfig(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetVvmAppConfig(phoneId),
                    "getVvmAppConfig", getBinderExecutor(),
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public IImsMultiIdentityInterface getMultiIdentityInterface(int phoneId)
                throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetMultiIdentityInterface(phoneId),
                    "getMultiIdentityInterface", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public IImsScreenShareController getScreenShareController(int phoneId)
                throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetScreenShareController(phoneId),
                    "getScreenShareController", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }

        @Override
        public int getImsFeatureState(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetImsFeatureState(phoneId),
                    "getImsFeatureState", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void setAnswerExtras(int phoneId, Bundle extras) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSetAnswerExtras(phoneId, extras),
                    "setAnswerExtras", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public boolean isCallComposerEnabled(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onIsCallComposerEnabled(phoneId),
                    "isCallComposerEnabled", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public ICrsCrbtController getCrsCrbtController(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetCrsCrbtController(phoneId),
                    "getCrsCrbtController", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void queryCallForwardStatus(int phoneId, int reason, int serviceClass,
                boolean expectMore, IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onQueryCallForwardStatus(phoneId, reason,
                    serviceClass, expectMore, listener),
                    "queryCallForwardStatus", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void queryCallBarring(int phoneId, int cbType, String password, int serviceClass,
                boolean expectMore, IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onQueryCallBarringStatus(phoneId, cbType,
                    password, serviceClass, expectMore, listener),
                    "queryCallBarring", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void exitScbm(int phoneId, IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onExitScbm(phoneId, listener),
                    "exitScbm", mExecutor, QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public boolean isExitScbmFeatureSupported(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onIsExitScbmFeatureSupported(phoneId),
                    "isExitScbmFeatureSupported", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public boolean isDataChannelEnabled(int phoneId) throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onIsDataChannelEnabled(phoneId),
                    "isDataChannelEnabled", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void sendVosSupportStatus(int phoneId, boolean isVosSupported,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() ->
                    QtiImsExtBase.this.onSendVosSupportStatus(phoneId,
                    isVosSupported, listener),
                    "sendVosSupportStatus", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public void sendVosActionInfo(int phoneId, VosActionInfo vosActionInfo,
                IQtiImsExtListener listener) throws RemoteException {
            QtiImsExtUtils.executeMethodAsync(() -> QtiImsExtBase.this.onSendVosActionInfo(phoneId,
                    vosActionInfo, listener),
                    "sendVosActionInfo", mExecutor,
                    QtiImsExtUtils.READ_PHONE_STATE, mContext);
        }

        @Override
        public IImsArController getArController(int phoneId)
                throws RemoteException {
            return QtiImsExtUtils.executeMethodAsyncForResult(() ->
                    QtiImsExtBase.this.onGetArController(phoneId),
                    "getArController", mExecutor,
                    QtiImsExtUtils.MODIFY_PHONE_STATE, mContext);
        }
    };

    private QtiImsExtBinder mQtiImsExtBinder;
    private Executor mExecutor;
    private Executor mBinderExecutor = Runnable::run;
    private Context mContext;

    public QtiImsExtBinder getBinder() {
        if (mQtiImsExtBinder == null) {
            mQtiImsExtBinder = new QtiImsExtBinder();
        }
        return mQtiImsExtBinder;
    }

    public QtiImsExtBase(Executor executor, Context context) {
        mExecutor = executor;
        mContext = context;
    }

    private Executor getBinderExecutor() {
        return mBinderExecutor;
    }

    protected void onSetCallForwardUncondTimer(int phoneId, int startHour, int startMinute,
            int endHour, int endMinute, int action, int condition, int serviceClass, String number,
            IQtiImsExtListener listener) {
        // no-op
    }
    protected void onGetCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener) {
        // no-op
    }
    protected void onResumePendingCall(int phoneId, int videoState) {
        // no-op
    }

    protected void onSendCancelModifyCall(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }

    protected void onSetUssdInfoListener(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }

    protected void onQueryVopsStatus(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onQuerySsacStatus(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onRegisterForViceRefreshInfo(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onRegisterForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onUpdateVoltePreference(int phoneId, int preference,
            IQtiImsExtListener listener) {
        // no-op
    }
    protected void onQueryVoltePreference(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onGetHandoverConfig(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onSetHandoverConfig(int phoneId, int hoConfig,
            IQtiImsExtListener listener) {
        // no-op
    }
    protected int onGetVvmAppConfig(int phoneId) {
        // no-op
        return 0; //DUMMY VALUE
    }
    protected int onSetVvmAppConfig(int phoneId, int defaultVvmApp) {
        // no-op
        return 0; //DUMMY VALUE
    }
    protected int onGetRcsAppConfig(int phoneId) {
        // no-op
        return 0; //DUMMY VALUE
    }
    protected int onSetRcsAppConfig(int phoneId, int defaultSmsApp) {
        // no-op
        return 0; //DUMMY VALUE
    }
    protected IImsMultiIdentityInterface onGetMultiIdentityInterface(int phoneId) {
        // no-op
        return null;
    }
    protected IImsScreenShareController onGetScreenShareController(int phoneId) {
        // no-op
        return null;
    }
    protected int onGetImsFeatureState(int phoneId) {
        // no-op
        return ImsFeature.STATE_UNAVAILABLE; //DUMMY VALUE
    }
    protected void onSetAnswerExtras(int phoneId, Bundle extras) {
        // no-op
    }
    protected boolean onIsCallComposerEnabled(int phoneId) {
        // no-op
        return false;
    }
    protected ICrsCrbtController onGetCrsCrbtController(int phoneId) {
        //no-op
        return null;
    }
    protected void onQueryCallForwardStatus(int phoneId, int reason, int serviceClass,
            boolean expectMore, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onQueryCallBarringStatus(int phoneId, int cbType, String password,
            int serviceClass, boolean expectMore, IQtiImsExtListener listener) {
        // no-op
    }
    protected void onExitScbm(int phoneId, IQtiImsExtListener listener) {
        // no-op
    }

    protected boolean onIsExitScbmFeatureSupported(int phoneId) {
        // no-op
        return false;
    }

    protected void onSetDataChannelCapabilityListener(int phoneId,
            IQtiImsExtListener listener) {
        // no-op
    }

    protected boolean onIsDataChannelEnabled(int phoneId) {
        // no-op
        return false;
    }

    protected void onSendVosSupportStatus(int phoneId, boolean isVosSupported,
            IQtiImsExtListener listener) {
        // no-op
    }

    protected void onSendVosActionInfo(int phoneId, VosActionInfo vosActionInfo,
            IQtiImsExtListener listener) {
        // no-op
    }

    protected IImsArController onGetArController(int phoneId) {
        // no-op
        return null;
    }
}
