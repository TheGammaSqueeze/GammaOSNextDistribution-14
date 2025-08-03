/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/RecursiveList.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/RecursiveList.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#RecursiveList {
  pub r#value: i32,
  pub r#next: Option<Box<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList>>,
}
impl Default for r#RecursiveList {
  fn default() -> Self {
    Self {
      r#value: 0,
      r#next: Default::default(),
    }
  }
}
impl binder::Parcelable for r#RecursiveList {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#value)?;
      subparcel.write(&self.r#next)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#value = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#next = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#RecursiveList);
binder::impl_deserialize_for_parcelable!(r#RecursiveList);
impl binder::binder_impl::ParcelableMetadata for r#RecursiveList {
  fn get_descriptor() -> &'static str { "android.aidl.tests.RecursiveList" }
}
pub(crate) mod mangled {
 pub use super::r#RecursiveList as _7_android_4_aidl_5_tests_13_RecursiveList;
}
