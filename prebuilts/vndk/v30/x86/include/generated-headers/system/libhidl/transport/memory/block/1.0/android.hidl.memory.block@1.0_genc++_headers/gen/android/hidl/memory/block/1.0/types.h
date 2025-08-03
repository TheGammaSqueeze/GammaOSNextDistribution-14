#ifndef HIDL_GENERATED_ANDROID_HIDL_MEMORY_BLOCK_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HIDL_MEMORY_BLOCK_V1_0_TYPES_H

#include <android/hidl/memory/token/1.0/IMemoryToken.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hidl {
namespace memory {
namespace block {
namespace V1_0 {

// Forward declaration for forward reference support:
struct MemoryBlock;

struct MemoryBlock final {
    ::android::sp<::android::hidl::memory::token::V1_0::IMemoryToken> token __attribute__ ((aligned(8)));
    uint64_t size __attribute__ ((aligned(8)));
    uint64_t offset __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hidl::memory::block::V1_0::MemoryBlock, token) == 0, "wrong offset");
static_assert(offsetof(::android::hidl::memory::block::V1_0::MemoryBlock, size) == 8, "wrong offset");
static_assert(offsetof(::android::hidl::memory::block::V1_0::MemoryBlock, offset) == 16, "wrong offset");
static_assert(sizeof(::android::hidl::memory::block::V1_0::MemoryBlock) == 24, "wrong size");
static_assert(__alignof(::android::hidl::memory::block::V1_0::MemoryBlock) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hidl::memory::block::V1_0::MemoryBlock& o);
static inline void PrintTo(const ::android::hidl::memory::block::V1_0::MemoryBlock& o, ::std::ostream*);
// operator== and operator!= are not generated for MemoryBlock

//
// type header definitions for package
//

static inline std::string toString(const ::android::hidl::memory::block::V1_0::MemoryBlock& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".token = ";
    os += ::android::hidl::memory::token::V1_0::toString(o.token);
    os += ", .size = ";
    os += ::android::hardware::toString(o.size);
    os += ", .offset = ";
    os += ::android::hardware::toString(o.offset);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hidl::memory::block::V1_0::MemoryBlock& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for MemoryBlock


}  // namespace V1_0
}  // namespace block
}  // namespace memory
}  // namespace hidl
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HIDL_MEMORY_BLOCK_V1_0_TYPES_H
