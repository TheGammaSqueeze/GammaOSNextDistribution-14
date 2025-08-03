// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use clap::Parser;
use log::warn;
use log::{error, info};
use netsim_common::system::netsimd_temp_dir_string;
use netsim_common::util::os_utils::{
    get_hci_port, get_instance, get_instance_name, remove_netsim_ini,
};
use netsim_common::util::zip_artifact::zip_artifacts;

use crate::captures::capture::spawn_capture_event_subscriber;
use crate::config_file;
use crate::devices::devices_handler::spawn_shutdown_publisher;
use crate::echip;
use crate::events;
use crate::events::{Event, ShutDown};
use crate::session::Session;
use crate::version::get_version;
use netsim_common::util::netsim_logger;

use crate::args::NetsimdArgs;
use crate::ffi::ffi_util;
use crate::service::{new_test_beacon, Service, ServiceParams};
#[cfg(feature = "cuttlefish")]
use netsim_common::util::os_utils::get_server_address;
use netsim_proto::config::{Bluetooth as BluetoothConfig, Capture, Config};
use std::env;
use std::ffi::{c_char, c_int};
use std::sync::mpsc::Receiver;

/// Wireless network simulator for android (and other) emulated devices.
///
/// # Safety
///
/// The file descriptors passed in `NetsimdArgs::fd_startup_str` must remain valid and open for as
/// long as the program runs.
#[no_mangle]
pub unsafe extern "C" fn rust_main(argc: c_int, argv: *const *const c_char) {
    ffi_util::set_up_crash_report();
    netsim_logger::init("netsimd");
    let netsimd_args = get_netsimd_args(argc, argv);
    run_netsimd_with_args(netsimd_args);
}

#[allow(unused)]
fn get_netsimd_args(argc: c_int, argv: *const *const c_char) -> NetsimdArgs {
    let env_args_or_err = env::var("NETSIM_ARGS");

    #[cfg(feature = "cuttlefish")]
    {
        // TODO: Use NetsimdArgs::parse() after netsimd binary is built with netsimd.rs.
        // In linux arm64 in aosp-main, it can't access CLI arguments by std::env::args() with netsimd.cc wrapper.
        let mut argv: Vec<_> = (0..argc)
            .map(|i|
                // SAFETY: argc and argv will remain valid as long as the program runs.
                unsafe {
                    std::ffi::CStr::from_ptr(*argv.add(i as usize)).to_str().unwrap().to_owned()
                })
            .collect();
        if let Ok(env_args) = env_args_or_err {
            env_args.split(' ').for_each(|arg| argv.push(arg.to_string()));
        }
        NetsimdArgs::parse_from(argv)
    }
    #[cfg(not(feature = "cuttlefish"))]
    {
        let mut argv = env::args().collect::<Vec<String>>();
        if let Ok(env_args) = env_args_or_err {
            env_args.split(' ').for_each(|arg| argv.push(arg.to_string()));
        }
        NetsimdArgs::parse_from(argv)
    }
}

fn run_netsimd_with_args(args: NetsimdArgs) {
    // Log version and terminate netsimd
    if args.version {
        println!("Netsimd Version: {}", get_version());
        return;
    }

    // Log where netsim artifacts are located
    info!("netsim artifacts path: {}", netsimd_temp_dir_string());

    // Log all args
    info!("{:#?}", args);

    if !args.logtostderr {
        cxx::let_cxx_string!(netsimd_temp_dir = netsimd_temp_dir_string());
        cxx::let_cxx_string!(
            netsimd_instance_name = get_instance_name(args.instance, args.connector_instance)
        );
        ffi_util::redirect_std_stream(&netsimd_temp_dir, &netsimd_instance_name);
        // Duplicating the previous two logs to be included in netsim_stderr.log
        info!("netsim artifacts path: {}", netsimd_temp_dir_string());
        info!("{:#?}", args);
    }

    match args.connector_instance {
        #[cfg(feature = "cuttlefish")]
        Some(connector_instance) => run_netsimd_connector(args, connector_instance),
        _ => run_netsimd_primary(args),
    }
}

/// Forwards packets to another netsim daemon.
#[cfg(feature = "cuttlefish")]
fn run_netsimd_connector(args: NetsimdArgs, instance: u16) {
    if args.fd_startup_str.is_none() {
        error!("Failed to start netsimd forwarder, missing `-s` arg");
        return;
    }
    let fd_startup = args.fd_startup_str.unwrap();

    let mut server: Option<String> = None;
    // Attempts multiple time for fetching netsim.ini
    for second in [1, 2, 4, 8, 0] {
        server = get_server_address(instance);
        if server.is_some() {
            break;
        } else {
            warn!("Unable to find ini file for instance {}, retrying", instance);
            std::thread::sleep(std::time::Duration::from_secs(second));
        }
    }
    if server.is_none() {
        error!("Failed to run netsimd connector");
        return;
    }
    let server = server.unwrap();
    // TODO: Make this function returns Result to use `?` instead of unwrap().
    info!("Starting in Connector mode to {}", server.as_str());
    crate::transport::fd::run_fd_connector(&fd_startup, server.as_str())
        .map_err(|e| error!("Failed to run fd connector: {}", e))
        .unwrap();
}

// loop until ShutDown event is received, then log and return.
fn main_loop(events_rx: Receiver<Event>) {
    loop {
        // events_rx.recv() will wait until the event is received.
        // TODO(b/305536480): Remove built-in devices during shutdown.
        if let Ok(Event::ShutDown(ShutDown { reason })) = events_rx.recv() {
            info!("Netsim is shutdown: {reason}");
            return;
        }
    }
}

// Disambiguate config and command line args and store merged setting in config
fn disambiguate_args(args: &mut NetsimdArgs, config: &mut Config) {
    // Command line override config file arguments

    // Currently capture cannot be specified off explicitly with command line.
    // Enable capture if enabled by command line arg
    if args.pcap {
        match config.capture.as_mut() {
            Some(capture) => {
                capture.enabled = Some(true);
            }
            None => {
                let mut capture = Capture::new();
                capture.enabled = Some(true);
                config.capture = Some(capture).into();
            }
        }
    }

    // Ensure Bluetooth config is initialized
    let bt_config = match config.bluetooth.as_mut() {
        Some(existing_bt_config) => existing_bt_config,
        None => {
            config.bluetooth = Some(BluetoothConfig::new()).into();
            config.bluetooth.as_mut().unwrap()
        }
    };

    // Set disable_address_reuse as needed
    if args.disable_address_reuse {
        bt_config.disable_address_reuse = Some(true);
    }

    // Determine test beacons configuration, default true for cuttlefish
    // TODO: remove default for cuttlefish by adding flag to tests
    bt_config.test_beacons = match (args.test_beacons, args.no_test_beacons) {
        (true, false) => Some(true),
        (false, true) => Some(false),
        (false, false) => match bt_config.test_beacons {
            Some(test_beacons) => Some(test_beacons),
            None => Some(cfg!(feature = "cuttlefish")),
        },
        (true, true) => panic!("unexpected flag combination"),
    };
}

fn run_netsimd_primary(mut args: NetsimdArgs) {
    info!("Netsim Version: {}", get_version());

    let fd_startup_str = args.fd_startup_str.clone().unwrap_or_default();
    let instance_num = get_instance(args.instance);
    let hci_port: u16 =
        get_hci_port(args.hci_port.unwrap_or_default(), instance_num - 1).try_into().unwrap();

    #[cfg(feature = "cuttlefish")]
    if fd_startup_str.is_empty() {
        warn!("Warning: netsimd startup flag -s is empty, waiting for gRPC connections.");
    }

    if ffi_util::is_netsimd_alive(instance_num) {
        warn!("Failed to start netsim daemon because a netsim daemon is already running");
        return;
    }

    // Load config file
    let mut config = Config::new();
    if let Some(ref filename) = args.config {
        match config_file::new_from_file(filename) {
            Ok(config_from_file) => {
                config = config_from_file;
            }
            Err(e) => {
                error!("Skipping config in {}: {:?}", filename, e);
            }
        }
    }
    // Disambiguate conflicts between cmdline args and config file
    disambiguate_args(&mut args, &mut config);

    // Print config file settings
    info!("{:#?}", config);

    let service_params = ServiceParams::new(
        fd_startup_str,
        args.no_cli_ui,
        args.no_web_ui,
        config.capture.enabled.unwrap_or_default(),
        hci_port,
        instance_num,
        args.dev,
        args.vsock.unwrap_or_default(),
    );

    // SAFETY: The caller guaranteed that the file descriptors in `fd_startup_str` would remain
    // valid and open for as long as the program runs.
    let mut service = unsafe { Service::new(service_params) };
    service.set_up();

    // Create all Event Receivers
    let capture_events_rx = events::subscribe();
    let device_events_rx = events::subscribe();
    let main_events_rx = events::subscribe();
    let session_events_rx = events::subscribe();

    // Start Session Event listener
    let mut session = Session::new();
    session.start(session_events_rx);

    // Pass all event receivers to each modules
    spawn_capture_event_subscriber(capture_events_rx);

    if !args.no_shutdown {
        spawn_shutdown_publisher(device_events_rx);
    }

    // Start radio facades
    echip::bluetooth::bluetooth_start(&config.bluetooth, instance_num);
    echip::wifi::wifi_start(&config.wifi);

    // Create test beacons if required
    if config.bluetooth.test_beacons == Some(true) {
        new_test_beacon(1, 1000);
        new_test_beacon(2, 1000);
    }

    // Run all netsimd services (grpc, socket, web)
    service.run();

    // Runs a synchronous main loop
    main_loop(main_events_rx);

    // Gracefully shutdown netsimd services
    service.shut_down();

    // write out session stats
    let _ = session.stop();

    // zip all artifacts
    if let Err(err) = zip_artifacts() {
        error!("Failed to zip artifacts: {err:?}");
    }

    // Once shutdown is complete, delete the netsim ini file
    remove_netsim_ini(instance_num);
}
