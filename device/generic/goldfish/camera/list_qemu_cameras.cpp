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

#include <algorithm>
#include <charconv>
#include <numeric>
#include <string_view>
#include <math.h>

#include <log/log.h>

#include "debug.h"
#include "list_qemu_cameras.h"
#include "QemuCamera.h"
#include "qemu_channel.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {
namespace {
bool findToken(const std::string_view str, const std::string_view key, std::string_view* value) {
    size_t pos = 0;
    while (true) {
        pos = str.find(key, pos);
        if (pos == std::string_view::npos) {
            return false;
        } else if ((!pos || str[pos - 1] == ' ') &&
                (str.size() >= (pos + key.size() + 1)) &&
                (str[pos + key.size()] == '=')) {
            const size_t vbegin = pos + key.size() + 1;
            const size_t vend = str.find(' ', vbegin);
            if (vend == std::string_view::npos) {
                *value = str.substr(vbegin, str.size() - vbegin);
            } else {
                *value = str.substr(vbegin, vend - vbegin);
            }
            return true;
        } else {
            ++pos;
        }
    }
}

bool parseResolutions(const std::string_view str, std::vector<Rect<uint16_t>>* supportedResolutions) {
    const char* i = &*str.begin();
    const char* const end = &*str.end();
    if (i == end) {
        return FAILURE(false);
    }

    while (true) {
        Rect<uint16_t> resolution;
        std::from_chars_result r =  std::from_chars(i, end, resolution.width, 10);
        if (r.ec != std::errc()) {
            return FAILURE(false);
        }
        i = r.ptr;
        if (i == end) {
            return FAILURE(false);
        }
        if (*i != 'x') {
            return FAILURE(false);
        }
        r =  std::from_chars(i + 1, end, resolution.height, 10);
        if (r.ec != std::errc()) {
            return FAILURE(false);
        }
        i = r.ptr;

        if ((resolution.width > 0) && (resolution.height > 0)) {
            supportedResolutions->push_back(resolution);
        }

        if (i == end) {
            break;
        } else {
            if (*i == ',') {
                ++i;
            } else {
                return FAILURE(false);
            }
        }
    }

    return true;
}

Rect<uint16_t> calcThumbnailResolution(const double aspectRatio,
                                       const size_t targetArea) {
    // round to a multiple of 16, a tad down
    const uint16_t height =
        ((uint16_t(sqrt(targetArea / aspectRatio)) + 7) >> 4) << 4;

    // round width to be even
    const uint16_t width = (uint16_t(height * aspectRatio + 1) >> 1) << 1;

    return {width, height};
}

struct RectAreaComparator {
    bool operator()(Rect<uint16_t> lhs, Rect<uint16_t> rhs) const {
        const size_t lArea = lhs.area();
        const size_t rArea = rhs.area();

        if (lArea < rArea) {
            return true;
        } else if (lArea > rArea) {
            return false;
        } else {
            return lhs.width < rhs.width;
        }
    }
};

} // namespace

bool listQemuCameras(const std::function<void(HwCameraFactory)>& cameraSink) {
    using namespace std::literals;
    static const char kListQuery[] = "list";

    const auto fd = qemuOpenChannel();
    if (!fd.ok()) { return false; }

    std::vector<uint8_t> data;
    if (qemuRunQuery(fd.get(), kListQuery, sizeof(kListQuery), &data) < 0) {
        return FAILURE(false);
    }

    const char* i = reinterpret_cast<const char*>(&*data.begin());
    const char* const end = reinterpret_cast<const char*>(&*data.end());

    while (i < end) {
        const char* const lf = std::find(i, end, '\n');
        if (lf == end) {
            if (*i) {
                return FAILURE(false);
            } else {
                break;
            }
        }

        // line='name=virtualscene channel=0 pix=876758866 dir=back framedims=640x480,352x288,320x240,176x144,1280x720,1280x960'
        const std::string_view line(i, lf - i);

        std::string_view name;
        if (!findToken(line, "name"sv, &name)) { return false; }

        std::string_view dir;
        if (!findToken(line, "dir"sv, &dir)) { return FAILURE(false); }

        std::string_view framedims;
        if (!findToken(line, "framedims"sv, &framedims)) { return FAILURE(false); }

        QemuCamera::Parameters params;
        if (!parseResolutions(framedims, &params.supportedResolutions)) {
            return FAILURE(false);
        }

        if (params.supportedResolutions.empty()) {
            return FAILURE(false);
        } else {
            std::sort(params.supportedResolutions.begin(),
                      params.supportedResolutions.end(),
                      RectAreaComparator());

            std::vector<Rect<uint16_t>> thumbnailResolutions;
            thumbnailResolutions.push_back({0, 0});

            for (const Rect<uint16_t> res : params.supportedResolutions) {
                const double aspectRatio = double(res.width) / res.height;
                const size_t resArea4 = res.area() / 4;
                Rect<uint16_t> thumbnailRes;
                size_t thumbnailResArea;

                do {
                    thumbnailRes = calcThumbnailResolution(aspectRatio, 4900);
                    thumbnailResArea = thumbnailRes.area();
                    if ((thumbnailResArea > 0) && (thumbnailResArea < resArea4)) {
                        thumbnailResolutions.push_back(thumbnailRes);
                    } else {
                        thumbnailRes = calcThumbnailResolution(aspectRatio, 1800);
                        thumbnailResArea = thumbnailRes.area();
                        if ((thumbnailResArea > 0) && (thumbnailRes.area() < resArea4)) {
                            thumbnailResolutions.push_back(thumbnailRes);
                        } else {
                            // `res` is too small for a thumbnail
                        }
                        break;
                    }

                    thumbnailRes = calcThumbnailResolution(aspectRatio, 19500);
                    thumbnailResArea = thumbnailRes.area();
                    if ((thumbnailResArea > 0) && (thumbnailRes.area() < resArea4)) {
                        thumbnailResolutions.push_back(thumbnailRes);
                    } else {
                        break;
                    }

                    thumbnailRes = calcThumbnailResolution(aspectRatio, 77000);
                    thumbnailResArea = thumbnailRes.area();
                    if ((thumbnailResArea > 0) && (thumbnailRes.area() < resArea4)) {
                        thumbnailResolutions.push_back(thumbnailRes);
                    }
                } while (false);
            }

            std::sort(thumbnailResolutions.begin(), thumbnailResolutions.end(),
                      RectAreaComparator());

            thumbnailResolutions.erase(std::unique(thumbnailResolutions.begin(),
                                                   thumbnailResolutions.end()),
                                       thumbnailResolutions.end());

            params.availableThumbnailResolutions = std::move(thumbnailResolutions);
        }

        ALOGD("%s:%d found a '%.*s' QEMU camera, dir=%.*s framedims=%.*s",
              __func__, __LINE__,
              int(name.size()), name.data(),
              int(dir.size()), dir.data(),
              int(framedims.size()), framedims.data());

        params.name = std::string(name.begin(), name.end());

        params.sensorSize = std::accumulate(
            std::next(params.supportedResolutions.begin()),
            params.supportedResolutions.end(),
            params.supportedResolutions.front(),
            [](Rect<uint16_t> z, Rect<uint16_t> x) -> Rect<uint16_t> {
                return {std::max(z.width, x.width),
                        std::max(z.height, x.height)};
        });

        params.isBackFacing = (dir == "back"sv);

        cameraSink([params = std::move(params)]() {
            return std::make_unique<QemuCamera>(params);
        });

        i = lf + 1;
    }

    return true;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
