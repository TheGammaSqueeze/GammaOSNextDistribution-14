/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/unions/UnionInUnion.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/unions/UnionInUnion.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug, Clone, PartialEq)]
pub enum r#UnionInUnion {
  First(crate::mangled::_7_android_4_aidl_5_tests_6_unions_9_EnumUnion),
  Second(i32),
}
impl Default for r#UnionInUnion {
  fn default() -> Self {
    Self::First(Default::default())
  }
}
impl binder::Parcelable for r#UnionInUnion {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    match self {
      Self::First(v) => {
        parcel.write(&0i32)?;
        parcel.write(v)
      }
      Self::Second(v) => {
        parcel.write(&1i32)?;
        parcel.write(v)
      }
    }
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    let tag: i32 = parcel.read()?;
    match tag {
      0 => {
        let value: crate::mangled::_7_android_4_aidl_5_tests_6_unions_9_EnumUnion = parcel.read()?;
        *self = Self::First(value);
        Ok(())
      }
      1 => {
        let value: i32 = parcel.read()?;
        *self = Self::Second(value);
        Ok(())
      }
      _ => {
        Err(binder::StatusCode::BAD_VALUE)
      }
    }
  }
}
binder::impl_serialize_for_parcelable!(r#UnionInUnion);
binder::impl_deserialize_for_parcelable!(r#UnionInUnion);
impl binder::binder_impl::ParcelableMetadata for r#UnionInUnion {
  fn get_descriptor() -> &'static str { "android.aidl.tests.unions.UnionInUnion" }
}
pub mod r#Tag {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#Tag : [i32; 2] {
      r#first = 0,
      r#second = 1,
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#UnionInUnion as _7_android_4_aidl_5_tests_6_unions_12_UnionInUnion;
 pub use super::r#Tag::r#Tag as _7_android_4_aidl_5_tests_6_unions_12_UnionInUnion_3_Tag;
}
