use std::time::Duration;

use log::{trace, warn};
use tokio::{
    sync::mpsc::{self, error::TrySendError},
    time::timeout,
};

use crate::{
    gatt::ids::AttHandle,
    packets::{AttAttributeDataChild, AttChild, AttHandleValueIndicationBuilder, Serializable},
    utils::packet::build_att_data,
};

use super::{
    att_database::{AttDatabase, StableAttDatabase},
    att_server_bearer::SendError,
};

#[derive(Debug)]
/// Errors that can occur while sending an indication
pub enum IndicationError {
    /// The provided data exceeds the MTU limitations
    DataExceedsMtu {
        /// The actual max payload size permitted
        /// (ATT_MTU - 3, since 3 bytes are needed for the header)
        mtu: usize,
    },
    /// The indicated attribute handle does not exist
    AttributeNotFound,
    /// The indicated attribute does not support indications
    IndicationsNotSupported,
    /// Failed to send the outgoing indication packet
    SendError(SendError),
    /// Did not receive a confirmation in the given time (30s)
    ConfirmationTimeout,
    /// The connection was dropped while waiting for a confirmation
    ConnectionDroppedWhileWaitingForConfirmation,
}

pub struct IndicationHandler<T> {
    db: T,
    pending_confirmation: mpsc::Receiver<()>,
}

impl<T: AttDatabase> IndicationHandler<T> {
    pub fn new(db: T) -> (Self, ConfirmationWatcher) {
        let (tx, rx) = mpsc::channel(1);
        (Self { db, pending_confirmation: rx }, ConfirmationWatcher(tx))
    }

    pub async fn send(
        &mut self,
        handle: AttHandle,
        data: AttAttributeDataChild,
        mtu: usize,
        send_packet: impl FnOnce(AttChild) -> Result<(), SendError>,
    ) -> Result<(), IndicationError> {
        let data_size = data
            .size_in_bits()
            .map_err(SendError::SerializeError)
            .map_err(IndicationError::SendError)?;
        // As per Core Spec 5.3 Vol 3F 3.4.7.2, the indicated value must be at most
        // ATT_MTU-3
        if data_size > (mtu - 3) * 8 {
            return Err(IndicationError::DataExceedsMtu { mtu: mtu - 3 });
        }

        if !self
            .db
            .snapshot()
            .find_attribute(handle)
            .ok_or(IndicationError::AttributeNotFound)?
            .permissions
            .indicate()
        {
            warn!("cannot send indication for {handle:?} since it does not support indications");
            return Err(IndicationError::IndicationsNotSupported);
        }

        // flushing any confirmations that arrived before we sent the next indication
        let _ = self.pending_confirmation.try_recv();

        send_packet(
            AttHandleValueIndicationBuilder { handle: handle.into(), value: build_att_data(data) }
                .into(),
        )
        .map_err(IndicationError::SendError)?;

        match timeout(Duration::from_secs(30), self.pending_confirmation.recv()).await {
            Ok(Some(())) => Ok(()),
            Ok(None) => {
                warn!("connection dropped while waiting for indication confirmation");
                Err(IndicationError::ConnectionDroppedWhileWaitingForConfirmation)
            }
            Err(_) => {
                warn!("Sent indication but received no response for 30s");
                Err(IndicationError::ConfirmationTimeout)
            }
        }
    }
}

pub struct ConfirmationWatcher(mpsc::Sender<()>);

impl ConfirmationWatcher {
    pub fn on_confirmation(&self) {
        match self.0.try_send(()) {
            Ok(_) => {
                trace!("Got AttHandleValueConfirmation")
            }
            Err(TrySendError::Full(_)) => {
                warn!("Got a second AttHandleValueConfirmation before the first was processed, dropping it")
            }
            Err(TrySendError::Closed(_)) => {
                warn!("Got an AttHandleValueConfirmation while no indications are outstanding, dropping it")
            }
        }
    }
}

#[cfg(test)]
mod test {
    use tokio::{sync::oneshot, task::spawn_local, time::Instant};

    use crate::{
        core::uuid::Uuid,
        gatt::server::{
            att_database::AttAttribute, gatt_database::AttPermissions,
            test::test_att_db::TestAttDatabase,
        },
        utils::task::block_on_locally,
    };

    use super::*;

    const HANDLE: AttHandle = AttHandle(1);
    const NONEXISTENT_HANDLE: AttHandle = AttHandle(2);
    const NON_INDICATE_HANDLE: AttHandle = AttHandle(3);
    const MTU: usize = 32;

    fn get_data() -> AttAttributeDataChild {
        AttAttributeDataChild::RawData([1, 2, 3].into())
    }

    fn get_att_database() -> TestAttDatabase {
        TestAttDatabase::new(vec![
            (
                AttAttribute {
                    handle: HANDLE,
                    type_: Uuid::new(123),
                    permissions: AttPermissions::INDICATE,
                },
                vec![],
            ),
            (
                AttAttribute {
                    handle: NON_INDICATE_HANDLE,
                    type_: Uuid::new(123),
                    permissions: AttPermissions::READABLE,
                },
                vec![],
            ),
        ])
    }

    #[test]
    fn test_indication_sent() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, _confirmation_watcher) =
                IndicationHandler::new(get_att_database());
            let (tx, rx) = oneshot::channel();

            // act: send an indication
            spawn_local(async move {
                indication_handler
                    .send(HANDLE, get_data(), MTU, move |packet| {
                        tx.send(packet).unwrap();
                        Ok(())
                    })
                    .await
            });

            // assert: that an AttHandleValueIndication was sent on the channel
            let AttChild::AttHandleValueIndication(indication) = rx.await.unwrap() else {
                unreachable!()
            };
            assert_eq!(
                indication,
                AttHandleValueIndicationBuilder {
                    handle: HANDLE.into(),
                    value: build_att_data(get_data()),
                }
            );
        });
    }

    #[test]
    fn test_invalid_handle() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, _confirmation_watcher) =
                IndicationHandler::new(get_att_database());

            // act: send an indication on a nonexistent handle
            let ret = indication_handler
                .send(NONEXISTENT_HANDLE, get_data(), MTU, move |_| unreachable!())
                .await;

            // assert: that we failed with IndicationError::AttributeNotFound
            assert!(matches!(ret, Err(IndicationError::AttributeNotFound)));
        });
    }

    #[test]
    fn test_unsupported_permission() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, _confirmation_watcher) =
                IndicationHandler::new(get_att_database());

            // act: send an indication on an attribute that does not support indications
            let ret = indication_handler
                .send(NON_INDICATE_HANDLE, get_data(), MTU, move |_| unreachable!())
                .await;

            // assert: that we failed with IndicationError::IndicationsNotSupported
            assert!(matches!(ret, Err(IndicationError::IndicationsNotSupported)));
        });
    }

    #[test]
    fn test_confirmation_handled() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, confirmation_watcher) =
                IndicationHandler::new(get_att_database());
            let (tx, rx) = oneshot::channel();

            // act: send an indication
            let pending_result = spawn_local(async move {
                indication_handler
                    .send(HANDLE, get_data(), MTU, move |packet| {
                        tx.send(packet).unwrap();
                        Ok(())
                    })
                    .await
            });
            // when the indication is sent, send a confirmation in response
            rx.await.unwrap();
            confirmation_watcher.on_confirmation();

            // assert: the indication was successfully sent
            assert!(matches!(pending_result.await.unwrap(), Ok(())));
        });
    }

    #[test]
    fn test_unblock_on_disconnect() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, confirmation_watcher) =
                IndicationHandler::new(get_att_database());
            let (tx, rx) = oneshot::channel();

            // act: send an indication
            let pending_result = spawn_local(async move {
                indication_handler
                    .send(HANDLE, get_data(), MTU, move |packet| {
                        tx.send(packet).unwrap();
                        Ok(())
                    })
                    .await
            });
            // when the indication is sent, drop the confirmation watcher (as would happen
            // upon a disconnection)
            rx.await.unwrap();
            drop(confirmation_watcher);

            // assert: we get the appropriate error
            assert!(matches!(
                pending_result.await.unwrap(),
                Err(IndicationError::ConnectionDroppedWhileWaitingForConfirmation)
            ));
        });
    }

    #[test]
    fn test_spurious_confirmations() {
        block_on_locally(async move {
            // arrange: send a few confirmations in advance
            let (mut indication_handler, confirmation_watcher) =
                IndicationHandler::new(get_att_database());
            let (tx, rx) = oneshot::channel();
            confirmation_watcher.on_confirmation();
            confirmation_watcher.on_confirmation();

            // act: send an indication
            let pending_result = spawn_local(async move {
                indication_handler
                    .send(HANDLE, get_data(), MTU, move |packet| {
                        tx.send(packet).unwrap();
                        Ok(())
                    })
                    .await
            });
            // when the indication is sent, drop the confirmation watcher (so we won't block
            // forever)
            rx.await.unwrap();
            drop(confirmation_watcher);

            // assert: we get the appropriate error, rather than an Ok(())
            // (which would have been the case if we had processed the spurious
            // confirmations)
            assert!(matches!(
                pending_result.await.unwrap(),
                Err(IndicationError::ConnectionDroppedWhileWaitingForConfirmation)
            ));
        });
    }

    #[test]
    fn test_indication_timeout() {
        block_on_locally(async move {
            // arrange: send a few confirmations in advance
            let (mut indication_handler, confirmation_watcher) =
                IndicationHandler::new(get_att_database());
            let (tx, rx) = oneshot::channel();
            confirmation_watcher.on_confirmation();
            confirmation_watcher.on_confirmation();

            // act: send an indication
            let time_sent = Instant::now();
            let pending_result = spawn_local(async move {
                indication_handler
                    .send(HANDLE, get_data(), MTU, move |packet| {
                        tx.send(packet).unwrap();
                        Ok(())
                    })
                    .await
            });
            // after it is sent, wait for the timer to fire
            rx.await.unwrap();

            // assert: we get the appropriate error
            assert!(matches!(
                pending_result.await.unwrap(),
                Err(IndicationError::ConfirmationTimeout)
            ));
            // after the appropriate interval
            // note: this is not really timing-dependent, since we are using a simulated
            // clock TODO(aryarahul) - why is this not exactly 30s?
            let time_slept = Instant::now().duration_since(time_sent);
            assert!(time_slept > Duration::from_secs(29));
            assert!(time_slept < Duration::from_secs(31));
        });
    }

    #[test]
    fn test_mtu_exceeds() {
        block_on_locally(async move {
            // arrange
            let (mut indication_handler, _confirmation_watcher) =
                IndicationHandler::new(get_att_database());

            // act: send an indication with an ATT_MTU of 4 and data length of 3
            let res = indication_handler
                .send(
                    HANDLE,
                    AttAttributeDataChild::RawData([1, 2, 3].into()),
                    4,
                    move |_| unreachable!(),
                )
                .await;

            // assert: that we got the expected error, indicating the max data size (not the
            // ATT_MTU, but ATT_MTU-3)
            assert!(matches!(res, Err(IndicationError::DataExceedsMtu { mtu: 1 })));
        });
    }
}
