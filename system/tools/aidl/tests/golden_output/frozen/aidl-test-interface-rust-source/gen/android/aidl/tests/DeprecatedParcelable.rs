/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/DeprecatedParcelable.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/DeprecatedParcelable.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
#[deprecated = "test"]
pub struct r#DeprecatedParcelable {
}
impl Default for r#DeprecatedParcelable {
  fn default() -> Self {
    Self {
    }
  }
}
impl binder::Parcelable for r#DeprecatedParcelable {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#DeprecatedParcelable);
binder::impl_deserialize_for_parcelable!(r#DeprecatedParcelable);
impl binder::binder_impl::ParcelableMetadata for r#DeprecatedParcelable {
  fn get_descriptor() -> &'static str { "android.aidl.tests.DeprecatedParcelable" }
}
pub(crate) mod mangled {
 pub use super::r#DeprecatedParcelable as _7_android_4_aidl_5_tests_20_DeprecatedParcelable;
}
