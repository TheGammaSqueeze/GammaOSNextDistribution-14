//! This module is a simple GATT server that shares the ATT channel with the
//! existing C++ GATT client. See go/private-gatt-in-platform for the design.

pub mod arbiter;
pub mod callbacks;
pub mod channel;
pub mod ffi;
pub mod ids;
pub mod mocks;
mod mtu;
pub mod opcode_types;
pub mod server;

pub use self::callbacks::GattCallbacks;

pub use ffi::GattServerCallbacks;
