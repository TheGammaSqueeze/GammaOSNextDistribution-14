// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, item 2.0 (the "License");
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

//! This file defines PcapngUciLoggerFactory, which implements UciLoggerFactory
//! trait and logging UCI packets into PCAPNG format.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

use log::{debug, error};
use tokio::runtime::Handle;
use tokio::sync::mpsc;

use crate::uci::pcapng_block::{
    BlockBuilder, BlockOption, HeaderBlockBuilder, InterfaceDescriptionBlockBuilder,
};
use crate::uci::uci_logger_factory::UciLoggerFactory;
use crate::uci::uci_logger_pcapng::UciLoggerPcapng;
use crate::utils::consuming_builder_field;

const DEFAULT_LOG_DIR: &str = "/var/log/uwb";
const DEFAULT_FILE_PREFIX: &str = "uwb_uci";
const DEFAULT_BUFFER_SIZE: usize = 10240; // 10 KB
const DEFAUL_FILE_SIZE: usize = 1048576; // 1 MB

/// The PCAPNG log file factory.
pub struct PcapngUciLoggerFactory {
    /// log_writer references to LogWriterActor.
    log_writer: LogWriter,
    /// Maps recording chip-id to interface-id for UciLoggerPcapng.
    ///
    /// Map is forwarded LogWriterActor, the "actor" that log_writer owns which performs
    /// actual writing of files which needs this map to build the InterfaceDescriptionBlock.
    /// Since PCAPNG format defines the interface ID by the order of appearance of IDB inside file,
    /// the "map" is a vector whose index coincides with the interface ID.
    chip_interface_id_map: Vec<String>,
}

impl UciLoggerFactory for PcapngUciLoggerFactory {
    type Logger = UciLoggerPcapng;

    /// PcapngUciLoggerFactory builds UciLoggerPcapng.
    fn build_logger(&mut self, chip_id: &str) -> Option<UciLoggerPcapng> {
        let chip_interface_id = match self.chip_interface_id_map.iter().position(|c| c == chip_id) {
            Some(id) => id as u32,
            None => {
                let id = self.chip_interface_id_map.len() as u32;
                self.chip_interface_id_map.push(chip_id.to_owned());
                if self.log_writer.send_chip(chip_id.to_owned(), id).is_none() {
                    error!("UCI log: associated LogWriterActor is dead");
                    return None;
                }
                id
            }
        };
        Some(UciLoggerPcapng::new(self.log_writer.clone(), chip_interface_id))
    }
}

/// Builder for PCAPNG log file factory.
pub struct PcapngUciLoggerFactoryBuilder {
    /// Buffer size.
    buffer_size: usize,
    /// Max file size:
    file_size: usize,
    /// Filename prefix for log file.
    filename_prefix: String,
    /// Directory for log file.
    log_path: PathBuf,
    /// Range for the rotating index of log files.
    rotate_range: usize,
    /// Tokio Runtime Handle for driving Log.
    runtime_handle: Option<Handle>,
}
impl Default for PcapngUciLoggerFactoryBuilder {
    fn default() -> Self {
        Self {
            buffer_size: DEFAULT_BUFFER_SIZE,
            file_size: DEFAUL_FILE_SIZE,
            filename_prefix: DEFAULT_FILE_PREFIX.to_owned(),
            log_path: PathBuf::from(DEFAULT_LOG_DIR),
            rotate_range: 8,
            runtime_handle: None,
        }
    }
}

impl PcapngUciLoggerFactoryBuilder {
    /// Constructor.
    pub fn new() -> Self {
        PcapngUciLoggerFactoryBuilder::default()
    }

    // Setter methods of each field.
    consuming_builder_field!(runtime_handle, Handle, Some);
    consuming_builder_field!(filename_prefix, String);
    consuming_builder_field!(rotate_range, usize);
    consuming_builder_field!(log_path, PathBuf);
    consuming_builder_field!(buffer_size, usize);
    consuming_builder_field!(file_size, usize);

    /// Builds PcapngUciLoggerFactory
    pub fn build(self) -> Option<PcapngUciLoggerFactory> {
        let file_factory = FileFactory::new(
            self.log_path,
            self.filename_prefix,
            self.buffer_size,
            self.rotate_range,
        );
        let log_writer = LogWriter::new(file_factory, self.file_size, self.runtime_handle?)?;
        let manager = PcapngUciLoggerFactory { log_writer, chip_interface_id_map: Vec::new() };
        Some(manager)
    }
}

#[derive(Clone, Debug)]
pub(crate) enum PcapngLoggerMessage {
    ByteStream(Vec<u8>),
    NewChip((String, u32)),
    Flush(mpsc::UnboundedSender<bool>),
}

/// LogWriterActor performs the log writing and file operations asynchronously.
struct LogWriterActor {
    /// Maps chip id to interface id. The content follows the content of the component in
    /// PcapngUciLoggerFactory with the same name.
    chip_interface_id_map: Vec<String>,
    current_file: Option<BufferedFile>,
    file_factory: FileFactory,
    file_size_limit: usize,
    log_receiver: mpsc::UnboundedReceiver<PcapngLoggerMessage>,
}

impl LogWriterActor {
    /// write data to file.
    fn write_once(&mut self, data: Vec<u8>) -> Option<()> {
        // Create new file if the file is not created, or does not fit incoming data:
        if self.current_file.is_none()
            || data.len() + self.current_file.as_ref().unwrap().file_size() > self.file_size_limit
        {
            self.current_file = Some(
                self.file_factory
                    .build_file_with_metadata(&self.chip_interface_id_map, self.file_size_limit)?,
            );
        }
        self.current_file.as_mut().unwrap().buffered_write(data)
    }

    /// Handle single new chip: stores chip in chip_interface_id_map and:
    ///
    /// a. Nothing extra if current_file is not created yet.
    /// b. If current file exists:
    ///    Insert IDB in current file if it fits, otherwise switch to new file.
    fn handle_new_chip(&mut self, chip_id: String, interface_id: u32) -> Option<()> {
        if self.chip_interface_id_map.contains(&chip_id)
            || self.chip_interface_id_map.len() as u32 != interface_id
        {
            error!(
                "UCI log: unexpected chip_id {} with associated interface id {}",
                &chip_id, interface_id
            );
            return None;
        }
        self.chip_interface_id_map.push(chip_id.clone());

        if let Some(current_file) = &mut self.current_file {
            let idb_data = into_interface_description_block(chip_id)?;
            if idb_data.len() + current_file.file_size() <= self.file_size_limit {
                current_file.buffered_write(idb_data)?;
            } else {
                self.current_file =
                    Some(self.file_factory.build_file_with_metadata(
                        &self.chip_interface_id_map,
                        self.file_size_limit,
                    )?);
            }
        }
        Some(())
    }

    async fn run(&mut self) {
        debug!("UCI log: LogWriterActor started");
        loop {
            match self.log_receiver.recv().await {
                Some(PcapngLoggerMessage::NewChip((chip_id, interface_id))) => {
                    if self.handle_new_chip(chip_id.clone(), interface_id).is_none() {
                        error!("UCI log: failed logging new chip {}", &chip_id);
                        break;
                    }
                }
                Some(PcapngLoggerMessage::ByteStream(data)) => {
                    if self.write_once(data).is_none() {
                        match &self.current_file {
                            Some(current_file) => {
                                error!(
                                    "UCI log: failed writting packet to log file {:?}",
                                    current_file.file
                                );
                            }
                            None => {
                                error!("UCI log: failed writting packet to log file: no log file.");
                            }
                        }
                        break;
                    }
                }
                Some(PcapngLoggerMessage::Flush(flush_sender)) => {
                    if self.current_file.is_some() {
                        match self.current_file.as_mut().unwrap().flush_file() {
                            Some(_) => {
                                let _ = flush_sender.send(true);
                            }
                            None => {
                                error!("UCI log: failed flushing the file");
                                let _ = flush_sender.send(false);
                            }
                        }
                    } else {
                        error!("UCI log: current_file not present");
                        let _ = flush_sender.send(false);
                    }
                }
                None => {
                    debug!("UCI log: LogWriterActor dropping.");
                    break;
                }
            }
        }
    }
}

/// Handle to LogWriterActor.
#[derive(Clone)]
pub(crate) struct LogWriter {
    log_sender: Option<mpsc::UnboundedSender<PcapngLoggerMessage>>,
}

impl LogWriter {
    /// Constructs LogWriter and its actor.
    ///
    /// runtime_handle must be a Handle to a multithread runtime that outlives LogWriterActor
    fn new(
        file_factory: FileFactory,
        file_size_limit: usize,
        runtime_handle: Handle,
    ) -> Option<Self> {
        let chip_interface_id_map = Vec::new();
        let (log_sender, log_receiver) = mpsc::unbounded_channel();
        let mut log_writer_actor = LogWriterActor {
            chip_interface_id_map,
            current_file: None,
            file_factory,
            file_size_limit,
            log_receiver,
        };
        runtime_handle.spawn(async move { log_writer_actor.run().await });
        Some(LogWriter { log_sender: Some(log_sender) })
    }

    pub fn send_bytes(&mut self, bytes: Vec<u8>) -> Option<()> {
        let log_sender = self.log_sender.as_ref()?;
        match log_sender.send(PcapngLoggerMessage::ByteStream(bytes)) {
            Ok(_) => Some(()),
            Err(e) => {
                error!("UCI log: LogWriterActor dead unexpectedly, sender error: {:?}", e);
                self.log_sender = None;
                None
            }
        }
    }

    pub fn flush(&mut self) -> Option<mpsc::UnboundedReceiver<bool>> {
        let log_sender = self.log_sender.as_ref()?;
        let (flush_sender, flush_receiver) = mpsc::unbounded_channel();
        match log_sender.send(PcapngLoggerMessage::Flush(flush_sender)) {
            Ok(_) => Some(flush_receiver),
            Err(e) => {
                error!("UCI log: LogWriterActor dead unexpectedly, sender error: {:?}", e);
                self.log_sender = None;
                None
            }
        }
    }

    fn send_chip(&mut self, chip_id: String, interface_id: u32) -> Option<()> {
        let log_sender = self.log_sender.as_ref()?;
        match log_sender.send(PcapngLoggerMessage::NewChip((chip_id, interface_id))) {
            Ok(_) => Some(()),
            Err(e) => {
                error!("UCI log: LogWriterActor dead unexpectedly, sender error: {:?}", e);
                self.log_sender = None;
                None
            }
        }
    }
}

fn into_interface_description_block(chip_id: String) -> Option<Vec<u8>> {
    let if_name_option = BlockOption::new(0x2, chip_id.into_bytes());
    InterfaceDescriptionBlockBuilder::new().append_option(if_name_option).into_le_bytes()
}

/// FileFactory builds next BufferedFile.
///
/// The most recent log file is {fileprefix}.pcapng. The archived log files have their index
/// increased: {fileprefix}_{n}.pcapng where n = 0..(rotate_range-1).
struct FileFactory {
    log_directory: PathBuf,
    filename_prefix: String,
    rotate_range: usize,
    buffer_size: usize,
}

impl FileFactory {
    /// Constructor.
    fn new(
        log_directory: PathBuf,
        filename_prefix: String,
        buffer_size: usize,
        rotate_range: usize,
    ) -> FileFactory {
        Self { log_directory, filename_prefix, rotate_range, buffer_size }
    }

    /// Builds pcapng file from a file factory, and prepares it with necessary header and metadata.
    fn build_file_with_metadata(
        &mut self,
        chip_interface_id_map: &[String],
        file_size_limit: usize,
    ) -> Option<BufferedFile> {
        let mut current_file = self.build_empty_file()?;
        let mut metadata = Vec::new();
        metadata.append(&mut HeaderBlockBuilder::new().into_le_bytes()?);
        for chip_id in chip_interface_id_map.iter() {
            metadata.append(&mut into_interface_description_block(chip_id.to_owned())?);
        }
        if metadata.len() > file_size_limit {
            error!(
                "UCI log: log file size limit is too small ({}) for file header and metadata ({})",
                file_size_limit,
                metadata.len()
            );
        }
        current_file.buffered_write(metadata)?;
        Some(current_file)
    }

    /// Builds next file as an empty BufferedFile.
    fn build_empty_file(&mut self) -> Option<BufferedFile> {
        self.rotate_file()?;
        let file_path = self.get_file_path(0);
        BufferedFile::new(&self.log_directory, &file_path, self.buffer_size)
    }

    /// get file path for log files of given index.
    fn get_file_path(&self, index: usize) -> PathBuf {
        let file_basename = if index == 0 {
            format!("{}.pcapng", self.filename_prefix)
        } else {
            format!("{}_{}.pcapng", self.filename_prefix, index)
        };
        self.log_directory.join(file_basename)
    }

    /// Vacates {filename_prefix}_0.pcapng for new log.
    fn rotate_file(&self) -> Option<()> {
        for source_idx in (0..self.rotate_range - 1).rev() {
            let target_idx = source_idx + 1;
            let source_path = self.get_file_path(source_idx);
            let target_path = self.get_file_path(target_idx);
            if source_path.is_dir() {
                error!("UCI log: expect {:?} to be a filename, but is a directory", &source_path);
                return None;
            }
            if source_path.is_file() && fs::rename(&source_path, &target_path).is_err() {
                error!(
                    "UCI log: failed to rename {} to {} while rotating log file.",
                    source_path.display(),
                    target_path.display(),
                );
                return None;
            }
        }
        Some(())
    }
}

struct BufferedFile {
    file: fs::File,
    written_size: usize,
    buffer_size: usize,
    buffer: Vec<u8>,
}

impl BufferedFile {
    /// Constructor.
    pub fn new(log_dir: &Path, file_path: &Path, buffer_size: usize) -> Option<Self> {
        if file_path.is_file() {
            if let Err(e) = fs::remove_file(file_path) {
                error!("UCI Log: failed to remove {}: {:?}", file_path.display(), e);
            };
        }
        if !log_dir.is_dir() {
            if let Err(e) = fs::create_dir_all(log_dir) {
                error!(
                    "UCI Log: failed to create log directory {}. Error: {:?}",
                    log_dir.display(),
                    e
                );
            }
        }

        let file = match fs::OpenOptions::new().write(true).create_new(true).open(file_path) {
            Ok(f) => f,
            Err(e) => {
                error!(
                    "UCI Log: failed to create log file {} for write: {:?}",
                    file_path.display(),
                    e
                );
                return None;
            }
        };
        Some(Self { file, written_size: 0, buffer_size, buffer: Vec::new() })
    }

    /// Returns the file size received.
    pub fn file_size(&self) -> usize {
        self.written_size + self.buffer.len()
    }

    /// Writes data to file with buffering.
    pub fn buffered_write(&mut self, mut data: Vec<u8>) -> Option<()> {
        if self.buffer.len() + data.len() >= self.buffer_size {
            self.flush_buffer();
        }
        self.buffer.append(&mut data);
        Some(())
    }

    /// Clears buffer.
    fn flush_buffer(&mut self) -> Option<()> {
        self.file.write_all(&self.buffer).ok()?;
        self.written_size += self.buffer.len();
        self.buffer.clear();

        self.file.flush().ok()
    }

    pub fn flush_file(&mut self) -> Option<()> {
        // Flush the buffer and then the file to storage.
        self.flush_buffer();
        self.file.sync_all().ok();
        Some(())
    }
}

/// Manual Drop implementation.
impl Drop for BufferedFile {
    fn drop(&mut self) {
        // Flush buffer before Closing file.
        self.flush_buffer();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use std::fs;

    use tempfile::tempdir;
    use tokio::runtime::Builder;
    use uwb_uci_packets::UciVendor_A_NotificationBuilder;

    use crate::uci::uci_logger::UciLogger;

    /// Gets block info from a little-endian PCAPNG file bytestream.
    ///
    /// Returns a vector of (block type, block length) if the bytestream is valid PCAPNG.
    fn get_block_info(datastream: Vec<u8>) -> Option<Vec<(u32, u32)>> {
        if datastream.len() % 4 != 0 || datastream.is_empty() {
            return None;
        }
        let mut block_info = Vec::new();
        let mut offset = 0usize;
        while offset < datastream.len() - 1 {
            let (_read, unread) = datastream.split_at(offset);
            if unread.len() < 8 {
                return None;
            }
            let (type_bytes, unread) = unread.split_at(4);
            let block_type = u32::from_le_bytes(type_bytes.try_into().unwrap());
            let (length_bytes, _unread) = unread.split_at(4);
            let block_length = u32::from_le_bytes(length_bytes.try_into().unwrap());
            offset += block_length as usize;
            if offset > datastream.len() {
                return None;
            }
            block_info.push((block_type, block_length));
        }
        Some(block_info)
    }

    async fn flush_loggers(loggers: Vec<UciLoggerPcapng>) {
        for mut logger in loggers {
            let flush_receiver = logger.flush();
            flush_receiver.unwrap().recv().await;
        }
    }

    #[test]
    fn test_no_file_write() {
        let dir = tempdir().unwrap();

        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.as_ref().to_owned())
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let logger_0 = file_manager.build_logger("logger 0").unwrap();
        let logger_1 = file_manager.build_logger("logger 1").unwrap();

        // Flush the loggers so that the files are created.
        runtime.block_on(flush_loggers(vec![logger_0, logger_1]));

        // Expect no log file created as no packet is received.
        let log_path = dir.as_ref().to_owned().join("log.pcapng");
        assert!(fs::read(log_path).is_err());
    }

    #[test]
    fn test_no_preexisting_dir_created() {
        let dir_root = Path::new("./uwb_test_dir_123");
        let dir = dir_root.join("this/path/doesnt/exist");
        let log_path = dir.join("log.pcapng");

        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.clone())
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let mut logger_0 = file_manager.build_logger("logger 0").unwrap();
        let packet_0 = UciVendor_A_NotificationBuilder { opcode: 0, payload: None }.build();
        logger_0.log_uci_control_packet(packet_0.into());

        // Flush all the loggers so that the files are created and all packets written.
        runtime.block_on(flush_loggers(vec![logger_0]));

        // Expect the dir was created.
        assert!(dir.is_dir());
        // Expect the log file exists.
        assert!(log_path.is_file());
        // Clear test dir
        let _ = fs::remove_dir_all(dir_root);
    }

    #[test]
    fn test_single_file_write() {
        let dir = tempdir().unwrap();
        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.as_ref().to_owned())
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let mut logger_0 = file_manager.build_logger("logger 0").unwrap();
        let packet_0 = UciVendor_A_NotificationBuilder { opcode: 0, payload: None }.build();
        logger_0.log_uci_control_packet(packet_0.into());
        let mut logger_1 = file_manager.build_logger("logger 1").unwrap();
        let packet_1 = UciVendor_A_NotificationBuilder { opcode: 1, payload: None }.build();
        logger_1.log_uci_control_packet(packet_1.into());
        let packet_2 = UciVendor_A_NotificationBuilder { opcode: 2, payload: None }.build();
        logger_0.log_uci_control_packet(packet_2.into());

        // Flush all the loggers so that the files are created and all packets written.
        runtime.block_on(flush_loggers(vec![logger_0, logger_1]));

        // Expect file log.pcapng consist of SHB->IDB(logger 0)->EPB(packet 0)->IDB(logger 1)
        // ->EPB(packet 1)->EPB(packet 2)
        let log_path = dir.as_ref().to_owned().join("log.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 6);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x6); // EPB
        assert_eq!(block_info[3].0, 0x1); // IDB
        assert_eq!(block_info[4].0, 0x6); // EPB
        assert_eq!(block_info[5].0, 0x6); // EPB
    }

    #[test]
    fn test_file_switch_epb_unfit_case() {
        let dir = tempdir().unwrap();
        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager_140 = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.as_ref().to_owned())
            .file_size(140)
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let mut logger_0 = file_manager_140.build_logger("logger 0").unwrap();
        let packet_0 = UciVendor_A_NotificationBuilder { opcode: 0, payload: None }.build();
        logger_0.log_uci_control_packet(packet_0.into());
        let mut logger_1 = file_manager_140.build_logger("logger 1").unwrap();
        let packet_1 = UciVendor_A_NotificationBuilder { opcode: 1, payload: None }.build();
        logger_1.log_uci_control_packet(packet_1.into());
        let packet_2 = UciVendor_A_NotificationBuilder { opcode: 2, payload: None }.build();
        logger_0.log_uci_control_packet(packet_2.into());

        // Flush all the loggers so that the files are created and all packets written.
        runtime.block_on(flush_loggers(vec![logger_0, logger_1]));

        // Expect (Old to new):
        // File 2: SHB->IDB->EPB->IDB (cannot fit next)
        // File 1: SHB->IDB->IDB->EPB (cannot fit next)
        // File 0: SHB->IDB->IDB->EPB
        let log_path = dir.as_ref().to_owned().join("log_2.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 4);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x6); // EPB
        assert_eq!(block_info[3].0, 0x1); // IDB
        let log_path = dir.as_ref().to_owned().join("log_1.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 4);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x1); // IDB
        assert_eq!(block_info[3].0, 0x6); // EPB
        let log_path = dir.as_ref().to_owned().join("log.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 4);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x1); // IDB
        assert_eq!(block_info[3].0, 0x6); // EPB
    }

    #[test]
    fn test_file_switch_idb_unfit_case() {
        let dir = tempdir().unwrap();
        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager_144 = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.as_ref().to_owned())
            .file_size(144)
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let mut logger_0 = file_manager_144.build_logger("logger 0").unwrap();
        let packet_0 = UciVendor_A_NotificationBuilder { opcode: 0, payload: None }.build();
        logger_0.log_uci_control_packet(packet_0.into());
        let packet_2 = UciVendor_A_NotificationBuilder { opcode: 2, payload: None }.build();
        logger_0.log_uci_control_packet(packet_2.into());
        let mut logger_1 = file_manager_144.build_logger("logger 1").unwrap();
        let packet_1 = UciVendor_A_NotificationBuilder { opcode: 1, payload: None }.build();
        logger_1.log_uci_control_packet(packet_1.into());

        // Flush all the loggers so that the files are created and all packets written.
        runtime.block_on(flush_loggers(vec![logger_0, logger_1]));

        // Expect (Old to new):
        // File 1: SHB->IDB->EPB->EPB (cannot fit next)
        // File 0: SHB->IDB->IDB->EPB
        let log_path = dir.as_ref().to_owned().join("log_1.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 4);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x6); // EPB
        assert_eq!(block_info[3].0, 0x6); // EPB
        let log_path = dir.as_ref().to_owned().join("log.pcapng");
        assert!(log_path.is_file());
        let log_content = fs::read(log_path).unwrap();
        let block_info = get_block_info(log_content).unwrap();
        assert_eq!(block_info.len(), 4);
        assert_eq!(block_info[0].0, 0x0A0D_0D0A); // SHB
        assert_eq!(block_info[1].0, 0x1); // IDB
        assert_eq!(block_info[2].0, 0x1); // IDB
        assert_eq!(block_info[3].0, 0x6); // EPB
    }

    // Program shall not panic even if log writing has failed for some reason.
    #[test]
    fn test_log_fail_safe() {
        let dir = tempdir().unwrap();
        let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
        let mut file_manager_96 = PcapngUciLoggerFactoryBuilder::new()
            .buffer_size(1024)
            .filename_prefix("log".to_owned())
            .log_path(dir.as_ref().to_owned())
            .file_size(96) // Fails logging, as metadata takes 100
            .runtime_handle(runtime.handle().to_owned())
            .build()
            .unwrap();
        let mut logger_0 = file_manager_96.build_logger("logger 0").unwrap();
        let packet_0 = UciVendor_A_NotificationBuilder { opcode: 0, payload: None }.build();
        logger_0.log_uci_control_packet(packet_0.into());
        let packet_2 = UciVendor_A_NotificationBuilder { opcode: 2, payload: None }.build();
        logger_0.log_uci_control_packet(packet_2.into());
        let mut logger_1 = file_manager_96.build_logger("logger 1").unwrap();
        let packet_1 = UciVendor_A_NotificationBuilder { opcode: 1, payload: None }.build();
        logger_1.log_uci_control_packet(packet_1.into());

        // Flush all the loggers so that the files are created and all packets written.
        runtime.block_on(flush_loggers(vec![logger_0, logger_1]));

        // Verify existence of the log files.
        let log_path = dir.as_ref().to_owned().join("log_1.pcapng");
        assert!(log_path.is_file());
        let log_path = dir.as_ref().to_owned().join("log.pcapng");
        assert!(log_path.is_file());
    }
}
