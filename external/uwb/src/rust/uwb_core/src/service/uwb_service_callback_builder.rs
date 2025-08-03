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

//! This module implements the UwbServiceCallbackBuilder, intended to be used
//! in cases where UwbServiceCallback is [Send](std::marker::Send).
//! If UwbServiceCallback is [!Send](std::marker::Send) a custom implementation
//! needs to be handed to UwbServiceBuilder instead.
use crate::service::uwb_service::{UwbServiceCallback, UwbServiceCallbackBuilder};

/// This struct defines a builder for UwbServiceCallbacks that are [Send](std::marker::Send).
pub struct UwbServiceCallbackSendBuilder<C: UwbServiceCallback + Send> {
    callback: C,
}

impl<C: UwbServiceCallback + Send> UwbServiceCallbackSendBuilder<C> {
    /// Creates a new UwbServiceCallbackBuilder with the given UwbServiceCallback
    /// that is [Send](std::marker::Send).
    pub fn new(callback: C) -> Self {
        Self { callback }
    }
}

impl<C: UwbServiceCallback + Send> UwbServiceCallbackBuilder<C>
    for UwbServiceCallbackSendBuilder<C>
{
    fn build(self) -> Option<C> {
        Some(self.callback)
    }
}
