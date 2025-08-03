/*
 * This file is partially derived from src/lib.rs in the Rust test library, used
 * under the Apache License, Version 2.0. The following is the original
 * copyright information from the Rust project:
 *
 * Copyrights in the Rust project are retained by their contributors. No
 * copyright assignment is required to contribute to the Rust project.
 *
 * Some files include explicit copyright notices and/or license notices.
 * For full authorship information, see the version control history or
 * https://thanks.rust-lang.org
 *
 * Except as otherwise noted (below and/or in individual files), Rust is
 * licensed under the Apache License, Version 2.0 <LICENSE-APACHE> or
 * <http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
 * <LICENSE-MIT> or <http://opensource.org/licenses/MIT>, at your option.
 *
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

//! # Trusty Rust Testing Framework

use core::cell::RefCell;
use log::{Log, Metadata, Record};
use tipc::{
    ConnectResult, Handle, Manager, MessageResult, PortCfg, Serialize, Serializer, Service, Uuid,
};
use trusty_log::{TrustyLogger, TrustyLoggerConfig};
use trusty_std::alloc::Vec;

// Public reexports
pub use self::bench::Bencher;
pub use self::options::{ColorConfig, Options, OutputFormat, RunIgnored, ShouldPanic};
pub use self::types::TestName::*;
pub use self::types::*;

pub mod asserts;
mod bench;
mod context;
mod macros;
mod options;
mod types;

use context::CONTEXT;

extern "Rust" {
    static TEST_PORT: &'static str;
}

/// Initialize a test service for this crate.
///
/// Including an invocation of this macro exactly once is required to configure a
/// crate to set up the Trusty Rust test framework.
///
/// # Examples
///
/// ```
/// #[cfg(test)]
/// mod test {
///     // Initialize the test framework
///     test::init!();
///
///     #[test]
///     fn test() {}
/// }
/// ```
#[macro_export]
macro_rules! init {
    () => {
        #[cfg(test)]
        #[used]
        #[no_mangle]
        pub static TEST_PORT: &'static str = env!(
            "TRUSTY_TEST_PORT",
            "Expected TRUSTY_TEST_PORT environment variable to be set during compilation",
        );
    };
}

// TestMessage::Message doesn't have a use yet
#[allow(dead_code)]
enum TestMessage<'m> {
    Passed,
    Failed,
    Message(&'m str),
}

impl<'m, 's> Serialize<'s> for TestMessage<'m> {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        match self {
            TestMessage::Passed => serializer.serialize_bytes(&[0u8]),
            TestMessage::Failed => serializer.serialize_bytes(&[1u8]),
            TestMessage::Message(msg) => {
                serializer.serialize_bytes(&[2u8])?;
                serializer.serialize_bytes(msg.as_bytes())
            }
        }
    }
}

pub struct TrustyTestLogger {
    stderr_logger: TrustyLogger,
    client_connection: RefCell<Option<Handle>>,
}

// SAFETY: This is not actually thread-safe, but we don't implement Mutex in
// Trusty's std yet.
unsafe impl Sync for TrustyTestLogger {}

impl TrustyTestLogger {
    const fn new() -> Self {
        Self {
            stderr_logger: TrustyLogger::new(TrustyLoggerConfig::new()),
            client_connection: RefCell::new(None),
        }
    }

    /// Connect a new client to this logger, disconnecting the existing client,
    /// if any.
    fn connect(&self, handle: &Handle) -> tipc::Result<()> {
        let _ = self.client_connection.replace(Some(handle.try_clone()?));
        Ok(())
    }

    /// Disconnect the current client, if connected.
    ///
    /// If there is not a current client, this method does nothing.
    fn disconnect(&self) {
        let _ = self.client_connection.take();
    }
}

impl Log for TrustyTestLogger {
    fn enabled(&self, metadata: &Metadata) -> bool {
        self.stderr_logger.enabled(metadata)
    }

    fn log(&self, record: &Record) {
        if !self.enabled(record.metadata()) {
            return;
        }
        self.stderr_logger.log(record);
        if let Some(client) = self.client_connection.borrow().as_ref() {
            let err = if let Some(msg) = record.args().as_str() {
                // avoid an allocation if message is a static str
                client.send(&TestMessage::Message(msg))
            } else {
                let msg = format!("{}\n", record.args());
                client.send(&TestMessage::Message(&msg))
            };
            if let Err(e) = err {
                eprintln!("Could not send log message to test client: {:?}", e);
            }
        }
    }

    fn flush(&self) {
        self.stderr_logger.flush()
    }
}

static LOGGER: TrustyTestLogger = TrustyTestLogger::new();

fn print_status(test: &TestDesc, msg: &str) {
    log::info!("[ {} ] {}", msg, test.name);
}

struct TestService {
    tests: Vec<TestDescAndFn>,
}

impl Service for TestService {
    type Connection = ();
    type Message = ();

    fn on_connect(
        &self,
        _port: &PortCfg,
        handle: &Handle,
        _peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        LOGGER.connect(handle)?;

        let mut passed_tests = 0;
        let mut failed_tests = 0;
        let mut skipped_tests = 0;
        let mut total_ran = 0;
        for test in &self.tests {
            CONTEXT.reset();
            total_ran += 1;
            print_status(&test.desc, "RUN     ");
            match test.testfn {
                StaticTestFn(f) => f(),
                StaticBenchFn(_f) => panic!("Test harness does not support benchmarking"),
                _ => panic!("non-static tests passed to test::test_main_static"),
            }
            if CONTEXT.skipped() {
                print_status(&test.desc, " SKIPPED");
                skipped_tests += 1;
            } else if CONTEXT.all_ok() {
                print_status(&test.desc, "      OK");
                passed_tests += 1;
            } else {
                print_status(&test.desc, " FAILED ");
                failed_tests += 1;
            }
            if CONTEXT.hard_fail() {
                break;
            }
        }

        log::info!("[==========] {} tests ran.", total_ran);
        if passed_tests > 0 {
            log::info!("[  PASSED  ] {} tests.", passed_tests);
        }
        if skipped_tests > 0 {
            log::info!("[  SKIPPED ] {} tests.", skipped_tests);
        }
        if failed_tests > 0 {
            log::info!("[  FAILED  ] {} tests.", failed_tests);
        }

        let response = if failed_tests == 0 { TestMessage::Passed } else { TestMessage::Failed };
        handle.send(&response)?;

        LOGGER.disconnect();
        Ok(ConnectResult::CloseConnection)
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        _handle: &Handle,
        _msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        Ok(MessageResult::CloseConnection)
    }

    fn on_disconnect(&self, _connection: &Self::Connection) {
        LOGGER.disconnect();
    }
}

/// A variant optimized for invocation with a static test vector.
/// This will panic (intentionally) when fed any dynamic tests.
///
/// This is the entry point for the main function generated by `rustc --test`
/// when panic=abort.
pub fn test_main_static_abort(tests: &[&TestDescAndFn]) {
    log::set_logger(&LOGGER).expect("Could not set global logger");
    log::set_max_level(log::LevelFilter::Info);

    let owned_tests: Vec<_> = tests.iter().map(make_owned_test).collect();

    // SAFETY: This static is declared in the crate being tested, so must be
    // external. This static should only ever be defined by the macro above.
    let port_str = unsafe { TEST_PORT };

    let cfg = PortCfg::new(port_str)
        .expect("Could not create port config")
        .allow_ta_connect()
        .allow_ns_connect();

    let test_service = TestService { tests: owned_tests };

    let buffer = [0u8; 4096];
    Manager::<_, _, 1, 4>::new(test_service, cfg, buffer)
        .expect("Could not create service manager")
        .run_event_loop()
        .expect("Test event loop failed");
}

/// Clones static values for putting into a dynamic vector, which test_main()
/// needs to hand out ownership of tests to parallel test runners.
///
/// This will panic when fed any dynamic tests, because they cannot be cloned.
fn make_owned_test(test: &&TestDescAndFn) -> TestDescAndFn {
    match test.testfn {
        StaticTestFn(f) => TestDescAndFn { testfn: StaticTestFn(f), desc: test.desc.clone() },
        StaticBenchFn(f) => TestDescAndFn { testfn: StaticBenchFn(f), desc: test.desc.clone() },
        _ => panic!("non-static tests passed to test::test_main_static"),
    }
}

/// Invoked when unit tests terminate. The normal Rust test harness supports
/// tests which return values, we don't, so we require the test to return unit.
pub fn assert_test_result(_result: ()) {}

/// Skip the current test case.
pub fn skip() {
    CONTEXT.skip();
}
