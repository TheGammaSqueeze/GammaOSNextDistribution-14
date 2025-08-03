// Copyright 2022 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

//! Functions to make PSCI calls.

use super::{
    error::Error, AffinityState, LowestAffinityLevel, MigrateType, PowerState, SuspendMode,
    PSCI_AFFINITY_INFO_64, PSCI_CPU_DEFAULT_SUSPEND_64, PSCI_CPU_FREEZE, PSCI_CPU_OFF,
    PSCI_CPU_ON_64, PSCI_CPU_SUSPEND_64, PSCI_FEATURES, PSCI_MEM_PROTECT,
    PSCI_MEM_PROTECT_CHECK_RANGE_64, PSCI_MIGRATE_64, PSCI_MIGRATE_INFO_TYPE,
    PSCI_MIGRATE_INFO_UP_CPU_64, PSCI_NODE_HW_STATE_64, PSCI_SET_SUSPEND_MODE, PSCI_STAT_COUNT_64,
    PSCI_STAT_RESIDENCY_64, PSCI_SYSTEM_OFF, PSCI_SYSTEM_RESET, PSCI_SYSTEM_RESET2_64,
    PSCI_SYSTEM_SUSPEND_64, PSCI_VERSION,
};
use crate::{
    error::{positive_or_error_32, success_or_error_32, success_or_error_64},
    Call,
};

/// Returns the version of PSCI implemented.
pub fn version<C: Call>() -> u32 {
    C::call32(PSCI_VERSION, [0; 7])[0]
}

/// Suspends execution of a core or topology node.
pub fn cpu_suspend<C: Call>(
    power_state: u32,
    entry_point_address: u64,
    context_id: u64,
) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_CPU_SUSPEND_64,
            [
                power_state.into(),
                entry_point_address,
                context_id,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ],
        )[0],
    )
}

/// Powers down the current core.
pub fn cpu_off<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(PSCI_CPU_OFF, [0; 7])[0])
}

/// Powers up a core.
pub fn cpu_on<C: Call>(
    target_cpu: u64,
    entry_point_address: u64,
    context_id: u64,
) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_CPU_ON_64,
            [
                target_cpu,
                entry_point_address,
                context_id,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ],
        )[0],
    )
}

/// Gets the status of an affinity instance.
pub fn affinity_info<C: Call>(
    target_affinity: u64,
    lowest_affinity_level: LowestAffinityLevel,
) -> Result<AffinityState, Error> {
    (C::call64(
        PSCI_AFFINITY_INFO_64,
        [
            target_affinity,
            lowest_affinity_level as u64,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        ],
    )[0] as i32)
        .try_into()
}

/// Asks the Trusted OS to migrate its context to a specific core.
pub fn migrate<C: Call>(target_cpu: u64) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_MIGRATE_64,
            [target_cpu, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
        )[0],
    )
}

/// Identifies the levelof multicore support in the Trusted OS.
pub fn migrate_info_type<C: Call>() -> Result<MigrateType, Error> {
    (C::call32(PSCI_MIGRATE_INFO_TYPE, [0; 7])[0] as i32).try_into()
}

/// Returns the MPIDR value of the current resident core of the Trusted OS.
pub fn migrate_info_up_cpu<C: Call>() -> u64 {
    C::call64(PSCI_MIGRATE_INFO_UP_CPU_64, [0; 17])[0]
}

/// Shuts down the system.
pub fn system_off<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(PSCI_SYSTEM_OFF, [0; 7])[0])
}

/// Resets the system.
pub fn system_reset<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(PSCI_SYSTEM_RESET, [0; 7])[0])
}

/// Resets the system in an architectural or vendor-specific way.
pub fn system_reset2<C: Call>(reset_type: u32, cookie: u64) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_SYSTEM_RESET2_64,
            [
                reset_type.into(),
                cookie,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ],
        )[0],
    )
}

/// Enables or disables memory protection.
pub fn mem_protect<C: Call>(enable: bool) -> Result<bool, Error> {
    match C::call32(PSCI_MEM_PROTECT, [enable as u32, 0, 0, 0, 0, 0, 0])[0] as i32 {
        0 => Ok(false),
        1 => Ok(true),
        error => Err(error.into()),
    }
}

/// Checks whether a memory range is protected by `MEM_PROTECT`.
pub fn mem_protect_check_range<C: Call>(base: u64, length: u64) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_MEM_PROTECT_CHECK_RANGE_64,
            [base, length, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
        )[0],
    )
}

/// Queries whether `SMCCC_VERSION` or a specific PSCI function is implemented, and what features
/// are supported.
pub fn psci_features<C: Call>(psci_function_id: u32) -> Result<u32, Error> {
    positive_or_error_32(C::call32(PSCI_FEATURES, [psci_function_id, 0, 0, 0, 0, 0, 0])[0])
}

/// Puts the current core into an implementation-defined low power state.
pub fn cpu_freeze<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(PSCI_CPU_FREEZE, [0; 7])[0])
}

/// Puts the current core into an implementation-defined low power state.
pub fn cpu_default_suspend<C: Call>(
    entry_point_address: u64,
    context_id: u64,
) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_CPU_DEFAULT_SUSPEND_64,
            [
                entry_point_address,
                context_id,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ],
        )[0],
    )
}

/// Retuns the true hardware state of a node in the power domain topology.
pub fn node_hw_state<C: Call>(target_cpu: u64, power_level: u32) -> Result<PowerState, Error> {
    (C::call64(
        PSCI_NODE_HW_STATE_64,
        [
            target_cpu,
            power_level.into(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        ],
    )[0] as i32)
        .try_into()
}

/// Suspends the system to RAM.
pub fn system_suspend<C: Call>(entry_point_address: u64, context_id: u64) -> Result<(), Error> {
    success_or_error_64(
        C::call64(
            PSCI_SYSTEM_SUSPEND_64,
            [
                entry_point_address,
                context_id,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ],
        )[0],
    )
}

/// Sets the mode used by `CPU_SUSPEND`.
pub fn set_suspend_mode<C: Call>(mode: SuspendMode) -> Result<(), Error> {
    success_or_error_32(C::call32(PSCI_SET_SUSPEND_MODE, [mode.into(), 0, 0, 0, 0, 0, 0])[0])
}

/// Returns the amount of time the platform has spend in the given power state since cold boot.
pub fn stat_residency<C: Call>(target_cpu: u64, power_state: u32) -> u64 {
    C::call64(
        PSCI_STAT_RESIDENCY_64,
        [
            target_cpu,
            power_state.into(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        ],
    )[0]
}

/// Returns the number of times the platform has used the given power state since cold boot.
pub fn stat_count<C: Call>(target_cpu: u64, power_state: u32) -> u64 {
    C::call64(
        PSCI_STAT_COUNT_64,
        [
            target_cpu,
            power_state.into(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        ],
    )[0]
}
