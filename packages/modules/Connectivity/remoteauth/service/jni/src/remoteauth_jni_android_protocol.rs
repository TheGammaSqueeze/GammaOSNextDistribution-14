/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Implementation of JNI protocol functionality.
use crate::unique_jvm;
use crate::utils::get_boolean_result;
use jni::objects::JObject;
use jni::sys::jboolean;
use jni::JNIEnv;

/// Initialize native library. Captures Java VM:
#[no_mangle]
pub extern "system" fn Java_com_android_server_remoteauth_jni_NativeRemoteAuthJavaPlatform_native_init(
    env: JNIEnv,
    _: JObject,
) -> jboolean {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("remoteauth")
            .with_max_level(log::LevelFilter::Trace)
            .with_filter("trace,jni=info"),
    );
    get_boolean_result(native_init(env), "native_init")
}

fn native_init(env: JNIEnv) -> anyhow::Result<()> {
    let jvm = env.get_java_vm()?;
    unique_jvm::set_once(jvm)
}
