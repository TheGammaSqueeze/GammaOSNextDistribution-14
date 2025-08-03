#pragma once

#include <aidl/android/hardware/graphics/allocator/AllocationResult.h>
#include <aidl/android/hardware/graphics/allocator/BnAllocator.h>
#include <aidlcommonsupport/NativeHandle.h>

#include <cstdint>
#include <vector>

namespace pixel {
namespace allocator {

namespace AidlAllocator = aidl::android::hardware::graphics::allocator;

class GrallocAllocator : public AidlAllocator::BnAllocator {
public:
    GrallocAllocator();

    ~GrallocAllocator();

    virtual ndk::ScopedAStatus allocate(const std::vector<uint8_t>& descriptor, int32_t count,
                                        AidlAllocator::AllocationResult* result) override;

    virtual binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;
};

} // namespace allocator
} // namespace pixel
