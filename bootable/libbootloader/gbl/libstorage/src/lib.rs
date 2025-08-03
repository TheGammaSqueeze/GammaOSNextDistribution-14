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

//! APIs for reading/writing with non-block-aligned ranges and unaligned buffer.
//!
//! Most block devices require reading/writing in the unit of block and that the input/output
//! buffer satisfies certain alignment (i.e. DMA). This library provides APIs that build on top
//! of them and relax these constraints. The library supports reading/writing raw block content
//! as well as parsing/reading/writing GPT partitions.
//!
//! # Examples
//!
//! ```rust
//! use gbl_storage::{AsBlockDevice, BlockIo, BlockDevice, required_scratch_size};
//!
//! /// Mocks a block device using a buffer.
//! pub struct RamBlockIo {
//!     storage: std::vec::Vec<u8>,
//! }
//!
//! impl BlockIo for RamBlockIo {
//!     fn block_size(&mut self) -> u64 {
//!         512
//!     }
//!
//!     fn num_blocks(&mut self) -> u64 {
//!         self.storage.len() as u64 / self.block_size()
//!     }
//!
//!     fn alignment(&mut self) -> u64 {
//!         64
//!     }
//!
//!     fn read_blocks(&mut self, blk_offset: u64, out: &mut [u8]) -> bool {
//!         let start = blk_offset * self.block_size();
//!         let end = start + out.len() as u64;
//!         out.clone_from_slice(&self.storage[start as usize..end as usize]);
//!         true
//!     }
//!
//!     fn write_blocks(&mut self, blk_offset: u64, data: &[u8]) -> bool {
//!         let start = blk_offset * self.block_size();
//!         let end = start + data.len() as u64;
//!         self.storage[start as usize..end as usize].clone_from_slice(&data);
//!         true
//!     }
//! }
//!
//! const MAX_GPT_ENTRIES: u64 = 128;
//!
//! let mut ram_block_io = RamBlockIo { storage: vec![0u8; 64 * 1024] };
//! // Prepare a scratch buffer, size calculated with `required_scratch_size()`.
//! let mut scratch =
//!     vec![0u8; required_scratch_size(&mut ram_block_io, MAX_GPT_ENTRIES).unwrap()];
//! // Create a `BlockDevice`
//! let mut ram_block_dev =
//!     BlockDevice::new(&mut ram_block_io, &mut scratch[..], MAX_GPT_ENTRIES);
//!
//! // Read/write with arbitrary range and buffer without worrying about alignment.
//! let mut out = vec![0u8; 1234];
//! ram_block_dev.read(4321, &mut out[..]).unwrap();
//! let mut data = vec![0u8; 5678];
//! // Mutable input. More efficient
//! ram_block_dev.write(8765, &mut data[..]).unwrap();
//! // Immutable input. Works too but not as efficient.
//! ram_block_dev.write(8765, &data[..]).unwrap();
//!
//! // Sync GPT
//! let _ = ram_block_dev.sync_gpt();
//! // Access GPT entries
//! let _ = ram_block_dev.gpt();
//! // Read/Write GPT partitions with arbitrary offset, size, buffer
//! let _ = ram_block_dev.read_gpt_partition("partition", 4321, &mut out[..]);
//! let _ = ram_block_dev.write_gpt_partition("partition", 8765, &mut data[..]);
//!
//! // Alterantively, you can also define a custom type that internally owns and binds the
//! // implementation of `BlockIo` and scratch buffer together, and then implement the
//! // `AsBlockDevice` trait. This gives a cleaner management of resources.
//! pub struct OwnedBlockDevice {
//!     io: RamBlockIo,
//!     scratch: std::vec::Vec<u8>,
//! }
//!
//! impl AsBlockDevice for OwnedBlockDevice {
//!     fn get(&mut self) -> (&mut dyn BlockIo, &mut [u8], u64) {
//!         (&mut self.io, &mut self.scratch[..], MAX_GPT_ENTRIES)
//!     }
//! }
//!
//! // `owned_block_dev` has the same APIs as `BlockDevice`.
//! let mut owned_block_dev = OwnedBlockDevice { io: ram_block_io, scratch: scratch };
//! ```

#![cfg_attr(not(test), no_std)]

use core::cmp::min;

// Selective export of submodule types.
mod gpt;
pub use gpt::Gpt;
pub use gpt::GptEntry;

/// The type of Result used in this library.
pub type Result<T> = core::result::Result<T, StorageError>;

/// Error code for this library.
#[derive(Debug, Copy, Clone)]
pub enum StorageError {
    ArithmeticOverflow,
    OutOfRange,
    ScratchTooSmall,
    BlockIoError,
    InvalidInput,
    NoValidGpt,
    NotExist,
    U64toUSizeOverflow,
}

impl Into<&'static str> for StorageError {
    fn into(self) -> &'static str {
        match self {
            StorageError::ArithmeticOverflow => "Arithmetic overflow",
            StorageError::OutOfRange => "Out of range",
            StorageError::ScratchTooSmall => "Not enough scratch buffer",
            StorageError::BlockIoError => "Block IO error",
            StorageError::InvalidInput => "Invalid input",
            StorageError::NoValidGpt => "GPT not found",
            StorageError::NotExist => "Not exists",
            StorageError::U64toUSizeOverflow => "u64 to usize fails",
        }
    }
}

impl core::fmt::Display for StorageError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "{}", Into::<&'static str>::into(*self))
    }
}

/// `BlockIo` contains methods for reading/writing blocks of data to a block device with aligned
/// input/output buffers.
pub trait BlockIo {
    /// Returns the block size of the block device.
    fn block_size(&mut self) -> u64;

    /// Returns the total number of blocks of the block device.
    fn num_blocks(&mut self) -> u64;

    /// Returns the alignment requirement for buffers passed to the `write_blocks()` and
    /// `read_blocks()` methods. For example, many block device drivers use DMA for data transfer,
    /// which typically requires that the buffer address for DMA be aligned to 16/32/64 bytes etc.
    /// If the block device has no alignment requirement, it can return 1.
    fn alignment(&mut self) -> u64;

    /// Read blocks of data from the block device
    ///
    /// # Args
    ///
    /// * `blk_offset`: Offset in number of blocks.
    ///
    /// * `out`: Buffer to store the read data. Callers of this method ensure that it is
    ///   aligned according to alignment() and `out.len()` is multiples of `block_size()`.
    ///
    /// # Returns
    ///
    /// Returns true if exactly out.len() number of bytes are read. Otherwise false.
    fn read_blocks(&mut self, blk_offset: u64, out: &mut [u8]) -> bool;

    /// Write blocks of data to the block device
    ///
    /// # Args
    ///
    /// * `blk_offset`: Offset in number of blocks.
    ///
    /// * `data`: Data to write. Callers of this method ensure that it is aligned according to
    ///   `alignment()` and `data.len()` is multiples of `block_size()`.
    ///
    /// # Returns
    ///
    /// Returns true if exactly data.len() number of bytes are written. Otherwise false.
    fn write_blocks(&mut self, blk_offset: u64, data: &[u8]) -> bool;
}

/// `AsBlockDevice` provides APIs for reading/writing raw block content and GPT partitions with
/// arbirary offset, size and input/output buffer.
pub trait AsBlockDevice {
    /// Returns a tuple containing the following information:
    ///
    ///   1. An implementation of block IO `&mut dyn BlockIo`.
    ///   2. A scratch buffer `&mut [u8]`.
    ///   3. A `u64` specifying the maximum allowed number of GPT entries.
    ///
    /// * The scratch buffer is internally used for two purposes: 1. to handle read/write with
    ///   offset, size that are not multiples of block size or input/output buffer that are not
    ///   aligned, and 2. to load and sync GPT headers.
    ///
    /// * The necessary size for the scratch buffer depends on `BlockIo:alignment()`,
    ///   `BlockIo:block_size()` and maximum allowed GPT entries. It can be computed using the
    ///   helper API `required_scratch_size()`. If maximum allowed GPT entries is 0, GPT is
    ///   considered unavailable and no buffer will be reserved for GPT headers. If additionally,
    ///   `BlockIo` has no alignment requirement, i.e. both alignment and block size are 1, the
    ///   total required scratch size is 0.
    ///
    /// * GPT headers will be cached in the scratch buffer after calling `Self::sync_gpt()` and
    ///   returning success. Subsequent call of `Self:read_gpt_partiton()` and
    ///   `Self::write_gpt_partition()` will look up partition entries from the cached GPT header.
    ///   Thus callers should make sure to always return the same scratch buffer and avoid
    ///   modifying its content. `Self::gpt()` returns a `Gpt` type that provides APIs for
    ///   accessing the content of the GPT header.
    ///
    /// * A smaller value of maximum allowed GPT entries gives smaller required scratch buffer
    ///   size. However if the `entries_count` field in the GPT header is greater than this value,
    ///   GPT parsing will fail. Note that most tools and OS fix the `entries_count` value to the
    ///   max value 128 regardless of the actual number of partition entries used. Thus unless you
    ///   have full control of GPT generation in your entire system where you can always ensure a
    ///   smaller bound on it, it is recommended to always return 128.
    fn get(&mut self) -> (&mut dyn BlockIo, &mut [u8], u64);

    /// Read data from the block device.
    ///
    /// # Args
    ///
    /// * `offset`: Offset in number of bytes.
    ///
    /// * `out`: Buffer to store the read data.
    ///
    /// * Returns success when exactly `out.len()` number of bytes are read.
    fn read(&mut self, offset: u64, out: &mut [u8]) -> Result<()> {
        let (io, alignment, _, _) = get_with_partitioned_scratch(self)?;
        read(io, offset, out, alignment)
    }

    /// Write data to the device.
    ///
    /// # Args
    ///
    /// * `offset`: Offset in number of bytes.
    ///
    /// * `data`: Data to write. It can be `&[u8]`, `&mut [u8]` or other types that implement the
    ///   `WriteBuffer` trait.
    ///
    /// * If `data` is an immutable bytes slice `&[u8]` or doesn't support
    ///   `WriteBuffer::as_mut_slice()`, when offset`/`data.len()` is not aligned to
    ///   `Self::block_size()` or data.as_ptr() is not aligned to `Self::alignment()`, the API may
    ///   reduce to a block by block read-modify-write in the worst case, which can be inefficient.
    ///
    /// * If `data` is a mutable bytes slice `&mut [u8]`, or support `WriteBuffer::as_mut_slice()`.
    ///   The API enables optimization which temporarily changes `data` layout internally and
    ///   reduces the number of calls to `Self::write_blocks()` down to O(1) regardless of inputs
    ///   alignment. This is the recommended usage.
    ///
    /// * Returns success when exactly `data.len()` number of bytes are written.
    fn write<B>(&mut self, offset: u64, data: B) -> Result<()>
    where
        B: WriteBuffer,
    {
        let (io, alignment_scratch, _, _) = get_with_partitioned_scratch(self)?;
        write(io, offset, data, alignment_scratch)
    }

    /// Parse and sync GPT from a block device.
    ///
    /// The API validates and restores primary/secondary GPT header.
    ///
    /// # Returns
    ///
    /// Returns success if GPT is loaded/restored successfully.
    fn sync_gpt(&mut self) -> Result<Gpt> {
        let (io, alignment_scratch, gpt_buffer, max_entries) = get_with_partitioned_scratch(self)?;
        gpt::gpt_sync(io, &mut Gpt::new_from_buffer(max_entries, gpt_buffer)?, alignment_scratch)?;
        self.gpt()
    }

    /// Read a GPT partition on a block device
    ///
    /// # Args
    ///
    /// * `part_name`: Name of the partition.
    ///
    /// * `offset`: Offset in number of bytes into the partition.
    ///
    /// * `out`: Buffer to store the read data.
    ///
    /// # Returns
    ///
    /// Returns success when exactly `out.len()` of bytes are read successfully.
    fn read_gpt_partition(&mut self, part_name: &str, offset: u64, out: &mut [u8]) -> Result<()> {
        let (io, alignment_scratch, gpt_buffer, _) = get_with_partitioned_scratch(self)?;
        gpt::read_gpt_partition(
            io,
            &mut Gpt::from_existing(gpt_buffer)?,
            part_name,
            offset,
            out,
            alignment_scratch,
        )
    }

    /// Write a GPT partition on a block device
    ///
    /// # Args
    ///
    /// * `part_name`: Name of the partition.
    ///
    /// * `offset`: Offset in number of bytes into the partition.
    ///
    /// * `data`: Data to write. See `data` passed to `BlockIo::write()` for details.
    ///
    /// # Returns
    ///
    /// Returns success when exactly `data.len()` of bytes are written successfully.
    fn write_gpt_partition<B>(&mut self, part_name: &str, offset: u64, data: B) -> Result<()>
    where
        B: WriteBuffer,
    {
        let (io, alignment_scratch, gpt_buffer, _) = get_with_partitioned_scratch(self)?;
        gpt::write_gpt_partition(
            io,
            &mut Gpt::from_existing(gpt_buffer)?,
            part_name,
            offset,
            data,
            alignment_scratch,
        )
    }

    /// Returns the GPT header.
    fn gpt(&mut self) -> Result<Gpt> {
        let (_, _, gpt_buffer, _) = get_with_partitioned_scratch(self)?;
        Gpt::from_existing(gpt_buffer)
    }

    /// Returns the `BlockIo` implementation.
    fn block_io(&mut self) -> &mut dyn BlockIo {
        self.get().0
    }
}

/// `BlockDevice` borrows a `BlockIo`, scratch buffer and implements `AsBlockDevice`.
pub struct BlockDevice<'a, 'b> {
    io: &'a mut dyn BlockIo,
    scratch: &'b mut [u8],
    max_gpt_entries: u64,
}

impl<'a, 'b> BlockDevice<'a, 'b> {
    pub fn new(io: &'a mut dyn BlockIo, scratch: &'b mut [u8], max_gpt_entries: u64) -> Self {
        Self { io, scratch, max_gpt_entries }
    }
}

impl AsBlockDevice for BlockDevice<'_, '_> {
    fn get(&mut self) -> (&mut dyn BlockIo, &mut [u8], u64) {
        (self.io, self.scratch, self.max_gpt_entries)
    }
}

/// Calculates the required scratch buffer size for a `BlockIo` and number of maximum GPT entries.
pub fn required_scratch_size(
    io: &mut (impl BlockIo + ?Sized),
    max_gpt_entries: u64,
) -> Result<usize> {
    let alignment_size = alignment_scratch_size(io)?;
    let gpt_buffer_size = match max_gpt_entries {
        0 => 0,
        v => Gpt::required_buffer_size(v)?,
    };
    alignment_size.checked_add(gpt_buffer_size).ok_or(StorageError::ArithmeticOverflow)
}

/// The helper calls `AsBlockDevice:get()`, partitions the scratch buffer into alignment scratch and
/// GPT buffers, and returns them.
fn get_with_partitioned_scratch(
    dev: &mut (impl AsBlockDevice + ?Sized),
) -> Result<(&mut dyn BlockIo, &mut [u8], &mut [u8], u64)> {
    let (io, scratch, max_entries) = dev.get();
    if scratch.len() < required_scratch_size(io, max_entries)? {
        return Err(StorageError::ScratchTooSmall);
    }
    let (alignment, gpt) = scratch.split_at_mut(alignment_scratch_size(io)?);
    Ok((io, alignment, gpt, max_entries))
}

/// Add two u64 integers and check overflow
fn add(lhs: u64, rhs: u64) -> Result<u64> {
    lhs.checked_add(rhs).ok_or_else(|| StorageError::ArithmeticOverflow)
}

/// Substract two u64 integers and check overflow
fn sub(lhs: u64, rhs: u64) -> Result<u64> {
    lhs.checked_sub(rhs).ok_or_else(|| StorageError::ArithmeticOverflow)
}

/// Calculate remainders and check overflow
fn rem(lhs: u64, rhs: u64) -> Result<u64> {
    lhs.checked_rem(rhs).ok_or_else(|| StorageError::ArithmeticOverflow)
}

/// Multiply two numbers and check overflow
fn mul(lhs: u64, rhs: u64) -> Result<u64> {
    lhs.checked_mul(rhs).ok_or_else(|| StorageError::ArithmeticOverflow)
}

/// Divide two numbers and check overflow
fn div(lhs: u64, rhs: u64) -> Result<u64> {
    lhs.checked_div(rhs).ok_or_else(|| StorageError::ArithmeticOverflow)
}

/// Check if `value` is aligned to (multiples of) `alignment`
/// It can fail if the remainider calculation fails overflow check.
fn is_aligned(value: u64, alignment: u64) -> Result<bool> {
    Ok(rem(value, alignment)? == 0)
}

/// Check if `buffer` address is aligned to `alignment`
/// It can fail if the remainider calculation fails overflow check.
fn is_buffer_aligned(buffer: &[u8], alignment: u64) -> Result<bool> {
    is_aligned(buffer.as_ptr() as u64, alignment)
}

/// Round down `size` according to `alignment`.
/// It can fail if any arithmetic operation fails overflow check
fn round_down(size: u64, alignment: u64) -> Result<u64> {
    sub(size, rem(size, alignment)?)
}

/// Round up `size` according to `alignment`.
/// It can fail if any arithmetic operation fails overflow check
fn round_up(size: u64, alignment: u64) -> Result<u64> {
    // equivalent to round_down(size + alignment - 1, alignment)
    round_down(add(size, sub(alignment, 1)?)?, alignment)
}

/// Check and convert u64 into usize
fn to_usize(val: u64) -> Result<usize> {
    val.try_into().map_err(|_| StorageError::U64toUSizeOverflow)
}

/// Check read/write range and calculate offset in number of blocks.
fn check_range(blk_io: &mut (impl BlockIo + ?Sized), offset: u64, buffer: &[u8]) -> Result<u64> {
    // The following should be invariants if implementation is correct.
    debug_assert!(is_aligned(offset, blk_io.block_size())?);
    debug_assert!(is_aligned(buffer.len() as u64, blk_io.block_size())?);
    debug_assert!(is_buffer_aligned(buffer, blk_io.alignment())?);
    let blk_offset = offset / blk_io.block_size();
    let blk_count = buffer.len() as u64 / blk_io.block_size();
    match add(blk_offset, blk_count)? <= blk_io.num_blocks() {
        true => Ok(blk_offset),
        false => Err(StorageError::OutOfRange),
    }
}

/// Read with block-aligned offset, length and aligned buffer
fn read_aligned_all(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    out: &mut [u8],
) -> Result<()> {
    let blk_offset = check_range(blk_io, offset, out)?;
    if blk_io.read_blocks(blk_offset, out) {
        return Ok(());
    }
    Err(StorageError::BlockIoError)
}

/// Read with block-aligned offset and aligned buffer. Size don't need to be block aligned.
///   |~~~~~~~~~read~~~~~~~~~|
///   |---------|---------|---------|
fn read_aligned_offset_and_buffer(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    out: &mut [u8],
    scratch: &mut [u8],
) -> Result<()> {
    debug_assert!(is_aligned(offset, blk_io.block_size())?);
    debug_assert!(is_buffer_aligned(out, blk_io.alignment())?);

    let aligned_read = round_down(out.len() as u64, blk_io.block_size())?;
    if aligned_read > 0 {
        read_aligned_all(blk_io, offset, &mut out[..to_usize(aligned_read)?])?;
    }
    let unaligned = &mut out[to_usize(aligned_read)?..];
    if unaligned.len() == 0 {
        return Ok(());
    }
    // Read unalinged part.
    let block_scratch = &mut scratch[..to_usize(blk_io.block_size())?];
    read_aligned_all(blk_io, add(offset, aligned_read)?, block_scratch)?;
    unaligned.clone_from_slice(&block_scratch[..unaligned.len()]);
    Ok(())
}

/// Read with aligned buffer. Offset and size don't need to be block aligned.
/// Case 1:
///            |~~~~~~read~~~~~~~|
///        |------------|------------|
/// Case 2:
///          |~~~read~~~|
///        |---------------|--------------|
fn read_aligned_buffer(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    out: &mut [u8],
    scratch: &mut [u8],
) -> Result<()> {
    debug_assert!(is_buffer_aligned(out, blk_io.alignment())?);

    if is_aligned(offset, blk_io.block_size())? {
        return read_aligned_offset_and_buffer(blk_io, offset, out, scratch);
    }

    let aligned_start = min(round_up(offset, blk_io.block_size())?, add(offset, out.len() as u64)?);
    let aligned_relative_offset = sub(aligned_start, offset)?;
    if aligned_relative_offset < out.len() as u64 {
        if is_buffer_aligned(&out[to_usize(aligned_relative_offset)?..], blk_io.alignment())? {
            // If new output address is aligned, read directly.
            read_aligned_offset_and_buffer(
                blk_io,
                aligned_start,
                &mut out[to_usize(aligned_relative_offset)?..],
                scratch,
            )?;
        } else {
            // Otherwise read into `out` (assumed aligned) and memmove to the correct
            // position
            let read_len = sub(out.len() as u64, aligned_relative_offset)?;
            read_aligned_offset_and_buffer(
                blk_io,
                aligned_start,
                &mut out[..to_usize(read_len)?],
                scratch,
            )?;
            out.copy_within(..to_usize(read_len)?, to_usize(aligned_relative_offset)?);
        }
    }

    // Now read the unaligned part
    let block_scratch = &mut scratch[..to_usize(blk_io.block_size())?];
    let round_down_offset = round_down(offset, blk_io.block_size())?;
    read_aligned_all(blk_io, round_down_offset, block_scratch)?;
    let offset_relative = sub(offset, round_down_offset)?;
    let unaligned = &mut out[..to_usize(aligned_relative_offset)?];
    unaligned.clone_from_slice(
        &block_scratch
            [to_usize(offset_relative)?..to_usize(add(offset_relative, unaligned.len() as u64)?)?],
    );
    Ok(())
}

/// Calculates the necessary scratch buffer size for handling block and buffer misalignment.
fn alignment_scratch_size(blk_io: &mut (impl BlockIo + ?Sized)) -> Result<usize> {
    // block_size() + 2 * (alignment() - 1)
    // This guarantees that we can craft out two aligned scratch buffers:
    // [u8; blk_io.alignment() - 1] and [u8; blk_io.block_size())] respectively. They are
    // needed for handing buffer and read/write range misalignment.
    let block_alignment = match blk_io.block_size() {
        1 => 0,
        v => v,
    };
    let buffer_alignment = mul(2, sub(blk_io.alignment(), 1)?)?;
    to_usize(add(block_alignment, buffer_alignment)?)
}

/// Gets a subslice of the given slice with aligned address according to `alignment`
fn aligned_subslice(buffer: &mut [u8], alignment: u64) -> Result<&mut [u8]> {
    let addr =
        u64::try_from(buffer.as_ptr() as usize).map_err(|_| StorageError::ArithmeticOverflow)?;
    Ok(&mut buffer[to_usize(sub(round_up(addr, alignment)?, addr)?)?..])
}

// Partition a scratch into two aligned parts: [u8; alignment()-1] and [u8; block_size())]
// for handling block and buffer misalignment respecitvely.
fn split_scratch<'a>(
    blk_io: &mut (impl BlockIo + ?Sized),
    scratch: &'a mut [u8],
) -> Result<(&'a mut [u8], &'a mut [u8])> {
    let (buffer_alignment, block_alignment) = aligned_subslice(scratch, blk_io.alignment())?
        .split_at_mut(to_usize(sub(blk_io.alignment(), 1)?)?);
    let block_alignment = aligned_subslice(block_alignment, blk_io.alignment())?;
    let block_alignment_scratch_size = match blk_io.block_size() {
        1 => 0usize,
        v => to_usize(v)?,
    };
    Ok((buffer_alignment, &mut block_alignment[..block_alignment_scratch_size]))
}

/// Read with no alignment requirement.
fn read(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    out: &mut [u8],
    scratch: &mut [u8],
) -> Result<()> {
    let (buffer_alignment_scratch, block_alignment_scratch) = split_scratch(blk_io, scratch)?;

    if is_buffer_aligned(out, blk_io.alignment())? {
        return read_aligned_buffer(blk_io, offset, out, block_alignment_scratch);
    }

    // Buffer misalignment:
    // Case 1:
    //     |~~~~~~~~~~~~buffer~~~~~~~~~~~~|
    //   |----------------------|---------------------|
    //      blk_io.alignment()
    //
    // Case 2:
    //    |~~~~~~buffer~~~~~|
    //  |----------------------|---------------------|
    //     blk_io.alignment()

    let out_addr_value = out.as_ptr() as u64;
    let unaligned_read =
        min(sub(round_up(out_addr_value, blk_io.alignment())?, out_addr_value)?, out.len() as u64);
    // Read unaligned part
    let unaligned_out = &mut buffer_alignment_scratch[..to_usize(unaligned_read)?];
    read_aligned_buffer(blk_io, offset, unaligned_out, block_alignment_scratch)?;
    out[..to_usize(unaligned_read)?].clone_from_slice(unaligned_out);

    if unaligned_read == out.len() as u64 {
        return Ok(());
    }
    // Read aligned part
    read_aligned_buffer(
        blk_io,
        add(offset, unaligned_read)?,
        &mut out[to_usize(unaligned_read)?..],
        block_alignment_scratch,
    )
}

fn write_aligned_all(blk_io: &mut (impl BlockIo + ?Sized), offset: u64, data: &[u8]) -> Result<()> {
    let blk_offset = check_range(blk_io, offset, data)?;
    if blk_io.write_blocks(blk_offset, data) {
        return Ok(());
    }
    Err(StorageError::BlockIoError)
}

/// Write with block-aligned offset and aligned buffer. `data.len()` can be unaligned.
///   |~~~~~~~~~size~~~~~~~~~|
///   |---------|---------|---------|
fn write_aligned_offset_and_buffer(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    data: &[u8],
    scratch: &mut [u8],
) -> Result<()> {
    debug_assert!(is_aligned(offset, blk_io.block_size())?);
    debug_assert!(is_buffer_aligned(data, blk_io.alignment())?);

    let aligned_write = round_down(data.len() as u64, blk_io.block_size())?;
    if aligned_write > 0 {
        write_aligned_all(blk_io, offset, &data[..to_usize(aligned_write)?])?;
    }
    let unaligned = &data[to_usize(aligned_write)?..];
    if unaligned.len() == 0 {
        return Ok(());
    }

    // Perform read-modify-write for the unaligned part
    let unaligned_start = add(offset, aligned_write)?;
    let block_scratch = &mut scratch[..to_usize(blk_io.block_size())?];
    read_aligned_all(blk_io, unaligned_start, block_scratch)?;
    block_scratch[..unaligned.len()].clone_from_slice(unaligned);
    write_aligned_all(blk_io, unaligned_start, block_scratch)
}

/// Write data to the device with immutable input bytes slice. However, if `offset`/`data.len()`
/// is not aligned to `blk_io.block_size()` or data.as_ptr() is not aligned to
/// `blk_io.alignment()`, the API may reduce to a block by block read-modify-write in the worst
/// case.
fn write_bytes(
    blk_io: &mut (impl BlockIo + ?Sized),
    mut offset: u64,
    data: &[u8],
    scratch: &mut [u8],
) -> Result<()> {
    let (_, block_scratch) = split_scratch(blk_io, scratch)?;
    let block_size = blk_io.block_size();
    let mut data_offset = 0;
    while data_offset < data.len() as u64 {
        if is_aligned(offset, blk_io.block_size())?
            && is_buffer_aligned(&data[to_usize(data_offset)?..], blk_io.alignment())?
        {
            return write_aligned_offset_and_buffer(
                blk_io,
                offset,
                &data[to_usize(data_offset)?..],
                block_scratch,
            );
        }

        let block_offset = round_down(offset, block_size)?;
        let copy_offset = offset - block_offset;
        let copy_size = min(data[to_usize(data_offset)?..].len() as u64, block_size - copy_offset);
        if copy_size < block_size {
            // Partial block copy. Perform read-modify-write
            read_aligned_all(blk_io, block_offset, block_scratch)?;
        }
        block_scratch[to_usize(copy_offset)?..to_usize(copy_offset + copy_size)?]
            .clone_from_slice(&data[to_usize(data_offset)?..to_usize(data_offset + copy_size)?]);
        write_aligned_all(blk_io, block_offset, block_scratch)?;
        data_offset += copy_size;
        offset += copy_size;
    }

    Ok(())
}

/// Swap the position of sub segment [0..pos] and [pos..]
fn swap_slice(slice: &mut [u8], pos: usize) {
    let (left, right) = slice.split_at_mut(pos);
    left.reverse();
    right.reverse();
    slice.reverse();
}

/// Write with aligned buffer. Offset and size don't need to be block aligned.
/// Case 1:
///            |~~~~~~write~~~~~~~|
///        |------------|------------|
/// Case 2:
///          |~~~write~~~|
///        |---------------|--------------|
fn write_aligned_buffer(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    data: &mut [u8],
    scratch: &mut [u8],
) -> Result<()> {
    debug_assert!(is_buffer_aligned(data, blk_io.alignment())?);

    if is_aligned(offset, blk_io.block_size())? {
        return write_aligned_offset_and_buffer(blk_io, offset, data, scratch);
    }

    let aligned_start =
        min(round_up(offset, blk_io.block_size())?, add(offset, data.len() as u64)?);
    let aligned_relative_offset = sub(aligned_start, offset)?;
    if aligned_relative_offset < data.len() as u64 {
        if is_buffer_aligned(&data[to_usize(aligned_relative_offset)?..], blk_io.alignment())? {
            // If new address is aligned, write directly.
            write_aligned_offset_and_buffer(
                blk_io,
                aligned_start,
                &data[to_usize(aligned_relative_offset)?..],
                scratch,
            )?;
        } else {
            let write_len = sub(data.len() as u64, aligned_relative_offset)?;
            // Swap the offset-aligned part to the beginning of the buffer (assumed aligned)
            swap_slice(data, to_usize(aligned_relative_offset)?);
            let res = write_aligned_offset_and_buffer(
                blk_io,
                aligned_start,
                &data[..to_usize(write_len)?],
                scratch,
            );
            // Swap the two parts back before checking the result.
            swap_slice(data, to_usize(write_len)?);
            res?;
        }
    }

    // perform read-modify-write for the unaligned part.
    let block_scratch = &mut scratch[..to_usize(blk_io.block_size())?];
    let round_down_offset = round_down(offset, blk_io.block_size())?;
    read_aligned_all(blk_io, round_down_offset, block_scratch)?;
    let offset_relative = sub(offset, round_down_offset)?;
    block_scratch[to_usize(offset_relative)?..to_usize(offset_relative + aligned_relative_offset)?]
        .clone_from_slice(&data[..to_usize(aligned_relative_offset)?]);
    write_aligned_all(blk_io, round_down_offset, block_scratch)
}

/// Same as write_bytes(). Expcet that the API takes a mutable input bytes slice instead.
/// It does internal optimization that temporarily modifies `data` layout to minimize number of
/// calls to `blk_io.read_blocks()`/`blk_io.write_blocks()` (down to O(1)).
fn write_bytes_mut(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    data: &mut [u8],
    scratch: &mut [u8],
) -> Result<()> {
    let (buffer_alignment_scratch, block_alignment_scratch) = split_scratch(blk_io, scratch)?;
    if is_buffer_aligned(data, blk_io.alignment())? {
        return write_aligned_buffer(blk_io, offset, data, block_alignment_scratch);
    }

    // Buffer misalignment:
    // Case 1:
    //     |~~~~~~~~~~~~buffer~~~~~~~~~~~~|
    //   |----------------------|---------------------|
    //      blk_io.alignment()
    //
    // Case 2:
    //    |~~~~~~buffer~~~~~|
    //  |----------------------|---------------------|
    //     blk_io.alignment()

    // Write unaligned part
    let data_addr_value = data.as_ptr() as u64;
    let unaligned_write = min(
        sub(round_up(data_addr_value, blk_io.alignment())?, data_addr_value)?,
        data.len() as u64,
    );
    let mut unaligned_data = &mut buffer_alignment_scratch[..to_usize(unaligned_write)?];
    unaligned_data.clone_from_slice(&data[..to_usize(unaligned_write)?]);
    write_aligned_buffer(blk_io, offset, &mut unaligned_data, block_alignment_scratch)?;
    if unaligned_write == data.len() as u64 {
        return Ok(());
    }

    // Write aligned part
    write_aligned_buffer(
        blk_io,
        add(offset, unaligned_write)?,
        &mut data[to_usize(unaligned_write)?..],
        block_alignment_scratch,
    )
}

/// Top level write API that accept mutable/immutable bytes slice
fn write<B>(
    blk_io: &mut (impl BlockIo + ?Sized),
    offset: u64,
    mut data: B,
    scratch: &mut [u8],
) -> Result<()>
where
    B: WriteBuffer,
{
    if let Some(slice) = data.as_mut_slice() {
        return write_bytes_mut(blk_io, offset, slice, scratch);
    } else if let Some(slice) = data.as_slice() {
        return write_bytes(blk_io, offset, slice, scratch);
    }

    Err(StorageError::InvalidInput)
}

/// The trait is for abstracting the representation of bytes data. The `AsBlockDevice::write()` API
/// will choose different algorithm based on the return result of the methods. The library
/// provides native implementation for `&[u8]` and `&mut [u8]`. It's technically possilbe for users
/// to implement other types such as `Vec<u8>` etc.
pub trait WriteBuffer {
    /// Return a mutable bytes slice for the data. If the data cannot be mutable, returns None.
    fn as_mut_slice(&mut self) -> Option<&mut [u8]>;

    /// Return a bytes slice for the data. Return None if not supported.
    fn as_slice(&mut self) -> Option<&[u8]>;

    // Provided methods

    /// Return the length of the data.
    fn data_len(&mut self) -> Result<usize> {
        self.as_slice()
            .map(|v| v.len())
            .or(self.as_mut_slice().map(|v| v.len()))
            .ok_or(StorageError::InvalidInput)
    }
}

impl WriteBuffer for &[u8] {
    fn as_mut_slice(&mut self) -> Option<&mut [u8]> {
        None
    }

    fn as_slice(&mut self) -> Option<&[u8]> {
        Some(*self)
    }
}

impl WriteBuffer for &mut [u8] {
    fn as_mut_slice(&mut self) -> Option<&mut [u8]> {
        Some(*self)
    }

    fn as_slice(&mut self) -> Option<&[u8]> {
        None
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use core::mem::size_of;
    use Vec;

    /// Mocks a block device with content from buffer.
    pub struct TestBlockIo {
        pub block_size: u64,
        pub alignment: u64,
        pub storage: Vec<u8>,
        pub num_writes: usize,
        pub num_reads: usize,
    }

    impl BlockIo for TestBlockIo {
        fn block_size(&mut self) -> u64 {
            self.block_size
        }

        fn num_blocks(&mut self) -> u64 {
            self.storage.len() as u64 / self.block_size()
        }

        fn alignment(&mut self) -> u64 {
            self.alignment
        }

        fn read_blocks(&mut self, blk_offset: u64, out: &mut [u8]) -> bool {
            assert!(is_buffer_aligned(out, self.alignment()).unwrap());
            assert!(is_aligned(out.len() as u64, self.block_size()).unwrap());
            let start = blk_offset * self.block_size();
            let end = start + out.len() as u64;
            out.clone_from_slice(&self.storage[to_usize(start).unwrap()..to_usize(end).unwrap()]);
            self.num_reads += 1;
            true
        }

        fn write_blocks(&mut self, blk_offset: u64, data: &[u8]) -> bool {
            assert!(is_buffer_aligned(data, self.alignment()).unwrap());
            assert!(is_aligned(data.len() as u64, self.block_size()).unwrap());
            let start = blk_offset * self.block_size();
            let end = start + data.len() as u64;
            self.storage[to_usize(start).unwrap()..to_usize(end).unwrap()].clone_from_slice(&data);
            self.num_writes += 1;
            true
        }
    }

    pub struct TestBlockDevice {
        pub io: TestBlockIo,
        pub scratch: Vec<u8>,
        max_gpt_entries: u64,
    }

    impl TestBlockDevice {
        /// Creates an instance with provided storage content.
        pub fn new_with_data(
            alignment: u64,
            block_size: u64,
            max_gpt_entries: u64,
            data: &[u8],
        ) -> Self {
            let mut io = TestBlockIo {
                block_size: block_size,
                alignment: alignment,
                storage: Vec::from(data),
                num_writes: 0,
                num_reads: 0,
            };
            let scratch_size = required_scratch_size(&mut io, max_gpt_entries).unwrap();
            Self { io: io, scratch: vec![0u8; scratch_size], max_gpt_entries: max_gpt_entries }
        }

        /// Creates an instance with default storage content.
        pub fn new(
            alignment: u64,
            block_size: u64,
            max_gpt_entries: u64,
            storage_size: u64,
        ) -> Self {
            // Initialize default storage content.
            let storage: Vec<u8> = (0..storage_size).map(|x| x as u8).collect();
            Self::new_with_data(alignment, block_size, max_gpt_entries, &storage)
        }
    }

    impl AsBlockDevice for TestBlockDevice {
        fn get(&mut self) -> (&mut dyn BlockIo, &mut [u8], u64) {
            (&mut self.io, &mut self.scratch[..], self.max_gpt_entries)
        }
    }

    #[derive(Debug)]
    struct TestCase {
        rw_offset: u64,
        rw_size: u64,
        misalignment: u64,
        alignment: u64,
        block_size: u64,
        storage_size: u64,
    }

    impl TestCase {
        fn new(
            rw_offset: u64,
            rw_size: u64,
            misalignment: u64,
            alignment: u64,
            block_size: u64,
            storage_size: u64,
        ) -> Self {
            Self { rw_offset, rw_size, misalignment, alignment, block_size, storage_size }
        }
    }

    // Helper object for allocating aligned buffer.
    struct AlignedBuffer {
        buffer: Vec<u8>,
        alignment: u64,
        size: u64,
    }

    impl AlignedBuffer {
        pub fn new(alignment: u64, size: u64) -> Self {
            let buffer = vec![0u8; to_usize(size + alignment).unwrap()];
            Self { buffer, alignment, size }
        }

        pub fn get(&mut self) -> &mut [u8] {
            let addr = self.buffer.as_ptr() as u64;
            let aligned_start = round_up(addr, self.alignment).unwrap() - addr;
            &mut self.buffer
                [to_usize(aligned_start).unwrap()..to_usize(aligned_start + self.size).unwrap()]
        }
    }

    /// Upper bound on the number of `BlockIo::read_blocks()/write_blocks()` calls by
    /// `AsBlockDevice::read()` and `AsBlockDevice::write()` with mutable input buffer.
    ///
    /// * `fn read_aligned_all()`: At most 1 call to `BlockIo::read_blocks()`.
    /// * `fn read_aligned_offset_and_buffer()`: At most 2 calls to `read_aligned_all()`.
    /// * `fn read_aligned_buffer()`: At most 1 call to `read_aligned_offset_and_buffer()` plus 1
    ///   call to `BlockIo::read_blocks()`.
    /// * `fn read()`: At most 2 calls to `read_aligned_buffer()`.
    ///
    /// Analysis is similar for `fn write()`.
    const READ_WRITE_BLOCKS_UPPER_BOUND: usize = 6;

    fn read_test_helper(case: &TestCase) {
        let mut blk = TestBlockDevice::new(case.alignment, case.block_size, 0, case.storage_size);
        // Make an aligned buffer. A misaligned version is created by taking a sub slice that
        // starts at an unaligned offset. Because of this we need to allocate
        // `case.misalignment` more to accommodate it.
        let mut aligned_buf = AlignedBuffer::new(case.alignment, case.rw_size + case.misalignment);
        let out = &mut aligned_buf.get()[to_usize(case.misalignment).unwrap()
            ..to_usize(case.misalignment + case.rw_size).unwrap()];
        blk.read(case.rw_offset, out).unwrap();
        assert_eq!(
            out.to_vec(),
            blk.io.storage[to_usize(case.rw_offset).unwrap()
                ..to_usize(case.rw_offset + case.rw_size).unwrap()]
                .to_vec(),
            "Failed. Test case {:?}",
            case,
        );

        assert!(blk.io.num_reads <= READ_WRITE_BLOCKS_UPPER_BOUND);
    }

    fn write_test_helper(case: &TestCase, write_func: fn(&mut TestBlockDevice, u64, &mut [u8])) {
        let mut blk = TestBlockDevice::new(case.alignment, case.block_size, 0, case.storage_size);
        // Write a reverse version of the current data.
        let mut expected = blk.io.storage
            [to_usize(case.rw_offset).unwrap()..to_usize(case.rw_offset + case.rw_size).unwrap()]
            .to_vec();
        expected.reverse();
        // Make an aligned buffer. A misaligned version is created by taking a sub slice that
        // starts at an unaligned offset. Because of this we need to allocate
        // `case.misalignment` more to accommodate it.
        let mut aligned_buf = AlignedBuffer::new(case.alignment, case.rw_size + case.misalignment);
        let data = &mut aligned_buf.get()[to_usize(case.misalignment).unwrap()
            ..to_usize(case.misalignment + case.rw_size).unwrap()];
        data.clone_from_slice(&expected);
        write_func(&mut blk, case.rw_offset, data);
        assert_eq!(
            expected,
            blk.io.storage[to_usize(case.rw_offset).unwrap()
                ..to_usize(case.rw_offset + case.rw_size).unwrap()]
                .to_vec(),
            "Failed. Test case {:?}",
            case,
        );
        // Check that input is not modified.
        assert_eq!(expected, data, "Input is modified. Test case {:?}", case,);
    }

    macro_rules! read_write_test {
        ($name:ident, $x0:expr, $x1:expr, $x2:expr, $x3:expr, $x4:expr, $x5:expr) => {
            mod $name {
                use super::*;

                #[test]
                fn read_test() {
                    read_test_helper(&TestCase::new($x0, $x1, $x2, $x3, $x4, $x5));
                }

                #[test]
                fn read_scaled_test() {
                    // Scaled all parameters by double and test again.
                    let (x0, x1, x2, x3, x4, x5) =
                        (2 * $x0, 2 * $x1, 2 * $x2, 2 * $x3, 2 * $x4, 2 * $x5);
                    read_test_helper(&TestCase::new(x0, x1, x2, x3, x4, x5));
                }

                // Input bytes slice is an immutable reference
                #[test]
                fn write_test() {
                    let func = |blk: &mut TestBlockDevice, offset: u64, data: &mut [u8]| {
                        blk.write(offset, data as &[u8]).unwrap();
                    };
                    write_test_helper(&TestCase::new($x0, $x1, $x2, $x3, $x4, $x5), func);
                }

                #[test]
                fn write_scaled_test() {
                    // Scaled all parameters by double and test again.
                    let func = |blk: &mut TestBlockDevice, offset: u64, data: &mut [u8]| {
                        blk.write(offset, data as &[u8]).unwrap();
                    };
                    let (x0, x1, x2, x3, x4, x5) =
                        (2 * $x0, 2 * $x1, 2 * $x2, 2 * $x3, 2 * $x4, 2 * $x5);
                    write_test_helper(&TestCase::new(x0, x1, x2, x3, x4, x5), func);
                }

                // Input bytes slice is a mutable reference
                #[test]
                fn write_mut_test() {
                    let func = |blk: &mut TestBlockDevice, offset: u64, data: &mut [u8]| {
                        blk.write(offset, data).unwrap();
                        assert!(blk.io.num_reads <= READ_WRITE_BLOCKS_UPPER_BOUND);
                        assert!(blk.io.num_writes <= READ_WRITE_BLOCKS_UPPER_BOUND);
                    };
                    write_test_helper(&TestCase::new($x0, $x1, $x2, $x3, $x4, $x5), func);
                }

                #[test]
                fn write_mut_scaled_test() {
                    // Scaled all parameters by double and test again.
                    let (x0, x1, x2, x3, x4, x5) =
                        (2 * $x0, 2 * $x1, 2 * $x2, 2 * $x3, 2 * $x4, 2 * $x5);
                    let func = |blk: &mut TestBlockDevice, offset: u64, data: &mut [u8]| {
                        blk.write(offset, data).unwrap();
                        assert!(blk.io.num_reads <= READ_WRITE_BLOCKS_UPPER_BOUND);
                        assert!(blk.io.num_writes <= READ_WRITE_BLOCKS_UPPER_BOUND);
                    };
                    write_test_helper(&TestCase::new(x0, x1, x2, x3, x4, x5), func);
                }
            }
        };
    }

    const BLOCK_SIZE: u64 = 512;
    const ALIGNMENT: u64 = 64;
    const STORAGE: u64 = BLOCK_SIZE * 32;

    // Test cases for different scenarios of read/write windows w.r.t buffer/block alignmnet
    // boundary.
    // offset
    //   |~~~~~~~~~~~~~size~~~~~~~~~~~~|
    //   |---------|---------|---------|
    read_write_test! {aligned_all, 0, STORAGE, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }

    // offset
    //   |~~~~~~~~~size~~~~~~~~~|
    //   |---------|---------|---------|
    read_write_test! {
        aligned_offset_uanligned_size, 0, STORAGE - 1, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }
    // offset
    //   |~~size~~|
    //   |---------|---------|---------|
    read_write_test! {
        aligned_offset_intra_block, 0, BLOCK_SIZE - 1, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }
    //     offset
    //       |~~~~~~~~~~~size~~~~~~~~~~|
    //   |---------|---------|---------|
    read_write_test! {
        unaligned_offset_aligned_end, 1, STORAGE - 1, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }
    //     offset
    //       |~~~~~~~~~size~~~~~~~~|
    //   |---------|---------|---------|
    read_write_test! {unaligned_offset_len, 1, STORAGE - 2, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }
    //     offset
    //       |~~~size~~~|
    //   |---------|---------|---------|
    read_write_test! {
        unaligned_offset_len_partial_cross_block, 1, BLOCK_SIZE, 0, ALIGNMENT, BLOCK_SIZE, STORAGE
    }
    //   offset
    //     |~size~|
    //   |---------|---------|---------|
    read_write_test! {
        ualigned_offset_len_partial_intra_block,
        1,
        BLOCK_SIZE - 2,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }

    // Same sets of test cases but with an additional block added to `rw_offset`
    read_write_test! {
        aligned_all_extra_offset,
        BLOCK_SIZE,
        STORAGE,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        aligned_offset_uanligned_size_extra_offset,
        BLOCK_SIZE,
        STORAGE - 1,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        aligned_offset_intra_block_extra_offset,
        BLOCK_SIZE,
        BLOCK_SIZE - 1,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        unaligned_offset_aligned_end_extra_offset,
        BLOCK_SIZE + 1,
        STORAGE - 1,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        unaligned_offset_len_extra_offset,
        BLOCK_SIZE + 1,
        STORAGE - 2,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        unaligned_offset_len_partial_cross_block_extra_offset,
        BLOCK_SIZE + 1,
        BLOCK_SIZE,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }
    read_write_test! {
        ualigned_offset_len_partial_intra_block_extra_offset,
        BLOCK_SIZE + 1,
        BLOCK_SIZE - 2,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE + BLOCK_SIZE
    }

    // Same sets of test cases but with unaligned output buffer {'misALIGNMENT` != 0}
    read_write_test! {
        aligned_all_unaligned_buffer,
        0,
        STORAGE,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        aligned_offset_uanligned_size_unaligned_buffer,
        0,
        STORAGE - 1,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        aligned_offset_intra_block_unaligned_buffer,
        0,
        BLOCK_SIZE - 1,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        unaligned_offset_aligned_end_unaligned_buffer,
        1,
        STORAGE - 1,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        unaligned_offset_len_unaligned_buffer,
        1,
        STORAGE - 2,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        unaligned_offset_len_partial_cross_block_unaligned_buffer,
        1,
        BLOCK_SIZE,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        ualigned_offset_len_partial_intra_block_unaligned_buffer,
        1,
        BLOCK_SIZE - 2,
        1,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }

    // Special cases where `rw_offset` is not block aligned but buffer aligned. This can
    // trigger some internal optimization code path.
    read_write_test! {
        buffer_aligned_offset_and_len,
        ALIGNMENT,
        STORAGE - ALIGNMENT,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        buffer_aligned_offset,
        ALIGNMENT,
        STORAGE - ALIGNMENT - 1,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        buffer_aligned_offset_aligned_end,
        ALIGNMENT,
        BLOCK_SIZE,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }
    read_write_test! {
        buffer_aligned_offset_intra_block,
        ALIGNMENT,
        BLOCK_SIZE - ALIGNMENT - 1,
        0,
        ALIGNMENT,
        BLOCK_SIZE,
        STORAGE
    }

    #[test]
    fn test_no_alignment_require_zero_size_scratch() {
        let mut blk = TestBlockDevice::new(1, 1, 0, 1);
        assert_eq!(required_scratch_size(blk.block_io(), 0).unwrap(), 0);
    }

    #[test]
    fn test_scratch_too_small() {
        let block_size: u64 = 512;
        let alignment: u64 = 64;
        let mut blk = TestBlockDevice::new(alignment, block_size, 0, block_size * 3);
        blk.scratch = vec![0u8; blk.scratch.len() - 1];
        assert!(blk.read(0, &mut vec![0u8; to_usize(block_size).unwrap()]).is_err());
    }

    #[test]
    fn test_read_overflow() {
        let mut blk = TestBlockDevice::new(1, 1, 0, 512);
        assert!(blk.read(512, &mut vec![0u8; 1]).is_err());
        assert!(blk.read(0, &mut vec![0u8; 513]).is_err());
    }

    #[test]
    fn test_read_arithmetic_overflow() {
        let mut blk = TestBlockDevice::new(1, 1, 0, 512);
        assert!(blk.read(u64::MAX, &mut vec![0u8; 1]).is_err());
    }

    #[test]
    fn test_write_overflow() {
        let mut blk = TestBlockDevice::new(1, 1, 0, 512);
        assert!(blk.write(512, vec![0u8; 1].as_mut_slice()).is_err());
        assert!(blk.write(0, vec![0u8; 513].as_mut_slice()).is_err());

        assert!(blk.write(512, vec![0u8; 1].as_slice()).is_err());
        assert!(blk.write(0, vec![0u8; 513].as_slice()).is_err());
    }

    #[test]
    fn test_write_arithmetic_overflow() {
        let mut blk = TestBlockDevice::new(1, 1, 0, 512);
        assert!(blk.write(u64::MAX, vec![0u8; 1].as_mut_slice()).is_err());
        assert!(blk.write(u64::MAX, vec![0u8; 1].as_slice()).is_err());
    }

    #[test]
    fn test_u64_not_narrower_than_usize() {
        // If this ever fails we need to adjust all code for >64 bit pointers and size.
        assert!(size_of::<u64>() >= size_of::<*const u8>());
        assert!(size_of::<u64>() >= size_of::<usize>());
    }
}
