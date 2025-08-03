use btstack::bluetooth_admin::{IBluetoothAdmin, IBluetoothAdminPolicyCallback, PolicyEffect};

use dbus::arg::RefArg;
use dbus::Path;
use dbus_macros::{dbus_method, dbus_propmap, dbus_proxy_obj, generate_dbus_exporter};

use dbus_projection::prelude::*;

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

use bt_topshim::btif::Uuid128Bit;

use btstack::bluetooth::BluetoothDevice;
use btstack::RPCProxy;

struct IBluetoothAdminDBus {}

struct IBluetoothAdminPolicyCallbackDBus {}

#[dbus_propmap(PolicyEffect)]
pub struct PolicyEffectDBus {
    pub service_blocked: Vec<Uuid128Bit>,
    pub affected: bool,
}

#[dbus_proxy_obj(AdminPolicyCallback, "org.chromium.bluetooth.AdminPolicyCallback")]
impl IBluetoothAdminPolicyCallback for IBluetoothAdminPolicyCallbackDBus {
    #[dbus_method("OnServiceAllowlistChanged")]
    fn on_service_allowlist_changed(&mut self, allowlist: Vec<Uuid128Bit>) {
        dbus_generated!()
    }

    #[dbus_method("OnDevicePolicyEffectChanged")]
    fn on_device_policy_effect_changed(
        &mut self,
        device: BluetoothDevice,
        new_policy_effect: Option<PolicyEffect>,
    ) {
        dbus_generated!()
    }
}

#[generate_dbus_exporter(export_bluetooth_admin_dbus_intf, "org.chromium.bluetooth.BluetoothAdmin")]
impl IBluetoothAdmin for IBluetoothAdminDBus {
    #[dbus_method("IsServiceAllowed")]
    fn is_service_allowed(&self, uuid: Uuid128Bit) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetAllowedServices")]
    fn set_allowed_services(&mut self, services: Vec<Uuid128Bit>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetAllowedServices")]
    fn get_allowed_services(&self) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("GetDevicePolicyEffect")]
    fn get_device_policy_effect(&self, device: BluetoothDevice) -> Option<PolicyEffect> {
        dbus_generated!()
    }

    #[dbus_method("RegisterAdminPolicyCallback")]
    fn register_admin_policy_callback(
        &mut self,
        callback: Box<dyn IBluetoothAdminPolicyCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterAdminPolicyCallback")]
    fn unregister_admin_policy_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }
}
