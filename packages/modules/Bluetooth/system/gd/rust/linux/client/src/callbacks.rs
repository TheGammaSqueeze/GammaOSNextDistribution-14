use crate::command_handler::SocketSchedule;
use crate::dbus_iface::{
    export_admin_policy_callback_dbus_intf, export_advertising_set_callback_dbus_intf,
    export_battery_manager_callback_dbus_intf, export_bluetooth_callback_dbus_intf,
    export_bluetooth_connection_callback_dbus_intf, export_bluetooth_gatt_callback_dbus_intf,
    export_bluetooth_manager_callback_dbus_intf, export_bluetooth_media_callback_dbus_intf,
    export_bluetooth_telephony_callback_dbus_intf, export_gatt_server_callback_dbus_intf,
    export_qa_callback_dbus_intf, export_scanner_callback_dbus_intf,
    export_socket_callback_dbus_intf, export_suspend_callback_dbus_intf,
};
use crate::ClientContext;
use crate::{console_red, console_yellow, print_error, print_info};
use bt_topshim::btif::{BtBondState, BtPropertyType, BtSspVariant, BtStatus, Uuid128Bit};
use bt_topshim::profiles::gatt::{AdvertisingStatus, GattStatus, LePhy};
use bt_topshim::profiles::hfp::HfpCodecId;
use bt_topshim::profiles::sdp::BtSdpRecord;
use btstack::battery_manager::{BatterySet, IBatteryManagerCallback};
use btstack::bluetooth::{
    BluetoothDevice, IBluetooth, IBluetoothCallback, IBluetoothConnectionCallback,
};
use btstack::bluetooth_admin::{IBluetoothAdminPolicyCallback, PolicyEffect};
use btstack::bluetooth_adv::IAdvertisingSetCallback;
use btstack::bluetooth_gatt::{
    BluetoothGattService, IBluetoothGattCallback, IBluetoothGattServerCallback, IScannerCallback,
    ScanResult,
};
use btstack::bluetooth_media::{
    BluetoothAudioDevice, IBluetoothMediaCallback, IBluetoothTelephonyCallback,
};
use btstack::bluetooth_qa::IBluetoothQACallback;
use btstack::socket_manager::{
    BluetoothServerSocket, BluetoothSocket, IBluetoothSocketManager,
    IBluetoothSocketManagerCallbacks, SocketId,
};
use btstack::suspend::ISuspendCallback;
use btstack::uuid::UuidWrapper;
use btstack::{RPCProxy, SuspendMode};
use chrono::{TimeZone, Utc};
use dbus::nonblock::SyncConnection;
use dbus_crossroads::Crossroads;
use dbus_projection::DisconnectWatcher;
use manager_service::iface_bluetooth_manager::IBluetoothManagerCallback;
use std::convert::TryFrom;
use std::io::{Read, Write};
use std::sync::{Arc, Mutex};
use std::time::Duration;

const SOCKET_TEST_WRITE: &[u8] =
    b"01234567890123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

// Avoid 32, 40, 64 consecutive hex characters so CrOS feedback redact tool
// doesn't trim our dump.
const BINARY_PACKET_STATUS_WRAP: usize = 50;

/// Callback context for manager interface callbacks.
pub(crate) struct BtManagerCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtManagerCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothManagerCallback for BtManagerCallback {
    fn on_hci_device_changed(&mut self, hci_interface: i32, present: bool) {
        print_info!("hci{} present = {}", hci_interface, present);

        if present {
            self.context.lock().unwrap().adapters.entry(hci_interface).or_insert(false);
        } else {
            self.context.lock().unwrap().adapters.remove(&hci_interface);
        }
    }

    fn on_hci_enabled_changed(&mut self, hci_interface: i32, enabled: bool) {
        self.context.lock().unwrap().set_adapter_enabled(hci_interface, enabled);
    }

    fn on_default_adapter_changed(&mut self, hci_interface: i32) {
        print_info!("hci{} is now the default", hci_interface);
    }
}

impl RPCProxy for BtManagerCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_manager_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

/// Callback container for adapter interface callbacks.
pub(crate) struct BtCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothCallback for BtCallback {
    fn on_adapter_property_changed(&mut self, _prop: BtPropertyType) {}

    fn on_device_properties_changed(
        &mut self,
        remote_device: BluetoothDevice,
        props: Vec<BtPropertyType>,
    ) {
        print_info!("Bluetooth properties {:?} changed for {:?}", props, remote_device);
    }

    fn on_address_changed(&mut self, addr: String) {
        print_info!("Address changed to {}", &addr);
        self.context.lock().unwrap().adapter_address = Some(addr);
    }

    fn on_name_changed(&mut self, name: String) {
        print_info!("Name changed to {}", &name);
    }

    fn on_discoverable_changed(&mut self, discoverable: bool) {
        print_info!("Discoverable changed to {}", &discoverable);
    }

    fn on_device_found(&mut self, remote_device: BluetoothDevice) {
        self.context
            .lock()
            .unwrap()
            .found_devices
            .entry(remote_device.address.clone())
            .or_insert(remote_device.clone());

        print_info!("Found device: {:?}", remote_device);
    }

    fn on_device_cleared(&mut self, remote_device: BluetoothDevice) {
        match self.context.lock().unwrap().found_devices.remove(&remote_device.address) {
            Some(_) => print_info!("Removed device: {:?}", remote_device),
            None => (),
        };

        self.context.lock().unwrap().bonded_devices.remove(&remote_device.address);
    }

    fn on_discovering_changed(&mut self, discovering: bool) {
        self.context.lock().unwrap().discovering_state = discovering;

        print_info!("Discovering: {}", discovering);
    }

    fn on_ssp_request(
        &mut self,
        remote_device: BluetoothDevice,
        _cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    ) {
        match variant {
            BtSspVariant::PasskeyNotification | BtSspVariant::PasskeyConfirmation => {
                print_info!(
                    "Device [{}: {}] would like to pair, enter passkey on remote device: {:06}",
                    &remote_device.address,
                    &remote_device.name,
                    passkey
                );
            }
            BtSspVariant::Consent => {
                let rd = remote_device.clone();
                self.context.lock().unwrap().run_callback(Box::new(move |context| {
                    // Auto-confirm bonding attempts that were locally initiated.
                    // Ignore all other bonding attempts.
                    let bonding_device = context.lock().unwrap().bonding_attempt.as_ref().cloned();
                    match bonding_device {
                        Some(bd) => {
                            if bd.address == rd.address {
                                context
                                    .lock()
                                    .unwrap()
                                    .adapter_dbus
                                    .as_ref()
                                    .unwrap()
                                    .set_pairing_confirmation(rd.clone(), true);
                            }
                        }
                        None => (),
                    }
                }));
            }
            BtSspVariant::PasskeyEntry => {
                println!("Got PasskeyEntry but it is not supported...");
            }
        }
    }

    fn on_pin_request(&mut self, remote_device: BluetoothDevice, _cod: u32, min_16_digit: bool) {
        print_info!(
            "Device [{}: {}] would like to pair, enter pin code {}",
            &remote_device.address,
            &remote_device.name,
            match min_16_digit {
                true => "with at least 16 digits",
                false => "",
            }
        );
    }

    fn on_pin_display(&mut self, remote_device: BluetoothDevice, pincode: String) {
        print_info!(
            "Device [{}: {}] would like to pair, enter pin code {} on the remote",
            &remote_device.address,
            &remote_device.name,
            pincode
        );
    }

    fn on_bond_state_changed(&mut self, status: u32, address: String, state: u32) {
        print_info!("Bonding state changed: [{}] state: {}, Status = {}", address, state, status);

        // Clear bonding attempt if bonding fails or succeeds
        match BtBondState::from(state) {
            BtBondState::NotBonded | BtBondState::Bonded => {
                let bonding_attempt =
                    self.context.lock().unwrap().bonding_attempt.as_ref().cloned();
                match bonding_attempt {
                    Some(bd) => {
                        if &address == &bd.address {
                            self.context.lock().unwrap().bonding_attempt = None;
                        }
                    }
                    None => (),
                }
            }
            BtBondState::Bonding => (),
        }

        let device =
            BluetoothDevice { address: address.clone(), name: String::from("Classic device") };

        // If bonded, we should also automatically connect all enabled profiles
        if BtBondState::Bonded == state.into() {
            self.context.lock().unwrap().bonded_devices.insert(address.clone(), device.clone());
            self.context.lock().unwrap().connect_all_enabled_profiles(device.clone());
        }

        if BtBondState::NotBonded == state.into() {
            self.context.lock().unwrap().bonded_devices.remove(&address);
        }
    }

    fn on_sdp_search_complete(
        &mut self,
        remote_device: BluetoothDevice,
        searched_uuid: Uuid128Bit,
        sdp_records: Vec<BtSdpRecord>,
    ) {
        print_info!(
            "SDP search of {} for UUID {} returned {} results",
            remote_device.address,
            UuidWrapper(&searched_uuid),
            sdp_records.len()
        );
        if !sdp_records.is_empty() {
            print_info!("{:?}", sdp_records);
        }
    }

    fn on_sdp_record_created(&mut self, record: BtSdpRecord, handle: i32) {
        print_info!("SDP record handle={} created", handle);
        if let BtSdpRecord::Mps(_) = record {
            let context = self.context.clone();
            // Callbacks first lock the DBus resource and then lock the context,
            // while the command handlers lock them in the reversed order.
            // `telephony enable` command happens to deadlock easily,
            // so use async call to prevent deadlock here.
            tokio::spawn(async move {
                context.lock().unwrap().mps_sdp_handle = Some(handle);
            });
        }
    }
}

impl RPCProxy for BtCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct BtConnectionCallback {
    objpath: String,
    _context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtConnectionCallback {
    pub(crate) fn new(
        objpath: String,
        _context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, _context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothConnectionCallback for BtConnectionCallback {
    fn on_device_connected(&mut self, remote_device: BluetoothDevice) {
        print_info!("Connected: [{}]: {}", remote_device.address, remote_device.name);
    }

    fn on_device_disconnected(&mut self, remote_device: BluetoothDevice) {
        print_info!("Disconnected: [{}]: {}", remote_device.address, remote_device.name);
    }
}

impl RPCProxy for BtConnectionCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_connection_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct ScannerCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl ScannerCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IScannerCallback for ScannerCallback {
    fn on_scanner_registered(&mut self, uuid: Uuid128Bit, scanner_id: u8, status: GattStatus) {
        if status != GattStatus::Success {
            print_error!("Failed registering scanner, status = {}", status);
            return;
        }

        print_info!(
            "Scanner callback registered, uuid = {}, id = {}",
            UuidWrapper(&uuid),
            scanner_id
        );
    }

    fn on_scan_result(&mut self, scan_result: ScanResult) {
        if self.context.lock().unwrap().active_scanner_ids.len() > 0 {
            print_info!("Scan result: {:#?}", scan_result);
        }
    }

    fn on_advertisement_found(&mut self, scanner_id: u8, scan_result: ScanResult) {
        if self.context.lock().unwrap().active_scanner_ids.len() > 0 {
            print_info!("Advertisement found for scanner_id {} : {:#?}", scanner_id, scan_result);
        }
    }

    fn on_advertisement_lost(&mut self, scanner_id: u8, scan_result: ScanResult) {
        if self.context.lock().unwrap().active_scanner_ids.len() > 0 {
            print_info!("Advertisement lost for scanner_id {} : {:#?}", scanner_id, scan_result);
        }
    }

    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode) {
        if self.context.lock().unwrap().active_scanner_ids.len() > 0 {
            print_info!("Scan suspend mode change: {:#?}", suspend_mode);
        }
    }
}

impl RPCProxy for ScannerCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_scanner_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct AdminCallback {
    objpath: String,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl AdminCallback {
    pub(crate) fn new(
        objpath: String,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothAdminPolicyCallback for AdminCallback {
    fn on_service_allowlist_changed(&mut self, allowlist: Vec<Uuid128Bit>) {
        print_info!("new allowlist: {:?}", allowlist);
    }

    fn on_device_policy_effect_changed(
        &mut self,
        device: BluetoothDevice,
        new_policy_effect: Option<PolicyEffect>,
    ) {
        print_info!(
            "new device policy effect. Device: {:?}. New Effect: {:?}",
            device,
            new_policy_effect
        );
    }
}

impl RPCProxy for AdminCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_admin_policy_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct AdvertisingSetCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl AdvertisingSetCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IAdvertisingSetCallback for AdvertisingSetCallback {
    fn on_advertising_set_started(
        &mut self,
        reg_id: i32,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    ) {
        print_info!(
            "on_advertising_set_started: reg_id = {}, advertiser_id = {}, tx_power = {}, status = {:?}",
            reg_id,
            advertiser_id,
            tx_power,
            status
        );

        let mut context = self.context.lock().unwrap();
        if status != AdvertisingStatus::Success {
            print_error!(
                "on_advertising_set_started: removing advertising set registered ({})",
                reg_id
            );
            context.adv_sets.remove(&reg_id);
            return;
        }
        if let Some(s) = context.adv_sets.get_mut(&reg_id) {
            s.adv_id = Some(advertiser_id);
        } else {
            print_error!("on_advertising_set_started: invalid callback for reg_id={}", reg_id);
        }
    }

    fn on_own_address_read(&mut self, advertiser_id: i32, address_type: i32, address: String) {
        print_info!(
            "on_own_address_read: advertiser_id = {}, address_type = {}, address = {}",
            advertiser_id,
            address_type,
            address
        );
    }

    fn on_advertising_set_stopped(&mut self, advertiser_id: i32) {
        print_info!("on_advertising_set_stopped: advertiser_id = {}", advertiser_id);
    }

    fn on_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    ) {
        print_info!(
            "on_advertising_enabled: advertiser_id = {}, enable = {}, status = {:?}",
            advertiser_id,
            enable,
            status
        );
    }

    fn on_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {
        print_info!(
            "on_advertising_data_set: advertiser_id = {}, status = {:?}",
            advertiser_id,
            status
        );
    }

    fn on_scan_response_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {
        print_info!(
            "on_scan_response_data_set: advertiser_id = {}, status = {:?}",
            advertiser_id,
            status
        );
    }

    fn on_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        tx_power: i32,
        status: AdvertisingStatus,
    ) {
        print_info!(
            "on_advertising_parameters_updated: advertiser_id = {}, tx_power: {}, status = {:?}",
            advertiser_id,
            tx_power,
            status
        );
    }

    fn on_periodic_advertising_parameters_updated(
        &mut self,
        advertiser_id: i32,
        status: AdvertisingStatus,
    ) {
        print_info!(
            "on_periodic_advertising_parameters_updated: advertiser_id = {}, status = {:?}",
            advertiser_id,
            status
        );
    }

    fn on_periodic_advertising_data_set(&mut self, advertiser_id: i32, status: AdvertisingStatus) {
        print_info!(
            "on_periodic_advertising_data_set: advertiser_id = {}, status = {:?}",
            advertiser_id,
            status
        );
    }

    fn on_periodic_advertising_enabled(
        &mut self,
        advertiser_id: i32,
        enable: bool,
        status: AdvertisingStatus,
    ) {
        print_info!(
            "on_periodic_advertising_enabled: advertiser_id = {}, enable = {}, status = {:?}",
            advertiser_id,
            enable,
            status
        );
    }

    fn on_suspend_mode_change(&mut self, suspend_mode: SuspendMode) {
        print_info!("on_suspend_mode_change: advertising suspend_mode = {:?}", suspend_mode);
    }
}

impl RPCProxy for AdvertisingSetCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_advertising_set_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct BtGattCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtGattCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothGattCallback for BtGattCallback {
    fn on_client_registered(&mut self, status: GattStatus, client_id: i32) {
        print_info!("GATT Client registered status = {}, client_id = {}", status, client_id);
        self.context.lock().unwrap().gatt_client_context.client_id = Some(client_id);
    }

    fn on_client_connection_state(
        &mut self,
        status: GattStatus,
        client_id: i32,
        connected: bool,
        addr: String,
    ) {
        print_info!(
            "GATT Client connection state = {}, client_id = {}, connected = {}, addr = {}",
            status,
            client_id,
            connected,
            addr
        );
    }

    fn on_phy_update(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {
        print_info!(
            "Phy updated: addr = {}, tx_phy = {:?}, rx_phy = {:?}, status = {:?}",
            addr,
            tx_phy,
            rx_phy,
            status
        );
    }

    fn on_phy_read(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {
        print_info!(
            "Phy read: addr = {}, tx_phy = {:?}, rx_phy = {:?}, status = {:?}",
            addr,
            tx_phy,
            rx_phy,
            status
        );
    }

    fn on_search_complete(
        &mut self,
        addr: String,
        services: Vec<BluetoothGattService>,
        status: GattStatus,
    ) {
        print_info!(
            "GATT DB Search complete: addr = {}, services = {:?}, status = {}",
            addr,
            services,
            status
        );
    }

    fn on_characteristic_read(
        &mut self,
        addr: String,
        status: GattStatus,
        handle: i32,
        value: Vec<u8>,
    ) {
        print_info!(
            "GATT Characteristic read: addr = {}, status = {}, handle = {}, value = {:?}",
            addr,
            status,
            handle,
            value
        );
    }

    fn on_characteristic_write(&mut self, addr: String, status: GattStatus, handle: i32) {
        print_info!(
            "GATT Characteristic write: addr = {}, status = {}, handle = {}",
            addr,
            status,
            handle
        );
    }

    fn on_execute_write(&mut self, addr: String, status: GattStatus) {
        print_info!("GATT execute write addr = {}, status = {}", addr, status);
    }

    fn on_descriptor_read(
        &mut self,
        addr: String,
        status: GattStatus,
        handle: i32,
        value: Vec<u8>,
    ) {
        print_info!(
            "GATT Descriptor read: addr = {}, status = {}, handle = {}, value = {:?}",
            addr,
            status,
            handle,
            value
        );
    }

    fn on_descriptor_write(&mut self, addr: String, status: GattStatus, handle: i32) {
        print_info!(
            "GATT Descriptor write: addr = {}, status = {}, handle = {}",
            addr,
            status,
            handle
        );
    }

    fn on_notify(&mut self, addr: String, handle: i32, value: Vec<u8>) {
        print_info!("GATT Notification: addr = {}, handle = {}, value = {:?}", addr, handle, value);
    }

    fn on_read_remote_rssi(&mut self, addr: String, rssi: i32, status: GattStatus) {
        print_info!("Remote RSSI read: addr = {}, rssi = {}, status = {}", addr, rssi, status);
    }

    fn on_configure_mtu(&mut self, addr: String, mtu: i32, status: GattStatus) {
        print_info!("MTU configured: addr = {}, mtu = {}, status = {}", addr, mtu, status);
    }

    fn on_connection_updated(
        &mut self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: GattStatus,
    ) {
        print_info!(
            "Connection updated: addr = {}, interval = {}, latency = {}, timeout = {}, status = {}",
            addr,
            interval,
            latency,
            timeout,
            status
        );
    }

    fn on_service_changed(&mut self, addr: String) {
        print_info!("Service changed for {}", addr,);
    }
}

impl RPCProxy for BtGattCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_gatt_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );

        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct BtGattServerCallback {
    objpath: String,
    _context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtGattServerCallback {
    pub(crate) fn new(
        objpath: String,
        _context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, _context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothGattServerCallback for BtGattServerCallback {
    fn on_server_registered(&mut self, status: GattStatus, server_id: i32) {
        print_info!("GATT Server registered status = {}, server_id = {}", status, server_id);
    }

    fn on_server_connection_state(&mut self, server_id: i32, connected: bool, addr: String) {
        print_info!(
            "GATT server connection with server_id = {}, connected = {}, addr = {}",
            server_id,
            connected,
            addr
        );
    }

    fn on_service_added(&mut self, status: GattStatus, service: BluetoothGattService) {
        print_info!("GATT service added with status = {}, service = {:?}", status, service)
    }

    fn on_service_removed(&mut self, status: GattStatus, handle: i32) {
        print_info!("GATT service removed with status = {}, handle = {:?}", status, handle);
    }

    fn on_characteristic_read_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        is_long: bool,
        handle: i32,
    ) {
        print_info!(
            "GATT characteristic read request for addr = {}, trans_id = {}, offset = {}, is_long = {}, handle = {}",
            addr,
            trans_id,
            offset,
            is_long,
            handle
        );
    }

    fn on_descriptor_read_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        is_long: bool,
        handle: i32,
    ) {
        print_info!(
            "GATT descriptor read request for addr = {}, trans_id = {}, offset = {}, is_long = {}, handle = {}",
            addr,
            trans_id,
            offset,
            is_long,
            handle
        );
    }

    fn on_characteristic_write_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        len: i32,
        is_prep: bool,
        need_rsp: bool,
        handle: i32,
        value: Vec<u8>,
    ) {
        print_info!(
            "GATT characteristic write request for \
                addr = {}, trans_id = {}, offset = {}, len = {}, is_prep = {}, need_rsp = {}, handle = {}, value = {:?}",
            addr,
            trans_id,
            offset,
            len,
            is_prep,
            need_rsp,
            handle,
            value
        );
    }

    fn on_descriptor_write_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        len: i32,
        is_prep: bool,
        need_rsp: bool,
        handle: i32,
        value: Vec<u8>,
    ) {
        print_info!(
            "GATT descriptor write request for \
                addr = {}, trans_id = {}, offset = {}, len = {}, is_prep = {}, need_rsp = {}, handle = {}, value = {:?}",
            addr,
            trans_id,
            offset,
            len,
            is_prep,
            need_rsp,
            handle,
            value
        );
    }

    fn on_execute_write(&mut self, addr: String, trans_id: i32, exec_write: bool) {
        print_info!(
            "GATT executed write for addr = {}, trans_id = {}, exec_write = {}",
            addr,
            trans_id,
            exec_write
        );
    }

    fn on_notification_sent(&mut self, addr: String, status: GattStatus) {
        print_info!(
            "GATT notification/indication sent for addr = {} with status = {}",
            addr,
            status
        );
    }

    fn on_mtu_changed(&mut self, addr: String, mtu: i32) {
        print_info!("GATT server MTU changed for addr = {}, mtu = {}", addr, mtu);
    }

    fn on_phy_update(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {
        print_info!(
            "GATT server phy updated for addr = {}: tx_phy = {:?}, rx_phy = {:?}, status = {}",
            addr,
            tx_phy,
            rx_phy,
            status
        );
    }

    fn on_phy_read(&mut self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {
        print_info!(
            "GATT server phy read for addr = {}: tx_phy = {:?}, rx_phy = {:?}, status = {}",
            addr,
            tx_phy,
            rx_phy,
            status
        );
    }

    fn on_connection_updated(
        &mut self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: GattStatus,
    ) {
        print_info!(
            "GATT server connection updated for addr = {}, interval = {}, latency = {}, timeout = {}, status = {}",
            addr,
            interval,
            latency,
            timeout,
            status
        );
    }

    fn on_subrate_change(
        &mut self,
        addr: String,
        subrate_factor: i32,
        latency: i32,
        cont_num: i32,
        timeout: i32,
        status: GattStatus,
    ) {
        print_info!(
            "GATT server subrate changed for addr = {}, subrate_factor = {}, latency = {}, cont_num = {}, timeout = {}, status = {}",
            addr,
            subrate_factor,
            latency,
            cont_num,
            timeout,
            status
        );
    }
}

impl RPCProxy for BtGattServerCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_gatt_server_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );

        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct BtSocketManagerCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,
    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BtSocketManagerCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }

    fn start_socket_schedule(&mut self, socket: BluetoothSocket) {
        let SocketSchedule { num_frame, send_interval, disconnect_delay } =
            match self.context.lock().unwrap().socket_test_schedule {
                Some(s) => s,
                None => return,
            };

        let mut fd = match socket.fd {
            Some(fd) => fd,
            None => {
                print_error!("incoming connection fd is None. Unable to send data");
                return;
            }
        };

        tokio::spawn(async move {
            for i in 0..num_frame {
                fd.write_all(SOCKET_TEST_WRITE).ok();
                print_info!("data sent: {}", i + 1);
                tokio::time::sleep(send_interval).await;
            }

            // dump any incoming data
            let interval = 100;
            for _d in (0..=disconnect_delay.as_millis()).step_by(interval) {
                let mut buf = [0; 128];
                let sz = fd.read(&mut buf).unwrap();
                let data = buf[..sz].to_vec();
                if sz > 0 {
                    print_info!("received {} bytes: {:?}", sz, data);
                }
                tokio::time::sleep(Duration::from_millis(interval as u64)).await;
            }

            //|fd| is dropped automatically when the scope ends.
        });
    }
}

impl IBluetoothSocketManagerCallbacks for BtSocketManagerCallback {
    fn on_incoming_socket_ready(&mut self, socket: BluetoothServerSocket, status: BtStatus) {
        if status != BtStatus::Success {
            print_error!(
                "Incoming socket {} failed to be ready, type = {:?}, flags = {}, status = {:?}",
                socket.id,
                socket.sock_type,
                socket.flags,
                status,
            );
            return;
        }

        print_info!(
            "Socket {} ready, details: {:?}, flags = {}, psm = {:?}, channel = {:?}, name = {:?}, uuid = {:?}",
            socket.id,
            socket.sock_type,
            socket.flags,
            socket.psm,
            socket.channel,
            socket.name,
            socket.uuid,
        );

        let callback_id = self.context.lock().unwrap().socket_manager_callback_id.clone().unwrap();

        self.context.lock().unwrap().run_callback(Box::new(move |context| {
            let status = context.lock().unwrap().socket_manager_dbus.as_mut().unwrap().accept(
                callback_id,
                socket.id,
                None,
            );
            if status != BtStatus::Success {
                print_error!("Failed to accept socket {}, status = {:?}", socket.id, status);
                return;
            }
            print_info!("Requested for accepting socket {}", socket.id);
        }));
    }

    fn on_incoming_socket_closed(&mut self, listener_id: SocketId, reason: BtStatus) {
        print_info!("Socket {} closed, reason = {:?}", listener_id, reason);
    }

    fn on_handle_incoming_connection(
        &mut self,
        listener_id: SocketId,
        connection: BluetoothSocket,
    ) {
        print_info!("Socket {} connected", listener_id);
        self.start_socket_schedule(connection);
    }

    fn on_outgoing_connection_result(
        &mut self,
        connecting_id: SocketId,
        result: BtStatus,
        socket: Option<BluetoothSocket>,
    ) {
        if let Some(s) = socket {
            print_info!("Connection success on {}: {:?} for {}", connecting_id, result, s);
            self.start_socket_schedule(s);
        } else {
            print_info!("Connection failed on {}: {:?}", connecting_id, result);
        }
    }
}

impl RPCProxy for BtSocketManagerCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_socket_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

/// Callback container for suspend interface callbacks.
pub(crate) struct SuspendCallback {
    objpath: String,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl SuspendCallback {
    pub(crate) fn new(
        objpath: String,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, dbus_connection, dbus_crossroads }
    }
}

impl ISuspendCallback for SuspendCallback {
    // TODO(b/224606285): Implement suspend utils in btclient.
    fn on_callback_registered(&mut self, _callback_id: u32) {}
    fn on_suspend_ready(&mut self, _suspend_id: i32) {}
    fn on_resumed(&mut self, _suspend_id: i32) {}
}

impl RPCProxy for SuspendCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_suspend_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

/// Callback container for suspend interface callbacks.
pub(crate) struct QACallback {
    objpath: String,
    _context: Arc<Mutex<ClientContext>>,
    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl QACallback {
    pub(crate) fn new(
        objpath: String,
        _context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, _context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothQACallback for QACallback {
    fn on_fetch_discoverable_mode_completed(&mut self, mode: bt_topshim::btif::BtDiscMode) {
        print_info!("Discoverable mode: {:?}", mode);
    }

    fn on_fetch_connectable_completed(&mut self, connectable: bool) {
        print_info!("Connectable mode: {:?}", connectable);
    }

    fn on_set_connectable_completed(&mut self, succeed: bool) {
        print_info!(
            "Set connectable mode: {}",
            match succeed {
                true => "succeeded",
                false => "failed",
            }
        );
    }

    fn on_fetch_alias_completed(&mut self, alias: String) {
        print_info!("Alias: {}", alias);
    }

    fn on_get_hid_report_completed(&mut self, status: BtStatus) {
        print_info!("Get HID report: {:?}", status);
    }

    fn on_set_hid_report_completed(&mut self, status: BtStatus) {
        print_info!("Set HID report: {:?}", status);
    }

    fn on_send_hid_data_completed(&mut self, status: BtStatus) {
        print_info!("Send HID data: {:?}", status);
    }
}

impl RPCProxy for QACallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_qa_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct MediaCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl MediaCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

fn timestamp_to_string(ts_in_us: u64) -> String {
    i64::try_from(ts_in_us)
        .and_then(|ts| Ok(Utc.timestamp_nanos(ts * 1000).to_rfc3339()))
        .unwrap_or("UNKNOWN".to_string())
}

impl IBluetoothMediaCallback for MediaCallback {
    fn on_bluetooth_audio_device_added(&mut self, _device: BluetoothAudioDevice) {}
    fn on_bluetooth_audio_device_removed(&mut self, _addr: String) {}
    fn on_absolute_volume_supported_changed(&mut self, _supported: bool) {}
    fn on_absolute_volume_changed(&mut self, _volume: u8) {}
    fn on_hfp_volume_changed(&mut self, _volume: u8, _addr: String) {}
    fn on_hfp_audio_disconnected(&mut self, _addr: String) {}
    fn on_hfp_debug_dump(
        &mut self,
        active: bool,
        codec_id: u16,
        total_num_decoded_frames: i32,
        pkt_loss_ratio: f64,
        begin_ts: u64,
        end_ts: u64,
        pkt_status_in_hex: String,
        pkt_status_in_binary: String,
    ) {
        // Invoke run_callback so that the callback can be handled through
        // ForegroundActions::RunCallback in main.rs.
        self.context.lock().unwrap().run_callback(Box::new(move |_context| {
            let is_wbs = codec_id == HfpCodecId::MSBC as u16;
            let is_swb = codec_id == HfpCodecId::LC3 as u16;
            let dump = if active && (is_wbs || is_swb) {
                let mut to_split_binary = pkt_status_in_binary.clone();
                let mut wrapped_binary = String::new();
                while to_split_binary.len() > BINARY_PACKET_STATUS_WRAP {
                    let remaining = to_split_binary.split_off(BINARY_PACKET_STATUS_WRAP);
                    wrapped_binary.push_str(&to_split_binary);
                    wrapped_binary.push('\n');
                    to_split_binary = remaining;
                }
                wrapped_binary.push_str(&to_split_binary);
                format!(
                    "\n--------{} packet loss--------\n\
                       Decoded Packets: {}, Packet Loss Ratio: {} \n\
                       {} [begin]\n\
                       {} [end]\n\
                       In Hex format:\n\
                       {}\n\
                       In binary format:\n\
                       {}",
                    if is_wbs { "WBS" } else { "SWB" },
                    total_num_decoded_frames,
                    pkt_loss_ratio,
                    timestamp_to_string(begin_ts),
                    timestamp_to_string(end_ts),
                    pkt_status_in_hex,
                    wrapped_binary
                )
            } else {
                "".to_string()
            };

            print_info!(
                "\n--------HFP debug dump---------\n\
                     HFP SCO: {}, Codec: {}\
                     {}
                     ",
                if active { "active" } else { "inactive" },
                if is_wbs {
                    "mSBC"
                } else if is_swb {
                    "LC3"
                } else {
                    "CVSD"
                },
                dump
            );
        }));
    }
}

impl RPCProxy for MediaCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_media_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct TelephonyCallback {
    objpath: String,
    _context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl TelephonyCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, _context: context, dbus_connection, dbus_crossroads }
    }
}

impl IBluetoothTelephonyCallback for TelephonyCallback {
    fn on_telephony_use(&mut self, addr: String, state: bool) {
        print_info!("Telephony use changed: [{}] state: {}", addr, state);
    }
}

impl RPCProxy for TelephonyCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_bluetooth_telephony_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}

pub(crate) struct BatteryManagerCallback {
    objpath: String,
    context: Arc<Mutex<ClientContext>>,

    dbus_connection: Arc<SyncConnection>,
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl BatteryManagerCallback {
    pub(crate) fn new(
        objpath: String,
        context: Arc<Mutex<ClientContext>>,
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
    ) -> Self {
        Self { objpath, context, dbus_connection, dbus_crossroads }
    }
}

impl IBatteryManagerCallback for BatteryManagerCallback {
    fn on_battery_info_updated(&mut self, remote_address: String, battery_set: BatterySet) {
        let address = remote_address.to_uppercase();
        if self.context.lock().unwrap().battery_address_filter.contains(&address) {
            if battery_set.batteries.len() == 0 {
                print_info!(
                    "Battery info for address '{}' updated with empty battery set. \
                    The batteries for this device may have been removed.",
                    address.clone()
                );
                return;
            }
            print_info!(
                "Battery data for '{}' from source '{}' and uuid '{}' changed to:",
                address.clone(),
                battery_set.source_uuid.clone(),
                battery_set.source_info.clone()
            );
            for battery in battery_set.batteries {
                print_info!("   {}%, variant: '{}'", battery.percentage, battery.variant);
            }
        }
    }
}

impl RPCProxy for BatteryManagerCallback {
    fn get_object_id(&self) -> String {
        self.objpath.clone()
    }

    fn export_for_rpc(self: Box<Self>) {
        let cr = self.dbus_crossroads.clone();
        let iface = export_battery_manager_callback_dbus_intf(
            self.dbus_connection.clone(),
            &mut cr.lock().unwrap(),
            Arc::new(Mutex::new(DisconnectWatcher::new())),
        );
        cr.lock().unwrap().insert(self.get_object_id(), &[iface], Arc::new(Mutex::new(self)));
    }
}
