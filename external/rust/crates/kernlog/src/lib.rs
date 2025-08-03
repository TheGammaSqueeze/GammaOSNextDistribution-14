//! Logger implementation for low level kernel log (using `/dev/kmsg`)
//!
//! Usually intended for low level implementations, like [systemd generators][1],
//! which have to use `/dev/kmsg`:
//!
//! > Since syslog is not available (see above) write log messages to /dev/kmsg instead.
//!
//! [1]: http://www.freedesktop.org/wiki/Software/systemd/Generators/
//!
//! # Examples
//!
//! ```toml
//! [dependencies]
//! log = "0.4"
//! kernlog = "0.3"
//! ```
//! 
//! ```rust
//! #[macro_use]
//! extern crate log;
//! extern crate kernlog;
//! 
//! fn main() {
//!     kernlog::init().unwrap();
//!     warn!("something strange happened");
//! }
//! ```
//! Note you have to have permissions to write to `/dev/kmsg`,
//! which normal users (not root) usually don't.
//! 
//! If compiled with nightly it can use libc feature to get process id
//! and report it into log. This feature is unavailable for stable release
//! for now. To enable nightly features, compile with `--features nightly`:
//!
//! ```toml
//! [dependencies.kernlog]
//! version = "*"
//! features = ["nightly"]
//! ```

#![deny(missing_docs)]
#![cfg_attr(feature="nightly", feature(libc))]

#[macro_use]
extern crate log;
extern crate libc;

use std::fs::{OpenOptions, File};
use std::io::{Error, ErrorKind, Write, self};
use std::sync::Mutex;
use std::env;

use log::{Log, Metadata, Record, Level, LevelFilter, SetLoggerError};

/// Kernel logger implementation
pub struct KernelLog {
    kmsg: Mutex<File>,
    maxlevel: LevelFilter
}

impl KernelLog {
    /// Create new kernel logger
    pub fn new() -> io::Result<KernelLog> {
        KernelLog::with_level(LevelFilter::Trace)
    }

    /// Create new kernel logger from `KERNLOG_LEVEL` environment variable
    pub fn from_env() -> io::Result<KernelLog> {
        match env::var("KERNLOG_LEVEL") {
            Err(_) => KernelLog::new(),
            Ok(s) => match s.parse() {
                Ok(filter) => KernelLog::with_level(filter),
                Err(_) => KernelLog::new(),
            }
        }
    }

    #[cfg(not(target_os = "android"))]
    fn open_kmsg() -> io::Result<File> {
        OpenOptions::new().write(true).open("/dev/kmsg")
    }

    #[cfg(target_os = "android")]
    fn open_kmsg() -> io::Result<File> {
        // In Android, a process normally doesn't have the permission to open /dev/kmsg. Instead it
        // is opened by init (via `file /dev/kmsg w` in the rc file) and the file descriptor is
        // shared when executing the process. The file descriptor number is passed via an
        // environment variable "ANDROID_FILE_<file_name>" where <file_name> is the path to the
        // file where non alpha-numeric characters are replaced with '_'.
        match env::var("ANDROID_FILE__dev_kmsg") {
            Ok(val) => OpenOptions::new().write(true).open(format!("/proc/self/fd/{}", val)),
            Err(e) => Err(Error::new(ErrorKind::Other, "ANDROID_FILE__dev_kmsg doesn't exist")),
        }
    }

    /// Create new kernel logger with error level filter
    pub fn with_level(filter: LevelFilter) -> io::Result<KernelLog> {
        Ok(KernelLog {
            kmsg: Mutex::new(Self::open_kmsg()?),
            maxlevel: filter
        })
    }
}

impl Log for KernelLog {
    fn enabled(&self, meta: &Metadata) -> bool {
        meta.level() <= self.maxlevel
    }

    fn log(&self, record: &Record) {
        if record.level() > self.maxlevel {
            return;
        }

        let level: u8 = match record.level() {
            Level::Error => 3,
            Level::Warn => 4,
            Level::Info => 5,
            Level::Debug => 6,
            Level::Trace => 7,
        };

        let mut buf = Vec::new();
        writeln!(buf, "<{}>{}[{}]: {}", level, record.target(),
                 unsafe { ::libc::getpid() },
                 record.args()).unwrap();

        if let Ok(mut kmsg) = self.kmsg.lock() {
            let _ = kmsg.write(&buf);
            let _ = kmsg.flush();
        }
    }

    fn flush(&self) {}
}

/// KernelLog initialization error
#[derive(Debug)]
pub enum KernelLogInitError {
    /// IO error
    Io(io::Error),
    /// Set logger error
    Log(SetLoggerError)
}

impl std::fmt::Display for KernelLogInitError {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            KernelLogInitError::Io(err) => err.fmt(f),
            KernelLogInitError::Log(err) => err.fmt(f),
        }
    }
}

impl std::error::Error for KernelLogInitError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            KernelLogInitError::Io(err) => Some(err),
            KernelLogInitError::Log(err) => Some(err),
        }
    }
}

impl From<SetLoggerError> for KernelLogInitError {
    fn from(err: SetLoggerError) -> Self {
        KernelLogInitError::Log(err)
    }
}
impl From<io::Error> for KernelLogInitError {
    fn from(err: io::Error) -> Self {
        KernelLogInitError::Io(err)
    }
}

/// Setup kernel logger as a default logger
pub fn init() -> Result<(), KernelLogInitError> {
    let klog = KernelLog::from_env()?;
    let maxlevel = klog.maxlevel;
    log::set_boxed_logger(Box::new(klog))?;
    log::set_max_level(maxlevel);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{KernelLog, init};

    #[test]
    fn log_to_kernel() {
        init().unwrap();
        debug!("hello, world!");
    }
}
