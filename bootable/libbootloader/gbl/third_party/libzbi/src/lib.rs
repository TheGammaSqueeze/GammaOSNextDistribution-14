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

#![cfg_attr(not(any(test, android_dylib)), no_std)]

//! ZBI Processing Library
//!
//! This library is meant to be a generic processing library for the ZBI format
//! defined in sdk/lib/zbi-format/include/lib/zbi-format/zbi.h.
//!
//! Mainly it provides [`ZbiContainer`] that can create ([`ZbiContainer::new`]) valid container in
//! the provided buffer. Or parses and checks ([`ZbiContainer::parse`]) existing container in the
//! buffer. In both cases it provides iterator to walk thorough the items in container.
//!
//! Note: in both cases provided buffer must be properly aligned to [`ZBI_ALIGNMENT_USIZE`].
//! Using [`align_buffer`] would do proper alignment for you.
//!
//! ```
//! use zbi::{ZbiContainer, ZbiFlags, ZbiType, align_buffer};
//!
//! let mut buffer = [0; 200];
//! let mut buffer = align_buffer(&mut buffer[..]).unwrap();
//! let mut container = ZbiContainer::new(buffer).unwrap();
//! container.create_entry(ZbiType::DebugData, 0, ZbiFlags::default(), 10).unwrap();
//! container.create_entry_with_payload(ZbiType::DebugData, 0, ZbiFlags::default(), &[]).unwrap();
//!
//! assert_eq!(container.iter().count(), 2);
//!
//! let mut it = container.iter();
//! assert_eq!(it.next().unwrap().header.length, 10);
//! assert_eq!(it.next().unwrap().header.length, 0);
//! assert_eq!(it.next(), None);
//! ```

mod zbi_format;

use bitflags::bitflags;
use core::{
    fmt::{Debug, Display, Formatter},
    mem::{size_of, take},
    ops::DerefMut,
};
use zbi_format::*;
use zerocopy::{AsBytes, ByteSlice, ByteSliceMut, Ref};

type ZbiResult<T> = Result<T, ZbiError>;

/// [`ZbiContainer`] requires buffer and each entry to be aligned to this amount of bytes.
/// [`align_buffer`] can be used to adjust buffer alignment to match this requirement.
// ZBI_ALIGNMENT is u32 and it is not productive to `try_into()` to usize all the time.
// Expectation is that value should always fit in `u32` and `usize`, which we test.
pub const ZBI_ALIGNMENT_USIZE: usize = ZBI_ALIGNMENT as usize;

#[cfg(any(target_arch = "arm", target_arch = "aarch64"))]
const ZBI_ARCH_KERNEL_TYPE: ZbiType = ZbiType::KernelArm64;
#[cfg(any(target_arch = "x86", target_arch = "x86_64"))]
const ZBI_ARCH_KERNEL_TYPE: ZbiType = ZbiType::KernelX64;
#[cfg(any(target_arch = "riscv", target_arch = "riscv64"))]
const ZBI_ARCH_KERNEL_TYPE: ZbiType = ZbiType::KernelRiscv64;

/// Aligns provided slice to [`ZBI_ALIGNMENT_USIZE`] bytes.
///
/// # Returns
///
/// * `Ok(aligned_slice)` - on success, which can have `length == 0`
/// * [`ZbiError::TooBig`] - returned if there is not enough space to align the slice
pub fn align_buffer<B: ByteSlice>(buffer: B) -> ZbiResult<B> {
    let tail_offset = get_align_buffer_offset(&buffer[..])?;
    let (_, aligned_buffer) = buffer.split_at(tail_offset);
    Ok(aligned_buffer)
}

/// ZbiItem is element representation in [`ZbiContainer`]
///
/// It contains of `header` and `payload`. Both contain references to actual location in buffer.
/// And `payload` goes right after `header` in the buffer.
///
/// Header must be [`ZBI_ALIGNMENT_USIZE`] aligned in the buffer.
/// The length field specifies the actual payload length and does not include the size of padding.
/// Since all headers in [`ZbiContainer`] are [`ZBI_ALIGNMENT_USIZE`] aligned payload may be followed by padding,
/// which is included in [`ZbiContainer`] length, but not in each [`ZbiItem`]
#[derive(Debug)]
pub struct ZbiItem<B: ByteSlice> {
    /// ZBI header
    pub header: Ref<B, ZbiHeader>,
    /// Payload corresponding to ZBI header
    pub payload: B,
}

impl<B: ByteSlice, C: ByteSlice> PartialEq<ZbiItem<C>> for ZbiItem<B> {
    fn eq(&self, other: &ZbiItem<C>) -> bool {
        self.header.as_bytes() == other.header.as_bytes()
            && self.payload.as_bytes() == other.payload.as_bytes()
    }
}

impl<B: ByteSlice + PartialEq> ZbiItem<B> {
    /// Attempts to parse provided buffer.
    ///
    /// # Arguments
    /// * `buffer` - buffer to parse (can be mutable if further changes to element is required)
    ///
    /// # Returns
    ///
    /// * `Ok((ZbiItem, tail))` - if parsing was successful function returns `ZbiItem` and tail of
    ///                           buffer that wasn't used.
    /// * `Err(ZbiError)` - if parsing fails Err is returned.
    ///
    /// # Example
    ///
    /// ```
    /// use zbi::ZbiItem;
    ///
    /// # const LEN: usize = 100;
    /// # #[repr(align(8))]
    /// # struct ZbiAligned([u8; LEN]);
    /// # let buffer = ZbiAligned(core::array::from_fn::<_, LEN, _>(|_| 0u8));
    /// # let buffer = &buffer.0[..];
    /// let (zbi_item, tail) = ZbiItem::parse(buffer).unwrap();
    /// println!("{}", zbi_item.header.type_);
    /// println!("{}", tail.len());
    /// assert_eq!(zbi_item.header.length, zbi_item.payload.len() as u32);
    /// ```
    pub fn parse(buffer: B) -> ZbiResult<(ZbiItem<B>, B)> {
        is_zbi_aligned(&buffer)?;

        let (hdr, payload) = Ref::<B, ZbiHeader>::new_from_prefix(buffer).ok_or(ZbiError::Error)?;

        let item_payload_len =
            usize::try_from(hdr.length).map_err(|_| ZbiError::PlatformBadLength)?;
        if payload.len() < item_payload_len {
            return Err(ZbiError::TooBig);
        }

        let (item_payload, tail) = payload.split_at(item_payload_len);
        let item = ZbiItem { header: hdr, payload: item_payload };
        Ok((item, tail))
    }

    /// Validates `ZbiItem` header values.
    ///
    /// # Example
    ///
    /// ```
    /// use zbi::{ZbiItem, ZbiError};
    ///
    /// # const LEN: usize = 100;
    /// # #[repr(align(8))]
    /// # struct ZbiAligned([u8; LEN]);
    /// # let buffer = ZbiAligned(core::array::from_fn::<_, LEN, _>(|_| 0u8));
    /// # let buffer = &buffer.0[..];
    /// // E.g. if `header.magic = 0` this is invalid value.
    /// let (zbi_item, tail) = ZbiItem::parse(buffer).unwrap();
    /// assert_eq!(zbi_item.header.magic, 0);
    /// assert_eq!(zbi_item.is_valid(), Err(ZbiError::BadMagic));
    /// ```
    pub fn is_valid(&self) -> ZbiResult<()> {
        if self.header.magic != ZBI_ITEM_MAGIC {
            Err(ZbiError::BadMagic)
        } else if !self.header.get_flags().contains(ZbiFlags::VERSION) {
            Err(ZbiError::BadVersion)
        } else if !self.header.get_flags().contains(ZbiFlags::CRC32)
            && (self.header.crc32 != ZBI_ITEM_NO_CRC32)
        {
            Err(ZbiError::BadCrc)
        } else {
            Ok(())
        }
    }
}

impl<B: ByteSliceMut + PartialEq> ZbiItem<B> {
    /// Create `ZbiItem` with provided information and payload length.
    ///
    ///
    /// # Result
    ///
    /// * `(ZbiItem, tail)` - returned on success. `ZbiItem` would have payload of requested
    ///                       length. And tail would be remaining part of the `buffer` that wasn't
    ///                       used.
    /// * `ZbiError::BadAlignment` - if buffer wasn't aligned.
    /// * `ZbiError::TooBig` - if buffer is not long enough to hold
    ///                        [`ZbiHeader`] + `payload` of `payload_len`.
    /// * `ZbiError::PlatformBadLength` - if `payload_len` value is bigger than `u32::MAX`
    ///
    /// # Example
    /// ```
    /// use zbi::{ZbiItem, ZbiFlags, ZbiType};
    ///
    /// # const LEN: usize = 100;
    /// # #[repr(align(8))]
    /// # struct ZbiAligned([u8; LEN]);
    /// # let mut buffer = ZbiAligned(core::array::from_fn::<_, LEN, _>(|_| 0u8));
    /// # let mut buffer = &mut buffer.0[..];
    /// let (item, _tail) = ZbiItem::new(
    ///     &mut buffer[..],
    ///     ZbiType::KernelX64,
    ///     0,
    ///     ZbiFlags::default(),
    ///     2,
    /// ).unwrap();
    /// assert_eq!(item.header.length, 2);
    /// assert_eq!(item.payload.len(), 2);
    /// ```
    pub fn new(
        buffer: B,
        type_: ZbiType,
        extra: u32,
        flags: ZbiFlags,
        payload_len: usize,
    ) -> ZbiResult<(ZbiItem<B>, B)> {
        if buffer.len() < core::mem::size_of::<ZbiHeader>()
            || buffer.len() - core::mem::size_of::<ZbiHeader>() < payload_len
        {
            return Err(ZbiError::TooBig);
        }

        is_zbi_aligned(&buffer)?;

        // Need to convert payload_len to u32 type to put in structure
        let payload_len_u32 =
            u32::try_from(payload_len).map_err(|_| ZbiError::PlatformBadLength)?;

        let (mut header, item_tail) =
            Ref::<B, ZbiHeader>::new_from_prefix(buffer).ok_or(ZbiError::Error)?;
        header.type_ = type_ as u32;
        header.length = payload_len_u32;
        header.extra = extra;
        header.set_flags(&flags);
        header.reserved0 = 0;
        header.reserved1 = 0;
        header.magic = ZBI_ITEM_MAGIC;
        header.crc32 = ZBI_ITEM_NO_CRC32;

        // It is safe to do split because we checked if input buffer big enough to contain header
        // and requested payload size.
        let (payload, tail) = item_tail.split_at(payload_len);

        Ok((ZbiItem { header, payload }, tail))
    }
}

/// Main structure to work with ZBI format.
///
/// It allows to create valid buffer as well as parse existing one.
/// Both cases would allow to iterate over elements in the container via [`ZbiContainer::iter`] or
/// [`ZbiContainer::iter_mut`].
#[derive(Debug, PartialEq)]
pub struct ZbiContainer<B: ByteSlice> {
    /// Container specific [`ZbiHeader`], witch would be first element if ZBI buffer.
    ///
    /// `header.length` would show how many bytes after this header is used for ZBI elements and
    /// padding.
    ///
    /// `header.type_` is always [`ZbiType::Container`]
    pub header: Ref<B, ZbiHeader>,

    // Same as header.header.length, but for convenience is `usize` to avoid use of try_into and
    // returning ZbiError if we need to use it.
    // Use getters and setters to access length:
    //  - set_payload_length_usize()
    //  - get_payload_length_u32()
    //  - get_payload_length_usize()
    payload_length: usize,

    // Buffer that follows `header`. It contains ZbiItems + padding if any and remaining tail for
    // possible growth.
    buffer: B,
}

impl<B: ByteSlice> ZbiContainer<B> {
    // Helper to construct [`ZbiContainer`] which handles `paload_length` value, which should be
    // in sync with `header.length`.
    fn construct(header: Ref<B, ZbiHeader>, buffer: B) -> ZbiResult<Self> {
        Ok(Self {
            payload_length: usize::try_from(header.length)
                .map_err(|_| ZbiError::PlatformBadLength)?,
            header,
            buffer,
        })
    }

    /// Returns current container length as `u32`. Length doesn't include container header, only
    /// items and padding.
    pub fn get_payload_length_u32(&self) -> u32 {
        self.header.length
    }

    /// Returns current container length as `usize`. Length doesn't include container header, only
    /// items and padding.
    pub fn get_payload_length_usize(&self) -> usize {
        self.payload_length
    }

    /// Immutable iterator over ZBI elements. First element is first ZBI element after
    /// container header. Container header is not available via iterator.
    pub fn iter(&self) -> ZbiContainerIterator<impl ByteSlice + Default + Debug + PartialEq + '_> {
        ZbiContainerIterator {
            state: Ok(()),
            buffer: &self.buffer[..self.get_payload_length_usize()],
        }
    }

    /// Validates if ZBI is bootable for the target platform.
    ///
    /// # Returns
    ///
    /// * `Ok(())` - if bootable
    /// * Err([`ZbiError::IncompleteKernel`]) - if first element in container has type not bootable
    ///                                         on target platform.
    /// * Err([`ZbiError::Truncated`]) - if container is empty
    pub fn is_bootable(&self) -> ZbiResult<()> {
        let hdr = &self.header;
        if hdr.length == 0 {
            return Err(ZbiError::Truncated);
        }

        match self.iter().next() {
            Some(ZbiItem { header, payload: _ }) if header.type_ == ZBI_ARCH_KERNEL_TYPE as u32 => {
                Ok(())
            }
            Some(_) => Err(ZbiError::IncompleteKernel),
            None => Err(ZbiError::Truncated),
        }
    }

    /// Creates `ZbiContainer` from provided buffer.
    ///
    /// Buffer must be aligned to [`ZBI_ALIGNMENT_USIZE`] ([`align_buffer`] could be
    /// used for that). If buffer is mutable than container can be mutable.
    ///
    /// # Returns
    ///
    /// * `Ok(ZbiContainer)` - if buffer is aligned and contain valid buffer.
    /// * Err([`ZbiError`]) - if error occurred.
    pub fn parse(buffer: B) -> ZbiResult<Self> {
        is_zbi_aligned(&buffer)?;

        let (header, payload) =
            Ref::<B, ZbiHeader>::new_from_prefix(buffer).ok_or(ZbiError::Error)?;

        let length: usize = header.length.try_into().map_err(|_| ZbiError::TooBig)?;
        if length > payload.len() {
            return Err(ZbiError::Truncated);
        }

        if header.type_ != ZbiType::Container as u32 {
            return Err(ZbiError::BadType);
        } else if header.extra != ZBI_CONTAINER_MAGIC || header.magic != ZBI_ITEM_MAGIC {
            return Err(ZbiError::BadMagic);
        } else if !header.get_flags().contains(ZbiFlags::VERSION) {
            return Err(ZbiError::BadVersion);
        } else if !header.get_flags().contains(ZbiFlags::CRC32) && header.crc32 != ZBI_ITEM_NO_CRC32
        {
            return Err(ZbiError::BadCrc);
        }

        let res = Self::construct(header, payload)?;
        // Compiler thinks it is still borrowed when we reach Ok(res), so adding scope for it
        {
            let mut it = res.iter();
            for b in &mut it {
                b.is_valid()?;
            }

            // Check if there were item parsing errors
            it.state?;
        }
        Ok(res)
    }
}

impl<B: ByteSliceMut + PartialEq> ZbiContainer<B> {
    fn set_payload_length_usize(&mut self, len: usize) -> ZbiResult<()> {
        if self.buffer.len() < len {
            return Err(ZbiError::Truncated);
        }
        self.header.length = u32::try_from(len).map_err(|_| ZbiError::PlatformBadLength)?;
        self.payload_length = len;
        Ok(())
    }

    /// Creates new empty `ZbiContainer` using provided buffer.
    ///
    /// # Returns
    ///
    /// * `Ok(ZbiContainer)` - on success
    /// * Err([`ZbiError`]) - on error
    pub fn new(buffer: B) -> ZbiResult<Self> {
        let (item, buffer) =
            ZbiItem::new(buffer, ZbiType::Container, ZBI_CONTAINER_MAGIC, ZbiFlags::default(), 0)?;

        Self::construct(item.header, buffer)
    }

    fn align_tail(&mut self) -> ZbiResult<()> {
        let length = self.get_payload_length_usize();
        let align_offset = get_align_buffer_offset(&self.buffer[length..])?;
        let new_length = length + align_offset;
        self.set_payload_length_usize(new_length)?;
        Ok(())
    }

    /// Get payload slice for the next ZBI entry Next.
    ///
    /// Next entry should be added using [`ZbiContainer::create_entry`].
    ///
    /// This is useful when it's non-trivial to determine the length of a payload ahead of time -
    /// for example, loading a variable-length string from persistent storage.
    ///
    /// Rather than loading the payload into a temporary buffer, determining the length, then
    /// copying it into the ZBI, this function allows loading data directly into the ZBI. Since this
    /// buffer is currently unused area, loading data here does not affect the ZBI until
    /// zbi_create_entry() is called.
    ///
    /// # Example
    ///
    /// ```
    /// # use zbi::{ZbiContainer, ZbiFlags, ZbiType, align_buffer};
    /// #
    /// # let mut buffer = [0; 100];
    /// # let mut buffer = align_buffer(&mut buffer[..]).unwrap();
    /// # let mut container = ZbiContainer::new(buffer).unwrap();
    /// #
    /// # let payload_to_use = [1, 2, 3, 4];
    /// let next_payload = container.get_next_payload().unwrap();
    /// next_payload[..payload_to_use.len()].copy_from_slice(&payload_to_use[..]);
    ///
    /// container
    ///     .create_entry(ZbiType::KernelX64, 0, ZbiFlags::default(), payload_to_use.len())
    ///     .unwrap();
    ///
    /// assert_eq!(container.iter().count(), 1);
    /// assert_eq!(&*container.iter().next().unwrap().payload, &payload_to_use[..]);
    /// ```
    ///
    /// # Returns:
    /// `Ok(&mut [u8])` - on success; slice of buffer where next entries payload would be located.
    /// Err([`ZbiError::TooBig`]) - if buffer is not big enough for new element without payload.
    pub fn get_next_payload(&mut self) -> ZbiResult<&mut [u8]> {
        let length = self.get_payload_length_usize();
        let align_payload_offset = length
            .checked_add(size_of::<ZbiHeader>())
            .ok_or(ZbiError::LengthOverflow)?
            .checked_add(get_align_buffer_offset(&self.buffer[length..])?)
            .ok_or(ZbiError::LengthOverflow)?;
        if self.buffer.len() < align_payload_offset {
            return Err(ZbiError::TooBig);
        }
        Ok(&mut self.buffer[align_payload_offset..])
    }

    /// Creates a new ZBI entry with the provided payload.
    ///
    /// The new entry is aligned to [`ZBI_ALIGNMENT_USIZE`]. The capacity of the base ZBI must
    /// be large enough to fit the new entry.
    ///
    /// The [`ZbiFlags::VERSION`] is unconditionally set for the new entry.
    ///
    /// The [`ZbiFlags::CRC32`] flag yields an error because CRC computation is not yet
    /// supported.
    ///
    /// # Arguments
    ///   * `type_` - The new entry's type
    ///   * `extra` - The new entry's type-specific data
    ///   * `flags` - The new entry's flags
    ///   * `payload` - The payload, copied into the new entry
    ///
    /// # Returns:
    ///   * Ok(()) - on success
    ///   * Err([`ZbiError::TooBig`]) - if buffer is not big enough for new element with payload
    ///   * Err([`ZbiError::Crc32NotSupported`]) - if unsupported [`ZbiFlags::CRC32`] is set
    ///   * Err([`ZbiError`]) - if other errors occurred
    ///
    /// # Example
    /// ```
    /// # use zbi::{ZbiContainer, ZbiFlags, ZbiType, align_buffer};
    /// #
    /// # let mut buffer = [0; 100];
    /// # let mut buffer = align_buffer(&mut buffer[..]).unwrap();
    /// # let mut container = ZbiContainer::new(buffer).unwrap();
    /// #
    /// container
    ///     .create_entry_with_payload(ZbiType::KernelX64, 0, ZbiFlags::default(), &[1, 2, 3, 4])
    ///     .unwrap();
    /// assert_eq!(container.iter().count(), 1);
    /// assert_eq!(&*container.iter().next().unwrap().payload, &[1, 2, 3, 4]);
    /// ```
    pub fn create_entry_with_payload(
        &mut self,
        type_: ZbiType,
        extra: u32,
        flags: ZbiFlags,
        payload: &[u8],
    ) -> ZbiResult<()> {
        self.get_next_payload()?[..payload.len()].copy_from_slice(payload);
        self.create_entry(type_, extra, flags, payload.len())
    }

    /// Creates a new ZBI entry and returns a pointer to the payload.
    ///
    /// The new entry is aligned to [`ZBI_ALIGNMENT_USIZE`]. The capacity of the base ZBI must
    /// be large enough to fit the new entry.
    ///
    /// The [`ZbiFlags::VERSION`] is unconditionally set for the new entry.
    ///
    /// The [`ZbiFlags::CRC32`] flag yields an error because CRC computation is not yet
    /// supported.
    ///
    /// # Arguments
    ///   * `type_` - The new entry's type.
    ///   * `extra` - The new entry's type-specific data.
    ///   * `flags` - The new entry's flags.
    ///   * `payload_length` - The length of the new entry's payload.
    ///
    /// # Returns
    ///   * Ok(()) - On success.
    ///   * Err([`ZbiError::TooBig`]) - if buffer is not big enough for new element with payload
    ///   * Err([`ZbiError::Crc32NotSupported`]) - if unsupported [`ZbiFlags::CRC32`] is set
    ///   * Err([`ZbiError`]) - if other errors occurred
    ///
    /// # Example
    /// ```
    /// # use zbi::{ZbiContainer, ZbiFlags, ZbiType, align_buffer};
    /// #
    /// # let mut buffer = [0; 100];
    /// # let mut buffer = align_buffer(&mut buffer[..]).unwrap();
    /// # let mut container = ZbiContainer::new(buffer).unwrap();
    /// #
    /// # let payload_to_use = [1, 2, 3, 4];
    /// let next_payload = container.get_next_payload().unwrap();
    /// next_payload[..payload_to_use.len()].copy_from_slice(&payload_to_use[..]);
    ///
    /// container
    ///     .create_entry(ZbiType::KernelX64, 0, ZbiFlags::default(), payload_to_use.len())
    ///     .unwrap();
    ///
    /// assert_eq!(container.iter().count(), 1);
    /// assert_eq!(&*container.iter().next().unwrap().payload, &payload_to_use[..]);
    /// ```
    pub fn create_entry(
        &mut self,
        type_: ZbiType,
        extra: u32,
        flags: ZbiFlags,
        payload_length: usize,
    ) -> ZbiResult<()> {
        // We don't support CRC computation (yet?)
        if flags.contains(ZbiFlags::CRC32) {
            return Err(ZbiError::Crc32NotSupported);
        }

        let length = self.get_payload_length_usize();
        let (item, _) =
            ZbiItem::new(&mut self.buffer[length..], type_, extra, flags, payload_length)?;
        let used = length
            .checked_add(core::mem::size_of::<ZbiHeader>())
            .ok_or(ZbiError::LengthOverflow)?
            .checked_add(item.payload.len())
            .ok_or(ZbiError::LengthOverflow)?;
        self.set_payload_length_usize(used)?;
        self.align_tail()?;
        Ok(())
    }

    /// Extends a ZBI container with another container's payload.
    ///
    /// # Arguments
    ///   * `other` - The container to copy the payload from.
    ///
    /// # Returns
    ///   * `Ok(())` - On success.
    ///   * Err([`ZbiError::TooBig`]) - If container is too small.
    ///
    /// # Example
    /// ```
    /// # use zbi::{ZbiContainer, ZbiType, ZbiFlags, align_buffer};
    /// #
    /// # let mut buffer = [0; 200];
    /// # let mut buffer = align_buffer(&mut buffer[..]).unwrap();
    /// let mut container_0 = ZbiContainer::new(&mut buffer[..]).unwrap();
    /// container_0
    ///     .create_entry_with_payload(ZbiType::DebugData, 0, ZbiFlags::default(), &[0, 1])
    ///     .unwrap();
    ///
    /// # let mut buffer = [0; 200];
    /// # let mut buffer = align_buffer(&mut buffer[..]).unwrap();
    /// let mut container_1 = ZbiContainer::new(&mut buffer[..]).unwrap();
    /// container_1
    ///     .create_entry_with_payload(ZbiType::KernelX64, 0, ZbiFlags::default(), &[0, 1, 3, 4])
    ///     .unwrap();
    ///
    /// container_0.extend(&container_1).unwrap();
    ///
    /// assert_eq!(container_0.iter().count(), 2);
    /// # let cont0_element_1 = &container_0
    /// #     .iter()
    /// #     .enumerate()
    /// #     .filter_map(|(i, e)| if i == 1 { Some(e) } else { None })
    /// #     .collect::<Vec<_>>()[0];
    /// # let cont1_element_0 = &container_1.iter().next().unwrap();
    /// # assert_eq!(cont0_element_1, cont1_element_0);
    /// ```
    pub fn extend(&mut self, other: &ZbiContainer<impl ByteSlice + PartialEq>) -> ZbiResult<()> {
        let new_length = self
            .get_payload_length_usize()
            .checked_add(other.get_payload_length_usize())
            .ok_or(ZbiError::LengthOverflow)?;
        if self.buffer.len() < new_length {
            return Err(ZbiError::TooBig);
        }

        for b in other.iter() {
            let start = self.get_payload_length_usize();
            let end = start + core::mem::size_of::<ZbiHeader>();
            self.buffer[start..end].clone_from_slice(b.header.bytes());
            let start = end;
            let end = start + b.payload.len();
            self.buffer[start..end].clone_from_slice(&b.payload);
            self.set_payload_length_usize(end)?;
            self.align_tail()?;
        }
        Ok(())
    }
}

impl<B: ByteSlice + PartialEq + DerefMut> ZbiContainer<B> {
    /// Mutable iterator over ZBI elements. First element is first ZBI element after
    /// container header. Container header is not available via iterator.
    pub fn iter_mut(
        &mut self,
    ) -> ZbiContainerIterator<impl ByteSliceMut + Debug + Default + PartialEq + '_> {
        let length = self.get_payload_length_usize();
        ZbiContainerIterator { state: Ok(()), buffer: &mut self.buffer[..length] }
    }
}

/// Container iterator
// State is required to check elements are valid during parsing.
// During this parsing can fail and we need to check if iterator returned `None` because there are
// no more elements left or because there was an error.
// If container object already exist state should never contain error, since container was already
// verified.
pub struct ZbiContainerIterator<B> {
    state: ZbiResult<()>,
    buffer: B,
}

impl<B: ByteSlice + PartialEq + Default + Debug> Iterator for ZbiContainerIterator<B> {
    type Item = ZbiItem<B>;

    fn next(&mut self) -> Option<Self::Item> {
        // Align buffer before parsing
        match align_buffer(take(&mut self.buffer)) {
            Ok(v) => self.buffer = v,
            Err(_) => {
                self.state = Err(ZbiError::Truncated);
                return None;
            }
        };

        if self.buffer.is_empty() {
            return None;
        }

        match ZbiItem::<B>::parse(take(&mut self.buffer)) {
            Ok((item, mut tail)) => {
                // Remove item that was just parsed from the buffer for next
                // iteration before returning it.
                core::mem::swap(&mut tail, &mut self.buffer);
                Some(item)
            }
            Err(e) => {
                // If there was an error during item parsing,
                // make sure to set state to error, before signalling end of iteration.
                self.state = Err(e);
                None
            }
        }
    }
}

#[repr(u32)]
#[derive(AsBytes, Clone, Copy, Debug, Eq, PartialEq)]
/// All possible [`ZbiHeader`]`.type` values.
pub enum ZbiType {
    /// Each ZBI starts with a container header.
    /// * `length`: Total size of the image after this header. This includes all item headers,
    ///             payloads, and padding. It does not include the container header itself.
    ///             Must be a multiple of [`ZBI_ALIGNMENT_USIZE`].
    /// * `extra`:  Must be `ZBI_CONTAINER_MAGIC`.
    /// * `flags`:  Must be [`ZbiFlags::VERSION`] and no other flags.
    Container = ZBI_TYPE_CONTAINER,

    /// x86-64 kernel. See [`ZbiKernel`] for a payload description.
    //
    // 'KRNL'
    KernelX64 = ZBI_TYPE_KERNEL_X64,

    /// ARM64 kernel. See [`ZbiKernel`] for a payload description.
    //
    // KRN8
    KernelArm64 = ZBI_TYPE_KERNEL_ARM64,

    /// RISC-V kernel. See [`ZbiKernel`] for a payload description.
    //
    // 'KRNV'
    KernelRiscv64 = ZBI_TYPE_KERNEL_RISCV64,

    /// A discarded item that should just be ignored.  This is used for an
    /// item that was already processed and should be ignored by whatever
    /// stage is now looking at the ZBI.  An earlier stage already "consumed"
    /// this information, but avoided copying data around to remove it from
    /// the ZBI item stream.
    //
    // 'SKIP'
    Discard = ZBI_TYPE_DISCARD,

    /// A virtual disk image.  This is meant to be treated as if it were a
    /// storage device.  The payload (after decompression) is the contents of
    /// the storage device, in whatever format that might be.
    //
    // 'RDSK'
    StorageRamdisk = ZBI_TYPE_STORAGE_RAMDISK,

    /// The /boot filesystem in BOOTFS format, specified in
    /// <lib/zbi-format/internal/bootfs.h>.  This represents an internal
    /// contract between Zircon userboot (//docs/userboot.md), which handles
    /// the contents of this filesystem, and platform tooling, which prepares
    /// them.
    //
    // 'BFSB'
    StorageBootFs = ZBI_TYPE_STORAGE_BOOTFS,

    /// Storage used by the kernel (such as a compressed image containing the
    /// actual kernel).  The meaning and format of the data is specific to the
    /// kernel, though it always uses the standard (private) storage
    /// compression protocol. Each particular `ZbiType::Kernel{ARCH}` item image and its
    /// `StorageKernel` item image are intimately tied and one cannot work
    /// without the exact correct corresponding other.
    //
    // 'KSTR'
    StorageKernel = ZBI_TYPE_STORAGE_KERNEL,

    /// Device-specific factory data, stored in BOOTFS format.
    //
    // TODO(fxbug.dev/34597): This should not use the "STORAGE" infix.
    //
    // 'BFSF'
    StorageBootFsFactory = ZBI_TYPE_STORAGE_BOOTFS_FACTORY,

    /// A kernel command line fragment, a UTF-8 string that need not be
    /// NULL-terminated.  The kernel's own option parsing accepts only printable
    /// 'ASCI'I and treats all other characters as equivalent to whitespace. Multiple
    /// `ZbiType::CmdLine` items can appear.  They are treated as if concatenated with
    /// ' ' between each item, in the order they appear: first items in the bootable
    /// ZBI containing the kernel; then items in the ZBI synthesized by the boot
    /// loader.  The kernel interprets the [whole command line](../../../../docs/kernel_cmdline.md).
    //
    // 'CMDL'
    CmdLine = ZBI_TYPE_CMDLINE,

    /// The crash log from the previous boot, a UTF-8 string.
    //
    // 'BOOM'
    CrashLog = ZBI_TYPE_CRASHLOG,

    /// Physical memory region that will persist across warm boots. See `zbi_nvram_t`
    /// for payload description.
    //
    // 'NVLL'
    Nvram = ZBI_TYPE_NVRAM,

    /// Platform ID Information.
    //
    // 'PLID'
    PlatformId = ZBI_TYPE_PLATFORM_ID,

    /// Board-specific information.
    //
    // mBSI
    DrvBoardInfo = ZBI_TYPE_DRV_BOARD_INFO,

    /// CPU configuration. See `zbi_topology_node_t` for a description of the payload.
    CpuTopology = ZBI_TYPE_CPU_TOPOLOGY,

    /// Device memory configuration. See `zbi_mem_range_t` for a description of the payload.
    //
    // 'MEMC'
    MemConfig = ZBI_TYPE_MEM_CONFIG,

    /// Kernel driver configuration.  The `ZbiHeader.extra` field gives a
    /// ZBI_KERNEL_DRIVER_* type that determines the payload format.
    /// See <lib/zbi-format/driver-config.h> for details.
    //
    // 'KDRV'
    KernelDriver = ZBI_TYPE_KERNEL_DRIVER,

    /// 'ACPI' Root Table Pointer, a `u64` physical address.
    //
    // 'RSDP'
    AcpiRsdp = ZBI_TYPE_ACPI_RSDP,

    /// 'SMBI'OS entry point, a [u64] physical address.
    //
    // 'SMBI'
    Smbios = ZBI_TYPE_SMBIOS,

    /// EFI system table, a [u64] physical address.
    //
    // 'EFIS'
    EfiSystemTable = ZBI_TYPE_EFI_SYSTEM_TABLE,

    /// EFI memory attributes table. An example of this format can be found in UEFI 2.10
    /// section 4.6.4, but the consumer of this item is responsible for interpreting whatever
    /// the bootloader supplies (in particular the "version" field may differ as the format
    /// evolves).
    //
    // 'EMAT'
    EfiMemoryAttributesTable = ZBI_TYPE_EFI_MEMORY_ATTRIBUTES_TABLE,

    /// Framebuffer parameters, a `zbi_swfb_t` entry.
    //
    // 'SWFB'
    FrameBuffer = ZBI_TYPE_FRAMEBUFFER,

    /// The image arguments, data is a trivial text format of one "key=value" per line
    /// with leading whitespace stripped and "#" comment lines and blank lines ignored.
    /// It is processed by bootsvc and parsed args are shared to others via Arguments service.
    /// TODO: the format can be streamlined after the /config/additional_boot_args compat support is
    /// removed.
    //
    // 'IARG'
    ImageArgs = ZBI_TYPE_IMAGE_ARGS,

    /// A copy of the boot version stored within the sysconfig partition
    //
    // 'BVRS'
    BootVersion = ZBI_TYPE_BOOT_VERSION,

    /// MAC address for Ethernet, Wifi, Bluetooth, etc.  `ZbiHeader.extra`
    /// is a board-specific index to specify which device the MAC address
    /// applies to.  `ZbiHeader.length` gives the size in bytes, which
    /// varies depending on the type of address appropriate for the device.
    //
    // mMAC
    DrvMacAddress = ZBI_TYPE_DRV_MAC_ADDRESS,

    /// A partition map for a storage device, a `zbi_partition_map_t` header
    /// followed by one or more `zbi_partition_t` entries.  `ZbiHeader.extra`
    /// is a board-specific index to specify which device this applies to.
    //
    // mPRT
    DrvPartitionMap = ZBI_TYPE_DRV_PARTITION_MAP,

    /// Private information for the board driver.
    //
    // mBOR
    DrvBoardPrivate = ZBI_TYPE_DRV_BOARD_PRIVATE,

    /// Information about reboot
    // 'HWRB'
    HwRebootReason = ZBI_TYPE_HW_REBOOT_REASON,

    /// The serial number, an unterminated ASCII string of printable non-whitespace
    /// characters with length `ZbiHeader.length`.
    //
    // 'SRLN'
    SerialNumber = ZBI_TYPE_SERIAL_NUMBER,

    /// This type specifies a binary file passed in by the bootloader.
    /// The first byte specifies the length of the filename without a NUL terminator.
    /// The filename starts on the second byte.
    /// The file contents are located immediately after the filename.
    /// ```none
    /// Layout: | name_len |        name       |   payload
    ///           ^(1 byte)  ^(name_len bytes)     ^(length of file)
    /// ```
    //
    // 'BTFL'
    BootloaderFile = ZBI_TYPE_BOOTLOADER_FILE,

    /// The devicetree blob from the legacy boot loader, if any.  This is used only
    /// for diagnostic and development purposes.  Zircon kernel and driver
    /// configuration is entirely driven by specific ZBI items from the boot
    /// loader.  The boot shims for legacy boot loaders pass the raw devicetree
    /// along for development purposes, but extract information from it to populate
    /// specific ZBI items such as [`ZbiType::KernelDriver`] et al.
    DeviceTree = ZBI_TYPE_DEVICETREE,

    /// An arbitrary number of random bytes attested to have high entropy.  Any
    /// number of items of any size can be provided, but no data should be provided
    /// that is not true entropy of cryptographic quality.  This is used to seed
    /// secure cryptographic pseudo-random number generators.
    //
    // 'RAND'
    SecureEntropy = ZBI_TYPE_SECURE_ENTROPY,

    /// This provides a data dump and associated logging from a boot loader,
    /// shim, or earlier incarnation that wants its data percolated up by the
    /// booting Zircon kernel. See `zbi_debugdata_t` for a description of the
    /// payload.
    //
    // 'DBGD'
    DebugData = ZBI_TYPE_DEBUGDATA,
}

impl ZbiType {
    /// Checks if [`ZbiType`] is a Kernel type. (E.g. [`ZbiType::KernelX64`])
    /// ```
    /// # use zbi::ZbiType;
    /// assert!(ZbiType::KernelX64.is_kernel());
    /// ```
    pub fn is_kernel(&self) -> bool {
        ((*self as u32) & ZBI_TYPE_KERNEL_MASK) == ZBI_TYPE_KERNEL_PREFIX
    }

    /// Checks if [`ZbiType`] is a Driver Metadata type. (E.g. [`ZbiType::DrvBoardInfo`])
    /// ```
    /// # use zbi::ZbiType;
    /// assert!(ZbiType::DrvBoardInfo.is_driver_metadata());
    /// ```
    pub fn is_driver_metadata(&self) -> bool {
        ((*self as u32) & ZBI_TYPE_DRIVER_METADATA_MASK) == ZBI_TYPE_DRIVER_METADATA_PREFIX
    }
}

impl From<ZbiType> for u32 {
    fn from(val: ZbiType) -> Self {
        val as u32
    }
}

impl TryFrom<u32> for ZbiType {
    type Error = ZbiError;
    fn try_from(val: u32) -> Result<Self, Self::Error> {
        match val {
            ZBI_TYPE_KERNEL_X64 => Ok(Self::KernelX64),
            ZBI_TYPE_KERNEL_ARM64 => Ok(Self::KernelArm64),
            ZBI_TYPE_KERNEL_RISCV64 => Ok(Self::KernelRiscv64),
            ZBI_TYPE_CONTAINER => Ok(Self::Container),
            ZBI_TYPE_DISCARD => Ok(Self::Discard),
            ZBI_TYPE_STORAGE_RAMDISK => Ok(Self::StorageRamdisk),
            ZBI_TYPE_STORAGE_BOOTFS => Ok(Self::StorageBootFs),
            ZBI_TYPE_STORAGE_KERNEL => Ok(Self::StorageKernel),
            ZBI_TYPE_STORAGE_BOOTFS_FACTORY => Ok(Self::StorageBootFsFactory),
            ZBI_TYPE_CMDLINE => Ok(Self::CmdLine),
            ZBI_TYPE_CRASHLOG => Ok(Self::CrashLog),
            ZBI_TYPE_NVRAM => Ok(Self::Nvram),
            ZBI_TYPE_PLATFORM_ID => Ok(Self::PlatformId),
            ZBI_TYPE_DRV_BOARD_INFO => Ok(Self::DrvBoardInfo),
            ZBI_TYPE_CPU_TOPOLOGY => Ok(Self::CpuTopology),
            ZBI_TYPE_MEM_CONFIG => Ok(Self::MemConfig),
            ZBI_TYPE_KERNEL_DRIVER => Ok(Self::KernelDriver),
            ZBI_TYPE_ACPI_RSDP => Ok(Self::AcpiRsdp),
            ZBI_TYPE_SMBIOS => Ok(Self::Smbios),
            ZBI_TYPE_EFI_SYSTEM_TABLE => Ok(Self::EfiSystemTable),
            ZBI_TYPE_EFI_MEMORY_ATTRIBUTES_TABLE => Ok(Self::EfiMemoryAttributesTable),
            ZBI_TYPE_FRAMEBUFFER => Ok(Self::FrameBuffer),
            ZBI_TYPE_IMAGE_ARGS => Ok(Self::ImageArgs),
            ZBI_TYPE_BOOT_VERSION => Ok(Self::BootVersion),
            ZBI_TYPE_DRV_MAC_ADDRESS => Ok(Self::DrvMacAddress),
            ZBI_TYPE_DRV_PARTITION_MAP => Ok(Self::DrvPartitionMap),
            ZBI_TYPE_DRV_BOARD_PRIVATE => Ok(Self::DrvBoardPrivate),
            ZBI_TYPE_HW_REBOOT_REASON => Ok(Self::HwRebootReason),
            ZBI_TYPE_SERIAL_NUMBER => Ok(Self::SerialNumber),
            ZBI_TYPE_BOOTLOADER_FILE => Ok(Self::BootloaderFile),
            ZBI_TYPE_DEVICETREE => Ok(Self::DeviceTree),
            ZBI_TYPE_SECURE_ENTROPY => Ok(Self::SecureEntropy),
            ZBI_TYPE_DEBUGDATA => Ok(Self::DebugData),
            _ => Err(ZbiError::BadType),
        }
    }
}

bitflags! {
    /// Flags associated with an item.
    ///
    /// A valid flags value must always include [`ZbiFlags::VERSION`].
    /// Values should also contain [`ZbiFlags::CRC32`] for any item
    /// where it's feasible to compute the [`ZbiFlags::CRC32`] at build time.
    /// Other flags are specific to each type.
    ///
    /// Matches C-reference `zbi_flags_t` which is `uint32_t`.
    pub struct ZbiFlags: u32 {
        /// This flag is always required.
        const VERSION = ZBI_FLAGS_VERSION;
        /// ZBI items with the `CRC32` flag must have a valid `crc32`.
        /// Otherwise their `crc32` field must contain `ZBI_ITEM_NO_CRC32`
        const CRC32 = ZBI_FLAGS_CRC32;
    }
}

/// A valid flags must always include [`ZbiFlags::VERSION`].
impl Default for ZbiFlags {
    fn default() -> ZbiFlags {
        ZbiFlags::VERSION
    }
}

/// Rust type generated from C-reference structure `zbi_header_t`.
///
/// It must correspond to following definition:
/// ```c++
/// typedef struct {
///   // ZBI_TYPE_* constant.
///   zbi_type_t type;
///
///   // Size of the payload immediately following this header.  This
///   // does not include the header itself nor any alignment padding
///   // after the payload.
///   uint32_t length;
///
///   // Type-specific extra data.  Each type specifies the use of this
///   // field.  When not explicitly specified, it should be zero.
///   uint32_t extra;
///
///   // Flags for this item.
///   zbi_flags_t flags;
///
///   // For future expansion.  Set to 0.
///   uint32_t reserved0;
///   uint32_t reserved1;
///
///   // Must be ZBI_ITEM_MAGIC.
///   uint32_t magic;
///
///   // Must be the CRC32 of payload if ZBI_FLAGS_CRC32 is set,
///   // otherwise must be ZBI_ITEM_NO_CRC32.
///   uint32_t crc32;
/// } zbi_header_t;
/// ```
pub type ZbiHeader = zbi_header_t;

impl ZbiHeader {
    /// Helper function to get `ZbiHeader.flags: u32` as `ZbiFlags`.
    pub fn get_flags(&self) -> ZbiFlags {
        ZbiFlags::from_bits_truncate(self.flags)
    }
    /// Helper function to set `ZbiHeader.flags: u32` from `ZbiFlags`.
    pub fn set_flags(&mut self, flags: &ZbiFlags) {
        self.flags = flags.bits();
    }
}

/// The kernel image.
///
/// In a bootable ZBI this item must always be first,
/// immediately after the [`ZbiType::Container`] header.  The contiguous memory
/// image of the kernel is formed from the [`ZbiType::Container`] header, the
/// `ZbiType::Kernel{ARCH}` header, and the payload.
///
/// The boot loader loads the whole image starting with the container header
/// through to the end of the kernel item's payload into contiguous physical
/// memory.  It then constructs a partial ZBI elsewhere in memory, which has
/// a [`ZbiType::Container`] header of its own followed by all the other items
/// that were in the booted ZBI plus other items synthesized by the boot
/// loader to describe the machine.  This partial ZBI must be placed at an
/// address (where the container header is found) that is aligned to the
/// machine's page size.  The precise protocol for transferring control to
/// the kernel's entry point varies by machine.
///
/// On all machines, the kernel requires some amount of scratch memory to be
/// available immediately after the kernel image at boot.  It needs this
/// space for early setup work before it has a chance to read any memory-map
/// information from the boot loader.  The `reserve_memory_size` field tells
/// the boot loader how much space after the kernel's load image it must
/// leave available for the kernel's use.  The boot loader must place its
/// constructed ZBI or other reserved areas at least this many bytes after
/// the kernel image.
///
/// # x86-64
///
/// The kernel assumes it was loaded at a fixed physical address of
/// 0x100000 (1MB).  `ZbiKernel.entry` is the absolute physical address
/// of the PC location where the kernel will start.
/// TODO(fxbug.dev/24762): Perhaps this will change??
/// The processor is in 64-bit mode with direct virtual to physical
/// mapping covering the physical memory where the kernel and
/// bootloader-constructed ZBI were loaded.
/// The %rsi register holds the physical address of the
/// bootloader-constructed ZBI.
/// All other registers are unspecified.
///
/// # ARM64
///
/// `ZbiKernel.entry` is an offset from the beginning of the image
/// (i.e., the [`ZbiType::Container`] header before the [`ZbiType::KernelArm64`]
/// header) to the PC location in the image where the kernel will
/// start.  The processor is in physical address mode at EL1 or
/// above.  The kernel image and the bootloader-constructed ZBI each
/// can be loaded anywhere in physical memory.  The x0 register
/// holds the physical address of the bootloader-constructed ZBI.
/// All other registers are unspecified.
///
/// # RISCV64
///
/// `ZbiKernel.entry` is an offset from the beginning of the image (i.e.,
/// the [`ZbiType::Container`] header before the [`ZbiType::KernelRiscv64`] header)
/// to the PC location in the image where the kernel will start.  The
/// processor is in S mode, satp is zero, sstatus.SIE is zero.  The kernel
/// image and the bootloader-constructed ZBI each can be loaded anywhere in
/// physical memory, aligned to 4KiB.  The a0 register holds the HART ID,
/// and the a1 register holds the 4KiB-aligned physical address of the
/// bootloader-constructed ZBI.  All other registers are unspecified.
///
/// # C-reference type
/// ```c
/// typedef struct {
///   // Entry-point address.  The interpretation of this differs by machine.
///   uint64_t entry;
///
///   // Minimum amount (in bytes) of scratch memory that the kernel requires
///   // immediately after its load image.
///   uint64_t reserve_memory_size;
/// } zbi_kernel_t;
/// ```
pub type ZbiKernel = zbi_kernel_t;

#[derive(Debug, PartialEq)]
/// Error values that can be returned by function in this library
pub enum ZbiError {
    /// Generic error
    Error,
    /// Bad type
    BadType,
    /// Bad magic
    BadMagic,
    /// Bad version
    BadVersion,
    /// Bad CRC
    BadCrc,
    /// Bad Alignment
    BadAlignment,
    /// Truncaded error
    Truncated,
    /// Too big
    TooBig,
    /// Incomplete Kernel
    IncompleteKernel,
    /// Bad ZBI length for this platform
    PlatformBadLength,
    /// CRC32 is not supported yet
    Crc32NotSupported,
    /// Length type overflow
    LengthOverflow,
}

// Unfortunately thiserror is not available in `no_std` world.
// Thus `Display` implementation is required.
impl Display for ZbiError {
    fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
        let str = match self {
            ZbiError::Error => "Generic error",
            ZbiError::BadType => "Bad type",
            ZbiError::BadMagic => "Bad magic",
            ZbiError::BadVersion => "Bad version",
            ZbiError::BadCrc => "Bad CRC",
            ZbiError::BadAlignment => "Bad Alignment",
            ZbiError::Truncated => "Truncaded error",
            ZbiError::TooBig => "Too big",
            ZbiError::IncompleteKernel => "Incomplete Kernel",
            ZbiError::PlatformBadLength => "Bad ZBI length for this platform",
            ZbiError::Crc32NotSupported => "CRC32 is not supported yet",
            ZbiError::LengthOverflow => "Length type overflow",
        };
        write!(f, "{str}")
    }
}

// Returns offset/idx of the first buffer element that will be aligned to `ZBI_ALIGNMENT`
fn get_align_buffer_offset(buffer: impl ByteSlice) -> ZbiResult<usize> {
    let addr = buffer.as_ptr() as usize;
    match addr % ZBI_ALIGNMENT_USIZE {
        0 => Ok(0),
        rem => {
            let tail_offset = ZBI_ALIGNMENT_USIZE - rem;
            if tail_offset > buffer.len() {
                return Err(ZbiError::TooBig);
            }
            Ok(tail_offset)
        }
    }
}

// Check if buffer is ZbiAligned
fn is_zbi_aligned(buffer: &impl ByteSlice) -> ZbiResult<()> {
    match (buffer.as_ptr() as usize) % ZBI_ALIGNMENT_USIZE {
        0 => Ok(()),
        _ => Err(ZbiError::BadAlignment),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Debug, PartialEq, Default)]
    struct TestZbiBuilder<'a> {
        buffer: &'a mut [u8],
        tail_offset: usize,
    }
    impl<'a> TestZbiBuilder<'a> {
        pub fn new(buffer: &'a mut [u8]) -> TestZbiBuilder<'a> {
            TestZbiBuilder { buffer, tail_offset: 0 }
        }
        pub fn add<T: AsBytes>(mut self, t: T) -> Self {
            t.write_to_prefix(&mut self.buffer[self.tail_offset..]).unwrap();
            self.tail_offset += size_of::<T>();
            self
        }
        pub fn add_slice(mut self, buf: &'a [u8]) -> Self {
            self.buffer[self.tail_offset..self.tail_offset + buf.len()].copy_from_slice(buf);
            self.tail_offset += buf.len();
            self
        }
        pub fn get_header_default() -> ZbiHeader {
            ZbiHeader {
                type_: ZbiType::KernelX64 as u32,
                length: 0,
                extra: ZBI_ITEM_MAGIC,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            }
        }
        pub fn item_default(self, payload: &'a [u8]) -> Self {
            self.item(
                ZbiHeader {
                    length: payload.len().try_into().unwrap(),
                    ..Self::get_header_default()
                },
                payload,
            )
        }
        pub fn item(self, header: ZbiHeader, payload: &'a [u8]) -> Self {
            self.add(header).add_slice(&payload[..payload.len()])
        }
        pub fn container_hdr(self, payload_len: usize) -> Self {
            self.item(
                ZbiHeader {
                    type_: ZBI_TYPE_CONTAINER,
                    length: payload_len.try_into().unwrap(),
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: ZbiFlags::default().bits(),
                    magic: ZBI_ITEM_MAGIC,
                    crc32: ZBI_ITEM_NO_CRC32,
                    ..Default::default()
                },
                &[],
            )
        }
        pub fn padding(mut self, val: u8, bytes: usize) -> Self {
            self.buffer[self.tail_offset..self.tail_offset + bytes].fill(val);
            self.tail_offset += bytes;
            self
        }
        pub fn align(mut self) -> Self {
            let rem = self.tail_offset % ZBI_ALIGNMENT_USIZE;
            if rem != 0 {
                self.tail_offset += ZBI_ALIGNMENT_USIZE - rem;
            }
            self
        }
        // Assumption is that first item in buffer is container header/item
        pub fn update_container_length(self) -> Self {
            let payload_length = self.tail_offset - size_of::<ZbiHeader>();
            let item = ZbiHeader {
                type_: ZBI_TYPE_CONTAINER,
                length: payload_length.try_into().unwrap(),
                extra: ZBI_CONTAINER_MAGIC,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            };
            item.write_to_prefix(&mut self.buffer[..]).unwrap();
            self
        }
        pub fn build(self) -> &'a mut [u8] {
            &mut self.buffer[..self.tail_offset]
        }
    }

    const ZBI_HEADER_SIZE: usize = core::mem::size_of::<ZbiHeader>();
    const ALIGNED_8_SIZE: usize = ZBI_HEADER_SIZE * 20;
    #[repr(align(8))]
    struct ZbiAligned([u8; ALIGNED_8_SIZE]);
    impl Default for ZbiAligned {
        fn default() -> Self {
            ZbiAligned(core::array::from_fn::<_, ALIGNED_8_SIZE, _>(|_| 0u8))
        }
    }

    #[test]
    fn zbi_test_align_overflow() {
        assert!(usize::MAX > ZBI_ALIGNMENT.try_into().unwrap());
        assert_eq!(u32::try_from(ZBI_ALIGNMENT_USIZE).unwrap(), ZBI_ALIGNMENT);
    }

    #[test]
    fn zbi_test_item_new() {
        let mut buffer = ZbiAligned::default();
        let expect = get_test_zbi_headers(1)[0];

        let (item, _) = ZbiItem::new(
            &mut buffer.0[..],
            expect.type_.try_into().unwrap(),
            expect.extra,
            expect.get_flags(),
            expect.length.try_into().unwrap(),
        )
        .unwrap();

        assert_eq!(*item.header, expect);
        assert_eq!(item.payload.len(), expect.length.try_into().unwrap());

        let u32_array =
            Ref::<&[u8], [u32]>::new_slice_from_prefix(&buffer.0[..ZBI_HEADER_SIZE], 8).unwrap().0;
        assert_eq!(u32_array[0], expect.type_);
        assert_eq!(u32_array[1], expect.length);
        assert_eq!(u32_array[2], expect.extra);
        assert_eq!(u32_array[3], expect.flags);
        // u32_array[4..5] - reserved
        assert_eq!(u32_array[6], expect.magic);
        assert_eq!(u32_array[7], expect.crc32);
    }

    #[test]
    fn zbi_test_item_new_too_small() {
        let mut buffer = ZbiAligned::default();

        assert_eq!(
            ZbiItem::new(
                &mut buffer.0[..ZBI_HEADER_SIZE - 1],
                ZbiType::Container,
                0,
                ZbiFlags::default(),
                0
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_item_new_not_aligned() {
        let mut buffer = ZbiAligned::default();
        for offset in [1, 2, 4] {
            assert_eq!(
                ZbiItem::new(
                    &mut buffer.0[offset..ZBI_HEADER_SIZE + offset],
                    ZbiType::Container,
                    0,
                    ZbiFlags::default(),
                    0
                ),
                Err(ZbiError::BadAlignment)
            );
        }
    }

    #[test]
    fn zbi_test_item_parse() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0).build();
        let buffer_hdr_extra_expected =
            Ref::<&[u8], [u32]>::new_slice_from_prefix(&buffer[8..12], 1).unwrap().0[0];

        let (zbi_item, _tail) = ZbiItem::parse(buffer).unwrap();

        assert_eq!(zbi_item.header.extra, buffer_hdr_extra_expected);
    }

    #[test]
    fn zbi_test_item_edit() {
        let mut buffer = ZbiAligned::default();
        let buffer_build = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0).build();
        let buffer_hdr_type =
            Ref::<&[u8], [u32]>::new_slice_from_prefix(&buffer_build[0..4], 1).unwrap().0[0];
        assert_eq!(buffer_hdr_type, ZBI_TYPE_CONTAINER);

        let (mut zbi_item, _tail) = ZbiItem::parse(&mut buffer_build[..]).unwrap();
        zbi_item.header.type_ = ZBI_TYPE_KERNEL_X64;
        let buffer_hdr_type =
            Ref::<&[u8], [u32]>::new_slice_from_prefix(&buffer_build[0..4], 1).unwrap().0[0];
        assert_eq!(buffer_hdr_type, ZBI_TYPE_KERNEL_X64);
    }

    #[test]
    fn zbi_test_container_new() {
        let mut buffer = ZbiAligned::default();
        let _container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        let expect_hdr = ZbiHeader {
            type_: ZBI_TYPE_CONTAINER,
            length: 0,
            extra: ZBI_CONTAINER_MAGIC,
            flags: ZbiFlags::default().bits(),
            magic: ZBI_ITEM_MAGIC,
            crc32: ZBI_ITEM_NO_CRC32,
            ..Default::default()
        };

        let (item, _) = ZbiItem::parse(&buffer.0[..]).unwrap();
        assert_eq!(*item.header, expect_hdr);
        assert_eq!(item.payload.len(), 0);
    }

    #[test]
    fn zbi_test_container_new_too_small() {
        let mut buffer = ZbiAligned::default();
        assert_eq!(ZbiContainer::new(&mut buffer.0[..ZBI_HEADER_SIZE - 1]), Err(ZbiError::TooBig));
    }

    #[test]
    fn zbi_test_container_new_unaligned() {
        let mut buffer = ZbiAligned::default();
        for offset in [1, 2, 3, 4, 5, 6, 7] {
            assert_eq!(
                ZbiContainer::new(&mut buffer.0[offset..ZBI_HEADER_SIZE + offset]),
                Err(ZbiError::BadAlignment)
            );
        }
    }

    #[test]
    fn zbi_test_container_parse_empty() {
        let mut buffer = ZbiAligned::default();
        let _container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        let expect_hdr = ZbiHeader {
            type_: ZBI_TYPE_CONTAINER,
            length: 0,
            extra: ZBI_CONTAINER_MAGIC,
            flags: ZbiFlags::default().bits(),
            magic: ZBI_ITEM_MAGIC,
            crc32: ZBI_ITEM_NO_CRC32,
            ..Default::default()
        };

        let ZbiContainer { header, buffer: _, payload_length } =
            ZbiContainer::parse(&buffer.0[..]).unwrap();
        assert_eq!(*header, expect_hdr);
        assert_eq!(payload_length, 0);
    }

    #[test]
    fn zbi_test_container_parse_bad_type() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .item(
                ZbiHeader {
                    type_: 0,
                    length: 0,
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: ZbiFlags::default().bits(),
                    magic: ZBI_ITEM_MAGIC,
                    crc32: ZBI_ITEM_NO_CRC32,
                    ..Default::default()
                },
                &[],
            )
            .build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadType))
    }

    #[test]
    fn zbi_test_container_parse_bad_magic() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .item(
                ZbiHeader {
                    type_: ZBI_TYPE_CONTAINER,
                    length: 0,
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: ZbiFlags::default().bits(),
                    magic: 0,
                    crc32: ZBI_ITEM_NO_CRC32,
                    ..Default::default()
                },
                &[],
            )
            .build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadMagic))
    }

    #[test]
    fn zbi_test_container_parse_bad_version() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .item(
                ZbiHeader {
                    type_: ZBI_TYPE_CONTAINER,
                    length: 0,
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: (ZbiFlags::default() & !ZbiFlags::VERSION).bits(),
                    magic: ZBI_ITEM_MAGIC,
                    crc32: ZBI_ITEM_NO_CRC32,
                    ..Default::default()
                },
                &[],
            )
            .build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadVersion))
    }

    #[test]
    fn zbi_test_container_parse_bad_crc32() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .item(
                ZbiHeader {
                    type_: ZBI_TYPE_CONTAINER,
                    length: 0,
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: (ZbiFlags::default() & !ZbiFlags::CRC32).bits(),
                    magic: ZBI_ITEM_MAGIC,
                    crc32: 0,
                    ..Default::default()
                },
                &[],
            )
            .build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadCrc))
    }

    #[test]
    fn zbi_test_container_parse_entries_bad_magic() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .item(
                ZbiHeader {
                    type_: ZBI_TYPE_CONTAINER,
                    length: 0,
                    extra: ZBI_CONTAINER_MAGIC,
                    flags: (ZbiFlags::default() & !ZbiFlags::CRC32).bits(),
                    magic: ZBI_ITEM_MAGIC,
                    crc32: 0,
                    ..Default::default()
                },
                &[],
            )
            .build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadCrc))
    }

    #[test]
    fn zbi_test_container_parse() {
        let expected_payloads: [&[u8]; 9] = [
            &[1],
            &[1, 2],
            &[1, 2, 3],
            &[1, 2, 3, 4],
            &[1, 2, 3, 4, 5],
            &[1, 2, 3, 4, 5, 6],
            &[1, 2, 3, 4, 5, 6, 7],
            &[1, 2, 3, 4, 5, 6, 7, 8],
            &[1, 2, 3, 4, 5, 6, 7, 8, 9],
        ];
        let expected_items = expected_payloads.map(|x| {
            (
                ZbiHeader {
                    length: x.len().try_into().unwrap(),
                    ..TestZbiBuilder::get_header_default()
                },
                x,
            )
        });
        let mut buffer = ZbiAligned::default();
        let mut builder = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0);
        for payloads in expected_payloads {
            builder = builder.align().item_default(payloads).align()
        }
        let buffer = builder.update_container_length().build();

        let zbi_container = ZbiContainer::parse(&*buffer).unwrap();

        let mut it = zbi_container.iter();
        for (expected_hdr, expected_payload) in expected_items.iter() {
            let Some(item) = it.next() else { panic!("expecting iterator with value") };
            assert_eq!(item.header.into_ref(), expected_hdr);
            assert_eq!(&item.payload[..], *expected_payload);
        }
        assert!(it.next().is_none());
    }

    #[test]
    fn zbi_test_container_parse_unaligned() {
        let buffer = ZbiAligned::default();
        for offset in [1, 2, 3, 4, 5, 6, 7] {
            assert_eq!(ZbiContainer::parse(&buffer.0[offset..]), Err(ZbiError::BadAlignment));
        }
    }

    #[test]
    fn zbi_test_container_parse_without_last_padding_fail_truncated() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1])
            .align()
            .item_default(&[1, 2])
            .update_container_length()
            .build();

        assert_eq!(ZbiContainer::parse(&*buffer), Err(ZbiError::Truncated));
    }

    #[test]
    fn zbi_test_container_parse_error_payload_truncated() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .add_slice(&[1])
            .update_container_length()
            .build();

        assert_eq!(ZbiContainer::parse(&buffer[..buffer.len() - 1]), Err(ZbiError::Truncated));
    }

    #[test]
    fn zbi_test_container_parse_error_truncated() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .padding(0, 1)
            .update_container_length()
            .build();

        assert_eq!(ZbiContainer::parse(&buffer[..buffer.len() - 1]), Err(ZbiError::Truncated));
    }

    #[test]
    fn zbi_test_container_parse_bad_first_entry_marked() {
        let mut buffer = get_test_creference_buffer();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        container
            .iter_mut()
            .filter(|e| {
                [ZbiType::CmdLine as u32, ZbiType::StorageRamdisk as u32].contains(&e.header.type_)
            })
            .for_each(|mut e| e.header.magic = 0);

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadMagic));
    }

    #[test]
    fn zbi_test_container_parse_bad_entry_magic() {
        let mut buffer = get_test_creference_buffer();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        container
            .iter_mut()
            .filter(|e| ZbiType::CmdLine as u32 == e.header.type_)
            .for_each(|mut e| e.header.magic = 0);

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadMagic));
    }

    #[test]
    fn zbi_test_container_parse_bad_entry_version() {
        let mut buffer = get_test_creference_buffer();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        container
            .iter_mut()
            .filter(|e| ZbiType::CmdLine as u32 == e.header.type_)
            .for_each(|mut e| e.header.flags &= (!ZbiFlags::VERSION).bits());

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadVersion));
    }

    #[test]
    fn zbi_test_container_parse_bad_entry_crc() {
        let mut buffer = get_test_creference_buffer();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        container.iter_mut().filter(|e| ZbiType::CmdLine as u32 == e.header.type_).for_each(
            |mut e| {
                e.header.flags &= (!ZbiFlags::CRC32).bits();
                e.header.crc32 = 0;
            },
        );

        assert_eq!(ZbiContainer::parse(&buffer.0[..]), Err(ZbiError::BadCrc));
    }

    #[test]
    fn zbi_test_container_new_entry() {
        let mut buffer = ZbiAligned::default();
        let new_entries = get_test_entries_all();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        for (e, payload) in &new_entries {
            container.get_next_payload().unwrap()[..payload.len()].copy_from_slice(payload);
            container
                .create_entry(e.type_.try_into().unwrap(), e.extra, e.get_flags(), payload.len())
                .unwrap();
        }

        let container = ZbiContainer::parse(&buffer.0[..]).unwrap();
        check_container_made_of(&container, &new_entries);
    }

    #[test]
    fn zbi_test_container_new_entry_crc32_not_supported() {
        let mut buffer = ZbiAligned::default();
        let (new_entry, payload) = get_test_entry_nonempty_payload();
        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        assert_eq!(
            container.create_entry_with_payload(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                ZbiFlags::default() | ZbiFlags::CRC32,
                payload,
            ),
            Err(ZbiError::Crc32NotSupported)
        );
    }

    #[test]
    fn zbi_test_container_new_entry_no_space_left() {
        let mut buffer = ZbiAligned::default();
        let new_entry = get_test_entry_empty_payload().0;

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();

        for _ in 1..(ALIGNED_8_SIZE / ZBI_HEADER_SIZE) {
            container
                .create_entry(
                    new_entry.type_.try_into().unwrap(),
                    new_entry.extra,
                    new_entry.get_flags(),
                    new_entry.length.try_into().unwrap(),
                )
                .unwrap();
        }

        // Now there is not enough space and it should fail
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                new_entry.length.try_into().unwrap(),
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_container_new_entry_no_space_for_header() {
        let mut buffer = ZbiAligned::default();
        let new_entry = get_test_entry_empty_payload().0;

        let buf_len = 2 * core::mem::size_of::<ZbiHeader>() - 1;
        let mut container = ZbiContainer::new(&mut buffer.0[..buf_len]).unwrap();

        // Now there is not enough space for header and it should fail
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                0,
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_container_new_entry_no_space_for_payload() {
        let mut buffer = ZbiAligned::default();
        let (new_entry, payload) = get_test_entry_nonempty_payload();

        let buf_len = 2 * core::mem::size_of::<ZbiHeader>() + payload.len() - 1;
        let mut container = ZbiContainer::new(&mut buffer.0[..buf_len]).unwrap();

        // Now there is not enough space for header and it should fail
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                new_entry.length.try_into().unwrap(),
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_container_new_entry_with_payload_just_enough_to_fit_no_align() {
        let mut buffer = ZbiAligned::default();
        let (new_entry, _payload) = get_test_entry_empty_payload();
        let payload = [0; ZBI_ALIGNMENT_USIZE];
        let buf_len = 2 * core::mem::size_of::<ZbiHeader>()
            + payload.len()
            + (/*alignment*/ZBI_ALIGNMENT_USIZE - payload.len());
        let mut container = ZbiContainer::new(&mut buffer.0[..buf_len]).unwrap();
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                payload.len(),
            ),
            Ok(())
        );
    }
    #[test]
    fn zbi_test_container_new_entry_with_payload_just_enough_to_fit_with_alignment() {
        let mut buffer = ZbiAligned::default();
        let (new_entry, payload) = get_test_entry_nonempty_payload();
        let buf_len = 2 * core::mem::size_of::<ZbiHeader>()
            + payload.len()
            + (ZBI_ALIGNMENT_USIZE - payload.len()/*alignment*/);
        let mut container = ZbiContainer::new(&mut buffer.0[..buf_len]).unwrap();
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                new_entry.length.try_into().unwrap(),
            ),
            Ok(())
        );
    }

    #[test]
    fn zbi_test_container_new_entry_payload_too_big() {
        let mut buffer = ZbiAligned::default();
        let (new_entry, _payload) = get_test_entry_nonempty_payload();
        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                usize::MAX,
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_container_new_entry_no_space_left_unaligned() {
        let mut buffer = ZbiAligned::default();
        let new_entry = get_test_entry_empty_payload().0;

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();

        for _ in 1..(ALIGNED_8_SIZE / ZBI_HEADER_SIZE) {
            container
                .create_entry(
                    new_entry.type_.try_into().unwrap(),
                    new_entry.extra,
                    new_entry.get_flags(),
                    new_entry.length.try_into().unwrap(),
                )
                .unwrap();
        }

        // Now there is not enough space and it should fail
        assert_eq!(
            container.create_entry(
                new_entry.type_.try_into().unwrap(),
                new_entry.extra,
                new_entry.get_flags(),
                new_entry.length.try_into().unwrap(),
            ),
            Err(ZbiError::TooBig)
        );
    }

    #[test]
    fn zbi_test_container_extend_new() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1])
            .align()
            .update_container_length()
            .build();
        let container_0 = ZbiContainer::parse(buffer).unwrap();
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1, 2])
            .align()
            .update_container_length()
            .build();
        let container_1 = ZbiContainer::parse(buffer).unwrap();

        let mut buffer = ZbiAligned::default();
        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        container.extend(&container_0).unwrap();
        container.extend(&container_1).unwrap();

        let container_check = ZbiContainer::parse(&buffer.0[..]).unwrap();
        assert_eq!(container_check.iter().count(), 2);
        assert_eq!(container_0.iter().count(), 1);
        assert_eq!(container_1.iter().count(), 1);
        let mut it = container_check.iter();
        assert_eq!(it.next().unwrap(), container_0.iter().next().unwrap());
        assert_eq!(it.next().unwrap(), container_1.iter().next().unwrap());
        assert!(it.next().is_none());
    }

    #[test]
    fn zbi_test_container_extend_with_empty() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1])
            .align()
            .update_container_length()
            .build();
        let mut container_0 = ZbiContainer::parse(&mut buffer[..]).unwrap();
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0).build();
        let container_1 = ZbiContainer::parse(&mut buffer[..]).unwrap();

        assert_eq!(container_0.iter().count(), 1);
        container_0.extend(&container_1).unwrap();
        assert_eq!(container_0.iter().count(), 1);
    }

    #[test]
    fn zbi_test_container_extend_full() {
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .update_container_length()
            .build();
        let mut container_full = ZbiContainer::parse(&mut buffer[..]).unwrap();
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1, 2])
            .align()
            .update_container_length()
            .build();
        let container = ZbiContainer::parse(buffer).unwrap();

        assert_eq!(container_full.extend(&container), Err(ZbiError::TooBig));
    }

    #[test]
    fn zbi_test_container_extend_1_byte_short() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .update_container_length()
            .build();
        let mut container_small =
            ZbiContainer::parse(&mut buffer.0[..ZBI_HEADER_SIZE * 2 + ZBI_ALIGNMENT_USIZE - 1])
                .unwrap();
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1, 2])
            .align()
            .update_container_length()
            .build();
        let container = ZbiContainer::parse(buffer).unwrap();

        assert_eq!(container_small.extend(&container), Err(ZbiError::TooBig));
    }

    #[test]
    fn zbi_test_container_extend_use_all_buffer() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .update_container_length()
            .build();
        let mut container_full = ZbiContainer::parse(
            &mut buffer.0[..ZBI_HEADER_SIZE + ZBI_HEADER_SIZE + ZBI_ALIGNMENT_USIZE],
        )
        .unwrap();
        let mut buffer = ZbiAligned::default();
        let buffer = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1, 2])
            .align()
            .update_container_length()
            .build();
        let container = ZbiContainer::parse(buffer).unwrap();

        assert!(container_full.extend(&container).is_ok());
    }

    #[test]
    fn zbi_test_container_new_entry_with_payload() {
        let mut buffer = ZbiAligned::default();
        let new_entries = get_test_entries_all();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        for (e, payload) in &new_entries {
            container
                .create_entry_with_payload(
                    e.type_.try_into().unwrap(),
                    e.extra,
                    e.get_flags(),
                    payload,
                )
                .unwrap();
        }

        let container = ZbiContainer::parse(&buffer.0[..]).unwrap();
        check_container_made_of(&container, &new_entries);
    }

    fn check_container_made_of<B: ByteSlice + PartialEq>(
        container: &ZbiContainer<B>,
        expected_items: &[(ZbiHeader, &[u8])],
    ) {
        // Check container header length
        assert_eq!(
            container.get_payload_length_usize(),
            expected_items.len() * ZBI_HEADER_SIZE // add header len
                + expected_items // add payloads
                    .iter()
                    .map(|(_, payload)| -> usize {
                        payload.len() +
                        match payload.len() % ZBI_ALIGNMENT_USIZE{
                            0 => 0,
                            rem => ZBI_ALIGNMENT_USIZE- rem,
                        }
                    })
                    .sum::<usize>()
        );

        // Check if container elements match provided items
        let mut it = expected_items.iter();
        for b in container.iter() {
            let (header, payload) = it.next().unwrap();
            assert_eq!(*b.header, *header);
            assert_eq!(b.payload.len(), payload.len());
            assert!(b.payload.iter().zip(payload.iter()).all(|(a, b)| a == b))
        }
    }

    #[test]
    fn zbi_test_container_get_next_paylad() {
        let mut buffer = ZbiAligned::default();
        let new_entries = get_test_entries_all();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();

        for (e, payload) in &new_entries {
            let next_payload: &mut [u8] = container.get_next_payload().unwrap();
            next_payload[..payload.len()].copy_from_slice(payload);
            container
                .create_entry(e.type_.try_into().unwrap(), e.extra, e.get_flags(), payload.len())
                .unwrap();
        }

        let container = ZbiContainer::parse(&buffer.0[..]).unwrap();
        check_container_made_of(&container, &new_entries);
    }

    #[test]
    fn zbi_test_container_get_next_paylad_length() {
        let mut buffer = ZbiAligned::default();
        // Expected payload length is same as buffer - container header - item header
        let expected_payload_len = buffer.0.len() - 2 * core::mem::size_of::<ZbiHeader>();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        let next_payload: &mut [u8] = container.get_next_payload().unwrap();

        assert_eq!(next_payload.len(), expected_payload_len);
    }

    #[test]
    fn zbi_test_container_get_next_paylad_only_header_can_fit() {
        let mut buffer = ZbiAligned::default();
        // Buffer length that only fits container and item header.
        let len = 2 * core::mem::size_of::<ZbiHeader>();

        let mut container = ZbiContainer::new(&mut buffer.0[..len]).unwrap();
        let next_payload: &mut [u8] = container.get_next_payload().unwrap();

        assert_eq!(next_payload.len(), 0);
    }

    #[test]
    fn zbi_test_container_get_next_paylad_header_cant_fit() {
        let mut buffer = ZbiAligned::default();
        // Buffer length that only fits container but not item header.
        let len = 2 * core::mem::size_of::<ZbiHeader>() - 1;

        let mut container = ZbiContainer::new(&mut buffer.0[..len]).unwrap();
        assert_eq!(container.get_next_payload(), Err(ZbiError::TooBig));
    }

    #[test]
    fn zbi_test_container_get_next_paylad_length_overflow() {
        let mut buffer = ZbiAligned::default();
        // Buffer length that only fits container but not item header.
        let len = 2 * core::mem::size_of::<ZbiHeader>() - 1;

        let mut container = ZbiContainer::new(&mut buffer.0[..len]).unwrap();
        container.payload_length = usize::MAX; // Pretend that length is too big and cause
                                               // overflow in following functions
        assert_eq!(container.get_next_payload(), Err(ZbiError::LengthOverflow));
    }

    /* Binary blob for parsing container was generated from C implementation, running following
     * test:
     * --- a/src/firmware/lib/zbi/test/zbi.cc
     * +++ b/src/firmware/lib/zbi/test/zbi.cc
     * @@ -926,3 +926,21 @@ TEST(ZbiTests, ZbiTestNoOverflow) {
     *
     *    ASSERT_NE(zbi_extend(dst_buffer, kUsableBufferSize, src_buffer), ZBI_RESULT_OK);
     *  }
     * +
     * +TEST(ZbiTests, ZbiTestGenDataForRustTest) {
     * +  const size_t kExtraBytes = 10;
     * +  uint8_t* buffer = get_test_zbi_extra(kExtraBytes);
     * +  // Based on `get_test_zbi_extra()` implementation this is buffer size
     * +  const size_t kBufferSize = sizeof(test_zbi_t) + kExtraBytes;
     * +
     * +  printf("buffer length = %zu\n", kBufferSize);
     * +  printf("----BEGIN----\n");
     * +  for (size_t i = 0; i < kBufferSize; i++) {
     * +    if (i % 16 == 0) {
     * +      printf("\n");
     * +    }
     * +    printf("%02x", buffer[i]);
     * +  }
     * +  printf("\n");
     * +  printf("-----END-----\n");
     * +}
     */
    #[test]
    fn zbi_test_container_parse_c_reference() {
        let ref_buffer = get_test_creference_buffer_vec();
        let expected_container_hdr = ZbiHeader {
            type_: ZBI_TYPE_CONTAINER,
            extra: ZBI_CONTAINER_MAGIC,
            length: 184,
            magic: ZBI_ITEM_MAGIC,
            crc32: ZBI_ITEM_NO_CRC32,
            flags: ZbiFlags::default().bits(),
            ..Default::default()
        };
        // Reference C implementation test uses cstrings for payload. That is why we need '\0' at
        // the end of the string.
        let expected_entries = get_test_entries_creference();

        let mut buffer = ZbiAligned::default();
        buffer.0[..ref_buffer.len()].clone_from_slice(&ref_buffer);

        let container = ZbiContainer::parse(&buffer.0[..ref_buffer.len()]).unwrap();
        assert_eq!(*container.header, expected_container_hdr);
        check_container_made_of(&container, &expected_entries);
    }

    #[test]
    fn zbi_test_container_new_entry_iterate() {
        let mut buffer = ZbiAligned::default();
        let new_entry = get_test_entry_nonempty_payload();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        let (e, payload) = new_entry;
        container
            .create_entry_with_payload(e.type_.try_into().unwrap(), e.extra, e.get_flags(), payload)
            .unwrap();

        assert_eq!(container.iter().count(), 1);
        let mut it = container.iter();
        let item = it.next().unwrap();
        assert_eq!(*item.header, e);
        assert_eq!(&item.payload[..], payload);
        assert!(it.next().is_none());
    }

    #[test]
    fn zbi_test_container_new_entry_mut_iterate() {
        let mut buffer = ZbiAligned::default();
        let new_entry = get_test_entry_nonempty_payload();

        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        let (e, payload) = new_entry;
        container
            .create_entry_with_payload(e.type_.try_into().unwrap(), e.extra, e.get_flags(), payload)
            .unwrap();

        {
            let mut item = container.iter_mut().next().unwrap();
            assert_ne!(item.header.type_, ZbiType::DebugData.into());
            item.header.type_ = ZbiType::DebugData.into();
        }
        {
            let item = container.iter().next().unwrap();
            assert_eq!(item.header.type_, ZbiType::DebugData.into());
        }
    }

    #[test]
    fn zbi_test_container_parse_new_entry_mut_iterate() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(&[1, 2])
            .align()
            .update_container_length()
            .build();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();
        let new_entry = get_test_entry_nonempty_payload();

        let (e, payload) = new_entry;
        container
            .create_entry_with_payload(e.type_.try_into().unwrap(), e.extra, e.get_flags(), payload)
            .unwrap();

        assert_eq!(container.iter().count(), 2);
        for mut item in container.iter_mut() {
            assert_ne!(item.header.type_, ZbiType::DebugData.into());
            item.header.type_ = ZbiType::DebugData.into();
        }

        for item in container.iter() {
            assert_eq!(item.header.type_, ZbiType::DebugData.into());
        }
    }

    #[test]
    fn zbi_test_container_iterate_empty() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0).build();

        assert_eq!(ZbiContainer::parse(&buffer.0[..]).unwrap().iter().count(), 0);
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();
        assert_eq!(container.iter().count(), 0);
        assert_eq!(container.iter_mut().count(), 0);
    }

    fn byteslice_cmp(byteslice: impl ByteSlice, slice: &[u8]) -> bool {
        byteslice.len() == slice.len() && byteslice.iter().zip(slice.iter()).all(|(a, b)| a == b)
    }

    #[test]
    fn zbi_test_container_iterate_ref() {
        let mut buffer = get_test_creference_buffer();
        let container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        assert_eq!(container.iter().count(), 4);
        assert!(container.iter().zip(get_test_entries_creference().iter()).all(
            |(it, (entry, payload))| { *it.header == *entry && byteslice_cmp(it.payload, payload) }
        ));
    }

    #[test]
    fn zbi_test_container_iterate_modify() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item_default(b"A")
            .align()
            .item_default(b"BB")
            .align()
            .item_default(b"CCC")
            .align()
            .update_container_length()
            .build();
        let mut container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();

        container.iter_mut().for_each(|mut item| item.payload[0] = b'D');

        assert!(container.iter().all(|b| b.payload[0] == b'D'));
    }

    #[test]
    fn zbi_test_bad_type() {
        assert_eq!(ZbiType::try_from(0), Err(ZbiError::BadType));
    }

    fn get_all_zbi_type_values() -> Vec<ZbiType> {
        // strum and enum-iterator crates are not available at the moment, so just hard coding
        // values
        vec![
            ZbiType::KernelX64,
            ZbiType::KernelArm64,
            ZbiType::KernelRiscv64,
            ZbiType::Container,
            ZbiType::Discard,
            ZbiType::StorageRamdisk,
            ZbiType::StorageBootFs,
            ZbiType::StorageKernel,
            ZbiType::StorageBootFsFactory,
            ZbiType::CmdLine,
            ZbiType::CrashLog,
            ZbiType::Nvram,
            ZbiType::PlatformId,
            ZbiType::DrvBoardInfo,
            ZbiType::CpuTopology,
            ZbiType::MemConfig,
            ZbiType::KernelDriver,
            ZbiType::AcpiRsdp,
            ZbiType::Smbios,
            ZbiType::EfiSystemTable,
            ZbiType::EfiMemoryAttributesTable,
            ZbiType::FrameBuffer,
            ZbiType::ImageArgs,
            ZbiType::BootVersion,
            ZbiType::DrvMacAddress,
            ZbiType::DrvPartitionMap,
            ZbiType::DrvBoardPrivate,
            ZbiType::HwRebootReason,
            ZbiType::SerialNumber,
            ZbiType::BootloaderFile,
            ZbiType::DeviceTree,
            ZbiType::SecureEntropy,
            ZbiType::DebugData,
        ]
    }

    fn get_kernel_zbi_types() -> Vec<ZbiType> {
        vec![ZbiType::KernelRiscv64, ZbiType::KernelX64, ZbiType::KernelArm64]
    }
    fn get_metadata_zbi_types() -> Vec<ZbiType> {
        vec![
            ZbiType::DrvBoardInfo,
            ZbiType::DrvMacAddress,
            ZbiType::DrvPartitionMap,
            ZbiType::DrvBoardPrivate,
        ]
    }

    #[test]
    fn zbi_test_type_is_kernel() {
        assert!(get_kernel_zbi_types().iter().all(|t| t.is_kernel()))
    }

    #[test]
    fn zbi_test_type_is_not_kernel() {
        assert!(get_all_zbi_type_values()
            .iter()
            .filter(|v| !get_kernel_zbi_types().contains(v))
            .all(|v| !v.is_kernel()));
    }

    #[test]
    fn zbi_test_type_is_driver_metadata() {
        assert!(get_metadata_zbi_types().iter().all(|t| t.is_driver_metadata()));
    }

    #[test]
    fn zbi_test_type_is_not_driver_metadata() {
        assert!(get_all_zbi_type_values()
            .iter()
            .filter(|v| !get_metadata_zbi_types().contains(v))
            .all(|v| !v.is_driver_metadata()));
    }

    #[test]
    fn zbi_test_default_type_has_version() {
        assert!(ZbiFlags::default().contains(ZbiFlags::VERSION));
    }

    #[test]
    fn zbi_test_is_bootable() {
        let mut buffer = ZbiAligned::default();
        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();

        container
            .create_entry_with_payload(ZBI_ARCH_KERNEL_TYPE, 0, ZbiFlags::default(), &[])
            .unwrap();

        assert!(container.is_bootable().is_ok());
    }

    #[cfg(target_arch = "x86_64")]
    #[test]
    fn zbi_test_is_bootable_reference() {
        let ref_buffer = get_test_creference_buffer_vec();
        let mut buffer = ZbiAligned::default();
        buffer.0[..ref_buffer.len()].clone_from_slice(&ref_buffer);
        let container = ZbiContainer::parse(&buffer.0[..]).unwrap();
        assert!(container.is_bootable().is_ok());
    }

    #[test]
    fn zbi_test_is_bootable_empty_container() {
        let mut buffer = ZbiAligned::default();
        let container = ZbiContainer::new(&mut buffer.0[..]).unwrap();
        assert_eq!(container.is_bootable(), Err(ZbiError::Truncated));
    }

    #[test]
    fn zbi_test_is_bootable_wrong_arch() {
        let mut buffer = ZbiAligned::default();
        let _ = TestZbiBuilder::new(&mut buffer.0[..])
            .container_hdr(0)
            .align()
            .item(ZbiHeader { type_: 0, ..TestZbiBuilder::get_header_default() }, &[])
            .align()
            .update_container_length()
            .build();
        let container = ZbiContainer::parse(&mut buffer.0[..]).unwrap();
        assert_eq!(container.is_bootable(), Err(ZbiError::IncompleteKernel));
    }

    #[test]
    fn zbi_test_is_bootable_not_first_item_fail() {
        let mut buffer = ZbiAligned::default();
        let mut container = ZbiContainer::new(&mut buffer.0[..]).unwrap();

        container
            .create_entry_with_payload(ZbiType::DebugData, 0, ZbiFlags::default(), &[])
            .unwrap();
        container
            .create_entry_with_payload(ZBI_ARCH_KERNEL_TYPE, 0, ZbiFlags::default(), &[])
            .unwrap();

        assert_eq!(container.is_bootable(), Err(ZbiError::IncompleteKernel));
    }

    #[test]
    fn zbi_test_header_alignment() {
        assert_eq!(core::mem::size_of::<ZbiHeader>() & ZBI_ALIGNMENT_USIZE, 0);
    }

    fn get_test_payloads_all() -> Vec<&'static [u8]> {
        vec![
            &[],
            &[1],
            &[1, 2],
            &[1, 2, 3, 4, 5],
            // This 4 elements are for C reference binary testing
            b"4567\0",
            b"0123\0",
            b"0123456789\0",
            b"abcdefghijklmnopqrs\0",
        ]
    }

    fn get_test_zbi_headers_all() -> Vec<ZbiHeader> {
        let test_payloads = get_test_payloads_all();
        vec![
            ZbiHeader {
                type_: ZBI_TYPE_KERNEL_RISCV64,
                length: test_payloads[0].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_KERNEL_ARM64,
                length: test_payloads[1].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_KERNEL_RISCV64,
                length: test_payloads[2].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_KERNEL_X64,
                length: test_payloads[3].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_KERNEL_X64,
                length: test_payloads[4].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_CMDLINE,
                length: test_payloads[5].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_STORAGE_RAMDISK,
                length: test_payloads[6].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
            ZbiHeader {
                type_: ZBI_TYPE_STORAGE_BOOTFS,
                length: test_payloads[7].len().try_into().unwrap(),
                extra: 0,
                flags: ZbiFlags::default().bits(),
                magic: ZBI_ITEM_MAGIC,
                crc32: ZBI_ITEM_NO_CRC32,
                ..Default::default()
            },
        ]
    }

    fn get_test_zbi_headers(num: usize) -> Vec<ZbiHeader> {
        get_test_zbi_headers_all()[..num].to_vec()
    }

    fn get_test_entries_all() -> Vec<(ZbiHeader, &'static [u8])> {
        let headers = get_test_zbi_headers_all();
        let payloads = get_test_payloads_all();
        assert_eq!(headers.len(), payloads.len());
        headers.iter().cloned().zip(payloads.iter().cloned()).collect()
    }

    fn get_test_entries(num: usize) -> Vec<(ZbiHeader, &'static [u8])> {
        get_test_entries_all()[..num].to_vec()
    }

    fn get_test_entry_empty_payload() -> (ZbiHeader, &'static [u8]) {
        get_test_entries(1)[0]
    }

    fn get_test_entry_nonempty_payload() -> (ZbiHeader, &'static [u8]) {
        get_test_entries(2)[1]
    }

    fn get_test_entries_creference() -> Vec<(ZbiHeader, &'static [u8])> {
        let entries = get_test_entries_all();
        entries[entries.len() - 4..].to_vec()
    }

    fn get_test_creference_buffer() -> ZbiAligned {
        let entries = get_test_entries_creference();
        let mut buffer = ZbiAligned::default();
        let mut builder = TestZbiBuilder::new(&mut buffer.0[..]).container_hdr(0);
        for entry in entries {
            builder = builder.item(entry.0, entry.1).align();
        }
        let _ = builder.update_container_length().padding(0xab_u8, 10).build();
        buffer
    }

    fn get_test_creference_buffer_vec() -> Vec<u8> {
        hex::decode(
            "424f4f54b8000000e6f78c8600000100\
            0000000000000000291778b5d6e8874a\
            4b524e4c050000000000000000000100\
            0000000000000000291778b5d6e8874a\
            3435363700000000434d444c05000000\
            00000000000001000000000000000000\
            291778b5d6e8874a3031323300000000\
            5244534b0b0000000000000000000100\
            0000000000000000291778b5d6e8874a\
            30313233343536373839000000000000\
            42465342140000000000000000000100\
            0000000000000000291778b5d6e8874a\
            6162636465666768696a6b6c6d6e6f70\
            7172730000000000abababababababab\
            abab",
        )
        .unwrap()
    }

    #[test]
    fn test_creference_buffer_generation() {
        let ref_buffer = get_test_creference_buffer_vec();
        let buffer = get_test_creference_buffer();
        assert_eq!(&ref_buffer[..ref_buffer.len()], &buffer.0[..ref_buffer.len()]);
    }

    #[test]
    fn zbi_test_zbi_error() {
        let e = ZbiError::Error;
        println!("{e}");
        println!("{e:?}");
        println!("{e:#?}");
    }

    #[test]
    fn zby_test_container_align_buffer() {
        let buffer = ZbiAligned::default();
        let original_len = buffer.0.len();
        let buffer = align_buffer(&buffer.0[1..]).unwrap();
        assert_eq!(buffer.as_ptr() as usize % ZBI_ALIGNMENT_USIZE, 0);
        assert_eq!(buffer.len(), original_len - ZBI_ALIGNMENT_USIZE);
    }

    #[test]
    fn zby_test_container_align_buffer_empty() {
        let buffer = ZbiAligned::default();
        let buffer = align_buffer(&buffer.0[..0]).unwrap();
        assert_eq!(buffer.as_ptr() as usize % ZBI_ALIGNMENT_USIZE, 0);
        assert_eq!(buffer.len(), 0);
    }

    #[test]
    fn zby_test_container_align_buffer_too_short() {
        let buffer = ZbiAligned::default();
        assert_eq!(align_buffer(&buffer.0[1..ZBI_ALIGNMENT_USIZE - 1]), Err(ZbiError::TooBig));
    }

    #[test]
    fn zby_test_container_align_buffer_just_enough() {
        let buffer = ZbiAligned::default();
        let buffer = align_buffer(&buffer.0[1..ZBI_ALIGNMENT_USIZE]).unwrap();
        assert_eq!(buffer.as_ptr() as usize % ZBI_ALIGNMENT_USIZE, 0);
        assert_eq!(buffer.len(), 0);
    }
}
