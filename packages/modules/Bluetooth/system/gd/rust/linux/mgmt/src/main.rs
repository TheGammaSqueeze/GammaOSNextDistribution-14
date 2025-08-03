// The manager binary (btmanagerd) is a fairly barebone bin file that depends on the manager_service
// library which implements most of the logic. The code is separated in this way so that we can
// apply certain linker flags (which is applied to the library but not the binary).
// Please keep main.rs logic light and write the heavy logic in the manager_service library instead.

use clap::{App, Arg};
use dbus::channel::MatchingReceiver;
use dbus::message::MatchRule;
use dbus_crossroads::Crossroads;
use dbus_projection::DisconnectWatcher;
use dbus_tokio::connection;
use log::LevelFilter;
use manager_service::bluetooth_manager::BluetoothManager;
use manager_service::powerd_suspend_manager::PowerdSuspendManager;
use manager_service::{bluetooth_experimental_dbus, iface_bluetooth_manager};
use manager_service::{bluetooth_manager_dbus, config_util, state_machine};
use std::sync::{Arc, Mutex};
use syslog::{BasicLogger, Facility, Formatter3164};

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let matches = App::new("Bluetooth Manager")
        .arg(Arg::with_name("systemd").long("systemd").help("If btadapterd uses systemd init"))
        .arg(Arg::with_name("debug").long("debug").short("d").help("Enables debug level logs"))
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
    let is_systemd = matches.is_present("systemd");

    let level_filter = if is_debug { LevelFilter::Debug } else { LevelFilter::Info };

    let log_output = matches.value_of("log-output").unwrap_or("syslog");

    if log_output == "stderr" {
        env_logger::Builder::new().filter(None, level_filter).init();
    } else {
        // syslog is the default log output.
        let formatter = Formatter3164 {
            facility: Facility::LOG_USER,
            hostname: None,
            process: "btmanagerd".into(),
            pid: 0,
        };

        let logger = syslog::unix(formatter).expect("could not connect to syslog");
        let _ = log::set_boxed_logger(Box::new(BasicLogger::new(logger)))
            .map(|()| log::set_max_level(config_util::get_log_level().unwrap_or(level_filter)));
    }

    // Initialize config util
    config_util::fix_config_file_format();

    // Connect to the D-Bus system bus (this is blocking, unfortunately).
    let (resource, conn) = connection::new_system_sync()?;

    // There are multiple signal handlers. We need to set signal match mode to true to allow signal
    // handlers process the signals independently. Otherwise only the first signal handler will get
    // the chance to handle the same signal in case the match rule overlaps.
    conn.set_signal_match_mode(true);

    // Determine whether to use upstart or systemd
    let invoker = if is_systemd {
        state_machine::Invoker::SystemdInvoker
    } else {
        state_machine::Invoker::UpstartInvoker
    };

    let context = state_machine::create_new_state_machine_context(invoker);
    let proxy = context.get_proxy();

    // The resource is a task that should be spawned onto a tokio compatible
    // reactor ASAP. If the resource ever finishes, you lost connection to D-Bus.
    tokio::spawn(async {
        let err = resource.await;
        panic!("Lost connection to D-Bus: {}", err);
    });

    // Let's request a name on the bus, so that clients can find us.
    conn.request_name("org.chromium.bluetooth.Manager", false, true, false).await?;
    log::debug!("D-Bus name: {}", conn.unique_name());

    // Create a new crossroads instance.
    // The instance is configured so that introspection and properties interfaces
    // are added by default on object path additions.
    let cr = Arc::new(Mutex::new(Crossroads::new()));

    // Enable async support for the crossroads instance.
    cr.lock().unwrap().set_async_support(Some((
        conn.clone(),
        Box::new(|x| {
            tokio::spawn(x);
        }),
    )));

    // Object manager is necessary for clients (to inform them when Bluetooth is
    // available). Create it at root (/) so subsequent additions generate
    // InterfaceAdded and InterfaceRemoved signals.
    cr.lock().unwrap().set_object_manager_support(Some(conn.clone()));
    let om = cr.lock().unwrap().object_manager();
    cr.lock().unwrap().insert("/", &[om], {});

    let bluetooth_manager = Arc::new(Mutex::new(Box::new(BluetoothManager::new(proxy))));

    // Set up the disconnect watcher to monitor client disconnects.
    let disconnect_watcher = Arc::new(Mutex::new(DisconnectWatcher::new()));
    disconnect_watcher.lock().unwrap().setup_watch(conn.clone()).await;

    // We add the Crossroads instance to the connection so that incoming method calls will be
    // handled.
    // This must be done before exporting interfaces so that clients that rely on InterfacesAdded
    // signal can rely on us being ready to handle methods on those exported interfaces.
    let cr_clone = cr.clone();
    conn.start_receive(
        MatchRule::new_method_call(),
        Box::new(move |msg, conn| {
            cr_clone.lock().unwrap().handle_message(msg, conn).unwrap();
            true
        }),
    );

    // Let's add the "/org/chromium/bluetooth/Manager" path, which implements
    // the org.chromium.bluetooth.Manager interface, to the crossroads instance.
    let iface = bluetooth_manager_dbus::export_bluetooth_manager_dbus_intf(
        conn.clone(),
        &mut cr.lock().unwrap(),
        disconnect_watcher.clone(),
    );

    // Let's add the "/org/chromium/bluetooth/Experimental" path, which implements
    // the org.chromium.bluetooth.Experimental interface, to the crossroads instance
    let iface_exp = bluetooth_experimental_dbus::export_bluetooth_experimental_dbus_intf(
        conn.clone(),
        &mut cr.lock().unwrap(),
        disconnect_watcher.clone(),
    );

    // Create mixin object for Manager + Experimental interfaces.
    let mixin = Box::new(iface_bluetooth_manager::BluetoothManagerMixin {
        manager: bluetooth_manager.clone(),
        experimental: bluetooth_manager.clone(),
    });

    cr.lock().unwrap().insert("/org/chromium/bluetooth/Manager", &[iface, iface_exp], mixin);

    let mut powerd_suspend_manager = PowerdSuspendManager::new(conn.clone(), cr);

    bluetooth_manager
        .lock()
        .unwrap()
        .set_suspend_manager_context(powerd_suspend_manager.get_suspend_manager_context());

    tokio::spawn(async move {
        powerd_suspend_manager.init().await;
        powerd_suspend_manager.mainloop().await;
    });

    tokio::spawn(async move {
        state_machine::mainloop(context, bluetooth_manager).await;
    });

    std::future::pending::<()>().await;

    // Run forever.
    unreachable!()
}
