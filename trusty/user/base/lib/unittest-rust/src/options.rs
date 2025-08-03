/*
 * This file is derived from src/options.rs in the Rust test library, used under
 * the Apache License, Version 2.0. The following is the original copyright
 * information from the Rust project:
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

#![allow(dead_code)]

//! Enums denoting options for test execution.

/// Whether to execute tests concurrently or not
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Concurrent {
    Yes,
    No,
}

/// Number of times to run a benchmarked function
#[derive(Clone, PartialEq, Eq)]
pub enum BenchMode {
    Auto,
    Single,
}

/// Whether test is expected to panic or not
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum ShouldPanic {
    No,
    Yes,
    YesWithMessage(&'static str),
}

/// Whether should console output be colored or not
#[derive(Copy, Clone, Debug)]
pub enum ColorConfig {
    AutoColor,
    AlwaysColor,
    NeverColor,
}

/// Format of the test results output
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum OutputFormat {
    /// Verbose output
    Pretty,
    /// Quiet output
    Terse,
    /// JSON output
    Json,
}

/// Whether ignored test should be run or not
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum RunIgnored {
    Yes,
    No,
    /// Run only ignored tests
    Only,
}

#[derive(Clone, Copy)]
pub enum RunStrategy {
    /// Runs the test in the current process, and sends the result back over the
    /// supplied channel.
    InProcess,

    /// Spawns a subprocess to run the test, and sends the result back over the
    /// supplied channel. Requires `argv[0]` to exist and point to the binary
    /// that's currently running.
    SpawnPrimary,
}

/// Options for the test run defined by the caller (instead of CLI arguments).
/// In case we want to add other options as well, just add them in this struct.
#[derive(Copy, Clone, Debug)]
pub struct Options {
    pub display_output: bool,
    pub panic_abort: bool,
}

impl Options {
    pub fn new() -> Options {
        Options { display_output: false, panic_abort: false }
    }

    pub fn display_output(mut self, display_output: bool) -> Options {
        self.display_output = display_output;
        self
    }

    pub fn panic_abort(mut self, panic_abort: bool) -> Options {
        self.panic_abort = panic_abort;
        self
    }
}
