// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! The core event loop for Rust modules. Here Rust modules are started in
//! dependency order.

use bt_common::init_flags::rust_event_loop_is_enabled;
use connection::le_manager::InactiveLeAclManager;
use gatt::{channel::AttTransport, GattCallbacks};
use log::{info, warn};
use tokio::task::LocalSet;

use self::core::shared_box::SharedBox;
use std::{rc::Rc, sync::Mutex};
use tokio::runtime::Builder;

use tokio::sync::mpsc;

#[cfg(feature = "via_android_bp")]
mod do_not_use {
    // DO NOT USE
    #[allow(unused)]
    use bt_shim::*;
}

pub mod connection;
pub mod core;
pub mod gatt;
pub mod packets;
pub mod utils;

/// The owner of the main Rust thread on which all Rust modules run
struct GlobalModuleRegistry {
    pub task_tx: MainThreadTx,
}

/// The ModuleViews lets us access all publicly accessible Rust modules from
/// Java / C++ while the stack is running. If a module should not be exposed
/// outside of Rust GD, there is no need to include it here.
pub struct ModuleViews<'a> {
    /// Lets us call out into C++
    pub gatt_outgoing_callbacks: Rc<dyn GattCallbacks>,
    /// Receives synchronous callbacks from JNI
    pub gatt_incoming_callbacks: Rc<gatt::callbacks::CallbackTransactionManager>,
    /// Proxies calls into GATT server
    pub gatt_module: &'a mut gatt::server::GattModule,
    /// Proxies calls into connection manager
    pub connection_manager: SharedBox<connection::ConnectionManager>,
}

static GLOBAL_MODULE_REGISTRY: Mutex<Option<GlobalModuleRegistry>> = Mutex::new(None);

impl GlobalModuleRegistry {
    /// Handles bringup of all Rust modules. This occurs after GD C++ modules
    /// have started, but before the legacy stack has initialized.
    /// Must be invoked from the Rust thread after JNI initializes it and passes
    /// in JNI modules.
    pub fn start(
        gatt_callbacks: Rc<dyn GattCallbacks>,
        att_transport: Rc<dyn AttTransport>,
        le_acl_manager: impl InactiveLeAclManager,
        on_started: impl FnOnce(),
    ) {
        info!("starting Rust modules");
        let rt = Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("failed to start tokio runtime");
        let local = LocalSet::new();

        let (tx, mut rx) = mpsc::unbounded_channel();
        let prev_registry = GLOBAL_MODULE_REGISTRY.lock().unwrap().replace(Self { task_tx: tx });

        // initialization should only happen once
        assert!(prev_registry.is_none());

        // First, setup FFI and C++ modules
        let arbiter = gatt::arbiter::initialize_arbiter();
        connection::register_callbacks();

        // Now enter the runtime
        local.block_on(&rt, async move {
            // Then follow the pure-Rust modules
            let gatt_incoming_callbacks =
                Rc::new(gatt::callbacks::CallbackTransactionManager::new(gatt_callbacks.clone()));
            let gatt_module = &mut gatt::server::GattModule::new(att_transport.clone(), arbiter);

            let connection_manager = connection::ConnectionManager::new(le_acl_manager);

            // All modules that are visible from incoming JNI / top-level interfaces should
            // be exposed here
            let mut modules = ModuleViews {
                gatt_outgoing_callbacks: gatt_callbacks,
                gatt_incoming_callbacks,
                gatt_module,
                connection_manager,
            };

            // notify upper layer that we are ready to receive messages
            on_started();

            // This is the core event loop that serializes incoming requests into the Rust
            // thread do_in_rust_thread lets us post into here from foreign
            // threads
            info!("starting Tokio event loop");
            while let Some(message) = rx.recv().await {
                match message {
                    MainThreadTxMessage::Callback(f) => f(&mut modules),
                    MainThreadTxMessage::Stop => {
                        break;
                    }
                }
            }
        });
        warn!("Rust thread queue has stopped, shutting down executor thread");
        GLOBAL_MODULE_REGISTRY.lock().unwrap().take();
        gatt::arbiter::clean_arbiter();
    }
}

type BoxedMainThreadCallback = Box<dyn for<'a> FnOnce(&'a mut ModuleViews) + Send + 'static>;
enum MainThreadTxMessage {
    Callback(BoxedMainThreadCallback),
    Stop,
}
type MainThreadTx = mpsc::UnboundedSender<MainThreadTxMessage>;

thread_local! {
    /// The TX end of a channel into the Rust thread, so external callers can
    /// access Rust modules. JNI / direct FFI should use do_in_rust_thread for
    /// convenience, but objects passed into C++ as callbacks should
    /// clone this channel to fail loudly if it's not yet initialized.
    ///
    /// This will be lazily initialized on first use from each client thread
    static MAIN_THREAD_TX: MainThreadTx =
        GLOBAL_MODULE_REGISTRY.lock().unwrap().as_ref().expect("stack not initialized").task_tx.clone();
}

/// Posts a callback to the Rust thread and gives it access to public Rust
/// modules, used from JNI.
///
/// Do not call this from Rust modules / the Rust thread! Instead, Rust modules
/// should receive references to their dependent modules at startup. If passing
/// callbacks into C++, don't use this method either - instead, acquire a clone
/// of MAIN_THREAD_TX when the callback is created. This ensures that there
/// never are "invalid" callbacks that may still work depending on when the
/// GLOBAL_MODULE_REGISTRY is initialized.
pub fn do_in_rust_thread<F>(f: F)
where
    F: for<'a> FnOnce(&'a mut ModuleViews) + Send + 'static,
{
    if !rust_event_loop_is_enabled() {
        warn!("ignoring do_in_rust_thread() invocation since Rust loop is inactive");
        return;
    }
    let ret = MAIN_THREAD_TX.with(|tx| tx.send(MainThreadTxMessage::Callback(Box::new(f))));
    if ret.is_err() {
        panic!("Rust call failed");
    }
}
