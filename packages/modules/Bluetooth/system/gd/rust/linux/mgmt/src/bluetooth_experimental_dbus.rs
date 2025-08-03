use dbus_macros::{dbus_method, generate_dbus_exporter};
use dbus_projection::prelude::*;

use crate::dbus_arg::DBusArg;
use crate::iface_bluetooth_experimental::IBluetoothExperimental;

use crate::iface_bluetooth_manager::BluetoothManagerMixin;

/// D-Bus projection of IBluetoothExperimental.
struct BluetoothExperimentalDBus {}

#[generate_dbus_exporter(
    export_bluetooth_experimental_dbus_intf,
    "org.chromium.bluetooth.Experimental",
    BluetoothManagerMixin,
    experimental
)]
impl IBluetoothExperimental for BluetoothExperimentalDBus {
    #[dbus_method("SetLLPrivacy")]
    fn set_ll_privacy(&mut self, enabled: bool) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetDevCoredump")]
    fn set_devcoredump(&mut self, enabled: bool) -> bool {
        dbus_generated!()
    }
}
