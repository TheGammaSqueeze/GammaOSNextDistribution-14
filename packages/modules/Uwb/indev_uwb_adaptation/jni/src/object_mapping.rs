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

//! This module contains structures and methods for converting
//! Java objects into native rust objects and vice versa.
use jni::objects::{JClass, JObject, JValue};
use jni::sys::{jbyteArray, jint};
use jni::JNIEnv;
use num_traits::FromPrimitive;
use std::num::TryFromIntError;

use uwb_core::params::{
    AoaResultRequest, AppConfigParams, BprfPhrDataRate, CccAppConfigParamsBuilder, CccHoppingMode,
    CccProtocolVersion, CccPulseShapeCombo, CccUwbChannel, CccUwbConfig, ChapsPerSlot, CountryCode,
    DeviceRole, DeviceType, FiraAppConfigParamsBuilder, HoppingMode, KeyRotation, MacAddressMode,
    MacFcsType, MultiNodeMode, PreambleDuration, PrfMode, PsduDataRate, PulseShape,
    RangeDataNtfConfig, RangingRoundUsage, RframeConfig, StsConfig, StsLength,
    TxAdaptivePayloadPower, UwbAddress, UwbChannel,
};
use uwb_core::uci::{RangingMeasurements, SessionRangeData};
use uwb_uci_packets::{
    Controlee, ExtendedAddressTwoWayRangingMeasurement, MacAddressIndicator, PowerStats,
    ShortAddressTwoWayRangingMeasurement, StatusCode,
};

use crate::context::JniContext;
use crate::error::{Error, Result};

/// Wrapper struct for FiraOpenSessionsParams Java class
pub struct FiraOpenSessionParamsJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> FiraOpenSessionParamsJni<'a> {
    pub fn new(env: JNIEnv<'a>, params_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, params_obj) }
    }

    int_field!(device_type, DeviceType, "getDeviceType");
    int_field!(ranging_round_usage, RangingRoundUsage, "getRangingRoundUsage");
    int_field!(sts_config, StsConfig, "getStsConfig");
    int_field!(multi_node_mode, MultiNodeMode, "getMultiNodeMode");
    int_field!(channel_number, UwbChannel, "getChannelNumber");
    int_field!(mac_fcs_type, MacFcsType, "getFcsType");
    int_field!(aoa_result_request, AoaResultRequest, "getAoaResultRequest");
    int_field!(range_data_ntf_config, RangeDataNtfConfig, "getRangeDataNtfConfig");
    int_field!(device_role, DeviceRole, "getDeviceRole");
    int_field!(rframe_config, RframeConfig, "getRframeConfig");
    int_field!(psdu_data_rate, PsduDataRate, "getPsduDataRate");
    int_field!(preamble_duration, PreambleDuration, "getPreambleDuration");
    int_field!(prf_mode, PrfMode, "getPrfMode");
    int_field!(mac_address_mode, MacAddressMode, "getMacAddressMode");
    int_field!(hopping_mode, HoppingMode, "getHoppingMode");
    int_field!(bprf_phr_data_rate, BprfPhrDataRate, "getBprfPhrDataRate");
    int_field!(sts_length, StsLength, "getStsLength");
    int_field!(slot_duration_rstu, u16, "getSlotDurationRstu");
    int_field!(ranging_interval_ms, u32, "getRangingIntervalMs");
    int_field!(range_data_ntf_proximity_near_cm, u16, "getRangeDataNtfProximityNear");
    int_field!(range_data_ntf_proximity_far_cm, u16, "getRangeDataNtfProximityFar");
    int_field!(preamble_code_index, u8, "getPreambleCodeIndex");
    int_field!(sfd_id, u8, "getSfdId");
    int_field!(slots_per_rr, u8, "getSlotsPerRangingRound");
    int_field!(key_rotation_rate, u8, "getKeyRotationRate");
    int_field!(session_priority, u8, "getSessionPriority");
    int_field!(number_of_sts_segments, u8, "getStsSegmentCount");
    int_field!(max_rr_retry, u16, "getMaxRangingRoundRetries");
    int_field!(uwb_initiation_time_ms, u32, "getInitiationTimeMs");
    int_field!(block_stride_length, u8, "getBlockStrideLength");
    int_field!(in_band_termination_attempt_count, u8, "getInBandTerminationAttemptCount");
    int_field!(sub_session_id, u32, "getSubSessionId");
    int_field!(number_of_range_measurements, u8, "getNumOfMsrmtFocusOnRange");
    int_field!(number_of_aoa_azimuth_measurements, u8, "getNumOfMsrmtFocusOnAoaAzimuth");
    int_field!(number_of_aoa_elevation_measurements, u8, "getNumOfMsrmtFocusOnAoaElevation");
    bool_field!(key_rotation, KeyRotation, "isKeyRotationEnabled");
    bool_field!(
        tx_adaptive_payload_power,
        TxAdaptivePayloadPower,
        "isTxAdaptivePayloadPowerEnabled"
    );

    fn device_mac_address(&self) -> Result<UwbAddress> {
        let address_obj =
            self.jni_context.object_getter("getDeviceAddress", "()Landroid/uwb/UwbAddress;")?;

        UwbAddressJni::new(self.jni_context.env, address_obj).try_into()
    }

    fn dest_mac_address(&self) -> Result<Vec<UwbAddress>> {
        let jlist = self.jni_context.list_getter("getDestAddressList")?;

        let mut dest_addresses = vec![];
        let size = jlist.size()? as u32;
        for i in 0..size {
            if let Some(obj) = jlist.get(i as jint)? {
                dest_addresses.push(UwbAddressJni::new(self.jni_context.env, obj).try_into()?);
            }
        }
        Ok(dest_addresses)
    }

    fn vendor_id(&self) -> Result<[u8; 2]> {
        let vendor_id_bytes = self.jni_context.byte_arr_getter("getVendorId")?;
        let len = vendor_id_bytes.len();

        vendor_id_bytes
            .try_into()
            .map_err(|_| Error::Parse(format!("Invalid vendor_id size, expected 2 got {}", len)))
    }

    fn static_sts_iv(&self) -> Result<[u8; 6]> {
        let static_sts_iv_bytes = self.jni_context.byte_arr_getter("getStaticStsIV")?;
        let len = static_sts_iv_bytes.len();

        static_sts_iv_bytes.try_into().map_err(|_| {
            Error::Parse(format!("Invalid static_sts_iv size, expected 6 got {}", len))
        })
    }
}

impl TryFrom<FiraOpenSessionParamsJni<'_>> for AppConfigParams {
    type Error = Error;

    fn try_from(jni_obj: FiraOpenSessionParamsJni) -> Result<Self> {
        FiraAppConfigParamsBuilder::new()
            .device_type(jni_obj.device_type()?)
            .ranging_round_usage(jni_obj.ranging_round_usage()?)
            .sts_config(jni_obj.sts_config()?)
            .multi_node_mode(jni_obj.multi_node_mode()?)
            .channel_number(jni_obj.channel_number()?)
            .slot_duration_rstu(jni_obj.slot_duration_rstu()?)
            .ranging_interval_ms(jni_obj.ranging_interval_ms()?)
            .mac_fcs_type(jni_obj.mac_fcs_type()?)
            .aoa_result_request(jni_obj.aoa_result_request()?)
            .range_data_ntf_config(jni_obj.range_data_ntf_config()?)
            .range_data_ntf_proximity_near_cm(jni_obj.range_data_ntf_proximity_near_cm()?)
            .range_data_ntf_proximity_far_cm(jni_obj.range_data_ntf_proximity_far_cm()?)
            .device_role(jni_obj.device_role()?)
            .rframe_config(jni_obj.rframe_config()?)
            .preamble_code_index(jni_obj.preamble_code_index()?)
            .sfd_id(jni_obj.sfd_id()?)
            .psdu_data_rate(jni_obj.psdu_data_rate()?)
            .preamble_duration(jni_obj.preamble_duration()?)
            .slots_per_rr(jni_obj.slots_per_rr()?)
            .prf_mode(jni_obj.prf_mode()?)
            .key_rotation_rate(jni_obj.key_rotation_rate()?)
            .session_priority(jni_obj.session_priority()?)
            .mac_address_mode(jni_obj.mac_address_mode()?)
            .number_of_sts_segments(jni_obj.number_of_sts_segments()?)
            .max_rr_retry(jni_obj.max_rr_retry()?)
            .uwb_initiation_time_ms(jni_obj.uwb_initiation_time_ms()?)
            .hopping_mode(jni_obj.hopping_mode()?)
            .block_stride_length(jni_obj.block_stride_length()?)
            .in_band_termination_attempt_count(jni_obj.in_band_termination_attempt_count()?)
            .sub_session_id(jni_obj.sub_session_id()?)
            .bprf_phr_data_rate(jni_obj.bprf_phr_data_rate()?)
            .sts_length(jni_obj.sts_length()?)
            .number_of_range_measurements(jni_obj.number_of_range_measurements()?)
            .number_of_aoa_azimuth_measurements(jni_obj.number_of_aoa_azimuth_measurements()?)
            .number_of_aoa_elevation_measurements(jni_obj.number_of_aoa_elevation_measurements()?)
            .tx_adaptive_payload_power(jni_obj.tx_adaptive_payload_power()?)
            .key_rotation(jni_obj.key_rotation()?)
            .device_mac_address(jni_obj.device_mac_address()?)
            .dst_mac_address(jni_obj.dest_mac_address()?)
            .vendor_id(jni_obj.vendor_id()?)
            .static_sts_iv(jni_obj.static_sts_iv()?)
            .build()
            .ok_or_else(|| Error::Parse(String::from("Bad parameters")))

        // TODO(b/244787320): implement the rest of the fields
        // ------
        // * result_report_config  -- struct (can't find corresponding java fields)
        // * ranging_round_control -- struct (can't find corresponding java fields)
        // ------
        // Following fields are defined in FiraOpenSessionParams in rust
        // but don't exist as params on Java's side:
        //  * ranging_time_struct
        //  * responder_slot_index
        //  * scheduled_mode
        //  * max_number_of_measurements
    }
}

/// Wrapper struct for UwbAddress Java class
struct UwbAddressJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> UwbAddressJni<'a> {
    fn new(env: JNIEnv<'a>, address_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, address_obj) }
    }

    fn bytes(&self) -> Result<Vec<u8>> {
        self.jni_context.byte_arr_getter("toBytes").map_err(|e| e.into())
    }
}

impl TryFrom<UwbAddressJni<'_>> for UwbAddress {
    type Error = Error;

    fn try_from(addr_obj: UwbAddressJni<'_>) -> Result<Self> {
        addr_obj.bytes()?.try_into().map_err(|e: &str| Error::Parse(e.to_string()))
    }
}

impl TryFrom<UwbAddressJni<'_>> for u16 {
    type Error = Error;

    fn try_from(addr_obj: UwbAddressJni<'_>) -> Result<Self> {
        let uwb_addr = UwbAddress::try_from(addr_obj)?;
        match uwb_addr {
            UwbAddress::Extended(_) => {
                Err(Error::Parse(String::from("Can't cast to u16 from UwbAddress::Extended")))
            }
            UwbAddress::Short(val) => Ok(u16::from_le_bytes(val)),
        }
    }
}

/// Wrapper struct for CccOpenRangingParams Java class
pub struct CccOpenRangingParamsJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> CccOpenRangingParamsJni<'a> {
    pub fn new(env: JNIEnv<'a>, params_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, params_obj) }
    }

    int_field!(uwb_config, CccUwbConfig, "getUwbConfig");
    int_field!(channel_number, CccUwbChannel, "getChannel");
    int_field!(chaps_per_slot, ChapsPerSlot, "getNumChapsPerSlot");
    int_field!(hopping_config_mode, u8, "getHoppingConfigMode");
    int_field!(hopping_sequence, u8, "getHoppingSequence");
    int_field!(ran_multiplier, u32, "getRanMultiplier");
    int_field!(num_responder_nodes, u8, "getNumResponderNodes");
    int_field!(slots_per_rr, u8, "getNumSlotsPerRound");
    int_field!(sync_code_index, u8, "getSyncCodeIndex");

    fn hopping_mode(&self) -> Result<CccHoppingMode> {
        let config_mode = self.hopping_config_mode()?;
        let sequence = self.hopping_sequence()?;

        // TODO(cante): maybe move this to ccc_params
        Ok(match (config_mode, sequence) {
            (0, _) => CccHoppingMode::Disable,
            (1, 0) => CccHoppingMode::ContinuousDefault,
            (1, 1) => CccHoppingMode::ContinuousAes,
            (2, 0) => CccHoppingMode::AdaptiveDefault,
            (2, 1) => CccHoppingMode::AdaptiveAes,
            _ => return Err(Error::Parse(String::from("Invalid hopping mode"))),
        })
    }

    fn pulse_shape_combo(&self) -> Result<CccPulseShapeCombo> {
        let pulse_obj = self.jni_context.object_getter(
            "getPulseShapeCombo",
            "()Lcom/google/uwb/support/ccc/CccPulseShapeCombo;",
        )?;

        CccPulseShapeComboJni::new(self.jni_context.env, pulse_obj).try_into()
    }

    fn protocol_version(&self) -> Result<CccProtocolVersion> {
        let protocol_version_obj = self.jni_context.object_getter(
            "getProtocolVersion",
            "()Lcom/google/uwb/support/ccc/CccProtocolVersion;",
        )?;

        CccProtocolVersionJni::new(self.jni_context.env, protocol_version_obj).try_into()
    }
}

impl TryFrom<CccOpenRangingParamsJni<'_>> for AppConfigParams {
    type Error = Error;

    fn try_from(jni_obj: CccOpenRangingParamsJni<'_>) -> Result<Self> {
        CccAppConfigParamsBuilder::new()
            .channel_number(jni_obj.channel_number()?)
            .chaps_per_slot(jni_obj.chaps_per_slot()?)
            .hopping_mode(jni_obj.hopping_mode()?)
            .num_responder_nodes(jni_obj.num_responder_nodes()?)
            .protocol_version(jni_obj.protocol_version()?)
            .pulse_shape_combo(jni_obj.pulse_shape_combo()?)
            .ran_multiplier(jni_obj.ran_multiplier()?)
            .slots_per_rr(jni_obj.slots_per_rr()?)
            .sync_code_index(jni_obj.sync_code_index()?)
            .uwb_config(jni_obj.uwb_config()?)
            .build()
            .ok_or_else(|| Error::Parse(String::from("Bad parameters")))
    }
}

/// Wrapper struct for CccPuleShapeCombo Java class
struct CccPulseShapeComboJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> CccPulseShapeComboJni<'a> {
    fn new(env: JNIEnv<'a>, pulse_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, pulse_obj) }
    }

    int_field!(initiator_tx, PulseShape, "getInitiatorTx");
    int_field!(responder_tx, PulseShape, "getResponderTx");
}

impl TryFrom<CccPulseShapeComboJni<'_>> for CccPulseShapeCombo {
    type Error = Error;

    fn try_from(jni_obj: CccPulseShapeComboJni<'_>) -> Result<Self> {
        let initiator_tx = jni_obj.initiator_tx()?;
        let responder_tx = jni_obj.responder_tx()?;

        Ok(CccPulseShapeCombo { initiator_tx, responder_tx })
    }
}

/// Wrapper struct for CccProtocolVersion Java class
struct CccProtocolVersionJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> CccProtocolVersionJni<'a> {
    fn new(env: JNIEnv<'a>, protocol_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, protocol_obj) }
    }

    int_field!(major, u8, "getMajor");
    int_field!(minor, u8, "getMinor");
}

impl TryFrom<CccProtocolVersionJni<'_>> for CccProtocolVersion {
    type Error = Error;

    fn try_from(jni_obj: CccProtocolVersionJni<'_>) -> Result<Self> {
        let major = jni_obj.major()?;
        let minor = jni_obj.minor()?;

        Ok(CccProtocolVersion { major, minor })
    }
}

/// Wrapper struct for FiraControleeParams.
/// Internally FiraControleeParams is an array of UwbAddresses and an array of subSessionIds,
/// The `Controlee` struct from uwb_uci_packets represents a single pair of UwbAddress and
/// subSessionId, hence this wrapper returns a Vec<Controlee> from the as_vec method.

pub struct FiraControleeParamsJni<'a> {
    jni_context: JniContext<'a>,
}

impl<'a> FiraControleeParamsJni<'a> {
    pub fn new(env: JNIEnv<'a>, controlee_obj: JObject<'a>) -> Self {
        Self { jni_context: JniContext::new(env, controlee_obj) }
    }

    pub fn as_vec(&self) -> Result<Vec<Controlee>> {
        let env = self.jni_context.env;
        let addr_arr =
            self.jni_context.object_getter("getAddressList", "[android/uwb/UwbAddress;")?;
        let addr_len = env.get_array_length(addr_arr.into_raw())?;

        let subs_arr = self.jni_context.object_getter("getSubSessionIdList", "[I")?;
        let subs_len = env.get_array_length(subs_arr.into_raw())?;

        if addr_len != subs_len {
            return Err(Error::Parse(format!(
                "Mismatched array sizes, addressList size: {}, subSessionIdList size: {}",
                addr_len, subs_len
            )));
        }

        let mut controlees = vec![];

        let size: usize = addr_len.try_into().unwrap();
        let mut subs_arr_vec = vec![0i32; size];
        env.get_int_array_region(subs_arr.into_raw(), 0, &mut subs_arr_vec)?;

        for (i, sub_session) in subs_arr_vec.iter().enumerate() {
            let uwb_address_obj = env.get_object_array_element(addr_arr.into_raw(), i as i32)?;
            let uwb_address: u16 = UwbAddressJni::new(env, uwb_address_obj).try_into()?;
            controlees
                .push(Controlee { short_address: uwb_address, subsession_id: *sub_session as u32 });
        }

        Ok(controlees)
    }
}

/// Wrapper struct for CountryCode
pub struct CountryCodeJni<'a> {
    env: JNIEnv<'a>,
    country_code_arr: jbyteArray,
}

impl<'a> CountryCodeJni<'a> {
    pub fn new(env: JNIEnv<'a>, country_code_arr: jbyteArray) -> Self {
        Self { env, country_code_arr }
    }
}

impl TryFrom<CountryCodeJni<'_>> for CountryCode {
    type Error = Error;

    fn try_from(jni: CountryCodeJni<'_>) -> Result<Self> {
        let country_code_vec = jni.env.convert_byte_array(jni.country_code_arr)?;
        if country_code_vec.len() != 2 {
            return Err(Error::Parse(format!(
                "Country code invalid length. Received {} bytes.",
                country_code_vec.len()
            )));
        }

        let country_code = [country_code_vec[0], country_code_vec[1]];
        match CountryCode::new(&country_code) {
            Some(val) => Ok(val),
            _ => Err(Error::Parse(format!(
                "Couldn't parse country code. Received {:?}",
                country_code_vec
            ))),
        }
    }
}

pub struct PowerStatsWithEnv<'a> {
    env: JNIEnv<'a>,
    power_stats: PowerStats,
}

impl<'a> PowerStatsWithEnv<'a> {
    pub fn new(env: JNIEnv<'a>, power_stats: PowerStats) -> Self {
        Self { env, power_stats }
    }
}

pub struct PowerStatsJni<'a> {
    pub jni_context: JniContext<'a>,
}

impl<'a> TryFrom<PowerStatsWithEnv<'a>> for PowerStatsJni<'a> {
    type Error = Error;

    fn try_from(pse: PowerStatsWithEnv<'a>) -> Result<Self> {
        let cls = pse.env.find_class("com/android/server/uwb/info/UwbPowerStats")?;
        let vals = vec![
            pse.power_stats.tx_time_ms,
            pse.power_stats.rx_time_ms,
            pse.power_stats.idle_time_ms,
            pse.power_stats.total_wake_count,
        ]
        .into_iter()
        .map(|val| Ok(JValue::Int(i32::try_from(val)?)))
        .collect::<std::result::Result<Vec<JValue>, TryFromIntError>>()
        .map_err(|_| Error::Parse(String::from("Power Stats parse error")))?;

        let new_obj = pse.env.new_object(cls, "(IIII)V", vals.as_slice())?;

        Ok(Self { jni_context: JniContext { env: pse.env, obj: new_obj } })
    }
}

pub struct SessionRangeDataWithEnv<'a> {
    env: JNIEnv<'a>,
    uwb_ranging_data_jclass: JClass<'a>,
    uwb_two_way_measurement_jclass: JClass<'a>,
    session_range_data: SessionRangeData,
}

impl<'a> SessionRangeDataWithEnv<'a> {
    pub fn new(
        env: JNIEnv<'a>,
        uwb_ranging_data_jclass: JClass<'a>,
        uwb_two_way_measurement_jclass: JClass<'a>,
        session_range_data: SessionRangeData,
    ) -> Self {
        Self { env, uwb_ranging_data_jclass, uwb_two_way_measurement_jclass, session_range_data }
    }
}
pub struct UwbRangingDataJni<'a> {
    pub jni_context: JniContext<'a>,
}

impl<'a> TryFrom<SessionRangeDataWithEnv<'a>> for UwbRangingDataJni<'a> {
    type Error = Error;
    fn try_from(data_obj: SessionRangeDataWithEnv<'a>) -> Result<Self> {
        let (mac_address_indicator, measurements_size) =
            match data_obj.session_range_data.ranging_measurements {
                RangingMeasurements::ShortAddressTwoWay(ref m) => {
                    (MacAddressIndicator::ShortAddress, m.len())
                }
                RangingMeasurements::ExtendedAddressTwoWay(ref m) => {
                    (MacAddressIndicator::ExtendedAddress, m.len())
                }
                RangingMeasurements::ShortAddressDltdoa(ref m) => {
                    (MacAddressIndicator::ShortAddress, m.len())
                }
                RangingMeasurements::ExtendedAddressDltdoa(ref m) => {
                    (MacAddressIndicator::ExtendedAddress, m.len())
                }
                RangingMeasurements::ShortAddressOwrAoa(ref m) => {
                    (MacAddressIndicator::ShortAddress, 1)
                }
                RangingMeasurements::ExtendedAddressOwrAoa(ref m) => {
                    (MacAddressIndicator::ExtendedAddress, 1)
                }
            };
        let measurements_jni = UwbTwoWayMeasurementJni::try_from(RangingMeasurementsWithEnv::new(
            data_obj.env,
            data_obj.uwb_two_way_measurement_jclass,
            data_obj.session_range_data.ranging_measurements,
        ))?;
        let raw_notification_jbytearray =
            data_obj.env.byte_array_from_slice(&data_obj.session_range_data.raw_ranging_data)?;
        // TODO(b/270443790): Check on using OwrAoa measurement class here.

        // Safety: raw_notification_jbytearray safely instantiated above.
        let raw_notification_jobject = unsafe { JObject::from_raw(raw_notification_jbytearray) };
        let ranging_data_jni = data_obj.env.new_object(
            data_obj.uwb_ranging_data_jclass,
            "(JJIJIII[Lcom/android/server/uwb/data/UwbTwoWayMeasurement;[B)V",
            &[
                JValue::Long(data_obj.session_range_data.sequence_number as i64),
                JValue::Long(data_obj.session_range_data.session_token as i64),
                JValue::Int(data_obj.session_range_data.rcr_indicator as i32),
                JValue::Long(data_obj.session_range_data.current_ranging_interval_ms as i64),
                JValue::Int(data_obj.session_range_data.ranging_measurement_type as i32),
                JValue::Int(mac_address_indicator as i32),
                JValue::Int(measurements_size as i32),
                JValue::Object(measurements_jni.jni_context.obj),
                JValue::Object(raw_notification_jobject),
            ],
        )?;

        Ok(UwbRangingDataJni { jni_context: JniContext::new(data_obj.env, ranging_data_jni) })
    }
}

// Byte size of mac address length:
const SHORT_MAC_ADDRESS_LEN: i32 = 2;
const EXTENDED_MAC_ADDRESS_LEN: i32 = 8;

enum MacAddress {
    Short(u16),
    Extended(u64),
}
impl MacAddress {
    fn into_ne_bytes_i8(self) -> Vec<i8> {
        match self {
            MacAddress::Short(val) => val.to_ne_bytes().into_iter().map(|b| b as i8).collect(),
            MacAddress::Extended(val) => val.to_ne_bytes().into_iter().map(|b| b as i8).collect(),
        }
    }
}
struct TwoWayRangingMeasurement {
    mac_address: MacAddress,
    status: StatusCode,
    nlos: u8,
    distance: u16,
    aoa_azimuth: u16,
    aoa_azimuth_fom: u8,
    aoa_elevation: u16,
    aoa_elevation_fom: u8,
    aoa_destination_azimuth: u16,
    aoa_destination_azimuth_fom: u8,
    aoa_destination_elevation: u16,
    aoa_destination_elevation_fom: u8,
    slot_index: u8,
    rssi: u8,
}

impl From<ShortAddressTwoWayRangingMeasurement> for TwoWayRangingMeasurement {
    fn from(measurement: ShortAddressTwoWayRangingMeasurement) -> Self {
        TwoWayRangingMeasurement {
            mac_address: MacAddress::Short(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            distance: (measurement.distance),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
            aoa_destination_azimuth: (measurement.aoa_destination_azimuth),
            aoa_destination_azimuth_fom: (measurement.aoa_destination_azimuth_fom),
            aoa_destination_elevation: (measurement.aoa_destination_elevation),
            aoa_destination_elevation_fom: (measurement.aoa_destination_elevation_fom),
            slot_index: (measurement.slot_index),
            rssi: (measurement.rssi),
        }
    }
}

impl From<ExtendedAddressTwoWayRangingMeasurement> for TwoWayRangingMeasurement {
    fn from(measurement: ExtendedAddressTwoWayRangingMeasurement) -> Self {
        TwoWayRangingMeasurement {
            mac_address: MacAddress::Extended(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            distance: (measurement.distance),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
            aoa_destination_azimuth: (measurement.aoa_destination_azimuth),
            aoa_destination_azimuth_fom: (measurement.aoa_destination_azimuth_fom),
            aoa_destination_elevation: (measurement.aoa_destination_elevation),
            aoa_destination_elevation_fom: (measurement.aoa_destination_elevation_fom),
            slot_index: (measurement.slot_index),
            rssi: (measurement.rssi),
        }
    }
}

pub struct RangingMeasurementsWithEnv<'a> {
    env: JNIEnv<'a>,
    uwb_two_way_measurement_jclass: JClass<'a>,
    ranging_measurements: RangingMeasurements,
}
impl<'a> RangingMeasurementsWithEnv<'a> {
    pub fn new(
        env: JNIEnv<'a>,
        uwb_two_way_measurement_jclass: JClass<'a>,
        ranging_measurements: RangingMeasurements,
    ) -> Self {
        Self { env, uwb_two_way_measurement_jclass, ranging_measurements }
    }
}
pub struct UwbTwoWayMeasurementJni<'a> {
    pub jni_context: JniContext<'a>,
}

impl<'a> TryFrom<RangingMeasurementsWithEnv<'a>> for UwbTwoWayMeasurementJni<'a> {
    type Error = Error;
    fn try_from(measurements_obj: RangingMeasurementsWithEnv<'a>) -> Result<Self> {
        let (measurements_vec, byte_arr_size) = match measurements_obj.ranging_measurements {
            RangingMeasurements::ShortAddressTwoWay(m) => (
                m.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>(),
                SHORT_MAC_ADDRESS_LEN,
            ),
            RangingMeasurements::ExtendedAddressTwoWay(m) => (
                m.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>(),
                EXTENDED_MAC_ADDRESS_LEN,
            ),
            // TODO(b/260495115): Re-work needed to handle DlTDoAShort and DlTDoAExtended.
            // TODO(b/270443790): Handle OwrAoa (Short and Extended).
            _ => todo!(),
        };
        let address_jbytearray = measurements_obj.env.new_byte_array(byte_arr_size)?;

        // Safety: address_jbytearray safely instantiated above.
        let address_jobject = unsafe { JObject::from_raw(address_jbytearray) };
        let zero_initiated_measurement_jobject = measurements_obj.env.new_object(
            measurements_obj.uwb_two_way_measurement_jclass,
            "([BIIIIIIIIIIIII)V",
            &[
                JValue::Object(address_jobject),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
                JValue::Int(0),
            ],
        )?;
        let measurements_array_jobject = measurements_obj.env.new_object_array(
            measurements_vec.len() as i32,
            measurements_obj.uwb_two_way_measurement_jclass,
            zero_initiated_measurement_jobject,
        )?;
        for (i, measurement) in measurements_vec.into_iter().enumerate() {
            let mac_address_bytes = measurement.mac_address.into_ne_bytes_i8();
            let mac_address_bytes_jbytearray =
                measurements_obj.env.new_byte_array(byte_arr_size)?;
            measurements_obj.env.set_byte_array_region(
                mac_address_bytes_jbytearray,
                0,
                mac_address_bytes.as_slice(),
            )?;

            // Safety: mac_address_bytes_jbytearray safely instantiated above.
            let mac_address_bytes_jobject =
                unsafe { JObject::from_raw(mac_address_bytes_jbytearray) };

            let measurement_jobject = measurements_obj.env.new_object(
                measurements_obj.uwb_two_way_measurement_jclass,
                "([BIIIIIIIIIIIII)V",
                &[
                    JValue::Object(mac_address_bytes_jobject),
                    JValue::Int(i32::from(measurement.status)),
                    JValue::Int(measurement.nlos as i32),
                    JValue::Int(measurement.distance as i32),
                    JValue::Int(measurement.aoa_azimuth as i32),
                    JValue::Int(measurement.aoa_azimuth_fom as i32),
                    JValue::Int(measurement.aoa_elevation as i32),
                    JValue::Int(measurement.aoa_elevation_fom as i32),
                    JValue::Int(measurement.aoa_destination_azimuth as i32),
                    JValue::Int(measurement.aoa_destination_azimuth_fom as i32),
                    JValue::Int(measurement.aoa_destination_elevation as i32),
                    JValue::Int(measurement.aoa_destination_elevation_fom as i32),
                    JValue::Int(measurement.slot_index as i32),
                    JValue::Int(measurement.rssi as i32),
                ],
            )?;
            measurements_obj.env.set_object_array_element(
                measurements_array_jobject,
                i as i32,
                measurement_jobject,
            )?;
        }

        // Safety: measurements_array_jobject safely instantiated above.
        let measurements_jobject = unsafe { JObject::from_raw(measurements_array_jobject) };
        Ok(UwbTwoWayMeasurementJni {
            jni_context: JniContext::new(measurements_obj.env, measurements_jobject),
        })
    }
}

/// Boilerplate code macro for defining int getters
macro_rules! int_field {
    ($field: ident, $ret: ty, $method: expr) => {
        fn $field(&self) -> Result<$ret> {
            let val = self.jni_context.int_getter($method)?;
            <$ret>::from_i32(val).ok_or_else(|| {
                Error::Parse(format!("{} parse error. Received {}", stringify!($field), val))
            })
        }
    };
}

/// Boilerplate code macro for defining bool getters
macro_rules! bool_field {
    ($field: ident, $ret: ty, $method: expr) => {
        fn $field(&self) -> Result<$ret> {
            let val = self.jni_context.bool_getter($method)?;
            <$ret>::from_u8(val as u8).ok_or_else(|| {
                Error::Parse(format!("{} Parse error. Received {}", stringify!($field), val))
            })
        }
    };
}

pub(crate) use bool_field;
pub(crate) use int_field;
