///! Rule group for tracking connection related issues.
use chrono::NaiveDateTime;
use std::collections::{HashMap, HashSet, VecDeque};
use std::convert::Into;
use std::io::Write;
use std::slice::Iter;

use crate::engine::{Rule, RuleGroup, Signal};
use crate::parser::{Packet, PacketChild};
use bt_packets::hci::{
    Acl, AclCommandChild, Address, AuthenticatedPayloadTimeoutExpired, CommandChild,
    ConnectionManagementCommandChild, DisconnectReason, Enable, ErrorCode, EventChild,
    InitiatorFilterPolicy, LeConnectionManagementCommandChild, LeMetaEventChild,
    LeSecurityCommandChild, NumberOfCompletedPackets, OpCode, ScoConnectionCommandChild,
    SecurityCommandChild,
};

enum ConnectionSignal {
    LinkKeyMismatch,     // Peer forgets the link key or it mismatches ours. (b/284802375)
    LongTermKeyMismatch, // Like LinkKeyMismatch but for LE cases. (b/303600602)
    NocpDisconnect,      // Peer is disconnected when NOCP packet isn't yet received. (b/249295604)
    NocpTimeout, // Host doesn't receive NOCP packet 5 seconds after ACL is sent. (b/249295604)
    ApteDisconnect, // Host doesn't receive a packet with valid MIC for a while. (b/299850738)
    RemoteFeatureNoReply, // Host doesn't receive a response for a remote feature request. (b/300851411)
    RemoteFeatureError,   // Controller replies error for remote feature request. (b/292116133)
    SecurityMode3,        // Peer uses the unsupported legacy security mode 3. (b/260625799)
}

impl Into<&'static str> for ConnectionSignal {
    fn into(self) -> &'static str {
        match self {
            ConnectionSignal::LinkKeyMismatch => "LinkKeyMismatch",
            ConnectionSignal::LongTermKeyMismatch => "LongTermKeyMismatch",
            ConnectionSignal::NocpDisconnect => "Nocp",
            ConnectionSignal::NocpTimeout => "Nocp",
            ConnectionSignal::ApteDisconnect => "AuthenticatedPayloadTimeoutExpired",
            ConnectionSignal::RemoteFeatureError => "RemoteFeatureError",
            ConnectionSignal::RemoteFeatureNoReply => "RemoteFeatureNoReply",
            ConnectionSignal::SecurityMode3 => "UnsupportedSecurityMode3",
        }
    }
}

/// Valid values are in the range 0x0000-0x0EFF.
pub type ConnectionHandle = u16;

/// When we attempt to create a sco connection on an unknown handle, use this address as
/// a placeholder.
pub const UNKNOWN_SCO_ADDRESS: [u8; 6] = [0xdeu8, 0xad, 0xbe, 0xef, 0x00, 0x00];

/// The tolerance duration of not receiving an expected reply. If 5s elapsed and timeout occurs,
/// we blame the pending event for causing timeout. This is used to detect NOCP and others.
pub const TIMEOUT_TOLERANCE_TIME_MS: i64 = 5000;

pub(crate) struct NocpData {
    /// Number of in-flight packets without a corresponding NOCP.
    pub inflight_acl_ts: VecDeque<NaiveDateTime>,
}

impl NocpData {
    fn new() -> Self {
        Self { inflight_acl_ts: VecDeque::new() }
    }
}

#[derive(Debug, Eq, PartialEq, Hash)]
enum PendingRemoteFeature {
    Supported,
    Extended,
    Le,
}

impl PendingRemoteFeature {
    fn iterate_all() -> Iter<'static, PendingRemoteFeature> {
        static FEATS: [PendingRemoteFeature; 3] = [
            PendingRemoteFeature::Supported,
            PendingRemoteFeature::Extended,
            PendingRemoteFeature::Le,
        ];
        FEATS.iter()
    }
}

/// Keeps track of connections and identifies odd disconnections.
struct OddDisconnectionsRule {
    /// Handles that had successful complete connections. The value has the timestamp of the
    /// connection completion and the address of the device.
    active_handles: HashMap<ConnectionHandle, (NaiveDateTime, Address)>,

    connection_attempt: HashMap<Address, Packet>,
    last_connection_attempt: Option<Address>,

    le_connection_attempt: HashMap<Address, Packet>,
    last_le_connection_attempt: Option<Address>,
    last_le_connection_filter_policy: Option<InitiatorFilterPolicy>,

    sco_connection_attempt: HashMap<Address, Packet>,
    last_sco_connection_attempt: Option<Address>,

    accept_list: HashSet<Address>,

    /// Keep track of some number of |Number of Completed Packets| and filter to
    /// identify bursts.
    nocp_by_handle: HashMap<ConnectionHandle, NocpData>,

    /// Number of |Authenticated Payload Timeout Expired| events hapened.
    apte_by_handle: HashMap<ConnectionHandle, u32>,

    /// Pending handles for read remote features.
    pending_supported_feat: HashMap<ConnectionHandle, NaiveDateTime>,
    pending_extended_feat: HashMap<ConnectionHandle, NaiveDateTime>,
    pending_le_feat: HashMap<ConnectionHandle, NaiveDateTime>,
    last_feat_handle: HashMap<PendingRemoteFeature, ConnectionHandle>,

    /// When powering off, the controller might or might not reply disconnection request. Therefore
    /// make this a special case.
    pending_disconnect_due_to_host_power_off: HashSet<ConnectionHandle>,

    /// Pre-defined signals discovered in the logs.
    signals: Vec<Signal>,

    /// Interesting occurrences surfaced by this rule.
    reportable: Vec<(NaiveDateTime, String)>,
}

impl OddDisconnectionsRule {
    pub fn new() -> Self {
        OddDisconnectionsRule {
            active_handles: HashMap::new(),
            connection_attempt: HashMap::new(),
            last_connection_attempt: None,
            le_connection_attempt: HashMap::new(),
            last_le_connection_attempt: None,
            last_le_connection_filter_policy: None,
            sco_connection_attempt: HashMap::new(),
            last_sco_connection_attempt: None,
            accept_list: HashSet::new(),
            nocp_by_handle: HashMap::new(),
            apte_by_handle: HashMap::new(),
            pending_supported_feat: HashMap::new(),
            pending_extended_feat: HashMap::new(),
            pending_le_feat: HashMap::new(),
            last_feat_handle: HashMap::new(),
            pending_disconnect_due_to_host_power_off: HashSet::new(),
            signals: vec![],
            reportable: vec![],
        }
    }

    fn process_classic_connection(&mut self, address: Address, packet: &Packet) {
        self.last_connection_attempt = Some(address);
        if let Some(p) = self.connection_attempt.insert(address, packet.clone()) {
            self.reportable.push((
                p.ts,
                format!("Dangling connection attempt at {:?} replaced with {:?}", p, packet),
            ));
        }
    }

    fn convert_sco_handle_to_address(&self, handle: ConnectionHandle) -> Address {
        match self.active_handles.get(&handle).as_ref() {
            Some((_ts, address)) => address.clone(),
            None => Address::from(&UNKNOWN_SCO_ADDRESS),
        }
    }

    fn process_sync_connection(&mut self, address: Address, packet: &Packet) {
        self.last_sco_connection_attempt = Some(address);
        if let Some(p) = self.sco_connection_attempt.insert(address, packet.clone()) {
            self.reportable.push((
                p.ts,
                format!("Dangling sco connection attempt at {:?} replaced with {:?}", p, packet),
            ));
        }
    }

    fn process_le_create_connection(
        &mut self,
        address: Address,
        policy: InitiatorFilterPolicy,
        packet: &Packet,
    ) {
        self.last_le_connection_attempt = Some(address);
        self.last_le_connection_filter_policy = Some(policy);
        if let Some(p) = self.le_connection_attempt.insert(address, packet.clone()) {
            self.reportable.push((
                p.ts,
                format!("Dangling LE connection attempt at {:?} replaced with {:?}", p, packet),
            ));
        }
    }

    fn process_add_accept_list(&mut self, address: Address, _packet: &Packet) {
        self.accept_list.insert(address);
    }

    fn process_remove_accept_list(&mut self, address: Address, _packet: &Packet) {
        self.accept_list.remove(&address);
    }

    fn process_clear_accept_list(&mut self, _packet: &Packet) {
        self.accept_list.clear();
    }

    fn get_feature_pending_map(
        &mut self,
        pending_type: &PendingRemoteFeature,
    ) -> &mut HashMap<ConnectionHandle, NaiveDateTime> {
        match pending_type {
            PendingRemoteFeature::Supported => &mut self.pending_supported_feat,
            PendingRemoteFeature::Extended => &mut self.pending_extended_feat,
            PendingRemoteFeature::Le => &mut self.pending_le_feat,
        }
    }

    fn process_remote_feat_cmd(
        &mut self,
        feat_type: PendingRemoteFeature,
        handle: &ConnectionHandle,
        packet: &Packet,
    ) {
        self.get_feature_pending_map(&feat_type).insert(*handle, packet.ts);
        self.last_feat_handle.insert(feat_type, *handle);
    }

    fn process_disconnect_cmd(
        &mut self,
        reason: DisconnectReason,
        handle: ConnectionHandle,
        packet: &Packet,
    ) {
        // If reason is power off, the host might not wait for connection complete event
        if reason == DisconnectReason::RemoteDeviceTerminatedConnectionPowerOff {
            self.process_disconn_complete_ev(handle, packet);
            self.pending_disconnect_due_to_host_power_off.insert(handle);
        }
    }

    fn process_command_status(&mut self, status: ErrorCode, opcode: OpCode, packet: &Packet) {
        match opcode {
            OpCode::CreateConnection
            | OpCode::AcceptConnectionRequest
            | OpCode::SetupSynchronousConnection
            | OpCode::AcceptSynchronousConnection
            | OpCode::EnhancedSetupSynchronousConnection
            | OpCode::EnhancedAcceptSynchronousConnection
            | OpCode::LeCreateConnection
            | OpCode::LeExtendedCreateConnection => {
                self.process_command_status_conn(status, opcode, packet);
            }

            OpCode::ReadRemoteSupportedFeatures
            | OpCode::ReadRemoteExtendedFeatures
            | OpCode::LeReadRemoteFeatures => {
                self.process_command_status_feat(status, opcode, packet);
            }

            _ => {}
        }
    }

    fn process_command_status_conn(&mut self, status: ErrorCode, opcode: OpCode, packet: &Packet) {
        // Clear last connection attempt since it was successful.
        let last_address = match opcode {
            OpCode::CreateConnection | OpCode::AcceptConnectionRequest => {
                self.last_connection_attempt.take()
            }

            OpCode::SetupSynchronousConnection
            | OpCode::AcceptSynchronousConnection
            | OpCode::EnhancedSetupSynchronousConnection
            | OpCode::EnhancedAcceptSynchronousConnection => {
                self.last_sco_connection_attempt.take()
            }

            OpCode::LeCreateConnection | OpCode::LeExtendedCreateConnection => {
                self.last_le_connection_attempt.take()
            }

            _ => return,
        };

        if let Some(address) = last_address {
            if status != ErrorCode::Success {
                self.reportable.push((
                    packet.ts,
                    format!("Failing command status on [{}]: {:?}", address, opcode),
                ));

                // Also remove the connection attempt.
                match opcode {
                    OpCode::CreateConnection | OpCode::AcceptConnectionRequest => {
                        self.connection_attempt.remove(&address);
                    }

                    OpCode::SetupSynchronousConnection
                    | OpCode::AcceptSynchronousConnection
                    | OpCode::EnhancedSetupSynchronousConnection
                    | OpCode::EnhancedAcceptSynchronousConnection => {
                        self.sco_connection_attempt.remove(&address);
                    }

                    OpCode::LeCreateConnection | OpCode::LeExtendedCreateConnection => {
                        self.le_connection_attempt.remove(&address);
                        self.last_le_connection_filter_policy = None;
                    }

                    _ => (),
                }
            }
        } else {
            if status != ErrorCode::Success {
                self.reportable.push((
                    packet.ts,
                    format!("Failing command status on unknown address: {:?}", opcode),
                ));
            }
        }
    }

    fn process_command_status_feat(&mut self, status: ErrorCode, opcode: OpCode, packet: &Packet) {
        let feat_type = match opcode {
            OpCode::ReadRemoteSupportedFeatures => PendingRemoteFeature::Supported,
            OpCode::ReadRemoteExtendedFeatures => PendingRemoteFeature::Extended,
            OpCode::LeReadRemoteFeatures => PendingRemoteFeature::Le,
            _ => return,
        };

        // If handle is not known, probably the request comes before btsnoop starts. In any case,
        // the command complete event will still complain. So let's just return here.
        let handle = match self.last_feat_handle.remove(&feat_type) {
            Some(handle) => handle,
            None => return,
        };

        // If failed, there won't be a following command complete event. So treat this as the
        // command complete.
        if status != ErrorCode::Success {
            self.process_remote_feat_ev(feat_type, status, handle, packet);
        }
    }

    fn process_conn_complete_ev(
        &mut self,
        status: ErrorCode,
        handle: ConnectionHandle,
        address: Address,
        packet: &Packet,
    ) {
        if let Some(_) = self.connection_attempt.remove(&address) {
            if status == ErrorCode::Success {
                self.active_handles.insert(handle, (packet.ts, address));
                self.pending_disconnect_due_to_host_power_off.remove(&handle);
            } else {
                self.reportable.push((
                    packet.ts,
                    format!(
                        "ConnectionComplete error {:?} for addr {} (handle={})",
                        status, address, handle
                    ),
                ));
            }
        } else {
            self.reportable.push((
                packet.ts,
                format!(
                    "ConnectionComplete with status {:?} for unknown addr {} (handle={})",
                    status, address, handle
                ),
            ));
        }
    }

    fn process_disconn_complete_ev(&mut self, handle: ConnectionHandle, packet: &Packet) {
        // If previously host send disconnect with power off reason, disconnection has been handled.
        if self.pending_disconnect_due_to_host_power_off.remove(&handle) {
            return;
        }

        self.active_handles.remove(&handle);

        // Check if this is a NOCP type disconnection and flag it.
        if let Some(nocp_data) = self.nocp_by_handle.get_mut(&handle) {
            if let Some(acl_front_ts) = nocp_data.inflight_acl_ts.pop_front() {
                let duration_since_acl = packet.ts.signed_duration_since(acl_front_ts);
                if duration_since_acl.num_milliseconds() > TIMEOUT_TOLERANCE_TIME_MS {
                    self.signals.push(Signal {
                        index: packet.index,
                        ts: packet.ts,
                        tag: ConnectionSignal::NocpDisconnect.into(),
                    });

                    self.reportable.push((
                        packet.ts,
                        format!("DisconnectionComplete for handle({}) showed incomplete in-flight ACL at {}",
                        handle, acl_front_ts)));
                }
            }
        }
        // Remove nocp information for handles that were removed.
        self.nocp_by_handle.remove(&handle);

        // Check if auth payload timeout happened.
        if let Some(apte_count) = self.apte_by_handle.remove(&handle) {
            self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ConnectionSignal::ApteDisconnect.into(),
            });

            self.reportable.push((
                packet.ts,
                format!("DisconnectionComplete with {} Authenticated Payload Timeout Expired (handle={})",
                apte_count, handle))
            );
        }

        // Check if remote feature request is pending
        for feat_type in PendingRemoteFeature::iterate_all() {
            if let Some(ts) = self.get_feature_pending_map(feat_type).remove(&handle) {
                let elapsed_time_ms = packet.ts.signed_duration_since(ts).num_milliseconds();
                if elapsed_time_ms > TIMEOUT_TOLERANCE_TIME_MS {
                    self.signals.push(Signal {
                        index: packet.index,
                        ts: packet.ts,
                        tag: ConnectionSignal::RemoteFeatureNoReply.into(),
                    });

                    self.reportable.push((
                        packet.ts,
                        format!(
                            "Handle {} doesn't respond to {:?} feature request at {}.",
                            handle,
                            feat_type,
                            ts.time()
                        ),
                    ));
                }
            }
        }
    }

    fn process_sync_conn_complete_ev(
        &mut self,
        status: ErrorCode,
        handle: ConnectionHandle,
        address: Address,
        packet: &Packet,
    ) {
        if let Some(_) = self.sco_connection_attempt.remove(&address) {
            if status == ErrorCode::Success {
                self.active_handles.insert(handle, (packet.ts, address));
                self.pending_disconnect_due_to_host_power_off.remove(&handle);
            } else {
                self.reportable.push((
                    packet.ts,
                    format!(
                        "SynchronousConnectionComplete error {:?} for addr {} (handle={})",
                        status, address, handle
                    ),
                ));
            }
        } else {
            self.reportable.push((
                packet.ts,
                format!(
                    "SynchronousConnectionComplete with status {:?} for unknown addr {} (handle={})",
                    status,
                    address,
                    handle
                ),
            ));
        }
    }

    fn process_le_conn_complete_ev(
        &mut self,
        status: ErrorCode,
        handle: ConnectionHandle,
        address: Address,
        packet: &Packet,
    ) {
        let use_accept_list = self
            .last_le_connection_filter_policy
            .map_or(false, |policy| policy == InitiatorFilterPolicy::UseFilterAcceptList);
        let addr_to_remove = if use_accept_list { bt_packets::hci::EMPTY_ADDRESS } else { address };

        if let Some(_) = self.le_connection_attempt.remove(&addr_to_remove) {
            if status == ErrorCode::Success {
                self.active_handles.insert(handle, (packet.ts, address));
                self.pending_disconnect_due_to_host_power_off.remove(&handle);
            } else {
                let message = if use_accept_list {
                    format!("LeConnectionComplete error {:?} for accept list", status)
                } else {
                    format!(
                        "LeConnectionComplete error {:?} for addr {} (handle={})",
                        status, address, handle
                    )
                };
                self.reportable.push((packet.ts, message));
            }
        } else {
            self.reportable.push((
                packet.ts,
                format!(
                    "LeConnectionComplete with status {:?} for unknown addr {} (handle={})",
                    status, address, handle
                ),
            ));
        }
    }

    fn process_acl_tx(&mut self, acl_tx: &Acl, packet: &Packet) {
        let handle = acl_tx.get_handle();

        // Insert empty Nocp data for handle if it doesn't exist.
        if !self.nocp_by_handle.contains_key(&handle) {
            self.nocp_by_handle.insert(handle, NocpData::new());
        }

        if let Some(nocp_data) = self.nocp_by_handle.get_mut(&handle) {
            nocp_data.inflight_acl_ts.push_back(packet.ts);
        }
    }

    fn process_nocp(&mut self, nocp: &NumberOfCompletedPackets, packet: &Packet) {
        let ts = &packet.ts;
        for completed_packet in nocp.get_completed_packets() {
            let handle = completed_packet.connection_handle;
            let num = completed_packet.host_num_of_completed_packets;
            if !self.nocp_by_handle.contains_key(&handle) {
                self.nocp_by_handle.insert(handle, NocpData::new());
            }

            if let Some(nocp_data) = self.nocp_by_handle.get_mut(&handle) {
                for _i in 0..num {
                    if let Some(acl_front_ts) = nocp_data.inflight_acl_ts.pop_front() {
                        let duration_since_acl = ts.signed_duration_since(acl_front_ts);
                        if duration_since_acl.num_milliseconds() > TIMEOUT_TOLERANCE_TIME_MS {
                            self.signals.push(Signal {
                                index: packet.index,
                                ts: packet.ts,
                                tag: ConnectionSignal::NocpTimeout.into(),
                            });
                            self.reportable.push((
                                packet.ts,
                                format!(
                                    "Nocp sent {} ms after ACL on handle({}).",
                                    duration_since_acl.num_milliseconds(),
                                    handle
                                ),
                            ));
                        }
                    }
                }
            }
        }
    }

    fn process_apte(&mut self, apte: &AuthenticatedPayloadTimeoutExpired, _packet: &Packet) {
        let handle = apte.get_connection_handle();
        *self.apte_by_handle.entry(handle).or_insert(0) += 1;
    }

    fn process_remote_feat_ev(
        &mut self,
        feat_type: PendingRemoteFeature,
        status: ErrorCode,
        handle: ConnectionHandle,
        packet: &Packet,
    ) {
        let feat_map = self.get_feature_pending_map(&feat_type);

        if feat_map.remove(&handle) == None {
            self.reportable.push((
                packet.ts,
                format!("Got remote {:?} for unknown handle {}", feat_type, handle),
            ));
        }

        if status != ErrorCode::Success {
            self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ConnectionSignal::RemoteFeatureError.into(),
            });

            self.reportable.push((
                packet.ts,
                format!("Got {:?} for remote {:?} feature, handle {}", status, feat_type, handle),
            ));
        }
    }

    fn process_reset(&mut self) {
        self.active_handles.clear();
        self.connection_attempt.clear();
        self.last_connection_attempt = None;
        self.le_connection_attempt.clear();
        self.last_le_connection_attempt = None;
        self.last_le_connection_filter_policy = None;
        self.sco_connection_attempt.clear();
        self.last_sco_connection_attempt = None;
        self.accept_list.clear();
        self.nocp_by_handle.clear();
        self.apte_by_handle.clear();
        self.pending_supported_feat.clear();
        self.pending_extended_feat.clear();
        self.pending_le_feat.clear();
        self.last_feat_handle.clear();
        self.pending_disconnect_due_to_host_power_off.clear();
    }
}

impl Rule for OddDisconnectionsRule {
    fn process(&mut self, packet: &Packet) {
        match &packet.inner {
            PacketChild::HciCommand(cmd) => match cmd.specialize() {
                CommandChild::AclCommand(aclpkt) => match aclpkt.specialize() {
                    AclCommandChild::ConnectionManagementCommand(conn) => match conn.specialize() {
                        ConnectionManagementCommandChild::CreateConnection(cc) => {
                            self.process_classic_connection(cc.get_bd_addr(), packet);
                        }
                        ConnectionManagementCommandChild::AcceptConnectionRequest(ac) => {
                            self.process_classic_connection(ac.get_bd_addr(), packet);
                        }
                        ConnectionManagementCommandChild::ReadRemoteSupportedFeatures(rrsf) => {
                            self.process_remote_feat_cmd(
                                PendingRemoteFeature::Supported,
                                &rrsf.get_connection_handle(),
                                packet,
                            );
                        }
                        ConnectionManagementCommandChild::ReadRemoteExtendedFeatures(rref) => {
                            self.process_remote_feat_cmd(
                                PendingRemoteFeature::Extended,
                                &rref.get_connection_handle(),
                                packet,
                            );
                        }
                        // End ConnectionManagementCommand.specialize()
                        _ => {}
                    },
                    AclCommandChild::ScoConnectionCommand(sco_con) => match sco_con.specialize() {
                        ScoConnectionCommandChild::SetupSynchronousConnection(ssc) => {
                            let address =
                                self.convert_sco_handle_to_address(ssc.get_connection_handle());
                            self.process_sync_connection(address, packet);
                        }
                        ScoConnectionCommandChild::EnhancedSetupSynchronousConnection(esc) => {
                            let address =
                                self.convert_sco_handle_to_address(esc.get_connection_handle());
                            self.process_sync_connection(address, packet);
                        }
                        ScoConnectionCommandChild::AcceptSynchronousConnection(asc) => {
                            self.process_sync_connection(asc.get_bd_addr(), packet);
                        }
                        ScoConnectionCommandChild::EnhancedAcceptSynchronousConnection(easc) => {
                            self.process_sync_connection(easc.get_bd_addr(), packet);
                        }
                        // End ScoConnectionCommand.specialize()
                        _ => {}
                    },
                    AclCommandChild::LeConnectionManagementCommand(le_conn) => match le_conn
                        .specialize()
                    {
                        LeConnectionManagementCommandChild::LeCreateConnection(lcc) => {
                            self.process_le_create_connection(
                                lcc.get_peer_address(),
                                lcc.get_initiator_filter_policy(),
                                packet,
                            );
                        }
                        LeConnectionManagementCommandChild::LeExtendedCreateConnection(lecc) => {
                            self.process_le_create_connection(
                                lecc.get_peer_address(),
                                lecc.get_initiator_filter_policy(),
                                packet,
                            );
                        }
                        LeConnectionManagementCommandChild::LeAddDeviceToFilterAcceptList(laac) => {
                            self.process_add_accept_list(laac.get_address(), packet);
                        }
                        LeConnectionManagementCommandChild::LeRemoveDeviceFromFilterAcceptList(
                            lrac,
                        ) => {
                            self.process_remove_accept_list(lrac.get_address(), packet);
                        }
                        LeConnectionManagementCommandChild::LeClearFilterAcceptList(_lcac) => {
                            self.process_clear_accept_list(packet);
                        }
                        LeConnectionManagementCommandChild::LeReadRemoteFeatures(lrrf) => {
                            self.process_remote_feat_cmd(
                                PendingRemoteFeature::Le,
                                &lrrf.get_connection_handle(),
                                packet,
                            );
                        }
                        // End LeConnectionManagementCommand.specialize()
                        _ => {}
                    },
                    AclCommandChild::Disconnect(dc_conn) => {
                        self.process_disconnect_cmd(
                            dc_conn.get_reason(),
                            dc_conn.get_connection_handle(),
                            packet,
                        );
                    }

                    // End AclCommand.specialize()
                    _ => (),
                },
                CommandChild::Reset(_) => {
                    self.process_reset();
                }

                // End HciCommand.specialize()
                _ => (),
            },

            PacketChild::HciEvent(ev) => match ev.specialize() {
                EventChild::CommandStatus(cs) => {
                    self.process_command_status(cs.get_status(), cs.get_command_op_code(), packet);
                }
                EventChild::ConnectionComplete(cc) => {
                    self.process_conn_complete_ev(
                        cc.get_status(),
                        cc.get_connection_handle(),
                        cc.get_bd_addr(),
                        packet,
                    );
                }
                EventChild::DisconnectionComplete(dsc) => {
                    self.process_disconn_complete_ev(dsc.get_connection_handle(), packet);
                }
                EventChild::SynchronousConnectionComplete(scc) => {
                    self.process_sync_conn_complete_ev(
                        scc.get_status(),
                        scc.get_connection_handle(),
                        scc.get_bd_addr(),
                        packet,
                    );
                }
                EventChild::NumberOfCompletedPackets(nocp) => {
                    self.process_nocp(&nocp, packet);
                }
                EventChild::AuthenticatedPayloadTimeoutExpired(apte) => {
                    self.process_apte(&apte, packet);
                }
                EventChild::ReadRemoteSupportedFeaturesComplete(rsfc) => {
                    self.process_remote_feat_ev(
                        PendingRemoteFeature::Supported,
                        rsfc.get_status(),
                        rsfc.get_connection_handle(),
                        packet,
                    );
                }
                EventChild::ReadRemoteExtendedFeaturesComplete(refc) => {
                    self.process_remote_feat_ev(
                        PendingRemoteFeature::Extended,
                        refc.get_status(),
                        refc.get_connection_handle(),
                        packet,
                    );
                }
                EventChild::LeMetaEvent(lme) => match lme.specialize() {
                    LeMetaEventChild::LeConnectionComplete(lcc) => {
                        self.process_le_conn_complete_ev(
                            lcc.get_status(),
                            lcc.get_connection_handle(),
                            lcc.get_peer_address(),
                            packet,
                        );
                    }
                    LeMetaEventChild::LeEnhancedConnectionComplete(lecc) => {
                        self.process_le_conn_complete_ev(
                            lecc.get_status(),
                            lecc.get_connection_handle(),
                            lecc.get_peer_address(),
                            packet,
                        );
                    }
                    LeMetaEventChild::LeReadRemoteFeaturesComplete(lrrfc) => {
                        self.process_remote_feat_ev(
                            PendingRemoteFeature::Le,
                            lrrfc.get_status(),
                            lrrfc.get_connection_handle(),
                            packet,
                        );
                    }
                    // End LeMetaEvent.specialize()
                    _ => {}
                },

                // End HciEvent.specialize()
                _ => (),
            },

            // Use tx packets for nocp tracking.
            PacketChild::AclTx(tx) => {
                self.process_acl_tx(&tx, packet);
            }

            // We don't do anything with RX packets yet.
            PacketChild::AclRx(_) => (),

            // End packet.inner match
            _ => (),
        }
    }

    fn report(&self, writer: &mut dyn Write) {
        if self.reportable.len() > 0 {
            let _ = writeln!(writer, "OddDisconnectionsRule report:");
            for (ts, message) in self.reportable.iter() {
                let _ = writeln!(writer, "[{:?}] {}", ts, message);
            }
        }
    }

    fn report_signals(&self) -> &[Signal] {
        self.signals.as_slice()
    }
}

// What state are we in for the LinkKeyMismatchRule state?
#[derive(Debug, PartialEq)]
enum LinkKeyMismatchState {
    Requested, // Controller requested link key to the host
    Replied,   // Host replied the link key
}

/// Identifies instances when the peer forgets the link key or it mismatches with ours.
/// For classic connections, first the controller asks for the link key via Link Key Request, then
/// the host replies with Link Key Request Reply, finally the controller will report the result,
/// usually via AuthenticationComplete.
/// For LE connections, the host passes the Long Term Key (LTK) via LE Start Encryption, then the
/// controller reports the result via Encryption Change event. There are also LE Long Term Key
/// Requests from the controller, but that is only used when the device is a peripheral.
struct LinkKeyMismatchRule {
    /// Addresses in authenticating process
    states: HashMap<Address, LinkKeyMismatchState>,

    /// Active handles
    handles: HashMap<ConnectionHandle, Address>,

    /// Handles pending for LE encryption
    pending_le_encrypt: HashSet<ConnectionHandle>,

    /// Pre-defined signals discovered in the logs.
    signals: Vec<Signal>,

    /// Interesting occurrences surfaced by this rule.
    reportable: Vec<(NaiveDateTime, String)>,
}

impl LinkKeyMismatchRule {
    pub fn new() -> Self {
        LinkKeyMismatchRule {
            states: HashMap::new(),
            handles: HashMap::new(),
            pending_le_encrypt: HashSet::new(),
            signals: vec![],
            reportable: vec![],
        }
    }

    fn process_address_auth(&mut self, status: ErrorCode, address: Address, packet: &Packet) {
        if status == ErrorCode::AuthenticationFailure {
            if let Some(LinkKeyMismatchState::Replied) = self.states.get(&address) {
                self.signals.push(Signal {
                    index: packet.index,
                    ts: packet.ts,
                    tag: ConnectionSignal::LinkKeyMismatch.into(),
                });

                self.reportable.push((
                    packet.ts,
                    format!("Peer {} forgets the link key, or it mismatches with ours.", address),
                ));
            }
        }
        self.states.remove(&address);
    }

    fn process_handle_auth(
        &mut self,
        status: ErrorCode,
        handle: ConnectionHandle,
        packet: &Packet,
    ) {
        if let Some(address) = self.handles.get(&handle) {
            self.process_address_auth(status, *address, packet);
        }
    }

    fn process_request_link_key(&mut self, address: Address) {
        self.states.insert(address, LinkKeyMismatchState::Requested);
    }

    fn process_reply_link_key(&mut self, address: Address, key_exist: bool) {
        if !key_exist {
            self.states.remove(&address);
            return;
        }

        if let Some(LinkKeyMismatchState::Requested) = self.states.get(&address) {
            self.states.insert(address, LinkKeyMismatchState::Replied);
        }
    }

    fn process_encryption_change(
        &mut self,
        status: ErrorCode,
        handle: ConnectionHandle,
        packet: &Packet,
    ) {
        if status != ErrorCode::Success {
            let address_format = self
                .handles
                .get(&handle)
                .map_or(format!("handle {}", handle), |addr| format!("{}", addr));
            self.reportable.push((
                packet.ts,
                format!("Encryption failure with {:?} for {}", status, address_format),
            ));

            if self.pending_le_encrypt.contains(&handle) {
                self.signals.push(Signal {
                    index: packet.index,
                    ts: packet.ts,
                    tag: ConnectionSignal::LongTermKeyMismatch.into(),
                });
            }
        }

        self.pending_le_encrypt.remove(&handle);
    }

    fn process_reset(&mut self) {
        self.states.clear();
        self.handles.clear();
        self.pending_le_encrypt.clear();
    }
}

impl Rule for LinkKeyMismatchRule {
    fn process(&mut self, packet: &Packet) {
        match &packet.inner {
            PacketChild::HciEvent(ev) => match ev.specialize() {
                EventChild::ConnectionComplete(ev) => {
                    if ev.get_status() == ErrorCode::Success {
                        self.handles.insert(ev.get_connection_handle(), ev.get_bd_addr());
                    }
                }
                EventChild::LinkKeyRequest(ev) => {
                    self.process_request_link_key(ev.get_bd_addr());
                }
                EventChild::SimplePairingComplete(ev) => {
                    self.process_address_auth(ev.get_status(), ev.get_bd_addr(), &packet);
                }
                EventChild::AuthenticationComplete(ev) => {
                    self.process_handle_auth(ev.get_status(), ev.get_connection_handle(), &packet);
                }
                EventChild::DisconnectionComplete(ev) => {
                    self.process_handle_auth(ev.get_status(), ev.get_connection_handle(), &packet);
                    self.handles.remove(&ev.get_connection_handle());
                }
                EventChild::EncryptionChange(ev) => {
                    self.process_encryption_change(
                        ev.get_status(),
                        ev.get_connection_handle(),
                        &packet,
                    );
                }
                EventChild::LeMetaEvent(ev) => match ev.specialize() {
                    LeMetaEventChild::LeConnectionComplete(ev) => {
                        if ev.get_status() == ErrorCode::Success {
                            self.handles.insert(ev.get_connection_handle(), ev.get_peer_address());
                        }
                    }
                    LeMetaEventChild::LeEnhancedConnectionComplete(ev) => {
                        if ev.get_status() == ErrorCode::Success {
                            self.handles.insert(ev.get_connection_handle(), ev.get_peer_address());
                        }
                    }

                    // EventChild::LeMetaEvent(ev).specialize()
                    _ => {}
                },

                // PacketChild::HciEvent(ev).specialize()
                _ => {}
            },

            PacketChild::HciCommand(cmd) => match cmd.specialize() {
                CommandChild::AclCommand(cmd) => match cmd.specialize() {
                    // Have an arm for Disconnect since sometimes we don't receive disconnect
                    // event when powering off. However, no need to actually match the reason
                    // since we just clean the handle in both cases.
                    AclCommandChild::Disconnect(cmd) => {
                        self.process_handle_auth(
                            ErrorCode::Success,
                            cmd.get_connection_handle(),
                            &packet,
                        );
                        self.handles.remove(&cmd.get_connection_handle());
                    }

                    // CommandChild::AclCommand(cmd).specialize()
                    _ => {}
                },

                CommandChild::SecurityCommand(cmd) => match cmd.specialize() {
                    SecurityCommandChild::LinkKeyRequestReply(cmd) => {
                        self.process_reply_link_key(cmd.get_bd_addr(), true);
                    }
                    SecurityCommandChild::LinkKeyRequestNegativeReply(cmd) => {
                        self.process_reply_link_key(cmd.get_bd_addr(), false);
                    }

                    // CommandChild::SecurityCommand(cmd).specialize()
                    _ => {}
                },

                CommandChild::LeSecurityCommand(cmd) => match cmd.specialize() {
                    LeSecurityCommandChild::LeStartEncryption(cmd) => {
                        self.pending_le_encrypt.insert(cmd.get_connection_handle());
                    }

                    // CommandChild::LeSecurityCommand(cmd).specialize()
                    _ => {}
                },

                CommandChild::Reset(_) => {
                    self.process_reset();
                }

                // PacketChild::HciCommand(cmd).specialize()
                _ => {}
            },

            // packet.inner
            _ => {}
        }
    }

    fn report(&self, writer: &mut dyn Write) {
        if self.reportable.len() > 0 {
            let _ = writeln!(writer, "LinkKeyMismatchRule report:");
            for (ts, message) in self.reportable.iter() {
                let _ = writeln!(writer, "[{:?}] {}", ts, message);
            }
        }
    }

    fn report_signals(&self) -> &[Signal] {
        self.signals.as_slice()
    }
}

struct SecurityMode3Rule {
    /// Pre-defined signals discovered in the logs.
    signals: Vec<Signal>,

    /// Interesting occurrences surfaced by this rule.
    reportable: Vec<(NaiveDateTime, String)>,
}

impl SecurityMode3Rule {
    pub fn new() -> Self {
        SecurityMode3Rule { signals: vec![], reportable: vec![] }
    }

    fn process_connect_complete(
        &mut self,
        status: ErrorCode,
        address: Address,
        encryption_enabled: Enable,
        packet: &Packet,
    ) {
        // See figure 5.2 and 5.3 in BT spec v5.4 Vol 3 Part C 5.2 for security mode 3 explanation.
        // It is indicated by encryption before the link setup is complete.
        // Therefore we just need to observe the encryption_enabled parameter in conn complete evt.
        if status == ErrorCode::Success && encryption_enabled == Enable::Enabled {
            self.signals.push(Signal {
                index: packet.index,
                ts: packet.ts,
                tag: ConnectionSignal::SecurityMode3.into(),
            });

            self.reportable.push((
                packet.ts,
                format!("Device {} uses unsupported legacy security mode 3 (b/260625799)", address),
            ));
        }
    }
}

impl Rule for SecurityMode3Rule {
    fn process(&mut self, packet: &Packet) {
        match &packet.inner {
            PacketChild::HciEvent(ev) => match ev.specialize() {
                EventChild::ConnectionComplete(ev) => {
                    self.process_connect_complete(
                        ev.get_status(),
                        ev.get_bd_addr(),
                        ev.get_encryption_enabled(),
                        packet,
                    );
                }
                _ => {}
            },
            _ => {}
        }
    }

    fn report(&self, writer: &mut dyn Write) {
        if self.reportable.len() > 0 {
            let _ = writeln!(writer, "SecurityMode3Rule report:");
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
pub fn get_connections_group() -> RuleGroup {
    let mut group = RuleGroup::new();
    group.add_rule(Box::new(LinkKeyMismatchRule::new()));
    group.add_rule(Box::new(OddDisconnectionsRule::new()));
    group.add_rule(Box::new(SecurityMode3Rule::new()));

    group
}
