//! Mocked implementation of GattDatabaseCallbacks for use in test

use std::ops::RangeInclusive;

use crate::{
    core::shared_box::{WeakBox, WeakBoxRef},
    gatt::{
        ids::{AttHandle, TransportIndex},
        server::{
            att_server_bearer::AttServerBearer,
            gatt_database::{AttDatabaseImpl, GattDatabaseCallbacks},
        },
    },
};
use tokio::sync::mpsc::{self, unbounded_channel, UnboundedReceiver};

/// Routes calls to GattDatabaseCallbacks into a channel of MockCallbackEvents
pub struct MockCallbacks(mpsc::UnboundedSender<MockCallbackEvents>);

impl MockCallbacks {
    /// Constructor. Returns self and the RX side of the associated channel.
    pub fn new() -> (Self, UnboundedReceiver<MockCallbackEvents>) {
        let (tx, rx) = unbounded_channel();
        (Self(tx), rx)
    }
}

/// Events representing calls to GattCallbacks
pub enum MockCallbackEvents {
    /// GattDatabaseCallbacks#on_le_connect invoked
    OnLeConnect(TransportIndex, WeakBox<AttServerBearer<AttDatabaseImpl>>),
    /// GattDatabaseCallbacks#on_le_disconnect invoked
    OnLeDisconnect(TransportIndex),
    /// GattDatabaseCallbacks#on_service_change invoked
    OnServiceChange(RangeInclusive<AttHandle>),
}

impl GattDatabaseCallbacks for MockCallbacks {
    fn on_le_connect(
        &self,
        tcb_idx: TransportIndex,
        bearer: WeakBoxRef<AttServerBearer<AttDatabaseImpl>>,
    ) {
        self.0.send(MockCallbackEvents::OnLeConnect(tcb_idx, bearer.downgrade())).ok().unwrap();
    }

    fn on_le_disconnect(&self, tcb_idx: TransportIndex) {
        self.0.send(MockCallbackEvents::OnLeDisconnect(tcb_idx)).ok().unwrap();
    }

    fn on_service_change(&self, range: RangeInclusive<AttHandle>) {
        self.0.send(MockCallbackEvents::OnServiceChange(range)).ok().unwrap();
    }
}
