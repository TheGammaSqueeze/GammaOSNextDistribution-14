/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/unions/EnumUnion.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/unions/EnumUnion.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug, Clone, PartialEq)]
pub enum r#EnumUnion {
  IntEnum(crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum),
  LongEnum(crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum),
  #[deprecated = "do not use this"]
  DeprecatedField(i32),
}
impl Default for r#EnumUnion {
  fn default() -> Self {
    Self::IntEnum(crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum::FOO)
  }
}
impl binder::Parcelable for r#EnumUnion {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    match self {
      Self::IntEnum(v) => {
        parcel.write(&0i32)?;
        parcel.write(v)
      }
      Self::LongEnum(v) => {
        parcel.write(&1i32)?;
        parcel.write(v)
      }
      Self::DeprecatedField(v) => {
        parcel.write(&2i32)?;
        parcel.write(v)
      }
    }
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    let tag: i32 = parcel.read()?;
    match tag {
      0 => {
        let value: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum = parcel.read()?;
        *self = Self::IntEnum(value);
        Ok(())
      }
      1 => {
        let value: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum = parcel.read()?;
        *self = Self::LongEnum(value);
        Ok(())
      }
      2 => {
        let value: i32 = parcel.read()?;
        *self = Self::DeprecatedField(value);
        Ok(())
      }
      _ => {
        Err(binder::StatusCode::BAD_VALUE)
      }
    }
  }
}
binder::impl_serialize_for_parcelable!(r#EnumUnion);
binder::impl_deserialize_for_parcelable!(r#EnumUnion);
impl binder::binder_impl::ParcelableMetadata for r#EnumUnion {
  fn get_descriptor() -> &'static str { "android.aidl.tests.unions.EnumUnion" }
}
pub mod r#Tag {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#Tag : [i32; 3] {
      r#intEnum = 0,
      r#longEnum = 1,
      #[deprecated = "do not use this"]
      r#deprecatedField = 2,
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#EnumUnion as _7_android_4_aidl_5_tests_6_unions_9_EnumUnion;
 pub use super::r#Tag::r#Tag as _7_android_4_aidl_5_tests_6_unions_9_EnumUnion_3_Tag;
}
