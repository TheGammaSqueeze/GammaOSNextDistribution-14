//! TODO(b/277818879) - Temporary DIS implementation

use log;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::Sender;

use crate::bluetooth_gatt::{
    BluetoothGatt, BluetoothGattCharacteristic, BluetoothGattService, GattDbElementType,
    IBluetoothGatt, IBluetoothGattServerCallback,
};
use crate::uuid::{Profile, UuidHelper};
use crate::{Message, RPCProxy};
use bt_topshim::profiles::gatt::{GattStatus, LePhy};
use bt_topshim::sysprop;

/// Random uuid generated for registering against gatt server.
const DIS_APP_RANDOM_UUID: &str = "1b518948-fd77-4459-906f-4923104bb639";

/// UUID for PNP ID characteristic.
const PNP_ID_CHAR_UUID: &str = "00002A50-0000-1000-8000-00805F9B34FB";

/// Handles exporting the Device Information Service (DIS).
pub struct DeviceInformation {
    /// Reference to Gatt server implementation to export service.
    bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,

    /// Server id (available once we are registered).
    gatt_server_id: Option<i32>,

    /// Handle for the PNP ID characteristic.
    pnp_id_handle: Option<i32>,

    /// Sender for stack mainloop.
    tx: Sender<Message>,
}

impl DeviceInformation {
    pub fn new(bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>, tx: Sender<Message>) -> Self {
        Self { bluetooth_gatt, gatt_server_id: None, pnp_id_handle: None, tx }
    }

    pub(crate) fn initialize(&mut self) {
        let callback = Box::new(DeviceInformationServerCallbacks::new(self.tx.clone()));

        // First register for callbacks with the server.
        self.bluetooth_gatt.lock().unwrap().register_server(
            DIS_APP_RANDOM_UUID.to_string(),
            callback,
            /*eatt_support=*/ true,
        );
    }

    pub(crate) fn handle_callbacks(&mut self, callback: &ServiceCallbacks) {
        match callback {
            ServiceCallbacks::Registered(status, server_id) => {
                if status != &GattStatus::Success {
                    log::error!("DIS failed to register callbacks. Status={:?}", status);
                    return;
                }

                self.gatt_server_id = Some(*server_id);

                // Construct and add Device Information service.
                let mut service = BluetoothGattService::new(
                    UuidHelper::get_profile_uuid(&Profile::Dis)
                        .expect("DIS uuid mapping missing")
                        .clone(),
                    /*instance_id=*/ 0,
                    GattDbElementType::PrimaryService.into(),
                );

                service.characteristics.push(BluetoothGattCharacteristic::new(
                    UuidHelper::from_string(PNP_ID_CHAR_UUID).expect("PNP ID uuid is malformed"),
                    /*instance_id=*/ 0,
                    BluetoothGattCharacteristic::PROPERTY_READ,
                    BluetoothGattCharacteristic::PERMISSION_READ,
                ));

                self.bluetooth_gatt.lock().unwrap().add_service(*server_id, service);
            }

            ServiceCallbacks::ServiceAdded(status, service) => {
                if status != &GattStatus::Success {
                    return;
                }

                let pnp_uuid =
                    UuidHelper::from_string(PNP_ID_CHAR_UUID).expect("PNP ID uuid is malformed");

                // Find the PNP ID characteristic we inserted before and store
                // the handle for it.
                for characteristic in &service.characteristics {
                    if characteristic.uuid == pnp_uuid {
                        self.pnp_id_handle = Some(characteristic.instance_id);
                    }
                }
            }
            ServiceCallbacks::OnCharacteristicReadRequest(
                addr,
                trans_id,
                offset,
                _is_long,
                handle,
            ) => match (self.gatt_server_id, self.pnp_id_handle) {
                (Some(server_id), Some(pnp_handle)) => {
                    if &pnp_handle == handle {
                        let vendor_id = sysprop::get_i32(sysprop::PropertyI32::VendorId);
                        let vendor_id_source =
                            sysprop::get_i32(sysprop::PropertyI32::VendorIdSource);
                        let product_id = sysprop::get_i32(sysprop::PropertyI32::ProductId);
                        let product_version =
                            sysprop::get_i32(sysprop::PropertyI32::ProductVersion);

                        // PNP ID ordering (all values are in little endian):
                        // - Vendor ID source (1 octet)
                        // - Vendor ID (2 octet)
                        // - Product ID (2 octet)
                        // - Product Version (2 octet)
                        let mut value: Vec<u8> = Vec::new();
                        value.push(vendor_id_source.to_le_bytes()[0]);
                        value.extend_from_slice(&vendor_id.to_le_bytes()[0..2]);
                        value.extend_from_slice(&product_id.to_le_bytes()[0..2]);
                        value.extend_from_slice(&product_version.to_le_bytes()[0..2]);

                        self.bluetooth_gatt.lock().unwrap().send_response(
                            server_id,
                            addr.clone(),
                            *trans_id,
                            GattStatus::Success,
                            *offset,
                            value,
                        );
                    }
                }

                (_, _) => (),
            },
        }
    }
}

// Callbacks we need to handle for DIS.
pub enum ServiceCallbacks {
    Registered(GattStatus, i32),
    ServiceAdded(GattStatus, BluetoothGattService),
    OnCharacteristicReadRequest(String, i32, i32, bool, i32),
}

// Handle callbacks for DIS to register
struct DeviceInformationServerCallbacks {
    // Sender to the main loop
    tx: Sender<Message>,
}

impl DeviceInformationServerCallbacks {
    fn new(tx: Sender<Message>) -> Self {
        Self { tx }
    }
}

impl IBluetoothGattServerCallback for DeviceInformationServerCallbacks {
    fn on_server_registered(&mut self, status: GattStatus, server_id: i32) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::Dis(ServiceCallbacks::Registered(status, server_id))).await;
        });
    }

    fn on_service_added(&mut self, status: GattStatus, service: BluetoothGattService) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl.send(Message::Dis(ServiceCallbacks::ServiceAdded(status, service))).await;
        });
    }

    fn on_characteristic_read_request(
        &mut self,
        addr: String,
        trans_id: i32,
        offset: i32,
        is_long: bool,
        handle: i32,
    ) {
        let txl = self.tx.clone();
        tokio::spawn(async move {
            let _ = txl
                .send(Message::Dis(ServiceCallbacks::OnCharacteristicReadRequest(
                    addr, trans_id, offset, is_long, handle,
                )))
                .await;
        });
    }

    // Remaining callbacks are unhandled

    fn on_service_removed(&mut self, _status: GattStatus, _handle: i32) {}
    fn on_server_connection_state(&mut self, _server_id: i32, _connected: bool, _addr: String) {}
    fn on_descriptor_read_request(
        &mut self,
        _addr: String,
        _trans_id: i32,
        _offset: i32,
        _is_long: bool,
        _handle: i32,
    ) {
    }
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
    ) {
    }
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
    ) {
    }
    fn on_execute_write(&mut self, _addr: String, _trans_id: i32, _exec_write: bool) {}
    fn on_notification_sent(&mut self, _addr: String, _status: GattStatus) {}
    fn on_mtu_changed(&mut self, _addr: String, _mtu: i32) {}
    fn on_phy_update(
        &mut self,
        _addr: String,
        _tx_phy: LePhy,
        _rx_phy: LePhy,
        _status: GattStatus,
    ) {
    }
    fn on_phy_read(&mut self, _addr: String, _tx_phy: LePhy, _rx_phy: LePhy, _status: GattStatus) {}
    fn on_connection_updated(
        &mut self,
        _addr: String,
        _interval: i32,
        _latency: i32,
        _timeout: i32,
        _status: GattStatus,
    ) {
    }
    fn on_subrate_change(
        &mut self,
        _addr: String,
        _subrate_factor: i32,
        _latency: i32,
        _cont_num: i32,
        _timeout: i32,
        _status: GattStatus,
    ) {
    }
}

impl RPCProxy for DeviceInformationServerCallbacks {
    fn get_object_id(&self) -> String {
        "DIS Gatt Server Callback".to_string()
    }
}
