// Copyright 2023, The Android Open Source Project
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

//! Helpers for the watchdog module.

/// This module provides helpers for simplified use of the watchdog module.
#[cfg(feature = "watchdog")]
pub mod watchdog {
    use lazy_static::lazy_static;
    use std::sync::Arc;
    use std::time::Duration;
    pub use watchdog_rs::WatchPoint;
    use watchdog_rs::Watchdog;

    lazy_static! {
        /// A Watchdog thread, that can be used to create watch points.
        static ref WD: Arc<Watchdog> = Watchdog::new(Duration::from_secs(10));
    }

    /// Sets a watch point with `id` and a timeout of `millis` milliseconds.
    pub fn watch_millis(id: &'static str, millis: u64) -> Option<WatchPoint> {
        Watchdog::watch(&WD, id, Duration::from_millis(millis))
    }

    /// Like `watch_millis` but with a callback that is called every time a report
    /// is printed about this watch point.
    pub fn watch_millis_with(
        id: &'static str,
        millis: u64,
        callback: impl Fn() -> String + Send + 'static,
    ) -> Option<WatchPoint> {
        Watchdog::watch_with(&WD, id, Duration::from_millis(millis), callback)
    }
}

/// This module provides empty/noop implementations of the watch dog utility functions.
#[cfg(not(feature = "watchdog"))]
pub mod watchdog {
    /// Noop watch point.
    pub struct WatchPoint();
    /// Sets a Noop watch point.
    fn watch_millis(_: &'static str, _: u64) -> Option<WatchPoint> {
        None
    }

    pub fn watch_millis_with(
        _: &'static str,
        _: u64,
        _: impl Fn() -> String + Send + 'static,
    ) -> Option<WatchPoint> {
        None
    }
}
