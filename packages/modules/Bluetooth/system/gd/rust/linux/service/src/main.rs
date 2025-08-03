use clap::{App, AppSettings, Arg};
use dbus_projection::DisconnectWatcher;
use dbus_tokio::connection;
use futures::future;
use lazy_static::lazy_static;
use nix::sys::signal;
use std::error::Error;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;
use tokio::sync::mpsc::Sender;

// Necessary to link right entries.
#[allow(unused_imports)]
use bt_shim;

use bt_topshim::{btif::get_btinterface, topstack};
use btstack::{
    battery_manager::BatteryManager,
    battery_provider_manager::BatteryProviderManager,
    battery_service::BatteryService,
    bluetooth::{Bluetooth, IBluetooth, SigData},
    bluetooth_admin::BluetoothAdmin,
    bluetooth_gatt::BluetoothGatt,
    bluetooth_logging::BluetoothLogging,
    bluetooth_media::BluetoothMedia,
    bluetooth_qa::BluetoothQA,
    dis::DeviceInformation,
    socket_manager::BluetoothSocketManager,
    suspend::Suspend,
    Message, Stack,
};

mod dbus_arg;
mod iface_battery_manager;
mod iface_battery_provider_manager;
mod iface_bluetooth;
mod iface_bluetooth_admin;
mod iface_bluetooth_gatt;
mod iface_bluetooth_media;
mod iface_bluetooth_qa;
mod iface_bluetooth_telephony;
mod iface_logging;
mod interface_manager;

const DBUS_SERVICE_NAME: &str = "org.chromium.bluetooth";
const ADMIN_SETTINGS_FILE_PATH: &str = "/var/lib/bluetooth/admin_policy.json";
// The maximum ACL disconnect timeout is 3.5s defined by BTA_DM_DISABLE_TIMER_MS
// and BTA_DM_DISABLE_TIMER_RETRIAL_MS
const STACK_TURN_OFF_TIMEOUT_MS: Duration = Duration::from_millis(4000);
// Time bt_stack_manager waits for cleanup
const STACK_CLEANUP_TIMEOUT_MS: Duration = Duration::from_millis(1000);

const INIT_LOGGING_MAX_RETRY: u8 = 3;

/// Runs the Bluetooth daemon serving D-Bus IPC.
fn main() -> Result<(), Box<dyn Error>> {
    let matches = App::new("Bluetooth Adapter Daemon")
        // Allows multiple INIT_ flags to be given at the end of the arguments.
        .setting(AppSettings::TrailingVarArg)
        .arg(
            Arg::with_name("hci")
                .long("hci")
                .value_name("HCI")
                .takes_value(true)
                .help("The HCI index"),
        )
        .arg(
            Arg::with_name("index")
                .long("index")
                .value_name("INDEX")
                .takes_value(true)
                .help("The Virtual index"),
        )
        .arg(Arg::with_name("debug").long("debug").short("d").help("Enables debug level logs"))
        .arg(
            Arg::with_name("verbose-debug")
                .long("verbose-debug")
                .short("v")
                .help("Enables VERBOSE and additional tags for debug logging. Use with --debug."),
        )
        .arg(Arg::from_usage("[init-flags] 'Fluoride INIT_ flags'").multiple(true))
        .arg(
            Arg::with_name("log-output")
                .long("log-output")
                .takes_value(true)
                .possible_values(&["syslog", "stderr"])
                .default_value("syslog")
                .help("Select log output"),
        )
        .get_matches();

    let is_debug = matches.is_present("debug");
    let is_verbose_debug = matches.is_present("verbose-debug");
    let log_output = matches.value_of("log-output").unwrap_or("syslog");

    let virt_index = matches.value_of("index").map_or(0, |idx| idx.parse::<i32>().unwrap_or(0));
    let hci_index = matches.value_of("hci").map_or(0, |idx| idx.parse::<i32>().unwrap_or(0));

    // The remaining flags are passed down to Fluoride as is.
    let mut init_flags: Vec<String> = match matches.values_of("init-flags") {
        Some(args) => args.map(|s| String::from(s)).collect(),
        None => vec![],
    };

    // Forward --hci to Fluoride.
    init_flags.push(format!("--hci={}", hci_index));

    let logging = Arc::new(Mutex::new(Box::new(BluetoothLogging::new(
        is_debug,
        is_verbose_debug,
        log_output,
    ))));
    // TODO(b/307171804): Investigate why connecting to unix syslog might fail.
    // Retry it a few times. Ignore the failure if fails too many times.
    for _ in 0..INIT_LOGGING_MAX_RETRY {
        match logging.lock().unwrap().initialize() {
            Ok(_) => break,
            Err(_) => continue,
        }
    }

    // Always treat discovery as classic only
    init_flags.push(String::from("INIT_classic_discovery_only=true"));

    let (tx, rx) = Stack::create_channel();
    let (api_tx, api_rx) = interface_manager::InterfaceManager::create_channel();
    let sig_notifier = Arc::new(SigData {
        enabled: Mutex::new(false),
        enabled_notify: Condvar::new(),
        thread_attached: Mutex::new(false),
        thread_notify: Condvar::new(),
    });

    let intf = Arc::new(Mutex::new(get_btinterface().unwrap()));
    let bluetooth_gatt =
        Arc::new(Mutex::new(Box::new(BluetoothGatt::new(intf.clone(), tx.clone()))));
    let battery_provider_manager =
        Arc::new(Mutex::new(Box::new(BatteryProviderManager::new(tx.clone()))));
    let battery_service = Arc::new(Mutex::new(Box::new(BatteryService::new(
        bluetooth_gatt.clone(),
        battery_provider_manager.clone(),
        tx.clone(),
        api_tx.clone(),
    ))));
    let battery_manager = Arc::new(Mutex::new(Box::new(BatteryManager::new(
        battery_provider_manager.clone(),
        tx.clone(),
    ))));
    let bluetooth_media = Arc::new(Mutex::new(Box::new(BluetoothMedia::new(
        tx.clone(),
        intf.clone(),
        battery_provider_manager.clone(),
    ))));
    let bluetooth_admin = Arc::new(Mutex::new(Box::new(BluetoothAdmin::new(
        String::from(ADMIN_SETTINGS_FILE_PATH),
        tx.clone(),
    ))));
    let bluetooth = Arc::new(Mutex::new(Box::new(Bluetooth::new(
        virt_index,
        hci_index,
        tx.clone(),
        api_tx.clone(),
        sig_notifier.clone(),
        intf.clone(),
        bluetooth_admin.clone(),
        bluetooth_gatt.clone(),
        bluetooth_media.clone(),
    ))));
    let suspend = Arc::new(Mutex::new(Box::new(Suspend::new(
        bluetooth.clone(),
        intf.clone(),
        bluetooth_gatt.clone(),
        bluetooth_media.clone(),
        tx.clone(),
    ))));
    let bt_sock_mgr = Arc::new(Mutex::new(Box::new(BluetoothSocketManager::new(
        tx.clone(),
        bluetooth_admin.clone(),
    ))));
    let bluetooth_qa = Arc::new(Mutex::new(Box::new(BluetoothQA::new(tx.clone()))));

    let dis =
        Arc::new(Mutex::new(Box::new(DeviceInformation::new(bluetooth_gatt.clone(), tx.clone()))));

    topstack::get_runtime().block_on(async {
        // Connect to D-Bus system bus.
        let (resource, conn) = connection::new_system_sync()?;

        // The `resource` is a task that should be spawned onto a tokio compatible
        // reactor ASAP. If the resource ever finishes, we lost connection to D-Bus.
        let conn_join_handle = tokio::spawn(async {
            let err = resource.await;
            panic!("Lost connection to D-Bus: {}", err);
        });

        // Request a service name and quit if not able to.
        conn.request_name(DBUS_SERVICE_NAME, false, true, false).await?;

        // Run the stack main dispatch loop.
        topstack::get_runtime().spawn(Stack::dispatch(
            rx,
            api_tx.clone(),
            bluetooth.clone(),
            bluetooth_gatt.clone(),
            battery_service.clone(),
            battery_manager.clone(),
            battery_provider_manager.clone(),
            bluetooth_media.clone(),
            suspend.clone(),
            bt_sock_mgr.clone(),
            bluetooth_admin.clone(),
            dis.clone(),
            bluetooth_qa.clone(),
        ));

        // Set up the disconnect watcher to monitor client disconnects.
        let disconnect_watcher = Arc::new(Mutex::new(DisconnectWatcher::new()));
        disconnect_watcher.lock().unwrap().setup_watch(conn.clone()).await;

        tokio::spawn(interface_manager::InterfaceManager::dispatch(
            api_rx,
            tx.clone(),
            virt_index,
            conn,
            conn_join_handle,
            disconnect_watcher.clone(),
            bluetooth.clone(),
            bluetooth_admin.clone(),
            bluetooth_gatt.clone(),
            battery_service.clone(),
            battery_manager.clone(),
            battery_provider_manager.clone(),
            bluetooth_media.clone(),
            bluetooth_qa.clone(),
            bt_sock_mgr.clone(),
            suspend.clone(),
            logging.clone(),
        ));

        // Hold locks and initialize all interfaces. This must be done AFTER DBus is
        // initialized so DBus can properly enforce user policies.
        {
            let adapter = bluetooth.clone();
            bluetooth_media.lock().unwrap().set_adapter(adapter.clone());
            bluetooth_admin.lock().unwrap().set_adapter(adapter.clone());

            let mut bluetooth = bluetooth.lock().unwrap();
            bluetooth.init(init_flags);
            bluetooth.enable();

            bluetooth_gatt.lock().unwrap().init_profiles(tx.clone(), api_tx.clone());
            bt_sock_mgr.lock().unwrap().initialize(intf.clone());

            // Install SIGTERM handler so that we can properly shutdown
            *SIG_DATA.lock().unwrap() = Some((tx.clone(), sig_notifier.clone()));

            let sig_action_term = signal::SigAction::new(
                signal::SigHandler::Handler(handle_sigterm),
                signal::SaFlags::empty(),
                signal::SigSet::empty(),
            );

            let sig_action_int = signal::SigAction::new(
                signal::SigHandler::Handler(handle_sigint),
                signal::SaFlags::empty(),
                signal::SigSet::empty(),
            );

            unsafe {
                signal::sigaction(signal::SIGTERM, &sig_action_term).unwrap();
                signal::sigaction(signal::SIGINT, &sig_action_int).unwrap();
            }
        }

        // Serve clients forever.
        future::pending::<()>().await;
        unreachable!()
    })
}

lazy_static! {
    /// Data needed for signal handling.
    static ref SIG_DATA: Mutex<Option<(Sender<Message>, Arc<SigData>)>> = Mutex::new(None);
}

extern "C" fn handle_sigterm(_signum: i32) {
    let guard = SIG_DATA.lock().unwrap();
    if let Some((tx, notifier)) = guard.as_ref() {
        log::debug!("Handling SIGTERM by disabling the adapter!");
        let txl = tx.clone();
        tokio::spawn(async move {
            // Send the shutdown message here.
            let _ = txl.send(Message::InterfaceShutdown).await;
        });

        let guard = notifier.enabled.lock().unwrap();
        if *guard {
            log::debug!("Waiting for stack to turn off for {:?}", STACK_TURN_OFF_TIMEOUT_MS);
            let _ = notifier.enabled_notify.wait_timeout(guard, STACK_TURN_OFF_TIMEOUT_MS);
        }

        log::debug!("SIGTERM cleaning up the stack.");
        let txl = tx.clone();
        tokio::spawn(async move {
            // Send the cleanup message here.
            let _ = txl.send(Message::Cleanup).await;
        });

        let guard = notifier.thread_attached.lock().unwrap();
        if *guard {
            log::debug!("Waiting for stack to clean up for {:?}", STACK_CLEANUP_TIMEOUT_MS);
            let _ = notifier.thread_notify.wait_timeout(guard, STACK_CLEANUP_TIMEOUT_MS);
        }
    }

    log::debug!("Sigterm completed");
    std::process::exit(0);
}

extern "C" fn handle_sigint(_signum: i32) {
    // Assumed this is from HAL Host, which is likely caused by chipset error.
    // In this case, don't crash the daemon and don't try to power off the adapter.
    log::debug!("Sigint completed");
    std::process::exit(0);
}
