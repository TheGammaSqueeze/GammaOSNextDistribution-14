use crate::dbus_arg::DBusArg;

use btstack::bluetooth_logging::IBluetoothLogging;
use dbus_macros::{dbus_method, generate_dbus_exporter};
use dbus_projection::prelude::*;

struct IBluetoothLoggingDBus {}

#[generate_dbus_exporter(export_bluetooth_logging_dbus_intf, "org.chromium.bluetooth.Logging")]
impl IBluetoothLogging for IBluetoothLoggingDBus {
    #[dbus_method("IsDebugEnabled")]
    fn is_debug_enabled(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetDebugLogging")]
    fn set_debug_logging(&mut self, enabled: bool) {
        dbus_generated!()
    }
}
