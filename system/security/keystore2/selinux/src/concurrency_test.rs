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

use keystore2_selinux::{check_access, Context};
use nix::sched::sched_setaffinity;
use nix::sched::CpuSet;
use nix::unistd::getpid;
use std::thread;
use std::{
    sync::{atomic::AtomicU8, atomic::Ordering, Arc},
    time::{Duration, Instant},
};

#[derive(Clone, Copy)]
struct CatCount(u8, u8, u8, u8);

impl CatCount {
    fn next(&mut self) -> CatCount {
        let result = *self;
        if self.3 == 255 {
            if self.2 == 254 {
                if self.1 == 253 {
                    if self.0 == 252 {
                        self.0 = 255;
                    }
                    self.0 += 1;
                    self.1 = self.0;
                }
                self.1 += 1;
                self.2 = self.1;
            }
            self.2 += 1;
            self.3 = self.2;
        }
        self.3 += 1;
        result
    }

    fn make_string(&self) -> String {
        format!("c{},c{},c{},c{}", self.0, self.1, self.2, self.3)
    }
}

impl Default for CatCount {
    fn default() -> Self {
        Self(0, 1, 2, 3)
    }
}

/// This test calls selinux_check_access concurrently causing access vector cache misses
/// in libselinux avc. The test then checks if any of the threads fails to report back
/// after a burst of access checks. The purpose of the test is to draw out a specific
/// access vector cache corruption that sends a calling thread into an infinite loop.
/// This was observed when keystore2 used libselinux concurrently in a non thread safe
/// way. See b/184006658.
#[test]
fn test_concurrent_check_access() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("keystore2_selinux_concurrency_test")
            .with_max_level(log::LevelFilter::Debug),
    );

    let cpus = num_cpus::get();
    let turnpike = Arc::new(AtomicU8::new(0));
    let complete_count = Arc::new(AtomicU8::new(0));
    let mut threads: Vec<thread::JoinHandle<()>> = Vec::new();

    for i in 0..cpus {
        log::info!("Spawning thread {}", i);
        let turnpike_clone = turnpike.clone();
        let complete_count_clone = complete_count.clone();
        threads.push(thread::spawn(move || {
            let mut cpu_set = CpuSet::new();
            cpu_set.set(i).unwrap();
            sched_setaffinity(getpid(), &cpu_set).unwrap();
            let mut cat_count: CatCount = Default::default();

            log::info!("Thread 0 reached turnpike");
            loop {
                turnpike_clone.fetch_add(1, Ordering::Relaxed);
                loop {
                    match turnpike_clone.load(Ordering::Relaxed) {
                        0 => break,
                        255 => return,
                        _ => {}
                    }
                }

                for _ in 0..250 {
                    let (tctx, sctx, perm, class) = (
                        Context::new("u:object_r:keystore:s0").unwrap(),
                        Context::new(&format!(
                            "u:r:untrusted_app:s0:{}",
                            cat_count.next().make_string()
                        ))
                        .unwrap(),
                        "use",
                        "keystore2_key",
                    );

                    check_access(&sctx, &tctx, class, perm).unwrap();
                }

                complete_count_clone.fetch_add(1, Ordering::Relaxed);
                while complete_count_clone.load(Ordering::Relaxed) as usize != cpus {
                    thread::sleep(Duration::from_millis(5));
                }
            }
        }));
    }

    let mut i = 0;
    let run_time = Instant::now();

    loop {
        const TEST_ITERATIONS: u32 = 500;
        const MAX_SLEEPS: u64 = 500;
        const SLEEP_MILLISECONDS: u64 = 5;
        let mut sleep_count: u64 = 0;
        while turnpike.load(Ordering::Relaxed) as usize != cpus {
            thread::sleep(Duration::from_millis(SLEEP_MILLISECONDS));
            sleep_count += 1;
            assert!(
                sleep_count < MAX_SLEEPS,
                "Waited too long to go ready on iteration {}, only {} are ready",
                i,
                turnpike.load(Ordering::Relaxed)
            );
        }

        if i % 100 == 0 {
            let elapsed = run_time.elapsed().as_secs();
            println!("{:02}:{:02}: Iteration {}", elapsed / 60, elapsed % 60, i);
        }

        // Give the threads some time to reach and spin on the turn pike.
        assert_eq!(turnpike.load(Ordering::Relaxed) as usize, cpus, "i = {}", i);
        if i >= TEST_ITERATIONS {
            turnpike.store(255, Ordering::Relaxed);
            break;
        }

        // Now go.
        complete_count.store(0, Ordering::Relaxed);
        turnpike.store(0, Ordering::Relaxed);
        i += 1;

        // Wait for them to all complete.
        sleep_count = 0;
        while complete_count.load(Ordering::Relaxed) as usize != cpus {
            thread::sleep(Duration::from_millis(SLEEP_MILLISECONDS));
            sleep_count += 1;
            if sleep_count >= MAX_SLEEPS {
                // Enable the following block to park the thread to allow attaching a debugger.
                if false {
                    println!(
                        "Waited {} seconds and we seem stuck. Going to sleep forever.",
                        (MAX_SLEEPS * SLEEP_MILLISECONDS) as f32 / 1000.0
                    );
                    loop {
                        thread::park();
                    }
                } else {
                    assert!(
                        sleep_count < MAX_SLEEPS,
                        "Waited too long to complete on iteration {}, only {} are complete",
                        i,
                        complete_count.load(Ordering::Relaxed)
                    );
                }
            }
        }
    }

    for t in threads {
        t.join().unwrap();
    }
}
