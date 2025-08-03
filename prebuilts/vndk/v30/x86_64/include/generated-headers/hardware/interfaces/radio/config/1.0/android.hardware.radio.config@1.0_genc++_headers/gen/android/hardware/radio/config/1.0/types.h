#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_TYPES_H

#include <android/hardware/radio/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class SlotState : int32_t;
struct SimSlotStatus;

enum class SlotState : int32_t {
    /**
     * Physical slot is inactive
     */
    INACTIVE = 0 /* 0x00 */,
    /**
     * Physical slot is active
     */
    ACTIVE = 1 /* 0x01 */,
};

struct SimSlotStatus final {
    /**
     * Card state in the physical slot
     */
    ::android::hardware::radio::V1_0::CardState cardState __attribute__ ((aligned(4)));
    /**
     * Slot state Active/Inactive
     */
    ::android::hardware::radio::config::V1_0::SlotState slotState __attribute__ ((aligned(4)));
    /**
     * An Answer To Reset (ATR) is a message output by a Smart Card conforming to ISO/IEC 7816
     * standards, following electrical reset of the card's chip. The ATR conveys information about
     * the communication parameters proposed by the card, and the card's nature and state.
     *
     * This data is applicable only when cardState is CardState:PRESENT.
     */
    ::android::hardware::hidl_string atr __attribute__ ((aligned(8)));
    uint32_t logicalSlotId __attribute__ ((aligned(4)));
    /**
     * Integrated Circuit Card IDentifier (ICCID) is Unique Identifier of the SIM CARD. File is
     * located in the SIM card at EFiccid (0x2FE2) as per ETSI 102.221. The ICCID is defined by
     * the ITU-T recommendation E.118 ISO/IEC 7816.
     *
     * This data is applicable only when cardState is CardState:PRESENT.
     */
    ::android::hardware::hidl_string iccid __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::config::V1_0::SimSlotStatus, cardState) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_0::SimSlotStatus, slotState) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_0::SimSlotStatus, atr) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_0::SimSlotStatus, logicalSlotId) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::radio::config::V1_0::SimSlotStatus, iccid) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::radio::config::V1_0::SimSlotStatus) == 48, "wrong size");
static_assert(__alignof(::android::hardware::radio::config::V1_0::SimSlotStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::config::V1_0::SlotState o);
static inline void PrintTo(::android::hardware::radio::config::V1_0::SlotState o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::config::V1_0::SlotState lhs, const ::android::hardware::radio::config::V1_0::SlotState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::config::V1_0::SlotState rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::config::V1_0::SlotState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::config::V1_0::SlotState lhs, const ::android::hardware::radio::config::V1_0::SlotState rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::config::V1_0::SlotState rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::config::V1_0::SlotState lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::config::V1_0::SlotState e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::config::V1_0::SlotState e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::config::V1_0::SimSlotStatus& o);
static inline void PrintTo(const ::android::hardware::radio::config::V1_0::SimSlotStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::config::V1_0::SimSlotStatus& lhs, const ::android::hardware::radio::config::V1_0::SimSlotStatus& rhs);
static inline bool operator!=(const ::android::hardware::radio::config::V1_0::SimSlotStatus& lhs, const ::android::hardware::radio::config::V1_0::SimSlotStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::radio::config::V1_0::SlotState>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::config::V1_0::SlotState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::config::V1_0::SlotState::INACTIVE) == static_cast<int32_t>(::android::hardware::radio::config::V1_0::SlotState::INACTIVE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE";
        first = false;
        flipped |= ::android::hardware::radio::config::V1_0::SlotState::INACTIVE;
    }
    if ((o & ::android::hardware::radio::config::V1_0::SlotState::ACTIVE) == static_cast<int32_t>(::android::hardware::radio::config::V1_0::SlotState::ACTIVE)) {
        os += (first ? "" : " | ");
        os += "ACTIVE";
        first = false;
        flipped |= ::android::hardware::radio::config::V1_0::SlotState::ACTIVE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::config::V1_0::SlotState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::config::V1_0::SlotState::INACTIVE) {
        return "INACTIVE";
    }
    if (o == ::android::hardware::radio::config::V1_0::SlotState::ACTIVE) {
        return "ACTIVE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::config::V1_0::SlotState o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::config::V1_0::SimSlotStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cardState = ";
    os += ::android::hardware::radio::V1_0::toString(o.cardState);
    os += ", .slotState = ";
    os += ::android::hardware::radio::config::V1_0::toString(o.slotState);
    os += ", .atr = ";
    os += ::android::hardware::toString(o.atr);
    os += ", .logicalSlotId = ";
    os += ::android::hardware::toString(o.logicalSlotId);
    os += ", .iccid = ";
    os += ::android::hardware::toString(o.iccid);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::config::V1_0::SimSlotStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::config::V1_0::SimSlotStatus& lhs, const ::android::hardware::radio::config::V1_0::SimSlotStatus& rhs) {
    if (lhs.cardState != rhs.cardState) {
        return false;
    }
    if (lhs.slotState != rhs.slotState) {
        return false;
    }
    if (lhs.atr != rhs.atr) {
        return false;
    }
    if (lhs.logicalSlotId != rhs.logicalSlotId) {
        return false;
    }
    if (lhs.iccid != rhs.iccid) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::config::V1_0::SimSlotStatus& lhs, const ::android::hardware::radio::config::V1_0::SimSlotStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::config::V1_0::SlotState, 2> hidl_enum_values<::android::hardware::radio::config::V1_0::SlotState> = {
    ::android::hardware::radio::config::V1_0::SlotState::INACTIVE,
    ::android::hardware::radio::config::V1_0::SlotState::ACTIVE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_TYPES_H
