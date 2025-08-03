// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! A module to collect and write session stats

use crate::devices::devices_handler::get_radio_stats;
use crate::events::{ChipRemoved, Event, ShutDown};
use crate::version::get_version;
use anyhow::Context;
use log::error;
use log::info;
use netsim_common::system::netsimd_temp_dir;
use netsim_proto::stats::NetsimStats;
use protobuf_json_mapping::print_to_string;
use std::fs::File;
use std::io::Write;
use std::sync::mpsc::Receiver;
use std::sync::Arc;
use std::sync::RwLock;
use std::sync::RwLockWriteGuard;
use std::thread::{Builder, JoinHandle};
use std::time::{Duration, Instant};

const WRITE_INTERVAL: Duration = Duration::from_secs(10);

pub struct Session {
    // Handle for the session monitor thread
    handle: Option<JoinHandle<String>>,
    // Session info is accessed by multiple threads
    info: Arc<RwLock<SessionInfo>>,
}

// Fields accessed by threads
struct SessionInfo {
    stats_proto: NetsimStats,
    current_device_count: i32,
    session_start: Instant,
    write_json: bool,
}

impl Session {
    pub fn new() -> Self {
        Self::new_internal(true)
    }
    fn new_internal(write_json: bool) -> Self {
        Session {
            handle: None,
            info: Arc::new(RwLock::new(SessionInfo {
                stats_proto: NetsimStats { version: Some(get_version()), ..Default::default() },
                current_device_count: 0,
                session_start: Instant::now(),
                write_json,
            })),
        }
    }

    // Start the netsim states session.
    //
    // Starts the session monitor thread to handle events and
    // write session stats to json file on event and periodically.
    pub fn start(&mut self, events_rx: Receiver<Event>) -> &mut Self {
        let info = Arc::clone(&self.info);

        // Start up session monitor thread
        self.handle = Some(
            Builder::new()
                .name("session_monitor".to_string())
                .spawn(move || {
                    let mut next_instant = Instant::now() + WRITE_INTERVAL;
                    loop {
                        let mut write_stats = true;
                        let this_instant = Instant::now();
                        let timeout = if next_instant > this_instant {
                            next_instant - this_instant
                        } else {
                            Duration::ZERO
                        };
                        let next_event = events_rx.recv_timeout(timeout);

                        // Hold the write lock for the duration of this loop iteration
                        let mut lock = info.write().expect("Could not acquire session lock");
                        match next_event {
                            Ok(Event::ShutDown(ShutDown { reason })) => {
                                // Shutting down, save the session duration and exit
                                update_session_duration(&mut lock);
                                return reason;
                            }

                            Ok(Event::DeviceRemoved(_)) => {
                                lock.current_device_count -= 1;
                            }

                            Ok(Event::DeviceAdded(_)) => {
                                // update the current_device_count and peak device usage
                                lock.current_device_count += 1;
                                let current_device_count = lock.current_device_count;
                                // incremement total number of devices started
                                let device_count = lock.stats_proto.device_count();
                                lock.stats_proto.set_device_count(device_count + 1);
                                // track the peak device usage
                                if current_device_count > lock.stats_proto.peak_concurrent_devices()
                                {
                                    lock.stats_proto
                                        .set_peak_concurrent_devices(current_device_count);
                                }
                            }

                            Ok(Event::ChipRemoved(ChipRemoved {
                                radio_stats, device_id, ..
                            })) => {
                                // Update the radio stats proto when a
                                // chip is removed.  In the case of
                                // bluetooth there will be 2 radios,
                                // otherwise 1
                                for mut r in radio_stats {
                                    r.set_device_id(device_id);
                                    lock.stats_proto.radio_stats.push(r);
                                }
                            }

                            Ok(Event::ChipAdded(_)) => {
                                // No session stat update required when Chip is added but do proceed to write stats
                            }
                            _ => {
                                // other events are ignored, check to perform periodic write
                                if next_instant > Instant::now() {
                                    write_stats = false;
                                }
                            }
                        }
                        // End of event match - write current stats to json
                        if write_stats {
                            update_session_duration(&mut lock);
                            if lock.write_json {
                                let current_stats = get_current_stats(lock.stats_proto.clone());
                                if let Err(err) = write_stats_to_json(current_stats) {
                                    error!("Failed to write stats to json: {err:?}");
                                }
                            }
                            next_instant = Instant::now() + WRITE_INTERVAL;
                        }
                    }
                })
                .expect("failed to start session monitor thread"),
        );
        self
    }

    // Stop the netsim stats session.
    //
    // Waits for the session monitor thread to finish and writes
    // the session proto to a json file. Consumes the session.
    pub fn stop(mut self) -> anyhow::Result<()> {
        if !self.handle.as_ref().expect("no session monitor").is_finished() {
            info!("session monitor active, waiting...");
        }

        // Synchronize on session monitor thread
        self.handle.take().map(JoinHandle::join);

        let lock = self.info.read().expect("Could not acquire session lock");
        if lock.write_json {
            write_stats_to_json(lock.stats_proto.clone())?;
        }
        Ok(())
    }
}

/// Update session duration
fn update_session_duration(session_lock: &mut RwLockWriteGuard<'_, SessionInfo>) {
    let duration_secs = session_lock.session_start.elapsed().as_secs();
    session_lock.stats_proto.set_duration_secs(duration_secs);
}

/// Construct current radio stats
fn get_current_stats(mut current_stats: NetsimStats) -> NetsimStats {
    current_stats.radio_stats.extend(get_radio_stats());
    current_stats
}

/// Write netsim stats to json file
fn write_stats_to_json(stats_proto: NetsimStats) -> anyhow::Result<()> {
    let filename = netsimd_temp_dir().join("netsim_session_stats.json");
    let mut file = File::create(filename)?;
    let json = print_to_string(&stats_proto)?;
    file.write(json.as_bytes()).context("Unable to write json session stats")?;
    file.flush()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events;
    use crate::events::{
        ChipAdded, ChipRemoved, DeviceAdded, DeviceRemoved, Event, Events, ShutDown,
    };
    use netsim_proto::stats::NetsimRadioStats;
    use std::sync::Mutex;

    #[test]
    fn test_new() {
        let session: Session = Session::new();
        assert!(session.handle.is_none());
        let lock = session.info.read().expect("Could not acquire session lock");
        assert_eq!(lock.current_device_count, 0);
        assert!(matches!(
            lock.stats_proto,
            NetsimStats { version: Some(_), duration_secs: None, .. }
        ));
        assert_eq!(lock.stats_proto.version.clone().unwrap(), get_version().clone());
        assert_eq!(lock.stats_proto.radio_stats.len(), 0);
    }

    fn setup_session_start_test() -> (Session, Arc<Mutex<Events>>) {
        let mut session = Session::new_internal(false);
        let mut events = events::test::new();
        let events_rx = events::test::subscribe(&mut events);
        session.start(events_rx);
        (session, events)
    }

    fn get_stats_proto(session: &Session) -> NetsimStats {
        session.info.read().expect("Could not acquire session lock").stats_proto.clone()
    }

    #[test]
    fn test_start_and_shutdown() {
        let (mut session, mut events) = setup_session_start_test();

        // we want to be able to check the session time gets incremented
        std::thread::sleep(std::time::Duration::from_secs(1));

        // publish the shutdown afterwards to cause the separate thread to stop
        events::test::publish(
            &mut events,
            Event::ShutDown(ShutDown { reason: "Stop the session".to_string() }),
        );

        // join the handle
        session.handle.take().map(JoinHandle::join);

        let stats_proto = get_stats_proto(&session);

        // check device counts are missing if no device add/remove events occurred
        assert!(stats_proto.device_count.is_none());

        assert!(stats_proto.peak_concurrent_devices.is_none());

        // check the session time is > 0
        assert!(stats_proto.duration_secs.unwrap() > 0u64);
    }

    #[test]
    fn test_start_and_stop() {
        let (session, mut events) = setup_session_start_test();

        // we want to be able to check the session time gets incremented
        std::thread::sleep(std::time::Duration::from_secs(1));

        // publish the shutdown which is required when using `session.stop()`
        events::test::publish(
            &mut events,
            Event::ShutDown(ShutDown { reason: "Stop the session".to_string() }),
        );

        // should not panic or deadlock
        session.stop().unwrap();
    }

    #[test]
    fn test_start_and_device_add() {
        let (mut session, mut events) = setup_session_start_test();

        // we want to be able to check the session time gets incremented
        std::thread::sleep(std::time::Duration::from_secs(1));

        events::test::publish(
            &mut events,
            Event::DeviceAdded(DeviceAdded { builtin: false, ..Default::default() }),
        );

        // publish the shutdown afterwards to cause the separate thread to stop
        events::test::publish(
            &mut events,
            Event::ShutDown(ShutDown { reason: "Stop the session".to_string() }),
        );

        // join the handle
        session.handle.take().map(JoinHandle::join);

        // check device counts were incremented
        assert_eq!(
            session.info.read().expect("Could not acquire session lock").current_device_count,
            1i32
        );
        let stats_proto = get_stats_proto(&session);

        assert_eq!(stats_proto.device_count.unwrap(), 1i32);
        assert_eq!(stats_proto.peak_concurrent_devices.unwrap(), 1i32);
        // check the session time is > 0
        assert!(stats_proto.duration_secs.unwrap() > 0u64);
    }

    #[test]
    fn test_start_and_device_add_and_remove() {
        let (mut session, mut events) = setup_session_start_test();

        // we want to be able to check the session time gets incremented
        std::thread::sleep(std::time::Duration::from_secs(1));

        events::test::publish(
            &mut events,
            Event::DeviceAdded(DeviceAdded { builtin: false, id: 1, ..Default::default() }),
        );

        events::test::publish(
            &mut events,
            Event::DeviceRemoved(DeviceRemoved { builtin: false, id: 1, ..Default::default() }),
        );

        events::test::publish(
            &mut events,
            Event::DeviceAdded(DeviceAdded { builtin: false, id: 2, ..Default::default() }),
        );

        events::test::publish(
            &mut events,
            Event::DeviceRemoved(DeviceRemoved { builtin: false, id: 2, ..Default::default() }),
        );

        // publish the shutdown afterwards to cause the separate thread to stop
        events::test::publish(
            &mut events,
            Event::ShutDown(ShutDown { reason: "Stop the session".to_string() }),
        );

        // join the handle
        session.handle.take().map(JoinHandle::join);

        // check the different device counts were incremented as expected
        assert_eq!(
            session.info.read().expect("Could not acquire session lock").current_device_count,
            0i32
        );
        let stats_proto = get_stats_proto(&session);
        assert_eq!(stats_proto.device_count.unwrap(), 2i32);
        assert_eq!(stats_proto.peak_concurrent_devices.unwrap(), 1i32);

        // check the session time is > 0
        assert!(stats_proto.duration_secs.unwrap() > 0u64);
    }

    #[test]
    fn test_start_and_chip_add_and_remove() {
        let (mut session, mut events) = setup_session_start_test();

        // we want to be able to check the session time gets incremented
        std::thread::sleep(std::time::Duration::from_secs(1));

        events::test::publish(
            &mut events,
            Event::ChipAdded(ChipAdded { builtin: false, chip_id: 0, ..Default::default() }),
        );

        std::thread::sleep(std::time::Duration::from_secs(1));

        // no radio stats until after we remove the chip
        assert_eq!(get_stats_proto(&session).radio_stats.len(), 0usize);

        events::test::publish(
            &mut events,
            Event::ChipRemoved(ChipRemoved {
                chip_id: 0,
                radio_stats: vec![NetsimRadioStats { ..Default::default() }],
                ..Default::default()
            }),
        );

        // publish the shutdown afterwards to cause the separate thread to stop
        events::test::publish(
            &mut events,
            Event::ShutDown(ShutDown { reason: "Stop the session".to_string() }),
        );

        // join the handle
        session.handle.take().map(JoinHandle::join);

        // check devices were not incremented (here we only added and removed the chip)
        assert_eq!(
            session.info.read().expect("Could not acquire session lock").current_device_count,
            0i32
        );

        let stats_proto = get_stats_proto(&session);
        assert_eq!(stats_proto.radio_stats.len(), 1usize);

        // these will still be none since no device level events were processed
        assert!(stats_proto.device_count.is_none());

        assert!(stats_proto.peak_concurrent_devices.is_none());

        // check the session time is > 0
        assert!(stats_proto.duration_secs.unwrap() > 0u64);
    }
}
