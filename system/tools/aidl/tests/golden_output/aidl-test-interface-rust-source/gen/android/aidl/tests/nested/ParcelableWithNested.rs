/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/nested/ParcelableWithNested.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/ParcelableWithNested.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#ParcelableWithNested {
  pub r#status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status,
}
impl Default for r#ParcelableWithNested {
  fn default() -> Self {
    Self {
      r#status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status::OK,
    }
  }
}
impl binder::Parcelable for r#ParcelableWithNested {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#status)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#status = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#ParcelableWithNested);
binder::impl_deserialize_for_parcelable!(r#ParcelableWithNested);
impl binder::binder_impl::ParcelableMetadata for r#ParcelableWithNested {
  fn get_descriptor() -> &'static str { "android.aidl.tests.nested.ParcelableWithNested" }
}
pub mod r#Status {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#Status : [i8; 2] {
      r#OK = 0,
      r#NOT_OK = 1,
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#ParcelableWithNested as _7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested;
 pub use super::r#Status::r#Status as _7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status;
}
