//! This module manages LE connection requests and active
//! LE connections. In particular, it de-duplicates connection requests,
//! avoids duplicate connections to the same devices (even with different RPAs),
//! and retries failed connections

use std::{
    cell::RefCell, collections::HashSet, fmt::Debug, future::Future, hash::Hash, ops::Deref,
    time::Duration,
};

use crate::{
    core::{
        address::AddressWithType,
        shared_box::{SharedBox, WeakBox, WeakBoxRef},
    },
    gatt::ids::ServerId,
};

use self::{
    acceptlist_manager::{determine_target_state, LeAcceptlistManager},
    attempt_manager::{ConnectionAttempts, ConnectionMode},
    le_manager::{ErrorCode, InactiveLeAclManager, LeAclManagerConnectionCallbacks},
};

mod acceptlist_manager;
mod attempt_manager;
mod ffi;
pub mod le_manager;
mod mocks;

pub use ffi::{register_callbacks, LeAclManagerImpl, LeAclManagerShim};
use log::info;
use scopeguard::ScopeGuard;
use tokio::{task::spawn_local, time::timeout};

/// Possible errors returned when making a connection attempt
#[derive(Debug, PartialEq, Eq)]
pub enum CreateConnectionFailure {
    /// This client is already making a connection of the same type
    /// to the same address.
    ConnectionAlreadyPending,
}

/// Errors returned if a connection successfully starts but fails afterwards.
#[derive(Debug, PartialEq, Eq)]
pub enum ConnectionFailure {
    /// The connection attempt was cancelled
    Cancelled,
    /// The connection completed but with an HCI error code
    Error(ErrorCode),
}

/// Errors returned if the client fails to cancel their connection attempt
#[derive(Debug, PartialEq, Eq)]
pub enum CancelConnectFailure {
    /// The connection attempt does not exist
    ConnectionNotPending,
}

/// Unique identifiers for a client of the connection manager
#[derive(Debug, Hash, PartialEq, Eq, Clone, Copy)]
pub enum ConnectionManagerClient {
    /// A GATT client with given client ID
    GattClient(u8),
    /// A GATT server with given server ID
    GattServer(ServerId),
}

/// An active connection
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct LeConnection {
    /// The address of the peer device, as reported in the connection complete event
    /// This is guaranteed to be unique across active connections, so we can implement
    /// PartialEq/Eq on this.
    pub remote_address: AddressWithType,
}

/// Responsible for managing the initiator state and the list of
/// devices on the filter accept list
#[derive(Debug)]
pub struct ConnectionManager {
    state: RefCell<ConnectionManagerState>,
}

#[derive(Debug)]
struct ConnectionManagerState {
    /// All pending connection attempts (unresolved direct + all background)
    attempts: ConnectionAttempts,
    /// The addresses we are currently connected to
    current_connections: HashSet<AddressWithType>,
    /// Tracks the state of the LE connect list, and updates it to drive to a
    /// specified target state
    acceptlist_manager: LeAcceptlistManager,
}

struct ConnectionManagerCallbackHandler(WeakBox<ConnectionManager>);

const DIRECT_CONNECTION_TIMEOUT: Duration = Duration::from_secs(
    29, /* ugly hack to avoid fighting with le_impl timeout, until I remove that timeout */
);

impl LeAclManagerConnectionCallbacks for ConnectionManagerCallbackHandler {
    fn on_le_connect(&self, address: AddressWithType, result: Result<LeConnection, ErrorCode>) {
        self.with_manager(|manager| manager.on_le_connect(address, result))
    }

    fn on_disconnect(&self, address: AddressWithType) {
        self.with_manager(|manager| manager.on_disconnect(address))
    }
}

impl ConnectionManagerCallbackHandler {
    fn with_manager(&self, f: impl FnOnce(&ConnectionManager)) {
        self.0.with(|manager| f(manager.expect("got connection event after stack died").deref()))
    }
}

impl ConnectionManager {
    /// Constructor
    pub fn new(le_manager: impl InactiveLeAclManager) -> SharedBox<Self> {
        SharedBox::new_cyclic(|weak| Self {
            state: RefCell::new(ConnectionManagerState {
                attempts: ConnectionAttempts::new(),
                current_connections: HashSet::new(),
                acceptlist_manager: LeAcceptlistManager::new(
                    le_manager.register_callbacks(ConnectionManagerCallbackHandler(weak)),
                ),
            }),
        })
    }
}

/// Make the state of the LeAcceptlistManager consistent with the attempts tracked in ConnectionAttempts
fn reconcile_state(state: &mut ConnectionManagerState) {
    state
        .acceptlist_manager
        .drive_to_state(determine_target_state(&state.attempts.active_attempts()));
}

impl WeakBoxRef<'_, ConnectionManager> {
    /// Start a direct connection to a peer device from a specified client. If the peer
    /// is connected, immediately resolve the attempt.
    pub fn start_direct_connection(
        &self,
        client: ConnectionManagerClient,
        address: AddressWithType,
    ) -> Result<(), CreateConnectionFailure> {
        spawn_local(timeout(DIRECT_CONNECTION_TIMEOUT, self.direct_connection(client, address)?));
        Ok(())
    }

    /// Start a direct connection to a peer device from a specified client.
    ///
    /// # Cancellation Safety
    /// If this future is dropped, the connection attempt will be cancelled. It can also be cancelled
    /// from the separate API ConnectionManager#cancel_connection.
    fn direct_connection(
        &self,
        client: ConnectionManagerClient,
        address: AddressWithType,
    ) -> Result<
        impl Future<Output = Result<LeConnection, ConnectionFailure>>,
        CreateConnectionFailure,
    > {
        let mut state = self.state.borrow_mut();

        // if connected, this is a no-op
        let attempt_and_guard = if state.current_connections.contains(&address) {
            None
        } else {
            let pending_attempt = state.attempts.register_direct_connection(client, address)?;
            let attempt_id = pending_attempt.id;
            reconcile_state(&mut state);
            Some((
                pending_attempt,
                scopeguard::guard(self.downgrade(), move |this| {
                    // remove the attempt after we are cancelled
                    this.with(|this| {
                        this.map(|this| {
                            info!("Cancelling attempt {attempt_id:?}");
                            let mut state = this.state.borrow_mut();
                            state.attempts.cancel_attempt_with_id(attempt_id);
                            reconcile_state(&mut state);
                        })
                    });
                }),
            ))
        };

        Ok(async move {
            let Some((attempt, guard)) = attempt_and_guard else {
                // if we did not make an attempt, the connection must be ready
                return Ok(LeConnection { remote_address: address })
            };
            // otherwise, wait until the attempt resolves
            let ret = attempt.await;
            // defuse scopeguard (no need to cancel now)
            ScopeGuard::into_inner(guard);
            ret
        })
    }
}

impl ConnectionManager {
    /// Start a background connection to a peer device with given parameters from a specified client.
    pub fn add_background_connection(
        &self,
        client: ConnectionManagerClient,
        address: AddressWithType,
    ) -> Result<(), CreateConnectionFailure> {
        let mut state = self.state.borrow_mut();
        state.attempts.register_background_connection(client, address)?;
        reconcile_state(&mut state);
        Ok(())
    }

    /// Cancel connection attempt from this client to the specified address with the specified mode.
    pub fn cancel_connection(
        &self,
        client: ConnectionManagerClient,
        address: AddressWithType,
        mode: ConnectionMode,
    ) -> Result<(), CancelConnectFailure> {
        let mut state = self.state.borrow_mut();
        state.attempts.cancel_attempt(client, address, mode)?;
        reconcile_state(&mut state);
        Ok(())
    }

    /// Cancel all connection attempts to this address
    pub fn cancel_unconditionally(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        state.attempts.remove_unconditionally(address);
        reconcile_state(&mut state);
    }

    /// Cancel all connection attempts from this client
    pub fn remove_client(&self, client: ConnectionManagerClient) {
        let mut state = self.state.borrow_mut();
        state.attempts.remove_client(client);
        reconcile_state(&mut state);
    }

    fn on_le_connect(&self, address: AddressWithType, result: Result<LeConnection, ErrorCode>) {
        let mut state = self.state.borrow_mut();
        // record this connection while it exists
        state.current_connections.insert(address);
        // all completed connections remove the address from the direct list
        state.acceptlist_manager.on_connect_complete(address);
        // invoke any pending callbacks, update set of attempts
        state.attempts.process_connection(address, result);
        // update the acceptlist
        reconcile_state(&mut state);
    }

    fn on_disconnect(&self, address: AddressWithType) {
        let mut state = self.state.borrow_mut();
        state.current_connections.remove(&address);
        reconcile_state(&mut state);
    }
}

#[cfg(test)]
mod test {
    use crate::{core::address::AddressType, utils::task::block_on_locally};

    use super::{mocks::mock_le_manager::MockLeAclManager, *};

    const CLIENT_1: ConnectionManagerClient = ConnectionManagerClient::GattClient(1);
    const CLIENT_2: ConnectionManagerClient = ConnectionManagerClient::GattClient(2);

    const ADDRESS_1: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 6], address_type: AddressType::Public };

    const ERROR: ErrorCode = ErrorCode(1);

    #[test]
    fn test_single_direct_connection() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a direct connection
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // assert: the direct connection is pending
            assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
            assert_eq!(mock_le_manager.current_acceptlist().len(), 1);
            assert!(mock_le_manager.current_acceptlist().contains(&ADDRESS_1));
        });
    }

    #[test]
    fn test_failed_direct_connection() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: the connection attempt fails
            mock_le_manager.on_le_connect(ADDRESS_1, ERROR);

            // assert: the direct connection has stopped
            assert_eq!(mock_le_manager.current_connection_mode(), None);
        });
    }

    #[test]
    fn test_single_background_connection() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a background connection
            connection_manager.as_ref().add_background_connection(CLIENT_1, ADDRESS_1).unwrap();

            // assert: the background connection is pending
            assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Background));
            assert_eq!(mock_le_manager.current_acceptlist().len(), 1);
            assert!(mock_le_manager.current_acceptlist().contains(&ADDRESS_1));
        });
    }

    #[test]
    fn test_resolved_connection() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a direct connection, that succeeds
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            mock_le_manager.on_le_connect(ADDRESS_1, ErrorCode::SUCCESS);

            // assert: no connection is pending
            assert_eq!(mock_le_manager.current_connection_mode(), None);
        });
    }

    #[test]
    fn test_resolved_background_connection() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a background connection, that succeeds
            connection_manager.as_ref().add_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            mock_le_manager.on_le_connect(ADDRESS_1, ErrorCode::SUCCESS);

            // assert: no connection is pending
            assert_eq!(mock_le_manager.current_connection_mode(), None);
        });
    }

    #[test]
    fn test_resolved_direct_connection_after_disconnect() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a direct connection, that succeeds, then disconnects
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            mock_le_manager.on_le_connect(ADDRESS_1, ErrorCode::SUCCESS);
            mock_le_manager.on_le_disconnect(ADDRESS_1);

            // assert: no connection is pending
            assert_eq!(mock_le_manager.current_connection_mode(), None);
        });
    }

    #[test]
    fn test_resolved_background_connection_after_disconnect() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: initiate a background connection, that succeeds, then disconnects
            connection_manager.as_ref().add_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            mock_le_manager.on_le_connect(ADDRESS_1, ErrorCode::SUCCESS);
            mock_le_manager.on_le_disconnect(ADDRESS_1);

            // assert: the background connection has resumed
            assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Background));
        });
    }

    #[test]
    fn test_direct_connection_timeout() {
        block_on_locally(async {
            // arrange: a pending direct connection
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: let it timeout
            tokio::time::sleep(DIRECT_CONNECTION_TIMEOUT).await;
            // go forward one tick to ensure all timers are fired
            // (since we are using fake time, this is not a race condition)
            tokio::time::sleep(Duration::from_millis(1)).await;

            // assert: it is cancelled and we are idle again
            assert_eq!(mock_le_manager.current_connection_mode(), None);
        });
    }

    #[test]
    fn test_stacked_direct_connections_timeout() {
        block_on_locally(async {
            // arrange
            let mock_le_manager = MockLeAclManager::new();
            let connection_manager = ConnectionManager::new(mock_le_manager.clone());

            // act: start a direct connection
            connection_manager.as_ref().start_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            tokio::time::sleep(DIRECT_CONNECTION_TIMEOUT * 3 / 4).await;
            // act: after some time, start a second one
            connection_manager.as_ref().start_direct_connection(CLIENT_2, ADDRESS_1).unwrap();
            // act: wait for the first one (but not the second) to time out
            tokio::time::sleep(DIRECT_CONNECTION_TIMEOUT * 3 / 4).await;

            // assert: we are still doing a direct connection
            assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
        });
    }
}
