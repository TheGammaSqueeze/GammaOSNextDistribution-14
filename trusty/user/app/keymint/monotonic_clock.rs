/*
 * Copyright (C) 2022 The Android Open Source Project
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

//! Module that implements [`MonotonicClock`] trait.
use kmr_common::crypto::{MillisecondsSinceEpoch, MonotonicClock};
use log::error;

/// Monotonic clock implementation for Trusty.
pub struct TrustyMonotonicClock;

impl MonotonicClock for TrustyMonotonicClock {
    fn now(&self) -> MillisecondsSinceEpoch {
        let mut secure_time_ns = 0;
        // Safety: external syscall gets valid raw pointer to a `u64`.
        let rc = unsafe { trusty_sys::gettime(0, 0, &mut secure_time_ns) };
        let secure_time_ns = if rc < 0 {
            // Couldn't get time; original behavior is to return here `u64::MAX` scaled to ms and
            // log an error
            error!("Error calling trusty_gettime: {:#x}", rc);
            ((u64::MAX / 1000) / 1000) as i64
        } else {
            (secure_time_ns / 1000) / 1000
        };

        return MillisecondsSinceEpoch(secure_time_ns);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use test::{expect, expect_ne};

    #[test]
    fn get_milliseconds_since_boot_test() {
        let trusty_clock = TrustyMonotonicClock;
        let time1 = trusty_clock.now().0;
        let time2 = trusty_clock.now().0;
        // Because we cannot sleep between calls and granularity is in milliseconds,
        // time1 and 2 might be the same
        expect!(time1 <= time2, "Time should not decrement.");
        expect!(time1 > 0, "time1 should be greater than 0");
        expect_ne!(time1, ((u64::MAX / 1000) / 1000) as i64, "time1 shouldn't indicate an error");
        expect_ne!(time2, ((u64::MAX / 1000) / 1000) as i64, "time2 shouldn't indicate an error");
    }
}
