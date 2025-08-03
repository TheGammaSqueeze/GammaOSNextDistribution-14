/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/nested/INestedService.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
#![allow(non_snake_case)]
#[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
use binder::declare_binder_interface;
declare_binder_interface! {
  INestedService["android.aidl.tests.nested.INestedService"] {
    native: BnNestedService(on_transact),
    proxy: BpNestedService {
    },
    async: INestedServiceAsync,
  }
}
pub trait INestedService: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService" }
  fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result>;
  fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()>;
  fn getDefaultImpl() -> INestedServiceDefaultRef where Self: Sized {
    DEFAULT_IMPL.lock().unwrap().clone()
  }
  fn setDefaultImpl(d: INestedServiceDefaultRef) -> INestedServiceDefaultRef where Self: Sized {
    std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
  }
}
pub trait INestedServiceAsync<P>: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService" }
  fn r#flipStatus<'a>(&'a self, _arg_p: &'a crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result>>;
  fn r#flipStatusWithCallback<'a>(&'a self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::BoxFuture<'a, binder::Result<()>>;
}
#[::async_trait::async_trait]
pub trait INestedServiceAsyncServer: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService" }
  async fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result>;
  async fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()>;
}
impl BnNestedService {
  /// Create a new async binder service.
  pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn INestedService>
  where
    T: INestedServiceAsyncServer + binder::Interface + Send + Sync + 'static,
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
    impl<T, R> INestedService for Wrapper<T, R>
    where
      T: INestedServiceAsyncServer + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result> {
        self._rt.block_on(self._inner.r#flipStatus(_arg_p))
      }
      fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()> {
        self._rt.block_on(self._inner.r#flipStatusWithCallback(_arg_status, _arg_cb))
      }
    }
    let wrapped = Wrapper { _inner: inner, _rt: rt };
    Self::new_binder(wrapped, features)
  }
}
pub trait INestedServiceDefault: Send + Sync {
  fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
  fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
}
pub mod transactions {
  pub const r#flipStatus: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
  pub const r#flipStatusWithCallback: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 1;
}
pub type INestedServiceDefaultRef = Option<std::sync::Arc<dyn INestedServiceDefault>>;
static DEFAULT_IMPL: std::sync::Mutex<INestedServiceDefaultRef> = std::sync::Mutex::new(None);
impl BpNestedService {
  fn build_parcel_flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(_arg_p)?;
    Ok(aidl_data)
  }
  fn read_response_flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as INestedService>::getDefaultImpl() {
        return _aidl_default_impl.r#flipStatus(_arg_p);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
  fn build_parcel_flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    aidl_data.write(&_arg_status)?;
    aidl_data.write(_arg_cb)?;
    Ok(aidl_data)
  }
  fn read_response_flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as INestedService>::getDefaultImpl() {
        return _aidl_default_impl.r#flipStatusWithCallback(_arg_status, _arg_cb);
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    Ok(())
  }
}
impl INestedService for BpNestedService {
  fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result> {
    let _aidl_data = self.build_parcel_flipStatus(_arg_p)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#flipStatus, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_flipStatus(_arg_p, _aidl_reply)
  }
  fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()> {
    let _aidl_data = self.build_parcel_flipStatusWithCallback(_arg_status, _arg_cb)?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#flipStatusWithCallback, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_flipStatusWithCallback(_arg_status, _arg_cb, _aidl_reply)
  }
}
impl<P: binder::BinderAsyncPool> INestedServiceAsync<P> for BpNestedService {
  fn r#flipStatus<'a>(&'a self, _arg_p: &'a crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::BoxFuture<'a, binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result>> {
    let _aidl_data = match self.build_parcel_flipStatus(_arg_p) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#flipStatus, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_flipStatus(_arg_p, _aidl_reply)
      }
    )
  }
  fn r#flipStatusWithCallback<'a>(&'a self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &'a binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::BoxFuture<'a, binder::Result<()>> {
    let _aidl_data = match self.build_parcel_flipStatusWithCallback(_arg_status, _arg_cb) {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#flipStatusWithCallback, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_flipStatusWithCallback(_arg_status, _arg_cb, _aidl_reply)
      }
    )
  }
}
impl INestedService for binder::binder_impl::Binder<BnNestedService> {
  fn r#flipStatus(&self, _arg_p: &crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested) -> binder::Result<crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result> { self.0.r#flipStatus(_arg_p) }
  fn r#flipStatusWithCallback(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _arg_cb: &binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback>) -> binder::Result<()> { self.0.r#flipStatusWithCallback(_arg_status, _arg_cb) }
}
fn on_transact(_aidl_service: &dyn INestedService, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
  match _aidl_code {
    transactions::r#flipStatus => {
      let _arg_p: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#flipStatus(&_arg_p);
      match &_aidl_return {
        Ok(_aidl_return) => {
          _aidl_reply.write(&binder::Status::from(binder::StatusCode::OK))?;
          _aidl_reply.write(_aidl_return)?;
        }
        Err(_aidl_status) => _aidl_reply.write(_aidl_status)?
      }
      Ok(())
    }
    transactions::r#flipStatusWithCallback => {
      let _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status = _aidl_data.read()?;
      let _arg_cb: binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback> = _aidl_data.read()?;
      let _aidl_return = _aidl_service.r#flipStatusWithCallback(_arg_status, &_arg_cb);
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
pub mod r#Result {
  #[derive(Debug, PartialEq)]
  pub struct r#Result {
    pub r#status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status,
  }
  impl Default for r#Result {
    fn default() -> Self {
      Self {
        r#status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status::OK,
      }
    }
  }
  impl binder::Parcelable for r#Result {
    fn write_to_parcel(&self, parcel: &mut binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_write(|subparcel| {
        subparcel.write(&self.r#status)?;
        Ok(())
      })
    }
    fn read_from_parcel(&mut self, parcel: &binder::binder_impl::BorrowedParcel) -> std::result::Result<(), binder::StatusCode> {
      parcel.sized_read(|subparcel| {
        if subparcel.has_more_data() {
          self.r#status = subparcel.read()?;
        }
        Ok(())
      })
    }
  }
  binder::impl_serialize_for_parcelable!(r#Result);
  binder::impl_deserialize_for_parcelable!(r#Result);
  impl binder::binder_impl::ParcelableMetadata for r#Result {
    fn get_descriptor() -> &'static str { "android.aidl.tests.nested.INestedService.Result" }
  }
}
pub mod r#ICallback {
  #![allow(non_upper_case_globals)]
  #![allow(non_snake_case)]
  #[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
  use binder::declare_binder_interface;
  declare_binder_interface! {
    ICallback["android.aidl.tests.nested.INestedService.ICallback"] {
      native: BnCallback(on_transact),
      proxy: BpCallback {
      },
      async: ICallbackAsync,
    }
  }
  pub trait ICallback: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService.ICallback" }
    fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()>;
    fn getDefaultImpl() -> ICallbackDefaultRef where Self: Sized {
      DEFAULT_IMPL.lock().unwrap().clone()
    }
    fn setDefaultImpl(d: ICallbackDefaultRef) -> ICallbackDefaultRef where Self: Sized {
      std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
    }
  }
  pub trait ICallbackAsync<P>: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService.ICallback" }
    fn r#done<'a>(&'a self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::BoxFuture<'a, binder::Result<()>>;
  }
  #[::async_trait::async_trait]
  pub trait ICallbackAsyncServer: binder::Interface + Send {
    fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.nested.INestedService.ICallback" }
    async fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()>;
  }
  impl BnCallback {
    /// Create a new async binder service.
    pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn ICallback>
    where
      T: ICallbackAsyncServer + binder::Interface + Send + Sync + 'static,
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
      impl<T, R> ICallback for Wrapper<T, R>
      where
        T: ICallbackAsyncServer + Send + Sync + 'static,
        R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
      {
        fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()> {
          self._rt.block_on(self._inner.r#done(_arg_status))
        }
      }
      let wrapped = Wrapper { _inner: inner, _rt: rt };
      Self::new_binder(wrapped, features)
    }
  }
  pub trait ICallbackDefault: Send + Sync {
    fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()> {
      Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
    }
  }
  pub mod transactions {
    pub const r#done: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
  }
  pub type ICallbackDefaultRef = Option<std::sync::Arc<dyn ICallbackDefault>>;
  static DEFAULT_IMPL: std::sync::Mutex<ICallbackDefaultRef> = std::sync::Mutex::new(None);
  impl BpCallback {
    fn build_parcel_done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<binder::binder_impl::Parcel> {
      let mut aidl_data = self.binder.prepare_transact()?;
      aidl_data.write(&_arg_status)?;
      Ok(aidl_data)
    }
    fn read_response_done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<()> {
      if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
        if let Some(_aidl_default_impl) = <Self as ICallback>::getDefaultImpl() {
          return _aidl_default_impl.r#done(_arg_status);
        }
      }
      let _aidl_reply = _aidl_reply?;
      let _aidl_status: binder::Status = _aidl_reply.read()?;
      if !_aidl_status.is_ok() { return Err(_aidl_status); }
      Ok(())
    }
  }
  impl ICallback for BpCallback {
    fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()> {
      let _aidl_data = self.build_parcel_done(_arg_status)?;
      let _aidl_reply = self.binder.submit_transact(transactions::r#done, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
      self.read_response_done(_arg_status, _aidl_reply)
    }
  }
  impl<P: binder::BinderAsyncPool> ICallbackAsync<P> for BpCallback {
    fn r#done<'a>(&'a self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::BoxFuture<'a, binder::Result<()>> {
      let _aidl_data = match self.build_parcel_done(_arg_status) {
        Ok(_aidl_data) => _aidl_data,
        Err(err) => return Box::pin(std::future::ready(Err(err))),
      };
      let binder = self.binder.clone();
      P::spawn(
        move || binder.submit_transact(transactions::r#done, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
        move |_aidl_reply| async move {
          self.read_response_done(_arg_status, _aidl_reply)
        }
      )
    }
  }
  impl ICallback for binder::binder_impl::Binder<BnCallback> {
    fn r#done(&self, _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status) -> binder::Result<()> { self.0.r#done(_arg_status) }
  }
  fn on_transact(_aidl_service: &dyn ICallback, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
    match _aidl_code {
      transactions::r#done => {
        let _arg_status: crate::mangled::_7_android_4_aidl_5_tests_6_nested_20_ParcelableWithNested_6_Status = _aidl_data.read()?;
        let _aidl_return = _aidl_service.r#done(_arg_status);
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
pub(crate) mod mangled {
 pub use super::r#INestedService as _7_android_4_aidl_5_tests_6_nested_14_INestedService;
 pub use super::r#Result::r#Result as _7_android_4_aidl_5_tests_6_nested_14_INestedService_6_Result;
 pub use super::r#ICallback::r#ICallback as _7_android_4_aidl_5_tests_6_nested_14_INestedService_9_ICallback;
}
