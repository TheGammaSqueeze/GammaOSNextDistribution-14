//! This represents the TX end of an ATT Transport, to be either mocked (in
//! test) or linked to FFI (in production).

use crate::packets::{AttBuilder, SerializeError};

use super::ids::TransportIndex;

/// An instance of this trait will be provided to the GattModule on
/// initialization.
pub trait AttTransport {
    /// Serializes and sends a packet to the device associated with the
    /// specified transport. Note that the packet may be dropped if the link
    /// is disconnected, but the result will still be Ok(()).
    ///
    /// The tcb_idx is an identifier for this transport supplied from the
    /// native stack, and represents an underlying ACL-LE connection.
    fn send_packet(
        &self,
        tcb_idx: TransportIndex,
        packet: AttBuilder,
    ) -> Result<(), SerializeError>;
}
