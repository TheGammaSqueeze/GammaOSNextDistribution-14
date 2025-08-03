/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/CircularParcelable.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/CircularParcelable.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#CircularParcelable {
  pub r#testService: Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>,
}
impl Default for r#CircularParcelable {
  fn default() -> Self {
    Self {
      r#testService: Default::default(),
    }
  }
}
impl binder::Parcelable for r#CircularParcelable {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#testService)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#testService = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#CircularParcelable);
binder::impl_deserialize_for_parcelable!(r#CircularParcelable);
impl binder::binder_impl::ParcelableMetadata for r#CircularParcelable {
  fn get_descriptor() -> &'static str { "android.aidl.tests.CircularParcelable" }
}
pub(crate) mod mangled {
 pub use super::r#CircularParcelable as _7_android_4_aidl_5_tests_18_CircularParcelable;
}
