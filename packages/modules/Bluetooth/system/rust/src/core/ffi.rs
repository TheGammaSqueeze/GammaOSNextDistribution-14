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

// TODO(b/290018030): Remove this and add proper safety comments.
#![allow(clippy::undocumented_unsafe_blocks)]

use crate::core::{start, stop};

use cxx::{type_id, ExternType};
pub use inner::*;

// SAFETY: `GattServerCallbacks` can be passed between threads.
unsafe impl Send for GattServerCallbacks {}

// SAFETY: `future_t` can be passed between threads.
unsafe impl Send for Future {}

unsafe impl ExternType for Uuid {
    type Id = type_id!("bluetooth::Uuid");
    type Kind = cxx::kind::Trivial;
}

unsafe impl ExternType for AddressWithType {
    type Id = type_id!("bluetooth::core::AddressWithType");
    type Kind = cxx::kind::Trivial;
}

#[allow(dead_code, missing_docs, unsafe_op_in_unsafe_fn)]
#[cxx::bridge]
mod inner {
    #[derive(Debug)]
    pub enum AddressTypeForFFI {
        Public,
        Random,
    }

    unsafe extern "C++" {
        include!("osi/include/future.h");
        include!("src/core/ffi/module.h");

        #[cxx_name = "future_t"]
        type Future;

        #[namespace = "bluetooth::rust_shim"]
        #[cxx_name = "FutureReady"]
        fn future_ready(future: Pin<&mut Future>);
    }

    #[namespace = "bluetooth::core"]
    extern "C++" {
        include!("src/core/ffi/types.h");
        type AddressWithType = crate::core::address::AddressWithType;
    }

    #[namespace = "bluetooth"]
    extern "C++" {
        include!("bluetooth/uuid.h");
        type Uuid = crate::core::uuid::Uuid;
    }

    #[namespace = "bluetooth::gatt"]
    unsafe extern "C++" {
        include!("src/gatt/ffi/gatt_shim.h");
        type GattServerCallbacks = crate::gatt::GattServerCallbacks;
    }

    #[namespace = "bluetooth::connection"]
    unsafe extern "C++" {
        include!("src/connection/ffi/connection_shim.h");
        type LeAclManagerShim = crate::connection::LeAclManagerShim;
    }

    #[namespace = "bluetooth::rust_shim"]
    extern "Rust" {
        fn start(
            gatt_server_callbacks: UniquePtr<GattServerCallbacks>,
            le_acl_manager: UniquePtr<LeAclManagerShim>,
            on_started: Pin<&'static mut Future>,
        );

        fn stop();
    }
}
