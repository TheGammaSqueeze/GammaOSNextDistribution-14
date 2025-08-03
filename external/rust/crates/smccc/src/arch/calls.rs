// Copyright 2023 the authors.
// This project is dual-licensed under Apache 2.0 and MIT terms.
// See LICENSE-APACHE and LICENSE-MIT for details.

use super::{
    error::Error, SocIdType, Version, SMCCC_ARCH_FEATURES, SMCCC_ARCH_SOC_ID,
    SMCCC_ARCH_WORKAROUND_1, SMCCC_ARCH_WORKAROUND_2, SMCCC_ARCH_WORKAROUND_3, SMCCC_VERSION,
};
use crate::{
    error::{positive_or_error_32, success_or_error_32},
    Call,
};

/// Returns the implemented version of the SMC Calling Convention.
pub fn version<C: Call>() -> Result<Version, Error> {
    (C::call32(SMCCC_VERSION, [0; 7])[0] as i32).try_into()
}

/// Returns whether the given Arm Architecture Service function is implemented, and any feature
/// flags specific to the function.
pub fn features<C: Call>(arch_func_id: u32) -> Result<u32, Error> {
    positive_or_error_32(C::call32(SMCCC_ARCH_FEATURES, [arch_func_id, 0, 0, 0, 0, 0, 0])[0])
}

/// Returns the SiP defined SoC identification details.
pub fn soc_id<C: Call>(soc_id_type: SocIdType) -> Result<u32, Error> {
    positive_or_error_32(C::call32(SMCCC_ARCH_SOC_ID, [soc_id_type.into(), 0, 0, 0, 0, 0, 0])[0])
}

/// Executes a firmware workaround to mitigate CVE-2017-5715.
pub fn arch_workaround_1<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(SMCCC_ARCH_WORKAROUND_1, [0; 7])[0])
}

/// Enables or disables the mitigation for CVE-2018-3639.
pub fn arch_workaround_2<C: Call>(enable: bool) -> Result<(), Error> {
    success_or_error_32(C::call32(SMCCC_ARCH_WORKAROUND_2, [enable.into(), 0, 0, 0, 0, 0, 0])[0])
}

/// Executes a firmware workaround to mitigate CVE-2017-5715 and CVE-2022-23960.
pub fn arch_workaround_3<C: Call>() -> Result<(), Error> {
    success_or_error_32(C::call32(SMCCC_ARCH_WORKAROUND_3, [0; 7])[0])
}
