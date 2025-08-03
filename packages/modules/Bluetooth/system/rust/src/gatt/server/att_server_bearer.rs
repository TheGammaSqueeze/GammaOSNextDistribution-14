//! This module handles an individual connection on the ATT fixed channel.
//! It handles ATT transactions and unacknowledged operations, backed by an
//! AttDatabase (that may in turn be backed by an upper-layer protocol)

use std::{cell::Cell, future::Future};

use anyhow::Result;
use log::{error, trace, warn};
use tokio::task::spawn_local;

use crate::{
    core::{
        shared_box::{WeakBox, WeakBoxRef},
        shared_mutex::SharedMutex,
    },
    gatt::{
        ids::AttHandle,
        mtu::{AttMtu, MtuEvent},
        opcode_types::{classify_opcode, OperationType},
    },
    packets::{
        AttAttributeDataChild, AttBuilder, AttChild, AttErrorCode, AttErrorResponseBuilder,
        AttView, Packet, SerializeError,
    },
    utils::{owned_handle::OwnedHandle, packet::HACK_child_to_opcode},
};

use super::{
    att_database::AttDatabase,
    command_handler::AttCommandHandler,
    indication_handler::{ConfirmationWatcher, IndicationError, IndicationHandler},
    request_handler::AttRequestHandler,
};

enum AttRequestState<T: AttDatabase> {
    Idle(AttRequestHandler<T>),
    Pending(Option<OwnedHandle<()>>),
}

/// The errors that can occur while trying to send a packet
#[derive(Debug)]
pub enum SendError {
    /// The packet failed to serialize
    SerializeError(SerializeError),
    /// The connection no longer exists
    ConnectionDropped,
}

/// This represents a single ATT bearer (currently, always the unenhanced fixed
/// channel on LE) The AttRequestState ensures that only one transaction can
/// take place at a time
pub struct AttServerBearer<T: AttDatabase> {
    // general
    send_packet: Box<dyn Fn(AttBuilder) -> Result<(), SerializeError>>,
    mtu: AttMtu,

    // request state
    curr_request: Cell<AttRequestState<T>>,

    // indication state
    indication_handler: SharedMutex<IndicationHandler<T>>,
    pending_confirmation: ConfirmationWatcher,

    // command handler (across all bearers)
    command_handler: AttCommandHandler<T>,
}

impl<T: AttDatabase + Clone + 'static> AttServerBearer<T> {
    /// Constructor, wrapping an ATT channel (for outgoing packets) and an
    /// AttDatabase
    pub fn new(
        db: T,
        send_packet: impl Fn(AttBuilder) -> Result<(), SerializeError> + 'static,
    ) -> Self {
        let (indication_handler, pending_confirmation) = IndicationHandler::new(db.clone());
        Self {
            send_packet: Box::new(send_packet),
            mtu: AttMtu::new(),

            curr_request: AttRequestState::Idle(AttRequestHandler::new(db.clone())).into(),

            indication_handler: SharedMutex::new(indication_handler),
            pending_confirmation,

            command_handler: AttCommandHandler::new(db),
        }
    }

    fn send_packet(&self, packet: impl Into<AttChild>) -> Result<(), SerializeError> {
        let child = packet.into();
        let packet = AttBuilder { opcode: HACK_child_to_opcode(&child), _child_: child };
        (self.send_packet)(packet)
    }
}

impl<T: AttDatabase + Clone + 'static> WeakBoxRef<'_, AttServerBearer<T>> {
    /// Handle an incoming packet, and send outgoing packets as appropriate
    /// using the owned ATT channel.
    pub fn handle_packet(&self, packet: AttView<'_>) {
        match classify_opcode(packet.get_opcode()) {
            OperationType::Command => {
                self.command_handler.process_packet(packet);
            }
            OperationType::Request => {
                self.handle_request(packet);
            }
            OperationType::Confirmation => self.pending_confirmation.on_confirmation(),
            OperationType::Response | OperationType::Notification | OperationType::Indication => {
                unreachable!("the arbiter should not let us receive these packet types")
            }
        }
    }

    /// Send an indication, wait for the peer confirmation, and return the
    /// appropriate status If multiple calls are outstanding, they are
    /// executed in FIFO order.
    pub fn send_indication(
        &self,
        handle: AttHandle,
        data: AttAttributeDataChild,
    ) -> impl Future<Output = Result<(), IndicationError>> {
        trace!("sending indication for handle {handle:?}");

        let locked_indication_handler = self.indication_handler.lock();
        let pending_mtu = self.mtu.snapshot();
        let this = self.downgrade();

        async move {
            // first wait until we are at the head of the queue and are ready to send
            // indications
            let mut indication_handler = locked_indication_handler
                .await
                .ok_or_else(|| {
                    warn!("indication for handle {handle:?} cancelled while queued since the connection dropped");
                    IndicationError::SendError(SendError::ConnectionDropped)
                })?;
            // then, if MTU negotiation is taking place, wait for it to complete
            let mtu = pending_mtu
                .await
                .ok_or_else(|| {
                    warn!("indication for handle {handle:?} cancelled while waiting for MTU exchange to complete since the connection dropped");
                    IndicationError::SendError(SendError::ConnectionDropped)
                })?;
            // finally, send, and wait for a response
            indication_handler.send(handle, data, mtu, |packet| this.try_send_packet(packet)).await
        }
    }

    /// Handle a snooped MTU event, to update the MTU we use for our various
    /// operations
    pub fn handle_mtu_event(&self, mtu_event: MtuEvent) -> Result<()> {
        self.mtu.handle_event(mtu_event)
    }

    fn handle_request(&self, packet: AttView<'_>) {
        let curr_request = self.curr_request.replace(AttRequestState::Pending(None));
        self.curr_request.replace(match curr_request {
            AttRequestState::Idle(mut request_handler) => {
                // even if the MTU is updated afterwards, 5.3 3F 3.4.2.2 states that the
                // request-time MTU should be used
                let mtu = self.mtu.snapshot_or_default();
                let packet = packet.to_owned_packet();
                let this = self.downgrade();
                let task = spawn_local(async move {
                    trace!("starting ATT transaction");
                    let reply = request_handler.process_packet(packet.view(), mtu).await;
                    this.with(|this| {
                        this.map(|this| {
                            match this.send_packet(reply) {
                                Ok(_) => {
                                    trace!("reply packet sent")
                                }
                                Err(err) => {
                                    error!("serializer failure {err:?}, dropping packet and sending failed reply");
                                    // if this also fails, we're stuck
                                    if let Err(err) = this.send_packet(AttErrorResponseBuilder {
                                        opcode_in_error: packet.view().get_opcode(),
                                        handle_in_error: AttHandle(0).into(),
                                        error_code: AttErrorCode::UNLIKELY_ERROR,
                                    }) {
                                        panic!("unexpected serialize error for known-good packet {err:?}")
                                    }
                                }
                            };
                            // ready for next transaction
                            this.curr_request.replace(AttRequestState::Idle(request_handler));
                        })
                    });
                });
                AttRequestState::Pending(Some(task.into()))
            }
            AttRequestState::Pending(_) => {
                warn!("multiple ATT operations cannot simultaneously take place, dropping one");
                // TODO(aryarahul) - disconnect connection here;
                curr_request
            }
        });
    }
}

impl<T: AttDatabase + Clone + 'static> WeakBox<AttServerBearer<T>> {
    fn try_send_packet(&self, packet: impl Into<AttChild>) -> Result<(), SendError> {
        self.with(|this| {
            this.ok_or_else(|| {
                warn!("connection dropped before packet sent");
                SendError::ConnectionDropped
            })?
            .send_packet(packet)
            .map_err(SendError::SerializeError)
        })
    }
}

#[cfg(test)]
mod test {
    use std::rc::Rc;

    use tokio::sync::mpsc::{error::TryRecvError, unbounded_channel, UnboundedReceiver};

    use super::*;

    use crate::{
        core::{shared_box::SharedBox, uuid::Uuid},
        gatt::{
            ffi::AttributeBackingType,
            ids::TransportIndex,
            mocks::mock_datastore::{MockDatastore, MockDatastoreEvents},
            server::{
                att_database::{AttAttribute, AttPermissions},
                gatt_database::{
                    GattCharacteristicWithHandle, GattDatabase, GattServiceWithHandle,
                },
                test::test_att_db::TestAttDatabase,
            },
        },
        packets::{
            AttAttributeDataChild, AttHandleValueConfirmationBuilder, AttOpcode,
            AttReadRequestBuilder, AttReadResponseBuilder,
        },
        utils::{
            packet::{build_att_data, build_att_view_or_crash},
            task::{block_on_locally, try_await},
        },
    };

    const VALID_HANDLE: AttHandle = AttHandle(3);
    const INVALID_HANDLE: AttHandle = AttHandle(4);
    const ANOTHER_VALID_HANDLE: AttHandle = AttHandle(10);

    const TCB_IDX: TransportIndex = TransportIndex(1);

    fn open_connection(
    ) -> (SharedBox<AttServerBearer<TestAttDatabase>>, UnboundedReceiver<AttBuilder>) {
        let db = TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: VALID_HANDLE,
                    type_: Uuid::new(0x1234),
                    permissions: AttPermissions::READABLE | AttPermissions::INDICATE,
                },
                vec![5, 6],
            ),
            (
                AttAttribute {
                    handle: ANOTHER_VALID_HANDLE,
                    type_: Uuid::new(0x5678),
                    permissions: AttPermissions::READABLE | AttPermissions::INDICATE,
                },
                vec![5, 6],
            ),
        ]);
        let (tx, rx) = unbounded_channel();
        let conn = AttServerBearer::new(db, move |packet| {
            tx.send(packet).unwrap();
            Ok(())
        })
        .into();
        (conn, rx)
    }

    #[test]
    fn test_single_transaction() {
        block_on_locally(async {
            let (conn, mut rx) = open_connection();
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttReadRequestBuilder {
                    attribute_handle: VALID_HANDLE.into(),
                })
                .view(),
            );
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::READ_RESPONSE);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
        });
    }

    #[test]
    fn test_sequential_transactions() {
        block_on_locally(async {
            let (conn, mut rx) = open_connection();
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttReadRequestBuilder {
                    attribute_handle: INVALID_HANDLE.into(),
                })
                .view(),
            );
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::ERROR_RESPONSE);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));

            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttReadRequestBuilder {
                    attribute_handle: VALID_HANDLE.into(),
                })
                .view(),
            );
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::READ_RESPONSE);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
        });
    }

    #[test]
    fn test_concurrent_transaction_failure() {
        // arrange: AttServerBearer linked to a backing datastore and packet queue, with
        // two characteristics in the database
        let (datastore, mut data_rx) = MockDatastore::new();
        let datastore = Rc::new(datastore);
        let db = SharedBox::new(GattDatabase::new());
        db.add_service_with_handles(
            GattServiceWithHandle {
                handle: AttHandle(1),
                type_: Uuid::new(1),
                characteristics: vec![
                    GattCharacteristicWithHandle {
                        handle: VALID_HANDLE,
                        type_: Uuid::new(2),
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    },
                    GattCharacteristicWithHandle {
                        handle: ANOTHER_VALID_HANDLE,
                        type_: Uuid::new(2),
                        permissions: AttPermissions::READABLE,
                        descriptors: vec![],
                    },
                ],
            },
            datastore,
        )
        .unwrap();
        let (tx, mut rx) = unbounded_channel();
        let send_packet = move |packet| {
            tx.send(packet).unwrap();
            Ok(())
        };
        let conn = SharedBox::new(AttServerBearer::new(db.get_att_database(TCB_IDX), send_packet));
        let data = AttAttributeDataChild::RawData([1, 2].into());

        // act: send two read requests before replying to either read
        // first request
        block_on_locally(async {
            let req1 = build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: VALID_HANDLE.into(),
            });
            conn.as_ref().handle_packet(req1.view());
            // second request
            let req2 = build_att_view_or_crash(AttReadRequestBuilder {
                attribute_handle: ANOTHER_VALID_HANDLE.into(),
            });
            conn.as_ref().handle_packet(req2.view());
            // handle first reply
            let MockDatastoreEvents::Read(TCB_IDX, VALID_HANDLE, AttributeBackingType::Characteristic, data_resp) =
                data_rx.recv().await.unwrap() else {
                    unreachable!();
            };
            data_resp.send(Ok(data.clone())).unwrap();
            trace!("reply sent from upper tester");

            // assert: that the first reply was made
            let resp = rx.recv().await.unwrap();
            assert_eq!(
                resp,
                AttBuilder {
                    opcode: AttOpcode::READ_RESPONSE,
                    _child_: AttReadResponseBuilder { value: build_att_data(data) }.into(),
                }
            );
            // assert no other replies were made
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
            // assert no callbacks are pending
            assert_eq!(data_rx.try_recv().unwrap_err(), TryRecvError::Empty);
        });
    }

    #[test]
    fn test_indication_confirmation() {
        block_on_locally(async {
            // arrange
            let (conn, mut rx) = open_connection();

            // act: send an indication
            let pending_send =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
            // and the confirmation
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );

            // assert: the indication was correctly sent
            assert!(matches!(pending_send.await.unwrap(), Ok(())));
        });
    }

    #[test]
    fn test_sequential_indications() {
        block_on_locally(async {
            // arrange
            let (conn, mut rx) = open_connection();

            // act: send the first indication
            let pending_send1 =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            // wait for/capture the outgoing packet
            let sent1 = rx.recv().await.unwrap();
            // send the response
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );
            // send the second indication
            let pending_send2 =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            // wait for/capture the outgoing packet
            let sent2 = rx.recv().await.unwrap();
            // and the response
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );

            // assert: exactly two indications were sent
            assert_eq!(sent1.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(sent2.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
            // and that both got successful responses
            assert!(matches!(pending_send1.await.unwrap(), Ok(())));
            assert!(matches!(pending_send2.await.unwrap(), Ok(())));
        });
    }

    #[test]
    fn test_queued_indications_only_one_sent() {
        block_on_locally(async {
            // arrange
            let (conn, mut rx) = open_connection();

            // act: send two indications simultaneously
            let pending_send1 =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            let pending_send2 = spawn_local(conn.as_ref().send_indication(
                ANOTHER_VALID_HANDLE,
                AttAttributeDataChild::RawData([1, 2, 3].into()),
            ));
            // assert: only one was initially sent
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
            // and both are still pending
            assert!(!pending_send1.is_finished());
            assert!(!pending_send2.is_finished());
        });
    }

    #[test]
    fn test_queued_indications_dequeue_second() {
        block_on_locally(async {
            // arrange
            let (conn, mut rx) = open_connection();

            // act: send two indications simultaneously
            let pending_send1 =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            let pending_send2 = spawn_local(conn.as_ref().send_indication(
                ANOTHER_VALID_HANDLE,
                AttAttributeDataChild::RawData([1, 2, 3].into()),
            ));
            // wait for/capture the outgoing packet
            let sent1 = rx.recv().await.unwrap();
            // send response for the first one
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );
            // wait for/capture the outgoing packet
            let sent2 = rx.recv().await.unwrap();

            // assert: the first future has completed successfully, the second one is
            // pending
            assert!(matches!(pending_send1.await.unwrap(), Ok(())));
            assert!(!pending_send2.is_finished());
            // and that both indications have been sent
            assert_eq!(sent1.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(sent2.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
        });
    }

    #[test]
    fn test_queued_indications_complete_both() {
        block_on_locally(async {
            // arrange
            let (conn, mut rx) = open_connection();

            // act: send two indications simultaneously
            let pending_send1 =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));
            let pending_send2 = spawn_local(conn.as_ref().send_indication(
                ANOTHER_VALID_HANDLE,
                AttAttributeDataChild::RawData([1, 2, 3].into()),
            ));
            // wait for/capture the outgoing packet
            let sent1 = rx.recv().await.unwrap();
            // send response for the first one
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );
            // wait for/capture the outgoing packet
            let sent2 = rx.recv().await.unwrap();
            // and now the second
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );

            // assert: both futures have completed successfully
            assert!(matches!(pending_send1.await.unwrap(), Ok(())));
            assert!(matches!(pending_send2.await.unwrap(), Ok(())));
            // and both indications have been sent
            assert_eq!(sent1.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(sent2.opcode, AttOpcode::HANDLE_VALUE_INDICATION);
            assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
        });
    }

    #[test]
    fn test_indication_connection_drop() {
        block_on_locally(async {
            // arrange: a pending indication
            let (conn, mut rx) = open_connection();
            let pending_send =
                spawn_local(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ));

            // act: drop the connection after the indication is sent
            rx.recv().await.unwrap();
            drop(conn);

            // assert: the pending indication fails with the appropriate error
            assert!(matches!(
                pending_send.await.unwrap(),
                Err(IndicationError::ConnectionDroppedWhileWaitingForConfirmation)
            ));
        });
    }

    #[test]
    fn test_single_indication_pending_mtu() {
        block_on_locally(async {
            // arrange: pending MTU negotiation
            let (conn, mut rx) = open_connection();
            conn.as_ref().handle_mtu_event(MtuEvent::OutgoingRequest).unwrap();

            // act: try to send an indication with a large payload size
            let _ =
                try_await(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData((1..50).collect()),
                ))
                .await;
            // then resolve the MTU negotiation with a large MTU
            conn.as_ref().handle_mtu_event(MtuEvent::IncomingResponse(100)).unwrap();

            // assert: the indication was sent
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::HANDLE_VALUE_INDICATION);
        });
    }

    #[test]
    fn test_single_indication_pending_mtu_fail() {
        block_on_locally(async {
            // arrange: pending MTU negotiation
            let (conn, _) = open_connection();
            conn.as_ref().handle_mtu_event(MtuEvent::OutgoingRequest).unwrap();

            // act: try to send an indication with a large payload size
            let pending_mtu =
                try_await(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData((1..50).collect()),
                ))
                .await
                .unwrap_err();
            // then resolve the MTU negotiation with a small MTU
            conn.as_ref().handle_mtu_event(MtuEvent::IncomingResponse(32)).unwrap();

            // assert: the indication failed to send
            assert!(matches!(pending_mtu.await, Err(IndicationError::DataExceedsMtu { .. })));
        });
    }

    #[test]
    fn test_server_transaction_pending_mtu() {
        block_on_locally(async {
            // arrange: pending MTU negotiation
            let (conn, mut rx) = open_connection();
            conn.as_ref().handle_mtu_event(MtuEvent::OutgoingRequest).unwrap();

            // act: send server packet
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttReadRequestBuilder {
                    attribute_handle: VALID_HANDLE.into(),
                })
                .view(),
            );

            // assert: that we reply even while the MTU req is outstanding
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::READ_RESPONSE);
        });
    }

    #[test]
    fn test_queued_indication_pending_mtu_uses_mtu_on_dequeue() {
        block_on_locally(async {
            // arrange: an outstanding indication
            let (conn, mut rx) = open_connection();
            let _ =
                try_await(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                ))
                .await;
            rx.recv().await.unwrap(); // flush rx_queue

            // act: enqueue an indication with a large payload
            let _ =
                try_await(conn.as_ref().send_indication(
                    VALID_HANDLE,
                    AttAttributeDataChild::RawData((1..50).collect()),
                ))
                .await;
            // then perform MTU negotiation to upgrade to a large MTU
            conn.as_ref().handle_mtu_event(MtuEvent::OutgoingRequest).unwrap();
            conn.as_ref().handle_mtu_event(MtuEvent::IncomingResponse(512)).unwrap();
            // finally resolve the first indication, so the second indication can be sent
            conn.as_ref().handle_packet(
                build_att_view_or_crash(AttHandleValueConfirmationBuilder {}).view(),
            );

            // assert: the second indication successfully sent (so it used the new MTU)
            assert_eq!(rx.recv().await.unwrap().opcode, AttOpcode::HANDLE_VALUE_INDICATION);
        });
    }
}
