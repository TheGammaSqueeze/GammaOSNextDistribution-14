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

use std::fmt;

use super::packets::ieee80211::MacAddress;
use super::packets::mac80211_hwsim::{
    self, HwsimAttr, HwsimAttrChild::*, HwsimCmd, HwsimMsg, HwsimMsgHdr, TxRate, TxRateFlag,
};
use super::packets::netlink::{NlAttrHdr, NlMsgHdr};
use anyhow::{anyhow, Context};
use log::{info, warn};
use pdl_runtime::Packet;

/// Parse or Build the Hwsim attributes into a set.
///
/// Hwsim attributes are used to exchange data between kernel's
/// mac80211_hwsim subsystem and a user space process and include:
///
///   HWSIM_ATTR_ADDR_TRANSMITTER,
///   HWSIM_ATTR_ADDR_RECEIVER,
///   HWSIM_ATTR_FRAME,
///   HWSIM_ATTR_FLAGS,
///   HWSIM_ATTR_RX_RATE,
///   HWSIM_ATTR_SIGNAL,
///   HWSIM_ATTR_COOKIE,
///   HWSIM_ATTR_FREQ (optional)
///   HWSIM_ATTR_TX_INFO (new use)
///   HWSIM_ATTR_TX_INFO_FLAGS (new use)

/// Aligns a length to the specified alignment boundary (`NLA_ALIGNTO`).
///
/// # Arguments
///
/// * `array_length`: The length in bytes to be aligned.
///
/// # Returns
///
/// * The aligned length, which is a multiple of `NLA_ALIGNTO`.
///
fn nla_align(array_length: usize) -> usize {
    const NLA_ALIGNTO: usize = 4;
    array_length.wrapping_add(NLA_ALIGNTO - 1) & !(NLA_ALIGNTO - 1)
}

#[derive(Default)]
pub struct HwsimAttrSetBuilder {
    transmitter: Option<MacAddress>,
    receiver: Option<MacAddress>,
    frame: Option<Vec<u8>>,
    flags: Option<u32>,
    rx_rate_idx: Option<u32>,
    signal: Option<u32>,
    cookie: Option<u64>,
    freq: Option<u32>,
    tx_info: Option<Vec<TxRate>>,
    tx_info_flags: Option<Vec<TxRateFlag>>,
    attributes: Vec<u8>,
}

#[derive(Debug)]
pub struct HwsimAttrSet {
    pub transmitter: Option<MacAddress>,
    pub receiver: Option<MacAddress>,
    pub frame: Option<Vec<u8>>,
    pub flags: Option<u32>,
    pub rx_rate_idx: Option<u32>,
    pub signal: Option<u32>,
    pub cookie: Option<u64>,
    pub freq: Option<u32>,
    pub tx_info: Option<Vec<TxRate>>,
    pub tx_info_flags: Option<Vec<TxRateFlag>>,
    pub attributes: Vec<u8>,
}

/// Builder pattern for each of the HWSIM_ATTR used in conjunction
/// with the HwsimAttr packet formats defined in `mac80211_hwsim.pdl`
///
/// Used during `parse` or to create new HwsimCmd packets containing
/// an attributes vector.
///
impl HwsimAttrSetBuilder {
    // Add packet to the attributes vec and pad for proper NLA
    // alignment. This provides for to_bytes for a HwsimMsg for
    // packets constructed by the Builder.

    fn extend_attributes<P: Packet>(&mut self, packet: P) {
        let mut vec: Vec<u8> = packet.to_vec();
        let nla_padding = nla_align(vec.len()) - vec.len();
        vec.extend(vec![0; nla_padding]);
        self.attributes.extend(vec);
    }

    pub fn transmitter(&mut self, transmitter: &[u8; 6]) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrAddrTransmitterBuilder {
                address: *transmitter,
                nla_m: 0,
                nla_o: 0,
            }
            .build(),
        );
        self.transmitter = Some(MacAddress::from(transmitter));
        self
    }

    pub fn receiver(&mut self, receiver: &[u8; 6]) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrAddrReceiverBuilder { address: *receiver, nla_m: 0, nla_o: 0 }
                .build(),
        );
        self.receiver = Some(MacAddress::from(receiver));
        self
    }

    pub fn frame(&mut self, frame: &[u8]) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrFrameBuilder { data: (*frame).to_vec(), nla_m: 0, nla_o: 0 }
                .build(),
        );
        self.frame = Some(frame.to_vec());
        self
    }

    pub fn flags(&mut self, flags: u32) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrFlagsBuilder { flags, nla_m: 0, nla_o: 0 }.build(),
        );
        self.flags = Some(flags);
        self
    }

    pub fn rx_rate(&mut self, rx_rate_idx: u32) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrRxRateBuilder { rx_rate_idx, nla_m: 0, nla_o: 0 }.build(),
        );
        self.rx_rate_idx = Some(rx_rate_idx);
        self
    }

    pub fn signal(&mut self, signal: u32) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrSignalBuilder { signal, nla_m: 0, nla_o: 0 }.build(),
        );
        self.signal = Some(signal);
        self
    }

    pub fn cookie(&mut self, cookie: u64) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrCookieBuilder { cookie, nla_m: 0, nla_o: 0 }.build(),
        );
        self.cookie = Some(cookie);
        self
    }

    pub fn freq(&mut self, freq: u32) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrFreqBuilder { freq, nla_m: 0, nla_o: 0 }.build(),
        );
        self.freq = Some(freq);
        self
    }

    pub fn tx_info(&mut self, tx_info: &[TxRate]) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrTxInfoBuilder {
                tx_rates: (*tx_info).to_vec(),
                nla_m: 0,
                nla_o: 0,
            }
            .build(),
        );
        self.tx_info = Some(tx_info.to_vec());
        self
    }

    pub fn tx_info_flags(&mut self, tx_rate_flags: &[TxRateFlag]) -> &mut Self {
        self.extend_attributes(
            mac80211_hwsim::HwsimAttrTxInfoFlagsBuilder {
                tx_rate_flags: (*tx_rate_flags).to_vec(),
                nla_m: 0,
                nla_o: 0,
            }
            .build(),
        );
        self.tx_info_flags = Some(tx_rate_flags.to_vec());
        self
    }

    pub fn build(mut self) -> anyhow::Result<HwsimAttrSet> {
        Ok(HwsimAttrSet {
            transmitter: self.transmitter,
            receiver: self.receiver,
            cookie: self.cookie,
            flags: self.flags,
            rx_rate_idx: self.rx_rate_idx,
            signal: self.signal,
            frame: self.frame,
            freq: self.freq,
            tx_info: self.tx_info,
            tx_info_flags: self.tx_info_flags,
            attributes: self.attributes,
        })
    }
}

impl fmt::Display for HwsimAttrSet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{{ ");
        self.transmitter.map(|v| write!(f, "transmitter: {}, ", v));
        self.receiver.map(|v| write!(f, "receiver: {}, ", v));
        self.cookie.map(|v| write!(f, "cookie: {}, ", v));
        self.flags.map(|v| write!(f, "flags: {}, ", v));
        self.rx_rate_idx.map(|v| write!(f, "rx_rate_idx: {}, ", v));
        self.signal.map(|v| write!(f, "signal: {}, ", v));
        self.frame.as_ref().map(|v| write!(f, "frame: {:?}, ", &v));
        self.freq.map(|v| write!(f, "freq: {}, ", v));
        self.tx_info.as_ref().map(|v| write!(f, "tx_info: {:?}, ", &v));
        self.tx_info_flags.as_ref().map(|v| write!(f, "tx_info_flags: {:?}, ", &v));
        write!(f, "}}");
        Ok(())
    }
}

impl HwsimAttrSet {
    /// Creates a new `HwsimAttrSetBuilder` with default settings, ready for configuring attributes.
    ///
    /// # Returns
    ///
    /// * A new `HwsimAttrSetBuilder` instance, initialized with default values.
    ///
    /// # Examples
    ///
    /// ```rust
    /// let mut builder = HwsimAttrSetBuilder::builder();
    /// builder.signal(42).cookie(32); // Example attribute configuration
    /// let attr_set = builder.build();
    /// ```
    pub fn builder() -> HwsimAttrSetBuilder {
        HwsimAttrSetBuilder::default()
    }

    /// Parse and validates the attributes from a HwsimMsg command.
    pub fn parse(attributes: &[u8]) -> anyhow::Result<HwsimAttrSet> {
        let mut index: usize = 0;
        let mut builder = HwsimAttrSet::builder();
        while (index < attributes.len()) {
            // Parse a generic netlink attribute to get the size
            let nla_hdr = NlAttrHdr::parse(&attributes[index..index + 4]).unwrap();
            let nla_len = nla_hdr.nla_len as usize;
            // Now parse a single attribute at a time from the
            // attributes to allow padding per attribute.
            let hwsim_attr = HwsimAttr::parse(&attributes[index..index + nla_len])?;
            match hwsim_attr.specialize() {
                HwsimAttrAddrTransmitter(child) => builder.transmitter(child.get_address()),
                HwsimAttrAddrReceiver(child) => builder.receiver(child.get_address()),
                HwsimAttrFrame(child) => builder.frame(child.get_data()),
                HwsimAttrFlags(child) => builder.flags(child.get_flags()),
                HwsimAttrRxRate(child) => builder.rx_rate(child.get_rx_rate_idx()),
                HwsimAttrSignal(child) => builder.signal(child.get_signal()),
                HwsimAttrCookie(child) => builder.cookie(child.get_cookie()),
                HwsimAttrFreq(child) => builder.freq(child.get_freq()),
                HwsimAttrTxInfo(child) => builder.tx_info(child.get_tx_rates()),
                HwsimAttrTxInfoFlags(child) => builder.tx_info_flags(child.get_tx_rate_flags()),
                _ => {
                    return Err(anyhow!(
                        "Invalid attribute message: {:?}",
                        hwsim_attr.get_nla_type() as u32
                    ))
                }
            };
            // Manually step through the attribute bytes aligning as
            // we go because netlink aligns each attribute which isn't
            // a feature of PDL parser.
            index += nla_align(nla_len);
        }
        builder.build()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // Validate `HwsimAttrSet` attribute parsing from byte vector.
    #[test]
    fn test_attr_set_parse() {
        let packet: Vec<u8> = include!("test_packets/hwsim_cmd_frame.csv");
        let hwsim_msg = HwsimMsg::parse(&packet).unwrap();
        assert_eq!(hwsim_msg.get_hwsim_hdr().hwsim_cmd, HwsimCmd::Frame);
        let attrs = HwsimAttrSet::parse(hwsim_msg.get_attributes()).unwrap();

        // Validate each attribute parsed
        assert_eq!(attrs.transmitter, MacAddress::try_from(11670786u64).ok());
        assert!(attrs.receiver.is_none());
        assert!(attrs.frame.is_some());
        assert_eq!(attrs.flags, Some(2));
        assert!(attrs.rx_rate_idx.is_none());
        assert!(attrs.signal.is_none());
        assert_eq!(attrs.cookie, Some(201));
        assert_eq!(attrs.freq, Some(2422));
        assert!(attrs.tx_info.is_some());
    }

    // Validate the contents of the `attributes` bytes constructed by
    // the Builder by matching with the bytes containing the input
    // attributes. Confirms attribute order, packet format and
    // padding.
    #[test]
    fn test_attr_set_attributes() {
        let packet: Vec<u8> = include!("test_packets/hwsim_cmd_frame.csv");
        let hwsim_msg = HwsimMsg::parse(&packet).unwrap();
        assert_eq!(hwsim_msg.get_hwsim_hdr().hwsim_cmd, HwsimCmd::Frame);
        let attrs = HwsimAttrSet::parse(hwsim_msg.get_attributes()).unwrap();
        assert_eq!(&attrs.attributes, hwsim_msg.get_attributes());
    }

    #[test]
    fn test_hwsim_attr_set_display() {
        let packet: Vec<u8> = include!("test_packets/hwsim_cmd_frame.csv");
        let hwsim_msg = HwsimMsg::parse(&packet).unwrap();
        let attrs = HwsimAttrSet::parse(hwsim_msg.get_attributes()).unwrap();

        let fmt_attrs = format!("{}", attrs);
        assert!(fmt_attrs.contains("transmitter: 02:15:b2:00:00:00"));
        assert!(fmt_attrs.contains("cookie: 201"));
    }
}
