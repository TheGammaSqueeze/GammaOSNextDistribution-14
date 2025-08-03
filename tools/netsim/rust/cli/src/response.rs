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

use std::cmp::max;

use crate::args::{self, Beacon, BeaconCreate, BeaconPatch, Capture, Command, OnOffState};
use crate::display::Displayer;
use netsim_common::util::time_display::TimeDisplay;
use netsim_proto::{
    common::ChipKind,
    frontend::{CreateDeviceResponse, ListCaptureResponse, ListDeviceResponse, VersionResponse},
    model::{self, State},
};
use protobuf::Message;

impl args::Command {
    /// Format and print the response received from the frontend server for the command
    pub fn print_response(&self, response: &[u8], verbose: bool) {
        match self {
            Command::Version => {
                Self::print_version_response(VersionResponse::parse_from_bytes(response).unwrap());
            }
            Command::Radio(cmd) => {
                if verbose {
                    println!(
                        "Radio {} is {} for {}",
                        cmd.radio_type,
                        cmd.status,
                        cmd.name.to_owned()
                    );
                }
            }
            Command::Move(cmd) => {
                if verbose {
                    println!(
                        "Moved device:{} to x: {:.2}, y: {:.2}, z: {:.2}",
                        cmd.name,
                        cmd.x,
                        cmd.y,
                        cmd.z.unwrap_or_default()
                    )
                }
            }
            Command::Devices(_) => {
                println!(
                    "{}",
                    Displayer::new(
                        ListDeviceResponse::parse_from_bytes(response).unwrap(),
                        verbose
                    )
                );
            }
            Command::Reset => {
                if verbose {
                    println!("All devices have been reset.");
                }
            }
            Command::Capture(Capture::List(cmd)) => Self::print_list_capture_response(
                ListCaptureResponse::parse_from_bytes(response).unwrap(),
                verbose,
                cmd.patterns.to_owned(),
            ),
            Command::Capture(Capture::Patch(cmd)) => {
                if verbose {
                    println!(
                        "Patched Capture state to {}",
                        Self::on_off_state_to_string(cmd.state),
                    );
                }
            }
            Command::Capture(Capture::Get(cmd)) => {
                if verbose {
                    println!("Successfully downloaded file: {}", cmd.current_file);
                }
            }
            Command::Gui => {
                unimplemented!("No Grpc Response for Gui Command.");
            }
            Command::Artifact => {
                unimplemented!("No Grpc Response for Artifact Command.");
            }
            Command::Beacon(action) => match action {
                Beacon::Create(kind) => match kind {
                    BeaconCreate::Ble(_) => {
                        if !verbose {
                            return;
                        }
                        let device = CreateDeviceResponse::parse_from_bytes(response)
                            .expect("could not read device from response")
                            .device;

                        if device.chips.len() == 1 {
                            println!(
                                "Created device '{}' with ble beacon chip '{}'",
                                device.name, device.chips[0].name
                            );
                        } else {
                            panic!("the gRPC request completed successfully but the response contained an unexpected number of chips");
                        }
                    }
                },
                Beacon::Patch(kind) => {
                    match kind {
                        BeaconPatch::Ble(args) => {
                            if !verbose {
                                return;
                            }
                            if let Some(advertise_mode) = &args.settings.advertise_mode {
                                match advertise_mode {
                                    args::Interval::Mode(mode) => {
                                        println!("Set advertise mode to {:#?}", mode)
                                    }
                                    args::Interval::Milliseconds(ms) => {
                                        println!("Set advertise interval to {} ms", ms)
                                    }
                                }
                            }
                            if let Some(tx_power_level) = &args.settings.tx_power_level {
                                match tx_power_level {
                                    args::TxPower::Level(level) => {
                                        println!("Set transmit power level to {:#?}", level)
                                    }
                                    args::TxPower::Dbm(dbm) => {
                                        println!("Set transmit power level to {} dBm", dbm)
                                    }
                                }
                            }
                            if args.settings.scannable {
                                println!("Set scannable to true");
                            }
                            if let Some(timeout) = args.settings.timeout {
                                println!("Set timeout to {} ms", timeout);
                            }
                            if args.advertise_data.include_device_name {
                                println!("Added the device's name to the advertise packet")
                            }
                            if args.advertise_data.include_tx_power_level {
                                println!("Added the beacon's transmit power level to the advertise packet")
                            }
                            if args.advertise_data.manufacturer_data.is_some() {
                                println!("Added manufacturer data to the advertise packet")
                            }
                            if args.settings.scannable {
                                println!("Set scannable to true");
                            }
                            if let Some(timeout) = args.settings.timeout {
                                println!("Set timeout to {} ms", timeout);
                            }
                        }
                    }
                }
                Beacon::Remove(args) => {
                    if !verbose {
                        return;
                    }
                    if let Some(chip_name) = &args.chip_name {
                        println!("Removed chip '{}' from device '{}'", chip_name, args.device_name)
                    } else {
                        println!("Removed device '{}'", args.device_name)
                    }
                }
            },
            Command::Bumble => {
                unimplemented!("No Grpc Response for Bumble Command.");
            }
        }
    }

    fn capture_state_to_string(state: State) -> String {
        match state {
            State::ON => "on".to_string(),
            State::OFF => "off".to_string(),
            _ => "unknown".to_string(),
        }
    }

    fn on_off_state_to_string(state: OnOffState) -> String {
        match state {
            OnOffState::On => "on".to_string(),
            OnOffState::Off => "off".to_string(),
        }
    }

    /// Helper function to format and print VersionResponse
    fn print_version_response(response: VersionResponse) {
        println!("Netsim version: {}", response.version);
    }

    /// Helper function to format and print ListCaptureResponse
    fn print_list_capture_response(
        mut response: ListCaptureResponse,
        verbose: bool,
        patterns: Vec<String>,
    ) {
        if response.captures.is_empty() {
            if verbose {
                println!("No available Capture found.");
            }
            return;
        }
        if patterns.is_empty() {
            println!("List of Captures:");
        } else {
            // Filter out list of captures with matching patterns
            Self::filter_captures(&mut response.captures, &patterns);
            if response.captures.is_empty() {
                if verbose {
                    println!("No available Capture found matching pattern(s) `{:?}`:", patterns);
                }
                return;
            }
            println!("List of Captures matching pattern(s) `{:?}`:", patterns);
        }
        // Create the header row and determine column widths
        let id_hdr = "ID";
        let name_hdr = "Device Name";
        let chipkind_hdr = "Chip Kind";
        let state_hdr = "State";
        let time_hdr = "Timestamp";
        let records_hdr = "Records";
        let size_hdr = "Size (bytes)";
        let id_width = 4; // ID width of 4 since capture id (=chip_id) starts at 1000
        let state_width = 8; // State width of 8 for 'detached' if device is disconnected
        let chipkind_width = 11; // ChipKind width 11 for 'UNSPECIFIED'
        let time_width = 9; // Timestamp width 9 for header (value format set to HH:MM:SS)
        let name_width = max(
            (response.captures.iter().max_by_key(|x| x.device_name.len()))
                .unwrap_or_default()
                .device_name
                .len(),
            name_hdr.len(),
        );
        let records_width = max(
            (response.captures.iter().max_by_key(|x| x.records))
                .unwrap_or_default()
                .records
                .to_string()
                .len(),
            records_hdr.len(),
        );
        let size_width = max(
            (response.captures.iter().max_by_key(|x| x.size))
                .unwrap_or_default()
                .size
                .to_string()
                .len(),
            size_hdr.len(),
        );
        // Print header for capture list
        println!(
            "{}",
            if verbose {
                format!("{:id_width$} | {:name_width$} | {:chipkind_width$} | {:state_width$} | {:time_width$} | {:records_width$} | {:size_width$} |",
                    id_hdr,
                    name_hdr,
                    chipkind_hdr,
                    state_hdr,
                    time_hdr,
                    records_hdr,
                    size_hdr,
                )
            } else {
                format!(
                    "{:name_width$} | {:chipkind_width$} | {:state_width$} | {:records_width$} |",
                    name_hdr, chipkind_hdr, state_hdr, records_hdr
                )
            }
        );
        // Print information of each Capture
        for capture in &response.captures {
            println!(
                "{}",
                if verbose {
                    format!("{:id_width$} | {:name_width$} | {:chipkind_width$} | {:state_width$} | {:time_width$} | {:records_width$} | {:size_width$} |",
                        capture.id.to_string(),
                        capture.device_name,
                        Self::chip_kind_to_string(capture.chip_kind.enum_value_or_default()),
                        if capture.valid {Self::capture_state_to_string(capture.state.enum_value_or_default())} else {"detached".to_string()},
                        TimeDisplay::new(
                            capture.timestamp.get_or_default().seconds,
                            capture.timestamp.get_or_default().nanos as u32,
                        ).utc_display_hms(),
                        capture.records,
                        capture.size,
                    )
                } else {
                    format!(
                        "{:name_width$} | {:chipkind_width$} | {:state_width$} | {:records_width$} |",
                        capture.device_name,
                        Self::chip_kind_to_string(capture.chip_kind.enum_value_or_default()),
                        if capture.valid {Self::capture_state_to_string(capture.state.enum_value_or_default())} else {"detached".to_string()},
                        capture.records,
                    )
                }
            );
        }
    }

    pub fn chip_kind_to_string(chip_kind: ChipKind) -> String {
        match chip_kind {
            ChipKind::UNSPECIFIED => "UNSPECIFIED".to_string(),
            ChipKind::BLUETOOTH => "BLUETOOTH".to_string(),
            ChipKind::WIFI => "WIFI".to_string(),
            ChipKind::UWB => "UWB".to_string(),
            ChipKind::BLUETOOTH_BEACON => "BLUETOOTH_BEACON".to_string(),
        }
    }

    pub fn filter_captures(captures: &mut Vec<model::Capture>, keys: &[String]) {
        // Filter out list of captures with matching pattern
        captures.retain(|capture| {
            keys.iter().map(|key| key.to_uppercase()).all(|key| {
                capture.id.to_string().contains(&key)
                    || capture.device_name.to_uppercase().contains(&key)
                    || Self::chip_kind_to_string(capture.chip_kind.enum_value_or_default())
                        .contains(&key)
            })
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn test_filter_captures_helper(patterns: Vec<String>, expected_captures: Vec<model::Capture>) {
        let mut captures = all_test_captures();
        Command::filter_captures(&mut captures, &patterns);
        assert_eq!(captures, expected_captures);
    }

    fn capture_1() -> model::Capture {
        model::Capture {
            id: 4001,
            chip_kind: ChipKind::BLUETOOTH.into(),
            device_name: "device 1".to_string(),
            ..Default::default()
        }
    }
    fn capture_1_wifi() -> model::Capture {
        model::Capture {
            id: 4002,
            chip_kind: ChipKind::WIFI.into(),
            device_name: "device 1".to_string(),
            ..Default::default()
        }
    }
    fn capture_2() -> model::Capture {
        model::Capture {
            id: 4003,
            chip_kind: ChipKind::BLUETOOTH.into(),
            device_name: "device 2".to_string(),
            ..Default::default()
        }
    }
    fn capture_3() -> model::Capture {
        model::Capture {
            id: 4004,
            chip_kind: ChipKind::WIFI.into(),
            device_name: "device 3".to_string(),
            ..Default::default()
        }
    }
    fn capture_4_uwb() -> model::Capture {
        model::Capture {
            id: 4005,
            chip_kind: ChipKind::UWB.into(),
            device_name: "device 4".to_string(),
            ..Default::default()
        }
    }
    fn all_test_captures() -> Vec<model::Capture> {
        vec![capture_1(), capture_1_wifi(), capture_2(), capture_3(), capture_4_uwb()]
    }

    #[test]
    fn test_no_match() {
        test_filter_captures_helper(vec!["test".to_string()], vec![]);
    }

    #[test]
    fn test_all_match() {
        test_filter_captures_helper(vec!["device".to_string()], all_test_captures());
    }

    #[test]
    fn test_match_capture_id() {
        test_filter_captures_helper(vec!["4001".to_string()], vec![capture_1()]);
        test_filter_captures_helper(vec!["03".to_string()], vec![capture_2()]);
        test_filter_captures_helper(vec!["40".to_string()], all_test_captures());
    }

    #[test]
    fn test_match_device_name() {
        test_filter_captures_helper(
            vec!["device 1".to_string()],
            vec![capture_1(), capture_1_wifi()],
        );
        test_filter_captures_helper(vec![" 2".to_string()], vec![capture_2()]);
    }

    #[test]
    fn test_match_device_name_case_insensitive() {
        test_filter_captures_helper(
            vec!["DEVICE 1".to_string()],
            vec![capture_1(), capture_1_wifi()],
        );
    }

    #[test]
    fn test_match_wifi() {
        test_filter_captures_helper(vec!["wifi".to_string()], vec![capture_1_wifi(), capture_3()]);
        test_filter_captures_helper(vec!["WIFI".to_string()], vec![capture_1_wifi(), capture_3()]);
    }

    #[test]
    fn test_match_uwb() {
        test_filter_captures_helper(vec!["uwb".to_string()], vec![capture_4_uwb()]);
        test_filter_captures_helper(vec!["UWB".to_string()], vec![capture_4_uwb()]);
    }

    #[test]
    fn test_match_bt() {
        test_filter_captures_helper(vec!["BLUETOOTH".to_string()], vec![capture_1(), capture_2()]);
        test_filter_captures_helper(vec!["blue".to_string()], vec![capture_1(), capture_2()]);
    }

    #[test]
    fn test_match_name_and_chip() {
        test_filter_captures_helper(
            vec!["device 1".to_string(), "wifi".to_string()],
            vec![capture_1_wifi()],
        );
    }
}
