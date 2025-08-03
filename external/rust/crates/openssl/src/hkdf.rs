use crate::cvt;
use crate::error::ErrorStack;
use crate::md::MdRef;
use foreign_types::ForeignTypeRef;
use openssl_macros::corresponds;

/// Computes HKDF (as specified by RFC 5869).
///
/// HKDF is an Extract-and-Expand algorithm. It does not do any key stretching,
/// and as such, is not suited to be used alone to generate a key from a
/// password.
#[corresponds(HKDF)]
#[inline]
pub fn hkdf(
    out_key: &mut [u8],
    md: &MdRef,
    secret: &[u8],
    salt: &[u8],
    info: &[u8],
) -> Result<(), ErrorStack> {
    unsafe {
        cvt(ffi::HKDF(
            out_key.as_mut_ptr(),
            out_key.len(),
            md.as_ptr(),
            secret.as_ptr(),
            secret.len(),
            salt.as_ptr(),
            salt.len(),
            info.as_ptr(),
            info.len(),
        ))?;
    }

    Ok(())
}

/// Computes a HKDF PRK (as specified by RFC 5869).
///
/// WARNING: This function orders the inputs differently from RFC 5869
/// specification. Double-check which parameter is the secret/IKM and which is
/// the salt when using.
#[corresponds(HKDF_extract)]
#[inline]
pub fn hkdf_extract<'a>(
    out_key: &'a mut [u8],
    md: &MdRef,
    secret: &[u8],
    salt: &[u8],
) -> Result<&'a [u8], ErrorStack> {
    let mut out_len = out_key.len();
    unsafe {
        cvt(ffi::HKDF_extract(
            out_key.as_mut_ptr(),
            &mut out_len,
            md.as_ptr(),
            secret.as_ptr(),
            secret.len(),
            salt.as_ptr(),
            salt.len(),
        ))?;
    }

    Ok(&out_key[..out_len])
}

/// Computes a HKDF OKM (as specified by RFC 5869).
#[corresponds(HKDF_expand)]
#[inline]
pub fn hkdf_expand(
    out_key: &mut [u8],
    md: &MdRef,
    prk: &[u8],
    info: &[u8],
) -> Result<(), ErrorStack> {
    unsafe {
        cvt(ffi::HKDF_expand(
            out_key.as_mut_ptr(),
            out_key.len(),
            md.as_ptr(),
            prk.as_ptr(),
            prk.len(),
            info.as_ptr(),
            info.len(),
        ))?;
    }

    Ok(())
}
