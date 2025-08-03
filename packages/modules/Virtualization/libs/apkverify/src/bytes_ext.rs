/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Provides extension methods Bytes::read<T>(), which calls back ReadFromBytes::read_from_byte()

use anyhow::{ensure, Result};
use bytes::{Buf, Bytes};
use std::ops::Deref;

#[derive(Clone, Debug)]
pub struct LengthPrefixed<T> {
    inner: T,
}

impl<T> Deref for LengthPrefixed<T> {
    type Target = T;
    fn deref(&self) -> &Self::Target {
        &self.inner
    }
}

impl<T> LengthPrefixed<T> {
    /// Consumes the `LengthPrefixed` instance, returning the wrapped value.
    pub fn into_inner(self) -> T {
        self.inner
    }
}

pub trait BytesExt {
    fn read<T: ReadFromBytes>(&mut self) -> Result<T>;
}

impl BytesExt for Bytes {
    fn read<T: ReadFromBytes>(&mut self) -> Result<T> {
        T::read_from_bytes(self)
    }
}

pub trait ReadFromBytes {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self>
    where
        Self: Sized;
}

impl ReadFromBytes for u32 {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(buf.get_u32_le())
    }
}

impl<T: ReadFromBytes> ReadFromBytes for Vec<T> {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        let mut result = vec![];
        while buf.has_remaining() {
            result.push(buf.read()?);
        }
        Ok(result)
    }
}

impl<T: ReadFromBytes> ReadFromBytes for LengthPrefixed<T> {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        let mut inner = read_length_prefixed_slice(buf)?;
        let inner = inner.read()?;
        Ok(LengthPrefixed { inner })
    }
}

impl ReadFromBytes for Bytes {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(buf.slice(..))
    }
}

fn read_length_prefixed_slice(buf: &mut Bytes) -> Result<Bytes> {
    ensure!(
        buf.remaining() >= 4,
        "Remaining buffer too short to contain length of length-prefixed field. Remaining: {}",
        buf.remaining()
    );
    let len = buf.get_u32_le() as usize;
    ensure!(
        buf.remaining() >= len,
        "length-prefixed field longer than remaining buffer. Field length: {}, remaining: {}",
        len,
        buf.remaining()
    );
    Ok(buf.split_to(len))
}

#[cfg(test)]
mod tests {
    use super::*;
    use bytes::{BufMut, BytesMut};

    #[test]
    fn test_read_length_prefixed_slice() {
        let data = b"hello world";
        let mut b = BytesMut::new();
        b.put_u32_le(data.len() as u32);
        b.put_slice(data);
        let mut slice = b.freeze();
        let res = read_length_prefixed_slice(&mut slice);
        assert!(res.is_ok());
        assert_eq!(data, res.ok().unwrap().as_ref());
    }
}
