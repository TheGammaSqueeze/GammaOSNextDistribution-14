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

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};

use tokio::sync::Notify;
use tokio::time::{timeout, Duration};

use crate::params::{DeviceState, ReasonCode, SessionId, SessionState};
use crate::service::uwb_service::UwbServiceCallback;
use crate::uci::SessionRangeData;

#[derive(Clone, Default)]
pub(crate) struct MockUwbServiceCallback {
    expected_calls: Arc<Mutex<VecDeque<ExpectedCall>>>,
    expect_call_consumed: Arc<Notify>,
}

impl MockUwbServiceCallback {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn expect_on_service_reset(&mut self, success: bool) {
        self.push_expected_call(ExpectedCall::ServiceReset { success });
    }

    pub fn expect_on_uci_device_status_changed(&mut self, state: DeviceState) {
        self.push_expected_call(ExpectedCall::UciDeviceStatus { state });
    }

    pub fn expect_on_session_state_changed(
        &mut self,
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) {
        self.push_expected_call(ExpectedCall::SessionState {
            session_id,
            session_state,
            reason_code,
        });
    }

    pub fn expect_on_range_data_received(
        &mut self,
        session_id: SessionId,
        range_data: SessionRangeData,
    ) {
        self.push_expected_call(ExpectedCall::RangeData { session_id, range_data });
    }

    pub fn expect_on_vendor_notification_received(&mut self, gid: u32, oid: u32, payload: Vec<u8>) {
        self.push_expected_call(ExpectedCall::VendorNotification { gid, oid, payload });
    }

    pub async fn wait_expected_calls_done(&mut self) -> bool {
        while !self.expected_calls.lock().unwrap().is_empty() {
            if timeout(Duration::from_secs(1), self.expect_call_consumed.notified()).await.is_err()
            {
                return false;
            }
        }
        true
    }

    fn push_expected_call(&mut self, call: ExpectedCall) {
        self.expected_calls.lock().unwrap().push_back(call);
    }

    fn pop_expected_call(&mut self) -> ExpectedCall {
        let call = self.expected_calls.lock().unwrap().pop_front().unwrap();
        self.expect_call_consumed.notify_one();
        call
    }
}

impl UwbServiceCallback for MockUwbServiceCallback {
    fn on_service_reset(&mut self, success: bool) {
        assert_eq!(self.pop_expected_call(), ExpectedCall::ServiceReset { success });
    }

    fn on_uci_device_status_changed(&mut self, state: DeviceState) {
        assert_eq!(self.pop_expected_call(), ExpectedCall::UciDeviceStatus { state });
    }

    fn on_session_state_changed(
        &mut self,
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    ) {
        assert_eq!(
            self.pop_expected_call(),
            ExpectedCall::SessionState { session_id, session_state, reason_code }
        );
    }

    fn on_range_data_received(&mut self, session_id: SessionId, range_data: SessionRangeData) {
        assert_eq!(self.pop_expected_call(), ExpectedCall::RangeData { session_id, range_data });
    }

    fn on_vendor_notification_received(&mut self, gid: u32, oid: u32, payload: Vec<u8>) {
        assert_eq!(
            self.pop_expected_call(),
            ExpectedCall::VendorNotification { gid, oid, payload }
        );
    }
}

#[derive(PartialEq, Debug)]
pub(crate) enum ExpectedCall {
    ServiceReset { success: bool },
    UciDeviceStatus { state: DeviceState },
    SessionState { session_id: SessionId, session_state: SessionState, reason_code: ReasonCode },
    RangeData { session_id: SessionId, range_data: SessionRangeData },
    VendorNotification { gid: u32, oid: u32, payload: Vec<u8> },
}
