//! This module determines which GATT server should be exposed to a given connection.

use std::collections::HashMap;

use log::{error, info};

use crate::gatt::ids::{AdvertiserId, ServerId, TransportIndex};

/// This class is responsible for tracking which connections and advertising we
/// own, and using this information to decide what servers should be exposed to
/// a given connetion.
#[derive(Default)]
pub struct IsolationManager {
    advertiser_to_server: HashMap<AdvertiserId, ServerId>,
    transport_to_server: HashMap<TransportIndex, ServerId>,
}

impl IsolationManager {
    /// Constructor
    pub fn new() -> Self {
        IsolationManager {
            advertiser_to_server: HashMap::new(),
            transport_to_server: HashMap::new(),
        }
    }

    /// Link a given GATT server to an LE advertising set, so incoming
    /// connections to this advertiser will be visible only by the linked
    /// server
    pub fn associate_server_with_advertiser(
        &mut self,
        server_id: ServerId,
        advertiser_id: AdvertiserId,
    ) {
        info!("associating server {server_id:?} with advertising set {advertiser_id:?}");
        let old = self.advertiser_to_server.insert(advertiser_id, server_id);
        if let Some(old) = old {
            error!("new server {server_id:?} associated with same advertiser {advertiser_id:?}, displacing old server {old:?}");
        }
    }

    /// Clear the server associated with this advertiser, if one exists
    pub fn clear_advertiser(&mut self, advertiser_id: AdvertiserId) {
        info!("removing server (if any) associated with advertiser {advertiser_id:?}");
        self.advertiser_to_server.remove(&advertiser_id);
    }

    /// Check if this transport is currently owned by the Rust stack
    pub fn is_connection_isolated(&self, tcb_idx: TransportIndex) -> bool {
        self.transport_to_server.contains_key(&tcb_idx)
    }

    /// Check if this advertiser is tied to a private server
    pub fn is_advertiser_isolated(&self, advertiser_id: AdvertiserId) -> bool {
        self.advertiser_to_server.contains_key(&advertiser_id)
    }

    /// Look up the server_id tied to a given tcb_idx, if one exists
    pub fn get_server_id(&self, tcb_idx: TransportIndex) -> Option<ServerId> {
        self.transport_to_server.get(&tcb_idx).copied()
    }

    /// Remove all linked advertising sets from the provided server
    ///
    /// This is invoked by the GATT server module, not separately from the upper layer.
    pub fn clear_server(&mut self, server_id: ServerId) {
        info!("clearing advertisers associated with {server_id:?}");
        self.advertiser_to_server.retain(|_, server| *server != server_id);
    }

    /// Handles an incoming connection
    ///
    /// This event should be supplied from the enclosing module, not directly from the upper layer.
    pub fn on_le_connect(&mut self, tcb_idx: TransportIndex, advertiser: Option<AdvertiserId>) {
        info!(
            "processing incoming connection on transport {tcb_idx:?} to advertiser {advertiser:?}"
        );
        let Some(advertiser) = advertiser else {
            info!("processing outgoing connection, granting access to all servers");
            return;
        };
        let Some(server_id) = self.advertiser_to_server.get(&advertiser).copied() else {
            info!("connection can access all servers");
            return;
        };
        info!("connection is isolated to server {server_id:?}");
        let old = self.transport_to_server.insert(tcb_idx, server_id);
        if old.is_some() {
            error!("new server {server_id:?} on transport {tcb_idx:?} displacing existing server {server_id:?}")
        }
    }

    /// Handle a disconnection
    ///
    /// This event should be supplied from the enclosing module, not directly from the upper layer.
    pub fn on_le_disconnect(&mut self, tcb_idx: TransportIndex) {
        info!("processing disconnection on transport {tcb_idx:?}");
        self.transport_to_server.remove(&tcb_idx);
    }
}

#[cfg(test)]
mod test {
    use super::*;

    const TCB_IDX: TransportIndex = TransportIndex(1);
    const ADVERTISER_ID: AdvertiserId = AdvertiserId(3);
    const SERVER_ID: ServerId = ServerId(4);

    const ANOTHER_ADVERTISER_ID: AdvertiserId = AdvertiserId(5);

    #[test]
    fn test_non_isolated_connect() {
        let mut isolation_manager = IsolationManager::new();

        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        assert!(server_id.is_none())
    }

    #[test]
    fn test_isolated_connect() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);

        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        assert_eq!(server_id, Some(SERVER_ID));
    }

    #[test]
    fn test_non_isolated_connect_with_isolated_advertiser() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);

        isolation_manager.on_le_connect(TCB_IDX, Some(ANOTHER_ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        assert!(server_id.is_none())
    }

    #[test]
    fn test_advertiser_id_reuse() {
        let mut isolation_manager = IsolationManager::new();
        // start an advertiser associated with the server, then kill the advertiser
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.clear_advertiser(ADVERTISER_ID);

        // a new advertiser appeared with the same ID and got a connection
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        // but we should not be isolated since this is a new advertiser reusing the old
        // ID
        assert!(server_id.is_none())
    }

    #[test]
    fn test_server_closed() {
        let mut isolation_manager = IsolationManager::new();
        // start an advertiser associated with the server, then kill the server
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.clear_server(SERVER_ID);

        // then afterwards we get a connection to this advertiser
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        // since the server is gone, we should not capture the connection
        assert!(server_id.is_none())
    }

    #[test]
    fn test_connection_isolated_after_advertiser_stops() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        isolation_manager.clear_advertiser(ADVERTISER_ID);

        let is_isolated = isolation_manager.is_connection_isolated(TCB_IDX);

        assert!(is_isolated)
    }

    #[test]
    fn test_connection_isolated_after_server_stops() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        isolation_manager.clear_server(SERVER_ID);

        let is_isolated = isolation_manager.is_connection_isolated(TCB_IDX);

        assert!(is_isolated)
    }

    #[test]
    fn test_not_isolated_after_disconnection() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));

        isolation_manager.on_le_disconnect(TCB_IDX);
        let is_isolated = isolation_manager.is_connection_isolated(TCB_IDX);

        assert!(!is_isolated);
    }

    #[test]
    fn test_tcb_idx_reuse_after_isolated() {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(SERVER_ID, ADVERTISER_ID);
        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        isolation_manager.clear_advertiser(ADVERTISER_ID);
        isolation_manager.on_le_disconnect(TCB_IDX);

        isolation_manager.on_le_connect(TCB_IDX, Some(ADVERTISER_ID));
        let server_id = isolation_manager.get_server_id(TCB_IDX);

        assert!(server_id.is_none());
        assert!(!isolation_manager.is_connection_isolated(TCB_IDX));
    }
}
