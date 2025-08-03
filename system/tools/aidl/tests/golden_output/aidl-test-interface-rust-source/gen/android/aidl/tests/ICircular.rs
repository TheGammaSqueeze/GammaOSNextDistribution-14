/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=rust -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen/android/aidl/tests/ICircular.rs.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-rust-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICircular.aidl
 */
#![forbid(unsafe_code)]
#![cfg_attr(rustfmt, rustfmt_skip)]
#![allow(non_upper_case_globals)]
#![allow(non_snake_case)]
#[allow(unused_imports)] use binder::binder_impl::IBinderInternal;
use binder::declare_binder_interface;
declare_binder_interface! {
  ICircular["android.aidl.tests.ICircular"] {
    native: BnCircular(on_transact),
    proxy: BpCircular {
    },
    async: ICircularAsync,
  }
}
pub trait ICircular: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ICircular" }
  fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>>;
  fn getDefaultImpl() -> ICircularDefaultRef where Self: Sized {
    DEFAULT_IMPL.lock().unwrap().clone()
  }
  fn setDefaultImpl(d: ICircularDefaultRef) -> ICircularDefaultRef where Self: Sized {
    std::mem::replace(&mut *DEFAULT_IMPL.lock().unwrap(), d)
  }
}
pub trait ICircularAsync<P>: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ICircular" }
  fn r#GetTestService<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>>>;
}
#[::async_trait::async_trait]
pub trait ICircularAsyncServer: binder::Interface + Send {
  fn get_descriptor() -> &'static str where Self: Sized { "android.aidl.tests.ICircular" }
  async fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>>;
}
impl BnCircular {
  /// Create a new async binder service.
  pub fn new_async_binder<T, R>(inner: T, rt: R, features: binder::BinderFeatures) -> binder::Strong<dyn ICircular>
  where
    T: ICircularAsyncServer + binder::Interface + Send + Sync + 'static,
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
    impl<T, R> ICircular for Wrapper<T, R>
    where
      T: ICircularAsyncServer + Send + Sync + 'static,
      R: binder::binder_impl::BinderAsyncRuntime + Send + Sync + 'static,
    {
      fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>> {
        self._rt.block_on(self._inner.r#GetTestService())
      }
    }
    let wrapped = Wrapper { _inner: inner, _rt: rt };
    Self::new_binder(wrapped, features)
  }
}
pub trait ICircularDefault: Send + Sync {
  fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>> {
    Err(binder::StatusCode::UNKNOWN_TRANSACTION.into())
  }
}
pub mod transactions {
  pub const r#GetTestService: binder::binder_impl::TransactionCode = binder::binder_impl::FIRST_CALL_TRANSACTION + 0;
}
pub type ICircularDefaultRef = Option<std::sync::Arc<dyn ICircularDefault>>;
static DEFAULT_IMPL: std::sync::Mutex<ICircularDefaultRef> = std::sync::Mutex::new(None);
impl BpCircular {
  fn build_parcel_GetTestService(&self) -> binder::Result<binder::binder_impl::Parcel> {
    let mut aidl_data = self.binder.prepare_transact()?;
    Ok(aidl_data)
  }
  fn read_response_GetTestService(&self, _aidl_reply: std::result::Result<binder::binder_impl::Parcel, binder::StatusCode>) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>> {
    if let Err(binder::StatusCode::UNKNOWN_TRANSACTION) = _aidl_reply {
      if let Some(_aidl_default_impl) = <Self as ICircular>::getDefaultImpl() {
        return _aidl_default_impl.r#GetTestService();
      }
    }
    let _aidl_reply = _aidl_reply?;
    let _aidl_status: binder::Status = _aidl_reply.read()?;
    if !_aidl_status.is_ok() { return Err(_aidl_status); }
    let _aidl_return: Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>> = _aidl_reply.read()?;
    Ok(_aidl_return)
  }
}
impl ICircular for BpCircular {
  fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>> {
    let _aidl_data = self.build_parcel_GetTestService()?;
    let _aidl_reply = self.binder.submit_transact(transactions::r#GetTestService, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL);
    self.read_response_GetTestService(_aidl_reply)
  }
}
impl<P: binder::BinderAsyncPool> ICircularAsync<P> for BpCircular {
  fn r#GetTestService<'a>(&'a self) -> binder::BoxFuture<'a, binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>>> {
    let _aidl_data = match self.build_parcel_GetTestService() {
      Ok(_aidl_data) => _aidl_data,
      Err(err) => return Box::pin(std::future::ready(Err(err))),
    };
    let binder = self.binder.clone();
    P::spawn(
      move || binder.submit_transact(transactions::r#GetTestService, _aidl_data, binder::binder_impl::FLAG_PRIVATE_LOCAL),
      move |_aidl_reply| async move {
        self.read_response_GetTestService(_aidl_reply)
      }
    )
  }
}
impl ICircular for binder::binder_impl::Binder<BnCircular> {
  fn r#GetTestService(&self) -> binder::Result<Option<binder::Strong<dyn crate::mangled::_7_android_4_aidl_5_tests_12_ITestService>>> { self.0.r#GetTestService() }
}
fn on_transact(_aidl_service: &dyn ICircular, _aidl_code: binder::binder_impl::TransactionCode, _aidl_data: &binder::binder_impl::BorrowedParcel<'_>, _aidl_reply: &mut binder::binder_impl::BorrowedParcel<'_>) -> std::result::Result<(), binder::StatusCode> {
  match _aidl_code {
    transactions::r#GetTestService => {
      let _aidl_return = _aidl_service.r#GetTestService();
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
pub(crate) mod mangled {
 pub use super::r#ICircular as _7_android_4_aidl_5_tests_9_ICircular;
}
