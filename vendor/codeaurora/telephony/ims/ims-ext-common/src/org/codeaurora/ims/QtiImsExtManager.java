/* Copyright (c) 2016-2021, The Linux Foundation. All rights reserved.
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
import android.telephony.TelephonyManager;
import android.util.Log;

import android.telephony.ims.stub.ImsConfigImplBase;

import java.util.ArrayList;

import org.codeaurora.ims.internal.ICrsCrbtController;
import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.internal.IQtiImsExtListener;
import org.codeaurora.ims.internal.IImsArController;
import org.codeaurora.ims.internal.IImsMultiIdentityInterface;
import org.codeaurora.ims.internal.IImsScreenShareController;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * Provides API's for IQtiImsExt Binder such as sending call transfer etc
 * You can acquire an instance of it by calling {@link QtiImsExtConnector().connect()}
 *
 * Note: The implementation of QtiImsExtManager is not synchronized hence this is
 * not a thread safe class, Assuming all the users will call the API's from the same thread
 */
public class QtiImsExtManager {

    private static final String LOG_TAG = "QtiImsExtManager";

    /**
     * All the QtiImsExt actions are performed using this interface,
     * this interface/binder provides API's such as sending
     * call transfer etc
     */
    private IQtiImsExt mQtiImsExt;
    private Context mContext;
    private ArrayList<ICleanupListener> mCleanupListeners = new ArrayList<>();

    //TODO remove later. This is only for dependent modules compilation
    public QtiImsExtManager(Context context) {
        this(context, null);
    }

    /*package-private*/
    QtiImsExtManager(Context context, IQtiImsExt qtiImsExt) {
        mContext = context;
        mQtiImsExt = qtiImsExt;
    }

    public void dispose() {
        mCleanupListeners.forEach(c -> c.dispose());
        mQtiImsExt = null;
        mContext = null;
    }

    //Used to cleanup all Managers
    public interface ICleanupListener {
        void dispose();
    }

    public void addCleanupListener(ICleanupListener listener) {
        if (listener != null) {
            mCleanupListeners.add(listener);
        }
    }

    public void removeCleanupListener(ICleanupListener listener) {
        if (listener != null) {
            mCleanupListeners.remove(listener);
        }
    }

    public void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute, int endHour,
            int endMinute, int action, int condition, int serviceClass, String number,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.setCallForwardUncondTimer(phoneId, startHour, startMinute, endHour,
                endMinute, action, condition, serviceClass, number, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setCallForwardUncondTimer : " + e);
        }
    }

    public void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.getCallForwardUncondTimer(phoneId, reason, serviceClass, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService getCallForwardUncondTimer : " + e);
        }
    }

    public void resumePendingCall(int phoneId, int videoState) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.resumePendingCall(phoneId, videoState);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService resumePendingCall : " + e);
        }
    }

    public void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.sendCancelModifyCall(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCancelModifyCall : " + e);
        }
    }

    public void queryVopsStatus(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.queryVopsStatus(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVopsStatus : " + e);
        }
    }

    public void querySsacStatus(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.querySsacStatus(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService querySsacStatus : " + e);
        }
    }

    public void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.registerForParticipantStatusInfo(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService registerForParticipantStatusInfo : " + e);
        }
    }

    public void updateVoltePreference(int phoneId, int preference,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.updateVoltePreference(phoneId, preference, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService updateVoltePreference : " + e);
        }
    }

    public void queryVoltePreference(int phoneId,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.queryVoltePreference(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVoltePreference : " + e);
        }
    }

    public void getHandoverConfig(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.getHandoverConfig(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService getHandoverConfig : " + e);
        }
    }

    public void setHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener)
           throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.setHandoverConfig(phoneId, hoConfig, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setHandoverConfig : " + e);
        }
    }

    /**
     * Check if binder is available,
     * if binder still doesn't exists throw {@link QtiImsException}
     */
    private void checkBinder() throws QtiImsException {
        if (mQtiImsExt == null) {
            throw new QtiImsException("QtiImsExt Service is not running");
        }
    }

    private void checkPhoneId(int phoneId) throws QtiImsException {
        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (phoneId < 0 || phoneId >= telephonyManager.getPhoneCount()) {
            Log.e(LOG_TAG, "phoneId " + phoneId + " is not valid");
            throw new QtiImsException("invalid phoneId");
        }
    }

    private void checkFeatureStatus(int phoneId) throws QtiImsException {
        try {
            if (mQtiImsExt.getImsFeatureState(phoneId) != ImsFeature.STATE_READY) {
                Log.e(LOG_TAG, "Feature status for phoneId " + phoneId + " is not ready");
                throw new QtiImsException("Feature state is NOT_READY");
            }
        } catch(RemoteException e) {
            throw new QtiImsException("Failed to get ImsFeature state");
        }
    }

    public int setRcsAppConfig(int phoneId, int defaultSmsApp) throws QtiImsException {
        validateInvariants(phoneId);
        int ret = ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        try {
            ret = mQtiImsExt.setRcsAppConfig(phoneId, defaultSmsApp);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setRcsAppConfig : " + e);
        }
        return ret;
    }

    public int getRcsAppConfig(int phoneId) throws QtiImsException {
        validateInvariants(phoneId);
        int ret = QtiImsExtUtils.QTI_IMS_SMS_APP_INVALID;

        try {
            ret = mQtiImsExt.getRcsAppConfig(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService getRcsAppConfig : " + e);
        }

        return ret;
    }

    public int setVvmAppConfig(int phoneId, int defaultVvmApp) throws QtiImsException {
        validateInvariants(phoneId);
        int ret = ImsConfigImplBase.CONFIG_RESULT_UNKNOWN;
        try {
            ret = mQtiImsExt.setVvmAppConfig(phoneId, defaultVvmApp);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setVvmAppConfig : " + e);
        }
        return ret;
    }

    public int getVvmAppConfig(int phoneId) throws QtiImsException {
        validateInvariants(phoneId);
        int ret = QtiImsExtUtils.QTI_IMS_VVM_APP_INVALID;

        try {
            ret = mQtiImsExt.getVvmAppConfig(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService getVvmAppConfig : " + e);
        }

        return ret;
    }

    public void setAnswerExtras(int phoneId, Bundle extras) throws QtiImsException {
        validateInvariants(phoneId);

        try {
            mQtiImsExt.setAnswerExtras(phoneId, extras);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setAnswerExtras : " + e);
        }
    }

    public ImsMultiIdentityManager createImsMultiIdentityManager(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        return new ImsMultiIdentityManager(phoneId, this);
    }

    /*package private*/
    IImsMultiIdentityInterface getMultiIdentityInterface(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.getMultiIdentityInterface(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Failed to retrieve MultiIdentityInterface : " + e);
        }
    }

    public ImsScreenShareManager createImsScreenShareManager(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        return new ImsScreenShareManager(phoneId, this);
    }

    /*package private*/
    IImsScreenShareController getScreenShareController(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.getScreenShareController(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Failed to retrieve ScreenShareInterface : " + e);
        }
    }

    /**
     * Used by client to register call back listener with vendor for
     * UNSOL indication when USSD put on IMS pipe at network fails.
     */
    public void setUssdInfoListener(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.setUssdInfoListener(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setUssdInfoListener : " + e);
        }
    }

    /**
     * Used by clients to check if IMS service CALLCOMPOSER is enabled/disabled
     * @param phoneId indicates the phone instance which triggered the request
     * @return boolean
     */
    public boolean isCallComposerEnabled(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.isCallComposerEnabled(phoneId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService isCallComposerEnabled: " + e);
        }
    }

    /*package private*/
    void validateInvariants(int phoneId)  throws QtiImsException {
        checkBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
    }

    public CrsCrbtManager createCrsCrbtManager(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        return new CrsCrbtManager(phoneId, this);
    }

    /*package private*/
    ICrsCrbtController getCrsCrbtController(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.getCrsCrbtController(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Failed to retrieve CrsCrbtInterface : " + e);
        }
    }

    public void queryCallForwardStatus(int phoneId, int reason, int serviceClass,
            boolean expectMore, IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.queryCallForwardStatus(phoneId, reason, serviceClass, expectMore, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService queryCallForwardStatus : " + e);
        }
    }

    public void queryCallBarring(int phoneId, int cbType, String password, int serviceClass,
            boolean expectMore, IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.queryCallBarring(phoneId, cbType, password, serviceClass, expectMore,
                    listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService queryCallBarring : " + e);
        }
    }

    public void exitScbm(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.exitScbm(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService exitScbm: " + e);
        }
    }

    public boolean isExitScbmFeatureSupported(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.isExitScbmFeatureSupported(phoneId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService isExitScbmFeatureSupported: " + e);
        }
    }

    /**
     * Used by client to register call back listener with vendor for
     * UNSOL indication when data channel capability updated.
     */
    public void setDataChannelCapabilityListener(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.setDataChannelCapabilityListener(phoneId, listener);
        } catch(RemoteException e) {
            throw new QtiImsException("Remote ImsService setDataChannelCapabilityListener : "
                    + e);
        }
    }
    /**
     * Used by clients to check if IMS service data channel is enabled/disabled
     * @param phoneId indicates the phone instance which triggered the request
     * @return boolean
     */
    public boolean isDataChannelEnabled(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.isDataChannelEnabled(phoneId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService isDataChannelEnabled: " + e);
        }
    }

    public void sendVosSupportStatus(int phoneId, boolean isVosSupported,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.sendVosSupportStatus(phoneId, isVosSupported, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendVosSupportStatus: " + e);
        }
    }

    public void sendVosActionInfo(int phoneId, VosActionInfo vosActionInfo,
            IQtiImsExtListener listener) throws QtiImsException {
        validateInvariants(phoneId);
        try {
            mQtiImsExt.sendVosActionInfo(phoneId, vosActionInfo, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendVosActionInfo: " + e);
        }
    }

    public ImsArManager createImsArManager(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        return new ImsArManager(phoneId, this);
    }

    /*package private*/
    IImsArController getArController(int phoneId)
            throws QtiImsException {
        validateInvariants(phoneId);
        try {
            return mQtiImsExt.getArController(phoneId);
        } catch(RemoteException e) {
            throw new QtiImsException("Failed to retrieve ARInterface : " + e);
        }
    }

}
