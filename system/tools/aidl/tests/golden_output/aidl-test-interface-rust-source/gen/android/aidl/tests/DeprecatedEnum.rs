/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/DeprecatedEnum.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/DeprecatedEnum.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
use binder::declare_binder_enum;
declare_binder_enum! {
  #[deprecated = "test"]
  r#DeprecatedEnum : [i32; 3] {
    r#A = 0,
    r#B = 1,
    r#C = 2,
  }
}
pub(crate) mod mangled {
 pub use super::r#DeprecatedEnum as _7_android_4_aidl_5_tests_14_DeprecatedEnum;
}
