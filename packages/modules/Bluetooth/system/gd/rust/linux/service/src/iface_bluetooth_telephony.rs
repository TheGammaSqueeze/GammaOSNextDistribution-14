use btstack::bluetooth_media::{IBluetoothTelephony, IBluetoothTelephonyCallback};
use btstack::RPCProxy;

use dbus::Path;

use dbus_macros::{dbus_method, dbus_proxy_obj, generate_dbus_exporter};

use dbus_projection::prelude::*;

use crate::dbus_arg::DBusArg;

#[allow(dead_code)]
struct BluetoothTelephonyCallbackDBus {}

#[dbus_proxy_obj(BluetoothTelephonyCallback, "org.chromium.bluetooth.BluetoothTelephonyCallback")]
impl IBluetoothTelephonyCallback for BluetoothTelephonyCallbackDBus {
    #[dbus_method("OnTelephonyUse")]
    fn on_telephony_use(&mut self, addr: String, state: bool) {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct IBluetoothTelephonyDBus {}

#[generate_dbus_exporter(
    export_bluetooth_telephony_dbus_intf,
    "org.chromium.bluetooth.BluetoothTelephony"
)]
impl IBluetoothTelephony for IBluetoothTelephonyDBus {
    #[dbus_method("RegisterTelephonyCallback")]
    fn register_telephony_callback(
        &mut self,
        callback: Box<dyn IBluetoothTelephonyCallback + Send>,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetNetworkAvailable")]
    fn set_network_available(&mut self, network_available: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetRoaming")]
    fn set_roaming(&mut self, roaming: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetSignalStrength")]
    fn set_signal_strength(&mut self, signal_strength: i32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetBatteryLevel")]
    fn set_battery_level(&mut self, battery_level: i32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetPhoneOpsEnabled")]
    fn set_phone_ops_enabled(&mut self, enable: bool) {
        dbus_generated!()
    }
    #[dbus_method("SetMpsQualificationEnabled")]
    fn set_mps_qualification_enabled(&mut self, enable: bool) {
        dbus_generated!()
    }
    #[dbus_method("IncomingCall")]
    fn incoming_call(&mut self, number: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("DialingCall")]
    fn dialing_call(&mut self, number: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AnswerCall")]
    fn answer_call(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("HangupCall")]
    fn hangup_call(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetMemoryCall")]
    fn set_memory_call(&mut self, number: Option<String>) -> bool {
        dbus_generated!()
    }
    #[dbus_method("SetLastCall")]
    fn set_last_call(&mut self, number: Option<String>) -> bool {
        dbus_generated!()
    }
    #[dbus_method("ReleaseHeld")]
    fn release_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("ReleaseActiveAcceptHeld")]
    fn release_active_accept_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("HoldActiveAcceptHeld")]
    fn hold_active_accept_held(&mut self) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AudioConnect")]
    fn audio_connect(&mut self, address: String) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AudioDisconnect")]
    fn audio_disconnect(&mut self, address: String) {
        dbus_generated!()
    }
}
