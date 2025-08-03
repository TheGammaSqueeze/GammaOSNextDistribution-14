// Copyright 2021, The Android Open Source Project
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

#![allow(missing_docs)]
#![allow(unused_must_use)]
#![no_main]

use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;
use rustutils::system_properties;
use std::cell::RefCell;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::{fmt, thread, time};

thread_local! {
    static COUNTER: RefCell<u64> = RefCell::new(0);
}

#[derive(Arbitrary, Clone, Debug)]
enum WritableProperty {
    Fuzzer1,
    Fuzzer2,
}

#[derive(Arbitrary, Clone, Debug)]
enum Property {
    KeystoreBootLevel,
    Random { name: String },
    Unique,
    Writable { prop: WritableProperty },
}

impl fmt::Display for Property {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", match self {
            Property::KeystoreBootLevel => "keystore.boot_level".to_string(),
            Property::Random { name } => name.to_string(),
            Property::Unique => COUNTER.with(|counter| {
                let val = *counter.borrow();
                *counter.borrow_mut() += 1;
                format!("unique.fuzz.prop.{}", val)
            }),
            Property::Writable { prop } => prop.to_string(),
        })
    }
}

impl fmt::Display for WritableProperty {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", match self {
            WritableProperty::Fuzzer1 => "unique.fuzz.prop".to_string(),
            WritableProperty::Fuzzer2 => "unique.fuzz.two.prop".to_string(),
        })
    }
}

#[derive(Arbitrary, Debug)]
enum Command {
    Read { prop: Property },
    Write { prop: WritableProperty, value: String },
    WatcherRead { prop: Property },
    WatcherWait { value: u8 },
}

fuzz_target!(|commands: Vec<Command>| {
    for command in commands {
        match command {
            Command::Read { prop } => {
                system_properties::read(&prop.to_string());
            }
            Command::Write { prop, value } => {
                system_properties::write(&prop.to_string(), &value);
            }
            Command::WatcherRead { prop } => {
                if let Ok(mut watcher) = system_properties::PropertyWatcher::new(&prop.to_string()) {
                    watcher.read(|_n, v| Ok(v.to_string()));
                }
            }
            Command::WatcherWait { value } => {
                // We want to ensure that we choose a property that can be written,
                // or else we'd just have to implement a timeout and do nothing,
                // so we use a hardcoded valid property.
                let prop_str = "keystore.boot_level";
                let waited = Arc::new(AtomicBool::new(false));
                let waited_clone = waited.clone();
                // Spawn a thread that will wait for a change to the property.
                let waiter = thread::spawn(move || {
                    let result = match system_properties::PropertyWatcher::new(prop_str) {
                        Ok(mut watcher) => watcher.wait(None),
                        Err(e) => Err(e),
                    };
                    waited_clone.store(true, Ordering::Relaxed);
                    result
                });
                // Write the property in a loop (so we're sure to follow the wait call).
                let mut cur_value = value;
                while !waited.load(Ordering::Relaxed) {
                    thread::sleep(time::Duration::from_millis(1));
                    system_properties::write(prop_str, &cur_value.to_string());
                    cur_value = cur_value.wrapping_add(1);
                }
                waiter.join();
            }
        }
    }
});
