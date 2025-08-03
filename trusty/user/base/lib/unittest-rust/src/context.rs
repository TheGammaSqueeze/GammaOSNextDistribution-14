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

use core::sync::atomic::{AtomicBool, Ordering};

pub static CONTEXT: TestContext = TestContext::new();

#[derive(Debug, Default)]
pub struct TestContext {
    all_ok: AtomicBool,
    hard_fail: AtomicBool,
    skipped: AtomicBool,
}

impl TestContext {
    const fn new() -> Self {
        Self {
            all_ok: AtomicBool::new(true),
            hard_fail: AtomicBool::new(false),
            skipped: AtomicBool::new(false),
        }
    }

    pub fn fail(&self, hard_fail: bool) {
        self.all_ok.store(false, Ordering::Relaxed);
        self.hard_fail.fetch_or(hard_fail, Ordering::Relaxed);
    }

    pub fn skip(&self) {
        self.skipped.store(true, Ordering::Relaxed);
    }

    pub(crate) fn reset(&self) {
        self.all_ok.store(true, Ordering::Relaxed);
        self.hard_fail.store(false, Ordering::Relaxed);
        self.skipped.store(false, Ordering::Relaxed);
    }

    pub(crate) fn skipped(&self) -> bool {
        self.skipped.load(Ordering::Relaxed)
    }

    pub(crate) fn all_ok(&self) -> bool {
        self.all_ok.load(Ordering::Relaxed)
    }

    pub(crate) fn hard_fail(&self) -> bool {
        self.hard_fail.load(Ordering::Relaxed)
    }
}
