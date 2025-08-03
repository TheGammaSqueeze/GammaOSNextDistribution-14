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

//! A module with mpmc channels for distributing global events.

use netsim_proto::common::ChipKind;
use std::sync::mpsc::{channel, Receiver, Sender};

use crate::devices::chip::ChipIdentifier;
use crate::devices::device::DeviceIdentifier;
use netsim_proto::stats::NetsimRadioStats as ProtoRadioStats;

use lazy_static::lazy_static;
use std::sync::{Arc, Mutex};

// Publish the event to all subscribers
pub fn publish(event: Event) {
    get_events().lock().expect("Failed to acquire lock on events").publish(event);
}

// Subscribe to events over the receiver
pub fn subscribe() -> Receiver<Event> {
    get_events().lock().expect("Failed to acquire locks on events").subscribe()
}

#[derive(Clone, Debug, Default)]
pub struct DeviceAdded {
    pub id: DeviceIdentifier,
    pub name: String,
    pub builtin: bool,
}

#[derive(Clone, Debug, Default)]
pub struct DeviceRemoved {
    pub id: DeviceIdentifier,
    pub name: String,
    pub builtin: bool,
}

#[derive(Clone, Debug, Default)]
pub struct DevicePatched {
    pub id: DeviceIdentifier,
    pub name: String,
}

#[derive(Clone, Debug, Default)]
pub struct ChipAdded {
    pub chip_id: ChipIdentifier,
    pub chip_kind: ChipKind,
    pub device_name: String,
    pub builtin: bool,
}

#[derive(Clone, Debug, Default)]
pub struct ChipRemoved {
    pub chip_id: ChipIdentifier,
    pub device_id: DeviceIdentifier,
    pub remaining_nonbuiltin_devices: usize,
    pub radio_stats: Vec<ProtoRadioStats>,
}

#[derive(Clone, Debug, Default)]
pub struct ShutDown {
    pub reason: String,
}

/// Event messages shared across various components in a loosely
/// coupled manner.
#[derive(Clone, Debug)]
pub enum Event {
    DeviceAdded(DeviceAdded),
    DeviceRemoved(DeviceRemoved),
    DevicePatched(DevicePatched),
    DeviceReset,
    ChipAdded(ChipAdded),
    ChipRemoved(ChipRemoved),
    ShutDown(ShutDown),
}

lazy_static! {
    static ref EVENTS: Arc<Mutex<Events>> = Events::new();
}

pub fn get_events() -> Arc<Mutex<Events>> {
    Arc::clone(&EVENTS)
}

/// A multi-producer, multi-consumer broadcast queue based on
/// `std::sync::mpsc`.
///
/// Each Event message `published` is seen by all subscribers.
///
/// Warning: invoke `subscribe()` before `publish()` or else messages
/// will be lost.
///
pub struct Events {
    // For each subscriber this module retrain the sender half and the
    // subscriber reads events from the receiver half.
    subscribers: Vec<Sender<Event>>,
}

impl Events {
    // Events is always owned by multiple publishers and subscribers
    // across threads so return an Arc type.
    fn new() -> Arc<Mutex<Events>> {
        Arc::new(Mutex::new(Self { subscribers: Vec::new() }))
    }

    // Creates a new asynchronous channel, returning the receiver
    // half. All `Event` messages sent through `publish` will become
    // available on the receiver in the same order as it was sent.
    fn subscribe(&mut self) -> Receiver<Event> {
        let (tx, rx) = channel::<Event>();
        self.subscribers.push(tx);
        rx
    }

    // Attempts to send an Event on the events channel.
    pub fn publish(&mut self, msg: Event) {
        if self.subscribers.is_empty() {
            log::warn!("No Subscribers to the event: {msg:?}");
        } else {
            // Any channel with a disconnected receiver will return an
            // error and be removed by retain.
            log::info!("{msg:?}");
            self.subscribers.retain(|subscriber| subscriber.send(msg.clone()).is_ok())
        }
    }
}

// Test public functions to allow testing with local Events struct.
#[cfg(test)]
pub mod test {
    use super::*;

    pub fn new() -> Arc<Mutex<Events>> {
        Events::new()
    }

    pub fn publish(s: &mut Arc<Mutex<Events>>, msg: Event) {
        s.lock().unwrap().publish(msg);
    }

    pub fn subscribe(s: &mut Arc<Mutex<Events>>) -> Receiver<Event> {
        s.lock().unwrap().subscribe()
    }
}

#[cfg(test)]
mod tests {
    use super::Events;
    use super::*;
    use std::sync::Arc;
    use std::thread;

    #[test]
    fn test_subscribe_and_publish() {
        let events = Events::new();

        let events_clone = Arc::clone(&events);
        let rx = events_clone.lock().unwrap().subscribe();
        let handle = thread::spawn(move || match rx.recv() {
            Ok(Event::DeviceAdded(DeviceAdded { id, name, builtin: false })) => {
                assert_eq!(id, 123);
                assert_eq!(name, "Device1");
            }
            _ => panic!("Unexpected event"),
        });

        events.lock().unwrap().publish(Event::DeviceAdded(DeviceAdded {
            id: 123,
            name: "Device1".into(),
            builtin: false,
        }));

        // Wait for the other thread to process the message.
        handle.join().unwrap();
    }

    #[test]
    fn test_publish_to_multiple_subscribers() {
        let events = Events::new();

        let num_subscribers = 10;
        let mut handles = Vec::with_capacity(num_subscribers);
        for _ in 0..num_subscribers {
            let events_clone = Arc::clone(&events);
            let rx = events_clone.lock().unwrap().subscribe();
            let handle = thread::spawn(move || match rx.recv() {
                Ok(Event::DeviceAdded(DeviceAdded { id, name, builtin: false })) => {
                    assert_eq!(id, 123);
                    assert_eq!(name, "Device1");
                }
                _ => panic!("Unexpected event"),
            });
            handles.push(handle);
        }

        events.lock().unwrap().publish(Event::DeviceAdded(DeviceAdded {
            id: 123,
            name: "Device1".into(),
            builtin: false,
        }));

        // Wait for the other threads to process the message.
        for handle in handles {
            handle.join().unwrap();
        }
    }

    #[test]
    // Test the case where the subscriber half of the channel returned
    // by subscribe() is dropped. We expect the subscriber to be auto
    // removed when send() notices an error.
    fn test_publish_to_dropped_subscriber() {
        let events = Events::new();
        let rx = events.lock().unwrap().subscribe();
        assert_eq!(events.lock().unwrap().subscribers.len(), 1);
        std::mem::drop(rx);
        events.lock().unwrap().publish(Event::DeviceAdded(DeviceAdded {
            id: 123,
            name: "Device1".into(),
            builtin: false,
        }));
        assert_eq!(events.lock().unwrap().subscribers.len(), 0);
    }
}
