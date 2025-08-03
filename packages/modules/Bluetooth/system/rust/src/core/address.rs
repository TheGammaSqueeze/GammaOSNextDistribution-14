//! An address with type (public / random)

#[derive(Copy, Clone, Debug, Hash, PartialEq, Eq)]
#[repr(u8)]
/// The type of an LE address (see: 5.3 Vol 6B 1.3 Device Axddress)
pub enum AddressType {
    /// A public address
    Public = 0x0,
    /// A random address (either random static or private)
    Random = 0x1,
}

/// An LE address
#[derive(Copy, Clone, Debug, Hash, PartialEq, Eq)]
#[repr(C)]
pub struct AddressWithType {
    /// The 6 address bytes stored in little-endian format
    pub address: [u8; 6],
    /// The address type, either public or random
    pub address_type: AddressType,
}

impl AddressWithType {
    /// An empty/invalid address
    pub const EMPTY: Self = Self { address: [0, 0, 0, 0, 0, 0], address_type: AddressType::Public };
}
