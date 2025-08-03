/*
 * Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
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

 * Changes from Qualcomm Innovation Center are provided under the following license:

 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>

#include <hardware_legacy/wifi_hal.h>
#include "common.h"
#include "cpp_bindings.h"
#include "wifihal_vendorcommand.h"

#define MAX_INFO 1
//Singleton Static Instance
NUDStatsCommand* NUDStatsCommand::mNUDStatsCommandInstance  = NULL;

// This function implements creation of Vendor command
// For NUDStats just call base Vendor command create
wifi_error NUDStatsCommand::create() {
    wifi_error ret = mMsg.create(NL80211_CMD_VENDOR, 0, 0);
    if (ret != WIFI_SUCCESS) {
        return ret;
    }
    // insert the oui in the msg
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_ID, mVendor_id);
    if (ret != WIFI_SUCCESS)
        goto out;

    // insert the subcmd in the msg
    ret = mMsg.put_u32(NL80211_ATTR_VENDOR_SUBCMD, mSubcmd);
    if (ret != WIFI_SUCCESS)
        goto out;

out:
    return ret;
}

NUDStatsCommand::NUDStatsCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd)
        : WifiVendorCommand(handle, id, vendor_id, subcmd)
{
    memset(&mStats, 0,sizeof(nud_stats));
    mpktInfo = NULL;
    mnumStats = 0;
}

NUDStatsCommand::~NUDStatsCommand()
{
    mNUDStatsCommandInstance = NULL;
}

NUDStatsCommand* NUDStatsCommand::instance(wifi_handle handle)
{
    if (handle == NULL) {
        ALOGE("Interface Handle is invalid");
        return NULL;
    }
    if (mNUDStatsCommandInstance == NULL) {
        mNUDStatsCommandInstance = new NUDStatsCommand(handle, 0,
                OUI_QCA,
                QCA_NL80211_VENDOR_SUBCMD_NUD_STATS_SET);
        return mNUDStatsCommandInstance;
    }
    else
    {
        if (handle != getWifiHandle(mNUDStatsCommandInstance->mInfo))
        {
            /* upper layer must have cleaned up the handle and reinitialized,
               so we need to update the same */
            ALOGE("Handle different, update the handle");
            mNUDStatsCommandInstance->mInfo = (hal_info *)handle;
        }
    }
    return mNUDStatsCommandInstance;
}

void NUDStatsCommand::setSubCmd(u32 subcmd)
{
    mSubcmd = subcmd;
}

void NUDStatsCommand::setHandler(pkt_stats_result_handler handler)
{
    mHandler = handler;
}

wifi_error NUDStatsCommand::requestResponse()
{
    return WifiCommand::requestResponse(mMsg);
}

wifi_error NUDStatsCommand::notifyResponse()
{
    wifi_error ret = WIFI_SUCCESS;

    if (mHandler.on_pkt_stats_results) {
        mHandler.on_pkt_stats_results(&mStats, mnumStats,
                                      mpktInfo);
    } else {
        ret = WIFI_ERROR_INVALID_ARGS;
    }
    return ret;
}

int NUDStatsCommand::handleResponse(WifiEvent &reply)
{
    wifi_error status = WIFI_ERROR_NONE;
    WifiVendorCommand::handleResponse(reply);

    // Parse the vendordata and get the attribute

    switch(mSubcmd)
    {
        case QCA_NL80211_VENDOR_SUBCMD_NUD_STATS_GET:
        {
            struct nlattr *tb_vendor[QCA_ATTR_NUD_STATS_GET_MAX + 1];
            nud_stats *stats = &mStats;

            memset(stats, 0, sizeof(nud_stats));
            nla_parse(tb_vendor, QCA_ATTR_NUD_STATS_GET_MAX,
                      (struct nlattr *)mVendorData, mDataLen, NULL);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_FROM_NETDEV])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_FROM_NETDEV"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_req_count_from_netdev = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_FROM_NETDEV]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TO_LOWER_MAC])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TO_LOWER_MAC"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_req_count_to_lower_mac = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TO_LOWER_MAC]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_REQ_RX_COUNT_BY_LOWER_MAC])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_REQ_RX_COUNT_BY_LOWER_MAC"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_req_rx_count_by_lower_mac = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_REQ_RX_COUNT_BY_LOWER_MAC]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TX_SUCCESS])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TX_SUCCESS"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_req_count_tx_success = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_REQ_COUNT_TX_SUCCESS]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_LOWER_MAC])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_LOWER_MAC"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_rsp_rx_count_by_lower_mac = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_LOWER_MAC]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_UPPER_MAC])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_UPPER_MAC"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_rsp_rx_count_by_upper_mac = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_RSP_RX_COUNT_BY_UPPER_MAC]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_TO_NETDEV])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_TO_NETDEV"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_rsp_count_to_netdev = nla_get_u16(tb_vendor[
                            QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_TO_NETDEV]);

            if (!tb_vendor[QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_OUT_OF_ORDER_DROP])
            {
                ALOGE("%s: QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_OUT_OF_ORDER_DROP"
                      " not found", __FUNCTION__);
                status = WIFI_ERROR_INVALID_ARGS;
                goto cleanup;
            }
            stats->arp_rsp_count_out_of_order_drop = nla_get_u16(tb_vendor[
                           QCA_ATTR_NUD_STATS_ARP_RSP_COUNT_OUT_OF_ORDER_DROP]);

            if (tb_vendor[QCA_ATTR_NUD_STATS_AP_LINK_ACTIVE])
                stats->ap_link_active = 1;

            if (tb_vendor[QCA_ATTR_NUD_STATS_IS_DAD])
                stats->is_duplicate_addr_detection = 1;

            ALOGV(" req_from_netdev %d count_to_lower :%d"
                  " count_by_lower :%d"
                  " count_tx_succ :%d rsp_count_lower :%d"
                  " rsp_count_upper :%d  rsp_count_netdev :%d"
                  " out_of_order_drop :%d active_aplink %d"
                  " DAD %d ",
                  stats->arp_req_count_from_netdev,
                  stats->arp_req_count_to_lower_mac,
                  stats->arp_req_rx_count_by_lower_mac,
                  stats->arp_req_count_tx_success,
                  stats->arp_rsp_rx_count_by_lower_mac,
                  stats->arp_rsp_rx_count_by_upper_mac,
                  stats->arp_rsp_count_to_netdev,
                  stats->arp_rsp_count_out_of_order_drop,
                  stats->ap_link_active,
                  stats->is_duplicate_addr_detection);

            if (tb_vendor[QCA_ATTR_NUD_STATS_DATA_PKT_STATS]) {
                mNUDStatsCommandInstance->GetPktInfo(tb_vendor);
            }
        }
    }
cleanup:
    if (status == WIFI_ERROR_INVALID_ARGS)
       memset(&mStats,0,sizeof(nud_stats));
       if(mpktInfo != NULL)
         free(mpktInfo);

    return status;
}

void NUDStatsCommand::GetPktInfo(struct nlattr **tbvendor)
{
    struct nlattr *tb;
    int rem;
    cmdData *pkt_stats;
    char ipv6_address[INET6_ADDRSTRLEN];
    cmdData pktstats;
    int nbuff = 0;

    for (tb = (struct nlattr *) nla_data(tbvendor[QCA_ATTR_NUD_STATS_DATA_PKT_STATS]),
         rem = nla_len(tbvendor[QCA_ATTR_NUD_STATS_DATA_PKT_STATS]);
         nla_ok(tb, rem); tb = nla_next(tb, &(rem)))
       {
           struct nlattr *tb2[QCA_ATTR_NUD_DATA_STATS_MAX + 1];
           nla_parse(tb2, QCA_ATTR_NUD_DATA_STATS_MAX,
                     (struct nlattr *) nla_data(tb), nla_len(tb), NULL);

           memset(&pktstats, 0, sizeof(cmdData));

           if (tb2[QCA_ATTR_NUD_STATS_PKT_TYPE])
           {
              pktstats.pkt_Type = nla_get_u32(tb2[QCA_ATTR_NUD_STATS_PKT_TYPE]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_DNS_DOMAIN_NAME])
           {
              pktstats.domain_name = nla_get_string(tb2[QCA_ATTR_NUD_STATS_PKT_DNS_DOMAIN_NAME]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_SRC_PORT])
           {
              pktstats.src_port = nla_get_u32(tb2[QCA_ATTR_NUD_STATS_PKT_SRC_PORT]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_DEST_PORT])
           {
              pktstats.dst_port = nla_get_u32(tb2[QCA_ATTR_NUD_STATS_PKT_DEST_PORT]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_DEST_IPV4])
           {
              pktstats.ipv4_addr.s_addr = nla_get_u32(tb2[QCA_ATTR_NUD_STATS_PKT_DEST_IPV4]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_DEST_IPV6])
           {
              memcpy(pktstats.ipv6_addr, nla_data(tb2[QCA_ATTR_NUD_STATS_PKT_DEST_IPV6]),
                     sizeof(pktstats.ipv6_addr));
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_FROM_NETDEV])
           {
              pktstats.stats.pkt_req_count_from_netdev = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_FROM_NETDEV]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_TO_LOWER_MAC])
           {
              pktstats.stats.pkt_req_count_to_lower_mac = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_TO_LOWER_MAC]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_REQ_RX_COUNT_BY_LOWER_MAC])
           {
              pktstats.stats.pkt_req_rx_count_by_lower_mac = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_REQ_RX_COUNT_BY_LOWER_MAC]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_TX_SUCCESS])
           {
              pktstats.stats.pkt_req_count_tx_success = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_REQ_COUNT_TX_SUCCESS]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_RSP_RX_COUNT_BY_LOWER_MAC])
           {
              pktstats.stats.pkt_rsp_rx_count_by_lower_mac = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_RSP_RX_COUNT_BY_LOWER_MAC]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_RSP_RX_COUNT_BY_UPPER_MAC])
           {
              pktstats.stats.pkt_rsp_rx_count_by_upper_mac = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_RSP_RX_COUNT_BY_UPPER_MAC]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_RSP_COUNT_TO_NETDEV])
           {
              pktstats.stats.pkt_rsp_count_to_netdev = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_RSP_COUNT_TO_NETDEV]);
           }

           if (tb2[QCA_ATTR_NUD_STATS_PKT_RSP_COUNT_OUT_OF_ORDER_DROP])
           {
              pktstats.stats.pkt_rsp_count_out_of_order_drop = nla_get_u16(tb2[
                            QCA_ATTR_NUD_STATS_PKT_RSP_COUNT_OUT_OF_ORDER_DROP]);
           }

           if (inet_ntop(AF_INET6, pktstats.ipv6_addr, ipv6_address,
                         INET6_ADDRSTRLEN) == NULL) {
               ALOGE("%s: failed to convert ipv6 address format", __FUNCTION__);
           }

           ALOGV(" pkt_type %d domain_name :%s"
                 " src_port %d dst_port :%d"
                 " ipv4_address :%x ipv6_address %s"
                 " req_from_netdev %d count_to_lower :%d"
                 " count_by_lower :%d"
                 " count_tx_succ :%d rsp_count_lower :%d"
                 " rsp_count_upper :%d  rsp_count_netdev :%d"
                 " out_of_order_drop :%d ",
                 pktstats.pkt_Type, pktstats.domain_name,
                 pktstats.src_port, pktstats.dst_port,
                 pktstats.ipv4_addr.s_addr, ipv6_address,
                 pktstats.stats.pkt_req_count_from_netdev,
                 pktstats.stats.pkt_req_count_to_lower_mac,
                 pktstats.stats.pkt_req_rx_count_by_lower_mac,
                 pktstats.stats.pkt_req_count_tx_success,
                 pktstats.stats.pkt_rsp_rx_count_by_lower_mac,
                 pktstats.stats.pkt_rsp_rx_count_by_upper_mac,
                 pktstats.stats.pkt_rsp_count_to_netdev,
                 pktstats.stats.pkt_rsp_count_out_of_order_drop);

            if (nbuff == 0)
               pkt_stats = (cmdData *)malloc(sizeof(cmdData));
            else
               pkt_stats = (cmdData *)realloc(pkt_stats,sizeof(cmdData) * (nbuff + 1));

            mpktInfo = pkt_stats;
            if (pkt_stats != NULL) {
                memcpy(&pkt_stats[nbuff], &pktstats,sizeof(cmdData));
                nbuff++;
                mnumStats = nbuff;
            }
       }
}

void NUDStatsCommand::copyStats(nud_stats *stats, cmdData *pktstats)
{
    memcpy(stats, &mStats, sizeof(nud_stats));
    pktstats = mpktInfo;
}

wifi_error wifi_set_nud_stats(wifi_interface_handle iface,
                              u32 gw_addr, cmdData Data)
{
    wifi_error ret;
    NUDStatsCommand *NUDCommand;
    struct nlattr *nl_data,*nl_pktInfo;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    cmdData mData = Data;
    cmdData pktstats = Data;

    ALOGV("gw_addr : %x", gw_addr);
    NUDCommand = NUDStatsCommand::instance(handle);
    if (NUDCommand == NULL) {
        ALOGE("%s: Error NUDStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    NUDCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_NUD_STATS_SET);

    /* create the message */
    ret = NUDCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = NUDCommand->set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /*add the attributes*/
    nl_data = NUDCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data)
        goto cleanup;
    /**/
    ret = NUDCommand->put_flag(QCA_ATTR_NUD_STATS_SET_START);

    ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_GW_IPV4, gw_addr);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    if (mData.pkt_Type) {
       /*start the packet info attributes in nested*/
       nl_pktInfo = NUDCommand->attr_start(QCA_ATTR_NUD_STATS_SET_DATA_PKT_INFO);
       if (!nl_pktInfo)
           goto cleanup;
       else {
           ALOGV(" pkt_type %d domain_name :%s"
                 " src_port %d dst_port :%d"
                 " ipv4_address :%x ipv6_address %s",
                 pktstats.pkt_Type, pktstats.domain_name,
                 pktstats.src_port, pktstats.dst_port,
                 pktstats.ipv4_addr.s_addr,pktstats.ipv6_addr);

           for (int i=0; i < MAX_INFO ; i++) {
               /*add the packet type attributes*/
               struct nlattr *tb_tmp;
               tb_tmp = NUDCommand->attr_start(i);

               ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_TYPE,mData.pkt_Type);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;

               if (mData.domain_name) {
                   /*add the domain name attributes*/
                   ret = NUDCommand->put_string(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_DNS_DOMAIN_NAME,
                                                mData.domain_name);
                   if (ret != WIFI_SUCCESS)
                       goto cleanup;
               }
               /*add the source port attributes*/
               ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_SRC_PORT,
                                         mData.src_port);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;

               /*add the dest port attributes*/
               ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_DEST_PORT,
                                         mData.dst_port);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;

               /*add the ipv4 address attributes*/
               ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_DEST_IPV4,
                                         mData.ipv4_addr.s_addr);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;

               /*add the ipv6 address attributes*/
               ret = NUDCommand->put_ipv6_addr(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_DEST_IPV6,
                                               mData.ipv6_addr);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;
               NUDCommand->attr_end(tb_tmp);
           }
       }
       NUDCommand->attr_end(nl_pktInfo);
    }
    NUDCommand->attr_end(nl_data);

    ret = NUDCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
    }

cleanup:
    return ret;
}


wifi_error wifi_get_nud_stats(wifi_interface_handle iface,
                              pkt_stats_result_handler handler)
{
    wifi_error ret;
    NUDStatsCommand *NUDCommand;
    struct nlattr *nl_data;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);

    NUDCommand = NUDStatsCommand::instance(handle);
    if (NUDCommand == NULL) {
        ALOGE("%s: Error NUDStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    NUDCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_NUD_STATS_GET);

    NUDCommand->setHandler(handler);

    /* create the message */
    ret = NUDCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = NUDCommand->set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;
    /*add the attributes*/
    nl_data = NUDCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }
    /**/
    NUDCommand->attr_end(nl_data);

    ret = NUDCommand->requestResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

    ret = NUDCommand->notifyResponse();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);
        goto cleanup;
    }

cleanup:
    return ret;
}


wifi_error wifi_clear_nud_stats(wifi_interface_handle iface,
                                cmdData Data)
{
    wifi_error ret;
    NUDStatsCommand *NUDCommand;
    struct nlattr *nl_data,*nl_pktInfo;
    interface_info *iinfo = getIfaceInfo(iface);
    wifi_handle handle = getWifiHandle(iface);
    cmdData mData = Data;

    NUDCommand = NUDStatsCommand::instance(handle);
    if (NUDCommand == NULL) {
        ALOGE("%s: Error NUDStatsCommand NULL", __FUNCTION__);
        return WIFI_ERROR_INVALID_ARGS;
    }
    NUDCommand->setSubCmd(QCA_NL80211_VENDOR_SUBCMD_NUD_STATS_SET);

    /* create the message */
    ret = NUDCommand->create();
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    ret = NUDCommand->set_iface_id(iinfo->name);
    if (ret != WIFI_SUCCESS)
        goto cleanup;

    /*add the attributes*/
    nl_data = NUDCommand->attr_start(NL80211_ATTR_VENDOR_DATA);
    if (!nl_data){
        ret = WIFI_ERROR_UNKNOWN;
        goto cleanup;
    }
    if (mData.pkt_Type) {
       /*set the packet info attributes in nested*/
       nl_pktInfo = NUDCommand->attr_start(QCA_ATTR_NUD_STATS_SET_DATA_PKT_INFO);
       if (!nl_pktInfo){
           ret = WIFI_ERROR_UNKNOWN;
           goto cleanup;
       }
       else {
           ALOGV(" %s: pkt_type %d domain_name :%s"
                 " src_port %d dst_port :%d"
                 " ipv4_address :%x ipv6_address %s",
                 __FUNCTION__,mData.pkt_Type, mData.domain_name,
                 mData.src_port, mData.dst_port,
                 mData.ipv4_addr.s_addr,mData.ipv6_addr);

           for (int i=0; i < MAX_INFO ; i++) {
               /*add the packet type attributes*/
               struct nlattr *tb_tmp;
               tb_tmp = NUDCommand->attr_start(i);

               ret = NUDCommand->put_u32(QCA_ATTR_NUD_STATS_DATA_PKT_INFO_TYPE,mData.pkt_Type);
               if (ret != WIFI_SUCCESS)
                   goto cleanup;

               NUDCommand->attr_end(tb_tmp);
           }
       }
       NUDCommand->attr_end(nl_pktInfo);
    }
    NUDCommand->attr_end(nl_data);

    ret = NUDCommand->requestResponse();
    if (ret != WIFI_SUCCESS)
        ALOGE("%s: requestResponse Error:%d",__FUNCTION__, ret);

cleanup:
    return ret;
}
