#![no_main]
#![allow(missing_docs)]

use android_logger::{AndroidLogger, Config, Filter, FilterBuilder, LogId};
use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use log::{Level, LevelFilter, Log, Record};
use std::ffi::CString;

// TODO: Remove once Level and LevelInput Arbitrary derivations are upstreamed.
// https://github.com/rust-lang/log/issues/530
#[derive(Arbitrary, Copy, Clone, Debug)]
pub enum LevelInput {
    Error,
    Warn,
    Info,
    Debug,
    Trace,
}

impl From<LevelInput> for Level {
    fn from(l: LevelInput) -> Level {
        match l {
            LevelInput::Error => Level::Error,
            LevelInput::Warn => Level::Warn,
            LevelInput::Info => Level::Info,
            LevelInput::Debug => Level::Debug,
            LevelInput::Trace => Level::Trace,
        }
    }
}

impl From<LevelInput> for LevelFilter {
    fn from(l: LevelInput) -> LevelFilter {
        match l {
            LevelInput::Error => LevelFilter::Error,
            LevelInput::Warn => LevelFilter::Warn,
            LevelInput::Info => LevelFilter::Info,
            LevelInput::Debug => LevelFilter::Debug,
            LevelInput::Trace => LevelFilter::Trace,
        }
    }
}

#[derive(Arbitrary, Debug)]
pub enum LevelFilterInput {
    Off,
    Error,
    Warn,
    Info,
    Debug,
    Trace,
}

impl From<&LevelFilterInput> for LevelFilter {
    fn from(l: &LevelFilterInput) -> LevelFilter {
        match l {
            LevelFilterInput::Off => LevelFilter::Off,
            LevelFilterInput::Error => LevelFilter::Error,
            LevelFilterInput::Warn => LevelFilter::Warn,
            LevelFilterInput::Info => LevelFilter::Info,
            LevelFilterInput::Debug => LevelFilter::Debug,
            LevelFilterInput::Trace => LevelFilter::Trace,
        }
    }
}

#[derive(Arbitrary, Copy, Clone, Debug)]
pub enum LogIdInput {
    Main,
    Radio,
    Events,
    System,
    Crash,
}

impl From<LogIdInput> for LogId {
    fn from(l: LogIdInput) -> LogId {
        match l {
            LogIdInput::Main => LogId::Main,
            LogIdInput::Radio => LogId::Radio,
            LogIdInput::Events => LogId::Events,
            LogIdInput::System => LogId::System,
            LogIdInput::Crash => LogId::Crash,
        }
    }
}

#[derive(Arbitrary, Debug)]
struct ConfigInput {
    log_level: LevelInput,
    log_id: LogIdInput,
    filters: Vec<(Option<String>, LevelFilterInput)>,
    tag: CString,
}

impl ConfigInput {
    fn get_filter(&self) -> Filter {
        let mut builder = FilterBuilder::new();
        for (name, level) in &self.filters {
            builder.filter(name.as_deref(), level.into());
        }
        builder.build()
    }
}

impl From<ConfigInput> for Config {
    fn from(config_input: ConfigInput) -> Config {
        Config::default()
            .with_filter(config_input.get_filter())
            .with_max_level(config_input.log_level.into())
            .with_tag(config_input.tag)
            .with_log_buffer(config_input.log_id.into())
    }
}

#[derive(Arbitrary, Debug)]
struct RecordInput {
    log_level: LevelInput,
    target: String,
    module_path: Option<String>,
    file: Option<String>,
    line: Option<u32>,
    message: String,
}

#[derive(Arbitrary, Debug)]
struct LoggerInput {
    config_input: ConfigInput,
    record_input: RecordInput,
}

fuzz_target!(|logger_input: LoggerInput| {
    let config: Config = logger_input.config_input.into();
    let logger = AndroidLogger::new(config);
    let record_input = &logger_input.record_input;
    logger.log(
        &Record::builder()
            .args(format_args!("{}", record_input.message))
            .level(record_input.log_level.into())
            .target(&record_input.target)
            .file(record_input.file.as_deref())
            .line(record_input.line)
            .module_path(record_input.module_path.as_deref())
            .build(),
    );
});
