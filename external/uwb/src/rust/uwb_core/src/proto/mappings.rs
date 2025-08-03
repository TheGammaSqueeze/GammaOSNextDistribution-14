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

//! Provide the conversion between the uwb_core's elements and protobuf bindings.

use std::convert::{TryFrom, TryInto};

use zeroize::Zeroize;

use crate::error::{Error, Result};
use crate::params::fira_app_config_params::{
    AoaResultRequest, BprfPhrDataRate, DeviceRole, DeviceType, FiraAppConfigParams,
    FiraAppConfigParamsBuilder, HoppingMode, KeyRotation, MacAddressMode, MacFcsType,
    MultiNodeMode, PreambleDuration, PrfMode, PsduDataRate, RangeDataNtfConfig,
    RangingRoundControl, RangingRoundUsage, RangingTimeStruct, ResultReportConfig, RframeConfig,
    ScheduledMode, StsConfig, StsLength, TxAdaptivePayloadPower, UwbAddress, UwbChannel,
};
use crate::params::uci_packets::{
    Controlee, DeviceState, ExtendedAddressDlTdoaRangingMeasurement,
    ExtendedAddressOwrAoaRangingMeasurement, ExtendedAddressTwoWayRangingMeasurement, PowerStats,
    RangingMeasurementType, ReasonCode, SessionState, SessionType,
    ShortAddressDlTdoaRangingMeasurement, ShortAddressOwrAoaRangingMeasurement,
    ShortAddressTwoWayRangingMeasurement, StatusCode, UpdateMulticastListAction,
};
use crate::params::AppConfigParams;
use crate::proto::bindings::{
    AoaResultRequest as ProtoAoaResultRequest, BprfPhrDataRate as ProtoBprfPhrDataRate,
    Controlee as ProtoControlee, DeviceRole as ProtoDeviceRole, DeviceState as ProtoDeviceState,
    DeviceType as ProtoDeviceType, DlTDoARangingMeasurement as ProtoDlTDoARangingMeasurement,
    FiraAppConfigParams as ProtoFiraAppConfigParams, HoppingMode as ProtoHoppingMode,
    KeyRotation as ProtoKeyRotation, MacAddressMode as ProtoMacAddressMode,
    MacFcsType as ProtoMacFcsType, MultiNodeMode as ProtoMultiNodeMode,
    OwrAoaRangingMeasurement as ProtoOwrAoaRangingMeasurement, PowerStats as ProtoPowerStats,
    PreambleDuration as ProtoPreambleDuration, PrfMode as ProtoPrfMode,
    PsduDataRate as ProtoPsduDataRate, RangeDataNtfConfig as ProtoRangeDataNtfConfig,
    RangingMeasurementType as ProtoRangingMeasurementType,
    RangingRoundControl as ProtoRangingRoundControl, RangingRoundUsage as ProtoRangingRoundUsage,
    RangingTimeStruct as ProtoRangingTimeStruct, ReasonCode as ProtoReasonCode,
    ResultReportConfig as ProtoResultReportConfig, RframeConfig as ProtoRframeConfig,
    ScheduledMode as ProtoScheduledMode, SessionRangeData as ProtoSessionRangeData,
    SessionState as ProtoSessionState, SessionType as ProtoSessionType, Status as ProtoStatus,
    StatusCode as ProtoStatusCode, StsConfig as ProtoStsConfig, StsLength as ProtoStsLength,
    TwoWayRangingMeasurement as ProtoTwoWayRangingMeasurement,
    TxAdaptivePayloadPower as ProtoTxAdaptivePayloadPower, UciLoggerMode as ProtoUciLoggerMode,
    UpdateMulticastListAction as ProtoUpdateMulticastListAction, UwbChannel as ProtoUwbChannel,
};
use crate::uci::notification::{RangingMeasurements, SessionRangeData};
use crate::uci::uci_logger::UciLoggerMode;
use protobuf::{EnumOrUnknown, MessageField};

/// Generate the conversion functions between 2 enum types, which field is 1-to-1 mapping.
///
/// Example:
/// ```
/// enum EnumA {
///     Value1,
///     Value2,
/// }
/// enum EnumB {
///     Foo,
///     Bar,
/// }
/// // This macro generates `From<EnumA> for EnumB` and `From<EnumB> for EnumA`.
/// uwb_core::enum_mapping! {
///     EnumA => EnumB,
///     Value1 => Foo,
///     Value2 => Bar,
/// }
/// ```
#[macro_export]
macro_rules! enum_mapping {
    ( $enum_a:ty => $enum_b:ty, $( $field_a:ident => $field_b:ident, )+ ) => {
        impl From<$enum_a> for $enum_b {
            fn from(item: $enum_a) -> $enum_b {
                match item {
                    $(
                        <$enum_a>::$field_a => <$enum_b>::$field_b,
                    )*
                }
            }
        }
        impl From<$enum_b> for $enum_a {
            fn from(item: $enum_b) -> $enum_a {
                match item {
                    $(
                        <$enum_b>::$field_b => <$enum_a>::$field_a,
                    )*
                }
            }
        }
    };
}

impl From<ProtoStatusCode> for StatusCode {
    fn from(item: ProtoStatusCode) -> Self {
        match item {
            ProtoStatusCode::UCI_STATUS_OK => StatusCode::UciStatusOk,
            ProtoStatusCode::UCI_STATUS_REJECTED => StatusCode::UciStatusRejected,
            ProtoStatusCode::UCI_STATUS_FAILED => StatusCode::UciStatusFailed,
            ProtoStatusCode::UCI_STATUS_SYNTAX_ERROR => StatusCode::UciStatusSyntaxError,
            ProtoStatusCode::UCI_STATUS_INVALID_PARAM => StatusCode::UciStatusInvalidParam,
            ProtoStatusCode::UCI_STATUS_INVALID_RANGE => StatusCode::UciStatusInvalidRange,
            ProtoStatusCode::UCI_STATUS_INVALID_MSG_SIZE => StatusCode::UciStatusInvalidMsgSize,
            ProtoStatusCode::UCI_STATUS_UNKNOWN_GID => StatusCode::UciStatusUnknownGid,
            ProtoStatusCode::UCI_STATUS_UNKNOWN_OID => StatusCode::UciStatusUnknownOid,
            ProtoStatusCode::UCI_STATUS_READ_ONLY => StatusCode::UciStatusReadOnly,
            ProtoStatusCode::UCI_STATUS_COMMAND_RETRY => StatusCode::UciStatusCommandRetry,
            ProtoStatusCode::UCI_STATUS_UNKNOWN => StatusCode::UciStatusUnknown,
            ProtoStatusCode::UCI_STATUS_SESSION_NOT_EXIST => StatusCode::UciStatusSessionNotExist,
            ProtoStatusCode::UCI_STATUS_SESSION_DUPLICATE => StatusCode::UciStatusSessionDuplicate,
            ProtoStatusCode::UCI_STATUS_SESSION_ACTIVE => StatusCode::UciStatusSessionActive,
            ProtoStatusCode::UCI_STATUS_MAX_SESSIONS_EXCEEDED => {
                StatusCode::UciStatusMaxSessionsExceeded
            }
            ProtoStatusCode::UCI_STATUS_SESSION_NOT_CONFIGURED => {
                StatusCode::UciStatusSessionNotConfigured
            }
            ProtoStatusCode::UCI_STATUS_ACTIVE_SESSIONS_ONGOING => {
                StatusCode::UciStatusActiveSessionsOngoing
            }
            ProtoStatusCode::UCI_STATUS_MULTICAST_LIST_FULL => {
                StatusCode::UciStatusMulticastListFull
            }
            ProtoStatusCode::UCI_STATUS_ADDRESS_NOT_FOUND => StatusCode::UciStatusAddressNotFound,
            ProtoStatusCode::UCI_STATUS_ADDRESS_ALREADY_PRESENT => {
                StatusCode::UciStatusAddressAlreadyPresent
            }
            ProtoStatusCode::UCI_STATUS_OK_NEGATIVE_DISTANCE_REPORT => {
                StatusCode::UciStatusOkNegativeDistanceReport
            }
            ProtoStatusCode::UCI_STATUS_RANGING_TX_FAILED => StatusCode::UciStatusRangingTxFailed,
            ProtoStatusCode::UCI_STATUS_RANGING_RX_TIMEOUT => {
                StatusCode::UciStatusRangingRxTimeout
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_DEC_FAILED => {
                StatusCode::UciStatusRangingRxPhyDecFailed
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_TOA_FAILED => {
                StatusCode::UciStatusRangingRxPhyToaFailed
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_STS_FAILED => {
                StatusCode::UciStatusRangingRxPhyStsFailed
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_DEC_FAILED => {
                StatusCode::UciStatusRangingRxMacDecFailed
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_IE_DEC_FAILED => {
                StatusCode::UciStatusRangingRxMacIeDecFailed
            }
            ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_IE_MISSING => {
                StatusCode::UciStatusRangingRxMacIeMissing
            }
            ProtoStatusCode::UCI_STATUS_ERROR_ROUND_INDEX_NOT_ACTIVATED => {
                StatusCode::UciStatusErrorRoundIndexNotActivated
            }
            ProtoStatusCode::UCI_STATUS_ERROR_NUMBER_OF_ACTIVE_RANGING_ROUNDS_EXCEEDED => {
                    StatusCode::UciStatusErrorNumberOfActiveRangingRoundsExceeded
            }
            ProtoStatusCode::UCI_STATUS_ERROR_DL_TDOA_DEVICE_ADDRESS_NOT_MATCHING_IN_REPLY_TIME_LIST =>
                    StatusCode::UciStatusErrorDlTdoaDeviceAddressNotMatchingInReplyTimeList,
            ProtoStatusCode::UCI_STATUS_DATA_MAX_TX_PSDU_SIZE_EXCEEDED => {
                StatusCode::UciStatusDataMaxTxPsduSizeExceeded
            }
            ProtoStatusCode::UCI_STATUS_DATA_RX_CRC_ERROR => StatusCode::UciStatusDataRxCrcError,
            ProtoStatusCode::UCI_STATUS_ERROR_CCC_SE_BUSY => StatusCode::UciStatusErrorCccSeBusy,
            ProtoStatusCode::UCI_STATUS_ERROR_CCC_LIFECYCLE => {
                StatusCode::UciStatusErrorCccLifecycle
            }
            ProtoStatusCode::UCI_STATUS_ERROR_STOPPED_DUE_TO_OTHER_SESSION_CONFLICT => {
                StatusCode::UciStatusErrorStoppedDueToOtherSessionConflict
            }
            ProtoStatusCode::UCI_STATUS_REGULATION_UWB_OFF => StatusCode::UciStatusRegulationUwbOff,
            _ =>  StatusCode::VendorSpecificStatusCode2,
        }
    }
}

impl From<StatusCode> for ProtoStatusCode {
    fn from(item: StatusCode) -> Self {
        match item {
            StatusCode::UciStatusOk => ProtoStatusCode::UCI_STATUS_OK,
            StatusCode::UciStatusRejected => ProtoStatusCode::UCI_STATUS_REJECTED,
            StatusCode::UciStatusFailed => ProtoStatusCode::UCI_STATUS_FAILED,
            StatusCode::UciStatusSyntaxError => ProtoStatusCode::UCI_STATUS_SYNTAX_ERROR,
            StatusCode::UciStatusInvalidParam => ProtoStatusCode::UCI_STATUS_INVALID_PARAM,
            StatusCode::UciStatusInvalidRange => ProtoStatusCode::UCI_STATUS_INVALID_RANGE,
            StatusCode::UciStatusInvalidMsgSize => ProtoStatusCode::UCI_STATUS_INVALID_MSG_SIZE,
            StatusCode::UciStatusUnknownGid => ProtoStatusCode::UCI_STATUS_UNKNOWN_GID,
            StatusCode::UciStatusUnknownOid => ProtoStatusCode::UCI_STATUS_UNKNOWN_OID,
            StatusCode::UciStatusReadOnly => ProtoStatusCode::UCI_STATUS_READ_ONLY,
            StatusCode::UciStatusCommandRetry => ProtoStatusCode::UCI_STATUS_COMMAND_RETRY,
            StatusCode::UciStatusUnknown => ProtoStatusCode::UCI_STATUS_UNKNOWN,
            StatusCode::UciStatusSessionNotExist => ProtoStatusCode::UCI_STATUS_SESSION_NOT_EXIST,
            StatusCode::UciStatusSessionDuplicate => ProtoStatusCode::UCI_STATUS_SESSION_DUPLICATE,
            StatusCode::UciStatusSessionActive => ProtoStatusCode::UCI_STATUS_SESSION_ACTIVE,
            StatusCode::UciStatusMaxSessionsExceeded => {
                ProtoStatusCode::UCI_STATUS_MAX_SESSIONS_EXCEEDED
            }
            StatusCode::UciStatusSessionNotConfigured => {
                ProtoStatusCode::UCI_STATUS_SESSION_NOT_CONFIGURED
            }
            StatusCode::UciStatusActiveSessionsOngoing => {
                ProtoStatusCode::UCI_STATUS_ACTIVE_SESSIONS_ONGOING
            }
            StatusCode::UciStatusMulticastListFull => {
                ProtoStatusCode::UCI_STATUS_MULTICAST_LIST_FULL
            }
            StatusCode::UciStatusAddressNotFound => {
                ProtoStatusCode::UCI_STATUS_ADDRESS_NOT_FOUND
            }
            StatusCode::UciStatusAddressAlreadyPresent => {
                ProtoStatusCode::UCI_STATUS_ADDRESS_ALREADY_PRESENT
            }
            StatusCode::UciStatusOkNegativeDistanceReport => {
                ProtoStatusCode::UCI_STATUS_OK_NEGATIVE_DISTANCE_REPORT
            }
            StatusCode::UciStatusRangingTxFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_TX_FAILED
            }
            StatusCode::UciStatusRangingRxTimeout => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_TIMEOUT
            }
            StatusCode::UciStatusRangingRxPhyDecFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_DEC_FAILED
            }
            StatusCode::UciStatusRangingRxPhyToaFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_TOA_FAILED
            }
            StatusCode::UciStatusRangingRxPhyStsFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_PHY_STS_FAILED
            }
            StatusCode::UciStatusRangingRxMacDecFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_DEC_FAILED
            }
            StatusCode::UciStatusRangingRxMacIeDecFailed => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_IE_DEC_FAILED
            }
            StatusCode::UciStatusRangingRxMacIeMissing => {
                ProtoStatusCode::UCI_STATUS_RANGING_RX_MAC_IE_MISSING
            }
            StatusCode::UciStatusErrorRoundIndexNotActivated => {
                ProtoStatusCode::UCI_STATUS_ERROR_ROUND_INDEX_NOT_ACTIVATED
            }
            StatusCode::UciStatusErrorNumberOfActiveRangingRoundsExceeded => {
                ProtoStatusCode::UCI_STATUS_ERROR_NUMBER_OF_ACTIVE_RANGING_ROUNDS_EXCEEDED
            }
            StatusCode::UciStatusErrorDlTdoaDeviceAddressNotMatchingInReplyTimeList => {
                ProtoStatusCode::UCI_STATUS_ERROR_DL_TDOA_DEVICE_ADDRESS_NOT_MATCHING_IN_REPLY_TIME_LIST
            }
            StatusCode::UciStatusDataMaxTxPsduSizeExceeded => {
                ProtoStatusCode::UCI_STATUS_DATA_MAX_TX_PSDU_SIZE_EXCEEDED
            }
            StatusCode::UciStatusDataRxCrcError => {
                ProtoStatusCode::UCI_STATUS_DATA_RX_CRC_ERROR
            }
            StatusCode::UciStatusErrorCccSeBusy => {
                ProtoStatusCode::UCI_STATUS_ERROR_CCC_SE_BUSY
            }
            StatusCode::UciStatusErrorCccLifecycle => {
                ProtoStatusCode::UCI_STATUS_ERROR_CCC_LIFECYCLE
            }
            StatusCode::UciStatusErrorStoppedDueToOtherSessionConflict => {
                ProtoStatusCode::UCI_STATUS_ERROR_STOPPED_DUE_TO_OTHER_SESSION_CONFLICT
            }
            StatusCode::UciStatusRegulationUwbOff => {
                ProtoStatusCode::UCI_STATUS_REGULATION_UWB_OFF
            }
            _ => ProtoStatusCode::UCI_STATUS_RFU_OR_VENDOR_SPECIFIC,
        }
    }
}

enum_mapping! {
    ProtoDeviceState => DeviceState,
    DEVICE_STATE_READY => DeviceStateReady,
    DEVICE_STATE_ACTIVE => DeviceStateActive,
    DEVICE_STATE_ERROR => DeviceStateError,
}

enum_mapping! {
    ProtoSessionState => SessionState,
    INIT => SessionStateInit,
    DEINIT => SessionStateDeinit,
    ACTIVE => SessionStateActive,
    IDLE => SessionStateIdle,
}

impl From<ProtoReasonCode> for ReasonCode {
    fn from(item: ProtoReasonCode) -> Self {
        match item {
            ProtoReasonCode::STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS => {
                ReasonCode::StateChangeWithSessionManagementCommands
            }
            ProtoReasonCode::MAX_RANGING_ROUND_RETRY_COUNT_REACHED => {
                ReasonCode::MaxRangingRoundRetryCountReached
            }
            ProtoReasonCode::MAX_NUMBER_OF_MEASUREMENTS_REACHED => {
                ReasonCode::MaxNumberOfMeasurementsReached
            }
            ProtoReasonCode::SESSION_SUSPENDED_DUE_TO_INBAND_SIGNAL => {
                ReasonCode::SessionSuspendedDueToInbandSignal
            }
            ProtoReasonCode::SESSION_RESUMED_DUE_TO_INBAND_SIGNAL => {
                ReasonCode::SessionResumedDueToInbandSignal
            }
            ProtoReasonCode::SESSION_STOPPED_DUE_TO_INBAND_SIGNAL => {
                ReasonCode::SessionStoppedDueToInbandSignal
            }
            ProtoReasonCode::ERROR_INVALID_UL_TDOA_RANDOM_WINDOW => {
                ReasonCode::ErrorInvalidUlTdoaRandomWindow
            }
            ProtoReasonCode::ERROR_MIN_RFRAMES_PER_RR_NOT_SUPPORTED => {
                ReasonCode::ErrorMinRframesPerRrNotSupported
            }
            ProtoReasonCode::ERROR_TX_DELAY_NOT_SUPPORTED => ReasonCode::ErrorTxDelayNotSupported,
            ProtoReasonCode::ERROR_SLOT_LENGTH_NOT_SUPPORTED => {
                ReasonCode::ErrorSlotLengthNotSupported
            }
            ProtoReasonCode::ERROR_INSUFFICIENT_SLOTS_PER_RR => {
                ReasonCode::ErrorInsufficientSlotsPerRr
            }
            ProtoReasonCode::ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED => {
                ReasonCode::ErrorMacAddressModeNotSupported
            }
            ProtoReasonCode::ERROR_INVALID_RANGING_DURATION => {
                ReasonCode::ErrorInvalidRangingDuration
            }
            ProtoReasonCode::ERROR_INVALID_STS_CONFIG => ReasonCode::ErrorInvalidStsConfig,
            ProtoReasonCode::ERROR_INVALID_RFRAME_CONFIG => ReasonCode::ErrorInvalidRframeConfig,
            ProtoReasonCode::ERROR_HUS_NOT_ENOUGH_SLOTS => ReasonCode::ErrorHusNotEnoughSlots,
            ProtoReasonCode::ERROR_HUS_CFP_PHASE_TOO_SHORT => ReasonCode::ErrorHusCfpPhaseTooShort,
            ProtoReasonCode::ERROR_HUS_CAP_PHASE_TOO_SHORT => ReasonCode::ErrorHusCapPhaseTooShort,
            ProtoReasonCode::ERROR_HUS_OTHERS => ReasonCode::ErrorHusOthers,
            ProtoReasonCode::ERROR_STATUS_SESSION_KEY_NOT_FOUND => {
                ReasonCode::ErrorStatusSessionKeyNotFound
            }
            ProtoReasonCode::ERROR_STATUS_SUB_SESSION_KEY_NOT_FOUND => {
                ReasonCode::ErrorStatusSubSessionKeyNotFound
            }
            ProtoReasonCode::ERROR_INVALID_PREAMBLE_CODE_INDEX => {
                ReasonCode::ErrorInvalidPreambleCodeIndex
            }
            ProtoReasonCode::ERROR_INVALID_SFD_ID => ReasonCode::ErrorInvalidSfdId,
            ProtoReasonCode::ERROR_INVALID_PSDU_DATA_RATE => ReasonCode::ErrorInvalidPsduDataRate,
            ProtoReasonCode::ERROR_INVALID_PHR_DATA_RATE => ReasonCode::ErrorInvalidPhrDataRate,
            ProtoReasonCode::ERROR_INVALID_PREAMBLE_DURATION => {
                ReasonCode::ErrorInvalidPreambleDuration
            }
            ProtoReasonCode::ERROR_INVALID_STS_LENGTH => ReasonCode::ErrorInvalidStsLength,
            ProtoReasonCode::ERROR_INVALID_NUM_OF_STS_SEGMENTS => {
                ReasonCode::ErrorInvalidNumOfStsSegments
            }
            ProtoReasonCode::ERROR_INVALID_NUM_OF_CONTROLEES => {
                ReasonCode::ErrorInvalidNumOfControlees
            }
            ProtoReasonCode::ERROR_MAX_RANGING_REPLY_TIME_EXCEEDED => {
                ReasonCode::ErrorMaxRangingReplyTimeExceeded
            }
            ProtoReasonCode::ERROR_INVALID_DST_ADDRESS_LIST => {
                ReasonCode::ErrorInvalidDstAddressList
            }
            ProtoReasonCode::ERROR_INVALID_OR_NOT_FOUND_SUB_SESSION_ID => {
                ReasonCode::ErrorInvalidOrNotFoundSubSessionId
            }
            ProtoReasonCode::ERROR_INVALID_RESULT_REPORT_CONFIG => {
                ReasonCode::ErrorInvalidResultReportConfig
            }
            ProtoReasonCode::ERROR_INVALID_RANGING_ROUND_USAGE => {
                ReasonCode::ErrorInvalidRangingRoundUsage
            }
            ProtoReasonCode::ERROR_INVALID_MULTI_NODE_MODE => ReasonCode::ErrorInvalidMultiNodeMode,
            ProtoReasonCode::ERROR_RDS_FETCH_FAILURE => ReasonCode::ErrorRdsFetchFailure,
            ProtoReasonCode::ERROR_REF_UWB_SESSION_DOES_NOT_EXIST => {
                ReasonCode::ErrorRefUwbSessionDoesNotExist
            }
            ProtoReasonCode::ERROR_REF_UWB_SESSION_RANGING_DURATION_MISMATCH => {
                ReasonCode::ErrorRefUwbSessionRangingDurationMismatch
            }
            ProtoReasonCode::ERROR_REF_UWB_SESSION_INVALID_OFFSET_TIME => {
                ReasonCode::ErrorRefUwbSessionInvalidOffsetTime
            }
            ProtoReasonCode::ERROR_REF_UWB_SESSION_LOST => ReasonCode::ErrorRefUwbSessionLost,
            ProtoReasonCode::ERROR_INVALID_CHANNEL_WITH_AOA => {
                ReasonCode::ErrorInvalidChannelWithAoa
            }
            ProtoReasonCode::ERROR_STOPPED_DUE_TO_OTHER_SESSION_CONFLICT => {
                ReasonCode::ErrorStoppedDueToOtherSessionConflict
            }
            _ => ReasonCode::VendorSpecificReasonCode2,
        }
    }
}

impl From<ReasonCode> for ProtoReasonCode {
    fn from(item: ReasonCode) -> Self {
        match item {
            ReasonCode::StateChangeWithSessionManagementCommands => {
                ProtoReasonCode::STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS
            }
            ReasonCode::MaxRangingRoundRetryCountReached => {
                ProtoReasonCode::MAX_RANGING_ROUND_RETRY_COUNT_REACHED
            }
            ReasonCode::MaxNumberOfMeasurementsReached => {
                ProtoReasonCode::MAX_NUMBER_OF_MEASUREMENTS_REACHED
            }
            ReasonCode::SessionSuspendedDueToInbandSignal => {
                ProtoReasonCode::SESSION_SUSPENDED_DUE_TO_INBAND_SIGNAL
            }
            ReasonCode::SessionResumedDueToInbandSignal => {
                ProtoReasonCode::SESSION_RESUMED_DUE_TO_INBAND_SIGNAL
            }
            ReasonCode::SessionStoppedDueToInbandSignal => {
                ProtoReasonCode::SESSION_STOPPED_DUE_TO_INBAND_SIGNAL
            }
            ReasonCode::ErrorInvalidUlTdoaRandomWindow => {
                ProtoReasonCode::ERROR_INVALID_UL_TDOA_RANDOM_WINDOW
            }
            ReasonCode::ErrorMinRframesPerRrNotSupported => {
                ProtoReasonCode::ERROR_MIN_RFRAMES_PER_RR_NOT_SUPPORTED
            }
            ReasonCode::ErrorTxDelayNotSupported => ProtoReasonCode::ERROR_TX_DELAY_NOT_SUPPORTED,
            ReasonCode::ErrorSlotLengthNotSupported => {
                ProtoReasonCode::ERROR_SLOT_LENGTH_NOT_SUPPORTED
            }
            ReasonCode::ErrorInsufficientSlotsPerRr => {
                ProtoReasonCode::ERROR_INSUFFICIENT_SLOTS_PER_RR
            }
            ReasonCode::ErrorMacAddressModeNotSupported => {
                ProtoReasonCode::ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED
            }
            ReasonCode::ErrorInvalidRangingDuration => {
                ProtoReasonCode::ERROR_INVALID_RANGING_DURATION
            }
            ReasonCode::ErrorInvalidStsConfig => ProtoReasonCode::ERROR_INVALID_STS_CONFIG,
            ReasonCode::ErrorInvalidRframeConfig => ProtoReasonCode::ERROR_INVALID_RFRAME_CONFIG,
            ReasonCode::ErrorHusNotEnoughSlots => ProtoReasonCode::ERROR_HUS_NOT_ENOUGH_SLOTS,
            ReasonCode::ErrorHusCfpPhaseTooShort => ProtoReasonCode::ERROR_HUS_CFP_PHASE_TOO_SHORT,
            ReasonCode::ErrorHusCapPhaseTooShort => ProtoReasonCode::ERROR_HUS_CAP_PHASE_TOO_SHORT,
            ReasonCode::ErrorHusOthers => ProtoReasonCode::ERROR_HUS_OTHERS,
            ReasonCode::ErrorStatusSessionKeyNotFound => {
                ProtoReasonCode::ERROR_STATUS_SESSION_KEY_NOT_FOUND
            }
            ReasonCode::ErrorStatusSubSessionKeyNotFound => {
                ProtoReasonCode::ERROR_STATUS_SUB_SESSION_KEY_NOT_FOUND
            }
            ReasonCode::ErrorInvalidPreambleCodeIndex => {
                ProtoReasonCode::ERROR_INVALID_PREAMBLE_CODE_INDEX
            }
            ReasonCode::ErrorInvalidSfdId => ProtoReasonCode::ERROR_INVALID_SFD_ID,
            ReasonCode::ErrorInvalidPsduDataRate => ProtoReasonCode::ERROR_INVALID_PSDU_DATA_RATE,
            ReasonCode::ErrorInvalidPhrDataRate => ProtoReasonCode::ERROR_INVALID_PHR_DATA_RATE,
            ReasonCode::ErrorInvalidPreambleDuration => {
                ProtoReasonCode::ERROR_INVALID_PREAMBLE_DURATION
            }
            ReasonCode::ErrorInvalidStsLength => ProtoReasonCode::ERROR_INVALID_STS_LENGTH,
            ReasonCode::ErrorInvalidNumOfStsSegments => {
                ProtoReasonCode::ERROR_INVALID_NUM_OF_STS_SEGMENTS
            }
            ReasonCode::ErrorInvalidNumOfControlees => {
                ProtoReasonCode::ERROR_INVALID_NUM_OF_CONTROLEES
            }
            ReasonCode::ErrorMaxRangingReplyTimeExceeded => {
                ProtoReasonCode::ERROR_MAX_RANGING_REPLY_TIME_EXCEEDED
            }
            ReasonCode::ErrorInvalidDstAddressList => {
                ProtoReasonCode::ERROR_INVALID_DST_ADDRESS_LIST
            }
            ReasonCode::ErrorInvalidOrNotFoundSubSessionId => {
                ProtoReasonCode::ERROR_INVALID_OR_NOT_FOUND_SUB_SESSION_ID
            }
            ReasonCode::ErrorInvalidResultReportConfig => {
                ProtoReasonCode::ERROR_INVALID_RESULT_REPORT_CONFIG
            }
            ReasonCode::ErrorInvalidRangingRoundUsage => {
                ProtoReasonCode::ERROR_INVALID_RANGING_ROUND_USAGE
            }
            ReasonCode::ErrorInvalidMultiNodeMode => ProtoReasonCode::ERROR_INVALID_MULTI_NODE_MODE,
            ReasonCode::ErrorRdsFetchFailure => ProtoReasonCode::ERROR_RDS_FETCH_FAILURE,
            ReasonCode::ErrorRefUwbSessionDoesNotExist => {
                ProtoReasonCode::ERROR_REF_UWB_SESSION_DOES_NOT_EXIST
            }
            ReasonCode::ErrorRefUwbSessionRangingDurationMismatch => {
                ProtoReasonCode::ERROR_REF_UWB_SESSION_RANGING_DURATION_MISMATCH
            }
            ReasonCode::ErrorRefUwbSessionInvalidOffsetTime => {
                ProtoReasonCode::ERROR_REF_UWB_SESSION_INVALID_OFFSET_TIME
            }
            ReasonCode::ErrorRefUwbSessionLost => ProtoReasonCode::ERROR_REF_UWB_SESSION_LOST,
            ReasonCode::ErrorInvalidChannelWithAoa => {
                ProtoReasonCode::ERROR_INVALID_CHANNEL_WITH_AOA
            }
            ReasonCode::ErrorStoppedDueToOtherSessionConflict => {
                ProtoReasonCode::ERROR_STOPPED_DUE_TO_OTHER_SESSION_CONFLICT
            }
            ReasonCode::ErrorDtAnchorRangingRoundsNotConfigured => {
                ProtoReasonCode::ERROR_DT_ANCHOR_RANGING_ROUNDS_NOT_CONFIGURED
            }
            ReasonCode::ErrorDtTagRangingRoundsNotConfigured => {
                ProtoReasonCode::ERROR_DT_TAG_RANGING_ROUNDS_NOT_CONFIGURED
            }
            _ => ProtoReasonCode::ERROR_RFU_OR_VENDOR_SPECIFIC,
        }
    }
}

enum_mapping! {
    ProtoUciLoggerMode => UciLoggerMode,
    UCI_LOGGER_MODE_DISABLED => Disabled,
    UCI_LOGGER_MODE_UNFILTERED => Unfiltered,
    UCI_LOGGER_MODE_FILTERED => Filtered,
}

enum_mapping! {
    ProtoRangingMeasurementType => RangingMeasurementType,
    ONE_WAY => OneWay,
    TWO_WAY => TwoWay,
    DL_TDOA => DlTdoa,
    OWR_AOA => OwrAoa,
}

enum_mapping! {
    ProtoSessionType => SessionType,
    FIRA_RANGING_SESSION => FiraRangingSession,
    FIRA_DATA_TRANSFER => FiraDataTransferSession,
    FIRA_RANGING_AND_IN_BAND_DATA_SESSION => FiraRangingAndInBandDataSession,
    FIRA_RANGING_ONLY_PHASE => FiraRangingOnlyPhase,
    FIRA_IN_BAND_DATA_PHASE => FiraInBandDataPhase,
    FIRA_RANGING_WITH_DATA_PHASE => FiraRangingWithDataPhase,
    CCC => Ccc,
    RADAR_SESSION => RadarSession,
    ALIRO => Aliro,
    DEVICE_TEST_MODE => DeviceTestMode,
}

enum_mapping! {
    ProtoDeviceType => DeviceType,
    CONTROLEE => Controlee,
    CONTROLLER => Controller,
}

enum_mapping! {
    ProtoRangingRoundUsage => RangingRoundUsage,
    SS_TWR => SsTwr,
    DS_TWR => DsTwr,
    SS_TWR_NON => SsTwrNon,
    DS_TWR_NON => DsTwrNon,
}

enum_mapping! {
    ProtoStsConfig => StsConfig,
    STATIC => Static,
    DYNAMIC => Dynamic,
    DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY => DynamicForControleeIndividualKey,
}

enum_mapping! {
    ProtoMultiNodeMode => MultiNodeMode,
    UNICAST => Unicast,
    ONE_TO_MANY => OneToMany,
    MANY_TO_MANY => ManyToMany,
}

enum_mapping! {
    ProtoUwbChannel => UwbChannel,
    CHANNEL_5 => Channel5,
    CHANNEL_6 => Channel6,
    CHANNEL_8 => Channel8,
    CHANNEL_9 => Channel9,
    CHANNEL_10 => Channel10,
    CHANNEL_12 => Channel12,
    CHANNEL_13 => Channel13,
    CHANNEL_14 => Channel14,
}

enum_mapping! {
    ProtoMacFcsType => MacFcsType,
    CRC_16 => Crc16,
    CRC_32 => Crc32,
}

enum_mapping! {
    ProtoAoaResultRequest => AoaResultRequest,
    NO_AOA_REPORT => NoAoaReport,
    REQ_AOA_RESULTS => ReqAoaResults,
    REQ_AOA_RESULTS_AZIMUTH_ONLY => ReqAoaResultsAzimuthOnly,
    REQ_AOA_RESULTS_ELEVATION_ONLY => ReqAoaResultsElevationOnly,
    REQ_AOA_RESULTS_INTERLEAVED => ReqAoaResultsInterleaved,
}

enum_mapping! {
    ProtoRangeDataNtfConfig => RangeDataNtfConfig,
    RANGE_DATA_NTF_CONFIG_DISABLE => Disable,
    RANGE_DATA_NTF_CONFIG_ENABLE => Enable,
    RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY => EnableProximity,
}

enum_mapping! {
    ProtoDeviceRole => DeviceRole,
    RESPONDER => Responder,
    INITIATOR => Initiator,
}

enum_mapping! {
    ProtoRframeConfig => RframeConfig,
    SP0 => SP0,
    SP1 => SP1,
    SP3 => SP3,
}

enum_mapping! {
    ProtoPsduDataRate => PsduDataRate,
    RATE_6M_81 => Rate6m81,
    RATE_7M_80 => Rate7m80,
    RATE_27M_2 => Rate27m2,
    RATE_31M_2 => Rate31m2,
    RATE_850K => Rate850k,
}

enum_mapping! {
    ProtoPreambleDuration => PreambleDuration,
    T32_SYMBOLS => T32Symbols,
    T64_SYMBOLS => T64Symbols,
}

enum_mapping! {
    ProtoRangingTimeStruct => RangingTimeStruct,
    INTERVAL_BASED_SCHEDULING => IntervalBasedScheduling,
    BLOCK_BASED_SCHEDULING => BlockBasedScheduling,
}

enum_mapping! {
    ProtoTxAdaptivePayloadPower => TxAdaptivePayloadPower,
    TX_ADAPTIVE_PAYLOAD_POWER_DISABLE => Disable,
    TX_ADAPTIVE_PAYLOAD_POWER_ENABLE => Enable,
}

enum_mapping! {
    ProtoPrfMode => PrfMode,
    BPRF => Bprf,
    HPRF_WITH_124_8_MHZ => HprfWith124_8MHz,
    HPRF_WITH_249_6_MHZ => HprfWith249_6MHz,
}

enum_mapping! {
    ProtoScheduledMode => ScheduledMode,
    TIME_SCHEDULED_RANGING => TimeScheduledRanging,
}

enum_mapping! {
    ProtoKeyRotation => KeyRotation,
    KEY_ROTATION_DISABLE => Disable,
    KEY_ROTATION_ENABLE => Enable,
}

enum_mapping! {
    ProtoMacAddressMode => MacAddressMode,
    MAC_ADDRESS_2_BYTES => MacAddress2Bytes,
    MAC_ADDRESS_8_BYTES_2_BYTES_HEADER => MacAddress8Bytes2BytesHeader,
    MAC_ADDRESS_8_BYTES => MacAddress8Bytes,
}

enum_mapping! {
    ProtoHoppingMode => HoppingMode,
    HOPPING_MODE_DISABLE => Disable,
    FIRA_HOPPING_ENABLE => FiraHoppingEnable,
}

enum_mapping! {
    ProtoBprfPhrDataRate => BprfPhrDataRate,
    BPRF_PHR_DATA_RATE_850K => Rate850k,
    BPRF_PHR_DATA_RATE_6M_81 => Rate6m81,
}

enum_mapping! {
    ProtoStsLength => StsLength,
    LENGTH_32 => Length32,
    LENGTH_64 => Length64,
    LENGTH_128 => Length128,
}

enum_mapping! {
    ProtoUpdateMulticastListAction => UpdateMulticastListAction,
    ADD_CONTROLEE => AddControlee,
    REMOVE_CONTROLEE => RemoveControlee,
    ADD_CONTROLEE_WITH_SHORT_SUB_SESSION_KEY => AddControleeWithShortSubSessionKey,
    ADD_CONTROLEE_WITH_LONG_SUB_SESSION_KEY => AddControleeWithLongSubSessionKey,
}

pub enum ProtoRangingMeasurements {
    TwoWay(Vec<ProtoTwoWayRangingMeasurement>),
    OwrAoa(ProtoOwrAoaRangingMeasurement),
    DlTDoa(Vec<ProtoDlTDoARangingMeasurement>),
}

impl<T> From<Result<T>> for ProtoStatus {
    fn from(item: Result<T>) -> Self {
        match item {
            Ok(_) => Self::OK,
            Err(Error::BadParameters) => Self::BAD_PARAMETERS,
            Err(Error::MaxSessionsExceeded) => Self::MAX_SESSIONS_EXCEEDED,
            Err(Error::MaxRrRetryReached) => Self::MAX_RR_RETRY_REACHED,
            Err(Error::ProtocolSpecific) => Self::PROTOCOL_SPECIFIC,
            Err(Error::RemoteRequest) => Self::REMOTE_REQUEST,
            Err(Error::Timeout) => Self::TIMEOUT,
            Err(Error::CommandRetry) => Self::COMMAND_RETRY,
            Err(Error::DuplicatedSessionId) => Self::DUPLICATED_SESSION_ID,
            Err(Error::RegulationUwbOff) => Self::REGULATION_UWB_OFF,
            Err(_) => Self::UNKNOWN,
        }
    }
}

impl From<ShortAddressTwoWayRangingMeasurement> for ProtoTwoWayRangingMeasurement {
    fn from(item: ShortAddressTwoWayRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address.into();
        result.status = EnumOrUnknown::new(item.status.into());
        result.nlos = item.nlos.into();
        result.distance = item.distance.into();
        result.aoa_azimuth = item.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.aoa_azimuth_fom.into();
        result.aoa_elevation = item.aoa_elevation.into();
        result.aoa_elevation_fom = item.aoa_elevation_fom.into();
        result.aoa_destination_azimuth = item.aoa_destination_azimuth.into();
        result.aoa_destination_azimuth_fom = item.aoa_destination_azimuth_fom.into();
        result.aoa_destination_elevation = item.aoa_destination_elevation.into();
        result.aoa_destination_elevation_fom = item.aoa_destination_elevation_fom.into();
        result.slot_index = item.slot_index.into();
        result.rssi = item.rssi.into();
        result
    }
}

impl From<ExtendedAddressTwoWayRangingMeasurement> for ProtoTwoWayRangingMeasurement {
    fn from(item: ExtendedAddressTwoWayRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address;
        result.status = EnumOrUnknown::new(item.status.into());
        result.nlos = item.nlos.into();
        result.distance = item.distance.into();
        result.aoa_azimuth = item.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.aoa_azimuth_fom.into();
        result.aoa_elevation = item.aoa_elevation.into();
        result.aoa_elevation_fom = item.aoa_elevation_fom.into();
        result.aoa_destination_azimuth = item.aoa_destination_azimuth.into();
        result.aoa_destination_azimuth_fom = item.aoa_destination_azimuth_fom.into();
        result.aoa_destination_elevation = item.aoa_destination_elevation.into();
        result.aoa_destination_elevation_fom = item.aoa_destination_elevation_fom.into();
        result.slot_index = item.slot_index.into();
        result.rssi = item.rssi.into();
        result
    }
}

impl From<ShortAddressOwrAoaRangingMeasurement> for ProtoOwrAoaRangingMeasurement {
    fn from(item: ShortAddressOwrAoaRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address.into();
        result.status = EnumOrUnknown::new(item.status.into());
        result.nlos = item.nlos.into();
        result.block_index = item.block_index.into();
        result.frame_sequence_number = item.frame_sequence_number.into();
        result.aoa_azimuth = item.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.aoa_azimuth_fom.into();
        result.aoa_elevation = item.aoa_elevation.into();
        result.aoa_elevation_fom = item.aoa_elevation_fom.into();
        result
    }
}

impl From<ExtendedAddressOwrAoaRangingMeasurement> for ProtoOwrAoaRangingMeasurement {
    fn from(item: ExtendedAddressOwrAoaRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address;
        result.status = EnumOrUnknown::new(item.status.into());
        result.nlos = item.nlos.into();
        result.block_index = item.block_index.into();
        result.frame_sequence_number = item.frame_sequence_number.into();
        result.aoa_azimuth = item.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.aoa_azimuth_fom.into();
        result.aoa_elevation = item.aoa_elevation.into();
        result.aoa_elevation_fom = item.aoa_elevation_fom.into();
        result
    }
}

impl From<ShortAddressDlTdoaRangingMeasurement> for ProtoDlTDoARangingMeasurement {
    fn from(item: ShortAddressDlTdoaRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address.into();
        result.status = EnumOrUnknown::new(
            StatusCode::try_from(item.measurement.status)
                .unwrap_or(StatusCode::UciStatusFailed)
                .into(),
        );
        result.message_control = item.measurement.message_control.into();
        result.block_index = item.measurement.block_index.into();
        result.round_index = item.measurement.round_index.into();
        result.nlos = item.measurement.nlos.into();
        result.aoa_azimuth = item.measurement.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.measurement.aoa_azimuth_fom.into();
        result.aoa_elevation = item.measurement.aoa_elevation.into();
        result.aoa_elevation_fom = item.measurement.aoa_elevation_fom.into();
        result.rssi = item.measurement.rssi.into();
        result.tx_timestamp = item.measurement.tx_timestamp;
        result.rx_timestamp = item.measurement.rx_timestamp;
        result.anchor_cfo = item.measurement.anchor_cfo.into();
        result.cfo = item.measurement.cfo.into();
        result.initiator_reply_time = item.measurement.initiator_reply_time;
        result.responder_reply_time = item.measurement.responder_reply_time;
        result.initiator_responder_tof = item.measurement.initiator_responder_tof.into();
        result.dt_anchor_location = item
            .measurement
            .dt_anchor_location
            .into_iter()
            .map(|val| val as u32)
            .collect::<Vec<u32>>();
        result.ranging_rounds =
            item.measurement.ranging_rounds.into_iter().map(|val| val as u32).collect::<Vec<u32>>();
        result
    }
}

impl From<ExtendedAddressDlTdoaRangingMeasurement> for ProtoDlTDoARangingMeasurement {
    fn from(item: ExtendedAddressDlTdoaRangingMeasurement) -> Self {
        let mut result = Self::new();
        result.mac_address = item.mac_address;
        result.status = EnumOrUnknown::new(
            StatusCode::try_from(item.measurement.status)
                .unwrap_or(StatusCode::UciStatusFailed)
                .into(),
        );
        result.message_control = item.measurement.message_control.into();
        result.block_index = item.measurement.block_index.into();
        result.round_index = item.measurement.round_index.into();
        result.nlos = item.measurement.nlos.into();
        result.aoa_azimuth = item.measurement.aoa_azimuth.into();
        result.aoa_azimuth_fom = item.measurement.aoa_azimuth_fom.into();
        result.aoa_elevation = item.measurement.aoa_elevation.into();
        result.aoa_elevation_fom = item.measurement.aoa_elevation_fom.into();
        result.rssi = item.measurement.rssi.into();
        result.tx_timestamp = item.measurement.tx_timestamp;
        result.rx_timestamp = item.measurement.rx_timestamp;
        result.anchor_cfo = item.measurement.anchor_cfo.into();
        result.cfo = item.measurement.cfo.into();
        result.initiator_reply_time = item.measurement.initiator_reply_time;
        result.responder_reply_time = item.measurement.responder_reply_time;
        result.initiator_responder_tof = item.measurement.initiator_responder_tof.into();
        result.dt_anchor_location = item
            .measurement
            .dt_anchor_location
            .into_iter()
            .map(|val| val as u32)
            .collect::<Vec<u32>>();
        result.ranging_rounds =
            item.measurement.ranging_rounds.into_iter().map(|val| val as u32).collect::<Vec<u32>>();
        result
    }
}

impl From<SessionRangeData> for ProtoSessionRangeData {
    fn from(item: SessionRangeData) -> Self {
        let mut result = Self::new();
        result.sequence_number = item.sequence_number;
        result.session_id = item.session_token;
        result.current_ranging_interval_ms = item.current_ranging_interval_ms;
        result.ranging_measurement_type = EnumOrUnknown::new(item.ranging_measurement_type.into());
        match to_proto_ranging_measurements(item.ranging_measurements) {
            ProtoRangingMeasurements::TwoWay(twoway_measurements) => {
                result.twoway_ranging_measurements = twoway_measurements;
            }
            ProtoRangingMeasurements::OwrAoa(owraoa_measurement) => {
                result.owraoa_ranging_measurement = MessageField::from(Some(owraoa_measurement));
            }
            ProtoRangingMeasurements::DlTDoa(dltdoa_measurements) => {
                result.dltdoa_ranging_measurements = dltdoa_measurements;
            }
        }
        result
    }
}

fn to_proto_ranging_measurements(item: RangingMeasurements) -> ProtoRangingMeasurements {
    match item {
        RangingMeasurements::ShortAddressTwoWay(arr) => {
            ProtoRangingMeasurements::TwoWay(arr.into_iter().map(|item| item.into()).collect())
        }
        RangingMeasurements::ExtendedAddressTwoWay(arr) => {
            ProtoRangingMeasurements::TwoWay(arr.into_iter().map(|item| item.into()).collect())
        }
        RangingMeasurements::ShortAddressOwrAoa(r) => ProtoRangingMeasurements::OwrAoa(r.into()),
        RangingMeasurements::ExtendedAddressOwrAoa(r) => ProtoRangingMeasurements::OwrAoa(r.into()),
        RangingMeasurements::ShortAddressDltdoa(arr) => {
            ProtoRangingMeasurements::DlTDoa(arr.into_iter().map(|item| item.into()).collect())
        }
        RangingMeasurements::ExtendedAddressDltdoa(arr) => {
            ProtoRangingMeasurements::DlTDoa(arr.into_iter().map(|item| item.into()).collect())
        }
    }
}

impl From<ProtoRangingRoundControl> for RangingRoundControl {
    fn from(item: ProtoRangingRoundControl) -> Self {
        Self {
            ranging_result_report_message: item.ranging_result_report_message,
            control_message: item.control_message,
            measurement_report_message: item.measurement_report_message,
        }
    }
}

impl From<RangingRoundControl> for ProtoRangingRoundControl {
    fn from(item: RangingRoundControl) -> Self {
        let mut res = Self::new();
        res.ranging_result_report_message = item.ranging_result_report_message;
        res.control_message = item.control_message;
        res.measurement_report_message = item.measurement_report_message;
        res
    }
}

impl From<ProtoResultReportConfig> for ResultReportConfig {
    fn from(item: ProtoResultReportConfig) -> Self {
        Self {
            tof: item.tof,
            aoa_azimuth: item.aoa_azimuth,
            aoa_elevation: item.aoa_elevation,
            aoa_fom: item.aoa_fom,
        }
    }
}

impl From<ResultReportConfig> for ProtoResultReportConfig {
    fn from(item: ResultReportConfig) -> Self {
        let mut res = Self::new();
        res.tof = item.tof;
        res.aoa_azimuth = item.aoa_azimuth;
        res.aoa_elevation = item.aoa_elevation;
        res.aoa_fom = item.aoa_fom;
        res
    }
}

fn to_uwb_address(bytes: Vec<u8>, mode: ProtoMacAddressMode) -> Option<UwbAddress> {
    match mode {
        ProtoMacAddressMode::MAC_ADDRESS_2_BYTES
        | ProtoMacAddressMode::MAC_ADDRESS_8_BYTES_2_BYTES_HEADER => {
            Some(UwbAddress::Short(bytes.try_into().ok()?))
        }
        ProtoMacAddressMode::MAC_ADDRESS_8_BYTES => {
            Some(UwbAddress::Extended(bytes.try_into().ok()?))
        }
    }
}

impl TryFrom<ProtoControlee> for Controlee {
    type Error = String;
    fn try_from(item: ProtoControlee) -> std::result::Result<Self, Self::Error> {
        Ok(Self {
            short_address: item.short_address.to_ne_bytes()[0..2]
                .try_into()
                .map_err(|_| "Failed to convert short_address")?,
            subsession_id: item.subsession_id,
        })
    }
}

impl From<PowerStats> for ProtoPowerStats {
    fn from(item: PowerStats) -> Self {
        let mut res = Self::new();
        res.status = ProtoStatusCode::from(item.status).into();
        res.idle_time_ms = item.idle_time_ms;
        res.tx_time_ms = item.tx_time_ms;
        res.rx_time_ms = item.rx_time_ms;
        res.total_wake_count = item.total_wake_count;
        res
    }
}

impl From<FiraAppConfigParams> for ProtoFiraAppConfigParams {
    fn from(item: FiraAppConfigParams) -> Self {
        let mut res = Self::new();
        res.device_type = EnumOrUnknown::new((*item.device_type()).into());
        res.ranging_round_usage = ProtoRangingRoundUsage::from(*item.ranging_round_usage()).into();
        res.sts_config = ProtoStsConfig::from(*item.sts_config()).into();
        res.multi_node_mode = ProtoMultiNodeMode::from(*item.multi_node_mode()).into();
        res.channel_number = ProtoUwbChannel::from(*item.channel_number()).into();
        res.device_mac_address = item.device_mac_address().clone().into();
        res.dst_mac_address =
            item.dst_mac_address().clone().into_iter().map(|addr| addr.into()).collect::<Vec<_>>();
        res.slot_duration_rstu = (*item.slot_duration_rstu()).into();
        res.ranging_duration_ms = *item.ranging_duration_ms();
        res.mac_fcs_type = ProtoMacFcsType::from(*item.mac_fcs_type()).into();
        res.ranging_round_control = MessageField::from(Some(ProtoRangingRoundControl::from(
            item.ranging_round_control().clone(),
        )));
        res.aoa_result_request = ProtoAoaResultRequest::from(*item.aoa_result_request()).into();
        res.range_data_ntf_config =
            ProtoRangeDataNtfConfig::from(*item.range_data_ntf_config()).into();
        res.range_data_ntf_proximity_near_cm = (*item.range_data_ntf_proximity_near_cm()).into();
        res.range_data_ntf_proximity_far_cm = (*item.range_data_ntf_proximity_far_cm()).into();
        res.device_role = ProtoDeviceRole::from(*item.device_role()).into();
        res.rframe_config = ProtoRframeConfig::from(*item.rframe_config()).into();
        res.preamble_code_index = (*item.preamble_code_index()).into();
        res.sfd_id = (*item.sfd_id()).into();
        res.psdu_data_rate = ProtoPsduDataRate::from(*item.psdu_data_rate()).into();
        res.preamble_duration = ProtoPreambleDuration::from(*item.preamble_duration()).into();
        res.ranging_time_struct = ProtoRangingTimeStruct::from(*item.ranging_time_struct()).into();
        res.slots_per_rr = (*item.slots_per_rr()).into();
        res.tx_adaptive_payload_power =
            ProtoTxAdaptivePayloadPower::from(*item.tx_adaptive_payload_power()).into();
        res.responder_slot_index = (*item.responder_slot_index()).into();
        res.prf_mode = ProtoPrfMode::from(*item.prf_mode()).into();
        res.scheduled_mode = ProtoScheduledMode::from(*item.scheduled_mode()).into();
        res.key_rotation = ProtoKeyRotation::from(*item.key_rotation()).into();
        res.key_rotation_rate = (*item.key_rotation_rate()).into();
        res.session_priority = (*item.session_priority()).into();
        res.mac_address_mode = ProtoMacAddressMode::from(*item.mac_address_mode()).into();
        res.vendor_id = (*item.vendor_id()).into();
        res.static_sts_iv = (*item.static_sts_iv()).into();
        res.number_of_sts_segments = (*item.number_of_sts_segments()).into();
        res.max_rr_retry = (*item.max_rr_retry()).into();
        res.uwb_initiation_time_ms = *item.uwb_initiation_time_ms();
        res.hopping_mode = ProtoHoppingMode::from(*item.hopping_mode()).into();
        res.block_stride_length = (*item.block_stride_length()).into();
        res.result_report_config = MessageField::from(Some(ProtoResultReportConfig::from(
            item.result_report_config().clone(),
        )));
        res.in_band_termination_attempt_count = (*item.in_band_termination_attempt_count()).into();
        res.sub_session_id = *item.sub_session_id();
        res.bprf_phr_data_rate = ProtoBprfPhrDataRate::from(*item.bprf_phr_data_rate()).into();
        res.max_number_of_measurements = (*item.max_number_of_measurements()).into();
        res.sts_length = ProtoStsLength::from(*item.sts_length()).into();
        res.number_of_range_measurements = (*item.number_of_range_measurements()).into();
        res.number_of_aoa_azimuth_measurements =
            (*item.number_of_aoa_azimuth_measurements()).into();
        res.number_of_aoa_elevation_measurements =
            (*item.number_of_aoa_elevation_measurements()).into();

        res
    }
}

impl TryFrom<ProtoFiraAppConfigParams> for AppConfigParams {
    type Error = String;
    fn try_from(mut item: ProtoFiraAppConfigParams) -> std::result::Result<Self, Self::Error> {
        let device_mac_address = to_uwb_address(
            item.device_mac_address.clone(),
            item.mac_address_mode.enum_value().map_err(|_| "Failed to read mac_address_mode")?,
        )
        .ok_or("Failed to convert device_mac_address")?;
        let mut dst_mac_address = vec![];
        for addr in item.dst_mac_address.clone().into_iter() {
            let addr = to_uwb_address(
                addr,
                item.mac_address_mode
                    .enum_value()
                    .map_err(|_| "Failed to convert mac_address_mode")?,
            )
            .ok_or("Failed to convert dst_mac_address")?;
            dst_mac_address.push(addr);
        }

        let mut builder = FiraAppConfigParamsBuilder::new();
        builder
            .device_type(
                item.device_type.enum_value().map_err(|_| "Failed to convert device_type")?.into(),
            )
            .ranging_round_usage(
                item.ranging_round_usage
                    .enum_value()
                    .map_err(|_| "Failed to convert ranging_round_usage")?
                    .into(),
            )
            .sts_config(
                item.sts_config.enum_value().map_err(|_| "Failed to convert sts_config")?.into(),
            )
            .multi_node_mode(
                item.multi_node_mode
                    .enum_value()
                    .map_err(|_| "Failed to convert multi_node_mode")?
                    .into(),
            )
            .channel_number(
                item.channel_number
                    .enum_value()
                    .map_err(|_| "Failed to convert channel_number")?
                    .into(),
            )
            .device_mac_address(device_mac_address)
            .dst_mac_address(dst_mac_address)
            .slot_duration_rstu(
                item.slot_duration_rstu
                    .try_into()
                    .map_err(|_| "Failed to convert slot_duration_rstu")?,
            )
            .ranging_duration_ms(item.ranging_duration_ms)
            .mac_fcs_type(
                item.mac_fcs_type
                    .enum_value()
                    .map_err(|_| "Failed to convert mac_fcs_type")?
                    .into(),
            )
            .ranging_round_control(
                item.ranging_round_control.take().ok_or("ranging_round_control is empty")?.into(),
            )
            .aoa_result_request(
                item.aoa_result_request
                    .enum_value()
                    .map_err(|_| "Failed to convert aoa_result_request")?
                    .into(),
            )
            .range_data_ntf_config(
                item.range_data_ntf_config
                    .enum_value()
                    .map_err(|_| "Failed to convert range_data_ntf_config")?
                    .into(),
            )
            .range_data_ntf_proximity_near_cm(
                item.range_data_ntf_proximity_near_cm
                    .try_into()
                    .map_err(|_| "Failed to convert range_data_ntf_proximity_near_cm")?,
            )
            .range_data_ntf_proximity_far_cm(
                item.range_data_ntf_proximity_far_cm
                    .try_into()
                    .map_err(|_| "Failed to convert range_data_ntf_proximity_far_cm")?,
            )
            .device_role(
                item.device_role.enum_value().map_err(|_| "Failed to convert device_role")?.into(),
            )
            .rframe_config(
                item.rframe_config
                    .enum_value()
                    .map_err(|_| "Failed to convert rframe_config")?
                    .into(),
            )
            .preamble_code_index(
                item.preamble_code_index
                    .try_into()
                    .map_err(|_| "Failed to convert preamble_code_index")?,
            )
            .sfd_id(item.sfd_id.try_into().map_err(|_| "Failed to convert sfd_id")?)
            .psdu_data_rate(
                item.psdu_data_rate
                    .enum_value()
                    .map_err(|_| "Failed to convert psdu_data_rate")?
                    .into(),
            )
            .preamble_duration(
                item.preamble_duration
                    .enum_value()
                    .map_err(|_| "Failed to convert preamble_duration")?
                    .into(),
            )
            .ranging_time_struct(
                item.ranging_time_struct
                    .enum_value()
                    .map_err(|_| "Failed to convert ranging_time_struct")?
                    .into(),
            )
            .slots_per_rr(
                item.slots_per_rr.try_into().map_err(|_| "Failed to convert slots_per_rr")?,
            )
            .tx_adaptive_payload_power(
                item.tx_adaptive_payload_power
                    .enum_value()
                    .map_err(|_| "Failed to convert tx_adaptive_payload_power")?
                    .into(),
            )
            .responder_slot_index(
                item.responder_slot_index
                    .try_into()
                    .map_err(|_| "Failed to convert responder_slot_index")?,
            )
            .prf_mode(item.prf_mode.enum_value().map_err(|_| "Failed to convert prf_mode")?.into())
            .scheduled_mode(
                item.scheduled_mode
                    .enum_value()
                    .map_err(|_| "Failed to convert scheduled_mode")?
                    .into(),
            )
            .key_rotation(
                item.key_rotation
                    .enum_value()
                    .map_err(|_| "Failed to convert key_rotation")?
                    .into(),
            )
            .key_rotation_rate(
                item.key_rotation_rate
                    .try_into()
                    .map_err(|_| "Failed to convert key_rotation_rate")?,
            )
            .session_priority(
                item.session_priority
                    .try_into()
                    .map_err(|_| "Failed to convert session_priority")?,
            )
            .mac_address_mode(
                item.mac_address_mode
                    .enum_value()
                    .map_err(|_| "Failed to convert mac_address_mode")?
                    .into(),
            )
            .vendor_id(
                item.vendor_id.clone().try_into().map_err(|_| "Failed to convert vendor_id")?,
            )
            .static_sts_iv(
                item.static_sts_iv
                    .clone()
                    .try_into()
                    .map_err(|_| "Failed to convert static_sts_iv")?,
            )
            .number_of_sts_segments(
                item.number_of_sts_segments
                    .try_into()
                    .map_err(|_| "Failed to convert number_of_sts_segments")?,
            )
            .max_rr_retry(
                item.max_rr_retry.try_into().map_err(|_| "Failed to convert max_rr_retry")?,
            )
            .uwb_initiation_time_ms(item.uwb_initiation_time_ms)
            .hopping_mode(item.hopping_mode.unwrap().into())
            .block_stride_length(
                item.block_stride_length
                    .try_into()
                    .map_err(|_| "Failed to convert block_stride_length")?,
            )
            .result_report_config(
                item.result_report_config.take().ok_or("ranging_round_control is empty")?.into(),
            )
            .in_band_termination_attempt_count(
                item.in_band_termination_attempt_count
                    .try_into()
                    .map_err(|_| "Failed to convert in_band_termination_attempt_count")?,
            )
            .sub_session_id(item.sub_session_id)
            .bprf_phr_data_rate(
                item.bprf_phr_data_rate
                    .enum_value()
                    .map_err(|_| "Failed to convert bprf_phr_data_rate")?
                    .into(),
            )
            .max_number_of_measurements(
                item.max_number_of_measurements
                    .try_into()
                    .map_err(|_| "Failed to convert max_number_of_measurements")?,
            )
            .sts_length(
                item.sts_length.enum_value().map_err(|_| "Failed to convert sts_length")?.into(),
            )
            .number_of_range_measurements(
                item.number_of_range_measurements
                    .try_into()
                    .map_err(|_| "Failed to convert number_of_range_measurements")?,
            )
            .number_of_aoa_azimuth_measurements(
                item.number_of_aoa_azimuth_measurements
                    .try_into()
                    .map_err(|_| "Failed to convert number_of_aoa_azimuth_measurements")?,
            )
            .number_of_aoa_elevation_measurements(
                item.number_of_aoa_elevation_measurements
                    .try_into()
                    .map_err(|_| "Failed to convert number_of_aoa_elevation_measurements")?,
            );

        Ok(builder.build().ok_or("Failed to build FiraAppConfigParam from builder")?)
    }
}

impl Drop for ProtoFiraAppConfigParams {
    fn drop(&mut self) {
        // Zero out the sensitive data before releasing memory.
        self.vendor_id.zeroize();
        self.static_sts_iv.zeroize();
        self.sub_session_id.zeroize();
    }
}
