/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/ParcelableForToString.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ParcelableForToString.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug)]
pub struct r#ParcelableForToString {
  pub r#intValue: i32,
  pub r#intArray: Vec<i32>,
  pub r#longValue: i64,
  pub r#longArray: Vec<i64>,
  pub r#doubleValue: f64,
  pub r#doubleArray: Vec<f64>,
  pub r#floatValue: f32,
  pub r#floatArray: Vec<f32>,
  pub r#byteValue: i8,
  pub r#byteArray: Vec<u8>,
  pub r#booleanValue: bool,
  pub r#booleanArray: Vec<bool>,
  pub r#stringValue: String,
  pub r#stringArray: Vec<String>,
  pub r#stringList: Vec<String>,
  pub r#parcelableValue: crate::mangled::_7_android_4_aidl_5_tests_26_OtherParcelableForToString,
  pub r#parcelableArray: Vec<crate::mangled::_7_android_4_aidl_5_tests_26_OtherParcelableForToString>,
  pub r#enumValue: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum,
  pub r#enumArray: Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>,
  pub r#nullArray: Vec<String>,
  pub r#nullList: Vec<String>,
  pub r#parcelableGeneric: crate::mangled::_7_android_4_aidl_5_tests_27_GenericStructuredParcelable<i32,crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable,crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum,>,
  pub r#unionValue: crate::mangled::_7_android_4_aidl_5_tests_5_Union,
}
impl Default for r#ParcelableForToString {
  fn default() -> Self {
    Self {
      r#intValue: 0,
      r#intArray: Default::default(),
      r#longValue: 0,
      r#longArray: Default::default(),
      r#doubleValue: 0.000000f64,
      r#doubleArray: Default::default(),
      r#floatValue: 0.000000f32,
      r#floatArray: Default::default(),
      r#byteValue: 0,
      r#byteArray: Default::default(),
      r#booleanValue: false,
      r#booleanArray: Default::default(),
      r#stringValue: Default::default(),
      r#stringArray: Default::default(),
      r#stringList: Default::default(),
      r#parcelableValue: Default::default(),
      r#parcelableArray: Default::default(),
      r#enumValue: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum::FOO,
      r#enumArray: Default::default(),
      r#nullArray: Default::default(),
      r#nullList: Default::default(),
      r#parcelableGeneric: Default::default(),
      r#unionValue: Default::default(),
    }
  }
}
impl binder::Parcelable for r#ParcelableForToString {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#intValue)?;
      subparcel.write(&self.r#intArray)?;
      subparcel.write(&self.r#longValue)?;
      subparcel.write(&self.r#longArray)?;
      subparcel.write(&self.r#doubleValue)?;
      subparcel.write(&self.r#doubleArray)?;
      subparcel.write(&self.r#floatValue)?;
      subparcel.write(&self.r#floatArray)?;
      subparcel.write(&self.r#byteValue)?;
      subparcel.write(&self.r#byteArray)?;
      subparcel.write(&self.r#booleanValue)?;
      subparcel.write(&self.r#booleanArray)?;
      subparcel.write(&self.r#stringValue)?;
      subparcel.write(&self.r#stringArray)?;
      subparcel.write(&self.r#stringList)?;
      subparcel.write(&self.r#parcelableValue)?;
      subparcel.write(&self.r#parcelableArray)?;
      subparcel.write(&self.r#enumValue)?;
      subparcel.write(&self.r#enumArray)?;
      subparcel.write(&self.r#nullArray)?;
      subparcel.write(&self.r#nullList)?;
      subparcel.write(&self.r#parcelableGeneric)?;
      subparcel.write(&self.r#unionValue)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#intValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#booleanValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#booleanArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringList = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#enumValue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#enumArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#nullArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#nullList = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableGeneric = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#unionValue = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#ParcelableForToString);
binder::impl_deserialize_for_parcelable!(r#ParcelableForToString);
impl binder::binder_impl::ParcelableMetadata for r#ParcelableForToString {
  fn get_descriptor() -> &'static str { "android.aidl.tests.ParcelableForToString" }
}
pub(crate) mod mangled {
 pub use super::r#ParcelableForToString as _7_android_4_aidl_5_tests_21_ParcelableForToString;
}
