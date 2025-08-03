/*
 * Copyright (c) 2020-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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
 */

/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.telephony.CellInfo;
import com.qti.extphone.BearerAllocationStatus;
import com.qti.extphone.DcParam;
import com.qti.extphone.DualDataRecommendation;
import com.qti.extphone.NetworkSelectionMode;
import com.qti.extphone.NrConfig;
import com.qti.extphone.NrConfigType;
import com.qti.extphone.NrIconType;
import com.qti.extphone.QRadioResponseInfo;
import com.qti.extphone.QosParametersResult;
import com.qti.extphone.QtiCallForwardInfo;
import com.qti.extphone.QtiImeiInfo;
import com.qti.extphone.QtiPersoUnlockStatus;
import com.qti.extphone.SignalStrength;
import com.qti.extphone.QtiSimType;
import com.qti.extphone.SmsResult;
import com.qti.extphone.Status;
import com.qti.extphone.Token;
import com.qti.extphone.UpperLayerIndInfo;
import java.util.List;

interface IExtPhoneCallback {

    void onNrIconType(int slotId, in Token token, in Status status,
            in NrIconType nrIconType);

    /**
    * Response to enableEndc
    * @param - slotId
    * @param - token is the same token which is recived in enableEndc
    * @param - status SUCCESS/FAILURE based on the modem Result code
    */
    void onEnableEndc(int slotId, in Token token, in Status status);

    /**
    * Response to queryEndcStatus
    * @param - slotId
    * @param - token is the same token which is recived in queryEndcStatus
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - enableStatus true if endce is enabled otherwise false
    */
    void onEndcStatus(int slotId, in Token token, in Status status, boolean enableStatus);

    /**
    * Response to setNrConfig
    * @param - slotId
    * @param - token is the same token which is recived in setNrConfig
    * @param - status SUCCESS/FAILURE based on the modem Result code
    */
    void onSetNrConfig(int slotId, in Token token, in Status status);

    /**
    * Response to queryNrConfig
    * @param - slotId
    * @param - token is the same token which is recived in queryNrConfig
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - nrConfig: NSA + SA/NSA/SA
    */
    void onNrConfigStatus(int slotId, in Token token, in Status status, in NrConfig nrConfig);

    /**
    * Response to sendCdmaSms
    * @param - slotId
    * @param - token is the same token which is recived in sendCdmaSms
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param sms Sms result struct as defined by SmsResult
    *
    */
    void sendCdmaSmsResponse(int slotId, in Token token, in Status status, in SmsResult sms);

    /**
    * Response to getQtiRadioCapability
    * @param - slotId
    * @param - token is the same token which is recived in getQtiRadioCapability
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - raf Radio Access Family 32-bit bitmap of
    *          vendor.qti.hardware.radio.qtiradio.V2_6.RadioAccessFamily
    */
    void getQtiRadioCapabilityResponse(int slotId, in Token token, in Status status, int raf);

    /**
    * @deprecated
    */
    void on5gStatus(int slotId, in Token token, in Status status, boolean enableStatus);

    /**
    * @deprecated
    */
    void onAnyNrBearerAllocation(int slotId, in Token token, in Status status,
            in BearerAllocationStatus bearerStatus);

    /**
    *@deprecated
    */
    void onNrDcParam(int slotId, in Token token, in Status status, in DcParam dcParam);

    /**
    * @deprecated
    */
    void onUpperLayerIndInfo(int slotId, in Token token, in Status status,
            in UpperLayerIndInfo upperLayerInfo);

    /**
    * @deprecated
    * use onNrConfigStatus instead to get current nr config.
    */
    void on5gConfigInfo(int slotId, in Token token, in Status status,
            in NrConfigType nrConfigType);

    /**
    * @deprecated
    */
    void onSignalStrength(int slotId, in Token token, in Status status,
            in SignalStrength signalStrength);

    /**
    * Response to setCarrierInfoForImsiEncryptionResponse
    * @param - slotId
    * @param - token is the same token which is recived in enableEndc
    * @param - info QtiRadioResponseInfo
    */
    void setCarrierInfoForImsiEncryptionResponse(int slotId, in Token token,
            in QRadioResponseInfo info);

   /**
     * @param status SUCCESS/FAILURE based on the modem Result code
     * @param infos indicates array of CallForwardInfo, one for
     *        each distinct registered phone number.
     * @return void.
     */
    void queryCallForwardStatusResponse(in Status status, in QtiCallForwardInfo[] infos);

   /**
     * @param - status SUCCESS/FAILURE based on the modem Result code
     * @param response 0 is the TS 27.007 service class bit vector of
     *        services for which the specified barring facility
     *        is active. "0" means "disabled for all"
     */
    void getFacilityLockForAppResponse(in Status status, in int[] response);

    /**
     * Response to setSmartDdsSwitchToggle
     * @param - token is the same token which is received in setSmartDdsSwitchToggle
     * @param - result SUCCESS/FAILURE based on the CnE HAL response
     */
    void setSmartDdsSwitchToggleResponse(in Token token, boolean result);

   /**
    * Indication would be sent whenever Primary IMEI mapping changes
    * @param - imeiInfo, array of imeiInfo object which contains
    *          slotId, IMEI string and type
    *
    * The calling application should not assume received array index as slotId, instead the
    * application has to use the slotId that present in QtiImeiInfo object to know the IMEI
    * corresponds to a slot.
    */
    void onImeiTypeChanged(in QtiImeiInfo[] imeiInfo);

    /**
     * Response to sendUserPreferenceForDataDuringVoiceCall
     * @param - slotId slot ID on whcih this callback is recevied.
     * @param - token is the same token which is recived in
     *          sendUserPreferenceForDataDuringVoiceCall
     * @param - status SUCCESS/FAILURE based on RIL data module response
     */
    void onSendUserPreferenceForDataDuringVoiceCall(int slotId, in Token token,
            in Status status);

    /**
     * Indicates DDS switch capability could be changed to Smart DDS
     * Upon receiving this message, HLOS has to inform modem if user has enabled/disabled
     * UI preference for data during voice call.
     * @param - slotId slot ID on whcih this callback is recevied.
     * @param - token is the same token which is recived in
     *          getDdsSwitchCapability
     * @param - status SUCCESS/FAILURE based on the modem Result code
     * @param - support true/false if smart DDS switch is supported or not
     */
    void onDdsSwitchCapabilityChange(int slotId, in Token token, in Status status,
            boolean support);

    /**
     * Indicates if telephony has to enable/disable its temp DDS switch logic
     * If telephony temp DDS switch is disabled, then telephony will wait for modem recommendations
     * in seperate indication to perform temp DDS switch.
     * @param - slotId
     * @param - telephonyDdsSwitch true/false based on telephony temp DDS switch
     *          logic should be enabled/disabled.
     */
    void onDdsSwitchCriteriaChange(int slotId, boolean telephonyDdsSwitch);

    /**
     * Indicates modem recommendation for temp DDS switch during voice call on non-DDS.
     * @param - slotId slot ID on whcih this callback is recevied.
     * @param - recommendedSlotId slot ID to which DDS has to be switched.
     */
    void onDdsSwitchRecommendation(int slotId, int recommendedSlotId);

    /**
     * Indiactes the delay time to deactivate default data pdn when cellular IWLAN feature is ON.
     * @param - delayTimeMilliSecs delayTimeMilliSecs>0 indicates one or more pdns
     *           are present on cellular IWLAN RAT and wait for delayTimeMilliSecs
     *           to deactivate default data pdn if required.
     *           delayTimeMilliSecs<=0 indicates no pdns are available on cellular IWLAN RAT.
     */
    void onDataDeactivateDelayTime(int slotId, long delayTimeMilliSecs);

    /**
     * Indicates epdg over cellular data (cellular IWLAN) feature is supported or not.
     *
     * @param - support support indicates if the feature is supported or not.
     */
    void onEpdgOverCellularDataSupported(int slotId, boolean support);

    /**
     * Response to IExtPhone.getQosParameters() request
     *
     * @param slotId
     * @param token is the same token which is received in getQosParameters request
     * @param status SUCCESS/FAILURE based on the modem result code
     * @param result QosParametersResult containing the requested QoS parameters
     */
    void getQosParametersResponse(int slotId, in Token token, in Status status,
                                  in QosParametersResult result);

    /**
     * Indication received when QoS parameters have changed for a given connection id
     *
     * @param slotId
     * @param cid Connection id for which the QoS parameters have changed
     * @param result QosParametersResult containing the updated QoS parameters
     */
    void onQosParametersChanged(int slotId, int cid, in QosParametersResult result);

    /**
     * Response to getSecureModeStatus
     * @param - token is the same token which is passed in from getSecureModeStatus
     * @param - status SUCCESS/FAILURE based on RIL data module response
     * @param - enableStatus Secure Mode status - true: enabled, false: disabled
     */
    void getSecureModeStatusResponse(in Token token, in Status status, boolean enableStatus);

    /**
     * Indicates that the device has entered/exited Secure Mode
     *
     * @param - enabled true/false whether Secure Mode is on/off
     */
    void onSecureModeStatusChange(boolean enabled);

    /**
    * Response to startNetworkScan
    * @param - slotId
    * @param - token is the same token which is recived in startNetworkScan
    * @param - errorCode
    */
    void startNetworkScanResponse(int slotId, in Token token, in int errorCode);

    /**
    * Response to stopNetworkScan
    * @param - slotId
    * @param - token is the same token which is recived in startNetworkScan
    * @param - errorCode
    */
    void stopNetworkScanResponse(int slotId, in Token token, in int errorCode);

    /**
    * Response to setNetworkSelectionModeAutomatic
    * @param - slotId
    * @param - token is the same token which is recived in setNetworkSelectionModeAutomatic
    * @param - errorCode
    */
    void setNetworkSelectionModeAutomaticResponse(int slotId, in Token token, in int errorCode);

    /**
    * Response to setNetworkSelectionModeManual
    * @param - slotId
    * @param - token is the same token which is recived in setNetworkSelectionModeManual
    * @param - errorCode
    */
    void setNetworkSelectionModeManualResponse(int slotId, in Token token, in int errorCode);

    /**
    * Response to getNetworkSelectionMode
    * @param - slotId
    * @param - token is the same token which is recived in getNetworkSelectionMode
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - modes Network selection mode
    */
    void getNetworkSelectionModeResponse(int slotId, in Token token, in Status status,
            in NetworkSelectionMode modes);

    /**
    * Indicates network scan results
    * @param - slotId
    * @param - token
    * @param - status SUCCESS/FAILURE based on the modem Result code
    * @param - error error code
    * @param - cellInfos is Cell information
    */
    void networkScanResult(int slotId, in Token token, in int status, in int error,
            in List<CellInfo> cellInfos);

     /**
     * Response to setMsimPreference
     *
     * @param - token is the same token which is received in setMsimPreference
     * @param - status SUCCESS/FAILURE based on RIL NAS module response
     */
    void setMsimPreferenceResponse(in Token token, in Status status);


    /**
     * Response to setSimType
     *
     * @param - token is the same token which is received in setSimType
     * @param - status SUCCESS/FAILURE based on below layers response
     */
    void setSimTypeResponse(in Token token, in Status status);

    /**
     * Indication sent when Sim Type on a slot changes.
     *
     * @param - simtype array contains the current Sim Type on each Slot
     */
    void onSimTypeChanged(in QtiSimType[] simtype);

    /**
     * Response to getDualDataCapability and also called when dual data capability changes
     *         in Modem.
     *
     * @param token to match request/response. Response must include same token as in request,
     *         otherwise token is set to -1.
     * @param status SUCCESS/FAILURE based on the modem result code
     * @param support True if modem supports dual data feature.
     */
    void onDualDataCapabilityChanged(in Token token, in Status status, in boolean support);

    /**
     * Response to setDualDataUserPreference
     * @param - token is the same token which is recived in
     *          sendUserPreferenceForDataDuringVoiceCall
     * @param - status SUCCESS/FAILURE based on RIL data module response
     */
    void setDualDataUserPreferenceResponse(in Token token, in Status status);

    /**
     * Received in the following conditions to allow/disallow internet pdn on nDDS
     * after dual data user preference is sent as true
     * to modem through IQtiRadioConfig#setDualDataUserPreference().
     * Condition to send onDualDataRecommendation(NON_DDS and DATA_ALLOW):
     *    1)UE is in DSDA sub-mode and in full concurrent condition
     * Conditions to send onDualDataRecommendation(NON_DDS and DATA_DISALLOW):
     *    1)UE is in DSDS sub-mode
     *    2)UE is in TX sharing condition
     *    3)IRAT is initiated on nDDS when UE is in L+NR RAT combo
     *    4)nDDS is OOS
     *
     * @param rec <DualDataRecommendation> to allow/disallow internet pdn on nDDS.
     */
    void onDualDataRecommendation(in DualDataRecommendation rec);

    /**
     * Indication received when persoSubState is unlocked either temporarily or permanently
     *
     * @param - slotId on which the persoSubState changed
     * @param - persoUnlockStatus which can be generally temporary or permanent.
     */
    void onSimPersoUnlockStatusChange(int slotId, in QtiPersoUnlockStatus persoUnlockStatus);
}
