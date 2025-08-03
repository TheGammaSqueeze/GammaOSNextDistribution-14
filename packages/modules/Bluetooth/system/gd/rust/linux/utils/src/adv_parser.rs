//! This library provides helper functions to parse info from advertising data.

use std::collections::HashMap;

use bt_topshim::bindings::root::bluetooth::Uuid;
use bt_topshim::btif::Uuid128Bit;

// Advertising data types.
const FLAGS: u8 = 0x01;
const COMPLETE_LIST_16_BIT_SERVICE_UUIDS: u8 = 0x03;
const COMPLETE_LIST_32_BIT_SERVICE_UUIDS: u8 = 0x05;
const COMPLETE_LIST_128_BIT_SERVICE_UUIDS: u8 = 0x07;
const SHORTENED_LOCAL_NAME: u8 = 0x08;
const COMPLETE_LOCAL_NAME: u8 = 0x09;
const SERVICE_DATA_16_BIT_UUID: u8 = 0x16;
const SERVICE_DATA_32_BIT_UUID: u8 = 0x20;
const SERVICE_DATA_128_BIT_UUID: u8 = 0x21;
const MANUFACTURER_SPECIFIC_DATA: u8 = 0xff;

struct AdvDataIterator<'a> {
    data: &'a [u8],
    data_type: u8,
    cur: usize, // to keep current position
}

// Iterates over Advertising Data's elements having the given AD type. `next()`
// returns the next slice of the advertising data element excluding the length
// and type.
impl<'a> Iterator for AdvDataIterator<'a> {
    type Item = &'a [u8];
    fn next(&mut self) -> Option<&'a [u8]> {
        let mut i = self.cur;
        while i < self.data.len() {
            let len: usize = self.data[i].into();
            if (len == 0) || (i + len >= self.data.len()) {
                break;
            }
            if self.data[i + 1] == self.data_type {
                self.cur = i + len + 1;
                return Some(&self.data[i + 2..self.cur]);
            }
            i += len + 1;
        }
        None
    }
}

fn iterate_adv_data(data: &[u8], data_type: u8) -> AdvDataIterator {
    AdvDataIterator { data, data_type, cur: 0 }
}

// Helper function to extract flags from advertising data
pub fn extract_flags(bytes: &[u8]) -> u8 {
    iterate_adv_data(bytes, FLAGS).next().map_or(0, |v| v[0])
}

// Helper function to extract service uuids (128bit) from advertising data
pub fn extract_service_uuids(bytes: &[u8]) -> Vec<Uuid128Bit> {
    iterate_adv_data(bytes, COMPLETE_LIST_16_BIT_SERVICE_UUIDS)
        .flat_map(|slice| slice.chunks(2))
        .filter_map(|chunk| Uuid::try_from_little_endian(chunk).ok().map(|uuid| uuid.uu))
        .chain(
            iterate_adv_data(bytes, COMPLETE_LIST_32_BIT_SERVICE_UUIDS)
                .flat_map(|slice| slice.chunks(4))
                .filter_map(|chunk| Uuid::try_from_little_endian(chunk).ok().map(|uuid| uuid.uu)),
        )
        .chain(
            iterate_adv_data(bytes, COMPLETE_LIST_128_BIT_SERVICE_UUIDS)
                .flat_map(|slice| slice.chunks(16))
                .filter_map(|chunk| Uuid::try_from_little_endian(chunk).ok().map(|uuid| uuid.uu)),
        )
        .collect()
}

// Helper function to extract name from advertising data
pub fn extract_name(bytes: &[u8]) -> String {
    iterate_adv_data(bytes, COMPLETE_LOCAL_NAME)
        .next()
        .or(iterate_adv_data(bytes, SHORTENED_LOCAL_NAME).next())
        .map_or("".to_string(), |v| String::from_utf8_lossy(v).to_string())
}

// Helper function to extract service data from advertising data
pub fn extract_service_data(bytes: &[u8]) -> HashMap<String, Vec<u8>> {
    iterate_adv_data(bytes, SERVICE_DATA_16_BIT_UUID)
        .filter_map(|slice| {
            Uuid::try_from_little_endian(slice.get(0..2)?)
                .ok()
                .map(|uuid| (uuid.to_string(), slice[2..].to_vec()))
        })
        .chain(iterate_adv_data(bytes, SERVICE_DATA_32_BIT_UUID).filter_map(|slice| {
            Uuid::try_from_little_endian(slice.get(0..4)?)
                .ok()
                .map(|uuid| (uuid.to_string(), slice[4..].to_vec()))
        }))
        .chain(iterate_adv_data(bytes, SERVICE_DATA_128_BIT_UUID).filter_map(|slice| {
            Uuid::try_from_little_endian(slice.get(0..16)?)
                .ok()
                .map(|uuid| (uuid.to_string(), slice[16..].to_vec()))
        }))
        .collect()
}

// Helper function to extract manufacturer data from advertising data
pub fn extract_manufacturer_data(bytes: &[u8]) -> HashMap<u16, Vec<u8>> {
    iterate_adv_data(bytes, MANUFACTURER_SPECIFIC_DATA)
        .filter_map(|slice| {
            slice.get(0..2)?.try_into().ok().map(|be| (u16::from_be_bytes(be), slice[2..].to_vec()))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_flags() {
        let payload: Vec<u8> = vec![
            2,
            FLAGS,
            3,
            17,
            COMPLETE_LIST_128_BIT_SERVICE_UUIDS,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
        ];
        let flags = extract_flags(payload.as_slice());
        assert_eq!(flags, 3);
    }

    #[test]
    fn test_extract_service_uuids() {
        let payload: Vec<u8> = vec![2, FLAGS, 3];
        let uuids = extract_service_uuids(payload.as_slice());
        assert_eq!(uuids.len(), 0);

        let payload: Vec<u8> = vec![
            2,
            FLAGS,
            3,
            3,
            COMPLETE_LIST_16_BIT_SERVICE_UUIDS,
            0x2C,
            0xFE,
            5,
            COMPLETE_LIST_32_BIT_SERVICE_UUIDS,
            2,
            3,
            4,
            5,
            17,
            COMPLETE_LIST_128_BIT_SERVICE_UUIDS,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
        ];
        let uuids = extract_service_uuids(payload.as_slice());
        assert_eq!(uuids.len(), 3);
        assert_eq!(
            uuids[0],
            Uuid::from([
                0x0, 0x0, 0xFE, 0x2C, 0x0, 0x0, 0x10, 0x0, 0x80, 0x0, 0x0, 0x80, 0x5f, 0x9b, 0x34,
                0xfb
            ])
            .uu
        );
        assert_eq!(
            uuids[1],
            Uuid::from([
                0x5, 0x4, 0x3, 0x2, 0x0, 0x0, 0x10, 0x0, 0x80, 0x0, 0x0, 0x80, 0x5f, 0x9b, 0x34,
                0xfb
            ])
            .uu
        );
        assert_eq!(uuids[2], Uuid::from([15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0]).uu);
    }

    #[test]
    fn test_extract_name() {
        let payload: Vec<u8> = vec![2, FLAGS, 3];
        let name = extract_name(payload.as_slice());
        assert_eq!(name, "");

        let payload: Vec<u8> = vec![2, FLAGS, 3, 5, COMPLETE_LOCAL_NAME, 116, 101, 115, 116];
        let name = extract_name(payload.as_slice());
        assert_eq!(name, "test");

        let payload: Vec<u8> = vec![2, FLAGS, 3, 5, SHORTENED_LOCAL_NAME, 116, 101, 115, 116];
        let name = extract_name(payload.as_slice());
        assert_eq!(name, "test");
    }

    #[test]
    fn test_extract_service_data() {
        let payload: Vec<u8> = vec![2, FLAGS, 3];
        let service_data = extract_service_data(payload.as_slice());
        assert_eq!(service_data.len(), 0);

        let payload: Vec<u8> = vec![
            4,
            SERVICE_DATA_16_BIT_UUID,
            0x2C,
            0xFE,
            0xFF,
            6,
            SERVICE_DATA_32_BIT_UUID,
            2,
            3,
            4,
            5,
            0xFE,
            18,
            SERVICE_DATA_128_BIT_UUID,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            SERVICE_DATA_128_BIT_UUID,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
        ];
        let service_data = extract_service_data(payload.as_slice());
        assert_eq!(service_data.len(), 4);
        let expected_uuid = Uuid::from([
            0x0, 0x0, 0xFE, 0x2C, 0x0, 0x0, 0x10, 0x0, 0x80, 0x0, 0x0, 0x80, 0x5f, 0x9b, 0x34, 0xfb,
        ])
        .to_string();
        assert_eq!(service_data.get(&expected_uuid), Some(&vec![0xFF]));
        let expected_uuid = Uuid::from([
            0x5, 0x4, 0x3, 0x2, 0x0, 0x0, 0x10, 0x0, 0x80, 0x0, 0x0, 0x80, 0x5f, 0x9b, 0x34, 0xfb,
        ])
        .to_string();
        assert_eq!(service_data.get(&expected_uuid), Some(&vec![0xFE]));
        let expected_uuid =
            Uuid::from([15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0]).to_string();
        assert_eq!(service_data.get(&expected_uuid), Some(&vec![16]));
        let expected_uuid =
            Uuid::from([16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]).to_string();
        assert_eq!(service_data.get(&expected_uuid), Some(&vec![]));
    }

    #[test]
    fn test_extract_manufacturer_data() {
        let payload: Vec<u8> = vec![2, FLAGS, 3];
        let manufacturer_data = extract_manufacturer_data(payload.as_slice());
        assert_eq!(manufacturer_data.len(), 0);

        let payload: Vec<u8> = vec![2, MANUFACTURER_SPECIFIC_DATA, 0];
        let manufacturer_data = extract_manufacturer_data(payload.as_slice());
        assert_eq!(manufacturer_data.len(), 0);

        let payload: Vec<u8> =
            vec![4, MANUFACTURER_SPECIFIC_DATA, 0, 1, 2, 3, MANUFACTURER_SPECIFIC_DATA, 1, 2];
        let manufacturer_data = extract_manufacturer_data(payload.as_slice());
        assert_eq!(manufacturer_data.len(), 2);
        assert_eq!(manufacturer_data.get(&1), Some(&vec![2]));
        assert_eq!(manufacturer_data.get(&258), Some(&vec![]));
    }
}
