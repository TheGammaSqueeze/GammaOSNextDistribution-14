# KeyMint Rust Reference Implementation

This repository holds a reference implementation of the Android
[KeyMint
HAL](https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/IKeyMintDevice.aidl?q=IKeyMintDevice.aidl),
including closely related HAL interfaces:

- [`IRemotelyProvisionedComponent`](https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/security/rkp/aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.aidl)
- [`ISharedSecret`](https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/security/sharedsecret/aidl/android/hardware/security/sharedsecret/ISharedSecret.aidl)
- [`ISecureClock`](https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/security/secureclock/aidl/android/hardware/security/secureclock/ISecureClock.aidl)

## Repository Structure

The codebase is divided into a number of interdependent crates, as follows.

- `derive/`: The `kmr-derive` crate holds [proc
  macros](https://doc.rust-lang.org/reference/procedural-macros.html) used for deriving the
  `kmr_wire::AsCborValue` trait that is used for message serialization. This crate uses `std`, but
  is only required for the build process on the host, and does not produce code that runs on the
  device.
- `wire/`: The `kmr-wire` crate holds the types that are used for communication between the
  userspace HAL service and the trusted application code that runs in the secure world, together
  with code for serializing and deserializing these types as CBOR. This crate is `no_std` but uses
  `alloc`.
- `common/`: The `kmr-common` crate holds common code used throughout the KeyMint
  implementation. This includes metadata processing code, keyblob manipulation code, and also the
  abstractions used to represent access to underlying cryptographic functionality. This crate is
  `no_std` but uses `alloc`.
- `ta/`: The `kmr-ta` crate holds the implementation of the KeyMint trusted application (TA), which
  is expected to run within the device's secure environment. This crate is `no_std` but uses
  `alloc`.
- `hal/`: The `kmr-hal` crate holds the implementation of the HAL service for KeyMint, which is
  expected to run in the Android userspace and respond to Binder method invocations. This crate uses
  `std` (as it runs within Android, not within the more restricted secure environment).
- `boringssl/`: The `kmr-crypto-boring` crate holds a BoringSSL-based implementation of the
  cryptographic abstractions from `kmr-common`. This crate is `no_std` (but using `alloc`); however,
  it relies on the Rust [`openssl` crate](https://docs.rs/openssl) for BoringSSL support, and that
  crate uses `std`.
- `tests/`: The `kmr-tests` crate holds internal testing code.

| Subdir           | Crate Name          | `std`?              | Description                                           |
|------------------|---------------------|---------------------|-------------------------------------------------------|
| **`derive`**     | `kmr-derive`        | Yes (build-only)    | Proc macros for deriving the `AsCborValue` trait      |
| **`wire`**       | `kmr-wire`          | No                  | Types for HAL <-> TA communication                    |
| **`common`**     | `kmr-common`        | No                  | Common code used throughout KeyMint/Rust              |
| **`ta`**         | `kmr-ta`            | No                  | TA implementation                                     |
| **`hal`**        | `kmr-hal`           | Yes                 | HAL service implementation                            |
| **`boringssl`**  | `kmr-crypto-boring` | Yes (via `openssl`) | Boring/OpenSSL-based implementations of crypto traits |
| `tests`          | `kmr-tests`         |                     | Tests and test infrastructure                         |

## Porting to a Device

To use the Rust reference implementation on an Android device, implementations of various
abstractions must be provided.  This section describes the different areas of functionality that are
required.

### Rust Toolchain and Heap Allocator

Using the reference implementation requires a Rust toolchain that can target the secure environment.
This toolchain (and any associated system libraries) must also support heap allocation (or an
approximation thereof) via the [`alloc` sysroot crate](https://doc.rust-lang.org/alloc/).

If the BoringSSL-based implementation of cryptographic functionality is used (see below), then some
parts of the Rust `std` library must also be provided, in order to support the compilation of the
[`openssl`](https://docs.rs/openssl) wrapper crate.

**Checklist:**

- [ ] Rust toolchain that targets secure environment.
- [ ] Heap allocation support via `alloc`.

### HAL Service

KeyMint appears as a HAL service in userspace, and so an executable that registers for and services
the KeyMint related HALs must be provided.

The implementation of this service is mostly provided by the `kmr-hal` crate, but a driver program
must be provided that:

- Performs start-of-day administration (e.g. logging setup, panic handler setup)
- Creates a communication channel to the KeyMint TA.
- Registers for the KeyMint HAL services.
- Starts a thread pool to service requests.

The KeyMint HAL service (which runs in userspace) must communicate with the KeyMint TA (which runs
in the secure environment).  The reference implementation assumes the existence of a reliable,
message-oriented, bi-directional communication channel for this, as encapsulated in the
`kmr_hal::SerializedChannel` trait.

This trait has a single method `execute()`, which takes as input a request message (as bytes), and
returns a response message (as bytes) or an error.

A (shared) instance of this trait must be provided to each of the `kmr_hal::<interface>::Device`
types, which allows them to service Binder requests for the relevant interface by forwarding the
requests to the TA as request/response pairs.

**Checklist:**

- [ ] Implementation of HAL service, which registers for all HAL services.
- [ ] SELinux policy for the HAL service.
- [ ] init.rc configuration for the HAL service.
- [ ] Implementation of `SerializedChannel` trait, for reliable HAL <-> TA communication.
- [ ] Populate userspace environment information at start of day, using `kmr_hal::send_hal_info()`.

The Cuttlefish implementation of the [KeyMint/Rust HAL
service](https://cs.android.com/android/platform/superproject/+/master:device/google/cuttlefish/guest/hals/keymint/rust/src/keymint_hal_main.rs)
provides an example of all of the above.

### TA Driver

The `kmr-ta` crate provides the majority of the implementation of the KeyMint TA, but needs a driver
program that:

- Performs start-of-day administration (e.g. logging setup).
- Populates initially required information (e.g. `kmr_ta::HardwareInfo`)
- Creates a `kmr_ta::KeyMintTa` instance.
- Configures the communication channel with the HAL service.
- Configures the communication channel with the bootloader, which is required so that the current
  root-of-trust boot information can be received.
- Holds the main loop that:
    - reads request messages from the channel(s)
    - passes request messages to `kmr_ta::KeyMintTa::process()`, receiving a response
    - writes response messages back to the relevant channel.

**Checklist:**

- [ ] Implementation of `main` equivalent for TA, handling scheduling of incoming requests.
- [ ] Implementation of communication channel between HAL service and TA.
- [ ] Implementation of communication channel from bootloader to TA.
    - [ ] Trigger call to `kmr_ta::KeyMintTa::set_boot_info` on receipt of boot info.

The Cuttlefish implementation of the [KeyMint/Rust
TA](https://cs.android.com/android/platform/superproject/+/master:device/google/cuttlefish/host/commands/secure_env_rust/secure_env.rs)
provides an example of all of the above.

### Bootloader

The bootloader is required to transmit root of trust and boot state information to the TA at start
of day, so the TA can bind keys to the root of trust appropriately.  The bootloader should fill out
and send a `kmr_wire::SetBootInfoRequest` message to do this.

**Checklist:**

- [ ] Implementation of communication channel from bootloader to TA.
- [ ] Trigger for and population of `kmr_wire::SetBootInfoRequest` message.

### Cryptographic Abstractions

The KeyMint TA requires implementations for low-level cryptographic primitives to be provided, in
the form of implementations of the various Rust traits held in
[`kmr_common::crypto`](common/src/crypto/traits.rs).

Note that some of these traits include methods that have default implementations, which means that
an external implementation is not required (but can be provided if desired).

**Checklist:**

- [ ] RNG implementation.
- [ ] Constant time comparison implementation.
- [ ] AES implementation.
- [ ] 3-DES implementation.
- [ ] HMAC implementation.
- [ ] RSA implementation.
- [ ] EC implementation (including curve 25519 support).
- [ ] AES-CMAC or CKDF implementation.
- [ ] Secure time implementation.

BoringSSL-based implementations are available for all of the above (except for secure time).

### Device Abstractions

The KeyMint TA requires implementations of traits that involve interaction with device-specific
features or provisioned information, in the form of implementations of the various Rust traits held
in [`kmr_hal::device`](hal/src/device.rs).

**Checklist:**

- [ ] Root key retrieval implementation.
- [ ] Attestation key / chain retrieval implementation.
- [ ] Attestation device ID retrieval implementation.
- [ ] Retrieval of BCC and DICE artefacts.
- [ ] Secure storage implementation (optional).
- [ ] Bootloader status retrieval (optional)
- [ ] Storage key wrapping integration (optional).
- [ ] Trusted user presence indication (optional).
- [ ] Legacy keyblob format converter (optional).

## Supporting Older Versions of the KeyMint HAL

The reference implementation has the ability to behave like an earlier version of the KeyMint
HAL. To enable emulation of (say) KeyMint v1, link the HAL service against the `libkmr_hal_v1` and
`libkmr_wire_hal_v1` targets rather than `libkmr_hal` / `libkmr_wire`.
