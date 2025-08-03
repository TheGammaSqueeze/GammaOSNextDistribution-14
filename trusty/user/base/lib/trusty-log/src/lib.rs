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

//! Trusty simple logger backend
//!
//! Logs to stderr based on a compile-time configured log level.

use log::{Level, Log, Metadata, Record};
use std::io::{stderr, Write};
use std::sync::Once;

/// Closure type that can be used by external callers to write a custom log formatter
///
/// # Examples
///
/// ```
/// fn log_function(record: &log::Record) -> String {
///     let line = match record.line() {
///         Some(line) => line,
///         None => 0,
///     };
///     let file = match record.file() {
///         Some(file) => file,
///         None => "unknown file",
///     };
///     format!("{}: MyApp - {}:{} {}\n", record.level(), file, line, record.args())
/// }
/// ```
type FormatFn = Box<dyn Fn(&log::Record) -> String + Sync + Send>;

/// Structure used to modify the logger behavior. It is based on the Android logger configuration
/// and implements a subset of its functionality.
/// It can be used to override the maximum logging level and to provide a custom log formatting
/// function.
/// Its default values are Level::Info for its log level and None for its formatter.
///
/// # Examples
///
/// ```
/// let config = trusty_log::TrustyLoggerConfig::default()
///     .with_min_level(log::Level::Trace)
///     .format(&log_function);
/// ```
pub struct TrustyLoggerConfig {
    log_level: log::Level,
    custom_format: Option<FormatFn>,
}

impl TrustyLoggerConfig {
    pub const fn new() -> Self {
        TrustyLoggerConfig { log_level: Level::Info, custom_format: None }
    }

    pub fn with_min_level(mut self, level: log::Level) -> Self {
        self.log_level = level;
        self
    }

    pub fn format<F>(mut self, format: F) -> Self
    where
        F: Fn(&log::Record) -> String + Sync + Send + 'static,
    {
        self.custom_format = Some(Box::new(format));
        self
    }
}

impl Default for TrustyLoggerConfig {
    fn default() -> Self {
        TrustyLoggerConfig::new()
    }
}

/// Main structure used by the logger operations.
/// The default values for its config are Level::Info for the log level and None for the formatter.
pub struct TrustyLogger {
    config: TrustyLoggerConfig,
}

impl TrustyLogger {
    pub const fn new(config: TrustyLoggerConfig) -> Self {
        TrustyLogger { config }
    }
}

impl Log for TrustyLogger {
    fn enabled(&self, metadata: &Metadata) -> bool {
        metadata.level() <= self.config.log_level
    }

    fn log(&self, record: &Record) {
        if self.enabled(record.metadata()) {
            let message_to_print = match &self.config.custom_format {
                Some(log_function) => log_function(record),
                None => default_log_function(record),
            };
            let _ = stderr().write(message_to_print.as_bytes());
        }
    }

    fn flush(&self) {}
}

fn default_log_function(record: &Record) -> String {
    format!("{} - {}\n", record.level(), record.args())
}

static mut LOGGER: Option<TrustyLogger> = None;
static LOGGER_INIT: Once = Once::new();

pub fn init() {
    init_with_config(TrustyLoggerConfig::default());
}

pub fn init_with_config(config: TrustyLoggerConfig) {
    let log_level_filter = config.log_level.to_level_filter();
    // SAFETY: We are using Once, so the mut global will only be written once even with multiple
    // calls
    let global_logger = unsafe {
        LOGGER_INIT.call_once(|| {
            LOGGER = Some(TrustyLogger::new(config));
        });
        // Logger is always Some(_) at this point, so we just unwrap it
        LOGGER.as_ref().unwrap()
    };
    log::set_logger(global_logger).expect("Could not set global logger");
    log::set_max_level(log_level_filter);
}
