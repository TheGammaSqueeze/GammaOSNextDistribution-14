//! Anything related to the GATT API (IBluetoothGatt).

use btif_macros::{btif_callback, btif_callbacks_dispatcher};

use bt_topshim::bindings::root::bluetooth::Uuid;
use bt_topshim::btif::{BluetoothInterface, BtStatus, BtTransport, RawAddress, Uuid128Bit};
use bt_topshim::profiles::gatt::{
    ffi::RustAdvertisingTrackInfo, AdvertisingStatus, BtGattDbElement, BtGattNotifyParams,
    BtGattReadParams, BtGattResponse, BtGattValue, Gatt, GattAdvCallbacksDispatcher,
    GattAdvInbandCallbacksDispatcher, GattClientCallbacks, GattClientCallbacksDispatcher,
    GattScannerCallbacks, GattScannerCallbacksDispatcher, GattScannerInbandCallbacks,
    GattScannerInbandCallbacksDispatcher, GattServerCallbacks, GattServerCallbacksDispatcher,
    GattStatus, LePhy, MsftAdvMonitor, MsftAdvMonitorPattern,
};
use bt_topshim::sysprop;
use bt_topshim::topstack;
use bt_utils::adv_parser;
use bt_utils::array_utils;

use crate::async_helper::{AsyncHelper, CallbackSender};
use crate::bluetooth::{Bluetooth, BluetoothDevice};
use crate::bluetooth_adv::{
    AdvertiseData, AdvertiseManager, AdvertiserActions, AdvertisingSetParameters,
    BtifGattAdvCallbacks, IAdvertisingSetCallback, PeriodicAdvertisingParameters,
};
use crate::callbacks::Callbacks;
use crate::uuid::UuidHelper;
use crate::{APIMessage, BluetoothAPI, Message, RPCProxy, SuspendMode};
use log::warn;
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::cast::{FromPrimitive, ToPrimitive};
use rand::rngs::SmallRng;
use rand::{RngCore, SeedableRng};
use std::collections::{HashMap, HashSet};
use std::convert::{TryFrom, TryInto};
use std::sync::{Arc, Mutex, MutexGuard};
use tokio::sync::mpsc::Sender;
use tokio::time;

struct Client {
    id: Option<i32>,
    cbid: u32,
    uuid: Uuid128Bit,
    is_congested: bool,

    // Queued on_characteristic_write callback.
    congestion_queue: Vec<(String, GattStatus, i32)>,
}

struct Connection {
    conn_id: i32,
    address: String,

    // Connections are made to either a client or server
    client_id: i32,
    server_id: i32,
}

struct ContextMap {
    // TODO(b/196635530): Consider using `multimap` for a more efficient implementation of get by
    // multiple keys.
    callbacks: Callbacks<dyn IBluetoothGattCallback + Send>,
    clients: Vec<Client>,
    connections: Vec<Connection>,
}

type GattClientCallback = Box<dyn IBluetoothGattCallback + Send>;

impl ContextMap {
    fn new(tx: Sender<Message>) -> ContextMap {
        ContextMap {
            callbacks: Callbacks::new(tx, Message::GattClientCallbackDisconnected),
            clients: vec![],
            connections: vec![],
        }
    }

    fn get_by_uuid(&self, uuid: &Uuid128Bit) -> Option<&Client> {
        self.clients.iter().find(|client| client.uuid == *uuid)
    }

    fn get_by_client_id(&self, client_id: i32) -> Option<&Client> {
        self.clients.iter().find(|client| client.id.is_some() && client.id.unwrap() == client_id)
    }

    fn get_by_client_id_mut(&mut self, client_id: i32) -> Option<&mut Client> {
        self.clients
            .iter_mut()
            .find(|client| client.id.is_some() && client.id.unwrap() == client_id)
    }

    fn get_by_callback_id(&self, callback_id: u32) -> Option<&Client> {
        self.clients.iter().find(|client| client.cbid == callback_id)
    }

    fn get_address_by_conn_id(&self, conn_id: i32) -> Option<String> {
        match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => None,
            Some(conn) => Some(conn.address.clone()),
        }
    }

    fn get_client_by_conn_id(&self, conn_id: i32) -> Option<&Client> {
        match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => None,
            Some(conn) => self.get_by_client_id(conn.client_id),
        }
    }

    fn get_client_by_conn_id_mut(&mut self, conn_id: i32) -> Option<&mut Client> {
        let client_id = match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => return None,
            Some(conn) => conn.client_id,
        };

        self.get_by_client_id_mut(client_id)
    }

    fn add(&mut self, uuid: &Uuid128Bit, callback: GattClientCallback) {
        if self.get_by_uuid(uuid).is_some() {
            return;
        }

        let cbid = self.callbacks.add_callback(callback);

        self.clients.push(Client {
            id: None,
            cbid,
            uuid: uuid.clone(),
            is_congested: false,
            congestion_queue: vec![],
        });
    }

    fn remove(&mut self, id: i32) {
        // Remove any callbacks
        if let Some(c) = self.get_by_client_id(id) {
            let cbid = c.cbid;
            self.remove_callback(cbid);
        }

        self.clients.retain(|client| !(client.id.is_some() && client.id.unwrap() == id));
    }

    fn remove_callback(&mut self, callback_id: u32) {
        self.callbacks.remove_callback(callback_id);
    }

    fn set_client_id(&mut self, uuid: &Uuid128Bit, id: i32) {
        let client = self.clients.iter_mut().find(|client| client.uuid == *uuid);
        if client.is_none() {
            return;
        }

        client.unwrap().id = Some(id);
    }

    fn add_connection(&mut self, client_id: i32, conn_id: i32, address: &String) {
        if self.get_conn_id_from_address(client_id, address).is_some() {
            return;
        }

        self.connections.push(Connection {
            conn_id,
            address: address.clone(),
            client_id,
            server_id: 0,
        });
    }

    fn remove_connection(&mut self, _client_id: i32, conn_id: i32) {
        self.connections.retain(|conn| conn.conn_id != conn_id);
    }

    fn get_conn_id_from_address(&self, client_id: i32, address: &String) -> Option<i32> {
        match self
            .connections
            .iter()
            .find(|conn| conn.client_id == client_id && conn.address == *address)
        {
            None => None,
            Some(conn) => Some(conn.conn_id),
        }
    }

    fn get_client_ids_from_address(&self, address: &String) -> Vec<i32> {
        self.connections
            .iter()
            .filter(|conn| conn.address == *address)
            .map(|conn| conn.client_id)
            .collect()
    }

    fn get_callback_from_callback_id(
        &mut self,
        callback_id: u32,
    ) -> Option<&mut GattClientCallback> {
        self.callbacks.get_by_id_mut(callback_id)
    }
}

struct Server {
    id: Option<i32>,
    cbid: u32,
    uuid: Uuid128Bit,
    services: Vec<BluetoothGattService>,
    is_congested: bool,

    // Queued on_notification_sent callback.
    congestion_queue: Vec<(String, GattStatus)>,
}

struct Request {
    id: i32,
    handle: i32,
}

struct ServerContextMap {
    // TODO(b/196635530): Consider using `multimap` for a more efficient implementation of get by
    // multiple keys.
    callbacks: Callbacks<dyn IBluetoothGattServerCallback + Send>,
    servers: Vec<Server>,
    connections: Vec<Connection>,
    requests: Vec<Request>,
}

type GattServerCallback = Box<dyn IBluetoothGattServerCallback + Send>;

impl ServerContextMap {
    fn new(tx: Sender<Message>) -> ServerContextMap {
        ServerContextMap {
            callbacks: Callbacks::new(tx, Message::GattServerCallbackDisconnected),
            servers: vec![],
            connections: vec![],
            requests: vec![],
        }
    }

    fn get_by_uuid(&self, uuid: &Uuid128Bit) -> Option<&Server> {
        self.servers.iter().find(|server| server.uuid == *uuid)
    }

    fn get_by_server_id(&self, server_id: i32) -> Option<&Server> {
        self.servers.iter().find(|server| server.id.map_or(false, |id| id == server_id))
    }

    fn get_mut_by_server_id(&mut self, server_id: i32) -> Option<&mut Server> {
        self.servers.iter_mut().find(|server| server.id.map_or(false, |id| id == server_id))
    }

    fn get_by_callback_id(&self, callback_id: u32) -> Option<&Server> {
        self.servers.iter().find(|server| server.cbid == callback_id)
    }

    fn get_by_conn_id(&self, conn_id: i32) -> Option<&Server> {
        self.connections
            .iter()
            .find(|conn| conn.conn_id == conn_id)
            .and_then(|conn| self.get_by_server_id(conn.server_id))
    }

    fn get_mut_by_conn_id(&mut self, conn_id: i32) -> Option<&mut Server> {
        self.connections
            .iter()
            .find_map(|conn| (conn.conn_id == conn_id).then(|| conn.server_id.clone()))
            .and_then(move |server_id| self.get_mut_by_server_id(server_id))
    }

    fn add(&mut self, uuid: &Uuid128Bit, callback: GattServerCallback) {
        if self.get_by_uuid(uuid).is_some() {
            return;
        }

        let cbid = self.callbacks.add_callback(callback);

        self.servers.push(Server {
            id: None,
            cbid,
            uuid: uuid.clone(),
            services: vec![],
            is_congested: false,
            congestion_queue: vec![],
        });
    }

    fn remove(&mut self, id: i32) {
        // Remove any callbacks
        if let Some(cbid) = self.get_by_server_id(id).map(|server| server.cbid) {
            self.remove_callback(cbid);
        }

        self.servers.retain(|server| !(server.id.is_some() && server.id.unwrap() == id));
    }

    fn remove_callback(&mut self, callback_id: u32) {
        self.callbacks.remove_callback(callback_id);
    }

    fn set_server_id(&mut self, uuid: &Uuid128Bit, id: i32) {
        let server = self.servers.iter_mut().find(|server| server.uuid == *uuid);
        if let Some(s) = server {
            s.id = Some(id);
        }
    }

    fn get_callback_from_callback_id(
        &mut self,
        callback_id: u32,
    ) -> Option<&mut GattServerCallback> {
        self.callbacks.get_by_id_mut(callback_id)
    }

    fn add_connection(&mut self, server_id: i32, conn_id: i32, address: &String) {
        if self.get_conn_id_from_address(server_id, address).is_some() {
            return;
        }

        self.connections.push(Connection {
            conn_id,
            address: address.clone(),
            client_id: 0,
            server_id,
        });
    }

    fn remove_connection(&mut self, conn_id: i32) {
        self.connections.retain(|conn| conn.conn_id != conn_id);
    }

    fn get_conn_id_from_address(&self, server_id: i32, address: &String) -> Option<i32> {
        return self
            .connections
            .iter()
            .find(|conn| conn.server_id == server_id && conn.address == *address)
            .map(|conn| conn.conn_id);
    }

    fn get_server_ids_from_address(&self, address: &String) -> Vec<i32> {
        self.connections
            .iter()
            .filter(|conn| conn.address == *address)
            .map(|conn| conn.server_id)
            .collect()
    }

    fn get_address_from_conn_id(&self, conn_id: i32) -> Option<String> {
        self.connections
            .iter()
            .find_map(|conn| (conn.conn_id == conn_id).then(|| conn.address.clone()))
    }

    fn add_service(&mut self, server_id: i32, service: BluetoothGattService) {
        if let Some(s) = self.get_mut_by_server_id(server_id) {
            s.services.push(service)
        }
    }

    fn delete_service(&mut self, server_id: i32, handle: i32) {
        self.get_mut_by_server_id(server_id)
            .map(|s: &mut Server| s.services.retain(|service| service.instance_id != handle));
    }

    fn add_request(&mut self, request_id: i32, handle: i32) {
        self.requests.push(Request { id: request_id, handle });
    }

    fn _delete_request(&mut self, request_id: i32) {
        self.requests.retain(|request| request.id != request_id);
    }

    fn get_request_handle_from_id(&self, request_id: i32) -> Option<i32> {
        self.requests.iter().find_map(|request| (request.id == request_id).then(|| request.handle))
    }
}

/// Defines the GATT API.
// TODO(242083290): Split out interfaces.
pub trait IBluetoothGatt {
    // Scanning

    /// Returns whether LE Scan can be performed by hardware offload defined by
    /// [MSFT HCI Extension](https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/microsoft-defined-bluetooth-hci-commands-and-events).
    fn is_msft_supported(&self) -> bool;

    /// Registers an LE scanner callback.
    ///
    /// Returns the callback id.
    fn register_scanner_callback(&mut self, callback: Box<dyn IScannerCallback + Send>) -> u32;

    /// Unregisters an LE scanner callback identified by the given id.
    fn unregister_scanner_callback(&mut self, callback_id: u32) -> bool;

    /// Registers LE scanner.
    ///
    /// `callback_id`: The callback to receive updates about the scanner state.
    /// Returns the UUID of the registered scanner.
    fn register_scanner(&mut self, callback_id: u32) -> Uuid128Bit;

    /// Unregisters an LE scanner identified by the given scanner id.
    fn unregister_scanner(&mut self, scanner_id: u8) -> bool;

    /// Activate scan of the given scanner id.
    fn start_scan(
        &mut self,
        scanner_id: u8,
        settings: Option<ScanSettings>,
        filter: Option<ScanFilter>,
    ) -> BtStatus;

    /// Deactivate scan of the given scanner id.
    fn stop_scan(&mut self, scanner_id: u8) -> BtStatus;

    /// Returns the current suspend mode.
    fn get_scan_suspend_mode(&self) -> SuspendMode;

    // Advertising

    /// Registers callback for BLE advertising.
    fn register_advertiser_callback(
        &mut self,
        callback: Box<dyn IAdvertisingSetCallback + Send>,
    ) -> u32;

    /// Unregisters callback for BLE advertising.
    fn unregister_advertiser_callback(&mut self, callback_id: u32) -> bool;

    /// Creates a new BLE advertising set and start advertising.
    ///
    /// Returns the reg_id for the advertising set, which is used in the callback
    /// `on_advertising_set_started` to identify the advertising set started.
    ///
    /// * `parameters` - Advertising set parameters.
    /// * `advertise_data` - Advertisement data to be broadcasted.
    /// * `scan_response` - Scan response.
    /// * `periodic_parameters` - Periodic advertising parameters. If None, periodic advertising
    ///     will not be started.
    /// * `periodic_data` - Periodic advertising data.
    /// * `duration` - Advertising duration, in 10 ms unit. Valid range is from 1 (10 ms) to
    ///     65535 (655.35 sec). 0 means no advertising timeout.
    /// * `max_ext_adv_events` - Maximum number of extended advertising events the controller
    ///     shall attempt to send before terminating the extended advertising, even if the
    ///     duration has not expired. Valid range is from 1 to 255. 0 means event count limitation.
    /// * `callback_id` - Identifies callback registered in register_advertiser_callback.
    fn start_advertising_set(
        &mut self,
        parameters: AdvertisingSetParameters,
        advertise_data: AdvertiseData,
        scan_response: Option<AdvertiseData>,
        periodic_parameters: Option<PeriodicAdvertisingParameters>,
        periodic_data: Option<AdvertiseData>,
        duration: i32,
        max_ext_adv_events: i32,
        callback_id: u32,
    ) -> i32;

    /// Disposes a BLE advertising set.
    fn stop_advertising_set(&mut self, advertiser_id: i32);

    /// Queries address associated with the advertising set.
    fn get_own_address(&mut self, advertiser_id: i32);

    /// Enables or disables an advertising set.
    fn enable_advertising_set(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    );

    /// Updates advertisement data of the advertising set.
    fn set_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Set the advertisement data of the advertising set.
    fn set_raw_adv_data(&mut self, advertiser_id: i32, data: Vec<u8>);

    /// Updates scan response of the advertising set.
    fn set_scan_response_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Updates advertising parameters of the advertising set.
    ///
    /// It must be called when advertising is not active.
    fn set_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: AdvertisingSetParameters,
    );

    /// Updates periodic advertising parameters.
    fn set_periodic_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: PeriodicAdvertisingParameters,
    );

    /// Updates periodic advertisement data.
    ///
    /// It must be called after `set_periodic_advertising_parameters`, or after
    /// advertising was started with periodic advertising data set.
    fn set_periodic_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData);

    /// Enables or disables periodic advertising.
    fn set_periodic_advertising_enable(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        include_adi: bool,
    );

    // GATT Client

    /// Registers a GATT Client.
    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    );

    /// Unregisters a GATT Client.
    fn unregister_client(&mut self, client_id: i32);

    /// Initiates a GATT connection to a peer device.
    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
        opportunistic: bool,
        phy: LePhy,
    );

    /// Disconnects a GATT connection.
    fn client_disconnect(&self, client_id: i32, addr: String);

    /// Clears the attribute cache of a device.
    fn refresh_device(&self, client_id: i32, addr: String);

    /// Enumerates all GATT services on a connected device.
    fn discover_services(&self, client_id: i32, addr: String);

    /// Discovers all GATT services on a connected device. Only used by PTS.
    fn btif_gattc_discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String);

    /// Search a GATT service on a connected device based on a UUID.
    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String);

    /// Reads a characteristic on a remote device.
    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32);

    /// Reads a characteristic on a remote device.
    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    );

    /// Writes a remote characteristic.
    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus;

    /// Reads the descriptor for a given characteristic.
    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32);

    /// Writes a remote descriptor for a given characteristic.
    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    );

    /// Registers to receive notifications or indications for a given characteristic.
    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool);

    /// Begins reliable write.
    fn begin_reliable_write(&mut self, client_id: i32, addr: String);

    /// Ends reliable write.
    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool);

    /// Requests RSSI for a given remote device.
    fn read_remote_rssi(&self, client_id: i32, addr: String);

    /// Configures the MTU of a given connection.
    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32);

    /// Requests a connection parameter update.
    /// This causes |on_connection_updated| to be called if there is already an existing
    /// connection to |addr|; Otherwise the method won't generate any callbacks.
    fn connection_parameter_update(
        &self,
        client_id: i32,
        addr: String,
        min_interval: i32,
        max_interval: i32,
        latency: i32,
        timeout: i32,
        min_ce_len: u16,
        max_ce_len: u16,
    );

    /// Sets preferred PHY.
    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    );

    /// Reads the PHY used by a peer.
    fn client_read_phy(&mut self, client_id: i32, addr: String);

    // GATT Server

    /// Registers a GATT Server.
    fn register_server(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattServerCallback + Send>,
        eatt_support: bool,
    );

    /// Unregisters a GATT Server.
    fn unregister_server(&mut self, server_id: i32);

    /// Initiates a GATT connection to the server.
    fn server_connect(
        &self,
        server_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
    ) -> bool;

    /// Disconnects the server GATT connection.
    fn server_disconnect(&self, server_id: i32, addr: String) -> bool;

    /// Adds a service to the GATT server.
    fn add_service(&self, server_id: i32, service: BluetoothGattService);

    /// Removes a service from the GATT server.
    fn remove_service(&self, server_id: i32, handle: i32);

    /// Clears all services from the GATT server.
    fn clear_services(&self, server_id: i32);

    /// Sends a response to a read/write operation.
    fn send_response(
        &self,
        server_id: i32,
        addr: String,
        request_id: i32,
        status: GattStatus,
        offset: i32,
        value: Vec<u8>,
    ) -> bool;

    /// Sends a notification to a remote device.
    fn send_notification(
        &self,
        server_id: i32,
        addr: String,
        handle: i32,
        confirm: bool,
        value: Vec<u8>,
    ) -> bool;

    /// Sets preferred PHY.
    fn server_set_preferred_phy(
        &self,
        server_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    );

    /// Reads the PHY used by a peer.
    fn server_read_phy(&self, server_id: i32, addr: String);
}

#[derive(Debug, Default, Clone)]
/// Represents a GATT Descriptor.
pub struct BluetoothGattDescriptor {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub permissions: i32,
}

impl BluetoothGattDescriptor {
    pub(crate) fn new(
        uuid: Uuid128Bit,
        instance_id: i32,
        permissions: i32,
    ) -> BluetoothGattDescriptor {
        BluetoothGattDescriptor { uuid, instance_id, permissions }
    }
}

#[derive(Debug, Default, Clone)]
/// Represents a GATT Characteristic.
pub struct BluetoothGattCharacteristic {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub properties: i32,
    pub permissions: i32,
    pub key_size: i32,
    pub write_type: GattWriteType,
    pub descriptors: Vec<BluetoothGattDescriptor>,
}

impl BluetoothGattCharacteristic {
    // Properties are u8 but i32 in these apis.
    pub const PROPERTY_BROADCAST: i32 = 1 << 0;
    pub const PROPERTY_READ: i32 = 1 << 1;
    pub const PROPERTY_WRITE_NO_RESPONSE: i32 = 1 << 2;
    pub const PROPERTY_WRITE: i32 = 1 << 3;
    pub const PROPERTY_NOTIFY: i32 = 1 << 4;
    pub const PROPERTY_INDICATE: i32 = 1 << 5;
    pub const PROPERTY_SIGNED_WRITE: i32 = 1 << 6;
    pub const PROPERTY_EXTENDED_PROPS: i32 = 1 << 7;

    // Permissions are u16 but i32 in these apis.
    pub const PERMISSION_READ: i32 = 1 << 0;
    pub const PERMISSION_READ_ENCRYPTED: i32 = 1 << 1;
    pub const PERMISSION_READ_ENCRYPED_MITM: i32 = 1 << 2;
    pub const PERMISSION_WRITE: i32 = 1 << 4;
    pub const PERMISSION_WRITE_ENCRYPTED: i32 = 1 << 5;
    pub const PERMISSION_WRITE_ENCRYPTED_MITM: i32 = 1 << 6;
    pub const PERMISSION_WRITE_SIGNED: i32 = 1 << 7;
    pub const PERMISSION_WRITE_SIGNED_MITM: i32 = 1 << 8;

    pub(crate) fn new(
        uuid: Uuid128Bit,
        instance_id: i32,
        properties: i32,
        permissions: i32,
    ) -> BluetoothGattCharacteristic {
        BluetoothGattCharacteristic {
            uuid,
            instance_id,
            properties,
            permissions,
            write_type: if properties & BluetoothGattCharacteristic::PROPERTY_WRITE_NO_RESPONSE != 0
            {
                GattWriteType::WriteNoRsp
            } else {
                GattWriteType::Write
            },
            key_size: 16,
            descriptors: vec![],
        }
    }
}

#[derive(Debug, Default, Clone)]
/// Represents a GATT Service.
pub struct BluetoothGattService {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub service_type: i32,
    pub characteristics: Vec<BluetoothGattCharacteristic>,
    pub included_services: Vec<BluetoothGattService>,
}

impl BluetoothGattService {
    pub fn new(uuid: Uuid128Bit, instance_id: i32, service_type: i32) -> BluetoothGattService {
        BluetoothGattService {
            uuid,
            instance_id,
            service_type,
            characteristics: vec![],
            included_services: vec![],
        }
    }

    fn from_db(elements: Vec<BtGattDbElement>) -> Vec<BluetoothGattService> {
        let mut db_out: Vec<BluetoothGattService> = vec![];

        for elem in elements {
            match GattDbElementType::from_u32(elem.type_).unwrap() {
                GattDbElementType::PrimaryService | GattDbElementType::SecondaryService => {
                    db_out.push(BluetoothGattService::new(
                        elem.uuid.uu,
                        elem.attribute_handle as i32,
                        elem.type_ as i32,
                    ));
                    // TODO(b/200065274): Mark restricted services.
                }

                GattDbElementType::Characteristic => {
                    match db_out.last_mut() {
                        Some(s) => s.characteristics.push(BluetoothGattCharacteristic::new(
                            elem.uuid.uu,
                            elem.attribute_handle as i32,
                            elem.properties as i32,
                            0,
                        )),
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                    // TODO(b/200065274): Mark restricted characteristics.
                }

                GattDbElementType::Descriptor => {
                    match db_out.last_mut() {
                        Some(s) => match s.characteristics.last_mut() {
                            Some(c) => c.descriptors.push(BluetoothGattDescriptor::new(
                                elem.uuid.uu,
                                elem.attribute_handle as i32,
                                0,
                            )),
                            None => {
                                // TODO(b/193685325): Log error.
                            }
                        },
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                    // TODO(b/200065274): Mark restricted descriptors.
                }

                GattDbElementType::IncludedService => {
                    match db_out.last_mut() {
                        Some(s) => {
                            s.included_services.push(BluetoothGattService::new(
                                elem.uuid.uu,
                                elem.attribute_handle as i32,
                                elem.type_ as i32,
                            ));
                        }
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                }
            }
        }

        db_out
    }

    fn into_db(service: BluetoothGattService) -> Vec<BtGattDbElement> {
        let mut db_out: Vec<BtGattDbElement> = vec![];
        db_out.push(BtGattDbElement {
            id: service.instance_id as u16,
            uuid: Uuid::from(service.uuid),
            type_: service.service_type as u32,
            attribute_handle: service.instance_id as u16,
            start_handle: service.instance_id as u16,
            end_handle: 0,
            properties: 0,
            extended_properties: 0,
            permissions: 0,
        });

        for char in service.characteristics {
            db_out.push(BtGattDbElement {
                id: char.instance_id as u16,
                uuid: Uuid::from(char.uuid),
                type_: GattDbElementType::Characteristic as u32,
                attribute_handle: char.instance_id as u16,
                start_handle: 0,
                end_handle: 0,
                properties: char.properties as u8,
                extended_properties: 0,
                permissions: char.permissions as u16,
            });

            for desc in char.descriptors {
                db_out.push(BtGattDbElement {
                    id: desc.instance_id as u16,
                    uuid: Uuid::from(desc.uuid),
                    type_: GattDbElementType::Descriptor as u32,
                    attribute_handle: desc.instance_id as u16,
                    start_handle: 0,
                    end_handle: 0,
                    properties: 0,
                    extended_properties: 0,
                    permissions: desc.permissions as u16,
                });
            }
        }

        for included_service in service.included_services {
            db_out.push(BtGattDbElement {
                id: included_service.instance_id as u16,
                uuid: Uuid::from(included_service.uuid),
                type_: included_service.service_type as u32,
                attribute_handle: included_service.instance_id as u16,
                start_handle: 0,
                end_handle: 0,
                properties: 0,
                extended_properties: 0,
                permissions: 0,
            });
        }

        // Set end handle of primary/secondary attribute to last element's handle
        if let Some(elem) = db_out.last() {
            db_out[0].end_handle = elem.attribute_handle;
        }

        db_out
    }
}

/// Callback for GATT Client API.
pub trait IBluetoothGattCallback: RPCProxy {
    /// When the `register_client` request is done.
    fn on_client_registered(&mut self, _status: GattStatus, _client_id: i32);

    /// When there is a change in the state of a GATT client connection.
    fn on_client_connection_state(
        &mut self,
        _status: GattStatus,
        _client_id: i32,
        _connected: bool,
        _addr: String,
    );

    /// When there is a change of PHY.
    fn on_phy_update(&mut self, _addr: String, _tx_phy: LePhy, _rx_phy: LePhy, _status: GattStatus);

    /// The completion of IBluetoothGatt::read_phy.
    fn on_phy_read(&mut self, _addr: String, _tx_phy: LePhy, _rx_phy: LePhy, _status: GattStatus);

    /// When GATT db is available.
    fn on_search_complete(
        &mut self,
        _addr: String,
        _services: Vec<BluetoothGattService>,
        _status: GattStatus,
    );

    /// The completion of IBluetoothGatt::read_characteristic.
    fn on_characteristic_read(
        &mut self,
        _addr: String,
        _status: GattStatus,
        _handle: i32,
        _value: Vec<u8>,
    );

    /// The completion of IBluetoothGatt::write_characteristic.
    fn on_characteristic_write(&mut self, _addr: String, _status: GattStatus, _handle: i32);

    /// When a reliable write is completed.
    fn on_execute_write(&mut self, _addr: String, _status: GattStatus);

    /// The completion of IBluetoothGatt::read_descriptor.
    fn on_descriptor_read(
        &mut self,
        _addr: String,
        _status: GattStatus,
        _handle: i32,
        _value: Vec<u8>,
    );

    /// The completion of IBluetoothGatt::write_descriptor.
    fn on_descriptor_write(&mut self, _addr: String, _status: GattStatus, _handle: i32);

    /// When notification or indication is received.
    fn on_notify(&mut self, _addr: String, _handle: i32, _value: Vec<u8>);

    /// The completion of IBluetoothGatt::read_remote_rssi.
    fn on_read_remote_rssi(&mut self, _addr: String, _rssi: i32, _status: GattStatus);

    /// The completion of IBluetoothGatt::configure_mtu.
    fn on_configure_mtu(&mut self, _addr: String, _mtu: i32, _status: GattStatus);

    /// When a connection parameter changes.
    fn on_connection_updated(
        &mut self,
        _addr: String,
        _interval: i32,
        _latency: i32,
        _timeout: i32,
        _status: GattStatus,
    );

    /// When there is an addition, removal, or change of a GATT service.
    fn on_service_changed(&mut self, _addr: String);
}

/// Callback for GATT Server API.
pub trait IBluetoothGattServerCallback: RPCProxy {
    /// When the `register_server` request is done.
    fn on_server_registered(&mut self, _status: GattStatus, _server_id: i32);

    /// When there is a change in the state of a GATT server connection.
    fn on_server_connection_state(&mut self, _server_id: i32, _connected: bool, _addr: String);

    /// When there is a service added to the GATT server.
    fn on_service_added(&mut self, _status: GattStatus, _service: BluetoothGattService);

    /// When a service has been removed from the GATT server.
    fn on_service_removed(&mut self, status: GattStatus, handle: i32);

    /// When a remote device has requested to read a characteristic.
    fn on_characteristic_read_request(
        &mut self,
        _addr: String,
        _trans_id: i32,
        _offset: i32,
        _is_long: bool,
        _handle: i32,
    );

    /// When a remote device has requested to read a descriptor.
    fn on_descriptor_read_request(
        &mut self,
        _addr: String,
        _trans_id: i32,
        _offset: i32,
        _is_long: bool,
        _handle: i32,
    );

    /// When a remote device has requested to write to a characteristic.
    fn on_characteristic_write_request(
        &mut self,
        _addr: String,
        _trans_id: i32,
        _offset: i32,
        _len: i32,
        _is_prep: bool,
        _need_rsp: bool,
        _handle: i32,
        _value: Vec<u8>,
    );

    /// When a remote device has requested to write to a descriptor.
    fn on_descriptor_write_request(
        &mut self,
        _addr: String,
        _trans_id: i32,
        _offset: i32,
        _len: i32,
        _is_prep: bool,
        _need_rsp: bool,
        _handle: i32,
        _value: Vec<u8>,
    );

    /// When a previously prepared write is to be executed.
    fn on_execute_write(&mut self, _addr: String, _trans_id: i32, _exec_write: bool);

    /// When a notification or indication has been sent to a remote device.
    fn on_notification_sent(&mut self, _addr: String, _status: GattStatus);

    /// When the MTU for a given connection changes
    fn on_mtu_changed(&mut self, addr: String, mtu: i32);

    /// When there is a change of PHY.
    fn on_phy_update(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus);

    /// The completion of IBluetoothGatt::server_read_phy.
    fn on_phy_read(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus);

    /// When the connection parameters for a given connection changes.
    fn on_connection_updated(
        &mut self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: GattStatus,
    );

    /// When the subrate change event for a given connection is received.
    fn on_subrate_change(
        &mut self,
        addr: String,
        subrate_factor: i32,
        latency: i32,
        cont_num: i32,
        timeout: i32,
        status: GattStatus,
    );
}

/// Interface for scanner callbacks to clients, passed to
/// `IBluetoothGatt::register_scanner_callback`.
pub trait IScannerCallback: RPCProxy {
    /// When the `register_scanner` request is done.
    fn on_scanner_registered(&mut self, uuid: Uuid128Bit, scanner_id: u8, status: GattStatus);

    /// When an LE advertisement matching aggregate filters is detected. This callback is shared
    /// among all scanner callbacks and is triggered for *every* advertisement that the controller
    /// receives. For listening to the beginning and end of a specific scanner's advertisements
    /// detected while in RSSI range, use on_advertisement_found and on_advertisement_lost below.
    fn on_scan_result(&mut self, scan_result: ScanResult);

    /// When an LE advertisement matching aggregate filters is found. The criteria of
    /// how a device is considered found is specified by ScanFilter.
    fn on_advertisement_found(&mut self, scanner_id: u8, scan_result: ScanResult);

    /// When an LE advertisement matching aggregate filters is no longer detected. The criteria of
    /// how a device is considered lost is specified by ScanFilter.
    // TODO(b/269343922): Rename this to on_advertisement_lost for symmetry with
    // on_advertisement_found.
    fn on_advertisement_lost(&mut self, scanner_id: u8, scan_result: ScanResult);

    /// When LE Scan module changes suspend mode due to system suspend/resume.
    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode);
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// GATT write type.
pub enum GattDbElementType {
    PrimaryService = 0,
    SecondaryService = 1,
    IncludedService = 2,
    Characteristic = 3,
    Descriptor = 4,
}

impl Into<i32> for GattDbElementType {
    fn into(self) -> i32 {
        self.to_u8().unwrap_or(0).into()
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive, Copy, Clone)]
#[repr(u8)]
/// GATT write type.
pub enum GattWriteType {
    Invalid = 0,
    WriteNoRsp = 1,
    Write = 2,
    WritePrepare = 3,
}

impl Default for GattWriteType {
    fn default() -> Self {
        GattWriteType::Write
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive, Clone, PartialEq)]
#[repr(u32)]
/// Scan type configuration.
pub enum ScanType {
    Active = 0,
    Passive = 1,
}

impl Default for ScanType {
    fn default() -> Self {
        ScanType::Passive
    }
}

/// Represents scanning configurations to be passed to `IBluetoothGatt::start_scan`.
///
/// This configuration is general and supported on all Bluetooth hardware, irrelevant of the
/// hardware filter offload (APCF or MSFT).
#[derive(Debug, Clone)]
pub struct ScanSettings {
    pub interval: i32,
    pub window: i32,
    pub scan_type: ScanType,
}

impl ScanSettings {
    fn extract_scan_parameters(&self) -> Option<(u8, u16, u16)> {
        let scan_type = match self.scan_type {
            ScanType::Passive => 0x00,
            ScanType::Active => 0x01,
        };
        let interval = match u16::try_from(self.interval) {
            Ok(i) => i,
            Err(e) => {
                println!("Invalid scan interval {}: {}", self.interval, e);
                return None;
            }
        };
        let window = match u16::try_from(self.window) {
            Ok(w) => w,
            Err(e) => {
                println!("Invalid scan window {}: {}", self.window, e);
                return None;
            }
        };
        return Some((scan_type, interval, window));
    }
}

/// Represents scan result
#[derive(Debug)]
pub struct ScanResult {
    pub name: String,
    pub address: String,
    pub addr_type: u8,
    pub event_type: u16,
    pub primary_phy: u8,
    pub secondary_phy: u8,
    pub advertising_sid: u8,
    pub tx_power: i8,
    pub rssi: i8,
    pub periodic_adv_int: u16,
    pub flags: u8,
    pub service_uuids: Vec<Uuid128Bit>,
    /// A map of 128-bit UUID and its corresponding service data.
    pub service_data: HashMap<String, Vec<u8>>,
    pub manufacturer_data: HashMap<u16, Vec<u8>>,
    pub adv_data: Vec<u8>,
}

#[derive(Debug, Clone)]
pub struct ScanFilterPattern {
    /// Specifies the starting byte position of the pattern immediately following AD Type.
    pub start_position: u8,

    /// Advertising Data type (https://www.bluetooth.com/specifications/assigned-numbers/).
    pub ad_type: u8,

    /// The pattern to be matched for the specified AD Type within the advertisement packet from
    /// the specified starting byte.
    pub content: Vec<u8>,
}

/// Represents the condition for matching advertisements.
///
/// Only pattern-based matching is implemented.
#[derive(Debug, Clone)]
pub enum ScanFilterCondition {
    /// All advertisements are matched.
    All,

    /// Match by pattern anywhere in the advertisement data. Multiple patterns are "OR"-ed.
    Patterns(Vec<ScanFilterPattern>),

    /// Match by UUID (not implemented).
    Uuid,

    /// Match if the IRK resolves an advertisement (not implemented).
    Irk,

    /// Match by Bluetooth address (not implemented).
    BluetoothAddress,
}

/// Represents a scan filter to be passed to `IBluetoothGatt::start_scan`.
///
/// This filter is intentionally modelled close to the MSFT hardware offload filter.
/// Reference:
/// https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/microsoft-defined-bluetooth-hci-commands-and-events
#[derive(Debug, Clone)]
pub struct ScanFilter {
    /// Advertisements with RSSI above or equal this value is considered "found".
    pub rssi_high_threshold: u8,

    /// Advertisements with RSSI below or equal this value (for a period of rssi_low_timeout) is
    /// considered "lost".
    pub rssi_low_threshold: u8,

    /// The time in seconds over which the RSSI value should be below rssi_low_threshold before
    /// being considered "lost".
    pub rssi_low_timeout: u8,

    /// The sampling interval in milliseconds.
    pub rssi_sampling_period: u8,

    /// The condition to match advertisements with.
    pub condition: ScanFilterCondition,
}

type ScannersMap = HashMap<Uuid, ScannerInfo>;

const DEFAULT_ASYNC_TIMEOUT_MS: u64 = 5000;

/// Abstraction for async GATT operations. Contains async methods for coordinating async operations
/// more conveniently.
struct GattAsyncIntf {
    scanners: Arc<Mutex<ScannersMap>>,
    gatt: Option<Arc<Mutex<Gatt>>>,

    async_helper_msft_adv_monitor_add: AsyncHelper<(u8, u8)>,
    async_helper_msft_adv_monitor_remove: AsyncHelper<u8>,
    async_helper_msft_adv_monitor_enable: AsyncHelper<u8>,
}

impl GattAsyncIntf {
    /// Adds an advertisement monitor. Returns monitor handle and status.
    async fn msft_adv_monitor_add(&mut self, monitor: MsftAdvMonitor) -> Result<(u8, u8), ()> {
        let gatt = self.gatt.as_ref().unwrap().clone();

        self.async_helper_msft_adv_monitor_add
            .call_method(
                move |call_id| {
                    gatt.lock().unwrap().scanner.msft_adv_monitor_add(call_id, &monitor);
                },
                Some(DEFAULT_ASYNC_TIMEOUT_MS),
            )
            .await
    }

    /// Removes an advertisement monitor. Returns status.
    async fn msft_adv_monitor_remove(&mut self, monitor_handle: u8) -> Result<u8, ()> {
        let gatt = self.gatt.as_ref().unwrap().clone();

        self.async_helper_msft_adv_monitor_remove
            .call_method(
                move |call_id| {
                    gatt.lock().unwrap().scanner.msft_adv_monitor_remove(call_id, monitor_handle);
                },
                Some(DEFAULT_ASYNC_TIMEOUT_MS),
            )
            .await
    }

    /// Enables/disables an advertisement monitor. Returns status.
    async fn msft_adv_monitor_enable(&mut self, enable: bool) -> Result<u8, ()> {
        let gatt = self.gatt.as_ref().unwrap().clone();

        self.async_helper_msft_adv_monitor_enable
            .call_method(
                move |call_id| {
                    gatt.lock().unwrap().scanner.msft_adv_monitor_enable(call_id, enable);
                },
                Some(DEFAULT_ASYNC_TIMEOUT_MS),
            )
            .await
    }

    /// Updates the scan state depending on the states of registered scanners:
    /// 1. Scan is started if there is at least 1 enabled scanner.
    /// 2. Always toggle the scan off and on so that we reset the scan parameters based on whether
    ///    we have enabled scanners using hardware filtering.
    ///    TODO(b/266752123): We can do more bookkeeping to optimize when we really need to
    ///    toggle. Also improve toggling API into 1 operation that guarantees correct ordering.
    /// 3. If there is an enabled ScanType::Active scanner, prefer its scan settings. Otherwise,
    ///    adopt the settings from any of the enabled scanners. We shouldn't just use the settings
    ///    from |scanner_id| because it may refer to a disabled scan.
    ///
    /// Note: this does not need to be async, but declared as async for consistency in this struct.
    /// May be converted into real async in the future if btif supports it.
    async fn update_scan(&mut self, scanner_id: u8) {
        let mut has_enabled_scan = false;
        let mut enabled_scan_param = None;
        let mut enabled_active_scan_param = None;
        for scanner in self.scanners.lock().unwrap().values() {
            if !scanner.is_enabled {
                continue;
            }
            has_enabled_scan = true;
            if let Some(ss) = &scanner.scan_settings {
                enabled_scan_param = ss.extract_scan_parameters();
                if ss.scan_type == ScanType::Active {
                    enabled_active_scan_param = ss.extract_scan_parameters();
                    break;
                }
            }
        }

        self.gatt.as_ref().unwrap().lock().unwrap().scanner.stop_scan();
        if !has_enabled_scan {
            return;
        }

        if let Some((scan_type, scan_interval, scan_window)) =
            enabled_active_scan_param.or(enabled_scan_param)
        {
            self.gatt.as_ref().unwrap().lock().unwrap().scanner.set_scan_parameters(
                scanner_id,
                scan_type,
                scan_interval,
                scan_window,
            );
        }
        self.gatt.as_ref().unwrap().lock().unwrap().scanner.start_scan();
    }
}

pub enum GattActions {
    /// This disconnects all server and client connections to the device.
    /// Params: remote_device
    Disconnect(BluetoothDevice),
}

/// Implementation of the GATT API (IBluetoothGatt).
pub struct BluetoothGatt {
    intf: Arc<Mutex<BluetoothInterface>>,
    // TODO(b/254870880): Wrapping in an `Option` makes the code unnecessarily verbose. Find a way
    // to not wrap this in `Option` since we know that we can't function without `gatt` being
    // initialized anyway.
    gatt: Option<Arc<Mutex<Gatt>>>,

    context_map: ContextMap,
    server_context_map: ServerContextMap,
    reliable_queue: HashSet<String>,
    scanner_callbacks: Callbacks<dyn IScannerCallback + Send>,
    scanners: Arc<Mutex<ScannersMap>>,
    scan_suspend_mode: SuspendMode,
    paused_scanner_ids: Vec<u8>,
    adv_manager: AdvertiseManager,

    adv_mon_add_cb_sender: CallbackSender<(u8, u8)>,
    adv_mon_remove_cb_sender: CallbackSender<u8>,
    adv_mon_enable_cb_sender: CallbackSender<u8>,

    // Used for generating random UUIDs. SmallRng is chosen because it is fast, don't use this for
    // cryptography.
    small_rng: SmallRng,

    gatt_async: Arc<tokio::sync::Mutex<GattAsyncIntf>>,
    enabled: bool,
}

impl BluetoothGatt {
    /// Constructs a new IBluetoothGatt implementation.
    pub fn new(intf: Arc<Mutex<BluetoothInterface>>, tx: Sender<Message>) -> BluetoothGatt {
        let scanners = Arc::new(Mutex::new(HashMap::new()));

        let async_helper_msft_adv_monitor_add = AsyncHelper::new("MsftAdvMonitorAdd");
        let async_helper_msft_adv_monitor_remove = AsyncHelper::new("MsftAdvMonitorRemove");
        let async_helper_msft_adv_monitor_enable = AsyncHelper::new("MsftAdvMonitorEnable");
        BluetoothGatt {
            intf,
            gatt: None,
            context_map: ContextMap::new(tx.clone()),
            server_context_map: ServerContextMap::new(tx.clone()),
            reliable_queue: HashSet::new(),
            scanner_callbacks: Callbacks::new(tx.clone(), Message::ScannerCallbackDisconnected),
            scanners: scanners.clone(),
            scan_suspend_mode: SuspendMode::Normal,
            paused_scanner_ids: Vec::new(),
            small_rng: SmallRng::from_entropy(),
            adv_manager: AdvertiseManager::new(tx.clone()),
            adv_mon_add_cb_sender: async_helper_msft_adv_monitor_add.get_callback_sender(),
            adv_mon_remove_cb_sender: async_helper_msft_adv_monitor_remove.get_callback_sender(),
            adv_mon_enable_cb_sender: async_helper_msft_adv_monitor_enable.get_callback_sender(),
            gatt_async: Arc::new(tokio::sync::Mutex::new(GattAsyncIntf {
                scanners,
                gatt: None,
                async_helper_msft_adv_monitor_add,
                async_helper_msft_adv_monitor_remove,
                async_helper_msft_adv_monitor_enable,
            })),
            enabled: false,
        }
    }

    pub fn init_profiles(&mut self, tx: Sender<Message>, api_tx: Sender<APIMessage>) {
        self.gatt = Gatt::new(&self.intf.lock().unwrap()).map(|gatt| Arc::new(Mutex::new(gatt)));

        let tx_clone = tx.clone();
        let gatt_client_callbacks_dispatcher = GattClientCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::GattClient(cb)).await;
                });
            }),
        };

        let tx_clone = tx.clone();
        let gatt_server_callbacks_dispatcher = GattServerCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::GattServer(cb)).await;
                });
            }),
        };

        let tx_clone = tx.clone();
        let gatt_scanner_callbacks_dispatcher = GattScannerCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::LeScanner(cb)).await;
                });
            }),
        };

        let tx_clone = tx.clone();
        let gatt_scanner_inband_callbacks_dispatcher = GattScannerInbandCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::LeScannerInband(cb)).await;
                });
            }),
        };

        let tx_clone = tx.clone();
        let gatt_adv_inband_callbacks_dispatcher = GattAdvInbandCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::LeAdvInband(cb)).await;
                });
            }),
        };

        let tx_clone = tx.clone();
        let gatt_adv_callbacks_dispatcher = GattAdvCallbacksDispatcher {
            dispatch: Box::new(move |cb| {
                let tx_clone = tx_clone.clone();
                topstack::get_runtime().spawn(async move {
                    let _ = tx_clone.send(Message::LeAdv(cb)).await;
                });
            }),
        };

        self.gatt.as_ref().unwrap().lock().unwrap().initialize(
            gatt_client_callbacks_dispatcher,
            gatt_server_callbacks_dispatcher,
            gatt_scanner_callbacks_dispatcher,
            gatt_scanner_inband_callbacks_dispatcher,
            gatt_adv_inband_callbacks_dispatcher,
            gatt_adv_callbacks_dispatcher,
        );

        let gatt = self.gatt.clone();
        let gatt_async = self.gatt_async.clone();
        let api_tx_clone = api_tx.clone();
        tokio::spawn(async move {
            gatt_async.lock().await.gatt = gatt;
            // TODO(b/247093293): Gatt topshim api is only usable some
            // time after init. Investigate why this delay is needed
            // and make it a blocking part before removing this.
            time::sleep(time::Duration::from_millis(500)).await;
            let _ = api_tx_clone.send(APIMessage::IsReady(BluetoothAPI::Gatt)).await;
        });
    }

    /// Initializes AdvertiseManager.
    ///
    /// Query |is_le_ext_adv_supported| outside this function (before locking BluetoothGatt) to
    /// avoid deadlock. |is_le_ext_adv_supported| can only be queried after Bluetooth is ready.
    ///
    /// TODO(b/242083290): Correctly fire IsReady message for Adv API in this function after the
    /// API is fully split out. For now Gatt is delayed for 500ms (see
    /// |BluetoothGatt::init_profiles|) which shall be enough for Bluetooth to become ready.
    pub fn init_adv_manager(
        &mut self,
        adapter: Arc<Mutex<Box<Bluetooth>>>,
        is_le_ext_adv_supported: bool,
    ) {
        self.adv_manager.initialize(
            self.gatt.as_ref().unwrap().clone(),
            adapter,
            is_le_ext_adv_supported,
        );
    }

    pub fn enable(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    /// Remove a scanner callback and unregisters all scanners associated with that callback.
    pub fn remove_scanner_callback(&mut self, callback_id: u32) -> bool {
        let affected_scanner_ids: Vec<u8> = self
            .scanners
            .lock()
            .unwrap()
            .iter()
            .filter(|(_uuid, scanner)| scanner.callback_id == callback_id)
            .filter_map(|(_uuid, scanner)| {
                if let Some(scanner_id) = scanner.scanner_id {
                    Some(scanner_id)
                } else {
                    None
                }
            })
            .collect();

        // All scanners associated with the callback must be also unregistered.
        for scanner_id in affected_scanner_ids {
            self.unregister_scanner(scanner_id);
        }

        self.scanner_callbacks.remove_callback(callback_id)
    }

    /// Set the suspend mode.
    pub fn set_scan_suspend_mode(&mut self, suspend_mode: SuspendMode) {
        if suspend_mode != self.scan_suspend_mode {
            self.scan_suspend_mode = suspend_mode.clone();

            // Notify current suspend mode to all active callbacks.
            self.scanner_callbacks.for_all_callbacks(|callback| {
                callback.on_suspend_mode_change(suspend_mode.clone());
            });
        }
    }

    /// Enters suspend mode for LE Scan.
    ///
    /// This "pauses" all operations managed by this module to prepare for system suspend. A
    /// callback is triggered to let clients know that this module is in suspend mode and some
    /// subsequent API calls will be blocked in this mode.
    pub fn scan_enter_suspend(&mut self) -> BtStatus {
        if self.get_scan_suspend_mode() != SuspendMode::Normal {
            return BtStatus::Busy;
        }
        self.set_scan_suspend_mode(SuspendMode::Suspending);

        // Collect the scanners that will be paused so that they can be re-enabled at resume.
        let paused_scanner_ids = self
            .scanners
            .lock()
            .unwrap()
            .iter()
            .filter_map(|(_uuid, scanner)| {
                if let (true, Some(scanner_id)) = (scanner.is_enabled, scanner.scanner_id) {
                    Some(scanner_id)
                } else {
                    None
                }
            })
            .collect();

        // Note: We can't simply disable the LE scanning. When a filter is offloaded
        // with the MSFT extension and it is monitoring a device, it sends a
        // `Monitor Device Event` to indicate that monitoring is stopped and this
        // can cause an early wake-up. Until we fix the disable + mask solution, we
        // must remove all monitors before suspend and re-monitor them on resume.
        for &scanner_id in &paused_scanner_ids {
            self.stop_scan(scanner_id);
            if let Some(scanner) =
                Self::find_scanner_by_id(&mut self.scanners.lock().unwrap(), scanner_id)
            {
                scanner.is_suspended = true;
            }
        }
        self.paused_scanner_ids = paused_scanner_ids;
        self.set_scan_suspend_mode(SuspendMode::Suspended);
        return BtStatus::Success;
    }

    /// Exits suspend mode for LE Scan.
    ///
    /// To be called after system resume/wake up. This "unpauses" the operations that were "paused"
    /// due to suspend. A callback is triggered to let clients when this module has exited suspend
    /// mode.
    pub fn scan_exit_suspend(&mut self) -> BtStatus {
        if self.get_scan_suspend_mode() != SuspendMode::Suspended {
            return BtStatus::Busy;
        }
        self.set_scan_suspend_mode(SuspendMode::Resuming);

        // The resume_scan() will add and reenable the monitors individually.
        for scanner_id in self.paused_scanner_ids.drain(..).collect::<Vec<_>>() {
            self.resume_scan(scanner_id);
        }
        self.set_scan_suspend_mode(SuspendMode::Normal);

        return BtStatus::Success;
    }

    fn find_scanner_by_id<'a>(
        scanners: &'a mut MutexGuard<ScannersMap>,
        scanner_id: u8,
    ) -> Option<&'a mut ScannerInfo> {
        scanners.values_mut().find(|scanner| scanner.scanner_id == Some(scanner_id))
    }

    /// The resume_scan method is used to resume scanning after system suspension.
    /// It assumes that scanner.filter has already had the filter data.
    fn resume_scan(&mut self, scanner_id: u8) -> BtStatus {
        if !self.enabled {
            return BtStatus::Fail;
        }

        let scan_suspend_mode = self.get_scan_suspend_mode();
        if scan_suspend_mode != SuspendMode::Normal && scan_suspend_mode != SuspendMode::Resuming {
            return BtStatus::Busy;
        }

        let filter = {
            let mut scanners_lock = self.scanners.lock().unwrap();
            if let Some(scanner) = Self::find_scanner_by_id(&mut scanners_lock, scanner_id) {
                if scanner.is_suspended {
                    scanner.is_suspended = false;
                    scanner.is_enabled = true;
                    // When a scanner resumes from a suspended state, the
                    // scanner.filter has already had the filter data.
                    scanner.filter.clone()
                } else {
                    log::warn!(
                        "This Scanner {} is supposed to resume from suspended state",
                        scanner_id
                    );
                    return BtStatus::Fail;
                }
            } else {
                log::warn!("Scanner {} not found", scanner_id);
                return BtStatus::Fail;
            }
        };

        self.add_monitor_and_update_scan(scanner_id, filter)
    }

    fn add_monitor_and_update_scan(
        &mut self,
        scanner_id: u8,
        filter: Option<ScanFilter>,
    ) -> BtStatus {
        let gatt_async = self.gatt_async.clone();
        let scanners = self.scanners.clone();
        let is_msft_supported = self.is_msft_supported();

        tokio::spawn(async move {
            // The three operations below (monitor add, monitor enable, update scan) happen one
            // after another, and cannot be interleaved with other GATT async operations.
            // So acquire the GATT async lock in the beginning of this block and will be released
            // at the end of this block.
            // TODO(b/217274432): Consider not using async model but instead add actions when
            // handling callbacks.
            let mut gatt_async = gatt_async.lock().await;

            // Add and enable the monitor filter only when the MSFT extension is supported.
            if is_msft_supported {
                if let Some(filter) = filter {
                    let monitor_handle =
                        match gatt_async.msft_adv_monitor_add((&filter).into()).await {
                            Ok((handle, 0)) => handle,
                            _ => {
                                log::error!("Error adding advertisement monitor");
                                return;
                            }
                        };

                    if let Some(scanner) =
                        Self::find_scanner_by_id(&mut scanners.lock().unwrap(), scanner_id)
                    {
                        // The monitor handle is needed in stop_scan().
                        scanner.monitor_handle = Some(monitor_handle);
                    }

                    log::debug!("Added adv monitor handle = {}", monitor_handle);
                }

                let has_enabled_unfiltered_scanner = scanners
                    .lock()
                    .unwrap()
                    .iter()
                    .any(|(_uuid, scanner)| scanner.is_enabled && scanner.filter.is_none());

                if !gatt_async
                    .msft_adv_monitor_enable(!has_enabled_unfiltered_scanner)
                    .await
                    .map_or(false, |status| status == 0)
                {
                    // TODO(b/266752123):
                    // Intel controller throws "Command Disallowed" error if we tried to enable/disable
                    // filter but it's already at the same state. This is harmless but we can improve
                    // the state machine to avoid calling enable/disable if it's already at that state
                    log::error!("Error updating Advertisement Monitor enable");
                }
            }

            gatt_async.update_scan(scanner_id).await;
        });

        BtStatus::Success
    }

    /// Remove an adv_manager callback and unregisters all advertising sets associated with that callback.
    pub fn remove_adv_callback(&mut self, callback_id: u32) -> bool {
        self.adv_manager.get_impl().unregister_callback(callback_id)
    }

    pub fn remove_client_callback(&mut self, callback_id: u32) {
        // Unregister client if client id exists.
        if let Some(client) = self.context_map.get_by_callback_id(callback_id) {
            if let Some(id) = client.id {
                self.unregister_client(id);
            }
        }

        // Always remove callback.
        self.context_map.remove_callback(callback_id);
    }

    pub fn remove_server_callback(&mut self, callback_id: u32) {
        // Unregister server if server id exists.
        if let Some(server) = self.server_context_map.get_by_callback_id(callback_id) {
            if let Some(id) = server.id {
                self.unregister_server(id);
            }
        }

        // Always remove callback.
        self.context_map.remove_callback(callback_id);
    }

    /// Enters suspend mode for LE advertising.
    pub fn advertising_enter_suspend(&mut self) {
        self.adv_manager.get_impl().enter_suspend()
    }

    /// Exits suspend mode for LE advertising.
    pub fn advertising_exit_suspend(&mut self) {
        self.adv_manager.get_impl().exit_suspend()
    }

    /// Start an active scan on given scanner id. This will look up and assign
    /// the correct ScanSettings for it as well.
    pub(crate) fn start_active_scan(&mut self, scanner_id: u8) -> BtStatus {
        let settings = ScanSettings {
            interval: sysprop::get_i32(sysprop::PropertyI32::LeInquiryScanInterval),
            window: sysprop::get_i32(sysprop::PropertyI32::LeInquiryScanWindow),
            scan_type: ScanType::Active,
        };

        self.start_scan(scanner_id, Some(settings), /*filter=*/ None)
    }

    pub(crate) fn stop_active_scan(&mut self, scanner_id: u8) -> BtStatus {
        self.stop_scan(scanner_id)
    }

    pub fn handle_action(&mut self, action: GattActions) {
        match action {
            GattActions::Disconnect(device) => {
                let address = match RawAddress::from_string(&device.address) {
                    None => {
                        warn!(
                            "GattActions::Disconnect failed: Invalid device address={}",
                            device.address
                        );
                        return;
                    }
                    Some(addr) => addr,
                };
                for client_id in self.context_map.get_client_ids_from_address(&device.address) {
                    if let Some(conn_id) =
                        self.context_map.get_conn_id_from_address(client_id, &device.address)
                    {
                        self.gatt
                            .as_ref()
                            .unwrap()
                            .lock()
                            .unwrap()
                            .client
                            .disconnect(client_id, &address, conn_id);
                    }
                }
                for server_id in
                    self.server_context_map.get_server_ids_from_address(&device.address)
                {
                    if let Some(conn_id) =
                        self.server_context_map.get_conn_id_from_address(server_id, &device.address)
                    {
                        self.gatt
                            .as_ref()
                            .unwrap()
                            .lock()
                            .unwrap()
                            .server
                            .disconnect(server_id, &address, conn_id);
                    }
                }
            }
        }
    }

    pub fn handle_adv_action(&mut self, action: AdvertiserActions) {
        self.adv_manager.get_impl().handle_action(action);
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// Status of WriteCharacteristic methods.
pub enum GattWriteRequestStatus {
    Success = 0,
    Fail = 1,
    Busy = 2,
}

// This structure keeps track of the lifecycle of a scanner.
struct ScannerInfo {
    // The callback to which events about this scanner needs to be sent to.
    // Another purpose of keeping track of the callback id is that when a callback is disconnected
    // or unregistered we need to also unregister all scanners associated with that callback to
    // prevent dangling unowned scanners.
    callback_id: u32,
    // If the scanner is registered successfully, this contains the scanner id, otherwise None.
    scanner_id: Option<u8>,
    // If one of scanners is enabled, we scan.
    is_enabled: bool,
    // Scan filter.
    filter: Option<ScanFilter>,
    // Adv monitor handle, if exists.
    monitor_handle: Option<u8>,
    // Used by start_scan() to determine if it is called because of system resuming.
    is_suspended: bool,
    // The scan parameters to use
    scan_settings: Option<ScanSettings>,
}

impl ScannerInfo {
    fn new(callback_id: u32) -> Self {
        Self {
            callback_id,
            scanner_id: None,
            is_enabled: false,
            filter: None,
            monitor_handle: None,
            is_suspended: false,
            scan_settings: None,
        }
    }
}

impl Into<MsftAdvMonitorPattern> for &ScanFilterPattern {
    fn into(self) -> MsftAdvMonitorPattern {
        MsftAdvMonitorPattern {
            ad_type: self.ad_type,
            start_byte: self.start_position,
            pattern: self.content.clone(),
        }
    }
}

impl Into<Vec<MsftAdvMonitorPattern>> for &ScanFilterCondition {
    fn into(self) -> Vec<MsftAdvMonitorPattern> {
        match self {
            ScanFilterCondition::Patterns(patterns) => {
                patterns.iter().map(|pattern| pattern.into()).collect()
            }
            _ => vec![],
        }
    }
}

impl Into<MsftAdvMonitor> for &ScanFilter {
    fn into(self) -> MsftAdvMonitor {
        MsftAdvMonitor {
            rssi_high_threshold: self.rssi_high_threshold.try_into().unwrap(),
            rssi_low_threshold: self.rssi_low_threshold.try_into().unwrap(),
            rssi_low_timeout: self.rssi_low_timeout.try_into().unwrap(),
            rssi_sampling_period: self.rssi_sampling_period.try_into().unwrap(),
            patterns: (&self.condition).into(),
        }
    }
}

impl IBluetoothGatt for BluetoothGatt {
    fn is_msft_supported(&self) -> bool {
        self.gatt.as_ref().unwrap().lock().unwrap().scanner.is_msft_supported()
    }

    fn register_scanner_callback(&mut self, callback: Box<dyn IScannerCallback + Send>) -> u32 {
        self.scanner_callbacks.add_callback(callback)
    }

    fn unregister_scanner_callback(&mut self, callback_id: u32) -> bool {
        self.remove_scanner_callback(callback_id)
    }

    fn register_scanner(&mut self, callback_id: u32) -> Uuid128Bit {
        if !self.enabled {
            return Uuid::empty().uu;
        }

        let mut bytes: [u8; 16] = [0; 16];
        self.small_rng.fill_bytes(&mut bytes);
        let uuid = Uuid::from(bytes);

        self.scanners.lock().unwrap().insert(uuid, ScannerInfo::new(callback_id));

        // libbluetooth's register_scanner takes a UUID of the scanning application. This UUID does
        // not correspond to higher level concept of "application" so we use random UUID that
        // functions as a unique identifier of the scanner.
        self.gatt.as_ref().unwrap().lock().unwrap().scanner.register_scanner(uuid);

        uuid.uu
    }

    fn unregister_scanner(&mut self, scanner_id: u8) -> bool {
        self.gatt.as_ref().unwrap().lock().unwrap().scanner.unregister(scanner_id);

        // The unregistered scanner must also be stopped.
        self.stop_scan(scanner_id);

        self.scanners
            .lock()
            .unwrap()
            .retain(|_uuid, scanner| scanner.scanner_id != Some(scanner_id));

        true
    }

    fn start_scan(
        &mut self,
        scanner_id: u8,
        settings: Option<ScanSettings>,
        filter: Option<ScanFilter>,
    ) -> BtStatus {
        if !self.enabled {
            return BtStatus::Fail;
        }

        let scan_suspend_mode = self.get_scan_suspend_mode();
        if scan_suspend_mode != SuspendMode::Normal && scan_suspend_mode != SuspendMode::Resuming {
            return BtStatus::Busy;
        }

        // If the client is not specifying scan settings, the default one will be used.
        let settings = settings.unwrap_or_else(|| ScanSettings {
            interval: sysprop::get_i32(sysprop::PropertyI32::LeInquiryScanInterval),
            window: sysprop::get_i32(sysprop::PropertyI32::LeInquiryScanWindow),
            scan_type: ScanType::default(),
        });

        // Multiplexing scanners happens at this layer. The implementations of start_scan
        // and stop_scan maintains the state of all registered scanners and based on the states
        // update the scanning and/or filter states of libbluetooth.
        {
            let mut scanners_lock = self.scanners.lock().unwrap();

            if let Some(scanner) = Self::find_scanner_by_id(&mut scanners_lock, scanner_id) {
                scanner.is_enabled = true;
                scanner.filter = filter.clone();
                scanner.scan_settings = Some(settings);
            } else {
                log::warn!("Scanner {} not found", scanner_id);
                return BtStatus::Fail;
            }
        }

        return self.add_monitor_and_update_scan(scanner_id, filter);
    }

    fn stop_scan(&mut self, scanner_id: u8) -> BtStatus {
        if !self.enabled {
            return BtStatus::Fail;
        }

        let scan_suspend_mode = self.get_scan_suspend_mode();
        if scan_suspend_mode != SuspendMode::Normal && scan_suspend_mode != SuspendMode::Suspending
        {
            return BtStatus::Busy;
        }

        let monitor_handle = {
            let mut scanners_lock = self.scanners.lock().unwrap();

            if let Some(scanner) = Self::find_scanner_by_id(&mut scanners_lock, scanner_id) {
                scanner.is_enabled = false;
                scanner.monitor_handle
            } else {
                log::warn!("Scanner {} not found", scanner_id);
                // Clients can assume success of the removal since the scanner does not exist.
                return BtStatus::Success;
            }
        };

        let gatt_async = self.gatt_async.clone();
        let scanners = self.scanners.clone();
        let is_msft_supported = self.is_msft_supported();
        tokio::spawn(async move {
            // The two operations below (monitor remove, update scan) happen one after another, and
            // cannot be interleaved with other GATT async operations.
            // So acquire the GATT async lock in the beginning of this block and will be released
            // at the end of this block.
            let mut gatt_async = gatt_async.lock().await;

            // Remove and disable the monitor only when the MSFT extension is supported.
            if is_msft_supported {
                if let Some(handle) = monitor_handle {
                    let _res = gatt_async.msft_adv_monitor_remove(handle).await;
                }

                let has_enabled_unfiltered_scanner = scanners
                    .lock()
                    .unwrap()
                    .iter()
                    .any(|(_uuid, scanner)| scanner.is_enabled && scanner.filter.is_none());

                if !gatt_async
                    .msft_adv_monitor_enable(!has_enabled_unfiltered_scanner)
                    .await
                    .map_or(false, |status| status == 0)
                {
                    log::error!("Error updating Advertisement Monitor enable");
                }
            }

            gatt_async.update_scan(scanner_id).await;
        });

        BtStatus::Success
    }

    fn get_scan_suspend_mode(&self) -> SuspendMode {
        self.scan_suspend_mode.clone()
    }

    // Advertising

    fn register_advertiser_callback(
        &mut self,
        callback: Box<dyn IAdvertisingSetCallback + Send>,
    ) -> u32 {
        self.adv_manager.get_impl().register_callback(callback)
    }

    fn unregister_advertiser_callback(&mut self, callback_id: u32) -> bool {
        self.adv_manager.get_impl().unregister_callback(callback_id)
    }

    fn start_advertising_set(
        &mut self,
        parameters: AdvertisingSetParameters,
        advertise_data: AdvertiseData,
        scan_response: Option<AdvertiseData>,
        periodic_parameters: Option<PeriodicAdvertisingParameters>,
        periodic_data: Option<AdvertiseData>,
        duration: i32,
        max_ext_adv_events: i32,
        callback_id: u32,
    ) -> i32 {
        self.adv_manager.get_impl().start_advertising_set(
            parameters,
            advertise_data,
            scan_response,
            periodic_parameters,
            periodic_data,
            duration,
            max_ext_adv_events,
            callback_id,
        )
    }

    fn stop_advertising_set(&mut self, advertiser_id: i32) {
        self.adv_manager.get_impl().stop_advertising_set(advertiser_id)
    }

    fn get_own_address(&mut self, advertiser_id: i32) {
        self.adv_manager.get_impl().get_own_address(advertiser_id);
    }

    fn enable_advertising_set(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        duration: i32,
        max_ext_adv_events: i32,
    ) {
        self.adv_manager.get_impl().enable_advertising_set(
            advertiser_id,
            enable,
            duration,
            max_ext_adv_events,
        );
    }

    fn set_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        self.adv_manager.get_impl().set_advertising_data(advertiser_id, data);
    }

    fn set_raw_adv_data(&mut self, advertiser_id: i32, data: Vec<u8>) {
        self.adv_manager.get_impl().set_raw_adv_data(advertiser_id, data);
    }

    fn set_scan_response_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        self.adv_manager.get_impl().set_scan_response_data(advertiser_id, data);
    }

    fn set_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: AdvertisingSetParameters,
    ) {
        self.adv_manager.get_impl().set_advertising_parameters(advertiser_id, parameters);
    }

    fn set_periodic_advertising_parameters(
        &mut self,
        advertiser_id: i32,
        parameters: PeriodicAdvertisingParameters,
    ) {
        self.adv_manager.get_impl().set_periodic_advertising_parameters(advertiser_id, parameters);
    }

    fn set_periodic_advertising_data(&mut self, advertiser_id: i32, data: AdvertiseData) {
        self.adv_manager.get_impl().set_periodic_advertising_data(advertiser_id, data);
    }

    fn set_periodic_advertising_enable(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        include_adi: bool,
    ) {
        self.adv_manager.get_impl().set_periodic_advertising_enable(
            advertiser_id,
            enable,
            include_adi,
        );
    }

    // GATT Client

    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    ) {
        let uuid = match UuidHelper::parse_string(&app_uuid) {
            Some(id) => id,
            None => {
                log::info!("Uuid is malformed: {}", app_uuid);
                return;
            }
        };
        self.context_map.add(&uuid.uu, callback);
        self.gatt
            .as_ref()
            .expect("GATT has not been initialized")
            .lock()
            .unwrap()
            .client
            .register_client(&uuid, eatt_support);
    }

    fn unregister_client(&mut self, client_id: i32) {
        self.context_map.remove(client_id);
        self.gatt.as_ref().unwrap().lock().unwrap().client.unregister_client(client_id);
    }

    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
        opportunistic: bool,
        phy: LePhy,
    ) {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return,
            Some(addr) => addr,
        };

        self.gatt.as_ref().unwrap().lock().unwrap().client.connect(
            client_id,
            &address,
            // Addr type is default PUBLIC.
            0,
            is_direct,
            transport.into(),
            opportunistic,
            phy.into(),
        );
    }

    fn client_disconnect(&self, client_id: i32, address: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &address);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().lock().unwrap().client.disconnect(
            client_id,
            &RawAddress::from_string(address).unwrap(),
            conn_id.unwrap(),
        );
    }

    fn refresh_device(&self, client_id: i32, addr: String) {
        self.gatt
            .as_ref()
            .unwrap()
            .lock()
            .unwrap()
            .client
            .refresh(client_id, &RawAddress::from_string(addr).unwrap());
    }

    fn discover_services(&self, client_id: i32, addr: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().lock().unwrap().client.search_service(conn_id.unwrap(), None);
    }

    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        let uuid = UuidHelper::parse_string(uuid);
        if uuid.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().lock().unwrap().client.search_service(conn_id.unwrap(), uuid);
    }

    fn btif_gattc_discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        let conn_id = match self.context_map.get_conn_id_from_address(client_id, &addr) {
            None => return,
            Some(id) => id,
        };

        let uuid = match UuidHelper::parse_string(uuid) {
            None => return,
            Some(uuid) => uuid,
        };

        self.gatt
            .as_ref()
            .unwrap()
            .lock()
            .unwrap()
            .client
            .btif_gattc_discover_service_by_uuid(conn_id, &uuid);
    }

    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().lock().unwrap().client.read_characteristic(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
        );
    }

    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        let uuid = UuidHelper::parse_string(uuid);
        if uuid.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().lock().unwrap().client.read_using_characteristic_uuid(
            conn_id.unwrap(),
            &uuid.unwrap(),
            start_handle as u16,
            end_handle as u16,
            auth_req,
        );
    }

    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        mut write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return GattWriteRequestStatus::Fail;
        }

        if self.reliable_queue.contains(&addr) {
            write_type = GattWriteType::WritePrepare;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        // TODO(b/200070162): Handle concurrent write characteristic.

        self.gatt.as_ref().unwrap().lock().unwrap().client.write_characteristic(
            conn_id.unwrap(),
            handle as u16,
            write_type.to_i32().unwrap(),
            auth_req,
            &value,
        );

        return GattWriteRequestStatus::Success;
    }

    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().lock().unwrap().client.read_descriptor(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
        );
    }

    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().lock().unwrap().client.write_descriptor(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
            &value,
        );
    }

    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        if enable {
            self.gatt.as_ref().unwrap().lock().unwrap().client.register_for_notification(
                client_id,
                &RawAddress::from_string(addr).unwrap(),
                handle as u16,
            );
        } else {
            self.gatt.as_ref().unwrap().lock().unwrap().client.deregister_for_notification(
                client_id,
                &RawAddress::from_string(addr).unwrap(),
                handle as u16,
            );
        }
    }

    fn begin_reliable_write(&mut self, _client_id: i32, addr: String) {
        self.reliable_queue.insert(addr);
    }

    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool) {
        self.reliable_queue.remove(&addr);

        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt
            .as_ref()
            .unwrap()
            .lock()
            .unwrap()
            .client
            .execute_write(conn_id.unwrap(), if execute { 1 } else { 0 });
    }

    fn read_remote_rssi(&self, client_id: i32, addr: String) {
        self.gatt
            .as_ref()
            .unwrap()
            .lock()
            .unwrap()
            .client
            .read_remote_rssi(client_id, &RawAddress::from_string(addr).unwrap());
    }

    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().lock().unwrap().client.configure_mtu(conn_id.unwrap(), mtu);
    }

    fn connection_parameter_update(
        &self,
        _client_id: i32,
        addr: String,
        min_interval: i32,
        max_interval: i32,
        latency: i32,
        timeout: i32,
        min_ce_len: u16,
        max_ce_len: u16,
    ) {
        self.gatt.as_ref().unwrap().lock().unwrap().client.conn_parameter_update(
            &RawAddress::from_string(addr).unwrap(),
            min_interval,
            max_interval,
            latency,
            timeout,
            min_ce_len,
            max_ce_len,
        );
    }

    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        address: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &address);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().lock().unwrap().client.set_preferred_phy(
            &RawAddress::from_string(address).unwrap(),
            tx_phy.to_u8().unwrap(),
            rx_phy.to_u8().unwrap(),
            phy_options as u16,
        );
    }

    fn client_read_phy(&mut self, client_id: i32, addr: String) {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return,
            Some(addr) => addr,
        };

        self.gatt.as_ref().unwrap().lock().unwrap().client.read_phy(client_id, &address);
    }

    // GATT Server

    fn register_server(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattServerCallback + Send>,
        eatt_support: bool,
    ) {
        let uuid = match UuidHelper::parse_string(&app_uuid) {
            Some(id) => id,
            None => {
                log::info!("Uuid is malformed: {}", app_uuid);
                return;
            }
        };
        self.server_context_map.add(&uuid.uu, callback);
        self.gatt
            .as_ref()
            .expect("GATT has not been initialized")
            .lock()
            .unwrap()
            .server
            .register_server(&uuid, eatt_support);
    }

    fn unregister_server(&mut self, server_id: i32) {
        self.server_context_map.remove(server_id);
        self.gatt.as_ref().unwrap().lock().unwrap().server.unregister_server(server_id);
    }

    fn server_connect(
        &self,
        server_id: i32,
        addr: String,
        is_direct: bool,
        transport: BtTransport,
    ) -> bool {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return false,
            Some(addr) => addr,
        };

        self.gatt.as_ref().unwrap().lock().unwrap().server.connect(
            server_id,
            &address,
            is_direct,
            transport.into(),
        );

        true
    }

    fn server_disconnect(&self, server_id: i32, addr: String) -> bool {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return false,
            Some(addr) => addr,
        };

        let conn_id = match self.server_context_map.get_conn_id_from_address(server_id, &addr) {
            None => return false,
            Some(id) => id,
        };

        self.gatt.as_ref().unwrap().lock().unwrap().server.disconnect(server_id, &address, conn_id);

        true
    }

    fn add_service(&self, server_id: i32, service: BluetoothGattService) {
        self.gatt
            .as_ref()
            .unwrap()
            .lock()
            .unwrap()
            .server
            .add_service(server_id, &BluetoothGattService::into_db(service));
    }

    fn remove_service(&self, server_id: i32, handle: i32) {
        self.gatt.as_ref().unwrap().lock().unwrap().server.delete_service(server_id, handle);
    }

    fn clear_services(&self, server_id: i32) {
        if let Some(s) = self.server_context_map.get_by_server_id(server_id) {
            for service in &s.services {
                self.gatt
                    .as_ref()
                    .unwrap()
                    .lock()
                    .unwrap()
                    .server
                    .delete_service(server_id, service.instance_id);
            }
        }
    }

    fn send_response(
        &self,
        server_id: i32,
        addr: String,
        request_id: i32,
        status: GattStatus,
        offset: i32,
        value: Vec<u8>,
    ) -> bool {
        (|| {
            let conn_id = self.server_context_map.get_conn_id_from_address(server_id, &addr)?;
            let handle = self.server_context_map.get_request_handle_from_id(request_id)?;
            let len = value.len() as u16;

            let data: [u8; 512] = array_utils::to_sized_array(&value);

            self.gatt.as_ref().unwrap().lock().unwrap().server.send_response(
                conn_id,
                request_id,
                status as i32,
                &BtGattResponse {
                    attr_value: BtGattValue {
                        value: data,
                        handle: handle as u16,
                        offset: offset as u16,
                        len,
                        auth_req: 0 as u8,
                    },
                },
            );

            Some(())
        })()
        .is_some()
    }

    fn send_notification(
        &self,
        server_id: i32,
        addr: String,
        handle: i32,
        confirm: bool,
        value: Vec<u8>,
    ) -> bool {
        let conn_id = match self.server_context_map.get_conn_id_from_address(server_id, &addr) {
            None => return false,
            Some(id) => id,
        };

        self.gatt.as_ref().unwrap().lock().unwrap().server.send_indication(
            server_id,
            handle,
            conn_id,
            confirm as i32,
            value.as_ref(),
        );

        true
    }

    fn server_set_preferred_phy(
        &self,
        _server_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        (|| {
            let address = RawAddress::from_string(addr)?;

            self.gatt.as_ref().unwrap().lock().unwrap().server.set_preferred_phy(
                &address,
                tx_phy.to_u8().unwrap_or_default(),
                rx_phy.to_u8().unwrap_or_default(),
                phy_options as u16,
            );

            Some(())
        })();
    }

    fn server_read_phy(&self, server_id: i32, addr: String) {
        if let Some(address) = RawAddress::from_string(addr.clone()) {
            self.gatt.as_ref().unwrap().lock().unwrap().server.read_phy(server_id, &address);
        }
    }
}

#[btif_callbacks_dispatcher(dispatch_gatt_client_callbacks, GattClientCallbacks)]
pub(crate) trait BtifGattClientCallbacks {
    #[btif_callback(RegisterClient)]
    fn register_client_cb(&mut self, status: GattStatus, client_id: i32, app_uuid: Uuid);

    #[btif_callback(Connect)]
    fn connect_cb(&mut self, conn_id: i32, status: GattStatus, client_id: i32, addr: RawAddress);

    #[btif_callback(Disconnect)]
    fn disconnect_cb(&mut self, conn_id: i32, status: GattStatus, client_id: i32, addr: RawAddress);

    #[btif_callback(SearchComplete)]
    fn search_complete_cb(&mut self, conn_id: i32, status: GattStatus);

    #[btif_callback(RegisterForNotification)]
    fn register_for_notification_cb(
        &mut self,
        conn_id: i32,
        registered: i32,
        status: GattStatus,
        handle: u16,
    );

    #[btif_callback(Notify)]
    fn notify_cb(&mut self, conn_id: i32, data: BtGattNotifyParams);

    #[btif_callback(ReadCharacteristic)]
    fn read_characteristic_cb(&mut self, conn_id: i32, status: GattStatus, data: BtGattReadParams);

    #[btif_callback(WriteCharacteristic)]
    fn write_characteristic_cb(
        &mut self,
        conn_id: i32,
        status: GattStatus,
        handle: u16,
        len: u16,
        value: *const u8,
    );

    #[btif_callback(ReadDescriptor)]
    fn read_descriptor_cb(&mut self, conn_id: i32, status: GattStatus, data: BtGattReadParams);

    #[btif_callback(WriteDescriptor)]
    fn write_descriptor_cb(
        &mut self,
        conn_id: i32,
        status: GattStatus,
        handle: u16,
        len: u16,
        value: *const u8,
    );

    #[btif_callback(ExecuteWrite)]
    fn execute_write_cb(&mut self, conn_id: i32, status: GattStatus);

    #[btif_callback(ReadRemoteRssi)]
    fn read_remote_rssi_cb(
        &mut self,
        client_id: i32,
        addr: RawAddress,
        rssi: i32,
        status: GattStatus,
    );

    #[btif_callback(ConfigureMtu)]
    fn configure_mtu_cb(&mut self, conn_id: i32, status: GattStatus, mtu: i32);

    #[btif_callback(Congestion)]
    fn congestion_cb(&mut self, conn_id: i32, congested: bool);

    #[btif_callback(GetGattDb)]
    fn get_gatt_db_cb(&mut self, conn_id: i32, elements: Vec<BtGattDbElement>, count: i32);

    #[btif_callback(PhyUpdated)]
    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: GattStatus);

    #[btif_callback(ConnUpdated)]
    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: GattStatus,
    );

    #[btif_callback(ServiceChanged)]
    fn service_changed_cb(&mut self, conn_id: i32);

    #[btif_callback(ReadPhy)]
    fn read_phy_cb(
        &mut self,
        client_id: i32,
        addr: RawAddress,
        tx_phy: u8,
        rx_phy: u8,
        status: GattStatus,
    );
}

impl BtifGattClientCallbacks for BluetoothGatt {
    fn register_client_cb(&mut self, status: GattStatus, client_id: i32, app_uuid: Uuid) {
        self.context_map.set_client_id(&app_uuid.uu, client_id);

        let client = self.context_map.get_by_uuid(&app_uuid.uu);
        match client {
            Some(c) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_client_registered(status, client_id);
                        Some(())
                    },
                );
            }
            None => {
                warn!("Warning: Client not registered for UUID {}", app_uuid);
            }
        }
    }

    fn connect_cb(&mut self, conn_id: i32, status: GattStatus, client_id: i32, addr: RawAddress) {
        if status == GattStatus::Success {
            self.context_map.add_connection(client_id, conn_id, &addr.to_string());
        }

        let client = self.context_map.get_by_client_id(client_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_client_connection_state(
                        status,
                        client_id,
                        status == GattStatus::Success,
                        addr.to_string(),
                    );
                    Some(())
                },
            );
        }
    }

    fn disconnect_cb(
        &mut self,
        conn_id: i32,
        status: GattStatus,
        client_id: i32,
        addr: RawAddress,
    ) {
        let client = self.context_map.get_by_client_id(client_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_client_connection_state(status, client_id, false, addr.to_string());
                    Some(())
                },
            );
        }
        self.context_map.remove_connection(client_id, conn_id);
    }

    fn search_complete_cb(&mut self, conn_id: i32, _status: GattStatus) {
        // Gatt DB is ready!
        self.gatt.as_ref().unwrap().lock().unwrap().client.get_gatt_db(conn_id);
    }

    fn register_for_notification_cb(
        &mut self,
        _conn_id: i32,
        _registered: i32,
        _status: GattStatus,
        _handle: u16,
    ) {
        // No-op.
    }

    fn notify_cb(&mut self, conn_id: i32, data: BtGattNotifyParams) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_notify(
                        data.bda.to_string(),
                        data.handle as i32,
                        data.value[0..data.len as usize].to_vec(),
                    );
                    Some(())
                },
            );
        }
    }

    fn read_characteristic_cb(&mut self, conn_id: i32, status: GattStatus, data: BtGattReadParams) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_characteristic_read(
                        address.unwrap().to_string(),
                        status,
                        data.handle as i32,
                        data.value.value[0..data.value.len as usize].to_vec(),
                    );
                    Some(())
                },
            );
        }
    }

    fn write_characteristic_cb(
        &mut self,
        conn_id: i32,
        mut status: GattStatus,
        handle: u16,
        _len: u16,
        _value: *const u8,
    ) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        // TODO(b/200070162): Design how to handle concurrent write characteristic to the same
        // peer.

        let client = self.context_map.get_client_by_conn_id_mut(conn_id);
        if client.is_none() {
            return;
        }

        match (client, address) {
            (Some(c), Some(addr)) => {
                if c.is_congested {
                    if status == GattStatus::Congested {
                        status = GattStatus::Success;
                    }

                    c.congestion_queue.push((addr.to_string(), status, handle as i32));
                    return;
                }

                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_characteristic_write(addr.to_string(), status, handle as i32);
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }

    fn read_descriptor_cb(&mut self, conn_id: i32, status: GattStatus, data: BtGattReadParams) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_descriptor_read(
                        address.unwrap().to_string(),
                        status,
                        data.handle as i32,
                        data.value.value[0..data.value.len as usize].to_vec(),
                    );
                    Some(())
                },
            );
        }
    }

    fn write_descriptor_cb(
        &mut self,
        conn_id: i32,
        status: GattStatus,
        handle: u16,
        _len: u16,
        _value: *const u8,
    ) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_descriptor_write(address.unwrap().to_string(), status, handle as i32);
                    Some(())
                },
            );
        }
    }

    fn execute_write_cb(&mut self, conn_id: i32, status: GattStatus) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_execute_write(address.unwrap().to_string(), status);
                    Some(())
                },
            );
        }
    }

    fn read_remote_rssi_cb(
        &mut self,
        client_id: i32,
        addr: RawAddress,
        rssi: i32,
        status: GattStatus,
    ) {
        let client = self.context_map.get_by_client_id(client_id);
        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_read_remote_rssi(addr.to_string(), rssi, status);
                    Some(())
                },
            );
        }
    }

    fn configure_mtu_cb(&mut self, conn_id: i32, status: GattStatus, mtu: i32) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        let addr = self.context_map.get_address_by_conn_id(conn_id);

        match (client, addr) {
            (Some(c), Some(addr)) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_configure_mtu(addr, mtu, status);
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }

    fn congestion_cb(&mut self, conn_id: i32, congested: bool) {
        if let Some(client) = self.context_map.get_client_by_conn_id_mut(conn_id) {
            client.is_congested = congested;
            if !client.is_congested {
                let cbid = client.cbid;
                let mut congestion_queue: Vec<(String, GattStatus, i32)> = vec![];
                client.congestion_queue.retain(|v| {
                    congestion_queue.push(v.clone());
                    false
                });

                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        for callback in congestion_queue.iter() {
                            cb.on_characteristic_write(callback.0.clone(), callback.1, callback.2);
                        }
                        Some(())
                    },
                );
            }
        }
    }

    fn get_gatt_db_cb(&mut self, conn_id: i32, elements: Vec<BtGattDbElement>, _count: i32) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        match (client, address) {
            (Some(c), Some(addr)) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_search_complete(
                            addr.to_string(),
                            BluetoothGattService::from_db(elements),
                            GattStatus::Success,
                        );
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }

    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: GattStatus) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }
        match (client, address) {
            (Some(c), Some(addr)) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_phy_update(
                            addr,
                            LePhy::from_u8(tx_phy).unwrap(),
                            LePhy::from_u8(rx_phy).unwrap(),
                            status,
                        );
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }

    fn read_phy_cb(
        &mut self,
        client_id: i32,
        addr: RawAddress,
        tx_phy: u8,
        rx_phy: u8,
        status: GattStatus,
    ) {
        let client = self.context_map.get_by_client_id(client_id);
        if client.is_none() {
            return;
        }

        if let Some(c) = client {
            let cbid = c.cbid;
            self.context_map.get_callback_from_callback_id(cbid).and_then(
                |cb: &mut GattClientCallback| {
                    cb.on_phy_read(
                        addr.to_string(),
                        LePhy::from_u8(tx_phy).unwrap(),
                        LePhy::from_u8(rx_phy).unwrap(),
                        status,
                    );
                    Some(())
                },
            );
        }
    }

    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: GattStatus,
    ) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        match (client, address) {
            (Some(c), Some(addr)) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_connection_updated(
                            addr,
                            interval as i32,
                            latency as i32,
                            timeout as i32,
                            status,
                        );
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }

    fn service_changed_cb(&mut self, conn_id: i32) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        match (client, address) {
            (Some(c), Some(addr)) => {
                let cbid = c.cbid;
                self.context_map.get_callback_from_callback_id(cbid).and_then(
                    |cb: &mut GattClientCallback| {
                        cb.on_service_changed(addr);
                        Some(())
                    },
                );
            }
            _ => (),
        };
    }
}

#[btif_callbacks_dispatcher(dispatch_gatt_server_callbacks, GattServerCallbacks)]
pub(crate) trait BtifGattServerCallbacks {
    #[btif_callback(RegisterServer)]
    fn register_server_cb(&mut self, status: GattStatus, server_id: i32, app_uuid: Uuid);

    #[btif_callback(Connection)]
    fn connection_cb(&mut self, conn_id: i32, server_id: i32, connected: i32, addr: RawAddress);

    #[btif_callback(ServiceAdded)]
    fn service_added_cb(
        &mut self,
        status: GattStatus,
        server_id: i32,
        elements: Vec<BtGattDbElement>,
        _count: usize,
    );

    #[btif_callback(ServiceDeleted)]
    fn service_deleted_cb(&mut self, status: GattStatus, server_id: i32, handle: i32);

    #[btif_callback(RequestReadCharacteristic)]
    fn request_read_characteristic_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        is_long: bool,
    );

    #[btif_callback(RequestReadDescriptor)]
    fn request_read_descriptor_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        is_long: bool,
    );

    #[btif_callback(RequestWriteCharacteristic)]
    fn request_write_characteristic_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        need_rsp: bool,
        is_prep: bool,
        data: Vec<u8>,
        len: usize,
    );

    #[btif_callback(RequestWriteDescriptor)]
    fn request_write_descriptor_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        need_rsp: bool,
        is_prep: bool,
        data: Vec<u8>,
        len: usize,
    );

    #[btif_callback(RequestExecWrite)]
    fn request_exec_write_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        exec_write: i32,
    );

    #[btif_callback(IndicationSent)]
    fn indication_sent_cb(&mut self, conn_id: i32, status: GattStatus);

    #[btif_callback(Congestion)]
    fn congestion_cb(&mut self, conn_id: i32, congested: bool);

    #[btif_callback(MtuChanged)]
    fn mtu_changed_cb(&mut self, conn_id: i32, mtu: i32);

    #[btif_callback(PhyUpdated)]
    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: GattStatus);

    #[btif_callback(ReadPhy)]
    fn read_phy_cb(
        &mut self,
        server_id: i32,
        addr: RawAddress,
        tx_phy: u8,
        rx_phy: u8,
        status: GattStatus,
    );

    #[btif_callback(ConnUpdated)]
    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: GattStatus,
    );

    #[btif_callback(SubrateChanged)]
    fn subrate_chg_cb(
        &mut self,
        conn_id: i32,
        subrate_factor: u16,
        latency: u16,
        cont_num: u16,
        timeout: u16,
        status: GattStatus,
    );
}

impl BtifGattServerCallbacks for BluetoothGatt {
    fn register_server_cb(&mut self, status: GattStatus, server_id: i32, app_uuid: Uuid) {
        self.server_context_map.set_server_id(&app_uuid.uu, server_id);

        let cbid = self.server_context_map.get_by_uuid(&app_uuid.uu).map(|server| server.cbid);
        match cbid {
            Some(cbid) => {
                if let Some(cb) =
                    self.server_context_map.get_callback_from_callback_id(cbid).as_mut()
                {
                    cb.on_server_registered(status, server_id)
                }
            }
            None => {
                warn!("Warning: No callback found for UUID {}", app_uuid);
            }
        }
    }

    fn connection_cb(&mut self, conn_id: i32, server_id: i32, connected: i32, addr: RawAddress) {
        let is_connected = connected != 0;
        if is_connected {
            self.server_context_map.add_connection(server_id, conn_id, &addr.to_string());
        } else {
            self.server_context_map.remove_connection(conn_id);
        }

        let cbid = self.server_context_map.get_by_server_id(server_id).map(|server| server.cbid);
        match cbid {
            Some(cbid) => {
                if let Some(cb) =
                    self.server_context_map.get_callback_from_callback_id(cbid).as_mut()
                {
                    cb.on_server_connection_state(server_id, is_connected, addr.to_string());
                }
            }
            None => {
                warn!("Warning: No callback found for server ID {}", server_id);
            }
        }
    }

    fn service_added_cb(
        &mut self,
        status: GattStatus,
        server_id: i32,
        elements: Vec<BtGattDbElement>,
        _count: usize,
    ) {
        for service in BluetoothGattService::from_db(elements) {
            if status == GattStatus::Success {
                self.server_context_map.add_service(server_id, service.clone());
            }

            let cbid =
                self.server_context_map.get_by_server_id(server_id).map(|server| server.cbid);
            match cbid {
                Some(cbid) => {
                    if let Some(cb) =
                        self.server_context_map.get_callback_from_callback_id(cbid).as_mut()
                    {
                        cb.on_service_added(status, service);
                    }
                }
                None => {
                    warn!("Warning: No callback found for server ID {}", server_id);
                }
            }
        }
    }

    fn service_deleted_cb(&mut self, status: GattStatus, server_id: i32, handle: i32) {
        if status == GattStatus::Success {
            self.server_context_map.delete_service(server_id, handle);
        }

        let cbid = self
            .server_context_map
            .get_by_server_id(server_id)
            .and_then(|server| Some(server.cbid));

        if let Some(cbid) = cbid {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_service_removed(status, handle);
            }
        }
    }

    fn request_read_characteristic_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        is_long: bool,
    ) {
        self.server_context_map.add_request(trans_id, handle);

        if let Some(cbid) =
            self.server_context_map.get_by_conn_id(conn_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_characteristic_read_request(
                    addr.to_string(),
                    trans_id,
                    offset,
                    is_long,
                    handle,
                );
            }
        }
    }

    fn request_read_descriptor_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        is_long: bool,
    ) {
        self.server_context_map.add_request(trans_id, handle);

        if let Some(cbid) =
            self.server_context_map.get_by_conn_id(conn_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_descriptor_read_request(addr.to_string(), trans_id, offset, is_long, handle);
            }
        }
    }

    fn request_write_characteristic_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        need_rsp: bool,
        is_prep: bool,
        data: Vec<u8>,
        len: usize,
    ) {
        self.server_context_map.add_request(trans_id, handle);

        if let Some(cbid) =
            self.server_context_map.get_by_conn_id(conn_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_characteristic_write_request(
                    addr.to_string(),
                    trans_id,
                    offset,
                    len as i32,
                    is_prep,
                    need_rsp,
                    handle,
                    data,
                );
            }
        }
    }

    fn request_write_descriptor_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        handle: i32,
        offset: i32,
        need_rsp: bool,
        is_prep: bool,
        data: Vec<u8>,
        len: usize,
    ) {
        self.server_context_map.add_request(trans_id, handle);

        if let Some(cbid) =
            self.server_context_map.get_by_conn_id(conn_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_descriptor_write_request(
                    addr.to_string(),
                    trans_id,
                    offset,
                    len as i32,
                    is_prep,
                    need_rsp,
                    handle,
                    data,
                );
            }
        }
    }

    fn request_exec_write_cb(
        &mut self,
        conn_id: i32,
        trans_id: i32,
        addr: RawAddress,
        exec_write: i32,
    ) {
        self.server_context_map.add_request(trans_id, 0);

        if let Some(cbid) =
            self.server_context_map.get_by_conn_id(conn_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_execute_write(addr.to_string(), trans_id, exec_write != 0);
            }
        }
    }

    fn indication_sent_cb(&mut self, conn_id: i32, mut status: GattStatus) {
        (|| {
            let address = self.server_context_map.get_address_from_conn_id(conn_id)?;
            let server = self.server_context_map.get_mut_by_conn_id(conn_id)?;

            if server.is_congested {
                if status == GattStatus::Congested {
                    status = GattStatus::Success;
                }

                server.congestion_queue.push((address, status));
                return None;
            }

            let cbid = server.cbid;
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_notification_sent(address.to_string(), status);
            }

            Some(())
        })();
    }

    fn congestion_cb(&mut self, conn_id: i32, congested: bool) {
        if let Some(server) = self.server_context_map.get_mut_by_conn_id(conn_id) {
            server.is_congested = congested;
            if !server.is_congested {
                let cbid = server.cbid;
                let congestion_queue: Vec<_> = server.congestion_queue.drain(..).collect();

                if let Some(cb) =
                    self.server_context_map.get_callback_from_callback_id(cbid).as_mut()
                {
                    for callback in congestion_queue {
                        cb.on_notification_sent(callback.0.clone(), callback.1);
                    }
                }
            }
        }
    }

    fn mtu_changed_cb(&mut self, conn_id: i32, mtu: i32) {
        (|| {
            let address = self.server_context_map.get_address_from_conn_id(conn_id)?;
            let server_cbid = self.server_context_map.get_by_conn_id(conn_id)?.cbid;

            if let Some(cb) =
                self.server_context_map.get_callback_from_callback_id(server_cbid).as_mut()
            {
                cb.on_mtu_changed(address, mtu);
            }

            Some(())
        })();
    }

    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: GattStatus) {
        (|| {
            let address = self.server_context_map.get_address_from_conn_id(conn_id)?;
            let server_cbid = self.server_context_map.get_by_conn_id(conn_id)?.cbid;

            if let Some(cb) =
                self.server_context_map.get_callback_from_callback_id(server_cbid).as_mut()
            {
                cb.on_phy_update(
                    address,
                    LePhy::from_u8(tx_phy).unwrap_or_default(),
                    LePhy::from_u8(rx_phy).unwrap_or_default(),
                    status,
                );
            }

            Some(())
        })();
    }

    fn read_phy_cb(
        &mut self,
        server_id: i32,
        addr: RawAddress,
        tx_phy: u8,
        rx_phy: u8,
        status: GattStatus,
    ) {
        if let Some(cbid) =
            self.server_context_map.get_by_server_id(server_id).map(|server| server.cbid)
        {
            if let Some(cb) = self.server_context_map.get_callback_from_callback_id(cbid).as_mut() {
                cb.on_phy_read(
                    addr.to_string(),
                    LePhy::from_u8(tx_phy).unwrap_or_default(),
                    LePhy::from_u8(rx_phy).unwrap_or_default(),
                    status,
                );
            }
        }
    }

    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: GattStatus,
    ) {
        (|| {
            let address = self.server_context_map.get_address_from_conn_id(conn_id)?;
            let server_cbid = self.server_context_map.get_by_conn_id(conn_id)?.cbid;

            if let Some(cb) =
                self.server_context_map.get_callback_from_callback_id(server_cbid).as_mut()
            {
                cb.on_connection_updated(
                    address,
                    interval as i32,
                    latency as i32,
                    timeout as i32,
                    status,
                );
            }

            Some(())
        })();
    }

    fn subrate_chg_cb(
        &mut self,
        conn_id: i32,
        subrate_factor: u16,
        latency: u16,
        cont_num: u16,
        timeout: u16,
        status: GattStatus,
    ) {
        (|| {
            let address = self.server_context_map.get_address_from_conn_id(conn_id)?;
            let server_cbid = self.server_context_map.get_by_conn_id(conn_id)?.cbid;

            if let Some(cb) =
                self.server_context_map.get_callback_from_callback_id(server_cbid).as_mut()
            {
                cb.on_subrate_change(
                    address,
                    subrate_factor as i32,
                    latency as i32,
                    cont_num as i32,
                    timeout as i32,
                    status,
                );
            }

            Some(())
        })();
    }
}

#[btif_callbacks_dispatcher(dispatch_le_scanner_callbacks, GattScannerCallbacks)]
pub(crate) trait BtifGattScannerCallbacks {
    #[btif_callback(OnScannerRegistered)]
    fn on_scanner_registered(&mut self, uuid: Uuid, scanner_id: u8, status: GattStatus);

    #[btif_callback(OnScanResult)]
    fn on_scan_result(
        &mut self,
        event_type: u16,
        addr_type: u8,
        bda: RawAddress,
        primary_phy: u8,
        secondary_phy: u8,
        advertising_sid: u8,
        tx_power: i8,
        rssi: i8,
        periodic_adv_int: u16,
        adv_data: Vec<u8>,
    );

    #[btif_callback(OnTrackAdvFoundLost)]
    fn on_track_adv_found_lost(&mut self, adv_track_info: RustAdvertisingTrackInfo);
}

#[btif_callbacks_dispatcher(dispatch_le_scanner_inband_callbacks, GattScannerInbandCallbacks)]
pub(crate) trait BtifGattScannerInbandCallbacks {
    #[btif_callback(RegisterCallback)]
    fn inband_register_callback(&mut self, app_uuid: Uuid, scanner_id: u8, btm_status: u8);

    #[btif_callback(StatusCallback)]
    fn inband_status_callback(&mut self, scanner_id: u8, btm_status: u8);

    #[btif_callback(EnableCallback)]
    fn inband_enable_callback(&mut self, action: u8, btm_status: u8);

    #[btif_callback(FilterParamSetupCallback)]
    fn inband_filter_param_setup_callback(
        &mut self,
        scanner_id: u8,
        available_space: u8,
        action_type: u8,
        btm_status: u8,
    );

    #[btif_callback(FilterConfigCallback)]
    fn inband_filter_config_callback(
        &mut self,
        filter_index: u8,
        filter_type: u8,
        available_space: u8,
        action: u8,
        btm_status: u8,
    );

    #[btif_callback(MsftAdvMonitorAddCallback)]
    fn inband_msft_adv_monitor_add_callback(
        &mut self,
        call_id: u32,
        monitor_handle: u8,
        status: u8,
    );

    #[btif_callback(MsftAdvMonitorRemoveCallback)]
    fn inband_msft_adv_monitor_remove_callback(&mut self, call_id: u32, status: u8);

    #[btif_callback(MsftAdvMonitorEnableCallback)]
    fn inband_msft_adv_monitor_enable_callback(&mut self, call_id: u32, status: u8);

    #[btif_callback(StartSyncCallback)]
    fn inband_start_sync_callback(
        &mut self,
        status: u8,
        sync_handle: u16,
        advertising_sid: u8,
        address_type: u8,
        address: RawAddress,
        phy: u8,
        interval: u16,
    );

    #[btif_callback(SyncReportCallback)]
    fn inband_sync_report_callback(
        &mut self,
        sync_handle: u16,
        tx_power: i8,
        rssi: i8,
        status: u8,
        data: Vec<u8>,
    );

    #[btif_callback(SyncLostCallback)]
    fn inband_sync_lost_callback(&mut self, sync_handle: u16);

    #[btif_callback(SyncTransferCallback)]
    fn inband_sync_transfer_callback(&mut self, status: u8, address: RawAddress);
}

impl BtifGattScannerInbandCallbacks for BluetoothGatt {
    fn inband_register_callback(&mut self, app_uuid: Uuid, scanner_id: u8, btm_status: u8) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::RegisterCallback(app_uuid, scanner_id, btm_status)
        );
    }

    fn inband_status_callback(&mut self, scanner_id: u8, btm_status: u8) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::StatusCallback(scanner_id, btm_status)
        );
    }

    fn inband_enable_callback(&mut self, action: u8, btm_status: u8) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::EnableCallback(action, btm_status)
        );
    }

    fn inband_filter_param_setup_callback(
        &mut self,
        scanner_id: u8,
        available_space: u8,
        action_type: u8,
        btm_status: u8,
    ) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::FilterParamSetupCallback(
                scanner_id,
                available_space,
                action_type,
                btm_status
            )
        );
    }

    fn inband_filter_config_callback(
        &mut self,
        filter_index: u8,
        filter_type: u8,
        available_space: u8,
        action: u8,
        btm_status: u8,
    ) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::FilterConfigCallback(
                filter_index,
                filter_type,
                available_space,
                action,
                btm_status,
            )
        );
    }

    fn inband_msft_adv_monitor_add_callback(
        &mut self,
        call_id: u32,
        monitor_handle: u8,
        status: u8,
    ) {
        (self.adv_mon_add_cb_sender.lock().unwrap())(call_id, (monitor_handle, status));
    }

    fn inband_msft_adv_monitor_remove_callback(&mut self, call_id: u32, status: u8) {
        (self.adv_mon_remove_cb_sender.lock().unwrap())(call_id, status);
    }

    fn inband_msft_adv_monitor_enable_callback(&mut self, call_id: u32, status: u8) {
        (self.adv_mon_enable_cb_sender.lock().unwrap())(call_id, status);
    }

    fn inband_start_sync_callback(
        &mut self,
        status: u8,
        sync_handle: u16,
        advertising_sid: u8,
        address_type: u8,
        address: RawAddress,
        phy: u8,
        interval: u16,
    ) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::StartSyncCallback(
                status,
                sync_handle,
                advertising_sid,
                address_type,
                address,
                phy,
                interval,
            )
        );
    }

    fn inband_sync_report_callback(
        &mut self,
        sync_handle: u16,
        tx_power: i8,
        rssi: i8,
        status: u8,
        data: Vec<u8>,
    ) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::SyncReportCallback(
                sync_handle,
                tx_power,
                rssi,
                status,
                data
            )
        );
    }

    fn inband_sync_lost_callback(&mut self, sync_handle: u16) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::SyncLostCallback(sync_handle,)
        );
    }

    fn inband_sync_transfer_callback(&mut self, status: u8, address: RawAddress) {
        log::debug!(
            "Callback received: {:#?}",
            GattScannerInbandCallbacks::SyncTransferCallback(status, address)
        );
    }
}

impl BtifGattScannerCallbacks for BluetoothGatt {
    fn on_scanner_registered(&mut self, uuid: Uuid, scanner_id: u8, status: GattStatus) {
        log::debug!(
            "on_scanner_registered UUID = {}, scanner_id = {}, status = {}",
            uuid,
            scanner_id,
            status
        );

        let mut scanners_lock = self.scanners.lock().unwrap();
        let scanner_info = scanners_lock.get_mut(&uuid);

        if let Some(info) = scanner_info {
            info.scanner_id = Some(scanner_id);
            if let Some(cb) = self.scanner_callbacks.get_by_id_mut(info.callback_id) {
                cb.on_scanner_registered(uuid.uu, scanner_id, status);
            } else {
                log::warn!("There is no callback for scanner UUID {}", uuid);
            }
        } else {
            log::warn!(
                "Scanner registered callback for non-existent scanner info, UUID = {}",
                uuid
            );
        }

        if status != GattStatus::Success {
            log::error!("Error registering scanner UUID {}", uuid);
            scanners_lock.remove(&uuid);
        }
    }

    fn on_scan_result(
        &mut self,
        event_type: u16,
        addr_type: u8,
        address: RawAddress,
        primary_phy: u8,
        secondary_phy: u8,
        advertising_sid: u8,
        tx_power: i8,
        rssi: i8,
        periodic_adv_int: u16,
        adv_data: Vec<u8>,
    ) {
        self.scanner_callbacks.for_all_callbacks(|callback| {
            callback.on_scan_result(ScanResult {
                name: adv_parser::extract_name(adv_data.as_slice()),
                address: address.to_string(),
                addr_type,
                event_type,
                primary_phy,
                secondary_phy,
                advertising_sid,
                tx_power,
                rssi,
                periodic_adv_int,
                flags: adv_parser::extract_flags(adv_data.as_slice()),
                service_uuids: adv_parser::extract_service_uuids(adv_data.as_slice()),
                service_data: adv_parser::extract_service_data(adv_data.as_slice()),
                manufacturer_data: adv_parser::extract_manufacturer_data(adv_data.as_slice()),
                adv_data: adv_data.clone(),
            });
        });
    }

    fn on_track_adv_found_lost(&mut self, track_adv_info: RustAdvertisingTrackInfo) {
        let scanner_id = match self.scanners.lock().unwrap().values().find_map(|scanner| {
            scanner.monitor_handle.and_then(|handle| {
                (handle == track_adv_info.monitor_handle).then(|| scanner.scanner_id).flatten()
            })
        }) {
            Some(scanner_id) => scanner_id,
            None => {
                log::warn!("No scanner id having monitor handle {}", track_adv_info.monitor_handle);
                return;
            }
        };

        self.scanner_callbacks.for_all_callbacks(|callback| {
            let adv_data =
                [&track_adv_info.adv_packet[..], &track_adv_info.scan_response[..]].concat();

            let scan_result = ScanResult {
                name: adv_parser::extract_name(adv_data.as_slice()),
                address: track_adv_info.advertiser_address.to_string(),
                addr_type: track_adv_info.advertiser_address_type,
                event_type: 0, /* not used */
                primary_phy: LePhy::Phy1m as u8,
                secondary_phy: 0,      /* not used */
                advertising_sid: 0xff, /* not present */
                /* A bug in libbluetooth that uses u8 for TX power.
                 * TODO(b/261482382): Fix the data type in C++ layer to use i8 instead of u8. */
                tx_power: track_adv_info.tx_power as i8,
                rssi: track_adv_info.rssi,
                periodic_adv_int: 0, /* not used */
                flags: adv_parser::extract_flags(adv_data.as_slice()),
                service_uuids: adv_parser::extract_service_uuids(adv_data.as_slice()),
                service_data: adv_parser::extract_service_data(adv_data.as_slice()),
                manufacturer_data: adv_parser::extract_manufacturer_data(adv_data.as_slice()),
                adv_data,
            };

            if track_adv_info.advertiser_state == 0x01 {
                callback.on_advertisement_found(scanner_id, scan_result);
            } else {
                callback.on_advertisement_lost(scanner_id, scan_result);
            }
        });
    }
}

impl BtifGattAdvCallbacks for BluetoothGatt {
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    ) {
        self.adv_manager.get_impl().on_advertising_set_started(
            reg_id,
            advertiser_id,
            tx_power,
            status,
        );
    }

    fn on_advertising_enabled(&mut self, adv_id: u8, enabled: bool, status: AdvertisingStatus) {
        self.adv_manager.get_impl().on_advertising_enabled(adv_id, enabled, status);
    }

    fn on_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        self.adv_manager.get_impl().on_advertising_data_set(adv_id, status);
    }

    fn on_scan_response_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        self.adv_manager.get_impl().on_scan_response_data_set(adv_id, status);
    }

    fn on_advertising_parameters_updated(
        &mut self,
        adv_id: u8,
        tx_power: i8,
        status: AdvertisingStatus,
    ) {
        self.adv_manager.get_impl().on_advertising_parameters_updated(adv_id, tx_power, status);
    }

    fn on_periodic_advertising_parameters_updated(
        &mut self,
        adv_id: u8,
        status: AdvertisingStatus,
    ) {
        self.adv_manager.get_impl().on_periodic_advertising_parameters_updated(adv_id, status);
    }

    fn on_periodic_advertising_data_set(&mut self, adv_id: u8, status: AdvertisingStatus) {
        self.adv_manager.get_impl().on_periodic_advertising_data_set(adv_id, status);
    }

    fn on_periodic_advertising_enabled(
        &mut self,
        adv_id: u8,
        enabled: bool,
        status: AdvertisingStatus,
    ) {
        self.adv_manager.get_impl().on_periodic_advertising_enabled(adv_id, enabled, status);
    }

    fn on_own_address_read(&mut self, adv_id: u8, addr_type: u8, address: RawAddress) {
        self.adv_manager.get_impl().on_own_address_read(adv_id, addr_type, address);
    }
}

#[cfg(test)]
mod tests {
    struct TestBluetoothGattCallback {
        id: String,
    }

    impl TestBluetoothGattCallback {
        fn new(id: String) -> TestBluetoothGattCallback {
            TestBluetoothGattCallback { id }
        }
    }

    impl IBluetoothGattCallback for TestBluetoothGattCallback {
        fn on_client_registered(&mut self, _status: GattStatus, _client_id: i32) {}
        fn on_client_connection_state(
            &mut self,
            _status: GattStatus,
            _client_id: i32,
            _connected: bool,
            _addr: String,
        ) {
        }

        fn on_phy_update(
            &mut self,
            _addr: String,
            _tx_phy: LePhy,
            _rx_phy: LePhy,
            _status: GattStatus,
        ) {
        }

        fn on_phy_read(
            &mut self,
            _addr: String,
            _tx_phy: LePhy,
            _rx_phy: LePhy,
            _status: GattStatus,
        ) {
        }

        fn on_search_complete(
            &mut self,
            _addr: String,
            _services: Vec<BluetoothGattService>,
            _status: GattStatus,
        ) {
        }

        fn on_characteristic_read(
            &mut self,
            _addr: String,
            _status: GattStatus,
            _handle: i32,
            _value: Vec<u8>,
        ) {
        }

        fn on_characteristic_write(&mut self, _addr: String, _status: GattStatus, _handle: i32) {}

        fn on_execute_write(&mut self, _addr: String, _status: GattStatus) {}

        fn on_descriptor_read(
            &mut self,
            _addr: String,
            _status: GattStatus,
            _handle: i32,
            _value: Vec<u8>,
        ) {
        }

        fn on_descriptor_write(&mut self, _addr: String, _status: GattStatus, _handle: i32) {}

        fn on_notify(&mut self, _addr: String, _handle: i32, _value: Vec<u8>) {}

        fn on_read_remote_rssi(&mut self, _addr: String, _rssi: i32, _status: GattStatus) {}

        fn on_configure_mtu(&mut self, _addr: String, _mtu: i32, _status: GattStatus) {}

        fn on_connection_updated(
            &mut self,
            _addr: String,
            _interval: i32,
            _latency: i32,
            _timeout: i32,
            _status: GattStatus,
        ) {
        }

        fn on_service_changed(&mut self, _addr: String) {}
    }

    impl RPCProxy for TestBluetoothGattCallback {
        fn get_object_id(&self) -> String {
            self.id.clone()
        }
    }

    use super::*;

    #[test]
    fn test_uuid_from_string() {
        let uuid = UuidHelper::parse_string("abcdef");
        assert!(uuid.is_none());

        let uuid = UuidHelper::parse_string("0123456789abcdef0123456789abcdef");
        assert!(uuid.is_some());
        let expected: [u8; 16] = [
            0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef, 0x01, 0x23, 0x45, 0x67, 0x89, 0xab,
            0xcd, 0xef,
        ];
        assert_eq!(Uuid::from(expected), uuid.unwrap());
    }

    #[test]
    fn test_context_map_clients() {
        let (tx, _rx) = crate::Stack::create_channel();
        let mut map = ContextMap::new(tx.clone());

        // Add client 1.
        let callback1 = Box::new(TestBluetoothGattCallback::new(String::from("Callback 1")));
        let uuid1 = UuidHelper::parse_string("00000000000000000000000000000001").unwrap().uu;
        map.add(&uuid1, callback1);
        let found = map.get_by_uuid(&uuid1);
        assert!(found.is_some());
        assert_eq!(
            "Callback 1",
            match found {
                Some(c) => {
                    let cbid = c.cbid;
                    map.callbacks
                        .get_by_id(cbid)
                        .and_then(|cb| Some(cb.get_object_id()))
                        .unwrap_or(String::new())
                }
                None => String::new(),
            }
        );

        // Add client 2.
        let callback2 = Box::new(TestBluetoothGattCallback::new(String::from("Callback 2")));
        let uuid2 = UuidHelper::parse_string("00000000000000000000000000000002").unwrap().uu;
        map.add(&uuid2, callback2);
        let found = map.get_by_uuid(&uuid2);
        assert!(found.is_some());
        assert_eq!(
            "Callback 2",
            match found {
                Some(c) => {
                    let cbid = c.cbid;
                    map.callbacks
                        .get_by_id(cbid)
                        .and_then(|cb| Some(cb.get_object_id()))
                        .unwrap_or(String::new())
                }
                None => String::new(),
            }
        );

        // Set client ID and get by client ID.
        map.set_client_id(&uuid1, 3);
        let found = map.get_by_client_id(3);
        assert!(found.is_some());

        // Remove client 1.
        map.remove(3);
        let found = map.get_by_uuid(&uuid1);
        assert!(found.is_none());
    }

    #[test]
    fn test_context_map_connections() {
        let (tx, _rx) = crate::Stack::create_channel();
        let mut map = ContextMap::new(tx.clone());
        let client_id = 1;

        map.add_connection(client_id, 3, &String::from("aa:bb:cc:dd:ee:ff"));
        map.add_connection(client_id, 4, &String::from("11:22:33:44:55:66"));

        let found = map.get_conn_id_from_address(client_id, &String::from("aa:bb:cc:dd:ee:ff"));
        assert!(found.is_some());
        assert_eq!(3, found.unwrap());

        let found = map.get_conn_id_from_address(client_id, &String::from("11:22:33:44:55:66"));
        assert!(found.is_some());
        assert_eq!(4, found.unwrap());
    }
}
