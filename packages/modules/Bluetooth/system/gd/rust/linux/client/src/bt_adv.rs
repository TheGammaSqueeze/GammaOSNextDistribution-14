use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::ClientContext;
use crate::{console_yellow, print_info};

use bt_topshim::btif::Uuid;
use bt_topshim::profiles::gatt::LePhy;
use btstack::bluetooth_adv::{AdvertiseData, AdvertiserId, AdvertisingSetParameters};
use btstack::bluetooth_gatt::IBluetoothGatt;

/// Avertisement parameter and data for a BLE advertising set.
#[derive(Debug, Clone)]
pub(crate) struct AdvSet {
    /// ID for the advertising set if it's being started successfully, None otherwise.
    pub(crate) adv_id: Option<AdvertiserId>,

    /// Advertising parameters.
    pub(crate) params: AdvertisingSetParameters,

    /// Advertising data.
    pub(crate) data: AdvertiseData,

    /// Scan response data.
    pub(crate) scan_rsp: AdvertiseData,
}

impl AdvSet {
    pub(crate) fn new(is_legacy: bool) -> Self {
        let params = AdvertisingSetParameters {
            connectable: false,
            scannable: false,
            is_legacy,
            is_anonymous: false,
            include_tx_power: true,
            primary_phy: LePhy::Phy1m,
            secondary_phy: LePhy::Phy1m,
            interval: 100,
            tx_power_level: 0x7f, // no preference
            own_address_type: 1,  // random
        };

        let data = AdvertiseData {
            service_uuids: Vec::new(),
            solicit_uuids: Vec::new(),
            transport_discovery_data: Vec::new(),
            manufacturer_data: HashMap::from([(0, vec![0, 1, 2])]),
            service_data: HashMap::new(),
            include_tx_power_level: true,
            include_device_name: true,
        };

        let scan_rsp = AdvertiseData {
            service_uuids: vec![Uuid::from([
                0x00, 0x00, 0xfe, 0xf3, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0x80, 0x5f, 0x9b,
                0x34, 0xfb,
            ])],
            solicit_uuids: Vec::new(),
            transport_discovery_data: Vec::new(),
            manufacturer_data: HashMap::new(),
            service_data: HashMap::from([(
                "0000fef3-0000-1000-8000-00805f9b34fb".to_string(),
                vec![0x0a, 0x0b],
            )]),
            include_tx_power_level: false,
            include_device_name: false,
        };

        AdvSet { adv_id: None, params, data, scan_rsp }
    }

    pub(crate) fn start(context: Arc<Mutex<ClientContext>>, s: AdvSet, callback_id: u32) {
        let mut context = context.lock().unwrap();

        let reg_id = context.gatt_dbus.as_mut().unwrap().start_advertising_set(
            s.params.clone(),
            s.data.clone(),
            None,
            None,
            None,
            0,
            0,
            callback_id,
        );
        print_info!("Starting advertising set for reg_id = {}", reg_id);
        context.adv_sets.insert(reg_id, s);
    }

    pub(crate) fn stop_all(context: Arc<Mutex<ClientContext>>) {
        let mut context = context.lock().unwrap();

        let adv_ids: Vec<_> = context.adv_sets.iter().filter_map(|(_, s)| s.adv_id).collect();
        for adv_id in adv_ids {
            print_info!("Stopping advertising set {}", adv_id);
            context.gatt_dbus.as_mut().unwrap().stop_advertising_set(adv_id);
        }
        context.adv_sets.clear();
    }
}
