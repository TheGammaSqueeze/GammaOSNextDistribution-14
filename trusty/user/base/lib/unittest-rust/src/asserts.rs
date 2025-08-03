/*
 * This file is partially derived from src/panicking.rs in the Rust libcore,
 * used under the Apache License, Version 2.0. The following is the original
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

use crate::context::CONTEXT;
use std::fmt;
use std::panic::Location;

#[derive(Debug)]
#[doc(hidden)]
pub enum AssertKind {
    Eq,
    Ne,
    Match,
}

#[cold]
#[track_caller]
#[doc(hidden)]
pub fn assert_failed<T, U>(kind: AssertKind, left: &T, right: &U, args: Option<fmt::Arguments<'_>>)
where
    T: fmt::Debug + ?Sized,
    U: fmt::Debug + ?Sized,
{
    assert_failed_inner(kind, &left, &right, args)
}

#[track_caller]
pub fn assert_failed_inner(
    kind: AssertKind,
    left: &dyn fmt::Debug,
    right: &dyn fmt::Debug,
    args: Option<fmt::Arguments<'_>>,
) {
    let op = match kind {
        AssertKind::Eq => "==",
        AssertKind::Ne => "!=",
        AssertKind::Match => "matches",
    };

    match args {
        Some(args) => eprintln!(
            r#"assertion failed: `(left {} right)`
  left: `{:?}`,
 right: `{:?}`: {}, {}"#,
            op,
            left,
            right,
            args,
            Location::caller(),
        ),
        None => eprintln!(
            r#"assertion failed: `(left {} right)`
  left: `{:?}`,
 right: `{:?}`, {}"#,
            op,
            left,
            right,
            Location::caller(),
        ),
    }

    CONTEXT.fail(false);
}

#[track_caller]
#[doc(hidden)]
pub fn assert_err<E: fmt::Display>(
    result: &'static str,
    err: &E,
    args: Option<fmt::Arguments<'_>>,
) {
    assert_err_inner(result, &*err, args);
}

#[track_caller]
#[doc(hidden)]
pub fn assert_err_inner(
    result: &'static str,
    err: &dyn fmt::Display,
    args: Option<fmt::Arguments<'_>>,
) {
    match args {
        Some(args) => eprintln!(
            r#"assertion failed: `{}` not Ok
error: `{}` : {}, {}`"#,
            result,
            err,
            args,
            Location::caller(),
        ),
        None => eprintln!(
            r#"assertion failed: `{}` not Ok
error: `{}`, {}`"#,
            result,
            err,
            Location::caller(),
        ),
    }
    CONTEXT.fail(false);
}

#[track_caller]
pub fn simple_assert_failed(cond: &'static str, args: Option<fmt::Arguments<'_>>) {
    match args {
        Some(args) => eprintln!("assertion failed: {}, {}", args, Location::caller()),
        None => eprintln!("assertion failed: {}, {}", cond, Location::caller()),
    }

    CONTEXT.fail(false);
}
