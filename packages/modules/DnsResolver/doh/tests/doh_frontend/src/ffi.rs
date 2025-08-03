/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use super::dns_https_frontend::DohFrontend;
use super::stats::Stats;

use anyhow::{bail, Result};
use libc::c_char;
use log::warn;
use std::ffi::CStr;
use std::net::{IpAddr, SocketAddr};
use std::str::FromStr;

/// Creates a DohFrontend object by the given IP addresss and ports. Returns the pointer of
/// the object if the creation succeeds; otherwise, returns a null pointer.
///
/// # Safety
///
/// The parameters `addr`, `port`, `backend_addr`, and `backend_port` must all point to null
/// terminated UTF-8 encoded strings.
#[no_mangle]
pub unsafe extern "C" fn frontend_new(
    addr: *const c_char,
    port: *const c_char,
    backend_addr: *const c_char,
    backend_port: *const c_char,
) -> *mut DohFrontend {
    // SAFETY: Our caller promises that addr is a valid C string.
    let addr = unsafe { CStr::from_ptr(addr) }.to_str().unwrap();
    // SAFETY: Our caller promises that port is a valid C string.
    let port = unsafe { CStr::from_ptr(port) }.to_str().unwrap();
    // SAFETY: Our caller promises that backend_addr is a valid C string.
    let backend_addr = unsafe { CStr::from_ptr(backend_addr) }.to_str().unwrap();
    // SAFETY: Our caller promises that backend_port is a valid C string.
    let backend_port = unsafe { CStr::from_ptr(backend_port) }.to_str().unwrap();

    let socket_addr = to_socket_addr(addr, port).or_else(logging_and_return_err);
    let backend_socket_addr =
        to_socket_addr(backend_addr, backend_port).or_else(logging_and_return_err);
    if socket_addr.is_err() || backend_socket_addr.is_err() {
        return std::ptr::null_mut();
    }

    match DohFrontend::new(socket_addr.unwrap(), backend_socket_addr.unwrap()) {
        Ok(c) => Box::into_raw(c),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Starts the `DohFrontend` worker thread. Returns true if the worker thread is spawned
/// successfully; otherwise, it returns false.
#[no_mangle]
pub extern "C" fn frontend_start(doh: &mut DohFrontend) -> bool {
    doh.start().or_else(logging_and_return_err).is_ok()
}

/// Stops the `DohFrontend` worker thread.
#[no_mangle]
pub extern "C" fn frontend_stop(doh: &mut DohFrontend) -> bool {
    doh.stop().or_else(logging_and_return_err).is_ok()
}

/// Deletes the `DohFrontend` created from `frontend_new`.
/// If the caller has called `frontend_start` to start `DohFrontend`, it has to call
/// call `frontend_stop` to stop the worker thread before deleting the object.
///
/// The DohFrontend is not set to null pointer, caller needs to do it on its own.
///
/// # Safety
///
/// `doh` must be a pointer either null or previously returned by `frontend_new`, and not yet passed
/// to `frontend_delete`.
#[no_mangle]
pub unsafe extern "C" fn frontend_delete(doh: *mut DohFrontend) {
    if !doh.is_null() {
        // SAFETY: Our caller promised that `doh` was either null or previously returned by
        // `frontend_new`. We just checked that it's not null, so it must have been returned by
        // `frontend_new`, which obtained it from `Box::into_raw`.
        drop(unsafe { Box::from_raw(doh) });
    }
}

/// Sets server certificate to `DohFrontend`.
///
/// # Safety
///
/// The given certificate must be a null-terminated UTF-8 encoded string.
#[no_mangle]
pub unsafe extern "C" fn frontend_set_certificate(
    doh: &mut DohFrontend,
    certificate: *const c_char,
) -> bool {
    if certificate.is_null() {
        return false;
    }
    // SAFETY: Our caller promises that certificate is a valid C string.
    let certificate = unsafe { CStr::from_ptr(certificate) }.to_str().unwrap();
    doh.set_certificate(certificate).or_else(logging_and_return_err).is_ok()
}

/// Sets server private key to `DohFrontend`.
///
/// # Safety
///
/// The given private key must be a null-terminated UTF-8 encoded string.
#[no_mangle]
pub unsafe extern "C" fn frontend_set_private_key(
    doh: &mut DohFrontend,
    private_key: *const c_char,
) -> bool {
    if private_key.is_null() {
        return false;
    }
    // SAFETY: Our caller promises that private_key is a valid C string.
    let private_key = unsafe { CStr::from_ptr(private_key) }.to_str().unwrap();
    doh.set_private_key(private_key).or_else(logging_and_return_err).is_ok()
}

/// Configures the `DohFrontend` not to process DoH queries until a given number of DoH queries
/// are received. This function works even in the middle of the worker thread.
#[no_mangle]
pub extern "C" fn frontend_set_delay_queries(doh: &mut DohFrontend, count: i32) -> bool {
    doh.set_delay_queries(count).or_else(logging_and_return_err).is_ok()
}

/// Configures the `DohFrontend` to use the given value for max_idle_timeout transport parameter.
#[no_mangle]
pub extern "C" fn frontend_set_max_idle_timeout(doh: &mut DohFrontend, value: u64) -> bool {
    doh.set_max_idle_timeout(value).or_else(logging_and_return_err).is_ok()
}

/// Configures the `DohFrontend` to use the given value for these transport parameters.
/// - initial_max_data
/// - initial_max_stream_data_bidi_local
/// - initial_max_stream_data_bidi_remote
/// - initial_max_stream_data_uni
#[no_mangle]
pub extern "C" fn frontend_set_max_buffer_size(doh: &mut DohFrontend, value: u64) -> bool {
    doh.set_max_buffer_size(value).or_else(logging_and_return_err).is_ok()
}

/// Configures the `DohFrontend` to use the given value for initial_max_streams_bidi transport
/// parameter.
#[no_mangle]
pub extern "C" fn frontend_set_max_streams_bidi(doh: &mut DohFrontend, value: u64) -> bool {
    doh.set_max_streams_bidi(value).or_else(logging_and_return_err).is_ok()
}

/// Sets the `DohFrontend` to block or unblock sending any data.
#[no_mangle]
pub extern "C" fn frontend_block_sending(doh: &mut DohFrontend, block: bool) -> bool {
    doh.block_sending(block).or_else(logging_and_return_err).is_ok()
}

/// If this function is called, the `DohFrontend` will send RESET_STREAM frame as a response
/// instead of a DoH answer on the stream |stream_id|. This will make the client fail to receive
/// this DoH answer.
#[no_mangle]
pub extern "C" fn frontend_set_reset_stream_id(doh: &mut DohFrontend, stream_id: u64) -> bool {
    doh.set_reset_stream_id(stream_id).or_else(logging_and_return_err).is_ok()
}

/// Gets the statistics of the `DohFrontend` and writes the result to |out|.
#[no_mangle]
pub extern "C" fn frontend_stats(doh: &mut DohFrontend, out: &mut Stats) -> bool {
    doh.request_stats()
        .map(|stats| {
            out.queries_received = stats.queries_received;
            out.connections_accepted = stats.connections_accepted;
            out.alive_connections = stats.alive_connections;
            out.resumed_connections = stats.resumed_connections;
            out.early_data_connections = stats.early_data_connections;
        })
        .or_else(logging_and_return_err)
        .is_ok()
}

/// Resets `queries_received` field of `Stats` owned by the `DohFrontend`.
#[no_mangle]
pub extern "C" fn frontend_stats_clear_queries(doh: &DohFrontend) -> bool {
    doh.stats_clear_queries().or_else(logging_and_return_err).is_ok()
}

/// Enable Rust debug logging.
#[no_mangle]
pub extern "C" fn init_android_logger() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("DohFrontend")
            .with_max_level(log::LevelFilter::Debug),
    );
}

fn to_socket_addr(addr: &str, port: &str) -> Result<SocketAddr> {
    let socket_addr = SocketAddr::new(IpAddr::from_str(addr)?, port.parse()?);
    Ok(socket_addr)
}

fn logging_and_return_err<T, U: std::fmt::Debug>(e: U) -> Result<T> {
    warn!("logging_and_return_err: {:?}", e);
    bail!("{:?}", e)
}
