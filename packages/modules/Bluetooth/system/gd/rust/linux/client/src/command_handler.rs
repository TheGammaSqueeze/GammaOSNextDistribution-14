use std::collections::HashMap;
use std::fmt::{Display, Formatter};
use std::slice::SliceIndex;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use crate::bt_adv::AdvSet;
use crate::bt_gatt::AuthReq;
use crate::callbacks::{BtGattCallback, BtGattServerCallback};
use crate::ClientContext;
use crate::{console_red, console_yellow, print_error, print_info};
use bt_topshim::btif::{BtConnectionState, BtDiscMode, BtStatus, BtTransport, Uuid, INVALID_RSSI};
use bt_topshim::profiles::hid_host::BthhReportType;
use bt_topshim::profiles::sdp::{BtSdpMpsRecord, BtSdpRecord};
use bt_topshim::profiles::{gatt::LePhy, ProfileConnectionState};
use btstack::battery_manager::IBatteryManager;
use btstack::bluetooth::{BluetoothDevice, IBluetooth};
use btstack::bluetooth_gatt::{
    BluetoothGattService, GattDbElementType, GattWriteType, IBluetoothGatt,
};
use btstack::bluetooth_media::{IBluetoothMedia, IBluetoothTelephony};
use btstack::bluetooth_qa::IBluetoothQA;
use btstack::socket_manager::{IBluetoothSocketManager, SocketResult};
use btstack::uuid::{Profile, UuidHelper, UuidWrapper};
use manager_service::iface_bluetooth_manager::IBluetoothManager;

const INDENT_CHAR: &str = " ";
const BAR1_CHAR: &str = "=";
const BAR2_CHAR: &str = "-";
const MAX_MENU_CHAR_WIDTH: usize = 72;
const GATT_CLIENT_APP_UUID: &str = "12345678123456781234567812345678";
const GATT_SERVER_APP_UUID: &str = "12345678123456781234567812345679";
const HEART_RATE_SERVICE_UUID: &str = "0000180D-0000-1000-8000-00805F9B34FB";

enum CommandError {
    // Command not handled due to invalid arguments.
    InvalidArgs,
    // Command handled but failed with the given reason.
    Failed(String),
}

impl From<&str> for CommandError {
    fn from(s: &str) -> CommandError {
        CommandError::Failed(String::from(s))
    }
}

impl From<String> for CommandError {
    fn from(s: String) -> CommandError {
        CommandError::Failed(s)
    }
}

type CommandResult = Result<(), CommandError>;

type CommandFunction = fn(&mut CommandHandler, &Vec<String>) -> CommandResult;

fn _noop(_handler: &mut CommandHandler, _args: &Vec<String>) -> CommandResult {
    // Used so we can add options with no direct function
    // e.g. help and quit
    Ok(())
}

pub struct CommandOption {
    rules: Vec<String>,
    description: String,
    function_pointer: CommandFunction,
}

/// Handles string command entered from command line.
pub(crate) struct CommandHandler {
    context: Arc<Mutex<ClientContext>>,
    command_options: HashMap<String, CommandOption>,
}

/// Define what to do when a socket connects. Mainly for qualification purposes.
/// Specifically, after a socket is connected/accepted, we will do
/// (1) send a chunk of data every |send_interval| time until |num_frame| chunks has been sent.
/// (2) wait another |disconnect_delay| time. any incoming data will be dumpted during this time.
/// (3) disconnect the socket.
#[derive(Copy, Clone)]
pub struct SocketSchedule {
    /// Number of times to send data
    pub num_frame: u32,
    /// Time interval between each sending
    pub send_interval: Duration,
    /// Extra time after the last sending. Any incoming data will be printed during this time.
    pub disconnect_delay: Duration,
}

struct DisplayList<T>(Vec<T>);

impl<T: Display> Display for DisplayList<T> {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        let _ = write!(f, "[\n");
        for item in self.0.iter() {
            let _ = write!(f, "  {}\n", item);
        }

        write!(f, "]")
    }
}

fn wrap_help_text(text: &str, max: usize, indent: usize) -> String {
    let remaining_count = std::cmp::max(
        // real_max
        std::cmp::max(max, text.chars().count())
        // take away char count
         - text.chars().count()
        // take away real_indent
         - (
             if std::cmp::max(max, text.chars().count())- text.chars().count() > indent {
                 indent
             } else {
                 0
             }),
        0,
    );

    format!("|{}{}{}|", INDENT_CHAR.repeat(indent), text, INDENT_CHAR.repeat(remaining_count))
}

// This should be called during the constructor in order to populate the command option map
fn build_commands() -> HashMap<String, CommandOption> {
    let mut command_options = HashMap::<String, CommandOption>::new();
    command_options.insert(
        String::from("adapter"),
        CommandOption {
            rules: vec![
                String::from("adapter enable"),
                String::from("adapter disable"),
                String::from("adapter show"),
                String::from("adapter discoverable <on|limited|off> <duration>"),
                String::from("adapter connectable <on|off>"),
                String::from("adapter set-name <name>"),
            ],
            description: String::from(
                "Enable/Disable/Show default bluetooth adapter. (e.g. adapter enable)\n
                 Discoverable On/Limited/Off (e.g. adapter discoverable on 60)\n
                 Connectable On/Off (e.g. adapter connectable on)",
            ),
            function_pointer: CommandHandler::cmd_adapter,
        },
    );
    command_options.insert(
        String::from("battery"),
        CommandOption {
            rules: vec![
                String::from("battery status <address>"),
                String::from("battery track <address>"),
                String::from("battery untrack <address>"),
            ],
            description: String::from(
                "
                status: Current battery status of a given device.\n
                track: Track a given device to monitor battery updates.\n
                untrack: Stop tracking a device for battery updates.
            ",
            ),
            function_pointer: CommandHandler::cmd_battery,
        },
    );
    command_options.insert(
        String::from("bond"),
        CommandOption {
            rules: vec![String::from("bond <add|remove|cancel> <address>")],
            description: String::from("Creates a bond with a device."),
            function_pointer: CommandHandler::cmd_bond,
        },
    );
    command_options.insert(
        String::from("device"),
        CommandOption {
            rules: vec![
                String::from("device <connect|disconnect|info> <address>"),
                String::from("device set-pairing-confirmation <address> <accept|reject>"),
                String::from("device set-pairing-pin <address> <pin|reject>"),
                String::from("device set-pairing-passkey <address> <passkey|reject>"),
                String::from("device set-alias <address> <new-alias>"),
                String::from("device get-rssi <address>"),
            ],
            description: String::from("Take action on a remote device. (i.e. info)"),
            function_pointer: CommandHandler::cmd_device,
        },
    );
    command_options.insert(
        String::from("discovery"),
        CommandOption {
            rules: vec![String::from("discovery <start|stop>")],
            description: String::from("Start and stop device discovery. (e.g. discovery start)"),
            function_pointer: CommandHandler::cmd_discovery,
        },
    );
    command_options.insert(
        String::from("floss"),
        CommandOption {
            rules: vec![String::from("floss <enable|disable>")],
            description: String::from("Enable or disable Floss for dogfood."),
            function_pointer: CommandHandler::cmd_floss,
        },
    );
    command_options.insert(
        String::from("gatt"),
        CommandOption {
            rules: vec![
                String::from("gatt register-client"),
                String::from("gatt client-connect <address>"),
                String::from("gatt client-read-phy <address>"),
                String::from("gatt client-discover-services <address>"),
                String::from("gatt client-discover-service-by-uuid-pts <address> <uuid>"),
                String::from("gatt client-disconnect <address>"),
                String::from("gatt configure-mtu <address> <mtu>"),
                String::from("gatt set-direct-connect <true|false>"),
                String::from("gatt set-connect-transport <Bredr|LE|Auto>"),
                String::from("gatt set-connect-opportunistic <true|false>"),
                String::from("gatt set-connect-phy <Phy1m|Phy2m|PhyCoded>"),
                String::from("gatt set-auth-req <NONE|EncNoMitm|EncMitm|SignedNoMitm|SignedMitm>"),
                String::from(
                    "gatt write-characteristic <address> <handle> <NoRsp|Write|Prepare> <value>",
                ),
                String::from("gatt read-characteristic <address> <handle>"),
                String::from(
                    "gatt read-characteristic-by-uuid <address> <uuid> <start_handle> <end_handle>",
                ),
                String::from("gatt register-notification <address> <handle> <enable|disable>"),
                String::from("gatt register-server"),
                String::from("gatt unregister-server <server_id>"),
                String::from("gatt server-connect <server_id> <client_address>"),
                String::from("gatt server-disconnect <server_id> <client_address>"),
                String::from("gatt server-add-heartrate-service <server_id>"),
                String::from("gatt server-remove-service <server_id> <service_handle>"),
                String::from("gatt server-clear-all-services <server_id>"),
                String::from("gatt server-set-direct-connect <true|false>"),
                String::from("gatt server-set-connect-transport <Bredr|LE|Auto>"),
            ],
            description: String::from("GATT tools"),
            function_pointer: CommandHandler::cmd_gatt,
        },
    );
    command_options.insert(
        String::from("le-scan"),
        CommandOption {
            rules: vec![
                String::from("le-scan register-scanner"),
                String::from("le-scan unregister-scanner <scanner-id>"),
                String::from("le-scan start-scan <scanner-id>"),
                String::from("le-scan stop-scan <scanner-id>"),
            ],
            description: String::from("LE scanning utilities."),
            function_pointer: CommandHandler::cmd_le_scan,
        },
    );
    command_options.insert(
        String::from("advertise"),
        CommandOption {
            rules: vec![
                String::from("advertise <on|off|ext>"),
                String::from("advertise set-interval <ms>"),
                String::from("advertise set-scan-rsp <enable|disable>"),
                String::from("advertise set-raw-data <raw-adv-data> <adv-id>"),
                String::from("advertise set-connectable <on|off> <adv-id>"),
            ],
            description: String::from("Advertising utilities."),
            function_pointer: CommandHandler::cmd_advertise,
        },
    );
    command_options.insert(
        String::from("sdp"),
        CommandOption {
            rules: vec![String::from("sdp search <address> <uuid>")],
            description: String::from("Service Discovery Protocol utilities."),
            function_pointer: CommandHandler::cmd_sdp,
        },
    );
    command_options.insert(
        String::from("socket"),
        CommandOption {
            rules: vec![
                String::from("socket listen <auth-required> <Bredr|LE>"),
                String::from("socket listen-rfcomm <scn>"),
                String::from("socket send-msc <dlci> <address>"),
                String::from(
                    "socket connect <address> <l2cap|rfcomm> <psm|uuid> <auth-required> <Bredr|LE>",
                ),
                String::from("socket close <socket_id>"),
                String::from("socket set-on-connect-schedule <send|resend|dump>"),
            ],
            description: String::from("Socket manager utilities."),
            function_pointer: CommandHandler::cmd_socket,
        },
    );
    command_options.insert(
        String::from("hid"),
        CommandOption {
            rules: vec![
                String::from("hid get-report <address> <Input|Output|Feature> <report_id>"),
                String::from("hid set-report <address> <Input|Output|Feature> <report_value>"),
                String::from("hid send-data <address> <data>"),
            ],
            description: String::from("Socket manager utilities."),
            function_pointer: CommandHandler::cmd_hid,
        },
    );
    command_options.insert(
        String::from("get-address"),
        CommandOption {
            rules: vec![String::from("get-address")],
            description: String::from("Gets the local device address."),
            function_pointer: CommandHandler::cmd_get_address,
        },
    );
    command_options.insert(
        String::from("qa"),
        CommandOption {
            rules: vec![String::from("qa add-media-player <name> <browsing_supported>")],
            description: String::from("Methods for testing purposes"),
            function_pointer: CommandHandler::cmd_qa,
        },
    );
    command_options.insert(
        String::from("help"),
        CommandOption {
            rules: vec![String::from("help")],
            description: String::from("Shows this menu."),
            function_pointer: CommandHandler::cmd_help,
        },
    );
    command_options.insert(
        String::from("list"),
        CommandOption {
            rules: vec![String::from("list <bonded|found|connected>")],
            description: String::from(
                "List bonded or found remote devices. Use: list <bonded|found>",
            ),
            function_pointer: CommandHandler::cmd_list_devices,
        },
    );
    command_options.insert(
        String::from("telephony"),
        CommandOption {
            rules: vec![
                String::from("telephony set-network <on|off>"),
                String::from("telephony set-roaming <on|off>"),
                String::from("telephony set-signal <strength>"),
                String::from("telephony set-battery <level>"),
                String::from("telephony <enable|disable>"),
                String::from("telephony <incoming-call|dialing-call> <number>"),
                String::from("telephony <answer-call|hangup-call>"),
                String::from("telephony <set-memory-call|set-last-call> [<number>]"),
                String::from(
                    "telephony <release-held|release-active-accept-held|hold-active-accept-held>",
                ),
                String::from("telephony <audio-connect|audio-disconnect> <address>"),
            ],
            description: String::from("Set device telephony status."),
            function_pointer: CommandHandler::cmd_telephony,
        },
    );
    command_options.insert(
        String::from("media"),
        CommandOption {
            rules: vec![String::from("media log")],
            description: String::from("Audio tools."),
            function_pointer: CommandHandler::cmd_media,
        },
    );
    command_options.insert(
        String::from("quit"),
        CommandOption {
            rules: vec![String::from("quit")],
            description: String::from("Quit out of the interactive shell."),
            function_pointer: _noop,
        },
    );
    command_options
}

// Helper to index a vector safely. The same as `args.get(i)` but converts the None into a
// CommandError::InvalidArgs.
//
// Use this to safely index an argument and conveniently return the error if the argument does not
// exist.
fn get_arg<I>(
    args: &Vec<String>,
    index: I,
) -> Result<&<I as SliceIndex<[String]>>::Output, CommandError>
where
    I: SliceIndex<[String]>,
{
    args.get(index).ok_or(CommandError::InvalidArgs)
}

impl CommandHandler {
    /// Creates a new CommandHandler.
    pub fn new(context: Arc<Mutex<ClientContext>>) -> CommandHandler {
        CommandHandler { context, command_options: build_commands() }
    }

    /// Entry point for command and arguments
    pub fn process_cmd_line(&mut self, command: &str, args: &Vec<String>) -> bool {
        // Ignore empty line
        match command {
            "" => false,
            _ => match self.command_options.get(command) {
                Some(cmd) => {
                    let rules = cmd.rules.clone();
                    match (cmd.function_pointer)(self, &args) {
                        Ok(()) => true,
                        Err(CommandError::InvalidArgs) => {
                            print_error!("Invalid arguments. Usage:\n{}", rules.join("\n"));
                            false
                        }
                        Err(CommandError::Failed(msg)) => {
                            print_error!("Command failed: {}", msg);
                            false
                        }
                    }
                }
                None => {
                    println!("'{}' is an invalid command!", command);
                    self.cmd_help(&args).ok();
                    false
                }
            },
        }
    }

    fn lock_context(&self) -> std::sync::MutexGuard<ClientContext> {
        self.context.lock().unwrap()
    }

    // Common message for when the adapter isn't ready
    fn adapter_not_ready(&self) -> CommandError {
        format!(
            "Default adapter {} is not enabled. Enable the adapter before using this command.",
            self.lock_context().default_adapter
        )
        .into()
    }

    fn cmd_help(&mut self, args: &Vec<String>) -> CommandResult {
        if let Some(command) = args.get(0) {
            match self.command_options.get(command) {
                Some(cmd) => {
                    println!(
                        "\n{}{}\n{}{}\n",
                        INDENT_CHAR.repeat(4),
                        command,
                        INDENT_CHAR.repeat(8),
                        cmd.description
                    );
                }
                None => {
                    println!("'{}' is an invalid command!", command);
                    self.cmd_help(&vec![]).ok();
                }
            }
        } else {
            // Build equals bar and Shave off sides
            let equal_bar = format!(" {} ", BAR1_CHAR.repeat(MAX_MENU_CHAR_WIDTH));

            // Build empty bar and Shave off sides
            let empty_bar = format!("|{}|", INDENT_CHAR.repeat(MAX_MENU_CHAR_WIDTH));

            // Header
            println!(
                "\n{}\n{}\n{}\n{}",
                equal_bar,
                wrap_help_text("Help Menu", MAX_MENU_CHAR_WIDTH, 2),
                // Minus bar
                format!("+{}+", BAR2_CHAR.repeat(MAX_MENU_CHAR_WIDTH)),
                empty_bar
            );

            // Print commands
            for (key, val) in self.command_options.iter() {
                println!(
                    "{}\n{}\n{}",
                    wrap_help_text(&key, MAX_MENU_CHAR_WIDTH, 4),
                    wrap_help_text(&val.description, MAX_MENU_CHAR_WIDTH, 8),
                    empty_bar
                );
            }

            // Footer
            println!("{}\n{}", empty_bar, equal_bar);
        }

        Ok(())
    }

    fn cmd_adapter(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().manager_dbus.get_floss_enabled() {
            return Err("Floss is not enabled. First run, `floss enable`".into());
        }

        let default_adapter = self.lock_context().default_adapter;

        let command = get_arg(args, 0)?;

        match &command[..] {
            "enable" => {
                if self.lock_context().is_restricted {
                    return Err("You are not allowed to toggle adapter power".into());
                }
                self.lock_context().manager_dbus.start(default_adapter);
            }
            "disable" => {
                if self.lock_context().is_restricted {
                    return Err("You are not allowed to toggle adapter power".into());
                }
                self.lock_context().manager_dbus.stop(default_adapter);
            }
            "show" => {
                if !self.lock_context().adapter_ready {
                    return Err(self.adapter_not_ready());
                }

                let enabled = self.lock_context().enabled;
                let address = match self.lock_context().adapter_address.as_ref() {
                    Some(x) => x.clone(),
                    None => String::from(""),
                };
                let context = self.lock_context();
                let adapter_dbus = context.adapter_dbus.as_ref().unwrap();
                let qa_dbus = context.qa_dbus.as_ref().unwrap();
                let name = adapter_dbus.get_name();
                let modalias = qa_dbus.get_modalias();
                let uuids = adapter_dbus.get_uuids();
                let is_discoverable = adapter_dbus.get_discoverable();
                let discoverable_timeout = adapter_dbus.get_discoverable_timeout();
                let cod = adapter_dbus.get_bluetooth_class();
                let multi_adv_supported = adapter_dbus.is_multi_advertisement_supported();
                let le_ext_adv_supported = adapter_dbus.is_le_extended_advertising_supported();
                let wbs_supported = adapter_dbus.is_wbs_supported();
                let supported_profiles = UuidHelper::get_supported_profiles();
                let connected_profiles: Vec<(Profile, ProfileConnectionState)> = supported_profiles
                    .iter()
                    .map(|&prof| {
                        if let Some(uuid) = UuidHelper::get_profile_uuid(&prof) {
                            (prof, adapter_dbus.get_profile_connection_state(uuid.clone()))
                        } else {
                            (prof, ProfileConnectionState::Disconnected)
                        }
                    })
                    .filter(|(_prof, state)| state != &ProfileConnectionState::Disconnected)
                    .collect();
                qa_dbus.fetch_connectable();
                qa_dbus.fetch_alias();
                qa_dbus.fetch_discoverable_mode();
                print_info!("Address: {}", address);
                print_info!("Name: {}", name);
                print_info!("Modalias: {}", modalias);
                print_info!("State: {}", if enabled { "enabled" } else { "disabled" });
                print_info!("Discoverable: {}", is_discoverable);
                print_info!("DiscoverableTimeout: {}s", discoverable_timeout);
                print_info!("Class: {:#06x}", cod);
                print_info!("IsMultiAdvertisementSupported: {}", multi_adv_supported);
                print_info!("IsLeExtendedAdvertisingSupported: {}", le_ext_adv_supported);
                print_info!("Connected profiles: {:?}", connected_profiles);
                print_info!("IsWbsSupported: {}", wbs_supported);
                print_info!(
                    "Uuids: {}",
                    DisplayList(
                        uuids
                            .iter()
                            .map(|&x| UuidHelper::known_uuid_to_string(&x))
                            .collect::<Vec<String>>()
                    )
                );
            }
            "discoverable" => match &get_arg(args, 1)?[..] {
                "on" => {
                    let duration = String::from(get_arg(args, 2)?)
                        .parse::<u32>()
                        .or(Err("Failed parsing duration."))?;

                    let discoverable = self
                        .lock_context()
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .set_discoverable(BtDiscMode::GeneralDiscoverable, duration);
                    print_info!(
                        "Set discoverable for {} seconds: {}",
                        duration,
                        if discoverable { "succeeded" } else { "failed" }
                    );
                }
                "limited" => {
                    let duration = String::from(get_arg(args, 2)?)
                        .parse::<u32>()
                        .or(Err("Failed parsing duration."))?;

                    let discoverable = self
                        .lock_context()
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .set_discoverable(BtDiscMode::LimitedDiscoverable, duration);
                    print_info!(
                        "Set limited discoverable for {} seconds: {}",
                        duration,
                        if discoverable { "succeeded" } else { "failed" }
                    );
                }
                "off" => {
                    let discoverable = self
                        .lock_context()
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .set_discoverable(BtDiscMode::NonDiscoverable, 0 /*not used*/);
                    print_info!(
                        "Turn discoverable off: {}",
                        if discoverable { "succeeded" } else { "failed" }
                    );
                }
                other => println!("Invalid argument for adapter discoverable '{}'", other),
            },
            "connectable" => match &get_arg(args, 1)?[..] {
                "on" => {
                    self.lock_context().qa_dbus.as_mut().unwrap().set_connectable(true);
                }
                "off" => {
                    self.lock_context().qa_dbus.as_mut().unwrap().set_connectable(false);
                }
                other => println!("Invalid argument for adapter connectable '{}'", other),
            },
            "set-name" => {
                if let Some(name) = args.get(1) {
                    self.lock_context().adapter_dbus.as_ref().unwrap().set_name(name.to_string());
                } else {
                    println!("usage: adapter set-name <name>");
                }
            }

            _ => return Err(CommandError::InvalidArgs),
        };

        Ok(())
    }

    fn cmd_get_address(&mut self, _args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let address = self.lock_context().update_adapter_address();
        print_info!("Local address = {}", &address);
        Ok(())
    }

    fn cmd_discovery(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "start" => {
                self.lock_context().adapter_dbus.as_mut().unwrap().start_discovery();
            }
            "stop" => {
                self.lock_context().adapter_dbus.as_mut().unwrap().cancel_discovery();
            }
            _ => return Err(CommandError::InvalidArgs),
        }

        Ok(())
    }

    fn cmd_battery(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;
        let address = get_arg(args, 1)?.to_uppercase();

        match &command[..] {
            "status" => {
                match self
                    .lock_context()
                    .battery_manager_dbus
                    .as_ref()
                    .unwrap()
                    .get_battery_information(address.clone())
                {
                    None => println!(
                        "Battery status for device {} could not be fetched",
                        address.clone()
                    ),
                    Some(set) => {
                        if set.batteries.len() == 0 {
                            println!("Battery set for device {} is empty", set.address.clone());
                            return Ok(());
                        }

                        println!(
                            "Battery data for '{}' from source '{}' and uuid '{}':",
                            set.address.clone(),
                            set.source_uuid.clone(),
                            set.source_info.clone()
                        );
                        for battery in set.batteries {
                            println!("   {}%, variant: '{}'", battery.percentage, battery.variant);
                        }
                    }
                }
            }
            "track" => {
                if self.lock_context().battery_address_filter.contains(&address) {
                    println!("Already tracking {}", address.clone());
                    return Ok(());
                }
                self.lock_context().battery_address_filter.insert(address.clone());

                println!("Currently tracking:");
                for addr in self.lock_context().battery_address_filter.iter() {
                    println!("{}", addr);
                }
            }
            "untrack" => {
                if !self.lock_context().battery_address_filter.remove(&address) {
                    println!("Not tracking {}", address.clone());
                    return Ok(());
                }
                println!("Stopped tracking {}", address.clone());

                if self.lock_context().battery_address_filter.len() == 0 {
                    println!("No longer tracking any addresses for battery status updates");
                    return Ok(());
                }

                println!("Currently tracking:");
                for addr in self.lock_context().battery_address_filter.iter() {
                    println!("{}", addr);
                }
            }
            _ => return Err(CommandError::InvalidArgs),
        }
        Ok(())
    }

    fn cmd_bond(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "add" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                let bonding_attempt = &self.lock_context().bonding_attempt.as_ref().cloned();

                if bonding_attempt.is_some() {
                    return Err(format!(
                        "Already bonding [{}]. Cancel bonding first.",
                        bonding_attempt.as_ref().unwrap().address,
                    )
                    .into());
                }

                let success = self
                    .lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .create_bond(device.clone(), BtTransport::Auto);

                if success {
                    self.lock_context().bonding_attempt = Some(device);
                }
            }
            "remove" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                self.lock_context().adapter_dbus.as_mut().unwrap().remove_bond(device);
            }
            "cancel" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                self.lock_context().adapter_dbus.as_mut().unwrap().cancel_bond_process(device);
            }
            other => {
                println!("Invalid argument '{}'", other);
            }
        }

        Ok(())
    }

    fn cmd_device(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = &get_arg(args, 0)?;

        match &command[..] {
            "connect" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                let success = self
                    .lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .connect_all_enabled_profiles(device.clone());

                if success {
                    println!("Connecting to {}", &device.address);
                } else {
                    println!("Can't connect to {}", &device.address);
                }
            }
            "disconnect" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                let success = self
                    .lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .disconnect_all_enabled_profiles(device.clone());

                if success {
                    println!("Disconnecting from {}", &device.address);
                } else {
                    println!("Can't disconnect from {}", &device.address);
                }
            }
            "info" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from("Classic Device"),
                };

                let (
                    name,
                    alias,
                    device_type,
                    addr_type,
                    class,
                    appearance,
                    bonded,
                    connection_state,
                    uuids,
                    wake_allowed,
                ) = {
                    let ctx = self.lock_context();
                    let adapter = ctx.adapter_dbus.as_ref().unwrap();

                    let name = adapter.get_remote_name(device.clone());
                    let device_type = adapter.get_remote_type(device.clone());
                    let addr_type = adapter.get_remote_address_type(device.clone());
                    let alias = adapter.get_remote_alias(device.clone());
                    let class = adapter.get_remote_class(device.clone());
                    let appearance = adapter.get_remote_appearance(device.clone());
                    let bonded = adapter.get_bond_state(device.clone());
                    let connection_state = match adapter.get_connection_state(device.clone()) {
                        BtConnectionState::NotConnected => "Not Connected",
                        BtConnectionState::ConnectedOnly => "Connected",
                        _ => "Connected and Paired",
                    };
                    let uuids = adapter.get_remote_uuids(device.clone());
                    let wake_allowed = adapter.get_remote_wake_allowed(device.clone());

                    (
                        name,
                        alias,
                        device_type,
                        addr_type,
                        class,
                        appearance,
                        bonded,
                        connection_state,
                        uuids,
                        wake_allowed,
                    )
                };

                print_info!("Address: {}", &device.address);
                print_info!("Name: {}", name);
                print_info!("Alias: {}", alias);
                print_info!("Device Type: {:?}", device_type);
                print_info!("Address Type: {:?}", addr_type);
                print_info!("Class: {}", class);
                print_info!("Appearance: {}", appearance);
                print_info!("Wake Allowed: {}", wake_allowed);
                print_info!("Bond State: {:?}", bonded);
                print_info!("Connection State: {}", connection_state);
                print_info!(
                    "Uuids: {}",
                    DisplayList(
                        uuids
                            .iter()
                            .map(|&x| UuidHelper::known_uuid_to_string(&x))
                            .collect::<Vec<String>>()
                    )
                );
            }
            "set-alias" => {
                let new_alias = get_arg(args, 2)?;
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };
                let old_alias = self
                    .lock_context()
                    .adapter_dbus
                    .as_ref()
                    .unwrap()
                    .get_remote_alias(device.clone());
                println!(
                    "Updating alias for {}: {} -> {}",
                    get_arg(args, 1)?,
                    old_alias,
                    new_alias
                );
                self.lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .set_remote_alias(device.clone(), new_alias.clone());
            }
            "set-pairing-confirmation" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };
                let accept = match &get_arg(args, 2)?[..] {
                    "accept" => true,
                    "reject" => false,
                    other => {
                        return Err(format!("Failed to parse '{}'", other).into());
                    }
                };

                self.lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .set_pairing_confirmation(device.clone(), accept);
            }
            "set-pairing-pin" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };
                let pin = get_arg(args, 2)?;

                let (accept, pin) = match (&pin[..], pin) {
                    ("reject", _) => (false, vec![]),
                    (_, p) => (true, p.as_bytes().iter().cloned().collect::<Vec<u8>>()),
                };

                self.lock_context().adapter_dbus.as_mut().unwrap().set_pin(
                    device.clone(),
                    accept,
                    pin,
                );
            }
            "set-pairing-passkey" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };
                let passkey = get_arg(args, 2)?;
                let (accept, passkey) = match (&passkey[..], String::from(passkey).parse::<u32>()) {
                    (_, Ok(p)) => (true, Vec::from(p.to_ne_bytes())),
                    ("reject", _) => (false, vec![]),
                    _ => {
                        return Err(format!("Failed to parse '{}'", passkey).into());
                    }
                };

                self.lock_context().adapter_dbus.as_mut().unwrap().set_passkey(
                    device.clone(),
                    accept,
                    passkey,
                );
            }
            "get-rssi" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };

                match self
                    .lock_context()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .get_remote_rssi(device.clone())
                {
                    INVALID_RSSI => {
                        println!("Invalid RSSI");
                    }
                    rssi => {
                        println!("RSSI: {}", rssi);
                    }
                };
            }
            other => {
                println!("Invalid argument '{}'", other);
            }
        }

        Ok(())
    }

    fn cmd_floss(&mut self, args: &Vec<String>) -> CommandResult {
        let command = get_arg(args, 0)?;

        match &command[..] {
            "enable" => {
                self.lock_context().manager_dbus.set_floss_enabled(true);
            }
            "disable" => {
                self.lock_context().manager_dbus.set_floss_enabled(false);
            }
            "show" => {
                let (major, minor) = self.lock_context().get_floss_api_version();
                print_info!("Floss API version: {}.{}", major, minor);
                print_info!(
                    "Floss enabled: {}",
                    self.lock_context().manager_dbus.get_floss_enabled()
                );
            }
            _ => return Err(CommandError::InvalidArgs),
        }

        Ok(())
    }

    fn cmd_gatt(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "register-client" => {
                let dbus_connection = self.lock_context().dbus_connection.clone();
                let dbus_crossroads = self.lock_context().dbus_crossroads.clone();

                self.lock_context().gatt_dbus.as_mut().unwrap().register_client(
                    String::from(GATT_CLIENT_APP_UUID),
                    Box::new(BtGattCallback::new(
                        String::from("/org/chromium/bluetooth/client/bluetooth_gatt_callback"),
                        self.context.clone(),
                        dbus_connection,
                        dbus_crossroads,
                    )),
                    false,
                );
            }
            "client-connect" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let addr = String::from(get_arg(args, 1)?);
                let is_direct = self.lock_context().gatt_client_context.is_connect_direct;
                let transport = self.lock_context().gatt_client_context.connect_transport;
                let oppurtunistic = self.lock_context().gatt_client_context.connect_opportunistic;
                let phy = self.lock_context().gatt_client_context.connect_phy;

                println!("Initiating GATT client connect. client_id: {}, addr: {}, is_direct: {}, transport: {:?}, oppurtunistic: {}, phy: {:?}", client_id, addr, is_direct, transport, oppurtunistic, phy);
                self.lock_context().gatt_dbus.as_ref().unwrap().client_connect(
                    client_id,
                    addr,
                    is_direct,
                    transport,
                    oppurtunistic,
                    phy,
                );
            }
            "client-disconnect" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let addr = String::from(get_arg(args, 1)?);
                self.lock_context().gatt_dbus.as_ref().unwrap().client_disconnect(client_id, addr);
            }
            "client-read-phy" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;
                let addr = String::from(get_arg(args, 1)?);
                self.lock_context().gatt_dbus.as_mut().unwrap().client_read_phy(client_id, addr);
            }
            "client-discover-services" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let addr = String::from(get_arg(args, 1)?);
                self.lock_context().gatt_dbus.as_ref().unwrap().discover_services(client_id, addr);
            }
            "client-discover-service-by-uuid-pts" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;
                let addr = String::from(get_arg(args, 1)?);
                let uuid = String::from(get_arg(args, 2)?);
                self.lock_context()
                    .gatt_dbus
                    .as_ref()
                    .unwrap()
                    .btif_gattc_discover_service_by_uuid(client_id, addr, uuid);
            }
            "configure-mtu" => {
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let addr = String::from(get_arg(args, 1)?);
                let mtu =
                    String::from(get_arg(args, 2)?).parse::<i32>().or(Err("Failed parsing mtu"))?;

                self.lock_context().gatt_dbus.as_ref().unwrap().configure_mtu(client_id, addr, mtu)
            }
            "set-direct-connect" => {
                let is_direct = String::from(get_arg(args, 1)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse is_direct"))?;

                self.lock_context().gatt_client_context.is_connect_direct = is_direct;
            }
            "set-connect-transport" => {
                let transport = match &get_arg(args, 1)?[..] {
                    "Bredr" => BtTransport::Bredr,
                    "LE" => BtTransport::Le,
                    "Auto" => BtTransport::Auto,
                    _ => {
                        return Err("Failed to parse transport".into());
                    }
                };
                self.lock_context().gatt_client_context.connect_transport = transport;
            }
            "set-connect-opportunistic" => {
                let opportunistic = String::from(get_arg(args, 1)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse opportunistic"))?;

                self.lock_context().gatt_client_context.connect_opportunistic = opportunistic;
            }
            "set-connect-phy" => {
                let phy = match &get_arg(args, 1)?[..] {
                    "Phy1m" => LePhy::Phy1m,
                    "Phy2m" => LePhy::Phy2m,
                    "PhyCoded" => LePhy::PhyCoded,
                    _ => {
                        return Err("Failed to parse phy".into());
                    }
                };

                self.lock_context().gatt_client_context.connect_phy = phy;
            }
            "set-auth-req" => {
                let flag = match &get_arg(args, 1)?[..] {
                    "NONE" => AuthReq::NONE,
                    "EncNoMitm" => AuthReq::EncNoMitm,
                    "EncMitm" => AuthReq::EncMitm,
                    "SignedNoMitm" => AuthReq::SignedNoMitm,
                    "SignedMitm" => AuthReq::SignedMitm,
                    _ => {
                        return Err("Failed to parse auth-req".into());
                    }
                };

                self.lock_context().gatt_client_context.auth_req = flag;
                println!("AuthReq: {:?}", self.lock_context().gatt_client_context.get_auth_req());
            }
            "write-characteristic" => {
                let addr = String::from(get_arg(args, 1)?);
                let handle = String::from(get_arg(args, 2)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse handle"))?;

                let write_type = match &get_arg(args, 3)?[..] {
                    "NoRsp" => GattWriteType::WriteNoRsp,
                    "Write" => GattWriteType::Write,
                    "Prepare" => GattWriteType::WritePrepare,
                    _ => {
                        return Err("Failed to parse write-type".into());
                    }
                };

                let value = hex::decode(&get_arg(args, 4)?).or(Err("Failed to parse value"))?;

                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let auth_req = self.lock_context().gatt_client_context.get_auth_req().into();

                self.lock_context()
                    .gatt_dbus
                    .as_ref()
                    .unwrap()
                    .write_characteristic(client_id, addr, handle, write_type, auth_req, value);
            }
            "read-characteristic" => {
                let addr = String::from(get_arg(args, 1)?);
                let handle = String::from(get_arg(args, 2)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse handle"))?;
                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let auth_req = self.lock_context().gatt_client_context.get_auth_req().into();

                self.lock_context()
                    .gatt_dbus
                    .as_ref()
                    .unwrap()
                    .read_characteristic(client_id, addr, handle, auth_req);
            }
            "read-characteristic-by-uuid" => {
                let addr = String::from(get_arg(args, 1)?);
                let uuid = String::from(get_arg(args, 2)?);
                let start_handle = String::from(get_arg(args, 3)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse start handle"))?;
                let end_handle = String::from(get_arg(args, 4)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse end handle"))?;

                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                let auth_req = self.lock_context().gatt_client_context.get_auth_req().into();

                self.lock_context().gatt_dbus.as_ref().unwrap().read_using_characteristic_uuid(
                    client_id,
                    addr,
                    uuid,
                    start_handle,
                    end_handle,
                    auth_req,
                );
            }
            "register-notification" => {
                let addr = String::from(get_arg(args, 1)?);
                let handle = String::from(get_arg(args, 2)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse handle"))?;
                let enable = match &get_arg(args, 3)?[..] {
                    "enable" => true,
                    "disable" => false,
                    _ => {
                        return Err("Failed to parse enable".into());
                    }
                };

                let client_id = self
                    .lock_context()
                    .gatt_client_context
                    .client_id
                    .ok_or("GATT client is not yet registered.")?;

                self.lock_context()
                    .gatt_dbus
                    .as_ref()
                    .unwrap()
                    .register_for_notification(client_id, addr, handle, enable);
            }
            "register-server" => {
                let dbus_connection = self.lock_context().dbus_connection.clone();
                let dbus_crossroads = self.lock_context().dbus_crossroads.clone();

                self.lock_context().gatt_dbus.as_mut().unwrap().register_server(
                    String::from(GATT_SERVER_APP_UUID),
                    Box::new(BtGattServerCallback::new(
                        String::from(
                            "/org/chromium/bluetooth/client/bluetooth_gatt_server_callback",
                        ),
                        self.context.clone(),
                        dbus_connection,
                        dbus_crossroads,
                    )),
                    false,
                );
            }
            "unregister-server" => {
                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing server id"))?;

                self.lock_context().gatt_dbus.as_mut().unwrap().unregister_server(server_id);
            }
            "server-connect" => {
                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse server_id"))?;
                let client_addr = String::from(get_arg(args, 2)?);
                let is_direct = self.lock_context().gatt_server_context.is_connect_direct;
                let transport = self.lock_context().gatt_server_context.connect_transport;

                if !self.lock_context().gatt_dbus.as_mut().unwrap().server_connect(
                    server_id,
                    client_addr.clone(),
                    is_direct,
                    transport,
                ) {
                    return Err("Connection was unsuccessful".into());
                }
            }
            "server-disconnect" => {
                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse server_id"))?;
                let client_addr = String::from(get_arg(args, 2)?);

                if !self
                    .lock_context()
                    .gatt_dbus
                    .as_mut()
                    .unwrap()
                    .server_disconnect(server_id, client_addr.clone())
                {
                    return Err("Disconnection was unsuccessful".into());
                }
            }
            "server-add-heartrate-service" => {
                let uuid = Uuid::from(UuidHelper::from_string(HEART_RATE_SERVICE_UUID).unwrap());

                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse server_id"))?;
                let service = BluetoothGattService::new(
                    uuid.into(),
                    0, // libbluetooth assigns this handle once the service is added
                    GattDbElementType::PrimaryService.into(),
                );

                self.lock_context().gatt_dbus.as_mut().unwrap().add_service(server_id, service);
            }
            "server-remove-service" => {
                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse server_id"))?;
                let service_handle = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse service handle"))?;

                self.lock_context()
                    .gatt_dbus
                    .as_mut()
                    .unwrap()
                    .remove_service(server_id, service_handle);
            }
            "server-clear-all-services" => {
                let server_id = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed to parse server_id"))?;
                self.lock_context().gatt_dbus.as_mut().unwrap().clear_services(server_id);
            }
            "server-set-direct-connect" => {
                let is_direct = String::from(get_arg(args, 1)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse is_direct"))?;

                self.lock_context().gatt_server_context.is_connect_direct = is_direct;
            }
            "server-set-connect-transport" => {
                let transport = match &get_arg(args, 1)?[..] {
                    "Bredr" => BtTransport::Bredr,
                    "LE" => BtTransport::Le,
                    "Auto" => BtTransport::Auto,
                    _ => {
                        return Err("Failed to parse transport".into());
                    }
                };
                self.lock_context().gatt_server_context.connect_transport = transport;
            }
            _ => return Err(CommandError::InvalidArgs),
        }
        Ok(())
    }

    fn cmd_le_scan(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "register-scanner" => {
                let scanner_callback_id = self
                    .lock_context()
                    .scanner_callback_id
                    .ok_or("Cannot register scanner before registering scanner callback")?;

                let uuid = self
                    .lock_context()
                    .gatt_dbus
                    .as_mut()
                    .unwrap()
                    .register_scanner(scanner_callback_id);

                print_info!("Scanner to be registered with UUID = {}", UuidWrapper(&uuid));
            }
            "unregister-scanner" => {
                let scanner_id = String::from(get_arg(args, 1)?)
                    .parse::<u8>()
                    .or(Err("Failed parsing scanner id"))?;

                self.lock_context().gatt_dbus.as_mut().unwrap().unregister_scanner(scanner_id);
            }
            "start-scan" => {
                let scanner_id = String::from(get_arg(args, 1)?)
                    .parse::<u8>()
                    .or(Err("Failed parsing scanner id"))?;

                self.lock_context().gatt_dbus.as_mut().unwrap().start_scan(
                    scanner_id,
                    // TODO(b/254870159): Construct real settings and filters depending on
                    // command line options.
                    None,
                    Some(btstack::bluetooth_gatt::ScanFilter {
                        rssi_high_threshold: 0,
                        rssi_low_threshold: 0,
                        rssi_low_timeout: 0,
                        rssi_sampling_period: 0,
                        condition: btstack::bluetooth_gatt::ScanFilterCondition::Patterns(vec![]),
                    }),
                );

                self.lock_context().active_scanner_ids.insert(scanner_id);
            }
            "stop-scan" => {
                let scanner_id = String::from(get_arg(args, 1)?)
                    .parse::<u8>()
                    .or(Err("Failed parsing scanner id"))?;

                self.lock_context().gatt_dbus.as_mut().unwrap().stop_scan(scanner_id);
                self.lock_context().active_scanner_ids.remove(&scanner_id);
            }
            _ => return Err(CommandError::InvalidArgs),
        }

        Ok(())
    }

    // TODO(b/233128828): More options will be implemented to test BLE advertising.
    // Such as setting advertising parameters, starting multiple advertising sets, etc.
    fn cmd_advertise(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        if self.lock_context().advertiser_callback_id == None {
            return Err("No advertiser callback registered".into());
        }

        let callback_id = self.lock_context().advertiser_callback_id.clone().unwrap();

        let command = get_arg(args, 0)?;

        match &command[..] {
            "on" => {
                print_info!("Creating legacy advertising set...");
                let s = AdvSet::new(true); // legacy advertising
                AdvSet::start(self.context.clone(), s, callback_id);
            }
            "off" => {
                AdvSet::stop_all(self.context.clone());
            }
            "ext" => {
                print_info!("Creating extended advertising set...");
                let s = AdvSet::new(false); // extended advertising
                AdvSet::start(self.context.clone(), s, callback_id);
            }
            "set-interval" => {
                let ms = String::from(get_arg(args, 1)?).parse::<i32>();
                if !ms.is_ok() {
                    return Err("Failed parsing interval".into());
                }
                let interval = ms.unwrap() * 8 / 5; // in 0.625 ms.

                let mut context = self.lock_context();
                context.adv_sets.iter_mut().for_each(|(_, s)| s.params.interval = interval);

                // To avoid borrowing context as mutable from an immutable borrow.
                // Required information is collected in advance and then passed
                // to the D-Bus call which requires a mutable borrow.
                let advs: Vec<(_, _)> = context
                    .adv_sets
                    .iter()
                    .filter_map(|(_, s)| s.adv_id.map(|adv_id| (adv_id.clone(), s.params.clone())))
                    .collect();
                for (adv_id, params) in advs {
                    print_info!("Setting advertising parameters for {}", adv_id);
                    context.gatt_dbus.as_mut().unwrap().set_advertising_parameters(adv_id, params);
                }
            }
            "set-connectable" => {
                let connectable = match &get_arg(args, 1)?[..] {
                    "on" => true,
                    "off" => false,
                    _ => false,
                };

                let adv_id = String::from(get_arg(args, 2)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing adv_id"))?;

                let mut context = self.context.lock().unwrap();

                let advs: Vec<(_, _)> = context
                    .adv_sets
                    .iter_mut()
                    .filter_map(|(_, s)| {
                        if !(s.adv_id.map_or(false, |id| id == adv_id)) {
                            return None;
                        }
                        s.params.connectable = connectable;
                        Some((s.params.clone(), s.data.clone()))
                    })
                    .collect();

                for (params, data) in advs {
                    print_info!("Setting advertising parameters for {}", adv_id);
                    context.gatt_dbus.as_mut().unwrap().set_advertising_parameters(adv_id, params);

                    // renew the flags
                    print_info!("Setting advertising data for {}", adv_id);
                    context.gatt_dbus.as_mut().unwrap().set_advertising_data(adv_id, data);
                }
            }
            "set-scan-rsp" => {
                let enable = match &get_arg(args, 1)?[..] {
                    "enable" => true,
                    "disable" => false,
                    _ => false,
                };

                let mut context = self.lock_context();
                context.adv_sets.iter_mut().for_each(|(_, s)| s.params.scannable = enable);

                let advs: Vec<(_, _, _)> = context
                    .adv_sets
                    .iter()
                    .filter_map(|(_, s)| {
                        s.adv_id
                            .map(|adv_id| (adv_id.clone(), s.params.clone(), s.scan_rsp.clone()))
                    })
                    .collect();
                for (adv_id, params, scan_rsp) in advs {
                    print_info!("Setting scan response data for {}", adv_id);
                    context.gatt_dbus.as_mut().unwrap().set_scan_response_data(adv_id, scan_rsp);
                    print_info!("Setting parameters for {}", adv_id);
                    context.gatt_dbus.as_mut().unwrap().set_advertising_parameters(adv_id, params);
                }
            }
            "set-raw-data" => {
                let data = hex::decode(get_arg(args, 1)?).or(Err("Failed parsing data"))?;

                let adv_id = String::from(get_arg(args, 2)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing adv_id"))?;

                let mut context = self.context.lock().unwrap();
                if context
                    .adv_sets
                    .iter()
                    .find(|(_, s)| s.adv_id.map_or(false, |id| id == adv_id))
                    .is_none()
                {
                    return Err("Failed to find advertising set".into());
                }

                print_info!("Setting advertising data for {}", adv_id);
                context.gatt_dbus.as_mut().unwrap().set_raw_adv_data(adv_id, data);
            }
            _ => return Err(CommandError::InvalidArgs),
        }

        Ok(())
    }

    fn cmd_sdp(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "search" => {
                let device = BluetoothDevice {
                    address: String::from(get_arg(args, 1)?),
                    name: String::from(""),
                };
                let uuid = match UuidHelper::parse_string(get_arg(args, 2)?) {
                    Some(uu) => uu.uu,
                    None => return Err(CommandError::Failed("Invalid UUID".into())),
                };
                let success =
                    self.lock_context().adapter_dbus.as_ref().unwrap().sdp_search(device, uuid);
                if !success {
                    return Err("Unable to execute SDP search".into());
                }
            }
            _ => return Err(CommandError::InvalidArgs),
        }
        Ok(())
    }

    fn cmd_socket(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let callback_id = match self.lock_context().socket_manager_callback_id.clone() {
            Some(id) => id,
            None => {
                return Err("No socket manager callback registered.".into());
            }
        };

        let command = get_arg(args, 0)?;

        match &command[..] {
            "set-on-connect-schedule" => {
                let schedule = match &get_arg(args, 1)?[..] {
                    "send" => SocketSchedule {
                        num_frame: 1,
                        send_interval: Duration::from_millis(0),
                        disconnect_delay: Duration::from_secs(30),
                    },
                    "resend" => SocketSchedule {
                        num_frame: 3,
                        send_interval: Duration::from_millis(100),
                        disconnect_delay: Duration::from_secs(30),
                    },
                    "dump" => SocketSchedule {
                        num_frame: 0,
                        send_interval: Duration::from_millis(0),
                        disconnect_delay: Duration::from_secs(30),
                    },
                    _ => {
                        return Err("Failed to parse schedule".into());
                    }
                };

                self.context.lock().unwrap().socket_test_schedule = Some(schedule);
            }
            "send-msc" => {
                let dlci =
                    String::from(get_arg(args, 1)?).parse::<u8>().or(Err("Failed parsing DLCI"))?;
                let addr = String::from(get_arg(args, 2)?);
                self.context.lock().unwrap().qa_dbus.as_mut().unwrap().rfcomm_send_msc(dlci, addr);
            }
            "listen-rfcomm" => {
                let scn = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing Service Channel Number"))?;
                let SocketResult { status, id } = self
                    .context
                    .lock()
                    .unwrap()
                    .socket_manager_dbus
                    .as_mut()
                    .unwrap()
                    .listen_using_rfcomm(callback_id, Some(scn), None, None, None);
                if status != BtStatus::Success {
                    return Err(format!(
                        "Failed to request for listening using rfcomm, status = {:?}",
                        status,
                    )
                    .into());
                }
                print_info!("Requested for listening using rfcomm on socket {}", id);
            }
            "listen" => {
                let auth_required = String::from(get_arg(args, 1)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse auth-required"))?;
                let is_le = match &get_arg(args, 2)?[..] {
                    "LE" => true,
                    "Bredr" => false,
                    _ => {
                        return Err("Failed to parse socket type".into());
                    }
                };

                let SocketResult { status, id } = {
                    let mut context_proxy = self.context.lock().unwrap();
                    let proxy = context_proxy.socket_manager_dbus.as_mut().unwrap();
                    if auth_required {
                        if is_le {
                            proxy.listen_using_l2cap_le_channel(callback_id)
                        } else {
                            proxy.listen_using_l2cap_channel(callback_id)
                        }
                    } else {
                        if is_le {
                            proxy.listen_using_insecure_l2cap_le_channel(callback_id)
                        } else {
                            proxy.listen_using_insecure_l2cap_channel(callback_id)
                        }
                    }
                };

                if status != BtStatus::Success {
                    return Err(format!(
                        "Failed to request for listening using l2cap channel, status = {:?}",
                        status,
                    )
                    .into());
                }
                print_info!("Requested for listening using l2cap channel on socket {}", id);
            }
            "connect" => {
                let (addr, sock_type, psm_or_uuid) =
                    (&get_arg(args, 1)?, &get_arg(args, 2)?, &get_arg(args, 3)?);
                let device = BluetoothDevice {
                    address: String::from(*addr),
                    name: String::from("Socket Connect Device"),
                };

                let auth_required = String::from(get_arg(args, 4)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse auth-required"))?;

                let is_le = match &get_arg(args, 5)?[..] {
                    "LE" => true,
                    "Bredr" => false,
                    _ => {
                        return Err("Failed to parse socket type".into());
                    }
                };

                let SocketResult { status, id } = {
                    let mut context_proxy = self.context.lock().unwrap();
                    let proxy = context_proxy.socket_manager_dbus.as_mut().unwrap();

                    match &sock_type[0..] {
                        "l2cap" => {
                            let psm = match psm_or_uuid.parse::<i32>() {
                                Ok(v) => v,
                                Err(e) => {
                                    return Err(CommandError::Failed(format!(
                                        "Bad PSM given. Error={}",
                                        e
                                    )));
                                }
                            };

                            if auth_required {
                                if is_le {
                                    proxy.create_l2cap_le_channel(callback_id, device, psm)
                                } else {
                                    proxy.create_l2cap_channel(callback_id, device, psm)
                                }
                            } else {
                                if is_le {
                                    proxy.create_insecure_l2cap_le_channel(callback_id, device, psm)
                                } else {
                                    proxy.create_insecure_l2cap_channel(callback_id, device, psm)
                                }
                            }
                        }
                        "rfcomm" => {
                            let uuid = match UuidHelper::parse_string(*psm_or_uuid) {
                                Some(uu) => uu,
                                None => {
                                    return Err(CommandError::Failed(format!(
                                        "Could not parse given uuid."
                                    )));
                                }
                            };

                            if auth_required {
                                proxy.create_rfcomm_socket_to_service_record(
                                    callback_id,
                                    device,
                                    uuid,
                                )
                            } else {
                                proxy.create_insecure_rfcomm_socket_to_service_record(
                                    callback_id,
                                    device,
                                    uuid,
                                )
                            }
                        }
                        _ => {
                            return Err(CommandError::Failed(format!(
                                "Unknown socket type: {}",
                                sock_type
                            )));
                        }
                    }
                };

                if status != BtStatus::Success {
                    return Err(CommandError::Failed(format!("Failed to create socket with status={:?} against {}, type {}, with psm/uuid {}",
                        status, addr, sock_type, psm_or_uuid)));
                } else {
                    print_info!("Called create socket with result ({:?}, {}) against {}, type {}, with psm/uuid {}",
                    status, id, addr, sock_type, psm_or_uuid);
                }
            }
            "close" => {
                let sockid = String::from(get_arg(args, 1)?)
                    .parse::<u64>()
                    .or(Err("Failed parsing socket ID"))?;
                let status = self
                    .context
                    .lock()
                    .unwrap()
                    .socket_manager_dbus
                    .as_mut()
                    .unwrap()
                    .close(callback_id, sockid);
                if status != BtStatus::Success {
                    return Err(format!(
                        "Failed to close the listening socket, status = {:?}",
                        status,
                    )
                    .into());
                }
            }

            _ => return Err(CommandError::InvalidArgs),
        };

        Ok(())
    }

    fn cmd_hid(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.context.lock().unwrap().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "get-report" => {
                let addr = String::from(get_arg(args, 1)?);
                let report_type = match &get_arg(args, 2)?[..] {
                    "Input" => BthhReportType::InputReport,
                    "Output" => BthhReportType::OutputReport,
                    "Feature" => BthhReportType::FeatureReport,
                    _ => {
                        return Err("Failed to parse report type".into());
                    }
                };
                let report_id = String::from(get_arg(args, 3)?)
                    .parse::<u8>()
                    .or(Err("Failed parsing report_id"))?;

                self.context.lock().unwrap().qa_dbus.as_mut().unwrap().get_hid_report(
                    addr,
                    report_type,
                    report_id,
                );
            }
            "set-report" => {
                let addr = String::from(get_arg(args, 1)?);
                let report_type = match &get_arg(args, 2)?[..] {
                    "Input" => BthhReportType::InputReport,
                    "Output" => BthhReportType::OutputReport,
                    "Feature" => BthhReportType::FeatureReport,
                    _ => {
                        return Err("Failed to parse report type".into());
                    }
                };
                let report_value = String::from(get_arg(args, 3)?);

                self.context.lock().unwrap().qa_dbus.as_mut().unwrap().set_hid_report(
                    addr,
                    report_type,
                    report_value,
                );
            }
            "send-data" => {
                let addr = String::from(get_arg(args, 1)?);
                let data = String::from(get_arg(args, 2)?);

                self.context.lock().unwrap().qa_dbus.as_mut().unwrap().send_hid_data(addr, data);
            }
            _ => return Err(CommandError::InvalidArgs),
        };

        Ok(())
    }

    /// Get the list of rules of supported commands
    pub fn get_command_rule_list(&self) -> Vec<String> {
        self.command_options.values().flat_map(|cmd| cmd.rules.clone()).collect()
    }

    fn cmd_list_devices(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.lock_context().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "bonded" => {
                print_info!("Known bonded devices:");
                let devices =
                    self.lock_context().adapter_dbus.as_ref().unwrap().get_bonded_devices();
                for device in devices.iter() {
                    print_info!("[{:17}] {}", device.address, device.name);
                }
            }
            "found" => {
                print_info!("Devices found in most recent discovery session:");
                for (key, val) in self.lock_context().found_devices.iter() {
                    print_info!("[{:17}] {}", key, val.name);
                }
            }
            "connected" => {
                print_info!("Connected devices:");
                let devices =
                    self.lock_context().adapter_dbus.as_ref().unwrap().get_connected_devices();
                for device in devices.iter() {
                    print_info!("[{:17}] {}", device.address, device.name);
                }
            }
            other => {
                println!("Invalid argument '{}'", other);
            }
        }

        Ok(())
    }

    fn cmd_telephony(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.context.lock().unwrap().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        match &get_arg(args, 0)?[..] {
            "set-network" => {
                self.context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .set_network_available(match &get_arg(args, 1)?[..] {
                        "on" => true,
                        "off" => false,
                        other => {
                            return Err(format!("Invalid argument '{}'", other).into());
                        }
                    });
            }
            "set-roaming" => {
                self.context.lock().unwrap().telephony_dbus.as_mut().unwrap().set_roaming(
                    match &get_arg(args, 1)?[..] {
                        "on" => true,
                        "off" => false,
                        other => {
                            return Err(format!("Invalid argument '{}'", other).into());
                        }
                    },
                );
            }
            "set-signal" => {
                let strength = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing signal strength"))?;
                if strength < 0 || strength > 5 {
                    return Err(
                        format!("Invalid signal strength, got {}, want 0 to 5", strength).into()
                    );
                }
                self.context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .set_signal_strength(strength);
            }
            "set-battery" => {
                let level = String::from(get_arg(args, 1)?)
                    .parse::<i32>()
                    .or(Err("Failed parsing battery level"))?;
                if level < 0 || level > 5 {
                    return Err(format!("Invalid battery level, got {}, want 0 to 5", level).into());
                }
                self.context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .set_battery_level(level);
            }
            "enable" => {
                let mut context = self.lock_context();
                context.telephony_dbus.as_mut().unwrap().set_mps_qualification_enabled(true);
                if context.mps_sdp_handle.is_none() {
                    let success = context
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .create_sdp_record(BtSdpRecord::Mps(BtSdpMpsRecord::default()));
                    if !success {
                        return Err(format!("Failed to create SDP record").into());
                    }
                }
            }
            "disable" => {
                let mut context = self.lock_context();
                context.telephony_dbus.as_mut().unwrap().set_mps_qualification_enabled(false);
                if let Some(handle) = context.mps_sdp_handle.take() {
                    let success = context.adapter_dbus.as_mut().unwrap().remove_sdp_record(handle);
                    if !success {
                        return Err(format!("Failed to remove SDP record").into());
                    }
                }
            }
            "incoming-call" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .incoming_call(String::from(get_arg(args, 1)?));
                if !success {
                    return Err("IncomingCall failed".into());
                }
            }
            "dialing-call" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .dialing_call(String::from(get_arg(args, 1)?));
                if !success {
                    return Err("DialingCall failed".into());
                }
            }
            "answer-call" => {
                let success =
                    self.context.lock().unwrap().telephony_dbus.as_mut().unwrap().answer_call();
                if !success {
                    return Err("AnswerCall failed".into());
                }
            }
            "hangup-call" => {
                let success =
                    self.context.lock().unwrap().telephony_dbus.as_mut().unwrap().hangup_call();
                if !success {
                    return Err("HangupCall failed".into());
                }
            }
            "set-memory-call" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .set_memory_call(get_arg(args, 1).ok().map(String::from));
                if !success {
                    return Err("SetMemoryCall failed".into());
                }
            }
            "set-last-call" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .set_last_call(get_arg(args, 1).ok().map(String::from));
                if !success {
                    return Err("SetLastCall failed".into());
                }
            }
            "release-held" => {
                let success =
                    self.context.lock().unwrap().telephony_dbus.as_mut().unwrap().release_held();
                if !success {
                    return Err("ReleaseHeld failed".into());
                }
            }
            "release-active-accept-held" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .release_active_accept_held();
                if !success {
                    return Err("ReleaseActiveAcceptHeld failed".into());
                }
            }
            "hold-active-accept-held" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .hold_active_accept_held();
                if !success {
                    return Err("HoldActiveAcceptHeld failed".into());
                }
            }
            "audio-connect" => {
                let success = self
                    .context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .audio_connect(String::from(get_arg(args, 1)?));
                if !success {
                    return Err("ConnectAudio failed".into());
                }
            }
            "audio-disconnect" => {
                self.context
                    .lock()
                    .unwrap()
                    .telephony_dbus
                    .as_mut()
                    .unwrap()
                    .audio_disconnect(String::from(get_arg(args, 1)?));
            }
            other => {
                return Err(format!("Invalid argument '{}'", other).into());
            }
        }
        Ok(())
    }

    fn cmd_qa(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.context.lock().unwrap().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        let command = get_arg(args, 0)?;

        match &command[..] {
            "add-media-player" => {
                let name = String::from(get_arg(args, 1)?);
                let browsing_supported = String::from(get_arg(args, 2)?)
                    .parse::<bool>()
                    .or(Err("Failed to parse browsing_supported"))?;
                self.context
                    .lock()
                    .unwrap()
                    .qa_dbus
                    .as_mut()
                    .unwrap()
                    .add_media_player(name, browsing_supported);
            }
            _ => return Err(CommandError::InvalidArgs),
        };

        Ok(())
    }

    fn cmd_media(&mut self, args: &Vec<String>) -> CommandResult {
        if !self.context.lock().unwrap().adapter_ready {
            return Err(self.adapter_not_ready());
        }

        match &get_arg(args, 0)?[..] {
            "log" => {
                self.context.lock().unwrap().media_dbus.as_mut().unwrap().trigger_debug_dump();
            }
            other => {
                return Err(format!("Invalid argument '{}'", other).into());
            }
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {

    use super::*;

    #[test]
    fn test_wrap_help_text() {
        let text = "hello";
        let text_len = text.chars().count();
        // ensure no overflow
        assert_eq!(format!("|{}|", text), wrap_help_text(text, 4, 0));
        assert_eq!(format!("|{}|", text), wrap_help_text(text, 5, 0));
        assert_eq!(format!("|{}{}|", text, " "), wrap_help_text(text, 6, 0));
        assert_eq!(format!("|{}{}|", text, " ".repeat(2)), wrap_help_text(text, 7, 0));
        assert_eq!(
            format!("|{}{}|", text, " ".repeat(100 - text_len)),
            wrap_help_text(text, 100, 0)
        );
        assert_eq!(format!("|{}{}|", " ", text), wrap_help_text(text, 4, 1));
        assert_eq!(format!("|{}{}|", " ".repeat(2), text), wrap_help_text(text, 5, 2));
        assert_eq!(format!("|{}{}{}|", " ".repeat(3), text, " "), wrap_help_text(text, 6, 3));
        assert_eq!(
            format!("|{}{}{}|", " ".repeat(4), text, " ".repeat(7 - text_len)),
            wrap_help_text(text, 7, 4)
        );
        assert_eq!(format!("|{}{}|", " ".repeat(9), text), wrap_help_text(text, 4, 9));
        assert_eq!(format!("|{}{}|", " ".repeat(10), text), wrap_help_text(text, 3, 10));
        assert_eq!(format!("|{}{}|", " ".repeat(11), text), wrap_help_text(text, 2, 11));
        assert_eq!(format!("|{}{}|", " ".repeat(12), text), wrap_help_text(text, 1, 12));
        assert_eq!("||", wrap_help_text("", 0, 0));
        assert_eq!("| |", wrap_help_text("", 1, 0));
        assert_eq!("|  |", wrap_help_text("", 1, 1));
        assert_eq!("| |", wrap_help_text("", 0, 1));
    }
}
