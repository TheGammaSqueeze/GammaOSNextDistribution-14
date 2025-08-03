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

//! This module provides a time hack to work around the broken `Instant` type in the standard
//! library.
//!
//! `BootTime` looks like `Instant`, but represents `CLOCK_BOOTTIME` instead of `CLOCK_MONOTONIC`.
//! This means the clock increments correctly during suspend.

pub use std::time::Duration;

use std::io;

use futures::future::pending;
use std::convert::TryInto;
use std::fmt;
use std::future::Future;
use std::os::unix::io::{AsRawFd, RawFd};
use tokio::io::unix::AsyncFd;
use tokio::select;

/// Represents a moment in time, with differences including time spent in suspend. Only valid for
/// a single boot - numbers from different boots are incomparable.
#[derive(Copy, Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct BootTime {
    d: Duration,
}

// Return an error with the same structure as tokio::time::timeout to facilitate migration off it,
// and hopefully some day back to it.
/// Error returned by timeout
#[derive(Debug, PartialEq, Eq)]
pub struct Elapsed(());

impl fmt::Display for Elapsed {
    fn fmt(&self, fmt: &mut fmt::Formatter<'_>) -> fmt::Result {
        "deadline has elapsed".fmt(fmt)
    }
}

impl std::error::Error for Elapsed {}

impl BootTime {
    /// Gets a `BootTime` representing the current moment in time.
    pub fn now() -> BootTime {
        let mut t = libc::timespec { tv_sec: 0, tv_nsec: 0 };
        // SAFETY: clock_gettime's only action will be to possibly write to the pointer provided,
        // and no borrows exist from that object other than the &mut used to construct the pointer
        // itself.
        if unsafe { libc::clock_gettime(libc::CLOCK_BOOTTIME, &mut t as *mut libc::timespec) } != 0
        {
            panic!(
                "libc::clock_gettime(libc::CLOCK_BOOTTIME) failed: {:?}",
                io::Error::last_os_error()
            );
        }
        BootTime { d: Duration::new(t.tv_sec as u64, t.tv_nsec as u32) }
    }

    /// Determines how long has elapsed since the provided `BootTime`.
    pub fn elapsed(&self) -> Duration {
        BootTime::now().checked_duration_since(*self).unwrap()
    }

    /// Add a specified time delta to a moment in time. If this would overflow the representation,
    /// returns `None`.
    pub fn checked_add(&self, duration: Duration) -> Option<BootTime> {
        Some(BootTime { d: self.d.checked_add(duration)? })
    }

    /// Finds the difference from an earlier point in time. If the provided time is later, returns
    /// `None`.
    pub fn checked_duration_since(&self, earlier: BootTime) -> Option<Duration> {
        self.d.checked_sub(earlier.d)
    }
}

struct TimerFd(RawFd);

impl Drop for TimerFd {
    fn drop(&mut self) {
        // SAFETY: The fd is owned by the TimerFd struct, and no memory access occurs as a result of
        // this call.
        unsafe {
            libc::close(self.0);
        }
    }
}

impl AsRawFd for TimerFd {
    fn as_raw_fd(&self) -> RawFd {
        self.0
    }
}

impl TimerFd {
    fn create() -> io::Result<Self> {
        // SAFETY: This libc call will either give us back a file descriptor or fail, it does not
        // act on memory or resources.
        let raw = unsafe {
            libc::timerfd_create(libc::CLOCK_BOOTTIME, libc::TFD_NONBLOCK | libc::TFD_CLOEXEC)
        };
        if raw < 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(Self(raw))
    }

    fn set(&self, duration: Duration) {
        assert_ne!(duration, Duration::from_millis(0));
        let timer = libc::itimerspec {
            it_interval: libc::timespec { tv_sec: 0, tv_nsec: 0 },
            it_value: libc::timespec {
                tv_sec: duration.as_secs().try_into().unwrap(),
                tv_nsec: duration.subsec_nanos().try_into().unwrap(),
            },
        };
        // SAFETY: We own `timer` and there are no borrows to it other than the pointer we pass to
        // timerfd_settime. timerfd_settime is explicitly documented to handle a null output
        // parameter for its fourth argument by not filling out the output. The fd passed in at
        // self.0 is owned by the `TimerFd` struct, so we aren't breaking anyone else's invariants.
        if unsafe { libc::timerfd_settime(self.0, 0, &timer, std::ptr::null_mut()) } != 0 {
            panic!("timerfd_settime failed: {:?}", io::Error::last_os_error());
        }
    }
}

/// Runs the provided future until completion or `duration` has passed on the `CLOCK_BOOTTIME`
/// clock. In the event of a timeout, returns the elapsed time as an error.
pub async fn timeout<T>(duration: Duration, future: impl Future<Output = T>) -> Result<T, Elapsed> {
    // Ideally, all timeouts in a runtime would share a timerfd. That will be much more
    // straightforwards to implement when moving this functionality into `tokio`.

    // According to timerfd_settime(), setting zero duration will disarm the timer, so
    // we return immediate timeout here.
    // Can't use is_zero() for now because sc-mainline-prod's Rust version is below 1.53.
    if duration == Duration::from_millis(0) {
        return Err(Elapsed(()));
    }

    // The failure conditions for this are rare (see `man 2 timerfd_create`) and the caller would
    // not be able to do much in response to them. When integrated into tokio, this would be called
    // during runtime setup.
    let timer_fd = TimerFd::create().unwrap();
    timer_fd.set(duration);
    let async_fd = AsyncFd::new(timer_fd).unwrap();
    select! {
        v = future => Ok(v),
        _ = async_fd.readable() => Err(Elapsed(())),
    }
}

/// Provides a future which will complete once the provided duration has passed, as measured by the
/// `CLOCK_BOOTTIME` clock.
pub async fn sleep(duration: Duration) {
    assert!(timeout(duration, pending::<()>()).await.is_err());
}

#[test]
fn monotonic_smoke() {
    for _ in 0..1000 {
        // If BootTime is not monotonic, .elapsed() will panic on the unwrap.
        BootTime::now().elapsed();
    }
}

#[test]
fn round_trip() {
    use std::thread::sleep;
    for _ in 0..10 {
        let start = BootTime::now();
        sleep(Duration::from_millis(1));
        let end = BootTime::now();
        let delta = end.checked_duration_since(start).unwrap();
        assert_eq!(start.checked_add(delta).unwrap(), end);
    }
}

#[tokio::test]
async fn timeout_drift() {
    let delta = Duration::from_millis(40);
    for _ in 0..5 {
        let start = BootTime::now();
        assert!(timeout(delta, pending::<()>()).await.is_err());
        let taken = start.elapsed();
        let drift = if taken > delta { taken - delta } else { delta - taken };
        assert!(drift < Duration::from_millis(10));
    }

    for _ in 0..5 {
        let start = BootTime::now();
        sleep(delta).await;
        let taken = start.elapsed();
        let drift = if taken > delta { taken - delta } else { delta - taken };
        assert!(drift < Duration::from_millis(10));
    }
}

#[tokio::test]
async fn timeout_duration_zero() {
    let start = BootTime::now();
    assert!(timeout(Duration::from_millis(0), pending::<()>()).await.is_err());
    let taken = start.elapsed();
    assert!(taken < Duration::from_millis(5));
}
