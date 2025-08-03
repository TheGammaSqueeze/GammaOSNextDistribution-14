//! Stack on top of the Bluetooth interface shim
//!
//! Helpers for dealing with the stack on top of the Bluetooth interface.

use lazy_static::lazy_static;
use std::any::{Any, TypeId};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tokio::runtime::{Builder, Runtime};

lazy_static! {
    // Shared runtime for topshim handlers. All async tasks will get run by this
    // runtime and this will properly serialize all spawned tasks.
    pub static ref RUNTIME: Arc<Runtime> = Arc::new(
        Builder::new_multi_thread()
            .worker_threads(1)
            .max_blocking_threads(1)
            .enable_all()
            .build()
            .unwrap()
    );
}

pub fn get_runtime() -> Arc<Runtime> {
    RUNTIME.clone()
}

lazy_static! {
    static ref CB_DISPATCHER: Arc<Mutex<DispatchContainer>> =
        Arc::new(Mutex::new(DispatchContainer { instances: HashMap::new() }));
}

/// A Box-ed struct that implements a `dispatch` fn.
///
///  Example:
///  ```
///  use std::sync::Arc;
///  use std::sync::Mutex;
///
///  #[derive(Debug)]
///  enum Foo {
///     First(i16),
///     Second(i32),
///  }
///
///  struct FooDispatcher {
///     dispatch: Box<dyn Fn(Foo) + Send>,
///  }
///
///  fn main() {
///     let foo_dispatcher = FooDispatcher {
///         dispatch: Box::new(move |value| {
///             println!("Dispatch {:?}", value);
///         })
///     };
///     let value = Arc::new(Mutex::new(foo_dispatcher));
///     let instance_box = Box::new(value);
///  }
///  ```
pub type InstanceBox = Box<dyn Any + Send + Sync>;

/// Manage enum dispatches for emulating callbacks.
///
/// Libbluetooth is highly callback based but our Rust code prefers using
/// channels. To reconcile these two systems, we pass static callbacks to
/// libbluetooth that convert callback args into an enum variant and call the
/// dispatcher for that enum. The dispatcher will then queue that enum into the
/// channel (using a captured channel tx in the closure).
pub struct DispatchContainer {
    instances: HashMap<TypeId, InstanceBox>,
}

impl DispatchContainer {
    /// Find registered dispatcher for enum specialization.
    ///
    /// # Return
    ///
    /// Returns an Option with a dispatcher object (the contents of
    /// [`InstanceBox`]).
    pub fn get<T: 'static + Clone + Send + Sync>(&self) -> Option<T> {
        let typeid = TypeId::of::<T>();

        if let Some(value) = self.instances.get(&typeid) {
            return Some(value.downcast_ref::<T>().unwrap().clone());
        }

        None
    }

    /// Set dispatcher for an enum specialization.
    ///
    /// # Arguments
    ///
    /// * `obj` - The contents of [`InstanceBox`], usually `Arc<Mutex<U>>`. See
    ///           the [`InstanceBox`] documentation for examples.
    ///
    /// # Returns
    ///
    /// True if this is replacing an existing enum dispatcher.
    pub fn set<T: 'static + Clone + Send + Sync>(&mut self, obj: T) -> bool {
        self.instances.insert(TypeId::of::<T>(), Box::new(obj)).is_some()
    }
}

/// Take a clone of the static dispatcher container.
pub fn get_dispatchers() -> Arc<Mutex<DispatchContainer>> {
    CB_DISPATCHER.clone()
}
