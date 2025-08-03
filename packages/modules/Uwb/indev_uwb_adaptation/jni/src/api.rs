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

//! This module exposes the native JNI API for communicating with the UWB core service.
//!
//! Internally after the UWB core service is instantiated, the pointer to the service is saved
//! on the calling Java side.
use jni::objects::{GlobalRef, JObject, JValue};
use jni::signature::ReturnType;
use jni::sys::{jboolean, jbyte, jbyteArray, jint, jlong, jobject, jvalue};
use jni::JNIEnv;
use log::{debug, error};
use tokio::runtime::Runtime;

use uci_hal_android::uci_hal_android::UciHalAndroid;
use uwb_core::params::{AppConfigParams, CountryCode};
use uwb_core::service::{default_runtime, UwbService, UwbServiceBuilder};
use uwb_core::uci::pcapng_uci_logger_factory::PcapngUciLoggerFactoryBuilder;
use uwb_uci_packets::{SessionType, UpdateMulticastListAction};

use crate::callback::UwbServiceCallbackBuilderImpl;
use crate::context::JniContext;
use crate::error::{Error, Result};
use crate::object_mapping::{
    CccOpenRangingParamsJni, CountryCodeJni, FiraControleeParamsJni, FiraOpenSessionParamsJni,
    PowerStatsJni, PowerStatsWithEnv,
};
use crate::unique_jvm;

/// Initialize native logging and capture static JavaVM ref
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeInit(
    env: JNIEnv,
    obj: JObject,
) {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("uwb")
            .with_max_level(log::LevelFilter::Trace)
            .with_filter("trace,jni=info"),
    );

    match env.get_java_vm() {
        Ok(vm) => {
            unique_jvm::set_once(vm);
        }
        Err(err) => {
            error!("Couldn't get a JavaVM from JNIEnv: {:?}", err);
        }
    };
}

/// Create a new UWB service and return the pointer
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeUwbServiceNew(
    env: JNIEnv,
    obj: JObject,
) -> jlong {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeUwbServiceNew : enter");
    let vm = match unique_jvm::get_static_ref() {
        Some(vm_ref) => vm_ref,
        None => {
            error!("Failed to get JavaVM reference");
            return *JObject::null() as jlong;
        }
    };
    let callback_obj = match env.new_global_ref(obj) {
        Ok(cb) => cb,
        Err(err) => {
            error!("Couldn't create global ref for callback obj: {:?}", err);
            return *JObject::null() as jlong;
        }
    };
    let class_loader_obj = match get_class_loader_obj(&env) {
        Ok(cl) => cl,
        Err(err) => {
            error!("Couldn't get class loader obj: {:?}", err);
            return *JObject::null() as jlong;
        }
    };
    let runtime = match default_runtime() {
        Some(r) => r,
        None => {
            error!("Couldn't build tokio Runtime.");
            return *JObject::null() as jlong;
        }
    };
    let uci_logger_factory = match PcapngUciLoggerFactoryBuilder::new()
        .log_path("/data/misc/apexdata/com.android.uwb/log".into())
        .filename_prefix("uwb_uci".to_owned())
        .runtime_handle(runtime.handle().to_owned())
        .build()
    {
        Some(m) => m,
        None => {
            error!("Couldn't build uci_logger_factory.");
            return *JObject::null() as jlong;
        }
    };
    if let Some(uwb_service) = UwbServiceBuilder::new()
        .runtime_handle(runtime.handle().to_owned())
        .callback_builder(UwbServiceCallbackBuilderImpl::new(vm, callback_obj, class_loader_obj))
        .uci_hal(UciHalAndroid::new("default"))
        .uci_logger_factory(uci_logger_factory)
        .build()
    {
        let service_wrapper = UwbServiceWrapper::new(uwb_service, runtime);
        return Box::into_raw(Box::new(service_wrapper)) as jlong;
    }

    error!("Failed to create Uwb Service");
    *JObject::null() as jlong
}

/// Destroy the UWB service object
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeUwbServiceDestroy(
    env: JNIEnv,
    obj: JObject,
) {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeUwbServiceDestroy : enter");
    let ctx = JniContext::new(env, obj);
    let uwb_service_ptr = match ctx.long_getter("getUwbServicePtr") {
        Ok(val) => val,
        Err(err) => {
            error!("Failed to get pointer value with: {:?}", err);
            return;
        }
    };

    unsafe {
        drop(Box::from_raw(uwb_service_ptr as *mut UwbServiceWrapper));
    }
    debug!("Uwb Service successfully destroyed.");
}

/// Enable the UWB service
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeEnable(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeEnable : enter");
    boolean_result_helper(enable(JniContext::new(env, obj)), "enable")
}

/// Disable the UWB service
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeDisable(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeDisable : enter");
    boolean_result_helper(disable(JniContext::new(env, obj)), "disable")
}

/// Initialize a new UWB session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeInitSession(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
    app_config_params: JObject,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeInitSession : enter");
    boolean_result_helper(
        init_session(JniContext::new(env, obj), session_id, session_type, app_config_params),
        "init_session",
    )
}

/// De-initialize an existing UWB session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeDeinitSession(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeDeinitSession : enter");
    boolean_result_helper(
        deinit_session(JniContext::new(env, obj), session_id as u32),
        "deinit_session",
    )
}

/// Start ranging for an existing UWB session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeStartRanging(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    // TODO(cante): figure out what to do with the return object from start raning
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeStartRanging : enter");
    boolean_result_helper(
        start_ranging(JniContext::new(env, obj), session_id as u32),
        "start_raning",
    )
}

/// Stop ranging
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeStopRanging(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeStopRanging : enter");
    boolean_result_helper(
        stop_ranging(JniContext::new(env, obj), session_id as u32),
        "stop_ranging",
    )
}

/// Reconfigure an existing UWB sessions with the given parameters
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeReconfigure(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    app_config_params: JObject,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeReconfigure( : enter");
    boolean_result_helper(
        reconfigure(JniContext::new(env, obj), session_id as u32, app_config_params),
        "reconfigure",
    )
}

/// Update controller multicast list
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeUpdateControllerMulticastList(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    update_multicast_list_action: jbyte,
    controlees: JObject,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeUpdateControllerMulticastList");
    boolean_result_helper(
        update_controller_multicast_list(
            JniContext::new(env, obj),
            session_id,
            update_multicast_list_action,
            controlees,
        ),
        "update_controller_multicast_list",
    )
}

/// Set country code
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeSetCountryCode(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
) -> jboolean {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeSetCountryCode: enter");
    boolean_result_helper(
        set_country_code(JniContext::new(env, obj), country_code),
        "set_country_code",
    )
}

/// Send raw vendor command
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeSendRawVendorCmd(
    env: JNIEnv,
    obj: JObject,
    mt: jint,
    gid: jint,
    oid: jint,
    payload: jbyteArray,
) -> jobject {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeSendRawVendorCmd: enter");
    object_result_helper(
        send_raw_vendor_cmd(JniContext::new(env, obj), mt, gid, oid, payload),
        "send_raw_vendor_cmd",
    )
}

/// Retrieve UWB power stats
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_indev_UwbServiceCore_nativeGetPowerStats(
    env: JNIEnv,
    obj: JObject,
) -> jobject {
    debug!("Java_com_android_server_uwb_indev_UwbServiceCore_nativeGetPowerStats: enter");
    object_result_helper(get_power_stats(JniContext::new(env, obj)), "get_power_stats")
}

fn enable(ctx: JniContext) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;
    Ok(uwb_service.enable()?)
}

fn disable(ctx: JniContext) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;
    Ok(uwb_service.disable()?)
}

fn init_session(
    ctx: JniContext,
    session_id: jint,
    session_type: jbyte,
    app_config_params: JObject,
) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;

    let session_id = session_id as u32;
    let session_type = session_type as u8;
    let session_type = SessionType::try_from(session_type)
        .map_err(|_| Error::Parse(format!("Invalid session type. Received {}", session_type)))?;
    let params = match session_type {
        SessionType::Ccc => {
            AppConfigParams::try_from(CccOpenRangingParamsJni::new(ctx.env, app_config_params))
        }
        _ => AppConfigParams::try_from(FiraOpenSessionParamsJni::new(ctx.env, app_config_params)),
    }?;

    Ok(uwb_service.init_session(session_id, session_type, params)?)
}

fn deinit_session(ctx: JniContext, session_id: u32) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;
    Ok(uwb_service.deinit_session(session_id)?)
}

fn start_ranging(ctx: JniContext, session_id: u32) -> Result<AppConfigParams> {
    let uwb_service = get_uwb_service(ctx)?;
    Ok(uwb_service.start_ranging(session_id)?)
}

fn stop_ranging(ctx: JniContext, session_id: u32) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;
    Ok(uwb_service.stop_ranging(session_id)?)
}

fn reconfigure(ctx: JniContext, session_id: u32, app_config_params: JObject) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;
    // TODO(b/244786744): Implement using session_params from uwb service
    todo!();
}

fn update_controller_multicast_list(
    ctx: JniContext,
    session_id: jint,
    update_multicast_list_action: jbyte,
    controlees: JObject,
) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;

    let session_id = session_id as u32;
    let action = update_multicast_list_action as u8;
    let action = UpdateMulticastListAction::try_from(action).map_err(|_| {
        Error::Parse(format!("Invalid value for UpdateMulticastListAction. Received {}", action))
    })?;
    let controlees = match FiraControleeParamsJni::new(ctx.env, controlees).as_vec() {
        Ok(val) => val,
        Err(err) => {
            return Err(Error::Parse(format!("Couldn't parse controlees. {:?}", err)));
        }
    };
    Ok(uwb_service.update_controller_multicast_list(session_id, action, controlees)?)
}

fn set_country_code(ctx: JniContext, country_code: jbyteArray) -> Result<()> {
    let uwb_service = get_uwb_service(ctx)?;

    let country_code = match CountryCode::try_from(CountryCodeJni::new(ctx.env, country_code)) {
        Ok(val) => val,
        Err(err) => {
            return Err(Error::Parse(format!("Invalid country code: {:?}", err)));
        }
    };
    Ok(uwb_service.android_set_country_code(country_code)?)
}

fn send_raw_vendor_cmd(
    ctx: JniContext,
    mt: jint,
    gid: jint,
    oid: jint,
    payload: jbyteArray,
) -> Result<jobject> {
    let uwb_service = get_uwb_service(ctx)?;

    let mt = mt as u32;
    let gid = gid as u32;
    let oid = oid as u32;
    let payload = match ctx.env.convert_byte_array(payload) {
        Ok(val) => val,
        Err(err) => {
            return Err(Error::Parse(format!("Failed to convert payload {:?}", err)));
        }
    };
    let vendor_message = uwb_service.raw_uci_cmd(mt, gid, oid, payload);
    // TODO(cante): figure out if we send RawUciMessage back in a callback
    todo!();
}

fn get_power_stats(ctx: JniContext) -> Result<jobject> {
    let uwb_service = get_uwb_service(ctx)?;

    let power_stats = uwb_service.android_get_power_stats()?;
    let ps_jni = PowerStatsJni::try_from(PowerStatsWithEnv::new(ctx.env, power_stats))?;
    Ok(ps_jni.jni_context.obj.into_raw())
}

fn get_uwb_service(ctx: JniContext) -> Result<&mut UwbServiceWrapper> {
    let uwb_service_ptr = ctx.long_getter("getUwbServicePtr")?;
    if uwb_service_ptr == 0i64 {
        return Err(Error::Jni(jni::errors::Error::NullPtr("Uwb Service is not initialized")));
    }
    // Safety: Uwb Service pointer must not be a null pointer
    // and it must point to a valid Uwb Service object.
    // This can be ensured because the Uwb Service is created in an earlier stage and
    // won't be deleted before calling doDeinitialize.
    unsafe { Ok(&mut *(uwb_service_ptr as *mut UwbServiceWrapper)) }
}

fn boolean_result_helper<T>(result: Result<T>, function_name: &str) -> jboolean {
    match result {
        Ok(_) => true as jboolean,
        Err(err) => {
            error!("{} failed with: {:?}", function_name, err);
            false as jboolean
        }
    }
}

fn object_result_helper(result: Result<jobject>, function_name: &str) -> jobject {
    match result {
        Ok(obj) => obj,
        Err(err) => {
            error!("{} failed with {:?}", function_name, err);
            *JObject::null()
        }
    }
}

/// Get the class loader object. Has to be called from a JNIEnv where the local java classes are
/// loaded. Results in a global reference to the class loader object that can be used to look for
/// classes in other native thread.
fn get_class_loader_obj(env: &JNIEnv) -> Result<GlobalRef> {
    let uwb_service_core_class = env.find_class("com/android/server/uwb/indev/UwbServiceCore")?;
    let uwb_service_core_obj = env.get_object_class(uwb_service_core_class)?;
    let get_class_loader_method =
        env.get_method_id(uwb_service_core_obj, "getClassLoader", "()Ljava/lang/ClassLoader;")?;
    let class_loader = env.call_method_unchecked(
        uwb_service_core_class,
        get_class_loader_method,
        ReturnType::Object,
        &[jvalue::from(JValue::Void)],
    )?;
    let class_loader_jobject = class_loader.l()?;
    Ok(env.new_global_ref(class_loader_jobject)?)
}

/// The wrapper of UwbService that is used to keep the outlived tokio runtime.
///
/// This wrapper implements Deref and DerefMut for UwbService target, so we could get the reference
/// of inner UwbService easily.
struct UwbServiceWrapper {
    /// The wrapped UwbService.
    service: UwbService,

    /// The working runtime, which should outlives the UwbService.
    ///
    /// Because the fields of a struct are dropped in declaration order, this field is guaranteed
    /// to be dropped after UwbService.
    _runtime: Runtime,
}

impl UwbServiceWrapper {
    pub fn new(service: UwbService, runtime: Runtime) -> Self {
        Self { service, _runtime: runtime }
    }
}

impl std::ops::Deref for UwbServiceWrapper {
    type Target = UwbService;

    fn deref(&self) -> &Self::Target {
        &self.service
    }
}

impl std::ops::DerefMut for UwbServiceWrapper {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.service
    }
}
