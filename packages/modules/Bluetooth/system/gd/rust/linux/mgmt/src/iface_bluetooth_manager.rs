use btstack::RPCProxy;

use std::sync::{Arc, Mutex};

use crate::bluetooth_manager::BluetoothManager;

#[derive(Debug, Default)]
pub struct AdapterWithEnabled {
    pub hci_interface: i32,
    pub enabled: bool,
}

/// A mixin of the several interfaces. The naming of the fields in the mixin must match
/// what is listed in the `generate_dbus_exporter` invocation.
pub struct BluetoothManagerMixin {
    pub manager: Arc<Mutex<Box<BluetoothManager>>>,
    pub experimental: Arc<Mutex<Box<BluetoothManager>>>,
}

/// Bluetooth stack management API.
pub trait IBluetoothManager {
    /// Starts the Bluetooth stack.
    fn start(&mut self, hci_interface: i32);

    /// Stops the Bluetooth stack.
    fn stop(&mut self, hci_interface: i32);

    /// Returns whether an adapter is enabled.
    fn get_adapter_enabled(&mut self, hci_interface: i32) -> bool;

    /// Registers a callback to the Bluetooth manager state.
    fn register_callback(&mut self, callback: Box<dyn IBluetoothManagerCallback + Send>);

    /// Returns whether Floss is enabled.
    fn get_floss_enabled(&mut self) -> bool;

    /// Enables/disables Floss.
    fn set_floss_enabled(&mut self, enabled: bool);

    /// Returns a list of available HCI devices and if they are enabled.
    fn get_available_adapters(&mut self) -> Vec<AdapterWithEnabled>;

    /// Get the default adapter to use for activity. The default adapter should
    /// be used for all device management and will be the |desired_adapter|, if
    /// present/enabled on the system, or the lowest numbered hci interface otherwise.
    fn get_default_adapter(&mut self) -> i32;

    /// Set the preferred default adapter.
    fn set_desired_default_adapter(&mut self, hci_interface: i32);

    /// Returns Floss API verion.The MSB 16-bit is the major version and
    /// LSB 16-bit is the minor version
    fn get_floss_api_version(&mut self) -> u32;

    /// Set the tablet mode of the device. The device that is in tablet mode does not allow
    /// wakeup by the HID devices.
    fn set_tablet_mode(&mut self, tablet_mode: bool);
}

/// Interface of Bluetooth Manager callbacks.
pub trait IBluetoothManagerCallback: RPCProxy {
    /// HCI device presence has changed.
    fn on_hci_device_changed(&mut self, hci_interface: i32, present: bool);

    /// HCI device is enabled or disabled.
    fn on_hci_enabled_changed(&mut self, hci_interface: i32, enabled: bool);

    /// The default adapter has changed. At start-up, if the default adapter is
    /// already available, this won't be sent out. This will only be sent in two
    /// cases:
    ///   * Default adapter is no longer available and we need to use a backup.
    ///   * Desired default adapter re-appears and we should switch back.
    fn on_default_adapter_changed(&mut self, hci_interface: i32);
}
