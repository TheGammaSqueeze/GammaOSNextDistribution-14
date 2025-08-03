use dbus::{channel::MatchingReceiver, message::MatchRule, nonblock::SyncConnection};
use dbus_crossroads::Crossroads;
use dbus_projection::DisconnectWatcher;

use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::{channel, Receiver, Sender};

use btstack::{
    battery_manager::BatteryManager, battery_provider_manager::BatteryProviderManager,
    battery_service::BatteryService, bluetooth::Bluetooth, bluetooth::IBluetooth,
    bluetooth_admin::BluetoothAdmin, bluetooth_gatt::BluetoothGatt,
    bluetooth_logging::BluetoothLogging, bluetooth_media::BluetoothMedia,
    bluetooth_qa::BluetoothQA, socket_manager::BluetoothSocketManager, suspend::Suspend,
    APIMessage, BluetoothAPI, Message,
};

use crate::iface_battery_manager;
use crate::iface_battery_provider_manager;
use crate::iface_bluetooth;
use crate::iface_bluetooth_admin;
use crate::iface_bluetooth_gatt;
use crate::iface_bluetooth_media;
use crate::iface_bluetooth_qa;
use crate::iface_bluetooth_telephony;
use crate::iface_logging;

pub(crate) struct InterfaceManager {}

impl InterfaceManager {
    fn make_object_name(idx: i32, name: &str) -> String {
        String::from(format!("/org/chromium/bluetooth/hci{}/{}", idx, name))
    }

    /// Creates an mpsc channel for passing messages to the main dispatch loop.
    pub fn create_channel() -> (Sender<APIMessage>, Receiver<APIMessage>) {
        channel::<APIMessage>(1)
    }

    /// Runs the dispatch loop for APIMessage
    ///
    /// # Arguments
    ///
    /// * `rx` - The receiver channel for APIMessage
    /// * `tx` - The sender channel for Message
    /// * `virt_index` - The virtual index of the adapter
    /// * `conn` - The DBus connection
    /// * `conn_join_handle` - The thread handle that's maintaining the DBus resource
    /// * `disconnect_watcher` - DisconnectWatcher to monitor client disconnects
    /// * `bluetooth` - Implementation of the Bluetooth API
    /// other implementations follow.
    ///
    pub async fn dispatch(
        mut rx: Receiver<APIMessage>,
        tx: Sender<Message>,
        virt_index: i32,
        conn: Arc<SyncConnection>,
        conn_join_handle: tokio::task::JoinHandle<()>,
        disconnect_watcher: Arc<Mutex<DisconnectWatcher>>,
        bluetooth: Arc<Mutex<Box<Bluetooth>>>,
        bluetooth_admin: Arc<Mutex<Box<BluetoothAdmin>>>,
        bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,
        battery_service: Arc<Mutex<Box<BatteryService>>>,
        battery_manager: Arc<Mutex<Box<BatteryManager>>>,
        battery_provider_manager: Arc<Mutex<Box<BatteryProviderManager>>>,
        bluetooth_media: Arc<Mutex<Box<BluetoothMedia>>>,
        bluetooth_qa: Arc<Mutex<Box<BluetoothQA>>>,
        bt_sock_mgr: Arc<Mutex<Box<BluetoothSocketManager>>>,
        suspend: Arc<Mutex<Box<Suspend>>>,
        logging: Arc<Mutex<Box<BluetoothLogging>>>,
    ) {
        // Prepare D-Bus interfaces.
        let cr = Arc::new(Mutex::new(Crossroads::new()));
        cr.lock().unwrap().set_async_support(Some((
            conn.clone(),
            Box::new(|x| {
                tokio::spawn(x);
            }),
        )));

        // Announce the exported adapter objects so that clients can properly detect the readiness
        // of the adapter APIs.
        cr.lock().unwrap().set_object_manager_support(Some(conn.clone()));
        let object_manager = cr.lock().unwrap().object_manager();
        cr.lock().unwrap().insert("/", &[object_manager], {});

        // Set up handling of D-Bus methods. This must be done before exporting interfaces so that
        // clients that rely on InterfacesAdded signal can rely on us being ready to handle methods
        // on those exported interfaces.
        let cr_clone = cr.clone();
        conn.start_receive(
            MatchRule::new_method_call(),
            Box::new(move |msg, conn| {
                cr_clone.lock().unwrap().handle_message(msg, conn).unwrap();
                true
            }),
        );

        // Register D-Bus method handlers of IBluetooth.
        let adapter_iface = iface_bluetooth::export_bluetooth_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );
        let qa_iface = iface_bluetooth_qa::export_bluetooth_qa_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );
        let qa_legacy_iface = iface_bluetooth::export_bluetooth_qa_legacy_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );
        let socket_mgr_iface = iface_bluetooth::export_socket_mgr_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );
        let suspend_iface = iface_bluetooth::export_suspend_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );
        let logging_iface = iface_logging::export_bluetooth_logging_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        // Register D-Bus method handlers of IBluetoothGatt.
        let gatt_iface = iface_bluetooth_gatt::export_bluetooth_gatt_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        let media_iface = iface_bluetooth_media::export_bluetooth_media_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        let telephony_iface = iface_bluetooth_telephony::export_bluetooth_telephony_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        let battery_provider_manager_iface =
            iface_battery_provider_manager::export_battery_provider_manager_dbus_intf(
                conn.clone(),
                &mut cr.lock().unwrap(),
                disconnect_watcher.clone(),
            );

        let battery_manager_iface = iface_battery_manager::export_battery_manager_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        let admin_iface = iface_bluetooth_admin::export_bluetooth_admin_dbus_intf(
            conn.clone(),
            &mut cr.lock().unwrap(),
            disconnect_watcher.clone(),
        );

        // Create mixin object for Bluetooth + Suspend interfaces.
        let mixin = Box::new(iface_bluetooth::BluetoothMixin {
            adapter: bluetooth.clone(),
            qa: bluetooth.clone(),
            suspend: suspend.clone(),
            socket_mgr: bt_sock_mgr.clone(),
        });

        loop {
            let m = rx.recv().await;

            if m.is_none() {
                eprintln!("APIMessage dispatch loop quit");
                break;
            }

            match m.unwrap() {
                APIMessage::IsReady(api) => match api {
                    BluetoothAPI::Adapter => {
                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "adapter"),
                            &[adapter_iface, qa_legacy_iface, socket_mgr_iface, suspend_iface],
                            mixin.clone(),
                        );

                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "admin"),
                            &[admin_iface],
                            bluetooth_admin.clone(),
                        );

                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "logging"),
                            &[logging_iface],
                            logging.clone(),
                        );

                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "qa"),
                            &[qa_iface],
                            bluetooth_qa.clone(),
                        );

                        // AdvertiseManager selects the stack per is_le_ext_adv_supported.
                        // Initialize it after Adapter is ready.
                        let bt_clone = bluetooth.clone();
                        let gatt_clone = bluetooth_gatt.clone();
                        tokio::spawn(async move {
                            let is_le_ext_adv_supported =
                                bt_clone.lock().unwrap().is_le_extended_advertising_supported();
                            gatt_clone
                                .lock()
                                .unwrap()
                                .init_adv_manager(bt_clone, is_le_ext_adv_supported);
                        });
                    }
                    BluetoothAPI::Gatt => {
                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "gatt"),
                            &[gatt_iface],
                            bluetooth_gatt.clone(),
                        );

                        // Battery service is on top of Gatt. Only initialize it after
                        // GATT is ready.
                        let bs = battery_service.clone();
                        tokio::spawn(async move {
                            bs.lock().unwrap().init();
                        });
                    }
                    BluetoothAPI::Media => {
                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "media"),
                            &[media_iface],
                            bluetooth_media.clone(),
                        );

                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "telephony"),
                            &[telephony_iface],
                            bluetooth_media.clone(),
                        );
                    }
                    BluetoothAPI::Battery => {
                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "battery_provider_manager"),
                            &[battery_provider_manager_iface],
                            battery_provider_manager.clone(),
                        );

                        cr.lock().unwrap().insert(
                            Self::make_object_name(virt_index, "battery_manager"),
                            &[battery_manager_iface],
                            battery_manager.clone(),
                        );
                    }
                },

                APIMessage::ShutDown => {
                    // To shut down the connection, call _handle.abort() and drop the connection.
                    conn_join_handle.abort();
                    drop(conn);

                    let tx = tx.clone();
                    tokio::spawn(async move {
                        let _ = tx.send(Message::AdapterShutdown).await;
                    });
                    break;
                }
            }
        }
    }
}
