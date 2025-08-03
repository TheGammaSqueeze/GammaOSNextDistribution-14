///! Rule group for tracking controller related issues.
use chrono::NaiveDateTime;
use lazy_static::lazy_static;
use std::collections::HashSet;
use std::convert::Into;
use std::io::Write;

use crate::engine::{Rule, RuleGroup, Signal};
use crate::parser::{NewIndex, Packet, PacketChild};
use bt_packets::hci::{CommandCompleteChild, ErrorCode, EventChild, LocalVersionInformation};

enum ControllerSignal {
    HardwareError,            // Controller reports HCI event: Hardware Error
    LikelyExternalController, // Controller is not in the known list. Likely to be an external controller.
}

impl Into<&'static str> for ControllerSignal {
    fn into(self) -> &'static str {
        match self {
            ControllerSignal::HardwareError => "HardwareError",
            ControllerSignal::LikelyExternalController => "LikelyExternalController",
        }
    }
}

lazy_static! {
    static ref KNOWN_CONTROLLER_NAMES: [String; 6] = [
        String::from("Bluemoon Universal Bluetooth Host Controller"),    // AC7625
        String::from("MTK MT7961 #1"),    // MT7921LE/MT7921LS
        String::from("MTK MT7922 #1"),    // MT7922
        String::from("RTK_BT_5.0"),       // RTL8822CE
        String::from("RT_BT"),            // RTL8852AE
        String::from(""),                 // AC9260/AC9560/AX200/AX201/AX203/AX211/MVL8897/QCA6174A3/QCA6174A5/QC_WCN6856
    ];
}
const KNOWN_CONTROLLER_MANUFACTURERS: [u16; 5] = [
    2,  // Intel.
    29, // Qualcomm
    70, // MediaTek
    72, // Marvell
    93, // Realtek
];

struct ControllerRule {
    /// Pre-defined signals discovered in the logs.
    signals: Vec<Signal>,

    /// Interesting occurrences surfaced by this rule.
    reportable: Vec<(NaiveDateTime, String)>,

    /// All detected open_index.
    controllers: HashSet<String>,
}

impl ControllerRule {
    pub fn new() -> Self {
        ControllerRule { signals: vec![], reportable: vec![], controllers: HashSet::new() }
    }

    pub fn report_hardware_error(&mut self, packet: &Packet) {
        self.signals.push(Signal {
            index: packet.index,
            ts: packet.ts.clone(),
            tag: ControllerSignal::HardwareError.into(),
        });

        self.reportable.push((packet.ts, format!("controller reported hardware error")));
    }

    fn process_local_name(&mut self, local_name: &[u8; 248], packet: &Packet) {
        let null_index = local_name.iter().position(|&b| b == 0).unwrap_or(local_name.len());
        match String::from_utf8(local_name[..null_index].to_vec()) {
            Ok(name) => {
                if !KNOWN_CONTROLLER_NAMES.contains(&name) {
                    self.signals.push(Signal {
                        index: packet.index,
                        ts: packet.ts,
                        tag: ControllerSignal::LikelyExternalController.into(),
                    })
                }
            }
            Err(_) => self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ControllerSignal::LikelyExternalController.into(),
            }),
        }
    }

    fn process_local_version(&mut self, version_info: &LocalVersionInformation, packet: &Packet) {
        if !KNOWN_CONTROLLER_MANUFACTURERS.contains(&version_info.manufacturer_name) {
            self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ControllerSignal::LikelyExternalController.into(),
            })
        }
    }

    fn process_new_index(&mut self, new_index: &NewIndex, packet: &Packet) {
        self.controllers.insert(new_index.get_addr_str());

        if self.controllers.len() > 1 {
            self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ControllerSignal::LikelyExternalController.into(),
            });
        }
    }
}

impl Rule for ControllerRule {
    fn process(&mut self, packet: &Packet) {
        match &packet.inner {
            PacketChild::HciEvent(ev) => match ev.specialize() {
                EventChild::HardwareError(_ev) => {
                    self.report_hardware_error(&packet);
                }
                EventChild::CommandComplete(ev) => match ev.specialize() {
                    CommandCompleteChild::ReadLocalNameComplete(ev) => {
                        if ev.get_status() != ErrorCode::Success {
                            return;
                        }

                        self.process_local_name(ev.get_local_name(), &packet);
                    }
                    CommandCompleteChild::ReadLocalVersionInformationComplete(ev) => {
                        if ev.get_status() != ErrorCode::Success {
                            return;
                        }

                        self.process_local_version(ev.get_local_version_information(), &packet);
                    }
                    _ => {}
                },
                _ => {}
            },
            PacketChild::NewIndex(ni) => {
                self.process_new_index(ni, &packet);
            }
            _ => {}
        }
    }

    fn report(&self, writer: &mut dyn Write) {
        if self.reportable.len() > 0 {
            let _ = writeln!(writer, "Controller report:");
            for (ts, message) in self.reportable.iter() {
                let _ = writeln!(writer, "[{:?}] {}", ts, message);
            }
        }
    }

    fn report_signals(&self) -> &[Signal] {
        self.signals.as_slice()
    }
}

/// Get a rule group with connection rules.
pub fn get_controllers_group() -> RuleGroup {
    let mut group = RuleGroup::new();
    group.add_rule(Box::new(ControllerRule::new()));

    group
}
