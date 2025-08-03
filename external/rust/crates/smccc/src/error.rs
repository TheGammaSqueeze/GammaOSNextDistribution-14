// Copyright 2023 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

//! Utility functions for error handling.
//!
//! These functions can be combined with the appropriate HVC or SMC functions to wrap calls which
//! return a single value where negative values indicate an error.
//!
//! For example, the [`system_off`](crate::psci::system_off) function is implemented approximately
//! as:
//!
//! ```
//! # #[cfg(target_arch = "aarch64")]
//! use smccc::{
//!     error::success_or_error_32,
//!     psci::{error::Error, PSCI_SYSTEM_OFF},
//!     smc32,
//! };
//!
//! # #[cfg(target_arch = "aarch64")]
//! pub fn system_off() -> Result<(), Error> {
//!     success_or_error_32(smc32(PSCI_SYSTEM_OFF, [0; 7])[0])
//! }
//! ```

/// A value commonly returned to indicate a successful SMCCC call.
pub const SUCCESS: i32 = 0;

/// Converts the given value (returned from an HVC32 or SMC32 call) either to `Ok(())` if it is
/// equal to [`SUCCESS`], or else an error of the given type.
pub fn success_or_error_32<E: From<i32>>(value: u32) -> Result<(), E> {
    let value = value as i32;
    if value == SUCCESS {
        Ok(())
    } else {
        Err(value.into())
    }
}

/// Converts the given value (returned from an HVC64 or SMC64 call) either to `Ok(())` if it is
/// equal to [`SUCCESS`], or else an error of the given type.
pub fn success_or_error_64<E: From<i64>>(value: u64) -> Result<(), E> {
    let value = value as i64;
    if value == SUCCESS.into() {
        Ok(())
    } else {
        Err(value.into())
    }
}

/// Returns `Ok(value)` if the given value has its high bit unset (i.e. would be positive when
/// treated as a signed value), or an error of the given type if the high bit is set.
///
/// This is intended to be used with the return value of [`hvc32`](super::hvc32) or
/// [`smc32`](super::smc32).
pub fn positive_or_error_32<E: From<i32>>(value: u32) -> Result<u32, E> {
    let signed = value as i32;
    if signed < 0 {
        Err(signed.into())
    } else {
        Ok(value)
    }
}

/// Returns `Ok(value)` if the given value has its high bit unset (i.e. would be positive when
/// treated as a signed value), or an error of the given type if the high bit is set.
///
/// This is intended to be used with the return value of [`hvc64`](super::hvc64) or
/// [`smc64`](super::smc64).
pub fn positive_or_error_64<E: From<i64>>(value: u64) -> Result<u64, E> {
    let signed = value as i64;
    if signed < 0 {
        Err(signed.into())
    } else {
        Ok(value)
    }
}
