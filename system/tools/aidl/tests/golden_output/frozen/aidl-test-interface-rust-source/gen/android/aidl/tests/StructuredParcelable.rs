/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/StructuredParcelable.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/StructuredParcelable.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug, Clone, PartialEq)]
pub struct r#StructuredParcelable {
  pub r#shouldContainThreeFs: Vec<i32>,
  pub r#f: i32,
  pub r#shouldBeJerry: String,
  pub r#shouldBeByteBar: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum,
  pub r#shouldBeIntBar: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum,
  pub r#shouldBeLongBar: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum,
  pub r#shouldContainTwoByteFoos: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>,
  pub r#shouldContainTwoIntFoos: Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>,
  pub r#shouldContainTwoLongFoos: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>,
  pub r#stringDefaultsToFoo: String,
  pub r#byteDefaultsToFour: i8,
  pub r#intDefaultsToFive: i32,
  pub r#longDefaultsToNegativeSeven: i64,
  pub r#booleanDefaultsToTrue: bool,
  pub r#charDefaultsToC: u16,
  pub r#floatDefaultsToPi: f32,
  pub r#doubleWithDefault: f64,
  pub r#arrayDefaultsTo123: Vec<i32>,
  pub r#arrayDefaultsToEmpty: Vec<i32>,
  pub r#boolDefault: bool,
  pub r#byteDefault: i8,
  pub r#intDefault: i32,
  pub r#longDefault: i64,
  pub r#floatDefault: f32,
  pub r#doubleDefault: f64,
  pub r#checkDoubleFromFloat: f64,
  pub r#checkStringArray1: Vec<String>,
  pub r#checkStringArray2: Vec<String>,
  pub r#int32_min: i32,
  pub r#int32_max: i32,
  pub r#int64_max: i64,
  pub r#hexInt32_neg_1: i32,
  pub r#ibinder: Option<binder::SpIBinder>,
  pub r#empty: crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable_5_Empty,
  pub r#int8_1: Vec<u8>,
  pub r#int32_1: Vec<i32>,
  pub r#int64_1: Vec<i64>,
  pub r#hexInt32_pos_1: i32,
  pub r#hexInt64_pos_1: i32,
  pub r#const_exprs_1: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_2: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_3: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_4: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_5: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_6: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_7: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_8: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_9: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#const_exprs_10: crate::mangled::_7_android_4_aidl_5_tests_22_ConstantExpressionEnum,
  pub r#addString1: String,
  pub r#addString2: String,
  pub r#shouldSetBit0AndBit2: i32,
  pub r#u: Option<crate::mangled::_7_android_4_aidl_5_tests_5_Union>,
  pub r#shouldBeConstS1: Option<crate::mangled::_7_android_4_aidl_5_tests_5_Union>,
  pub r#defaultWithFoo: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum,
}
pub const r#BIT0: i32 = 1;
pub const r#BIT1: i32 = 2;
pub const r#BIT2: i32 = 4;
impl Default for r#StructuredParcelable {
  fn default() -> Self {
    Self {
      r#shouldContainThreeFs: Default::default(),
      r#f: 0,
      r#shouldBeJerry: Default::default(),
      r#shouldBeByteBar: Default::default(),
      r#shouldBeIntBar: Default::default(),
      r#shouldBeLongBar: Default::default(),
      r#shouldContainTwoByteFoos: Default::default(),
      r#shouldContainTwoIntFoos: Default::default(),
      r#shouldContainTwoLongFoos: Default::default(),
      r#stringDefaultsToFoo: "foo".into(),
      r#byteDefaultsToFour: 4,
      r#intDefaultsToFive: 5,
      r#longDefaultsToNegativeSeven: -7,
      r#booleanDefaultsToTrue: true,
      r#charDefaultsToC: 'C' as u16,
      r#floatDefaultsToPi: 3.140000f32,
      r#doubleWithDefault: -314000000000000000.000000f64,
      r#arrayDefaultsTo123: vec![1, 2, 3],
      r#arrayDefaultsToEmpty: vec![],
      r#boolDefault: false,
      r#byteDefault: 0,
      r#intDefault: 0,
      r#longDefault: 0,
      r#floatDefault: 0.000000f32,
      r#doubleDefault: 0.000000f64,
      r#checkDoubleFromFloat: 3.140000f64,
      r#checkStringArray1: vec!["a".into(), "b".into()],
      r#checkStringArray2: vec!["a".into(), "b".into()],
      r#int32_min: -2147483648,
      r#int32_max: 2147483647,
      r#int64_max: 9223372036854775807,
      r#hexInt32_neg_1: -1,
      r#ibinder: Default::default(),
      r#empty: Default::default(),
      r#int8_1: vec![1, 1, 1, 1, 1],
      r#int32_1: vec![1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      r#int64_1: vec![1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],
      r#hexInt32_pos_1: 1,
      r#hexInt64_pos_1: 1,
      r#const_exprs_1: Default::default(),
      r#const_exprs_2: Default::default(),
      r#const_exprs_3: Default::default(),
      r#const_exprs_4: Default::default(),
      r#const_exprs_5: Default::default(),
      r#const_exprs_6: Default::default(),
      r#const_exprs_7: Default::default(),
      r#const_exprs_8: Default::default(),
      r#const_exprs_9: Default::default(),
      r#const_exprs_10: Default::default(),
      r#addString1: "hello world!".into(),
      r#addString2: "The quick brown fox jumps over the lazy dog.".into(),
      r#shouldSetBit0AndBit2: 0,
      r#u: Default::default(),
      r#shouldBeConstS1: Default::default(),
      r#defaultWithFoo: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum::FOO,
    }
  }
}
impl binder::Parcelable for r#StructuredParcelable {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#shouldContainThreeFs)?;
      subparcel.write(&self.r#f)?;
      subparcel.write(&self.r#shouldBeJerry)?;
      subparcel.write(&self.r#shouldBeByteBar)?;
      subparcel.write(&self.r#shouldBeIntBar)?;
      subparcel.write(&self.r#shouldBeLongBar)?;
      subparcel.write(&self.r#shouldContainTwoByteFoos)?;
      subparcel.write(&self.r#shouldContainTwoIntFoos)?;
      subparcel.write(&self.r#shouldContainTwoLongFoos)?;
      subparcel.write(&self.r#stringDefaultsToFoo)?;
      subparcel.write(&self.r#byteDefaultsToFour)?;
      subparcel.write(&self.r#intDefaultsToFive)?;
      subparcel.write(&self.r#longDefaultsToNegativeSeven)?;
      subparcel.write(&self.r#booleanDefaultsToTrue)?;
      subparcel.write(&self.r#charDefaultsToC)?;
      subparcel.write(&self.r#floatDefaultsToPi)?;
      subparcel.write(&self.r#doubleWithDefault)?;
      subparcel.write(&self.r#arrayDefaultsTo123)?;
      subparcel.write(&self.r#arrayDefaultsToEmpty)?;
      subparcel.write(&self.r#boolDefault)?;
      subparcel.write(&self.r#byteDefault)?;
      subparcel.write(&self.r#intDefault)?;
      subparcel.write(&self.r#longDefault)?;
      subparcel.write(&self.r#floatDefault)?;
      subparcel.write(&self.r#doubleDefault)?;
      subparcel.write(&self.r#checkDoubleFromFloat)?;
      subparcel.write(&self.r#checkStringArray1)?;
      subparcel.write(&self.r#checkStringArray2)?;
      subparcel.write(&self.r#int32_min)?;
      subparcel.write(&self.r#int32_max)?;
      subparcel.write(&self.r#int64_max)?;
      subparcel.write(&self.r#hexInt32_neg_1)?;
      subparcel.write(&self.r#ibinder)?;
      subparcel.write(&self.r#empty)?;
      subparcel.write(&self.r#int8_1)?;
      subparcel.write(&self.r#int32_1)?;
      subparcel.write(&self.r#int64_1)?;
      subparcel.write(&self.r#hexInt32_pos_1)?;
      subparcel.write(&self.r#hexInt64_pos_1)?;
      subparcel.write(&self.r#const_exprs_1)?;
      subparcel.write(&self.r#const_exprs_2)?;
      subparcel.write(&self.r#const_exprs_3)?;
      subparcel.write(&self.r#const_exprs_4)?;
      subparcel.write(&self.r#const_exprs_5)?;
      subparcel.write(&self.r#const_exprs_6)?;
      subparcel.write(&self.r#const_exprs_7)?;
      subparcel.write(&self.r#const_exprs_8)?;
      subparcel.write(&self.r#const_exprs_9)?;
      subparcel.write(&self.r#const_exprs_10)?;
      subparcel.write(&self.r#addString1)?;
      subparcel.write(&self.r#addString2)?;
      subparcel.write(&self.r#shouldSetBit0AndBit2)?;
      subparcel.write(&self.r#u)?;
      subparcel.write(&self.r#shouldBeConstS1)?;
      subparcel.write(&self.r#defaultWithFoo)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#shouldContainThreeFs = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#f = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldBeJerry = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldBeByteBar = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldBeIntBar = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldBeLongBar = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldContainTwoByteFoos = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldContainTwoIntFoos = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldContainTwoLongFoos = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringDefaultsToFoo = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteDefaultsToFour = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intDefaultsToFive = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longDefaultsToNegativeSeven = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#booleanDefaultsToTrue = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#charDefaultsToC = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatDefaultsToPi = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleWithDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#arrayDefaultsTo123 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#arrayDefaultsToEmpty = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#boolDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleDefault = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#checkDoubleFromFloat = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#checkStringArray1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#checkStringArray2 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int32_min = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int32_max = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int64_max = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#hexInt32_neg_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#ibinder = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#empty = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int8_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int32_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#int64_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#hexInt32_pos_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#hexInt64_pos_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_2 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_3 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_4 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_5 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_6 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_7 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_8 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_9 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#const_exprs_10 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#addString1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#addString2 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldSetBit0AndBit2 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#u = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#shouldBeConstS1 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#defaultWithFoo = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#StructuredParcelable);
binder::impl_deserialize_for_parcelable!(r#StructuredParcelable);
impl binder::binder_impl::ParcelableMetadata for r#StructuredParcelable {
  fn get_descriptor() -> &'static str { "android.aidl.tests.StructuredParcelable" }
}
pub mod r#Empty {
  #[derive(Debug, Clone, PartialEq)]
  pub struct r#Empty {
  }
  impl Default for r#Empty {
    fn default() -> Self {
      Self {
      }
    }
  }
  impl binder::Parcelable for r#Empty {
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
  binder::impl_serialize_for_parcelable!(r#Empty);
  binder::impl_deserialize_for_parcelable!(r#Empty);
  impl binder::binder_impl::ParcelableMetadata for r#Empty {
    fn get_descriptor() -> &'static str { "android.aidl.tests.StructuredParcelable.Empty" }
  }
}
pub(crate) mod mangled {
 pub use super::r#StructuredParcelable as _7_android_4_aidl_5_tests_20_StructuredParcelable;
 pub use super::r#Empty::r#Empty as _7_android_4_aidl_5_tests_20_StructuredParcelable_5_Empty;
}
