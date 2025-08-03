/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/nested/DeeplyNested.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/DeeplyNested.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#DeeplyNested {
}
impl Default for r#DeeplyNested {
  fn default() -> Self {
    Self {
    }
  }
}
impl binder::Parcelable for r#DeeplyNested {
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
binder::impl_serialize_for_parcelable!(r#DeeplyNested);
binder::impl_deserialize_for_parcelable!(r#DeeplyNested);
impl binder::binder_impl::ParcelableMetadata for r#DeeplyNested {
  fn get_descriptor() -> &'static str { "android.aidl.tests.nested.DeeplyNested" }
}
pub mod r#A {
  #[derive(Debug)]
  pub struct r#A {
    pub r#e: crate::mangled::_7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B_1_C_1_D_1_E,
  }
  impl Default for r#A {
    fn default() -> Self {
      Self {
        r#e: crate::mangled::_7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B_1_C_1_D_1_E::OK,
      }
    }
  }
  impl binder::Parcelable for r#A {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_write(|subparcel| {
        subparcel.write(&self.r#e)?;
        Ok(())
      })
    }
    fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_read(|subparcel| {
        if subparcel.has_more_data() {
          self.r#e = subparcel.read()?;
        }
        Ok(())
      })
    }
  }
  binder::impl_serialize_for_parcelable!(r#A);
  binder::impl_deserialize_for_parcelable!(r#A);
  impl binder::binder_impl::ParcelableMetadata for r#A {
    fn get_descriptor() -> &'static str { "android.aidl.tests.nested.DeeplyNested.A" }
  }
}
pub mod r#B {
  #[derive(Debug)]
  pub struct r#B {
  }
  impl Default for r#B {
    fn default() -> Self {
      Self {
      }
    }
  }
  impl binder::Parcelable for r#B {
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
  binder::impl_serialize_for_parcelable!(r#B);
  binder::impl_deserialize_for_parcelable!(r#B);
  impl binder::binder_impl::ParcelableMetadata for r#B {
    fn get_descriptor() -> &'static str { "android.aidl.tests.nested.DeeplyNested.B" }
  }
  pub mod r#C {
    #[derive(Debug)]
    pub struct r#C {
    }
    impl Default for r#C {
      fn default() -> Self {
        Self {
        }
      }
    }
    impl binder::Parcelable for r#C {
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
    binder::impl_serialize_for_parcelable!(r#C);
    binder::impl_deserialize_for_parcelable!(r#C);
    impl binder::binder_impl::ParcelableMetadata for r#C {
      fn get_descriptor() -> &'static str { "android.aidl.tests.nested.DeeplyNested.B.C" }
    }
    pub mod r#D {
      #[derive(Debug)]
      pub struct r#D {
      }
      impl Default for r#D {
        fn default() -> Self {
          Self {
          }
        }
      }
      impl binder::Parcelable for r#D {
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
      binder::impl_serialize_for_parcelable!(r#D);
      binder::impl_deserialize_for_parcelable!(r#D);
      impl binder::binder_impl::ParcelableMetadata for r#D {
        fn get_descriptor() -> &'static str { "android.aidl.tests.nested.DeeplyNested.B.C.D" }
      }
      pub mod r#E {
        #![allow(non_upper_case_globals)]
        use binder::declare_binder_enum;
        declare_binder_enum! {
          r#E : [i8; 1] {
            r#OK = 0,
          }
        }
      }
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#DeeplyNested as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested;
 pub use super::r#A::r#A as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_A;
 pub use super::r#B::r#B as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B;
 pub use super::r#B::r#C::r#C as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B_1_C;
 pub use super::r#B::r#C::r#D::r#D as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B_1_C_1_D;
 pub use super::r#B::r#C::r#D::r#E::r#E as _7_android_4_aidl_5_tests_6_nested_12_DeeplyNested_1_B_1_C_1_D_1_E;
}
