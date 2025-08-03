/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/ConstantExpressionEnum.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ConstantExpressionEnum.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
use binder::declare_binder_enum;
declare_binder_enum! {
  r#ConstantExpressionEnum : [i32; 10] {
    r#decInt32_1 = 1,
    r#decInt32_2 = 1,
    r#decInt64_1 = 1,
    r#decInt64_2 = 1,
    r#decInt64_3 = 1,
    r#decInt64_4 = 1,
    r#hexInt32_1 = 1,
    r#hexInt32_2 = 1,
    r#hexInt32_3 = 1,
    r#hexInt64_1 = 1,
  }
}
pub(crate) mod mangled {
 pub use super::r#ConstantExpressionEnum as _7_android_4_aidl_5_tests_22_ConstantExpressionEnum;
}
