//! These are strongly-typed identifiers representing the various objects
//! interacted with, mostly over FFI

/// The ID of a connection at the GATT layer.
/// A ConnectionId is logically a (TransportIndex, ServerId) tuple,
/// where each contribute 8 bits to the 16-bit value.
#[derive(Debug, Copy, Clone, PartialEq, Hash, Eq, PartialOrd, Ord)]
pub struct ConnectionId(pub u16);

impl ConnectionId {
    /// Create a ConnectionId from a TransportIndex and ServerId
    pub const fn new(tcb_idx: TransportIndex, server_id: ServerId) -> ConnectionId {
        ConnectionId(((tcb_idx.0 as u16) << 8) + (server_id.0 as u16))
    }

    /// Extract the TransportIndex from a ConnectionId (upper 8 bits)
    pub fn get_tcb_idx(&self) -> TransportIndex {
        TransportIndex((self.0 >> 8) as u8)
    }

    /// Extract the ServerId from a ConnectionId (lower 8 bits)
    pub fn get_server_id(&self) -> ServerId {
        ServerId((self.0 & (u8::MAX as u16)) as u8)
    }
}

/// The server_if of a GATT server registered in legacy
#[derive(Debug, Copy, Clone, PartialEq, Hash, Eq)]
pub struct ServerId(pub u8);

/// An arbitrary id representing a GATT transaction (request/response)
#[derive(Debug, Copy, Clone, PartialEq, Hash, Eq)]
pub struct TransactionId(pub u32);

/// The TCB index in legacy GATT
#[derive(Debug, Copy, Clone, PartialEq, Hash, Eq)]
pub struct TransportIndex(pub u8);

/// An advertising set ID (zero-based)
#[derive(Debug, Copy, Clone, PartialEq, Hash, Eq)]
pub struct AdvertiserId(pub u8);

/// The handle of a given ATT attribute
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct AttHandle(pub u16);

impl AttHandle {
    /// The (only) reserved AttHandle
    pub const RESERVED: Self = AttHandle(0);
    /// The smallest valid AttHandle
    pub const MIN: Self = AttHandle(1);
    /// The largest valid AttHandle
    pub const MAX: Self = AttHandle(0xFFFF);
}
