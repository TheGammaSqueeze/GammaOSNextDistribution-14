# AVB resource manager

The AVB ([Android Verified Boot](https://android.googlesource.com/platform/external/avb))
resource manager is intended to provide tamper proof storage for data used by
[libavb](https://android.googlesource.com/platform/external/avb/+/master#Device-Integration).
This includes the verified boot lock state, stored rollback index values, and ATX
([Android Things eXtension](https://android.googlesource.com/platform/external/avb/+/master/libavb_atx/))
permanent attributes.

## Operations

### Reading/Writing Stored Rollback Indexes

Rollback indexes are strictly increasing, and any request to write a value to a
rollback index that is smaller than the existing value will fail. A mask
(0xF000) is used to map a rollback index to a file, and a file may contain a
maximum of 32 rollback indexes. For example, 0xF01F and 0x0001 are valid values
for the rollback index, but 0x10000 and 0x0020 are not.

### Reading/Writing Verified Boot Lock State

If the lock state is 1, or LOCKED, then verification errors are fatal, and
booting MUST fail. If the lock state is 0, or UNLOCKED, the device may boot
even when verification fails. When the device changes lock state, all stored
rollback indexes are cleared.

### Reading/Writing ATX Permanent Attributes

A hash of the
[attributes](https://android.googlesource.com/platform/external/avb/+/master/libavb_atx/avb_atx_types.h)
MUST be stored in write-once fuses. Once this is written, any subsequent
requests to write it will fail. Attributes are stored as an opaque buffer and
parsed by the bootloader.

### Locking Boot State

Once the AVB resource manager receives a LOCK_BOOT_STATE request, all requests
to write to resources will fail until the next reboot. This should be called
after libavb has acquired all necessary resources, and before the bootloader
passes control to the HLOS. This prevents a compromised HLOS from tampering
with AVB resources.

## Client Code

Since libavb is executed by the bootloader, the non-secure side API that
makes requests to the AVB resource manager is located
[here](https://android.googlesource.com/trusty/external/trusty/+/master/ql-tipc/).
