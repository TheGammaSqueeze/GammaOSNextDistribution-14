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

//! ieee80211 frames

#![allow(clippy::all)]
#![allow(missing_docs)]
include!(concat!(env!("OUT_DIR"), "/ieee80211_packets.rs"));

use anyhow::anyhow;

/// A Ieee80211 MAC address

impl MacAddress {
    pub fn to_vec(&self) -> [u8; 6] {
        u64::to_le_bytes(self.0)[0..6].try_into().expect("slice with incorrect length")
    }
}

// TODO: Add unit tests.
impl fmt::Display for MacAddress {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let bytes = u64::to_le_bytes(self.0);
        write!(
            f,
            "{:02x}:{:02x}:{:02x}:{:02x}:{:02x}:{:02x}",
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5],
        )
    }
}

impl fmt::Display for Ieee80211 {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{{ds: {}, src: {}, dst: {}}}",
            self.get_ds(),
            self.get_source(),
            self.get_destination()
        )
    }
}

impl From<&[u8; 6]> for MacAddress {
    fn from(bytes: &[u8; 6]) -> Self {
        Self(u64::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], 0, 0]))
    }
}

impl From<MacAddress> for [u8; 6] {
    fn from(MacAddress(addr): MacAddress) -> Self {
        let bytes = u64::to_le_bytes(addr);
        bytes[0..6].try_into().unwrap()
    }
}

impl MacAddress {
    pub fn is_multicast(&self) -> bool {
        let addr = u64::to_le_bytes(self.0);
        addr[0] == 0x1
    }

    pub fn is_broadcast(&self) -> bool {
        let addr = u64::to_le_bytes(self.0);
        addr[0] == 0xff
    }
}

impl Ieee80211 {
    // Frame has addr4 field
    pub fn has_a4(&self) -> bool {
        self.ieee80211.to_ds == 1 || self.ieee80211.from_ds == 1
    }

    pub fn is_to_ap(&self) -> bool {
        self.ieee80211.to_ds == 1 && self.ieee80211.from_ds == 0
    }

    // Frame type is management
    pub fn is_mgmt(&self) -> bool {
        self.ieee80211.ftype == FrameType::Mgmt
    }

    // Frame type is data
    pub fn is_data(&self) -> bool {
        self.ieee80211.ftype == FrameType::Data
    }

    // Frame is probe request
    pub fn is_probe_req(&self) -> bool {
        self.ieee80211.ftype == FrameType::Ctl
            && self.ieee80211.stype == (ManagementSubType::ProbeReq as u8)
    }

    pub fn get_ds(&self) -> String {
        match self.specialize() {
            Ieee80211Child::Ieee80211ToAp(hdr) => "ToAp",
            Ieee80211Child::Ieee80211FromAp(hdr) => "FromAp",
            Ieee80211Child::Ieee80211Ibss(hdr) => "Ibss",
            Ieee80211Child::Ieee80211Wds(hdr) => "Wds",
            _ => panic!("unexpected specialized header"),
        }
        .to_string()
    }

    pub fn get_source(&self) -> MacAddress {
        match self.specialize() {
            Ieee80211Child::Ieee80211ToAp(hdr) => hdr.get_source(),
            Ieee80211Child::Ieee80211FromAp(hdr) => hdr.get_source(),
            Ieee80211Child::Ieee80211Ibss(hdr) => hdr.get_source(),
            Ieee80211Child::Ieee80211Wds(hdr) => hdr.get_source(),
            _ => panic!("unexpected specialized header"),
        }
    }

    /// Ieee80211 packets have 3-4 addresses in different positions based
    /// on the FromDS and ToDS flags. This function gets the destination
    /// address depending on the FromDS+ToDS packet subtypes.
    pub fn get_destination(&self) -> MacAddress {
        match self.specialize() {
            Ieee80211Child::Ieee80211ToAp(hdr) => hdr.get_destination(),
            Ieee80211Child::Ieee80211FromAp(hdr) => hdr.get_destination(),
            Ieee80211Child::Ieee80211Ibss(hdr) => hdr.get_destination(),
            Ieee80211Child::Ieee80211Wds(hdr) => hdr.get_destination(),
            _ => panic!("unexpected specialized header"),
        }
    }

    pub fn get_bssid(&self) -> Option<MacAddress> {
        match self.specialize() {
            Ieee80211Child::Ieee80211ToAp(hdr) => Some(hdr.get_bssid()),
            Ieee80211Child::Ieee80211FromAp(hdr) => Some(hdr.get_bssid()),
            Ieee80211Child::Ieee80211Ibss(hdr) => Some(hdr.get_bssid()),
            Ieee80211Child::Ieee80211Wds(hdr) => None,
            _ => panic!("unexpected specialized header"),
        }
    }

    /// Covert Ieee80211ToAp to Ieee80211FromAp packet.
    pub fn into_from_ap(&self) -> anyhow::Result<Ieee80211FromAp> {
        let frame_payload: Ieee80211Child = self.specialize();
        return match frame_payload {
            Ieee80211Child::Ieee80211ToAp(frame_to_ap) => {
                // Flip from_ap and to_ap bits.
                // TODO: Investigate if there is a way to copy frame_control flags at once.
                // The header struct only has 7 fields, not 15. Most fields come from le16 frame_control.
                Ok(Ieee80211FromApBuilder {
                    duration_id: frame_to_ap.get_duration_id(),
                    ftype: frame_to_ap.get_ftype(),
                    more_data: frame_to_ap.get_more_data(),
                    more_frags: frame_to_ap.get_more_frags(),
                    order: frame_to_ap.get_order(),
                    pm: frame_to_ap.get_pm(),
                    protected: frame_to_ap.get_protected(),
                    retry: frame_to_ap.get_retry(),
                    stype: frame_to_ap.get_stype(),
                    version: frame_to_ap.get_version(),
                    bssid: frame_to_ap.get_bssid(),
                    source: frame_to_ap.get_source(),
                    destination: frame_to_ap.get_destination(),
                    seq_ctrl: frame_to_ap.get_seq_ctrl(),
                    payload: frame_to_ap.get_payload().to_vec(),
                }
                .build())
            }
            _ => Err(anyhow!(
                "Invalid Ieee80211Child packet. from_ds: {}, to_ds: {}",
                self.get_from_ds(),
                self.get_to_ds()
            )),
        };
    }
}

fn parse_mac_address(s: &str) -> Option<MacAddress> {
    let parts: Vec<&str> = s.split(':').collect();
    if parts.len() != 6 {
        return None;
    }
    let mut bytes = [0u8; 6];
    for (i, part) in parts.iter().enumerate() {
        match u8::from_str_radix(part, 16) {
            Ok(n) => bytes[i] = n,
            Err(e) => return None,
        }
    }
    Some(MacAddress::from(&bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mad_address_to_vec() {
        let mac_address: MacAddress = parse_mac_address("00:0b:85:71:20:ce").unwrap();
        let mac_address_bytes = mac_address.to_vec();
        let reconstructed_mac_address = MacAddress::from(&mac_address_bytes);
        assert_eq!(mac_address, reconstructed_mac_address);
    }

    // These tests use the packets available here
    // https://community.cisco.com/t5/wireless-mobility-knowledge-base/802-11-frames-a-starter-guide-to-learn-wireless-sniffer-traces/ta-p/3110019

    #[test]
    fn test_frame_qos() {
        let frame: Vec<u8> = vec![
            0x88, 0x02, 0x2c, 0x00, 0x00, 0x13, 0xe8, 0xeb, 0xd6, 0x03, 0x00, 0x0b, 0x85, 0x71,
            0x20, 0xce, 0x00, 0x0b, 0x85, 0x71, 0x20, 0xce, 0x00, 0x26, 0x00, 0x00,
        ];
        let hdr = Ieee80211::parse(&frame).unwrap();
        assert!(hdr.is_data());
        assert_eq!(hdr.get_stype(), DataSubType::Qos as u8);
        assert_eq!(hdr.get_from_ds(), 1);
        assert_eq!(hdr.get_to_ds(), 0);
        assert_eq!(hdr.get_duration_id(), 44);
        // Source address: Cisco_71:20:ce (00:0b:85:71:20:ce)
        let a = format!("{}", hdr.get_source());
        let b = format!("{}", parse_mac_address("00:0b:85:71:20:ce").unwrap());
        assert_eq!(a, b);
    }

    #[test]
    fn test_is_multicast() {
        // Multicast MAC address: 01:00:5E:00:00:FB
        let mdns_mac_address = parse_mac_address("01:00:5e:00:00:fb").unwrap();
        assert!(mdns_mac_address.is_multicast());
        // Source address: Cisco_71:20:ce (00:0b:85:71:20:ce)
        let non_mdns_mac_address = parse_mac_address("00:0b:85:71:20:ce").unwrap();
        assert!(!non_mdns_mac_address.is_multicast());
    }
}
