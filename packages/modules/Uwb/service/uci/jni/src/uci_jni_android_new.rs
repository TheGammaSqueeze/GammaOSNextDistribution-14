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

//! Implementation of JNI functions.

use crate::dispatcher::Dispatcher;
use crate::helper::{boolean_result_helper, byte_result_helper, option_result_helper};
use crate::jclass_name::{
    CONFIG_STATUS_DATA_CLASS, DT_RANGING_ROUNDS_STATUS_CLASS, POWER_STATS_CLASS, TLV_DATA_CLASS,
    UWB_DEVICE_INFO_RESPONSE_CLASS, UWB_RANGING_DATA_CLASS, VENDOR_RESPONSE_CLASS,
};
use crate::unique_jvm;

use std::convert::TryInto;
use std::iter::zip;

use jni::errors::Error as JNIError;
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::signature::ReturnType;
use jni::sys::{
    jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject, jobjectArray, jshort, jvalue,
};
use jni::JNIEnv;
use log::{debug, error};
use uwb_core::error::{Error, Result};
use uwb_core::params::{
    AndroidRadarConfigResponse, AppConfigTlv, CountryCode, GetDeviceInfoResponse, PhaseList,
    RadarConfigTlv, RawAppConfigTlv, RawUciMessage, SessionUpdateDtTagRangingRoundsResponse,
    SetAppConfigResponse, UpdateTime,
};
use uwb_uci_packets::{
    AppConfigTlvType, CapTlv, Controlee, Controlee_V2_0_16_Byte_Version,
    Controlee_V2_0_32_Byte_Version, Controlees, PowerStats, ResetConfig, SessionState, SessionType,
    StatusCode, UpdateMulticastListAction,
};

/// Macro capturing the name of the function calling this macro.
///
/// function_name()! -> &'static str
/// Returns the function name as 'static reference.
macro_rules! function_name {
    () => {{
        // Declares function f inside current function.
        fn f() {}
        fn type_name_of<T>(_: T) -> &'static str {
            std::any::type_name::<T>()
        }
        // type name of f is struct_or_crate_name::calling_function_name::f
        let name = type_name_of(f);
        // Find and cut the rest of the path:
        // Third to last character, up to the first semicolon: is calling_function_name
        match &name[..name.len() - 3].rfind(':') {
            Some(pos) => &name[pos + 1..name.len() - 3],
            None => &name[..name.len() - 3],
        }
    }};
}

/// Initialize native library. Captures VM:
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit(
    env: JNIEnv,
    _obj: JObject,
) -> jboolean {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("uwb")
            .with_max_level(log::LevelFilter::Trace)
            .with_filter("trace,jni=info"),
    );
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_init(env), function_name!())
}

fn native_init(env: JNIEnv) -> Result<()> {
    let jvm = env.get_java_vm().map_err(|_| Error::ForeignFunctionInterface)?;
    unique_jvm::set_once(jvm)
}

fn create_device_info_response(rsp: GetDeviceInfoResponse, env: JNIEnv) -> Result<jobject> {
    let device_info_response_class = env
        .find_class(UWB_DEVICE_INFO_RESPONSE_CLASS)
        .map_err(|_| Error::ForeignFunctionInterface)?;

    let vendor_spec_info_jbytearray = env
        .byte_array_from_slice(rsp.vendor_spec_info.as_ref())
        .map_err(|_| Error::ForeignFunctionInterface)?;
    // Safety: vendor_spec_info_jbytearray is safely instantiated above.
    let vendor_spec_info_jobject = unsafe { JObject::from_raw(vendor_spec_info_jbytearray) };

    match env.new_object(
        device_info_response_class,
        "(IIIII[B)V",
        &[
            JValue::Int(i32::from(rsp.status)),
            JValue::Int(rsp.uci_version as i32),
            JValue::Int(rsp.mac_version as i32),
            JValue::Int(rsp.phy_version as i32),
            JValue::Int(rsp.uci_test_version as i32),
            JValue::Object(vendor_spec_info_jobject),
        ],
    ) {
        Ok(o) => Ok(*o),
        Err(_) => Err(Error::ForeignFunctionInterface),
    }
}

/// Turn on Single UWB chip.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_do_initialize(env, obj, chip_id), function_name!()) {
        Some(rsp) => create_device_info_response(rsp, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_do_initialize(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> Result<GetDeviceInfoResponse> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.open_hal()
}

/// Turn off single UWB chip.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jboolean {
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_do_deinitialize(env, obj, chip_id), function_name!())
}

fn native_do_deinitialize(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.close_hal(true)
}

/// Get nanos. Not currently used and returns placeholder value.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(
    _env: JNIEnv,
    _obj: JObject,
) -> jlong {
    debug!("{}: enter", function_name!());
    0
}

/// Reset a single UWB device by sending UciDeviceReset command. Return value defined by
/// <AndroidRoot>/external/uwb/src/rust/uwb_uci_packets/uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset(
    env: JNIEnv,
    obj: JObject,
    _reset_config: jbyte,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_device_reset(env, obj, chip_id), function_name!())
}

fn native_device_reset(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.device_reset(ResetConfig::UwbsReset)
}

/// Init the session on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_session_init(env, obj, session_id, session_type, chip_id),
        function_name!(),
    )
}

fn native_session_init(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
    chip_id: JString,
) -> Result<()> {
    let session_type =
        SessionType::try_from(session_type as u8).map_err(|_| Error::BadParameters)?;
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.session_init(session_id as u32, session_type)
}

/// DeInit the session on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_session_deinit(env, obj, session_id, chip_id), function_name!())
}

fn native_session_deinit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.session_deinit(session_id as u32)
}

/// Get session count on a single UWB device. return -1 if failed
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_session_count(env, obj, chip_id), function_name!()) {
        // Max session count is 5, will not overflow i8
        Some(c) => c as i8,
        None => -1,
    }
}

fn native_get_session_count(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<u8> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.session_get_count()
}

/// Start ranging on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_ranging_start(env, obj, session_id, chip_id), function_name!())
}

fn native_ranging_start(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.range_start(session_id as u32)
}

/// Stop ranging on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_ranging_stop(env, obj, session_id, chip_id), function_name!())
}

fn native_ranging_stop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.range_stop(session_id as u32)
}

/// Get session stateon a single UWB device. Return -1 if failed
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_get_session_state(env, obj, session_id, chip_id),
        function_name!(),
    ) {
        // SessionState does not overflow i8
        Some(s) => s as i8,
        None => -1,
    }
}

fn native_get_session_state(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<SessionState> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.session_get_state(session_id as u32)
}

fn parse_app_config_tlv_vec(no_of_params: i32, mut byte_array: &[u8]) -> Result<Vec<AppConfigTlv>> {
    let mut parsed_tlvs_len = 0;
    let received_tlvs_len = byte_array.len();
    let mut tlvs = Vec::<AppConfigTlv>::new();
    for _ in 0..no_of_params {
        // The tlv consists of the type of payload in 1 byte, the length of payload as u8
        // in 1 byte, and the payload.
        const TLV_HEADER_SIZE: usize = 2;
        let tlv = RawAppConfigTlv::parse(byte_array).map_err(|_| Error::BadParameters)?;
        byte_array = byte_array.get(tlv.v.len() + TLV_HEADER_SIZE..).ok_or(Error::BadParameters)?;
        parsed_tlvs_len += tlv.v.len() + TLV_HEADER_SIZE;
        tlvs.push(tlv.into());
    }
    if parsed_tlvs_len != received_tlvs_len {
        return Err(Error::BadParameters);
    };
    Ok(tlvs)
}

fn parse_radar_config_tlv_vec(
    no_of_params: i32,
    mut byte_array: &[u8],
) -> Result<Vec<RadarConfigTlv>> {
    let mut parsed_tlvs_len = 0;
    let received_tlvs_len = byte_array.len();
    let mut tlvs = Vec::<RadarConfigTlv>::new();
    for _ in 0..no_of_params {
        // The tlv consists of the type of payload in 1 byte, the length of payload as u8
        // in 1 byte, and the payload.
        const TLV_HEADER_SIZE: usize = 2;
        let tlv = RadarConfigTlv::parse(byte_array).map_err(|_| Error::BadParameters)?;
        byte_array = byte_array.get(tlv.v.len() + TLV_HEADER_SIZE..).ok_or(Error::BadParameters)?;
        parsed_tlvs_len += tlv.v.len() + TLV_HEADER_SIZE;
        tlvs.push(tlv);
    }
    if parsed_tlvs_len != received_tlvs_len {
        return Err(Error::BadParameters);
    };
    Ok(tlvs)
}

fn create_radar_config_response(
    response: AndroidRadarConfigResponse,
    env: JNIEnv,
) -> Result<jbyteArray> {
    let uwb_config_status_class =
        env.find_class(CONFIG_STATUS_DATA_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    let mut buf = Vec::<u8>::new();
    for config_status in &response.config_status {
        buf.push(u8::from(config_status.cfg_id));
        buf.push(u8::from(config_status.status));
    }
    let config_status_jbytearray =
        env.byte_array_from_slice(&buf).map_err(|_| Error::ForeignFunctionInterface)?;

    // Safety: config_status_jbytearray is safely instantiated above.
    let config_status_jobject = unsafe { JObject::from_raw(config_status_jbytearray) };
    let config_status_jobject = env
        .new_object(
            uwb_config_status_class,
            "(II[B)V",
            &[
                JValue::Int(i32::from(response.status)),
                JValue::Int(response.config_status.len() as i32),
                JValue::Object(config_status_jobject),
            ],
        )
        .map_err(|_| Error::ForeignFunctionInterface)?;
    Ok(*config_status_jobject)
}

fn create_set_config_response(response: SetAppConfigResponse, env: JNIEnv) -> Result<jbyteArray> {
    let uwb_config_status_class =
        env.find_class(CONFIG_STATUS_DATA_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    let mut buf = Vec::<u8>::new();
    for config_status in &response.config_status {
        buf.push(u8::from(config_status.cfg_id));
        buf.push(u8::from(config_status.status));
    }
    let config_status_jbytearray =
        env.byte_array_from_slice(&buf).map_err(|_| Error::ForeignFunctionInterface)?;

    // Safety: config_status_jbytearray is safely instantiated above.
    let config_status_jobject = unsafe { JObject::from_raw(config_status_jbytearray) };
    let config_status_jobject = env
        .new_object(
            uwb_config_status_class,
            "(II[B)V",
            &[
                JValue::Int(i32::from(response.status)),
                JValue::Int(response.config_status.len() as i32),
                JValue::Object(config_status_jobject),
            ],
        )
        .map_err(|_| Error::ForeignFunctionInterface)?;
    Ok(*config_status_jobject)
}

/// Set app configurations on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    _app_config_param_len: jint, // TODO(ningyuan): Obsolete parameter
    app_config_params: jbyteArray,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_set_app_configurations(
            env,
            obj,
            session_id,
            no_of_params,
            app_config_params,
            chip_id,
        ),
        function_name!(),
    ) {
        Some(config_response) => create_set_config_response(config_response, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_set_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> Result<SetAppConfigResponse> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let config_byte_array =
        env.convert_byte_array(app_config_params).map_err(|_| Error::ForeignFunctionInterface)?;
    let tlvs = parse_app_config_tlv_vec(no_of_params, &config_byte_array)?;
    uci_manager.session_set_app_config(session_id as u32, tlvs)
}

/// Set radar app configurations on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetRadarAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    _radar_config_param_len: jint,
    radar_config_params: jbyteArray,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_set_radar_app_configurations(
            env,
            obj,
            session_id,
            no_of_params,
            radar_config_params,
            chip_id,
        ),
        function_name!(),
    ) {
        Some(config_response) => create_radar_config_response(config_response, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_set_radar_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    radar_config_params: jbyteArray,
    chip_id: JString,
) -> Result<AndroidRadarConfigResponse> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let config_byte_array =
        env.convert_byte_array(radar_config_params).map_err(|_| Error::ForeignFunctionInterface)?;
    let tlvs = parse_radar_config_tlv_vec(no_of_params, &config_byte_array)?;
    uci_manager.android_set_radar_config(session_id as u32, tlvs)
}

fn parse_hybrid_config_phase_list_vec(
    number_of_phases: usize,
    byte_array: &[u8],
) -> Result<Vec<PhaseList>> {
    let mut parsed_phase_lists_len = 0;
    let received_phase_list_len = byte_array.len();
    let mut phase_lists = Vec::with_capacity(number_of_phases);
    // The PhaseList consists of session handle as u32 in 4 bytes, Start Slot Index as u16
    // in 2 byte and End Slot Index as u16 in 2 bytes
    const PHASE_LIST_SIZE: usize = 8;
    for chunk in byte_array.chunks_exact(PHASE_LIST_SIZE) {
        let phase_list = PhaseList::parse(chunk).map_err(|_| Error::BadParameters)?;
        parsed_phase_lists_len += PHASE_LIST_SIZE;
        phase_lists.push(phase_list);
    }

    if parsed_phase_lists_len != received_phase_list_len {
        return Err(Error::BadParameters);
    }
    Ok(phase_lists)
}

/// Set hybrid session configurations. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetHybridSessionConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    number_of_phases: jint,
    update_time: jbyteArray,
    phase_list: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_set_hybrid_session_configurations(
            env,
            obj,
            session_id,
            number_of_phases,
            update_time,
            phase_list,
            chip_id,
        ),
        function_name!(),
    )
}

fn native_set_hybrid_session_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    number_of_phases: jint,
    update_time: jbyteArray,
    phase_list: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let phase_list_bytes =
        env.convert_byte_array(phase_list).map_err(|_| Error::ForeignFunctionInterface)?;
    let phase_list_vec =
        parse_hybrid_config_phase_list_vec(number_of_phases as usize, &phase_list_bytes)?;

    let update_time_bytes =
        env.convert_byte_array(update_time).map_err(|_| Error::ForeignFunctionInterface)?;
    let update_time_array: [u8; 8] =
        TryFrom::try_from(&update_time_bytes[..]).map_err(|_| Error::BadParameters)?;

    uci_manager.session_set_hybrid_config(
        session_id as u32,
        number_of_phases as u8,
        UpdateTime::new(&update_time_array).unwrap(),
        phase_list_vec,
    )
}

fn create_get_config_response(tlvs: Vec<AppConfigTlv>, env: JNIEnv) -> Result<jbyteArray> {
    let tlv_data_class =
        env.find_class(TLV_DATA_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    let tlvs_len = tlvs.len();
    let mut buf = Vec::<u8>::new();
    for tlv in tlvs.into_iter() {
        let tlv = tlv.into_inner();
        buf.push(u8::from(tlv.cfg_id));
        buf.push(tlv.v.len() as u8);
        buf.extend(&tlv.v);
    }
    let tlvs_jbytearray =
        env.byte_array_from_slice(&buf).map_err(|_| Error::ForeignFunctionInterface)?;

    // Safety: tlvs_jbytearray is safely instantiated above.
    let tlvs_jobject = unsafe { JObject::from_raw(tlvs_jbytearray) };
    let tlvs_jobject_env = env
        .new_object(
            tlv_data_class,
            "(II[B)V",
            &[
                JValue::Int(i32::from(StatusCode::UciStatusOk)),
                JValue::Int(tlvs_len as i32),
                JValue::Object(tlvs_jobject),
            ],
        )
        .map_err(|_| Error::ForeignFunctionInterface)?;
    Ok(*tlvs_jobject_env)
}

/// Get app configurations on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    _no_of_params: jint,
    _app_config_param_len: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_get_app_configurations(env, obj, session_id, app_config_params, chip_id),
        function_name!(),
    ) {
        Some(v) => create_get_config_response(v, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_app_configurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    app_config_params: jbyteArray,
    chip_id: JString,
) -> Result<Vec<AppConfigTlv>> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let app_config_bytearray =
        env.convert_byte_array(app_config_params).map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.session_get_app_config(
        session_id as u32,
        app_config_bytearray
            .into_iter()
            .map(AppConfigTlvType::try_from)
            .map(std::result::Result::ok)
            .collect::<Option<Vec<_>>>()
            .ok_or(Error::BadParameters)?,
    )
}

fn create_cap_response(tlvs: Vec<CapTlv>, env: JNIEnv) -> Result<jbyteArray> {
    let tlv_data_class =
        env.find_class(TLV_DATA_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    let mut buf = Vec::<u8>::new();
    for tlv in &tlvs {
        buf.push(u8::from(tlv.t));
        buf.push(tlv.v.len() as u8);
        buf.extend(&tlv.v);
    }
    let tlvs_jbytearray =
        env.byte_array_from_slice(&buf).map_err(|_| Error::ForeignFunctionInterface)?;

    // Safety: tlvs_jbytearray is safely instantiated above.
    let tlvs_jobject = unsafe { JObject::from_raw(tlvs_jbytearray) };
    let tlvs_jobject_env = env
        .new_object(
            tlv_data_class,
            "(II[B)V",
            &[
                JValue::Int(i32::from(StatusCode::UciStatusOk)),
                JValue::Int(tlvs.len() as i32),
                JValue::Object(tlvs_jobject),
            ],
        )
        .map_err(|_| Error::ForeignFunctionInterface)?;
    Ok(*tlvs_jobject_env)
}

/// Get capability info on a single UWB device. Return null JObject if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jbyteArray {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_caps_info(env, obj, chip_id), function_name!()) {
        Some(v) => create_cap_response(v, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_caps_info(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<Vec<CapTlv>> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.core_get_caps_info()
}

/// Update multicast list on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jbyteArray,
    sub_session_ids: jintArray,
    sub_session_keys: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_controller_multicast_list_update(
            env,
            obj,
            session_id,
            action,
            no_of_controlee,
            addresses,
            sub_session_ids,
            sub_session_keys,
            chip_id,
        ),
        function_name!(),
    )
}

// Function is used only once that copies arguments from JNI
#[allow(clippy::too_many_arguments)]
fn native_controller_multicast_list_update(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jbyteArray,
    sub_session_ids: jintArray,
    sub_session_keys: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;

    let addresses_bytes =
        env.convert_byte_array(addresses).map_err(|_| Error::ForeignFunctionInterface)?;

    let address_list: Vec<[u8; 2]> =
        addresses_bytes.chunks_exact(2).map(|chunk| [chunk[0], chunk[1]]).collect();

    let mut sub_session_id_list = vec![
        0i32;
        env.get_array_length(sub_session_ids)
            .map_err(|_| Error::ForeignFunctionInterface)?
            .try_into()
            .map_err(|_| Error::BadParameters)?
    ];
    env.get_int_array_region(sub_session_ids, 0, &mut sub_session_id_list)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    if address_list.len() != sub_session_id_list.len()
        || address_list.len() != no_of_controlee as usize
    {
        return Err(Error::BadParameters);
    }
    let controlee_list = match UpdateMulticastListAction::try_from(action as u8)
        .map_err(|_| Error::BadParameters)?
    {
        UpdateMulticastListAction::AddControlee | UpdateMulticastListAction::RemoveControlee => {
            Controlees::NoSessionKey(
                zip(address_list, sub_session_id_list)
                    .map(|(a, s)| Controlee { short_address: a, subsession_id: s as u32 })
                    .collect::<Vec<Controlee>>(),
            )
        }
        UpdateMulticastListAction::AddControleeWithShortSubSessionKey => {
            if sub_session_keys.is_null() {
                Controlees::NoSessionKey(
                    zip(address_list, sub_session_id_list)
                        .map(|(a, s)| Controlee { short_address: a, subsession_id: s as u32 })
                        .collect::<Vec<Controlee>>(),
                )
            } else {
                Controlees::ShortSessionKey(
                    zip(
                        zip(address_list, sub_session_id_list),
                        env.convert_byte_array(sub_session_keys)
                            .map_err(|_| Error::ForeignFunctionInterface)?
                            .chunks(16),
                    )
                    .map(|((address, id), key)| {
                        Ok(Controlee_V2_0_16_Byte_Version {
                            short_address: address,
                            subsession_id: id as u32,
                            subsession_key: key.try_into().map_err(|_| Error::BadParameters)?,
                        })
                    })
                    .collect::<Result<Vec<Controlee_V2_0_16_Byte_Version>>>()?,
                )
            }
        }
        UpdateMulticastListAction::AddControleeWithLongSubSessionKey => {
            if sub_session_keys.is_null() {
                Controlees::NoSessionKey(
                    zip(address_list, sub_session_id_list)
                        .map(|(a, s)| Controlee { short_address: a, subsession_id: s as u32 })
                        .collect::<Vec<Controlee>>(),
                )
            } else {
                Controlees::LongSessionKey(
                    zip(
                        zip(address_list, sub_session_id_list),
                        env.convert_byte_array(sub_session_keys)
                            .map_err(|_| Error::ForeignFunctionInterface)?
                            .chunks(32),
                    )
                    .map(|((address, id), key)| {
                        Ok(Controlee_V2_0_32_Byte_Version {
                            short_address: address,
                            subsession_id: id as u32,
                            subsession_key: key.try_into().map_err(|_| Error::BadParameters)?,
                        })
                    })
                    .collect::<Result<Vec<Controlee_V2_0_32_Byte_Version>>>()?,
                )
            }
        }
    };
    uci_manager.session_update_controller_multicast_list(
        session_id as u32,
        UpdateMulticastListAction::try_from(action as u8).map_err(|_| Error::BadParameters)?,
        controlee_list,
    )
}

/// Set country code on a single UWB device. Return value defined by uci_packets.pdl
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(native_set_country_code(env, obj, country_code, chip_id), function_name!())
}

fn native_set_country_code(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let country_code =
        env.convert_byte_array(country_code).map_err(|_| Error::ForeignFunctionInterface)?;
    debug!("Country code: {:?}", country_code);
    if country_code.len() != 2 {
        return Err(Error::BadParameters);
    }
    uci_manager.android_set_country_code(
        CountryCode::new(&[country_code[0], country_code[1]]).ok_or(Error::BadParameters)?,
    )
}

/// Set log mode.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetLogMode(
    env: JNIEnv,
    obj: JObject,
    log_mode_jstring: JString,
) -> jboolean {
    debug!("{}: enter", function_name!());
    boolean_result_helper(native_set_log_mode(env, obj, log_mode_jstring), function_name!())
}

fn native_set_log_mode(env: JNIEnv, obj: JObject, log_mode_jstring: JString) -> Result<()> {
    let dispatcher = Dispatcher::get_dispatcher(env, obj)?;
    let logger_mode_str = String::from(
        env.get_string(log_mode_jstring).map_err(|_| Error::ForeignFunctionInterface)?,
    );
    debug!("UCI log: log started in {} mode", &logger_mode_str);
    let logger_mode = logger_mode_str.try_into()?;
    dispatcher.set_logger_mode(logger_mode)
}

// # Safety
//
// For this to be safe, the validity of msg should be checked before calling.
unsafe fn create_vendor_response(msg: RawUciMessage, env: JNIEnv) -> Result<jobject> {
    let vendor_response_class =
        env.find_class(VENDOR_RESPONSE_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;

    // Safety: the byte array jobject is just constructed so it must be valid.
    let payload_jobject = unsafe {
        JObject::from_raw(
            env.byte_array_from_slice(&msg.payload).map_err(|_| Error::ForeignFunctionInterface)?,
        )
    };

    match env.new_object(
        vendor_response_class,
        "(BII[B)V",
        &[
            JValue::Byte(u8::from(StatusCode::UciStatusOk) as i8),
            JValue::Int(msg.gid as i32),
            JValue::Int(msg.oid as i32),
            JValue::Object(payload_jobject),
        ],
    ) {
        Ok(obj) => Ok(*obj),
        Err(_) => Err(Error::ForeignFunctionInterface),
    }
}

fn create_invalid_vendor_response(env: JNIEnv) -> Result<jobject> {
    let vendor_response_class =
        env.find_class(VENDOR_RESPONSE_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    match env.new_object(
        vendor_response_class,
        "(BII[B)V",
        &[
            JValue::Byte(u8::from(StatusCode::UciStatusFailed) as i8),
            JValue::Int(-1),
            JValue::Int(-1),
            JValue::Object(JObject::null()),
        ],
    ) {
        Ok(obj) => Ok(*obj),
        Err(_) => Err(Error::ForeignFunctionInterface),
    }
}

/// # Safety
///
/// `response` should be checked before calling to ensure safety.
unsafe fn create_ranging_round_status(
    response: SessionUpdateDtTagRangingRoundsResponse,
    env: JNIEnv,
) -> Result<jobject> {
    let dt_ranging_rounds_update_status_class = env
        .find_class(DT_RANGING_ROUNDS_STATUS_CLASS)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let indexes = response.ranging_round_indexes;

    // Safety: the byte array jobject is just constructed so it must be valid.
    let indexes_jobject = unsafe {
        JObject::from_raw(
            env.byte_array_from_slice(indexes.as_ref())
                .map_err(|_| Error::ForeignFunctionInterface)?,
        )
    };

    match env.new_object(
        dt_ranging_rounds_update_status_class,
        "(II[B)V",
        &[
            JValue::Int(i32::from(response.status)),
            JValue::Int(indexes.len() as i32),
            JValue::Object(indexes_jobject),
        ],
    ) {
        Ok(o) => Ok(*o),
        Err(_) => Err(Error::ForeignFunctionInterface),
    }
}

/// Send Raw vendor command on a single UWB device. Returns an invalid response if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSendRawVendorCmd(
    env: JNIEnv,
    obj: JObject,
    mt: jint,
    gid: jint,
    oid: jint,
    payload_jarray: jbyteArray,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_send_raw_vendor_cmd(env, obj, mt, gid, oid, payload_jarray, chip_id),
        function_name!(),
    ) {
        // Note: unwrap() here is not desirable, but unavoidable given non-null object is returned
        // even for failing cases.

        // Safety: create_vendor_response is unsafe, however msg is safely returned from
        // native_send_raw_vendor_cmd.
        Some(msg) => unsafe {
            create_vendor_response(msg, env)
                .map_err(|e| {
                    error!("{} failed with {:?}", function_name!(), &e);
                    e
                })
                .unwrap_or_else(|_| create_invalid_vendor_response(env).unwrap())
        },
        None => create_invalid_vendor_response(env).unwrap(),
    }
}

fn native_send_raw_vendor_cmd(
    env: JNIEnv,
    obj: JObject,
    mt: jint,
    gid: jint,
    oid: jint,
    payload_jarray: jbyteArray,
    chip_id: JString,
) -> Result<RawUciMessage> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let payload =
        env.convert_byte_array(payload_jarray).map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.raw_uci_cmd(mt as u32, gid as u32, oid as u32, payload)
}

fn create_power_stats(power_stats: PowerStats, env: JNIEnv) -> Result<jobject> {
    let power_stats_class =
        env.find_class(POWER_STATS_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    match env.new_object(
        power_stats_class,
        "(IIII)V",
        &[
            JValue::Int(power_stats.idle_time_ms as i32),
            JValue::Int(power_stats.tx_time_ms as i32),
            JValue::Int(power_stats.rx_time_ms as i32),
            JValue::Int(power_stats.total_wake_count as i32),
        ],
    ) {
        Ok(o) => Ok(*o),
        Err(_) => Err(Error::ForeignFunctionInterface),
    }
}

/// Get UWB power stats on a single UWB device. Returns a null object if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_get_power_stats(env, obj, chip_id), function_name!()) {
        Some(ps) => create_power_stats(ps, env)
            .map_err(|e| {
                error!("{} failed with {:?}", function_name!(), &e);
                e
            })
            .unwrap_or(*JObject::null()),
        None => *JObject::null(),
    }
}

fn native_get_power_stats(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<PowerStats> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    uci_manager.android_get_power_stats()
}

/// Update ranging rounds for DT-TAG
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionUpdateDtTagRangingRounds(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    _ranging_rounds: jint,
    ranging_round_indexes: jbyteArray,
    chip_id: JString,
) -> jobject {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_set_ranging_rounds_dt_tag(
            env,
            obj,
            session_id as u32,
            ranging_round_indexes,
            chip_id,
        ),
        function_name!(),
    ) {
        // Safety: rr is safely returned from native_set_ranging_rounds_dt_tag
        Some(rr) => unsafe {
            create_ranging_round_status(rr, env)
                .map_err(|e| {
                    error!("{} failed with {:?}", function_name!(), &e);
                    e
                })
                .unwrap_or(*JObject::null())
        },
        None => *JObject::null(),
    }
}

fn native_set_ranging_rounds_dt_tag(
    env: JNIEnv,
    obj: JObject,
    session_id: u32,
    ranging_round_indexes: jbyteArray,
    chip_id: JString,
) -> Result<SessionUpdateDtTagRangingRoundsResponse> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)?;
    let indexes = env
        .convert_byte_array(ranging_round_indexes)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.session_update_dt_tag_ranging_rounds(session_id, indexes)
}

/// Send a data packet to the remote device.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSendData(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    address: jbyteArray,
    uci_sequence_number: jshort,
    app_payload_data: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_send_data(
            env,
            obj,
            session_id,
            address,
            uci_sequence_number,
            app_payload_data,
            chip_id,
        ),
        function_name!(),
    )
}

#[allow(clippy::too_many_arguments)]
fn native_send_data(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    address: jbyteArray,
    uci_sequence_number: jshort,
    app_payload_data: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let address_bytearray =
        env.convert_byte_array(address).map_err(|_| Error::ForeignFunctionInterface)?;
    let app_payload_data_bytearray =
        env.convert_byte_array(app_payload_data).map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.send_data_packet(
        session_id as u32,
        address_bytearray,
        uci_sequence_number as u16,
        app_payload_data_bytearray,
    )
}

/// Get max application data size, that can be sent by the UWBS. Return 0 if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeQueryDataSize(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jshort {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_query_data_size(env, obj, session_id, chip_id),
        function_name!(),
    ) {
        Some(s) => s.try_into().unwrap(),
        None => 0,
    }
}

fn native_query_data_size(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<u16> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.session_query_max_data_size(session_id as u32)
}

/// Set data transfer phase configuration
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDataTransferPhaseConfig(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    dtpcm_repetition: jbyte,
    data_transfer_control: jbyte,
    dtpml_size: jbyte,
    mac_address: jbyteArray,
    slot_bitmap: jbyteArray,
    chip_id: JString,
) -> jbyte {
    debug!("{}: enter", function_name!());
    byte_result_helper(
        native_session_data_transfer_phase_config(
            env,
            obj,
            session_id,
            dtpcm_repetition,
            data_transfer_control,
            dtpml_size,
            mac_address,
            slot_bitmap,
            chip_id,
        ),
        function_name!(),
    )
}

#[allow(clippy::too_many_arguments)]
fn native_session_data_transfer_phase_config(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    dtpcm_repetition: jbyte,
    data_transfer_control: jbyte,
    dtpml_size: jbyte,
    mac_address: jbyteArray,
    slot_bitmap: jbyteArray,
    chip_id: JString,
) -> Result<()> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.session_data_transfer_phase_config(
        session_id as u32,
        dtpcm_repetition as u8,
        data_transfer_control as u8,
        dtpml_size as u8,
        env.convert_byte_array(mac_address).map_err(|_| Error::ForeignFunctionInterface)?,
        env.convert_byte_array(slot_bitmap).map_err(|_| Error::ForeignFunctionInterface)?,
    )
}

/// Get UWBS timestamp, Return 0 if failed.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeQueryUwbTimestamp(
    env: JNIEnv,
    obj: JObject,
    chip_id: JString,
) -> jlong {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_query_time_stamp(env, obj, chip_id), function_name!()) {
        Some(s) => s.try_into().unwrap(),
        None => 0,
    }
}

fn native_query_time_stamp(env: JNIEnv, obj: JObject, chip_id: JString) -> Result<u64> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.core_query_uwb_timestamp()
}

/// Get session token for the UWB session.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionToken(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> jlong {
    debug!("{}: enter", function_name!());
    match option_result_helper(
        native_get_session_token(env, obj, session_id, chip_id),
        function_name!(),
    ) {
        Some(s) => s.try_into().unwrap(),
        None => 0,
    }
}

fn native_get_session_token(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    chip_id: JString,
) -> Result<u32> {
    let uci_manager = Dispatcher::get_uci_manager(env, obj, chip_id)
        .map_err(|_| Error::ForeignFunctionInterface)?;
    uci_manager.get_session_token(session_id as u32)
}

/// Get the class loader object. Has to be called from a JNIEnv where the local java classes are
/// loaded. Results in a global reference to the class loader object that can be used to look for
/// classes in other native thread.
fn get_class_loader_obj(env: &JNIEnv) -> Result<GlobalRef> {
    let ranging_data_class =
        env.find_class(UWB_RANGING_DATA_CLASS).map_err(|_| Error::ForeignFunctionInterface)?;
    let ranging_data_class_class =
        env.get_object_class(ranging_data_class).map_err(|_| Error::ForeignFunctionInterface)?;
    let get_class_loader_method = env
        .get_method_id(ranging_data_class_class, "getClassLoader", "()Ljava/lang/ClassLoader;")
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let class_loader = env
        .call_method_unchecked(
            ranging_data_class,
            get_class_loader_method,
            ReturnType::Object,
            &[jvalue::from(JValue::Void)],
        )
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let class_loader_jobject = class_loader.l().map_err(|_| Error::ForeignFunctionInterface)?;
    env.new_global_ref(class_loader_jobject).map_err(|_| Error::ForeignFunctionInterface)
}

/// Create the dispatcher. Returns pointer to Dispatcher casted as jlong that owns the dispatcher.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherNew(
    env: JNIEnv,
    obj: JObject,
    chip_ids_jarray: jobjectArray,
) -> jlong {
    debug!("{}: enter", function_name!());
    match option_result_helper(native_dispatcher_new(env, obj, chip_ids_jarray), function_name!()) {
        Some(ptr) => ptr as jlong,
        None => *JObject::null() as jlong,
    }
}

fn native_dispatcher_new(
    env: JNIEnv,
    obj: JObject,
    chip_ids_jarray: jobjectArray,
) -> Result<*const Dispatcher> {
    let chip_ids_len: i32 =
        env.get_array_length(chip_ids_jarray).map_err(|_| Error::ForeignFunctionInterface)?;
    let chip_ids = (0..chip_ids_len)
        .map(|i| env.get_string(env.get_object_array_element(chip_ids_jarray, i)?.into()))
        .collect::<std::result::Result<Vec<_>, JNIError>>()
        .map_err(|_| Error::ForeignFunctionInterface)?;
    let chip_ids = chip_ids.into_iter().map(String::from).collect::<Vec<String>>();
    let class_loader_obj = get_class_loader_obj(&env)?;
    Dispatcher::new_dispatcher(
        unique_jvm::get_static_ref().ok_or(Error::Unknown)?,
        class_loader_obj,
        env.new_global_ref(obj).map_err(|_| Error::ForeignFunctionInterface)?,
        &chip_ids,
    )?;
    Dispatcher::get_dispatcher_ptr()
}

/// Destroys the dispatcher.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherDestroy(
    env: JNIEnv,
    obj: JObject,
) {
    debug!("{}: enter", function_name!());
    if option_result_helper(native_dispatcher_destroy(env, obj), function_name!()).is_some() {
        debug!("The dispatcher is successfully destroyed.");
    }
}

fn native_dispatcher_destroy(env: JNIEnv, obj: JObject) -> Result<()> {
    let dispatcher_ptr_long = env
        .get_field(obj, "mDispatcherPointer", "J")
        .map_err(|_| Error::ForeignFunctionInterface)?
        .j()
        .map_err(|_| Error::ForeignFunctionInterface)?;
    if Dispatcher::get_dispatcher_ptr()? as jlong == dispatcher_ptr_long {
        Dispatcher::destroy_dispatcher()
    } else {
        Err(Error::BadParameters)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use tokio::runtime::Builder;
    use uwb_core::uci::mock_uci_manager::MockUciManager;
    use uwb_core::uci::uci_manager_sync::{
        NotificationManager, NotificationManagerBuilder, UciManagerSync,
    };
    use uwb_core::uci::{
        CoreNotification, DataRcvNotification, RadarDataRcvNotification, SessionNotification,
    };

    struct NullNotificationManager {}
    impl NotificationManager for NullNotificationManager {
        fn on_core_notification(&mut self, _core_notification: CoreNotification) -> Result<()> {
            Ok(())
        }
        fn on_session_notification(
            &mut self,
            _session_notification: SessionNotification,
        ) -> Result<()> {
            Ok(())
        }
        fn on_vendor_notification(&mut self, _vendor_notification: RawUciMessage) -> Result<()> {
            Ok(())
        }
        fn on_data_rcv_notification(&mut self, _data_rcv_notf: DataRcvNotification) -> Result<()> {
            Ok(())
        }
        /// Callback for RadarDataRcvNotification.
        fn on_radar_data_rcv_notification(
            &mut self,
            _radar_data_rcv_notification: RadarDataRcvNotification,
        ) -> Result<()> {
            Ok(())
        }
    }

    struct NullNotificationManagerBuilder {}

    impl NullNotificationManagerBuilder {
        fn new() -> Self {
            Self {}
        }
    }

    impl NotificationManagerBuilder for NullNotificationManagerBuilder {
        type NotificationManager = NullNotificationManager;

        fn build(self) -> Option<Self::NotificationManager> {
            Some(NullNotificationManager {})
        }
    }

    /// Checks validity of the function_name! macro.
    #[test]
    fn test_function_name() {
        assert_eq!(function_name!(), "test_function_name");
    }

    /// Checks native_set_app_configurations by mocking non-jni logic.
    #[test]
    fn test_native_set_app_configurations() {
        // Constructs mock UciManagerSync.
        let test_rt = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut uci_manager_impl = MockUciManager::new();
        uci_manager_impl.expect_session_set_app_config(
            42, // Session id
            vec![
                AppConfigTlv::new(AppConfigTlvType::DeviceType, vec![1]),
                AppConfigTlv::new(AppConfigTlvType::RangingRoundUsage, vec![1]),
            ],
            vec![],
            Ok(SetAppConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] }),
        );
        let uci_manager_sync = UciManagerSync::new_mock(
            uci_manager_impl,
            test_rt.handle().to_owned(),
            NullNotificationManagerBuilder::new(),
        )
        .unwrap();

        let app_config_byte_array: Vec<u8> = vec![
            0, 1, 1, // DeviceType: controller
            1, 1, 1, // RangingRoundUsage: DS_TWR
        ];
        let tlvs = parse_app_config_tlv_vec(2, &app_config_byte_array).unwrap();
        assert!(uci_manager_sync.session_set_app_config(42, tlvs).is_ok());
    }
}
