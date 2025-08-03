/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use alloc::{rc::Rc, vec::Vec};
use authgraph_boringssl as boring;
use authgraph_core::{
    keyexchange::{AuthGraphParticipant, MAX_OPENED_SESSIONS},
    ta::{AuthGraphTa, Role},
};
use authgraph_wire::fragmentation::{Fragmenter, Reassembler, PLACEHOLDER_MORE_TO_COME};
use core::cell::RefCell;
use log::{debug, error, info};
use secretkeeper_core::ta::SecretkeeperTa;
use tipc::{
    service_dispatcher, ConnectResult, Deserialize, Handle, Manager, MessageResult, PortCfg,
    Serialize, Serializer, TipcError, Uuid,
};
use trusty_std::alloc::TryAllocFrom;

mod store;
#[cfg(test)]
mod tests;

/// Port for Secretkeeper communication with userspace.
const SK_PORT_NAME: &str = "com.android.trusty.secretkeeper";

/// Port for AuthGraph communication with userspace.
const AG_PORT_NAME: &str = "com.android.trusty.secretkeeper.authgraph";

/// Port for bootloader to communicate with Secretkeeper.
const BL_PORT_NAME: &str = "com.android.trusty.secretkeeper.bootloader";

/// Port count for this TA (as above).
const PORT_COUNT: usize = 3;

/// Maximum connection count for this TA:
/// - AuthGraph
/// - Secretkeeper
/// - bootloader
const MAX_CONNECTION_COUNT: usize = 3;

/// Maximum message size.
const MAX_MSG_SIZE: usize = 4000;

fn log_formatter(record: &log::Record) -> String {
    // line number should be present, so keeping it simple by just returning a 0.
    let line = record.line().unwrap_or(0);
    let file = record.file().unwrap_or("unknown file");
    format!("{}: {}:{} {}\n", record.level(), file, line, record.args())
}

/// Newtype wrapper for opaque messages.
struct SkMessage(Vec<u8>);

impl Deserialize for SkMessage {
    type Error = TipcError;
    const MAX_SERIALIZED_SIZE: usize = MAX_MSG_SIZE;

    fn deserialize(bytes: &[u8], _handles: &mut [Option<Handle>]) -> tipc::Result<Self> {
        Ok(SkMessage(Vec::try_alloc_from(bytes)?))
    }
}

impl<'s> Serialize<'s> for SkMessage {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        serializer.serialize_bytes(&self.0.as_slice())
    }
}

/// Break a response into fragments and send back to HAL service.
fn fragmented_send(handle: &Handle, full_rsp: Vec<u8>) -> tipc::Result<()> {
    for rsp_frag in Fragmenter::new(&full_rsp, MAX_MSG_SIZE) {
        handle.send(&SkMessage(rsp_frag))?;
    }
    Ok(())
}

/// Process an incoming request that may arrive in fragments.
fn fragmented_process<T>(
    handle: &Handle,
    msg: &[u8],
    pending: &mut Reassembler,
    process: T,
) -> tipc::Result<MessageResult>
where
    T: Fn(&[u8]) -> Vec<u8>,
{
    // Accumulate request fragments until able to feed complete request to `process`.
    if let Some(full_req) = pending.accumulate(msg) {
        let full_rsp = process(&full_req);
        fragmented_send(handle, full_rsp)?;
    } else {
        handle.send(&SkMessage(PLACEHOLDER_MORE_TO_COME.to_vec()))?;
    }
    Ok(MessageResult::MaintainConnection)
}

/// Implementation of the TIPC service for AuthGraph.
struct AuthGraphService {
    ta: Rc<RefCell<AuthGraphTa>>,
    pending_req: RefCell<Reassembler>,
}

impl tipc::Service for AuthGraphService {
    type Connection = ();
    type Message = SkMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Accepted AuthGraph connection from uuid {peer:?}");
        Ok(ConnectResult::Accept(()))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received an AuthGraph message");

        fragmented_process(handle, &msg.0, &mut self.pending_req.borrow_mut(), |req| {
            self.ta.borrow_mut().process(req)
        })
    }
}

/// Implementation of the TIPC service for Secretkeeper.
struct SecretkeeperService {
    ta: Rc<RefCell<SecretkeeperTa>>,
    pending_req: RefCell<Reassembler>,
}

impl tipc::Service for SecretkeeperService {
    type Connection = ();
    type Message = SkMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Accepted Secretkeeper connection from uuid {peer:?}");
        Ok(ConnectResult::Accept(()))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received a SecretKeeper message");

        fragmented_process(handle, &msg.0, &mut self.pending_req.borrow_mut(), |req| {
            self.ta.borrow_mut().process(req)
        })
    }
}

/// Implementation of the TIPC service for bootloader comms.
struct BootloaderService {
    ta: Rc<RefCell<SecretkeeperTa>>,
}

impl tipc::Service for BootloaderService {
    type Connection = ();
    type Message = SkMessage;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        info!("Accepted bootloader connection from uuid {peer:?}");
        Ok(ConnectResult::Accept(()))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        debug!("Received a bootloader message");

        let rsp = self.ta.borrow().process_bootloader(&msg.0);
        handle.send(&SkMessage(rsp))?;
        Ok(MessageResult::MaintainConnection)
    }
}

service_dispatcher! {
    enum SkServiceDispatcher {
        AuthGraphService,
        SecretkeeperService,
        BootloaderService,
    }
}

#[derive(Debug)]
struct SecureConnections(bool);

fn add_service_port<const PORT_COUNT: usize, T>(
    dispatcher: &mut SkServiceDispatcher<PORT_COUNT>,
    service: T,
    port_name: &str,
    secure: SecureConnections,
) -> Result<(), TipcError>
where
    ServiceKind: From<Rc<T>>,
{
    let cfg = PortCfg::new(port_name)
        .map_err(|e| {
            error!("Could not create port config for '{port_name}': {e:?}");
            TipcError::UnknownError
        })?
        .msg_max_size(MAX_MSG_SIZE as u32)
        .allow_ns_connect();
    // All ports support non-secure connections (from either Android userspace or the bootloader,
    // both of which are outside Trusty), but may also allow secure connections.
    let cfg = if secure.0 { cfg.allow_ta_connect() } else { cfg };

    dispatcher.add_service(Rc::new(service), cfg).map_err(|e| {
        error!("Could not add service for '{port_name}' to dispatcher: {e:?}");
        e
    })
}

/// Internal main function.
pub fn inner_main() -> Result<(), TipcError> {
    let config = trusty_log::TrustyLoggerConfig::default()
        .with_min_level(log::Level::Info)
        .format(&log_formatter);
    trusty_log::init_with_config(config);
    info!("Secretkeeper TA startup, on: '{AG_PORT_NAME}', '{SK_PORT_NAME}', '{BL_PORT_NAME}'");

    let mut crypto_impls = boring::crypto_trait_impls();
    let storage_impl = Box::new(store::SecureStore::default());
    let sk_ta = Rc::new(RefCell::new(
        SecretkeeperTa::new(&mut crypto_impls, storage_impl, coset::iana::EllipticCurve::Ed25519)
            .expect("Failed to create local Secretkeeper TA"),
    ));
    let ag_ta = Rc::new(RefCell::new(AuthGraphTa::new(
        AuthGraphParticipant::new(crypto_impls, sk_ta.clone(), MAX_OPENED_SESSIONS)
            .expect("Failed to create local AuthGraph TA"),
        Role::Sink,
    )));

    let ag_service = AuthGraphService { ta: ag_ta, pending_req: Default::default() };
    let sk_service = SecretkeeperService { ta: sk_ta.clone(), pending_req: Default::default() };
    let bl_service = BootloaderService { ta: sk_ta };

    // Handle multiple TIPC services, one service per port.
    let mut dispatcher = SkServiceDispatcher::<PORT_COUNT>::new().map_err(|e| {
        error!("could not create multi-service dispatcher: {e:?}");
        e
    })?;

    // Add the TIPC services into the dispatcher.
    add_service_port(&mut dispatcher, ag_service, AG_PORT_NAME, SecureConnections(false))?;
    add_service_port(&mut dispatcher, sk_service, SK_PORT_NAME, SecureConnections(false))?;
    // Allow secure connections to the bootloader service for testing (as the Trusty
    // test app uses a secure connection).
    add_service_port(&mut dispatcher, bl_service, BL_PORT_NAME, SecureConnections(true))?;

    let buffer = [0u8; MAX_MSG_SIZE];
    let manager =
        Manager::<_, _, PORT_COUNT, MAX_CONNECTION_COUNT>::new_with_dispatcher(dispatcher, buffer)
            .map_err(|e| {
                error!("Could not create service manager: {e:?}");
                e
            })?;
    manager.run_event_loop()
}
