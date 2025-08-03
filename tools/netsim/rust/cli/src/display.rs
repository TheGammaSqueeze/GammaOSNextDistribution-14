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

use netsim_proto::frontend::ListDeviceResponse;
use netsim_proto::model::{
    self,
    chip::ble_beacon::advertise_settings,
    chip::ble_beacon::{AdvertiseData, AdvertiseSettings},
};
use protobuf::MessageField;
use std::fmt;

const INDENT_WIDTH: usize = 2;

/// Displayer for model protobufs. Implements fmt::Display.
/// # Invariants
/// Displayed values **do not** end in a newline.
pub struct Displayer<T> {
    value: T,
    verbose: bool,
    indent: usize,
}

impl<T> Displayer<T> {
    /// Returns a new displayer for values of the provided type.
    pub fn new(value: T, verbose: bool) -> Self {
        Displayer { value, verbose, indent: 0 }
    }

    /// Indent the displayed string by a given amount. Returns `self`.
    pub fn indent(&mut self, current_indent: usize) -> &Self {
        self.indent = current_indent + INDENT_WIDTH;
        self
    }
}

impl fmt::Display for Displayer<ListDeviceResponse> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;

        let mut devices = self.value.devices.iter().peekable();
        while let Some(device) = devices.next() {
            write!(f, "{:indent$}{}", "", Displayer::new(device, self.verbose))?;
            if devices.peek().is_some() {
                // We print the newline here instead of in the Device displayer because we don't want a newline before the very first device.
                writeln!(f)?;
            }
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::Device> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 9;

        write!(
            f,
            "{:indent$}{:width$} {}",
            "",
            self.value.name,
            Displayer::new(self.value.position.as_ref().unwrap_or_default(), self.verbose)
        )?;

        for chip in self.value.chips.iter() {
            write!(f, "{:indent$}{}", "", Displayer::new(chip, self.verbose).indent(self.indent))?;
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::Chip> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 9;

        match self.value.chip.as_ref() {
            Some(model::chip::Chip::BleBeacon(ble_beacon)) => {
                let radio = ble_beacon.bt.low_energy.as_ref().unwrap_or_default();
                let beacon_width = 16 + width;

                if self.verbose || radio.state.enum_value_or_default() == model::State::OFF {
                    writeln!(f)?;
                    write!(
                        f,
                        "{:indent$}{:beacon_width$} {}",
                        "",
                        format!("beacon-ble ({}):", self.value.name),
                        Displayer::new(radio, self.verbose),
                    )?;
                }

                if self.verbose {
                    writeln!(f)?;
                    write!(f, "{}", Displayer::new(ble_beacon, self.verbose).indent(self.indent))?;
                }
            }
            Some(model::chip::Chip::Bt(bt)) => {
                if let Some(ble) = bt.low_energy.as_ref() {
                    if self.verbose || ble.state.enum_value_or_default() == model::State::OFF {
                        writeln!(f)?;
                        write!(
                            f,
                            "{:indent$}{:width$}{}",
                            "",
                            "ble: ",
                            Displayer::new(ble, self.verbose),
                        )?;
                    }
                };

                if let Some(classic) = bt.classic.as_ref() {
                    if self.verbose || classic.state.enum_value_or_default() == model::State::OFF {
                        writeln!(f)?;
                        write!(
                            f,
                            "{:indent$}{:width$}{}",
                            "",
                            "classic: ",
                            Displayer::new(classic, self.verbose),
                        )?;
                    }
                };
            }
            Some(model::chip::Chip::Wifi(wifi)) => {
                if self.verbose || wifi.state.enum_value_or_default() == model::State::OFF {
                    writeln!(f)?;
                    write!(
                        f,
                        "{:indent$}{:width$}{}",
                        "",
                        "wifi: ",
                        Displayer::new(wifi, self.verbose)
                    )?;
                }
            }
            Some(model::chip::Chip::Uwb(uwb)) => {
                if self.verbose || uwb.state.enum_value_or_default() == model::State::OFF {
                    writeln!(f)?;
                    write!(
                        f,
                        "{:indent$}{:width$}{}",
                        "",
                        "uwb: ",
                        Displayer::new(uwb, self.verbose)
                    )?;
                }
            }
            _ => {
                if self.verbose {
                    writeln!(f)?;
                    write!(f, "{:indent$}Unknown chip", "")?
                }
            }
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::chip::BleBeacon> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let address_width = 16;
        write!(f, "{:indent$}address: {:address_width$}", "", self.value.address)?;

        if self.value.settings.is_some()
            && self.value.settings != MessageField::some(AdvertiseSettings::default())
        {
            writeln!(f)?;
            write!(
                f,
                "{:indent$}advertise settings:{}",
                "",
                Displayer::new(self.value.settings.as_ref().unwrap_or_default(), self.verbose)
                    .indent(self.indent)
            )?;
        }

        if self.value.adv_data.is_some()
            && self.value.adv_data != MessageField::some(AdvertiseData::default())
        {
            writeln!(f)?;
            write!(
                f,
                "{:indent$}advertise packet data:{}",
                "",
                Displayer::new(self.value.adv_data.as_ref().unwrap_or_default(), self.verbose)
                    .indent(self.indent)
            )?;
        }

        // TODO(jmes): Add scan response data.

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::Position> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let precision = 2;

        if self.verbose
            || (self.value.x != f32::default()
                || self.value.y != f32::default()
                || self.value.z != f32::default())
        {
            write!(
                f,
                "{:indent$} position: {:.precision$}, {:.precision$}, {:.precision$}",
                "", self.value.x, self.value.y, self.value.z,
            )?;
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::chip::ble_beacon::AdvertiseSettings> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 25;

        if let Some(tx_power) = self.value.tx_power.as_ref() {
            writeln!(f)?;
            write!(f, "{:indent$}{}", "", Displayer::new(tx_power, self.verbose))?;
        }

        if let Some(interval) = self.value.interval.as_ref() {
            writeln!(f)?;
            write!(f, "{:indent$}{}", "", Displayer::new(interval, self.verbose))?;
        }

        if self.value.scannable {
            writeln!(f)?;
            write!(f, "{:indent$}{:width$}: {}", "", "scannable", self.value.scannable)?;
        }

        if self.value.timeout != u64::default() {
            writeln!(f)?;
            write!(f, "{:indent$}{:width$}: {} ms", "", "timeout", self.value.timeout)?;
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::chip::ble_beacon::AdvertiseData> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 25;

        if self.value.include_device_name != bool::default() {
            writeln!(f)?;
            write!(
                f,
                "{:indent$}{:width$}: {}",
                "", "include device name", self.value.include_device_name
            )?;
        }

        if self.value.include_tx_power_level != bool::default() {
            writeln!(f)?;
            write!(
                f,
                "{:indent$}{:width$}: {}",
                "", "include tx power level", self.value.include_tx_power_level
            )?;
        }

        if self.value.manufacturer_data != Vec::<u8>::default() {
            writeln!(f)?;
            write!(
                f,
                "{:indent$}{:width$}: {}",
                "",
                "manufacturer data bytes",
                self.value.manufacturer_data.len()
            )?;
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&advertise_settings::Interval> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 25;

        match self.value {
            advertise_settings::Interval::Milliseconds(interval) => {
                write!(f, "{:indent$}{:width$}: {} ms", "", "interval", interval)
            }
            advertise_settings::Interval::AdvertiseMode(mode) => {
                write!(f, "{:indent$}{:width$}: {:?}", "", "advertise mode", mode)
            }
            _ => Err(fmt::Error),
        }
    }
}

impl fmt::Display for Displayer<&advertise_settings::Tx_power> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 25;

        match self.value {
            advertise_settings::Tx_power::Dbm(dbm) => {
                write!(f, "{:indent$}{:width$}: {} dBm", "", "tx power", dbm)
            }
            advertise_settings::Tx_power::TxPowerLevel(level) => {
                write!(f, "{:indent$}{:width$}: {:?}", "", "tx power level", level)
            }
            _ => Err(fmt::Error),
        }
    }
}

impl fmt::Display for Displayer<&model::chip::Radio> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let count_width = 9;
        write!(
            f,
            "{:indent$}{}",
            "",
            Displayer::new(&self.value.state.enum_value_or_default(), self.verbose),
        )?;

        if self.verbose {
            write!(
                f,
                "| rx_count: {:count_width$} | tx_count: {:count_width$}",
                self.value.rx_count, self.value.tx_count
            )?
        }

        Ok(())
    }
}

impl fmt::Display for Displayer<&model::State> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let indent = self.indent;
        let width = 9;

        write!(
            f,
            "{:indent$}{:width$}",
            "",
            match self.value {
                model::State::ON => "up",
                model::State::OFF => "down",
                _ => "unknown",
            }
        )
    }
}
