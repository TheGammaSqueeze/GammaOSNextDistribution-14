//! Handling for data represented as CBOR. Cryptographic objects are encoded following COSE.

mod dice;
mod field_value;
mod publickey;
pub(crate) mod rkp;

use ciborium::{de::from_reader, value::Value};
use std::io::Read;

type CiboriumError = ciborium::de::Error<std::io::Error>;

/// Decodes the provided binary CBOR-encoded value and returns a
/// ciborium::Value struct wrapped in Result.
fn value_from_bytes(mut bytes: &[u8]) -> Result<Value, CiboriumError> {
    let value = from_reader(bytes.by_ref())?;
    // Ciborium tries to read one Value, but doesn't care if there is trailing data. We do.
    if !bytes.is_empty() {
        return Err(CiboriumError::Semantic(Some(0), "unexpected trailing data".to_string()));
    }
    Ok(value)
}

#[cfg(test)]
fn serialize(value: Value) -> Vec<u8> {
    let mut data = Vec::new();
    ciborium::ser::into_writer(&value, &mut data).unwrap();
    data
}

#[cfg(test)]
mod tests {
    use super::*;
    use anyhow::Result;

    #[test]
    fn value_from_bytes_valid_succeeds() -> Result<()> {
        let bytes = [0x82, 0x04, 0x02]; // [4, 2]
        let val = value_from_bytes(&bytes)?;
        let array = val.as_array().unwrap();
        assert_eq!(array.len(), 2);
        Ok(())
    }

    #[test]
    fn value_from_bytes_truncated_fails() {
        let bytes = [0x82, 0x04];
        assert!(value_from_bytes(&bytes).is_err());
    }

    #[test]
    fn value_from_bytes_trailing_bytes_fails() {
        let bytes = [0x82, 0x04, 0x02, 0x00];
        assert!(value_from_bytes(&bytes).is_err());
    }
}
