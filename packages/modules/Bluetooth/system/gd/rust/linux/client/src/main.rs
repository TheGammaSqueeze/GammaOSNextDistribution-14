use clap::{value_t, App, Arg};

use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use dbus::channel::MatchingReceiver;
use dbus::message::MatchRule;
use dbus::nonblock::SyncConnection;
use dbus_crossroads::Crossroads;
use tokio::sync::mpsc;
use tokio::time::{sleep, timeout};

use crate::bt_adv::AdvSet;
use crate::bt_gatt::{GattClientContext, GattServerContext};
use crate::callbacks::{
    AdminCallback, AdvertisingSetCallback, BatteryManagerCallback, BtCallback,
    BtConnectionCallback, BtManagerCallback, BtSocketManagerCallback, MediaCallback, QACallback,
    ScannerCallback, SuspendCallback, TelephonyCallback,
};
use crate::command_handler::{CommandHandler, SocketSchedule};
use crate::dbus_iface::{
    BatteryManagerDBus, BluetoothAdminDBus, BluetoothDBus, BluetoothGattDBus, BluetoothManagerDBus,
    BluetoothMediaDBus, BluetoothQADBus, BluetoothQALegacyDBus, BluetoothSocketManagerDBus,
    BluetoothTelephonyDBus, SuspendDBus,
};
use crate::editor::AsyncEditor;
use bt_topshim::topstack;
use btstack::bluetooth::{BluetoothDevice, IBluetooth};
use btstack::suspend::ISuspend;
use manager_service::iface_bluetooth_manager::IBluetoothManager;

mod bt_adv;
mod bt_gatt;
mod callbacks;
mod command_handler;
mod console;
mod dbus_arg;
mod dbus_iface;
mod editor;

/// Context structure for the client. Used to keep track details about the active adapter and its
/// state.
pub(crate) struct ClientContext {
    /// List of adapters and whether they are enabled.
    pub(crate) adapters: HashMap<i32, bool>,

    // TODO(abps) - Change once we have multi-adapter support.
    /// The default adapter is also the active adapter. Defaults to 0.
    pub(crate) default_adapter: i32,

    /// Current adapter is enabled?
    pub(crate) enabled: bool,

    /// Current adapter is ready to be used?
    pub(crate) adapter_ready: bool,

    /// Current adapter address if known.
    pub(crate) adapter_address: Option<String>,

    /// Currently active bonding attempt. If it is not none, we are currently attempting to bond
    /// this device.
    pub(crate) bonding_attempt: Option<BluetoothDevice>,

    /// Is adapter discovering?
    pub(crate) discovering_state: bool,

    /// Devices found in current discovery session. List should be cleared when a new discovery
    /// session starts so that previous results don't pollute current search.
    pub(crate) found_devices: HashMap<String, BluetoothDevice>,

    /// List of bonded devices.
    pub(crate) bonded_devices: HashMap<String, BluetoothDevice>,

    /// Proxy for manager interface.
    pub(crate) manager_dbus: BluetoothManagerDBus,

    /// Proxy for adapter interface. Only exists when the default adapter is enabled.
    pub(crate) adapter_dbus: Option<BluetoothDBus>,

    /// Proxy for adapter QA Legacy interface. Only exists when the default adapter is enabled.
    pub(crate) qa_legacy_dbus: Option<BluetoothQALegacyDBus>,

    /// Proxy for adapter QA interface.
    pub(crate) qa_dbus: Option<BluetoothQADBus>,

    /// Proxy for GATT interface.
    pub(crate) gatt_dbus: Option<BluetoothGattDBus>,

    /// Proxy for Admin interface.
    pub(crate) admin_dbus: Option<BluetoothAdminDBus>,

    /// Proxy for suspend interface.
    pub(crate) suspend_dbus: Option<SuspendDBus>,

    /// Proxy for socket manager interface.
    pub(crate) socket_manager_dbus: Option<BluetoothSocketManagerDBus>,

    /// Proxy for Telephony interface.
    pub(crate) telephony_dbus: Option<BluetoothTelephonyDBus>,

    /// Proxy for Media interface.
    pub(crate) media_dbus: Option<BluetoothMediaDBus>,

    /// Proxy for battery manager interface.
    pub(crate) battery_manager_dbus: Option<BatteryManagerDBus>,

    /// Channel to send actions to take in the foreground
    fg: mpsc::Sender<ForegroundActions>,

    /// Internal DBus connection object.
    dbus_connection: Arc<SyncConnection>,

    /// Internal DBus crossroads object.
    dbus_crossroads: Arc<Mutex<Crossroads>>,

    /// Identifies the callback to receive IScannerCallback method calls.
    scanner_callback_id: Option<u32>,

    /// Identifies the callback to receive IAdvertisingSetCallback method calls.
    advertiser_callback_id: Option<u32>,

    /// Identifies the callback to receive IBluetoothAdminPolicyCallback method calls.
    admin_callback_id: Option<u32>,

    /// Keeps track of active LE scanners.
    active_scanner_ids: HashSet<u8>,

    /// Keeps track of advertising sets registered. Map from reg_id to AdvSet.
    adv_sets: HashMap<i32, AdvSet>,

    /// Identifies the callback to receive IBluetoothSocketManagerCallback method calls.
    socket_manager_callback_id: Option<u32>,

    /// Identifies the callback to receive IBluetoothQACallback method calls.
    qa_callback_id: Option<u32>,

    /// Is btclient running in restricted mode?
    is_restricted: bool,

    /// Data of GATT client preference.
    gatt_client_context: GattClientContext,

    /// Data of GATT server preference.
    gatt_server_context: GattServerContext,

    /// The schedule when a socket is connected.
    socket_test_schedule: Option<SocketSchedule>,

    /// The handle of the SDP record for MPS (Multi-Profile Specification).
    mps_sdp_handle: Option<i32>,

    /// The set of client commands that need to wait for callbacks.
    client_commands_with_callbacks: Vec<String>,

    /// A set of addresses whose battery changes are being tracked.
    pub(crate) battery_address_filter: HashSet<String>,
}

impl ClientContext {
    pub fn new(
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
        tx: mpsc::Sender<ForegroundActions>,
        is_restricted: bool,
        client_commands_with_callbacks: Vec<String>,
    ) -> ClientContext {
        // Manager interface is almost always available but adapter interface
        // requires that the specific adapter is enabled.
        let manager_dbus = BluetoothManagerDBus::new(dbus_connection.clone());

        ClientContext {
            adapters: HashMap::new(),
            default_adapter: 0,
            enabled: false,
            adapter_ready: false,
            adapter_address: None,
            bonding_attempt: None,
            discovering_state: false,
            found_devices: HashMap::new(),
            bonded_devices: HashMap::new(),
            manager_dbus,
            adapter_dbus: None,
            qa_legacy_dbus: None,
            qa_dbus: None,
            gatt_dbus: None,
            admin_dbus: None,
            suspend_dbus: None,
            socket_manager_dbus: None,
            telephony_dbus: None,
            media_dbus: None,
            battery_manager_dbus: None,
            fg: tx,
            dbus_connection,
            dbus_crossroads,
            scanner_callback_id: None,
            advertiser_callback_id: None,
            admin_callback_id: None,
            active_scanner_ids: HashSet::new(),
            adv_sets: HashMap::new(),
            socket_manager_callback_id: None,
            qa_callback_id: None,
            is_restricted,
            gatt_client_context: GattClientContext::new(),
            gatt_server_context: GattServerContext::new(),
            socket_test_schedule: None,
            mps_sdp_handle: None,
            client_commands_with_callbacks,
            battery_address_filter: HashSet::new(),
        }
    }

    // Sets required values for the adapter when enabling or disabling
    fn set_adapter_enabled(&mut self, hci_interface: i32, enabled: bool) {
        print_info!("hci{} enabled = {}", hci_interface, enabled);

        self.adapters.entry(hci_interface).and_modify(|v| *v = enabled).or_insert(enabled);

        // When the default adapter's state is updated, we need to modify a few more things.
        // Only do this if we're not repeating the previous state.
        let prev_enabled = self.enabled;
        let default_adapter = self.default_adapter;
        if hci_interface == default_adapter && prev_enabled != enabled {
            self.enabled = enabled;
            self.adapter_ready = false;
            if enabled {
                self.create_adapter_proxy(hci_interface);
            } else {
                self.adapter_dbus = None;
            }
        }
    }

    // Creates adapter proxy, registers callbacks and initializes address.
    fn create_adapter_proxy(&mut self, idx: i32) {
        let conn = self.dbus_connection.clone();

        let dbus = BluetoothDBus::new(conn.clone(), idx);
        self.adapter_dbus = Some(dbus);
        self.qa_legacy_dbus = Some(BluetoothQALegacyDBus::new(conn.clone(), idx));
        self.qa_dbus = Some(BluetoothQADBus::new(conn.clone(), idx));

        let gatt_dbus = BluetoothGattDBus::new(conn.clone(), idx);
        self.gatt_dbus = Some(gatt_dbus);

        let admin_dbus = BluetoothAdminDBus::new(conn.clone(), idx);
        self.admin_dbus = Some(admin_dbus);

        let socket_manager_dbus = BluetoothSocketManagerDBus::new(conn.clone(), idx);
        self.socket_manager_dbus = Some(socket_manager_dbus);

        self.suspend_dbus = Some(SuspendDBus::new(conn.clone(), idx));

        self.telephony_dbus = Some(BluetoothTelephonyDBus::new(conn.clone(), idx));

        self.media_dbus = Some(BluetoothMediaDBus::new(conn.clone(), idx));

        self.battery_manager_dbus = Some(BatteryManagerDBus::new(conn.clone(), idx));

        // Trigger callback registration in the foreground
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let adapter = String::from(format!("adapter{}", idx));
            // Floss won't export the interface until it is ready to be used.
            // Wait 1 second before registering the callbacks.
            sleep(Duration::from_millis(1000)).await;
            let _ = fg.send(ForegroundActions::RegisterAdapterCallback(adapter)).await;
        });
    }

    // Foreground-only: Updates the adapter address.
    fn update_adapter_address(&mut self) -> String {
        let address = self.adapter_dbus.as_ref().unwrap().get_address();
        self.adapter_address = Some(address.clone());

        address
    }

    // Foreground-only: Updates bonded devices.
    fn update_bonded_devices(&mut self) {
        let bonded_devices = self.adapter_dbus.as_ref().unwrap().get_bonded_devices();

        for device in bonded_devices {
            self.bonded_devices.insert(device.address.clone(), device.clone());
        }
    }

    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) {
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let _ = fg.send(ForegroundActions::ConnectAllEnabledProfiles(device)).await;
        });
    }

    fn run_callback(&mut self, callback: Box<dyn Fn(Arc<Mutex<ClientContext>>) + Send>) {
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let _ = fg.send(ForegroundActions::RunCallback(callback)).await;
        });
    }

    fn get_devices(&self) -> Vec<String> {
        let mut result: Vec<String> = vec![];

        result.extend(
            self.found_devices.keys().map(|key| String::from(key)).collect::<Vec<String>>(),
        );
        result.extend(
            self.bonded_devices
                .keys()
                .filter(|key| !self.found_devices.contains_key(&String::from(*key)))
                .map(|key| String::from(key))
                .collect::<Vec<String>>(),
        );

        result
    }

    fn get_floss_api_version(&mut self) -> (u32, u32) {
        let ver = self.manager_dbus.get_floss_api_version();
        let major = (ver & 0xFFFF_0000) >> 16;
        let minor = ver & 0x0000_FFFF;
        (major, minor)
    }
}

/// Actions to take on the foreground loop. This allows us to queue actions in
/// callbacks that get run in the foreground context.
enum ForegroundActions {
    ConnectAllEnabledProfiles(BluetoothDevice), // Connect all enabled profiles for this device
    RunCallback(Box<dyn Fn(Arc<Mutex<ClientContext>>) + Send>), // Run callback in foreground
    RegisterAdapterCallback(String),            // Register callbacks for this adapter
    Readline(rustyline::Result<String>),        // Readline result from rustyline
}

/// Runs a command line program that interacts with a Bluetooth stack.
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("btclient")
        .arg(Arg::with_name("restricted").long("restricted").takes_value(false))
        .arg(
            Arg::with_name("command")
                .short("c")
                .long("command")
                .takes_value(true)
                .help("Executes a non-interactive command"),
        )
        .arg(
            Arg::with_name("timeout")
                .short("t")
                .long("timeout")
                .takes_value(true)
                .help("Specify a timeout in seconds for a non-interactive command"),
        )
        .get_matches();
    let command = value_t!(matches, "command", String);
    let is_restricted = matches.is_present("restricted");
    let timeout_secs = value_t!(matches, "timeout", u64);

    topstack::get_runtime().block_on(async move {
        // Connect to D-Bus system bus.
        let (resource, conn) = dbus_tokio::connection::new_system_sync()?;

        // The `resource` is a task that should be spawned onto a tokio compatible
        // reactor ASAP. If the resource ever finishes, we lost connection to D-Bus.
        tokio::spawn(async {
            let err = resource.await;
            panic!("Lost connection to D-Bus: {}", err);
        });

        // Sets up Crossroads for receiving callbacks.
        let cr = Arc::new(Mutex::new(Crossroads::new()));
        cr.lock().unwrap().set_async_support(Some((
            conn.clone(),
            Box::new(|x| {
                tokio::spawn(x);
            }),
        )));
        let cr_clone = cr.clone();
        conn.start_receive(
            MatchRule::new_method_call(),
            Box::new(move |msg, conn| {
                cr_clone.lock().unwrap().handle_message(msg, conn).unwrap();
                true
            }),
        );

        // Accept foreground actions with mpsc
        let (tx, rx) = mpsc::channel::<ForegroundActions>(10);

        // Include the commands
        // (1) that will be run as non-interactive client commands, and
        // (2) that will need to wait for the callbacks to complete.
        let client_commands_with_callbacks = vec!["media".to_string()];

        // Create the context needed for handling commands
        let context = Arc::new(Mutex::new(ClientContext::new(
            conn.clone(),
            cr.clone(),
            tx.clone(),
            is_restricted,
            client_commands_with_callbacks,
        )));

        // Check if manager interface is valid. We only print some help text before failing on the
        // first actual access to the interface (so we can also capture the actual reason the
        // interface isn't valid).
        if !context.lock().unwrap().manager_dbus.is_valid() {
            println!("Bluetooth manager doesn't seem to be working correctly.");
            println!("Check if service is running.");
            return Ok(());
        }

        // TODO: Registering the callback should be done when btmanagerd is ready (detect with
        // ObjectManager).
        context.lock().unwrap().manager_dbus.register_callback(Box::new(BtManagerCallback::new(
            String::from("/org/chromium/bluetooth/client/bluetooth_manager_callback"),
            context.clone(),
            conn.clone(),
            cr.clone(),
        )));

        // Check if the default adapter is enabled. If yes, we should create the adapter proxy
        // right away.
        let default_adapter = context.lock().unwrap().default_adapter;

        {
            let mut context_locked = context.lock().unwrap();
            match context_locked.manager_dbus.rpc.get_adapter_enabled(default_adapter).await {
                Ok(ret) => {
                    if ret {
                        context_locked.set_adapter_enabled(default_adapter, true);
                    }
                }
                Err(e) => {
                    panic!("Bluetooth Manager is not available. Exiting. D-Bus error: {}", e);
                }
            }
        }

        let handler = CommandHandler::new(context.clone());
        if let Ok(_) = command {
            // Timeout applies only to non-interactive commands.
            if let Ok(timeout_secs) = timeout_secs {
                let timeout_duration = Duration::from_secs(timeout_secs);
                match timeout(
                    timeout_duration,
                    handle_client_command(handler, tx, rx, context, command),
                )
                .await
                {
                    Ok(_) => {
                        return Result::Ok(());
                    }
                    Err(_) => {
                        return Result::Err("btclient timeout".into());
                    }
                };
            }
        }
        // There are two scenarios in which handle_client_command is run without a timeout.
        // - Interactive commands: none of these commands require a timeout.
        // - Non-interactive commands that have not specified a timeout.
        handle_client_command(handler, tx, rx, context, command).await?;
        return Result::Ok(());
    })
}

// If btclient runs without command arguments, the interactive shell
// actions are performed.
// If btclient runs with command arguments, the command is executed
// once. There are two cases to exit.
//   Case 1: if the command does not need a callback, e.g., "help",
//           it will exit after running handler.process_cmd_line().
//   Case 2: if the command needs a callback, e.g., "media log",
//           it will exit after the callback has been run in the arm
//           of ForegroundActions::RunCallback(callback).
async fn handle_client_command(
    mut handler: CommandHandler,
    tx: mpsc::Sender<ForegroundActions>,
    mut rx: mpsc::Receiver<ForegroundActions>,
    context: Arc<Mutex<ClientContext>>,
    command: Result<String, clap::Error>,
) -> Result<(), Box<dyn std::error::Error>> {
    let semaphore_fg = Arc::new(tokio::sync::Semaphore::new(1));

    // If there are no command arguments, start the interactive shell.
    if let Err(_) = command {
        let command_rule_list = handler.get_command_rule_list().clone();
        let context_for_closure = context.clone();

        // Async task to keep reading new lines from user
        let semaphore = semaphore_fg.clone();
        let editor = AsyncEditor::new(command_rule_list, context_for_closure)
            .map_err(|x| format!("creating async editor failed: {x}"))?;
        tokio::spawn(async move {
            loop {
                // Wait until ForegroundAction::Readline finishes its task.
                let permit = semaphore.acquire().await;
                if permit.is_err() {
                    break;
                };
                // Let ForegroundAction::Readline decide when it's done.
                permit.unwrap().forget();

                // It's good to do readline now.
                let result = editor.readline().await;
                let _ = tx.send(ForegroundActions::Readline(result)).await;
            }
        });
    }

    'readline: loop {
        let m = rx.recv().await;

        if m.is_none() {
            break;
        }

        match m.unwrap() {
            ForegroundActions::ConnectAllEnabledProfiles(device) => {
                if context.lock().unwrap().adapter_ready {
                    context
                        .lock()
                        .unwrap()
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .connect_all_enabled_profiles(device);
                } else {
                    println!("Adapter isn't ready to connect profiles.");
                }
            }
            ForegroundActions::RunCallback(callback) => {
                callback(context.clone());

                // Break the loop as a non-interactive command is completed.
                if let Ok(_) = command {
                    break;
                }
            }
            // Once adapter is ready, register callbacks, get the address and mark it as ready
            ForegroundActions::RegisterAdapterCallback(adapter) => {
                let cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/bluetooth_callback", adapter);
                let conn_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/bluetooth_conn_callback", adapter);
                let suspend_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/suspend_callback", adapter);
                let scanner_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/scanner_callback", adapter);
                let advertiser_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/advertising_set_callback", adapter);
                let admin_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/admin_callback", adapter);
                let socket_manager_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/socket_manager_callback", adapter);
                let qa_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/qa_manager_callback", adapter);
                let media_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/bluetooth_media_callback", adapter);
                let telephony_cb_objpath: String = format!(
                    "/org/chromium/bluetooth/client/{}/bluetooth_telephony_callback",
                    adapter
                );
                let battery_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/battery_manager_callback", adapter);

                let dbus_connection = context.lock().unwrap().dbus_connection.clone();
                let dbus_crossroads = context.lock().unwrap().dbus_crossroads.clone();

                context
                    .lock()
                    .unwrap()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_callback(Box::new(BtCallback::new(
                        cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetooth::RegisterCallback");
                context
                    .lock()
                    .unwrap()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_connection_callback(Box::new(BtConnectionCallback::new(
                        conn_cb_objpath,
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetooth::RegisterConnectionCallback");

                // Register callback listener for le-scan`commands.
                let scanner_callback_id = context
                    .lock()
                    .unwrap()
                    .gatt_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_scanner_callback(Box::new(ScannerCallback::new(
                        scanner_cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothGatt::RegisterScannerCallback");
                context.lock().unwrap().scanner_callback_id = Some(scanner_callback_id);

                let advertiser_callback_id = context
                    .lock()
                    .unwrap()
                    .gatt_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_advertiser_callback(Box::new(AdvertisingSetCallback::new(
                        advertiser_cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothGatt::RegisterAdvertiserCallback");
                context.lock().unwrap().advertiser_callback_id = Some(advertiser_callback_id);

                let admin_callback_id = context
                    .lock()
                    .unwrap()
                    .admin_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_admin_policy_callback(Box::new(AdminCallback::new(
                        admin_cb_objpath.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothAdmin::RegisterAdminCallback");
                context.lock().unwrap().admin_callback_id = Some(admin_callback_id);

                let socket_manager_callback_id = context
                    .lock()
                    .unwrap()
                    .socket_manager_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_callback(Box::new(BtSocketManagerCallback::new(
                        socket_manager_cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothSocketManager::RegisterCallback");
                context.lock().unwrap().socket_manager_callback_id =
                    Some(socket_manager_callback_id);

                let qa_callback_id = context
                    .lock()
                    .unwrap()
                    .qa_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_qa_callback(Box::new(QACallback::new(
                        qa_cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothQA::RegisterCallback");
                context.lock().unwrap().qa_callback_id = Some(qa_callback_id);

                // When adapter is ready, Suspend API is also ready. Register as an observer.
                // TODO(b/224606285): Implement suspend debug utils in btclient.
                context.lock().unwrap().suspend_dbus.as_mut().unwrap().register_callback(Box::new(
                    SuspendCallback::new(
                        suspend_cb_objpath,
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    ),
                ));

                context
                    .lock()
                    .unwrap()
                    .media_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_callback(Box::new(MediaCallback::new(
                        media_cb_objpath,
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothMedia::RegisterCallback");

                context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_telephony_callback(Box::new(TelephonyCallback::new(
                        telephony_cb_objpath,
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBluetoothMedia::RegisterTelephonyCallback");

                context
                    .lock()
                    .unwrap()
                    .battery_manager_dbus
                    .as_mut()
                    .unwrap()
                    .rpc
                    .register_battery_callback(Box::new(BatteryManagerCallback::new(
                        battery_cb_objpath,
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )))
                    .await
                    .expect("D-Bus error on IBatteryManagerDBus::RegisterBatteryCallback");

                context.lock().unwrap().adapter_ready = true;
                let adapter_address = context.lock().unwrap().update_adapter_address();
                context.lock().unwrap().update_bonded_devices();

                print_info!("Adapter {} is ready", adapter_address);

                // Run the command with the command arguments as the client is
                // non-interactive.
                if let Some(command) = command.as_ref().ok() {
                    let mut iter = command.split(' ').map(String::from);
                    let first = iter.next().unwrap_or(String::from(""));
                    if !handler.process_cmd_line(&first, &iter.collect::<Vec<String>>()) {
                        // Break immediately if the command fails to execute.
                        break;
                    }

                    // Break the loop immediately if there is no callback
                    // to wait for.
                    if !context.lock().unwrap().client_commands_with_callbacks.contains(&first) {
                        break;
                    }
                }
            }
            ForegroundActions::Readline(result) => match result {
                Err(rustyline::error::ReadlineError::Interrupted) => {
                    // Ctrl-C cancels the currently typed line, do nothing and ready to do next
                    // readline again.
                    semaphore_fg.add_permits(1);
                }
                Err(_err) => {
                    break;
                }
                Ok(line) => {
                    // Currently Chrome OS uses Rust 1.60 so use the 1-time loop block to
                    // workaround this.
                    // With Rust 1.65 onwards we can convert this loop hack into a named block:
                    // https://blog.rust-lang.org/2022/11/03/Rust-1.65.0.html#break-from-labeled-blocks
                    // TODO: Use named block when Android and Chrome OS Rust upgrade Rust to 1.65.
                    loop {
                        let args = match shell_words::split(line.as_str()) {
                            Ok(words) => words,
                            Err(e) => {
                                print_error!("Error parsing arguments: {}", e);
                                break;
                            }
                        };

                        let (cmd, rest) = match args.split_first() {
                            Some(pair) => pair,
                            None => break,
                        };

                        if cmd == "quit" {
                            break 'readline;
                        }

                        handler.process_cmd_line(cmd, &rest.to_vec());
                        break;
                    }

                    // Ready to do readline again.
                    semaphore_fg.add_permits(1);
                }
            },
        }
    }

    semaphore_fg.close();

    print_info!("Client exiting");
    Ok(())
}
