/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __WIFI_HAL_NAN_COMMAND_H__
#define __WIFI_HAL_NAN_COMMAND_H__

#include "common.h"
#include "cpp_bindings.h"
#include "wifi_hal.h"
#include "nan_cert.h"

/*
 * NAN Salt is a concatenation of salt_version, CSID, Service ID, PeerMac
 * resulting in a total length of 14 bytes
 */
#define NAN_SECURITY_SALT_SIZE 14
/* In Service ID calculation SHA-256 hash size is of max. 64 bytes */
#define NAN_SVC_HASH_SIZE 64
/* Service ID is the first 48 bits of the SHA-256 hash of the Service Name */
#define NAN_SVC_ID_SIZE 6
/* Default Service name length is 21 bytes */
#define NAN_DEF_SVC_NAME_LEN 21
/* As per NAN spec, 4096 iterations to be used for PMK calculation */
#define NAN_PMK_ITERATIONS 4096
/* Keep NCS-SK-128 Cipher Suite as default i.e. HMAC-SHA-256 algorithm */
#define NAN_DEFAULT_NCS_SK NAN_CIPHER_SUITE_SHARED_KEY_128_MASK
/* Currently by default max 6 Publishes/Subscribes are allowed */
#define NAN_DEF_PUB_SUB 6
/*
 * First bit of discovery_indication_cfg in NanEnableRequest indicates
 * disableDiscoveryAddressChangeIndication
 */
#define NAN_DISC_ADDR_IND_DISABLED 0x01

typedef struct PACKED
{
    u32 instance_id;
    u16 subscriber_publisher_id;
    u8 service_id[NAN_SVC_ID_SIZE];
} NanStoreSvcParams;

typedef enum
{
    NAN_ROLE_NONE,
    NAN_ROLE_PUBLISHER,
    NAN_ROLE_SUBSCRIBER
} NanRole;

class NanCommand : public WifiVendorCommand
{
private:
    NanCallbackHandler mHandler;
    char *mNanVendorEvent;
    u32 mNanDataLen;
    NanStaParameter *mStaParam;
    u8 mNmiMac[NAN_MAC_ADDR_LEN];
    u32 mNanMaxPublishes;
    u32 mNanMaxSubscribes;
    NanStoreSvcParams *mStorePubParams;
    NanStoreSvcParams *mStoreSubParams;
    bool mNanDiscAddrIndDisabled;

    //Function to check the initial few bytes of data to
    //determine whether NanResponse or NanEvent
    int isNanResponse();
    //Function which unparses the data and calls the NotifyResponse
    int handleNanResponse();
    //Function which will parse the mVendorData and gets
    // the rsp_data appropriately.
    int getNanResponse(transaction_id *id, NanResponseMsg *pRsp);
    //Function which will return the Nan Indication type based on
    //the initial few bytes of mVendorData
    NanIndicationType getIndicationType();
    //Function which calls the necessaryIndication callback
    //based on the indication type
    int handleNanIndication();
    //Various Functions to get the appropriate indications
    int getNanPublishReplied(NanPublishRepliedInd *event);
    int getNanPublishTerminated(NanPublishTerminatedInd *event);
    int getNanMatch(NanMatchInd *event);
    int getNanMatchExpired(NanMatchExpiredInd *event);
    int getNanSubscribeTerminated(NanSubscribeTerminatedInd *event);
    int getNanFollowup(NanFollowupInd *event);
    int getNanDiscEngEvent(NanDiscEngEventInd *event);
    int getNanDisabled(NanDisabledInd *event);
    int getNanTca(NanTCAInd *event);
    int getNanBeaconSdfPayload(NanBeaconSdfPayloadInd *event);
    //Internal cleanup function
    void cleanup();

    static NanCommand *mNanCommandInstance;

    // Other private helper functions
    int calcNanTransmitPostDiscoverySize(
        const NanTransmitPostDiscovery *pPostDiscovery);
    void fillNanSocialChannelParamVal(
        const NanSocialChannelScanParams *pScanParams,
        u32* pChannelParamArr);
    u32 getNanTransmitPostConnectivityCapabilityVal(
        const NanTransmitPostConnectivityCapability *pCapab);
    void fillNanTransmitPostDiscoveryVal(
        const NanTransmitPostDiscovery *pTxDisc,
        u8 *pOutValue);
    int calcNanFurtherAvailabilityMapSize(
        const NanFurtherAvailabilityMap *pFam);
    void fillNanFurtherAvailabilityMapVal(
        const NanFurtherAvailabilityMap *pFam,
        u8 *pOutValue);

    void getNanReceivePostConnectivityCapabilityVal(
        const u8* pInValue,
        NanReceivePostConnectivityCapability *pRxCapab);
    void getNanReceiveSdeaCtrlParams(const u8* pInValue,
        NanSdeaCtrlParams *pPeerSdeaParams);
    int getNanReceivePostDiscoveryVal(const u8 *pInValue,
                                      u32 length,
                                      NanReceivePostDiscovery *pRxDisc);
    int getNanFurtherAvailabilityMap(const u8 *pInValue,
                                     u32 length,
                                     u8* num_chans,
                                     NanFurtherAvailabilityChannel *pFac);
    void handleNanStatsResponse(NanStatsType stats_type,
                                char* rspBuf,
                                NanStatsResponse *pRsp,
                                u32 message_len);

    //Function which unparses the data and calls the NotifyResponse
    int handleNdpResponse(NanResponseType ndpCmdtyp, struct nlattr **tb_vendor);
    int handleNdpIndication(u32 ndpCmdType, struct nlattr **tb_vendor);
    int getNdpRequest(struct nlattr **tb_vendor, NanDataPathRequestInd *event);
    int getNdpConfirm(struct nlattr **tb_vendor, NanDataPathConfirmInd *event);
    int getNdpEnd(struct nlattr **tb_vendor, NanDataPathEndInd *event);
    int getNanTransmitFollowupInd(NanTransmitFollowupInd *event);
    int getNanRangeRequestReceivedInd(NanRangeRequestInd *event);
    int getNanRangeReportInd(NanRangeReportInd *event);
    int getNdpScheduleUpdate(struct nlattr **tb_vendor, NanDataPathScheduleUpdateInd *event);
public:
    NanCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);
    static NanCommand* instance(wifi_handle handle);
    virtual ~NanCommand();

    // This function implements creation of NAN specific Request
    // based on  the request type
    virtual wifi_error create();
    virtual wifi_error requestEvent();
    virtual int handleResponse(WifiEvent &reply);
    virtual int handleEvent(WifiEvent &event);
    wifi_error setCallbackHandler(NanCallbackHandler nHandler);


    //Functions to fill the vendor data appropriately
    wifi_error putNanEnable(transaction_id id, const NanEnableRequest *pReq);
    wifi_error putNanDisable(transaction_id id);
    wifi_error putNanPublish(transaction_id id, const NanPublishRequest *pReq);
    wifi_error putNanPublishCancel(transaction_id id, const NanPublishCancelRequest *pReq);
    wifi_error putNanSubscribe(transaction_id id, const NanSubscribeRequest *pReq);
    wifi_error putNanSubscribeCancel(transaction_id id, const NanSubscribeCancelRequest *pReq);
    wifi_error putNanTransmitFollowup(transaction_id id, const NanTransmitFollowupRequest *pReq);
    wifi_error putNanStats(transaction_id id, const NanStatsRequest *pReq);
    wifi_error putNanConfig(transaction_id id, const NanConfigRequest *pReq);
    wifi_error putNanTCA(transaction_id id, const NanTCARequest *pReq);
    wifi_error putNanBeaconSdfPayload(transaction_id id, const NanBeaconSdfPayloadRequest *pReq);
    wifi_error getNanStaParameter(wifi_interface_handle iface, NanStaParameter *pRsp);
    wifi_error putNanCapabilities(transaction_id id);
    wifi_error putNanDebugCommand(NanDebugParams debug, int debug_msg_length);

    /* Functions for NAN error translation
       For NanResponse, NanPublishTerminatedInd, NanSubscribeTerminatedInd,
       NanDisabledInd, NanTransmitFollowupInd:
       function to translate firmware specific errors
       to generic freamework error along with the error string
    */
    void NanErrorTranslation(NanInternalStatusType firmwareErrorRecvd,
                             u32 valueRcvd,
                             void *pRsp,
                             bool is_ndp_rsp);

    /* Functions for NAN passphrase to PMK calculation */
    void saveNmi(u8 *mac);
    u8 *getNmi();
    void saveServiceId(u8 *service_id, u16 sub_pub_handle,
                        u32 instance_id, NanRole Pool);
    u8 *getServiceId(u32 instance_id, NanRole Pool);
    void deleteServiceId(u16 sub_handle, u32 instance_id, NanRole pool);
    void allocSvcParams();
    void reallocSvcParams(NanRole pool);
    void deallocSvcParams();
};
#endif /* __WIFI_HAL_NAN_COMMAND_H__ */

