/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/GenericStructuredParcelable.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/GenericStructuredParcelable.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub struct r#GenericStructuredParcelable<T,U,B,> {
  pub r#a: i32,
  pub r#b: i32,
  _phantom_B: std::marker::PhantomData<B>,
  _phantom_T: std::marker::PhantomData<T>,
  _phantom_U: std::marker::PhantomData<U>,
}
impl<T: Default,U: Default,B: Default,> Default for r#GenericStructuredParcelable<T,U,B,> {
  fn default() -> Self {
    Self {
      r#a: 0,
      r#b: 0,
      r#_phantom_B: Default::default(),
      r#_phantom_T: Default::default(),
      r#_phantom_U: Default::default(),
    }
  }
}
impl<T,U,B,> binder::Parcelable for r#GenericStructuredParcelable<T,U,B,> {
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
binder::impl_serialize_for_parcelable!(r#GenericStructuredParcelable<T,U,B,>);
binder::impl_deserialize_for_parcelable!(r#GenericStructuredParcelable<T,U,B,>);
impl<T,U,B,> binder::binder_impl::ParcelableMetadata for r#GenericStructuredParcelable<T,U,B,> {
  fn get_descriptor() -> &'static str { "android.aidl.tests.GenericStructuredParcelable" }
}
pub(crate) mod mangled {
 pub use super::r#GenericStructuredParcelable as _7_android_4_aidl_5_tests_27_GenericStructuredParcelable;
}
