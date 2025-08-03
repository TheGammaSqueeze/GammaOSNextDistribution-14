// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::convert::{TryFrom, TryInto};

use crate::error::{Error, Result};
use crate::params::uci_packets::{
    AndroidRadarConfigResponse, AppConfigTlv, CapTlv, CoreSetConfigResponse, DeviceConfigTlv,
    GetDeviceInfoResponse, PowerStats, RadarConfigTlv, RawUciMessage, SessionHandle, SessionState,
    SessionUpdateDtTagRangingRoundsResponse, SetAppConfigResponse, StatusCode, UciControlPacket,
};
use crate::uci::error::status_code_to_result;

#[derive(Debug, Clone, PartialEq)]
pub(super) enum UciResponse {
    SetLoggerMode,
    SetNotification,
    OpenHal,
    CloseHal,
    DeviceReset(Result<()>),
    CoreGetDeviceInfo(Result<GetDeviceInfoResponse>),
    CoreGetCapsInfo(Result<Vec<CapTlv>>),
    CoreSetConfig(CoreSetConfigResponse),
    CoreGetConfig(Result<Vec<DeviceConfigTlv>>),
    CoreQueryTimeStamp(Result<u64>),
    SessionInit(Result<Option<SessionHandle>>),
    SessionDeinit(Result<()>),
    SessionSetAppConfig(SetAppConfigResponse),
    SessionGetAppConfig(Result<Vec<AppConfigTlv>>),
    SessionGetCount(Result<u8>),
    SessionGetState(Result<SessionState>),
    SessionUpdateControllerMulticastList(Result<()>),
    SessionUpdateDtTagRangingRounds(Result<SessionUpdateDtTagRangingRoundsResponse>),
    SessionQueryMaxDataSize(Result<u16>),
    SessionStart(Result<()>),
    SessionStop(Result<()>),
    SessionGetRangingCount(Result<usize>),
    AndroidSetCountryCode(Result<()>),
    AndroidGetPowerStats(Result<PowerStats>),
    AndroidSetRadarConfig(AndroidRadarConfigResponse),
    AndroidGetRadarConfig(Result<Vec<RadarConfigTlv>>),
    RawUciCmd(Result<RawUciMessage>),
    SendUciData(Result<()>),
    SessionSetHybridConfig(Result<()>),
    SessionDataTransferPhaseConfig(Result<()>),
}

impl UciResponse {
    pub fn need_retry(&self) -> bool {
        match self {
            Self::SetNotification | Self::OpenHal | Self::CloseHal | Self::SetLoggerMode => false,
            Self::DeviceReset(result) => Self::matches_result_retry(result),
            Self::CoreGetDeviceInfo(result) => Self::matches_result_retry(result),
            Self::CoreGetCapsInfo(result) => Self::matches_result_retry(result),
            Self::CoreGetConfig(result) => Self::matches_result_retry(result),
            Self::CoreQueryTimeStamp(result) => Self::matches_result_retry(result),
            Self::SessionInit(result) => Self::matches_result_retry(result),
            Self::SessionDeinit(result) => Self::matches_result_retry(result),
            Self::SessionGetAppConfig(result) => Self::matches_result_retry(result),
            Self::SessionGetCount(result) => Self::matches_result_retry(result),
            Self::SessionGetState(result) => Self::matches_result_retry(result),
            Self::SessionUpdateControllerMulticastList(result) => {
                Self::matches_result_retry(result)
            }
            Self::SessionUpdateDtTagRangingRounds(result) => Self::matches_result_retry(result),
            Self::SessionStart(result) => Self::matches_result_retry(result),
            Self::SessionStop(result) => Self::matches_result_retry(result),
            Self::SessionGetRangingCount(result) => Self::matches_result_retry(result),
            Self::AndroidSetCountryCode(result) => Self::matches_result_retry(result),
            Self::AndroidGetPowerStats(result) => Self::matches_result_retry(result),
            Self::AndroidGetRadarConfig(result) => Self::matches_result_retry(result),
            Self::AndroidSetRadarConfig(resp) => Self::matches_status_retry(&resp.status),
            Self::RawUciCmd(result) => Self::matches_result_retry(result),
            Self::SessionSetHybridConfig(result) => Self::matches_result_retry(result),
            Self::SessionDataTransferPhaseConfig(result) => Self::matches_result_retry(result),

            Self::CoreSetConfig(resp) => Self::matches_status_retry(&resp.status),
            Self::SessionSetAppConfig(resp) => Self::matches_status_retry(&resp.status),

            Self::SessionQueryMaxDataSize(result) => Self::matches_result_retry(result),
            // TODO(b/273376343): Implement retry logic for Data packet send.
            Self::SendUciData(_result) => false,
        }
    }

    fn matches_result_retry<T>(result: &Result<T>) -> bool {
        matches!(result, Err(Error::CommandRetry))
    }
    fn matches_status_retry(status: &StatusCode) -> bool {
        matches!(status, StatusCode::UciStatusCommandRetry)
    }
}

impl TryFrom<uwb_uci_packets::UciResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::UciResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::UciResponseChild;
        match evt.specialize() {
            UciResponseChild::CoreResponse(evt) => evt.try_into(),
            UciResponseChild::SessionConfigResponse(evt) => evt.try_into(),
            UciResponseChild::SessionControlResponse(evt) => evt.try_into(),
            UciResponseChild::AndroidResponse(evt) => evt.try_into(),
            UciResponseChild::UciVendor_9_Response(evt) => raw_response(evt.into()),
            UciResponseChild::UciVendor_A_Response(evt) => raw_response(evt.into()),
            UciResponseChild::UciVendor_B_Response(evt) => raw_response(evt.into()),
            UciResponseChild::UciVendor_E_Response(evt) => raw_response(evt.into()),
            UciResponseChild::UciVendor_F_Response(evt) => raw_response(evt.into()),
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::CoreResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::CoreResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::CoreResponseChild;
        match evt.specialize() {
            CoreResponseChild::GetDeviceInfoRsp(evt) => Ok(UciResponse::CoreGetDeviceInfo(
                status_code_to_result(evt.get_status()).map(|_| GetDeviceInfoResponse {
                    status: evt.get_status(),
                    uci_version: evt.get_uci_version(),
                    mac_version: evt.get_mac_version(),
                    phy_version: evt.get_phy_version(),
                    uci_test_version: evt.get_uci_test_version(),
                    vendor_spec_info: evt.get_vendor_spec_info().clone(),
                }),
            )),
            CoreResponseChild::GetCapsInfoRsp(evt) => Ok(UciResponse::CoreGetCapsInfo(
                status_code_to_result(evt.get_status()).map(|_| evt.get_tlvs().clone()),
            )),
            CoreResponseChild::DeviceResetRsp(evt) => {
                Ok(UciResponse::DeviceReset(status_code_to_result(evt.get_status())))
            }
            CoreResponseChild::SetConfigRsp(evt) => {
                Ok(UciResponse::CoreSetConfig(CoreSetConfigResponse {
                    status: evt.get_status(),
                    config_status: evt.get_cfg_status().clone(),
                }))
            }

            CoreResponseChild::GetConfigRsp(evt) => Ok(UciResponse::CoreGetConfig(
                status_code_to_result(evt.get_status()).map(|_| evt.get_tlvs().clone()),
            )),
            CoreResponseChild::CoreQueryTimeStampRsp(evt) => Ok(UciResponse::CoreQueryTimeStamp(
                status_code_to_result(evt.get_status()).map(|_| evt.get_timeStamp()),
            )),
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionConfigResponse> for UciResponse {
    type Error = Error;
    fn try_from(
        evt: uwb_uci_packets::SessionConfigResponse,
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionConfigResponseChild;
        match evt.specialize() {
            SessionConfigResponseChild::SessionInitRsp(evt) => {
                Ok(UciResponse::SessionInit(status_code_to_result(evt.get_status()).map(|_| None)))
            }
            SessionConfigResponseChild::SessionInitRsp_V2(evt) => Ok(UciResponse::SessionInit(
                status_code_to_result(evt.get_status()).map(|_| Some(evt.get_session_handle())),
            )),
            SessionConfigResponseChild::SessionDeinitRsp(evt) => {
                Ok(UciResponse::SessionDeinit(status_code_to_result(evt.get_status())))
            }
            SessionConfigResponseChild::SessionGetCountRsp(evt) => {
                Ok(UciResponse::SessionGetCount(
                    status_code_to_result(evt.get_status()).map(|_| evt.get_session_count()),
                ))
            }
            SessionConfigResponseChild::SessionGetStateRsp(evt) => {
                Ok(UciResponse::SessionGetState(
                    status_code_to_result(evt.get_status()).map(|_| evt.get_session_state()),
                ))
            }
            SessionConfigResponseChild::SessionUpdateControllerMulticastListRsp(evt) => {
                Ok(UciResponse::SessionUpdateControllerMulticastList(status_code_to_result(
                    evt.get_status(),
                )))
            }
            SessionConfigResponseChild::SessionUpdateDtTagRangingRoundsRsp(evt) => {
                Ok(UciResponse::SessionUpdateDtTagRangingRounds(Ok(
                    SessionUpdateDtTagRangingRoundsResponse {
                        status: evt.get_status(),
                        ranging_round_indexes: evt.get_ranging_round_indexes().to_vec(),
                    },
                )))
            }
            SessionConfigResponseChild::SessionSetAppConfigRsp(evt) => {
                Ok(UciResponse::SessionSetAppConfig(SetAppConfigResponse {
                    status: evt.get_status(),
                    config_status: evt.get_cfg_status().clone(),
                }))
            }
            SessionConfigResponseChild::SessionGetAppConfigRsp(evt) => {
                Ok(UciResponse::SessionGetAppConfig(
                    status_code_to_result(evt.get_status()).map(|_| {
                        evt.get_tlvs().clone().into_iter().map(|tlv| tlv.into()).collect()
                    }),
                ))
            }
            SessionConfigResponseChild::SessionQueryMaxDataSizeRsp(evt) => {
                Ok(UciResponse::SessionQueryMaxDataSize(
                    status_code_to_result(evt.get_status()).map(|_| evt.get_max_data_size()),
                ))
            }
            SessionConfigResponseChild::SessionSetHybridConfigRsp(evt) => {
                Ok(UciResponse::SessionSetHybridConfig(status_code_to_result(evt.get_status())))
            }
            SessionConfigResponseChild::SessionDataTransferPhaseConfigRsp(evt) => {
                Ok(UciResponse::SessionDataTransferPhaseConfig(status_code_to_result(
                    evt.get_status(),
                )))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionControlResponse> for UciResponse {
    type Error = Error;
    fn try_from(
        evt: uwb_uci_packets::SessionControlResponse,
    ) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::SessionControlResponseChild;
        match evt.specialize() {
            SessionControlResponseChild::SessionStartRsp(evt) => {
                Ok(UciResponse::SessionStart(status_code_to_result(evt.get_status())))
            }
            SessionControlResponseChild::SessionStopRsp(evt) => {
                Ok(UciResponse::SessionStop(status_code_to_result(evt.get_status())))
            }
            SessionControlResponseChild::SessionGetRangingCountRsp(evt) => {
                Ok(UciResponse::SessionGetRangingCount(
                    status_code_to_result(evt.get_status()).map(|_| evt.get_count() as usize),
                ))
            }
            _ => Err(Error::Unknown),
        }
    }
}

impl TryFrom<uwb_uci_packets::AndroidResponse> for UciResponse {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::AndroidResponse) -> std::result::Result<Self, Self::Error> {
        use uwb_uci_packets::AndroidResponseChild;
        match evt.specialize() {
            AndroidResponseChild::AndroidSetCountryCodeRsp(evt) => {
                Ok(UciResponse::AndroidSetCountryCode(status_code_to_result(evt.get_status())))
            }
            AndroidResponseChild::AndroidGetPowerStatsRsp(evt) => {
                Ok(UciResponse::AndroidGetPowerStats(
                    status_code_to_result(evt.get_stats().status).map(|_| evt.get_stats().clone()),
                ))
            }
            AndroidResponseChild::AndroidSetRadarConfigRsp(evt) => {
                Ok(UciResponse::AndroidSetRadarConfig(AndroidRadarConfigResponse {
                    status: evt.get_status(),
                    config_status: evt.get_cfg_status().clone(),
                }))
            }
            AndroidResponseChild::AndroidGetRadarConfigRsp(evt) => {
                Ok(UciResponse::AndroidGetRadarConfig(
                    status_code_to_result(evt.get_status()).map(|_| evt.get_tlvs().clone()),
                ))
            }
            _ => Err(Error::Unknown),
        }
    }
}

fn raw_response(evt: uwb_uci_packets::UciResponse) -> Result<UciResponse> {
    let gid: u32 = evt.get_group_id().into();
    let oid: u32 = evt.get_opcode().into();
    let packet: UciControlPacket = evt.into();
    Ok(UciResponse::RawUciCmd(Ok(RawUciMessage { gid, oid, payload: packet.to_raw_payload() })))
}
