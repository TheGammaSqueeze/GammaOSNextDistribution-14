// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::convert::TryInto;

use log::error;

pub fn u8_to_bytes(value: u8) -> Vec<u8> {
    value.to_le_bytes().to_vec()
}

pub fn u16_to_bytes(value: u16) -> Vec<u8> {
    value.to_le_bytes().to_vec()
}

pub fn u32_to_bytes(value: u32) -> Vec<u8> {
    value.to_le_bytes().to_vec()
}

#[allow(dead_code)]
pub fn u64_to_bytes(value: u64) -> Vec<u8> {
    value.to_le_bytes().to_vec()
}

pub fn bytes_to_u8(value: Vec<u8>) -> Option<u8> {
    Some(u8::from_le_bytes(value.try_into().ok()?))
}

pub fn bytes_to_u16(value: Vec<u8>) -> Option<u16> {
    Some(u16::from_le_bytes(value.try_into().ok()?))
}

pub fn bytes_to_u32(value: Vec<u8>) -> Option<u32> {
    Some(u32::from_le_bytes(value.try_into().ok()?))
}

pub fn bytes_to_u64(value: Vec<u8>) -> Option<u64> {
    Some(u64::from_le_bytes(value.try_into().ok()?))
}

pub fn validate(value: bool, err_msg: &str) -> Option<()> {
    match value {
        true => Some(()),
        false => {
            error!("{}", err_msg);
            None
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_convert_u8_bytes() {
        let value: u8 = 0x57;
        let arr = u8_to_bytes(value);

        assert_eq!(arr, vec![0x57]);
        assert_eq!(bytes_to_u8(arr), Some(value));
    }

    #[test]
    fn test_convert_u16_bytes() {
        let value: u16 = 0x1357;
        let arr = u16_to_bytes(value);

        assert_eq!(arr, vec![0x57, 0x13]);
        assert_eq!(bytes_to_u16(arr), Some(value));
    }

    #[test]
    fn test_convert_u32_bytes() {
        let value: u32 = 0x12345678;
        let arr = u32_to_bytes(value);

        assert_eq!(arr, vec![0x78, 0x56, 0x34, 0x12]);
        assert_eq!(bytes_to_u32(arr), Some(value));
    }

    #[test]
    fn test_convert_u64_bytes() {
        let value: u64 = 0x0123456789ABCDEF;
        let arr = u64_to_bytes(value);

        assert_eq!(arr, vec![0xEF, 0xCD, 0xAB, 0x89, 0x67, 0x45, 0x23, 0x01]);
        assert_eq!(bytes_to_u64(arr), Some(value));
    }
}
