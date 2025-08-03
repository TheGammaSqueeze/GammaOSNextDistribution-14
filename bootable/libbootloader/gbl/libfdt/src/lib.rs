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

//! This library provides a few wrapper APIs for libfdt_c

#![cfg_attr(not(test), no_std)]

extern crate libc;

use core::ffi::CStr;
use core::mem::size_of;
use core::slice::{from_raw_parts, from_raw_parts_mut};

use libfdt_c_def::{
    fdt_add_subnode_namelen, fdt_header, fdt_setprop, fdt_setprop_placeholder, fdt_strerror,
    fdt_subnode_offset_namelen,
};

use zerocopy::{AsBytes, FromBytes, FromZeroes, Ref};

/// libfdt error type.
#[derive(Debug)]
pub enum FdtError {
    CLibError(&'static str),
    InvalidInput,
    IntegerOverflow,
}

/// libfdt result type,
pub type Result<T> = core::result::Result<T, FdtError>;

/// Convert libfdt_c error code to Result
fn map_result(code: core::ffi::c_int) -> Result<core::ffi::c_int> {
    match code {
        // SAFETY: Static null terminated string returned from libfdt_c API.
        v if v < 0 => Err(FdtError::CLibError(unsafe {
            core::ffi::CStr::from_ptr(fdt_strerror(v)).to_str().unwrap()
        })),
        v => Ok(v),
    }
}

/// Check header and verified that totalsize does not exceed buffer size.
fn fdt_check_header(fdt: &[u8]) -> Result<()> {
    map_result(unsafe { libfdt_c_def::fdt_check_header(fdt.as_ptr() as *const _) })?;
    match FdtHeader::from_bytes_ref(fdt)?.totalsize() <= fdt.len() {
        true => Ok(()),
        _ => Err(FdtError::InvalidInput),
    }
}

/// Wrapper of fdt_add_subnode_namelen()
fn fdt_add_subnode(
    fdt: &mut [u8],
    parent: core::ffi::c_int,
    name: &str,
) -> Result<core::ffi::c_int> {
    // SAFETY: API from libfdt_c.
    map_result(unsafe {
        fdt_add_subnode_namelen(
            fdt.as_mut_ptr() as *mut _,
            parent,
            name.as_ptr() as *const _,
            name.len().try_into().map_err(|_| FdtError::IntegerOverflow)?,
        )
    })
}

/// Wrapper of fdt_subnode_offset_namelen()
fn fdt_subnode_offset(
    fdt: &[u8],
    parent: core::ffi::c_int,
    name: &str,
) -> Result<core::ffi::c_int> {
    // SAFETY: API from libfdt_c.
    map_result(unsafe {
        fdt_subnode_offset_namelen(
            fdt.as_ptr() as *const _,
            parent,
            name.as_ptr() as *const _,
            name.len().try_into().map_err(|_| FdtError::IntegerOverflow)?,
        )
    })
}

#[repr(transparent)]
#[derive(Debug, Copy, Clone, AsBytes, FromBytes, FromZeroes, PartialEq)]
pub struct FdtHeader(fdt_header);

impl FdtHeader {
    /// Return the totalsize field.
    pub fn totalsize(&self) -> usize {
        u32::from_be(self.0.totalsize) as usize
    }

    /// Return the minimal size of the FDT. Disregard trailing free space.
    pub fn actual_size(&self) -> usize {
        u32::from_be(self.0.off_dt_strings)
            .checked_add(u32::from_be(self.0.size_dt_strings))
            .unwrap() as usize
    }

    /// Update the totalsize field.
    pub fn set_totalsize(&mut self, value: u32) {
        self.0.totalsize = value.to_be();
    }

    /// Cast a bytes into a reference of FDT header
    pub fn from_bytes_ref(buffer: &[u8]) -> Result<&FdtHeader> {
        Ok(Ref::<_, FdtHeader>::new_from_prefix(buffer)
            .ok_or_else(|| FdtError::InvalidInput)?
            .0
            .into_ref())
    }

    /// Cast a bytes into a mutable reference of FDT header.
    pub fn from_bytes_mut(buffer: &mut [u8]) -> Result<&mut FdtHeader> {
        Ok(Ref::<_, FdtHeader>::new_from_prefix(buffer)
            .ok_or_else(|| FdtError::InvalidInput)?
            .0
            .into_mut())
    }

    /// Get FDT header and raw bytes from a raw pointer.
    ///
    /// Caller should guarantee that
    ///   1. `ptr` contains a valid FDT.
    ///   2. The buffer remains valid as long as the returned references are in use.
    pub unsafe fn from_raw(ptr: *const u8) -> Result<(&'static FdtHeader, &'static [u8])> {
        // SAFETY: By safety requirement of this function, `ptr` points to a valid FDT and remains
        // valid when in use.
        unsafe {
            map_result(libfdt_c_def::fdt_check_header(ptr as *const _))?;
            let header_bytes = from_raw_parts(ptr, size_of::<FdtHeader>());
            let header = Self::from_bytes_ref(header_bytes)?;
            Ok((header, from_raw_parts(ptr, header.totalsize())))
        }
    }
}

/// Object for managing an FDT.
pub struct Fdt<T>(T);

/// Read only APIs.
impl<T: AsRef<[u8]>> Fdt<T> {
    pub fn new(init: T) -> Result<Self> {
        fdt_check_header(init.as_ref())?;
        Ok(Fdt(init))
    }

    pub fn header_ref(&self) -> Result<&FdtHeader> {
        FdtHeader::from_bytes_ref(self.0.as_ref())
    }

    /// Returns the totalsize according to FDT header. Trailing free space is included.
    pub fn size(&self) -> Result<usize> {
        Ok(self.header_ref()?.totalsize())
    }

    /// Get a property from an existing node.
    pub fn get_property<'a>(&'a self, path: &str, name: &CStr) -> Result<&'a [u8]> {
        let node = self.find_node(path)?;
        let mut len: core::ffi::c_int = 0;
        // SAFETY: API from libfdt_c.
        let ptr = unsafe {
            libfdt_c_def::fdt_get_property(
                self.0.as_ref().as_ptr() as *const _,
                node,
                name.to_bytes_with_nul().as_ptr() as *const _,
                &mut len as *mut _,
            )
        };
        // SAFETY: Buffer returned by API from libfdt_c.
        match unsafe { ptr.as_ref() } {
            // SAFETY: Buffer returned by API from libfdt_c.
            Some(v) => Ok(unsafe {
                from_raw_parts(
                    v.data.as_ptr() as *const u8,
                    u32::from_be(v.len).try_into().map_err(|_| FdtError::IntegerOverflow)?,
                )
            }),
            _ => Err(map_result(len).unwrap_err()),
        }
    }

    /// Find the offset of a node by a given node path.
    fn find_node(&self, path: &str) -> Result<core::ffi::c_int> {
        let mut curr: core::ffi::c_int = 0;
        for name in path.split('/') {
            if name.len() == 0 {
                continue;
            }
            curr = fdt_subnode_offset(self.0.as_ref(), curr, name)?;
        }
        Ok(curr)
    }
}

/// APIs when data can be modified.
impl<T: AsMut<[u8]> + AsRef<[u8]>> Fdt<T> {
    pub fn new_from_init(mut fdt: T, init: &[u8]) -> Result<Self> {
        fdt_check_header(init)?;
        // SAFETY: API from libfdt_c.
        map_result(unsafe {
            libfdt_c_def::fdt_move(
                init.as_ptr() as *const _,
                fdt.as_mut().as_ptr() as *mut _,
                fdt.as_mut().len().try_into().map_err(|_| FdtError::IntegerOverflow)?,
            )
        })?;
        let new_size: u32 = fdt.as_mut().len().try_into().map_err(|_| FdtError::IntegerOverflow)?;
        let mut ret = Fdt::new(fdt)?;
        ret.header_mut()?.set_totalsize(new_size);
        Ok(ret)
    }

    /// Parse and get the FDT header.
    fn header_mut(&mut self) -> Result<&mut FdtHeader> {
        FdtHeader::from_bytes_mut(self.0.as_mut())
    }

    /// Reduce the total size field in the header to minimum that will fit existing content.
    /// No more data can be added to the FDT. This should be called after all modification is
    /// done and before passing to the kernel. This is to prevent kernel hang when FDT size is too
    /// big.
    pub fn shrink_to_fit(&mut self) -> Result<()> {
        let actual = self.header_ref()?.actual_size();
        self.header_mut()?.set_totalsize(actual.try_into().unwrap());
        Ok(())
    }

    /// Set the value of a node's property. Create the node and property if it doesn't exist.
    pub fn set_property(&mut self, path: &str, name: &CStr, val: &[u8]) -> Result<()> {
        let node = self.find_or_add_node(path)?;
        // SAFETY: API from libfdt_c.
        map_result(unsafe {
            fdt_setprop(
                self.0.as_mut().as_mut_ptr() as *mut _,
                node,
                name.to_bytes_with_nul().as_ptr() as *const _,
                val.as_ptr() as *const _,
                val.len().try_into().map_err(|_| FdtError::IntegerOverflow)?,
            )
        })?;
        Ok(())
    }

    /// Wrapper/equivalent of fdt_setprop_placeholder.
    /// It creates/resizes a node's property to the given size and returns the buffer for caller
    /// to modify content.
    pub fn set_property_placeholder(
        &mut self,
        path: &str,
        name: &CStr,
        len: usize,
    ) -> Result<&mut [u8]> {
        let node = self.find_or_add_node(path)?;
        let mut out_ptr: *mut u8 = core::ptr::null_mut();
        // SAFETY: API from libfdt_c.
        map_result(unsafe {
            fdt_setprop_placeholder(
                self.0.as_mut().as_mut_ptr() as *mut _,
                node,
                name.to_bytes_with_nul().as_ptr() as *const _,
                len.try_into().map_err(|_| FdtError::IntegerOverflow)?,
                &mut out_ptr as *mut *mut u8 as *mut _,
            )
        })?;
        assert!(!out_ptr.is_null());
        // SAFETY: Buffer returned by API from libfdt_c.
        Ok(unsafe { from_raw_parts_mut(out_ptr, len) })
    }

    /// Find the offset of a node by a given node path. Add if node does not exist.
    fn find_or_add_node(&mut self, path: &str) -> Result<core::ffi::c_int> {
        let mut curr: core::ffi::c_int = 0;
        for name in path.split('/') {
            if name.len() == 0 {
                continue;
            }
            curr = match fdt_subnode_offset(self.0.as_ref(), curr, name) {
                Ok(v) => v,
                _ => fdt_add_subnode(self.0.as_mut(), curr, name)?,
            };
        }
        Ok(curr)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use std::ffi::CString;

    fn to_cstr(s: &str) -> CString {
        CString::new(s).unwrap()
    }

    #[test]
    fn test_new_from_invalid_fdt() {
        let mut init = include_bytes!("../test/test.dtb").to_vec();
        let mut fdt_buf = vec![0u8; init.len()];
        // Invalid total size
        assert!(Fdt::new_from_init(&mut fdt_buf[..], &init[..init.len() - 1]).is_err());
        // Invalid FDT
        init[..4].fill(0);
        assert!(Fdt::new_from_init(&mut fdt_buf[..], &init[..]).is_err());
    }

    #[test]
    fn test_get_property() {
        let init = include_bytes!("../test/test.dtb").to_vec();
        let mut fdt_buf = vec![0u8; init.len()];
        let fdt = Fdt::new_from_init(&mut fdt_buf[..], &init[..]).unwrap();

        assert_eq!(
            CStr::from_bytes_with_nul(fdt.get_property("/", &to_cstr("info")).unwrap())
                .unwrap()
                .to_str()
                .unwrap(),
            "test device tree"
        );
        assert_eq!(
            CStr::from_bytes_with_nul(
                fdt.get_property("/dev-2/dev-2.2/dev-2.2.1", &to_cstr("property-1")).unwrap()
            )
            .unwrap()
            .to_str()
            .unwrap(),
            "dev-2.2.1-property-1"
        );

        // Non eixsts
        assert!(fdt.get_property("/", &to_cstr("non-exist")).is_err());
    }

    #[test]
    fn test_set_property() {
        let init = include_bytes!("../test/test.dtb").to_vec();
        let mut fdt_buf = vec![0u8; init.len() + 512];
        let mut fdt = Fdt::new_from_init(&mut fdt_buf[..], &init[..]).unwrap();
        let data = vec![0x11u8, 0x22u8, 0x33u8];
        fdt.set_property("/new-node", &to_cstr("custom"), &data).unwrap();
        assert_eq!(fdt.get_property("/new-node", &to_cstr("custom")).unwrap().to_vec(), data);
    }

    #[test]
    fn test_set_property_placeholder() {
        let init = include_bytes!("../test/test.dtb").to_vec();
        let mut fdt_buf = vec![0u8; init.len() + 512];
        let mut fdt = Fdt::new_from_init(&mut fdt_buf[..], &init[..]).unwrap();
        let data = vec![0x11u8, 0x22u8, 0x33u8, 0x44u8, 0x55u8];
        let payload =
            fdt.set_property_placeholder("/new-node", &to_cstr("custom"), data.len()).unwrap();
        payload.clone_from_slice(&data[..]);
        assert_eq!(fdt.get_property("/new-node", &to_cstr("custom")).unwrap().to_vec(), data);
    }

    #[test]
    fn test_header_from_raw() {
        let init = include_bytes!("../test/test.dtb").to_vec();
        // Pointer points to `init`
        let (header, bytes) = unsafe { FdtHeader::from_raw(init.as_ptr()).unwrap() };
        assert_eq!(header.totalsize(), init.len());
        assert_eq!(bytes.to_vec(), init);
    }

    #[test]
    fn test_header_from_raw_invalid() {
        let mut init = include_bytes!("../test/test.dtb").to_vec();
        init[..4].fill(0);
        // Pointer points to `init`
        assert!(unsafe { FdtHeader::from_raw(init.as_ptr()).is_err() });
    }

    #[test]
    fn test_fdt_shrink_to_fit() {
        let init = include_bytes!("../test/test.dtb").to_vec();
        let mut fdt_buf = vec![0u8; init.len() + 512];
        let fdt_buf_len = fdt_buf.len();
        let mut fdt = Fdt::new_from_init(&mut fdt_buf[..], &init[..]).unwrap();
        assert_eq!(fdt.size().unwrap(), fdt_buf_len);
        fdt.shrink_to_fit().unwrap();
        assert_eq!(fdt.size().unwrap(), init.len());
    }
}
