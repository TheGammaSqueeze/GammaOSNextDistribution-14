//! FFI interfaces for the Connection module.

use std::{fmt::Debug, pin::Pin};

use bt_common::init_flags;
use cxx::UniquePtr;
pub use inner::*;
use log::warn;
use tokio::{
    sync::mpsc::{unbounded_channel, UnboundedSender},
    task::spawn_local,
};

use crate::do_in_rust_thread;

use super::{
    attempt_manager::ConnectionMode,
    le_manager::{ErrorCode, InactiveLeAclManager, LeAclManager, LeAclManagerConnectionCallbacks},
    ConnectionManagerClient, LeConnection,
};

// SAFETY: `LeAclManagerShim` can be passed between threads.
unsafe impl Send for LeAclManagerShim {}

#[cxx::bridge]
#[allow(clippy::needless_lifetimes)]
#[allow(clippy::too_many_arguments)]
#[allow(missing_docs)]
#[allow(unsafe_op_in_unsafe_fn)]
mod inner {
    impl UniquePtr<LeAclManagerShim> {}

    #[namespace = "bluetooth::core"]
    extern "C++" {
        type AddressWithType = crate::core::address::AddressWithType;
    }

    #[namespace = "bluetooth::connection"]
    unsafe extern "C++" {
        include!("src/connection/ffi/connection_shim.h");

        /// This lets us send HCI commands, either directly,
        /// or via the address manager
        type LeAclManagerShim;

        /// Add address to direct/background connect list, if not already connected
        /// If connected, then adding to direct list is a no-op, but adding to the
        /// background list will still take place.
        #[cxx_name = "CreateLeConnection"]
        fn create_le_connection(&self, address: AddressWithType, is_direct: bool);

        /// Remove address from both direct + background connect lists
        #[cxx_name = "CancelLeConnect"]
        fn cancel_le_connect(&self, address: AddressWithType);

        /// Register Rust callbacks for connection events
        ///
        /// # Safety
        /// `callbacks` must be Send + Sync, since C++ moves it to a different thread and
        /// invokes it from several others (GD + legacy threads).
        #[cxx_name = "RegisterRustCallbacks"]
        unsafe fn unchecked_register_rust_callbacks(
            self: Pin<&mut Self>,
            callbacks: Box<LeAclManagerCallbackShim>,
        );
    }

    #[namespace = "bluetooth::connection"]
    extern "Rust" {
        type LeAclManagerCallbackShim;
        #[cxx_name = "OnLeConnectSuccess"]
        fn on_le_connect_success(&self, address: AddressWithType);
        #[cxx_name = "OnLeConnectFail"]
        fn on_le_connect_fail(&self, address: AddressWithType, status: u8);
        #[cxx_name = "OnLeDisconnection"]
        fn on_disconnect(&self, address: AddressWithType);
    }

    #[namespace = "bluetooth::connection"]
    unsafe extern "C++" {
        include!("stack/arbiter/acl_arbiter.h");

        /// Register APIs exposed by Rust
        fn RegisterRustApis(
            start_direct_connection: fn(client_id: u8, address: AddressWithType),
            stop_direct_connection: fn(client_id: u8, address: AddressWithType),
            add_background_connection: fn(client_id: u8, address: AddressWithType),
            remove_background_connection: fn(client_id: u8, address: AddressWithType),
            remove_client: fn(client_id: u8),
            stop_all_connections_to_device: fn(address: AddressWithType),
        );
    }
}

impl LeAclManagerShim {
    fn register_rust_callbacks(
        self: Pin<&mut LeAclManagerShim>,
        callbacks: Box<LeAclManagerCallbackShim>,
    ) where
        Box<LeAclManagerCallbackShim>: Send + Sync,
    {
        // SAFETY: The requirements of this method are enforced
        // by our own trait bounds.
        unsafe {
            self.unchecked_register_rust_callbacks(callbacks);
        }
    }
}

/// Implementation of HciConnectProxy wrapping the corresponding C++ methods
pub struct LeAclManagerImpl(pub UniquePtr<LeAclManagerShim>);

pub struct LeAclManagerCallbackShim(
    UnboundedSender<Box<dyn FnOnce(&dyn LeAclManagerConnectionCallbacks) + Send>>,
);

impl LeAclManagerCallbackShim {
    fn on_le_connect_success(&self, address: AddressWithType) {
        let _ = self.0.send(Box::new(move |callback| {
            callback.on_le_connect(address, Ok(LeConnection { remote_address: address }))
        }));
    }

    fn on_le_connect_fail(&self, address: AddressWithType, status: u8) {
        let _ = self.0.send(Box::new(move |callback| {
            callback.on_le_connect(address, Err(ErrorCode(status)))
        }));
    }

    fn on_disconnect(&self, address: AddressWithType) {
        let _ = self.0.send(Box::new(move |callback| {
            callback.on_disconnect(address);
        }));
    }
}

impl InactiveLeAclManager for LeAclManagerImpl {
    type ActiveManager = Self;

    fn register_callbacks(
        mut self,
        callbacks: impl LeAclManagerConnectionCallbacks + 'static,
    ) -> Self::ActiveManager {
        let (tx, mut rx) = unbounded_channel();

        // only register callbacks if the feature is enabled
        if init_flags::use_unified_connection_manager_is_enabled() {
            self.0.pin_mut().register_rust_callbacks(Box::new(LeAclManagerCallbackShim(tx)));
        }

        spawn_local(async move {
            while let Some(f) = rx.recv().await {
                f(&callbacks)
            }
        });
        self
    }
}

impl LeAclManager for LeAclManagerImpl {
    fn add_to_direct_list(&self, address: AddressWithType) {
        self.0.create_le_connection(address, /* is_direct= */ true)
    }

    fn add_to_background_list(&self, address: AddressWithType) {
        self.0.create_le_connection(address, /* is_direct= */ false)
    }

    fn remove_from_all_lists(&self, address: AddressWithType) {
        self.0.cancel_le_connect(address)
    }
}

impl Debug for LeAclManagerImpl {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_tuple("LeAclManagerImpl").finish()
    }
}

/// Registers all connection-manager callbacks into C++ dependencies
pub fn register_callbacks() {
    RegisterRustApis(
        |client, address| {
            let client = ConnectionManagerClient::GattClient(client);
            do_in_rust_thread(move |modules| {
                let result =
                    modules.connection_manager.as_ref().start_direct_connection(client, address);
                if let Err(err) = result {
                    warn!("Failed to start direct connection from {client:?} to {address:?} ({err:?})")
                }
            });
        },
        |client, address| {
            let client = ConnectionManagerClient::GattClient(client);
            do_in_rust_thread(move |modules| {
                let result = modules.connection_manager.cancel_connection(
                    client,
                    address,
                    ConnectionMode::Direct,
                );
                if let Err(err) = result {
                    warn!("Failed to cancel direct connection from {client:?} to {address:?} ({err:?})")
                }
            })
        },
        |client, address| {
            let client = ConnectionManagerClient::GattClient(client);
            do_in_rust_thread(move |modules| {
                let result = modules.connection_manager.add_background_connection(client, address);
                if let Err(err) = result {
                    warn!("Failed to add background connection from {client:?} to {address:?} ({err:?})")
                }
            })
        },
        |client, address| {
            let client = ConnectionManagerClient::GattClient(client);
            do_in_rust_thread(move |modules| {
                let result = modules.connection_manager.cancel_connection(
                    client,
                    address,
                    ConnectionMode::Background,
                );
                if let Err(err) = result {
                    warn!("Failed to remove background connection from {client:?} to {address:?} ({err:?})")
                }
            })
        },
        |client| {
            let client = ConnectionManagerClient::GattClient(client);
            do_in_rust_thread(move |modules| {
                modules.connection_manager.remove_client(client);
            })
        },
        |address| {
            do_in_rust_thread(move |modules| {
                modules.connection_manager.cancel_unconditionally(address);
            })
        },
    )
}
