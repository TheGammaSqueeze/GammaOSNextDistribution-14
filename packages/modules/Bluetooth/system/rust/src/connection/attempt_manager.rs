use std::{
    collections::{hash_map::Entry, HashMap},
    future::{Future, IntoFuture},
};

use tokio::sync::oneshot;

use crate::core::address::AddressWithType;

use super::{
    le_manager::ErrorCode, CancelConnectFailure, ConnectionFailure, ConnectionManagerClient,
    CreateConnectionFailure, LeConnection,
};

#[derive(Debug, PartialEq, Eq, Clone, Copy, Hash)]
pub enum ConnectionMode {
    Background,
    Direct,
}

#[derive(Debug)]
struct ConnectionAttemptData {
    id: AttemptId,
    conn_tx: Option<oneshot::Sender<Result<LeConnection, ErrorCode>>>,
}

#[derive(Debug, Hash, PartialEq, Eq, Clone, Copy)]
pub struct ConnectionAttempt {
    pub client: ConnectionManagerClient,
    pub mode: ConnectionMode,
    pub remote_address: AddressWithType,
}

#[derive(Debug, Copy, Clone, PartialEq, Eq)]
pub struct AttemptId(u64);

#[derive(Debug)]
pub struct ConnectionAttempts {
    attempt_id: AttemptId,
    attempts: HashMap<ConnectionAttempt, ConnectionAttemptData>,
}

#[derive(Debug)]
pub struct PendingConnectionAttempt<F> {
    pub id: AttemptId,
    f: F,
}

impl<F> IntoFuture for PendingConnectionAttempt<F>
where
    F: Future<Output = Result<LeConnection, ConnectionFailure>>,
{
    type Output = F::Output;
    type IntoFuture = F;

    fn into_future(self) -> Self::IntoFuture {
        self.f
    }
}

impl ConnectionAttempts {
    /// Constructor
    pub fn new() -> Self {
        Self { attempt_id: AttemptId(0), attempts: HashMap::new() }
    }

    fn new_attempt_id(&mut self) -> AttemptId {
        let AttemptId(id) = self.attempt_id;
        self.attempt_id = AttemptId(id.wrapping_add(1));
        AttemptId(id)
    }

    /// Register a pending direct connection to the peer. Note that the peer MUST NOT be connected at this point.
    /// Returns the AttemptId of this attempt, as well as a future resolving with the connection (once created) or an
    /// error.
    ///
    /// Note that only one connection attempt from the same (client, address, mode) tuple can be pending at any time.
    ///
    /// # Cancellation Safety
    /// If this future is cancelled, the attempt will NOT BE REMOVED! It must be cancelled explicitly. To avoid
    /// cancelling the wrong future, the returned ID should be used.
    pub fn register_direct_connection(
        &mut self,
        client: ConnectionManagerClient,
        address: AddressWithType,
    ) -> Result<
        PendingConnectionAttempt<impl Future<Output = Result<LeConnection, ConnectionFailure>>>,
        CreateConnectionFailure,
    > {
        let attempt =
            ConnectionAttempt { client, mode: ConnectionMode::Direct, remote_address: address };

        let id = self.new_attempt_id();
        let Entry::Vacant(entry) = self.attempts.entry(attempt) else {
            return Err(CreateConnectionFailure::ConnectionAlreadyPending)
        };
        let (tx, rx) = oneshot::channel();
        entry.insert(ConnectionAttemptData { conn_tx: Some(tx), id });

        Ok(PendingConnectionAttempt {
            id,
            f: async move {
                rx.await
                    .map_err(|_| ConnectionFailure::Cancelled)?
                    .map_err(ConnectionFailure::Error)
            },
        })
    }

    /// Register a pending background connection to the peer. Returns the AttemptId of this attempt.
    ///
    /// Note that only one connection attempt from the same (client, address, mode) tuple can be pending at any time.
    pub fn register_background_connection(
        &mut self,
        client: ConnectionManagerClient,
        address: AddressWithType,
    ) -> Result<AttemptId, CreateConnectionFailure> {
        let attempt =
            ConnectionAttempt { client, mode: ConnectionMode::Background, remote_address: address };

        let id = self.new_attempt_id();
        let Entry::Vacant(entry) = self.attempts.entry(attempt) else {
            return Err(CreateConnectionFailure::ConnectionAlreadyPending)
        };
        entry.insert(ConnectionAttemptData { conn_tx: None, id });

        Ok(id)
    }

    /// Cancel connection attempts with the specified mode from this client to the specified address.
    pub fn cancel_attempt(
        &mut self,
        client: ConnectionManagerClient,
        address: AddressWithType,
        mode: ConnectionMode,
    ) -> Result<(), CancelConnectFailure> {
        let existing =
            self.attempts.remove(&ConnectionAttempt { client, mode, remote_address: address });

        if existing.is_some() {
            // note: dropping the ConnectionAttemptData is sufficient to close the channel and send a cancellation error
            Ok(())
        } else {
            Err(CancelConnectFailure::ConnectionNotPending)
        }
    }

    /// Cancel the connection attempt with the given ID.
    pub fn cancel_attempt_with_id(&mut self, id: AttemptId) {
        self.attempts.retain(|_, attempt| attempt.id != id);
    }

    /// Cancel all connection attempts to this address
    pub fn remove_unconditionally(&mut self, address: AddressWithType) {
        self.attempts.retain(|attempt, _| attempt.remote_address != address);
    }

    /// Cancel all connection attempts from this client
    pub fn remove_client(&mut self, client: ConnectionManagerClient) {
        self.attempts.retain(|attempt, _| attempt.client != client);
    }

    /// List all active connection attempts. Note that we can have active background (but NOT) direct
    /// connection attempts to connected devices, as we will resume the connection attempt when the
    /// peer disconnects from us.
    pub fn active_attempts(&self) -> Vec<ConnectionAttempt> {
        self.attempts.keys().cloned().collect()
    }

    /// Handle a successful connection by notifying clients and resolving direct connect attempts
    pub fn process_connection(
        &mut self,
        address: AddressWithType,
        result: Result<LeConnection, ErrorCode>,
    ) {
        let interested_clients = self
            .attempts
            .keys()
            .filter(|attempt| attempt.remote_address == address)
            .copied()
            .collect::<Vec<_>>();

        for attempt in interested_clients {
            if attempt.mode == ConnectionMode::Direct {
                // TODO(aryarahul): clean up these unwraps
                let _ = self.attempts.remove(&attempt).unwrap().conn_tx.unwrap().send(result);
            } else {
                // TODO(aryarahul): inform background clients of the connection
            }
        }
    }
}

#[cfg(test)]
mod test {
    use crate::{
        core::address::AddressType,
        utils::task::{block_on_locally, try_await},
    };

    use super::*;

    const CLIENT_1: ConnectionManagerClient = ConnectionManagerClient::GattClient(1);
    const CLIENT_2: ConnectionManagerClient = ConnectionManagerClient::GattClient(2);

    const ADDRESS_1: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 6], address_type: AddressType::Public };
    const ADDRESS_2: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 6], address_type: AddressType::Random };

    const CONNECTION_1: LeConnection = LeConnection { remote_address: ADDRESS_1 };
    const CONNECTION_2: LeConnection = LeConnection { remote_address: ADDRESS_2 };

    #[test]
    fn test_direct_connection() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: start a pending direct connection
            let _ = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // assert: this attempt is pending
            assert_eq!(attempts.active_attempts().len(), 1);
            assert_eq!(attempts.active_attempts()[0].client, CLIENT_1);
            assert_eq!(attempts.active_attempts()[0].mode, ConnectionMode::Direct);
            assert_eq!(attempts.active_attempts()[0].remote_address, ADDRESS_1);
        });
    }

    #[test]
    fn test_cancel_direct_connection() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mut attempts = ConnectionAttempts::new();
            let pending_direct_connection =
                attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: cancel it
            attempts.cancel_attempt(CLIENT_1, ADDRESS_1, ConnectionMode::Direct).unwrap();
            let resp = pending_direct_connection.await;

            // assert: the original future resolved, and the attempt is cleared
            assert_eq!(resp, Err(ConnectionFailure::Cancelled));
            assert!(attempts.active_attempts().is_empty());
        });
    }

    #[test]
    fn test_multiple_direct_connections() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: start two direct connections
            attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            attempts.register_direct_connection(CLIENT_2, ADDRESS_1).unwrap();

            // assert: both attempts are pending
            assert_eq!(attempts.active_attempts().len(), 2);
        });
    }

    #[test]
    fn test_two_direct_connection_cancel_one() {
        block_on_locally(async {
            // arrange: two pending direct connections
            let mut attempts = ConnectionAttempts::new();
            attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            attempts.register_direct_connection(CLIENT_2, ADDRESS_1).unwrap();

            // act: cancel one
            attempts.cancel_attempt(CLIENT_1, ADDRESS_1, ConnectionMode::Direct).unwrap();

            // assert: one attempt is still pending
            assert_eq!(attempts.active_attempts().len(), 1);
            assert_eq!(attempts.active_attempts()[0].client, CLIENT_2);
        });
    }

    #[test]
    fn test_drop_pending_connection_after_cancel_and_restart() {
        // arrange
        let mut attempts = ConnectionAttempts::new();

        // act: start one pending direct connection, cancel it, restart it, and then drop the first future
        let pending_1 = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
        attempts.cancel_attempt(CLIENT_1, ADDRESS_1, ConnectionMode::Direct).unwrap();
        let _pending_2 = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
        drop(pending_1);

        // assert: the restart is still pending
        assert_eq!(attempts.active_attempts().len(), 1);
    }

    #[test]
    fn test_background_connection() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: start a pending background connection
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();

            // assert: this attempt is pending
            assert_eq!(attempts.active_attempts().len(), 1);
            assert_eq!(attempts.active_attempts()[0].client, CLIENT_1);
            assert_eq!(attempts.active_attempts()[0].mode, ConnectionMode::Background);
            assert_eq!(attempts.active_attempts()[0].remote_address, ADDRESS_1);
        });
    }

    #[test]
    fn test_reject_duplicate_direct_connection() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: start two background connections with the same parameters
            let _fut = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            let ret = attempts.register_direct_connection(CLIENT_1, ADDRESS_1);

            // assert: this attempt is pending
            assert!(matches!(ret, Err(CreateConnectionFailure::ConnectionAlreadyPending)));
        });
    }

    #[test]
    fn test_reject_duplicate_background_connection() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: start two background connections with the same parameters
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            let ret = attempts.register_background_connection(CLIENT_1, ADDRESS_1);

            // assert: this attempt is pending
            assert_eq!(ret, Err(CreateConnectionFailure::ConnectionAlreadyPending));
        });
    }

    #[test]
    fn test_resolved_direct_connection() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mut attempts = ConnectionAttempts::new();
            let pending_conn = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: resolve with an incoming connection
            attempts.process_connection(ADDRESS_1, Ok(CONNECTION_1));

            // assert: the attempt is resolved and is no longer active
            assert_eq!(pending_conn.await.unwrap(), CONNECTION_1);
            assert!(attempts.active_attempts().is_empty());
        });
    }

    #[test]
    fn test_failed_direct_connection() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mut attempts = ConnectionAttempts::new();
            let pending_conn = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: resolve with an incoming connection
            attempts.process_connection(ADDRESS_1, Err(ErrorCode(1)));

            // assert: the attempt is resolved and is no longer active
            assert_eq!(pending_conn.await, Err(ConnectionFailure::Error(ErrorCode(1))));
            assert!(attempts.active_attempts().is_empty());
        });
    }

    #[test]
    fn test_resolved_background_connection() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mut attempts = ConnectionAttempts::new();
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: resolve with an incoming connection
            attempts.process_connection(ADDRESS_1, Ok(CONNECTION_1));

            // assert: the attempt is still active
            assert_eq!(attempts.active_attempts().len(), 1);
        });
    }

    #[test]
    fn test_incoming_connection_while_another_is_pending() {
        block_on_locally(async {
            // arrange: one pending direct connection
            let mut attempts = ConnectionAttempts::new();
            let pending_conn = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: an incoming connection arrives to a different address
            attempts.process_connection(ADDRESS_2, Ok(CONNECTION_2));

            // assert: the attempt is still pending
            assert!(try_await(pending_conn).await.is_err());
            assert_eq!(attempts.active_attempts().len(), 1);
        });
    }

    #[test]
    fn test_incoming_connection_resolves_some_but_not_all() {
        block_on_locally(async {
            // arrange: one pending direct connection and one background connection to each of two addresses
            let mut attempts = ConnectionAttempts::new();
            let pending_conn_1 = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            let pending_conn_2 = attempts.register_direct_connection(CLIENT_1, ADDRESS_2).unwrap();
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            attempts.register_background_connection(CLIENT_1, ADDRESS_2).unwrap();

            // act: an incoming connection arrives to the first address
            attempts.process_connection(ADDRESS_1, Ok(CONNECTION_1));

            // assert: one direct attempt is completed, one is still pending
            assert_eq!(pending_conn_1.await, Ok(CONNECTION_1));
            assert!(try_await(pending_conn_2).await.is_err());
            // three attempts remain (the unresolved direct, and both background attempts)
            assert_eq!(attempts.active_attempts().len(), 3);
        });
    }

    #[test]
    fn test_remove_background_connection() {
        block_on_locally(async {
            // arrange: one pending background connection
            let mut attempts = ConnectionAttempts::new();
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();

            // act: remove it
            attempts.cancel_attempt(CLIENT_1, ADDRESS_1, ConnectionMode::Background).unwrap();

            // assert: no pending attempts
            assert!(attempts.active_attempts().is_empty());
        });
    }

    #[test]
    fn test_cancel_nonexistent_connection() {
        block_on_locally(async {
            // arrange
            let mut attempts = ConnectionAttempts::new();

            // act: cancel a nonexistent direct connection
            let resp = attempts.cancel_attempt(CLIENT_1, ADDRESS_1, ConnectionMode::Direct);

            // assert: got an error
            assert_eq!(resp, Err(CancelConnectFailure::ConnectionNotPending));
        });
    }

    #[test]
    fn test_remove_unconditionally() {
        block_on_locally(async {
            // arrange: one pending direct connection, and one background connection, to each address
            let mut attempts = ConnectionAttempts::new();
            let pending_conn_1 = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            let pending_conn_2 = attempts.register_direct_connection(CLIENT_1, ADDRESS_2).unwrap();
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            attempts.register_background_connection(CLIENT_1, ADDRESS_2).unwrap();

            // act: cancel all connections to the first address
            attempts.remove_unconditionally(ADDRESS_1);

            // assert: one direct attempt is completed, one is still pending
            assert_eq!(pending_conn_1.await, Err(ConnectionFailure::Cancelled));
            assert!(try_await(pending_conn_2).await.is_err());
            // assert: two attempts remain, both to the other address
            assert_eq!(attempts.active_attempts().len(), 2);
            assert_eq!(attempts.active_attempts()[0].remote_address, ADDRESS_2);
            assert_eq!(attempts.active_attempts()[1].remote_address, ADDRESS_2);
        });
    }

    #[test]
    fn test_remove_client() {
        block_on_locally(async {
            // arrange: one pending direct connection, and one background connection, from each address
            let mut attempts = ConnectionAttempts::new();
            let pending_conn_1 = attempts.register_direct_connection(CLIENT_1, ADDRESS_1).unwrap();
            let pending_conn_2 = attempts.register_direct_connection(CLIENT_2, ADDRESS_1).unwrap();
            attempts.register_background_connection(CLIENT_1, ADDRESS_1).unwrap();
            attempts.register_background_connection(CLIENT_2, ADDRESS_1).unwrap();

            // act: remove the first client
            attempts.remove_client(CLIENT_1);

            // assert: one direct attempt is completed, one is still pending
            assert_eq!(pending_conn_1.await, Err(ConnectionFailure::Cancelled));
            assert!(try_await(pending_conn_2).await.is_err());
            // assert: two attempts remain, both from the second client
            assert_eq!(attempts.active_attempts().len(), 2);
            assert_eq!(attempts.active_attempts()[0].client, CLIENT_2);
            assert_eq!(attempts.active_attempts()[1].client, CLIENT_2);
        });
    }
}
