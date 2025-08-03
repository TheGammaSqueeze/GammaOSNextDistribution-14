use btstack::bluetooth_qa::{IBluetoothQA, IBluetoothQACallback};

use bt_topshim::btif::BtDiscMode;
use dbus_macros::{dbus_method, dbus_proxy_obj, generate_dbus_exporter};
use dbus_projection::prelude::*;

use crate::dbus_arg::DBusArg;
use bt_topshim::btif::BtStatus;
use bt_topshim::profiles::hid_host::BthhReportType;
use btstack::RPCProxy;
use dbus::Path;

struct IBluetoothQACallbackDBus {}
struct IBluetoothQADBus {}

#[generate_dbus_exporter(export_bluetooth_qa_dbus_intf, "org.chromium.bluetooth.BluetoothQA")]
impl IBluetoothQA for IBluetoothQADBus {
    #[dbus_method("RegisterQACallback")]
    fn register_qa_callback(&mut self, callback: Box<dyn IBluetoothQACallback + Send>) -> u32 {
        dbus_generated!()
    }
    #[dbus_method("UnregisterQACallback")]
    fn unregister_qa_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }
    #[dbus_method("AddMediaPlayer")]
    fn add_media_player(&self, name: String, browsing_supported: bool) {
        dbus_generated!()
    }
    #[dbus_method("RfcommSendMsc")]
    fn rfcomm_send_msc(&self, dlci: u8, addr: String) {
        dbus_generated!()
    }
    #[dbus_method("FetchDiscoverableMode")]
    fn fetch_discoverable_mode(&self) {
        dbus_generated!()
    }
    #[dbus_method("FetchConnectable")]
    fn fetch_connectable(&self) {
        dbus_generated!()
    }
    #[dbus_method("SetConnectable")]
    fn set_connectable(&self, mode: bool) {
        dbus_generated!()
    }
    #[dbus_method("FetchAlias")]
    fn fetch_alias(&self) {
        dbus_generated!()
    }
    #[dbus_method("GetModalias")]
    fn get_modalias(&self) -> String {
        dbus_generated!()
    }
    #[dbus_method("FetchHIDReport")]
    fn get_hid_report(&self, addr: String, report_type: BthhReportType, report_id: u8) {
        dbus_generated!()
    }
    #[dbus_method("SetHIDReport")]
    fn set_hid_report(&self, addr: String, report_type: BthhReportType, report: String) {
        dbus_generated!()
    }
    #[dbus_method("SendHIDData")]
    fn send_hid_data(&self, addr: String, data: String) {
        dbus_generated!()
    }
}

#[dbus_proxy_obj(QACallback, "org.chromium.bluetooth.QACallback")]
impl IBluetoothQACallback for IBluetoothQACallbackDBus {
    #[dbus_method("OnFetchDiscoverableModeComplete")]
    fn on_fetch_discoverable_mode_completed(&mut self, disc_mode: BtDiscMode) {
        dbus_generated!()
    }
    #[dbus_method("OnFetchConnectableComplete")]
    fn on_fetch_connectable_completed(&mut self, connectable: bool) {
        dbus_generated!()
    }
    #[dbus_method("OnSetConnectableComplete")]
    fn on_set_connectable_completed(&mut self, succeed: bool) {
        dbus_generated!()
    }
    #[dbus_method("OnFetchAliasComplete")]
    fn on_fetch_alias_completed(&mut self, alias: String) {
        dbus_generated!()
    }
    #[dbus_method("OnGetHIDReportComplete")]
    fn on_get_hid_report_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
    #[dbus_method("OnSetHIDReportComplete")]
    fn on_set_hid_report_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
    #[dbus_method("OnSendHIDDataComplete")]
    fn on_send_hid_data_completed(&mut self, status: BtStatus) {
        dbus_generated!()
    }
}
