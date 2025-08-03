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

use super::packets::link_layer::LegacyAdvertisingType;
use netsim_proto::model::chip::ble_beacon::{
    advertise_settings::{
        AdvertiseMode as Mode, AdvertiseTxPower as Level, Interval as IntervalProto,
        Tx_power as TxPowerProto,
    },
    AdvertiseSettings as AdvertiseSettingsProto,
};

use std::time::Duration;

// From packages/modules/Bluetooth/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java#151
const MODE_LOW_POWER_MS: u64 = 1000;
const MODE_BALANCED_MS: u64 = 250;
const MODE_LOW_LATENCY_MS: u64 = 100;

// From packages/modules/Bluetooth/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java#159
const TX_POWER_ULTRA_LOW_DBM: i8 = -21;
const TX_POWER_LOW_DBM: i8 = -15;
const TX_POWER_MEDIUM_DBM: i8 = -7;
const TX_POWER_HIGH_DBM: i8 = 1;

/// Configurable settings for ble beacon advertisements.
#[derive(Debug, PartialEq)]
pub struct AdvertiseSettings {
    /// Time interval between advertisements.
    pub mode: AdvertiseMode,
    /// Transmit power level for advertisements and scan responses.
    pub tx_power_level: TxPowerLevel,
    /// Whether the beacon will respond to scan requests.
    pub scannable: bool,
    /// How long to send advertisements for before stopping.
    pub timeout: Option<Duration>,
}

impl AdvertiseSettings {
    /// Returns a new advertise settings builder with no fields.
    pub fn builder() -> AdvertiseSettingsBuilder {
        AdvertiseSettingsBuilder::default()
    }

    /// Returns a new advertise settings with fields from a protobuf.
    pub fn from_proto(proto: &AdvertiseSettingsProto) -> Result<Self, String> {
        proto.try_into()
    }

    /// Returns the PDU type of advertise packets with the provided settings
    pub fn get_packet_type(&self) -> LegacyAdvertisingType {
        if self.scannable {
            LegacyAdvertisingType::AdvScanInd
        } else {
            LegacyAdvertisingType::AdvNonconnInd
        }
    }
}

impl TryFrom<&AdvertiseSettingsProto> for AdvertiseSettings {
    type Error = String;

    fn try_from(value: &AdvertiseSettingsProto) -> Result<Self, Self::Error> {
        let mut builder = AdvertiseSettingsBuilder::default();

        if let Some(mode) = value.interval.as_ref() {
            builder.mode(mode.into());
        }

        if let Some(tx_power) = value.tx_power.as_ref() {
            builder.tx_power_level(tx_power.try_into()?);
        }

        if value.scannable {
            builder.scannable();
        }

        if value.timeout != u64::default() {
            builder.timeout(Duration::from_millis(value.timeout));
        }

        Ok(builder.build())
    }
}

impl TryFrom<&AdvertiseSettings> for AdvertiseSettingsProto {
    type Error = String;

    fn try_from(value: &AdvertiseSettings) -> Result<Self, Self::Error> {
        Ok(AdvertiseSettingsProto {
            interval: Some(value.mode.try_into()?),
            tx_power: Some(value.tx_power_level.into()),
            scannable: value.scannable,
            timeout: value.timeout.unwrap_or_default().as_millis().try_into().map_err(|_| {
                String::from("could not convert timeout to millis: must fit in a u64")
            })?,
            ..Default::default()
        })
    }
}

#[derive(Default)]
/// Builder for BLE beacon advertise settings.
pub struct AdvertiseSettingsBuilder {
    mode: Option<AdvertiseMode>,
    tx_power_level: Option<TxPowerLevel>,
    scannable: bool,
    timeout: Option<Duration>,
}

impl AdvertiseSettingsBuilder {
    /// Returns a new advertise settings builder with empty fields.
    pub fn new() -> Self {
        Self::default()
    }

    /// Build the advertise settings.
    pub fn build(&self) -> AdvertiseSettings {
        AdvertiseSettings {
            mode: self.mode.unwrap_or_default(),
            tx_power_level: self.tx_power_level.unwrap_or_default(),
            scannable: self.scannable,
            timeout: self.timeout,
        }
    }

    /// Set the advertise mode.
    pub fn mode(&mut self, mode: AdvertiseMode) -> &mut Self {
        self.mode = Some(mode);
        self
    }

    /// Set the transmit power level.
    pub fn tx_power_level(&mut self, tx_power_level: TxPowerLevel) -> &mut Self {
        self.tx_power_level = Some(tx_power_level);
        self
    }

    /// Set whether the beacon will respond to scan requests.
    pub fn scannable(&mut self) -> &mut Self {
        self.scannable = true;
        self
    }

    /// Set how long the beacon will send advertisements for.
    pub fn timeout(&mut self, timeout: Duration) -> &mut Self {
        self.timeout = Some(timeout);
        self
    }
}

/// A BLE beacon advertise mode. Can be casted to/from a protobuf message.
#[derive(Debug, Copy, Clone, PartialEq)]
pub struct AdvertiseMode {
    /// The time interval between advertisements.
    pub interval: Duration,
}

impl AdvertiseMode {
    /// Create an `AdvertiseMode` from an `std::time::Duration` representing the time interval between advertisements.
    pub fn new(interval: Duration) -> Self {
        AdvertiseMode { interval }
    }
}

impl Default for AdvertiseMode {
    fn default() -> Self {
        Self { interval: Duration::from_millis(MODE_LOW_POWER_MS) }
    }
}

impl From<&IntervalProto> for AdvertiseMode {
    fn from(value: &IntervalProto) -> Self {
        Self {
            interval: Duration::from_millis(match value {
                IntervalProto::Milliseconds(ms) => *ms,
                IntervalProto::AdvertiseMode(mode) => match mode.enum_value_or_default() {
                    Mode::LOW_POWER => MODE_LOW_POWER_MS,
                    Mode::BALANCED => MODE_BALANCED_MS,
                    Mode::LOW_LATENCY => MODE_LOW_LATENCY_MS,
                },
                _ => MODE_LOW_POWER_MS,
            }),
        }
    }
}

impl TryFrom<AdvertiseMode> for IntervalProto {
    type Error = String;

    fn try_from(value: AdvertiseMode) -> Result<Self, Self::Error> {
        Ok(
            match value.interval.as_millis().try_into().map_err(|_| {
                String::from("failed to convert interval: duration as millis must fit in a u64")
            })? {
                MODE_LOW_POWER_MS => IntervalProto::AdvertiseMode(Mode::LOW_POWER.into()),
                MODE_BALANCED_MS => IntervalProto::AdvertiseMode(Mode::BALANCED.into()),
                MODE_LOW_LATENCY_MS => IntervalProto::AdvertiseMode(Mode::LOW_LATENCY.into()),
                ms => IntervalProto::Milliseconds(ms),
            },
        )
    }
}

/// A BLE beacon transmit power level. Can be casted to/from a protobuf message.
#[derive(Debug, Copy, Clone, PartialEq)]
pub struct TxPowerLevel {
    /// The transmit power in dBm.
    pub dbm: i8,
}

impl TxPowerLevel {
    /// Create a `TxPowerLevel` from an `i8` measuring power in dBm.
    pub fn new(dbm: i8) -> Self {
        TxPowerLevel { dbm }
    }
}

impl Default for TxPowerLevel {
    fn default() -> Self {
        TxPowerLevel { dbm: TX_POWER_LOW_DBM }
    }
}

impl TryFrom<&TxPowerProto> for TxPowerLevel {
    type Error = String;

    fn try_from(value: &TxPowerProto) -> Result<Self, Self::Error> {
        Ok(Self {
            dbm: (match value {
                TxPowerProto::Dbm(dbm) => (*dbm)
                    .try_into()
                    .map_err(|_| "failed to convert tx power level: it must fit in an i8")?,
                TxPowerProto::TxPowerLevel(level) => match level.enum_value_or_default() {
                    Level::ULTRA_LOW => TX_POWER_ULTRA_LOW_DBM,
                    Level::LOW => TX_POWER_LOW_DBM,
                    Level::MEDIUM => TX_POWER_MEDIUM_DBM,
                    Level::HIGH => TX_POWER_HIGH_DBM,
                },
                _ => TX_POWER_LOW_DBM,
            }),
        })
    }
}

impl From<TxPowerLevel> for TxPowerProto {
    fn from(value: TxPowerLevel) -> Self {
        match value.dbm {
            TX_POWER_ULTRA_LOW_DBM => TxPowerProto::TxPowerLevel(Level::ULTRA_LOW.into()),
            TX_POWER_LOW_DBM => TxPowerProto::TxPowerLevel(Level::LOW.into()),
            TX_POWER_MEDIUM_DBM => TxPowerProto::TxPowerLevel(Level::MEDIUM.into()),
            TX_POWER_HIGH_DBM => TxPowerProto::TxPowerLevel(Level::HIGH.into()),
            dbm => TxPowerProto::Dbm(dbm.into()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build() {
        let mode = AdvertiseMode::new(Duration::from_millis(200));
        let tx_power_level = TxPowerLevel::new(-1);
        let timeout = Duration::from_millis(8000);

        let settings = AdvertiseSettingsBuilder::new()
            .mode(mode)
            .tx_power_level(tx_power_level)
            .scannable()
            .timeout(timeout)
            .build();

        assert_eq!(
            AdvertiseSettings { mode, tx_power_level, scannable: true, timeout: Some(timeout) },
            settings
        )
    }

    #[test]
    fn test_from_proto_succeeds() {
        let interval = IntervalProto::Milliseconds(150);
        let tx_power = TxPowerProto::Dbm(3);
        let timeout_ms = 5555;

        let proto = AdvertiseSettingsProto {
            interval: Some(interval.clone()),
            tx_power: Some(tx_power.clone()),
            scannable: true,
            timeout: timeout_ms,
            ..Default::default()
        };

        let settings = AdvertiseSettings::from_proto(&proto);
        assert!(settings.is_ok());

        let tx_power: Result<TxPowerLevel, _> = (&tx_power).try_into();
        assert!(tx_power.is_ok());
        let tx_power_level = tx_power.unwrap();

        let exp_settings = AdvertiseSettingsBuilder::new()
            .mode((&interval).into())
            .tx_power_level(tx_power_level)
            .scannable()
            .timeout(Duration::from_millis(timeout_ms))
            .build();

        assert_eq!(exp_settings, settings.unwrap());
    }

    #[test]
    fn test_from_proto_fails() {
        let proto = AdvertiseSettingsProto {
            tx_power: Some(TxPowerProto::Dbm((std::i8::MAX as i32) + 1)),
            ..Default::default()
        };

        assert!(AdvertiseSettings::from_proto(&proto).is_err());
    }

    #[test]
    fn test_into_proto() {
        let proto = AdvertiseSettingsProto {
            interval: Some(IntervalProto::Milliseconds(123)),
            tx_power: Some(TxPowerProto::Dbm(-3)),
            scannable: true,
            timeout: 1234,
            ..Default::default()
        };

        let settings = AdvertiseSettings::from_proto(&proto);
        assert!(settings.is_ok());
        let settings: Result<AdvertiseSettingsProto, _> = settings.as_ref().unwrap().try_into();
        assert!(settings.is_ok());

        assert_eq!(proto, settings.unwrap());
    }

    #[test]
    fn test_from_proto_default() {
        let proto = AdvertiseSettingsProto {
            tx_power: Default::default(),
            interval: Default::default(),
            ..Default::default()
        };

        let settings = AdvertiseSettings::from_proto(&proto);
        assert!(settings.is_ok());
        let settings = settings.unwrap();

        let tx_power: i8 = proto
            .tx_power
            .as_ref()
            .map(|proto| TxPowerLevel::try_from(proto).unwrap())
            .unwrap_or_default()
            .dbm;
        let interval: Duration =
            proto.interval.as_ref().map(AdvertiseMode::from).unwrap_or_default().interval;

        assert_eq!(TX_POWER_LOW_DBM, tx_power);
        assert_eq!(Duration::from_millis(MODE_LOW_POWER_MS), interval);
    }

    #[test]
    fn test_from_proto_timeout_unset() {
        let proto = AdvertiseSettingsProto::default();

        let settings = AdvertiseSettings::from_proto(&proto);
        assert!(settings.is_ok());
        let settings = settings.unwrap();

        assert!(settings.timeout.is_none());
    }
}
