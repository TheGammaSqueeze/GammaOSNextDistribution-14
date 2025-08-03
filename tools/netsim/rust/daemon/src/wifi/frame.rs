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

use super::packets::ieee80211::{Ieee80211, MacAddress};
use super::packets::mac80211_hwsim::{
    HwsimAttr, HwsimCmd, HwsimMsg, HwsimMsgHdr, TxRate, TxRateFlag,
};
use crate::wifi::hwsim_attr_set::HwsimAttrSet;
use anyhow::{anyhow, Context};
use log::{info, warn};

/// Parser for the hwsim Frame command (HWSIM_CMD_FRAME).
///
/// The Frame command is sent by the kernel's mac80211_hwsim subsystem
/// and contains the IEEE 802.11 frame along with hwsim attributes.
///
/// This module parses the required and optional hwsim attributes and
/// returns errors if any required attributes are missing.

// The Frame struct contains parsed attributes along with the raw and
// parsed 802.11 frame in `data` and `ieee80211.`
#[derive(Debug)]
pub struct Frame {
    pub transmitter: Option<MacAddress>,
    pub flags: Option<u32>,
    pub tx_info: Option<Vec<TxRate>>,
    pub cookie: Option<u64>,
    pub signal: Option<u32>,
    pub freq: Option<u32>,
    pub data: Vec<u8>,
    pub ieee80211: Ieee80211,
    pub hwsim_msg: HwsimMsg,
    pub attrs: HwsimAttrSet,
}

impl Frame {
    // Builds and validates the Frame from the attributes in the
    // packet. Called when a hwsim packet with HwsimCmd::Frame is
    // found.
    pub fn parse(msg: &HwsimMsg) -> anyhow::Result<Frame> {
        // Only expected to be called with HwsimCmd::Frame
        if (msg.get_hwsim_hdr().hwsim_cmd != HwsimCmd::Frame) {
            panic!("Invalid hwsim_cmd");
        }
        let attrs = HwsimAttrSet::parse(msg.get_attributes()).context("HwsimAttrSet")?;
        let frame = attrs.frame.clone().context("Frame")?;
        let ieee80211 = Ieee80211::parse(&frame).context("Ieee80211")?;
        // Required attributes are unwrapped and return an error if
        // they are not present.
        Ok(Frame {
            transmitter: attrs.transmitter,
            flags: attrs.flags,
            tx_info: attrs.tx_info.clone(),
            cookie: attrs.cookie,
            signal: attrs.signal,
            freq: attrs.freq,
            data: frame,
            ieee80211,
            hwsim_msg: msg.clone(),
            attrs,
        })
    }
}
