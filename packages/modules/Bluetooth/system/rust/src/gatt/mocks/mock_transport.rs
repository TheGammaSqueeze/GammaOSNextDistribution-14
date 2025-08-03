//! Mocked implementation of AttTransport for use in test

use crate::{
    gatt::{channel::AttTransport, ids::TransportIndex},
    packets::{AttBuilder, Serializable, SerializeError},
};
use tokio::sync::mpsc::{self, unbounded_channel, UnboundedReceiver};

/// Routes calls to AttTransport into a channel containing AttBuilders
pub struct MockAttTransport(mpsc::UnboundedSender<(TransportIndex, AttBuilder)>);

impl MockAttTransport {
    /// Constructor. Returns Self and the RX side of a channel containing
    /// AttBuilders sent on TransportIndices
    pub fn new() -> (Self, UnboundedReceiver<(TransportIndex, AttBuilder)>) {
        let (tx, rx) = unbounded_channel();
        (Self(tx), rx)
    }
}

impl AttTransport for MockAttTransport {
    fn send_packet(
        &self,
        tcb_idx: TransportIndex,
        packet: AttBuilder,
    ) -> Result<(), SerializeError> {
        packet.to_vec()?; // trigger SerializeError if needed
        self.0.send((tcb_idx, packet)).unwrap();
        Ok(())
    }
}
