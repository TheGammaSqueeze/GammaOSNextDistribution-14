use crate::battery_provider_manager::BatteryProviderManager;
use crate::callbacks::Callbacks;
use crate::uuid;
use crate::Message;
use crate::RPCProxy;
use itertools::Itertools;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::Sender;

/// The primary representation of battery information for internal passing and external calls.
#[derive(Debug, Clone)]
pub struct BatterySet {
    /// Address of the remote device.
    pub address: String,
    /// UUID of where the battery info is decoded from as found in BT Spec.
    pub source_uuid: String,
    /// Information about the battery source, e.g. "BAS" or "HFP 1.8".
    pub source_info: String,
    /// Collection of batteries from this source.
    pub batteries: Vec<Battery>,
}

/// Describes an individual battery measurement, possibly one of many for a given device.
#[derive(Debug, Clone)]
pub struct Battery {
    /// Battery charge percentage between 0 and 100. For protocols that use 0-5 this will be that
    /// number multiplied by 20.
    pub percentage: u32,
    /// Description of this battery, such as Left, Right, or Case. Only present if the source has
    /// this level of detail.
    pub variant: String,
}

/// Helper representation of a collection of BatterySet to simplify passing around data internally.
pub struct Batteries(Vec<BatterySet>);

/// Callback for interacting with the BatteryManager.
pub trait IBatteryManagerCallback: RPCProxy {
    /// Invoked whenever battery information associated with the given remote changes.
    fn on_battery_info_updated(&mut self, remote_address: String, battery_set: BatterySet);
}

/// Central point for getting battery information that might be sourced from numerous systems.
pub trait IBatteryManager {
    /// Registers a callback for interfacing with the BatteryManager and returns a unique
    /// callback_id for future calls.
    fn register_battery_callback(
        &mut self,
        battery_manager_callback: Box<dyn IBatteryManagerCallback + Send>,
    ) -> u32;

    /// Unregister a callback.
    fn unregister_battery_callback(&mut self, callback_id: u32) -> bool;

    /// Returns battery information for the remote, sourced from the highest priority origin.
    fn get_battery_information(&self, remote_address: String) -> Option<BatterySet>;
}

/// Repesentation of the BatteryManager.
pub struct BatteryManager {
    battery_provider_manager: Arc<Mutex<Box<BatteryProviderManager>>>,
    callbacks: Callbacks<dyn IBatteryManagerCallback + Send>,
}

impl BatteryManager {
    /// Construct a new BatteryManager with callbacks communicating on tx.
    pub fn new(
        battery_provider_manager: Arc<Mutex<Box<BatteryProviderManager>>>,
        tx: Sender<Message>,
    ) -> BatteryManager {
        let callbacks = Callbacks::new(tx.clone(), Message::BatteryManagerCallbackDisconnected);
        Self { battery_provider_manager, callbacks }
    }

    /// Remove a callback due to disconnection or unregistration.
    pub fn remove_callback(&mut self, callback_id: u32) -> bool {
        self.callbacks.remove_callback(callback_id)
    }

    /// Handles a BatterySet update.
    pub fn handle_battery_updated(&mut self, remote_address: String, battery_set: BatterySet) {
        self.callbacks.for_all_callbacks(|callback| {
            callback.on_battery_info_updated(remote_address.clone(), battery_set.clone())
        });
    }
}

impl IBatteryManager for BatteryManager {
    fn register_battery_callback(
        &mut self,
        battery_manager_callback: Box<dyn IBatteryManagerCallback + Send>,
    ) -> u32 {
        self.callbacks.add_callback(battery_manager_callback)
    }

    fn unregister_battery_callback(&mut self, callback_id: u32) -> bool {
        self.remove_callback(callback_id)
    }

    fn get_battery_information(&self, remote_address: String) -> Option<BatterySet> {
        self.battery_provider_manager.lock().unwrap().get_battery_info(remote_address)
    }
}

impl BatterySet {
    pub fn new(
        address: String,
        source_uuid: String,
        source_info: String,
        batteries: Vec<Battery>,
    ) -> Self {
        Self { address, source_uuid, source_info, batteries }
    }

    pub fn add_or_update_battery(&mut self, new_battery: Battery) {
        match self.batteries.iter_mut().find(|battery| battery.variant == new_battery.variant) {
            Some(battery) => *battery = new_battery,
            None => self.batteries.push(new_battery),
        }
    }
}

impl Batteries {
    pub fn new() -> Self {
        Self(vec![])
    }

    /// Updates a battery matching all non-battery-level fields if found, otherwise adds new_battery
    /// verbatim.
    pub fn add_or_update_battery_set(&mut self, new_battery_set: BatterySet) {
        match self
            .0
            .iter_mut()
            .find(|battery_set| battery_set.source_uuid == new_battery_set.source_uuid)
        {
            Some(battery_set) => *battery_set = new_battery_set,
            None => self.0.push(new_battery_set),
        }
    }

    pub fn remove_battery_set(&mut self, uuid: &String) {
        self.0.retain(|battery_set| &battery_set.source_uuid != uuid);
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }

    /// Returns the best BatterySet from among reported battery data.
    pub fn pick_best(&self) -> Option<BatterySet> {
        self.0
            .iter()
            .filter(|battery_set| !battery_set.batteries.is_empty())
            // Now we prefer BAS, but we might need to prioritize other sources first
            // TODO (b/295577710): Make a preference list
            .find_or_first(|battery_set| battery_set.source_uuid == uuid::BAS)
            .or_else(|| self.0.first())
            .cloned()
    }
}
