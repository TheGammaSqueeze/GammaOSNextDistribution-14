/* Copyright (c) 2015-2017, 2019-2021 The Linux Foundation. All rights reserved.
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

package org.codeaurora.ims.internal;

import android.os.Bundle;
import org.codeaurora.ims.internal.ICrsCrbtController;
import org.codeaurora.ims.internal.IQtiImsExtListener;
import org.codeaurora.ims.internal.IImsArController;
import org.codeaurora.ims.internal.IImsMultiIdentityInterface;
import org.codeaurora.ims.internal.IImsScreenShareController;
import org.codeaurora.ims.VosActionInfo;

/**
 * Interface through which APP and vendor communicates.
 * {@hide}
 */
interface IQtiImsExt {
   /**
     * setCallForwardingUncondTimerOptions
     * sets a call forwarding unconditional Timer option.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param startHour indicates starting hour
     * @param startMinute indicates starting minute
     * @param endHour indicates ending hour
     * @param endMinute indicates ending minute
     * @param action is one of the valid call forwarding
     *        CF_ACTIONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass is service class, that is used to set CFT
     *        SERVICE_CLASS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param dialingNumber is the target phone number to forward calls to
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute, int endHour,
            int endMinute, int action, int reason, int serviceClass, String dialingNumber,
            IQtiImsExtListener listener);

   /**
     * getCallForwardingUncondTimerOptions
     * gets a call forwarding option.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass is service class, that is used to get CFT
     *        SERVICE_CLASS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener);

   /**
     * sendCancelModifyCall
     * cancel a call modify request
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener);

    /**
     * Query Vops information
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void queryVopsStatus(int phoneId, IQtiImsExtListener listener);

   /**
     * Query Ssac information
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void querySsacStatus(int phoneId, IQtiImsExtListener listener);

   /**
     * resumePendingCall
     * This API shall continue to place DIAL request with videoState as either
     * Video or Voice based on user confirmation on low battery MO Video call
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param videoState indicates either Video or Voice type
     * @return void
     */
    oneway void resumePendingCall(int phoneId, int videoState);

   /**
     * Register for Pariticipant status information
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener, to get notified for participant status information.
     * @return void
     *
     */
    oneway void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener);

   /**
     * updateVoltePreference
     * Updates the user's VoLTE preference to lower layers
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param preference is one of the values QTI_IMS_VOLTE_PREF_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void updateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener);

   /**
     * queryVoltePreference
     * Retrieves the user's VoLTE preference from lower layers
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void queryVoltePreference(int phoneId, IQtiImsExtListener listener);

   /**
     * getHandoverConfig
     * Get IMS Handover Enabled status
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener, provided if caller needs to be notified for get result.
     * @return void
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    oneway void getHandoverConfig(int phoneId, IQtiImsExtListener listener);

   /**
     * setHandoverConfig
     * Set IMS Handover Enabled status
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param hoConfig is one of the values QTI_IMS_HO_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    oneway void setHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener);

   /**
     * getRcsAppConfig
     * Get default sms app.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @return int is one of the values QTI_IMS_SMS_APP_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    int getRcsAppConfig(int phoneId);

   /**
     * setRcsAppConfig
     * Set default sms app.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param defaultSmsApp is one of the values QTI_IMS_SMS_APP_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @return int is one of the values OperationStatusConstants *, as defined in
     *        <code>com.android.ims.ImsConfig</code>
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    int setRcsAppConfig(int phoneId, int defaultSmsApp);

   /**
     * getVvmAppConfig
     * Get default video voice mail app.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @return int is one of the values QTI_IMS_VVM_APP_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    int getVvmAppConfig(int phoneId);

   /**
     * setVvmAppConfig
     * Set default video voice mail app.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param defaultVvmApp is one of the values QTI_IMS_VVM_APP_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @return int is one of the values OperationStatusConstants *, as defined in
     *        <code>com.android.ims.ImsConfig</code>
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    int setVvmAppConfig(int phoneId, int defaultVvmApp);

   /**
    * Returns the IImsMultiIdentityInterface IBinder
    */
    IImsMultiIdentityInterface getMultiIdentityInterface(int phoneId);

   /**
    * Returns the IImsScreenShareInterface IBinder
    */
    IImsScreenShareController getScreenShareController(int phoneId);

   /**
     * getImsFeatureState
     * Get current IMS feature state.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @return int is one of the values of #ImsState defined in android.telephony.ims.feature.
     *
     * @throws RemoteException if calling the IMS service results in an error.
     */
    int getImsFeatureState(int phoneId);

   /**
    * setAnswerExtras
    *
    *
    * @param phoneId indicates the phone instance which triggered the request
    * @param extras Bundle containing additional information for answer request
    *
    * @throws RemoteException if calling the IMS service results in an error.
    */
    oneway void setAnswerExtras(int phoneId, in Bundle extras);

   /**
     * setUssdInfoListener
     * Set USSD info listener.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void setUssdInfoListener(int phoneId, IQtiImsExtListener listener);

    /**
     * isCallComposerEnabled
     * Retrieves whether IMS service CALLCOMPOSER is enabled/disabled
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @return boolean
     *
     *@throws RemoteException if calling the IMS service results in an error.
     */
    boolean isCallComposerEnabled(int phoneId);

   /**
    * Returns the ICrsCrbtnterface IBinder
    */
    ICrsCrbtController getCrsCrbtController(int phoneId);

   /**
     * queryCallForwardStatus
     * gets a call forwarding option.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param serviceClass is service class, that is used to get CFT
     *        SERVICE_CLASS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @param expectMore flag to indicate call forward callforward requests are in line.
     * @return void
     */
    oneway void queryCallForwardStatus(int phoneId, int reason, int serviceClass,
            boolean expectMore, IQtiImsExtListener listener);


    /**
     * Retrieves the configuration of the call barring for specified service class.
     *
     * @param cbType type of call barring to be queried; ImsUtInterface#CB_XXX
     * @param result message to pass the result of this operation
     *      The return value of ((AsyncResult)result.obj) is an array of {@link ImsSsInfo}.
     * @param serviceClass service class for e.g. voice/video
     * @param expectMore flag to indicate call forward callbarring requests are in line.
     */
    oneway void queryCallBarring(int phoneId, int cbType, String password, int serviceClass,
            boolean expectMore, IQtiImsExtListener listener);

    /**
      * exitScbm
      * Exit SCBM mode
      *
      * @param phoneId indicates the phone instance which triggered the request
      * @param listener an IQtiImsExtListener instance to indicate the response
      * @return boolean request got success or not
      *
      * @throws RemoteException if calling the IMS service results in an error.
      */
     oneway void exitScbm(int phoneId, IQtiImsExtListener listener);

    /**
      * Checks whether the SCBM exit feature is supported.
      *
      * @param phoneId indicates the phone instance which triggered the request
      * @return boolean request got success or not
      *
      * @throws RemoteException if calling the IMS service results in an error.
      */
     boolean isExitScbmFeatureSupported(int phoneId);

   /**
     * setDataChannelCapabilityListener
     * Set data channel capability listener.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void setDataChannelCapabilityListener(int phoneId, IQtiImsExtListener listener);

    /**
     * isDataChannelEnabled
     * Retrieves whether IMS service data channel is enabled/disabled
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @return boolean
     *
     *@throws RemoteException if calling the IMS service results in an error.
     */
    boolean isDataChannelEnabled(int phoneId);

    /**
     * sendVosSupportStatus
     * Send video online service status supported
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param isVosSupported sends whether device supports video online service to lower layer
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void sendVosSupportStatus(int phoneId, boolean isVosSupported,
        IQtiImsExtListener listener);

    /**
     * sendVosActionInfo
     * Send user's action info like touch or move
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param vosActionInfo sends user's touch or move info to lower layer
     * @param listener an IQtiImsExtListener instance to indicate the response
     * @return void
     */
    oneway void sendVosActionInfo(int phoneId, in VosActionInfo vosActionInfo,
        IQtiImsExtListener listener);

   /**
    * Returns the IImsArInterface IBinder
    */
    IImsArController getArController(int phoneId);

}
