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

//! Library for constructing kernel bootconfig. See the following for more detail:
//!
//!   https://source.android.com/docs/core/architecture/bootloader/implementing-bootconfig#bootloader-changes

#![cfg_attr(not(test), no_std)]

/// Library error type.
#[derive(Debug)]
pub enum BootConfigError {
    ArithmeticOverflow,
    BufferTooSmall,
    InvalidConfigString,
    GenericReaderError(i64),
}

/// The type of Result used in this library.
pub type Result<T> = core::result::Result<T, BootConfigError>;

/// A class for constructing bootconfig section.
pub struct BootConfigBuilder<'a> {
    current_size: usize,
    buffer: &'a mut [u8],
}

const BOOTCONFIG_MAGIC: &str = "#BOOTCONFIG\n";
// Trailer structure:
// struct {
//     config_size: u32,
//     checksum: u32,
//     bootconfig_magic: [u8]
// }
const BOOTCONFIG_TRAILER_SIZE: usize = 4 + 4 + BOOTCONFIG_MAGIC.len();

impl<'a> BootConfigBuilder<'a> {
    /// Initialize with a given buffer.
    pub fn new(buffer: &'a mut [u8]) -> Result<Self> {
        if buffer.len() < BOOTCONFIG_TRAILER_SIZE {
            return Err(BootConfigError::BufferTooSmall);
        }
        let mut ret = Self { current_size: 0, buffer: buffer };
        ret.update_trailer()?;
        Ok(ret)
    }

    /// Get the remaining capacity for adding new bootconfig.
    pub fn remaining_capacity(&self) -> usize {
        self.buffer
            .len()
            .checked_sub(self.current_size)
            .unwrap()
            .checked_sub(BOOTCONFIG_TRAILER_SIZE)
            .unwrap()
    }

    /// Get the whole config bytes including trailer.
    pub fn config_bytes(&self) -> &[u8] {
        // Arithmetic not expected to fail.
        &self.buffer[..self.current_size.checked_add(BOOTCONFIG_TRAILER_SIZE).unwrap()]
    }

    /// Append a new config via a reader callback.
    ///
    /// A `&mut [u8]` that covers the remaining space is passed to the callback for reading the
    /// config bytes. It should return the total size read if operation is successful or a custom
    /// error code via `BootConfigError::GenericReaderError(<code>)`. Attempting to return a size
    /// greater than the input will cause it to panic. Empty read is allowed. It's up to the caller
    /// to make sure the read content will eventually form a valid boot config. The API is for
    /// situations where configs are read from sources such as disk and separate buffer allocation
    /// is not possible or desired.
    pub fn add_with<F>(&mut self, reader: F) -> Result<()>
    where
        F: FnOnce(&mut [u8]) -> Result<usize>,
    {
        let remains = self.remaining_capacity();
        let size = reader(&mut self.buffer[self.current_size..][..remains])?;
        assert!(size <= remains);
        self.current_size += size;
        // Content may have been modified. Re-compute trailer.
        self.update_trailer()
    }

    /// Append a new config from string.
    pub fn add(&mut self, config: &str) -> Result<()> {
        if self.remaining_capacity() < config.len() {
            return Err(BootConfigError::BufferTooSmall);
        }
        self.add_with(|out| {
            out[..config.len()].clone_from_slice(config.as_bytes());
            Ok(config.len())
        })
    }

    /// Update the boot config trailer at the end of parameter list.
    /// See specification at:
    /// https://source.android.com/docs/core/architecture/bootloader/implementing-bootconfig#bootloader-changes
    fn update_trailer(&mut self) -> Result<()> {
        // Config size
        let size: u32 =
            self.current_size.try_into().map_err(|_| BootConfigError::ArithmeticOverflow)?;
        // Check sum.
        let checksum = self.checksum();
        let trailer = &mut self.buffer[self.current_size..];
        trailer[..4].clone_from_slice(&size.to_le_bytes());
        trailer[4..8].clone_from_slice(&checksum.to_le_bytes());
        trailer[8..][..BOOTCONFIG_MAGIC.len()].clone_from_slice(BOOTCONFIG_MAGIC.as_bytes());
        Ok(())
    }

    /// Compute the checksum value.
    fn checksum(&self) -> u32 {
        self.buffer[..self.current_size]
            .iter()
            .map(|v| *v as u32)
            .reduce(|acc, v| acc.overflowing_add(v).0)
            .unwrap_or(0)
    }
}

impl core::fmt::Display for BootConfigBuilder<'_> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        let bytes = self.config_bytes();
        for val in &bytes[..bytes.len().checked_sub(BOOTCONFIG_TRAILER_SIZE).unwrap()] {
            write!(f, "{}", core::ascii::escape_default(*val))?;
        }
        Ok(())
    }
}

impl core::fmt::Write for BootConfigBuilder<'_> {
    fn write_str(&mut self, s: &str) -> core::fmt::Result {
        self.add_with(|out| {
            out.get_mut(..s.len())
                .ok_or(BootConfigError::BufferTooSmall)?
                .clone_from_slice(s.as_bytes());
            Ok(s.len())
        })
        .map_err(|_| core::fmt::Error)?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use core::fmt::Write;

    // Taken from Cuttlefish on QEMU aarch64.
    const TEST_CONFIG: &str = "androidboot.hardware=cutf_cvm
kernel.mac80211_hwsim.radios=0
kernel.vmw_vsock_virtio_transport_common.virtio_transport_max_vsock_pkt_buf_size=16384
androidboot.vendor.apex.com.google.emulated.camera.provider.hal=com.google.emulated.camera.provider.hal
androidboot.slot_suffix=_a
androidboot.force_normal_boot=1
androidboot.hw_timeout_multiplier=50
androidboot.fstab_suffix=cf.f2fs.hctr2
androidboot.hypervisor.protected_vm.supported=0
androidboot.modem_simulator_ports=9600
androidboot.vsock_lights_port=6900
androidboot.lcd_density=320
androidboot.vendor.audiocontrol.server.port=9410
androidboot.vendor.audiocontrol.server.cid=3
androidboot.cuttlefish_config_server_port=6800
androidboot.hardware.gralloc=minigbm
androidboot.vsock_lights_cid=3
androidboot.enable_confirmationui=0
androidboot.hypervisor.vm.supported=0
androidboot.setupwizard_mode=DISABLED
androidboot.serialno=CUTTLEFISHCVD011
androidboot.enable_bootanimation=1
androidboot.hardware.hwcomposer.display_finder_mode=drm
androidboot.hardware.angle_feature_overrides_enabled=preferLinearFilterForYUV:mapUnspecifiedColorSpaceToPassThrough
androidboot.hardware.egl=mesa
androidboot.boot_devices=4010000000.pcie
androidboot.opengles.version=196608
androidboot.wifi_mac_prefix=5554
androidboot.vsock_tombstone_port=6600
androidboot.hardware.hwcomposer=ranchu
androidboot.hardware.hwcomposer.mode=client
androidboot.console=ttyAMA0
androidboot.ddr_size=4915MB
androidboot.cpuvulkan.version=0
androidboot.serialconsole=1
androidboot.vbmeta.device=PARTUUID=2b7e273a-42a1-654b-bbad-8cb6ab2b6911
androidboot.vbmeta.avb_version=1.1
androidboot.vbmeta.device_state=unlocked
androidboot.vbmeta.hash_alg=sha256
androidboot.vbmeta.size=23040
androidboot.vbmeta.digest=6d6cdbad779475dd945ed79e6bd79c0574541d34ff488fa5aeeb024d739dd0d2
androidboot.vbmeta.invalidate_on_error=yes
androidboot.veritymode=enforcing
androidboot.verifiedbootstate=orange
";

    const TEST_CONFIG_TRAILER: &[u8; BOOTCONFIG_TRAILER_SIZE] =
        b"i\x07\x00\x00\xf9\xc4\x02\x00#BOOTCONFIG\n";

    #[test]
    fn test_add() {
        let mut buffer = [0u8; TEST_CONFIG.len() + TEST_CONFIG_TRAILER.len()];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        builder.add(TEST_CONFIG).unwrap();
        assert_eq!(
            builder.config_bytes().to_vec(),
            [TEST_CONFIG.as_bytes(), TEST_CONFIG_TRAILER].concat().to_vec()
        );
    }

    #[test]
    fn test_add_incremental() {
        let mut buffer = [0u8; TEST_CONFIG.len() + TEST_CONFIG_TRAILER.len()];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        for ele in TEST_CONFIG.strip_suffix('\n').unwrap().split('\n') {
            let config = std::string::String::from(ele) + "\n";
            builder.add(config.as_str()).unwrap();
        }
        assert_eq!(
            builder.config_bytes().to_vec(),
            [TEST_CONFIG.as_bytes(), TEST_CONFIG_TRAILER].concat().to_vec()
        );
    }

    #[test]
    fn test_add_incremental_via_fmt_write() {
        let mut buffer = [0u8; TEST_CONFIG.len() + TEST_CONFIG_TRAILER.len()];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        for ele in TEST_CONFIG.strip_suffix('\n').unwrap().split('\n') {
            write!(builder, "{}\n", ele).unwrap();
        }
        assert_eq!(
            builder.config_bytes().to_vec(),
            [TEST_CONFIG.as_bytes(), TEST_CONFIG_TRAILER].concat().to_vec()
        );
    }

    #[test]
    fn test_new_buffer_too_small() {
        let mut buffer = [0u8; BOOTCONFIG_TRAILER_SIZE - 1];
        assert!(BootConfigBuilder::new(&mut buffer[..]).is_err());
    }

    #[test]
    fn test_add_buffer_too_small() {
        let mut buffer = [0u8; BOOTCONFIG_TRAILER_SIZE + 1];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        assert!(builder.add("a\n").is_err());
    }

    #[test]
    fn test_add_empty_string() {
        let mut buffer = [0u8; BOOTCONFIG_TRAILER_SIZE + 1];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        builder.add("").unwrap();
    }

    #[test]
    fn test_add_with_error() {
        let mut buffer = [0u8; BOOTCONFIG_TRAILER_SIZE + 1];
        let mut builder = BootConfigBuilder::new(&mut buffer[..]).unwrap();
        assert!(builder.add_with(|_| Err(BootConfigError::GenericReaderError(123))).is_err());
    }
}
