/*
 * Copyright (C) 2021 The Android Open Source Project
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

//! A tool to start a standalone compsvc server that serves over RPC binder.

mod artifact_signer;
mod compilation;
mod compos_key;
mod compsvc;
mod fsverity;

use anyhow::Result;
use binder::unstable_api::AsNative;
use compos_common::COMPOS_VSOCK_PORT;
use log::{debug, error};
use std::os::raw::c_void;
use std::panic;
use std::ptr;
use vm_payload_bindgen::{AIBinder, AVmPayload_notifyPayloadReady, AVmPayload_runVsockRpcServer};

fn main() {
    if let Err(e) = try_main() {
        error!("failed with {:?}", e);
        std::process::exit(1);
    }
}

fn try_main() -> Result<()> {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("compsvc")
            .with_max_level(log::LevelFilter::Debug),
    );
    // Redirect panic messages to logcat.
    panic::set_hook(Box::new(|panic_info| {
        error!("{}", panic_info);
    }));

    debug!("compsvc is starting as a rpc service.");
    let param = ptr::null_mut();
    let mut service = compsvc::new_binder()?.as_binder();
    let service = service.as_native_mut() as *mut AIBinder;
    // SAFETY: We hold a strong pointer, so the raw pointer remains valid. The bindgen AIBinder
    // is the same type as sys::AIBinder. It is safe for on_ready to be invoked at any time, with
    // any parameter.
    unsafe { AVmPayload_runVsockRpcServer(service, COMPOS_VSOCK_PORT, Some(on_ready), param) }
}

extern "C" fn on_ready(_param: *mut c_void) {
    // SAFETY: Invokes a method from the bindgen library `vm_payload_bindgen` which is safe to
    // call at any time.
    unsafe { AVmPayload_notifyPayloadReady() };
}
