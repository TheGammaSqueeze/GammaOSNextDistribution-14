use btstack::battery_manager::{Battery, BatterySet, IBatteryManager, IBatteryManagerCallback};
use btstack::RPCProxy;
use dbus::arg::RefArg;
use dbus::strings::Path;
use dbus_macros::{dbus_method, dbus_propmap, dbus_proxy_obj, generate_dbus_exporter};
use dbus_projection::prelude::*;

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

#[dbus_propmap(BatterySet)]
pub struct BatterySetDBus {
    address: String,
    source_uuid: String,
    source_info: String,
    batteries: Vec<Battery>,
}

#[dbus_propmap(Battery)]
pub struct BatteryDBus {
    percentage: u32,
    variant: String,
}

struct IBatteryManagerCallbackDBus {}

#[dbus_proxy_obj(BatteryManagerCallback, "org.chromium.bluetooth.BatteryManagerCallback")]
impl IBatteryManagerCallback for IBatteryManagerCallbackDBus {
    #[dbus_method("OnBatteryInfoUpdated")]
    fn on_battery_info_updated(&mut self, remote_address: String, battery_set: BatterySet) {
        dbus_generated!()
    }
}

struct IBatteryManagerDBus {}

#[generate_dbus_exporter(export_battery_manager_dbus_intf, "org.chromium.bluetooth.BatteryManager")]
impl IBatteryManager for IBatteryManagerDBus {
    #[dbus_method("RegisterBatteryCallback")]
    fn register_battery_callback(
        &mut self,
        battery_manager_callback: Box<dyn IBatteryManagerCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterBatteryCallback")]
    fn unregister_battery_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBatteryInformation")]
    fn get_battery_information(&self, remote_address: String) -> Option<BatterySet> {
        dbus_generated!()
    }
}
