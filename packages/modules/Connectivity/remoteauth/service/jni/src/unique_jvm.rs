/*
 * Copyright (C) 2023 The Android Open Source Project
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

//! takes a JavaVM to a static reference.
//!
//! JavaVM is shared as multiple JavaVM within a single process is not allowed
//! per [JNI spec](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html)
//! The unique JavaVM need to be shared over (potentially) different threads.

use std::sync::{Arc, Once};

use anyhow::Result;
use jni::JavaVM;

static mut JVM: Option<Arc<JavaVM>> = None;
static INIT: Once = Once::new();
/// set_once sets the unique JavaVM that can be then accessed using get_static_ref()
///
/// The function shall only be called once.
pub(crate) fn set_once(jvm: JavaVM) -> Result<()> {
    // Safety: follows [this pattern](https://doc.rust-lang.org/std/sync/struct.Once.html).
    // Modification to static mut is nested inside call_once.
    unsafe {
        INIT.call_once(|| {
            JVM = Some(Arc::new(jvm));
        });
    }
    Ok(())
}
/// Gets a 'static reference to the unique JavaVM. Returns None if set_once() was never called.
pub(crate) fn get_static_ref() -> Option<&'static Arc<JavaVM>> {
    // Safety: follows [this pattern](https://doc.rust-lang.org/std/sync/struct.Once.html).
    // Modification to static mut is nested inside call_once.
    unsafe { JVM.as_ref() }
}
