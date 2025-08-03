// Copyright 2022 Google LLC
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

use crate::ffi::frontend_client_ffi::{FrontendClient, GrpcMethod};
use clap::builder::{PossibleValue, TypedValueParser};
use clap::{Args, Parser, Subcommand, ValueEnum};
use hex::{decode as hex_to_bytes, FromHexError};
use log::error;
use netsim_common::util::time_display::TimeDisplay;
use netsim_proto::common::ChipKind;
use netsim_proto::frontend;
use netsim_proto::frontend::patch_capture_request::PatchCapture as PatchCaptureProto;
use netsim_proto::model::chip::ble_beacon::advertise_settings::{
    AdvertiseMode as AdvertiseModeProto, AdvertiseTxPower as AdvertiseTxPowerProto,
    Interval as IntervalProto, Tx_power as TxPowerProto,
};
use netsim_proto::model::chip::ble_beacon::{
    AdvertiseData as AdvertiseDataProto, AdvertiseSettings as AdvertiseSettingsProto,
};
use netsim_proto::model::chip::{
    BleBeacon as Chip_Ble_Beacon, Bluetooth as Chip_Bluetooth, Chip as Chip_Type,
    Radio as Chip_Radio,
};
use netsim_proto::model::{
    self, chip_create, Chip, ChipCreate as ChipCreateProto, Device,
    DeviceCreate as DeviceCreateProto, Position, State,
};
use protobuf::{Message, MessageField};
use std::fmt;
use std::iter;
use std::str::FromStr;

pub type BinaryProtobuf = Vec<u8>;

#[derive(Debug, Parser)]
pub struct NetsimArgs {
    #[command(subcommand)]
    pub command: Command,
    /// Set verbose mode
    #[arg(short, long)]
    pub verbose: bool,
    /// Set custom grpc port
    #[arg(short, long)]
    pub port: Option<i32>,
    /// Set netsimd instance number to connect
    #[arg(short, long)]
    pub instance: Option<u16>,
    /// Set vsock cid:port pair
    #[arg(long)]
    pub vsock: Option<String>,
}

#[derive(Debug, Subcommand)]
#[command(infer_subcommands = true)]
pub enum Command {
    /// Print Netsim version information
    Version,
    /// Control the radio state of a device
    Radio(Radio),
    /// Set the device location
    Move(Move),
    /// Display device(s) information
    Devices(Devices),
    /// Reset Netsim device scene
    Reset,
    /// Open netsim Web UI
    Gui,
    /// Control the packet capture functionalities with commands: list, patch, get
    #[command(subcommand, visible_alias("pcap"))]
    Capture(Capture),
    /// Opens netsim artifacts directory (log, pcaps)
    Artifact,
    /// A chip that sends advertisements at a set interval
    #[command(subcommand)]
    Beacon(Beacon),
    /// Open Bumble Hive Web Page
    Bumble,
}

impl Command {
    /// Return the generated request protobuf as a byte vector
    /// The parsed command parameters are used to construct the request protobuf which is
    /// returned as a byte vector that can be sent to the server.
    pub fn get_request_bytes(&self) -> BinaryProtobuf {
        match self {
            Command::Version => Vec::new(),
            Command::Radio(cmd) => {
                let mut chip = Chip { ..Default::default() };
                let chip_state = match cmd.status {
                    UpDownStatus::Up => State::ON,
                    UpDownStatus::Down => State::OFF,
                };
                if cmd.radio_type == RadioType::Wifi {
                    let mut wifi_chip = Chip_Radio::new();
                    wifi_chip.state = chip_state.into();
                    chip.set_wifi(wifi_chip);
                    chip.kind = ChipKind::WIFI.into();
                } else if cmd.radio_type == RadioType::Uwb {
                    let mut uwb_chip = Chip_Radio::new();
                    uwb_chip.state = chip_state.into();
                    chip.set_uwb(uwb_chip);
                    chip.kind = ChipKind::UWB.into();
                } else {
                    let mut bt_chip = Chip_Bluetooth::new();
                    let mut bt_chip_radio = Chip_Radio::new();
                    bt_chip_radio.state = chip_state.into();
                    if cmd.radio_type == RadioType::Ble {
                        bt_chip.low_energy = Some(bt_chip_radio).into();
                    } else {
                        bt_chip.classic = Some(bt_chip_radio).into();
                    }
                    chip.kind = ChipKind::BLUETOOTH.into();
                    chip.set_bt(bt_chip);
                }
                let mut result = frontend::PatchDeviceRequest::new();
                let mut device = Device::new();
                device.name = cmd.name.to_owned();
                device.chips.push(chip);
                result.device = Some(device).into();
                result.write_to_bytes().unwrap()
            }
            Command::Move(cmd) => {
                let mut result = frontend::PatchDeviceRequest::new();
                let mut device = Device::new();
                let position = Position {
                    x: cmd.x,
                    y: cmd.y,
                    z: cmd.z.unwrap_or_default(),
                    ..Default::default()
                };
                device.name = cmd.name.to_owned();
                device.position = Some(position).into();
                result.device = Some(device).into();
                result.write_to_bytes().unwrap()
            }
            Command::Devices(_) => Vec::new(),
            Command::Reset => Vec::new(),
            Command::Gui => {
                unimplemented!("get_request_bytes is not implemented for Gui Command.");
            }
            Command::Capture(cmd) => match cmd {
                Capture::List(_) => Vec::new(),
                Capture::Get(_) => {
                    unimplemented!("get_request_bytes not implemented for Capture Get command. Use get_requests instead.")
                }
                Capture::Patch(_) => {
                    unimplemented!("get_request_bytes not implemented for Capture Patch command. Use get_requests instead.")
                }
            },
            Command::Artifact => {
                unimplemented!("get_request_bytes is not implemented for Artifact Command.");
            }
            Command::Beacon(action) => match action {
                Beacon::Create(kind) => match kind {
                    BeaconCreate::Ble(args) => {
                        let device = MessageField::some(DeviceCreateProto {
                            name: args.device_name.clone().unwrap_or_default(),
                            chips: vec![ChipCreateProto {
                                name: args.chip_name.clone().unwrap_or_default(),
                                kind: ChipKind::BLUETOOTH_BEACON.into(),
                                chip: Some(chip_create::Chip::BleBeacon(
                                    chip_create::BleBeaconCreate {
                                        address: args.address.clone().unwrap_or_default(),
                                        settings: MessageField::some((&args.settings).into()),
                                        adv_data: MessageField::some((&args.advertise_data).into()),
                                        scan_response: MessageField::some(
                                            (&args.scan_response_data).into(),
                                        ),
                                        ..Default::default()
                                    },
                                )),
                                ..Default::default()
                            }],
                            ..Default::default()
                        });

                        let result = frontend::CreateDeviceRequest { device, ..Default::default() };
                        result.write_to_bytes().unwrap()
                    }
                },
                Beacon::Patch(kind) => match kind {
                    BeaconPatch::Ble(args) => {
                        let device = MessageField::some(Device {
                            name: args.device_name.clone(),
                            chips: vec![Chip {
                                name: args.chip_name.clone(),
                                kind: ChipKind::BLUETOOTH_BEACON.into(),
                                chip: Some(Chip_Type::BleBeacon(Chip_Ble_Beacon {
                                    bt: MessageField::some(Chip_Bluetooth::new()),
                                    address: args.address.clone().unwrap_or_default(),
                                    settings: MessageField::some((&args.settings).into()),
                                    adv_data: MessageField::some((&args.advertise_data).into()),
                                    scan_response: MessageField::some(
                                        (&args.scan_response_data).into(),
                                    ),
                                    ..Default::default()
                                })),
                                ..Default::default()
                            }],
                            ..Default::default()
                        });

                        let result = frontend::PatchDeviceRequest { device, ..Default::default() };
                        result.write_to_bytes().unwrap()
                    }
                },
                Beacon::Remove(_) => Vec::new(),
            },
            Command::Bumble => {
                unimplemented!("get_request_bytes is not implemented for Bumble Command.");
            }
        }
    }

    /// Create and return the request protobuf(s) for the command.
    /// In the case of a command with pattern argument(s) there may be multiple gRPC requests.
    /// The parsed command parameters are used to construct the request protobuf.
    /// The client is used to send gRPC call(s) to retrieve information needed for request protobufs.
    pub fn get_requests(&mut self, client: &cxx::UniquePtr<FrontendClient>) -> Vec<BinaryProtobuf> {
        match self {
            Command::Capture(Capture::Patch(cmd)) => {
                let mut reqs = Vec::new();
                let filtered_captures = Self::get_filtered_captures(client, &cmd.patterns);
                // Create a request for each capture
                for capture in &filtered_captures {
                    let mut result = frontend::PatchCaptureRequest::new();
                    result.id = capture.id;
                    let capture_state = match cmd.state {
                        OnOffState::On => State::ON,
                        OnOffState::Off => State::OFF,
                    };
                    let mut patch_capture = PatchCaptureProto::new();
                    patch_capture.state = capture_state.into();
                    result.patch = Some(patch_capture).into();
                    reqs.push(result.write_to_bytes().unwrap())
                }
                reqs
            }
            Command::Capture(Capture::Get(cmd)) => {
                let mut reqs = Vec::new();
                let filtered_captures = Self::get_filtered_captures(client, &cmd.patterns);
                // Create a request for each capture
                for capture in &filtered_captures {
                    let mut result = frontend::GetCaptureRequest::new();
                    result.id = capture.id;
                    reqs.push(result.write_to_bytes().unwrap());
                    let time_display = TimeDisplay::new(
                        capture.timestamp.get_or_default().seconds,
                        capture.timestamp.get_or_default().nanos as u32,
                    );
                    let file_extension = "pcap";
                    cmd.filenames.push(format!(
                        "netsim-{:?}-{}-{}-{}.{}",
                        capture.id,
                        capture.device_name.to_owned().replace(' ', "_"),
                        Self::chip_kind_to_string(capture.chip_kind.enum_value_or_default()),
                        time_display.utc_display(),
                        file_extension
                    ));
                }
                reqs
            }
            _ => {
                unimplemented!(
                    "get_requests not implemented for this command. Use get_request_bytes instead."
                )
            }
        }
    }

    fn get_filtered_captures(
        client: &cxx::UniquePtr<FrontendClient>,
        patterns: &[String],
    ) -> Vec<model::Capture> {
        // Get list of captures
        let result = client.send_grpc(&GrpcMethod::ListCapture, &Vec::new());
        if !result.is_ok() {
            error!("ListCapture Grpc call error: {}", result.err());
            return Vec::new();
        }
        let mut response =
            frontend::ListCaptureResponse::parse_from_bytes(result.byte_vec().as_slice()).unwrap();
        if !patterns.is_empty() {
            // Filter out list of captures with matching patterns
            Self::filter_captures(&mut response.captures, patterns)
        }
        response.captures
    }
}

#[derive(Debug, Args)]
pub struct Radio {
    /// Radio type
    #[arg(value_enum, ignore_case = true)]
    pub radio_type: RadioType,
    /// Radio status
    #[arg(value_enum, ignore_case = true)]
    pub status: UpDownStatus,
    /// Device name
    pub name: String,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, ValueEnum)]
pub enum RadioType {
    Ble,
    Classic,
    Wifi,
    Uwb,
}

impl fmt::Display for RadioType {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, ValueEnum)]
pub enum UpDownStatus {
    Up,
    Down,
}

impl fmt::Display for UpDownStatus {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}

#[derive(Debug, Args)]
pub struct Move {
    /// Device name
    pub name: String,
    /// x position of device
    pub x: f32,
    /// y position of device
    pub y: f32,
    /// Optional z position of device
    pub z: Option<f32>,
}

#[derive(Debug, Args)]
pub struct Devices {
    /// Continuously print device(s) information every second
    #[arg(short, long)]
    pub continuous: bool,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, ValueEnum)]
pub enum OnOffState {
    On,
    Off,
}

#[derive(Debug, Subcommand)]
pub enum Beacon {
    /// Create a beacon chip
    #[command(subcommand)]
    Create(BeaconCreate),
    /// Modify a beacon chip
    #[command(subcommand)]
    Patch(BeaconPatch),
    /// Remove a beacon chip
    Remove(BeaconRemove),
}

#[derive(Debug, Subcommand)]
pub enum BeaconCreate {
    /// Create a Bluetooth low-energy beacon chip
    Ble(BeaconCreateBle),
}

#[derive(Debug, Args)]
pub struct BeaconCreateBle {
    /// Name of the device to create
    pub device_name: Option<String>,
    /// Name of the beacon chip to create within the new device. May only be specified if device_name is specified
    pub chip_name: Option<String>,
    /// Bluetooth address of the beacon. Must be a 6-byte hexadecimal string with each byte separated by a colon. Will be generated if not provided
    #[arg(long)]
    pub address: Option<String>,
    #[command(flatten)]
    pub settings: BeaconBleSettings,
    #[command(flatten)]
    pub advertise_data: BeaconBleAdvertiseData,
    #[command(flatten)]
    pub scan_response_data: BeaconBleScanResponseData,
}

#[derive(Debug, Subcommand)]
pub enum BeaconPatch {
    /// Modify a Bluetooth low-energy beacon chip
    Ble(BeaconPatchBle),
}

#[derive(Debug, Args)]
pub struct BeaconPatchBle {
    /// Name of the device that contains the chip
    pub device_name: String,
    /// Name of the beacon chip to modify
    pub chip_name: String,
    /// Bluetooth address of the beacon. Must be a 6-byte hexadecimal string with each byte separated by a colon
    #[arg(long)]
    pub address: Option<String>,
    #[command(flatten)]
    pub settings: BeaconBleSettings,
    #[command(flatten)]
    pub advertise_data: BeaconBleAdvertiseData,
    #[command(flatten)]
    pub scan_response_data: BeaconBleScanResponseData,
}

#[derive(Debug, Args)]
pub struct BeaconRemove {
    /// Name of the device to remove
    pub device_name: String,
    /// Name of the beacon chip to remove. Can be omitted if the device has exactly 1 chip
    pub chip_name: Option<String>,
}

#[derive(Debug, Args)]
pub struct BeaconBleAdvertiseData {
    /// Whether the device name should be included in the advertise packet
    #[arg(long, required = false)]
    pub include_device_name: bool,
    /// Whether the transmission power level should be included in the advertise packet
    #[arg(long, required = false)]
    pub include_tx_power_level: bool,
    /// Manufacturer-specific data given as bytes in hexadecimal
    #[arg(long)]
    pub manufacturer_data: Option<ParsableBytes>,
}

#[derive(Debug, Clone)]
pub struct ParsableBytes(Vec<u8>);

impl ParsableBytes {
    fn unwrap(self) -> Vec<u8> {
        self.0
    }
}

impl FromStr for ParsableBytes {
    type Err = FromHexError;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        hex_to_bytes(s.strip_prefix("0x").unwrap_or(s)).map(ParsableBytes)
    }
}

#[derive(Debug, Args)]
pub struct BeaconBleScanResponseData {
    /// Whether the device name should be included in the scan response packet
    #[arg(long, required = false)]
    pub scan_response_include_device_name: bool,
    /// Whether the transmission power level should be included in the scan response packet
    #[arg(long, required = false)]
    pub scan_response_include_tx_power_level: bool,
    /// Manufacturer-specific data to include in the scan response packet given as bytes in hexadecimal
    #[arg(long, value_name = "MANUFACTURER_DATA")]
    pub scan_response_manufacturer_data: Option<ParsableBytes>,
}

#[derive(Debug, Args)]
pub struct BeaconBleSettings {
    /// Set advertise mode to control the advertising latency
    #[arg(long, value_parser = IntervalParser)]
    pub advertise_mode: Option<Interval>,
    /// Set advertise TX power level to control the beacon's transmission power
    #[arg(long, value_parser = TxPowerParser, allow_hyphen_values = true)]
    pub tx_power_level: Option<TxPower>,
    /// Set whether the beacon will respond to scan requests
    #[arg(long)]
    pub scannable: bool,
    /// Limit advertising to an amount of time given in milliseconds
    #[arg(long, value_name = "MS")]
    pub timeout: Option<u64>,
}

#[derive(Clone, Debug)]
pub enum Interval {
    Mode(AdvertiseMode),
    Milliseconds(u64),
}

#[derive(Clone)]
struct IntervalParser;

impl TypedValueParser for IntervalParser {
    type Value = Interval;

    fn parse_ref(
        &self,
        cmd: &clap::Command,
        arg: Option<&clap::Arg>,
        value: &std::ffi::OsStr,
    ) -> Result<Self::Value, clap::Error> {
        let millis_parser = clap::value_parser!(u64);
        let mode_parser = clap::value_parser!(AdvertiseMode);

        mode_parser
            .parse_ref(cmd, arg, value)
            .map(Self::Value::Mode)
            .or(millis_parser.parse_ref(cmd, arg, value).map(Self::Value::Milliseconds))
    }

    fn possible_values(&self) -> Option<Box<dyn Iterator<Item = PossibleValue> + '_>> {
        Some(Box::new(
            AdvertiseMode::value_variants().iter().map(|v| v.to_possible_value().unwrap()).chain(
                iter::once(
                    PossibleValue::new("<MS>").help("An exact advertise interval in milliseconds"),
                ),
            ),
        ))
    }
}

#[derive(Clone, Debug)]
pub enum TxPower {
    Level(TxPowerLevel),
    Dbm(i8),
}

#[derive(Clone)]
struct TxPowerParser;

impl TypedValueParser for TxPowerParser {
    type Value = TxPower;

    fn parse_ref(
        &self,
        cmd: &clap::Command,
        arg: Option<&clap::Arg>,
        value: &std::ffi::OsStr,
    ) -> Result<Self::Value, clap::Error> {
        let dbm_parser = clap::value_parser!(i8);
        let level_parser = clap::value_parser!(TxPowerLevel);

        level_parser
            .parse_ref(cmd, arg, value)
            .map(Self::Value::Level)
            .or(dbm_parser.parse_ref(cmd, arg, value).map(Self::Value::Dbm))
    }

    fn possible_values(&self) -> Option<Box<dyn Iterator<Item = PossibleValue> + '_>> {
        Some(Box::new(
            TxPowerLevel::value_variants().iter().map(|v| v.to_possible_value().unwrap()).chain(
                iter::once(
                    PossibleValue::new("<DBM>").help("An exact transmit power level in dBm"),
                ),
            ),
        ))
    }
}

#[derive(Debug, Clone, ValueEnum)]
pub enum AdvertiseMode {
    /// Lowest power consumption, preferred advertising mode
    LowPower,
    /// Balanced between advertising frequency and power consumption
    Balanced,
    /// Highest power consumption
    LowLatency,
}

#[derive(Debug, Clone, ValueEnum)]
pub enum TxPowerLevel {
    /// Lowest transmission power level
    UltraLow,
    /// Low transmission power level
    Low,
    /// Medium transmission power level
    Medium,
    /// High transmission power level
    High,
}

#[derive(Debug, Subcommand)]
pub enum Capture {
    /// List currently available Captures (packet captures)
    List(ListCapture),
    /// Patch a Capture source to turn packet capture on/off
    Patch(PatchCapture),
    /// Download the packet capture content
    Get(GetCapture),
}

#[derive(Debug, Args)]
pub struct ListCapture {
    /// Optional strings of pattern for captures to list. Possible filter fields include Capture ID, Device Name, and Chip Kind
    pub patterns: Vec<String>,
    /// Continuously print Capture information every second
    #[arg(short, long)]
    pub continuous: bool,
}

#[derive(Debug, Args)]
pub struct PatchCapture {
    /// Packet capture state
    #[arg(value_enum, ignore_case = true)]
    pub state: OnOffState,
    /// Optional strings of pattern for captures to patch. Possible filter fields include Capture ID, Device Name, and Chip Kind
    pub patterns: Vec<String>,
}

#[derive(Debug, Args)]
pub struct GetCapture {
    /// Optional strings of pattern for captures to get. Possible filter fields include Capture ID, Device Name, and Chip Kind
    pub patterns: Vec<String>,
    /// Directory to store downloaded capture file(s)
    #[arg(short = 'o', long)]
    pub location: Option<String>,
    #[arg(skip)]
    pub filenames: Vec<String>,
    #[arg(skip)]
    pub current_file: String,
}

impl From<&BeaconBleSettings> for AdvertiseSettingsProto {
    fn from(value: &BeaconBleSettings) -> Self {
        AdvertiseSettingsProto {
            interval: value.advertise_mode.as_ref().map(IntervalProto::from),
            tx_power: value.tx_power_level.as_ref().map(TxPowerProto::from),
            scannable: value.scannable,
            timeout: value.timeout.unwrap_or_default(),
            ..Default::default()
        }
    }
}

impl From<&Interval> for IntervalProto {
    fn from(value: &Interval) -> Self {
        match value {
            Interval::Mode(mode) => IntervalProto::AdvertiseMode(
                match mode {
                    AdvertiseMode::LowPower => AdvertiseModeProto::LOW_POWER,
                    AdvertiseMode::Balanced => AdvertiseModeProto::BALANCED,
                    AdvertiseMode::LowLatency => AdvertiseModeProto::LOW_LATENCY,
                }
                .into(),
            ),
            Interval::Milliseconds(ms) => IntervalProto::Milliseconds(*ms),
        }
    }
}

impl From<&TxPower> for TxPowerProto {
    fn from(value: &TxPower) -> Self {
        match value {
            TxPower::Level(level) => TxPowerProto::TxPowerLevel(
                match level {
                    TxPowerLevel::UltraLow => AdvertiseTxPowerProto::ULTRA_LOW,
                    TxPowerLevel::Low => AdvertiseTxPowerProto::LOW,
                    TxPowerLevel::Medium => AdvertiseTxPowerProto::MEDIUM,
                    TxPowerLevel::High => AdvertiseTxPowerProto::HIGH,
                }
                .into(),
            ),
            TxPower::Dbm(dbm) => TxPowerProto::Dbm((*dbm).into()),
        }
    }
}

impl From<&BeaconBleAdvertiseData> for AdvertiseDataProto {
    fn from(value: &BeaconBleAdvertiseData) -> Self {
        AdvertiseDataProto {
            include_device_name: value.include_device_name,
            include_tx_power_level: value.include_tx_power_level,
            manufacturer_data: value
                .manufacturer_data
                .clone()
                .map(ParsableBytes::unwrap)
                .unwrap_or_default(),
            ..Default::default()
        }
    }
}

impl From<&BeaconBleScanResponseData> for AdvertiseDataProto {
    fn from(value: &BeaconBleScanResponseData) -> Self {
        AdvertiseDataProto {
            include_device_name: value.scan_response_include_device_name,
            include_tx_power_level: value.scan_response_include_tx_power_level,
            manufacturer_data: value
                .scan_response_manufacturer_data
                .clone()
                .map(ParsableBytes::unwrap)
                .unwrap_or_default(),
            ..Default::default()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hex_parser_succeeds() {
        let hex = ParsableBytes::from_str("beef1234");
        assert!(hex.is_ok(), "{}", hex.unwrap_err());
        let hex = hex.unwrap().unwrap();

        assert_eq!(vec![0xbeu8, 0xef, 0x12, 0x34], hex);
    }

    #[test]
    fn test_hex_parser_prefix_succeeds() {
        let hex = ParsableBytes::from_str("0xabcd");
        assert!(hex.is_ok(), "{}", hex.unwrap_err());
        let hex = hex.unwrap().unwrap();

        assert_eq!(vec![0xabu8, 0xcd], hex);
    }

    #[test]
    fn test_hex_parser_empty_str_succeeds() {
        let hex = ParsableBytes::from_str("");
        assert!(hex.is_ok(), "{}", hex.unwrap_err());
        let hex = hex.unwrap().unwrap();

        assert_eq!(Vec::<u8>::new(), hex);
    }

    #[test]
    fn test_hex_parser_bad_digit_fails() {
        assert!(ParsableBytes::from_str("0xabcdefg").is_err());
    }
}
