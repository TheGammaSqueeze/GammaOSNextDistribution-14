use std::{cell::RefCell, collections::HashMap, rc::Rc, time::Duration};

use async_trait::async_trait;
use log::{trace, warn};
use tokio::{sync::oneshot, time::timeout};

use crate::{
    gatt::{
        ids::{AttHandle, ConnectionId, ServerId, TransactionId, TransportIndex},
        GattCallbacks,
    },
    packets::{AttAttributeDataChild, AttAttributeDataView, AttErrorCode},
};

use super::{
    AttributeBackingType, GattWriteRequestType, GattWriteType, RawGattDatastore,
    TransactionDecision,
};

struct PendingTransaction {
    response: oneshot::Sender<Result<AttAttributeDataChild, AttErrorCode>>,
}

#[derive(Debug)]
struct PendingTransactionWatcher {
    conn_id: ConnectionId,
    trans_id: TransactionId,
    rx: oneshot::Receiver<Result<AttAttributeDataChild, AttErrorCode>>,
}

/// This struct converts the asynchronus read/write operations of GattDatastore
/// into the callback-based interface expected by JNI
pub struct CallbackTransactionManager {
    callbacks: Rc<dyn GattCallbacks>,
    pending_transactions: RefCell<PendingTransactionsState>,
}

struct PendingTransactionsState {
    pending_transactions: HashMap<(ConnectionId, TransactionId), PendingTransaction>,
    next_transaction_id: u32,
}

/// We expect all responses to be provided within this timeout
/// It should be less than 30s, as that is the ATT timeout that causes
/// the client to disconnect.
const TIMEOUT: Duration = Duration::from_secs(15);

/// The cause of a failure to dispatch a call to send_response()
#[derive(Debug, PartialEq, Eq)]
pub enum CallbackResponseError {
    /// The TransactionId supplied was invalid for the specified connection
    NonExistentTransaction(TransactionId),
    /// The TransactionId was valid but has since terminated
    ListenerHungUp(TransactionId),
}

impl CallbackTransactionManager {
    /// Constructor, wrapping a GattCallbacks instance with the GattDatastore
    /// interface
    pub fn new(callbacks: Rc<dyn GattCallbacks>) -> Self {
        Self {
            callbacks,
            pending_transactions: RefCell::new(PendingTransactionsState {
                pending_transactions: HashMap::new(),
                next_transaction_id: 1,
            }),
        }
    }

    /// Invoked from server implementations in response to read/write requests
    pub fn send_response(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        value: Result<AttAttributeDataChild, AttErrorCode>,
    ) -> Result<(), CallbackResponseError> {
        let mut pending = self.pending_transactions.borrow_mut();
        if let Some(transaction) = pending.pending_transactions.remove(&(conn_id, trans_id)) {
            if transaction.response.send(value).is_err() {
                Err(CallbackResponseError::ListenerHungUp(trans_id))
            } else {
                trace!("got expected response for transaction {trans_id:?}");
                Ok(())
            }
        } else {
            Err(CallbackResponseError::NonExistentTransaction(trans_id))
        }
    }

    /// Get an impl GattDatastore tied to a particular server
    pub fn get_datastore(self: &Rc<Self>, server_id: ServerId) -> impl RawGattDatastore {
        GattDatastoreImpl { callback_transaction_manager: self.clone(), server_id }
    }
}

impl PendingTransactionsState {
    fn alloc_transaction_id(&mut self) -> TransactionId {
        let trans_id = TransactionId(self.next_transaction_id);
        self.next_transaction_id = self.next_transaction_id.wrapping_add(1);
        trans_id
    }

    fn start_new_transaction(&mut self, conn_id: ConnectionId) -> PendingTransactionWatcher {
        let trans_id = self.alloc_transaction_id();
        let (tx, rx) = oneshot::channel();
        self.pending_transactions.insert((conn_id, trans_id), PendingTransaction { response: tx });
        PendingTransactionWatcher { conn_id, trans_id, rx }
    }
}

impl PendingTransactionWatcher {
    /// Wait for the transaction to resolve, or to hit the timeout. If the
    /// timeout is reached, clean up state related to transaction watching.
    async fn wait(
        self,
        manager: &CallbackTransactionManager,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        if let Ok(Ok(result)) = timeout(TIMEOUT, self.rx).await {
            result
        } else {
            manager
                .pending_transactions
                .borrow_mut()
                .pending_transactions
                .remove(&(self.conn_id, self.trans_id));
            warn!("no response received from Java after timeout - returning UNLIKELY_ERROR");
            Err(AttErrorCode::UNLIKELY_ERROR)
        }
    }
}

struct GattDatastoreImpl {
    callback_transaction_manager: Rc<CallbackTransactionManager>,
    server_id: ServerId,
}

#[async_trait(?Send)]
impl RawGattDatastore for GattDatastoreImpl {
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        offset: u32,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        let conn_id = ConnectionId::new(tcb_idx, self.server_id);

        let pending_transaction = self
            .callback_transaction_manager
            .pending_transactions
            .borrow_mut()
            .start_new_transaction(conn_id);
        let trans_id = pending_transaction.trans_id;

        self.callback_transaction_manager.callbacks.on_server_read(
            ConnectionId::new(tcb_idx, self.server_id),
            trans_id,
            handle,
            attr_type,
            offset,
        );

        pending_transaction.wait(&self.callback_transaction_manager).await
    }

    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteRequestType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        let conn_id = ConnectionId::new(tcb_idx, self.server_id);

        let pending_transaction = self
            .callback_transaction_manager
            .pending_transactions
            .borrow_mut()
            .start_new_transaction(conn_id);
        let trans_id = pending_transaction.trans_id;

        self.callback_transaction_manager.callbacks.on_server_write(
            conn_id,
            trans_id,
            handle,
            attr_type,
            GattWriteType::Request(write_type),
            data,
        );

        // the data passed back is irrelevant for write requests
        pending_transaction.wait(&self.callback_transaction_manager).await.map(|_| ())
    }

    fn write_no_response(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    ) {
        let conn_id = ConnectionId::new(tcb_idx, self.server_id);

        let trans_id = self
            .callback_transaction_manager
            .pending_transactions
            .borrow_mut()
            .alloc_transaction_id();
        self.callback_transaction_manager.callbacks.on_server_write(
            conn_id,
            trans_id,
            handle,
            attr_type,
            GattWriteType::Command,
            data,
        );
    }

    async fn execute(
        &self,
        tcb_idx: TransportIndex,
        decision: TransactionDecision,
    ) -> Result<(), AttErrorCode> {
        let conn_id = ConnectionId::new(tcb_idx, self.server_id);

        let pending_transaction = self
            .callback_transaction_manager
            .pending_transactions
            .borrow_mut()
            .start_new_transaction(conn_id);
        let trans_id = pending_transaction.trans_id;

        self.callback_transaction_manager.callbacks.on_execute(conn_id, trans_id, decision);

        // the data passed back is irrelevant for execute requests
        pending_transaction.wait(&self.callback_transaction_manager).await.map(|_| ())
    }
}
