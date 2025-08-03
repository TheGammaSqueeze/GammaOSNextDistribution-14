/*
 * This file is derived from src/types.rs in the Rust test library, used under
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

//! Common types used by `libtest`.

use alloc::borrow::Cow;
use alloc::boxed::Box;
use alloc::string::String;
use core::fmt;

use super::bench::Bencher;
use super::options;

pub use NamePadding::*;
pub use TestFn::*;
pub use TestName::*;

/// Type of the test according to the [rust book](https://doc.rust-lang.org/cargo/guide/tests.html)
/// conventions.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash)]
pub enum TestType {
    /// Unit-tests are expected to be in the `src` folder of the crate.
    UnitTest,
    /// Integration-style tests are expected to be in the `tests` folder of the crate.
    IntegrationTest,
    /// Doctests are created by the `librustdoc` manually, so it's a different type of test.
    DocTest,
    /// Tests for the sources that don't follow the project layout convention
    /// (e.g. tests in raw `main.rs` compiled by calling `rustc --test` directly).
    Unknown,
}

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub enum NamePadding {
    PadNone,
    PadOnRight,
}

// The name of a test. By convention this follows the rules for rust
// paths; i.e., it should be a series of identifiers separated by double
// colons. This way if some test runner wants to arrange the tests
// hierarchically it may.
#[derive(Clone, PartialEq, Eq, Hash, Debug)]
pub enum TestName {
    StaticTestName(&'static str),
    DynTestName(String),
    AlignedTestName(Cow<'static, str>, NamePadding),
}

impl TestName {
    pub fn as_slice(&self) -> &str {
        match *self {
            StaticTestName(s) => s,
            DynTestName(ref s) => s,
            AlignedTestName(ref s, _) => &*s,
        }
    }

    pub fn padding(&self) -> NamePadding {
        match self {
            &AlignedTestName(_, p) => p,
            _ => PadNone,
        }
    }

    pub fn with_padding(&self, padding: NamePadding) -> TestName {
        let name = match *self {
            TestName::StaticTestName(name) => Cow::Borrowed(name),
            TestName::DynTestName(ref name) => Cow::Owned(name.clone()),
            TestName::AlignedTestName(ref name, _) => name.clone(),
        };

        TestName::AlignedTestName(name, padding)
    }
}
impl fmt::Display for TestName {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Display::fmt(self.as_slice(), f)
    }
}

/// Represents a benchmark function.
pub trait TDynBenchFn: Send {
    fn run(&self, harness: &mut Bencher);
}

// A function that runs a test. If the function returns successfully,
// the test succeeds; if the function panics then the test fails. We
// may need to come up with a more clever definition of test in order
// to support isolation of tests into threads.
pub enum TestFn {
    StaticTestFn(fn()),
    StaticBenchFn(fn(&mut Bencher)),
    DynTestFn(Box<dyn FnOnce() + Send>),
    DynBenchFn(Box<dyn TDynBenchFn + 'static>),
}

impl TestFn {
    pub fn padding(&self) -> NamePadding {
        match *self {
            StaticTestFn(..) => PadNone,
            StaticBenchFn(..) => PadOnRight,
            DynTestFn(..) => PadNone,
            DynBenchFn(..) => PadOnRight,
        }
    }
}

impl fmt::Debug for TestFn {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match *self {
            StaticTestFn(..) => "StaticTestFn(..)",
            StaticBenchFn(..) => "StaticBenchFn(..)",
            DynTestFn(..) => "DynTestFn(..)",
            DynBenchFn(..) => "DynBenchFn(..)",
        })
    }
}

// A unique integer associated with each test.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct TestId(pub usize);

// The definition of a single test. A test runner will run a list of
// these.
#[derive(Clone, Debug)]
pub struct TestDesc {
    pub name: TestName,
    pub ignore: bool,
    #[cfg(not(bootstrap))]
    pub ignore_message: Option<&'static str>,
    pub should_panic: options::ShouldPanic,
    pub compile_fail: bool,
    pub no_run: bool,
    pub test_type: TestType,
    pub source_file: &'static str,
    pub start_line: usize,
    pub start_col: usize,
    pub end_line: usize,
    pub end_col: usize,
}

impl TestDesc {
    pub fn padded_name(&self, column_count: usize, align: NamePadding) -> String {
        let mut name = String::from(self.name.as_slice());
        let fill = column_count.saturating_sub(name.len());
        let pad = " ".repeat(fill);
        match align {
            PadNone => name,
            PadOnRight => {
                name.push_str(&pad);
                name
            }
        }
    }

    /// Returns None for ignored test or that that are just run, otherwise give a description of the type of test.
    /// Descriptions include "should panic", "compile fail" and "compile".
    pub fn test_mode(&self) -> Option<&'static str> {
        if self.ignore {
            return None;
        }
        match self.should_panic {
            options::ShouldPanic::Yes | options::ShouldPanic::YesWithMessage(_) => {
                return Some("should panic");
            }
            options::ShouldPanic::No => {}
        }
        if self.compile_fail {
            return Some("compile fail");
        }
        if self.no_run {
            return Some("compile");
        }
        None
    }
}

#[derive(Debug)]
pub struct TestDescAndFn {
    pub desc: TestDesc,
    pub testfn: TestFn,
}
