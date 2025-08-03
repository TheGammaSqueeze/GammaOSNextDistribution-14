//! These are the interfaces between the GattModule and JNI. The synchronous
//! interface is mapped to the asynchronous interface using the
//! CallbackTransactionManager;

mod callback_transaction_manager;

pub use callback_transaction_manager::{CallbackResponseError, CallbackTransactionManager};

use async_trait::async_trait;
use log::warn;

use crate::packets::{AttAttributeDataChild, AttAttributeDataView, AttErrorCode};

use super::{
    ffi::AttributeBackingType,
    ids::{AttHandle, ConnectionId, TransactionId, TransportIndex},
    server::IndicationError,
};

/// These callbacks are expected to be made available to the GattModule from
/// JNI.
pub trait GattCallbacks {
    /// Invoked when a client tries to read a characteristic/descriptor. Expects
    /// a response using bluetooth::gatt::send_response();
    fn on_server_read(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        offset: u32,
    );

    /// Invoked when a client tries to write a characteristic/descriptor.
    /// Expects a response using bluetooth::gatt::send_response();
    #[allow(clippy::too_many_arguments)] // needed to match the C++ interface
    fn on_server_write(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteType,
        value: AttAttributeDataView,
    );

    /// Invoked when a handle value indication transaction completes
    /// (either due to an error, link loss, or the peer confirming it)
    fn on_indication_sent_confirmation(
        &self,
        conn_id: ConnectionId,
        result: Result<(), IndicationError>,
    );

    /// Execute or cancel any prepared writes
    fn on_execute(
        &self,
        conn_id: ConnectionId,
        trans_id: TransactionId,
        decision: TransactionDecision,
    );
}

/// The various write types available (requests + commands)
#[derive(PartialEq, Eq, Clone, Copy, Debug)]
pub enum GattWriteType {
    /// Reliable, expects a response (WRITE_REQ or PREPARE_WRITE_REQ)
    Request(GattWriteRequestType),
    /// Unreliable, no response required (WRITE_CMD)
    Command,
}

/// The types of write requests (that need responses)
#[derive(PartialEq, Eq, Clone, Copy, Debug)]
pub enum GattWriteRequestType {
    /// Atomic (WRITE_REQ)
    Request,
    /// Transactional, should not be committed yet (PREPARE_WRITE_REQ)
    Prepare {
        /// The byte offset at which to write
        offset: u32,
    },
}

/// Whether to commit or cancel a transaction
#[derive(Clone, Copy, Debug)]
pub enum TransactionDecision {
    /// Commit all pending writes
    Execute,
    /// Discard all pending writes
    Cancel,
}

/// This interface is an "async" version of the above, and is passed directly
/// into the GattModule
#[async_trait(?Send)]
pub trait RawGattDatastore {
    /// Read a characteristic from the specified connection at the given handle.
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        offset: u32,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode>;

    /// Write data to a given characteristic on the specified connection.
    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteRequestType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode>;

    /// Write data to a given characteristic on the specified connection, without waiting
    /// for a response from the upper layer.
    fn write_no_response(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    );

    /// Execute or cancel any prepared writes
    async fn execute(
        &self,
        tcb_idx: TransportIndex,
        decision: TransactionDecision,
    ) -> Result<(), AttErrorCode>;
}

/// This interface simplifies the interface of RawGattDatastore by rejecting all unsupported
/// operations, rather than requiring clients to do so.
#[async_trait(?Send)]
pub trait GattDatastore {
    /// Read a characteristic from the specified connection at the given handle.
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode>;

    /// Write data to a given characteristic on the specified connection.
    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode>;
}

#[async_trait(?Send)]
impl<T: GattDatastore + ?Sized> RawGattDatastore for T {
    /// Read a characteristic from the specified connection at the given handle.
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        offset: u32,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        if offset != 0 {
            warn!("got read blob request for non-long attribute {handle:?}");
            return Err(AttErrorCode::ATTRIBUTE_NOT_LONG);
        }
        self.read(tcb_idx, handle, attr_type).await
    }

    /// Write data to a given characteristic on the specified connection.
    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteRequestType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        match write_type {
            GattWriteRequestType::Prepare { .. } => {
                warn!("got prepare write attempt on {tcb_idx:?} to characteristic {handle:?} not supporting write_without_response");
                Err(AttErrorCode::WRITE_REQUEST_REJECTED)
            }
            GattWriteRequestType::Request => self.write(tcb_idx, handle, attr_type, data).await,
        }
    }

    fn write_no_response(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        _: AttributeBackingType,
        _: AttAttributeDataView<'_>,
    ) {
        // silently drop, since there's no way to return an error
        warn!("got write command on {tcb_idx:?} to characteristic {handle:?} not supporting write_without_response");
    }

    /// Execute or cancel any prepared writes
    async fn execute(&self, _: TransportIndex, _: TransactionDecision) -> Result<(), AttErrorCode> {
        // we never do prepared writes, so who cares
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use tokio::{sync::mpsc::error::TryRecvError, task::spawn_local};

    use crate::{
        gatt::mocks::mock_datastore::{MockDatastore, MockDatastoreEvents},
        packets::OwnedAttAttributeDataView,
        utils::{
            packet::{build_att_data, build_view_or_crash},
            task::block_on_locally,
        },
    };

    use super::*;

    const TCB_IDX: TransportIndex = TransportIndex(1);
    const HANDLE: AttHandle = AttHandle(1);
    const DATA: [u8; 4] = [1, 2, 3, 4];

    #[test]
    fn test_regular_read_invoke() {
        block_on_locally(async {
            // arrange
            let (datastore, mut rx) = MockDatastore::new();

            // act: send read request
            spawn_local(async move {
                RawGattDatastore::read(
                    &datastore,
                    TCB_IDX,
                    HANDLE,
                    0,
                    AttributeBackingType::Characteristic,
                )
                .await
            });
            let resp = rx.recv().await.unwrap();

            // assert: got read event
            assert!(matches!(
                resp,
                MockDatastoreEvents::Read(TCB_IDX, HANDLE, AttributeBackingType::Characteristic, _)
            ));
        });
    }

    #[test]
    fn test_regular_read_response() {
        block_on_locally(async {
            // arrange
            let (datastore, mut rx) = MockDatastore::new();

            // act: send read request
            let pending = spawn_local(async move {
                RawGattDatastore::read(
                    &datastore,
                    TCB_IDX,
                    HANDLE,
                    0,
                    AttributeBackingType::Characteristic,
                )
                .await
            });
            let resp = rx.recv().await.unwrap();
            let MockDatastoreEvents::Read(_, _, _, resp) = resp else {
                unreachable!();
            };
            resp.send(Err(AttErrorCode::APPLICATION_ERROR)).unwrap();

            // assert: got the supplied response
            assert_eq!(pending.await.unwrap(), Err(AttErrorCode::APPLICATION_ERROR));
        });
    }

    #[test]
    fn test_rejected_read_blob() {
        // arrange
        let (datastore, mut rx) = MockDatastore::new();

        // act: send read blob request
        let resp = block_on_locally(RawGattDatastore::read(
            &datastore,
            TCB_IDX,
            HANDLE,
            1,
            AttributeBackingType::Characteristic,
        ));

        // assert: got the correct error code
        assert_eq!(resp, Err(AttErrorCode::ATTRIBUTE_NOT_LONG));
        // assert: no pending events
        assert_eq!(rx.try_recv().unwrap_err(), TryRecvError::Empty);
    }

    fn make_data() -> OwnedAttAttributeDataView {
        build_view_or_crash(build_att_data(AttAttributeDataChild::RawData(DATA.into())))
    }

    #[test]
    fn test_write_request_invoke() {
        block_on_locally(async {
            // arrange
            let (datastore, mut rx) = MockDatastore::new();

            // act: send write request
            spawn_local(async move {
                RawGattDatastore::write(
                    &datastore,
                    TCB_IDX,
                    HANDLE,
                    AttributeBackingType::Characteristic,
                    GattWriteRequestType::Request,
                    make_data().view(),
                )
                .await
            });
            let resp = rx.recv().await.unwrap();

            // assert: got write event
            assert!(matches!(
                resp,
                MockDatastoreEvents::Write(
                    TCB_IDX,
                    HANDLE,
                    AttributeBackingType::Characteristic,
                    _,
                    _
                )
            ));
        });
    }

    #[test]
    fn test_write_request_response() {
        block_on_locally(async {
            // arrange
            let (datastore, mut rx) = MockDatastore::new();

            // act: send write request
            let pending = spawn_local(async move {
                RawGattDatastore::write(
                    &datastore,
                    TCB_IDX,
                    HANDLE,
                    AttributeBackingType::Characteristic,
                    GattWriteRequestType::Request,
                    make_data().view(),
                )
                .await
            });
            let resp = rx.recv().await.unwrap();
            let MockDatastoreEvents::Write(_, _, _, _, resp) = resp else {
                unreachable!();
            };
            resp.send(Err(AttErrorCode::APPLICATION_ERROR)).unwrap();

            // assert: got the supplied response
            assert_eq!(pending.await.unwrap(), Err(AttErrorCode::APPLICATION_ERROR));
        });
    }

    #[test]
    fn test_rejected_prepared_write() {
        // arrange
        let (datastore, mut rx) = MockDatastore::new();

        // act: send prepare write request
        let resp = block_on_locally(RawGattDatastore::write(
            &datastore,
            TCB_IDX,
            HANDLE,
            AttributeBackingType::Characteristic,
            GattWriteRequestType::Prepare { offset: 1 },
            make_data().view(),
        ));

        // assert: got the correct error code
        assert_eq!(resp, Err(AttErrorCode::WRITE_REQUEST_REJECTED));
        // assert: no event sent up
        assert_eq!(rx.try_recv().unwrap_err(), TryRecvError::Empty);
    }

    #[test]
    fn test_dropped_write_command() {
        // arrange
        let (datastore, mut rx) = MockDatastore::new();

        // act: send write command
        RawGattDatastore::write_no_response(
            &datastore,
            TCB_IDX,
            HANDLE,
            AttributeBackingType::Characteristic,
            make_data().view(),
        );

        // assert: no event sent up
        assert_eq!(rx.try_recv().unwrap_err(), TryRecvError::Empty);
    }

    #[test]
    fn test_execute_noop() {
        // arrange
        let (datastore, mut rx) = MockDatastore::new();

        // act: send execute request
        let resp = block_on_locally(RawGattDatastore::execute(
            &datastore,
            TCB_IDX,
            TransactionDecision::Execute,
        ));

        // assert: succeeds trivially
        assert!(resp.is_ok());
        // assert: no event sent up
        assert_eq!(rx.try_recv().unwrap_err(), TryRecvError::Empty);
    }
}
