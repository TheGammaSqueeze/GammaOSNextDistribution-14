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

use avb_bindgen::{
    avb_descriptor_foreach, avb_descriptor_validate_and_byteswap,
    avb_hashtree_descriptor_validate_and_byteswap, AvbDescriptor, AvbDescriptorTag,
    AvbHashtreeDescriptor,
};
use std::ffi::c_void;
use std::mem::{size_of, MaybeUninit};
use std::slice;

use super::VbMetaImageParseError;

/// The descriptors from a VBMeta image.
pub struct Descriptors<'a> {
    descriptors: Vec<Descriptor<'a>>,
}

/// Enumeration of the possible descriptors.
#[allow(missing_docs)]
pub enum Descriptor<'a> {
    Property(&'a [u8]),
    Hashtree(&'a [u8]),
    Hash(&'a [u8]),
    KernelCmdline(&'a [u8]),
    ChainPartition(&'a [u8]),
    Unknown,
}

/// A hashtree descriptor.
pub struct HashtreeDescriptor<'a> {
    descriptor: AvbHashtreeDescriptor,
    data: &'a [u8],
}

impl Descriptors<'_> {
    /// Find the descriptors in a well-formed VBMeta image.
    pub(super) fn from_image(data: &[u8]) -> Result<Descriptors<'_>, VbMetaImageParseError> {
        extern "C" fn desc_cb(descriptor: *const AvbDescriptor, user_data: *mut c_void) -> bool {
            // SAFETY: libavb gives a good pointer for us to work with.
            let desc = unsafe {
                let mut desc = MaybeUninit::uninit();
                if !avb_descriptor_validate_and_byteswap(descriptor, desc.as_mut_ptr()) {
                    return false;
                }
                desc.assume_init()
            };
            // SAFETY: the descriptor has been validated so it is contained within the image.
            let data = unsafe {
                slice::from_raw_parts(
                    descriptor as *const _ as *const u8,
                    size_of::<AvbDescriptor>() + desc.num_bytes_following as usize,
                )
            };
            // SAFETY: this cast gets a reference to the Vec passed as the user_data below.
            let descriptors = unsafe { &mut *(user_data as *mut Vec<Descriptor>) };
            descriptors.push(match desc.tag.try_into() {
                Ok(AvbDescriptorTag::AVB_DESCRIPTOR_TAG_PROPERTY) => Descriptor::Property(data),
                Ok(AvbDescriptorTag::AVB_DESCRIPTOR_TAG_HASHTREE) => Descriptor::Hashtree(data),
                Ok(AvbDescriptorTag::AVB_DESCRIPTOR_TAG_HASH) => Descriptor::Hash(data),
                Ok(AvbDescriptorTag::AVB_DESCRIPTOR_TAG_KERNEL_CMDLINE) => {
                    Descriptor::KernelCmdline(data)
                }
                Ok(AvbDescriptorTag::AVB_DESCRIPTOR_TAG_CHAIN_PARTITION) => {
                    Descriptor::ChainPartition(data)
                }
                _ => Descriptor::Unknown,
            });
            true
        }

        let mut descriptors = Vec::new();
        // SAFETY: the function only reads from the provided data and passes the Vec pointer to the
        // callback function, treating it as an opaque handle. The descriptors added to the Vec are
        // contained within the provided data so the lifetime is bound accordingly.
        if unsafe {
            let desc = &mut descriptors as *mut _ as *mut c_void;
            avb_descriptor_foreach(data.as_ptr(), data.len(), Some(desc_cb), desc)
        } {
            Ok(Descriptors { descriptors })
        } else {
            Err(VbMetaImageParseError::InvalidDescriptor)
        }
    }

    /// Get an iterator over the descriptors.
    pub fn iter(&self) -> slice::Iter<Descriptor> {
        self.descriptors.iter()
    }
}

impl<'a> IntoIterator for Descriptors<'a> {
    type Item = Descriptor<'a>;
    type IntoIter = std::vec::IntoIter<Self::Item>;

    fn into_iter(self) -> Self::IntoIter {
        self.descriptors.into_iter()
    }
}

impl Descriptor<'_> {
    /// Parse the descriptor as a hashtree descriptor.
    pub fn to_hashtree(&self) -> Result<HashtreeDescriptor, VbMetaImageParseError> {
        match self {
            Self::Hashtree(data) => {
                // SAFETY: data contains the entire descriptor.
                let descriptor = unsafe {
                    let mut desc = MaybeUninit::uninit();
                    let src = data.as_ptr() as *const _ as *const AvbHashtreeDescriptor;
                    if !avb_hashtree_descriptor_validate_and_byteswap(src, desc.as_mut_ptr()) {
                        return Err(VbMetaImageParseError::InvalidDescriptor);
                    }
                    desc.assume_init()
                };
                Ok(HashtreeDescriptor { descriptor, data })
            }
            _ => Err(VbMetaImageParseError::InvalidDescriptor),
        }
    }

    // TODO: handle other descriptor type as required
}

impl HashtreeDescriptor<'_> {
    /// Get the root digest of the hashtree.
    pub fn root_digest(&self) -> &[u8] {
        let begin = size_of::<AvbHashtreeDescriptor>()
            + self.descriptor.partition_name_len as usize
            + self.descriptor.salt_len as usize;
        let end = begin + self.descriptor.root_digest_len as usize;
        &self.data[begin..end]
    }

    // TODO: expose other fields as required
}
