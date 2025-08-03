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

use hashlink::LruCache;
use libfuzzer_sys::arbitrary::Arbitrary;
use libfuzzer_sys::fuzz_target;

#[derive(Arbitrary, Debug, Eq, Hash, PartialEq)]
enum Data {
    A,
    B,
    Int { val: u8 },
}

#[derive(Arbitrary, Debug)]
struct LruCacheFuzzInfo {
    capacity: u8,
    commands: Vec<LruCacheMethods>,
}

#[derive(Arbitrary, Debug)]
enum LruCacheMethods {
    Insert { key: Data, value: Data },
    Remove { key: Data },
    ContainsKey { key: Data },
    Get { key: Data },
    EntryOrInsert { key: Data, value: Data },
    Iter,
    Drain,
    Clear,
    Peek { key: Data },
    RemoveLru,
}

fuzz_target!(|info: LruCacheFuzzInfo| {
    let mut cache = LruCache::new(info.capacity.into());
    for command in info.commands {
        match command {
            LruCacheMethods::Insert { key, value } => {
                cache.insert(key, value);
            }
            LruCacheMethods::Remove { key } => {
                cache.remove(&key);
            }
            LruCacheMethods::ContainsKey { key } => {
                cache.contains_key(&key);
            }
            LruCacheMethods::Get { key } => {
                cache.get(&key);
            }
            LruCacheMethods::EntryOrInsert { key, value } => {
                cache.entry(key).or_insert(value);
            }
            LruCacheMethods::Iter => {
                std::hint::black_box(cache.iter().count());
            }
            LruCacheMethods::Drain => {
                std::hint::black_box(cache.drain().count());
            }
            LruCacheMethods::Clear => {
                cache.clear();
            }
            LruCacheMethods::Peek { key } => {
                cache.peek(&key);
            }
            LruCacheMethods::RemoveLru => {
                cache.remove_lru();
            }
        }
    }
});
