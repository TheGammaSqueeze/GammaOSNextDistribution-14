//! Mocked implementation of GattDatastore for use in test

use crate::{
    gatt::{
        callbacks::GattDatastore,
        ffi::AttributeBackingType,
        ids::{AttHandle, TransportIndex},
    },
    packets::{
        AttAttributeDataChild, AttAttributeDataView, AttErrorCode, OwnedAttAttributeDataView,
        Packet,
    },
};
use async_trait::async_trait;
use log::info;
use tokio::sync::{
    mpsc::{self, unbounded_channel, UnboundedReceiver},
    oneshot,
};

/// Routes calls to GattDatastore into a channel of MockDatastoreEvents
pub struct MockDatastore(mpsc::UnboundedSender<MockDatastoreEvents>);

impl MockDatastore {
    /// Constructor. Returns self and the RX side of the associated channel.
    pub fn new() -> (Self, UnboundedReceiver<MockDatastoreEvents>) {
        let (tx, rx) = unbounded_channel();
        (Self(tx), rx)
    }
}

/// Events representing calls to GattDatastore
#[derive(Debug)]
pub enum MockDatastoreEvents {
    /// A characteristic was read on a given handle. The oneshot is used to
    /// return the value read.
    Read(
        TransportIndex,
        AttHandle,
        AttributeBackingType,
        oneshot::Sender<Result<AttAttributeDataChild, AttErrorCode>>,
    ),
    /// A characteristic was written to on a given handle. The oneshot is used
    /// to return whether the write succeeded.
    Write(
        TransportIndex,
        AttHandle,
        AttributeBackingType,
        OwnedAttAttributeDataView,
        oneshot::Sender<Result<(), AttErrorCode>>,
    ),
}

#[async_trait(?Send)]
impl GattDatastore for MockDatastore {
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        let (tx, rx) = oneshot::channel();
        self.0.send(MockDatastoreEvents::Read(tcb_idx, handle, attr_type, tx)).unwrap();
        let resp = rx.await.unwrap();
        info!("sending {resp:?} down from upper tester");
        resp
    }

    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        let (tx, rx) = oneshot::channel();
        self.0
            .send(MockDatastoreEvents::Write(
                tcb_idx,
                handle,
                attr_type,
                data.to_owned_packet(),
                tx,
            ))
            .unwrap();
        rx.await.unwrap()
    }
}
