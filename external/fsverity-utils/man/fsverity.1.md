% FSVERITY(1) fsverity-utils v1.5 | User Commands
%
% February 2022

# NAME

fsverity - userspace utility for fs-verity

# SYNOPSIS
**fsverity digest** [*OPTION*...] *FILE*... \
**fsverity dump_metadata** [*OPTION*...] *TYPE* *FILE* \
**fsverity enable** [*OPTION*...] *FILE* \
**fsverity measure** *FILE*... \
**fsverity sign** [*OPTION*...] *FILE* *OUT_SIGFILE*

# DESCRIPTION

**fsverity** is a userspace utility for fs-verity.  fs-verity is a Linux kernel
filesystem feature that does transparent on-demand verification of the contents
of read-only files using Merkle trees.

**fsverity** can enable fs-verity on files, retrieve the digests of fs-verity
files, and sign files for use with fs-verity (among other things).
**fsverity**'s functionality is divided among various subcommands.

This manual page focuses on documenting all **fsverity** subcommands and
options.  For examples and more information about the fs-verity kernel feature,
see the references at the end of this page.

# OPTIONS

**fsverity** always accepts the following options:

**\-\-help**
:   Show the help, for either one subcommand or for all subcommands.

**\-\-version**
:   Show the version of fsverity-utils.

# SUBCOMMANDS

## **fsverity digest** [*OPTION*...] *FILE*...

Compute the fs-verity digest of the given file(s).  This is mainly intended to
used in preparation for signing the digest.  In some cases **fsverity sign**
can be used instead to digest and sign the file in one step.

Options accepted by **fsverity digest**:

**\-\-block-size**=*BLOCK_SIZE*
:   The Merkle tree block size (in bytes) to use.  This must be a power of 2 and
    at least twice the size of the hash values.  However, note that currently
    (as of Linux kernel v5.13), the Linux kernel implementations of fs-verity
    only support the case where the Merkle tree block size is equal to the
    system page size, usually 4096 bytes.  The default value of this option is
    4096.

**\-\-compact**
:   When printing the file digest, only print the actual digest hex string;
    don't print the algorithm name and filename.

**\-\-for-builtin-sig**
:   Format the file digest in a way that is compatible with the Linux kernel's
    fs-verity built-in signature verification support.  This means formatting it
    as a `struct fsverity_formatted_digest`.  Use this option if you are using
    built-in signatures but are not using **fsverity sign** to do the signing.

**\-\-hash-alg**=*HASH_ALG*
:   The hash algorithm to use to build the Merkle tree.  Valid options are
    sha256 and sha512.  Default is sha256.

**\-\-out-merkle-tree**=*FILE*
:   Write the computed Merkle tree to the given file.  The Merkle tree layout
    will be the same as that used by the Linux kernel's
    `FS_IOC_READ_VERITY_METADATA` ioctl.

    Normally this option isn't useful, but it can be needed in cases where the
    fs-verity metadata needs to be consumed by something other than one of the
    native Linux kernel implementations of fs-verity.  This is not needed for
    file signing.

**\-\-out-descriptor**=*FILE*
:   Write the computed fs-verity descriptor to the given file.

    Normally this option isn't useful, but it can be needed in cases where the
    fs-verity metadata needs to be consumed by something other than one of the
    native Linux kernel implementations of fs-verity.  This is not needed for
    file signing.

**\-\-salt**=*SALT*
:   The salt to use in the Merkle tree, as a hex string.  The salt is a value
    that is prepended to every hashed block; it can be used to personalize the
    hashing for a particular file or device.  The default is no salt.

## **fsverity dump_metadata** [*OPTION*...] *TYPE* *FILE*

Dump the fs-verity metadata of the given file.  The file must have fs-verity
enabled, and the filesystem must support the `FS_IOC_READ_VERITY_METADATA` ioctl
(it was added in Linux v5.12).  This subcommand normally isn't useful, but it
can be useful in cases where a userspace server program is serving a verity file
to a client which implements fs-verity compatible verification.

*TYPE* may be "merkle\_tree", "descriptor", or "signature", indicating the type
of metadata to dump.  "signature" refers to the built-in signature, if present;
userspace-managed signatures will not be included.

Options accepted by **fsverity dump_metadata**:

**\-\-length**=*LENGTH*
:   Length in bytes to dump from the specified metadata item.  Only accepted in
    combination with **\-\-offset**.

**\-\-offset**=*offset*
:   Offset in bytes into the specified metadata item at which to start dumping.
    Only accepted in combination with **\-\-length**.

## **fsverity enable** [*OPTION*...] *FILE*

Enable fs-verity on the specified file.  This will only work if the filesystem
supports fs-verity.

Options accepted by **fsverity enable**:

**\-\-block-size**=*BLOCK_SIZE*
:   Same as for **fsverity digest**.

**\-\-hash-alg**=*HASH_ALG*
:   Same as for **fsverity digest**.

**\-\-salt**=*SALT*
:   Same as for **fsverity digest**.

**\-\-signature**=*SIGFILE*
:   Specifies the built-in signature to apply to the file.  *SIGFILE* must be a
    file that contains the signature in PKCS#7 DER format, e.g. as produced by
    the **fsverity sign** command.

    Note that this option is only needed if the Linux kernel's fs-verity
    built-in signature verification support is being used.  It is not needed if
    the signatures will be verified in userspace, as in that case the signatures
    should be stored separately.

## **fsverity measure** *FILE*...

Display the fs-verity digest of the given file(s).  The files must have
fs-verity enabled.  The output will be the same as **fsverity digest** with
the appropriate parameters, but **fsverity measure** will take constant time
for each file regardless of the size of the file.

**fsverity measure** does not accept any options.

## **fsverity sign** [*OPTION*...] *FILE* *OUT_SIGFILE*

Sign the given file for fs-verity, in a way that is compatible with the Linux
kernel's fs-verity built-in signature verification support.  The signature will
be written to *OUT_SIGFILE* in PKCS#7 DER format.

The private key can be specified either by key file or by PKCS#11 token.  To use
a key file, provide **\-\-key** and optionally **\-\-cert**.  To use a PKCS#11
token, provide **\-\-pkcs11-engine**, **\-\-pkcs11-module**, **\-\-cert**, and
optionally **\-\-pkcs11-keyid**.  PKCS#11 token support is unavailable when
fsverity-utils was built with BoringSSL rather than OpenSSL.

**fsverity sign** should only be used if you need compatibility with fs-verity
built-in signatures.  It is not the only way to do signatures with fs-verity.
For more information, see the fsverity-utils README.

Options accepted by **fsverity sign**:

**\-\-block-size**=*BLOCK_SIZE*
:   Same as for **fsverity digest**.

**\-\-cert**=*CERTFILE*
:   Specifies the file that contains the certificate, in PEM format.  This
    option is required if *KEYFILE* contains only the private key and not also
    the certificate, or if a PKCS#11 token is used.

**\-\-hash-alg**=*HASH_ALG*
:   Same as for **fsverity digest**.

**\-\-key**=*KEYFILE*
:   Specifies the file that contains the private key, in PEM format.  This
    option is required when not using a PKCS#11 token.

**\-\-out-descriptor**=*FILE*
:   Same as for **fsverity digest**.

**\-\-out-merkle-tree**=*FILE*
:   Same as for **fsverity digest**.

**\-\-pkcs11-engine**=*SOFILE*
:   Specifies the path to the OpenSSL PKCS#11 engine file.  This typically will
    be a path to the libp11 .so file.  This option is required when using a
    PKCS#11 token.

**\-\-pkcs11-keyid**=*KEYID*
:   Specifies the key identifier in the form of a PKCS#11 URI.  If not provided,
    the default key associated with the token is used.  This option is only
    applicable when using a PKCS#11 token.

**\-\-pkcs11-module**=*SOFILE*
:   Specifies the path to the PKCS#11 token-specific module library.  This
    option is required when using a PKCS#11 token.

**\-\-salt**=*SALT*
:   Same as for **fsverity digest**.

# SEE ALSO

For example commands and more information, see the
[README file for
fsverity-utils](https://git.kernel.org/pub/scm/linux/kernel/git/ebiggers/fsverity-utils.git/tree/README.md).

Also see the [kernel documentation for
fs-verity](https://www.kernel.org/doc/html/latest/filesystems/fsverity.html).
