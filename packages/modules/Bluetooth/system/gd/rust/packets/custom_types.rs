//! custom types to be imported by hci packet pdl
//! (since hci depends on the packet library, we need to split these out)

use std::convert::TryFrom;
use std::fmt;

/// Signal for "empty" address
pub const EMPTY_ADDRESS: Address = Address { bytes: [0x00, 0x00, 0x00, 0x00, 0x00, 0x00] };
/// Signal for "any" address
pub const ANY_ADDRESS: Address = Address { bytes: [0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF] };

/// A Bluetooth address
#[derive(Clone, Copy, Eq, PartialEq, Hash, Ord, PartialOrd, Debug)]
pub struct Address {
    /// the actual bytes representing this address
    pub bytes: [u8; 6],
}

impl Address {
    /// whether this address is empty
    pub fn is_empty(&self) -> bool {
        *self == EMPTY_ADDRESS
    }
}

impl fmt::Display for Address {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
            self.bytes[5],
            self.bytes[4],
            self.bytes[3],
            self.bytes[2],
            self.bytes[1],
            self.bytes[0]
        )
    }
}

/// When you parse an address and it's not valid
#[derive(Debug, Clone)]
pub struct InvalidAddressError;

impl TryFrom<&[u8]> for Address {
    type Error = InvalidAddressError;

    fn try_from(slice: &[u8]) -> Result<Self, Self::Error> {
        if slice.len() == 6 {
            match <[u8; 6]>::try_from(slice) {
                Ok(bytes) => Ok(Self { bytes }),
                Err(_) => Err(InvalidAddressError),
            }
        } else {
            Err(InvalidAddressError)
        }
    }
}

impl From<Address> for [u8; 6] {
    fn from(addr: Address) -> [u8; 6] {
        addr.bytes
    }
}

/// A Bluetooth class of device
#[derive(Clone, Eq, Copy, PartialEq, Hash, Ord, PartialOrd, Debug)]
pub struct ClassOfDevice {
    /// the actual bytes representing this class of device
    pub bytes: [u8; 3],
}

impl fmt::Display for ClassOfDevice {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{:03X}-{:01X}-{:02X}",
            ((self.bytes[2] as u16) << 4) | ((self.bytes[1] as u16) >> 4),
            self.bytes[1] & 0x0F,
            self.bytes[0]
        )
    }
}

/// When you parse a class of device and it's not valid
#[derive(Debug, Clone)]
pub struct InvalidClassOfDeviceError;

impl TryFrom<&[u8]> for ClassOfDevice {
    type Error = InvalidClassOfDeviceError;

    fn try_from(slice: &[u8]) -> Result<Self, Self::Error> {
        if slice.len() == 3 {
            match <[u8; 3]>::try_from(slice) {
                Ok(bytes) => Ok(Self { bytes }),
                Err(_) => Err(InvalidClassOfDeviceError),
            }
        } else {
            Err(InvalidClassOfDeviceError)
        }
    }
}

impl From<ClassOfDevice> for [u8; 3] {
    fn from(cod: ClassOfDevice) -> [u8; 3] {
        cod.bytes
    }
}
