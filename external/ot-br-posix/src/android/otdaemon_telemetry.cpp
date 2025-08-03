/*
 *    Copyright (c) 2023, The OpenThread Authors.
 *    All rights reserved.
 *
 *    Redistribution and use in source and binary forms, with or without
 *    modification, are permitted provided that the following conditions are met:
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *    POSSIBILITY OF SUCH DAMAGE.
 */
#include "android/otdaemon_telemetry.hpp"

#include <openthread/openthread-system.h>
#include <openthread/thread.h>
#include <openthread/thread_ftd.h>
#include <openthread/platform/radio.h>

#if OTBR_ENABLE_DNSSD_DISCOVERY_PROXY
#include <openthread/dnssd_server.h>
#endif
#if OTBR_ENABLE_SRP_ADVERTISING_PROXY
#include <openthread/srp_server.h>
#endif

#include "statslog_threadnetwork.h"
#include "common/code_utils.hpp"
#include "mdns/mdns.hpp"
#include "proto/threadnetwork_atoms.pb.h"

namespace otbr {
namespace Android {
using android::os::statsd::threadnetwork::ThreadnetworkDeviceInfoReported;
using android::os::statsd::threadnetwork::ThreadnetworkTelemetryDataReported;
using android::os::statsd::threadnetwork::ThreadnetworkTopoEntryRepeated;

static uint32_t TelemetryNodeTypeFromRoleAndLinkMode(const otDeviceRole &aRole, const otLinkModeConfig &aLinkModeCfg)
{
    uint32_t nodeType;

    switch (aRole)
    {
    case OT_DEVICE_ROLE_DISABLED:
        nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_DISABLED;
        break;
    case OT_DEVICE_ROLE_DETACHED:
        nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_DETACHED;
        break;
    case OT_DEVICE_ROLE_ROUTER:
        nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_ROUTER;
        break;
    case OT_DEVICE_ROLE_LEADER:
        nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_LEADER;
        break;
    case OT_DEVICE_ROLE_CHILD:
        if (!aLinkModeCfg.mRxOnWhenIdle)
        {
            nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_SLEEPY_END;
        }
        else if (!aLinkModeCfg.mDeviceType)
        {
            // If it's not an FTD, return as minimal end device.
            nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_MINIMAL_END;
        }
        else
        {
            nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_END;
        }
        break;
    default:
        nodeType = ThreadnetworkTelemetryDataReported::NODE_TYPE_UNSPECIFIED;
    }

    return nodeType;
}

#if OTBR_ENABLE_SRP_ADVERTISING_PROXY
ThreadnetworkTelemetryDataReported::SrpServerState SrpServerStateFromOtSrpServerState(otSrpServerState aSrpServerState)
{
    ThreadnetworkTelemetryDataReported::SrpServerState srpServerState;

    switch (aSrpServerState)
    {
    case OT_SRP_SERVER_STATE_DISABLED:
        srpServerState = ThreadnetworkTelemetryDataReported::SRP_SERVER_STATE_DISABLED;
        break;
    case OT_SRP_SERVER_STATE_RUNNING:
        srpServerState = ThreadnetworkTelemetryDataReported::SRP_SERVER_STATE_RUNNING;
        break;
    case OT_SRP_SERVER_STATE_STOPPED:
        srpServerState = ThreadnetworkTelemetryDataReported::SRP_SERVER_STATE_STOPPED;
        break;
    default:
        srpServerState = ThreadnetworkTelemetryDataReported::SRP_SERVER_STATE_UNSPECIFIED;
    }
    return srpServerState;
}

ThreadnetworkTelemetryDataReported::SrpServerAddressMode SrpServerAddressModeFromOtSrpServerAddressMode(
    otSrpServerAddressMode aSrpServerAddressMode)
{
    ThreadnetworkTelemetryDataReported::SrpServerAddressMode srpServerAddressMode;

    switch (aSrpServerAddressMode)
    {
    case OT_SRP_SERVER_ADDRESS_MODE_ANYCAST:
        srpServerAddressMode = ThreadnetworkTelemetryDataReported::SRP_SERVER_ADDRESS_MODE_STATE_ANYCAST;
        break;
    case OT_SRP_SERVER_ADDRESS_MODE_UNICAST:
        srpServerAddressMode = ThreadnetworkTelemetryDataReported::SRP_SERVER_ADDRESS_MODE_UNICAST;
        break;
    default:
        srpServerAddressMode = ThreadnetworkTelemetryDataReported::SRP_SERVER_ADDRESS_MODE_UNSPECIFIED;
    }
    return srpServerAddressMode;
}
#endif // OTBR_ENABLE_SRP_ADVERTISING_PROXY

void CopyMdnsResponseCounters(const MdnsResponseCounters                               &from,
                              ThreadnetworkTelemetryDataReported::MdnsResponseCounters *to)
{
    to->set_success_count(from.mSuccess);
    to->set_not_found_count(from.mNotFound);
    to->set_invalid_args_count(from.mInvalidArgs);
    to->set_duplicated_count(from.mDuplicated);
    to->set_not_implemented_count(from.mNotImplemented);
    to->set_unknown_error_count(from.mUnknownError);
    to->set_aborted_count(from.mAborted);
    to->set_invalid_state_count(from.mInvalidState);
}

otError RetrieveTelemetryAtom(otInstance                         *otInstance,
                              Mdns::Publisher                    *aPublisher,
                              ThreadnetworkTelemetryDataReported &telemetryDataReported,
                              ThreadnetworkTopoEntryRepeated     &topoEntryRepeated,
                              ThreadnetworkDeviceInfoReported    &deviceInfoReported)
{
    otError                     error = OT_ERROR_NONE;
    std::vector<otNeighborInfo> neighborTable;

    // Begin of WpanStats section.
    auto wpanStats = telemetryDataReported.mutable_wpan_stats();

    {
        otDeviceRole     role  = otThreadGetDeviceRole(otInstance);
        otLinkModeConfig otCfg = otThreadGetLinkMode(otInstance);

        wpanStats->set_node_type(TelemetryNodeTypeFromRoleAndLinkMode(role, otCfg));
    }

    wpanStats->set_channel(otLinkGetChannel(otInstance));

    {
        uint16_t ccaFailureRate = otLinkGetCcaFailureRate(otInstance);

        wpanStats->set_mac_cca_fail_rate(static_cast<float>(ccaFailureRate) / 0xffff);
    }

    {
        int8_t radioTxPower;

        if (otPlatRadioGetTransmitPower(otInstance, &radioTxPower) == OT_ERROR_NONE)
        {
            wpanStats->set_radio_tx_power(radioTxPower);
        }
        else
        {
            error = OT_ERROR_FAILED;
        }
    }

    {
        const otMacCounters *linkCounters = otLinkGetCounters(otInstance);

        wpanStats->set_phy_rx(linkCounters->mRxTotal);
        wpanStats->set_phy_tx(linkCounters->mTxTotal);
        wpanStats->set_mac_unicast_rx(linkCounters->mRxUnicast);
        wpanStats->set_mac_unicast_tx(linkCounters->mTxUnicast);
        wpanStats->set_mac_broadcast_rx(linkCounters->mRxBroadcast);
        wpanStats->set_mac_broadcast_tx(linkCounters->mTxBroadcast);
        wpanStats->set_mac_tx_ack_req(linkCounters->mTxAckRequested);
        wpanStats->set_mac_tx_no_ack_req(linkCounters->mTxNoAckRequested);
        wpanStats->set_mac_tx_acked(linkCounters->mTxAcked);
        wpanStats->set_mac_tx_data(linkCounters->mTxData);
        wpanStats->set_mac_tx_data_poll(linkCounters->mTxDataPoll);
        wpanStats->set_mac_tx_beacon(linkCounters->mTxBeacon);
        wpanStats->set_mac_tx_beacon_req(linkCounters->mTxBeaconRequest);
        wpanStats->set_mac_tx_other_pkt(linkCounters->mTxOther);
        wpanStats->set_mac_tx_retry(linkCounters->mTxRetry);
        wpanStats->set_mac_rx_data(linkCounters->mRxData);
        wpanStats->set_mac_rx_data_poll(linkCounters->mRxDataPoll);
        wpanStats->set_mac_rx_beacon(linkCounters->mRxBeacon);
        wpanStats->set_mac_rx_beacon_req(linkCounters->mRxBeaconRequest);
        wpanStats->set_mac_rx_other_pkt(linkCounters->mRxOther);
        wpanStats->set_mac_rx_filter_whitelist(linkCounters->mRxAddressFiltered);
        wpanStats->set_mac_rx_filter_dest_addr(linkCounters->mRxDestAddrFiltered);
        wpanStats->set_mac_tx_fail_cca(linkCounters->mTxErrCca);
        wpanStats->set_mac_rx_fail_decrypt(linkCounters->mRxErrSec);
        wpanStats->set_mac_rx_fail_no_frame(linkCounters->mRxErrNoFrame);
        wpanStats->set_mac_rx_fail_unknown_neighbor(linkCounters->mRxErrUnknownNeighbor);
        wpanStats->set_mac_rx_fail_invalid_src_addr(linkCounters->mRxErrInvalidSrcAddr);
        wpanStats->set_mac_rx_fail_fcs(linkCounters->mRxErrFcs);
        wpanStats->set_mac_rx_fail_other(linkCounters->mRxErrOther);
    }

    {
        const otIpCounters *ipCounters = otThreadGetIp6Counters(otInstance);

        wpanStats->set_ip_tx_success(ipCounters->mTxSuccess);
        wpanStats->set_ip_rx_success(ipCounters->mRxSuccess);
        wpanStats->set_ip_tx_failure(ipCounters->mTxFailure);
        wpanStats->set_ip_rx_failure(ipCounters->mRxFailure);
    }
    // End of WpanStats section.

    {
        // Begin of WpanTopoFull section.
        auto     wpanTopoFull = telemetryDataReported.mutable_wpan_topo_full();
        uint16_t rloc16       = otThreadGetRloc16(otInstance);

        wpanTopoFull->set_rloc16(rloc16);

        {
            otRouterInfo info;

            if (otThreadGetRouterInfo(otInstance, rloc16, &info) == OT_ERROR_NONE)
            {
                wpanTopoFull->set_router_id(info.mRouterId);
            }
            else
            {
                error = OT_ERROR_FAILED;
            }
        }

        {
            otNeighborInfoIterator iter = OT_NEIGHBOR_INFO_ITERATOR_INIT;
            otNeighborInfo         neighborInfo;

            while (otThreadGetNextNeighborInfo(otInstance, &iter, &neighborInfo) == OT_ERROR_NONE)
            {
                neighborTable.push_back(neighborInfo);
            }
        }
        wpanTopoFull->set_neighbor_table_size(neighborTable.size());

        uint16_t                 childIndex = 0;
        otChildInfo              childInfo;
        std::vector<otChildInfo> childTable;

        while (otThreadGetChildInfoByIndex(otInstance, childIndex, &childInfo) == OT_ERROR_NONE)
        {
            childTable.push_back(childInfo);
            childIndex++;
        }
        wpanTopoFull->set_child_table_size(childTable.size());

        {
            struct otLeaderData leaderData;

            if (otThreadGetLeaderData(otInstance, &leaderData) == OT_ERROR_NONE)
            {
                wpanTopoFull->set_leader_router_id(leaderData.mLeaderRouterId);
                wpanTopoFull->set_leader_weight(leaderData.mWeighting);
                // Do not log network_data_version.
            }
            else
            {
                error = OT_ERROR_FAILED;
            }
        }

        uint8_t weight = otThreadGetLocalLeaderWeight(otInstance);

        wpanTopoFull->set_leader_local_weight(weight);

        int8_t rssi = otPlatRadioGetRssi(otInstance);

        wpanTopoFull->set_instant_rssi(rssi);

        const otExtendedPanId *extPanId = otThreadGetExtendedPanId(otInstance);
        uint64_t               extPanIdVal;

        extPanIdVal = ConvertOpenThreadUint64(extPanId->m8);
        wpanTopoFull->set_has_extended_pan_id(extPanIdVal != 0);
        // Note: Used leader_router_id instead of leader_rloc16.
        // Note: Network level info (e.g., extended_pan_id, partition_id, is_active_br) is not logged.
        // TODO: populate is_active_srp_server, sum_on_link_prefix_changes, preferred_router_id
        // if needed.
        // End of WpanTopoFull section.

        // Begin of TopoEntry section.
        std::map<uint16_t, const otChildInfo *> childMap;

        for (const otChildInfo &childInfo : childTable)
        {
            auto pair = childMap.insert({childInfo.mRloc16, &childInfo});
            if (!pair.second)
            {
                // This shouldn't happen, so log an error. It doesn't matter which
                // duplicate is kept.
                otbrLogErr("Children with duplicate RLOC16 found: 0x%04x", static_cast<int>(childInfo.mRloc16));
            }
        }

        for (const otNeighborInfo &neighborInfo : neighborTable)
        {
            auto topoEntry = topoEntryRepeated.mutable_topo_entry_repeated()->add_topo_entries();

            // 0~15: uint16_t rloc_16
            // 16~31: uint16_t version Thread version of the neighbor
            uint32_t comboTelemetry1 = 0;
            comboTelemetry1 |= (((uint32_t)neighborInfo.mRloc16) & 0x0000FFFF);
            comboTelemetry1 |= ((((uint32_t)neighborInfo.mVersion) & 0x0000FFFF) << 16);
            topoEntry->set_combo_telemetry1(comboTelemetry1);

            topoEntry->set_age_sec(neighborInfo.mAge);

            // 0~7: uint8_t link_quality_in
            // 8~15: int8_t average_rssi
            // 16~23: int8_t last_rssi
            // 24~31: uint8_t network_data_version
            uint32_t comboTelemetry2 = 0;
            comboTelemetry2 |= (((uint32_t)neighborInfo.mLinkQualityIn) & 0x000000FF);
            comboTelemetry2 |= ((((uint32_t)neighborInfo.mAverageRssi) & 0x000000FF) << 8);
            comboTelemetry2 |= ((((uint32_t)neighborInfo.mLastRssi) & 0x000000FF) << 16);
            // network_data_version is populated in the next section.
            topoEntry->set_combo_telemetry2(comboTelemetry2);

            // Each bit on the flag represents a bool flag
            // 0: rx_on_when_idle
            // 1: full_function
            // 2: secure_data_request
            // 3: full_network_data
            // 4: is_child
            uint32_t topoEntryFlags = 0;
            topoEntryFlags |= (neighborInfo.mRxOnWhenIdle ? 1 : 0);
            topoEntryFlags |= ((neighborInfo.mFullThreadDevice ? 1 : 0) << 1);
            topoEntryFlags |= ((/* secure_data_request */ true ? 1 : 0) << 2);
            topoEntryFlags |= ((neighborInfo.mFullNetworkData ? 1 : 0) << 3);
            topoEntry->set_topo_entry_flags(topoEntryFlags);

            topoEntry->set_link_frame_counter(neighborInfo.mLinkFrameCounter);
            topoEntry->set_mle_frame_counter(neighborInfo.mMleFrameCounter);

            // 0~15: uint16_t mac_frame_error_rate. Frame error rate (0xffff->100%). Requires error tracking feature.
            // 16~31: uint16_t ip_message_error_rate. (IPv6) msg error rate (0xffff->100%). Requires error tracking
            // feature.
            uint32_t comboTelemetry3 = 0;
            comboTelemetry3 |= ((uint32_t)(neighborInfo.mFrameErrorRate) & 0x0000FFFF);
            comboTelemetry3 |= ((((uint32_t)neighborInfo.mMessageErrorRate) & 0x0000FFFF) << 16);
            topoEntry->set_combo_telemetry3(comboTelemetry3);

            if (!neighborInfo.mIsChild)
            {
                continue;
            }

            auto it = childMap.find(neighborInfo.mRloc16);
            if (it == childMap.end())
            {
                otbrLogErr("Neighbor 0x%04x not found in child table", static_cast<int>(neighborInfo.mRloc16));
                continue;
            }
            const otChildInfo *childInfo = it->second;

            comboTelemetry2 |= ((((uint32_t)childInfo->mNetworkDataVersion) & 0x000000FF) << 24);
            topoEntry->set_combo_telemetry2(comboTelemetry2);

            topoEntryFlags |= ((/* is_child */ true ? 1 : 0) << 4);
            topoEntry->set_topo_entry_flags(topoEntryFlags);

            topoEntry->set_timeout_sec(childInfo->mTimeout);
        }
        // End of TopoEntry section.
    }

    {
        // Begin of WpanBorderRouter section.
        auto wpanBorderRouter = telemetryDataReported.mutable_wpan_border_router();
        // Begin of BorderRoutingCounters section.
        auto                           borderRoutingCouters    = wpanBorderRouter->mutable_border_routing_counters();
        const otBorderRoutingCounters *otBorderRoutingCounters = otIp6GetBorderRoutingCounters(otInstance);

        borderRoutingCouters->mutable_inbound_unicast()->set_packet_count(
            otBorderRoutingCounters->mInboundUnicast.mPackets);
        borderRoutingCouters->mutable_inbound_unicast()->set_byte_count(
            otBorderRoutingCounters->mInboundUnicast.mBytes);
        borderRoutingCouters->mutable_inbound_multicast()->set_packet_count(
            otBorderRoutingCounters->mInboundMulticast.mPackets);
        borderRoutingCouters->mutable_inbound_multicast()->set_byte_count(
            otBorderRoutingCounters->mInboundMulticast.mBytes);
        borderRoutingCouters->mutable_outbound_unicast()->set_packet_count(
            otBorderRoutingCounters->mOutboundUnicast.mPackets);
        borderRoutingCouters->mutable_outbound_unicast()->set_byte_count(
            otBorderRoutingCounters->mOutboundUnicast.mBytes);
        borderRoutingCouters->mutable_outbound_multicast()->set_packet_count(
            otBorderRoutingCounters->mOutboundMulticast.mPackets);
        borderRoutingCouters->mutable_outbound_multicast()->set_byte_count(
            otBorderRoutingCounters->mOutboundMulticast.mBytes);
        borderRoutingCouters->set_ra_rx(otBorderRoutingCounters->mRaRx);
        borderRoutingCouters->set_ra_tx_success(otBorderRoutingCounters->mRaTxSuccess);
        borderRoutingCouters->set_ra_tx_failure(otBorderRoutingCounters->mRaTxFailure);
        borderRoutingCouters->set_rs_rx(otBorderRoutingCounters->mRsRx);
        borderRoutingCouters->set_rs_tx_success(otBorderRoutingCounters->mRsTxSuccess);
        borderRoutingCouters->set_rs_tx_failure(otBorderRoutingCounters->mRsTxFailure);

        // End of BorderRoutingCounters section.

#if OTBR_ENABLE_SRP_ADVERTISING_PROXY
        // Begin of SrpServerInfo section.
        {
            auto                               srpServer = wpanBorderRouter->mutable_srp_server();
            otSrpServerLeaseInfo               leaseInfo;
            const otSrpServerHost             *host             = nullptr;
            const otSrpServerResponseCounters *responseCounters = otSrpServerGetResponseCounters(otInstance);

            srpServer->set_state(SrpServerStateFromOtSrpServerState(otSrpServerGetState(otInstance)));
            srpServer->set_port(otSrpServerGetPort(otInstance));
            srpServer->set_address_mode(
                SrpServerAddressModeFromOtSrpServerAddressMode(otSrpServerGetAddressMode(otInstance)));

            auto srpServerHosts            = srpServer->mutable_hosts();
            auto srpServerServices         = srpServer->mutable_services();
            auto srpServerResponseCounters = srpServer->mutable_response_counters();

            while ((host = otSrpServerGetNextHost(otInstance, host)))
            {
                const otSrpServerService *service = nullptr;

                if (otSrpServerHostIsDeleted(host))
                {
                    srpServerHosts->set_deleted_count(srpServerHosts->deleted_count() + 1);
                }
                else
                {
                    srpServerHosts->set_fresh_count(srpServerHosts->fresh_count() + 1);
                    otSrpServerHostGetLeaseInfo(host, &leaseInfo);
                    srpServerHosts->set_lease_time_total_ms(srpServerHosts->lease_time_total_ms() + leaseInfo.mLease);
                    srpServerHosts->set_key_lease_time_total_ms(srpServerHosts->key_lease_time_total_ms() +
                                                                leaseInfo.mKeyLease);
                    srpServerHosts->set_remaining_lease_time_total_ms(srpServerHosts->remaining_lease_time_total_ms() +
                                                                      leaseInfo.mRemainingLease);
                    srpServerHosts->set_remaining_key_lease_time_total_ms(
                        srpServerHosts->remaining_key_lease_time_total_ms() + leaseInfo.mRemainingKeyLease);
                }

                while ((service = otSrpServerHostGetNextService(host, service)))
                {
                    if (otSrpServerServiceIsDeleted(service))
                    {
                        srpServerServices->set_deleted_count(srpServerServices->deleted_count() + 1);
                    }
                    else
                    {
                        srpServerServices->set_fresh_count(srpServerServices->fresh_count() + 1);
                        otSrpServerServiceGetLeaseInfo(service, &leaseInfo);
                        srpServerServices->set_lease_time_total_ms(srpServerServices->lease_time_total_ms() +
                                                                   leaseInfo.mLease);
                        srpServerServices->set_key_lease_time_total_ms(srpServerServices->key_lease_time_total_ms() +
                                                                       leaseInfo.mKeyLease);
                        srpServerServices->set_remaining_lease_time_total_ms(
                            srpServerServices->remaining_lease_time_total_ms() + leaseInfo.mRemainingLease);
                        srpServerServices->set_remaining_key_lease_time_total_ms(
                            srpServerServices->remaining_key_lease_time_total_ms() + leaseInfo.mRemainingKeyLease);
                    }
                }
            }

            srpServerResponseCounters->set_success_count(responseCounters->mSuccess);
            srpServerResponseCounters->set_server_failure_count(responseCounters->mServerFailure);
            srpServerResponseCounters->set_format_error_count(responseCounters->mFormatError);
            srpServerResponseCounters->set_name_exists_count(responseCounters->mNameExists);
            srpServerResponseCounters->set_refused_count(responseCounters->mRefused);
            srpServerResponseCounters->set_other_count(responseCounters->mOther);
        }
        // End of SrpServerInfo section.
#endif // OTBR_ENABLE_SRP_ADVERTISING_PROXY

#if OTBR_ENABLE_DNSSD_DISCOVERY_PROXY
        // Begin of DnsServerInfo section.
        {
            auto            dnsServer                 = wpanBorderRouter->mutable_dns_server();
            auto            dnsServerResponseCounters = dnsServer->mutable_response_counters();
            otDnssdCounters otDnssdCounters           = *otDnssdGetCounters(otInstance);

            dnsServerResponseCounters->set_success_count(otDnssdCounters.mSuccessResponse);
            dnsServerResponseCounters->set_server_failure_count(otDnssdCounters.mServerFailureResponse);
            dnsServerResponseCounters->set_format_error_count(otDnssdCounters.mFormatErrorResponse);
            dnsServerResponseCounters->set_name_error_count(otDnssdCounters.mNameErrorResponse);
            dnsServerResponseCounters->set_not_implemented_count(otDnssdCounters.mNotImplementedResponse);
            dnsServerResponseCounters->set_other_count(otDnssdCounters.mOtherResponse);

            dnsServer->set_resolved_by_local_srp_count(otDnssdCounters.mResolvedBySrp);
        }
        // End of DnsServerInfo section.
#endif // OTBR_ENABLE_DNSSD_DISCOVERY_PROXY

        // Start of MdnsInfo section.
        if (aPublisher != nullptr)
        {
            auto                     mdns     = wpanBorderRouter->mutable_mdns();
            const MdnsTelemetryInfo &mdnsInfo = aPublisher->GetMdnsTelemetryInfo();

            CopyMdnsResponseCounters(mdnsInfo.mHostRegistrations, mdns->mutable_host_registration_responses());
            CopyMdnsResponseCounters(mdnsInfo.mServiceRegistrations, mdns->mutable_service_registration_responses());
            CopyMdnsResponseCounters(mdnsInfo.mHostResolutions, mdns->mutable_host_resolution_responses());
            CopyMdnsResponseCounters(mdnsInfo.mServiceResolutions, mdns->mutable_service_resolution_responses());

            mdns->set_host_registration_ema_latency_ms(mdnsInfo.mHostRegistrationEmaLatency);
            mdns->set_service_registration_ema_latency_ms(mdnsInfo.mServiceRegistrationEmaLatency);
            mdns->set_host_resolution_ema_latency_ms(mdnsInfo.mHostResolutionEmaLatency);
            mdns->set_service_resolution_ema_latency_ms(mdnsInfo.mServiceResolutionEmaLatency);
        }
        // End of MdnsInfo section.

        // End of WpanBorderRouter section.

        // Start of WpanRcp section.
        {
            auto                        wpanRcp                = telemetryDataReported.mutable_wpan_rcp();
            const otRadioSpinelMetrics *otRadioSpinelMetrics   = otSysGetRadioSpinelMetrics();
            auto                        rcpStabilityStatistics = wpanRcp->mutable_rcp_stability_statistics();

            if (otRadioSpinelMetrics != nullptr)
            {
                rcpStabilityStatistics->set_rcp_timeout_count(otRadioSpinelMetrics->mRcpTimeoutCount);
                rcpStabilityStatistics->set_rcp_reset_count(otRadioSpinelMetrics->mRcpUnexpectedResetCount);
                rcpStabilityStatistics->set_rcp_restoration_count(otRadioSpinelMetrics->mRcpRestorationCount);
                rcpStabilityStatistics->set_spinel_parse_error_count(otRadioSpinelMetrics->mSpinelParseErrorCount);
            }

            // TODO: provide rcp_firmware_update_count info.
            rcpStabilityStatistics->set_thread_stack_uptime(otInstanceGetUptime(otInstance));

            const otRcpInterfaceMetrics *otRcpInterfaceMetrics = otSysGetRcpInterfaceMetrics();

            if (otRcpInterfaceMetrics != nullptr)
            {
                auto rcpInterfaceStatistics = wpanRcp->mutable_rcp_interface_statistics();

                rcpInterfaceStatistics->set_rcp_interface_type(otRcpInterfaceMetrics->mRcpInterfaceType);
                rcpInterfaceStatistics->set_transferred_frames_count(otRcpInterfaceMetrics->mTransferredFrameCount);
                rcpInterfaceStatistics->set_transferred_valid_frames_count(
                    otRcpInterfaceMetrics->mTransferredValidFrameCount);
                rcpInterfaceStatistics->set_transferred_garbage_frames_count(
                    otRcpInterfaceMetrics->mTransferredGarbageFrameCount);
                rcpInterfaceStatistics->set_rx_frames_count(otRcpInterfaceMetrics->mRxFrameCount);
                rcpInterfaceStatistics->set_rx_bytes_count(otRcpInterfaceMetrics->mRxFrameByteCount);
                rcpInterfaceStatistics->set_tx_frames_count(otRcpInterfaceMetrics->mTxFrameCount);
                rcpInterfaceStatistics->set_tx_bytes_count(otRcpInterfaceMetrics->mTxFrameByteCount);
            }
        }
        // End of WpanRcp section.

        // Start of CoexMetrics section.
        {
            auto               coexMetrics = telemetryDataReported.mutable_coex_metrics();
            otRadioCoexMetrics otRadioCoexMetrics;

            if (otPlatRadioGetCoexMetrics(otInstance, &otRadioCoexMetrics) == OT_ERROR_NONE)
            {
                coexMetrics->set_count_tx_request(otRadioCoexMetrics.mNumTxRequest);
                coexMetrics->set_count_tx_grant_immediate(otRadioCoexMetrics.mNumTxGrantImmediate);
                coexMetrics->set_count_tx_grant_wait(otRadioCoexMetrics.mNumTxGrantWait);
                coexMetrics->set_count_tx_grant_wait_activated(otRadioCoexMetrics.mNumTxGrantWaitActivated);
                coexMetrics->set_count_tx_grant_wait_timeout(otRadioCoexMetrics.mNumTxGrantWaitTimeout);
                coexMetrics->set_count_tx_grant_deactivated_during_request(
                    otRadioCoexMetrics.mNumTxGrantDeactivatedDuringRequest);
                coexMetrics->set_tx_average_request_to_grant_time_us(otRadioCoexMetrics.mAvgTxRequestToGrantTime);
                coexMetrics->set_count_rx_request(otRadioCoexMetrics.mNumRxRequest);
                coexMetrics->set_count_rx_grant_immediate(otRadioCoexMetrics.mNumRxGrantImmediate);
                coexMetrics->set_count_rx_grant_wait(otRadioCoexMetrics.mNumRxGrantWait);
                coexMetrics->set_count_rx_grant_wait_activated(otRadioCoexMetrics.mNumRxGrantWaitActivated);
                coexMetrics->set_count_rx_grant_wait_timeout(otRadioCoexMetrics.mNumRxGrantWaitTimeout);
                coexMetrics->set_count_rx_grant_deactivated_during_request(
                    otRadioCoexMetrics.mNumRxGrantDeactivatedDuringRequest);
                coexMetrics->set_count_rx_grant_none(otRadioCoexMetrics.mNumRxGrantNone);
                coexMetrics->set_rx_average_request_to_grant_time_us(otRadioCoexMetrics.mAvgRxRequestToGrantTime);
            }
            else
            {
                error = OT_ERROR_FAILED;
            }
        }
        // End of CoexMetrics section.
    }

    deviceInfoReported.set_thread_version(otThreadGetVersion());
    deviceInfoReported.set_ot_rcp_version(otGetRadioVersionString(otInstance));
    // TODO: populate ot_host_version, thread_daemon_version.

    return error;
}

int PushAtom(const ThreadnetworkTelemetryDataReported &telemetryDataReported)
{
    const std::string        &wpanStats        = telemetryDataReported.wpan_stats().SerializeAsString();
    const std::string        &wpanTopoFull     = telemetryDataReported.wpan_topo_full().SerializeAsString();
    const std::string        &wpanBorderRouter = telemetryDataReported.wpan_border_router().SerializeAsString();
    const std::string        &wpanRcp          = telemetryDataReported.wpan_rcp().SerializeAsString();
    const std::string        &coexMetrics      = telemetryDataReported.coex_metrics().SerializeAsString();
    threadnetwork::BytesField wpanStatsBytesField{wpanStats.c_str(), wpanStats.size()};
    threadnetwork::BytesField wpanTopoFullBytesField{wpanTopoFull.c_str(), wpanTopoFull.size()};
    threadnetwork::BytesField wpanBorderRouterBytesField{wpanBorderRouter.c_str(), wpanBorderRouter.size()};
    threadnetwork::BytesField wpanRcpBytesField{wpanRcp.c_str(), wpanRcp.size()};
    threadnetwork::BytesField coexMetricsBytesField{coexMetrics.c_str(), coexMetrics.size()};
    return threadnetwork::stats_write(threadnetwork::THREADNETWORK_TELEMETRY_DATA_REPORTED, wpanStatsBytesField,
                                      wpanTopoFullBytesField, wpanBorderRouterBytesField, wpanRcpBytesField,
                                      coexMetricsBytesField);
}

int PushAtom(const ThreadnetworkTopoEntryRepeated &topoEntryRepeated)
{
    const std::string        &topoEntryField = topoEntryRepeated.topo_entry_repeated().SerializeAsString();
    threadnetwork::BytesField topoEntryFieldBytesField{topoEntryField.c_str(), topoEntryField.size()};
    return threadnetwork::stats_write(threadnetwork::THREADNETWORK_TOPO_ENTRY_REPEATED, topoEntryFieldBytesField);
}

int PushAtom(const ThreadnetworkDeviceInfoReported &deviceInfoReported)
{
    const std::string &otHostVersion       = deviceInfoReported.ot_host_version();
    const std::string &otRcpVersion        = deviceInfoReported.ot_rcp_version();
    const int32_t     &threadVersion       = deviceInfoReported.thread_version();
    const std::string &threadDaemonVersion = deviceInfoReported.thread_daemon_version();
    return threadnetwork::stats_write(threadnetwork::THREADNETWORK_DEVICE_INFO_REPORTED, otHostVersion.c_str(),
                                      otRcpVersion.c_str(), threadVersion, threadDaemonVersion.c_str());
}

void RetrieveAndPushAtoms(otInstance *otInstance)
{
    ThreadnetworkTelemetryDataReported telemetryDataReported;
    ThreadnetworkTopoEntryRepeated     topoEntryRepeated;
    ThreadnetworkDeviceInfoReported    deviceInfoReported;

    otbrLogInfo("Try to push threadnetwork ATOMs.");
    if (RetrieveTelemetryAtom(otInstance, nullptr, telemetryDataReported, topoEntryRepeated, deviceInfoReported) !=
        OTBR_ERROR_NONE)
    {
        otbrLogWarning("Some telemetries are not populated");
    }
    if (PushAtom(telemetryDataReported) <= 0)
    {
        otbrLogWarning("Failed to push ThreadnetworkTelemetryDataReported");
    }
    if (PushAtom(topoEntryRepeated) <= 0)
    {
        otbrLogWarning("Failed to push ThreadnetworkTopoEntryRepeated");
    }
    if (PushAtom(deviceInfoReported) <= 0)
    {
        otbrLogWarning("Failed to push ThreadnetworkDeviceInfoReported");
    }
    otbrLogInfo("Pushed threadnetwork ATOMs.");
}
} // namespace Android
} // namespace otbr
