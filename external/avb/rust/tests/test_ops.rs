// Copyright 2023, The Android Open Source Project
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

//! Provides `avb::Ops` test fixtures.

use avb::{IoError, IoResult, Ops, PublicKeyForPartitionInfo};
use std::{cmp::min, collections::HashMap, ffi::CStr};
#[cfg(feature = "uuid")]
use uuid::Uuid;

/// Represents a single fake partition.
#[derive(Default)]
pub struct FakePartition {
    /// Partition contents.
    pub contents: Vec<u8>,

    /// Whether the partition should report as preloaded or not.
    pub preloaded: bool,

    /// Partition UUID.
    #[cfg(feature = "uuid")]
    pub uuid: Uuid,
}

/// Fake vbmeta key state.
pub struct FakeVbmetaKeyState {
    /// Key trust & rollback index info.
    pub info: PublicKeyForPartitionInfo,

    /// If specified, indicates the specific partition this vbmeta is tied to (for
    /// `validate_public_key_for_partition()`).
    pub for_partition: Option<&'static str>,
}

/// Fake `Ops` test fixture.
///
/// The user is expected to set up the internal values to the desired device state - disk contents,
/// rollback indices, etc. This class then uses this state to implement the avb callback operations.
pub struct TestOps {
    /// Partitions to provide to libavb callbacks.
    pub partitions: HashMap<&'static str, FakePartition>,

    /// Vbmeta public keys as a map of {(key, metadata): state}. Querying unknown keys will
    /// return `IoError::Io`.
    ///
    /// See `add_vbmeta_key*()` functions for simpler wrappers to inject these keys.
    pub vbmeta_keys: HashMap<(Vec<u8>, Option<Vec<u8>>), FakeVbmetaKeyState>,

    /// Rollback indices. Accessing unknown locations will return `IoError::Io`.
    pub rollbacks: HashMap<usize, u64>,

    /// Unlock state. Set an error to simulate IoError during access.
    pub unlock_state: IoResult<bool>,

    /// Persistent named values. Set an error to simulate `IoError` during access. Writing
    /// a non-existent persistent value will create it; to simulate `NoSuchValue` instead,
    /// create an entry with `Err(IoError::NoSuchValue)` as the value.
    pub persistent_values: HashMap<String, IoResult<Vec<u8>>>,
}

impl TestOps {
    /// Adds a partition with the given contents.
    ///
    /// Reduces boilerplate a bit by taking in a raw array and returning a &mut so tests can
    /// do something like this:
    ///
    /// ```
    /// test_ops.add_partition("foo", [1, 2, 3, 4]);
    /// test_ops.add_partition("bar", [0, 0]).preloaded = true;
    /// ```
    pub fn add_partition<T: Into<Vec<u8>>>(
        &mut self,
        name: &'static str,
        contents: T,
    ) -> &mut FakePartition {
        self.partitions.insert(
            name,
            FakePartition {
                contents: contents.into(),
                ..Default::default()
            },
        );
        self.partitions.get_mut(name).unwrap()
    }

    /// Adds a persistent value with the given state.
    ///
    /// Reduces boilerplate by allowing array input:
    ///
    /// ```
    /// test_ops.add_persistent_value("foo", Ok(b"contents"));
    /// test_ops.add_persistent_value("bar", Err(IoError::NoSuchValue));
    /// ```
    pub fn add_persistent_value(&mut self, name: &str, contents: IoResult<&[u8]>) {
        self.persistent_values
            .insert(name.into(), contents.map(|b| b.into()));
    }

    /// Adds a fake vbmeta key not tied to any partition.
    pub fn add_vbmeta_key(&mut self, key: Vec<u8>, metadata: Option<Vec<u8>>, trusted: bool) {
        self.vbmeta_keys.insert(
            (key, metadata),
            FakeVbmetaKeyState {
                // `rollback_index_location` doesn't matter in this case, it will be read from
                // the vbmeta blob.
                info: PublicKeyForPartitionInfo {
                    trusted,
                    rollback_index_location: 0,
                },
                for_partition: None,
            },
        );
    }

    /// Adds a fake vbmeta key tied to the given partition and rollback index location.
    pub fn add_vbmeta_key_for_partition(
        &mut self,
        key: Vec<u8>,
        metadata: Option<Vec<u8>>,
        trusted: bool,
        partition: &'static str,
        rollback_index_location: u32,
    ) {
        self.vbmeta_keys.insert(
            (key, metadata),
            FakeVbmetaKeyState {
                info: PublicKeyForPartitionInfo {
                    trusted,
                    rollback_index_location,
                },
                for_partition: Some(partition),
            },
        );
    }
}

impl Default for TestOps {
    fn default() -> Self {
        Self {
            partitions: HashMap::new(),
            vbmeta_keys: HashMap::new(),
            rollbacks: HashMap::new(),
            unlock_state: Err(IoError::Io),
            persistent_values: HashMap::new(),
        }
    }
}

impl Ops for TestOps {
    fn read_from_partition(
        &mut self,
        partition: &CStr,
        offset: i64,
        buffer: &mut [u8],
    ) -> IoResult<usize> {
        let partition = self
            .partitions
            .get(partition.to_str()?)
            .ok_or(IoError::NoSuchPartition)?;

        // We should never be trying to read a preloaded partition from disk since we already
        // have it available in memory.
        assert!(!partition.preloaded);

        let contents = &partition.contents;

        // Negative offset means count backwards from the end.
        let offset = {
            if offset < 0 {
                offset
                    .checked_add(i64::try_from(contents.len()).unwrap())
                    .unwrap()
            } else {
                offset
            }
        };
        if offset < 0 {
            return Err(IoError::RangeOutsidePartition);
        }
        let offset = usize::try_from(offset).unwrap();

        if offset >= contents.len() {
            return Err(IoError::RangeOutsidePartition);
        }

        // Truncating is allowed for reads past the partition end.
        let end = min(offset.checked_add(buffer.len()).unwrap(), contents.len());
        let bytes_read = end - offset;

        buffer[..bytes_read].copy_from_slice(&contents[offset..end]);
        Ok(bytes_read)
    }

    fn get_preloaded_partition(&mut self, partition: &CStr) -> IoResult<&[u8]> {
        match self.partitions.get(partition.to_str()?) {
            Some(FakePartition {
                contents,
                preloaded: true,
                ..
            }) => Ok(&contents[..]),
            _ => Err(IoError::NotImplemented),
        }
    }

    fn validate_vbmeta_public_key(
        &mut self,
        public_key: &[u8],
        public_key_metadata: Option<&[u8]>,
    ) -> IoResult<bool> {
        self.vbmeta_keys
            // The compiler can't match (&[u8], Option<&[u8]>) to keys of type
            // (Vec<u8>, Option<Vec<u8>>) so we turn the &[u8] into vectors here. This is a bit
            // inefficient, but it's simple which is more important for tests than efficiency.
            .get(&(public_key.to_vec(), public_key_metadata.map(|m| m.to_vec())))
            .ok_or(IoError::Io)
            .map(|k| k.info.trusted)
    }

    fn read_rollback_index(&mut self, location: usize) -> IoResult<u64> {
        self.rollbacks.get(&location).ok_or(IoError::Io).copied()
    }

    fn write_rollback_index(&mut self, location: usize, index: u64) -> IoResult<()> {
        *(self.rollbacks.get_mut(&location).ok_or(IoError::Io)?) = index;
        Ok(())
    }

    fn read_is_device_unlocked(&mut self) -> IoResult<bool> {
        self.unlock_state.clone()
    }

    #[cfg(feature = "uuid")]
    fn get_unique_guid_for_partition(&mut self, partition: &CStr) -> IoResult<Uuid> {
        self.partitions
            .get(partition.to_str()?)
            .map(|p| p.uuid)
            .ok_or(IoError::NoSuchPartition)
    }

    fn get_size_of_partition(&mut self, partition: &CStr) -> IoResult<u64> {
        self.partitions
            .get(partition.to_str()?)
            .map(|p| u64::try_from(p.contents.len()).unwrap())
            .ok_or(IoError::NoSuchPartition)
    }

    fn read_persistent_value(&mut self, name: &CStr, value: &mut [u8]) -> IoResult<usize> {
        match self
            .persistent_values
            .get(name.to_str()?)
            .ok_or(IoError::NoSuchValue)?
        {
            // If we were given enough space, write the value contents.
            Ok(contents) if contents.len() <= value.len() => {
                value[..contents.len()].clone_from_slice(contents);
                Ok(contents.len())
            }
            // Not enough space, tell the caller how much we need.
            Ok(contents) => Err(IoError::InsufficientSpace(contents.len())),
            // Simulated error, return it.
            Err(e) => Err(e.clone()),
        }
    }

    fn write_persistent_value(&mut self, name: &CStr, value: &[u8]) -> IoResult<()> {
        let name = name.to_str()?;

        // If the test requested a simulated error on this value, return it.
        if let Some(Err(e)) = self.persistent_values.get(name) {
            return Err(e.clone());
        }

        self.persistent_values
            .insert(name.to_string(), Ok(value.to_vec()));
        Ok(())
    }

    fn erase_persistent_value(&mut self, name: &CStr) -> IoResult<()> {
        let name = name.to_str()?;

        // If the test requested a simulated error on this value, return it.
        if let Some(Err(e)) = self.persistent_values.get(name) {
            return Err(e.clone());
        }

        self.persistent_values.remove(name);
        Ok(())
    }

    fn validate_public_key_for_partition(
        &mut self,
        partition: &CStr,
        public_key: &[u8],
        public_key_metadata: Option<&[u8]>,
    ) -> IoResult<PublicKeyForPartitionInfo> {
        let key = self
            .vbmeta_keys
            .get(&(public_key.to_vec(), public_key_metadata.map(|m| m.to_vec())))
            .ok_or(IoError::Io)?;

        if let Some(for_partition) = key.for_partition {
            if for_partition == partition.to_str()? {
                // The key is registered for this partition; return its info.
                return Ok(key.info);
            }
        }

        // No match.
        Err(IoError::Io)
    }
}
