//
//  Copyright 2023 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

//! A logger for use by netsim and netsimd.
//!
//! Uses the env_logger crate that allows control of logging through
//! the RUST_LOG environment variable.

use env_logger::{Builder, Env};
use log::{Level, Record};
use std::{ffi::OsStr, io::Write, path::Path};

use crate::util::time_display::log_current_time;

/// Initiating the environment for logging with given prefix
///
/// The current log format follows the same format as Android Emulator team.
pub fn init(prefix: &'static str) {
    let mut builder = Builder::from_env(Env::default().default_filter_or("info"));
    builder.format(move |buf, record| {
        let level = level_to_string(record.level());
        let message = format!(
            "{} {} {} {}:{} - {}",
            prefix,
            level,
            log_current_time(),
            format_file(record),
            record.line().unwrap_or(0),
            record.args()
        );
        writeln!(buf, "{}", message)
    });
    builder.init();
}

/// Initiating the environment for logging in Rust unit tests
///
/// The current log format follows the same format as Android Emulator team.
pub fn init_for_test() {
    let mut binding = Builder::from_env(Env::default().default_filter_or("info"));
    let builder = binding.is_test(true);
    builder.format(move |buf, record| {
        let level = level_to_string(record.level());
        let message =
            format!("{} {} \t| netsim-test: {}", level, log_current_time(), record.args());
        writeln!(buf, "{}", message)
    });
    builder.init();
}

/// Helper function for parsing the file name from given record file path
fn format_file<'a>(record: &'a Record<'a>) -> &'a str {
    match record.file() {
        Some(filepath) => {
            Path::new(filepath).file_name().unwrap_or(OsStr::new("N/A")).to_str().unwrap()
        }
        None => "N/A",
    }
}

/// Helper function for translating log levels to string.
fn level_to_string(level: Level) -> &'static str {
    match level {
        Level::Error => "E",
        Level::Warn => "W",
        Level::Info => "I",
        Level::Debug => "D",
        Level::Trace => "T",
    }
}

/// This test is an example of having logs in Rust unit tests
///
/// Expected log: INFO  | netsim-test: Hello Netsim
#[test]
fn test_init_for_test() {
    init_for_test();
    log::info!("Hello Netsim");
}
