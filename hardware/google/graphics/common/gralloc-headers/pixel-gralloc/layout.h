#pragma once

#include <aidl/android/hardware/graphics/common/PlaneLayoutComponentType.h>

#include <cstdint>

namespace pixel::graphics {

using aidl::android::hardware::graphics::common::PlaneLayoutComponentType;

// These are represented as bitwise in PlaneLayoutComponentType.aidl, but we
// do not treat them as such. This helps in better separation of component type
// as required.
enum class ComponentType : uint32_t {
    Y = static_cast<uint32_t>(PlaneLayoutComponentType::Y),
    CB = static_cast<uint32_t>(PlaneLayoutComponentType::CB),
    CR = static_cast<uint32_t>(PlaneLayoutComponentType::CR),
    R = static_cast<uint32_t>(PlaneLayoutComponentType::R),
    G = static_cast<uint32_t>(PlaneLayoutComponentType::G),
    B = static_cast<uint32_t>(PlaneLayoutComponentType::B),
    RAW = static_cast<uint32_t>(PlaneLayoutComponentType::RAW),
    A = static_cast<uint32_t>(PlaneLayoutComponentType::A),

    // These are not in PlaneLayoutComponentType
    D = 1 << 21,
    S,
    BLOB,
};

} // namespace pixel::graphics
