/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#![no_std]

use hwwsk::{HwWskReq, HWWSK_MAX_MSG_SIZE};
use tipc::{ConnectResult, Handle, Manager, MessageResult, PortCfg, Service, Uuid};
use trusty_std::{self, vec};

struct HwWskService;

impl Service for HwWskService {
    type Connection = ();
    type Message = HwWskReq;

    fn on_connect(
        &self,
        _port: &PortCfg,
        _handle: &Handle,
        _peer: &Uuid,
    ) -> tipc::Result<ConnectResult<Self::Connection>> {
        /*
            TODO: implement any nuance of extant hwwsk service
        */
        Ok(ConnectResult::Accept(()))
    }

    fn on_message(
        &self,
        _connection: &Self::Connection,
        handle: &Handle,
        msg: Self::Message,
    ) -> tipc::Result<MessageResult> {
        /*
            TODO: this is a temporary placeholder for
            a reference implementation of the hwwsk service.

            - parse incoming cmd
            - generate, import, or export key depending on request
        */
        let response = msg.response_from(0, vec![]);
        handle.send(&response)?;
        Ok(MessageResult::MaintainConnection)
    }
}

fn main() {
    trusty_log::init();

    let cfg = PortCfg::new("com.android.trusty.hwwsk")
        .expect("Could not create port config")
        .allow_ta_connect();

    // TODO: determine if it's necessary for the non-secure world to connect
    // .allow_ns_connect();

    let service = HwWskService {};

    let buffer = [0u8; HWWSK_MAX_MSG_SIZE as usize];
    Manager::<_, _, 1, 4>::new(service, cfg, buffer)
        .expect("Could not create service manager")
        .run_event_loop()
        .expect("Hwwsk service quit unexpectedly");
}
