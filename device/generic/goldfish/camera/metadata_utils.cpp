/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <inttypes.h>

#include <memory>
#include <numeric>

#include <system/camera_metadata.h>

#include "debug.h"
#include "metadata_utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace {

struct CameraMetadataDeleter {
    void operator()(camera_metadata_t* p) const {
        free_camera_metadata(p);
    }
};

using CameraMetadataPtr = std::unique_ptr<camera_metadata_t, CameraMetadataDeleter>;

CameraMetadata metadataCompactRaw(const camera_metadata_t* raw) {
    const size_t size = get_camera_metadata_compact_size(raw);
    CameraMetadata r;
    r.metadata.resize(size);
    copy_camera_metadata(r.metadata.data(), size, raw);
    return r;
}

} // namespace

CameraMetadata metadataCompact(const CameraMetadata& m) {
    return metadataCompactRaw(reinterpret_cast<const camera_metadata_t*>(m.metadata.data()));
}

std::optional<CameraMetadata> serializeCameraMetadataMap(const CameraMetadataMap& m) {
    const size_t dataSize = std::accumulate(m.begin(), m.end(), 0,
        [](const size_t z, const CameraMetadataMap::value_type& kv) {
            return z + ((kv.second.count > 0) ? ((kv.second.data.size() + 7) & ~7U) : 0);
        }
    );

    CameraMetadataPtr cm(allocate_camera_metadata(m.size() * 5 / 4, dataSize * 3 / 2));

    unsigned numIncorrectTagDataSize = 0;
    for (const auto& [tag, value] : m) {
        if (value.count > 0) {
            const int tagType = get_camera_metadata_tag_type(tag);
            const size_t elementSize = camera_metadata_type_size[tagType];
            const size_t expectedDataSize = value.count * elementSize;

            if (value.data.size() == expectedDataSize) {
                if (add_camera_metadata_entry(cm.get(), tag, value.data.data(), value.count)) {
                    return FAILURE_V(std::nullopt, "failed to add tag=%s.%s(%u), count=%u",
                                     get_camera_metadata_section_name(tag),
                                     get_camera_metadata_tag_name(tag), tag,
                                     value.count);
                }
            } else {
                ++numIncorrectTagDataSize;
                ALOGE("%s:%d: Incorrect tag (%s.%s(%u), %s[%u]) data size, "
                      "expected=%zu, actual=%zu", __func__, __LINE__,
                       get_camera_metadata_section_name(tag),
                       get_camera_metadata_tag_name(tag),
                       tag, camera_metadata_type_names[tagType],
                       value.count, expectedDataSize, value.data.size());
            }
        }
    }

    LOG_ALWAYS_FATAL_IF(numIncorrectTagDataSize > 0, "%s:%d: there are %u tags "
                        "with incorrect data size, see the messages above.",
                        __func__, __LINE__, numIncorrectTagDataSize);

    if (sort_camera_metadata(cm.get())) {
        return FAILURE(std::nullopt);
    }

    return metadataCompactRaw(cm.get());
}

CameraMetadataMap parseCameraMetadataMap(const CameraMetadata& m) {
    const camera_metadata_t* const raw =
        reinterpret_cast<const camera_metadata_t*>(m.metadata.data());
    const size_t n = get_camera_metadata_entry_count(raw);

    CameraMetadataMap r;
    for (size_t i = 0; i < n; ++i) {
        camera_metadata_ro_entry_t e;
        if (get_camera_metadata_ro_entry(raw, i, &e)) {
            ALOGW("%s:%d get_camera_metadata_ro_entry(%zu) failed",
                  __func__, __LINE__, i);
        } else {
            auto& v = r[e.tag];
            v.count = e.count;
            const size_t sz = camera_metadata_type_size[e.type] * e.count;
            v.data.assign(e.data.u8, e.data.u8 + sz);
        }
    }
    return r;
}

void metadataSetShutterTimestamp(CameraMetadata* m, const int64_t shutterTimestampNs) {
    if (m->metadata.empty()) {
        return;
    }

    camera_metadata_t* const raw =
        reinterpret_cast<camera_metadata_t*>(m->metadata.data());

    camera_metadata_ro_entry_t entry;
    if (find_camera_metadata_ro_entry(raw, ANDROID_SENSOR_TIMESTAMP, &entry)) {
        ALOGW("%s:%d: find_camera_metadata_ro_entry(ANDROID_SENSOR_TIMESTAMP) failed",
              __func__, __LINE__);
    } else if (update_camera_metadata_entry(raw, entry.index, &shutterTimestampNs, 1, nullptr)) {
        ALOGW("%s:%d: update_camera_metadata_entry(ANDROID_SENSOR_TIMESTAMP) failed",
              __func__, __LINE__);
    }
}

void prettyPrintCameraMetadata(const CameraMetadata& m) {
    const camera_metadata_t* const raw =
        reinterpret_cast<const camera_metadata_t*>(m.metadata.data());
    const size_t n = get_camera_metadata_entry_count(raw);

    for (size_t i = 0; i < n; ++i) {
        camera_metadata_ro_entry_t e;
        get_camera_metadata_ro_entry(raw, i, &e);
        std::vector<char> value;

        if (e.count > 0) {
            switch (e.type) {
            case TYPE_BYTE: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 4);
                        s += snprintf(&value[s], value.size() - s, "%s%u",
                                      ((j > 0) ? "," : ""), e.data.u8[j]);
                    }
                }
                break;
            case TYPE_INT32: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 12);
                        s += snprintf(&value[s], value.size() - s, "%s%d",
                                      ((j > 0) ? "," : ""), e.data.i32[j]);
                    }
                }
                break;
            case TYPE_FLOAT: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 12);
                        s += snprintf(&value[s], value.size() - s, "%s%g",
                                      ((j > 0) ? "," : ""), e.data.f[j]);
                    }
                }
                break;
            case TYPE_INT64: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 24);
                        s += snprintf(&value[s], value.size() - s, "%s%" PRId64,
                                      ((j > 0) ? "," : ""), e.data.i64[j]);
                    }
                }
                break;
            case TYPE_DOUBLE: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 25);
                        s += snprintf(&value[s], value.size() - s, "%s%g",
                                      ((j > 0) ? "," : ""), e.data.d[j]);
                    }
                }
                break;
            case TYPE_RATIONAL: {
                    int s = 0;
                    for (unsigned j = 0; j < e.count; ++j) {
                        value.resize(s + 25);
                        s += snprintf(&value[s], value.size() - s, "%s%d/%d",
                                      ((j > 0) ? "," : ""),
                                      e.data.r[j].numerator,
                                      e.data.r[j].denominator);
                    }
                }
                break;
            default:
                value.resize(12);
                snprintf(&value[0], value.size(), "%s", "bad type");
                break;
            }
        } else {
            value.resize(8);
            snprintf(&value[0], value.size(), "%s", "empty");
        }

        ALOGD("%s:%d i=%zu tag=%s.%s(%u),%s[%zu]: %s", __func__, __LINE__, i,
              get_camera_metadata_section_name(e.tag),
              get_camera_metadata_tag_name(e.tag),
              e.tag, camera_metadata_type_names[e.type], e.count, value.data());
    }
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
