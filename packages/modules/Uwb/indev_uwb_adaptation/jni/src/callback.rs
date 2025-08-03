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

//! Defines the callback structure that knows how to communicate with Java UWB service
//! when certain events happen such as device reset, data or notification received
//! and status changes.

use std::collections::HashMap;

use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JValue};
use jni::signature::TypeSignature;
use jni::sys::jvalue;
use jni::{AttachGuard, JavaVM};
use log::error;

use uwb_core::params::uci_packets::{DeviceState, ReasonCode, SessionId, SessionState};
use uwb_core::service::{UwbServiceCallback, UwbServiceCallbackBuilder};
use uwb_core::uci::SessionRangeData;

use crate::error::{Error, Result};
use crate::object_mapping::{SessionRangeDataWithEnv, UwbRangingDataJni};

pub struct UwbServiceCallbackBuilderImpl {
    vm: &'static JavaVM,
    callback_obj: GlobalRef,
    class_loader_obj: GlobalRef,
}
impl UwbServiceCallbackBuilderImpl {
    pub fn new(vm: &'static JavaVM, callback_obj: GlobalRef, class_loader_obj: GlobalRef) -> Self {
        Self { vm, callback_obj, class_loader_obj }
    }
}
impl UwbServiceCallbackBuilder<UwbServiceCallbackImpl> for UwbServiceCallbackBuilderImpl {
    fn build(self) -> Option<UwbServiceCallbackImpl> {
        let env = match self.vm.attach_current_thread() {
            Ok(env) => env,
            Err(err) => {
                error!("Failed to attached callback thread to JVM: {:?}", err);
                return None;
            }
        };
        Some(UwbServiceCallbackImpl::new(env, self.class_loader_obj, self.callback_obj))
    }
}

pub struct UwbServiceCallbackImpl {
    env: AttachGuard<'static>,
    class_loader_obj: GlobalRef,
    callback_obj: GlobalRef,
    jmethod_id_map: HashMap<String, JMethodID>,
    jclass_map: HashMap<String, GlobalRef>,
}

impl UwbServiceCallbackImpl {
    pub fn new(
        env: AttachGuard<'static>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
    ) -> Self {
        Self {
            env,
            class_loader_obj,
            callback_obj,
            jmethod_id_map: HashMap::new(),
            jclass_map: HashMap::new(),
        }
    }

    fn find_local_class(&mut self, class_name: &str) -> Result<GlobalRef> {
        let class_name_jobject = *self.env.new_string(class_name)?;

        let jclass = match self.jclass_map.get(class_name) {
            Some(jclass) => jclass.clone(),
            None => {
                let class_value = self
                    .env
                    .call_method(
                        self.class_loader_obj.as_obj(),
                        "findClass",
                        "(Ljava/lang/String;)Ljava/lang/Class;",
                        &[JValue::Object(class_name_jobject)],
                    )?
                    .l()?;

                let jclass_global_ref = self.env.new_global_ref(JClass::from(class_value))?;
                self.jclass_map.insert(class_name.to_owned(), jclass_global_ref.clone());
                jclass_global_ref
            }
        };

        Ok(jclass)
    }

    fn cached_jni_call(&mut self, name: &str, sig: &str, args: &[jvalue]) -> Result<()> {
        let type_signature = TypeSignature::from_str(sig)?;
        if type_signature.args.len() != args.len() {
            return Err(Error::Jni(jni::errors::Error::InvalidArgList(type_signature)));
        }
        let name_signature = name.to_owned() + sig;
        let jmethod_id = match self.jmethod_id_map.get(&name_signature) {
            Some(jmethod_id) => jmethod_id.to_owned(),
            None => {
                let jmethod_id = self.env.get_method_id(self.callback_obj.as_obj(), name, sig)?;
                self.jmethod_id_map.insert(name_signature.clone(), jmethod_id);
                jmethod_id.to_owned()
            }
        };

        self.env.call_method_unchecked(
            self.callback_obj.as_obj(),
            jmethod_id,
            type_signature.ret,
            args,
        )?;
        Ok(())
    }
}

impl UwbServiceCallback for UwbServiceCallbackImpl {
    fn on_service_reset(&mut self, success: bool) {
        let result = self.cached_jni_call(
            "onServiceResetReceived",
            "(Z)V",
            &[jvalue::from(JValue::Bool(success as u8))],
        );
        result_helper("on_service_reset", result);
    }

    fn on_uci_device_status_changed(&mut self, state: DeviceState) {
        let result = self.cached_jni_call(
            "onDeviceStatusNotificationReceived",
            "(I)V",
            &[jvalue::from(JValue::Int(state as i32))],
        );
        result_helper("on_uci_device_status_changed", result);
    }

    fn on_session_state_changed(
        &mut self,
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) {
        let result = self.cached_jni_call(
            "onSessionStatusNotificationReceived",
            "(JII)V",
            &[
                jvalue::from(JValue::Long(session_id as i64)),
                jvalue::from(JValue::Int(session_state as i32)),
                jvalue::from(JValue::Int(i32::from(reason_code))),
            ],
        );
        result_helper("on_session_state_changed", result);
    }

    fn on_range_data_received(&mut self, session_id: SessionId, range_data: SessionRangeData) {
        let uwb_ranging_data_obj =
            match self.find_local_class("com/android/server/uwb/data/UwbRangingData") {
                Ok(ranging_class) => ranging_class,
                Err(err) => {
                    error!("UWB Callback Service: Failed to load uwb ranging jclass: {:?}", err);
                    return;
                }
            };
        let uwb_two_way_measurement_obj = match self
            .find_local_class("com/android/server/uwb/data/UwbTwoWayMeasurement")
        {
            Ok(measurement_class) => measurement_class,
            Err(err) => {
                error!("UWB Callback Service: Failed to load uwb measurement jclass: {:?}", err);
                return;
            }
        };

        let uwb_ranging_data_jclass = JClass::from(uwb_ranging_data_obj.as_obj());
        let uwb_two_way_measurement_jclass = JClass::from(uwb_two_way_measurement_obj.as_obj());
        let session_range_with_env = SessionRangeDataWithEnv::new(
            *self.env,
            uwb_ranging_data_jclass,
            uwb_two_way_measurement_jclass,
            range_data,
        );
        let uwb_ranging_data_jni = match UwbRangingDataJni::try_from(session_range_with_env) {
            Ok(v) => v,
            Err(err) => {
                error!("UWB Service Callback: Failed to convert UwbRangingDataJni: {:?}", err);
                return;
            }
        };

        let uwb_raning_data_jobject = uwb_ranging_data_jni.jni_context.obj;

        let result = self.cached_jni_call(
            "onRangeDataNotificationReceived",
            "(JLcom/android/server/uwb/data/UwbRangingData;)V",
            &[
                jvalue::from(JValue::Long(session_id as i64)),
                jvalue::from(JValue::Object(uwb_raning_data_jobject)),
            ],
        );
        result_helper("on_range_data_received", result);
    }

    fn on_vendor_notification_received(&mut self, gid: u32, oid: u32, payload: Vec<u8>) {
        let payload_i8 = payload.iter().map(|b| b.to_owned() as i8).collect::<Vec<_>>();
        let payload_jbyte_array = match self.env.new_byte_array(payload_i8.len() as i32) {
            Ok(p) => p,
            Err(err) => {
                error!("Uwb Service Callback: Failed to create jbyteArray: {:?}", err);
                return;
            }
        };
        if let Err(err) =
            self.env.set_byte_array_region(payload_jbyte_array, 0, payload_i8.as_slice())
        {
            error!("UWB Service Callback: Failed to set byte array: {:?}", err);
            return;
        }

        // Safety: payload_jbyte_array safely instantiated above.
        let payload_jobject = unsafe { JObject::from_raw(payload_jbyte_array) };

        let result = self.cached_jni_call(
            "onVendorUciNotificationReceived",
            "(II[B)V",
            &[
                jvalue::from(JValue::Int(gid as i32)),
                jvalue::from(JValue::Int(oid as i32)),
                jvalue::from(JValue::Object(payload_jobject)),
            ],
        );

        result_helper("on_range_data_received", result);
    }
}

fn result_helper(function_name: &str, result: Result<()>) {
    if let Err(err) = result {
        error!("UWB Service Callback: {} failed: {:?}", function_name, err);
    }
}
