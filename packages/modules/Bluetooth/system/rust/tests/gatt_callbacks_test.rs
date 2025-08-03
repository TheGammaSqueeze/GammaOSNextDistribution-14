mod utils;

use std::{rc::Rc, time::Duration};

use bluetooth_core::{
    gatt::{
        callbacks::{
            CallbackResponseError, CallbackTransactionManager, GattWriteRequestType, GattWriteType,
            RawGattDatastore, TransactionDecision,
        },
        ffi::AttributeBackingType,
        ids::{AttHandle, ConnectionId, ServerId, TransactionId, TransportIndex},
        mocks::mock_callbacks::{MockCallbackEvents, MockCallbacks},
    },
    packets::{AttAttributeDataChild, AttErrorCode, Packet},
    utils::packet::{build_att_data, build_view_or_crash},
};
use tokio::{sync::mpsc::UnboundedReceiver, task::spawn_local, time::Instant};
use utils::start_test;

const TCB_IDX: TransportIndex = TransportIndex(1);
const SERVER_ID: ServerId = ServerId(2);

const CONN_ID: ConnectionId = ConnectionId::new(TCB_IDX, SERVER_ID);

const HANDLE_1: AttHandle = AttHandle(3);
const BACKING_TYPE: AttributeBackingType = AttributeBackingType::Descriptor;

const OFFSET: u32 = 12;
const WRITE_REQUEST_TYPE: GattWriteRequestType = GattWriteRequestType::Prepare { offset: 7 };

fn initialize_manager_with_connection(
) -> (Rc<CallbackTransactionManager>, UnboundedReceiver<MockCallbackEvents>) {
    let (callbacks, callbacks_rx) = MockCallbacks::new();
    let callback_manager = Rc::new(CallbackTransactionManager::new(Rc::new(callbacks)));
    (callback_manager, callbacks_rx)
}

async fn pull_trans_id(events_rx: &mut UnboundedReceiver<MockCallbackEvents>) -> TransactionId {
    match events_rx.recv().await.unwrap() {
        MockCallbackEvents::OnServerRead(_, trans_id, _, _, _) => trans_id,
        MockCallbackEvents::OnServerWrite(_, trans_id, _, _, _, _) => trans_id,
        MockCallbackEvents::OnExecute(_, trans_id, _) => trans_id,
        _ => unimplemented!(),
    }
}

#[test]
fn test_read_characteristic_callback() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start read operation
        spawn_local(async move {
            callback_manager
                .get_datastore(SERVER_ID)
                .read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE)
                .await
        });

        // assert: verify the read callback is received
        let MockCallbackEvents::OnServerRead(
            CONN_ID, _, HANDLE_1, BACKING_TYPE, OFFSET,
        ) = callbacks_rx.recv().await.unwrap() else {
          unreachable!()
        };
    });
}

#[test]
fn test_read_characteristic_response() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();
        let data = Ok(AttAttributeDataChild::RawData([1, 2].into()));

        // act: start read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_read =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );
        // provide a response
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager.send_response(CONN_ID, trans_id, data.clone()).unwrap();

        // assert: that the supplied data was correctly read
        assert_eq!(pending_read.await.unwrap(), data);
    });
}

#[test]
fn test_sequential_reads() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();
        let data1 = Ok(AttAttributeDataChild::RawData([1, 2].into()));
        let data2 = Ok(AttAttributeDataChild::RawData([3, 4].into()));

        // act: start read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_read_1 =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );
        // respond to first
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager.send_response(CONN_ID, trans_id, data1.clone()).unwrap();

        // do a second read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_read_2 =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );
        // respond to second
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager.send_response(CONN_ID, trans_id, data2.clone()).unwrap();

        // assert: that both operations got the correct response
        assert_eq!(pending_read_1.await.unwrap(), data1);
        assert_eq!(pending_read_2.await.unwrap(), data2);
    });
}

#[test]
fn test_concurrent_reads() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();
        let data1 = Ok(AttAttributeDataChild::RawData([1, 2].into()));
        let data2 = Ok(AttAttributeDataChild::RawData([3, 4].into()));

        // act: start read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_read_1 =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );

        // do a second read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_read_2 =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );

        // respond to first
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager.send_response(CONN_ID, trans_id, data1.clone()).unwrap();

        // respond to second
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager.send_response(CONN_ID, trans_id, data2.clone()).unwrap();

        // assert: that both operations got the correct response
        assert_eq!(pending_read_1.await.unwrap(), data1);
        assert_eq!(pending_read_2.await.unwrap(), data2);
    });
}

#[test]
fn test_distinct_transaction_ids() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start two read operations concurrently
        let datastore = callback_manager.get_datastore(SERVER_ID);
        spawn_local(async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await });
        let datastore = callback_manager.get_datastore(SERVER_ID);
        spawn_local(async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await });

        // pull both trans_ids
        let trans_id_1 = pull_trans_id(&mut callbacks_rx).await;
        let trans_id_2 = pull_trans_id(&mut callbacks_rx).await;

        // assert: that the trans_ids are distinct
        assert_ne!(trans_id_1, trans_id_2);
    });
}

#[test]
fn test_invalid_trans_id() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();
        let data = Ok(AttAttributeDataChild::RawData([1, 2].into()));

        // act: start a read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        spawn_local(async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await });
        // respond with the correct conn_id but an invalid trans_id
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        let invalid_trans_id = TransactionId(trans_id.0 + 1);
        let err = callback_manager.send_response(CONN_ID, invalid_trans_id, data).unwrap_err();

        // assert
        assert_eq!(err, CallbackResponseError::NonExistentTransaction(invalid_trans_id));
    });
}

#[test]
fn test_invalid_conn_id() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();
        let data = Ok(AttAttributeDataChild::RawData([1, 2].into()));

        // act: start a read operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        spawn_local(async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await });
        // respond with the correct trans_id but an invalid conn_id
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        let invalid_conn_id = ConnectionId(CONN_ID.0 + 1);
        let err = callback_manager.send_response(invalid_conn_id, trans_id, data).unwrap_err();

        // assert
        assert_eq!(err, CallbackResponseError::NonExistentTransaction(trans_id));
    });
}

#[test]
fn test_write_characteristic_callback() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start write operation
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData([1, 2].into())));
        let cloned_data = data.view().to_owned_packet();
        spawn_local(async move {
            callback_manager
                .get_datastore(SERVER_ID)
                .write(TCB_IDX, HANDLE_1, BACKING_TYPE, WRITE_REQUEST_TYPE, cloned_data.view())
                .await
        });

        // assert: verify the write callback is received
        let MockCallbackEvents::OnServerWrite(
            CONN_ID, _, HANDLE_1, BACKING_TYPE, GattWriteType::Request(WRITE_REQUEST_TYPE), recv_data
        ) = callbacks_rx.recv().await.unwrap() else {
          unreachable!()
        };
        assert_eq!(
            recv_data.view().get_raw_payload().collect::<Vec<_>>(),
            data.view().get_raw_payload().collect::<Vec<_>>()
        );
    });
}

#[test]
fn test_write_characteristic_response() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start write operation
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData([1, 2].into())));
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_write = spawn_local(async move {
            datastore
                .write(TCB_IDX, HANDLE_1, BACKING_TYPE, GattWriteRequestType::Request, data.view())
                .await
        });
        // provide a response with some error code
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager
            .send_response(CONN_ID, trans_id, Err(AttErrorCode::WRITE_NOT_PERMITTED))
            .unwrap();

        // assert: that the error code was received
        assert_eq!(pending_write.await.unwrap(), Err(AttErrorCode::WRITE_NOT_PERMITTED));
    });
}

#[test]
fn test_response_timeout() {
    start_test(async {
        // arrange
        let (callback_manager, _callbacks_rx) = initialize_manager_with_connection();

        // act: start operation
        let time_sent = Instant::now();
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending_write =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );

        // assert: that we time-out after 15s
        assert_eq!(pending_write.await.unwrap(), Err(AttErrorCode::UNLIKELY_ERROR));
        let time_slept = Instant::now().duration_since(time_sent);
        assert!(time_slept > Duration::from_secs(14));
        assert!(time_slept < Duration::from_secs(16));
    });
}

#[test]
fn test_transaction_cleanup_after_timeout() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start an operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        // let it time out
        assert_eq!(pending.await.unwrap(), Err(AttErrorCode::UNLIKELY_ERROR));
        // try responding to it now
        let resp =
            callback_manager.send_response(CONN_ID, trans_id, Err(AttErrorCode::INVALID_HANDLE));

        // assert: the response failed
        assert_eq!(resp, Err(CallbackResponseError::NonExistentTransaction(trans_id)));
    });
}

#[test]
fn test_listener_hang_up() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start an operation
        let datastore = callback_manager.get_datastore(SERVER_ID);
        let pending =
            spawn_local(
                async move { datastore.read(TCB_IDX, HANDLE_1, OFFSET, BACKING_TYPE).await },
            );
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        // cancel the listener, wait for it to stop
        pending.abort();
        pending.await.unwrap_err();
        // try responding to it now
        let resp =
            callback_manager.send_response(CONN_ID, trans_id, Err(AttErrorCode::INVALID_HANDLE));

        // assert: we get the expected error
        assert_eq!(resp, Err(CallbackResponseError::ListenerHungUp(trans_id)));
    });
}

#[test]
fn test_write_no_response_callback() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start write_no_response operation
        let data =
            build_view_or_crash(build_att_data(AttAttributeDataChild::RawData([1, 2].into())));
        callback_manager.get_datastore(SERVER_ID).write_no_response(
            TCB_IDX,
            HANDLE_1,
            BACKING_TYPE,
            data.view(),
        );

        // assert: verify the write callback is received
        let MockCallbackEvents::OnServerWrite(
            CONN_ID, _, HANDLE_1, BACKING_TYPE, GattWriteType::Command, recv_data
        ) = callbacks_rx.recv().await.unwrap() else {
          unreachable!()
        };
        assert_eq!(
            recv_data.view().get_raw_payload().collect::<Vec<_>>(),
            data.view().get_raw_payload().collect::<Vec<_>>()
        );
    });
}

#[test]
fn test_execute_characteristic_callback() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start execute operation
        spawn_local(async move {
            callback_manager
                .get_datastore(SERVER_ID)
                .execute(TCB_IDX, TransactionDecision::Cancel)
                .await
        });

        // assert: verify the execute callback is received
        let MockCallbackEvents::OnExecute(
            CONN_ID, _, TransactionDecision::Cancel
        ) = callbacks_rx.recv().await.unwrap() else {
          unreachable!()
        };
    });
}

#[test]
fn test_execute_characteristic_response() {
    start_test(async {
        // arrange
        let (callback_manager, mut callbacks_rx) = initialize_manager_with_connection();

        // act: start execute operation
        let cloned_manager = callback_manager.clone();
        let pending_execute = spawn_local(async move {
            cloned_manager
                .get_datastore(SERVER_ID)
                .execute(TCB_IDX, TransactionDecision::Cancel)
                .await
        });
        // provide a response with some error code
        let trans_id = pull_trans_id(&mut callbacks_rx).await;
        callback_manager
            .send_response(CONN_ID, trans_id, Err(AttErrorCode::WRITE_NOT_PERMITTED))
            .unwrap();

        // assert: that the error code was received
        assert_eq!(pending_execute.await.unwrap(), Err(AttErrorCode::WRITE_NOT_PERMITTED));
    });
}
