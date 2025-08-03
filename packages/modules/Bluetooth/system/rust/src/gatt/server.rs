//! This module is a simple GATT server that shares the ATT channel with the
//! existing C++ GATT client.

mod att_database;
pub mod att_server_bearer;
pub mod gatt_database;
mod indication_handler;
mod request_handler;
pub mod services;
mod transactions;

mod command_handler;
pub mod isolation_manager;
#[cfg(test)]
mod test;

use std::{
    collections::HashMap,
    rc::Rc,
    sync::{Arc, Mutex, MutexGuard},
};

use crate::{
    core::shared_box::{SharedBox, WeakBox, WeakBoxRef},
    gatt::server::gatt_database::GattDatabase,
};

use self::{
    super::ids::ServerId,
    att_server_bearer::AttServerBearer,
    gatt_database::{AttDatabaseImpl, GattServiceWithHandle},
    isolation_manager::IsolationManager,
    services::register_builtin_services,
};

use super::{
    callbacks::RawGattDatastore,
    channel::AttTransport,
    ids::{AdvertiserId, AttHandle, TransportIndex},
};
use anyhow::{anyhow, bail, Result};
use bt_common::init_flags::always_use_private_gatt_for_debugging_is_enabled;
use log::info;

pub use indication_handler::IndicationError;

#[allow(missing_docs)]
pub struct GattModule {
    connections: HashMap<TransportIndex, GattConnection>,
    databases: HashMap<ServerId, SharedBox<GattDatabase>>,
    transport: Rc<dyn AttTransport>,
    // NOTE: this is logically owned by the GattModule. We share it behind a Mutex just so we
    // can use it as part of the Arbiter. Once the Arbiter is removed, this should be owned
    // fully by the GattModule.
    isolation_manager: Arc<Mutex<IsolationManager>>,
}

struct GattConnection {
    bearer: SharedBox<AttServerBearer<AttDatabaseImpl>>,
    database: WeakBox<GattDatabase>,
}

impl GattModule {
    /// Constructor.
    pub fn new(
        transport: Rc<dyn AttTransport>,
        isolation_manager: Arc<Mutex<IsolationManager>>,
    ) -> Self {
        Self {
            connections: HashMap::new(),
            databases: HashMap::new(),
            transport,
            isolation_manager,
        }
    }

    /// Handle LE link connect
    pub fn on_le_connect(
        &mut self,
        tcb_idx: TransportIndex,
        advertiser_id: Option<AdvertiserId>,
    ) -> Result<()> {
        info!("connected on tcb_idx {tcb_idx:?}");
        self.isolation_manager.lock().unwrap().on_le_connect(tcb_idx, advertiser_id);

        let Some(server_id) = self.isolation_manager.lock().unwrap().get_server_id(tcb_idx) else {
            bail!("non-isolated servers are not yet supported (b/274945531)")
        };
        let database = self.databases.get(&server_id);
        let Some(database) = database else {
            bail!("got connection to {server_id:?} but this server does not exist!");
        };

        let transport = self.transport.clone();
        let bearer = SharedBox::new(AttServerBearer::new(
            database.get_att_database(tcb_idx),
            move |packet| transport.send_packet(tcb_idx, packet),
        ));
        database.on_bearer_ready(tcb_idx, bearer.as_ref());
        self.connections.insert(tcb_idx, GattConnection { bearer, database: database.downgrade() });
        Ok(())
    }

    /// Handle an LE link disconnect
    pub fn on_le_disconnect(&mut self, tcb_idx: TransportIndex) -> Result<()> {
        info!("disconnected conn_id {tcb_idx:?}");
        self.isolation_manager.lock().unwrap().on_le_disconnect(tcb_idx);
        let connection = self.connections.remove(&tcb_idx);
        let Some(connection) = connection else {
            bail!("got disconnection from {tcb_idx:?} but bearer does not exist");
        };
        drop(connection.bearer);
        connection.database.with(|db| db.map(|db| db.on_bearer_dropped(tcb_idx)));
        Ok(())
    }

    /// Register a new GATT service on a given server
    pub fn register_gatt_service(
        &mut self,
        server_id: ServerId,
        service: GattServiceWithHandle,
        datastore: impl RawGattDatastore + 'static,
    ) -> Result<()> {
        self.databases
            .get(&server_id)
            .ok_or_else(|| anyhow!("server {server_id:?} not opened"))?
            .add_service_with_handles(service, Rc::new(datastore))
    }

    /// Unregister an existing GATT service on a given server
    pub fn unregister_gatt_service(
        &mut self,
        server_id: ServerId,
        service_handle: AttHandle,
    ) -> Result<()> {
        self.databases
            .get(&server_id)
            .ok_or_else(|| anyhow!("server {server_id:?} not opened"))?
            .remove_service_at_handle(service_handle)
    }

    /// Open a GATT server
    pub fn open_gatt_server(&mut self, server_id: ServerId) -> Result<()> {
        let mut db = GattDatabase::new();
        register_builtin_services(&mut db)?;
        let old = self.databases.insert(server_id, db.into());
        if old.is_some() {
            bail!("GATT server {server_id:?} already exists but was re-opened, clobbering old value...")
        }
        Ok(())
    }

    /// Close a GATT server
    pub fn close_gatt_server(&mut self, server_id: ServerId) -> Result<()> {
        let old = self.databases.remove(&server_id);
        if old.is_none() {
            bail!("GATT server {server_id:?} did not exist")
        };

        if !always_use_private_gatt_for_debugging_is_enabled() {
            self.isolation_manager.lock().unwrap().clear_server(server_id);
        }

        Ok(())
    }

    /// Get an ATT bearer for a particular connection
    pub fn get_bearer(
        &self,
        tcb_idx: TransportIndex,
    ) -> Option<WeakBoxRef<AttServerBearer<AttDatabaseImpl>>> {
        self.connections.get(&tcb_idx).map(|x| x.bearer.as_ref())
    }

    /// Get the IsolationManager to manage associations between servers + advertisers
    pub fn get_isolation_manager(&mut self) -> MutexGuard<'_, IsolationManager> {
        self.isolation_manager.lock().unwrap()
    }
}
