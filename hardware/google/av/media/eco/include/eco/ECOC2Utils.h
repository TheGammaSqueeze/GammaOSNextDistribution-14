/*
 * Copyright (C) 2020 The Android Open Source Project
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

// Helper classes and functions to be used by provider and listener.
#ifndef ANDROID_MEDIA_ECO_C2UTILS_H_
#define ANDROID_MEDIA_ECO_C2UTILS_H_

#include <cutils/atomic.h>
#include <utils/Errors.h>

#include <C2Config.h>
#include "ECOData.h"
#include "ECODataKey.h"
#include "ECOServiceConstants.h"

namespace android {
namespace media {
namespace eco {

// Convert C2MediaType to ECOCodecType
extern int C2MediaType2ECOCodecType(std::shared_ptr<C2PortMediaTypeSetting::output> mediatype);

// Convert C2Profile to ECOProfile
extern int C2Profile2ECOProfile(C2Config::profile_t profile);

// Convert C2Level to ECOLevel
extern int C2Level2ECOLevel(C2Config::level_t level);

// Convert C2PictureType to ECOFrameType
extern int C2PictureType2ECOFrameType(C2Config::picture_type_t frametype);

}  // namespace eco
}  // namespace media
}  // namespace android

#endif  // ANDROID_MEDIA_ECO_C2UTILS_H_
