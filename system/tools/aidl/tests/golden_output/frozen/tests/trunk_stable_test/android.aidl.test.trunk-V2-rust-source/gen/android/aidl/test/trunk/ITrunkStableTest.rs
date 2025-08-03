/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen --min_sdk_version current --previous_api_dir=system/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1 --previous_hash 88311b9118fb6fe9eff4a2ca19121de0587f6d5f --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-rust-source/gen/android/aidl/test/trunk/ITrunkStableTest.rs.d -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-rust-source/gen -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
#![allow(non_snake_case)]
#[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
use binder::declare_binder_interface;
declare_binder_interface! {
  ITrunkStableTest["android.aidl.test.trunk.ITrunkStableTest"] {
    native: BnTrunkStableTest(on_transact),
    proxy: BpTrunkStableTest {
      cached_version: std::sync::atomic::AtomicI32 = std::sync::atomic::AtomicI32::new(-1),
      cached_hash: std::sync::Mutex<Option<String>> = std::sync::Mutex::new(None)
    },
    async: ITrunkStableTestAsync,
  }
}
pub trait ITrunkStableTest: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest" }
  fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>;
  fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>;
  fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>;
  fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()>;
  fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>;
  fn r#getInterfaceVersion(&self) -> binder::Result<i32> {
    Ok(VERSION)
  }
  fn r#getInterfaceHash(&self) -> binder::Result<String> {
    Ok(HASH.into())
  }
  fn getDefaultImpl() -> ITrunkStableTestDefaultRef where Self: Sized {
    DEFAULT_IMPL.lock().unwrap().clone()
  }
  fn setDefaultImpl(d: ITrunkStableTestDefaultRef) -> ITrunkStableTestDefaultRef where Self: Sized {
    std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
  }
}
pub trait ITrunkStableTestAsync<P>: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest" }
  fn r#repeatParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>>;
  fn r#repeatEnum<'a>(&'a self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>>;
  fn r#repeatUnion<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>>;
  fn r#callMyCallback<'a>(&'a self, _arg_cb: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::BoxFuture<'a, binder::Result<()>>;
  fn r#repeatOtherParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>>;
  fn r#getInterfaceVersion<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<i32>> {
    Box::pin(async move { Ok(VERSION) })
  }
  fn r#getInterfaceHash<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<String>> {
    Box::pin(async move { Ok(HASH.into()) })
  }
}
#[::async_trait::async_trait]
pub trait ITrunkStableTestAsyncServer: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest" }
  async fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>;
  async fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>;
  async fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>;
  async fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()>;
  async fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>;
}
impl BnTrunkStableTest {
  /// Create a new async binder service.
  pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn ITrunkStableTest>
  where
    T: ITrunkStableTestAsyncServer + binder::Interface + Send + Sync + 'static,
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
    impl<T, R> ITrunkStableTest for Wrapper<T, R>
    where
      T: ITrunkStableTestAsyncServer + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
        self._rt.block_on(self._inner.r#repeatParcelable(_arg_input))
      }
      fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
        self._rt.block_on(self._inner.r#repeatEnum(_arg_input))
      }
      fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
        self._rt.block_on(self._inner.r#repeatUnion(_arg_input))
      }
      fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#callMyCallback(_arg_cb))
      }
      fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
        self._rt.block_on(self._inner.r#repeatOtherParcelable(_arg_input))
      }
    }
    let wrapped = Wrapper { _inner: inner, _rt: rt };
    Self::new_binder(wrapped, features)
  }
}
pub trait ITrunkStableTestDefault: Send + Sync {
  fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
}
pub mod transactions {
  pub const r#repeatParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
  pub const r#repeatEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 1;
  pub const r#repeatUnion: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 2;
  pub const r#callMyCallback: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 3;
  pub const r#repeatOtherParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 4;
  pub const r#getInterfaceVersion: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 16777214;
  pub const r#getInterfaceHash: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 16777213;
}
pub type ITrunkStableTestDefaultRef = Option<std::sync::Arc<dyn ITrunkStableTestDefault>>;
static DEFAULT_IMPL: std::sync::Mutex<ITrunkStableTestDefaultRef> = std::sync::Mutex::new(None);
pub const VERSION: i32 = if true {1} else {2};
pub const HASH: &str = if true {"88311b9118fb6fe9eff4a2ca19121de0587f6d5f"} else {"notfrozen"};
impl BpTrunkStableTest {
  fn build_parcel_repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITrunkStableTest>::getDefaultImpl() {
        return _aidl_default_impl.r#repeatParcelable(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(&_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITrunkStableTest>::getDefaultImpl() {
        return _aidl_default_impl.r#repeatEnum(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITrunkStableTest>::getDefaultImpl() {
        return _aidl_default_impl.r#repeatUnion(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(_arg_cb)?;
    Ok(aidl_data)
  }
  fn read_response_callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITrunkStableTest>::getDefaultImpl() {
        return _aidl_default_impl.r#callMyCallback(_arg_cb);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
  fn build_parcel_repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(_arg_input)?;
    Ok(aidl_data)
  }
  fn read_response_repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ITrunkStableTest>::getDefaultImpl() {
        return _aidl_default_impl.r#repeatOtherParcelable(_arg_input);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_getInterfaceVersion(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    Ok(aidl_data)
  }
  fn read_response_getInterfaceVersion(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i32> {
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: i32 = _aidl_reply.read()?;
    self.cached_version.store(_aidl_return, std::sync::atomic::Ordering::Relaxed);
    Ok(_aidl_return)
  }
  fn build_parcel_getInterfaceHash(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    Ok(aidl_data)
  }
  fn read_response_getInterfaceHash(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<String> {
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: String = _aidl_reply.read()?;
    *self.cached_hash.lock().unwrap() = Some(_aidl_return.clone());
    Ok(_aidl_return)
  }
}
impl ITrunkStableTest for BpTrunkStableTest {
  fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
    let _aidl_data = self.build_parcel_repeatParcelable(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#repeatParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_repeatParcelable(_arg_input, _aidl_reply)
  }
  fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
    let _aidl_data = self.build_parcel_repeatEnum(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#repeatEnum, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_repeatEnum(_arg_input, _aidl_reply)
  }
  fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
    let _aidl_data = self.build_parcel_repeatUnion(_arg_input)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#repeatUnion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_repeatUnion(_arg_input, _aidl_reply)
  }
  fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_callMyCallback(_arg_cb)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#callMyCallback, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_callMyCallback(_arg_cb, _aidl_reply)
  }
  fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
    if (true) {
     return Err(binder::Status::from(binder::StatusCode::UNKNOWN_TRANSACTION));
    } else {
      let _aidl_data = self.build_parcel_repeatOtherParcelable(_arg_input)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#repeatOtherParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_repeatOtherParcelable(_arg_input, _aidl_reply)
    }
  }
  fn r#getInterfaceVersion(&self) -> binder::Result<i32> {
    let _aidl_version = self.cached_version.load(std::sync::atomic::Ordering::Relaxed);
    if _aidl_version != -1 { return Ok(_aidl_version); }
    let _aidl_data = self.build_parcel_getInterfaceVersion()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#getInterfaceVersion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_getInterfaceVersion(_aidl_reply)
  }
  fn r#getInterfaceHash(&self) -> binder::Result<String> {
    {
      let _aidl_hash_lock = self.cached_hash.lock().unwrap();
      if let Some(ref _aidl_hash) = *_aidl_hash_lock {
        return Ok(_aidl_hash.clone());
      }
    }
    let _aidl_data = self.build_parcel_getInterfaceHash()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#getInterfaceHash, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_getInterfaceHash(_aidl_reply)
  }
}
impl<P: binder::BinderAsyncPool> ITrunkStableTestAsync<P> for BpTrunkStableTest {
  fn r#repeatParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>> {
    let _aidl_data = match self.build_parcel_repeatParcelable(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#repeatParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_repeatParcelable(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#repeatEnum<'a>(&'a self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>> {
    let _aidl_data = match self.build_parcel_repeatEnum(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#repeatEnum, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_repeatEnum(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#repeatUnion<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>> {
    let _aidl_data = match self.build_parcel_repeatUnion(_arg_input) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#repeatUnion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_repeatUnion(_arg_input, _aidl_reply)
      }
    )
  }
  fn r#callMyCallback<'a>(&'a self, _arg_cb: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_callMyCallback(_arg_cb) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#callMyCallback, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_callMyCallback(_arg_cb, _aidl_reply)
      }
    )
  }
  fn r#repeatOtherParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>> {
    if (true) {
     return Box::pin(std::future::ready(Err(binder::Status::from(binder::StatusCode::UNKNOWN_TRANSACTION))));
    } else {
      let _aidl_data = match self.build_parcel_repeatOtherParcelable(_arg_input) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#repeatOtherParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_repeatOtherParcelable(_arg_input, _aidl_reply)
        }
      )
    }
  }
  fn r#getInterfaceVersion<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<i32>> {
    let _aidl_version = self.cached_version.load(std::sync::atomic::Ordering::Relaxed);
    if _aidl_version != -1 { return Box::pin(std::future::ready(Ok(_aidl_version))); }
    let _aidl_data = match self.build_parcel_getInterfaceVersion() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#getInterfaceVersion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_getInterfaceVersion(_aidl_reply)
      }
    )
  }
  fn r#getInterfaceHash<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<String>> {
    {
      let _aidl_hash_lock = self.cached_hash.lock().unwrap();
      if let Some(ref _aidl_hash) = *_aidl_hash_lock {
        return Box::pin(std::future::ready(Ok(_aidl_hash.clone())));
      }
    }
    let _aidl_data = match self.build_parcel_getInterfaceHash() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#getInterfaceHash, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_getInterfaceHash(_aidl_reply)
      }
    )
  }
}
impl ITrunkStableTest for binder::binder_impl::Binder<BnTrunkStableTest> {
  fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> { self.0.r#repeatParcelable(_arg_input) }
  fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> { self.0.r#repeatEnum(_arg_input) }
  fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> { self.0.r#repeatUnion(_arg_input) }
  fn r#callMyCallback(&self, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback>) -> binder::Result<()> { self.0.r#callMyCallback(_arg_cb) }
  fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> { self.0.r#repeatOtherParcelable(_arg_input) }
  fn r#getInterfaceVersion(&self) -> binder::Result<i32> { self.0.r#getInterfaceVersion() }
  fn r#getInterfaceHash(&self) -> binder::Result<String> { self.0.r#getInterfaceHash() }
}
fn on_transact(_aidl_service: &dyn ITrunkStableTest, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
  match _aidl_code {
    transactions::r#repeatParcelable => {
      let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#repeatParcelable(&_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#repeatEnum => {
      let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#repeatEnum(_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#repeatUnion => {
      let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#repeatUnion(&_arg_input);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#callMyCallback => {
      let _arg_cb: binder::Strong<dyn crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#callMyCallback(&_arg_cb);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#repeatOtherParcelable => {
      if (true) {
        Err(binder::StatusCode::UNKNOWN_TRANSACTION)
      } else {
        let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable = _aidl_data.read()?;
        let _aidl_return = _aidl_service.r#repeatOtherParcelable(&_arg_input);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
    }
    transactions::r#getInterfaceVersion => {
      let _aidl_return = _aidl_service.r#getInterfaceVersion();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#getInterfaceHash => {
      let _aidl_return = _aidl_service.r#getInterfaceHash();
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
  }
}
pub mod r#MyParcelable {
  #[derive(Debug)]
  pub struct r#MyParcelable {
    pub r#a: i32,
    pub r#b: i32,
    pub r#c: i32,
  }
  impl Default for r#MyParcelable {
    fn default() -> Self {
      Self {
        r#a: 0,
        r#b: 0,
        r#c: 0,
      }
    }
  }
  impl binder::Parcelable for r#MyParcelable {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_write(|subparcel| {
        subparcel.write(&self.r#a)?;
        subparcel.write(&self.r#b)?;
        if (false) {
          subparcel.write(&self.r#c)?;
        }
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
        if (false) {
          if subparcel.has_more_data() {
            self.r#c = subparcel.read()?;
          }
        }
        Ok(())
      })
    }
  }
  binder::impl_serialize_for_parcelable!(r#MyParcelable);
  binder::impl_deserialize_for_parcelable!(r#MyParcelable);
  impl binder::binder_impl::ParcelableMetadata for r#MyParcelable {
    fn get_descriptor() -> &'static str { "android.aidl.test.trunk.ITrunkStableTest.MyParcelable" }
  }
}
pub mod r#MyEnum {
  #![allow(non_upper_case_globals)]
  use binder::declare_binder_enum;
  declare_binder_enum! {
    r#MyEnum : [i8; 4] {
      r#ZERO = 0,
      r#ONE = 1,
      r#TWO = 2,
      r#THREE = 3,
    }
  }
}
pub mod r#MyUnion {
  #[derive(Debug)]
  pub enum r#MyUnion {
    A(i32),
    B(i32),
    C(i32),
  }
  impl Default for r#MyUnion {
    fn default() -> Self {
      Self::A(0)
    }
  }
  impl binder::Parcelable for r#MyUnion {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      match self {
        Self::A(v) => {
          parcel.write(&0i32)?;
          parcel.write(v)
        }
        Self::B(v) => {
          parcel.write(&1i32)?;
          parcel.write(v)
        }
        Self::C(v) => {
          if (true) {
            Err(binder::StatusCode::BAD_VALUE)
          } else {
            parcel.write(&2i32)?;
            parcel.write(v)
          }
        }
      }
    }
    fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      let tag: i32 = parcel.read()?;
      match tag {
        0 => {
          let value: i32 = parcel.read()?;
          *self = Self::A(value);
          Ok(())
        }
        1 => {
          let value: i32 = parcel.read()?;
          *self = Self::B(value);
          Ok(())
        }
        2 => {
          if (true) {
            Err(binder::StatusCode::BAD_VALUE)
          } else {
            let value: i32 = parcel.read()?;
            *self = Self::C(value);
            Ok(())
          }
        }
        _ => {
          Err(binder::StatusCode::BAD_VALUE)
        }
      }
    }
  }
  binder::impl_serialize_for_parcelable!(r#MyUnion);
  binder::impl_deserialize_for_parcelable!(r#MyUnion);
  impl binder::binder_impl::ParcelableMetadata for r#MyUnion {
    fn get_descriptor() -> &'static str { "android.aidl.test.trunk.ITrunkStableTest.MyUnion" }
  }
  pub mod r#Tag {
    #![allow(non_upper_case_globals)]
    use binder::declare_binder_enum;
    declare_binder_enum! {
      r#Tag : [i32; 3] {
        r#a = 0,
        r#b = 1,
        r#c = 2,
      }
    }
  }
}
pub mod r#IMyCallback {
  #![allow(non_upper_case_globals)]
  #![allow(non_snake_case)]
  #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
  use binder::declare_binder_interface;
  declare_binder_interface! {
    IMyCallback["android.aidl.test.trunk.ITrunkStableTest.IMyCallback"] {
      native: BnMyCallback(on_transact),
      proxy: BpMyCallback {
        cached_version: std::sync::atomic::AtomicI32 = std::sync::atomic::AtomicI32::new(-1),
        cached_hash: std::sync::Mutex<Option<String>> = std::sync::Mutex::new(None)
      },
      async: IMyCallbackAsync,
    }
  }
  pub trait IMyCallback: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest.IMyCallback" }
    fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>;
    fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>;
    fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>;
    fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>;
    fn r#getInterfaceVersion(&self) -> binder::Result<i32> {
      Ok(VERSION)
    }
    fn r#getInterfaceHash(&self) -> binder::Result<String> {
      Ok(HASH.into())
    }
    fn getDefaultImpl() -> IMyCallbackDefaultRef where Self: Sized {
      DEFAULT_IMPL.lock().unwrap().clone()
    }
    fn setDefaultImpl(d: IMyCallbackDefaultRef) -> IMyCallbackDefaultRef where Self: Sized {
      std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
    }
  }
  pub trait IMyCallbackAsync<P>: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest.IMyCallback" }
    fn r#repeatParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>>;
    fn r#repeatEnum<'a>(&'a self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>>;
    fn r#repeatUnion<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>>;
    fn r#repeatOtherParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>>;
    fn r#getInterfaceVersion<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<i32>> {
      Box::pin(async move { Ok(VERSION) })
    }
    fn r#getInterfaceHash<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<String>> {
      Box::pin(async move { Ok(HASH.into()) })
    }
  }
  #[::async_trait::async_trait]
  pub trait IMyCallbackAsyncServer: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.test.trunk.ITrunkStableTest.IMyCallback" }
    async fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>;
    async fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>;
    async fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>;
    async fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>;
  }
  impl BnMyCallback {
    /// Create a new async binder service.
    pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn IMyCallback>
    where
      T: IMyCallbackAsyncServer + binder::Interface + Send + Sync + 'static,
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
      impl<T, R> IMyCallback for Wrapper<T, R>
      where
        T: IMyCallbackAsyncServer + Send + Sync + 'static,
        R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
      {
        fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
          self._rt.block_on(self._inner.r#repeatParcelable(_arg_input))
        }
        fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
          self._rt.block_on(self._inner.r#repeatEnum(_arg_input))
        }
        fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
          self._rt.block_on(self._inner.r#repeatUnion(_arg_input))
        }
        fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
          self._rt.block_on(self._inner.r#repeatOtherParcelable(_arg_input))
        }
      }
      let wrapped = Wrapper { _inner: inner, _rt: rt };
      Self::new_binder(wrapped, features)
    }
  }
  pub trait IMyCallbackDefault: Send + Sync {
    fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
    fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
  }
  pub mod transactions {
    pub const r#repeatParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
    pub const r#repeatEnum: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 1;
    pub const r#repeatUnion: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 2;
    pub const r#repeatOtherParcelable: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 3;
    pub const r#getInterfaceVersion: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 16777214;
    pub const r#getInterfaceHash: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 16777213;
  }
  pub type IMyCallbackDefaultRef = Option<std::sync::Arc<dyn IMyCallbackDefault>>;
  static DEFAULT_IMPL: std::sync::Mutex<IMyCallbackDefaultRef> = std::sync::Mutex::new(None);
  pub const VERSION: i32 = if true {1} else {2};
  pub const HASH: &str = if true {"88311b9118fb6fe9eff4a2ca19121de0587f6d5f"} else {"notfrozen"};
  impl BpMyCallback {
    fn build_parcel_repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IMyCallback>::getDefaultImpl() {
          return _aidl_default_impl.r#repeatParcelable(_arg_input);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable = _aidl_reply.read()?;
      Ok(_aidl_return)
    }
    fn build_parcel_repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(&_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IMyCallback>::getDefaultImpl() {
          return _aidl_default_impl.r#repeatEnum(_arg_input);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum = _aidl_reply.read()?;
      Ok(_aidl_return)
    }
    fn build_parcel_repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IMyCallback>::getDefaultImpl() {
          return _aidl_default_impl.r#repeatUnion(_arg_input);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion = _aidl_reply.read()?;
      Ok(_aidl_return)
    }
    fn build_parcel_repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(_arg_input)?;
      Ok(aidl_data)
    }
    fn read_response_repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as IMyCallback>::getDefaultImpl() {
          return _aidl_default_impl.r#repeatOtherParcelable(_arg_input);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable = _aidl_reply.read()?;
      Ok(_aidl_return)
    }
    fn build_parcel_getInterfaceVersion(&self) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      Ok(aidl_data)
    }
    fn read_response_getInterfaceVersion(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<i32> {
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: i32 = _aidl_reply.read()?;
      self.cached_version.store(_aidl_return, std::sync::atomic::Ordering::Relaxed);
      Ok(_aidl_return)
    }
    fn build_parcel_getInterfaceHash(&self) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      Ok(aidl_data)
    }
    fn read_response_getInterfaceHash(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<String> {
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      let _aidl_return: String = _aidl_reply.read()?;
      *self.cached_hash.lock().unwrap() = Some(_aidl_return.clone());
      Ok(_aidl_return)
    }
  }
  impl IMyCallback for BpMyCallback {
    fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> {
      let _aidl_data = self.build_parcel_repeatParcelable(_arg_input)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#repeatParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_repeatParcelable(_arg_input, _aidl_reply)
    }
    fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> {
      let _aidl_data = self.build_parcel_repeatEnum(_arg_input)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#repeatEnum, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_repeatEnum(_arg_input, _aidl_reply)
    }
    fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> {
      let _aidl_data = self.build_parcel_repeatUnion(_arg_input)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#repeatUnion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_repeatUnion(_arg_input, _aidl_reply)
    }
    fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> {
      if (true) {
       return Err(binder::Status::from(binder::StatusCode::UNKNOWN_TRANSACTION));
      } else {
        let _aidl_data = self.build_parcel_repeatOtherParcelable(_arg_input)?;
        let _aidl_reply = self.binder.submit_transact(transactions::r#repeatOtherParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
        self.read_response_repeatOtherParcelable(_arg_input, _aidl_reply)
      }
    }
    fn r#getInterfaceVersion(&self) -> binder::Result<i32> {
      let _aidl_version = self.cached_version.load(std::sync::atomic::Ordering::Relaxed);
      if _aidl_version != -1 { return Ok(_aidl_version); }
      let _aidl_data = self.build_parcel_getInterfaceVersion()?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#getInterfaceVersion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_getInterfaceVersion(_aidl_reply)
    }
    fn r#getInterfaceHash(&self) -> binder::Result<String> {
      {
        let _aidl_hash_lock = self.cached_hash.lock().unwrap();
        if let Some(ref _aidl_hash) = *_aidl_hash_lock {
          return Ok(_aidl_hash.clone());
        }
      }
      let _aidl_data = self.build_parcel_getInterfaceHash()?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#getInterfaceHash, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_getInterfaceHash(_aidl_reply)
    }
  }
  impl<P: binder::BinderAsyncPool> IMyCallbackAsync<P> for BpMyCallback {
    fn r#repeatParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable>> {
      let _aidl_data = match self.build_parcel_repeatParcelable(_arg_input) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#repeatParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_repeatParcelable(_arg_input, _aidl_reply)
        }
      )
    }
    fn r#repeatEnum<'a>(&'a self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum>> {
      let _aidl_data = match self.build_parcel_repeatEnum(_arg_input) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#repeatEnum, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_repeatEnum(_arg_input, _aidl_reply)
        }
      )
    }
    fn r#repeatUnion<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion>> {
      let _aidl_data = match self.build_parcel_repeatUnion(_arg_input) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#repeatUnion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_repeatUnion(_arg_input, _aidl_reply)
        }
      )
    }
    fn r#repeatOtherParcelable<'a>(&'a self, _arg_input: &'a crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable>> {
      if (true) {
       return Box::pin(std::future::ready(Err(binder::Status::from(binder::StatusCode::UNKNOWN_TRANSACTION))));
      } else {
        let _aidl_data = match self.build_parcel_repeatOtherParcelable(_arg_input) {
          Ok(_aidl_data) => _aidl_data,
          Err(err) => return Box::pin(std::future::ready(Err(err))),
        };
        let binder = self.binder.clone();
        P::spawn(
          move || binder.submit_transact(transactions::r#repeatOtherParcelable, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
          move |_aidl_reply| async move {
            self.read_response_repeatOtherParcelable(_arg_input, _aidl_reply)
          }
        )
      }
    }
    fn r#getInterfaceVersion<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<i32>> {
      let _aidl_version = self.cached_version.load(std::sync::atomic::Ordering::Relaxed);
      if _aidl_version != -1 { return Box::pin(std::future::ready(Ok(_aidl_version))); }
      let _aidl_data = match self.build_parcel_getInterfaceVersion() {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#getInterfaceVersion, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_getInterfaceVersion(_aidl_reply)
        }
      )
    }
    fn r#getInterfaceHash<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<String>> {
      {
        let _aidl_hash_lock = self.cached_hash.lock().unwrap();
        if let Some(ref _aidl_hash) = *_aidl_hash_lock {
          return Box::pin(std::future::ready(Ok(_aidl_hash.clone())));
        }
      }
      let _aidl_data = match self.build_parcel_getInterfaceHash() {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#getInterfaceHash, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_getInterfaceHash(_aidl_reply)
        }
      )
    }
  }
  impl IMyCallback for binder::binder_impl::Binder<BnMyCallback> {
    fn r#repeatParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable> { self.0.r#repeatParcelable(_arg_input) }
    fn r#repeatEnum(&self, _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum> { self.0.r#repeatEnum(_arg_input) }
    fn r#repeatUnion(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion> { self.0.r#repeatUnion(_arg_input) }
    fn r#repeatOtherParcelable(&self, _arg_input: &crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable) -> binder::Result<crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable> { self.0.r#repeatOtherParcelable(_arg_input) }
    fn r#getInterfaceVersion(&self) -> binder::Result<i32> { self.0.r#getInterfaceVersion() }
    fn r#getInterfaceHash(&self) -> binder::Result<String> { self.0.r#getInterfaceHash() }
  }
  fn on_transact(_aidl_service: &dyn IMyCallback, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
    match _aidl_code {
      transactions::r#repeatParcelable => {
        let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable = _aidl_data.read()?;
        let _aidl_return = _aidl_service.r#repeatParcelable(&_arg_input);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#repeatEnum => {
        let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum = _aidl_data.read()?;
        let _aidl_return = _aidl_service.r#repeatEnum(_arg_input);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#repeatUnion => {
        let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion = _aidl_data.read()?;
        let _aidl_return = _aidl_service.r#repeatUnion(&_arg_input);
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#repeatOtherParcelable => {
        if (true) {
          Err(binder::StatusCode::UNKNOWN_TRANSACTION)
        } else {
          let _arg_input: crate::mangled::_7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable = _aidl_data.read()?;
          let _aidl_return = _aidl_service.r#repeatOtherParcelable(&_arg_input);
          match &_aidl_return {
            Ok(_aidl_return) => {
              _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
              _aidl_reply.write(_aidl_return)?;
            }
            Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
          }
          Ok(())
        }
      }
      transactions::r#getInterfaceVersion => {
        let _aidl_return = _aidl_service.r#getInterfaceVersion();
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      transactions::r#getInterfaceHash => {
        let _aidl_return = _aidl_service.r#getInterfaceHash();
        match &_aidl_return {
          Ok(_aidl_return) => {
            _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
            _aidl_reply.write(_aidl_return)?;
          }
          Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
        }
        Ok(())
      }
      _ => Err(binder::StatusCode::UNKNOWN_TRANSACTION)
    }
  }
}
pub mod r#MyOtherParcelable {
  #[derive(Debug)]
  pub struct r#MyOtherParcelable {
    pub r#a: i32,
    pub r#b: i32,
  }
  impl Default for r#MyOtherParcelable {
    fn default() -> Self {
      Self {
        r#a: 0,
        r#b: 0,
      }
    }
  }
  impl binder::Parcelable for r#MyOtherParcelable {
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
  binder::impl_serialize_for_parcelable!(r#MyOtherParcelable);
  binder::impl_deserialize_for_parcelable!(r#MyOtherParcelable);
  impl binder::binder_impl::ParcelableMetadata for r#MyOtherParcelable {
    fn get_descriptor() -> &'static str { "android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable" }
  }
}
pub(crate) mod mangled {
 pub use super::r#ITrunkStableTest as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest;
 pub use super::r#MyParcelable::r#MyParcelable as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_12_MyParcelable;
 pub use super::r#MyEnum::r#MyEnum as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_6_MyEnum;
 pub use super::r#MyUnion::r#MyUnion as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion;
 pub use super::r#MyUnion::r#Tag::r#Tag as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_7_MyUnion_3_Tag;
 pub use super::r#IMyCallback::r#IMyCallback as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_11_IMyCallback;
 pub use super::r#MyOtherParcelable::r#MyOtherParcelable as _7_android_4_aidl_4_test_5_trunk_16_ITrunkStableTest_17_MyOtherParcelable;
}
