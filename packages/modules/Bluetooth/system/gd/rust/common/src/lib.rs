//! Bluetooth common library

/// Provides waking timer abstractions
pub mod time;

/// Provides parameters
pub mod parameter_provider;

pub mod bridge;

#[macro_use]
mod ready;

#[cfg(test)]
#[macro_use]
mod asserts;

/// Provides runtime configured-at-startup flags
pub mod init_flags;

/// Provides runtime configured system properties. Stubbed for non-Android.
pub mod sys_prop;

mod logging;
pub use logging::*;

/// Indicates the object can be converted to a GRPC service
pub trait GrpcFacade {
    /// Convert the object into the service
    fn into_grpc(self) -> grpcio::Service;
}

/// Useful for distinguishing between BT classic & LE in functions that support both
#[derive(Debug, Clone, Copy)]
pub enum Bluetooth {
    /// Classic BT we all know and love, started in the 90s.
    Classic,
    /// Bluetooth low energy from the 2010s. Also known as BLE, BTLE, etc.
    Le,
}
