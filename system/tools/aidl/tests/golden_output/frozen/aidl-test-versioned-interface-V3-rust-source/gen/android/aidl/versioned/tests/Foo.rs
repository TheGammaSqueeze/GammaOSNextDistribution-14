/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust --structured --version 3 --hash 70d76c61eb0c82288e924862c10b910d1b7d8cf8 --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-rust-source/gen/android/aidl/versioned/tests/Foo.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-rust-source/gen -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/3 system/tools/aidl/aidl_api/aidl-test-versioned-interface/3/android/aidl/versioned/tests/Foo.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#Foo {
  pub r#intDefault42: i32,
}
impl Default for r#Foo {
  fn default() -> Self {
    Self {
      r#intDefault42: 42,
    }
  }
}
impl binder::Parcelable for r#Foo {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#intDefault42)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#intDefault42 = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#Foo);
binder::impl_deserialize_for_parcelable!(r#Foo);
impl binder::binder_impl::ParcelableMetadata for r#Foo {
  fn get_descriptor() -> &'static str { "android.aidl.versioned.tests.Foo" }
}
pub(crate) mod mangled {
 pub use super::r#Foo as _7_android_4_aidl_9_versioned_5_tests_3_Foo;
}
