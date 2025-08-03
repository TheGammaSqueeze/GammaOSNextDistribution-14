use bt_topshim::btif::BtTransport;
use bt_topshim::profiles::gatt::LePhy;

#[repr(i32)]
#[derive(Debug, Copy, Clone)]
pub enum AuthReq {
    // reference to system/stack/include/gatt_api.h
    NONE = 0,
    EncNoMitm = 1,
    EncMitm = 2,
    SignedNoMitm = 3,
    SignedMitm = 4,
}

impl From<AuthReq> for i32 {
    fn from(auth_req: AuthReq) -> Self {
        auth_req as i32
    }
}

/// User preferenece of GATT operations
pub(crate) struct GattClientContext {
    /// If set, the registered GATT client id. None otherwise.
    pub(crate) client_id: Option<i32>,
    /// Type of authentication requirement
    pub(crate) auth_req: AuthReq,
    /// Is connection going to be directed?
    pub(crate) is_connect_direct: bool,
    /// Transport of connection
    pub(crate) connect_transport: BtTransport,
    /// Is connection going to be opportunistic?
    pub(crate) connect_opportunistic: bool,
    /// Type of connect phy
    pub(crate) connect_phy: LePhy,
}

impl GattClientContext {
    pub(crate) fn new() -> Self {
        GattClientContext {
            client_id: None,
            auth_req: AuthReq::NONE,
            is_connect_direct: false,
            connect_transport: BtTransport::Le,
            connect_opportunistic: false,
            connect_phy: LePhy::Phy1m,
        }
    }

    pub(crate) fn get_auth_req(&self) -> AuthReq {
        self.auth_req
    }
}

/// User preference of GATT server operations
pub(crate) struct GattServerContext {
    /// Is connection going to be directed?
    pub(crate) is_connect_direct: bool,
    /// Transport of connection
    pub(crate) connect_transport: BtTransport,
}

impl GattServerContext {
    pub(crate) fn new() -> Self {
        GattServerContext { is_connect_direct: false, connect_transport: BtTransport::Le }
    }
}
