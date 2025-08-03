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

//! The internal structure of CaptureInfo and CaptureMaps
//!
//! CaptureInfo is the internal structure of any Capture that includes
//! the protobuf structure. CaptureMaps contains mappings of
//! ChipIdentifier and <FacadeIdentifier, Kind> to CaptureInfo.

use std::collections::btree_map::{Iter, Values};
use std::collections::BTreeMap;
use std::fs::{File, OpenOptions};
use std::io::{Error, ErrorKind, Result};
use std::sync::mpsc::Receiver;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{SystemTime, UNIX_EPOCH};

use super::pcap_util::{write_pcap_header, write_pcapng_header, LinkType};
use log::{info, warn};

use netsim_proto::{
    common::ChipKind,
    model::{Capture as ProtoCapture, State},
};
use protobuf::well_known_types::timestamp::Timestamp;

use crate::config::get_pcap;
use crate::events::{ChipAdded, ChipRemoved, Event};
use crate::resource::clone_captures;

use crate::devices::chip::ChipIdentifier;

/// Internal Capture struct
pub struct CaptureInfo {
    /// Some(File) if the file is opened and capture is actively happening.
    /// None if the file is not opened.
    pub file: Option<File>,
    // Following items will be returned as ProtoCapture. (state: file.is_some())
    id: ChipIdentifier,
    /// ChipKind (BLUETOOTH, WIFI, or UWB)
    pub chip_kind: ChipKind,
    /// Device name
    pub device_name: String,
    /// Size of pcap file
    pub size: usize,
    /// Number of packet records
    pub records: i32,
    /// Timestamp as seconds
    pub seconds: i64,
    /// Timestamp as sub-nanoseconds
    pub nanos: i32,
    /// Boolean status of whether the device is connected to netsim
    pub valid: bool,
    /// Extension (pcap or pcapng)
    pub extension: String,
}

/// Captures contains a recent copy of all chips and their ChipKind, chip_id,
/// and owning device name.
///
/// Information for any recent or ongoing captures is also stored in the ProtoCapture.
pub struct Captures {
    /// A mapping of chip id to CaptureInfo.
    ///
    /// BTreeMap is used for chip_id_to_capture, so that the CaptureInfo can always be
    /// ordered by ChipId. ListCaptureResponse will produce a ordered list of CaptureInfos.
    pub chip_id_to_capture: BTreeMap<ChipIdentifier, Arc<Mutex<CaptureInfo>>>,
}

impl CaptureInfo {
    /// Create an instance of CaptureInfo
    pub fn new(chip_kind: ChipKind, chip_id: ChipIdentifier, device_name: String) -> Self {
        let extension = match chip_kind {
            ChipKind::UWB => "pcapng".to_string(),
            _ => "pcap".to_string(),
        };
        CaptureInfo {
            id: chip_id,
            chip_kind,
            device_name,
            size: 0,
            records: 0,
            seconds: 0,
            nanos: 0,
            valid: true,
            file: None,
            extension,
        }
    }

    /// Creates a pcap file with headers and store it under temp directory.
    ///
    /// The lifecycle of the file is NOT tied to the lifecycle of the struct
    /// Format: /tmp/netsimd/$USER/pcaps/netsim-{chip_id}-{device_name}-{chip_kind}.{extension}
    pub fn start_capture(&mut self) -> Result<()> {
        if self.file.is_some() {
            return Ok(());
        }
        let mut filename = netsim_common::system::netsimd_temp_dir();
        filename.push("pcaps");
        std::fs::create_dir_all(&filename)?;
        filename.push(format!(
            "netsim-{:?}-{:}-{:?}.{}",
            self.id, self.device_name, self.chip_kind, self.extension
        ));
        let mut file = OpenOptions::new().write(true).truncate(true).create(true).open(filename)?;
        let link_type = match self.chip_kind {
            ChipKind::BLUETOOTH => LinkType::BluetoothHciH4WithPhdr,
            ChipKind::BLUETOOTH_BEACON => LinkType::BluetoothHciH4WithPhdr,
            ChipKind::WIFI => LinkType::Ieee80211RadioTap,
            ChipKind::UWB => LinkType::FiraUci,
            _ => return Err(Error::new(ErrorKind::Other, "Unsupported link type")),
        };
        let size = match self.extension.as_str() {
            "pcap" => write_pcap_header(link_type, &mut file)?,
            "pcapng" => write_pcapng_header(link_type, &mut file)?,
            _ => return Err(Error::new(ErrorKind::Other, "Incorrect Extension for file")),
        };
        let timestamp = SystemTime::now().duration_since(UNIX_EPOCH).expect("Time went backwards");
        self.size = size;
        self.records = 0;
        self.seconds = timestamp.as_secs() as i64;
        self.nanos = timestamp.subsec_nanos() as i32;
        self.file = Some(file);
        Ok(())
    }

    /// Closes file by removing ownership of self.file.
    ///
    /// Capture info will still retain the size and record count
    /// So it can be downloaded easily when GetCapture is invoked.
    pub fn stop_capture(&mut self) {
        self.file = None;
    }

    /// Returns a Capture protobuf from CaptureInfo
    pub fn get_capture_proto(&self) -> ProtoCapture {
        let timestamp =
            Timestamp { seconds: self.seconds, nanos: self.nanos, ..Default::default() };
        ProtoCapture {
            id: self.id,
            chip_kind: self.chip_kind.into(),
            device_name: self.device_name.clone(),
            state: match self.file.is_some() {
                true => State::ON.into(),
                false => State::OFF.into(),
            },
            size: self.size as i32,
            records: self.records,
            timestamp: Some(timestamp).into(),
            valid: self.valid,
            ..Default::default()
        }
    }
}

impl Captures {
    /// Create an instance of Captures, which includes 2 empty hashmaps
    pub fn new() -> Self {
        Captures { chip_id_to_capture: BTreeMap::<ChipIdentifier, Arc<Mutex<CaptureInfo>>>::new() }
    }

    /// Returns true if key exists in Captures.chip_id_to_capture
    pub fn contains(&self, key: ChipIdentifier) -> bool {
        self.chip_id_to_capture.contains_key(&key)
    }

    /// Returns an Option of lockable and mutable CaptureInfo with given key
    pub fn get(&mut self, key: ChipIdentifier) -> Option<&mut Arc<Mutex<CaptureInfo>>> {
        self.chip_id_to_capture.get_mut(&key)
    }

    /// Inserts the given CatpureInfo into Captures hashmaps
    pub fn insert(&mut self, capture: CaptureInfo) {
        let chip_id = capture.id;
        let arc_capture = Arc::new(Mutex::new(capture));
        self.chip_id_to_capture.insert(chip_id, arc_capture.clone());
    }

    /// Returns true if chip_id_to_capture is empty
    pub fn is_empty(&self) -> bool {
        self.chip_id_to_capture.is_empty()
    }

    /// Returns an iterable object of chip_id_to_capture hashmap
    pub fn iter(&self) -> Iter<ChipIdentifier, Arc<Mutex<CaptureInfo>>> {
        self.chip_id_to_capture.iter()
    }

    /// Removes a CaptureInfo with given key from Captures
    ///
    /// When Capture is removed, remove from each map and also invoke closing of files.
    pub fn remove(&mut self, key: &ChipIdentifier) {
        if let Some(arc_capture) = self.chip_id_to_capture.get(key) {
            let mut capture = arc_capture.lock().expect("Failed to acquire lock on CaptureInfo");
            // Valid is marked false when chip is disconnected from netsim
            capture.valid = false;
            capture.stop_capture();
        } else {
            info!("key does not exist in Captures");
        }
        // CaptureInfo is not removed even after chip is removed
    }

    /// Returns Values of chip_id_to_capture hashmap values
    pub fn values(&self) -> Values<ChipIdentifier, Arc<Mutex<CaptureInfo>>> {
        self.chip_id_to_capture.values()
    }
}

impl Default for Captures {
    fn default() -> Self {
        Self::new()
    }
}

/// Create a thread to process events that matter to the Capture resource.
///
/// We maintain a CaptureInfo for each chip that has been
/// connected to the simulation. This procedure monitors ChipAdded
/// and ChipRemoved events and updates the collection of CaptureInfo.
///
pub fn spawn_capture_event_subscriber(event_rx: Receiver<Event>) {
    let _ =
        thread::Builder::new().name("capture_event_subscriber".to_string()).spawn(move || loop {
            match event_rx.recv() {
                Ok(Event::ChipAdded(ChipAdded { chip_id, chip_kind, device_name, .. })) => {
                    let mut capture_info =
                        CaptureInfo::new(chip_kind, chip_id, device_name.clone());
                    if get_pcap() {
                        if let Err(err) = capture_info.start_capture() {
                            warn!("{err:?}");
                        }
                    }
                    clone_captures().write().unwrap().insert(capture_info);
                    info!("Capture event: ChipAdded chip_id: {chip_id} device_name: {device_name}");
                }
                Ok(Event::ChipRemoved(ChipRemoved { chip_id, .. })) => {
                    clone_captures().write().unwrap().remove(&chip_id);
                    info!("Capture event: ChipRemoved chip_id: {chip_id}");
                }
                _ => {}
            }
        });
}
