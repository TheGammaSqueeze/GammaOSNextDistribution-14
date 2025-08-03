/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-rust-source/gen/android/aidl/fixedsizearray/FixedSizeArrayExample.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/fixedsizearray/FixedSizeArrayExample.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#[derive(Debug, PartialEq)]
pub struct r#FixedSizeArrayExample {
  pub r#int2x3: [[i32; 3]; 2],
  pub r#boolArray: [bool; 2],
  pub r#byteArray: [u8; 2],
  pub r#charArray: [u16; 2],
  pub r#intArray: [i32; 2],
  pub r#longArray: [i64; 2],
  pub r#floatArray: [f32; 2],
  pub r#doubleArray: [f64; 2],
  pub r#stringArray: [String; 2],
  pub r#byteEnumArray: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_ByteEnum; 2],
  pub r#intEnumArray: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_7_IntEnum; 2],
  pub r#longEnumArray: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_LongEnum; 2],
  pub r#parcelableArray: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 2],
  pub r#boolMatrix: [[bool; 2]; 2],
  pub r#byteMatrix: [[u8; 2]; 2],
  pub r#charMatrix: [[u16; 2]; 2],
  pub r#intMatrix: [[i32; 2]; 2],
  pub r#longMatrix: [[i64; 2]; 2],
  pub r#floatMatrix: [[f32; 2]; 2],
  pub r#doubleMatrix: [[f64; 2]; 2],
  pub r#stringMatrix: [[String; 2]; 2],
  pub r#byteEnumMatrix: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_ByteEnum; 2]; 2],
  pub r#intEnumMatrix: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_7_IntEnum; 2]; 2],
  pub r#longEnumMatrix: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_LongEnum; 2]; 2],
  pub r#parcelableMatrix: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 2]; 2],
  pub r#boolNullableArray: Option<[bool; 2]>,
  pub r#byteNullableArray: Option<[u8; 2]>,
  pub r#charNullableArray: Option<[u16; 2]>,
  pub r#intNullableArray: Option<[i32; 2]>,
  pub r#longNullableArray: Option<[i64; 2]>,
  pub r#floatNullableArray: Option<[f32; 2]>,
  pub r#doubleNullableArray: Option<[f64; 2]>,
  pub r#stringNullableArray: Option<[Option<String>; 2]>,
  pub r#byteEnumNullableArray: Option<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_ByteEnum; 2]>,
  pub r#intEnumNullableArray: Option<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_7_IntEnum; 2]>,
  pub r#longEnumNullableArray: Option<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_LongEnum; 2]>,
  pub r#binderNullableArray: Option<[Option<binder::SpIBinder>; 2]>,
  pub r#pfdNullableArray: Option<[Option<binder::ParcelFileDescriptor>; 2]>,
  pub r#parcelableNullableArray: Option<[Option<crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable>; 2]>,
  pub r#interfaceNullableArray: Option<[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_15_IEmptyInterface>>; 2]>,
  pub r#boolNullableMatrix: Option<[[bool; 2]; 2]>,
  pub r#byteNullableMatrix: Option<[[u8; 2]; 2]>,
  pub r#charNullableMatrix: Option<[[u16; 2]; 2]>,
  pub r#intNullableMatrix: Option<[[i32; 2]; 2]>,
  pub r#longNullableMatrix: Option<[[i64; 2]; 2]>,
  pub r#floatNullableMatrix: Option<[[f32; 2]; 2]>,
  pub r#doubleNullableMatrix: Option<[[f64; 2]; 2]>,
  pub r#stringNullableMatrix: Option<[[Option<String>; 2]; 2]>,
  pub r#byteEnumNullableMatrix: Option<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_ByteEnum; 2]; 2]>,
  pub r#intEnumNullableMatrix: Option<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_7_IntEnum; 2]; 2]>,
  pub r#longEnumNullableMatrix: Option<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_LongEnum; 2]; 2]>,
  pub r#binderNullableMatrix: Option<[[Option<binder::SpIBinder>; 2]; 2]>,
  pub r#pfdNullableMatrix: Option<[[Option<binder::ParcelFileDescriptor>; 2]; 2]>,
  pub r#parcelableNullableMatrix: Option<[[Option<crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable>; 2]; 2]>,
  pub r#interfaceNullableMatrix: Option<[[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_15_IEmptyInterface>>; 2]; 2]>,
}
impl Default for r#FixedSizeArrayExample {
  fn default() -> Self {
    Self {
      r#int2x3: [[1, 2, 3], [4, 5, 6]],
      r#boolArray: [Default::default(), Default::default()],
      r#byteArray: [Default::default(), Default::default()],
      r#charArray: [Default::default(), Default::default()],
      r#intArray: [Default::default(), Default::default()],
      r#longArray: [Default::default(), Default::default()],
      r#floatArray: [Default::default(), Default::default()],
      r#doubleArray: [Default::default(), Default::default()],
      r#stringArray: ["hello".into(), "world".into()],
      r#byteEnumArray: [Default::default(), Default::default()],
      r#intEnumArray: [Default::default(), Default::default()],
      r#longEnumArray: [Default::default(), Default::default()],
      r#parcelableArray: [Default::default(), Default::default()],
      r#boolMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#byteMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#charMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#intMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#longMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#floatMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#doubleMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#stringMatrix: [["hello".into(), "world".into()], ["Ciao".into(), "mondo".into()]],
      r#byteEnumMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#intEnumMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#longEnumMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#parcelableMatrix: [[Default::default(), Default::default()], [Default::default(), Default::default()]],
      r#boolNullableArray: Default::default(),
      r#byteNullableArray: Default::default(),
      r#charNullableArray: Default::default(),
      r#intNullableArray: Default::default(),
      r#longNullableArray: Default::default(),
      r#floatNullableArray: Default::default(),
      r#doubleNullableArray: Default::default(),
      r#stringNullableArray: Some([Some("hello".into()), Some("world".into())]),
      r#byteEnumNullableArray: Default::default(),
      r#intEnumNullableArray: Default::default(),
      r#longEnumNullableArray: Default::default(),
      r#binderNullableArray: Default::default(),
      r#pfdNullableArray: Default::default(),
      r#parcelableNullableArray: Default::default(),
      r#interfaceNullableArray: Default::default(),
      r#boolNullableMatrix: Default::default(),
      r#byteNullableMatrix: Default::default(),
      r#charNullableMatrix: Default::default(),
      r#intNullableMatrix: Default::default(),
      r#longNullableMatrix: Default::default(),
      r#floatNullableMatrix: Default::default(),
      r#doubleNullableMatrix: Default::default(),
      r#stringNullableMatrix: Some([[Some("hello".into()), Some("world".into())], [Some("Ciao".into()), Some("mondo".into())]]),
      r#byteEnumNullableMatrix: Default::default(),
      r#intEnumNullableMatrix: Default::default(),
      r#longEnumNullableMatrix: Default::default(),
      r#binderNullableMatrix: Default::default(),
      r#pfdNullableMatrix: Default::default(),
      r#parcelableNullableMatrix: Default::default(),
      r#interfaceNullableMatrix: Default::default(),
    }
  }
}
impl binder::Parcelable for r#FixedSizeArrayExample {
  fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_write(|subparcel| {
      subparcel.write(&self.r#int2x3)?;
      subparcel.write(&self.r#boolArray)?;
      subparcel.write(&self.r#byteArray)?;
      subparcel.write(&self.r#charArray)?;
      subparcel.write(&self.r#intArray)?;
      subparcel.write(&self.r#longArray)?;
      subparcel.write(&self.r#floatArray)?;
      subparcel.write(&self.r#doubleArray)?;
      subparcel.write(&self.r#stringArray)?;
      subparcel.write(&self.r#byteEnumArray)?;
      subparcel.write(&self.r#intEnumArray)?;
      subparcel.write(&self.r#longEnumArray)?;
      subparcel.write(&self.r#parcelableArray)?;
      subparcel.write(&self.r#boolMatrix)?;
      subparcel.write(&self.r#byteMatrix)?;
      subparcel.write(&self.r#charMatrix)?;
      subparcel.write(&self.r#intMatrix)?;
      subparcel.write(&self.r#longMatrix)?;
      subparcel.write(&self.r#floatMatrix)?;
      subparcel.write(&self.r#doubleMatrix)?;
      subparcel.write(&self.r#stringMatrix)?;
      subparcel.write(&self.r#byteEnumMatrix)?;
      subparcel.write(&self.r#intEnumMatrix)?;
      subparcel.write(&self.r#longEnumMatrix)?;
      subparcel.write(&self.r#parcelableMatrix)?;
      subparcel.write(&self.r#boolNullableArray)?;
      subparcel.write(&self.r#byteNullableArray)?;
      subparcel.write(&self.r#charNullableArray)?;
      subparcel.write(&self.r#intNullableArray)?;
      subparcel.write(&self.r#longNullableArray)?;
      subparcel.write(&self.r#floatNullableArray)?;
      subparcel.write(&self.r#doubleNullableArray)?;
      subparcel.write(&self.r#stringNullableArray)?;
      subparcel.write(&self.r#byteEnumNullableArray)?;
      subparcel.write(&self.r#intEnumNullableArray)?;
      subparcel.write(&self.r#longEnumNullableArray)?;
      subparcel.write(&self.r#binderNullableArray)?;
      subparcel.write(&self.r#pfdNullableArray)?;
      subparcel.write(&self.r#parcelableNullableArray)?;
      subparcel.write(&self.r#interfaceNullableArray)?;
      subparcel.write(&self.r#boolNullableMatrix)?;
      subparcel.write(&self.r#byteNullableMatrix)?;
      subparcel.write(&self.r#charNullableMatrix)?;
      subparcel.write(&self.r#intNullableMatrix)?;
      subparcel.write(&self.r#longNullableMatrix)?;
      subparcel.write(&self.r#floatNullableMatrix)?;
      subparcel.write(&self.r#doubleNullableMatrix)?;
      subparcel.write(&self.r#stringNullableMatrix)?;
      subparcel.write(&self.r#byteEnumNullableMatrix)?;
      subparcel.write(&self.r#intEnumNullableMatrix)?;
      subparcel.write(&self.r#longEnumNullableMatrix)?;
      subparcel.write(&self.r#binderNullableMatrix)?;
      subparcel.write(&self.r#pfdNullableMatrix)?;
      subparcel.write(&self.r#parcelableNullableMatrix)?;
      subparcel.write(&self.r#interfaceNullableMatrix)?;
      Ok(())
    })
  }
  fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
    parcel.sized_read(|subparcel| {
      if subparcel.has_more_data() {
        self.r#int2x3 = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#boolArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#charArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteEnumArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intEnumArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longEnumArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#boolMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#charMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteEnumMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intEnumMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longEnumMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#boolNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#charNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteEnumNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intEnumNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longEnumNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#binderNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#pfdNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#interfaceNullableArray = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#boolNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#charNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#floatNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#doubleNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#stringNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#byteEnumNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#intEnumNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#longEnumNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#binderNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#pfdNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#parcelableNullableMatrix = subparcel.read()?;
      }
      if subparcel.has_more_data() {
        self.r#interfaceNullableMatrix = subparcel.read()?;
      }
      Ok(())
    })
  }
}
binder::impl_serialize_for_parcelable!(r#FixedSizeArrayExample);
binder::impl_deserialize_for_parcelable!(r#FixedSizeArrayExample);
impl binder::binder_impl::ParcelableMetadata for r#FixedSizeArrayExample {
  fn get_descriptor() -> &'static str { "android.aidl.fixedsizearray.FixedSizeArrayExample" }
}
pub mod r#IRepeatFixedSizeArray {
  #![allow(non_upper_case_globals)]
  #![allow(non_snake_case)]
  #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
  use binder::declare_binder_interface;
  declare_binder_interface! {
    IRepeatFixedSizeArray["android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray"] {
      native: BnRepeatFixedSizeArray(on_transact),
      proxy: BpRepeatFixedSizeArray {
      },
      async: IRepeatFixedSizeArrayAsync,
    }
  }
  pub trait IRepeatFixedSizeArray: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray" }
    fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]>;
    fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]>;
    fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]>;
    fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]>;
    fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]>;
    fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]>;
    fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]>;
    fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]>;
    fn getDefaultImpl() -> IRepeatFixedSizeArrayDefaultRef where Self: Sized {
      DEFAULT_IMPL.lock().unwrap().clone()
    }
    fn setDefaultImpl(d: IRepeatFixedSizeArrayDefaultRef) -> IRepeatFixedSizeArrayDefaultRef where Self: Sized {
      std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
    }
  }
  pub trait IRepeatFixedSizeArrayAsync<P>: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray" }
    fn r#RepeatBytes<'a>(&'a self, _arg_input: &'a [u8; 3], _arg_repeated: &'a mut [u8; 3]) -> binder::BoxFuture<'a, binder::Result<[u8; 3]>>;
    fn r#RepeatInts<'a>(&'a self, _arg_input: &'a [i32; 3], _arg_repeated: &'a mut [i32; 3]) -> binder::BoxFuture<'a, binder::Result<[i32; 3]>>;
    fn r#RepeatBinders<'a>(&'a self, _arg_input: &'a [binder::SpIBinder; 3], _arg_repeated: &'a mut [Option<binder::SpIBinder>; 3]) -> binder::BoxFuture<'a, binder::Result<[binder::SpIBinder; 3]>>;
    fn r#RepeatParcelables<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &'a mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::BoxFuture<'a, binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]>>;
    fn r#Repeat2dBytes<'a>(&'a self, _arg_input: &'a [[u8; 3]; 2], _arg_repeated: &'a mut [[u8; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[u8; 3]; 2]>>;
    fn r#Repeat2dInts<'a>(&'a self, _arg_input: &'a [[i32; 3]; 2], _arg_repeated: &'a mut [[i32; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[i32; 3]; 2]>>;
    fn r#Repeat2dBinders<'a>(&'a self, _arg_input: &'a [[binder::SpIBinder; 3]; 2], _arg_repeated: &'a mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[binder::SpIBinder; 3]; 2]>>;
    fn r#Repeat2dParcelables<'a>(&'a self, _arg_input: &'a [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &'a mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]>>;
  }
  #[::async_trait::async_trait]
  pub trait IRepeatFixedSizeArrayAsyncServer: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray" }
    async fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]>;
    async fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]>;
    async fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]>;
    async fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]>;
    async fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]>;
    async fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]>;
    async fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]>;
    async fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]>;
  }
  impl BnRepeatFixedSizeArray {
    /// Create a new async binder service.
    pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn IRepeatFixedSizeArray>
    where
      T: IRepeatFixedSizeArrayAsyncServer + binder::Interface + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      struct Wrapper<T, R> {
        _inner: T,
        _rt: R,
      }
      impl<T, R> binder::Interface for Wrapper<T, R> where T: binder::Interface, R: Send + Sync + 'static {
        fn as_binder(&self) -> binder::SpIBinder { self._inner.as_binder() }
        fn dump(&self, _writer: &mut dyn std::io::Write, _args: &[&std::ffi::CStr]) -> std::result::Result<(), binder::StatusCode> { self._inner.dump(_writer, _args) }
      }
      impl<T, R> IRepeatFixedSizeArray for Wrapper<T, R>
      where
        T: IRepeatFixedSizeArrayAsyncServer + Send + Sync + 'static,
        R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
      {
        fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]> {
          self._rt.block_on(self._inner.r#RepeatBytes(_arg_input, _arg_repeated))
        }
        fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]> {
          self._rt.block_on(self._inner.r#RepeatInts(_arg_input, _arg_repeated))
        }
        fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]> {
          self._rt.block_on(self._inner.r#RepeatBinders(_arg_input, _arg_repeated))
        }
        fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]> {
          self._rt.block_on(self._inner.r#RepeatParcelables(_arg_input, _arg_repeated))
        }
        fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]> {
          self._rt.block_on(self._inner.r#Repeat2dBytes(_arg_input, _arg_repeated))
        }
        fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]> {
          self._rt.block_on(self._inner.r#Repeat2dInts(_arg_input, _arg_repeated))
        }
        fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]> {
          self._rt.block_on(self._inner.r#Repeat2dBinders(_arg_input, _arg_repeated))
        }
        fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]> {
          self._rt.block_on(self._inner.r#Repeat2dParcelables(_arg_input, _arg_repeated))
        }
      }
      let wrapped = Wrapper { _inner: inner, _rt: rt };
      Self::new_binder(wrapped, features)
    }
  }
  pub trait IRepeatFixedSizeArrayDefault: Send + Sync {
    fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
  }
  pub mod transactions {
    pub const r#RepeatBytes: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
    pub const r#RepeatInts: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 1;
    pub const r#RepeatBinders: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 2;
    pub const r#RepeatParcelables: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 3;
    pub const r#Repeat2dBytes: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 4;
    pub const r#Repeat2dInts: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 5;
    pub const r#Repeat2dBinders: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 6;
    pub const r#Repeat2dParcelables: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 7;
  }
  pub type IRepeatFixedSizeArrayDefaultRef = Option<std::sync::Arc<dyn IRepeatFixedSizeArrayDefault>>;
  static DEFAULT_IMPL: std::sync::Mutex<IRepeatFixedSizeArrayDefaultRef> = std::sync::Mutex::new(None);
  impl BpRepeatFixedSizeArray {
    fn build_parcel_RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[u8; 3]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#RepeatBytes(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [u8; 3] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[i32; 3]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#RepeatInts(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [i32; 3] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[binder::SpIBinder; 3]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#RepeatBinders(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [binder::SpIBinder; 3] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#RepeatParcelables(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[[u8; 3]; 2]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#Repeat2dBytes(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [[u8; 3]; 2] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[[i32; 3]; 2]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#Repeat2dInts(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [[i32; 3]; 2] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[[binder::SpIBinder; 3]; 2]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#Repeat2dBinders(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [[binder::SpIBinder; 3]; 2] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
    fn build_parcel_Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IRepeatFixedSizeArray>::getDefaultImpl() {
          return _aidl_default_impl.r#Repeat2dParcelables(_arg_input, _arg_repeated);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2] = _aidl_reply.read()?;
      _aidl_reply.read_onto(_arg_repeated)?;
      Ok(_aidl_return)
    }
  }
  impl IRepeatFixedSizeArray for BpRepeatFixedSizeArray {
    fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]> {
      let _aidl_data = self.build_parcel_RepeatBytes(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatBytes, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_RepeatBytes(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]> {
      let _aidl_data = self.build_parcel_RepeatInts(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatInts, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_RepeatInts(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]> {
      let _aidl_data = self.build_parcel_RepeatBinders(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatBinders, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_RepeatBinders(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]> {
      let _aidl_data = self.build_parcel_RepeatParcelables(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatParcelables, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_RepeatParcelables(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]> {
      let _aidl_data = self.build_parcel_Repeat2dBytes(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#Repeat2dBytes, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_Repeat2dBytes(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]> {
      let _aidl_data = self.build_parcel_Repeat2dInts(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#Repeat2dInts, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_Repeat2dInts(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]> {
      let _aidl_data = self.build_parcel_Repeat2dBinders(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#Repeat2dBinders, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_Repeat2dBinders(_arg_input, _arg_repeated, _aidl_reply)
    }
    fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]> {
      let _aidl_data = self.build_parcel_Repeat2dParcelables(_arg_input, _arg_repeated)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#Repeat2dParcelables, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_Repeat2dParcelables(_arg_input, _arg_repeated, _aidl_reply)
    }
  }
  impl<P: binder::BinderAsyncPool> IRepeatFixedSizeArrayAsync<P> for BpRepeatFixedSizeArray {
    fn r#RepeatBytes<'a>(&'a self, _arg_input: &'a [u8; 3], _arg_repeated: &'a mut [u8; 3]) -> binder::BoxFuture<'a, binder::Result<[u8; 3]>> {
      let _aidl_data = match self.build_parcel_RepeatBytes(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#RepeatBytes, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_RepeatBytes(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#RepeatInts<'a>(&'a self, _arg_input: &'a [i32; 3], _arg_repeated: &'a mut [i32; 3]) -> binder::BoxFuture<'a, binder::Result<[i32; 3]>> {
      let _aidl_data = match self.build_parcel_RepeatInts(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#RepeatInts, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_RepeatInts(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#RepeatBinders<'a>(&'a self, _arg_input: &'a [binder::SpIBinder; 3], _arg_repeated: &'a mut [Option<binder::SpIBinder>; 3]) -> binder::BoxFuture<'a, binder::Result<[binder::SpIBinder; 3]>> {
      let _aidl_data = match self.build_parcel_RepeatBinders(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#RepeatBinders, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_RepeatBinders(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#RepeatParcelables<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &'a mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::BoxFuture<'a, binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]>> {
      let _aidl_data = match self.build_parcel_RepeatParcelables(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#RepeatParcelables, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_RepeatParcelables(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#Repeat2dBytes<'a>(&'a self, _arg_input: &'a [[u8; 3]; 2], _arg_repeated: &'a mut [[u8; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[u8; 3]; 2]>> {
      let _aidl_data = match self.build_parcel_Repeat2dBytes(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#Repeat2dBytes, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_Repeat2dBytes(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#Repeat2dInts<'a>(&'a self, _arg_input: &'a [[i32; 3]; 2], _arg_repeated: &'a mut [[i32; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[i32; 3]; 2]>> {
      let _aidl_data = match self.build_parcel_Repeat2dInts(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#Repeat2dInts, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_Repeat2dInts(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#Repeat2dBinders<'a>(&'a self, _arg_input: &'a [[binder::SpIBinder; 3]; 2], _arg_repeated: &'a mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[binder::SpIBinder; 3]; 2]>> {
      let _aidl_data = match self.build_parcel_Repeat2dBinders(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#Repeat2dBinders, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_Repeat2dBinders(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
    fn r#Repeat2dParcelables<'a>(&'a self, _arg_input: &'a [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &'a mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::BoxFuture<'a, binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]>> {
      let _aidl_data = match self.build_parcel_Repeat2dParcelables(_arg_input, _arg_repeated) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#Repeat2dParcelables, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_Repeat2dParcelables(_arg_input, _arg_repeated, _aidl_reply)
        }
      )
    }
  }
  impl IRepeatFixedSizeArray for binder::binder_impl::Binder<BnRepeatFixedSizeArray> {
    fn r#RepeatBytes(&self, _arg_input: &[u8; 3], _arg_repeated: &mut [u8; 3]) -> binder::Result<[u8; 3]> { self.0.r#RepeatBytes(_arg_input, _arg_repeated) }
    fn r#RepeatInts(&self, _arg_input: &[i32; 3], _arg_repeated: &mut [i32; 3]) -> binder::Result<[i32; 3]> { self.0.r#RepeatInts(_arg_input, _arg_repeated) }
    fn r#RepeatBinders(&self, _arg_input: &[binder::SpIBinder; 3], _arg_repeated: &mut [Option<binder::SpIBinder>; 3]) -> binder::Result<[binder::SpIBinder; 3]> { self.0.r#RepeatBinders(_arg_input, _arg_repeated) }
    fn r#RepeatParcelables(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3], _arg_repeated: &mut [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]) -> binder::Result<[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]> { self.0.r#RepeatParcelables(_arg_input, _arg_repeated) }
    fn r#Repeat2dBytes(&self, _arg_input: &[[u8; 3]; 2], _arg_repeated: &mut [[u8; 3]; 2]) -> binder::Result<[[u8; 3]; 2]> { self.0.r#Repeat2dBytes(_arg_input, _arg_repeated) }
    fn r#Repeat2dInts(&self, _arg_input: &[[i32; 3]; 2], _arg_repeated: &mut [[i32; 3]; 2]) -> binder::Result<[[i32; 3]; 2]> { self.0.r#Repeat2dInts(_arg_input, _arg_repeated) }
    fn r#Repeat2dBinders(&self, _arg_input: &[[binder::SpIBinder; 3]; 2], _arg_repeated: &mut [[Option<binder::SpIBinder>; 3]; 2]) -> binder::Result<[[binder::SpIBinder; 3]; 2]> { self.0.r#Repeat2dBinders(_arg_input, _arg_repeated) }
    fn r#Repeat2dParcelables(&self, _arg_input: &[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2], _arg_repeated: &mut [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]) -> binder::Result<[[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2]> { self.0.r#Repeat2dParcelables(_arg_input, _arg_repeated) }
  }
  fn on_transact(_aidl_service: &dyn IRepeatFixedSizeArray, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
    match _aidl_code {
      transactions::r#RepeatBytes => {
        let _arg_input: [u8; 3] = _aidl_data.read()?;
        let mut _arg_repeated: [u8; 3] = Default::default();
        let _aidl_return = _aidl_service.r#RepeatBytes(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#RepeatInts => {
        let _arg_input: [i32; 3] = _aidl_data.read()?;
        let mut _arg_repeated: [i32; 3] = Default::default();
        let _aidl_return = _aidl_service.r#RepeatInts(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#RepeatBinders => {
        let _arg_input: [binder::SpIBinder; 3] = _aidl_data.read()?;
        let mut _arg_repeated: [Option<binder::SpIBinder>; 3] = Default::default();
        let _aidl_return = _aidl_service.r#RepeatBinders(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#RepeatParcelables => {
        let _arg_input: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3] = _aidl_data.read()?;
        let mut _arg_repeated: [crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3] = Default::default();
        let _aidl_return = _aidl_service.r#RepeatParcelables(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#Repeat2dBytes => {
        let _arg_input: [[u8; 3]; 2] = _aidl_data.read()?;
        let mut _arg_repeated: [[u8; 3]; 2] = Default::default();
        let _aidl_return = _aidl_service.r#Repeat2dBytes(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#Repeat2dInts => {
        let _arg_input: [[i32; 3]; 2] = _aidl_data.read()?;
        let mut _arg_repeated: [[i32; 3]; 2] = Default::default();
        let _aidl_return = _aidl_service.r#Repeat2dInts(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#Repeat2dBinders => {
        let _arg_input: [[binder::SpIBinder; 3]; 2] = _aidl_data.read()?;
        let mut _arg_repeated: [[Option<binder::SpIBinder>; 3]; 2] = Default::default();
        let _aidl_return = _aidl_service.r#Repeat2dBinders(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#Repeat2dParcelables => {
        let _arg_input: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2] = _aidl_data.read()?;
        let mut _arg_repeated: [[crate::mangled::_7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable; 3]; 2] = Default::default();
        let _aidl_return = _aidl_service.r#Repeat2dParcelables(&_arg_input, &mut _arg_repeated);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
            _aidl_reply.write(&_arg_repeated)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
    }
  }
}
pub mod r#ByteEnum {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#ByteEnum : [i8; 1] {
      r#A = 0,
    }
  }
}
pub mod r#IntEnum {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#IntEnum : [i32; 1] {
      r#A = 0,
    }
  }
}
pub mod r#LongEnum {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#LongEnum : [i64; 1] {
      r#A = 0,
    }
  }
}
pub mod r#IntParcelable {
  #[derive(Debug, Clone, Copy, PartialEq)]
  pub struct r#IntParcelable {
    pub r#value: i32,
  }
  impl Default for r#IntParcelable {
    fn default() -> Self {
      Self {
        r#value: 0,
      }
    }
  }
  impl binder::Parcelable for r#IntParcelable {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_write(|subparcel| {
        subparcel.write(&self.r#value)?;
        Ok(())
      })
    }
    fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_read(|subparcel| {
        if subparcel.has_more_data() {
          self.r#value = subparcel.read()?;
        }
        Ok(())
      })
    }
  }
  binder::impl_serialize_for_parcelable!(r#IntParcelable);
  binder::impl_deserialize_for_parcelable!(r#IntParcelable);
  impl binder::binder_impl::ParcelableMetadata for r#IntParcelable {
    fn get_descriptor() -> &'static str { "android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable" }
  }
}
pub mod r#IEmptyInterface {
  #![allow(non_upper_case_globals)]
  #![allow(non_snake_case)]
  #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
  use binder::declare_binder_interface;
  declare_binder_interface! {
    IEmptyInterface["android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface"] {
      native: BnEmptyInterface(on_transact),
      proxy: BpEmptyInterface {
      },
      async: IEmptyInterfaceAsync,
    }
  }
  pub trait IEmptyInterface: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface" }
    fn getDefaultImpl() -> IEmptyInterfaceDefaultRef where Self: Sized {
      DEFAULT_IMPL.lock().unwrap().clone()
    }
    fn setDefaultImpl(d: IEmptyInterfaceDefaultRef) -> IEmptyInterfaceDefaultRef where Self: Sized {
      std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
    }
  }
  pub trait IEmptyInterfaceAsync<P>: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface" }
  }
  #[::async_trait::async_trait]
  pub trait IEmptyInterfaceAsyncServer: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.fixedsizearray.FixedSizeArrayExample.IEmptyInterface" }
  }
  impl BnEmptyInterface {
    /// Create a new async binder service.
    pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn IEmptyInterface>
    where
      T: IEmptyInterfaceAsyncServer + binder::Interface + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      struct Wrapper<T, R> {
        _inner: T,
        _rt: R,
      }
      impl<T, R> binder::Interface for Wrapper<T, R> where T: binder::Interface, R: Send + Sync + 'static {
        fn as_binder(&self) -> binder::SpIBinder { self._inner.as_binder() }
        fn dump(&self, _writer: &mut dyn std::io::Write, _args: &[&std::ffi::CStr]) -> std::result::Result<(), binder::StatusCode> { self._inner.dump(_writer, _args) }
      }
      impl<T, R> IEmptyInterface for Wrapper<T, R>
      where
        T: IEmptyInterfaceAsyncServer + Send + Sync + 'static,
        R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
      {
      }
      let wrapped = Wrapper { _inner: inner, _rt: rt };
      Self::new_binder(wrapped, features)
    }
  }
  pub trait IEmptyInterfaceDefault: Send + Sync {
  }
  pub mod transactions {
  }
  pub type IEmptyInterfaceDefaultRef = Option<std::sync::Arc<dyn IEmptyInterfaceDefault>>;
  static DEFAULT_IMPL: std::sync::Mutex<IEmptyInterfaceDefaultRef> = std::sync::Mutex::new(None);
  impl BpEmptyInterface {
  }
  impl IEmptyInterface for BpEmptyInterface {
  }
  impl<P: binder::BinderAsyncPool> IEmptyInterfaceAsync<P> for BpEmptyInterface {
  }
  impl IEmptyInterface for binder::binder_impl::Binder<BnEmptyInterface> {
  }
  fn on_transact(_aidl_service: &dyn IEmptyInterface, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
    match _aidl_code {
      _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#FixedSizeArrayExample as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample;
 pub use super::r#IRepeatFixedSizeArray::r#IRepeatFixedSizeArray as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_21_IRepeatFixedSizeArray;
 pub use super::r#ByteEnum::r#ByteEnum as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_ByteEnum;
 pub use super::r#IntEnum::r#IntEnum as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_7_IntEnum;
 pub use super::r#LongEnum::r#LongEnum as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_8_LongEnum;
 pub use super::r#IntParcelable::r#IntParcelable as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_13_IntParcelable;
 pub use super::r#IEmptyInterface::r#IEmptyInterface as _7_android_4_aidl_14_fixedsizearray_21_FixedSizeArrayExample_15_IEmptyInterface;
}
