//! Floss Bluetooth stack.
//!
//! This crate provides the API implementation of the Fluoride/GD Bluetooth
//! stack, independent of any RPC projection.

pub mod async_helper;
pub mod battery_manager;
pub mod battery_provider_manager;
pub mod battery_service;
pub mod bluetooth;
pub mod bluetooth_admin;
pub mod bluetooth_adv;
pub mod bluetooth_gatt;
pub mod bluetooth_logging;
pub mod bluetooth_media;
pub mod bluetooth_qa;
pub mod callbacks;
pub mod dis;
pub mod socket_manager;
pub mod suspend;
pub mod uuid;

use bluetooth_qa::{BluetoothQA, IBluetoothQA};
use log::debug;
use num_derive::{FromPrimitive, ToPrimitive};
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::channel;
use tokio::sync::mpsc::{Receiver, Sender};

use crate::battery_manager::{BatteryManager, BatterySet};
use crate::battery_provider_manager::BatteryProviderManager;
use crate::battery_service::{BatteryService, BatteryServiceActions};
use crate::bluetooth::{
    dispatch_base_callbacks, dispatch_hid_host_callbacks, dispatch_sdp_callbacks, Bluetooth,
    BluetoothDevice, DelayedActions, IBluetooth,
};
use crate::bluetooth_admin::{BluetoothAdmin, IBluetoothAdmin};
use crate::bluetooth_adv::{dispatch_le_adv_callbacks, AdvertiserActions};
use crate::bluetooth_gatt::{
    dispatch_gatt_client_callbacks, dispatch_gatt_server_callbacks, dispatch_le_scanner_callbacks,
    dispatch_le_scanner_inband_callbacks, BluetoothGatt, GattActions,
};
use crate::bluetooth_media::{BluetoothMedia, MediaActions};
use crate::dis::{DeviceInformation, ServiceCallbacks};
use crate::socket_manager::{BluetoothSocketManager, SocketActions};
use crate::suspend::Suspend;
use bt_topshim::{
    btif::{BaseCallbacks, BtTransport},
    profiles::{
        a2dp::A2dpCallbacks,
        avrcp::AvrcpCallbacks,
        gatt::GattAdvCallbacks,
        gatt::GattAdvInbandCallbacks,
        gatt::GattClientCallbacks,
        gatt::GattScannerCallbacks,
        gatt::GattScannerInbandCallbacks,
        gatt::GattServerCallbacks,
        hfp::HfpCallbacks,
        hid_host::{BthhReportType, HHCallbacks},
        sdp::SdpCallbacks,
    },
};

/// Message types that are sent to the stack main dispatch loop.
pub enum Message {
    /// Remove the DBus API. Call it before other AdapterShutdown.
    InterfaceShutdown,
    /// Disable the adapter by calling btif disable.
    AdapterShutdown,
    /// Clean up the adapter by calling btif cleanup.
    Cleanup,

    // Adapter is enabled and ready.
    AdapterReady,

    // Callbacks from libbluetooth
    A2dp(A2dpCallbacks),
    Avrcp(AvrcpCallbacks),
    Base(BaseCallbacks),
    GattClient(GattClientCallbacks),
    GattServer(GattServerCallbacks),
    LeScanner(GattScannerCallbacks),
    LeScannerInband(GattScannerInbandCallbacks),
    LeAdvInband(GattAdvInbandCallbacks),
    LeAdv(GattAdvCallbacks),
    HidHost(HHCallbacks),
    Hfp(HfpCallbacks),
    Sdp(SdpCallbacks),

    // Actions within the stack
    Media(MediaActions),
    MediaCallbackDisconnected(u32),
    TelephonyCallbackDisconnected(u32),

    // Client callback disconnections
    AdapterCallbackDisconnected(u32),
    ConnectionCallbackDisconnected(u32),

    // Some delayed actions for the adapter.
    DelayedAdapterActions(DelayedActions),

    // Follows IBluetooth's on_device_(dis)connected callback but doesn't require depending on
    // Bluetooth.
    OnAclConnected(BluetoothDevice, BtTransport),
    OnAclDisconnected(BluetoothDevice),

    // Suspend related
    SuspendCallbackRegistered(u32),
    SuspendCallbackDisconnected(u32),
    SuspendReady(i32),
    ResumeReady(i32),
    AudioReconnectOnResumeComplete,

    // Scanner related
    ScannerCallbackDisconnected(u32),

    // Advertising related
    AdvertiserCallbackDisconnected(u32),
    AdvertiserActions(AdvertiserActions),

    SocketManagerActions(SocketActions),
    SocketManagerCallbackDisconnected(u32),

    // Battery related
    BatteryProviderManagerCallbackDisconnected(u32),
    BatteryProviderManagerBatteryUpdated(String, BatterySet),
    BatteryServiceCallbackDisconnected(u32),
    BatteryService(BatteryServiceActions),
    BatteryServiceRefresh,
    BatteryManagerCallbackDisconnected(u32),

    GattActions(GattActions),
    GattClientCallbackDisconnected(u32),
    GattServerCallbackDisconnected(u32),

    // Admin policy related
    AdminCallbackDisconnected(u32),
    HidHostEnable,
    AdminPolicyChanged,

    // Dis callbacks
    Dis(ServiceCallbacks),

    // Device removal
    DisconnectDevice(BluetoothDevice),

    // Qualification Only
    QaCallbackDisconnected(u32),
    QaAddMediaPlayer(String, bool),
    QaRfcommSendMsc(u8, String),
    QaFetchDiscoverableMode,
    QaFetchConnectable,
    QaSetConnectable(bool),
    QaFetchAlias,
    QaGetHidReport(String, BthhReportType, u8),
    QaSetHidReport(String, BthhReportType, String),
    QaSendHidData(String, String),

    // UHid callbacks
    UHidHfpOutputCallback(String, u8, u8),
    UHidTelephonyUseCallback(String, bool),
}

pub enum BluetoothAPI {
    Adapter,
    Battery,
    Media,
    Gatt,
}

/// Message types that are sent to the InterfaceManager's dispatch loop.
pub enum APIMessage {
    /// Indicates a subcomponent is ready to receive DBus messages.
    IsReady(BluetoothAPI),
    /// Indicates bluetooth is shutting down, so we remove all DBus endpoints.
    ShutDown,
}

/// Represents suspend mode of a module.
///
/// Being in suspend mode means that the module pauses some activities if required for suspend and
/// some subsequent API calls will be blocked with a retryable error.
#[derive(FromPrimitive, ToPrimitive, Debug, PartialEq, Clone)]
pub enum SuspendMode {
    Normal = 0,
    Suspending = 1,
    Suspended = 2,
    Resuming = 3,
}

/// Umbrella class for the Bluetooth stack.
pub struct Stack {}

impl Stack {
    /// Creates an mpsc channel for passing messages to the main dispatch loop.
    pub fn create_channel() -> (Sender<Message>, Receiver<Message>) {
        channel::<Message>(1)
    }

    /// Runs the main dispatch loop.
    pub async fn dispatch(
        mut rx: Receiver<Message>,
        api_tx: Sender<APIMessage>,
        bluetooth: Arc<Mutex<Box<Bluetooth>>>,
        bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,
        battery_service: Arc<Mutex<Box<BatteryService>>>,
        battery_manager: Arc<Mutex<Box<BatteryManager>>>,
        battery_provider_manager: Arc<Mutex<Box<BatteryProviderManager>>>,
        bluetooth_media: Arc<Mutex<Box<BluetoothMedia>>>,
        suspend: Arc<Mutex<Box<Suspend>>>,
        bluetooth_socketmgr: Arc<Mutex<Box<BluetoothSocketManager>>>,
        bluetooth_admin: Arc<Mutex<Box<BluetoothAdmin>>>,
        bluetooth_dis: Arc<Mutex<Box<DeviceInformation>>>,
        bluetooth_qa: Arc<Mutex<Box<BluetoothQA>>>,
    ) {
        loop {
            let m = rx.recv().await;

            if m.is_none() {
                eprintln!("Message dispatch loop quit");
                break;
            }

            match m.unwrap() {
                Message::InterfaceShutdown => {
                    let tx = api_tx.clone();
                    tokio::spawn(async move {
                        let _ = tx.send(APIMessage::ShutDown).await;
                    });
                }

                Message::AdapterShutdown => {
                    bluetooth.lock().unwrap().disable();
                }

                Message::Cleanup => {
                    bluetooth.lock().unwrap().cleanup();
                }

                Message::AdapterReady => {
                    // Initialize objects that need the adapter to be fully
                    // enabled before running.

                    // Register device information service.
                    bluetooth_dis.lock().unwrap().initialize();
                }

                Message::A2dp(a) => {
                    bluetooth_media.lock().unwrap().dispatch_a2dp_callbacks(a);
                }

                Message::Avrcp(av) => {
                    bluetooth_media.lock().unwrap().dispatch_avrcp_callbacks(av);
                }

                Message::Base(b) => {
                    dispatch_base_callbacks(bluetooth.lock().unwrap().as_mut(), b.clone());
                    dispatch_base_callbacks(suspend.lock().unwrap().as_mut(), b);
                }

                Message::GattClient(m) => {
                    dispatch_gatt_client_callbacks(bluetooth_gatt.lock().unwrap().as_mut(), m);
                }

                Message::GattServer(m) => {
                    dispatch_gatt_server_callbacks(bluetooth_gatt.lock().unwrap().as_mut(), m);
                }

                Message::LeScanner(m) => {
                    dispatch_le_scanner_callbacks(bluetooth_gatt.lock().unwrap().as_mut(), m);
                }

                Message::LeScannerInband(m) => {
                    dispatch_le_scanner_inband_callbacks(
                        bluetooth_gatt.lock().unwrap().as_mut(),
                        m,
                    );
                }

                Message::LeAdvInband(m) => {
                    debug!("Received LeAdvInband message: {:?}. This is unexpected!", m);
                }

                Message::LeAdv(m) => {
                    dispatch_le_adv_callbacks(bluetooth_gatt.lock().unwrap().as_mut(), m);
                }

                Message::Hfp(hf) => {
                    bluetooth_media.lock().unwrap().dispatch_hfp_callbacks(hf);
                }

                Message::HidHost(h) => {
                    dispatch_hid_host_callbacks(bluetooth.lock().unwrap().as_mut(), h);
                }

                Message::Sdp(s) => {
                    dispatch_sdp_callbacks(bluetooth.lock().unwrap().as_mut(), s);
                }

                Message::Media(action) => {
                    bluetooth_media.lock().unwrap().dispatch_media_actions(action);
                }

                Message::MediaCallbackDisconnected(cb_id) => {
                    bluetooth_media.lock().unwrap().remove_callback(cb_id);
                }

                Message::TelephonyCallbackDisconnected(cb_id) => {
                    bluetooth_media.lock().unwrap().remove_telephony_callback(cb_id);
                }

                Message::AdapterCallbackDisconnected(id) => {
                    bluetooth.lock().unwrap().adapter_callback_disconnected(id);
                }

                Message::ConnectionCallbackDisconnected(id) => {
                    bluetooth.lock().unwrap().connection_callback_disconnected(id);
                }

                Message::DelayedAdapterActions(action) => {
                    bluetooth.lock().unwrap().handle_delayed_actions(action);
                }

                // Any service needing an updated list of devices can have an
                // update method triggered from here rather than needing a
                // reference to Bluetooth.
                Message::OnAclConnected(device, transport) => {
                    battery_service
                        .lock()
                        .unwrap()
                        .handle_action(BatteryServiceActions::Connect(device, transport));
                }

                // For battery service, use this to clean up internal handles. GATT connection is
                // already dropped if ACL disconnect has occurred.
                Message::OnAclDisconnected(device) => {
                    battery_service
                        .lock()
                        .unwrap()
                        .handle_action(BatteryServiceActions::Disconnect(device));
                }

                Message::SuspendCallbackRegistered(id) => {
                    suspend.lock().unwrap().callback_registered(id);
                }

                Message::SuspendCallbackDisconnected(id) => {
                    suspend.lock().unwrap().remove_callback(id);
                }

                Message::SuspendReady(suspend_id) => {
                    suspend.lock().unwrap().suspend_ready(suspend_id);
                }

                Message::ResumeReady(suspend_id) => {
                    suspend.lock().unwrap().resume_ready(suspend_id);
                }

                Message::AudioReconnectOnResumeComplete => {
                    suspend.lock().unwrap().audio_reconnect_complete();
                }

                Message::ScannerCallbackDisconnected(id) => {
                    bluetooth_gatt.lock().unwrap().remove_scanner_callback(id);
                }

                Message::AdvertiserCallbackDisconnected(id) => {
                    bluetooth_gatt.lock().unwrap().remove_adv_callback(id);
                }

                Message::AdvertiserActions(action) => {
                    bluetooth_gatt.lock().unwrap().handle_adv_action(action);
                }

                Message::SocketManagerActions(action) => {
                    bluetooth_socketmgr.lock().unwrap().handle_actions(action);
                }
                Message::SocketManagerCallbackDisconnected(id) => {
                    bluetooth_socketmgr.lock().unwrap().remove_callback(id);
                }
                Message::BatteryProviderManagerBatteryUpdated(remote_address, battery_set) => {
                    battery_manager
                        .lock()
                        .unwrap()
                        .handle_battery_updated(remote_address, battery_set);
                }
                Message::BatteryProviderManagerCallbackDisconnected(id) => {
                    battery_provider_manager.lock().unwrap().remove_battery_provider_callback(id);
                }
                Message::BatteryServiceCallbackDisconnected(id) => {
                    battery_service.lock().unwrap().remove_callback(id);
                }
                Message::BatteryService(action) => {
                    battery_service.lock().unwrap().handle_action(action);
                }
                Message::BatteryServiceRefresh => {
                    battery_service.lock().unwrap().refresh_all_devices();
                }
                Message::BatteryManagerCallbackDisconnected(id) => {
                    battery_manager.lock().unwrap().remove_callback(id);
                }
                Message::GattActions(action) => {
                    bluetooth_gatt.lock().unwrap().handle_action(action);
                }
                Message::GattClientCallbackDisconnected(id) => {
                    bluetooth_gatt.lock().unwrap().remove_client_callback(id);
                }
                Message::GattServerCallbackDisconnected(id) => {
                    bluetooth_gatt.lock().unwrap().remove_server_callback(id);
                }
                Message::AdminCallbackDisconnected(id) => {
                    bluetooth_admin.lock().unwrap().unregister_admin_policy_callback(id);
                }
                Message::HidHostEnable => {
                    bluetooth.lock().unwrap().enable_hidhost();
                }
                Message::AdminPolicyChanged => {
                    bluetooth_socketmgr.lock().unwrap().handle_admin_policy_changed();
                }
                Message::Dis(callback) => {
                    bluetooth_dis.lock().unwrap().handle_callbacks(&callback);
                }
                Message::DisconnectDevice(addr) => {
                    bluetooth.lock().unwrap().disconnect_all_enabled_profiles(addr);
                }
                // Qualification Only
                Message::QaAddMediaPlayer(name, browsing_supported) => {
                    bluetooth_media.lock().unwrap().add_player(name, browsing_supported);
                }
                Message::QaRfcommSendMsc(dlci, addr) => {
                    bluetooth_socketmgr.lock().unwrap().rfcomm_send_msc(dlci, addr);
                }
                Message::QaCallbackDisconnected(id) => {
                    bluetooth_qa.lock().unwrap().unregister_qa_callback(id);
                }
                Message::QaFetchDiscoverableMode => {
                    let mode = bluetooth.lock().unwrap().get_discoverable_mode_internal();
                    bluetooth_qa.lock().unwrap().on_fetch_discoverable_mode_completed(mode);
                }
                Message::QaFetchConnectable => {
                    let connectable = bluetooth.lock().unwrap().get_connectable_internal();
                    bluetooth_qa.lock().unwrap().on_fetch_connectable_completed(connectable);
                }
                Message::QaSetConnectable(mode) => {
                    let succeed = bluetooth.lock().unwrap().set_connectable_internal(mode);
                    bluetooth_qa.lock().unwrap().on_set_connectable_completed(succeed);
                }
                Message::QaFetchAlias => {
                    let alias = bluetooth.lock().unwrap().get_alias_internal();
                    bluetooth_qa.lock().unwrap().on_fetch_alias_completed(alias);
                }
                Message::QaGetHidReport(addr, report_type, report_id) => {
                    let status = bluetooth.lock().unwrap().get_hid_report_internal(
                        addr,
                        report_type,
                        report_id,
                    );
                    bluetooth_qa.lock().unwrap().on_get_hid_report_completed(status);
                }
                Message::QaSetHidReport(addr, report_type, report) => {
                    let status = bluetooth.lock().unwrap().set_hid_report_internal(
                        addr,
                        report_type,
                        report,
                    );
                    bluetooth_qa.lock().unwrap().on_set_hid_report_completed(status);
                }
                Message::QaSendHidData(addr, data) => {
                    let status = bluetooth.lock().unwrap().send_hid_data_internal(addr, data);
                    bluetooth_qa.lock().unwrap().on_send_hid_data_completed(status);
                }

                // UHid callbacks
                Message::UHidHfpOutputCallback(addr, id, data) => {
                    bluetooth_media
                        .lock()
                        .unwrap()
                        .dispatch_uhid_hfp_output_callback(addr, id, data);
                }

                Message::UHidTelephonyUseCallback(addr, state) => {
                    bluetooth_media
                        .lock()
                        .unwrap()
                        .dispatch_uhid_telephony_use_callback(addr, state);
                }
            }
        }
    }
}

/// Signifies that the object may be a proxy to a remote RPC object.
///
/// An object that implements RPCProxy trait signifies that the object may be a proxy to a remote
/// RPC object. Therefore the object may be disconnected and thus should implement
/// `register_disconnect` to let others observe the disconnection event.
pub trait RPCProxy {
    /// Registers disconnect observer that will be notified when the remote object is disconnected.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }

    /// Returns the ID of the object. For example this would be an object path in D-Bus RPC.
    fn get_object_id(&self) -> String {
        String::from("")
    }

    /// Unregisters callback with this id.
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }

    /// Makes this object available for remote call.
    fn export_for_rpc(self: Box<Self>) {}
}
