/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/ITestService.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
#![allow(non_snake_case)]
#[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
use binder::declare_binder_interface;
declare_binder_interface! {
  ITestService["android.aidl.tests.ITestService"] {
    native: BnTestService(on_transact),
    proxy: BpTestService {
    },
    async: ITestServiceAsync,
  }
}
pub trait ITestService: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService" }
  fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32>;
  #[deprecated = "to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"]
  fn r#Deprecated(&self) -> binder::Result<()>;
  fn r#TestOneway(&self) -> binder::Result<()>;
  fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool>;
  fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8>;
  fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16>;
  fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32>;
  fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64>;
  fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32>;
  fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64>;
  fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String>;
  fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>;
  fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>;
  fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>;
  fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>>;
  fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>>;
  fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>>;
  fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>>;
  fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>>;
  fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>>;
  fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>>;
  fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>;
  fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>;
  fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>;
  fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>;
  fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool>;
  fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool>;
  fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>;
  fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool>;
  fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>;
  fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool>;
  fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>;
  fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool>;
  fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor>;
  fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>>;
  fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()>;
  fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>>;
  fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>>;
  fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>>;
  fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>>;
  fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>>;
  fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>>;
  fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>;
  fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>;
  fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>;
  fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()>;
  fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()>;
  fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()>;
  fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()>;
  fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String>;
  fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>>;
  fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>>;
  fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>>;
  fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>;
  fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()>;
  fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()>;
  fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList>;
  fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>>;
  fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>>;
  fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable>;
  fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>>;
  fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>>;
  fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>>;
  fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>>;
  fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>>;
  fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType>;
  fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>>;
  fn getDefaultImpl() -> ITestServiceDefaultRef where Self: Sized {
    DEFAULT_IMPL.lock().unwrap().clone()
  }
  fn setDefaultImpl(d: ITestServiceDefaultRef) -> ITestServiceDefaultRef where Self: Sized {
    std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
  }
}
pub trait ITestServiceAsync<P>: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService" }
  fn r#UnimplementedMethod<'a>(&'a self, _arg_arg: i32) -> binder::BoxFuture<'a, binder::Result<i32>>;
  #[deprecated = "to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"]
  fn r#Deprecated<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#TestOneway(&self) -> std::future::Ready<binder::Result<()>>;
  fn r#RepeatBoolean<'a>(&'a self, _arg_token: bool) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#RepeatByte<'a>(&'a self, _arg_token: i8) -> binder::BoxFuture<'a, binder::Result<i8>>;
  fn r#RepeatChar<'a>(&'a self, _arg_token: u16) -> binder::BoxFuture<'a, binder::Result<u16>>;
  fn r#RepeatInt<'a>(&'a self, _arg_token: i32) -> binder::BoxFuture<'a, binder::Result<i32>>;
  fn r#RepeatLong<'a>(&'a self, _arg_token: i64) -> binder::BoxFuture<'a, binder::Result<i64>>;
  fn r#RepeatFloat<'a>(&'a self, _arg_token: f32) -> binder::BoxFuture<'a, binder::Result<f32>>;
  fn r#RepeatDouble<'a>(&'a self, _arg_token: f64) -> binder::BoxFuture<'a, binder::Result<f64>>;
  fn r#RepeatString<'a>(&'a self, _arg_token: &'a str) -> binder::BoxFuture<'a, binder::Result<String>>;
  fn r#RepeatByteEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>;
  fn r#RepeatIntEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>;
  fn r#RepeatLongEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>;
  fn r#ReverseBoolean<'a>(&'a self, _arg_input: &'a [bool], _arg_repeated: &'a mut Vec<bool>) -> binder::BoxFuture<'a, binder::Result<Vec<bool>>>;
  fn r#ReverseByte<'a>(&'a self, _arg_input: &'a [u8], _arg_repeated: &'a mut Vec<u8>) -> binder::BoxFuture<'a, binder::Result<Vec<u8>>>;
  fn r#ReverseChar<'a>(&'a self, _arg_input: &'a [u16], _arg_repeated: &'a mut Vec<u16>) -> binder::BoxFuture<'a, binder::Result<Vec<u16>>>;
  fn r#ReverseInt<'a>(&'a self, _arg_input: &'a [i32], _arg_repeated: &'a mut Vec<i32>) -> binder::BoxFuture<'a, binder::Result<Vec<i32>>>;
  fn r#ReverseLong<'a>(&'a self, _arg_input: &'a [i64], _arg_repeated: &'a mut Vec<i64>) -> binder::BoxFuture<'a, binder::Result<Vec<i64>>>;
  fn r#ReverseFloat<'a>(&'a self, _arg_input: &'a [f32], _arg_repeated: &'a mut Vec<f32>) -> binder::BoxFuture<'a, binder::Result<Vec<f32>>>;
  fn r#ReverseDouble<'a>(&'a self, _arg_input: &'a [f64], _arg_repeated: &'a mut Vec<f64>) -> binder::BoxFuture<'a, binder::Result<Vec<f64>>>;
  fn r#ReverseString<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>>;
  fn r#ReverseByteEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>>;
  fn r#ReverseIntEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>>;
  fn r#ReverseLongEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>>;
  fn r#GetOtherTestService<'a>(&'a self, _arg_name: &'a str) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>;
  fn r#SetOtherTestService<'a>(&'a self, _arg_name: &'a str, _arg_service: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#VerifyName<'a>(&'a self, _arg_service: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &'a str) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#GetInterfaceArray<'a>(&'a self, _arg_names: &'a [String]) -> binder::BoxFuture<'a, binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>;
  fn r#VerifyNamesWithInterfaceArray<'a>(&'a self, _arg_services: &'a [binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &'a [String]) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#GetNullableInterfaceArray<'a>(&'a self, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>>;
  fn r#VerifyNamesWithNullableInterfaceArray<'a>(&'a self, _arg_services: Option<&'a [Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#GetInterfaceList<'a>(&'a self, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>>;
  fn r#VerifyNamesWithInterfaceList<'a>(&'a self, _arg_services: Option<&'a [Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<bool>>;
  fn r#ReverseStringList<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>>;
  fn r#RepeatParcelFileDescriptor<'a>(&'a self, _arg_read: &'a binder::ParcelFileDescriptor) -> binder::BoxFuture<'a, binder::Result<binder::ParcelFileDescriptor>>;
  fn r#ReverseParcelFileDescriptorArray<'a>(&'a self, _arg_input: &'a [binder::ParcelFileDescriptor], _arg_repeated: &'a mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::BoxFuture<'a, binder::Result<Vec<binder::ParcelFileDescriptor>>>;
  fn r#ThrowServiceException<'a>(&'a self, _arg_code: i32) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#RepeatNullableIntArray<'a>(&'a self, _arg_input: Option<&'a [i32]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<i32>>>>;
  fn r#RepeatNullableByteEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>>>;
  fn r#RepeatNullableIntEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>>>;
  fn r#RepeatNullableLongEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>>>;
  fn r#RepeatNullableString<'a>(&'a self, _arg_input: Option<&'a str>) -> binder::BoxFuture<'a, binder::Result<Option<String>>>;
  fn r#RepeatNullableStringList<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>>;
  fn r#RepeatNullableParcelable<'a>(&'a self, _arg_input: Option<&'a crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::BoxFuture<'a, binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>;
  fn r#RepeatNullableParcelableArray<'a>(&'a self, _arg_input: Option<&'a [Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>>;
  fn r#RepeatNullableParcelableList<'a>(&'a self, _arg_input: Option<&'a [Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>>;
  fn r#TakesAnIBinder<'a>(&'a self, _arg_input: &'a binder::SpIBinder) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#TakesANullableIBinder<'a>(&'a self, _arg_input: Option<&'a binder::SpIBinder>) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#TakesAnIBinderList<'a>(&'a self, _arg_input: &'a [binder::SpIBinder]) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#TakesANullableIBinderList<'a>(&'a self, _arg_input: Option<&'a [Option<binder::SpIBinder>]>) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#RepeatUtf8CppString<'a>(&'a self, _arg_token: &'a str) -> binder::BoxFuture<'a, binder::Result<String>>;
  fn r#RepeatNullableUtf8CppString<'a>(&'a self, _arg_token: Option<&'a str>) -> binder::BoxFuture<'a, binder::Result<Option<String>>>;
  fn r#ReverseUtf8CppString<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>>;
  fn r#ReverseNullableUtf8CppString<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>, _arg_repeated: &'a mut Option<Vec<Option<String>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>>;
  fn r#ReverseUtf8CppStringList<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>, _arg_repeated: &'a mut Option<Vec<Option<String>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>>;
  fn r#GetCallback<'a>(&'a self, _arg_return_null: bool) -> binder::BoxFuture<'a, binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>;
  fn r#FillOutStructuredParcelable<'a>(&'a self, _arg_parcel: &'a mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#RepeatExtendableParcelable<'a>(&'a self, _arg_ep: &'a crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &'a mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#ReverseList<'a>(&'a self, _arg_list: &'a crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList>>;
  fn r#ReverseIBinderArray<'a>(&'a self, _arg_input: &'a [binder::SpIBinder], _arg_repeated: &'a mut Vec<Option<binder::SpIBinder>>) -> binder::BoxFuture<'a, binder::Result<Vec<binder::SpIBinder>>>;
  fn r#ReverseNullableIBinderArray<'a>(&'a self, _arg_input: Option<&'a [Option<binder::SpIBinder>]>, _arg_repeated: &'a mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::SpIBinder>>>>>;
  fn r#RepeatSimpleParcelable<'a>(&'a self, _arg_input: &'a simple_parcelable::SimpleParcelable, _arg_repeat: &'a mut simple_parcelable::SimpleParcelable) -> binder::BoxFuture<'a, binder::Result<simple_parcelable::SimpleParcelable>>;
  fn r#ReverseSimpleParcelables<'a>(&'a self, _arg_input: &'a [simple_parcelable::SimpleParcelable], _arg_repeated: &'a mut Vec<simple_parcelable::SimpleParcelable>) -> binder::BoxFuture<'a, binder::Result<Vec<simple_parcelable::SimpleParcelable>>>;
  fn r#GetOldNameInterface<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>>>;
  fn r#GetNewNameInterface<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>>>;
  fn r#GetUnionTags<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>>>;
  fn r#GetCppJavaTests<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<Option<binder::SpIBinder>>>;
  fn r#getBackendType<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType>>;
  fn r#GetCircular<'a>(&'a self, _arg_cp: &'a mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>>>;
}
#[::async_trait::async_trait]
pub trait ITestServiceAsyncServer: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService" }
  async fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32>;
  #[deprecated = "to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"]
  async fn r#Deprecated(&self) -> binder::Result<()>;
  async fn r#TestOneway(&self) -> binder::Result<()>;
  async fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool>;
  async fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8>;
  async fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16>;
  async fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32>;
  async fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64>;
  async fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32>;
  async fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64>;
  async fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String>;
  async fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>;
  async fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>;
  async fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>;
  async fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>>;
  async fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>>;
  async fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>>;
  async fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>>;
  async fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>>;
  async fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>>;
  async fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>>;
  async fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  async fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>;
  async fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>;
  async fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>;
  async fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>;
  async fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool>;
  async fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool>;
  async fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>;
  async fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool>;
  async fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>;
  async fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool>;
  async fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>;
  async fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool>;
  async fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  async fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor>;
  async fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>>;
  async fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()>;
  async fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>>;
  async fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>>;
  async fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>>;
  async fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>>;
  async fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>>;
  async fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>>;
  async fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>;
  async fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>;
  async fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>;
  async fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()>;
  async fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()>;
  async fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()>;
  async fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()>;
  async fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String>;
  async fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>>;
  async fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>>;
  async fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>>;
  async fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>>;
  async fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>;
  async fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()>;
  async fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()>;
  async fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList>;
  async fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>>;
  async fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>>;
  async fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable>;
  async fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>>;
  async fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>>;
  async fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>>;
  async fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>>;
  async fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>>;
  async fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType>;
  async fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>>;
}
impl BnTestService {
  /// Create a new async binder service.
  pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn ITestService>
  where
    T: ITestServiceAsyncServer + binder::Interface + Send + Sync + 'static,
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
    impl<T, R> ITestService for Wrapper<T, R>
    where
      T: ITestServiceAsyncServer + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32> {
        self._rt.block_on(self._inner.r#UnimplementedMethod(_arg_arg))
      }
      fn r#Deprecated(&self) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#Deprecated())
      }
      fn r#TestOneway(&self) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#TestOneway())
      }
      fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#RepeatBoolean(_arg_token))
      }
      fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8> {
        self._rt.block_on(self._inner.r#RepeatByte(_arg_token))
      }
      fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16> {
        self._rt.block_on(self._inner.r#RepeatChar(_arg_token))
      }
      fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32> {
        self._rt.block_on(self._inner.r#RepeatInt(_arg_token))
      }
      fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64> {
        self._rt.block_on(self._inner.r#RepeatLong(_arg_token))
      }
      fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32> {
        self._rt.block_on(self._inner.r#RepeatFloat(_arg_token))
      }
      fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64> {
        self._rt.block_on(self._inner.r#RepeatDouble(_arg_token))
      }
      fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String> {
        self._rt.block_on(self._inner.r#RepeatString(_arg_token))
      }
      fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> {
        self._rt.block_on(self._inner.r#RepeatByteEnum(_arg_token))
      }
      fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> {
        self._rt.block_on(self._inner.r#RepeatIntEnum(_arg_token))
      }
      fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> {
        self._rt.block_on(self._inner.r#RepeatLongEnum(_arg_token))
      }
      fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>> {
        self._rt.block_on(self._inner.r#ReverseBoolean(_arg_input, _arg_repeated))
      }
      fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>> {
        self._rt.block_on(self._inner.r#ReverseByte(_arg_input, _arg_repeated))
      }
      fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>> {
        self._rt.block_on(self._inner.r#ReverseChar(_arg_input, _arg_repeated))
      }
      fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>> {
        self._rt.block_on(self._inner.r#ReverseInt(_arg_input, _arg_repeated))
      }
      fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>> {
        self._rt.block_on(self._inner.r#ReverseLong(_arg_input, _arg_repeated))
      }
      fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>> {
        self._rt.block_on(self._inner.r#ReverseFloat(_arg_input, _arg_repeated))
      }
      fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>> {
        self._rt.block_on(self._inner.r#ReverseDouble(_arg_input, _arg_repeated))
      }
      fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
        self._rt.block_on(self._inner.r#ReverseString(_arg_input, _arg_repeated))
      }
      fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> {
        self._rt.block_on(self._inner.r#ReverseByteEnum(_arg_input, _arg_repeated))
      }
      fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> {
        self._rt.block_on(self._inner.r#ReverseIntEnum(_arg_input, _arg_repeated))
      }
      fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> {
        self._rt.block_on(self._inner.r#ReverseLongEnum(_arg_input, _arg_repeated))
      }
      fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> {
        self._rt.block_on(self._inner.r#GetOtherTestService(_arg_name))
      }
      fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#SetOtherTestService(_arg_name, _arg_service))
      }
      fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#VerifyName(_arg_service, _arg_name))
      }
      fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
        self._rt.block_on(self._inner.r#GetInterfaceArray(_arg_names))
      }
      fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#VerifyNamesWithInterfaceArray(_arg_services, _arg_names))
      }
      fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
        self._rt.block_on(self._inner.r#GetNullableInterfaceArray(_arg_names))
      }
      fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names))
      }
      fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
        self._rt.block_on(self._inner.r#GetInterfaceList(_arg_names))
      }
      fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
        self._rt.block_on(self._inner.r#VerifyNamesWithInterfaceList(_arg_services, _arg_names))
      }
      fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
        self._rt.block_on(self._inner.r#ReverseStringList(_arg_input, _arg_repeated))
      }
      fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor> {
        self._rt.block_on(self._inner.r#RepeatParcelFileDescriptor(_arg_read))
      }
      fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>> {
        self._rt.block_on(self._inner.r#ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated))
      }
      fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#ThrowServiceException(_arg_code))
      }
      fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>> {
        self._rt.block_on(self._inner.r#RepeatNullableIntArray(_arg_input))
      }
      fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> {
        self._rt.block_on(self._inner.r#RepeatNullableByteEnumArray(_arg_input))
      }
      fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> {
        self._rt.block_on(self._inner.r#RepeatNullableIntEnumArray(_arg_input))
      }
      fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> {
        self._rt.block_on(self._inner.r#RepeatNullableLongEnumArray(_arg_input))
      }
      fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>> {
        self._rt.block_on(self._inner.r#RepeatNullableString(_arg_input))
      }
      fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>> {
        self._rt.block_on(self._inner.r#RepeatNullableStringList(_arg_input))
      }
      fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>> {
        self._rt.block_on(self._inner.r#RepeatNullableParcelable(_arg_input))
      }
      fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
        self._rt.block_on(self._inner.r#RepeatNullableParcelableArray(_arg_input))
      }
      fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
        self._rt.block_on(self._inner.r#RepeatNullableParcelableList(_arg_input))
      }
      fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#TakesAnIBinder(_arg_input))
      }
      fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#TakesANullableIBinder(_arg_input))
      }
      fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#TakesAnIBinderList(_arg_input))
      }
      fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#TakesANullableIBinderList(_arg_input))
      }
      fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String> {
        self._rt.block_on(self._inner.r#RepeatUtf8CppString(_arg_token))
      }
      fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>> {
        self._rt.block_on(self._inner.r#RepeatNullableUtf8CppString(_arg_token))
      }
      fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
        self._rt.block_on(self._inner.r#ReverseUtf8CppString(_arg_input, _arg_repeated))
      }
      fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
        self._rt.block_on(self._inner.r#ReverseNullableUtf8CppString(_arg_input, _arg_repeated))
      }
      fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
        self._rt.block_on(self._inner.r#ReverseUtf8CppStringList(_arg_input, _arg_repeated))
      }
      fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
        self._rt.block_on(self._inner.r#GetCallback(_arg_return_null))
      }
      fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#FillOutStructuredParcelable(_arg_parcel))
      }
      fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#RepeatExtendableParcelable(_arg_ep, _arg_ep2))
      }
      fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList> {
        self._rt.block_on(self._inner.r#ReverseList(_arg_list))
      }
      fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>> {
        self._rt.block_on(self._inner.r#ReverseIBinderArray(_arg_input, _arg_repeated))
      }
      fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>> {
        self._rt.block_on(self._inner.r#ReverseNullableIBinderArray(_arg_input, _arg_repeated))
      }
      fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable> {
        self._rt.block_on(self._inner.r#RepeatSimpleParcelable(_arg_input, _arg_repeat))
      }
      fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>> {
        self._rt.block_on(self._inner.r#ReverseSimpleParcelables(_arg_input, _arg_repeated))
      }
      fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>> {
        self._rt.block_on(self._inner.r#GetOldNameInterface())
      }
      fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>> {
        self._rt.block_on(self._inner.r#GetNewNameInterface())
      }
      fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>> {
        self._rt.block_on(self._inner.r#GetUnionTags(_arg_input))
      }
      fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>> {
        self._rt.block_on(self._inner.r#GetCppJavaTests())
      }
      fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType> {
        self._rt.block_on(self._inner.r#getBackendType())
      }
      fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>> {
        self._rt.block_on(self._inner.r#GetCircular(_arg_cp))
      }
    }
    let wrapped = Wrapper { _inner: inner, _rt: rt };
    Self::new_binder(wrapped, features)
  }
}
pub trait ITestServiceDefault: Send + Sync {
  fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#Deprecated(&self) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#TestOneway(&self) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
}
pub mod transactions {
  pub const r#UnimplementedMethod: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
  pub const r#Deprecated: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 1;
  pub const r#TestOneway: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 2;
  pub const r#RepeatBoolean: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 3;
  pub const r#RepeatByte: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 4;
  pub const r#RepeatChar: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 5;
  pub const r#RepeatInt: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 6;
  pub const r#RepeatLong: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 7;
  pub const r#RepeatFloat: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 8;
  pub const r#RepeatDouble: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 9;
  pub const r#RepeatString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 10;
  pub const r#RepeatByteEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 11;
  pub const r#RepeatIntEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 12;
  pub const r#RepeatLongEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 13;
  pub const r#ReverseBoolean: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 14;
  pub const r#ReverseByte: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 15;
  pub const r#ReverseChar: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 16;
  pub const r#ReverseInt: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 17;
  pub const r#ReverseLong: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 18;
  pub const r#ReverseFloat: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 19;
  pub const r#ReverseDouble: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 20;
  pub const r#ReverseString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 21;
  pub const r#ReverseByteEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 22;
  pub const r#ReverseIntEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 23;
  pub const r#ReverseLongEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 24;
  pub const r#GetOtherTestService: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 25;
  pub const r#SetOtherTestService: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 26;
  pub const r#VerifyName: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 27;
  pub const r#GetInterfaceArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 28;
  pub const r#VerifyNamesWithInterfaceArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 29;
  pub const r#GetNullableInterfaceArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 30;
  pub const r#VerifyNamesWithNullableInterfaceArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 31;
  pub const r#GetInterfaceList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 32;
  pub const r#VerifyNamesWithInterfaceList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 33;
  pub const r#ReverseStringList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 34;
  pub const r#RepeatParcelFileDescriptor: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 35;
  pub const r#ReverseParcelFileDescriptorArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 36;
  pub const r#ThrowServiceException: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 37;
  pub const r#RepeatNullableIntArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 38;
  pub const r#RepeatNullableByteEnumArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 39;
  pub const r#RepeatNullableIntEnumArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 40;
  pub const r#RepeatNullableLongEnumArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 41;
  pub const r#RepeatNullableString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 42;
  pub const r#RepeatNullableStringList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 43;
  pub const r#RepeatNullableParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 44;
  pub const r#RepeatNullableParcelableArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 45;
  pub const r#RepeatNullableParcelableList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 46;
  pub const r#TakesAnIBinder: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 47;
  pub const r#TakesANullableIBinder: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 48;
  pub const r#TakesAnIBinderList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 49;
  pub const r#TakesANullableIBinderList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 50;
  pub const r#RepeatUtf8CppString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 51;
  pub const r#RepeatNullableUtf8CppString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 52;
  pub const r#ReverseUtf8CppString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 53;
  pub const r#ReverseNullableUtf8CppString: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 54;
  pub const r#ReverseUtf8CppStringList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 55;
  pub const r#GetCallback: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 56;
  pub const r#FillOutStructuredParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 57;
  pub const r#RepeatExtendableParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 58;
  pub const r#ReverseList: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 59;
  pub const r#ReverseIBinderArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 60;
  pub const r#ReverseNullableIBinderArray: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 61;
  pub const r#RepeatSimpleParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 62;
  pub const r#ReverseSimpleParcelables: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 63;
  pub const r#GetOldNameInterface: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 64;
  pub const r#GetNewNameInterface: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 65;
  pub const r#GetUnionTags: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 66;
  pub const r#GetCppJavaTests: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 67;
  pub const r#getBackendType: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 68;
  pub const r#GetCircular: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 69;
}
pub type ITestServiceDefaultRef = Option<std::sync::Arc<dyn ITestServiceDefault>>;
static DEFAULT_IMPL: std::sync::Mutex<ITestServiceDefaultRef> = std::sync::Mutex::new(None);
pub const r#CONSTANT: i32 = 42;
pub const r#CONSTANT2: i32 = -42;
pub const r#CONSTANT3: i32 = 42;
pub const r#CONSTANT4: i32 = 4;
pub const r#CONSTANT5: i32 = -4;
pub const r#CONSTANT6: i32 = 0;
pub const r#CONSTANT7: i32 = 0;
pub const r#CONSTANT8: i32 = 0;
pub const r#CONSTANT9: i32 = 86;
pub const r#CONSTANT10: i32 = 165;
pub const r#CONSTANT11: i32 = 250;
pub const r#CONSTANT12: i32 = -1;
pub const r#BYTE_CONSTANT: i8 = 17;
pub const r#LONG_CONSTANT: i64 = 1099511627776;
pub const r#STRING_CONSTANT: &str = "foo";
pub const r#STRING_CONSTANT2: &str = "bar";
pub const r#FLOAT_CONSTANT: f32 = 1.000000f32;
pub const r#FLOAT_CONSTANT2: f32 = -1.000000f32;
pub const r#FLOAT_CONSTANT3: f32 = 1.000000f32;
pub const r#FLOAT_CONSTANT4: f32 = 2.200000f32;
pub const r#FLOAT_CONSTANT5: f32 = -2.200000f32;
pub const r#FLOAT_CONSTANT6: f32 = -0.000000f32;
pub const r#FLOAT_CONSTANT7: f32 = 0.000000f32;
pub const r#DOUBLE_CONSTANT: f64 = 1.000000f64;
pub const r#DOUBLE_CONSTANT2: f64 = -1.000000f64;
pub const r#DOUBLE_CONSTANT3: f64 = 1.000000f64;
pub const r#DOUBLE_CONSTANT4: f64 = 2.200000f64;
pub const r#DOUBLE_CONSTANT5: f64 = -2.200000f64;
pub const r#DOUBLE_CONSTANT6: f64 = -0.000000f64;
pub const r#DOUBLE_CONSTANT7: f64 = 0.000000f64;
pub const r#DOUBLE_CONSTANT8: f64 = 1.100000f64;
pub const r#DOUBLE_CONSTANT9: f64 = -1.100000f64;
pub const r#STRING_CONSTANT_UTF8: &str = "baz";
pub const r#A1: i32 = 1;
pub const r#A2: i32 = 1;
pub const r#A3: i32 = 1;
pub const r#A4: i32 = 1;
pub const r#A5: i32 = 1;
pub const r#A6: i32 = 1;
pub const r#A7: i32 = 1;
pub const r#A8: i32 = 1;
pub const r#A9: i32 = 1;
pub const r#A10: i32 = 1;
pub const r#A11: i32 = 1;
pub const r#A12: i32 = 1;
pub const r#A13: i32 = 1;
pub const r#A14: i32 = 1;
pub const r#A15: i32 = 1;
pub const r#A16: i32 = 1;
pub const r#A17: i32 = 1;
pub const r#A18: i32 = 1;
pub const r#A19: i32 = 1;
pub const r#A20: i32 = 1;
pub const r#A21: i32 = 1;
pub const r#A22: i32 = 1;
pub const r#A23: i32 = 1;
pub const r#A24: i32 = 1;
pub const r#A25: i32 = 1;
pub const r#A26: i32 = 1;
pub const r#A27: i32 = 1;
pub const r#A28: i32 = 1;
pub const r#A29: i32 = 1;
pub const r#A30: i32 = 1;
pub const r#A31: i32 = 1;
pub const r#A32: i32 = 1;
pub const r#A33: i32 = 1;
pub const r#A34: i32 = 1;
pub const r#A35: i32 = 1;
pub const r#A36: i32 = 1;
pub const r#A37: i32 = 1;
pub const r#A38: i32 = 1;
pub const r#A39: i32 = 1;
pub const r#A40: i32 = 1;
pub const r#A41: i32 = 1;
pub const r#A42: i32 = 1;
pub const r#A43: i32 = 1;
pub const r#A44: i32 = 1;
pub const r#A45: i32 = 1;
pub const r#A46: i32 = 1;
pub const r#A47: i32 = 1;
pub const r#A48: i32 = 1;
pub const r#A49: i32 = 1;
pub const r#A50: i32 = 1;
pub const r#A51: i32 = 1;
pub const r#A52: i32 = 1;
pub const r#A53: i32 = 1;
pub const r#A54: i32 = 1;
pub const r#A55: i32 = 1;
pub const r#A56: i32 = 1;
pub const r#A57: i32 = 1;
impl BpTestService {
  fn build_parcel_UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_arg)?;
    Ok(aidl_data)
  }
  fn read_response_UnimplementedMethod(&self, _arg_arg: i32, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i32> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#UnimplementedMethod(_arg_arg);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: i32 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_Deprecated(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_Deprecated(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#Deprecated();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_TestOneway(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_TestOneway(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#TestOneway();
      }
    }
    let _aidl_reply = _aidl_reply?;
    Ok(())
  }
  fn build_parcel_RepeatBoolean(&self, _arg_token: bool) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatBoolean(&self, _arg_token: bool, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatBoolean(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatByte(&self, _arg_token: i8) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatByte(&self, _arg_token: i8, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i8> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatByte(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: i8 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatChar(&self, _arg_token: u16) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatChar(&self, _arg_token: u16, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<u16> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatChar(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: u16 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatInt(&self, _arg_token: i32) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatInt(&self, _arg_token: i32, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i32> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatInt(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: i32 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatLong(&self, _arg_token: i64) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatLong(&self, _arg_token: i64, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i64> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatLong(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: i64 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatFloat(&self, _arg_token: f32) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatFloat(&self, _arg_token: f32, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<f32> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatFloat(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: f32 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatDouble(&self, _arg_token: f64) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatDouble(&self, _arg_token: f64, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<f64> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatDouble(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: f64 = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatString(&self, _arg_token: &str) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatString(&self, _arg_token: &str, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<String> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatString(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: String = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatByteEnum(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatIntEnum(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatLongEnum(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<bool>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseBoolean(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<bool> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<u8>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseByte(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<u8> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<u16>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseChar(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<u16> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<i32>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseInt(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<i32> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<i64>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseLong(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<i64> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<f32>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseFloat(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<f32> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<f64>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseDouble(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<f64> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<String>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseString(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<String> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseByteEnum(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseIntEnum(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseLongEnum(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_name)?;
    Ok(aidl_data)
  }
  fn read_response_GetOtherTestService(&self, _arg_name: &str, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetOtherTestService(_arg_name);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_name)?;
    aidl_data.write(_arg_service)?;
    Ok(aidl_data)
  }
  fn read_response_SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#SetOtherTestService(_arg_name, _arg_service);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_service)?;
    aidl_data.write(_arg_name)?;
    Ok(aidl_data)
  }
  fn read_response_VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#VerifyName(_arg_service, _arg_name);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_GetInterfaceArray(&self, _arg_names: &[String], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetInterfaceArray(_arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_services)?;
    aidl_data.write(_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#VerifyNamesWithInterfaceArray(_arg_services, _arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetNullableInterfaceArray(_arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_services)?;
    aidl_data.write(&_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetInterfaceList(_arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_services)?;
    aidl_data.write(&_arg_names)?;
    Ok(aidl_data)
  }
  fn read_response_VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<bool> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#VerifyNamesWithInterfaceList(_arg_services, _arg_names);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: bool = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<String>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseStringList(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<String> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_read)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<binder::ParcelFileDescriptor> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatParcelFileDescriptor(_arg_read);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: binder::ParcelFileDescriptor = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<binder::ParcelFileDescriptor>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<binder::ParcelFileDescriptor> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ThrowServiceException(&self, _arg_code: i32) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_code)?;
    Ok(aidl_data)
  }
  fn read_response_ThrowServiceException(&self, _arg_code: i32, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ThrowServiceException(_arg_code);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<i32>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableIntArray(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<i32>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableByteEnumArray(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableIntEnumArray(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableLongEnumArray(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableString(&self, _arg_input: Option<&str>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<String>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableString(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<String> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<String>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableStringList(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<String>>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableParcelable(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableParcelableArray(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableParcelableList(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_TakesAnIBinder(&self, _arg_input: &binder::SpIBinder, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#TakesAnIBinder(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#TakesANullableIBinder(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#TakesAnIBinderList(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#TakesANullableIBinderList(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatUtf8CppString(&self, _arg_token: &str, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<String> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatUtf8CppString(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: String = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_token)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<String>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatNullableUtf8CppString(_arg_token);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<String> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<String>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseUtf8CppString(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<String> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    aidl_data.write_slice_size(_arg_repeated.as_deref())?;
    Ok(aidl_data)
  }
  fn read_response_ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<String>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseNullableUtf8CppString(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<String>>> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<String>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseUtf8CppStringList(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<String>>> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetCallback(&self, _arg_return_null: bool) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_return_null)?;
    Ok(aidl_data)
  }
  fn read_response_GetCallback(&self, _arg_return_null: bool, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetCallback(_arg_return_null);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_parcel)?;
    Ok(aidl_data)
  }
  fn read_response_FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#FillOutStructuredParcelable(_arg_parcel);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    _aidl_reply.read_onto(_arg_parcel)?;
    Ok(())
  }
  fn build_parcel_RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_ep)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatExtendableParcelable(_arg_ep, _arg_ep2);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    _aidl_reply.read_onto(_arg_ep2)?;
    Ok(())
  }
  fn build_parcel_ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_list)?;
    Ok(aidl_data)
  }
  fn read_response_ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseList(_arg_list);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<binder::SpIBinder>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseIBinderArray(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<binder::SpIBinder> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(&_arg_input)?;
    aidl_data.write_slice_size(_arg_repeated.as_deref())?;
    Ok(aidl_data)
  }
  fn read_response_ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseNullableIBinderArray(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<Vec<Option<binder::SpIBinder>>> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<simple_parcelable::SimpleParcelable> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#RepeatSimpleParcelable(_arg_input, _arg_repeat);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: simple_parcelable::SimpleParcelable = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeat)?;
    Ok(_aidl_return)
  }
  fn build_parcel_ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    aidl_data.write_slice_size(Some(_arg_repeated))?;
    Ok(aidl_data)
  }
  fn read_response_ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#ReverseSimpleParcelables(_arg_input, _arg_repeated);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<simple_parcelable::SimpleParcelable> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_repeated)?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetOldNameInterface(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_GetOldNameInterface(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetOldNameInterface();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetNewNameInterface(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_GetNewNameInterface(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetNewNameInterface();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union], _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetUnionTags(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetCppJavaTests(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_GetCppJavaTests(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<binder::SpIBinder>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetCppJavaTests();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<binder::SpIBinder> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_getBackendType(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_getBackendType(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#getBackendType();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_11_BackendType = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.mark_sensitive();
    Ok(aidl_data)
  }
  fn read_response_GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITestService>::getDefaultImpl() {
        return _aidl_default_impl.r#GetCircular(_arg_cp);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular> = _aidl_reply.read()?;
    _aidl_reply.read_onto(_arg_cp)?;
    Ok(_aidl_return)
  }
}
impl ITestService for BpTestService {
  fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32> {
    let _aidl_data = self.build_parcel_UnimplementedMethod(_arg_arg)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#UnimplementedMethod, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_UnimplementedMethod(_arg_arg, _aidl_reply)
  }
  fn r#Deprecated(&self) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_Deprecated()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#Deprecated, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_Deprecated(_aidl_reply)
  }
  fn r#TestOneway(&self) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_TestOneway()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#TestOneway, _aidl_data, binder::binder_impl::FLAG_ONEWAY | binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_TestOneway(_aidl_reply)
  }
  fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_RepeatBoolean(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatBoolean, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatBoolean(_arg_token, _aidl_reply)
  }
  fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8> {
    let _aidl_data = self.build_parcel_RepeatByte(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatByte, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatByte(_arg_token, _aidl_reply)
  }
  fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16> {
    let _aidl_data = self.build_parcel_RepeatChar(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatChar, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatChar(_arg_token, _aidl_reply)
  }
  fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32> {
    let _aidl_data = self.build_parcel_RepeatInt(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatInt, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatInt(_arg_token, _aidl_reply)
  }
  fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64> {
    let _aidl_data = self.build_parcel_RepeatLong(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatLong, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatLong(_arg_token, _aidl_reply)
  }
  fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32> {
    let _aidl_data = self.build_parcel_RepeatFloat(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatFloat, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatFloat(_arg_token, _aidl_reply)
  }
  fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64> {
    let _aidl_data = self.build_parcel_RepeatDouble(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatDouble, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatDouble(_arg_token, _aidl_reply)
  }
  fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String> {
    let _aidl_data = self.build_parcel_RepeatString(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatString(_arg_token, _aidl_reply)
  }
  fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> {
    let _aidl_data = self.build_parcel_RepeatByteEnum(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatByteEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatByteEnum(_arg_token, _aidl_reply)
  }
  fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> {
    let _aidl_data = self.build_parcel_RepeatIntEnum(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatIntEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatIntEnum(_arg_token, _aidl_reply)
  }
  fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> {
    let _aidl_data = self.build_parcel_RepeatLongEnum(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatLongEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatLongEnum(_arg_token, _aidl_reply)
  }
  fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>> {
    let _aidl_data = self.build_parcel_ReverseBoolean(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseBoolean, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseBoolean(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>> {
    let _aidl_data = self.build_parcel_ReverseByte(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseByte, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseByte(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>> {
    let _aidl_data = self.build_parcel_ReverseChar(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseChar, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseChar(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>> {
    let _aidl_data = self.build_parcel_ReverseInt(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseInt, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseInt(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>> {
    let _aidl_data = self.build_parcel_ReverseLong(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseLong, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseLong(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>> {
    let _aidl_data = self.build_parcel_ReverseFloat(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseFloat, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseFloat(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>> {
    let _aidl_data = self.build_parcel_ReverseDouble(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseDouble, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseDouble(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    let _aidl_data = self.build_parcel_ReverseString(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseString(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> {
    let _aidl_data = self.build_parcel_ReverseByteEnum(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseByteEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseByteEnum(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> {
    let _aidl_data = self.build_parcel_ReverseIntEnum(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseIntEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseIntEnum(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> {
    let _aidl_data = self.build_parcel_ReverseLongEnum(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseLongEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseLongEnum(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> {
    let _aidl_data = self.build_parcel_GetOtherTestService(_arg_name)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetOtherTestService, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetOtherTestService(_arg_name, _aidl_reply)
  }
  fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_SetOtherTestService(_arg_name, _arg_service)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#SetOtherTestService, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_SetOtherTestService(_arg_name, _arg_service, _aidl_reply)
  }
  fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_VerifyName(_arg_service, _arg_name)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#VerifyName, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_VerifyName(_arg_service, _arg_name, _aidl_reply)
  }
  fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    let _aidl_data = self.build_parcel_GetInterfaceArray(_arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetInterfaceArray(_arg_names, _aidl_reply)
  }
  fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_VerifyNamesWithInterfaceArray(_arg_services, _arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#VerifyNamesWithInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_VerifyNamesWithInterfaceArray(_arg_services, _arg_names, _aidl_reply)
  }
  fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    let _aidl_data = self.build_parcel_GetNullableInterfaceArray(_arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetNullableInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetNullableInterfaceArray(_arg_names, _aidl_reply)
  }
  fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#VerifyNamesWithNullableInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names, _aidl_reply)
  }
  fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> {
    let _aidl_data = self.build_parcel_GetInterfaceList(_arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetInterfaceList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetInterfaceList(_arg_names, _aidl_reply)
  }
  fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> {
    let _aidl_data = self.build_parcel_VerifyNamesWithInterfaceList(_arg_services, _arg_names)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#VerifyNamesWithInterfaceList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_VerifyNamesWithInterfaceList(_arg_services, _arg_names, _aidl_reply)
  }
  fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    let _aidl_data = self.build_parcel_ReverseStringList(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseStringList(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor> {
    let _aidl_data = self.build_parcel_RepeatParcelFileDescriptor(_arg_read)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatParcelFileDescriptor, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatParcelFileDescriptor(_arg_read, _aidl_reply)
  }
  fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>> {
    let _aidl_data = self.build_parcel_ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseParcelFileDescriptorArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_ThrowServiceException(_arg_code)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ThrowServiceException, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ThrowServiceException(_arg_code, _aidl_reply)
  }
  fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>> {
    let _aidl_data = self.build_parcel_RepeatNullableIntArray(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableIntArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableIntArray(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> {
    let _aidl_data = self.build_parcel_RepeatNullableByteEnumArray(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableByteEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableByteEnumArray(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> {
    let _aidl_data = self.build_parcel_RepeatNullableIntEnumArray(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableIntEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableIntEnumArray(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> {
    let _aidl_data = self.build_parcel_RepeatNullableLongEnumArray(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableLongEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableLongEnumArray(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>> {
    let _aidl_data = self.build_parcel_RepeatNullableString(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableString(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>> {
    let _aidl_data = self.build_parcel_RepeatNullableStringList(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableStringList(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>> {
    let _aidl_data = self.build_parcel_RepeatNullableParcelable(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableParcelable(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    let _aidl_data = self.build_parcel_RepeatNullableParcelableArray(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableParcelableArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableParcelableArray(_arg_input, _aidl_reply)
  }
  fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> {
    let _aidl_data = self.build_parcel_RepeatNullableParcelableList(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableParcelableList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableParcelableList(_arg_input, _aidl_reply)
  }
  fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_TakesAnIBinder(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#TakesAnIBinder, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_TakesAnIBinder(_arg_input, _aidl_reply)
  }
  fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_TakesANullableIBinder(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#TakesANullableIBinder, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_TakesANullableIBinder(_arg_input, _aidl_reply)
  }
  fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_TakesAnIBinderList(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#TakesAnIBinderList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_TakesAnIBinderList(_arg_input, _aidl_reply)
  }
  fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_TakesANullableIBinderList(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#TakesANullableIBinderList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_TakesANullableIBinderList(_arg_input, _aidl_reply)
  }
  fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String> {
    let _aidl_data = self.build_parcel_RepeatUtf8CppString(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatUtf8CppString(_arg_token, _aidl_reply)
  }
  fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>> {
    let _aidl_data = self.build_parcel_RepeatNullableUtf8CppString(_arg_token)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatNullableUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatNullableUtf8CppString(_arg_token, _aidl_reply)
  }
  fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> {
    let _aidl_data = self.build_parcel_ReverseUtf8CppString(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseUtf8CppString(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
    let _aidl_data = self.build_parcel_ReverseNullableUtf8CppString(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseNullableUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseNullableUtf8CppString(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> {
    let _aidl_data = self.build_parcel_ReverseUtf8CppStringList(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseUtf8CppStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseUtf8CppStringList(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    let _aidl_data = self.build_parcel_GetCallback(_arg_return_null)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetCallback, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetCallback(_arg_return_null, _aidl_reply)
  }
  fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_FillOutStructuredParcelable(_arg_parcel)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#FillOutStructuredParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_FillOutStructuredParcelable(_arg_parcel, _aidl_reply)
  }
  fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_RepeatExtendableParcelable(_arg_ep, _arg_ep2)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatExtendableParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatExtendableParcelable(_arg_ep, _arg_ep2, _aidl_reply)
  }
  fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList> {
    let _aidl_data = self.build_parcel_ReverseList(_arg_list)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseList(_arg_list, _aidl_reply)
  }
  fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>> {
    let _aidl_data = self.build_parcel_ReverseIBinderArray(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseIBinderArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseIBinderArray(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>> {
    let _aidl_data = self.build_parcel_ReverseNullableIBinderArray(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseNullableIBinderArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseNullableIBinderArray(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable> {
    let _aidl_data = self.build_parcel_RepeatSimpleParcelable(_arg_input, _arg_repeat)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#RepeatSimpleParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_RepeatSimpleParcelable(_arg_input, _arg_repeat, _aidl_reply)
  }
  fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>> {
    let _aidl_data = self.build_parcel_ReverseSimpleParcelables(_arg_input, _arg_repeated)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#ReverseSimpleParcelables, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_ReverseSimpleParcelables(_arg_input, _arg_repeated, _aidl_reply)
  }
  fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>> {
    let _aidl_data = self.build_parcel_GetOldNameInterface()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetOldNameInterface, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetOldNameInterface(_aidl_reply)
  }
  fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>> {
    let _aidl_data = self.build_parcel_GetNewNameInterface()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetNewNameInterface, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetNewNameInterface(_aidl_reply)
  }
  fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>> {
    let _aidl_data = self.build_parcel_GetUnionTags(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetUnionTags, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetUnionTags(_arg_input, _aidl_reply)
  }
  fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>> {
    let _aidl_data = self.build_parcel_GetCppJavaTests()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetCppJavaTests, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetCppJavaTests(_aidl_reply)
  }
  fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType> {
    let _aidl_data = self.build_parcel_getBackendType()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#getBackendType, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_getBackendType(_aidl_reply)
  }
  fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>> {
    let _aidl_data = self.build_parcel_GetCircular(_arg_cp)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetCircular, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetCircular(_arg_cp, _aidl_reply)
  }
}
impl<P: binder::BinderAsyncPool> ITestServiceAsync<P> for BpTestService {
  fn r#UnimplementedMethod<'a>(&'a self, _arg_arg: i32) -> binder::BoxFuture<'a, binder::Result<i32>> {
    let _aidl_data = match self.build_parcel_UnimplementedMethod(_arg_arg) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#UnimplementedMethod, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_UnimplementedMethod(_arg_arg, _aidl_reply)
      }
    )
  }
  fn r#Deprecated<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_Deprecated() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#Deprecated, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_Deprecated(_aidl_reply)
      }
    )
  }
  fn r#TestOneway(&self) -> std::future::Ready<binder::Result<()>> {
    let _aidl_data = match self.build_parcel_TestOneway() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return std::future::ready(Err(err)),
    };
    let _aidl_reply = self.binder.submit_transact(transactions::r#TestOneway, _aidl_data, binder::binder_impl::FLAG_ONEWAY | binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL);
    std::future::ready(self.read_response_TestOneway(_aidl_reply))
  }
  fn r#RepeatBoolean<'a>(&'a self, _arg_token: bool) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_RepeatBoolean(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatBoolean, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatBoolean(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatByte<'a>(&'a self, _arg_token: i8) -> binder::BoxFuture<'a, binder::Result<i8>> {
    let _aidl_data = match self.build_parcel_RepeatByte(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatByte, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatByte(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatChar<'a>(&'a self, _arg_token: u16) -> binder::BoxFuture<'a, binder::Result<u16>> {
    let _aidl_data = match self.build_parcel_RepeatChar(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatChar, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatChar(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatInt<'a>(&'a self, _arg_token: i32) -> binder::BoxFuture<'a, binder::Result<i32>> {
    let _aidl_data = match self.build_parcel_RepeatInt(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatInt, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatInt(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatLong<'a>(&'a self, _arg_token: i64) -> binder::BoxFuture<'a, binder::Result<i64>> {
    let _aidl_data = match self.build_parcel_RepeatLong(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatLong, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatLong(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatFloat<'a>(&'a self, _arg_token: f32) -> binder::BoxFuture<'a, binder::Result<f32>> {
    let _aidl_data = match self.build_parcel_RepeatFloat(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatFloat, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatFloat(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatDouble<'a>(&'a self, _arg_token: f64) -> binder::BoxFuture<'a, binder::Result<f64>> {
    let _aidl_data = match self.build_parcel_RepeatDouble(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatDouble, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatDouble(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatString<'a>(&'a self, _arg_token: &'a str) -> binder::BoxFuture<'a, binder::Result<String>> {
    let _aidl_data = match self.build_parcel_RepeatString(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatString(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatByteEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> {
    let _aidl_data = match self.build_parcel_RepeatByteEnum(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatByteEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatByteEnum(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatIntEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> {
    let _aidl_data = match self.build_parcel_RepeatIntEnum(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatIntEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatIntEnum(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatLongEnum<'a>(&'a self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> {
    let _aidl_data = match self.build_parcel_RepeatLongEnum(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatLongEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatLongEnum(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#ReverseBoolean<'a>(&'a self, _arg_input: &'a [bool], _arg_repeated: &'a mut Vec<bool>) -> binder::BoxFuture<'a, binder::Result<Vec<bool>>> {
    let _aidl_data = match self.build_parcel_ReverseBoolean(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseBoolean, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseBoolean(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseByte<'a>(&'a self, _arg_input: &'a [u8], _arg_repeated: &'a mut Vec<u8>) -> binder::BoxFuture<'a, binder::Result<Vec<u8>>> {
    let _aidl_data = match self.build_parcel_ReverseByte(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseByte, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseByte(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseChar<'a>(&'a self, _arg_input: &'a [u16], _arg_repeated: &'a mut Vec<u16>) -> binder::BoxFuture<'a, binder::Result<Vec<u16>>> {
    let _aidl_data = match self.build_parcel_ReverseChar(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseChar, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseChar(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseInt<'a>(&'a self, _arg_input: &'a [i32], _arg_repeated: &'a mut Vec<i32>) -> binder::BoxFuture<'a, binder::Result<Vec<i32>>> {
    let _aidl_data = match self.build_parcel_ReverseInt(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseInt, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseInt(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseLong<'a>(&'a self, _arg_input: &'a [i64], _arg_repeated: &'a mut Vec<i64>) -> binder::BoxFuture<'a, binder::Result<Vec<i64>>> {
    let _aidl_data = match self.build_parcel_ReverseLong(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseLong, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseLong(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseFloat<'a>(&'a self, _arg_input: &'a [f32], _arg_repeated: &'a mut Vec<f32>) -> binder::BoxFuture<'a, binder::Result<Vec<f32>>> {
    let _aidl_data = match self.build_parcel_ReverseFloat(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseFloat, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseFloat(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseDouble<'a>(&'a self, _arg_input: &'a [f64], _arg_repeated: &'a mut Vec<f64>) -> binder::BoxFuture<'a, binder::Result<Vec<f64>>> {
    let _aidl_data = match self.build_parcel_ReverseDouble(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseDouble, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseDouble(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseString<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>> {
    let _aidl_data = match self.build_parcel_ReverseString(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseString(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseByteEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> {
    let _aidl_data = match self.build_parcel_ReverseByteEnum(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseByteEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseByteEnum(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseIntEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> {
    let _aidl_data = match self.build_parcel_ReverseIntEnum(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseIntEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseIntEnum(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseLongEnum<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &'a mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> {
    let _aidl_data = match self.build_parcel_ReverseLongEnum(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseLongEnum, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseLongEnum(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#GetOtherTestService<'a>(&'a self, _arg_name: &'a str) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> {
    let _aidl_data = match self.build_parcel_GetOtherTestService(_arg_name) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetOtherTestService, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetOtherTestService(_arg_name, _aidl_reply)
      }
    )
  }
  fn r#SetOtherTestService<'a>(&'a self, _arg_name: &'a str, _arg_service: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_SetOtherTestService(_arg_name, _arg_service) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#SetOtherTestService, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_SetOtherTestService(_arg_name, _arg_service, _aidl_reply)
      }
    )
  }
  fn r#VerifyName<'a>(&'a self, _arg_service: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &'a str) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_VerifyName(_arg_service, _arg_name) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#VerifyName, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_VerifyName(_arg_service, _arg_name, _aidl_reply)
      }
    )
  }
  fn r#GetInterfaceArray<'a>(&'a self, _arg_names: &'a [String]) -> binder::BoxFuture<'a, binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> {
    let _aidl_data = match self.build_parcel_GetInterfaceArray(_arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetInterfaceArray(_arg_names, _aidl_reply)
      }
    )
  }
  fn r#VerifyNamesWithInterfaceArray<'a>(&'a self, _arg_services: &'a [binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &'a [String]) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_VerifyNamesWithInterfaceArray(_arg_services, _arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#VerifyNamesWithInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_VerifyNamesWithInterfaceArray(_arg_services, _arg_names, _aidl_reply)
      }
    )
  }
  fn r#GetNullableInterfaceArray<'a>(&'a self, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>> {
    let _aidl_data = match self.build_parcel_GetNullableInterfaceArray(_arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetNullableInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetNullableInterfaceArray(_arg_names, _aidl_reply)
      }
    )
  }
  fn r#VerifyNamesWithNullableInterfaceArray<'a>(&'a self, _arg_services: Option<&'a [Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#VerifyNamesWithNullableInterfaceArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names, _aidl_reply)
      }
    )
  }
  fn r#GetInterfaceList<'a>(&'a self, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>>> {
    let _aidl_data = match self.build_parcel_GetInterfaceList(_arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetInterfaceList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetInterfaceList(_arg_names, _aidl_reply)
      }
    )
  }
  fn r#VerifyNamesWithInterfaceList<'a>(&'a self, _arg_services: Option<&'a [Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<bool>> {
    let _aidl_data = match self.build_parcel_VerifyNamesWithInterfaceList(_arg_services, _arg_names) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#VerifyNamesWithInterfaceList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_VerifyNamesWithInterfaceList(_arg_services, _arg_names, _aidl_reply)
      }
    )
  }
  fn r#ReverseStringList<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>> {
    let _aidl_data = match self.build_parcel_ReverseStringList(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseStringList(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#RepeatParcelFileDescriptor<'a>(&'a self, _arg_read: &'a binder::ParcelFileDescriptor) -> binder::BoxFuture<'a, binder::Result<binder::ParcelFileDescriptor>> {
    let _aidl_data = match self.build_parcel_RepeatParcelFileDescriptor(_arg_read) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatParcelFileDescriptor, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatParcelFileDescriptor(_arg_read, _aidl_reply)
      }
    )
  }
  fn r#ReverseParcelFileDescriptorArray<'a>(&'a self, _arg_input: &'a [binder::ParcelFileDescriptor], _arg_repeated: &'a mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::BoxFuture<'a, binder::Result<Vec<binder::ParcelFileDescriptor>>> {
    let _aidl_data = match self.build_parcel_ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseParcelFileDescriptorArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ThrowServiceException<'a>(&'a self, _arg_code: i32) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_ThrowServiceException(_arg_code) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ThrowServiceException, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ThrowServiceException(_arg_code, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableIntArray<'a>(&'a self, _arg_input: Option<&'a [i32]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<i32>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableIntArray(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableIntArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableIntArray(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableByteEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableByteEnumArray(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableByteEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableByteEnumArray(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableIntEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableIntEnumArray(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableIntEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableIntEnumArray(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableLongEnumArray<'a>(&'a self, _arg_input: Option<&'a [crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableLongEnumArray(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableLongEnumArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableLongEnumArray(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableString<'a>(&'a self, _arg_input: Option<&'a str>) -> binder::BoxFuture<'a, binder::Result<Option<String>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableString(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableString(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableStringList<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableStringList(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableStringList(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableParcelable<'a>(&'a self, _arg_input: Option<&'a crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::BoxFuture<'a, binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableParcelable(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableParcelable(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableParcelableArray<'a>(&'a self, _arg_input: Option<&'a [Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableParcelableArray(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableParcelableArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableParcelableArray(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableParcelableList<'a>(&'a self, _arg_input: Option<&'a [Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableParcelableList(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableParcelableList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableParcelableList(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#TakesAnIBinder<'a>(&'a self, _arg_input: &'a binder::SpIBinder) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_TakesAnIBinder(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#TakesAnIBinder, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_TakesAnIBinder(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#TakesANullableIBinder<'a>(&'a self, _arg_input: Option<&'a binder::SpIBinder>) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_TakesANullableIBinder(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#TakesANullableIBinder, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_TakesANullableIBinder(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#TakesAnIBinderList<'a>(&'a self, _arg_input: &'a [binder::SpIBinder]) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_TakesAnIBinderList(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#TakesAnIBinderList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_TakesAnIBinderList(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#TakesANullableIBinderList<'a>(&'a self, _arg_input: Option<&'a [Option<binder::SpIBinder>]>) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_TakesANullableIBinderList(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#TakesANullableIBinderList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_TakesANullableIBinderList(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#RepeatUtf8CppString<'a>(&'a self, _arg_token: &'a str) -> binder::BoxFuture<'a, binder::Result<String>> {
    let _aidl_data = match self.build_parcel_RepeatUtf8CppString(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatUtf8CppString(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#RepeatNullableUtf8CppString<'a>(&'a self, _arg_token: Option<&'a str>) -> binder::BoxFuture<'a, binder::Result<Option<String>>> {
    let _aidl_data = match self.build_parcel_RepeatNullableUtf8CppString(_arg_token) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatNullableUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatNullableUtf8CppString(_arg_token, _aidl_reply)
      }
    )
  }
  fn r#ReverseUtf8CppString<'a>(&'a self, _arg_input: &'a [String], _arg_repeated: &'a mut Vec<String>) -> binder::BoxFuture<'a, binder::Result<Vec<String>>> {
    let _aidl_data = match self.build_parcel_ReverseUtf8CppString(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseUtf8CppString(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseNullableUtf8CppString<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>, _arg_repeated: &'a mut Option<Vec<Option<String>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>> {
    let _aidl_data = match self.build_parcel_ReverseNullableUtf8CppString(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseNullableUtf8CppString, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseNullableUtf8CppString(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseUtf8CppStringList<'a>(&'a self, _arg_input: Option<&'a [Option<String>]>, _arg_repeated: &'a mut Option<Vec<Option<String>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<String>>>>> {
    let _aidl_data = match self.build_parcel_ReverseUtf8CppStringList(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseUtf8CppStringList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseUtf8CppStringList(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#GetCallback<'a>(&'a self, _arg_return_null: bool) -> binder::BoxFuture<'a, binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> {
    let _aidl_data = match self.build_parcel_GetCallback(_arg_return_null) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetCallback, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetCallback(_arg_return_null, _aidl_reply)
      }
    )
  }
  fn r#FillOutStructuredParcelable<'a>(&'a self, _arg_parcel: &'a mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_FillOutStructuredParcelable(_arg_parcel) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#FillOutStructuredParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_FillOutStructuredParcelable(_arg_parcel, _aidl_reply)
      }
    )
  }
  fn r#RepeatExtendableParcelable<'a>(&'a self, _arg_ep: &'a crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &'a mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_RepeatExtendableParcelable(_arg_ep, _arg_ep2) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatExtendableParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatExtendableParcelable(_arg_ep, _arg_ep2, _aidl_reply)
      }
    )
  }
  fn r#ReverseList<'a>(&'a self, _arg_list: &'a crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList>> {
    let _aidl_data = match self.build_parcel_ReverseList(_arg_list) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseList, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseList(_arg_list, _aidl_reply)
      }
    )
  }
  fn r#ReverseIBinderArray<'a>(&'a self, _arg_input: &'a [binder::SpIBinder], _arg_repeated: &'a mut Vec<Option<binder::SpIBinder>>) -> binder::BoxFuture<'a, binder::Result<Vec<binder::SpIBinder>>> {
    let _aidl_data = match self.build_parcel_ReverseIBinderArray(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseIBinderArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseIBinderArray(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#ReverseNullableIBinderArray<'a>(&'a self, _arg_input: Option<&'a [Option<binder::SpIBinder>]>, _arg_repeated: &'a mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::BoxFuture<'a, binder::Result<Option<Vec<Option<binder::SpIBinder>>>>> {
    let _aidl_data = match self.build_parcel_ReverseNullableIBinderArray(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseNullableIBinderArray, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseNullableIBinderArray(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#RepeatSimpleParcelable<'a>(&'a self, _arg_input: &'a simple_parcelable::SimpleParcelable, _arg_repeat: &'a mut simple_parcelable::SimpleParcelable) -> binder::BoxFuture<'a, binder::Result<simple_parcelable::SimpleParcelable>> {
    let _aidl_data = match self.build_parcel_RepeatSimpleParcelable(_arg_input, _arg_repeat) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#RepeatSimpleParcelable, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_RepeatSimpleParcelable(_arg_input, _arg_repeat, _aidl_reply)
      }
    )
  }
  fn r#ReverseSimpleParcelables<'a>(&'a self, _arg_input: &'a [simple_parcelable::SimpleParcelable], _arg_repeated: &'a mut Vec<simple_parcelable::SimpleParcelable>) -> binder::BoxFuture<'a, binder::Result<Vec<simple_parcelable::SimpleParcelable>>> {
    let _aidl_data = match self.build_parcel_ReverseSimpleParcelables(_arg_input, _arg_repeated) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#ReverseSimpleParcelables, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_ReverseSimpleParcelables(_arg_input, _arg_repeated, _aidl_reply)
      }
    )
  }
  fn r#GetOldNameInterface<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>>> {
    let _aidl_data = match self.build_parcel_GetOldNameInterface() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetOldNameInterface, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetOldNameInterface(_aidl_reply)
      }
    )
  }
  fn r#GetNewNameInterface<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>>> {
    let _aidl_data = match self.build_parcel_GetNewNameInterface() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetNewNameInterface, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetNewNameInterface(_aidl_reply)
      }
    )
  }
  fn r#GetUnionTags<'a>(&'a self, _arg_input: &'a [crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::BoxFuture<'a, binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>>> {
    let _aidl_data = match self.build_parcel_GetUnionTags(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetUnionTags, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetUnionTags(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#GetCppJavaTests<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<Option<binder::SpIBinder>>> {
    let _aidl_data = match self.build_parcel_GetCppJavaTests() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetCppJavaTests, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetCppJavaTests(_aidl_reply)
      }
    )
  }
  fn r#getBackendType<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType>> {
    let _aidl_data = match self.build_parcel_getBackendType() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#getBackendType, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_getBackendType(_aidl_reply)
      }
    )
  }
  fn r#GetCircular<'a>(&'a self, _arg_cp: &'a mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::BoxFuture<'a, binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>>> {
    let _aidl_data = match self.build_parcel_GetCircular(_arg_cp) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetCircular, _aidl_data, binder::binder_impl::FLAG_CLEAR_BUF | binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetCircular(_arg_cp, _aidl_reply)
      }
    )
  }
}
impl ITestService for binder::binder_impl::Binder<BnTestService> {
  fn r#UnimplementedMethod(&self, _arg_arg: i32) -> binder::Result<i32> { self.0.r#UnimplementedMethod(_arg_arg) }
  fn r#Deprecated(&self) -> binder::Result<()> { self.0.r#Deprecated() }
  fn r#TestOneway(&self) -> binder::Result<()> { self.0.r#TestOneway() }
  fn r#RepeatBoolean(&self, _arg_token: bool) -> binder::Result<bool> { self.0.r#RepeatBoolean(_arg_token) }
  fn r#RepeatByte(&self, _arg_token: i8) -> binder::Result<i8> { self.0.r#RepeatByte(_arg_token) }
  fn r#RepeatChar(&self, _arg_token: u16) -> binder::Result<u16> { self.0.r#RepeatChar(_arg_token) }
  fn r#RepeatInt(&self, _arg_token: i32) -> binder::Result<i32> { self.0.r#RepeatInt(_arg_token) }
  fn r#RepeatLong(&self, _arg_token: i64) -> binder::Result<i64> { self.0.r#RepeatLong(_arg_token) }
  fn r#RepeatFloat(&self, _arg_token: f32) -> binder::Result<f32> { self.0.r#RepeatFloat(_arg_token) }
  fn r#RepeatDouble(&self, _arg_token: f64) -> binder::Result<f64> { self.0.r#RepeatDouble(_arg_token) }
  fn r#RepeatString(&self, _arg_token: &str) -> binder::Result<String> { self.0.r#RepeatString(_arg_token) }
  fn r#RepeatByteEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> { self.0.r#RepeatByteEnum(_arg_token) }
  fn r#RepeatIntEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> { self.0.r#RepeatIntEnum(_arg_token) }
  fn r#RepeatLongEnum(&self, _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> { self.0.r#RepeatLongEnum(_arg_token) }
  fn r#ReverseBoolean(&self, _arg_input: &[bool], _arg_repeated: &mut Vec<bool>) -> binder::Result<Vec<bool>> { self.0.r#ReverseBoolean(_arg_input, _arg_repeated) }
  fn r#ReverseByte(&self, _arg_input: &[u8], _arg_repeated: &mut Vec<u8>) -> binder::Result<Vec<u8>> { self.0.r#ReverseByte(_arg_input, _arg_repeated) }
  fn r#ReverseChar(&self, _arg_input: &[u16], _arg_repeated: &mut Vec<u16>) -> binder::Result<Vec<u16>> { self.0.r#ReverseChar(_arg_input, _arg_repeated) }
  fn r#ReverseInt(&self, _arg_input: &[i32], _arg_repeated: &mut Vec<i32>) -> binder::Result<Vec<i32>> { self.0.r#ReverseInt(_arg_input, _arg_repeated) }
  fn r#ReverseLong(&self, _arg_input: &[i64], _arg_repeated: &mut Vec<i64>) -> binder::Result<Vec<i64>> { self.0.r#ReverseLong(_arg_input, _arg_repeated) }
  fn r#ReverseFloat(&self, _arg_input: &[f32], _arg_repeated: &mut Vec<f32>) -> binder::Result<Vec<f32>> { self.0.r#ReverseFloat(_arg_input, _arg_repeated) }
  fn r#ReverseDouble(&self, _arg_input: &[f64], _arg_repeated: &mut Vec<f64>) -> binder::Result<Vec<f64>> { self.0.r#ReverseDouble(_arg_input, _arg_repeated) }
  fn r#ReverseString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> { self.0.r#ReverseString(_arg_input, _arg_repeated) }
  fn r#ReverseByteEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> { self.0.r#ReverseByteEnum(_arg_input, _arg_repeated) }
  fn r#ReverseIntEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> { self.0.r#ReverseIntEnum(_arg_input, _arg_repeated) }
  fn r#ReverseLongEnum(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum], _arg_repeated: &mut Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> { self.0.r#ReverseLongEnum(_arg_input, _arg_repeated) }
  fn r#GetOtherTestService(&self, _arg_name: &str) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> { self.0.r#GetOtherTestService(_arg_name) }
  fn r#SetOtherTestService(&self, _arg_name: &str, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>) -> binder::Result<bool> { self.0.r#SetOtherTestService(_arg_name, _arg_service) }
  fn r#VerifyName(&self, _arg_service: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>, _arg_name: &str) -> binder::Result<bool> { self.0.r#VerifyName(_arg_service, _arg_name) }
  fn r#GetInterfaceArray(&self, _arg_names: &[String]) -> binder::Result<Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> { self.0.r#GetInterfaceArray(_arg_names) }
  fn r#VerifyNamesWithInterfaceArray(&self, _arg_services: &[binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>], _arg_names: &[String]) -> binder::Result<bool> { self.0.r#VerifyNamesWithInterfaceArray(_arg_services, _arg_names) }
  fn r#GetNullableInterfaceArray(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> { self.0.r#GetNullableInterfaceArray(_arg_names) }
  fn r#VerifyNamesWithNullableInterfaceArray(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> { self.0.r#VerifyNamesWithNullableInterfaceArray(_arg_services, _arg_names) }
  fn r#GetInterfaceList(&self, _arg_names: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>>> { self.0.r#GetInterfaceList(_arg_names) }
  fn r#VerifyNamesWithInterfaceList(&self, _arg_services: Option<&[Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>]>, _arg_names: Option<&[Option<String>]>) -> binder::Result<bool> { self.0.r#VerifyNamesWithInterfaceList(_arg_services, _arg_names) }
  fn r#ReverseStringList(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> { self.0.r#ReverseStringList(_arg_input, _arg_repeated) }
  fn r#RepeatParcelFileDescriptor(&self, _arg_read: &binder::ParcelFileDescriptor) -> binder::Result<binder::ParcelFileDescriptor> { self.0.r#RepeatParcelFileDescriptor(_arg_read) }
  fn r#ReverseParcelFileDescriptorArray(&self, _arg_input: &[binder::ParcelFileDescriptor], _arg_repeated: &mut Vec<Option<binder::ParcelFileDescriptor>>) -> binder::Result<Vec<binder::ParcelFileDescriptor>> { self.0.r#ReverseParcelFileDescriptorArray(_arg_input, _arg_repeated) }
  fn r#ThrowServiceException(&self, _arg_code: i32) -> binder::Result<()> { self.0.r#ThrowServiceException(_arg_code) }
  fn r#RepeatNullableIntArray(&self, _arg_input: Option<&[i32]>) -> binder::Result<Option<Vec<i32>>> { self.0.r#RepeatNullableIntArray(_arg_input) }
  fn r#RepeatNullableByteEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>>> { self.0.r#RepeatNullableByteEnumArray(_arg_input) }
  fn r#RepeatNullableIntEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>>> { self.0.r#RepeatNullableIntEnumArray(_arg_input) }
  fn r#RepeatNullableLongEnumArray(&self, _arg_input: Option<&[crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum]>) -> binder::Result<Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>>> { self.0.r#RepeatNullableLongEnumArray(_arg_input) }
  fn r#RepeatNullableString(&self, _arg_input: Option<&str>) -> binder::Result<Option<String>> { self.0.r#RepeatNullableString(_arg_input) }
  fn r#RepeatNullableStringList(&self, _arg_input: Option<&[Option<String>]>) -> binder::Result<Option<Vec<Option<String>>>> { self.0.r#RepeatNullableStringList(_arg_input) }
  fn r#RepeatNullableParcelable(&self, _arg_input: Option<&crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>) -> binder::Result<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>> { self.0.r#RepeatNullableParcelable(_arg_input) }
  fn r#RepeatNullableParcelableArray(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> { self.0.r#RepeatNullableParcelableArray(_arg_input) }
  fn r#RepeatNullableParcelableList(&self, _arg_input: Option<&[Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>]>) -> binder::Result<Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>> { self.0.r#RepeatNullableParcelableList(_arg_input) }
  fn r#TakesAnIBinder(&self, _arg_input: &binder::SpIBinder) -> binder::Result<()> { self.0.r#TakesAnIBinder(_arg_input) }
  fn r#TakesANullableIBinder(&self, _arg_input: Option<&binder::SpIBinder>) -> binder::Result<()> { self.0.r#TakesANullableIBinder(_arg_input) }
  fn r#TakesAnIBinderList(&self, _arg_input: &[binder::SpIBinder]) -> binder::Result<()> { self.0.r#TakesAnIBinderList(_arg_input) }
  fn r#TakesANullableIBinderList(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>) -> binder::Result<()> { self.0.r#TakesANullableIBinderList(_arg_input) }
  fn r#RepeatUtf8CppString(&self, _arg_token: &str) -> binder::Result<String> { self.0.r#RepeatUtf8CppString(_arg_token) }
  fn r#RepeatNullableUtf8CppString(&self, _arg_token: Option<&str>) -> binder::Result<Option<String>> { self.0.r#RepeatNullableUtf8CppString(_arg_token) }
  fn r#ReverseUtf8CppString(&self, _arg_input: &[String], _arg_repeated: &mut Vec<String>) -> binder::Result<Vec<String>> { self.0.r#ReverseUtf8CppString(_arg_input, _arg_repeated) }
  fn r#ReverseNullableUtf8CppString(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> { self.0.r#ReverseNullableUtf8CppString(_arg_input, _arg_repeated) }
  fn r#ReverseUtf8CppStringList(&self, _arg_input: Option<&[Option<String>]>, _arg_repeated: &mut Option<Vec<Option<String>>>) -> binder::Result<Option<Vec<Option<String>>>> { self.0.r#ReverseUtf8CppStringList(_arg_input, _arg_repeated) }
  fn r#GetCallback(&self, _arg_return_null: bool) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>> { self.0.r#GetCallback(_arg_return_null) }
  fn r#FillOutStructuredParcelable(&self, _arg_parcel: &mut crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable) -> binder::Result<()> { self.0.r#FillOutStructuredParcelable(_arg_parcel) }
  fn r#RepeatExtendableParcelable(&self, _arg_ep: &crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable, _arg_ep2: &mut crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable) -> binder::Result<()> { self.0.r#RepeatExtendableParcelable(_arg_ep, _arg_ep2) }
  fn r#ReverseList(&self, _arg_list: &crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList> { self.0.r#ReverseList(_arg_list) }
  fn r#ReverseIBinderArray(&self, _arg_input: &[binder::SpIBinder], _arg_repeated: &mut Vec<Option<binder::SpIBinder>>) -> binder::Result<Vec<binder::SpIBinder>> { self.0.r#ReverseIBinderArray(_arg_input, _arg_repeated) }
  fn r#ReverseNullableIBinderArray(&self, _arg_input: Option<&[Option<binder::SpIBinder>]>, _arg_repeated: &mut Option<Vec<Option<binder::SpIBinder>>>) -> binder::Result<Option<Vec<Option<binder::SpIBinder>>>> { self.0.r#ReverseNullableIBinderArray(_arg_input, _arg_repeated) }
  fn r#RepeatSimpleParcelable(&self, _arg_input: &simple_parcelable::SimpleParcelable, _arg_repeat: &mut simple_parcelable::SimpleParcelable) -> binder::Result<simple_parcelable::SimpleParcelable> { self.0.r#RepeatSimpleParcelable(_arg_input, _arg_repeat) }
  fn r#ReverseSimpleParcelables(&self, _arg_input: &[simple_parcelable::SimpleParcelable], _arg_repeated: &mut Vec<simple_parcelable::SimpleParcelable>) -> binder::Result<Vec<simple_parcelable::SimpleParcelable>> { self.0.r#ReverseSimpleParcelables(_arg_input, _arg_repeated) }
  fn r#GetOldNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_IOldName>> { self.0.r#GetOldNameInterface() }
  fn r#GetNewNameInterface(&self) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_8_INewName>> { self.0.r#GetNewNameInterface() }
  fn r#GetUnionTags(&self, _arg_input: &[crate::mangled::_7_android_4_aidl_5_tests_5_Union]) -> binder::Result<Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union_3_Tag>> { self.0.r#GetUnionTags(_arg_input) }
  fn r#GetCppJavaTests(&self) -> binder::Result<Option<binder::SpIBinder>> { self.0.r#GetCppJavaTests() }
  fn r#getBackendType(&self) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_11_BackendType> { self.0.r#getBackendType() }
  fn r#GetCircular(&self, _arg_cp: &mut crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable) -> binder::Result<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_9_ICircular>> { self.0.r#GetCircular(_arg_cp) }
}
fn on_transact(_aidl_service: &dyn ITestService, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
  match _aidl_code {
    transactions::r#UnimplementedMethod => {
      let _arg_arg: i32 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#UnimplementedMethod(_arg_arg);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#Deprecated => {
      let _aidl_return = _aidl_service.r#Deprecated();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#TestOneway => {
      let _aidl_return = _aidl_service.r#TestOneway();
      Ok(())
    }
    transactions::r#RepeatBoolean => {
      let _arg_token: bool = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatBoolean(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatByte => {
      let _arg_token: i8 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatByte(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatChar => {
      let _arg_token: u16 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatChar(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatInt => {
      let _arg_token: i32 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatInt(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatLong => {
      let _arg_token: i64 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatLong(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatFloat => {
      let _arg_token: f32 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatFloat(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatDouble => {
      let _arg_token: f64 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatDouble(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatString => {
      let _arg_token: String = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatString(&_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatByteEnum => {
      let _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatByteEnum(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatIntEnum => {
      let _arg_token: crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatIntEnum(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatLongEnum => {
      let _arg_token: crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatLongEnum(_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseBoolean => {
      let _arg_input: Vec<bool> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<bool> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseBoolean(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseByte => {
      let _arg_input: Vec<u8> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<u8> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseByte(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseChar => {
      let _arg_input: Vec<u16> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<u16> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseChar(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseInt => {
      let _arg_input: Vec<i32> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<i32> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseInt(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseLong => {
      let _arg_input: Vec<i64> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<i64> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseLong(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseFloat => {
      let _arg_input: Vec<f32> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<f32> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseFloat(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseDouble => {
      let _arg_input: Vec<f64> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<f64> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseDouble(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseString => {
      let _arg_input: Vec<String> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<String> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseString(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseByteEnum => {
      let _arg_input: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseByteEnum(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseIntEnum => {
      let _arg_input: Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseIntEnum(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseLongEnum => {
      let _arg_input: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseLongEnum(&_arg_input, &mut _arg_repeated);
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
    transactions::r#GetOtherTestService => {
      let _arg_name: String = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetOtherTestService(&_arg_name);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#SetOtherTestService => {
      let _arg_name: String = _aidl_data.read()?;
      let _arg_service: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#SetOtherTestService(&_arg_name, &_arg_service);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#VerifyName => {
      let _arg_service: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback> = _aidl_data.read()?;
      let _arg_name: String = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#VerifyName(&_arg_service, &_arg_name);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetInterfaceArray => {
      let _arg_names: Vec<String> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetInterfaceArray(&_arg_names);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#VerifyNamesWithInterfaceArray => {
      let _arg_services: Vec<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>> = _aidl_data.read()?;
      let _arg_names: Vec<String> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#VerifyNamesWithInterfaceArray(&_arg_services, &_arg_names);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetNullableInterfaceArray => {
      let _arg_names: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetNullableInterfaceArray(_arg_names.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#VerifyNamesWithNullableInterfaceArray => {
      let _arg_services: Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> = _aidl_data.read()?;
      let _arg_names: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#VerifyNamesWithNullableInterfaceArray(_arg_services.as_deref(), _arg_names.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetInterfaceList => {
      let _arg_names: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetInterfaceList(_arg_names.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#VerifyNamesWithInterfaceList => {
      let _arg_services: Option<Vec<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_14_INamedCallback>>>> = _aidl_data.read()?;
      let _arg_names: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#VerifyNamesWithInterfaceList(_arg_services.as_deref(), _arg_names.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseStringList => {
      let _arg_input: Vec<String> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<String> = Default::default();
      let _aidl_return = _aidl_service.r#ReverseStringList(&_arg_input, &mut _arg_repeated);
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
    transactions::r#RepeatParcelFileDescriptor => {
      let _arg_read: binder::ParcelFileDescriptor = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatParcelFileDescriptor(&_arg_read);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseParcelFileDescriptorArray => {
      let _arg_input: Vec<binder::ParcelFileDescriptor> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<Option<binder::ParcelFileDescriptor>> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseParcelFileDescriptorArray(&_arg_input, &mut _arg_repeated);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
          if _arg_repeated.iter().any(Option::is_none) { return Err(binder::StatusCode::UNEXPECTED_NULL); }
          _aidl_reply.write(&_arg_repeated)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ThrowServiceException => {
      let _arg_code: i32 = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#ThrowServiceException(_arg_code);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableIntArray => {
      let _arg_input: Option<Vec<i32>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableIntArray(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableByteEnumArray => {
      let _arg_input: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_ByteEnum>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableByteEnumArray(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableIntEnumArray => {
      let _arg_input: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_7_IntEnum>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableIntEnumArray(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableLongEnumArray => {
      let _arg_input: Option<Vec<crate::mangled::_7_android_4_aidl_5_tests_8_LongEnum>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableLongEnumArray(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableString => {
      let _arg_input: Option<String> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableString(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableStringList => {
      let _arg_input: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableStringList(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableParcelable => {
      let _arg_input: Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableParcelable(_arg_input.as_ref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableParcelableArray => {
      let _arg_input: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableParcelableArray(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableParcelableList => {
      let _arg_input: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableParcelableList(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#TakesAnIBinder => {
      let _arg_input: binder::SpIBinder = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#TakesAnIBinder(&_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#TakesANullableIBinder => {
      let _arg_input: Option<binder::SpIBinder> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#TakesANullableIBinder(_arg_input.as_ref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#TakesAnIBinderList => {
      let _arg_input: Vec<binder::SpIBinder> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#TakesAnIBinderList(&_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#TakesANullableIBinderList => {
      let _arg_input: Option<Vec<Option<binder::SpIBinder>>> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#TakesANullableIBinderList(_arg_input.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatUtf8CppString => {
      let _arg_token: String = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatUtf8CppString(&_arg_token);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatNullableUtf8CppString => {
      let _arg_token: Option<String> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#RepeatNullableUtf8CppString(_arg_token.as_deref());
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseUtf8CppString => {
      let _arg_input: Vec<String> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<String> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseUtf8CppString(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseNullableUtf8CppString => {
      let _arg_input: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let mut _arg_repeated: Option<Vec<Option<String>>> = Default::default();
      _aidl_data.resize_nullable_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseNullableUtf8CppString(_arg_input.as_deref(), &mut _arg_repeated);
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
    transactions::r#ReverseUtf8CppStringList => {
      let _arg_input: Option<Vec<Option<String>>> = _aidl_data.read()?;
      let mut _arg_repeated: Option<Vec<Option<String>>> = Default::default();
      let _aidl_return = _aidl_service.r#ReverseUtf8CppStringList(_arg_input.as_deref(), &mut _arg_repeated);
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
    transactions::r#GetCallback => {
      let _arg_return_null: bool = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetCallback(_arg_return_null);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#FillOutStructuredParcelable => {
      let mut _arg_parcel: crate::mangled::_7_android_4_aidl_5_tests_20_StructuredParcelable = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#FillOutStructuredParcelable(&mut _arg_parcel);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(&_arg_parcel)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#RepeatExtendableParcelable => {
      let _arg_ep: crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable = _aidl_data.read()?;
      let mut _arg_ep2: crate::mangled::_7_android_4_aidl_5_tests_9_extension_20_ExtendableParcelable = Default::default();
      let _aidl_return = _aidl_service.r#RepeatExtendableParcelable(&_arg_ep, &mut _arg_ep2);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(&_arg_ep2)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseList => {
      let _arg_list: crate::mangled::_7_android_4_aidl_5_tests_13_RecursiveList = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#ReverseList(&_arg_list);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseIBinderArray => {
      let _arg_input: Vec<binder::SpIBinder> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<Option<binder::SpIBinder>> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseIBinderArray(&_arg_input, &mut _arg_repeated);
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
    transactions::r#ReverseNullableIBinderArray => {
      let _arg_input: Option<Vec<Option<binder::SpIBinder>>> = _aidl_data.read()?;
      let mut _arg_repeated: Option<Vec<Option<binder::SpIBinder>>> = Default::default();
      _aidl_data.resize_nullable_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseNullableIBinderArray(_arg_input.as_deref(), &mut _arg_repeated);
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
    transactions::r#RepeatSimpleParcelable => {
      let _arg_input: simple_parcelable::SimpleParcelable = _aidl_data.read()?;
      let mut _arg_repeat: simple_parcelable::SimpleParcelable = Default::default();
      let _aidl_return = _aidl_service.r#RepeatSimpleParcelable(&_arg_input, &mut _arg_repeat);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
          _aidl_reply.write(&_arg_repeat)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#ReverseSimpleParcelables => {
      let _arg_input: Vec<simple_parcelable::SimpleParcelable> = _aidl_data.read()?;
      let mut _arg_repeated: Vec<simple_parcelable::SimpleParcelable> = Default::default();
      _aidl_data.resize_out_vec(&mut _arg_repeated)?;
      let _aidl_return = _aidl_service.r#ReverseSimpleParcelables(&_arg_input, &mut _arg_repeated);
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
    transactions::r#GetOldNameInterface => {
      let _aidl_return = _aidl_service.r#GetOldNameInterface();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetNewNameInterface => {
      let _aidl_return = _aidl_service.r#GetNewNameInterface();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetUnionTags => {
      let _arg_input: Vec<crate::mangled::_7_android_4_aidl_5_tests_5_Union> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#GetUnionTags(&_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetCppJavaTests => {
      let _aidl_return = _aidl_service.r#GetCppJavaTests();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#getBackendType => {
      let _aidl_return = _aidl_service.r#getBackendType();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#GetCircular => {
      let mut _arg_cp: crate::mangled::_7_android_4_aidl_5_tests_18_CircularParcelable = Default::default();
      let _aidl_return = _aidl_service.r#GetCircular(&mut _arg_cp);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
          _aidl_reply.write(&_arg_cp)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
  }
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
    fn get_descriptor() -> &'static str { "android.aidl.tests.ITestService.Empty" }
  }
}
pub mod r#CompilerChecks {
  #[derive(Debug)]
  pub struct r#CompilerChecks {
    pub r#binder: Option<binder::SpIBinder>,
    pub r#nullable_binder: Option<binder::SpIBinder>,
    pub r#binder_array: Vec<binder::SpIBinder>,
    pub r#nullable_binder_array: Option<Vec<Option<binder::SpIBinder>>>,
    pub r#binder_list: Vec<binder::SpIBinder>,
    pub r#nullable_binder_list: Option<Vec<Option<binder::SpIBinder>>>,
    pub r#pfd: Option<binder::ParcelFileDescriptor>,
    pub r#nullable_pfd: Option<binder::ParcelFileDescriptor>,
    pub r#pfd_array: Vec<binder::ParcelFileDescriptor>,
    pub r#nullable_pfd_array: Option<Vec<Option<binder::ParcelFileDescriptor>>>,
    pub r#pfd_list: Vec<binder::ParcelFileDescriptor>,
    pub r#nullable_pfd_list: Option<Vec<Option<binder::ParcelFileDescriptor>>>,
    pub r#parcel: crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty,
    pub r#nullable_parcel: Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>,
    pub r#parcel_array: Vec<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>,
    pub r#nullable_parcel_array: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>,
    pub r#parcel_list: Vec<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>,
    pub r#nullable_parcel_list: Option<Vec<Option<crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_5_Empty>>>,
  }
  impl Default for r#CompilerChecks {
    fn default() -> Self {
      Self {
        r#binder: Default::default(),
        r#nullable_binder: Default::default(),
        r#binder_array: Default::default(),
        r#nullable_binder_array: Default::default(),
        r#binder_list: Default::default(),
        r#nullable_binder_list: Default::default(),
        r#pfd: Default::default(),
        r#nullable_pfd: Default::default(),
        r#pfd_array: Default::default(),
        r#nullable_pfd_array: Default::default(),
        r#pfd_list: Default::default(),
        r#nullable_pfd_list: Default::default(),
        r#parcel: Default::default(),
        r#nullable_parcel: Default::default(),
        r#parcel_array: Default::default(),
        r#nullable_parcel_array: Default::default(),
        r#parcel_list: Default::default(),
        r#nullable_parcel_list: Default::default(),
      }
    }
  }
  impl binder::Parcelable for r#CompilerChecks {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_write(|subparcel| {
        let __field_ref = self.r#binder.as_ref().ok_or(binder::StatusCode::UNEXPECTED_NULL)?;
        subparcel.write(__field_ref)?;
        subparcel.write(&self.r#nullable_binder)?;
        subparcel.write(&self.r#binder_array)?;
        subparcel.write(&self.r#nullable_binder_array)?;
        subparcel.write(&self.r#binder_list)?;
        subparcel.write(&self.r#nullable_binder_list)?;
        let __field_ref = self.r#pfd.as_ref().ok_or(binder::StatusCode::UNEXPECTED_NULL)?;
        subparcel.write(__field_ref)?;
        subparcel.write(&self.r#nullable_pfd)?;
        subparcel.write(&self.r#pfd_array)?;
        subparcel.write(&self.r#nullable_pfd_array)?;
        subparcel.write(&self.r#pfd_list)?;
        subparcel.write(&self.r#nullable_pfd_list)?;
        subparcel.write(&self.r#parcel)?;
        subparcel.write(&self.r#nullable_parcel)?;
        subparcel.write(&self.r#parcel_array)?;
        subparcel.write(&self.r#nullable_parcel_array)?;
        subparcel.write(&self.r#parcel_list)?;
        subparcel.write(&self.r#nullable_parcel_list)?;
        Ok(())
      })
    }
    fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_read(|subparcel| {
        if subparcel.has_more_data() {
          self.r#binder = Some(subparcel.read()?);
        }
        if subparcel.has_more_data() {
          self.r#nullable_binder = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#binder_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_binder_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#binder_list = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_binder_list = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#pfd = Some(subparcel.read()?);
        }
        if subparcel.has_more_data() {
          self.r#nullable_pfd = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#pfd_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_pfd_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#pfd_list = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_pfd_list = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#parcel = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_parcel = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#parcel_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_parcel_array = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#parcel_list = subparcel.read()?;
        }
        if subparcel.has_more_data() {
          self.r#nullable_parcel_list = subparcel.read()?;
        }
        Ok(())
      })
    }
  }
  binder::impl_serialize_for_parcelable!(r#CompilerChecks);
  binder::impl_deserialize_for_parcelable!(r#CompilerChecks);
  impl binder::binder_impl::ParcelableMetadata for r#CompilerChecks {
    fn get_descriptor() -> &'static str { "android.aidl.tests.ITestService.CompilerChecks" }
  }
  pub mod r#Foo {
    #![allow(non_upper_case_globals)]
    #![allow(non_snake_case)]
    #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
    use binder::declare_binder_interface;
    declare_binder_interface! {
      IFoo["android.aidl.tests.ITestService.CompilerChecks.Foo"] {
        native: BnFoo(on_transact),
        proxy: BpFoo {
        },
        async: IFooAsync,
      }
    }
    pub trait IFoo: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.Foo" }
      fn getDefaultImpl() -> IFooDefaultRef where Self: Sized {
        DEFAULT_IMPL.lock().unwrap().clone()
      }
      fn setDefaultImpl(d: IFooDefaultRef) -> IFooDefaultRef where Self: Sized {
        std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
      }
    }
    pub trait IFooAsync<P>: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.Foo" }
    }
    #[::async_trait::async_trait]
    pub trait IFooAsyncServer: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.Foo" }
    }
    impl BnFoo {
      /// Create a new async binder service.
      pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn IFoo>
      where
        T: IFooAsyncServer + binder::Interface + Send + Sync + 'static,
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
        impl<T, R> IFoo for Wrapper<T, R>
        where
          T: IFooAsyncServer + Send + Sync + 'static,
          R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
        {
        }
        let wrapped = Wrapper { _inner: inner, _rt: rt };
        Self::new_binder(wrapped, features)
      }
    }
    pub trait IFooDefault: Send + Sync {
    }
    pub mod transactions {
    }
    pub type IFooDefaultRef = Option<std::sync::Arc<dyn IFooDefault>>;
    static DEFAULT_IMPL: std::sync::Mutex<IFooDefaultRef> = std::sync::Mutex::new(None);
    impl BpFoo {
    }
    impl IFoo for BpFoo {
    }
    impl<P: binder::BinderAsyncPool> IFooAsync<P> for BpFoo {
    }
    impl IFoo for binder::binder_impl::Binder<BnFoo> {
    }
    fn on_transact(_aidl_service: &dyn IFoo, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
      match _aidl_code {
        _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
      }
    }
  }
  pub mod r#HasDeprecated {
    #[derive(Debug)]
    pub struct r#HasDeprecated {
      #[deprecated = "field"]
      pub r#deprecated: i32,
    }
    impl Default for r#HasDeprecated {
      fn default() -> Self {
        Self {
          r#deprecated: 0,
        }
      }
    }
    impl binder::Parcelable for r#HasDeprecated {
      fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
        parcel.sized_write(|subparcel| {
          subparcel.write(&self.r#deprecated)?;
          Ok(())
        })
      }
      fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
        parcel.sized_read(|subparcel| {
          if subparcel.has_more_data() {
            self.r#deprecated = subparcel.read()?;
          }
          Ok(())
        })
      }
    }
    binder::impl_serialize_for_parcelable!(r#HasDeprecated);
    binder::impl_deserialize_for_parcelable!(r#HasDeprecated);
    impl binder::binder_impl::ParcelableMetadata for r#HasDeprecated {
      fn get_descriptor() -> &'static str { "android.aidl.tests.ITestService.CompilerChecks.HasDeprecated" }
    }
  }
  pub mod r#UsingHasDeprecated {
    #[derive(Debug)]
    pub enum r#UsingHasDeprecated {
      N(i32),
      M(crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_13_HasDeprecated),
    }
    impl Default for r#UsingHasDeprecated {
      fn default() -> Self {
        Self::N(0)
      }
    }
    impl binder::Parcelable for r#UsingHasDeprecated {
      fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
        match self {
          Self::N(v) => {
            parcel.write(&0i32)?;
            parcel.write(v)
          }
          Self::M(v) => {
            parcel.write(&1i32)?;
            parcel.write(v)
          }
        }
      }
      fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
        let tag: i32 = parcel.read()?;
        match tag {
          0 => {
            let value: i32 = parcel.read()?;
            *self = Self::N(value);
            Ok(())
          }
          1 => {
            let value: crate::mangled::_7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_13_HasDeprecated = parcel.read()?;
            *self = Self::M(value);
            Ok(())
          }
          _ => {
            Err(binder::StatusCode::BAD_VALUE)
          }
        }
      }
    }
    binder::impl_serialize_for_parcelable!(r#UsingHasDeprecated);
    binder::impl_deserialize_for_parcelable!(r#UsingHasDeprecated);
    impl binder::binder_impl::ParcelableMetadata for r#UsingHasDeprecated {
      fn get_descriptor() -> &'static str { "android.aidl.tests.ITestService.CompilerChecks.UsingHasDeprecated" }
    }
    pub mod r#Tag {
      #![allow(non_upper_case_globals)]
      use binder::declare_binder_enum;
      declare_binder_enum! {
        r#Tag : [i32; 2] {
          r#n = 0,
          r#m = 1,
        }
      }
    }
  }
  pub mod r#NoPrefixInterface {
    #![allow(non_upper_case_globals)]
    #![allow(non_snake_case)]
    #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
    use binder::declare_binder_interface;
    declare_binder_interface! {
      INoPrefixInterface["android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface"] {
        native: BnNoPrefixInterface(on_transact),
        proxy: BpNoPrefixInterface {
        },
        async: INoPrefixInterfaceAsync,
      }
    }
    pub trait INoPrefixInterface: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface" }
      fn r#foo(&self) -> binder::Result<()>;
      fn getDefaultImpl() -> INoPrefixInterfaceDefaultRef where Self: Sized {
        DEFAULT_IMPL.lock().unwrap().clone()
      }
      fn setDefaultImpl(d: INoPrefixInterfaceDefaultRef) -> INoPrefixInterfaceDefaultRef where Self: Sized {
        std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
      }
    }
    pub trait INoPrefixInterfaceAsync<P>: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface" }
      fn r#foo<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>>;
    }
    #[::async_trait::async_trait]
    pub trait INoPrefixInterfaceAsyncServer: binder::Interface + Send {
      fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface" }
      async fn r#foo(&self) -> binder::Result<()>;
    }
    impl BnNoPrefixInterface {
      /// Create a new async binder service.
      pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn INoPrefixInterface>
      where
        T: INoPrefixInterfaceAsyncServer + binder::Interface + Send + Sync + 'static,
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
        impl<T, R> INoPrefixInterface for Wrapper<T, R>
        where
          T: INoPrefixInterfaceAsyncServer + Send + Sync + 'static,
          R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
        {
          fn r#foo(&self) -> binder::Result<()> {
            self._rt.block_on(self._inner.r#foo())
          }
        }
        let wrapped = Wrapper { _inner: inner, _rt: rt };
        Self::new_binder(wrapped, features)
      }
    }
    pub trait INoPrefixInterfaceDefault: Send + Sync {
      fn r#foo(&self) -> binder::Result<()> {
        Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
      }
    }
    pub mod transactions {
      pub const r#foo: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
    }
    pub type INoPrefixInterfaceDefaultRef = Option<std::sync::Arc<dyn INoPrefixInterfaceDefault>>;
    static DEFAULT_IMPL: std::sync::Mutex<INoPrefixInterfaceDefaultRef> = std::sync::Mutex::new(None);
    impl BpNoPrefixInterface {
      fn build_parcel_foo(&self) -> binder::Result<binder::binder_impl::Parcel> {
        let mut aidl_data = self.binder.prepare_transact()?;
        Ok(aidl_data)
      }
      fn read_response_foo(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
        if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
          if let Some(_aidl_default_impl) = <Self as INoPrefixInterface>::getDefaultImpl() {
            return _aidl_default_impl.r#foo();
          }
        }
        let _aidl_reply = _aidl_reply?;
        let _aidl_status: binder::Status = _aidl_reply.read()?;
        if !_aidl_status.is_ok() { return Err(_aidl_status); }
        Ok(())
      }
    }
    impl INoPrefixInterface for BpNoPrefixInterface {
      fn r#foo(&self) -> binder::Result<()> {
        let _aidl_data = self.build_parcel_foo()?;
        let _aidl_reply = self.binder.submit_transact(transactions::r#foo, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
        self.read_response_foo(_aidl_reply)
      }
    }
    impl<P: binder::BinderAsyncPool> INoPrefixInterfaceAsync<P> for BpNoPrefixInterface {
      fn r#foo<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>> {
        let _aidl_data = match self.build_parcel_foo() {
          Ok(_aidl_data) => _aidl_data,
          Err(err) => return Box::pin(std::future::ready(Err(err))),
        };
        let binder = self.binder.clone();
        P::spawn(
          move || binder.submit_transact(transactions::r#foo, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
          move |_aidl_reply| async move {
            self.read_response_foo(_aidl_reply)
          }
        )
      }
    }
    impl INoPrefixInterface for binder::binder_impl::Binder<BnNoPrefixInterface> {
      fn r#foo(&self) -> binder::Result<()> { self.0.r#foo() }
    }
    fn on_transact(_aidl_service: &dyn INoPrefixInterface, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
      match _aidl_code {
        transactions::r#foo => {
          let _aidl_return = _aidl_service.r#foo();
          match &_aidl_return {
            Ok(_aidl_return) => {
              _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            }
            Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
          }
          Ok(())
        }
        _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
      }
    }
    pub mod r#Nested {
      #[derive(Debug)]
      pub struct r#Nested {
      }
      impl Default for r#Nested {
        fn default() -> Self {
          Self {
          }
        }
      }
      impl binder::Parcelable for r#Nested {
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
      binder::impl_serialize_for_parcelable!(r#Nested);
      binder::impl_deserialize_for_parcelable!(r#Nested);
      impl binder::binder_impl::ParcelableMetadata for r#Nested {
        fn get_descriptor() -> &'static str { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.Nested" }
      }
    }
    pub mod r#NestedNoPrefixInterface {
      #![allow(non_upper_case_globals)]
      #![allow(non_snake_case)]
      #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
      use binder::declare_binder_interface;
      declare_binder_interface! {
        INestedNoPrefixInterface["android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface"] {
          native: BnNestedNoPrefixInterface(on_transact),
          proxy: BpNestedNoPrefixInterface {
          },
          async: INestedNoPrefixInterfaceAsync,
        }
      }
      pub trait INestedNoPrefixInterface: binder::Interface + Send {
        fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface" }
        fn r#foo(&self) -> binder::Result<()>;
        fn getDefaultImpl() -> INestedNoPrefixInterfaceDefaultRef where Self: Sized {
          DEFAULT_IMPL.lock().unwrap().clone()
        }
        fn setDefaultImpl(d: INestedNoPrefixInterfaceDefaultRef) -> INestedNoPrefixInterfaceDefaultRef where Self: Sized {
          std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
        }
      }
      pub trait INestedNoPrefixInterfaceAsync<P>: binder::Interface + Send {
        fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface" }
        fn r#foo<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>>;
      }
      #[::async_trait::async_trait]
      pub trait INestedNoPrefixInterfaceAsyncServer: binder::Interface + Send {
        fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface" }
        async fn r#foo(&self) -> binder::Result<()>;
      }
      impl BnNestedNoPrefixInterface {
        /// Create a new async binder service.
        pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn INestedNoPrefixInterface>
        where
          T: INestedNoPrefixInterfaceAsyncServer + binder::Interface + Send + Sync + 'static,
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
          impl<T, R> INestedNoPrefixInterface for Wrapper<T, R>
          where
            T: INestedNoPrefixInterfaceAsyncServer + Send + Sync + 'static,
            R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
          {
            fn r#foo(&self) -> binder::Result<()> {
              self._rt.block_on(self._inner.r#foo())
            }
          }
          let wrapped = Wrapper { _inner: inner, _rt: rt };
          Self::new_binder(wrapped, features)
        }
      }
      pub trait INestedNoPrefixInterfaceDefault: Send + Sync {
        fn r#foo(&self) -> binder::Result<()> {
          Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
        }
      }
      pub mod transactions {
        pub const r#foo: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
      }
      pub type INestedNoPrefixInterfaceDefaultRef = Option<std::sync::Arc<dyn INestedNoPrefixInterfaceDefault>>;
      static DEFAULT_IMPL: std::sync::Mutex<INestedNoPrefixInterfaceDefaultRef> = std::sync::Mutex::new(None);
      impl BpNestedNoPrefixInterface {
        fn build_parcel_foo(&self) -> binder::Result<binder::binder_impl::Parcel> {
          let mut aidl_data = self.binder.prepare_transact()?;
          Ok(aidl_data)
        }
        fn read_response_foo(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
          if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
            if let Some(_aidl_default_impl) = <Self as INestedNoPrefixInterface>::getDefaultImpl() {
              return _aidl_default_impl.r#foo();
            }
          }
          let _aidl_reply = _aidl_reply?;
          let _aidl_status: binder::Status = _aidl_reply.read()?;
          if !_aidl_status.is_ok() { return Err(_aidl_status); }
          Ok(())
        }
      }
      impl INestedNoPrefixInterface for BpNestedNoPrefixInterface {
        fn r#foo(&self) -> binder::Result<()> {
          let _aidl_data = self.build_parcel_foo()?;
          let _aidl_reply = self.binder.submit_transact(transactions::r#foo, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
          self.read_response_foo(_aidl_reply)
        }
      }
      impl<P: binder::BinderAsyncPool> INestedNoPrefixInterfaceAsync<P> for BpNestedNoPrefixInterface {
        fn r#foo<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<()>> {
          let _aidl_data = match self.build_parcel_foo() {
            Ok(_aidl_data) => _aidl_data,
            Err(err) => return Box::pin(std::future::ready(Err(err))),
          };
          let binder = self.binder.clone();
          P::spawn(
            move || binder.submit_transact(transactions::r#foo, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
            move |_aidl_reply| async move {
              self.read_response_foo(_aidl_reply)
            }
          )
        }
      }
      impl INestedNoPrefixInterface for binder::binder_impl::Binder<BnNestedNoPrefixInterface> {
        fn r#foo(&self) -> binder::Result<()> { self.0.r#foo() }
      }
      fn on_transact(_aidl_service: &dyn INestedNoPrefixInterface, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
        match _aidl_code {
          transactions::r#foo => {
            let _aidl_return = _aidl_service.r#foo();
            match &_aidl_return {
              Ok(_aidl_return) => {
                _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
              }
              Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
            }
            Ok(())
          }
          _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
        }
      }
    }
  }
}
pub(crate) mod mangled {
 pub use super::r#ITestService as _7_android_4_aidl_5_tests_12_ITestService;
 pub use super::r#Empty::r#Empty as _7_android_4_aidl_5_tests_12_ITestService_5_Empty;
 pub use super::r#CompilerChecks::r#CompilerChecks as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks;
 pub use super::r#CompilerChecks::r#Foo::r#IFoo as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_3_Foo;
 pub use super::r#CompilerChecks::r#HasDeprecated::r#HasDeprecated as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_13_HasDeprecated;
 pub use super::r#CompilerChecks::r#UsingHasDeprecated::r#UsingHasDeprecated as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_18_UsingHasDeprecated;
 pub use super::r#CompilerChecks::r#UsingHasDeprecated::r#Tag::r#Tag as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_18_UsingHasDeprecated_3_Tag;
 pub use super::r#CompilerChecks::r#NoPrefixInterface::r#INoPrefixInterface as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_17_NoPrefixInterface;
 pub use super::r#CompilerChecks::r#NoPrefixInterface::r#Nested::r#Nested as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_17_NoPrefixInterface_6_Nested;
 pub use super::r#CompilerChecks::r#NoPrefixInterface::r#NestedNoPrefixInterface::r#INestedNoPrefixInterface as _7_android_4_aidl_5_tests_12_ITestService_14_CompilerChecks_17_NoPrefixInterface_23_NestedNoPrefixInterface;
}
