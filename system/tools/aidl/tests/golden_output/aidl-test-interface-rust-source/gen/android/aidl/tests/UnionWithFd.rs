/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/UnionWithFd.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/UnionWithFd.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub enum r#UnionWithFd {
  Num(i32),
  Pfd(Option<binder::ParcelFileDescriptor>),
}
impl Default for r#UnionWithFd {
  fn default() -> Self {
    Self::Num(0)
  }
}
impl binder::Parcelable for r#UnionWithFd {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    match self {
      Self::Num(v) => {
        parcel.write(&0i32)?;
        parcel.write(v)
      }
      Self::Pfd(v) => {
        parcel.write(&1i32)?;
        let __field_ref = v.as_ref().ok_or(binder::StatusCode::UNEXPECTED_NULL)?;
        parcel.write(__field_ref)
      }
    }
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    let tag: i32 = parcel.read()?;
    match tag {
      0 => {
        let value: i32 = parcel.read()?;
        *self = Self::Num(value);
        Ok(())
      }
      1 => {
        let value: Option<binder::ParcelFileDescriptor> = Some(parcel.read()?);
        *self = Self::Pfd(value);
        Ok(())
      }
      _ => {
        Err(binder::StatusCode::BAD_VALUE)
      }
    }
  }
}
binder::impl_serialize_for_parcelable!(r#UnionWithFd);
binder::impl_deserialize_for_parcelable!(r#UnionWithFd);
impl binder::binder_impl::ParcelableMetadata for r#UnionWithFd {
  fn get_descriptor() -> &'static str { "android.aidl.tests.UnionWithFd" }
}
pub mod r#Tag {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#Tag : [i32; 2] {
      r#num = 0,
      r#pfd = 1,
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#UnionWithFd as _7_android_4_aidl_5_tests_11_UnionWithFd;
 pub use super::r#Tag::r#Tag as _7_android_4_aidl_5_tests_11_UnionWithFd_3_Tag;
}
