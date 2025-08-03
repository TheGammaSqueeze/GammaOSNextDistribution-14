//! This library provides array utils.

/// Converts a vector of bytes to a fixed sized array.
/// If the vector is longer it will be truncated and if shorter
/// zero padding will be added at the end.
pub fn to_sized_array<const S: usize>(v: &Vec<u8>) -> [u8; S] {
    // Okay to do naked unwrap since we enforce at compile time that
    // the iter length is the same as the destination array length.
    v.iter().chain(std::iter::repeat(&0)).take(S).cloned().collect::<Vec<u8>>().try_into().unwrap()
}
