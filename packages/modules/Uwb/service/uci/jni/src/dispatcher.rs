// Copyright 2022, The Android Open Source Project
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

//! Implementation of Dispatcher and related methods.

use crate::notification_manager_android::NotificationManagerAndroidBuilder;

use std::collections::HashMap;
use std::ops::Deref;
use std::sync::{Arc, RwLock, RwLockReadGuard};

use jni::objects::{GlobalRef, JObject, JString};
use jni::{JNIEnv, JavaVM, MonitorGuard};
use lazy_static::lazy_static;
use log::error;
use tokio::runtime::{Builder as RuntimeBuilder, Runtime};
use uci_hal_android::uci_hal_android::UciHalAndroid;
use uwb_core::error::{Error, Result};
use uwb_core::uci::pcapng_uci_logger_factory::PcapngUciLoggerFactoryBuilder;
use uwb_core::uci::uci_logger::UciLoggerMode;
use uwb_core::uci::uci_logger_factory::UciLoggerFactory;
use uwb_core::uci::uci_manager_sync::UciManagerSync;
use uwb_core::uci::UciManagerImpl;

lazy_static! {
    /// Shared unique dispatcher that may be created and deleted during runtime.
    static ref DISPATCHER: RwLock<Option<Dispatcher>> = RwLock::new(None);
}

/// Dispatcher is managed by Java side. Construction and Destruction are provoked by JNI function
/// nativeDispatcherNew and nativeDispatcherDestroy respectively.
/// Destruction does NOT wait until the spawned threads are closed.
pub(crate) struct Dispatcher {
    pub manager_map: HashMap<String, UciManagerSync<UciManagerImpl>>,
    _runtime: Runtime,
}
impl Dispatcher {
    /// Constructs Dispatcher.
    fn new<T: AsRef<str>>(
        vm: &'static Arc<JavaVM>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
        chip_ids: &[T],
    ) -> Result<Dispatcher> {
        let runtime = RuntimeBuilder::new_multi_thread()
            .thread_name("UwbService")
            .enable_all()
            .build()
            .map_err(|_| Error::ForeignFunctionInterface)?;
        let mut manager_map = HashMap::<String, UciManagerSync<UciManagerImpl>>::new();
        let mut log_file_factory = PcapngUciLoggerFactoryBuilder::new()
            .log_path("/data/misc/apexdata/com.android.uwb/log".into())
            .filename_prefix("uwb_uci".to_owned())
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .ok_or(Error::Unknown)?;
        for chip_id in chip_ids {
            let logger = log_file_factory.build_logger(chip_id.as_ref()).ok_or(Error::Unknown)?;
            let manager = UciManagerSync::new(
                UciHalAndroid::new(chip_id.as_ref()),
                NotificationManagerAndroidBuilder {
                    chip_id: chip_id.as_ref().to_owned(),
                    vm,
                    class_loader_obj: class_loader_obj.clone(),
                    callback_obj: callback_obj.clone(),
                },
                logger,
                UciLoggerMode::Filtered,
                runtime.handle().to_owned(),
            )?;
            manager_map.insert(chip_id.as_ref().to_string(), manager);
        }
        Ok(Self { manager_map, _runtime: runtime })
    }

    /// Sets log mode for all chips.
    pub fn set_logger_mode(&self, logger_mode: UciLoggerMode) -> Result<()> {
        for (_, manager) in self.manager_map.iter() {
            manager.set_logger_mode(logger_mode.clone())?;
        }
        Ok(())
    }

    /// Constructs the unique dispatcher.
    pub fn new_dispatcher<T: AsRef<str>>(
        vm: &'static Arc<JavaVM>,
        class_loader_obj: GlobalRef,
        callback_obj: GlobalRef,
        chip_ids: &[T],
    ) -> Result<()> {
        if DISPATCHER.try_read().map_err(|_| Error::Unknown)?.is_some() {
            error!("UCI JNI: Dispatcher already exists when trying to create.");
            return Err(Error::BadParameters);
        }
        let dispatcher = Dispatcher::new(vm, class_loader_obj, callback_obj, chip_ids)?;
        DISPATCHER.write().map_err(|_| Error::Unknown)?.replace(dispatcher);
        Ok(())
    }

    /// Gets pointer value of the unique dispatcher
    pub fn get_dispatcher_ptr() -> Result<*const Dispatcher> {
        let read_lock = DISPATCHER.read().map_err(|_| Error::Unknown)?;
        match &*read_lock {
            Some(dispatcher_ref) => Ok(dispatcher_ref),
            None => Err(Error::BadParameters),
        }
    }

    /// Destroys the unique Dispather.
    pub fn destroy_dispatcher() -> Result<()> {
        if DISPATCHER.try_read().map_err(|_| Error::Unknown)?.is_none() {
            error!("UCI JNI: Dispatcher already does not exist when trying to destroy.");
            return Err(Error::BadParameters);
        }
        let _ = DISPATCHER.write().map_err(|_| Error::Unknown)?.take();
        Ok(())
    }

    /// Gets reference to the unique Dispatcher.
    pub fn get_dispatcher<'a>(env: JNIEnv<'a>, obj: JObject<'a>) -> Result<GuardedDispatcher<'a>> {
        let jni_guard = env.lock_obj(obj).map_err(|_| Error::ForeignFunctionInterface)?;
        let read_lock = DISPATCHER.read().map_err(|_| Error::Unknown)?;
        GuardedDispatcher::new(jni_guard, read_lock)
    }

    /// Gets reference to UciManagerSync with chip_id.
    pub fn get_uci_manager<'a>(
        env: JNIEnv<'a>,
        obj: JObject<'a>,
        chip_id: JString,
    ) -> Result<GuardedUciManager<'a>> {
        let guarded_dispatcher = Self::get_dispatcher(env, obj)?;
        let chip_id_str =
            String::from(env.get_string(chip_id).map_err(|_| Error::ForeignFunctionInterface)?);
        guarded_dispatcher.into_guarded_uci_manager(&chip_id_str)
    }
}

/// Lifetimed reference to UciManagerSync that locks Java object while reference is alive.
pub(crate) struct GuardedUciManager<'a> {
    _jni_guard: MonitorGuard<'a>,
    read_lock: RwLockReadGuard<'a, Option<Dispatcher>>,
    chip_id: String,
}

impl<'a> Deref for GuardedUciManager<'a> {
    type Target = UciManagerSync<UciManagerImpl>;
    fn deref(&self) -> &Self::Target {
        // Unwrap GuardedUciManager will not panic since content is checked at creation.
        self.read_lock.as_ref().unwrap().manager_map.get(&self.chip_id).unwrap()
    }
}

/// Lifetimed reference to Dispatcher that locks Java object while reference is alive.
pub(crate) struct GuardedDispatcher<'a> {
    _jni_guard: MonitorGuard<'a>,
    read_lock: RwLockReadGuard<'a, Option<Dispatcher>>,
}

impl<'a> GuardedDispatcher<'a> {
    /// Constructor:
    pub fn new(
        jni_guard: MonitorGuard<'a>,
        read_lock: RwLockReadGuard<'a, Option<Dispatcher>>,
    ) -> Result<Self> {
        // Check RwLockReadGuard contains Dispatcher:
        if read_lock.is_none() {
            return Err(Error::BadParameters);
        };
        Ok(GuardedDispatcher { _jni_guard: jni_guard, read_lock })
    }

    /// Conversion to GuardedUciManager:
    pub fn into_guarded_uci_manager(self, chip_id: &str) -> Result<GuardedUciManager<'a>> {
        let _uci_manager = self.manager_map.get(chip_id).ok_or(Error::BadParameters)?;
        Ok(GuardedUciManager {
            _jni_guard: self._jni_guard,
            read_lock: self.read_lock,
            chip_id: chip_id.to_owned(),
        })
    }
}

impl<'a> Deref for GuardedDispatcher<'a> {
    type Target = Dispatcher;
    fn deref(&self) -> &Self::Target {
        // Unwrap GuardedDispatcher will not panic since content is checked at creation.
        self.read_lock.as_ref().unwrap()
    }
}
