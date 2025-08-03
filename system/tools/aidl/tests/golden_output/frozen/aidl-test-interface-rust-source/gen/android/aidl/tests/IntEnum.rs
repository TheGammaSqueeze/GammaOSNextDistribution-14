/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/IntEnum.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IntEnum.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
use binder::declare_binder_enum;
declare_binder_enum! {
  r#IntEnum : [i32; 4] {
    r#FOO = 1000,
    r#BAR = 2000,
    r#BAZ = 2001,
    #[deprecated = "do not use this"]
    r#QUX = 2002,
  }
}
pub(crate) mod mangled {
 pub use super::r#IntEnum as _7_android_4_aidl_5_tests_7_IntEnum;
}
