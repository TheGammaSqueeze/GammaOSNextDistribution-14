// Copyright (C) 2021 The Android Open Source Project
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

//! This crate provides the PropertyWatcher type, which watches for changes
//! in Android system properties.

use anyhow::Context;
use libc::timespec;
use std::os::raw::c_char;
use std::ptr::null;
use std::{
    ffi::{c_uint, c_void, CStr, CString},
    str::Utf8Error,
    time::{Duration, Instant},
};
use system_properties_bindgen::prop_info as PropInfo;
use thiserror::Error;

#[doc(hidden)]
pub mod parsers_formatters;

/// Errors this crate can generate
#[derive(Error, Debug)]
pub enum PropertyWatcherError {
    /// We can't watch for a property whose name contains a NUL character.
    #[error("Cannot convert name to C string")]
    BadNameError(#[from] std::ffi::NulError),
    /// We can only watch for properties that exist when the watcher is created.
    #[error("System property is absent")]
    SystemPropertyAbsent,
    /// System properties are not initialized
    #[error("System properties are not initialized.")]
    Uninitialized,
    /// __system_property_wait timed out.
    #[error("Wait failed")]
    WaitFailed,
    /// read callback was not called
    #[error("__system_property_read_callback did not call callback")]
    ReadCallbackNotCalled,
    /// read callback gave us a NULL pointer
    #[error("__system_property_read_callback gave us a NULL pointer instead of a string")]
    MissingCString,
    /// read callback gave us a bad C string
    #[error("__system_property_read_callback gave us a non-UTF8 C string")]
    BadCString(#[from] Utf8Error),
    /// read callback returned an error
    #[error("Callback failed")]
    CallbackError(#[from] anyhow::Error),
    /// Failure in setting the system property
    #[error("__system_property_set failed.")]
    SetPropertyFailed,
}

/// Result type specific for this crate.
pub type Result<T> = std::result::Result<T, PropertyWatcherError>;

/// PropertyWatcher takes the name of an Android system property such
/// as `keystore.boot_level`; it can report the current value of this
/// property, or wait for it to change.
pub struct PropertyWatcher {
    prop_name: CString,
    prop_info: *const PropInfo,
    serial: c_uint,
}

impl PropertyWatcher {
    /// Create a PropertyWatcher for the named system property.
    pub fn new(name: &str) -> Result<Self> {
        Ok(Self {
            prop_name: CString::new(name)?,
            prop_info: null(),
            serial: 0,
        })
    }

    // Lazy-initializing accessor for self.prop_info.
    fn get_prop_info(&mut self) -> Option<*const PropInfo> {
        if self.prop_info.is_null() {
            // SAFETY: Input and output are both const. The returned pointer is valid for the
            // lifetime of the program.
            self.prop_info = unsafe {
                system_properties_bindgen::__system_property_find(self.prop_name.as_ptr())
            };
        }
        if self.prop_info.is_null() {
            None
        } else {
            Some(self.prop_info)
        }
    }

    fn read_raw(prop_info: *const PropInfo, mut f: impl FnMut(Option<&CStr>, Option<&CStr>)) {
        // Unsafe function converts values passed to us by
        // __system_property_read_callback to Rust form
        // and pass them to inner callback.
        unsafe extern "C" fn callback(
            res_p: *mut c_void,
            name: *const c_char,
            value: *const c_char,
            _: c_uint,
        ) {
            let name = if name.is_null() {
                None
            } else {
                // SAFETY: system property names are null-terminated C strings in UTF-8. See
                // IsLegalPropertyName in system/core/init/util.cpp.
                Some(unsafe { CStr::from_ptr(name) })
            };
            let value = if value.is_null() {
                None
            } else {
                // SAFETY: system property values are null-terminated C strings in UTF-8. See
                // IsLegalPropertyValue in system/core/init/util.cpp.
                Some(unsafe { CStr::from_ptr(value) })
            };
            // SAFETY: We converted the FnMut from `f` to a void pointer below, now we convert it
            // back.
            let f = unsafe { &mut *res_p.cast::<&mut dyn FnMut(Option<&CStr>, Option<&CStr>)>() };
            f(name, value);
        }

        let mut f: &mut dyn FnMut(Option<&CStr>, Option<&CStr>) = &mut f;

        // SAFETY: We convert the FnMut to a void pointer, and unwrap it in our callback.
        unsafe {
            system_properties_bindgen::__system_property_read_callback(
                prop_info,
                Some(callback),
                &mut f as *mut _ as *mut c_void,
            )
        }
    }

    /// Call the passed function, passing it the name and current value
    /// of this system property. See documentation for
    /// `__system_property_read_callback` for details.
    /// Returns an error if the property is empty or doesn't exist.
    pub fn read<T, F>(&mut self, mut f: F) -> Result<T>
    where
        F: FnMut(&str, &str) -> anyhow::Result<T>,
    {
        let prop_info = self
            .get_prop_info()
            .ok_or(PropertyWatcherError::SystemPropertyAbsent)?;
        let mut result = Err(PropertyWatcherError::ReadCallbackNotCalled);
        Self::read_raw(prop_info, |name, value| {
            // use a wrapping closure as an erzatz try block.
            result = (|| {
                let name = name.ok_or(PropertyWatcherError::MissingCString)?.to_str()?;
                let value = value
                    .ok_or(PropertyWatcherError::MissingCString)?
                    .to_str()?;
                f(name, value).map_err(PropertyWatcherError::CallbackError)
            })()
        });
        result
    }

    // Waits for the property that self is watching to be created. Returns immediately if the
    // property already exists.
    fn wait_for_property_creation_until(&mut self, until: Option<Instant>) -> Result<()> {
        let mut global_serial = 0;
        loop {
            match self.get_prop_info() {
                Some(_) => return Ok(()),
                None => {
                    let remaining_timeout = remaining_time_until(until);
                    // SAFETY: The function modifies only global_serial, and has no side-effects.
                    if !unsafe {
                        // Wait for a global serial number change, then try again. On success,
                        // the function will update global_serial with the last version seen.
                        system_properties_bindgen::__system_property_wait(
                            null(),
                            global_serial,
                            &mut global_serial,
                            if let Some(remaining_timeout) = &remaining_timeout {
                                remaining_timeout
                            } else {
                                null()
                            },
                        )
                    } {
                        return Err(PropertyWatcherError::WaitFailed);
                    }
                }
            }
        }
    }

    /// Waits until the system property changes, or `until` is reached.
    ///
    /// This records the serial number of the last change, so race conditions are avoided.
    fn wait_for_property_change_until(&mut self, until: Option<Instant>) -> Result<()> {
        // If the property is null, then wait for it to be created. Subsequent waits will
        // skip this step and wait for our specific property to change.
        if self.prop_info.is_null() {
            return self.wait_for_property_creation_until(None);
        }

        let remaining_timeout = remaining_time_until(until);
        let mut new_serial = self.serial;
        // SAFETY: All arguments are private to PropertyWatcher so we can be confident they are
        // valid.
        if !unsafe {
            system_properties_bindgen::__system_property_wait(
                self.prop_info,
                self.serial,
                &mut new_serial,
                if let Some(remaining_timeout) = &remaining_timeout {
                    remaining_timeout
                } else {
                    null()
                },
            )
        } {
            return Err(PropertyWatcherError::WaitFailed);
        }
        self.serial = new_serial;
        Ok(())
    }

    /// Waits for the system property to change, or the timeout to elapse.
    ///
    /// This records the serial number of the last change, so race conditions are avoided.
    pub fn wait(&mut self, timeout: Option<Duration>) -> Result<()> {
        let until = timeout.map(|timeout| Instant::now() + timeout);
        self.wait_for_property_change_until(until)
    }

    /// Waits until the property exists and has the given value.
    pub fn wait_for_value(
        &mut self,
        expected_value: &str,
        timeout: Option<Duration>,
    ) -> Result<()> {
        let until = timeout.map(|timeout| Instant::now() + timeout);

        self.wait_for_property_creation_until(until)?;

        while self.read(|_, value| Ok(value != expected_value))? {
            self.wait_for_property_change_until(until)?;
        }

        Ok(())
    }
}

/// Reads a system property.
///
/// Returns `Ok(None)` if the property doesn't exist.
pub fn read(name: &str) -> Result<Option<String>> {
    match PropertyWatcher::new(name)?.read(|_name, value| Ok(value.to_owned())) {
        Ok(value) => Ok(Some(value)),
        Err(PropertyWatcherError::SystemPropertyAbsent) => Ok(None),
        Err(e) => Err(e),
    }
}

fn parse_bool(value: &str) -> Option<bool> {
    match value {
        "1" | "y" | "yes" | "on" | "true" => Some(true),
        "0" | "n" | "no" | "off" | "false" => Some(false),
        _ => None,
    }
}

/// Returns the duration remaining until the given instant.
///
/// Returns `None` if `None` is passed in, or `Some(0)` if `until` is in the past.
fn remaining_time_until(until: Option<Instant>) -> Option<timespec> {
    until.map(|until| {
        duration_to_timespec(
            until
                .checked_duration_since(Instant::now())
                .unwrap_or_default(),
        )
    })
}

/// Converts the given `Duration` to a C `timespec`.
fn duration_to_timespec(duration: Duration) -> timespec {
    timespec {
        tv_sec: duration.as_secs().try_into().unwrap(),
        tv_nsec: duration.subsec_nanos() as _,
    }
}

/// Returns true if the system property `name` has the value "1", "y", "yes", "on", or "true",
/// false for "0", "n", "no", "off", or "false", or `default_value` otherwise.
pub fn read_bool(name: &str, default_value: bool) -> Result<bool> {
    Ok(read(name)?
        .as_deref()
        .and_then(parse_bool)
        .unwrap_or(default_value))
}

/// Writes a system property.
pub fn write(name: &str, value: &str) -> Result<()> {
    if
    // SAFETY: Input and output are both const and valid strings.
    unsafe {
        // If successful, __system_property_set returns 0, otherwise, returns -1.
        system_properties_bindgen::__system_property_set(
            CString::new(name)
                .context("Failed to construct CString from name.")?
                .as_ptr(),
            CString::new(value)
                .context("Failed to construct CString from value.")?
                .as_ptr(),
        )
    } == 0
    {
        Ok(())
    } else {
        Err(PropertyWatcherError::SetPropertyFailed)
    }
}

/// Iterates through the properties (that the current process is allowed to access).
pub fn foreach<F>(mut f: F) -> Result<()>
where
    F: FnMut(&str, &str),
{
    extern "C" fn read_callback<F: FnMut(&str, &str)>(
        res_p: *mut c_void,
        name: *const c_char,
        value: *const c_char,
        _: c_uint,
    ) {
        // SAFETY: system property names are null-terminated C strings in UTF-8. See
        // IsLegalPropertyName in system/core/init/util.cpp.
        let name = unsafe { CStr::from_ptr(name) }.to_str().unwrap();
        // SAFETY: system property values are null-terminated C strings in UTF-8. See
        // IsLegalPropertyValue in system/core/init/util.cpp.
        let value = unsafe { CStr::from_ptr(value) }.to_str().unwrap();

        let ptr = res_p as *mut F;
        // SAFETY: ptr points to the API user's callback, which was cast to `*mut c_void` below.
        // Here we're casting it back.
        let f = unsafe { ptr.as_mut() }.unwrap();
        f(name, value);
    }

    extern "C" fn foreach_callback<F: FnMut(&str, &str)>(
        prop_info: *const PropInfo,
        res_p: *mut c_void,
    ) {
        // SAFETY: FFI call with an internal callback function in Rust, with other parameters
        // passed through.
        unsafe {
            system_properties_bindgen::__system_property_read_callback(
                prop_info,
                Some(read_callback::<F>),
                res_p,
            )
        }
    }

    // SAFETY: FFI call with an internal callback function in Rust, and another client's callback
    // that's cast only for our own use right above.
    let retval = unsafe {
        system_properties_bindgen::__system_property_foreach(
            Some(foreach_callback::<F>),
            &mut f as *mut _ as *mut c_void,
        )
    };
    if retval < 0 {
        Err(PropertyWatcherError::Uninitialized)
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn parse_bool_test() {
        for s in ["1", "y", "yes", "on", "true"] {
            assert_eq!(parse_bool(s), Some(true), "testing with {}", s);
        }
        for s in ["0", "n", "no", "off", "false"] {
            assert_eq!(parse_bool(s), Some(false), "testing with {}", s);
        }
        for s in ["random", "00", "of course", "no way", "YES", "Off"] {
            assert_eq!(parse_bool(s), None, "testing with {}", s);
        }
    }

    #[test]
    fn read_absent_bool_test() {
        let prop = "certainly.does.not.exist";
        assert!(matches!(read(prop), Ok(None)));
        assert!(read_bool(prop, true).unwrap_or(false));
        assert!(!read_bool(prop, false).unwrap_or(true));
    }

    #[test]
    fn foreach_test() {
        let mut properties = Vec::new();
        assert!(foreach(|name, value| {
            properties.push((name.to_owned(), value.to_owned()));
        })
        .is_ok());
        // Assuming the test runs on Android, any process can at least see some system properties.
        assert!(!properties.is_empty());
    }
}
