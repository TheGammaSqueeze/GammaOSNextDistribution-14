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

//! The library implements Rust wrappers for a set of UEFI interfaces needed by GBL. It also
//! provides a global allocator and supports auto release of dynamic UEFI resource such as
//! protocols and UEFI allocated buffers.
//!
//! # Examples
//!
//! The following example covers the basic use pattern of the library. It scans all block devices
//! and prints out the device path, block size and io alignment info for each of them.
//!
//! ```
//! fn main(image: EfiHandle, systab_ptr: *mut EfiSystemTable) -> efi::EfiResult<()> {
//!     let efi_entry = initialize(image, systab_ptr)?;
//!     let mut con_out = efi_entry.system_table().con_out()?;
//!     let boot_services = efi_entry.system_table().boot_services();
//!     let path_to_text = boot_services.find_first_and_open::<DevicePathToTextProtocol>()?;
//!
//!     write!(con_out, "Scanning block devices...\n")?;
//!
//!     let block_handles = boot_services.locate_handle_buffer_by_protocol::<BlockIoProtocol>()?;
//!
//!     for (i, handle) in block_handles.handles().iter().enumerate() {
//!         let path = boot_services.open_protocol::<DevicePathProtocol>(*handle)?;
//!         write!(con_out, "Block Device #{}: ", i)?;
//!         path_to_text.convert_device_path_to_text(&path, false, false)?.print()?;
//!         write!(con_out, "\n")?;
//!
//!         let block_io_protocol = boot_services.open_protocol::<BlockIoProtocol>(*handle)?;
//!         let media = block_io_protocol.media()?;
//!         write!(con_out, "  block size = {}\n", media.block_size)?;
//!         write!(con_out, "  io alignment = {}\n", media.io_align)?;
//!     }
//!
//!     Ok(())
//! }
//! ```

#![cfg_attr(not(test), no_std)]

use core::ptr::null_mut;
use core::slice::from_raw_parts;
#[cfg(not(test))]
use core::{fmt::Write, panic::PanicInfo};

use zerocopy::Ref;

#[rustfmt::skip]
pub mod defs;
use defs::*;

#[cfg(not(test))]
mod allocation;

#[cfg(not(test))]
pub use allocation::{efi_free, efi_malloc};

mod protocol;
// Protocol type and implementation to export.
pub use protocol::BlockIoProtocol;
pub use protocol::DevicePathProtocol;
pub use protocol::DevicePathText;
pub use protocol::DevicePathToTextProtocol;
pub use protocol::LoadedImageProtocol;
pub use protocol::Protocol;
pub use protocol::ProtocolInfo;
pub use protocol::RiscvBootProtocol;
pub use protocol::SimpleNetworkProtocol;
pub use protocol::SimpleTextOutputProtocol;

mod error {
    use super::defs::EFI_STATUS_SUCCESS;
    use super::EfiStatus;

    #[derive(Debug, Copy, Clone, PartialEq)]
    pub enum ErrorTypes {
        Unknown,
        EfiStatusError(EfiStatus),
    }

    #[derive(Debug, PartialEq)]
    pub struct EfiError(ErrorTypes);

    impl EfiError {
        pub fn err(&self) -> ErrorTypes {
            self.0
        }
    }

    impl From<EfiStatus> for EfiError {
        fn from(efi_status: EfiStatus) -> EfiError {
            EfiError(match efi_status {
                EFI_STATUS_SUCCESS => ErrorTypes::Unknown,
                _ => ErrorTypes::EfiStatusError(
                    // Remove the highest bit in the error code so that it's eaiser to interpret
                    // when printing.
                    efi_status & !(1 << (core::mem::size_of::<EfiStatus>() * 8 - 1)),
                ),
            })
        }
    }
}

pub use error::*;

/// Result type for this library.
pub type EfiResult<T> = core::result::Result<T, EfiError>;

/// Helper method to convert an EFI status code to EfiResult.
fn map_efi_err(code: EfiStatus) -> EfiResult<()> {
    match code {
        EFI_STATUS_SUCCESS => Ok(()),
        _ => Err(code.into()),
    }
}

/// `EfiEntry` stores the EFI system table pointer and image handle passed from the entry point.
/// It's the root data structure that derives all other wrapper APIs and structures.
pub struct EfiEntry {
    image_handle: EfiHandle,
    systab_ptr: *const EfiSystemTable,
}

impl EfiEntry {
    /// Gets an instance of `SystemTable`.
    pub fn system_table(&self) -> SystemTable {
        // SAFETY: Pointers to UEFI data strucutres.
        SystemTable { efi_entry: self, table: unsafe { self.systab_ptr.as_ref() }.unwrap() }
    }

    /// Gets the image handle.
    pub fn image_handle(&self) -> DeviceHandle {
        DeviceHandle(self.image_handle)
    }
}

/// Creates an `EfiEntry` and initialize EFI global allocator.
///
/// # Safety
///
/// The API modifies internal global state. It should only be called once upon EFI entry to obtain
/// an instance of `EfiEntry` for accessing other APIs. Calling it again when EFI APIs are already
/// being used can introduce a risk of race.
#[cfg(not(test))]
pub unsafe fn initialize(
    image_handle: EfiHandle,
    systab_ptr: *const EfiSystemTable,
) -> EfiResult<EfiEntry> {
    let efi_entry = EfiEntry { image_handle, systab_ptr };
    // SAFETY: By safety requirement of this function, `initialize` is only called once upon
    // entering EFI application, where there should be no event notify function that can be
    // triggered.
    unsafe {
        // Create another one for internal global allocator.
        allocation::init_efi_global_alloc(EfiEntry { image_handle, systab_ptr })?;
    }
    Ok(efi_entry)
}

/// A helper for getting a subslice with an aligned address.
pub fn aligned_subslice(buffer: &mut [u8], alignment: usize) -> Option<&mut [u8]> {
    let addr = buffer.as_ptr() as usize;
    let aligned_offset = addr
        .checked_add(alignment - 1)?
        .checked_div(alignment)?
        .checked_mul(alignment)?
        .checked_sub(addr)?;
    buffer.get_mut(aligned_offset..)
}

/// Exits boot service and returns the memory map in the given buffer.
///
/// The API takes ownership of the given `entry` and causes it to go out of scope.
/// This enforces strict compile time check that any reference/borrow in effect will cause compile
/// errors.
///
/// Existing heap allocated memories will maintain their states. All system memory including them
/// will be under onwership of the subsequent OS or OS loader code.
pub fn exit_boot_services(entry: EfiEntry, mmap_buffer: &mut [u8]) -> EfiResult<EfiMemoryMap> {
    let aligned = aligned_subslice(mmap_buffer, core::mem::align_of::<EfiMemoryDescriptor>())
        .ok_or_else::<EfiError, _>(|| EFI_STATUS_BUFFER_TOO_SMALL.into())?;

    let res = entry.system_table().boot_services().get_memory_map(aligned)?;
    entry.system_table().boot_services().exit_boot_services(&res)?;
    // SAFETY:
    // At this point, UEFI has successfully exited boot services and no event/notification can be
    // triggered.
    #[cfg(not(test))]
    unsafe {
        allocation::exit_efi_global_alloc();
    }
    Ok(res)
}

/// `SystemTable` provides methods for accessing fields in `EFI_SYSTEM_TABLE`.
#[derive(Clone, Copy)]
pub struct SystemTable<'a> {
    efi_entry: &'a EfiEntry,
    table: &'a EfiSystemTable,
}

impl<'a> SystemTable<'a> {
    /// Creates an instance of `BootServices`
    pub fn boot_services(&self) -> BootServices<'a> {
        BootServices {
            efi_entry: self.efi_entry,
            // SAFETY: Pointers to UEFI data strucutres.
            boot_services: unsafe { self.table.boot_services.as_ref() }.unwrap(),
        }
    }

    /// Gets the `EFI_SYSTEM_TABLE.ConOut` field.
    pub fn con_out(&self) -> EfiResult<Protocol<'a, SimpleTextOutputProtocol>> {
        // SAFETY: `EFI_SYSTEM_TABLE.ConOut` is a pointer to EfiSimpleTextOutputProtocol structure
        // by definition. It lives until ExitBootService and thus as long as `self.efi_entry` or,
        // 'a
        Ok(unsafe {
            Protocol::<SimpleTextOutputProtocol>::new(
                // No device handle. This protocol is a permanent reference.
                DeviceHandle(null_mut()),
                self.table.con_out,
                self.efi_entry,
            )
        })
    }

    /// Gets the `EFI_SYSTEM_TABLE.ConfigurationTable` array.
    pub fn configuration_table(&self) -> Option<&[EfiConfigurationTable]> {
        match self.table.configuration_table.is_null() {
            true => None,
            // SAFETY: Non-null pointer to EFI configuration table.
            false => unsafe {
                Some(from_raw_parts(
                    self.table.configuration_table,
                    self.table.number_of_table_entries,
                ))
            },
        }
    }
}

/// `BootServices` provides methods for accessing various EFI_BOOT_SERVICES interfaces.
#[derive(Clone, Copy)]
pub struct BootServices<'a> {
    efi_entry: &'a EfiEntry,
    boot_services: &'a EfiBootService,
}

impl<'a> BootServices<'a> {
    /// Wrapper of `EFI_BOOT_SERVICES.AllocatePool()`.
    #[allow(dead_code)]
    fn allocate_pool(
        &self,
        pool_type: EfiMemoryType,
        size: usize,
    ) -> EfiResult<*mut core::ffi::c_void> {
        let mut out: *mut core::ffi::c_void = null_mut();
        // SAFETY: `EFI_BOOT_SERVICES` method call.
        unsafe {
            efi_call!(self.boot_services.allocate_pool, pool_type, size, &mut out)?;
        }
        Ok(out)
    }

    /// Wrapper of `EFI_BOOT_SERVICES.FreePool()`.
    fn free_pool(&self, buf: *mut core::ffi::c_void) -> EfiResult<()> {
        // SAFETY: `EFI_BOOT_SERVICES` method call.
        unsafe { efi_call!(self.boot_services.free_pool, buf) }
    }

    /// Wrapper of `EFI_BOOT_SERVICES.OpenProtocol()`.
    pub fn open_protocol<T: ProtocolInfo>(
        &self,
        handle: DeviceHandle,
    ) -> EfiResult<Protocol<'a, T>> {
        let mut out_handle: EfiHandle = null_mut();
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(
                self.boot_services.open_protocol,
                handle.0,
                &T::GUID,
                &mut out_handle as *mut _,
                self.efi_entry.image_handle().0,
                null_mut(),
                EFI_OPEN_PROTOCOL_ATTRIBUTE_BY_HANDLE_PROTOCOL
            )?;
        }
        // SAFETY: `EFI_SYSTEM_TABLE.OpenProtocol` returns a valid pointer to `T::InterfaceType`
        // on success. The pointer remains valid until closed by
        // `EFI_BOOT_SERVICES.CloseProtocol()` when Protocol goes out of scope.
        Ok(unsafe { Protocol::<T>::new(handle, out_handle as *mut _, self.efi_entry) })
    }

    /// Wrapper of `EFI_BOOT_SERVICES.CloseProtocol()`.
    fn close_protocol<T: ProtocolInfo>(&self, handle: DeviceHandle) -> EfiResult<()> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(
                self.boot_services.close_protocol,
                handle.0,
                &T::GUID,
                self.efi_entry.image_handle().0,
                null_mut()
            )
        }
    }

    /// Call `EFI_BOOT_SERVICES.LocateHandleBuffer()` with fixed
    /// `EFI_LOCATE_HANDLE_SEARCH_TYPE_BY_PROTOCOL` and without search key.
    pub fn locate_handle_buffer_by_protocol<T: ProtocolInfo>(
        &self,
    ) -> EfiResult<LocatedHandles<'a>> {
        let mut num_handles: usize = 0;
        let mut handles: *mut EfiHandle = null_mut();
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(
                self.boot_services.locate_handle_buffer,
                EFI_LOCATE_HANDLE_SEARCH_TYPE_BY_PROTOCOL,
                &T::GUID,
                null_mut(),
                &mut num_handles as *mut usize as *mut _,
                &mut handles as *mut *mut EfiHandle
            )?
        };
        // `handles` should be a valid pointer if the above succeeds. But just double check
        // to be safe. If assert fails, then there's a bug in the UEFI firmware.
        assert!(!handles.is_null());
        Ok(LocatedHandles::new(handles, num_handles, self.efi_entry))
    }

    /// Search and open the first found target EFI protocol.
    pub fn find_first_and_open<T: ProtocolInfo>(&self) -> EfiResult<Protocol<'a, T>> {
        // We don't use EFI_BOOT_SERVICES.LocateProtocol() because it doesn't give device handle
        // which is required to close the protocol.
        let handle = *self
            .locate_handle_buffer_by_protocol::<T>()?
            .handles()
            .first()
            .ok_or::<EfiError>(EFI_STATUS_NOT_FOUND.into())?;
        self.open_protocol::<T>(handle)
    }

    /// Wrapper of `EFI_BOOT_SERVICE.GetMemoryMap()`.
    pub fn get_memory_map<'b>(&self, mmap_buffer: &'b mut [u8]) -> EfiResult<EfiMemoryMap<'b>> {
        let mut mmap_size = mmap_buffer.len();
        let mut map_key: usize = 0;
        let mut descriptor_size: usize = 0;
        let mut descriptor_version: u32 = 0;
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(
                self.boot_services.get_memory_map,
                &mut mmap_size,
                mmap_buffer.as_mut_ptr() as *mut _,
                &mut map_key,
                &mut descriptor_size,
                &mut descriptor_version
            )
        }?;
        Ok(EfiMemoryMap::new(
            &mut mmap_buffer[..mmap_size],
            map_key,
            descriptor_size,
            descriptor_version,
        ))
    }

    /// Wrapper of `EFI_BOOT_SERVICE.ExitBootServices()`.
    fn exit_boot_services<'b>(&self, mmap: &'b EfiMemoryMap<'b>) -> EfiResult<()> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(
                self.boot_services.exit_boot_services,
                self.efi_entry.image_handle().0,
                mmap.map_key()
            )
        }
    }

    /// Wrapper of `EFI_BOOT_SERVICE.Stall()`.
    pub fn stall(&self, micro: usize) -> EfiResult<()> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe { efi_call!(self.boot_services.stall, micro) }
    }

    /// Wrapper of `EFI_BOOT_SERVICE.CreateEvent()`.
    ///
    /// Args:
    ///
    ///  * `event_type`: The EFI event type.
    ///  * `cb`: An optional `&'e mut EventNotify`, which implements the event
    ///          notification function and provides the task level priority setting.
    pub fn create_event<'n, 'e: 'n>(
        &self,
        event_type: EventType,
        mut cb: Option<&'n mut EventNotify<'e>>,
    ) -> EfiResult<Event<'a, 'n>> {
        let mut efi_event: EfiEvent = null_mut();
        let (tpl, c_callback, cookie): (EfiTpl, EfiEventNotify, *mut core::ffi::c_void) = match cb {
            Some(ref mut event_notify) => {
                (event_notify.tpl as _, Some(efi_event_cb), *event_notify as *mut _ as _)
            }
            None => (0, None, null_mut()),
        };
        // SAFETY:
        // Pointers passed are output/callback context pointers which will not be retained by the
        // callback (`fn efi_event_cb()`).
        // The returned `Event` enforces a borrow to `cb` for 'e. It closes the event when it
        // goes out of scope. This ensures that `cb` lives at least as long as the event is in
        // effect and there can be no other borrows to `cb`.
        unsafe {
            efi_call!(
                self.boot_services.create_event,
                event_type as u32,
                tpl as usize,
                c_callback,
                cookie,
                &mut efi_event
            )?;
        }
        Ok(Event {
            efi_entry: self.efi_entry,
            efi_event: efi_event,
            _cb: cb.map::<&'n mut dyn FnMut(EfiEvent), _>(|v| v.cb),
        })
    }

    /// Wrapper of `EFI_BOOT_SERVICE.CloseEvent()`.
    fn close_event(&self, event: &Event) -> EfiResult<()> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe { efi_call!(self.boot_services.close_event, event.efi_event) }
    }

    /// Wrapper of `EFI_BOOT_SERVICE.CheckEvent()`.
    ///
    /// On success, returns true if the event is signaled, false if not.
    pub fn check_event(&self, event: &Event) -> EfiResult<bool> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        match unsafe { efi_call!(self.boot_services.check_event, event.efi_event) } {
            Err(e) if e != EFI_STATUS_NOT_READY.into() => Err(e),
            Ok(()) => Ok(true),
            _ => Ok(false),
        }
    }

    /// Wrapper of `EFI_BOOT_SERVICE.SetTimer()`.
    pub fn set_timer(
        &self,
        event: &Event,
        delay_type: EfiTimerDelay,
        trigger_time: u64,
    ) -> EfiResult<()> {
        // SAFETY: EFI_BOOT_SERVICES method call.
        unsafe {
            efi_call!(self.boot_services.set_timer, event.efi_event, delay_type, trigger_time)
        }
    }
}

/// EFI Event type to pass to BootServicess::create_event;
#[repr(u32)]
pub enum EventType {
    Timer = 0x80000000,
    RunTime = 0x40000000,
    NotifyWait = 0x00000100,
    NotifySignal = 0x00000200,
    SignalExitBootServices = 0x00000201,
    SignalVirtualAddressChange = 0x60000202,
}

/// EFI task level priority setting for event notify function.
#[repr(usize)]
#[derive(Copy, Clone)]
pub enum Tpl {
    Application = 4,
    Callback = 8,
    Notify = 16,
    HighLevel = 31,
}

/// `EventNotify` contains the task level priority setting and a mutable reference to a
/// `EventNotifyImpl` trait object. It is passed as the context pointer to low level EFI event
/// notification function entry (`unsafe extern "C" fn efi_event_cb(...)`).
pub struct EventNotify<'e> {
    tpl: Tpl,
    cb: &'e mut dyn FnMut(EfiEvent),
}

impl<'e> EventNotify<'e> {
    pub fn new(tpl: Tpl, cb: &'e mut dyn FnMut(EfiEvent)) -> Self {
        Self { tpl, cb }
    }
}

/// `Event` wraps the raw `EfiEvent` handle and internally enforces a borrow of the registered
/// callback for the given life time `e. The event is automatically closed when going out of scope.
pub struct Event<'a, 'n> {
    efi_entry: &'a EfiEntry,
    efi_event: EfiEvent,
    _cb: Option<&'n mut dyn FnMut(EfiEvent)>,
}

impl Drop for Event<'_, '_> {
    fn drop(&mut self) {
        self.efi_entry.system_table().boot_services().close_event(self).unwrap();
    }
}

/// Event notify function entry for EFI events.
///
/// Safety:
///
///   `ctx` must point to a `EventNotify` type object.
///   `ctx` must live longer than the event.
///   There should be no other references to `ctx`.
unsafe extern "C" fn efi_event_cb(event: EfiEvent, ctx: *mut core::ffi::c_void) {
    // SAFETY: By safety requirement of this function, ctx points to a valid `EventNotify` object,
    // outlives the event/the function call, and there is no other borrows.
    let event_cb = unsafe { (ctx as *mut EventNotify).as_mut() }.unwrap();
    (event_cb.cb)(event);
}

/// A type for accessing memory map.
pub struct EfiMemoryMap<'a> {
    buffer: &'a mut [u8],
    map_key: usize,
    descriptor_size: usize,
    descriptor_version: u32,
}

/// Iterator for traversing `EfiMemoryDescriptor` items in `EfiMemoryMap::buffer`.
pub struct EfiMemoryMapIter<'a: 'b, 'b> {
    memory_map: &'b EfiMemoryMap<'a>,
    offset: usize,
}

impl<'a, 'b> Iterator for EfiMemoryMapIter<'a, 'b> {
    type Item = &'b EfiMemoryDescriptor;

    fn next(&mut self) -> Option<Self::Item> {
        if self.offset >= self.memory_map.buffer.len() {
            return None;
        }
        let bytes = &self.memory_map.buffer[self.offset..][..self.memory_map.descriptor_size];
        self.offset += self.memory_map.descriptor_size;
        Some(Ref::<_, EfiMemoryDescriptor>::new_from_prefix(bytes).unwrap().0.into_ref())
    }
}

impl<'a> EfiMemoryMap<'a> {
    /// Creates a new instance with the given parameters obtained from `get_memory_map()`.
    fn new(
        buffer: &'a mut [u8],
        map_key: usize,
        descriptor_size: usize,
        descriptor_version: u32,
    ) -> Self {
        Self { buffer, map_key, descriptor_size, descriptor_version }
    }

    /// Returns the buffer.
    pub fn buffer(&self) -> &[u8] {
        self.buffer
    }

    /// Returns the value of `map_key`.
    pub fn map_key(&self) -> usize {
        self.map_key
    }

    /// Returns the value of `descriptor_version`.
    pub fn descriptor_version(&self) -> u32 {
        self.descriptor_version
    }

    /// Returns the number of descriptors.
    pub fn len(&self) -> usize {
        self.buffer.len() / self.descriptor_size
    }
}

impl<'a: 'b, 'b> IntoIterator for &'b EfiMemoryMap<'a> {
    type Item = &'b EfiMemoryDescriptor;
    type IntoIter = EfiMemoryMapIter<'a, 'b>;

    fn into_iter(self) -> Self::IntoIter {
        EfiMemoryMapIter { memory_map: self, offset: 0 }
    }
}

/// A type representing a UEFI handle to a UEFI device.
#[derive(Debug, Copy, Clone, PartialEq)]
pub struct DeviceHandle(EfiHandle);

/// `LocatedHandles` holds the array of handles return by
/// `BootServices::locate_handle_buffer_by_protocol()`.
pub struct LocatedHandles<'a> {
    handles: &'a [DeviceHandle],
    efi_entry: &'a EfiEntry,
}

impl<'a> LocatedHandles<'a> {
    pub(crate) fn new(handles: *mut EfiHandle, len: usize, efi_entry: &'a EfiEntry) -> Self {
        // Implementation is not suppose to call this with a NULL pointer.
        debug_assert!(!handles.is_null());
        Self {
            // SAFETY: Given correct UEFI firmware, non-null pointer points to valid memory.
            // The memory is owned by the objects.
            handles: unsafe { from_raw_parts(handles as *mut DeviceHandle, len) },
            efi_entry: efi_entry,
        }
    }
    /// Get the list of handles as a slice.
    pub fn handles(&self) -> &[DeviceHandle] {
        self.handles
    }
}

impl Drop for LocatedHandles<'_> {
    fn drop(&mut self) {
        self.efi_entry
            .system_table()
            .boot_services()
            .free_pool(self.handles.as_ptr() as *mut _)
            .unwrap();
    }
}

/// Helper macro for printing message via `EFI_SIMPLE_TEXT_OUTPUT_PROTOCOL` in
/// `EFI_SYSTEM_TABLE.ConOut`.
#[macro_export]
macro_rules! efi_print {
    ( $efi_entry:expr, $( $x:expr ),* $(,)? ) => {
            write!($efi_entry.system_table().con_out().unwrap(), $($x,)*).unwrap()
    };
}

#[macro_export]
macro_rules! efi_println {
    ( $efi_entry:expr, $( $x:expr ),* ) => {
        efi_print!($efi_entry, $($x,)*);
        efi_print!($efi_entry, "\r\n");
    };
}

/// Provides a builtin panic handler.
/// In the long term, to improve flexibility, consider allowing application to install a custom
/// handler into `EfiEntry` to be called here.
#[cfg(not(test))]
#[panic_handler]
fn panic(panic: &PanicInfo) -> ! {
    // If there is a valid internal `efi_entry` from global allocator, print the panic info.
    let entry = allocation::internal_efi_entry();
    if let Some(e) = entry {
        match e.system_table().con_out() {
            Ok(mut con_out) => {
                let _ = write!(con_out, "Panics! {}\r\n", panic);
            }
            _ => {}
        }
    }
    loop {}
}

#[cfg(test)]
mod test {
    use super::*;
    use std::cell::RefCell;
    use std::collections::VecDeque;
    use std::mem::size_of;
    use std::slice::from_raw_parts_mut;

    use zerocopy::AsBytes;

    /// A structure to store the traces of arguments/outputs for EFI methods.
    #[derive(Default)]
    pub struct EfiCallTraces {
        pub free_pool_trace: FreePoolTrace,
        pub open_protocol_trace: OpenProtocolTrace,
        pub close_protocol_trace: CloseProtocolTrace,
        pub locate_handle_buffer_trace: LocateHandleBufferTrace,
        pub get_memory_map_trace: GetMemoryMapTrace,
        pub exit_boot_services_trace: ExitBootServicespTrace,
        pub create_event_trace: CreateEventTrace,
        pub close_event_trace: CloseEventTrace,
        pub check_event_trace: CheckEventTrace,
    }

    // Declares a global instance of EfiCallTraces.
    // Need to use thread local storage because rust unit test is multi-threaded.
    thread_local! {
        static EFI_CALL_TRACES: RefCell<EfiCallTraces> = RefCell::new(Default::default());
    }

    /// Exports for unit-test in submodules.
    pub fn efi_call_traces() -> &'static std::thread::LocalKey<RefCell<EfiCallTraces>> {
        &EFI_CALL_TRACES
    }

    /// EFI_BOOT_SERVICE.FreePool() test implementation.
    #[derive(Default)]
    pub struct FreePoolTrace {
        // Capture `buf`
        pub inputs: VecDeque<*mut core::ffi::c_void>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.FreePool` C API in test environment.
    extern "C" fn free_pool(buf: *mut core::ffi::c_void) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            traces.borrow_mut().free_pool_trace.inputs.push_back(buf);
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.OpenProtocol() test implementation.
    #[derive(Default)]
    pub struct OpenProtocolTrace {
        // Capture `handle`, `protocol_guid`, `agent_handle`.
        pub inputs: VecDeque<(DeviceHandle, EfiGuid, EfiHandle)>,
        // Return `intf`, EfiStatus.
        pub outputs: VecDeque<(EfiHandle, EfiStatus)>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.OpenProtocol` C API in test environment.
    ///
    /// # Safety
    ///
    ///   Caller should guarantee that `intf` and `protocol_guid` point to valid memory locations.
    unsafe extern "C" fn open_protocol(
        handle: EfiHandle,
        protocol_guid: *const EfiGuid,
        intf: *mut *mut core::ffi::c_void,
        agent_handle: EfiHandle,
        _: EfiHandle,
        attr: u32,
    ) -> EfiStatus {
        assert_eq!(attr, EFI_OPEN_PROTOCOL_ATTRIBUTE_BY_HANDLE_PROTOCOL);
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().open_protocol_trace;
            trace.inputs.push_back((DeviceHandle(handle), *protocol_guid, agent_handle));

            let (intf_handle, status) = trace.outputs.pop_front().unwrap();
            *intf = intf_handle;

            status
        })
    }

    /// EFI_BOOT_SERVICE.CloseProtocol() test implementation.
    #[derive(Default)]
    pub struct CloseProtocolTrace {
        // Capture `handle`, `protocol_guid`, `agent_handle`
        pub inputs: VecDeque<(DeviceHandle, EfiGuid, EfiHandle)>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.CloseProtocol` C API in test environment.
    ///
    /// # Safety
    ///
    ///   Caller should guarantee that `protocol_guid` points to valid memory location.
    unsafe extern "C" fn close_protocol(
        handle: EfiHandle,
        protocol_guid: *const EfiGuid,
        agent_handle: EfiHandle,
        _: EfiHandle,
    ) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            traces.borrow_mut().close_protocol_trace.inputs.push_back((
                DeviceHandle(handle),
                *protocol_guid,
                agent_handle,
            ));
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.LocateHandleBuffer.
    #[derive(Default)]
    pub struct LocateHandleBufferTrace {
        // Capture `protocol`.
        pub inputs: VecDeque<EfiGuid>,
        // For returning in `num_handles` and `buf`.
        pub outputs: VecDeque<(usize, *mut DeviceHandle)>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.LocateHandleBuffer` C API in test environment.
    ///
    /// # Safety
    ///
    ///   Caller should guarantee that `num_handles` and `buf` point to valid memory locations.
    unsafe extern "C" fn locate_handle_buffer(
        search_type: EfiLocateHandleSearchType,
        protocol: *const EfiGuid,
        search_key: *mut core::ffi::c_void,
        num_handles: *mut usize,
        buf: *mut *mut EfiHandle,
    ) -> EfiStatus {
        assert_eq!(search_type, EFI_LOCATE_HANDLE_SEARCH_TYPE_BY_PROTOCOL);
        assert_eq!(search_key, null_mut());
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().locate_handle_buffer_trace;
            trace.inputs.push_back(*protocol);

            let (num, handles) = trace.outputs.pop_front().unwrap();
            *num_handles = num as usize;
            *buf = handles as *mut EfiHandle;

            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.GetMemoryMap.
    #[derive(Default)]
    pub struct GetMemoryMapTrace {
        // Capture `memory_map_size` and `memory_map` argument.
        pub inputs: VecDeque<(usize, *mut EfiMemoryDescriptor)>,
        // Output value `map_key`, `memory_map_size`.
        pub outputs: VecDeque<(usize, usize)>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.GetMemoryMap` C API in test environment.
    ///
    /// # Safety
    ///
    ///   Caller should guarantee that `memory_map_size`, `map_key` and `desc_size` point to valid
    ///   memory locations.
    unsafe extern "C" fn get_memory_map(
        memory_map_size: *mut usize,
        memory_map: *mut EfiMemoryDescriptor,
        map_key: *mut usize,
        desc_size: *mut usize,
        _: *mut u32,
    ) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().get_memory_map_trace;
            trace.inputs.push_back((unsafe { *memory_map_size }, memory_map));
            (*map_key, *memory_map_size) = trace.outputs.pop_front().unwrap();
            *desc_size = size_of::<EfiMemoryDescriptor>();
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.ExitBootServices.
    #[derive(Default)]
    pub struct ExitBootServicespTrace {
        // Capture `image_handle`, `map_key`
        pub inputs: VecDeque<(EfiHandle, usize)>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.ExitBootServices` C API in test environment.
    extern "C" fn exit_boot_services(image_handle: EfiHandle, map_key: usize) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().exit_boot_services_trace;
            trace.inputs.push_back((image_handle, map_key));
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.CreateEvent.
    #[derive(Default)]
    pub struct CreateEventTrace {
        // Capture `type_`, `notify_tpl`, `notify_fn`, `notify_ctx`
        pub inputs: VecDeque<(u32, EfiTpl, EfiEventNotify, *mut core::ffi::c_void)>,
        // Output a EfiEvent.
        pub outputs: VecDeque<EfiEvent>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.CreateEvent` C API in test environment.
    ///
    /// # Safety
    ///
    ///   Caller should guarantee that `event` points to valid memory location.
    unsafe extern "C" fn create_event(
        type_: u32,
        notify_tpl: EfiTpl,
        notify_fn: EfiEventNotify,
        notify_ctx: *mut core::ffi::c_void,
        event: *mut EfiEvent,
    ) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().create_event_trace;
            trace.inputs.push_back((type_, notify_tpl, notify_fn, notify_ctx));
            *event = trace.outputs.pop_front().unwrap();
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.CloseEvent.
    #[derive(Default)]
    pub struct CloseEventTrace {
        // Capture `event`
        pub inputs: VecDeque<EfiEvent>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.CloseEvent` C API in test environment.
    extern "C" fn close_event(event: EfiEvent) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().close_event_trace;
            trace.inputs.push_back(event);
            EFI_STATUS_SUCCESS
        })
    }

    /// EFI_BOOT_SERVICE.CheckEvent.
    #[derive(Default)]
    pub struct CheckEventTrace {
        // EfiStatus for return.
        pub outputs: VecDeque<EfiStatus>,
    }

    /// Mock of the `EFI_BOOT_SERVICE.CheckEvent` C API in test environment.
    extern "C" fn check_event(_: EfiEvent) -> EfiStatus {
        EFI_CALL_TRACES.with(|traces| {
            let trace = &mut traces.borrow_mut().check_event_trace;
            trace.outputs.pop_front().unwrap()
        })
    }

    /// A test wrapper that sets up a system table, image handle and runs a test function like it
    /// is an EFI application.
    /// TODO(300168989): Investigate using procedural macro to generate test that auto calls this.
    pub fn run_test(func: fn(EfiHandle, *mut EfiSystemTable) -> ()) {
        // Reset all traces
        EFI_CALL_TRACES.with(|trace| {
            *trace.borrow_mut() = Default::default();
        });

        let mut systab: EfiSystemTable = Default::default();
        let mut boot_services: EfiBootService = Default::default();

        boot_services.free_pool = Some(free_pool);
        boot_services.open_protocol = Some(open_protocol);
        boot_services.close_protocol = Some(close_protocol);
        boot_services.locate_handle_buffer = Some(locate_handle_buffer);
        boot_services.get_memory_map = Some(get_memory_map);
        boot_services.exit_boot_services = Some(exit_boot_services);
        boot_services.create_event = Some(create_event);
        boot_services.close_event = Some(close_event);
        boot_services.check_event = Some(check_event);
        systab.boot_services = &mut boot_services as *mut _;
        let image_handle: usize = 1234; // Don't care.

        func(image_handle as EfiHandle, &mut systab as *mut _);

        // Reset all traces
        EFI_CALL_TRACES.with(|trace| {
            *trace.borrow_mut() = Default::default();
        });
    }

    /// Get the pointer to an object as an EfiHandle type.
    fn as_efi_handle<T>(val: &mut T) -> EfiHandle {
        val as *mut T as *mut _
    }

    #[test]
    fn test_open_close_protocol() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };

            // Set up open_protocol trace
            let mut block_io: EfiBlockIoProtocol = Default::default();
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().open_protocol_trace.outputs =
                    VecDeque::from([(as_efi_handle(&mut block_io), EFI_STATUS_SUCCESS)]);
            });

            let mut device_handle: usize = 0; // Don't care
            {
                // Open a protocol
                let protocol = efi_entry
                    .system_table()
                    .boot_services()
                    .open_protocol::<BlockIoProtocol>(DeviceHandle(as_efi_handle(
                        &mut device_handle,
                    )))
                    .unwrap();

                // Validate call args
                EFI_CALL_TRACES.with(|trace| {
                    assert_eq!(
                        trace.borrow_mut().open_protocol_trace.inputs,
                        [(
                            DeviceHandle(as_efi_handle(&mut device_handle)),
                            BlockIoProtocol::GUID,
                            image_handle
                        ),]
                    );

                    // close_protocol not called yet.
                    assert_eq!(trace.borrow_mut().close_protocol_trace.inputs, []);
                });

                // The protocol gets the correct EfiBlockIoProtocol structure we pass in.
                assert_eq!(protocol.interface_ptr(), &mut block_io as *mut _);
            }

            // Close protocol is called as `protocol` goes out of scope.
            EFI_CALL_TRACES.with(|trace| {
                assert_eq!(
                    trace.borrow_mut().close_protocol_trace.inputs,
                    [(
                        DeviceHandle(as_efi_handle(&mut device_handle)),
                        BlockIoProtocol::GUID,
                        image_handle
                    ),]
                )
            });
        })
    }

    #[test]
    fn test_null_efi_method() {
        // Test that wrapper call fails if efi method is None.
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };

            // Set up open_protocol trace
            let mut block_io: EfiBlockIoProtocol = Default::default();
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().open_protocol_trace.outputs =
                    VecDeque::from([(as_efi_handle(&mut block_io), EFI_STATUS_SUCCESS)]);
            });

            // Set the method to None.
            // SAFETY:
            // run_test() guarantees `boot_services` pointer points to valid object.
            unsafe { (*(*systab_ptr).boot_services).open_protocol = None };

            let mut device_handle: usize = 0; // Don't care
            assert!(efi_entry
                .system_table()
                .boot_services()
                .open_protocol::<BlockIoProtocol>(DeviceHandle(as_efi_handle(&mut device_handle)))
                .is_err());
        })
    }

    #[test]
    fn test_error_efi_method() {
        // Test that wrapper call fails if efi method returns error.
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };

            // Set up open_protocol trace.
            let mut block_io: EfiBlockIoProtocol = Default::default();
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().open_protocol_trace.outputs =
                    VecDeque::from([(as_efi_handle(&mut block_io), EFI_STATUS_NOT_FOUND)]);
            });

            let mut device_handle: usize = 0; // Don't care
            assert!(efi_entry
                .system_table()
                .boot_services()
                .open_protocol::<BlockIoProtocol>(DeviceHandle(as_efi_handle(&mut device_handle)))
                .is_err());
        })
    }

    #[test]
    fn test_locate_handle_buffer_by_protocol() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };

            // Set up locate_handle_buffer_trace trace.
            let mut located_handles: [DeviceHandle; 3] =
                [DeviceHandle(1 as *mut _), DeviceHandle(2 as *mut _), DeviceHandle(3 as *mut _)];
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().locate_handle_buffer_trace.outputs =
                    VecDeque::from([(located_handles.len(), located_handles.as_mut_ptr())]);
            });

            {
                let handles = efi_entry
                    .system_table()
                    .boot_services()
                    .locate_handle_buffer_by_protocol::<BlockIoProtocol>()
                    .unwrap();

                // Returned handles are expected.
                assert_eq!(handles.handles().to_vec(), located_handles);
            }

            EFI_CALL_TRACES.with(|traces| {
                let traces = traces.borrow_mut();
                // Arguments are passed correctly.
                assert_eq!(traces.locate_handle_buffer_trace.inputs, [BlockIoProtocol::GUID]);
                // Free pool is called with the correct address.
                assert_eq!(traces.free_pool_trace.inputs, [located_handles.as_mut_ptr() as *mut _]);
            });
        })
    }

    #[test]
    fn test_find_first_and_open() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };

            // Set up locate_handle_buffer_trace trace.
            let mut located_handles: [DeviceHandle; 3] =
                [DeviceHandle(1 as *mut _), DeviceHandle(2 as *mut _), DeviceHandle(3 as *mut _)];
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().locate_handle_buffer_trace.outputs =
                    VecDeque::from([(located_handles.len(), located_handles.as_mut_ptr())]);
            });

            // Set up open_protocol trace.
            let mut block_io: EfiBlockIoProtocol = Default::default();
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().open_protocol_trace.outputs =
                    VecDeque::from([(as_efi_handle(&mut block_io), EFI_STATUS_SUCCESS)]);
            });

            efi_entry
                .system_table()
                .boot_services()
                .find_first_and_open::<BlockIoProtocol>()
                .unwrap();

            // Check open_protocol is called on the first handle.
            EFI_CALL_TRACES.with(|traces| {
                assert_eq!(
                    traces.borrow_mut().open_protocol_trace.inputs,
                    [(DeviceHandle(1 as *mut _), BlockIoProtocol::GUID, image_handle),]
                );
            });
        })
    }

    #[test]
    fn test_exit_boot_services() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };
            // Create a buffer large enough to hold two EfiMemoryDescriptor.
            let mut descriptors: [EfiMemoryDescriptor; 2] = [
                EfiMemoryDescriptor {
                    memory_type: EFI_MEMORY_TYPE_LOADER_DATA,
                    padding: 0,
                    physical_start: 0,
                    virtual_start: 0,
                    number_of_pages: 0,
                    attributes: 0,
                },
                EfiMemoryDescriptor {
                    memory_type: EFI_MEMORY_TYPE_LOADER_CODE,
                    padding: 0,
                    physical_start: 0,
                    virtual_start: 0,
                    number_of_pages: 0,
                    attributes: 0,
                },
            ];
            let map_key: usize = 12345;
            // Set up get_memory_map trace.
            EFI_CALL_TRACES.with(|traces| {
                // Output only the first EfiMemoryDescriptor.
                traces.borrow_mut().get_memory_map_trace.outputs =
                    VecDeque::from([(map_key, 1 * size_of::<EfiMemoryDescriptor>())]);
            });

            // SAFETY: Buffer is guaranteed valid.
            let buffer = unsafe {
                from_raw_parts_mut(
                    descriptors.as_mut_ptr() as *mut u8,
                    descriptors.len() * size_of::<EfiMemoryDescriptor>(),
                )
            };

            // Test `exit_boot_services`
            let desc = super::exit_boot_services(efi_entry, buffer).unwrap();

            // Validate that UEFI APIs are correctly called.
            EFI_CALL_TRACES.with(|traces| {
                assert_eq!(
                    traces.borrow_mut().get_memory_map_trace.inputs,
                    [(
                        descriptors.len() * size_of::<EfiMemoryDescriptor>(),
                        descriptors.as_mut_ptr()
                    )]
                );

                assert_eq!(
                    traces.borrow_mut().exit_boot_services_trace.inputs,
                    [(image_handle, map_key)],
                );
            });

            // Validate that the returned `EfiMemoryMap` contains only 1 EfiMemoryDescriptor.
            assert_eq!(desc.into_iter().map(|v| *v).collect::<Vec<_>>(), descriptors[..1].to_vec());
            // Validate that the returned `EfiMemoryMap` has the correct map_key.
            assert_eq!(desc.map_key(), map_key);
        })
    }

    #[test]
    fn test_exit_boot_services_unaligned_buffer() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };
            // Create a buffer for 2 EfiMemoryDescriptor.
            let descriptors: [EfiMemoryDescriptor; 2] = [
                EfiMemoryDescriptor {
                    memory_type: EFI_MEMORY_TYPE_LOADER_DATA,
                    padding: 0,
                    physical_start: 0,
                    virtual_start: 0,
                    number_of_pages: 0,
                    attributes: 0,
                },
                EfiMemoryDescriptor {
                    memory_type: EFI_MEMORY_TYPE_LOADER_CODE,
                    padding: 0,
                    physical_start: 0,
                    virtual_start: 0,
                    number_of_pages: 0,
                    attributes: 0,
                },
            ];

            let map_key: usize = 12345;
            // Set up get_memory_map trace.
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().get_memory_map_trace.outputs =
                    VecDeque::from([(map_key, 2 * size_of::<EfiMemoryDescriptor>())]);
            });

            // Construct the destination buffer.
            let mut buffer = [0u8; 256];
            let alignment = core::mem::align_of::<EfiMemoryDescriptor>();
            let size = core::mem::size_of::<EfiMemoryDescriptor>();
            let aligned = aligned_subslice(&mut buffer[..], alignment).unwrap();
            // Offset by 1 element so that we can make an unaligned buffer starting somewhere in
            // between.
            let start = aligned.get_mut(size..).unwrap();
            start[..size].clone_from_slice(descriptors[0].as_bytes());
            start[size..][..size].clone_from_slice(descriptors[1].as_bytes());
            // Pass an unaligned address.
            let desc = super::exit_boot_services(efi_entry, &mut aligned[size - 1..]).unwrap();
            // Validate that the returned `EfiMemoryMap` contains the correct EfiMemoryDescriptor.
            assert_eq!(desc.into_iter().map(|v| *v).collect::<Vec<_>>(), descriptors[..2].to_vec());
            // Validate that the returned `EfiMemoryMap` has the correct map_key.
            assert_eq!(desc.map_key(), map_key);
        });
    }

    #[test]
    fn test_create_event_with_notify_fn() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };
            let mut cb_impl = |_: EfiEvent| {};
            let mut cb = EventNotify::new(Tpl::Callback, &mut cb_impl);
            let event: EfiEvent = 1234usize as _;
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().create_event_trace.outputs.push_back(event);
            });
            {
                let _ = efi_entry
                    .system_table()
                    .boot_services()
                    .create_event(EventType::Timer, Some(&mut cb))
                    .unwrap();
            }
            let efi_cb: EfiEventNotify = Some(efi_event_cb);
            EFI_CALL_TRACES.with(|traces| {
                assert_eq!(
                    traces.borrow_mut().create_event_trace.inputs,
                    [(
                        EventType::Timer as _,
                        Tpl::Callback as _,
                        efi_cb,
                        &mut cb as *mut _ as *mut _
                    )]
                )
            });
            // Verify close_event is called.
            EFI_CALL_TRACES
                .with(|traces| assert_eq!(traces.borrow_mut().close_event_trace.inputs, [event]));
        });
    }

    #[test]
    fn test_create_event_wo_notify_fn() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };
            let event: EfiEvent = 1234usize as _;
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().create_event_trace.outputs.push_back(event);
            });
            {
                let _ = efi_entry
                    .system_table()
                    .boot_services()
                    .create_event(EventType::Timer, None)
                    .unwrap();
            }
            EFI_CALL_TRACES.with(|traces| {
                assert_eq!(
                    traces.borrow_mut().create_event_trace.inputs,
                    [(EventType::Timer as _, 0, None, null_mut())]
                )
            });
        });
    }

    #[test]
    fn test_check_event() {
        run_test(|image_handle, systab_ptr| {
            let efi_entry = EfiEntry { image_handle, systab_ptr };
            let event: EfiEvent = 1234usize as _;
            EFI_CALL_TRACES.with(|traces| {
                traces.borrow_mut().create_event_trace.outputs.push_back(event);
                traces.borrow_mut().check_event_trace.outputs.push_back(EFI_STATUS_SUCCESS);
                traces.borrow_mut().check_event_trace.outputs.push_back(EFI_STATUS_NOT_READY);
                traces.borrow_mut().check_event_trace.outputs.push_back(EFI_STATUS_UNSUPPORTED);
            });
            let res = efi_entry
                .system_table()
                .boot_services()
                .create_event(EventType::Timer, None)
                .unwrap();
            assert_eq!(efi_entry.system_table().boot_services().check_event(&res), Ok(true));
            assert_eq!(efi_entry.system_table().boot_services().check_event(&res), Ok(false));
            assert!(efi_entry.system_table().boot_services().check_event(&res).is_err());
        });
    }

    #[test]
    fn test_efi_error() {
        let res: EfiResult<()> = Err(EFI_STATUS_NOT_FOUND.into());
        assert_eq!(res.unwrap_err().err(), ErrorTypes::EfiStatusError(14));
    }
}
