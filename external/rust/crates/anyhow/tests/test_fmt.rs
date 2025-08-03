use anyhow::{bail, Context, Result};
use std::io;

fn f() -> Result<()> {
    bail!(io::Error::new(io::ErrorKind::PermissionDenied, "oh no!"));
}

fn g() -> Result<()> {
    f().context("f failed")
}

fn h() -> Result<()> {
    g().context("g failed")
}

const EXPECTED_ALTDISPLAY_F: &str = "oh no!";

const EXPECTED_ALTDISPLAY_G: &str = "f failed: oh no!";

const EXPECTED_ALTDISPLAY_H: &str = "g failed: f failed: oh no!";

const EXPECTED_DEBUG_F: &str = "oh no!";

const EXPECTED_DEBUG_G: &str = "\
f failed

Caused by:
    oh no!\
";

const EXPECTED_DEBUG_H: &str = "\
g failed

Caused by:
    0: f failed
    1: oh no!\
";

const EXPECTED_ALTDEBUG_F: &str = "\
Custom {
    kind: PermissionDenied,
    error: \"oh no!\",
}\
";

const EXPECTED_ALTDEBUG_G: &str = "\
Error {
    context: \"f failed\",
    source: Custom {
        kind: PermissionDenied,
        error: \"oh no!\",
    },
}\
";

const EXPECTED_ALTDEBUG_H: &str = "\
Error {
    context: \"g failed\",
    source: Error {
        context: \"f failed\",
        source: Custom {
            kind: PermissionDenied,
            error: \"oh no!\",
        },
    },
}\
";

#[test]
fn test_display() {
    assert!(h().unwrap_err().to_string().starts_with("g failed"));
}

#[test]
fn test_altdisplay() {
    assert!(format!("{:#}", f().unwrap_err()).starts_with(EXPECTED_ALTDISPLAY_F));
    assert!(format!("{:#}", g().unwrap_err()).starts_with(EXPECTED_ALTDISPLAY_G));
    assert!(format!("{:#}", h().unwrap_err()).starts_with(EXPECTED_ALTDISPLAY_H));
}

#[test]
#[cfg_attr(not(std_backtrace), ignore)]
fn test_debug() {
    assert!(format!("{:?}", f().unwrap_err()).starts_with(EXPECTED_DEBUG_F));
    assert!(format!("{:?}", g().unwrap_err()).starts_with(EXPECTED_DEBUG_G));
    assert!(format!("{:?}", h().unwrap_err()).starts_with(EXPECTED_DEBUG_H));
}

#[test]
fn test_altdebug() {
    assert!(format!("{:#?}", f().unwrap_err()).starts_with(EXPECTED_ALTDEBUG_F));
    assert!(format!("{:#?}", g().unwrap_err()).starts_with(EXPECTED_ALTDEBUG_G));
    assert!(format!("{:#?}", h().unwrap_err()).starts_with(EXPECTED_ALTDEBUG_H));
}
