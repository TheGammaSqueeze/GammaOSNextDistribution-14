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

//! C API for the DoH backend for the Android DnsResolver module.

use crate::boot_time::{timeout, BootTime, Duration};
use crate::dispatcher::{Command, Dispatcher, Response, ServerInfo};
use crate::network::{SocketTagger, ValidationReporter};
use base64::{prelude::BASE64_URL_SAFE_NO_PAD, Engine};
use futures::FutureExt;
use libc::{c_char, int32_t, size_t, ssize_t, uint32_t, uint64_t};
use log::{error, warn};
use std::ffi::CString;
use std::net::{IpAddr, SocketAddr};
use std::ops::DerefMut;
use std::os::unix::io::RawFd;
use std::str::FromStr;
use std::sync::{Arc, Mutex};
use std::{ptr, slice};
use tokio::runtime::Builder;
use tokio::sync::oneshot;
use tokio::task;
use url::Url;

pub type ValidationCallback = unsafe extern "C" fn(
    net_id: uint32_t,
    success: bool,
    ip_addr: *const c_char,
    host: *const c_char,
);
pub type TagSocketCallback = extern "C" fn(sock: RawFd);

#[repr(C)]
pub struct FeatureFlags {
    probe_timeout_ms: uint64_t,
    idle_timeout_ms: uint64_t,
    use_session_resumption: bool,
    enable_early_data: bool,
}

fn wrap_validation_callback(validation_fn: ValidationCallback) -> ValidationReporter {
    Arc::new(move |info: &ServerInfo, success: bool| {
        async move {
            let (ip_addr, domain) = match (
                CString::new(info.peer_addr.ip().to_string()),
                CString::new(info.domain.clone().unwrap_or_default()),
            ) {
                (Ok(ip_addr), Ok(domain)) => (ip_addr, domain),
                _ => {
                    error!("validation_callback bad input");
                    return;
                }
            };
            let netd_id = info.net_id;
            // SAFETY: The string pointers are obtained from `CString`, so they must be valid C
            // strings.
            task::spawn_blocking(move || unsafe {
                validation_fn(netd_id, success, ip_addr.as_ptr(), domain.as_ptr())
            })
            .await
            .unwrap_or_else(|e| warn!("Validation function task failed: {}", e))
        }
        .boxed()
    })
}

fn wrap_tag_socket_callback(tag_socket_fn: TagSocketCallback) -> SocketTagger {
    use std::os::unix::io::AsRawFd;
    Arc::new(move |udp_socket: &std::net::UdpSocket| {
        let fd = udp_socket.as_raw_fd();
        async move {
            task::spawn_blocking(move || {
                tag_socket_fn(fd);
            })
            .await
            .unwrap_or_else(|e| warn!("Socket tag function task failed: {}", e))
        }
        .boxed()
    })
}

pub struct DohDispatcher(Mutex<Dispatcher>);

impl DohDispatcher {
    fn lock(&self) -> impl DerefMut<Target = Dispatcher> + '_ {
        self.0.lock().unwrap()
    }
}

const SYSTEM_CERT_PATH: &str = "/system/etc/security/cacerts";

/// The return code of doh_query means that there is no answer.
pub const DOH_RESULT_INTERNAL_ERROR: ssize_t = -1;
/// The return code of doh_query means that query can't be sent.
pub const DOH_RESULT_CAN_NOT_SEND: ssize_t = -2;
/// The return code of doh_query to indicate that the query timed out.
pub const DOH_RESULT_TIMEOUT: ssize_t = -255;

/// The error log level.
pub const DOH_LOG_LEVEL_ERROR: u32 = 0;
/// The warning log level.
pub const DOH_LOG_LEVEL_WARN: u32 = 1;
/// The info log level.
pub const DOH_LOG_LEVEL_INFO: u32 = 2;
/// The debug log level.
pub const DOH_LOG_LEVEL_DEBUG: u32 = 3;
/// The trace log level.
pub const DOH_LOG_LEVEL_TRACE: u32 = 4;

const DOH_PORT: u16 = 443;

fn level_from_u32(level: u32) -> Option<log::LevelFilter> {
    use log::LevelFilter::*;
    match level {
        DOH_LOG_LEVEL_ERROR => Some(Error),
        DOH_LOG_LEVEL_WARN => Some(Warn),
        DOH_LOG_LEVEL_INFO => Some(Info),
        DOH_LOG_LEVEL_DEBUG => Some(Debug),
        DOH_LOG_LEVEL_TRACE => Some(Trace),
        _ => None,
    }
}

/// Performs static initialization for android logger.
/// If an invalid level is passed, defaults to logging errors only.
/// If called more than once, it will have no effect on subsequent calls.
#[no_mangle]
pub extern "C" fn doh_init_logger(level: u32) {
    let log_level = level_from_u32(level).unwrap_or(log::LevelFilter::Error);
    android_logger::init_once(android_logger::Config::default().with_max_level(log_level));
}

/// Set the log level.
/// If an invalid level is passed, defaults to logging errors only.
#[no_mangle]
pub extern "C" fn doh_set_log_level(level: u32) {
    let level_filter = level_from_u32(level).unwrap_or(log::LevelFilter::Error);
    log::set_max_level(level_filter);
}

/// Performs the initialization for the DoH engine.
/// Creates and returns a DoH engine instance.
#[no_mangle]
pub extern "C" fn doh_dispatcher_new(
    validation_fn: ValidationCallback,
    tag_socket_fn: TagSocketCallback,
) -> *mut DohDispatcher {
    match Dispatcher::new(
        wrap_validation_callback(validation_fn),
        wrap_tag_socket_callback(tag_socket_fn),
    ) {
        Ok(c) => Box::into_raw(Box::new(DohDispatcher(Mutex::new(c)))),
        Err(e) => {
            error!("doh_dispatcher_new: failed: {:?}", e);
            ptr::null_mut()
        }
    }
}

/// Deletes a DoH engine created by doh_dispatcher_new().
///
/// # Safety
///
/// `doh` must be a non-null pointer previously created by `doh_dispatcher_new()`
/// and not yet deleted by `doh_dispatcher_delete()`.
#[no_mangle]
pub unsafe extern "C" fn doh_dispatcher_delete(doh: *mut DohDispatcher) {
    // SAFETY: The caller guarantees that `doh` was created by `doh_dispatcher_new` (which does so
    // using `Box::into_raw`), and that it hasn't yet been deleted by this function.
    unsafe { Box::from_raw(doh) }.lock().exit_handler()
}

/// Probes and stores the DoH server with the given configurations.
/// Use the negative errno-style codes as the return value to represent the result.
/// # Safety
/// `doh` must be a non-null pointer previously created by `doh_dispatcher_new()`
/// and not yet deleted by `doh_dispatcher_delete()`.
/// `url`, `domain`, `ip_addr`, `cert_path` are null terminated strings.
#[no_mangle]
pub unsafe extern "C" fn doh_net_new(
    doh: &DohDispatcher,
    net_id: uint32_t,
    url: *const c_char,
    domain: *const c_char,
    ip_addr: *const c_char,
    sk_mark: libc::uint32_t,
    cert_path: *const c_char,
    flags: &FeatureFlags,
    network_type: uint32_t,
    private_dns_mode: uint32_t,
) -> int32_t {
    // SAFETY: The caller guarantees that these are all valid nul-terminated C strings.
    let (url, domain, ip_addr, cert_path) = match unsafe {
        (
            std::ffi::CStr::from_ptr(url).to_str(),
            std::ffi::CStr::from_ptr(domain).to_str(),
            std::ffi::CStr::from_ptr(ip_addr).to_str(),
            std::ffi::CStr::from_ptr(cert_path).to_str(),
        )
    } {
        (Ok(url), Ok(domain), Ok(ip_addr), Ok(cert_path)) => {
            if domain.is_empty() {
                (url, None, ip_addr.to_string(), None)
            } else if !cert_path.is_empty() {
                (url, Some(domain.to_string()), ip_addr.to_string(), Some(cert_path.to_string()))
            } else {
                (
                    url,
                    Some(domain.to_string()),
                    ip_addr.to_string(),
                    Some(SYSTEM_CERT_PATH.to_string()),
                )
            }
        }
        _ => {
            error!("bad input"); // Should not happen
            return -libc::EINVAL;
        }
    };

    let (url, ip_addr) = match (Url::parse(url), IpAddr::from_str(&ip_addr)) {
        (Ok(url), Ok(ip_addr)) => (url, ip_addr),
        _ => {
            error!("bad ip or url"); // Should not happen
            return -libc::EINVAL;
        }
    };
    let cmd = Command::Probe {
        info: ServerInfo {
            net_id,
            url,
            peer_addr: SocketAddr::new(ip_addr, DOH_PORT),
            domain,
            sk_mark,
            cert_path,
            idle_timeout_ms: flags.idle_timeout_ms,
            use_session_resumption: flags.use_session_resumption,
            enable_early_data: flags.enable_early_data,
            network_type,
            private_dns_mode,
        },
        timeout: Duration::from_millis(flags.probe_timeout_ms),
    };
    if let Err(e) = doh.lock().send_cmd(cmd) {
        error!("Failed to send the probe: {:?}", e);
        return -libc::EPIPE;
    }
    0
}

/// Sends a DNS query via the network associated to the given |net_id| and waits for the response.
/// The return code should be either one of the public constant DOH_RESULT_* to indicate the error
/// or the size of the answer.
/// # Safety
/// `doh` must be a non-null pointer previously created by `doh_dispatcher_new()`
/// and not yet deleted by `doh_dispatcher_delete()`.
/// `dns_query` must point to a buffer at least `dns_query_len` in size.
/// `response` must point to a buffer at least `response_len` in size.
#[no_mangle]
pub unsafe extern "C" fn doh_query(
    doh: &DohDispatcher,
    net_id: uint32_t,
    dns_query: *mut u8,
    dns_query_len: size_t,
    response: *mut u8,
    response_len: size_t,
    timeout_ms: uint64_t,
) -> ssize_t {
    // SAFETY: The caller guarantees that `dns_query` is a valid pointer to a buffer of at least
    // `dns_query_len` items.
    let q = unsafe { slice::from_raw_parts_mut(dns_query, dns_query_len) };

    let (resp_tx, resp_rx) = oneshot::channel();
    let t = Duration::from_millis(timeout_ms);
    if let Some(expired_time) = BootTime::now().checked_add(t) {
        let cmd = Command::Query {
            net_id,
            base64_query: BASE64_URL_SAFE_NO_PAD.encode(q),
            expired_time,
            resp: resp_tx,
        };

        if let Err(e) = doh.lock().send_cmd(cmd) {
            error!("Failed to send the query: {:?}", e);
            return DOH_RESULT_CAN_NOT_SEND;
        }
    } else {
        error!("Bad timeout parameter: {}", timeout_ms);
        return DOH_RESULT_CAN_NOT_SEND;
    }

    if let Ok(rt) = Builder::new_current_thread().enable_all().build() {
        let local = task::LocalSet::new();
        match local.block_on(&rt, async { timeout(t, resp_rx).await }) {
            Ok(v) => match v {
                Ok(v) => match v {
                    Response::Success { answer } => {
                        if answer.len() > response_len || answer.len() > isize::MAX as usize {
                            return DOH_RESULT_INTERNAL_ERROR;
                        }
                        // SAFETY: The caller guarantees that response points to a valid buffer at
                        // least `response_len` long, and we just checked that `answer.len()` is no
                        // longer than `response_len`.
                        let response = unsafe { slice::from_raw_parts_mut(response, answer.len()) };
                        response.copy_from_slice(&answer);
                        answer.len() as ssize_t
                    }
                    rsp => {
                        error!("Non-successful response: {:?}", rsp);
                        DOH_RESULT_CAN_NOT_SEND
                    }
                },
                Err(e) => {
                    error!("no result {}", e);
                    DOH_RESULT_CAN_NOT_SEND
                }
            },
            Err(e) => {
                error!("timeout: {}", e);
                DOH_RESULT_TIMEOUT
            }
        }
    } else {
        DOH_RESULT_CAN_NOT_SEND
    }
}

/// Clears the DoH servers associated with the given |netid|.
/// # Safety
/// `doh` must be a non-null pointer previously created by `doh_dispatcher_new()`
/// and not yet deleted by `doh_dispatcher_delete()`.
#[no_mangle]
pub extern "C" fn doh_net_delete(doh: &DohDispatcher, net_id: uint32_t) {
    if let Err(e) = doh.lock().send_cmd(Command::Clear { net_id }) {
        error!("Failed to send the query: {:?}", e);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TEST_NET_ID: u32 = 50;
    const LOOPBACK_ADDR: &str = "127.0.0.1:443";
    const LOCALHOST_URL: &str = "https://mylocal.com/dns-query";

    unsafe extern "C" fn success_cb(
        net_id: uint32_t,
        success: bool,
        ip_addr: *const c_char,
        host: *const c_char,
    ) {
        assert!(success);
        // SAFETY: The caller guarantees that ip_addr and host are valid nul-terminated C strings.
        unsafe {
            assert_validation_info(net_id, ip_addr, host);
        }
    }

    unsafe extern "C" fn fail_cb(
        net_id: uint32_t,
        success: bool,
        ip_addr: *const c_char,
        host: *const c_char,
    ) {
        assert!(!success);
        // SAFETY: The caller guarantees that ip_addr and host are valid nul-terminated C strings.
        unsafe {
            assert_validation_info(net_id, ip_addr, host);
        }
    }

    // # Safety
    // `ip_addr`, `host` are null terminated strings
    unsafe fn assert_validation_info(
        net_id: uint32_t,
        ip_addr: *const c_char,
        host: *const c_char,
    ) {
        assert_eq!(net_id, TEST_NET_ID);
        // SAFETY: The caller guarantees that `ip_addr` is a valid nul-terminated C string.
        let ip_addr = unsafe { std::ffi::CStr::from_ptr(ip_addr) }.to_str().unwrap();
        let expected_addr: SocketAddr = LOOPBACK_ADDR.parse().unwrap();
        assert_eq!(ip_addr, expected_addr.ip().to_string());
        // SAFETY: The caller guarantees that `host` is a valid nul-terminated C string.
        let host = unsafe { std::ffi::CStr::from_ptr(host) }.to_str().unwrap();
        assert_eq!(host, "");
    }

    #[tokio::test]
    async fn wrap_validation_callback_converts_correctly() {
        let info = ServerInfo {
            net_id: TEST_NET_ID,
            url: Url::parse(LOCALHOST_URL).unwrap(),
            peer_addr: LOOPBACK_ADDR.parse().unwrap(),
            domain: None,
            sk_mark: 0,
            cert_path: None,
            idle_timeout_ms: 0,
            use_session_resumption: true,
            enable_early_data: true,
            network_type: 2,
            private_dns_mode: 3,
        };

        wrap_validation_callback(success_cb)(&info, true).await;
        wrap_validation_callback(fail_cb)(&info, false).await;
    }

    extern "C" fn tag_socket_cb(raw_fd: RawFd) {
        assert!(raw_fd > 0)
    }

    #[tokio::test]
    async fn wrap_tag_socket_callback_converts_correctly() {
        let sock = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        wrap_tag_socket_callback(tag_socket_cb)(&sock).await;
    }
}
