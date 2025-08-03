// Copyright 2022 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

//! Functions for version 1.4 of the Arm SMC Calling Convention and version 1.1 of the Arm Power
//! State Coordination Interface (PSCI) version 1.1, and relevant constants.
//!
//! Note that the PSCI and SMCCC arch calls may be made via either HVC or SMC. You can choose which
//! one to use by passing either [`Hvc`] or [`Smc`] as a type parameter to the relevant function.
//!
//! This crate currently only supports aarch64 and the SMC64 versions of the PSCI calls, in the
//! cases that both SMC32 and SMC64 versions exist.

#![no_std]

pub mod arch;
pub mod error;
pub mod psci;

/// Use a Hypervisor Call (HVC).
#[cfg(target_arch = "aarch64")]
pub struct Hvc;

/// Use a Secure Moniter Call (SMC).
#[cfg(target_arch = "aarch64")]
pub struct Smc;

/// Functions to make an HVC or SMC call.
pub trait Call {
    /// Makes a call using the 32-bit calling convention.
    fn call32(function: u32, args: [u32; 7]) -> [u32; 8];
    /// Makes a call using the 64-bit calling convention.
    fn call64(function: u32, args: [u64; 17]) -> [u64; 18];
}

#[cfg(target_arch = "aarch64")]
impl Call for Hvc {
    fn call32(function: u32, args: [u32; 7]) -> [u32; 8] {
        hvc32(function, args)
    }

    fn call64(function: u32, args: [u64; 17]) -> [u64; 18] {
        hvc64(function, args)
    }
}

#[cfg(target_arch = "aarch64")]
impl Call for Smc {
    fn call32(function: u32, args: [u32; 7]) -> [u32; 8] {
        smc32(function, args)
    }

    fn call64(function: u32, args: [u64; 17]) -> [u64; 18] {
        smc64(function, args)
    }
}

/// Makes an HVC32 call to the hypervisor, following the SMC Calling Convention version 1.3.
#[cfg(target_arch = "aarch64")]
#[inline(always)]
pub fn hvc32(function: u32, args: [u32; 7]) -> [u32; 8] {
    unsafe {
        let mut ret = [0; 8];

        core::arch::asm!(
            "hvc #0",
            inout("w0") function => ret[0],
            inout("w1") args[0] => ret[1],
            inout("w2") args[1] => ret[2],
            inout("w3") args[2] => ret[3],
            inout("w4") args[3] => ret[4],
            inout("w5") args[4] => ret[5],
            inout("w6") args[5] => ret[6],
            inout("w7") args[6] => ret[7],
            options(nomem, nostack)
        );

        ret
    }
}

/// Makes an SMC32 call to the firmware, following the SMC Calling Convention version 1.3.
#[cfg(target_arch = "aarch64")]
#[inline(always)]
pub fn smc32(function: u32, args: [u32; 7]) -> [u32; 8] {
    unsafe {
        let mut ret = [0; 8];

        core::arch::asm!(
            "smc #0",
            inout("w0") function => ret[0],
            inout("w1") args[0] => ret[1],
            inout("w2") args[1] => ret[2],
            inout("w3") args[2] => ret[3],
            inout("w4") args[3] => ret[4],
            inout("w5") args[4] => ret[5],
            inout("w6") args[5] => ret[6],
            inout("w7") args[6] => ret[7],
            options(nomem, nostack)
        );

        ret
    }
}

/// Makes an HVC64 call to the hypervisor, following the SMC Calling Convention version 1.3.
#[cfg(target_arch = "aarch64")]
#[inline(always)]
pub fn hvc64(function: u32, args: [u64; 17]) -> [u64; 18] {
    unsafe {
        let mut ret = [0; 18];

        core::arch::asm!(
            "hvc #0",
            inout("x0") function as u64 => ret[0],
            inout("x1") args[0] => ret[1],
            inout("x2") args[1] => ret[2],
            inout("x3") args[2] => ret[3],
            inout("x4") args[3] => ret[4],
            inout("x5") args[4] => ret[5],
            inout("x6") args[5] => ret[6],
            inout("x7") args[6] => ret[7],
            inout("x8") args[7] => ret[8],
            inout("x9") args[8] => ret[9],
            inout("x10") args[9] => ret[10],
            inout("x11") args[10] => ret[11],
            inout("x12") args[11] => ret[12],
            inout("x13") args[12] => ret[13],
            inout("x14") args[13] => ret[14],
            inout("x15") args[14] => ret[15],
            inout("x16") args[15] => ret[16],
            inout("x17") args[16] => ret[17],
            options(nomem, nostack)
        );

        ret
    }
}

/// Makes an SMC64 call to the firmware, following the SMC Calling Convention version 1.3.
#[cfg(target_arch = "aarch64")]
#[inline(always)]
pub fn smc64(function: u32, args: [u64; 17]) -> [u64; 18] {
    unsafe {
        let mut ret = [0; 18];

        core::arch::asm!(
            "smc #0",
            inout("x0") function as u64 => ret[0],
            inout("x1") args[0] => ret[1],
            inout("x2") args[1] => ret[2],
            inout("x3") args[2] => ret[3],
            inout("x4") args[3] => ret[4],
            inout("x5") args[4] => ret[5],
            inout("x6") args[5] => ret[6],
            inout("x7") args[6] => ret[7],
            inout("x8") args[7] => ret[8],
            inout("x9") args[8] => ret[9],
            inout("x10") args[9] => ret[10],
            inout("x11") args[10] => ret[11],
            inout("x12") args[11] => ret[12],
            inout("x13") args[12] => ret[13],
            inout("x14") args[13] => ret[14],
            inout("x15") args[14] => ret[15],
            inout("x16") args[15] => ret[16],
            inout("x17") args[16] => ret[17],
            options(nomem, nostack)
        );

        ret
    }
}
