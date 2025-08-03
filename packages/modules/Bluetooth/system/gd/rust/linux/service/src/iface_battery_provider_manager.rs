use btstack::battery_manager::BatterySet;
use btstack::battery_provider_manager::{IBatteryProviderCallback, IBatteryProviderManager};
use btstack::RPCProxy;
use dbus::strings::Path;
use dbus_macros::{dbus_method, dbus_proxy_obj, generate_dbus_exporter};
use dbus_projection::prelude::*;

use crate::dbus_arg::DBusArg;

struct IBatteryProviderCallbackDBus {}

#[dbus_proxy_obj(BatteryProviderCallback, "org.chromium.bluetooth.BatteryProviderCallback")]
impl IBatteryProviderCallback for IBatteryProviderCallbackDBus {
    #[dbus_method("RefreshBatteryInfo")]
    fn refresh_battery_info(&mut self) {
        dbus_generated!()
    }
}

struct IBatteryProviderManagerDBus {}

#[generate_dbus_exporter(
    export_battery_provider_manager_dbus_intf,
    "org.chromium.bluetooth.BatteryProviderManager"
)]
impl IBatteryProviderManager for IBatteryProviderManagerDBus {
    #[dbus_method("RegisterBatteryProvider")]
    fn register_battery_provider(
        &mut self,
        battery_provider_callback: Box<dyn IBatteryProviderCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterBatteryProvider")]
    fn unregister_battery_provider(&mut self, battery_provider_id: u32) {
        dbus_generated!()
    }

    #[dbus_method("SetBatteryInfo")]
    fn set_battery_info(&mut self, battery_provider_id: u32, battery_set: BatterySet) {
        dbus_generated!()
    }

    #[dbus_method("RemoveBatteryInfo")]
    fn remove_battery_info(&mut self, battery_provider_id: u32, address: String, uuid: String) {
        dbus_generated!()
    }
}
