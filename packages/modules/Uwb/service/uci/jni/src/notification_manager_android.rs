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

//! Implementation of NotificationManagerAndroid and its builder.

use crate::jclass_name::{
    MULTICAST_LIST_UPDATE_STATUS_CLASS, UWB_DL_TDOA_MEASUREMENT_CLASS,
    UWB_OWR_AOA_MEASUREMENT_CLASS, UWB_RADAR_DATA_CLASS, UWB_RADAR_SWEEP_DATA_CLASS,
    UWB_RANGING_DATA_CLASS, UWB_TWO_WAY_MEASUREMENT_CLASS,
};

use std::collections::HashMap;
use std::sync::Arc;

use jni::errors::Error as JNIError;
use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::signature::TypeSignature;
use jni::sys::jvalue;
use jni::{AttachGuard, JavaVM};
use log::{debug, error};
use uwb_core::error::{Error as UwbError, Result as UwbResult};
use uwb_core::params::UwbAddress;
use uwb_core::uci::uci_manager_sync::{NotificationManager, NotificationManagerBuilder};
use uwb_core::uci::{
    CoreNotification, DataRcvNotification, RadarDataRcvNotification, RangingMeasurements,
    SessionNotification, SessionRangeData,
};
use uwb_uci_packets::{
    radar_bytes_per_sample_value, ControleeStatus, ExtendedAddressDlTdoaRangingMeasurement,
    ExtendedAddressOwrAoaRangingMeasurement, ExtendedAddressTwoWayRangingMeasurement,
    MacAddressIndicator, RangingMeasurementType, SessionState,
    ShortAddressDlTdoaRangingMeasurement, ShortAddressOwrAoaRangingMeasurement,
    ShortAddressTwoWayRangingMeasurement, StatusCode,
};

// Byte size of mac address length:
const SHORT_MAC_ADDRESS_LEN: i32 = 2;
const EXTENDED_MAC_ADDRESS_LEN: i32 = 8;
const MAX_ANCHOR_LOCATION_LEN: i32 = 12;
const MAX_RANGING_ROUNDS_LEN: i32 = 16;
const MAX_RADAR_VENDOR_DATA_LEN: i32 = 256;

// Maximum allowed number of Java Object to be allocated inside with_local_frame
const MAX_JAVA_OBJECTS_CAPACITY: i32 = 50;

enum MacAddress {
    Short(u16),
    Extended(u64),
}
impl MacAddress {
    fn into_ne_bytes(self) -> Vec<u8> {
        match self {
            MacAddress::Short(val) => val.to_ne_bytes().into(),
            MacAddress::Extended(val) => val.to_ne_bytes().into(),
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

struct OwrAoaRangingMeasurement {
    mac_address: MacAddress,
    status: StatusCode,
    nlos: u8,
    frame_sequence_number: u8,
    block_index: u16,
    aoa_azimuth: u16,
    aoa_azimuth_fom: u8,
    aoa_elevation: u16,
    aoa_elevation_fom: u8,
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

impl From<ShortAddressOwrAoaRangingMeasurement> for OwrAoaRangingMeasurement {
    fn from(measurement: ShortAddressOwrAoaRangingMeasurement) -> Self {
        OwrAoaRangingMeasurement {
            mac_address: MacAddress::Short(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            frame_sequence_number: (measurement.frame_sequence_number),
            block_index: (measurement.block_index),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
        }
    }
}

impl From<ExtendedAddressOwrAoaRangingMeasurement> for OwrAoaRangingMeasurement {
    fn from(measurement: ExtendedAddressOwrAoaRangingMeasurement) -> Self {
        OwrAoaRangingMeasurement {
            mac_address: MacAddress::Extended(measurement.mac_address),
            status: (measurement.status),
            nlos: (measurement.nlos),
            frame_sequence_number: (measurement.frame_sequence_number),
            block_index: (measurement.block_index),
            aoa_azimuth: (measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.aoa_elevation_fom),
        }
    }
}

struct DlTdoaRangingMeasurement {
    mac_address: MacAddress,
    pub status: u8,
    pub message_type: u8,
    pub message_control: u16,
    pub block_index: u16,
    pub round_index: u8,
    pub nlos: u8,
    pub aoa_azimuth: u16,
    pub aoa_azimuth_fom: u8,
    pub aoa_elevation: u16,
    pub aoa_elevation_fom: u8,
    pub rssi: u8,
    pub tx_timestamp: u64,
    pub rx_timestamp: u64,
    pub anchor_cfo: u16,
    pub cfo: u16,
    pub initiator_reply_time: u32,
    pub responder_reply_time: u32,
    pub initiator_responder_tof: u16,
    pub dt_anchor_location: Vec<u8>,
    pub ranging_rounds: Vec<u8>,
}

impl From<ExtendedAddressDlTdoaRangingMeasurement> for DlTdoaRangingMeasurement {
    fn from(measurement: ExtendedAddressDlTdoaRangingMeasurement) -> Self {
        DlTdoaRangingMeasurement {
            mac_address: MacAddress::Extended(measurement.mac_address),
            status: (measurement.measurement.status),
            message_type: (measurement.measurement.message_type),
            message_control: (measurement.measurement.message_control),
            block_index: (measurement.measurement.block_index),
            round_index: (measurement.measurement.round_index),
            nlos: (measurement.measurement.nlos),
            aoa_azimuth: (measurement.measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.measurement.aoa_elevation_fom),
            rssi: (measurement.measurement.rssi),
            tx_timestamp: (measurement.measurement.tx_timestamp),
            rx_timestamp: (measurement.measurement.rx_timestamp),
            anchor_cfo: (measurement.measurement.anchor_cfo),
            cfo: (measurement.measurement.cfo),
            initiator_reply_time: (measurement.measurement.initiator_reply_time),
            responder_reply_time: (measurement.measurement.responder_reply_time),
            initiator_responder_tof: (measurement.measurement.initiator_responder_tof),
            dt_anchor_location: (measurement.measurement.dt_anchor_location),
            ranging_rounds: (measurement.measurement.ranging_rounds),
        }
    }
}

impl From<ShortAddressDlTdoaRangingMeasurement> for DlTdoaRangingMeasurement {
    fn from(measurement: ShortAddressDlTdoaRangingMeasurement) -> Self {
        DlTdoaRangingMeasurement {
            mac_address: MacAddress::Short(measurement.mac_address),
            status: (measurement.measurement.status),
            message_type: (measurement.measurement.message_type),
            message_control: (measurement.measurement.message_control),
            block_index: (measurement.measurement.block_index),
            round_index: (measurement.measurement.round_index),
            nlos: (measurement.measurement.nlos),
            aoa_azimuth: (measurement.measurement.aoa_azimuth),
            aoa_azimuth_fom: (measurement.measurement.aoa_azimuth_fom),
            aoa_elevation: (measurement.measurement.aoa_elevation),
            aoa_elevation_fom: (measurement.measurement.aoa_elevation_fom),
            rssi: (measurement.measurement.rssi),
            tx_timestamp: (measurement.measurement.tx_timestamp),
            rx_timestamp: (measurement.measurement.rx_timestamp),
            anchor_cfo: (measurement.measurement.anchor_cfo),
            cfo: (measurement.measurement.cfo),
            initiator_reply_time: (measurement.measurement.initiator_reply_time),
            responder_reply_time: (measurement.measurement.responder_reply_time),
            initiator_responder_tof: (measurement.measurement.initiator_responder_tof),
            dt_anchor_location: (measurement.measurement.dt_anchor_location),
            ranging_rounds: (measurement.measurement.ranging_rounds),
        }
    }
}

pub(crate) struct NotificationManagerAndroid {
    pub chip_id: String,
    // 'static annotation is needed as env is 'sent' by tokio::task::spawn_local.
    pub env: AttachGuard<'static>,
    /// Global reference to the class loader object (java/lang/ClassLoader) from the java thread
    /// that local java UCI classes can be loaded.
    /// See http://yangyingchao.github.io/android/2015/01/13/Android-JNI-FindClass-Error.html
    pub class_loader_obj: GlobalRef,
    /// Global reference to the java class holding the various UCI notification callback functions.
    pub callback_obj: GlobalRef,
    // *_jmethod_id are cached for faster callback using call_method_unchecked
    pub jmethod_id_map: HashMap<String, JMethodID>,
    // jclass are cached for faster callback
    pub jclass_map: HashMap<String, GlobalRef>,
}

// TODO(b/246678053): Need to add callbacks for Data Packet Rx, and Data Packet Tx events (like
// DATA_CREDIT_NTF, DATA_STATUS_NTF).
impl NotificationManagerAndroid {
    /// Finds JClass stored in jclass map. Should be a member function, but disjoint field borrow
    /// checker fails and mutability of individual fields has to be annotated.
    fn find_local_class<'a>(
        jclass_map: &'a mut HashMap<String, GlobalRef>,
        class_loader_obj: &'a GlobalRef,
        env: &'a AttachGuard<'static>,
        class_name: &'a str,
    ) -> Result<JClass<'a>, JNIError> {
        // Look for cached class
        if jclass_map.get(class_name).is_none() {
            // Find class using the class loader object, needed as this call is initiated from a
            // different native thread.

            let env_class_name = *env.new_string(class_name).map_err(|e| {
                error!("UCI JNI: failed to create Java String: {e:?}");
                e
            })?;
            let class_value = env
                .call_method(
                    class_loader_obj.as_obj(),
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    &[JValue::Object(env_class_name)],
                )
                .map_err(|e| {
                    error!("UCI JNI: failed to find java class {}: {:?}", class_name, e);
                    e
                })?;
            let jclass = match class_value.l() {
                Ok(obj) => Ok(JClass::from(obj)),
                Err(e) => {
                    error!("UCI JNI: failed to find java class {}: {:?}", class_name, e);
                    Err(e)
                }
            }?;
            // Cache JClass as a global reference.
            jclass_map.insert(
                class_name.to_owned(),
                env.new_global_ref(jclass).map_err(|e| {
                    error!("UCI JNI: global reference conversion failed: {:?}", e);
                    e
                })?,
            );
        }
        // Return JClass
        Ok(jclass_map.get(class_name).unwrap().as_obj().into())
    }

    fn cached_jni_call(
        &mut self,
        name: &str,
        sig: &str,
        args: &[jvalue],
    ) -> Result<JObject, JNIError> {
        debug!("UCI JNI: callback {}", name);
        let type_signature = TypeSignature::from_str(sig).map_err(|e| {
            error!("UCI JNI: Invalid type signature: {:?}", e);
            e
        })?;
        if type_signature.args.len() != args.len() {
            error!(
                "UCI: type_signature requires {} args, but {} is provided",
                type_signature.args.len(),
                args.len()
            );
            return Err(jni::errors::Error::InvalidArgList(type_signature));
        }
        let name_signature = name.to_owned() + sig;
        if self.jmethod_id_map.get(&name_signature).is_none() {
            self.jmethod_id_map.insert(
                name_signature.clone(),
                self.env.get_method_id(self.callback_obj.as_obj(), name, sig).map_err(|e| {
                    error!("UCI JNI: failed to get method: {:?}", e);
                    e
                })?,
            );
        }
        match self.env.call_method_unchecked(
            self.callback_obj.as_obj(),
            self.jmethod_id_map.get(&name_signature).unwrap().to_owned(),
            type_signature.ret,
            args,
        ) {
            Ok(_) => Ok(JObject::null()),
            Err(e) => {
                error!("UCI JNI: callback {} failed!", name);
                Err(e)
            }
        }
    }

    fn on_session_status_notification(
        &mut self,
        session_id: u32,
        session_state: SessionState,
        reason_code: u8,
    ) -> Result<JObject, JNIError> {
        self.cached_jni_call(
            "onSessionStatusNotificationReceived",
            "(JII)V",
            &[
                jvalue::from(JValue::Long(session_id as i64)),
                jvalue::from(JValue::Int(session_state as i32)),
                jvalue::from(JValue::Int(reason_code as i32)),
            ],
        )
    }

    fn on_session_update_multicast_notification(
        &mut self,
        session_id: u32,
        remaining_multicast_list_size: usize,
        status_list: Vec<ControleeStatus>,
    ) -> Result<JObject, JNIError> {
        let remaining_multicast_list_size: i32 =
            remaining_multicast_list_size.try_into().map_err(|_| JNIError::InvalidCtorReturn)?;
        let count: i32 = status_list.len().try_into().map_err(|_| JNIError::InvalidCtorReturn)?;
        let subsession_id_jlongarray = self.env.new_long_array(count)?;
        let status_jintarray = self.env.new_int_array(count)?;
        let (mac_address_vec, (subsession_id_vec, status_vec)): (Vec<[u8; 2]>, (Vec<_>, Vec<_>)) =
            status_list
                .into_iter()
                .map(|cs| (cs.mac_address, (cs.subsession_id as i64, i32::from(cs.status))))
                .unzip();

        let mac_address_vec_i8 =
            mac_address_vec.iter().flat_map(|&[a, b]| vec![a as i8, b as i8]).collect::<Vec<i8>>();
        let mac_address_slice: &[i8] = &mac_address_vec_i8;
        let mac_address_jbytearray = self.env.new_byte_array(mac_address_slice.len() as i32)?;

        self.env.set_byte_array_region(mac_address_jbytearray, 0, mac_address_slice)?;
        self.env.set_long_array_region(subsession_id_jlongarray, 0, &subsession_id_vec)?;
        self.env.set_int_array_region(status_jintarray, 0, &status_vec)?;
        let multicast_update_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            MULTICAST_LIST_UPDATE_STATUS_CLASS,
        )?;
        let method_sig = "(L".to_owned() + MULTICAST_LIST_UPDATE_STATUS_CLASS + ";)V";

        // Safety: mac_address_jintarray is safely instantiated above.
        let mac_address_jobject = unsafe { JObject::from_raw(mac_address_jbytearray) };

        // Safety: subsession_id_jlongarray is safely instantiated above.
        let subsession_id_jobject = unsafe { JObject::from_raw(subsession_id_jlongarray) };

        // Safety: status_jintarray is safely instantiated above.
        let status_jobject = unsafe { JObject::from_raw(status_jintarray) };

        let multicast_update_jobject = self.env.new_object(
            multicast_update_jclass,
            "(JII[B[J[I)V",
            &[
                JValue::Long(session_id as i64),
                JValue::Int(remaining_multicast_list_size),
                JValue::Int(count),
                JValue::Object(mac_address_jobject),
                JValue::Object(subsession_id_jobject),
                JValue::Object(status_jobject),
            ],
        )?;
        self.cached_jni_call(
            "onMulticastListUpdateNotificationReceived",
            &method_sig,
            &[jvalue::from(JValue::Object(multicast_update_jobject))],
        )
    }

    // TODO(b/246678053): Re-factor usage of the RangingMeasurement enum below, to extract the
    // fields in a common/caller method (and preferably not handle TwoWay/OwrAoa in this method).
    fn on_session_dl_tdoa_range_data_notification(
        &mut self,
        range_data: SessionRangeData,
    ) -> Result<JObject, JNIError> {
        let raw_notification_jbytearray =
            self.env.byte_array_from_slice(&range_data.raw_ranging_data)?;
        let measurement_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_DL_TDOA_MEASUREMENT_CLASS,
        )?;
        let bytearray_len: i32 = match &range_data.ranging_measurements {
            uwb_core::uci::RangingMeasurements::ShortAddressTwoWay(_) => SHORT_MAC_ADDRESS_LEN,
            uwb_core::uci::RangingMeasurements::ExtendedAddressTwoWay(_) => {
                EXTENDED_MAC_ADDRESS_LEN
            }
            uwb_core::uci::RangingMeasurements::ShortAddressDltdoa(_) => SHORT_MAC_ADDRESS_LEN,
            uwb_core::uci::RangingMeasurements::ExtendedAddressDltdoa(_) => {
                EXTENDED_MAC_ADDRESS_LEN
            }
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        };
        let address_jbytearray = self.env.new_byte_array(bytearray_len)?;
        let anchor_location = self.env.new_byte_array(MAX_ANCHOR_LOCATION_LEN)?;
        let active_ranging_rounds = self.env.new_byte_array(MAX_RANGING_ROUNDS_LEN)?;

        // Safety: address_jbytearray is safely instantiated above.
        let address_jobject = unsafe { JObject::from_raw(address_jbytearray) };
        // Safety: anchor_location is safely instantiated above.
        let anchor_jobject = unsafe { JObject::from_raw(anchor_location) };
        // Safety: active_ranging_rounds is safely instantiated above.
        let active_ranging_rounds_jobject = unsafe { JObject::from_raw(active_ranging_rounds) };

        let zero_initiated_measurement_jobject = self
            .env
            .new_object(
                measurement_jclass,
                "([BIIIIIIIIIIIJJIIJJI[B[B)V",
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
                    JValue::Long(0),
                    JValue::Long(0),
                    JValue::Int(0),
                    JValue::Int(0),
                    JValue::Long(0),
                    JValue::Long(0),
                    JValue::Int(0),
                    JValue::Object(anchor_jobject),
                    JValue::Object(active_ranging_rounds_jobject),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: measurement object creation failed: {:?}", e);
                e
            })?;
        let measurement_count: i32 = match &range_data.ranging_measurements {
            RangingMeasurements::ShortAddressTwoWay(v) => v.len(),
            RangingMeasurements::ExtendedAddressTwoWay(v) => v.len(),
            RangingMeasurements::ShortAddressDltdoa(v) => v.len(),
            RangingMeasurements::ExtendedAddressDltdoa(v) => v.len(),
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        }
        .try_into()
        .map_err(|_| JNIError::InvalidCtorReturn)?;
        let mac_indicator = match &range_data.ranging_measurements {
            RangingMeasurements::ShortAddressTwoWay(_) => MacAddressIndicator::ShortAddress,
            RangingMeasurements::ExtendedAddressTwoWay(_) => MacAddressIndicator::ExtendedAddress,
            RangingMeasurements::ShortAddressDltdoa(_) => MacAddressIndicator::ShortAddress,
            RangingMeasurements::ExtendedAddressDltdoa(_) => MacAddressIndicator::ExtendedAddress,
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        };

        let measurements_jobjectarray = self.env.new_object_array(
            measurement_count,
            measurement_jclass,
            zero_initiated_measurement_jobject,
        )?;

        for (i, measurement) in match range_data.ranging_measurements {
            RangingMeasurements::ShortAddressDltdoa(v) => {
                v.into_iter().map(DlTdoaRangingMeasurement::from).collect::<Vec<_>>()
            }
            RangingMeasurements::ExtendedAddressDltdoa(v) => {
                v.into_iter().map(DlTdoaRangingMeasurement::from).collect::<Vec<_>>()
            }
            _ => Vec::new(),
        }
        .into_iter()
        .enumerate()
        {
            // cast to i8 as java do not support unsigned:
            let mac_address_i8 = measurement
                .mac_address
                .into_ne_bytes()
                .iter()
                .map(|b| b.to_owned() as i8)
                .collect::<Vec<_>>();
            let mac_address_jbytearray = self.env.new_byte_array(mac_address_i8.len() as i32)?;
            self.env.set_byte_array_region(mac_address_jbytearray, 0, &mac_address_i8)?;

            let dt_anchor_location_jbytearray =
                self.env.byte_array_from_slice(&measurement.dt_anchor_location)?;

            let ranging_rounds_jbytearray =
                self.env.byte_array_from_slice(&measurement.ranging_rounds)?;

            // Safety: mac_address_jbytearray is safely instantiated above.
            let mac_address_jobject = unsafe { JObject::from_raw(mac_address_jbytearray) };
            let dt_anchor_location_jobject =
            // Safety: dt_anchor_location_jbytearray is safely instantiated above.
                unsafe { JObject::from_raw(dt_anchor_location_jbytearray) };
            // Safety: ranging_rounds_jbytearray is safely instantiated above.
            let ranging_rounds_jobject = unsafe { JObject::from_raw(ranging_rounds_jbytearray) };

            let measurement_jobject = self
                .env
                .new_object(
                    measurement_jclass,
                    "([BIIIIIIIIIIIJJIIJJI[B[B)V",
                    &[
                        JValue::Object(mac_address_jobject),
                        JValue::Int(measurement.status as i32),
                        JValue::Int(measurement.message_type as i32),
                        JValue::Int(measurement.message_control as i32),
                        JValue::Int(measurement.block_index as i32),
                        JValue::Int(measurement.round_index as i32),
                        JValue::Int(measurement.nlos as i32),
                        JValue::Int(measurement.aoa_azimuth as i32),
                        JValue::Int(measurement.aoa_azimuth_fom as i32),
                        JValue::Int(measurement.aoa_elevation as i32),
                        JValue::Int(measurement.aoa_elevation_fom as i32),
                        JValue::Int(measurement.rssi as i32),
                        JValue::Long(measurement.tx_timestamp as i64),
                        JValue::Long(measurement.rx_timestamp as i64),
                        JValue::Int(measurement.anchor_cfo as i32),
                        JValue::Int(measurement.cfo as i32),
                        JValue::Long(measurement.initiator_reply_time as i64),
                        JValue::Long(measurement.responder_reply_time as i64),
                        JValue::Int(measurement.initiator_responder_tof as i32),
                        JValue::Object(dt_anchor_location_jobject),
                        JValue::Object(ranging_rounds_jobject),
                    ],
                )
                .map_err(|e| {
                    error!("UCI JNI: measurement object creation failed: {:?}", e);
                    e
                })?;
            self.env
                .set_object_array_element(measurements_jobjectarray, i as i32, measurement_jobject)
                .map_err(|e| {
                    error!("UCI JNI: measurement object copy failed: {:?}", e);
                    e
                })?;
        }
        // Create UwbRangingData
        let ranging_data_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_RANGING_DATA_CLASS,
        )?;

        let method_sig = "(JJIJIII[L".to_owned() + UWB_DL_TDOA_MEASUREMENT_CLASS + ";[B)V";

        // Safety: measurements_jobjectarray is safely instantiated above.
        let measurements_jobject = unsafe { JObject::from_raw(measurements_jobjectarray) };
        // Safety: raw_notification_jbytearray is safely instantiated above.
        let raw_notification_jobject = unsafe { JObject::from_raw(raw_notification_jbytearray) };

        let range_data_jobject = self
            .env
            .new_object(
                ranging_data_jclass,
                &method_sig,
                &[
                    JValue::Long(range_data.sequence_number as i64),
                    // session_token below has already been mapped to session_id by uci layer.
                    JValue::Long(range_data.session_token as i64),
                    JValue::Int(range_data.rcr_indicator as i32),
                    JValue::Long(range_data.current_ranging_interval_ms as i64),
                    JValue::Int(range_data.ranging_measurement_type as i32),
                    JValue::Int(mac_indicator as i32),
                    JValue::Int(measurement_count),
                    JValue::Object(measurements_jobject),
                    JValue::Object(raw_notification_jobject),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: Ranging Data object creation failed: {:?}", e);
                e
            })?;

        let method_sig = "(L".to_owned() + UWB_RANGING_DATA_CLASS + ";)V";
        self.cached_jni_call(
            "onRangeDataNotificationReceived",
            &method_sig,
            &[jvalue::from(JValue::Object(range_data_jobject))],
        )
    }

    fn on_two_way_range_data_notification(
        &mut self,
        bytearray_len: i32,
        measurement_count: i32,
        measurements: Vec<TwoWayRangingMeasurement>,
    ) -> Result<jni::sys::jobjectArray, JNIError> {
        let measurement_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_TWO_WAY_MEASUREMENT_CLASS,
        )?;
        let address_jbytearray = self.env.new_byte_array(bytearray_len)?;

        // Safety: address_jbytearray is safely instantiated above.
        let address_jobject = unsafe { JObject::from_raw(address_jbytearray) };

        let zero_initiated_measurement_jobject = self
            .env
            .new_object(
                measurement_jclass,
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
            )
            .map_err(|e| {
                error!("UCI JNI: measurement object creation failed: {:?}", e);
                e
            })?;

        let measurements_jobjectarray = self.env.new_object_array(
            measurement_count,
            measurement_jclass,
            zero_initiated_measurement_jobject,
        )?;
        for (i, measurement) in measurements.into_iter().enumerate() {
            // cast to i8 as java do not support unsigned:
            let mac_address_i8 = measurement
                .mac_address
                .into_ne_bytes()
                .iter()
                .map(|b| b.to_owned() as i8)
                .collect::<Vec<_>>();
            let mac_address_jbytearray = self.env.new_byte_array(mac_address_i8.len() as i32)?;
            self.env.set_byte_array_region(mac_address_jbytearray, 0, &mac_address_i8)?;
            // casting as i32 is fine since it is wider than actual integer type.

            // Safety: mac_address_jbytearray is safely instantiated above.
            let mac_address_jobject = unsafe { JObject::from_raw(mac_address_jbytearray) };
            let measurement_jobject = self
                .env
                .new_object(
                    measurement_jclass,
                    "([BIIIIIIIIIIIII)V",
                    &[
                        JValue::Object(mac_address_jobject),
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
                )
                .map_err(|e| {
                    error!("UCI JNI: measurement object creation failed: {:?}", e);
                    e
                })?;
            self.env
                .set_object_array_element(measurements_jobjectarray, i as i32, measurement_jobject)
                .map_err(|e| {
                    error!("UCI JNI: measurement object copy failed: {:?}", e);
                    e
                })?;
        }

        Ok(measurements_jobjectarray)
    }

    fn on_session_owr_aoa_range_data_notification(
        &mut self,
        range_data: SessionRangeData,
    ) -> Result<JObject, JNIError> {
        if range_data.ranging_measurement_type != RangingMeasurementType::OwrAoa {
            return Err(JNIError::InvalidCtorReturn);
        }

        let raw_notification_jbytearray =
            self.env.byte_array_from_slice(&range_data.raw_ranging_data)?;

        let (mac_indicator, measurement): (MacAddressIndicator, OwrAoaRangingMeasurement) =
            match range_data.ranging_measurements {
                RangingMeasurements::ExtendedAddressOwrAoa(m) => {
                    (MacAddressIndicator::ExtendedAddress, m.into())
                }
                RangingMeasurements::ShortAddressOwrAoa(m) => {
                    (MacAddressIndicator::ShortAddress, m.into())
                }
                _ => {
                    return Err(JNIError::InvalidCtorReturn);
                }
            };

        // cast to i8 as java do not support unsigned.
        let mac_address_i8 = measurement
            .mac_address
            .into_ne_bytes()
            .iter()
            .map(|b| b.to_owned() as i8)
            .collect::<Vec<_>>();
        // casting as i32 is fine since it is wider than actual integer type.
        let mac_address_jbytearray = self.env.new_byte_array(mac_address_i8.len() as i32)?;
        self.env.set_byte_array_region(mac_address_jbytearray, 0, &mac_address_i8)?;
        // Safety: mac_address_jbytearray is safely instantiated above.
        let mac_address_jobject = unsafe { JObject::from_raw(mac_address_jbytearray) };

        let measurement_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_OWR_AOA_MEASUREMENT_CLASS,
        )?;
        let measurement_jobject = self
            .env
            .new_object(
                measurement_jclass,
                "([BIIIIIIII)V",
                &[
                    JValue::Object(mac_address_jobject),
                    JValue::Int(i32::from(measurement.status)),
                    JValue::Int(measurement.nlos as i32),
                    JValue::Int(measurement.frame_sequence_number as i32),
                    JValue::Int(measurement.block_index as i32),
                    JValue::Int(measurement.aoa_azimuth as i32),
                    JValue::Int(measurement.aoa_azimuth_fom as i32),
                    JValue::Int(measurement.aoa_elevation as i32),
                    JValue::Int(measurement.aoa_elevation_fom as i32),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: OwrAoA measurement jobject creation failed: {:?}", e);
                e
            })?;

        // Create UwbRangingData
        let ranging_data_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_RANGING_DATA_CLASS,
        )?;
        let method_sig = "(JJIJIIIL".to_owned() + UWB_OWR_AOA_MEASUREMENT_CLASS + ";[B)V";

        // Safety: raw_notification_jobject is safely instantiated above.
        let raw_notification_jobject = unsafe { JObject::from_raw(raw_notification_jbytearray) };

        let range_data_jobject = self
            .env
            .new_object(
                ranging_data_jclass,
                &method_sig,
                &[
                    JValue::Long(range_data.sequence_number as i64),
                    // session_token below has already been mapped to session_id by uci layer.
                    JValue::Long(range_data.session_token as i64),
                    JValue::Int(range_data.rcr_indicator as i32),
                    JValue::Long(range_data.current_ranging_interval_ms as i64),
                    JValue::Int(range_data.ranging_measurement_type as i32),
                    JValue::Int(mac_indicator as i32),
                    JValue::Int(1), // measurement_count
                    JValue::Object(measurement_jobject),
                    JValue::Object(raw_notification_jobject),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: Ranging Data object creation failed: {:?}", e);
                e
            })?;
        let method_sig = "(L".to_owned() + UWB_RANGING_DATA_CLASS + ";)V";
        self.cached_jni_call(
            "onRangeDataNotificationReceived",
            &method_sig,
            &[jvalue::from(JValue::Object(range_data_jobject))],
        )
    }

    fn on_session_two_way_range_data_notification(
        &mut self,
        range_data: SessionRangeData,
    ) -> Result<JObject, JNIError> {
        let raw_notification_jbytearray =
            self.env.byte_array_from_slice(&range_data.raw_ranging_data)?;

        let (bytearray_len, mac_indicator) = match &range_data.ranging_measurements {
            RangingMeasurements::ExtendedAddressTwoWay(_) => {
                (EXTENDED_MAC_ADDRESS_LEN, MacAddressIndicator::ExtendedAddress)
            }
            RangingMeasurements::ShortAddressTwoWay(_) => {
                (SHORT_MAC_ADDRESS_LEN, MacAddressIndicator::ShortAddress)
            }
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        };

        let measurement_count: i32 = match &range_data.ranging_measurements {
            RangingMeasurements::ShortAddressTwoWay(v) => v.len().try_into(),
            RangingMeasurements::ExtendedAddressTwoWay(v) => v.len().try_into(),
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        }
        .map_err(|_| JNIError::InvalidCtorReturn)?;

        let measurements_jobjectarray = match range_data.ranging_measurement_type {
            RangingMeasurementType::TwoWay => {
                let measurements = match range_data.ranging_measurements {
                    RangingMeasurements::ExtendedAddressTwoWay(v) => {
                        v.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>()
                    }
                    RangingMeasurements::ShortAddressTwoWay(v) => {
                        v.into_iter().map(TwoWayRangingMeasurement::from).collect::<Vec<_>>()
                    }
                    _ => return Err(JNIError::InvalidCtorReturn),
                };
                self.on_two_way_range_data_notification(
                    bytearray_len,
                    measurement_count,
                    measurements,
                )?
            }
            _ => {
                return Err(JNIError::InvalidCtorReturn);
            }
        };

        // Create UwbRangingData
        let ranging_data_jclass = NotificationManagerAndroid::find_local_class(
            &mut self.jclass_map,
            &self.class_loader_obj,
            &self.env,
            UWB_RANGING_DATA_CLASS,
        )?;
        let method_sig = "(JJIJIII[L".to_owned() + UWB_TWO_WAY_MEASUREMENT_CLASS + ";[B)V";

        // Safety: measurements_jobjectarray is safely instantiated above.
        let measurements_jobject = unsafe { JObject::from_raw(measurements_jobjectarray) };
        // Safety: raw_notification_jobject is safely instantiated above.
        let raw_notification_jobject = unsafe { JObject::from_raw(raw_notification_jbytearray) };
        let range_data_jobject = self
            .env
            .new_object(
                ranging_data_jclass,
                &method_sig,
                &[
                    JValue::Long(range_data.sequence_number as i64),
                    // session_token below has already been mapped to session_id by uci layer.
                    JValue::Long(range_data.session_token as i64),
                    JValue::Int(range_data.rcr_indicator as i32),
                    JValue::Long(range_data.current_ranging_interval_ms as i64),
                    JValue::Int(range_data.ranging_measurement_type as i32),
                    JValue::Int(mac_indicator as i32),
                    JValue::Int(measurement_count),
                    JValue::Object(measurements_jobject),
                    JValue::Object(raw_notification_jobject),
                ],
            )
            .map_err(|e| {
                error!("UCI JNI: Ranging Data object creation failed: {:?}", e);
                e
            })?;
        let method_sig = "(L".to_owned() + UWB_RANGING_DATA_CLASS + ";)V";
        self.cached_jni_call(
            "onRangeDataNotificationReceived",
            &method_sig,
            &[jvalue::from(JValue::Object(range_data_jobject))],
        )
    }

    fn on_data_transfer_status_notification(
        &mut self,
        session_id: u32,
        uci_sequence_number: u16,
        status_code: u8,
        tx_count: u8,
    ) -> Result<JObject, JNIError> {
        self.cached_jni_call(
            "onDataSendStatus",
            "(JIJI)V",
            &[
                jvalue::from(JValue::Long(session_id as i64)),
                jvalue::from(JValue::Int(status_code as i32)),
                jvalue::from(JValue::Long(uci_sequence_number as i64)),
                jvalue::from(JValue::Int(tx_count as i32)),
            ],
        )
    }

    fn on_data_transfer_phase_config_notification(
        &mut self,
        session_id: u32,
        status_code: u8,
    ) -> Result<JObject, JNIError> {
        self.cached_jni_call(
            "onDataTransferPhaseConfigNotificationReceived",
            "(JI)V",
            &[
                jvalue::from(JValue::Long(session_id as i64)),
                jvalue::from(JValue::Int(status_code as i32)),
            ],
        )
    }
}

impl NotificationManager for NotificationManagerAndroid {
    fn on_core_notification(&mut self, core_notification: CoreNotification) -> UwbResult<()> {
        debug!("UCI JNI: core notification callback.");
        let env = *self.env;
        env.with_local_frame(MAX_JAVA_OBJECTS_CAPACITY, || {
            let env_chip_id_jobject = *env.new_string(&self.chip_id).map_err(|e| {
                error!("UCI JNI: failed to create Java String: {e:?}");
                e
            })?;

            match core_notification {
                CoreNotification::DeviceStatus(device_state) => self.cached_jni_call(
                    "onDeviceStatusNotificationReceived",
                    "(ILjava/lang/String;)V",
                    &[
                        jvalue::from(JValue::Int(device_state as i32)),
                        jvalue::from(JValue::Object(env_chip_id_jobject)),
                    ],
                ),
                CoreNotification::GenericError(generic_error) => self.cached_jni_call(
                    "onCoreGenericErrorNotificationReceived",
                    "(ILjava/lang/String;)V",
                    &[
                        jvalue::from(JValue::Int(i32::from(generic_error))),
                        jvalue::from(JValue::Object(env_chip_id_jobject)),
                    ],
                ),
            }
        })
        .map_err(|_| UwbError::ForeignFunctionInterface)?;

        Ok(())
    }

    fn on_session_notification(
        &mut self,
        session_notification: SessionNotification,
    ) -> UwbResult<()> {
        debug!("UCI JNI: session notification callback.");
        let env = *self.env;
        env.with_local_frame(MAX_JAVA_OBJECTS_CAPACITY, || {
            match session_notification {
                // session_token below has already been mapped to session_id by uci layer.
                SessionNotification::Status { session_token, session_state, reason_code } => {
                    self.on_session_status_notification(session_token, session_state, reason_code)
                }
                SessionNotification::UpdateControllerMulticastList {
                    session_token,
                    remaining_multicast_list_size,
                    status_list,
                } => self.on_session_update_multicast_notification(
                    session_token,
                    remaining_multicast_list_size,
                    status_list,
                ),
                // TODO(b/246678053): Match here on range_data.ranging_measurement_type instead.
                SessionNotification::SessionInfo(range_data) => {
                    match range_data.ranging_measurements {
                        uwb_core::uci::RangingMeasurements::ShortAddressTwoWay(_) => {
                            self.on_session_two_way_range_data_notification(range_data)
                        }
                        uwb_core::uci::RangingMeasurements::ExtendedAddressTwoWay(_) => {
                            self.on_session_two_way_range_data_notification(range_data)
                        }
                        uwb_core::uci::RangingMeasurements::ShortAddressOwrAoa(_) => {
                            self.on_session_owr_aoa_range_data_notification(range_data)
                        }
                        uwb_core::uci::RangingMeasurements::ExtendedAddressOwrAoa(_) => {
                            self.on_session_owr_aoa_range_data_notification(range_data)
                        }
                        uwb_core::uci::RangingMeasurements::ShortAddressDltdoa(_) => {
                            self.on_session_dl_tdoa_range_data_notification(range_data)
                        }
                        uwb_core::uci::RangingMeasurements::ExtendedAddressDltdoa(_) => {
                            self.on_session_dl_tdoa_range_data_notification(range_data)
                        }
                    }
                }
                SessionNotification::DataTransferStatus {
                    session_token,
                    uci_sequence_number,
                    status,
                    tx_count,
                } => self.on_data_transfer_status_notification(
                    session_token,
                    uci_sequence_number,
                    u8::from(status),
                    tx_count,
                ),
                // This session notification should not come here, as it's handled within
                // UciManager, for internal state management related to sending data packet(s).
                SessionNotification::DataCredit { session_token, credit_availability } => {
                    error!(
                        "UCI JNI: Received unexpected DataCredit notification for \
                            session_token {}, credit_availability {:?}",
                        session_token, credit_availability
                    );
                    Err(JNIError::InvalidCtorReturn)
                }
                SessionNotification::DataTransferPhaseConfig { session_token, status } => {
                    self.on_data_transfer_phase_config_notification(session_token, u8::from(status))
                }
            }
        })
        .map_err(|_| UwbError::ForeignFunctionInterface)?;
        Ok(())
    }

    fn on_vendor_notification(
        &mut self,
        vendor_notification: uwb_core::params::RawUciMessage,
    ) -> UwbResult<()> {
        debug!("UCI JNI: vendor notification callback.");
        let env = *self.env;
        env.with_local_frame(MAX_JAVA_OBJECTS_CAPACITY, || {
            let payload_jbytearray =
                self.env.byte_array_from_slice(&vendor_notification.payload)?;

            // Safety: payload_jbytearray safely instantiated above.
            let payload_jobject = unsafe { JObject::from_raw(payload_jbytearray) };
            self.cached_jni_call(
                "onVendorUciNotificationReceived",
                "(II[B)V",
                &[
                    // Java only has signed integer. The range for signed int32 should be sufficient.
                    jvalue::from(JValue::Int(
                        vendor_notification
                            .gid
                            .try_into()
                            .map_err(|_| JNIError::InvalidCtorReturn)?,
                    )),
                    jvalue::from(JValue::Int(
                        vendor_notification
                            .oid
                            .try_into()
                            .map_err(|_| JNIError::InvalidCtorReturn)?,
                    )),
                    jvalue::from(JValue::Object(payload_jobject)),
                ],
            )
        })
        .map_err(|_| UwbError::ForeignFunctionInterface)?;
        Ok(())
    }

    fn on_data_rcv_notification(
        &mut self,
        data_rcv_notification: DataRcvNotification,
    ) -> UwbResult<()> {
        debug!("UCI JNI: Data Rcv notification callback.");
        let env = *self.env;
        env.with_local_frame(MAX_JAVA_OBJECTS_CAPACITY, || {
            let source_address_jbytearray = match &data_rcv_notification.source_address {
                UwbAddress::Short(a) => self.env.byte_array_from_slice(a)?,
                UwbAddress::Extended(a) => self.env.byte_array_from_slice(a)?,
            };
            let payload_jbytearray =
                self.env.byte_array_from_slice(&data_rcv_notification.payload)?;
            // Safety: source_address_jbytearray safely instantiated above.
            let source_address_jobject = unsafe { JObject::from_raw(source_address_jbytearray) };
            // Safety: payload_jbytearray safely instantiated above.
            let payload_jobject = unsafe { JObject::from_raw(payload_jbytearray) };
            self.cached_jni_call(
                "onDataReceived",
                "(JIJ[B[B)V",
                &[
                    // session_token below has already been mapped to session_id by uci layer.
                    jvalue::from(JValue::Long(data_rcv_notification.session_token as i64)),
                    jvalue::from(JValue::Int(i32::from(data_rcv_notification.status))),
                    jvalue::from(JValue::Long(data_rcv_notification.uci_sequence_num as i64)),
                    jvalue::from(JValue::Object(source_address_jobject)),
                    jvalue::from(JValue::Object(payload_jobject)),
                ],
            )
        })
        .map_err(|_| UwbError::ForeignFunctionInterface)?;
        Ok(())
    }

    fn on_radar_data_rcv_notification(
        &mut self,
        radar_data_rcv_notification: RadarDataRcvNotification,
    ) -> UwbResult<()> {
        debug!("UCI JNI: Radar Data Rcv notification callback.");
        let env = *self.env;
        env.with_local_frame(MAX_JAVA_OBJECTS_CAPACITY, || {
            let radar_sweep_data_jclass = NotificationManagerAndroid::find_local_class(
                &mut self.jclass_map,
                &self.class_loader_obj,
                &self.env,
                UWB_RADAR_SWEEP_DATA_CLASS,
            )?;

            let max_sample_data_length =
                radar_bytes_per_sample_value(radar_data_rcv_notification.bits_per_sample) as i32
                    * radar_data_rcv_notification.samples_per_sweep as i32;
            let sample_data_jbytearray = self.env.new_byte_array(max_sample_data_length)?;
            let vendor_data_jbytearray = self.env.new_byte_array(MAX_RADAR_VENDOR_DATA_LEN)?;

            // Safety: sample_data_jbytearray is safely instantiated above.
            let sample_data_jobject = unsafe { JObject::from_raw(sample_data_jbytearray) };
            // Safety: vendor_data_jbytearray is safely instantiated above.
            let vendor_data_jobject = unsafe { JObject::from_raw(vendor_data_jbytearray) };

            let sweep_data_sig: &str = "(JJ[B[B)V";

            let zero_initiated_sweep_data = self
                .env
                .new_object(
                    radar_sweep_data_jclass,
                    sweep_data_sig,
                    &[
                        JValue::Long(0),
                        JValue::Long(0),
                        JValue::Object(vendor_data_jobject),
                        JValue::Object(sample_data_jobject),
                    ],
                )
                .map_err(|e| {
                    error!(
                        "UCI JNI: zero initiated RadarSweepData object creation failed: {:?}",
                        e
                    );
                    e
                })?;

            let radar_sweep_data_jobjectarray = self
                .env
                .new_object_array(
                    radar_data_rcv_notification.sweep_data.len() as i32,
                    radar_sweep_data_jclass,
                    zero_initiated_sweep_data,
                )
                .map_err(|e| {
                    error!("UCI JNI: RadarSweepData object array creation failed: {:?}", e);
                    e
                })?;

            for (i, sweep_data) in radar_data_rcv_notification.sweep_data.into_iter().enumerate() {
                let vendor_data_jbytearray =
                    self.env.byte_array_from_slice(&sweep_data.vendor_specific_data)?;
                let sample_data_jbytearray =
                    self.env.byte_array_from_slice(&sweep_data.sample_data)?;
                // Safety: vendor_data_jbytearray instantiated above
                let vendor_data_jobject = unsafe { JObject::from_raw(vendor_data_jbytearray) };
                // Safety: sample_data_jbytearray instantiated above
                let sample_data_jobject = unsafe { JObject::from_raw(sample_data_jbytearray) };
                let sweep_data_jobject = self
                    .env
                    .new_object(
                        radar_sweep_data_jclass,
                        sweep_data_sig,
                        &[
                            JValue::Long(sweep_data.sequence_number as i64),
                            JValue::Long(sweep_data.timestamp as i64),
                            JValue::Object(vendor_data_jobject),
                            JValue::Object(sample_data_jobject),
                        ],
                    )
                    .map_err(|e| {
                        error!("UCI JNI: RadarSweepData object creation failed: {:?}", e);
                        e
                    })?;

                self.env
                    .set_object_array_element(
                        radar_sweep_data_jobjectarray,
                        i as i32,
                        sweep_data_jobject,
                    )
                    .map_err(|e| {
                        error!(
                            "UCI JNI: sweep_data_jobject copy into jobjectarray failed: {:?}",
                            e
                        );
                        e
                    })?;
            }

            let radar_sweep_data_array_jobject =
                // Safety: radar_sweep_data_jobjectarray is safely instantiated above.
                unsafe { JObject::from_raw(radar_sweep_data_jobjectarray) };

            let radar_data_jclass = NotificationManagerAndroid::find_local_class(
                &mut self.jclass_map,
                &self.class_loader_obj,
                &self.env,
                UWB_RADAR_DATA_CLASS,
            )?;

            let radar_data_jobject = self
                .env
                .new_object(
                    radar_data_jclass,
                    "(JIIIII[L".to_owned() + UWB_RADAR_SWEEP_DATA_CLASS + ";)V",
                    &[
                        // session_token below has already been mapped to session_id by uci layer.
                        JValue::Long(radar_data_rcv_notification.session_token as i64),
                        JValue::Int(radar_data_rcv_notification.status as i32),
                        JValue::Int(radar_data_rcv_notification.radar_data_type as i32),
                        JValue::Int(radar_data_rcv_notification.samples_per_sweep as i32),
                        JValue::Int(radar_data_rcv_notification.bits_per_sample as i32),
                        JValue::Int(radar_data_rcv_notification.sweep_offset as i32),
                        JValue::Object(radar_sweep_data_array_jobject),
                    ],
                )
                .map_err(|e| {
                    error!("UCI JNI: UwbRadarData object creation failed: {:?}", e);
                    e
                })?;

            let method_sig = "(L".to_owned() + UWB_RADAR_DATA_CLASS + ";)V";

            self.cached_jni_call(
                "onRadarDataMessageReceived",
                &method_sig,
                &[jvalue::from(JValue::Object(radar_data_jobject))],
            )
        })
        .map_err(|_| UwbError::ForeignFunctionInterface)?;
        Ok(())
    }
}
pub(crate) struct NotificationManagerAndroidBuilder {
    pub chip_id: String,
    pub vm: &'static Arc<JavaVM>,
    pub class_loader_obj: GlobalRef,
    pub callback_obj: GlobalRef,
}

impl NotificationManagerBuilder for NotificationManagerAndroidBuilder {
    type NotificationManager = NotificationManagerAndroid;

    fn build(self) -> Option<Self::NotificationManager> {
        if let Ok(env) = self.vm.attach_current_thread() {
            Some(NotificationManagerAndroid {
                chip_id: self.chip_id,
                env,
                class_loader_obj: self.class_loader_obj,
                callback_obj: self.callback_obj,
                jmethod_id_map: HashMap::new(),
                jclass_map: HashMap::new(),
            })
        } else {
            None
        }
    }
}
