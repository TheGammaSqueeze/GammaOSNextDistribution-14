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

//! This module provides convenience functions for doh logging.

use crate::connection::driver::Cause;
use crate::connection::driver::HandshakeInfo;
use crate::connection::driver::HandshakeResult;
use statslog_rust::network_dns_handshake_reported::{
    Cause as StatsdCause, NetworkDnsHandshakeReported, NetworkType as StatsdNetworkType,
    PrivateDnsMode as StatsdPrivateDnsMode, Protocol as StatsdProtocol, Result as StatsdResult,
};

const CELLULAR: u32 = 1;
const WIFI: u32 = 2;
const BLUETOOTH: u32 = 3;
const ETHERNET: u32 = 4;
const VPN: u32 = 5;
const WIFI_AWARE: u32 = 6;
const LOWPAN: u32 = 7;
const CELLULAR_VPN: u32 = 8;
const WIFI_VPN: u32 = 9;
const BLUETOOTH_VPN: u32 = 10;
const ETHERNET_VPN: u32 = 11;
const WIFI_CELLULAR_VPN: u32 = 12;

const OFF: u32 = 1;
const OPPORTUNISTIC: u32 = 2;
const STRICT: u32 = 3;

const TLS1_3_VERSION: u32 = 3;

fn create_default_handshake_atom() -> NetworkDnsHandshakeReported {
    NetworkDnsHandshakeReported {
        protocol: StatsdProtocol::ProtoUnknown,
        result: StatsdResult::HrUnknown,
        cause: StatsdCause::HcUnknown,
        network_type: StatsdNetworkType::NtUnknown,
        private_dns_mode: StatsdPrivateDnsMode::PdmUnknown,
        latency_micros: -1,
        bytes_sent: -1,
        bytes_received: -1,
        round_trips: -1,
        tls_session_cache_hit: false,
        tls_version: -1,
        hostname_verification: false,
        quic_version: -1,
        server_index: -1,
        sampling_rate_denom: -1,
    }
}

fn construct_handshake_event_stats(
    result: HandshakeResult,
    handshake_info: HandshakeInfo,
) -> NetworkDnsHandshakeReported {
    let mut handshake_event_atom = create_default_handshake_atom();
    handshake_event_atom.protocol = StatsdProtocol::ProtoDoh;
    handshake_event_atom.result = match result {
        HandshakeResult::Success => StatsdResult::HrSuccess,
        HandshakeResult::Timeout => StatsdResult::HrTimeout,
        _ => StatsdResult::HrUnknown,
    };
    handshake_event_atom.cause = match handshake_info.cause {
        Cause::Probe => StatsdCause::HcServerProbe,
        Cause::Reconnect => StatsdCause::HcReconnectAfterIdle,
        Cause::Retry => StatsdCause::HcRetryAfterError,
    };
    handshake_event_atom.network_type = match handshake_info.network_type {
        CELLULAR => StatsdNetworkType::NtCellular,
        WIFI => StatsdNetworkType::NtWifi,
        BLUETOOTH => StatsdNetworkType::NtBluetooth,
        ETHERNET => StatsdNetworkType::NtEthernet,
        VPN => StatsdNetworkType::NtVpn,
        WIFI_AWARE => StatsdNetworkType::NtWifiAware,
        LOWPAN => StatsdNetworkType::NtLowpan,
        CELLULAR_VPN => StatsdNetworkType::NtCellularVpn,
        WIFI_VPN => StatsdNetworkType::NtWifiVpn,
        BLUETOOTH_VPN => StatsdNetworkType::NtBluetoothVpn,
        ETHERNET_VPN => StatsdNetworkType::NtEthernetVpn,
        WIFI_CELLULAR_VPN => StatsdNetworkType::NtWifiCellularVpn,
        _ => StatsdNetworkType::NtUnknown,
    };
    handshake_event_atom.private_dns_mode = match handshake_info.private_dns_mode {
        OFF => StatsdPrivateDnsMode::PdmOff,
        OPPORTUNISTIC => StatsdPrivateDnsMode::PdmOpportunistic,
        STRICT => StatsdPrivateDnsMode::PdmStrict,
        _ => StatsdPrivateDnsMode::PdmUnknown,
    };
    handshake_event_atom.latency_micros = handshake_info.elapsed as i32;
    handshake_event_atom.bytes_sent = handshake_info.sent_bytes as i32;
    handshake_event_atom.bytes_received = handshake_info.recv_bytes as i32;
    handshake_event_atom.tls_session_cache_hit = handshake_info.session_hit_checker;
    handshake_event_atom.tls_version = TLS1_3_VERSION as i32;
    handshake_event_atom.hostname_verification = matches!(handshake_info.private_dns_mode, STRICT);
    handshake_event_atom.quic_version = handshake_info.quic_version as i32;
    handshake_event_atom
}

/// Log hankshake events via statsd API.
pub fn log_handshake_event_stats(result: HandshakeResult, handshake_info: HandshakeInfo) {
    let handshake_event_stats = construct_handshake_event_stats(result, handshake_info);

    let logging_result = handshake_event_stats.stats_write();
    if let Err(e) = logging_result {
        log::error!("Error in logging handshake event. {:?}", e);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metrics_write() {
        let handshake_info = HandshakeInfo {
            cause: Cause::Retry,
            network_type: WIFI,
            private_dns_mode: STRICT,
            elapsed: 42596,
            sent_bytes: 761,
            recv_bytes: 6420,
            session_hit_checker: false,
            quic_version: 1,
        };
        let result = HandshakeResult::Timeout;
        let handshake_event_stats = construct_handshake_event_stats(result, handshake_info);
        assert_eq!(handshake_event_stats.protocol as i32, StatsdProtocol::ProtoDoh as i32);
        assert_eq!(handshake_event_stats.result as i32, HandshakeResult::Timeout as i32);
        assert_eq!(handshake_event_stats.cause as i32, StatsdCause::HcRetryAfterError as i32);
        assert_eq!(handshake_event_stats.network_type as i32, StatsdNetworkType::NtWifi as i32);
        assert_eq!(
            handshake_event_stats.private_dns_mode as i32,
            StatsdPrivateDnsMode::PdmStrict as i32
        );
        assert_eq!(handshake_event_stats.latency_micros, 42596);
        assert_eq!(handshake_event_stats.bytes_sent, 761);
        assert_eq!(handshake_event_stats.bytes_received, 6420);
        assert_eq!(handshake_event_stats.round_trips, -1);
        assert!(!handshake_event_stats.tls_session_cache_hit);
        assert!(handshake_event_stats.hostname_verification);
        assert_eq!(handshake_event_stats.quic_version, 1);
        assert_eq!(handshake_event_stats.server_index, -1);
        assert_eq!(handshake_event_stats.sampling_rate_denom, -1);
    }
}
