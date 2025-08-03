use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::convert::{TryFrom, TryInto};
use std::ffi::CString;
use std::fs::File;
use std::os::unix::io::FromRawFd;

use crate::bindings::root as bindings;
use crate::btif::{BluetoothInterface, BtStatus, RawAddress, SupportedProfiles, Uuid, Uuid128Bit};
use crate::ccall;
use crate::utils::{LTCheckedPtr, LTCheckedPtrMut};

#[derive(Clone, Debug, FromPrimitive, ToPrimitive)]
#[repr(u32)]
/// Socket interface type.
pub enum SocketType {
    /// Unknown socket type value.
    Unknown = 0,

    Rfcomm = 1,
    Sco = 2,
    L2cap = 3,
    L2capLe = 4,
}

impl From<bindings::btsock_type_t> for SocketType {
    fn from(item: bindings::btsock_type_t) -> Self {
        SocketType::from_u32(item).unwrap_or(SocketType::Unknown)
    }
}

impl From<SocketType> for bindings::btsock_type_t {
    fn from(item: SocketType) -> Self {
        item.to_u32().unwrap_or(0)
    }
}

/// Socket flag: No flags (used for insecure connections).
pub const SOCK_FLAG_NONE: i32 = 0;
/// Socket flag: connection must be encrypted.
pub const SOCK_FLAG_ENCRYPT: i32 = 1 << 0;
/// Socket flag: require authentication.
pub const SOCK_FLAG_AUTH: i32 = 1 << 1;
/// Socket flag: don't generate SDP entry for listening socket.
pub const SOCK_FLAG_NO_SDP: i32 = 1 << 2;
/// Socket flag: require authentication with MITM protection.
pub const SOCK_FLAG_AUTH_MITM: i32 = 1 << 3;
/// Socket flag: require a minimum of 16 digits for sec mode 2 connections.
pub const SOCK_FLAG_AUTH_16_DIGIT: i32 = 1 << 4;
/// Socket flag: LE connection oriented channel.
pub const SOCK_FLAG_LE_COC: i32 = 1 << 5;

/// Combination of SOCK_FLAG_ENCRYPT and SOCK_FLAG_AUTH.
pub const SOCK_META_FLAG_SECURE: i32 = SOCK_FLAG_ENCRYPT | SOCK_FLAG_AUTH;

/// Struct showing a completed socket event. This is the first data that should
/// arrive on a connecting socket once it is connected.
pub struct ConnectionComplete {
    pub size: u16,
    pub addr: RawAddress,
    pub channel: i32,
    pub status: i32,
    pub max_tx_packet_size: u16,
    pub max_rx_packet_size: u16,
}

/// Size of connect complete data. The data read from libbluetooth is packed but
/// the Rust struct is not, so depend on this value for validation.
pub const CONNECT_COMPLETE_SIZE: usize = 20;

// Convert from raw bytes to struct.
impl TryFrom<&[u8]> for ConnectionComplete {
    type Error = String;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        if bytes.len() != CONNECT_COMPLETE_SIZE {
            return Err(format!("Wrong number of bytes for Connection Complete: {}", bytes.len()));
        }

        // The ConnectComplete event is constructed within libbluetooth and uses
        // the native endianness of the machine when writing to the socket. When
        // parsing, make sure to use native endianness here.
        let (size_bytes, rest) = bytes.split_at(std::mem::size_of::<u16>());
        if u16::from_ne_bytes(size_bytes.clone().try_into().unwrap())
            != (CONNECT_COMPLETE_SIZE as u16)
        {
            return Err(format!("Wrong size in Connection Complete: {:?}", size_bytes));
        }

        // We know from previous size checks that all these splits will work.
        let (addr_bytes, rest) = rest.split_at(std::mem::size_of::<RawAddress>());
        let (channel_bytes, rest) = rest.split_at(std::mem::size_of::<i32>());
        let (status_bytes, rest) = rest.split_at(std::mem::size_of::<i32>());
        let (max_tx_packet_size_bytes, rest) = rest.split_at(std::mem::size_of::<u16>());
        let (max_rx_packet_size_bytes, _unused) = rest.split_at(std::mem::size_of::<u16>());

        let addr = match RawAddress::from_bytes(addr_bytes) {
            Some(v) => v,
            None => {
                return Err("Invalid address in Connection Complete".into());
            }
        };

        Ok(ConnectionComplete {
            size: CONNECT_COMPLETE_SIZE.try_into().unwrap_or_default(),
            addr,
            channel: i32::from_ne_bytes(channel_bytes.try_into().unwrap()),
            status: i32::from_ne_bytes(status_bytes.try_into().unwrap()),
            max_tx_packet_size: u16::from_ne_bytes(max_tx_packet_size_bytes.try_into().unwrap()),
            max_rx_packet_size: u16::from_ne_bytes(max_rx_packet_size_bytes.try_into().unwrap()),
        })
    }
}

/// Represents the standard BT SOCKET interface.
///
/// For parameter documentation, see the type |sock_connect_signal_t|.
pub type SocketConnectSignal = bindings::sock_connect_signal_t;

struct RawBtSockWrapper {
    raw: *const bindings::btsock_interface_t,
}

// Pointers unsafe due to ownership but this is a static pointer so Send is ok.
unsafe impl Send for RawBtSockWrapper {}

/// Bluetooth socket interface wrapper. This allows creation of RFCOMM and L2CAP sockets.
/// For documentation of functions, see definition of |btsock_interface_t|.
pub struct BtSocket {
    internal: RawBtSockWrapper,
}

pub type FdError = &'static str;

pub fn try_from_fd(fd: i32) -> Result<File, FdError> {
    if fd >= 0 {
        Ok(unsafe { File::from_raw_fd(fd) })
    } else {
        Err("Invalid FD")
    }
}

impl BtSocket {
    pub fn new(intf: &BluetoothInterface) -> Self {
        let r = intf.get_profile_interface(SupportedProfiles::Socket);
        BtSocket { internal: RawBtSockWrapper { raw: r as *const bindings::btsock_interface_t } }
    }

    pub fn listen(
        &self,
        sock_type: SocketType,
        service_name: String,
        service_uuid: Option<Uuid128Bit>,
        channel: i32,
        flags: i32,
        calling_uid: i32,
    ) -> (BtStatus, Result<File, FdError>) {
        let mut sockfd: i32 = -1;
        let sockfd_ptr = LTCheckedPtrMut::from_ref(&mut sockfd);

        let uuid = match service_uuid {
            Some(uu) => Some(Uuid::from(uu)),
            None => Some(Uuid::from([0; 16])),
        };
        let uuid_ptr = LTCheckedPtr::from(&uuid);

        let name = CString::new(service_name).expect("Service name has null in it.");
        let name_ptr = LTCheckedPtr::from(&name);

        let status: BtStatus = ccall!(
            self,
            listen,
            sock_type.into(),
            name_ptr.into(),
            uuid_ptr.into(),
            channel,
            sockfd_ptr.into(),
            flags,
            calling_uid
        )
        .into();

        (status, try_from_fd(sockfd))
    }

    pub fn connect(
        &self,
        addr: RawAddress,
        sock_type: SocketType,
        service_uuid: Option<Uuid128Bit>,
        channel: i32,
        flags: i32,
        calling_uid: i32,
    ) -> (BtStatus, Result<File, FdError>) {
        let mut sockfd: i32 = -1;
        let sockfd_ptr = LTCheckedPtrMut::from_ref(&mut sockfd);

        let uuid = match service_uuid {
            Some(uu) => Some(Uuid::from(uu)),
            None => None,
        };
        let uuid_ptr = LTCheckedPtr::from(&uuid);

        let addr_ptr = LTCheckedPtr::from_ref(&addr);

        let status: BtStatus = ccall!(
            self,
            connect,
            addr_ptr.into(),
            sock_type.into(),
            uuid_ptr.into(),
            channel,
            sockfd_ptr.into(),
            flags,
            calling_uid
        )
        .into();

        (status, try_from_fd(sockfd))
    }

    pub fn request_max_tx_data_length(&self, addr: RawAddress) {
        ccall!(self, request_max_tx_data_length, &addr);
    }

    pub fn send_msc(&self, dlci: u8, addr: RawAddress) -> BtStatus {
        // PORT_DTRDSR_ON | PORT_CTSRTS_ON | PORT_DCD_ON
        const DEFAULT_MODEM_SIGNAL: u8 = 0x01 | 0x02 | 0x08;

        const DEFAULT_BREAK_SIGNAL: u8 = 0;
        const DEFAULT_DISCARD_BUFFERS: u8 = 0;
        const DEFAULT_BREAK_SIGNAL_SEQ: u8 = 1; // In sequence.

        // In RFCOMM/DEVA-DEVB/RFC/BV-21-C and RFCOMM/DEVA-DEVB/RFC/BV-22-C test flow
        // we are requested to send an MSC command with FC=0.
        const FC: bool = false;

        ccall!(
            self,
            control_req,
            dlci,
            &addr,
            DEFAULT_MODEM_SIGNAL,
            DEFAULT_BREAK_SIGNAL,
            DEFAULT_DISCARD_BUFFERS,
            DEFAULT_BREAK_SIGNAL_SEQ,
            FC
        )
        .into()
    }

    pub fn disconnect_all(&self, addr: RawAddress) -> BtStatus {
        ccall!(self, disconnect_all, &addr).into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_conncomplete_parsing() {
        // Actual slice size doesn't match
        let small_input = [0u8; 18];
        let large_input = [0u8; 21];

        assert_eq!(false, ConnectionComplete::try_from(&small_input[0..]).is_ok());
        assert_eq!(false, ConnectionComplete::try_from(&large_input[0..]).is_ok());

        // Size param in slice doesn't match.
        let mut size_no_match = vec![0x0u8, 0x13u8];
        size_no_match.extend([0u8; 18]);

        assert_eq!(false, ConnectionComplete::try_from(size_no_match.as_slice()).is_ok());

        // Valid input with various values.
        let raw_addr = RawAddress { address: [0x1, 0x2, 0x3, 0x4, 0x5, 0x6] };
        let mut valid: Vec<u8> = vec![];
        valid.extend(u16::to_ne_bytes(20));
        valid.extend(raw_addr.to_byte_arr());
        valid.extend(i32::to_ne_bytes(1));
        valid.extend(i32::to_ne_bytes(5));
        valid.extend(u16::to_ne_bytes(16));
        valid.extend(u16::to_ne_bytes(17));

        let result = ConnectionComplete::try_from(valid.as_slice());
        assert_eq!(true, result.is_ok());

        if let Ok(cc) = result {
            assert_eq!(cc.size, 20u16);
            assert_eq!(cc.channel, 1);
            assert_eq!(cc.status, 5);
            assert_eq!(cc.max_tx_packet_size, 16u16);
            assert_eq!(cc.max_rx_packet_size, 17u16);
        }
    }
}
