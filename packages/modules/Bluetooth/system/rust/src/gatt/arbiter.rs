//! This module handles "arbitration" of ATT packets, to determine whether they
//! should be handled by the primary stack or by the Rust stack

use std::sync::{Arc, Mutex};

use log::{error, trace, warn};
use std::sync::RwLock;

use crate::{
    do_in_rust_thread,
    packets::{AttOpcode, OwnedAttView, OwnedPacket},
};

use super::{
    ffi::{InterceptAction, StoreCallbacksFromRust},
    ids::{AdvertiserId, TransportIndex},
    mtu::MtuEvent,
    opcode_types::{classify_opcode, OperationType},
    server::isolation_manager::IsolationManager,
};

static ARBITER: RwLock<Option<Arc<Mutex<IsolationManager>>>> = RwLock::new(None);

/// Initialize the Arbiter
pub fn initialize_arbiter() -> Arc<Mutex<IsolationManager>> {
    let arbiter = Arc::new(Mutex::new(IsolationManager::new()));
    let mut lock = ARBITER.write().unwrap();
    assert!(lock.is_none(), "Rust stack should only start up once");
    *lock = Some(arbiter.clone());

    StoreCallbacksFromRust(
        on_le_connect,
        on_le_disconnect,
        intercept_packet,
        |tcb_idx| on_mtu_event(TransportIndex(tcb_idx), MtuEvent::OutgoingRequest),
        |tcb_idx, mtu| on_mtu_event(TransportIndex(tcb_idx), MtuEvent::IncomingResponse(mtu)),
        |tcb_idx, mtu| on_mtu_event(TransportIndex(tcb_idx), MtuEvent::IncomingRequest(mtu)),
    );

    arbiter
}

/// Clean the Arbiter
pub fn clean_arbiter() {
    let mut lock = ARBITER.write().unwrap();
    *lock = None
}

/// Acquire the mutex holding the Arbiter and provide a mutable reference to the
/// supplied closure
pub fn with_arbiter<T>(f: impl FnOnce(&mut IsolationManager) -> T) -> T {
    f(ARBITER.read().unwrap().as_ref().expect("Rust stack is not started").lock().as_mut().unwrap())
}

/// Check if the Arbiter is initialized.
pub fn has_arbiter() -> bool {
    ARBITER.read().unwrap().is_some()
}

/// Test to see if a buffer contains a valid ATT packet with an opcode we
/// are interested in intercepting (those intended for servers that are isolated)
fn try_parse_att_server_packet(
    isolation_manager: &IsolationManager,
    tcb_idx: TransportIndex,
    packet: Box<[u8]>,
) -> Option<OwnedAttView> {
    isolation_manager.get_server_id(tcb_idx)?;

    let att = OwnedAttView::try_parse(packet).ok()?;

    if att.view().get_opcode() == AttOpcode::EXCHANGE_MTU_REQUEST {
        // special case: this server opcode is handled by legacy stack, and we snoop
        // on its handling, since the MTU is shared between the client + server
        return None;
    }

    match classify_opcode(att.view().get_opcode()) {
        OperationType::Command | OperationType::Request | OperationType::Confirmation => Some(att),
        _ => None,
    }
}

fn on_le_connect(tcb_idx: u8, advertiser: u8) {
    let tcb_idx = TransportIndex(tcb_idx);
    let advertiser = AdvertiserId(advertiser);
    let is_isolated = with_arbiter(|arbiter| arbiter.is_advertiser_isolated(advertiser));
    if is_isolated {
        do_in_rust_thread(move |modules| {
            if let Err(err) = modules.gatt_module.on_le_connect(tcb_idx, Some(advertiser)) {
                error!("{err:?}")
            }
        })
    }
}

fn on_le_disconnect(tcb_idx: u8) {
    // Events may be received after a FactoryReset
    // is initiated for Bluetooth and the rust arbiter is taken
    // down.
    if !has_arbiter() {
        warn!("arbiter is not yet initialized");
        return;
    }

    let tcb_idx = TransportIndex(tcb_idx);
    let was_isolated = with_arbiter(|arbiter| arbiter.is_connection_isolated(tcb_idx));
    if was_isolated {
        do_in_rust_thread(move |modules| {
            if let Err(err) = modules.gatt_module.on_le_disconnect(tcb_idx) {
                error!("{err:?}")
            }
        })
    }
}

fn intercept_packet(tcb_idx: u8, packet: Vec<u8>) -> InterceptAction {
    // Events may be received after a FactoryReset
    // is initiated for Bluetooth and the rust arbiter is taken
    // down.
    if !has_arbiter() {
        warn!("arbiter is not yet initialized");
        return InterceptAction::Drop;
    }

    let tcb_idx = TransportIndex(tcb_idx);
    if let Some(att) = with_arbiter(|arbiter| {
        try_parse_att_server_packet(arbiter, tcb_idx, packet.into_boxed_slice())
    }) {
        do_in_rust_thread(move |modules| {
            trace!("pushing packet to GATT");
            if let Some(bearer) = modules.gatt_module.get_bearer(tcb_idx) {
                bearer.handle_packet(att.view())
            } else {
                error!("Bearer for {tcb_idx:?} not found");
            }
        });
        InterceptAction::Drop
    } else {
        InterceptAction::Forward
    }
}

fn on_mtu_event(tcb_idx: TransportIndex, event: MtuEvent) {
    if with_arbiter(|arbiter| arbiter.is_connection_isolated(tcb_idx)) {
        do_in_rust_thread(move |modules| {
            let Some(bearer) = modules.gatt_module.get_bearer(tcb_idx) else {
                error!("Bearer for {tcb_idx:?} not found");
                return;
            };
            if let Err(err) = bearer.handle_mtu_event(event) {
                error!("{err:?}")
            }
        });
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::{
        gatt::ids::{AttHandle, ServerId},
        packets::{
            AttBuilder, AttExchangeMtuRequestBuilder, AttOpcode, AttReadRequestBuilder,
            Serializable,
        },
    };

    const TCB_IDX: TransportIndex = TransportIndex(1);
    const ADVERTISER_ID: AdvertiserId = AdvertiserId(3);
    const SERVER_ID: ServerId = ServerId(4);

    fn create_manager_with_isolated_connection(
        tcb_idx: TransportIndex,
        server_id: ServerId,
    ) -> IsolationManager {
        let mut isolation_manager = IsolationManager::new();
        isolation_manager.associate_server_with_advertiser(server_id, ADVERTISER_ID);
        isolation_manager.on_le_connect(tcb_idx, Some(ADVERTISER_ID));
        isolation_manager
    }

    #[test]
    fn test_packet_capture_when_isolated() {
        let isolation_manager = create_manager_with_isolated_connection(TCB_IDX, SERVER_ID);
        let packet = AttBuilder {
            opcode: AttOpcode::READ_REQUEST,
            _child_: AttReadRequestBuilder { attribute_handle: AttHandle(1).into() }.into(),
        };

        let out = try_parse_att_server_packet(
            &isolation_manager,
            TCB_IDX,
            packet.to_vec().unwrap().into(),
        );

        assert!(out.is_some());
    }

    #[test]
    fn test_packet_bypass_when_isolated() {
        let isolation_manager = create_manager_with_isolated_connection(TCB_IDX, SERVER_ID);
        let packet = AttBuilder {
            opcode: AttOpcode::ERROR_RESPONSE,
            _child_: AttReadRequestBuilder { attribute_handle: AttHandle(1).into() }.into(),
        };

        let out = try_parse_att_server_packet(
            &isolation_manager,
            TCB_IDX,
            packet.to_vec().unwrap().into(),
        );

        assert!(out.is_none());
    }

    #[test]
    fn test_mtu_bypass() {
        let isolation_manager = create_manager_with_isolated_connection(TCB_IDX, SERVER_ID);
        let packet = AttBuilder {
            opcode: AttOpcode::EXCHANGE_MTU_REQUEST,
            _child_: AttExchangeMtuRequestBuilder { mtu: 64 }.into(),
        };

        let out = try_parse_att_server_packet(
            &isolation_manager,
            TCB_IDX,
            packet.to_vec().unwrap().into(),
        );

        assert!(out.is_none());
    }

    #[test]
    fn test_packet_bypass_when_not_isolated() {
        let isolation_manager = IsolationManager::new();
        let packet = AttBuilder {
            opcode: AttOpcode::READ_REQUEST,
            _child_: AttReadRequestBuilder { attribute_handle: AttHandle(1).into() }.into(),
        };

        let out = try_parse_att_server_packet(
            &isolation_manager,
            TCB_IDX,
            packet.to_vec().unwrap().into(),
        );

        assert!(out.is_none());
    }
}
