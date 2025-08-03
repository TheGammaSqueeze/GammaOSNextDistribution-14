//! Waking timers for Bluetooth. Implemented using timerfd, but supposed to feel similar to
///Tokio's time
use nix::sys::time::TimeSpec;
use nix::sys::timerfd::{ClockId, Expiration, TimerFd, TimerFlags, TimerSetTimeFlags};
use std::os::fd::{AsFd, AsRawFd, RawFd};
use std::time::Duration;
use tokio::io::unix::AsyncFd;

/// A wrapper for `TimerFd` which implements `AsRawFd`.
#[derive(Debug)]
struct TimerFdWrapper(TimerFd);

impl TimerFdWrapper {
    fn get(&self) -> nix::Result<Option<Expiration>> {
        self.0.get()
    }

    fn set(&self, expiration: Expiration, flags: TimerSetTimeFlags) -> nix::Result<()> {
        self.0.set(expiration, flags)
    }

    fn wait(&self) -> nix::Result<()> {
        self.0.wait()
    }
}

impl AsRawFd for TimerFdWrapper {
    fn as_raw_fd(&self) -> RawFd {
        self.0.as_fd().as_raw_fd()
    }
}

/// A single shot Alarm
pub struct Alarm {
    fd: AsyncFd<TimerFdWrapper>,
}

impl Alarm {
    /// Construct a new alarm
    pub fn new() -> Self {
        let timer = TimerFd::new(get_clock(), TimerFlags::empty()).unwrap();
        Self { fd: AsyncFd::new(TimerFdWrapper(timer)).unwrap() }
    }

    /// Reset the alarm to duration, starting from now
    pub fn reset(&self, duration: Duration) {
        self.fd
            .get_ref()
            .set(Expiration::OneShot(TimeSpec::from(duration)), TimerSetTimeFlags::empty())
            .unwrap();
    }

    /// Stop the alarm if it is currently started
    pub fn cancel(&self) {
        self.reset(Duration::from_millis(0));
    }

    /// Completes when the alarm has expired
    pub async fn expired(&self) {
        let mut read_ready = self.fd.readable().await.unwrap();
        read_ready.clear_ready();
        drop(read_ready);
        // Will not block, since we have confirmed it is readable
        if self.fd.get_ref().get().unwrap().is_some() {
            self.fd.get_ref().wait().unwrap();
        }
    }
}

impl Default for Alarm {
    fn default() -> Self {
        Alarm::new()
    }
}

/// Similar to tokio's interval, except the first tick does *not* complete immediately
pub fn interval(period: Duration) -> Interval {
    let timer = TimerFd::new(get_clock(), TimerFlags::empty()).unwrap();
    timer.set(Expiration::Interval(TimeSpec::from(period)), TimerSetTimeFlags::empty()).unwrap();

    Interval { fd: AsyncFd::new(TimerFdWrapper(timer)).unwrap() }
}

/// Future returned by interval()
pub struct Interval {
    fd: AsyncFd<TimerFdWrapper>,
}

impl Interval {
    /// Call this to get the future for the next tick of the interval
    pub async fn tick(&mut self) {
        let mut read_ready = self.fd.readable().await.unwrap();
        read_ready.clear_ready();
        drop(read_ready);
        // Will not block, since we have confirmed it is readable
        if self.fd.get_ref().get().unwrap().is_some() {
            self.fd.get_ref().wait().unwrap();
        }
    }
}

fn get_clock() -> ClockId {
    if cfg!(target_os = "android") {
        ClockId::CLOCK_BOOTTIME_ALARM
    } else {
        ClockId::CLOCK_BOOTTIME
    }
}

#[cfg(test)]
mod tests {
    use super::interval;
    use super::Alarm;
    use crate::assert_near;
    use std::time::{Duration, Instant};

    #[test]
    fn alarm_cancel_after_expired() {
        let runtime = tokio::runtime::Runtime::new().unwrap();
        runtime.block_on(async {
            let alarm = Alarm::new();
            alarm.reset(Duration::from_millis(10));
            tokio::time::sleep(Duration::from_millis(30)).await;
            alarm.cancel();

            for _ in 0..10 {
                let ready_in_10_ms = async {
                    tokio::time::sleep(Duration::from_millis(10)).await;
                };

                tokio::select! {
                    _ = alarm.expired() => (),
                    _ = ready_in_10_ms => (),
                }
            }
        });
    }

    #[test]
    fn alarm_clear_ready_after_expired() {
        // After an alarm expired, we need to make sure we clear ready from AsyncFdReadyGuard.
        // Otherwise it's still ready and select! won't work.
        let runtime = tokio::runtime::Runtime::new().unwrap();
        runtime.block_on(async {
            let timer = Instant::now();
            let alarm = Alarm::new();
            alarm.reset(Duration::from_millis(10));
            alarm.expired().await;
            let ready_in_10_ms = async {
                tokio::time::sleep(Duration::from_millis(10)).await;
            };
            tokio::select! {
                _ = alarm.expired() => (),
                _ = ready_in_10_ms => (),
            }
            assert_near!(timer.elapsed().as_millis(), 20, 3);
        });
    }

    #[test]
    fn interval_schedule_and_then_drop() {
        let runtime = tokio::runtime::Runtime::new().unwrap();
        runtime.block_on(async {
            interval(Duration::from_millis(10));
        });
    }
}
