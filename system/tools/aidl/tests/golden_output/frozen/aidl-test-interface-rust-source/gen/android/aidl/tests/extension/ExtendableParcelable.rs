/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/extension/ExtendableParcelable.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/extension/ExtendableParcelable.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#ExtendableParcelable {
  pub r#a: i32,
  pub r#b: String,
  pub r#ext: binder::ParcelableHolder,
  pub r#c: i64,
  pub r#ext2: binder::ParcelableHolder,
}
impl Default for r#ExtendableParcelable {
  fn default() -> Self {
    Self {
      r#a: 0,
      r#b: Default::default(),
      r#ext: binder::ParcelableHolder::new(binder::binder_impl::Stability::Local),
      r#c: 0,
      r#ext2: binder::ParcelableHolder::new(binder::binder_impl::Stability::Local),
    }
  }
}
impl binder::Parcelable for r#ExtendableParcelable {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#a)?;
      subparcel.write(&self.r#b)?;
      subparcel.write(&self.r#ext)?;
      subparcel.write(&self.r#c)?;
      subparcel.write(&self.r#ext2)?;
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
      if subparcel.has_more_data() {
        self.r#ext = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#c = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#ext2 = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#ExtendableParcelable);
binder::impl_deserialize_for_parcelable!(r#ExtendableParcelable);
impl binder::binder_impl::ParcelableMetadata for r#ExtendableParcelable {
  fn get_descriptor() -> &'static str { "android.aidl.tests.extension.ExtendableParcelable" }
}
pub(crate) mod mangled {
 pub use super::r#ExtendableParcelable as _7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable;
}
