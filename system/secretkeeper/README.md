# Secretkeeper

Secretkeeper provides secure storage of secrets on behalf of other components in Android.
It is specified as [a HAL][secretkeeperhal] and must be implemented in an environment with
privilege higher than any of its clients. Typically this will be a trusted execution environment
such as ARM TrustZone.

The core SecretManagement API is a [CBOR based protocol][secretmanagement_cddl] and can be used to
store (& get) 32 bytes of secret data. Secretkeeper supports establishing a secure channel with
clients as well as deletion of some or all data.

## AuthGraph key exchange

The requests (from the client) & responses (from Secretkeeper) must be encrypted using symmetric
keys agreed between the client & service. For this, Secretkeeper (& client) must implement the
[AuthGraph key exchange protocol][authgraphke] to establish a secure channel between them.

In the key exchange protocol, the client acts as P1 (source) and Secretkeeper as P2 (sink). The
interface returned by [getAuthGraphKe()][getauthgraphke] can be used to invoke methods on the sink.

## Policy Gated Storage

The storage layer of Secretkeeper, in addition to conventional storage, provides DICE
policy based access control. A client can restrict the access to its stored entry.

### Storage

The underlying storage of Secretkeeper should offer the following security guarantees:

1. Confidentiality: No entity (of security privilege lower than Secretkeeper) should be able to get
   a client's data in the clear.
2. Integrity: The data is protected against malicious Android OS tampering with database. i.e., if
   Android (userspace & kernel) tampers with the client's secret, the Secretkeeper service must be
   able to detect it & return an error when clients requests for their secrets.
   **The integrity requirements also include rollback protection i.e., reverting the database
   into an old state should be detected.**
3. Persistence: The data is persistent across device boot.
   Note: Denial of service is not in scope. A malicious Android may be able to delete data.
   In ideal situation, the data should be persistent.

### Access control

Secretkeeper uses [DICE policy][DicePolicyCDDL] based access control. Each secret is associated
with a sealing policy, which is a DICE policy. This is a required input while storing a secret.
Further access to this secret is restricted to clients whose DICE chain adheres to the
corresponding sealing policy.

## Reference Implementation

Android provides a reference implementation of Secretkeeper as well as the required AuthGraph Key
exchange HAL. The implementation is modular and easily configurable. For example,
partners can plug in their implementation of AES-GCM, RNG instead of using the BoringSSL
implementations.

Navigating this project:

1. [./core/][sk_core_dir]: Contains the reference implementation of Secretkeeper TA.
2. [./hal/][sk_hal_dir]: Contains the reference implementation of Secretkeeper HAL.
3. [./client/][sk_client_dir]: A client library for Secretkeeper, which can be used for managing
   sessions with Secretkeeper, sending requests etc.
4. [./comm/][sk_comm_dir]: Secretkeeper is a CBOR heavy protocol, the Rust definition
   of Requests/Response/Arguments/Errors is contained in this directory.
   Additionally, Rust types for supporting the CBOR based communication between the TA and HAL is
   also exported here. This is used by secretkeeper_core, secretkeeper_hal and secretkeeper_client.
5. [./dice_policy/][dice_policy_dir]: Contains code for building dice_policies as well
   as well as matching them against Dice chain. As explained [here](#Policy-Gated-Storage), this
   forms the foundation of Policy Gated Storage in Secretkeeper.

Outside this directory:

1. Secretkeeper HAL Spec: [hardware/interfaces/security/secretkeeper/aidl/android/hardware/security/secretkeeper/ISecretkeeper.aidl][secretkeeperhal]
2. [Vendor Test Suite for Secretkeeper][secretkeeper_vts]
3. [HAL Integration for Trusty][sk_hal_trusty]
4. [TA Integration for Trusty][sk_ta_trusty]
5. [Command line test tool for interacting with Secretkeeper][secretkeeper_cli]
6. [AuthGraph HAL Spec & Test Suite][authgraphhal_dir]
7. AuthGraph Reference implementation: [system/authgraph/][authgraph_ref]

[sk_core_dir]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/core/
[sk_hal_dir]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/hal/
[sk_client_dir]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/client/
[sk_comm_dir]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/comm/
[dice_policy_dir]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/dice_policy/
[secretkeeper_vts]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/secretkeeper/aidl/vts/secretkeeper_test_client.rs
[sk_hal_trusty]: https://cs.android.com/android/platform/superproject/main/+/main:system/core/trusty/secretkeeper/src/hal_main.rs
[sk_ta_trusty]: https://android.googlesource.com/trusty/app/secretkeeper/+/refs/heads/master/lib.rs
[secretkeeper_cli]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/secretkeeper/aidl/vts/secretkeeper_cli.rs
[authgraphhal_dir]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/authgraph/aidl/
[authgraph_ref]: https://cs.android.com/android/platform/superproject/main/+/main:system/authgraph/

### Porting to a Device

To use the Rust reference implementation on an Android device, device-specific implementations of
various abstractions must be provided.  This section describes the different areas of functionality
that are required.

#### Rust Toolchain and Heap Allocator

Using the reference implementation requires a Rust toolchain that can target the secure environment.
This toolchain (and any associated system libraries) must also support heap allocation (or an
approximation thereof) via the [`alloc` sysroot crate](https://doc.rust-lang.org/alloc/).

If the BoringSSL-based implementation of cryptographic functionality is used (see below), then some
parts of the Rust `std` library must also be provided, in order to support the compilation of the
[`openssl`](https://docs.rs/openssl) wrapper crate.

**Checklist:**

- [ ] Rust toolchain that targets secure environment.
- [ ] Heap allocation support via `alloc`.

#### HAL Service

Secretkeeper appears as a HAL service in userspace, and so an executable that registers for and
services the Secretkeeper HAL must be provided.

The implementation of this service is mostly provided by the [`secretkeeper_hal` crate][sk_hal_dir]
(and by the associated [`authgraph_hal`
crate](https://cs.android.com/android/platform/superproject/main/+/main:system/authgraph/hal/)),
but a driver program must be provided that:

- Performs start-of-day administration (e.g. logging setup, panic handler setup).
- Creates communication channels to the Secretkeeper TA.
- Registers for the Secretkeeper HAL service.
- Starts a thread pool to service requests.

The Secretkeeper HAL service (which runs in userspace) must communicate with the Secretkeeper TA
(which runs in the secure environment).  The reference implementation assumes the existence of two
reliable, message-oriented, bi-directional communication channels for this (one for Secretkeeper,
one for AuthGraph), as encapsulated in the `authgraph_hal::channel::SerializedChannel` trait.

This trait has a single method `execute()`, which takes as input a request message (as bytes), and
returns a response message (as bytes) or an error.

Two instances of this trait must be provided to the `secretkeeper_hal::SecretkeeperService` type,
which allows it to service Binder requests by forwarding the requests to the TA as request/response
pairs.

**Checklist:**

- [ ] Implementation of HAL service, which registers the Secretkeeper HAL service with Binder.
- [ ] SELinux policy for the HAL service.
- [ ] `init.rc` configuration for the HAL service.
- [ ] Implementation of `SerializedChannel` trait, for reliable HAL <-> TA communication.

The [Trusty implementation of the Secretkeeper
HAL](https://cs.android.com/android/platform/superproject/+/main:system/core/trusty/secretkeeper/src/hal_main.rs)
provides an example of all of the above.

#### TA Driver

The `secretkeeper_core::ta` module provides the majority of the implementation of the Secretkeeper
TA, but needs a driver program that:

- Performs start-of-day administration (e.g. logging setup).
- Creates an `authgraph_core::ta::AuthGraphTa` instance.
- Creates a `secretkeeper_core_ta::SecretkeeperTa` instance.
- Configures the pair of communication channels that communicate with the HAL service (one for
  Secretkeeper, one for AuthGraph).
- Configures the communication channel with the bootloader, which is required so that the current
  Secretkeeper identity information can be retrieved at start-of-day.
- Holds the main loop that:
    - reads request messages from the channel(s)
    - passes request messages to `SecretkeeperTa::process()` or `AuthGraphTa::process()`, receiving
      a response
    - writes response messages back to the relevant channel.

**Checklist:**

- [ ] Implementation of `main` equivalent for TA, handling scheduling of incoming requests.
- [ ] Implementation of communication channels between HAL service and TA.
- [ ] Implementation of communication channel between bootloader and TA.

The [Trusty implementation of the Secretkeeper
TA](https://android.googlesource.com/trusty/app/secretkeeper/+/refs/heads/main/lib.rs)
provides an example of all of the above.

#### Bootloader

If Secretkeeper is used to store secrets on behalf of protected virtual machines (pVMs), then the
bootloader is required to retrieve the identity of Secretkeeper (expressed as a public key) at boot
time so that the identity can be (securely) provided to pVM instances, as described
[below](#secretkeeper-public-key).  The bootloader should use the
[`secretkeeper_core::ta::bootloader::GetIdentityKey`
message](https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/core/src/ta/bootloader.rs?q=GetIdentityKey)
to do this.

**Checklist:**

- [ ] Implementation of communication channel from bootloader to TA.
- [ ] Send `GetIdentityKey` request at boot time.
- [ ] Populate the relevant device tree property.

#### Cryptographic Abstractions

The Secretkeeper TA requires implementations for low-level cryptographic primitives to be provided,
in the form of implementations of the various Rust traits held in
[`authgraph_core::traits`](https://cs.android.com/android/platform/superproject/main/+/main:system/authgraph/core/src/traits.rs).

Note that some of these traits include methods that have default implementations, which means that
an external implementation is not required (but can be provided if desired).

**Checklist:**

- [ ] RNG implementation.
- [ ] AES-GCM implementation.
- [ ] HMAC implementation.
- [ ] ECDH implementation with P-256.
- [ ] ECDSA implementation (P-256, P-384, Ed25519).

BoringSSL-based implementations are
[available](https://cs.android.com/android/platform/superproject/main/+/main:system/authgraph/boringssl/src/lib.rs)
for all of the above.

#### Device-Specific Abstractions

The Secretkeeper requires an implementation of the [`secretkeeper_core::store::KeyValueStore`
trait](https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/core/src/store.rs?q=%22trait%20KeyValueStore%22)
that abstracts away access to secure storage.

The Trusty implementation of the Secretkeeper TA includes an [example
implementation](https://android.googlesource.com/trusty/app/secretkeeper/+/refs/heads/main/store.rs).

**Checklist:**

- [ ] `KeyValueStore` implementation.

## Example usage: Rollback protected secrets of Microdroid based pVMs

Microdroid instances use Secretkeeper to store their secrets while protecting against the Rollback
attacks on the boot images & packages. Such secrets (and data protected by the secrets) are
accessible on updates but not on downgrades of boot images and apks.

[authgraphke]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/IAuthGraphKeyExchange.aidl
[getauthgraphke]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/secretkeeper/aidl/android/hardware/security/secretkeeper/ISecretkeeper.aidl?q=getAuthGraphKe
[secretkeeperhal]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/secretkeeper/aidl/android/hardware/security/secretkeeper/ISecretkeeper.aidl
[secretmanagement_cddl]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/secretkeeper/aidl/android/hardware/security/secretkeeper/SecretManagement.cddl
[DicePolicyCDDL]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/authgraph/aidl/android/hardware/security/authgraph/DicePolicy.cddl

### Secretkeeper public key

As described [above](#authgraph-key-exchange), Microdroid as a Secretkeeper
client establishes a secure channel with the Secretkeeper implementation using
the AuthGraph key exchange protocol. As part of this Microdroid needs to verify
that it is communicating with the real Secretkeeper.

To achieve this the Secretkeeper implementation should generate a per-boot key
pair and use that as its identity in the AuthGraph protocol. The public key from
the pair then needs to be securely communicated to the Microdroid VM which uses
it to verify the identity of Secretkeeper.

The public key is transported as a CBOR-encoded COSE_key, as a PubKeyEd25519 /
PubKeyECDSA256 / PubKeyECDSA384 as defined in
[generateCertificateRequestV2.cddl][pubkeycddl].

Microdroid expects the public key to be present in the Linux device tree as the
value of the `secretkeeper_public_key` property of the `/avf` node - exposed to
userspace at `/proc/device-tree/avf/secretkeeper_public_key`.

When a protected VM is started, AVF populates this property in the VM DT `/avf`
node from the corresponding property in the `/avf/reference/avf` node in the
host DT. pvmfw verifies that the value is correct using the VM reference DT that
is included in the pvmfw [configuration data][pvmfwconfig].

The [Android bootloader][androidbootloader] should request the public key from
the Secretkeeper implementation at boot time and populate it in both the host
Android DT and the VM Reference DT for pvmfw.

The reference code for Secretkeeper defines a protocol that can be used by the
bootloader to retrieve the public key; see
[core/src/ta/bootloader.rs][skbootloader].

[pubkeycddl]: https://cs.android.com/android/platform/superproject/main/+/main:hardware/interfaces/security/rkp/aidl/android/hardware/security/keymint/generateCertificateRequestV2.cddl;l=143
[pvmfwconfig]: https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/heads/main/pvmfw/README.md#configuration-data-format
[androidbootloader]: https://source.android.com/docs/core/architecture/bootloader
[skbootloader]: https://cs.android.com/android/platform/superproject/main/+/main:system/secretkeeper/core/src/ta/bootloader.rs
