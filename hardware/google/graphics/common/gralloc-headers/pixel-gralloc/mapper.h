#pragma once

#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <log/log.h>

#include "metadata.h"
#include "utils.h"

namespace pixel::graphics::mapper {

namespace {

using ::aidl::android::hardware::graphics::common::PlaneLayout;
using android::hardware::graphics::mapper::V4_0::Error;
using android::hardware::graphics::mapper::V4_0::IMapper;
using HidlPixelFormat = ::android::hardware::graphics::common::V1_2::PixelFormat;
using namespace ::pixel::graphics;

android::sp<IMapper> get_mapper() {
    static android::sp<IMapper> mapper = []() {
        auto mapper = IMapper::getService();
        if (!mapper) {
            ALOGE("Failed to get mapper service");
        }
        return mapper;
    }();

    return mapper;
}

} // namespace

template <MetadataType T>
struct always_false : std::false_type {};

template <MetadataType T>
struct ReturnType {
    static_assert(always_false<T>::value, "Unspecialized ReturnType is not supported");
    using type = void;
};

template <MetadataType T>
static std::optional<typename ReturnType<T>::type> get(buffer_handle_t /*handle*/) {
    static_assert(always_false<T>::value, "Unspecialized get is not supported");
    return {};
}

// TODO: Add support for stable-c mapper
#define GET(metadata, return_type)                                                       \
    template <>                                                                          \
    struct ReturnType<MetadataType::metadata> {                                          \
        using type = return_type;                                                        \
    };                                                                                   \
                                                                                         \
    template <>                                                                          \
    [[maybe_unused]] std::optional<typename ReturnType<MetadataType::metadata>::type>    \
    get<MetadataType::metadata>(buffer_handle_t handle) {                                \
        auto mapper = get_mapper();                                                      \
        IMapper::MetadataType type = {                                                   \
                .name = kPixelMetadataTypeName,                                          \
                .value = static_cast<int64_t>(MetadataType::metadata),                   \
        };                                                                               \
                                                                                         \
        android::hardware::hidl_vec<uint8_t> vec;                                        \
        Error error;                                                                     \
        auto ret = mapper->get(const_cast<native_handle_t*>(handle), type,               \
                               [&](const auto& tmpError,                                 \
                                   const android::hardware::hidl_vec<uint8_t>& tmpVec) { \
                                   error = tmpError;                                     \
                                   vec = tmpVec;                                         \
                               });                                                       \
        if (!ret.isOk()) {                                                               \
            return {};                                                                   \
        }                                                                                \
                                                                                         \
        return utils::decode<return_type>(vec);                                          \
    }

GET(PLANE_DMA_BUFS, std::vector<int>);
GET(VIDEO_HDR, void*);
GET(VIDEO_ROI, void*);
GET(VIDEO_GMV, VideoGMV);
#undef GET

template <MetadataType T>
static Error set(buffer_handle_t /*handle*/, typename ReturnType<T>::type /*data*/) {
    static_assert(always_false<T>::value, "Unspecialized set is not supported");
    return {};
}

#define SET(metadata, metadata_typename)                                                  \
    template <>                                                                           \
    [[maybe_unused]] Error                                                                \
    set<MetadataType::metadata>(buffer_handle_t handle,                                   \
                                typename ReturnType<MetadataType::metadata>::type data) { \
        auto mapper = get_mapper();                                                       \
        auto encoded_data = utils::encode<metadata_typename>(data);                       \
        IMapper::MetadataType type = {                                                    \
                .name = kPixelMetadataTypeName,                                           \
                .value = static_cast<int64_t>(MetadataType::metadata),                    \
        };                                                                                \
                                                                                          \
        auto ret = mapper->set(const_cast<native_handle_t*>(handle), type, encoded_data); \
                                                                                          \
        return ret;                                                                       \
    }

SET(VIDEO_GMV, VideoGMV);
#undef SET

} // namespace pixel::graphics::mapper
