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

//! Builder for Advertising Data

use netsim_proto::model::{
    chip::ble_beacon::AdvertiseData as AdvertiseDataProto,
    chip_create::BleBeaconCreate as BleBeaconCreateProto,
};
use std::convert::TryInto;

use super::advertise_settings::TxPowerLevel;

// Core Specification (v5.3 Vol 6 Part B §2.3.1.3 and §2.3.1.4)
const MAX_ADV_NONCONN_DATA_LEN: usize = 31;

// Assigned Numbers Document (§2.3)
const AD_TYPE_COMPLETE_NAME: u8 = 0x09;
const AD_TYPE_TX_POWER: u8 = 0x0A;
const AD_TYPE_MANUFACTURER_DATA: u8 = 0xFF;

#[derive(Debug)]
pub struct AdvertiseData {
    /// Whether or not to include the device name in the packet.
    pub include_device_name: bool,
    /// Whether or not to include the transmit power in the packet.
    pub include_tx_power_level: bool,
    /// Manufacturer-specific data.
    pub manufacturer_data: Option<Vec<u8>>,
    bytes: Vec<u8>,
}

impl AdvertiseData {
    /// Returns a new advertise data builder with no fields.
    pub fn builder(device_name: String, tx_power_level: TxPowerLevel) -> AdvertiseDataBuilder {
        AdvertiseDataBuilder::new(device_name, tx_power_level)
    }

    /// Returns a new advertise data with fields from a protobuf.
    pub fn from_proto(
        device_name: String,
        tx_power_level: TxPowerLevel,
        proto: &AdvertiseDataProto,
    ) -> Result<Self, String> {
        let mut builder = AdvertiseDataBuilder::new(device_name, tx_power_level);

        if proto.include_device_name {
            builder.include_device_name();
        }

        if proto.include_tx_power_level {
            builder.include_tx_power_level();
        }

        if !proto.manufacturer_data.is_empty() {
            builder.manufacturer_data(proto.manufacturer_data.clone());
        }

        builder.build()
    }

    /// Gets the raw bytes to be sent in the advertise data field of a BLE advertise packet.
    pub fn to_bytes(&self) -> Vec<u8> {
        self.bytes.clone()
    }
}

impl From<&AdvertiseData> for AdvertiseDataProto {
    fn from(value: &AdvertiseData) -> Self {
        AdvertiseDataProto {
            include_device_name: value.include_device_name,
            include_tx_power_level: value.include_tx_power_level,
            manufacturer_data: value.manufacturer_data.clone().unwrap_or_default(),
            ..Default::default()
        }
    }
}

#[derive(Default)]
/// Builder for the advertise data field of a Bluetooth packet.
pub struct AdvertiseDataBuilder {
    device_name: String,
    tx_power_level: TxPowerLevel,
    include_device_name: bool,
    include_tx_power_level: bool,
    manufacturer_data: Option<Vec<u8>>,
}

impl AdvertiseDataBuilder {
    /// Returns a new advertise data builder with empty fields.
    pub fn new(device_name: String, tx_power_level: TxPowerLevel) -> Self {
        AdvertiseDataBuilder { device_name, tx_power_level, ..Self::default() }
    }

    /// Build the advertise data.
    ///
    /// Returns a vector of bytes holding the serialized advertise data based on the fields added to the builder, or `Err(String)` if the data would be malformed.
    pub fn build(&self) -> Result<AdvertiseData, String> {
        Ok(AdvertiseData {
            include_device_name: self.include_device_name,
            include_tx_power_level: self.include_tx_power_level,
            manufacturer_data: self.manufacturer_data.clone(),
            bytes: self.serialize()?,
        })
    }

    /// Add a complete device name field to the advertise data.
    pub fn include_device_name(&mut self) -> &mut Self {
        self.include_device_name = true;
        self
    }

    /// Add a transmit power field to the advertise data.
    pub fn include_tx_power_level(&mut self) -> &mut Self {
        self.include_tx_power_level = true;
        self
    }

    /// Add a manufacturer data field to the advertise data.
    pub fn manufacturer_data(&mut self, manufacturer_data: Vec<u8>) -> &mut Self {
        self.manufacturer_data = Some(manufacturer_data);
        self
    }

    fn serialize(&self) -> Result<Vec<u8>, String> {
        let mut bytes = Vec::new();

        if self.include_device_name {
            let device_name = self.device_name.as_bytes();

            if device_name.len() > MAX_ADV_NONCONN_DATA_LEN - 2 {
                return Err(format!(
                    "complete name must be less than {} chars",
                    MAX_ADV_NONCONN_DATA_LEN - 2
                ));
            }

            bytes.extend(vec![
                (1 + device_name.len())
                    .try_into()
                    .map_err(|_| "complete name must be less than 255 chars")?,
                AD_TYPE_COMPLETE_NAME,
            ]);
            bytes.extend_from_slice(device_name);
        }

        if self.include_tx_power_level {
            bytes.extend(vec![2, AD_TYPE_TX_POWER, self.tx_power_level.dbm as u8]);
        }

        if let Some(manufacturer_data) = &self.manufacturer_data {
            if manufacturer_data.len() < 2 {
                // Supplement to the Core Specification (v10 Part A §1.4.2)
                return Err("manufacturer data must be at least 2 bytes".to_string());
            }

            if manufacturer_data.len() > MAX_ADV_NONCONN_DATA_LEN - 2 {
                return Err(format!(
                    "manufacturer data must be less than {} bytes",
                    MAX_ADV_NONCONN_DATA_LEN - 2
                ));
            }

            bytes.extend(vec![
                (1 + manufacturer_data.len())
                    .try_into()
                    .map_err(|_| "manufacturer data must be less than 255 bytes")?,
                AD_TYPE_MANUFACTURER_DATA,
            ]);
            bytes.extend_from_slice(manufacturer_data);
        }

        if bytes.len() > MAX_ADV_NONCONN_DATA_LEN {
            return Err(format!(
                "exceeded maximum advertising packet length of {} bytes",
                MAX_ADV_NONCONN_DATA_LEN
            ));
        }

        Ok(bytes)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use netsim_proto::model::chip::ble_beacon::AdvertiseSettings as AdvertiseSettingsProto;
    use protobuf::MessageField;

    const HEADER_LEN: usize = 2;

    #[test]
    fn test_from_proto_succeeds() {
        let device_name = String::from("test-device-name");
        let tx_power = TxPowerLevel::new(1);
        let exp_name_len = HEADER_LEN + device_name.len();
        let exp_tx_power_len = HEADER_LEN + 1;

        let ad = AdvertiseData::from_proto(
            device_name.clone(),
            tx_power,
            &AdvertiseDataProto {
                include_device_name: true,
                include_tx_power_level: true,
                ..Default::default()
            },
        );

        assert!(ad.is_ok());
        let bytes = ad.unwrap().bytes;

        assert_eq!(exp_name_len + exp_tx_power_len, bytes.len());
        assert_eq!(
            [
                vec![(exp_name_len - 1) as u8, AD_TYPE_COMPLETE_NAME],
                device_name.into_bytes(),
                vec![(exp_tx_power_len - 1) as u8, AD_TYPE_TX_POWER, tx_power.dbm as u8]
            ]
            .concat(),
            bytes
        );
    }

    #[test]
    fn test_from_proto_fails() {
        let device_name = "a".repeat(MAX_ADV_NONCONN_DATA_LEN - HEADER_LEN + 1);
        let data = AdvertiseData::from_proto(
            device_name,
            TxPowerLevel::new(0),
            &AdvertiseDataProto { include_device_name: true, ..Default::default() },
        );

        assert!(data.is_err());
    }

    #[test]
    fn test_from_proto_sets_proto_field() {
        let device_name = String::from("test-device-name");
        let tx_power = TxPowerLevel::new(1);
        let ad_proto = AdvertiseDataProto {
            include_device_name: true,
            include_tx_power_level: true,
            ..Default::default()
        };

        let ad = AdvertiseData::from_proto(device_name.clone(), tx_power, &ad_proto);

        assert!(ad.is_ok());
        assert_eq!(ad_proto, (&ad.unwrap()).into());
    }

    #[test]
    fn test_set_device_name_succeeds() {
        let device_name = String::from("test-device-name");
        let ad = AdvertiseData::builder(device_name.clone(), TxPowerLevel::default())
            .include_device_name()
            .build();
        let exp_len = HEADER_LEN + device_name.len();

        assert!(ad.is_ok());
        let bytes = ad.unwrap().bytes;

        assert_eq!(exp_len, bytes.len());
        assert_eq!(
            [vec![(exp_len - 1) as u8, AD_TYPE_COMPLETE_NAME], device_name.into_bytes()].concat(),
            bytes
        );
    }

    #[test]
    fn test_set_device_name_fails() {
        let device_name = "a".repeat(MAX_ADV_NONCONN_DATA_LEN - HEADER_LEN + 1);
        let data = AdvertiseData::builder(device_name, TxPowerLevel::default())
            .include_device_name()
            .build();

        assert!(data.is_err());
    }

    #[test]
    fn test_set_tx_power_level() {
        let tx_power = TxPowerLevel::new(-6);
        let ad =
            AdvertiseData::builder(String::default(), tx_power).include_tx_power_level().build();
        let exp_len = HEADER_LEN + 1;

        assert!(ad.is_ok());
        let bytes = ad.unwrap().bytes;

        assert_eq!(exp_len, bytes.len());
        assert_eq!(vec![(exp_len - 1) as u8, AD_TYPE_TX_POWER, tx_power.dbm as u8], bytes);
    }

    #[test]
    fn test_set_manufacturer_data_succeeds() {
        let manufacturer_data = String::from("test-manufacturer-data");
        let ad = AdvertiseData::builder(String::default(), TxPowerLevel::default())
            .manufacturer_data(manufacturer_data.clone().into_bytes())
            .build();
        let exp_len = HEADER_LEN + manufacturer_data.len();

        assert!(ad.is_ok());
        let bytes = ad.unwrap().bytes;

        assert_eq!(exp_len, bytes.len());
        assert_eq!(
            [vec![(exp_len - 1) as u8, AD_TYPE_MANUFACTURER_DATA], manufacturer_data.into_bytes()]
                .concat(),
            bytes
        );
    }

    #[test]
    fn test_set_manufacturer_data_fails() {
        let manufacturer_data = "a".repeat(MAX_ADV_NONCONN_DATA_LEN - HEADER_LEN + 1);
        let data = AdvertiseData::builder(String::default(), TxPowerLevel::default())
            .manufacturer_data(manufacturer_data.into_bytes())
            .build();

        assert!(data.is_err());
    }

    #[test]
    fn test_set_name_and_power_succeeds() {
        let exp_data = [
            0x0F, 0x09, b'g', b'D', b'e', b'v', b'i', b'c', b'e', b'-', b'b', b'e', b'a', b'c',
            b'o', b'n', 0x02, 0x0A, 0x0,
        ];
        let data = AdvertiseData::builder(String::from("gDevice-beacon"), TxPowerLevel::new(0))
            .include_device_name()
            .include_tx_power_level()
            .build();

        assert!(data.is_ok());
        assert_eq!(exp_data, data.unwrap().bytes.as_slice());
    }
}
