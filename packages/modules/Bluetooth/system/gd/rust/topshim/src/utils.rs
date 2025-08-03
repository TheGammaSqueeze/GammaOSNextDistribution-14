//! Topshim utils.

use std::ffi::CString;
use std::marker::PhantomData;
use std::os::raw::c_char;

/// Lifetime-checked const pointer wrapper.
///
/// The wrapper holds the raw pointer and clones the lifetime from the pointing object,
/// which forces the compiler to check and fail when the wrapper lives longer than the data.
///
/// Example 1:
///     Get the pointer with from_ref(), and pass the pointer into() a C function.
///     ```
///     // let addr: RawAddress;
///     let addr_ptr = LTCheckedPtr::from_ref(&addr);
///     // The pointer type would be `*const RawAddress`.
///     ccall!(self, foo, addr_ptr.into());
///     ```
///
/// Example 2:
///     Get the pointer from() an array-like type, such as slice, Vec, and String.
///     Cast and pass the pointer into a C function with cast_into().
///     ```
///     // let profile: Vec<u8>;
///     let profile_ptr = LTCheckedPtr::from(&profile);
///     // The pointer type would be `*const c_char`.
///     ccall!(self, bar, profile_ptr.cast_into::<c_char>());
///     ```
///
/// Example 3:
///     Get the pointer from() a Box or Option, and pass the pointer into() a C function.
///     ```
///     // let uuid: Option<Uuid>;
///     let uuid_ptr = LTCheckedPtr::from(&uuid);
///     // The pointer type would be `*const Uuid`, while it could be NULL.
///     ccall!(self, foobar, uuid_ptr.into());
///     ```
pub(crate) struct LTCheckedPtr<'a, T> {
    ptr: *const T,
    _covariant: PhantomData<&'a ()>,
}

impl<T> LTCheckedPtr<'static, T> {
    /// Returns a null pointer, which has static lifetime.
    pub(crate) fn null() -> Self {
        Self { ptr: std::ptr::null(), _covariant: PhantomData }
    }
}

impl<'a, T> LTCheckedPtr<'a, T> {
    /// Constructs a lifetime-checked constant pointer from a reference.
    pub(crate) fn from_ref(val: &'a T) -> Self {
        Self { ptr: val, _covariant: PhantomData }
    }

    /// Returns the casted raw constant pointer.
    pub(crate) fn cast_into<CT>(self) -> *const CT {
        self.ptr as *const CT
    }
}

impl<'a, T> Into<*const T> for LTCheckedPtr<'a, T> {
    fn into(self) -> *const T {
        self.ptr
    }
}

impl<'a, T> From<&'a [T]> for LTCheckedPtr<'a, T> {
    fn from(val: &'a [T]) -> Self {
        Self { ptr: val.as_ptr(), _covariant: PhantomData }
    }
}

impl<'a, T> From<&'a Vec<T>> for LTCheckedPtr<'a, T> {
    fn from(val: &'a Vec<T>) -> Self {
        Self { ptr: val.as_ptr(), _covariant: PhantomData }
    }
}

impl<'a> From<&'a String> for LTCheckedPtr<'a, u8> {
    fn from(val: &'a String) -> Self {
        Self { ptr: val.as_ptr(), _covariant: PhantomData }
    }
}

impl<'a> From<&'a CString> for LTCheckedPtr<'a, c_char> {
    fn from(val: &'a CString) -> Self {
        Self { ptr: val.as_ptr(), _covariant: PhantomData }
    }
}

impl<'a, T> From<&'a Option<T>> for LTCheckedPtr<'a, T> {
    fn from(val: &'a Option<T>) -> Self {
        match val {
            Some(ref v) => Self { ptr: v, _covariant: PhantomData },
            None => LTCheckedPtr::null(),
        }
    }
}

impl<'a, T> From<&'a Box<T>> for LTCheckedPtr<'a, T> {
    fn from(val: &'a Box<T>) -> Self {
        Self { ptr: &**val, _covariant: PhantomData }
    }
}

/// Lifetime-checked mutable pointer wrapper.
///
/// The wrapper holds the raw pointer and clones the lifetime from the pointing object,
/// which forces the compiler to check and fail when the wrapper lives longer than the data.
///
/// Example 1:
///     Get the pointer with from_ref(), and pass the pointer into() a C function.
///     ```
///     // let mut record: bluetooth_sdp_record;
///     let record_ptr = LTCheckedPtrMut::from_ref(&mut report);
///     // The pointer type would be `*mut bluetooth_sdp_record`.
///     ccall!(self, foo, record_ptr.into());
///     ```
///
/// Example 2:
///     Get the pointer from() an array-like type, such as slice, Vec, and String.
///     Cast and pass the pointer into a C function with cast_into().
///     ```
///     // let mut report: [u8];
///     let report_ptr = LTCheckedPtrMut::from(&mut report);
///     // The pointer type would be `*mut c_char`.
///     ccall!(self, bar, report_ptr.cast_into::<c_char>());
///     ```
///
/// Example 3:
///     Get the pointer from() a Box or Option, and pass the pointer into() a C function.
///     ```
///     // let mut callbacks: Box<bt_callbacks_t>;
///     let cb_ptr = LTCheckedPtrMut::from(&mut callbacks);
///     // The pointer type would be `*mut bt_callbacks_t`.
///     ccall!(self, init, cb_ptr.into());
///     ```
pub(crate) struct LTCheckedPtrMut<'a, T> {
    ptr: *mut T,
    _covariant: PhantomData<&'a ()>,
}

impl<T> LTCheckedPtrMut<'static, T> {
    /// Returns a null pointer, which has static lifetime.
    pub(crate) fn null() -> Self {
        Self { ptr: std::ptr::null_mut(), _covariant: PhantomData }
    }
}

impl<'a, T> LTCheckedPtrMut<'a, T> {
    /// Constructs a lifetime-checked mutable pointer from a reference.
    pub(crate) fn from_ref(val: &'a mut T) -> Self {
        Self { ptr: val, _covariant: PhantomData }
    }

    /// Returns the casted raw mutable pointer.
    pub(crate) fn cast_into<CT>(self) -> *mut CT {
        self.ptr as *mut CT
    }
}

impl<'a, T> Into<*mut T> for LTCheckedPtrMut<'a, T> {
    fn into(self) -> *mut T {
        self.ptr
    }
}

impl<'a, T> From<&'a mut [T]> for LTCheckedPtrMut<'a, T> {
    fn from(val: &'a mut [T]) -> Self {
        Self { ptr: val.as_mut_ptr(), _covariant: PhantomData }
    }
}

impl<'a, T> From<&'a mut Vec<T>> for LTCheckedPtrMut<'a, T> {
    fn from(val: &'a mut Vec<T>) -> Self {
        Self { ptr: val.as_mut_ptr(), _covariant: PhantomData }
    }
}

impl<'a> From<&'a mut String> for LTCheckedPtrMut<'a, u8> {
    fn from(val: &'a mut String) -> Self {
        Self { ptr: val.as_mut_ptr(), _covariant: PhantomData }
    }
}

impl<'a, T> From<&'a mut Option<T>> for LTCheckedPtrMut<'a, T> {
    fn from(val: &'a mut Option<T>) -> Self {
        match val {
            Some(ref mut v) => Self { ptr: v, _covariant: PhantomData },
            None => LTCheckedPtrMut::null(),
        }
    }
}

impl<'a, T> From<&'a mut Box<T>> for LTCheckedPtrMut<'a, T> {
    fn from(val: &'a mut Box<T>) -> Self {
        Self { ptr: &mut **val, _covariant: PhantomData }
    }
}
