///! Rule group for tracking command collision issues.
use chrono::NaiveDateTime;
use std::convert::Into;
use std::io::Write;

use crate::engine::{Rule, RuleGroup, Signal};
use crate::parser::{Packet, PacketChild};
use bt_packets::hci::{ErrorCode, EventChild, OpCode};

enum CollisionSignal {
    RnrAndInquiry,
    RnrAndConnection,
    InquiryAndConnection,
    InquirySelf,
}

impl Into<&'static str> for CollisionSignal {
    fn into(self) -> &'static str {
        match self {
            CollisionSignal::RnrAndInquiry => "RnR-Inquiry-Collision",
            CollisionSignal::RnrAndConnection => "RnR-Connection-Collision",
            CollisionSignal::InquiryAndConnection => "Inquiry-Connection-Collision",
            CollisionSignal::InquirySelf => "Inquiry-Self-Collision",
        }
    }
}

// What state are we in for the serializable state?
#[derive(Debug, PartialEq)]
enum CollisionState {
    Nothing,
    ConnectionActive,
    RemoteNameReqActive,
    InquiryActive,
}

/// This rule keeps track of collisions that occur due to serializable commands
/// not being serialized correctly: Create Connection, Remote Name Request and Inquiry.
struct ConnectionSerializationRule {
    /// What's the current state of the serializable commands?
    state: CollisionState,

    /// When was the last state set? (Except Nothing)
    state_set_at: Option<(usize, NaiveDateTime)>,

    /// Pre-defined signals discovered in the logs.
    signals: Vec<Signal>,

    /// Interesting occurrences surfaced by this rule.
    reportable: Vec<(NaiveDateTime, String)>,
}

impl ConnectionSerializationRule {
    pub fn new() -> Self {
        ConnectionSerializationRule {
            state: CollisionState::Nothing,
            state_set_at: None,
            signals: vec![],
            reportable: vec![],
        }
    }

    // Determine signal to emit based on the currently active state.
    pub(crate) fn get_signal_type(&self, opcode: &OpCode) -> Option<CollisionSignal> {
        match (opcode, &self.state) {
            (&OpCode::CreateConnection, &CollisionState::InquiryActive) => {
                Some(CollisionSignal::InquiryAndConnection)
            }

            (&OpCode::CreateConnection, &CollisionState::RemoteNameReqActive) => {
                Some(CollisionSignal::RnrAndConnection)
            }

            (&OpCode::Inquiry, &CollisionState::InquiryActive) => {
                Some(CollisionSignal::InquirySelf)
            }

            (&OpCode::Inquiry, &CollisionState::RemoteNameReqActive) => {
                Some(CollisionSignal::RnrAndInquiry)
            }

            (&OpCode::Inquiry, &CollisionState::ConnectionActive) => {
                Some(CollisionSignal::InquiryAndConnection)
            }

            (&OpCode::RemoteNameRequest, &CollisionState::InquiryActive) => {
                Some(CollisionSignal::RnrAndInquiry)
            }

            (&OpCode::RemoteNameRequest, &CollisionState::ConnectionActive) => {
                Some(CollisionSignal::RnrAndConnection)
            }

            (_, _) => None,
        }
    }
}

impl Rule for ConnectionSerializationRule {
    fn process(&mut self, packet: &Packet) {
        match &packet.inner {
            PacketChild::HciEvent(ev) => match ev.specialize() {
                // Most of the serializable commands will get "Disallowed" on
                // command status except `Remote Name Req`.
                EventChild::CommandStatus(cs) => match cs.get_command_op_code() {
                    OpCode::CreateConnection | OpCode::Inquiry | OpCode::RemoteNameRequest => {
                        // Set the state to the new successful command if it was
                        // previously not set.
                        if cs.get_status() == ErrorCode::Success
                            && self.state == CollisionState::Nothing
                        {
                            let new_state: Option<CollisionState> = match cs.get_command_op_code() {
                                OpCode::CreateConnection => Some(CollisionState::ConnectionActive),
                                OpCode::Inquiry => Some(CollisionState::InquiryActive),
                                OpCode::RemoteNameRequest => {
                                    Some(CollisionState::RemoteNameReqActive)
                                }
                                _ => None,
                            };

                            if let Some(new_state) = new_state {
                                self.state = new_state;
                                self.state_set_at = Some((packet.index, packet.ts.clone()));
                            }
                        }
                        // We've hit a disallowed status. Check if we're
                        // conflicting with something that should be serializable.
                        else if cs.get_status() == ErrorCode::CommandDisallowed {
                            self.reportable.push((
                                packet.ts,
                                format!("Command Status was 'Disallowed' on {:?}. Potential conflict with: {:?} at {:?}",
                                    cs.get_command_op_code(), self.state, self.state_set_at)
                            ));

                            let signal: Option<CollisionSignal> =
                                self.get_signal_type(&cs.get_command_op_code());

                            if let Some(signal) = signal {
                                self.signals.push(Signal {
                                    index: packet.index,
                                    ts: packet.ts.clone(),
                                    tag: signal.into(),
                                });
                            }
                        }
                    }

                    _ => (),
                },

                // RnR will only tell you "Disallowed" on the event itself.
                // However, we should handle this in CommandStatus as well in case
                // this is a controller quirk.
                EventChild::RemoteNameRequestComplete(rnr_ev) => {
                    // Everything except "Disallowed" resets the state.
                    if rnr_ev.get_status() != ErrorCode::CommandDisallowed
                        && self.state == CollisionState::RemoteNameReqActive
                    {
                        self.state = CollisionState::Nothing;
                        self.state_set_at = None;
                    } else if rnr_ev.get_status() == ErrorCode::CommandDisallowed {
                        self.reportable.push((
                                packet.ts,
                                format!("Remote name req complete with disallowed. Potential conflict with: {:?} at {:?}",
                                    self.state, self.state_set_at)

                        ));

                        // Insert signals based on current serializable state.
                        let signal = self.get_signal_type(&OpCode::RemoteNameRequest);

                        if let Some(signal) = signal {
                            self.signals.push(Signal {
                                index: packet.index,
                                ts: packet.ts.clone(),
                                tag: signal.into(),
                            });
                        }
                    }
                }

                EventChild::InquiryComplete(_) => {
                    // Inquiry was observed to be disallowed only on |CommandStatus|
                    // so always clear state here.
                    if self.state == CollisionState::InquiryActive {
                        self.state = CollisionState::Nothing;
                        self.state_set_at = None;
                    }
                }

                EventChild::ConnectionComplete(_) => {
                    // Connection was observed to be disallowed only on |CommandStatus|
                    // so always clear state here.
                    if self.state == CollisionState::ConnectionActive {
                        self.state = CollisionState::Nothing;
                        self.state_set_at = None;
                    }
                }

                _ => {}
            },

            _ => {}
        }
    }

    fn report(&self, writer: &mut dyn Write) {
        if self.reportable.len() > 0 {
            let _ = writeln!(writer, "ConnectionSerializationRule report:");
            for (ts, message) in self.reportable.iter() {
                let _ = writeln!(writer, "[{:?}] {}", ts, message);
            }
        }
    }

    fn report_signals(&self) -> &[Signal] {
        self.signals.as_slice()
    }
}

/// Get a rule group with collision rules.
pub fn get_collisions_group() -> RuleGroup {
    let mut group = RuleGroup::new();
    group.add_rule(Box::new(ConnectionSerializationRule::new()));

    group
}
