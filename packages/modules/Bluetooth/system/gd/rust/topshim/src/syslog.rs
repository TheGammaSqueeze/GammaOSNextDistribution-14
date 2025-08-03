//! Expose some internal methods to set the log level for system syslog.
//!
//! On systems that use syslog (i.e. `vlog_syslog.cc`), there is support
//! to filter out logs before they go to syslog. This module provides Rust apis
//! to tune log levels for syslog.

use num_derive::ToPrimitive;
use num_traits::cast::ToPrimitive;
use std::ffi::CString;
use std::os::raw::c_char;

#[derive(ToPrimitive)]
#[repr(u8)]
/// Android framework log priority levels.
/// They are defined in system/logging/liblog/include/android/log.h by
/// the Android Framework code.
pub enum Level {
    Verbose = 2,
    Debug = 3,
    Info = 4,
    Warn = 5,
    Error = 6,
    Fatal = 7,
}

// Defined in syslog linkage. See |vlog_syslog.cc|.
extern "C" {
    fn SetLogLevelForTag(tag: *const c_char, level: u8);
    fn SetDefaultLogLevel(level: u8);
}

/// Set a default level value for failed |level.to_u8()| of |Level::Info|.
const DEFAULT_LEVEL: u8 = 4;

/// Set the level of logs which will get printed for the given tag.
///
/// Args:
///     tag - LOG_TAG for the system module that's logging.
///     level - Minimum log level that will be sent to syslog.
pub fn set_log_level_for_tag(tag: &str, level: Level) {
    let cstr: CString = CString::new(tag).expect("CString::new failed on log tag");

    unsafe {
        SetLogLevelForTag(cstr.as_ptr(), level.to_u8().unwrap_or(DEFAULT_LEVEL));
    }
}

/// Set the default log level for log tags. Will be overridden by any tag specific levels.
///
/// Args:
///     level - Minimum log level that will be sent to syslog.
pub fn set_default_log_level(level: Level) {
    unsafe {
        SetDefaultLogLevel(level.to_u8().unwrap_or(DEFAULT_LEVEL));
    }
}
