//! Modify the Bluetooth logging configuration to enable debug logging.
//!
//! There are two logging implementations depending on whether the log is
//! emitted from Rust or C/C++. In order to keep log levels in sync between the
//! two, the |BluetoothLogging| struct will configure both the Rust logging and
//! the C/C++ logging (via topshim).
use bt_topshim::syslog::{set_default_log_level, set_log_level_for_tag, Level};
use log::LevelFilter;
use syslog::{BasicLogger, Error, Facility, Formatter3164};

use log_panics;

/// API to modify log levels that is exposed via RPC.
pub trait IBluetoothLogging {
    /// Check whether debug logging is enabled.
    fn is_debug_enabled(&self) -> bool;

    /// Change whether debug logging is enabled.
    fn set_debug_logging(&mut self, enabled: bool);
}

/// Logging related implementation.
pub struct BluetoothLogging {
    /// Should debug logs be emitted?
    is_debug: bool,

    /// If this flag is not set, we will not emit debug logs for all tags.
    /// `VERBOSE_ONLY_LOG_TAGS` will be set to emit up to `INFO` only. This
    /// can only be configured in the constructor (not modifiable at runtime).
    is_verbose_debug: bool,

    /// Log to stderr?
    is_stderr: bool,

    /// Is logging already initialized?
    is_initialized: bool,
}

const VERBOSE_ONLY_LOG_TAGS: &[&str] = &[
    "bt_bta_av", // AV apis
    "btm_sco",   // SCO data path logs
    "l2c_csm",   // L2CAP state machine
    "l2c_link",  // L2CAP link layer logs
    "sco_hci",   // SCO over HCI
    "uipc",      // Userspace IPC implementation
];

impl BluetoothLogging {
    pub fn new(is_debug: bool, is_verbose_debug: bool, log_output: &str) -> Self {
        let is_stderr = log_output == "stderr";
        Self { is_debug, is_verbose_debug, is_stderr, is_initialized: false }
    }

    pub fn initialize(&mut self) -> Result<(), Error> {
        let level = if self.is_debug { LevelFilter::Debug } else { LevelFilter::Info };

        if self.is_stderr {
            env_logger::Builder::new().filter(None, level).init();
        } else {
            let formatter = Formatter3164 {
                facility: Facility::LOG_USER,
                hostname: None,
                process: "btadapterd".into(),
                pid: 0,
            };

            let logger = syslog::unix(formatter)?;
            let _ = log::set_boxed_logger(Box::new(BasicLogger::new(logger)))
                .map(|()| log::set_max_level(level));
            log_panics::init();
        }

        // Set initial log levels and filter out tags if not verbose debug.
        set_default_log_level(self.get_libbluetooth_level());
        if self.is_debug && !self.is_verbose_debug {
            for tag in VERBOSE_ONLY_LOG_TAGS {
                set_log_level_for_tag(tag, Level::Info);
            }
        }

        // Initialize the underlying system as well.
        self.is_initialized = true;
        Ok(())
    }

    fn get_libbluetooth_level(&self) -> Level {
        if self.is_debug {
            if self.is_verbose_debug {
                Level::Verbose
            } else {
                Level::Debug
            }
        } else {
            Level::Info
        }
    }
}

impl IBluetoothLogging for BluetoothLogging {
    fn is_debug_enabled(&self) -> bool {
        self.is_initialized && self.is_debug
    }

    fn set_debug_logging(&mut self, enabled: bool) {
        if !self.is_initialized {
            return;
        }

        self.is_debug = enabled;

        // Update log level in Linux stack.
        let level = if self.is_debug { LevelFilter::Debug } else { LevelFilter::Info };
        log::set_max_level(level);

        // Update log level in libbluetooth.
        let level = self.get_libbluetooth_level();
        set_default_log_level(level);

        // Mark the start of debug logging with a debug print.
        if self.is_debug {
            log::debug!("Debug logging successfully enabled!");
        }

        log::info!("Setting debug logging to {}", self.is_debug);
    }
}
