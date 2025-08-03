/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/extension/MyExtLike.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/extension/MyExtLike.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#MyExtLike {
  pub r#a: i32,
  pub r#b: String,
}
impl Default for r#MyExtLike {
  fn default() -> Self {
    Self {
      r#a: 0,
      r#b: Default::default(),
    }
  }
}
impl binder::Parcelable for r#MyExtLike {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#a)?;
      subparcel.write(&self.r#b)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#a = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#b = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#MyExtLike);
binder::impl_deserialize_for_parcelable!(r#MyExtLike);
impl binder::binder_impl::ParcelableMetadata for r#MyExtLike {
  fn get_descriptor() -> &'static str { "android.aidl.tests.extension.MyExtLike" }
}
pub(crate) mod mangled {
 pub use super::r#MyExtLike as _7_android_4_aidl_5_tests_9_extension_9_MyExtLike;
}
