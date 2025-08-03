//! The MTU on an ATT bearer is determined either by L2CAP (if EATT) or by the
//! ATT_EXCHANGE_MTU procedure (if on an unenhanced bearer).
//!
//! In the latter case, the MTU may be either (1) unset, (2) pending, or (3)
//! set. If the MTU is pending, ATT notifications/indications may not be sent.
//! Refer to Core Spec 5.3 Vol 3F 3.4.2 MTU exchange for full details.

use std::{cell::Cell, future::Future};

use anyhow::{bail, Result};
use log::info;
use tokio::sync::OwnedMutexGuard;

use crate::core::shared_mutex::SharedMutex;

/// An MTU event that we have snooped
pub enum MtuEvent {
    /// We have sent an MTU_REQ
    OutgoingRequest,
    /// We have received an MTU_RESP
    IncomingResponse(usize),
    /// We have received an MTU_REQ (and will immediately reply)
    IncomingRequest(usize),
}

/// The state of MTU negotiation on an unenhanced ATT bearer
pub struct AttMtu {
    /// The MTU we have committed to (i.e. sent a REQ and got a RESP, or
    /// vice-versa)
    previous_mtu: Cell<usize>,
    /// The MTU we have committed or are about to commit to (if a REQ is
    /// pending)
    stable_mtu: SharedMutex<usize>,
    /// Lock guard held if we are currrently performing MTU negotiation
    pending_exchange: Cell<Option<OwnedMutexGuard<usize>>>,
}

// NOTE: this is only true for ATT, not EATT
const DEFAULT_ATT_MTU: usize = 23;

impl AttMtu {
    /// Constructor
    pub fn new() -> Self {
        Self {
            previous_mtu: Cell::new(DEFAULT_ATT_MTU),
            stable_mtu: SharedMutex::new(DEFAULT_ATT_MTU),
            pending_exchange: Cell::new(None),
        }
    }

    /// Get the most recently negotiated MTU, or the default (if an MTU_REQ is
    /// outstanding and we get an ATT_REQ)
    pub fn snapshot_or_default(&self) -> usize {
        self.stable_mtu.try_lock().as_deref().cloned().unwrap_or_else(|_| self.previous_mtu.get())
    }

    /// Get the most recently negotiated MTU, or block if negotiation is ongoing
    /// (i.e. if an MTU_REQ is outstanding)
    pub fn snapshot(&self) -> impl Future<Output = Option<usize>> {
        let pending_snapshot = self.stable_mtu.lock();
        async move { pending_snapshot.await.as_deref().cloned() }
    }

    /// Handle an MtuEvent and update the stored MTU
    pub fn handle_event(&self, event: MtuEvent) -> Result<()> {
        match event {
            MtuEvent::OutgoingRequest => self.on_outgoing_request(),
            MtuEvent::IncomingResponse(mtu) => self.on_incoming_response(mtu),
            MtuEvent::IncomingRequest(mtu) => {
                self.on_incoming_request(mtu);
                Ok(())
            }
        }
    }

    fn on_outgoing_request(&self) -> Result<()> {
        let Ok(pending_mtu) = self.stable_mtu.try_lock() else {
          bail!("Sent ATT_EXCHANGE_MTU_REQ while an existing MTU exchange is taking place");
        };
        info!("Sending MTU_REQ, pausing indications/notifications");
        self.pending_exchange.replace(Some(pending_mtu));
        Ok(())
    }

    fn on_incoming_response(&self, mtu: usize) -> Result<()> {
        let Some(mut pending_exchange) = self.pending_exchange.take() else {
            bail!("Got ATT_EXCHANGE_MTU_RESP when transaction not taking place");
        };
        info!("Got an MTU_RESP of {mtu}");
        *pending_exchange = mtu;
        // note: since MTU_REQ can be sent at most once, this is a no-op, as the
        // stable_mtu will never again be blocked we do it anyway for clarity
        self.previous_mtu.set(mtu);
        Ok(())
    }

    fn on_incoming_request(&self, mtu: usize) {
        self.previous_mtu.set(mtu);
        if let Ok(mut stable_mtu) = self.stable_mtu.try_lock() {
            info!("Accepted an MTU_REQ of {mtu:?}");
            *stable_mtu = mtu;
        } else {
            info!("Accepted an MTU_REQ while our own MTU_REQ was outstanding")
        }
    }
}

#[cfg(test)]
mod test {
    use crate::utils::task::{block_on_locally, try_await};

    use super::*;

    const NEW_MTU: usize = 51;
    const ANOTHER_NEW_MTU: usize = 52;

    #[test]
    fn test_default_mtu() {
        let mtu = AttMtu::new();

        let stable_value = mtu.snapshot_or_default();
        let latest_value = tokio_test::block_on(mtu.snapshot()).unwrap();

        assert_eq!(stable_value, DEFAULT_ATT_MTU);
        assert_eq!(latest_value, DEFAULT_ATT_MTU);
    }

    #[test]
    fn test_guaranteed_mtu_during_client_negotiation() {
        // arrange
        let mtu = AttMtu::new();

        // act: send an MTU_REQ and validate snapshotted value
        mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
        let stable_value = mtu.snapshot_or_default();

        // assert: we use the default MTU for requests handled
        // while our request is pending
        assert_eq!(stable_value, DEFAULT_ATT_MTU);
    }

    #[test]
    fn test_mtu_blocking_snapshot_during_client_negotiation() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: send an MTU_REQ
            mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
            // take snapshot of pending future
            let pending_mtu = try_await(mtu.snapshot()).await.unwrap_err();
            // resolve MTU_REQ
            mtu.handle_event(MtuEvent::IncomingResponse(NEW_MTU)).unwrap();

            // assert: that the snapshot resolved with the NEW_MTU
            assert_eq!(pending_mtu.await.unwrap(), NEW_MTU);
        });
    }

    #[test]
    fn test_receive_mtu_request() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: receive an MTU_REQ
            mtu.handle_event(MtuEvent::IncomingRequest(NEW_MTU)).unwrap();
            // take snapshot
            let snapshot = mtu.snapshot().await;

            // assert: that the snapshot resolved with the NEW_MTU
            assert_eq!(snapshot.unwrap(), NEW_MTU);
        });
    }

    #[test]
    fn test_client_then_server_negotiation() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: send an MTU_REQ
            mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
            // receive an MTU_RESP
            mtu.handle_event(MtuEvent::IncomingResponse(NEW_MTU)).unwrap();
            // receive an MTU_REQ
            mtu.handle_event(MtuEvent::IncomingRequest(ANOTHER_NEW_MTU)).unwrap();
            // take snapshot
            let snapshot = mtu.snapshot().await;

            // assert: that the snapshot resolved with ANOTHER_NEW_MTU
            assert_eq!(snapshot.unwrap(), ANOTHER_NEW_MTU);
        });
    }

    #[test]
    fn test_server_negotiation_then_pending_client_default_value() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: receive an MTU_REQ
            mtu.handle_event(MtuEvent::IncomingRequest(NEW_MTU)).unwrap();
            // send a MTU_REQ
            mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
            // take snapshot for requests
            let snapshot = mtu.snapshot_or_default();

            // assert: that the snapshot resolved to NEW_MTU
            assert_eq!(snapshot, NEW_MTU);
        });
    }

    #[test]
    fn test_server_negotiation_then_pending_client_finalized_value() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: receive an MTU_REQ
            mtu.handle_event(MtuEvent::IncomingRequest(NEW_MTU)).unwrap();
            // send a MTU_REQ
            mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
            // take snapshot of pending future
            let snapshot = try_await(mtu.snapshot()).await.unwrap_err();
            // receive MTU_RESP
            mtu.handle_event(MtuEvent::IncomingResponse(ANOTHER_NEW_MTU)).unwrap();

            // assert: that the snapshot resolved to ANOTHER_NEW_MTU
            assert_eq!(snapshot.await.unwrap(), ANOTHER_NEW_MTU);
        });
    }

    #[test]
    fn test_mtu_dropped_while_pending() {
        block_on_locally(async move {
            // arrange
            let mtu = AttMtu::new();

            // act: send a MTU_REQ
            mtu.handle_event(MtuEvent::OutgoingRequest).unwrap();
            // take snapshot and store pending future
            let pending_mtu = try_await(mtu.snapshot()).await.unwrap_err();
            // drop the mtu (when the bearer closes)
            drop(mtu);

            // assert: that the snapshot resolves to None since the bearer is gone
            assert!(pending_mtu.await.is_none());
        });
    }
}
