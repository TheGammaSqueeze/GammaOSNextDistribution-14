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

//! This module defines the UwbServiceBuilder, the builder of the UwbService.

use tokio::runtime::{Handle, Runtime};

use crate::service::uwb_service::{UwbService, UwbServiceCallback, UwbServiceCallbackBuilder};
use crate::uci::uci_hal::UciHal;
use crate::uci::uci_logger::UciLoggerMode;
use crate::uci::uci_logger_factory::UciLoggerFactory;
use crate::uci::uci_manager::UciManagerImpl;
use crate::utils::consuming_builder_field;

/// Create the default runtime for UwbService.
pub fn default_runtime() -> Option<Runtime> {
    tokio::runtime::Builder::new_multi_thread().thread_name("UwbService").enable_all().build().ok()
}

/// The builder of UwbService, used to keep the backward compatibility when adding new parameters
/// of creating a UwbService instance.
pub struct UwbServiceBuilder<B, C, U, L>
where
    B: UwbServiceCallbackBuilder<C>,
    C: UwbServiceCallback,
    U: UciHal,
    L: UciLoggerFactory,
{
    runtime_handle: Option<Handle>,
    callback_builder: Option<B>,
    uci_hal: Option<U>,
    uci_logger_factory: Option<L>,
    uci_logger_mode: UciLoggerMode,
    // Circuimvents unused parameter "C" error
    phantom: std::marker::PhantomData<C>,
}

impl<B, C, U, L> Default for UwbServiceBuilder<B, C, U, L>
where
    B: UwbServiceCallbackBuilder<C>,
    C: UwbServiceCallback,
    U: UciHal,
    L: UciLoggerFactory,
{
    fn default() -> Self {
        Self {
            runtime_handle: None,
            callback_builder: None,
            uci_hal: None,
            uci_logger_factory: None,
            uci_logger_mode: UciLoggerMode::Disabled,
            phantom: std::marker::PhantomData,
        }
    }
}

impl<B, C, U, L> UwbServiceBuilder<B, C, U, L>
where
    B: UwbServiceCallbackBuilder<C>,
    C: UwbServiceCallback,
    U: UciHal,
    L: UciLoggerFactory,
{
    /// Create a new builder.
    pub fn new() -> Self {
        Default::default()
    }

    // Setter methods of each field.
    consuming_builder_field!(runtime_handle, Handle, Some);
    consuming_builder_field!(callback_builder, B, Some);
    consuming_builder_field!(uci_hal, U, Some);
    consuming_builder_field!(uci_logger_factory, L, Some);
    consuming_builder_field!(uci_logger_mode, UciLoggerMode);

    /// Build the UwbService.
    pub fn build(mut self) -> Option<UwbService> {
        let runtime_handle = self.runtime_handle.take()?;
        let uci_hal = self.uci_hal.take()?;
        let mut uci_logger_factory = self.uci_logger_factory.take()?;
        let uci_logger = uci_logger_factory.build_logger("default")?;
        let uci_logger_mode = self.uci_logger_mode;
        let uci_manager = runtime_handle
            .block_on(async move { UciManagerImpl::new(uci_hal, uci_logger, uci_logger_mode) });
        UwbService::new(runtime_handle, self.callback_builder.take()?, uci_manager)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::service::mock_uwb_service_callback::MockUwbServiceCallback;
    use crate::service::uwb_service_callback_builder::UwbServiceCallbackSendBuilder;
    use crate::uci::mock_uci_hal::MockUciHal;
    use crate::uci::uci_logger_factory::NopUciLoggerFactory;

    #[test]
    fn test_build_fail() {
        let result = UwbServiceBuilder::<
            UwbServiceCallbackSendBuilder<MockUwbServiceCallback>,
            MockUwbServiceCallback,
            MockUciHal,
            NopUciLoggerFactory,
        >::new()
        .build();
        assert!(result.is_none());
    }

    #[test]
    fn test_build_ok() {
        let runtime = default_runtime().unwrap();
        let callback = MockUwbServiceCallback::new();
        let result = UwbServiceBuilder::new()
            .runtime_handle(runtime.handle().to_owned())
            .callback_builder(UwbServiceCallbackSendBuilder::new(callback))
            .uci_hal(MockUciHal::new())
            .uci_logger_factory(NopUciLoggerFactory::default())
            .build();
        assert!(result.is_some());
    }
}
