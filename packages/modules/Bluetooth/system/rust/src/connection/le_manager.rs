//! This trait represents the lower-level operations
//! made available to the connection manager. In particular,
//! we can add devices to either the "direct" or "background"
//! connect list, which are in turn mapped to an appropriate choice
//! of scan parameters / the filter accept list.
//!
//! Note that the ACL manager is unaware of address resolution,
//! so this must be handled by the connection manager. Conversely, the connection
//! manager does not need to consider the HCI state machine, and can send requests
//! at any time.
//!
//! In addition to the supplied API, when a connection completes to a peer device,
//! it is removed from the "direct" connect list (based on exact address match).

use std::fmt::Debug;

use crate::core::address::AddressWithType;

use super::LeConnection;

/// An HCI Error Code from the controller
#[derive(Copy, Clone, Eq, PartialEq, Debug)]
pub struct ErrorCode(pub u8);

impl ErrorCode {
    /// Operation completed successfully
    pub const SUCCESS: Self = ErrorCode(0);
}

/// The LeAclManager before callbacks are registered
pub trait InactiveLeAclManager {
    /// The type implementing LeAclManager once callbacks are registered
    type ActiveManager: LeAclManager + 'static;

    /// Register callbacks for connection events, and produuce an ActiveManager
    fn register_callbacks(
        self,
        callbacks: impl LeAclManagerConnectionCallbacks + 'static,
    ) -> Self::ActiveManager;
}

/// The operations provided by GD AclManager to the connection manager
pub trait LeAclManager: Debug {
    /// Adds an address to the direct connect list, if not already connected.
    /// WARNING: the connection timeout is set the FIRST time the address is added, and is
    /// NOT RESET! TODO(aryarahul): remove connection timeout from le_impl since it belongs here instead
    /// Precondition: Must NOT be currently connected to this adddress (if connected due to race, is a no-op)
    fn add_to_direct_list(&self, address: AddressWithType); // CreateLeConnection(is_direct=true)
    /// Adds an address to the background connect list
    fn add_to_background_list(&self, address: AddressWithType); // CreateLeConnection(is_direct=false)
    /// Removes address from both the direct + background connect lists
    /// Due to races, it is possible to call this, and THEN get a connection complete with us as central
    fn remove_from_all_lists(&self, address: AddressWithType); // CancelLeConnect
}

/// The callbacks invoked by the LeAclManager in response to events from the controller
pub trait LeAclManagerConnectionCallbacks {
    /// Invoked when an LE connection to a given address completes
    fn on_le_connect(&self, address: AddressWithType, result: Result<LeConnection, ErrorCode>);
    /// Invoked when a peer device disconnects from us. The address must match the address
    /// supplied on the initial connection.
    fn on_disconnect(&self, address: AddressWithType);
}
