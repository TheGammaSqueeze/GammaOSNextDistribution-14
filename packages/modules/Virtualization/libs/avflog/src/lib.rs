// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Provides random utilities for components in AVF

use log::error;
use std::fmt::Debug;

/// Convenient trait for logging an error while returning it
pub trait LogResult<T, E> {
    /// If this is `Err`, the error is debug-formatted and is logged via `error!`.
    fn with_log(self) -> Result<T, E>;
}

impl<T, E: Debug> LogResult<T, E> for Result<T, E> {
    fn with_log(self) -> Result<T, E> {
        self.map_err(|e| {
            error!("{e:?}");
            e
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use log::{LevelFilter, Log, Metadata, Record};
    use std::cell::RefCell;
    use std::io::{Error, ErrorKind};

    struct TestLogger {
        last_log: RefCell<String>,
    }
    static TEST_LOGGER: TestLogger = TestLogger { last_log: RefCell::new(String::new()) };

    // SAFETY: TestLogger is used only inside the test which is single-treaded.
    unsafe impl Sync for TestLogger {}

    impl Log for TestLogger {
        fn enabled(&self, _metadata: &Metadata) -> bool {
            true
        }
        fn log(&self, record: &Record) {
            *self.last_log.borrow_mut() = format!("{}", record.args());
        }
        fn flush(&self) {}
    }

    #[test]
    fn test_logresult_emits_error_log() {
        log::set_logger(&TEST_LOGGER).unwrap();
        log::set_max_level(LevelFilter::Info);

        let e = Error::from(ErrorKind::NotFound);
        let res: Result<(), Error> = Err(e).with_log();

        assert!(res.is_err());
        assert_eq!(TEST_LOGGER.last_log.borrow().as_str(), "Kind(NotFound)");
    }
}
