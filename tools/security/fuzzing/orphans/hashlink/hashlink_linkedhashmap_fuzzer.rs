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
#![no_main]

use hashlink::LinkedHashMap;
use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;

const MAX_RESERVE: usize = 1024;

#[derive(Arbitrary, Debug, Eq, Hash, PartialEq)]
enum Data {
    A,
    B,
    Int { val: u8 },
}

#[derive(Arbitrary, Debug)]
enum LinkedHashMapMethods {
    Insert { key: Data, value: Data },
    Remove { key: Data },
    ContainsKey { key: Data },
    Get { key: Data },
    EntryOrInsert { key: Data, value: Data },
    Iter,
    Drain,
    Clear,
    Reserve { additional: usize },
    ShrinkToFit,
}

fuzz_target!(|commands: Vec<LinkedHashMapMethods>| {
    let mut map = LinkedHashMap::new();
    for command in commands {
        match command {
            LinkedHashMapMethods::Insert { key, value } => {
                map.insert(key, value);
            }
            LinkedHashMapMethods::Remove { key } => {
                map.remove(&key);
            }
            LinkedHashMapMethods::ContainsKey { key } => {
                map.contains_key(&key);
            }
            LinkedHashMapMethods::Get { key } => {
                map.get(&key);
            }
            LinkedHashMapMethods::EntryOrInsert { key, value } => {
                map.entry(key).or_insert(value);
            }
            LinkedHashMapMethods::Iter => {
                std::hint::black_box(map.iter().count());
            }
            LinkedHashMapMethods::Drain => {
                std::hint::black_box(map.drain().count());
            }
            LinkedHashMapMethods::Clear => {
                map.clear();
            }
            LinkedHashMapMethods::Reserve { additional } => {
                // Avoid allocating too much memory and crashing the fuzzer.
                map.reserve(additional % MAX_RESERVE);
            }
            LinkedHashMapMethods::ShrinkToFit => {
                map.shrink_to_fit();
            }
        }
    }
});
