//! Mocked implementation of GattDatastore for use in test

use crate::{
    gatt::{
        callbacks::{GattWriteRequestType, RawGattDatastore, TransactionDecision},
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

/// Routes calls to RawGattDatastore into a channel of MockRawDatastoreEvents
pub struct MockRawDatastore(mpsc::UnboundedSender<MockRawDatastoreEvents>);

impl MockRawDatastore {
    /// Constructor. Returns self and the RX side of the associated channel.
    pub fn new() -> (Self, UnboundedReceiver<MockRawDatastoreEvents>) {
        let (tx, rx) = unbounded_channel();
        (Self(tx), rx)
    }
}

/// Events representing calls to GattDatastore
#[derive(Debug)]
pub enum MockRawDatastoreEvents {
    /// A characteristic was read on a given handle. The oneshot is used to
    /// return the value read.
    Read(
        TransportIndex,
        AttHandle,
        AttributeBackingType,
        u32,
        oneshot::Sender<Result<AttAttributeDataChild, AttErrorCode>>,
    ),
    /// A characteristic was written to on a given handle. The oneshot is used
    /// to return whether the write succeeded.
    Write(
        TransportIndex,
        AttHandle,
        AttributeBackingType,
        GattWriteRequestType,
        OwnedAttAttributeDataView,
        oneshot::Sender<Result<(), AttErrorCode>>,
    ),
    /// A characteristic was written to on a given handle, where the response was disregarded.
    WriteNoResponse(TransportIndex, AttHandle, AttributeBackingType, OwnedAttAttributeDataView),
    /// The prepared writes have been committed / aborted. The oneshot is used
    /// to return whether this operation succeeded.
    Execute(TransportIndex, TransactionDecision, oneshot::Sender<Result<(), AttErrorCode>>),
}

#[async_trait(?Send)]
impl RawGattDatastore for MockRawDatastore {
    async fn read(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        offset: u32,
        attr_type: AttributeBackingType,
    ) -> Result<AttAttributeDataChild, AttErrorCode> {
        let (tx, rx) = oneshot::channel();
        self.0.send(MockRawDatastoreEvents::Read(tcb_idx, handle, attr_type, offset, tx)).unwrap();
        let resp = rx.await.unwrap();
        info!("sending {resp:?} down from upper tester");
        resp
    }

    async fn write(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        write_type: GattWriteRequestType,
        data: AttAttributeDataView<'_>,
    ) -> Result<(), AttErrorCode> {
        let (tx, rx) = oneshot::channel();
        self.0
            .send(MockRawDatastoreEvents::Write(
                tcb_idx,
                handle,
                attr_type,
                write_type,
                data.to_owned_packet(),
                tx,
            ))
            .unwrap();
        rx.await.unwrap()
    }

    fn write_no_response(
        &self,
        tcb_idx: TransportIndex,
        handle: AttHandle,
        attr_type: AttributeBackingType,
        data: AttAttributeDataView<'_>,
    ) {
        self.0
            .send(MockRawDatastoreEvents::WriteNoResponse(
                tcb_idx,
                handle,
                attr_type,
                data.to_owned_packet(),
            ))
            .unwrap();
    }

    async fn execute(
        &self,
        tcb_idx: TransportIndex,
        decision: TransactionDecision,
    ) -> Result<(), AttErrorCode> {
        let (tx, rx) = oneshot::channel();
        self.0.send(MockRawDatastoreEvents::Execute(tcb_idx, decision, tx)).unwrap();
        rx.await.unwrap()
    }
}
