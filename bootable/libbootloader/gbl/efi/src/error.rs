// Copyright 2024, The Android Open Source Project
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

use avb::{IoError, SlotVerifyError};
use boot::BootError;
use bootconfig::BootConfigError;
use bootimg::ImageError;
use efi::EfiError;
use fdt::FdtError;
use gbl_storage::StorageError;
use zbi::ZbiError;

/// Error types for EFI application.
#[derive(Debug)]
pub enum EfiAppError {
    ArithmeticOverflow,
    BufferAlignment,
    BufferTooSmall,
    InvalidInput,
    InvalidString,
    NoFdt,
    NotFound,
    NoZbiImage,
    Unsupported,
}

/// A convenient macro for declaring a composite enum type that simply wraps other types as
/// entries. It auto-generate `From<...>` implementation for each entry type. i.e.:
///
/// ```rust
///   composite_enum! {
///       pub enum MyEnum {
///           Usize(usize),
///           I64(i64),
///       }
///   }
/// ```
///
/// expands to
///
/// ```rust
///   pub enum MyEnum {
///       Usize(usize),
///       I64(i64),
///   }
///
///   impl From<usize> for MyEnum {
///       fn from(entry: usize) -> MyEnum {
///           MyEnum::Usize(entry)
///       }
///   }
///
///   impl From<i64> for MyEnum {
///       fn from(entry: i64) -> MyEnum {
///           MyEnum::I64(entry)
///       }
///   }
/// ```
///
/// The macro assumes that each entry is a different type.
macro_rules! composite_enum {
    // Top level macro entry. Match enum declaration code and call recursively for `From<>`
    // generation.
    (
        $(#[$outer:meta])*
        $vis:vis enum $EnumName:ident {
            $($entry:ident($entry_type:ty)),*
            $(,)*
        }
    ) => {
        // Copy over enum declaration as it is.
        $(#[$outer])*
        $vis enum $EnumName {
            $($entry($entry_type)),*
        }

        // Generate `From<...>` implementation.
        composite_enum!{$EnumName,  $($entry($entry_type)),*}
    };
    // `From<>` implementation generation. Base case.
    ($EnumName:ident, $entry:ident($entry_type:ty)) => {
        impl From<$entry_type> for $EnumName {
            fn from(entry: $entry_type) -> $EnumName {
                $EnumName::$entry(entry)
            }
        }
    };
    // `From<>` implementation generation. Recursive case.
    ($EnumName:ident, $entry:ident($entry_type:ty), $($entry_next:ident($entry_type_next:ty)),+) => {
        composite_enum!{$EnumName, $entry($entry_type)}
        composite_enum!{$EnumName, $($entry_next($entry_type_next)),*}
    };
}

composite_enum! {
    /// A top level error type that consolidates errors from different libraries.
    #[derive(Debug)]
    pub enum GblEfiError {
        AvbIoError(IoError),
        BootConfigError(BootConfigError),
        BootError(BootError),
        EfiAppError(EfiAppError),
        EfiError(EfiError),
        FdtError(FdtError),
        ImageError(ImageError),
        SlotVerifyError(SlotVerifyError<'static>),
        StorageError(StorageError),
        ZbiError(ZbiError),
    }
}

/// Top level error type.
pub type Result<T> = core::result::Result<T, GblEfiError>;
