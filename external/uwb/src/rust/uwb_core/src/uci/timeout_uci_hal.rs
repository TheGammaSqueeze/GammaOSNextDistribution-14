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

use std::future::Future;
use std::time::Duration;

use async_trait::async_trait;
use tokio::sync::mpsc;
use tokio::time::timeout;

use crate::error::{Error, Result};
use crate::params::uci_packets::SessionId;
use crate::uci::command::UciCommand;
use crate::uci::uci_hal::{UciHal, UciHalPacket};

const HAL_API_TIMEOUT_MS: u64 = 1000;
// TODO(b/279175027): Reduce this once vendor fixes their initialization sequence.
const HAL_OPEN_TIMEOUT_MS: u64 = 20000; // Extra time may be needed for starting UWB stack.

pub(crate) struct TimeoutUciHal<T: UciHal>(T);

impl<T: UciHal> TimeoutUciHal<T> {
    pub fn new(hal: T) -> Self {
        Self(hal)
    }

    async fn call_with_timeout(
        future: impl Future<Output = Result<()>>,
        duration: u64,
    ) -> Result<()> {
        match timeout(Duration::from_millis(duration), future).await {
            Ok(result) => result,
            Err(_) => Err(Error::Timeout),
        }
    }
}

#[async_trait]
impl<T: UciHal> UciHal for TimeoutUciHal<T> {
    async fn open(&mut self, packet_sender: mpsc::UnboundedSender<UciHalPacket>) -> Result<()> {
        Self::call_with_timeout(self.0.open(packet_sender), HAL_OPEN_TIMEOUT_MS).await
    }

    async fn close(&mut self) -> Result<()> {
        Self::call_with_timeout(self.0.close(), HAL_API_TIMEOUT_MS).await
    }

    async fn notify_session_initialized(&mut self, session_id: SessionId) -> Result<()> {
        Self::call_with_timeout(self.0.notify_session_initialized(session_id), HAL_API_TIMEOUT_MS)
            .await
    }

    async fn send_command(&mut self, cmd: UciCommand) -> Result<()> {
        Self::call_with_timeout(self.0.send_command(cmd), HAL_API_TIMEOUT_MS).await
    }

    async fn send_packet(&mut self, packet: UciHalPacket) -> Result<()> {
        Self::call_with_timeout(self.0.send_packet(packet), HAL_API_TIMEOUT_MS).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use std::matches;

    use tokio::time::sleep;

    use crate::utils::init_test_logging;

    struct FakeUciHal;

    #[async_trait]
    impl UciHal for FakeUciHal {
        async fn open(&mut self, _: mpsc::UnboundedSender<UciHalPacket>) -> Result<()> {
            Ok(())
        }
        async fn close(&mut self) -> Result<()> {
            Err(Error::Unknown)
        }
        async fn send_packet(&mut self, _: UciHalPacket) -> Result<()> {
            sleep(Duration::MAX).await;
            Ok(())
        }
    }

    fn setup_hal() -> TimeoutUciHal<FakeUciHal> {
        init_test_logging();
        TimeoutUciHal::new(FakeUciHal {})
    }

    #[tokio::test]
    async fn test_ok() {
        let mut hal = setup_hal();
        let (sender, _receiver) = mpsc::unbounded_channel();

        assert!(matches!(hal.open(sender).await, Ok(())));
    }

    #[tokio::test]
    async fn test_fail() {
        let mut hal = setup_hal();

        assert!(matches!(hal.close().await, Err(Error::Unknown)));
    }

    #[tokio::test]
    async fn test_timeout() {
        let mut hal = setup_hal();
        let cmd = vec![];

        assert!(matches!(hal.send_packet(cmd).await, Err(Error::Timeout)));
    }
}
