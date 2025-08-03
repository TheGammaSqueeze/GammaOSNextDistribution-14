/* Copyright (c) 2015-2016, 2019-2021 The Linux Foundation. All rights reserved.
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

/**
 * Used by client application to get the result from lower layer by
 * communicating with vendor.
 * {@hide}
 */
oneway interface IQtiImsExtListener {
    /**
     * Notifies client the value of the set operation result.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param status To return status of request.
     * @return void.
     */
    void onSetCallForwardUncondTimer(int phoneId, int status);

     /**
     * Notifies client the value of the get operation result.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param startHour indicates starting hour
     * @param startMinute indicates starting minute
     * @param endHour indicates ending hour
     * @param endMinute indicates ending minute
     * @param reason is one of the valid call forwarding
     *        CF_REASONS, as defined in
     *        <code>com.android.internal.telephony.CommandsInterface.</code>
     * @param status indicates status of CF service.
     * @param number is the target phone number to forward calls to
     * @param serviceClass indicates serviceClass type that is supported.
     * @return void.
     */
    void onGetCallForwardUncondTimer(int phoneId, int startHour, int endHour, int startMinute,
            int endMinute, int reason, int status, String number, int serviceClass);

   /**
     * Notifies client with any UT failure result.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param errCode contains error code
     * @param errString contains error string if any.
     * @return void.
     */
    void onUTReqFailed(int phoneId, int errCode, String errString);

    /**
     * Notifies client the result of cancel modify call
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void receiveCancelModifyCallResponse(int phoneId, int result);

    /**
     * Notifies Vops value to the clients
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param vopsStatus
     *        if true  : Voice is supported on LTE
     *        if false : Voice is not supported on LTE
     * @return void.
     */
    void notifyVopsStatus(int phoneId, boolean vopsStatus);

    /**
     * Notifies Ssac value to the clients
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param ssacStatusResponse
     *        if true  : Access barring factor for voice calls is 0
     *        if false : Access barring factor for voice calls is non-zero
     *                   Range: 0 to 100. Value 100 is used when
     *                   the UE goes to the LTE Connected state
     * @return void.
     */
    void notifySsacStatus(int phoneId, boolean ssacStatusResponse);

    /**
     * Notifies client when Participant status information is received
     *
     * @param  phoneId indicates the phone instance which triggered the request
     * @param  operation - operation add or remove participant
     * @param  sipStatus - sip code indicating status of operation
     *                     180 - Ringing, 603 - remote user rejected the call
     *                     200 - Remote user accepted the call, if operation is add
     *                           Participant removed successfully if operation is remove
     * @param  participantUri - Participant URI
     * @param  isEct -  Explicit call transfer true or false
     *
     * @return void.
     */
    void notifyParticipantStatusInfo(int phoneId, int operation, int sipStatus,
            String participantUri, boolean isEct);

    /**
     * Notifies client the result of updateVoltePreference request
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void onVoltePreferenceUpdated(int phoneId, int result);

    /**
     * Notifies client the result of queryVoltePreference request
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @param <mode> is valid only when <result> is QTI_IMS_REQUEST_SUCCESS and
     *        is one of the values QTI_IMS_VOLTE_PREF_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void onVoltePreferenceQueried(int phoneId, int result, int mode);

    /**
     * Notifies client the value of the set handover config result.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <status> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @return void.
     */
    void onSetHandoverConfig(int phoneId,int status);

   /**
     * Notifies client the value of the get operation result on get handover config item.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <status> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @param hoConfig is valid when status is QTI_IMS_REQUEST_SUCCESS and it can have
     *        one of the values QTI_IMS_HO_* excluding QTI_IMS_HO_INVALID, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils</code>
     * @return void
     */
   void onGetHandoverConfig(int phoneId, int status, int hoConfig);

   /**
     * Notifies client about the USSD info for IMS.
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param type - notify, request or error
     * @param errorCode - sip error code
     * @param errorMessage - error description
     *
     */
   void onUssdFailed(int phoneId, int type, int errorCode, String errorMessage);

   /**
     * @param phoneId indicates the phone instance which triggered the request
     * @param cfInfoList array of ImsCallForwardInfo
     * @return void.
     */
   void queryCallForwardStatusResponse(int phoneId, in ImsCallForwardInfo[] cfInfoList);

   /**
     * @param response indicates the callbarring status enabled or disabled.
     */
   void queryCallBarringResponse(in int[] response);

   /** Notifies client on exit of SCBM
     * @return void.
     */
   void onScbmExited(boolean status);

   /** Notifies client on data channel capability
     * @param phoneId indicates the phone instance which triggered the request
     * @param dcCapability
     *        if true  : has data channel capability
     *        if false : no data channel capability
     * @return void.
     */
   void notifyDataChannelCapability(int phoneId, boolean dcCapability);

    /**
     * Notifies client the result of send vos support status
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void handleSendVosSupportStatusResponse(int phoneId, int result);

    /**
     * Notifies client the result of send vos action info
     *
     * @param phoneId indicates the phone instance which triggered the request
     * @param <result> is one of the values QTI_IMS_REQUEST_*, as defined in
     *        <code>org.codeaurora.ims.utils.QtiImsExtUtils.</code>
     * @return void.
     */
    void handleSendVosActionInfoResponse(int phoneId, int result);
}
