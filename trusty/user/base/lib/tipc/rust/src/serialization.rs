use crate::{Handle, TipcError};
use core::fmt::Debug;
use core::{mem, slice};

/// A helper provided by the transport handle for the message type to serialize
/// into.
///
/// Borrows the serialized bytes with the `'s` lifetime, so data does not need
/// to be copied when sending a message.
///
/// The serialization methods may be called multiple times, and the final
/// serialized data will be the concatenation of the sequences of bytes and
/// sequences of handles from these calls.
pub trait Serializer<'s> {
    type Ok;
    type Error: Debug;

    /// Serialize a sequence of bytes.
    fn serialize_bytes(&mut self, bytes: &'s [u8]) -> Result<Self::Ok, Self::Error>;

    /// Serialize a structure directly as raw bytes.
    ///
    /// Safety: The structure must have a well-defined layout (`repr(C,
    /// packed)`) which exactly matches what the receiver expects. This may
    /// serialize uninitialized memory if the structure contains padding, so a
    /// packed structure without any padding is required to prevent accidental
    /// disclosure of previous data.
    unsafe fn serialize_as_bytes<T: Sized>(&mut self, obj: &'s T) -> Result<Self::Ok, Self::Error> {
        let ptr = obj as *const _ as *const u8;
        // SAFETY: Converting a repr(C) struct to a slice of bytes. obj is a
        // reference of our serializer liftime, so explicitly assigning that
        // lifetime to the resulting slice is safe.
        let bytes: &'s [u8] = slice::from_raw_parts(&*ptr, mem::size_of::<T>());
        self.serialize_bytes(bytes)
    }

    /// Serialize a handle to be sent along with the message bytes.
    ///
    /// The handle is copied, and should remain open and valid until
    /// serialization is complete and the message has been sent.
    fn serialize_handle(&mut self, handle: &'s Handle) -> Result<Self::Ok, Self::Error>;
}

/// A type that can serialize itself into a sequence of bytes and handles.
///
/// Serialization is done using callbacks in the [`Serializer`] type to avoid
/// unnecessarily copying data.
pub trait Serialize<'s> {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error>;
}

impl<'s> Serialize<'s> for u32 {
    fn serialize<'a: 's, S: Serializer<'s>>(
        &'a self,
        serializer: &mut S,
    ) -> Result<S::Ok, S::Error> {
        // SAFETY:
        //  u32 is a trivial type with a
        //  corresponding C representation
        unsafe { serializer.serialize_as_bytes(self) }
    }
}

/// A type that can deserialize itself from a sequence of bytes and handles.
pub trait Deserialize: Sized {
    type Error: From<TipcError> + Debug;

    /// The maximum amount of data that can be deserialized into this type.
    ///
    /// Buffering clients use this value to determine how large of a buffer
    /// is required to receive a message that deserializes into this type.
    ///
    /// # Examples
    ///
    /// Allocate a stack buffer and receive a response type into it:
    ///
    /// ```
    /// let mut buf = [0; Response::MAX_SERIALIZED_SIZE];
    /// let response: Response = handle.recv(&mut buf)
    ///                              .expect("Could not deserialize response");
    /// ```
    const MAX_SERIALIZED_SIZE: usize;

    /// Construct a new instance of this type from the provided bytes and
    /// handles.
    ///
    /// The resulting value must be a copy of the data, if needed.
    ///
    /// The list of received handles is passed as a `&mut [Option<Handle>]` so
    /// that you can use [`Option::take`] to take ownership of the handles. As
    /// such, all values in `handles` will be `Some` when `deserialize` is
    /// called.
    fn deserialize(bytes: &[u8], handles: &mut [Option<Handle>]) -> Result<Self, Self::Error>;
}

impl Deserialize for () {
    type Error = TipcError;

    const MAX_SERIALIZED_SIZE: usize = 0;

    fn deserialize(_bytes: &[u8], _handles: &mut [Option<Handle>]) -> Result<Self, Self::Error> {
        Ok(())
    }
}
