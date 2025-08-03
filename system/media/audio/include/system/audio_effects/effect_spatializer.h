/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef ANDROID_EFFECT_SPATIALIZER_CORE_H_
#define ANDROID_EFFECT_SPATIALIZER_CORE_H_

#include <system/audio_effect.h>

#if __cplusplus
extern "C" {
#endif

#define FX_IID_SPATIALIZER__ \
        { 0xccd4cf09, 0xa79d, 0x46c2, 0x9aae, { 0x06, 0xa1, 0x69, 0x8d, 0x6c, 0x8f } }
static const effect_uuid_t FX_IID_SPATIALIZER_ = FX_IID_SPATIALIZER__;
const effect_uuid_t * const FX_IID_SPATIALIZER = &FX_IID_SPATIALIZER_;

typedef enum
{
    SPATIALIZER_PARAM_SUPPORTED_LEVELS,             // See SpatializationLevel.aidl
    SPATIALIZER_PARAM_LEVEL,                        // See SpatializationLevel.aidl
    SPATIALIZER_PARAM_HEADTRACKING_SUPPORTED,
    SPATIALIZER_PARAM_HEADTRACKING_MODE,            // See SpatializerHeadTrackingMode.aidl
    // list of supported input channel masks:
    //  first uint32_t is the number of channel masks followed by the corresponding
    // number of audio_channel_mask_t.
    SPATIALIZER_PARAM_SUPPORTED_CHANNEL_MASKS,
    // list of supported spatialization modes:
    //  first uint32_t is the number of modes followed by the corresponding
    // number of spatialization_mode_t.
    SPATIALIZER_PARAM_SUPPORTED_SPATIALIZATION_MODES,
    // Vector of 6 floats representing the head to stage pose:
    // first three are a translation vector and the last three are a rotation vector.
    SPATIALIZER_PARAM_HEAD_TO_STAGE,
    // foldable device hinge angle as a float value in rad
    SPATIALIZER_PARAM_HINGE_ANGLE,

    // The default display orientation as reported by DisplayManager.
    //
    // The DisplayManager reports 0, 90, 180, 270 degrees,
    // here it is reported as a float value in radians.
    // Only 4 values 0, PI/2, PI, 3PI/2 will be sent as of Android 14 (U).
    // Due to precision, compare with an epsilon range, suggest
    // rounding to the nearest integer degree for practical use.
    //
    // Notes:
    //    1) A device may have more than one display.
    //    2) A display may be locked which prevents the application from rotating.
    SPATIALIZER_PARAM_DISPLAY_ORIENTATION,

    // The fold state as reported by DeviceStateManager for a foldable.
    // This is an integer value of either 0 (open) or 1 (folded).
    //
    // The device fold state may affect which display is active (if any).
    // The open/closed logical state differs than the hinge angle,
    // which may be reported by a hinge sensor.
    SPATIALIZER_PARAM_FOLD_STATE,

    // Query the list of supported connection modes for head tracking data
    // - FRAMEWORK_PROCESSED: the audio framework provides pre processed IMU data via
    //   SPATIALIZER_PARAM_HEAD_TO_STAGE. This is the default and typically used for software
    //   effect implementations (not offloaded to a DSP).
    // - DIRECT_TO_SENSOR_SW: the audio framework just controls the enabled state of the sensor.
    //   The Spatializer effect directly connects to the sensor via the sensor software stack.
    //   Can be used by software implementations which do not want to benefit from the
    //   preprocessing done on IMU data by AOSP libheadtracking. Can also be used by DSP
    //   offloaded implementations.
    // - DIRECT_TO_SENSOR_TUNNEL: the audio framework just controls the enabled state of the
    //   sensor. The Spatializer effect directly connects to the sensor via hardware tunneling.
    //   This mode is reserved for DSP offloaded implementations when the offload mode is enabled
    //   by the framework.
    SPATIALIZER_PARAM_SUPPORTED_HEADTRACKING_CONNECTION,

    // Set/get the head tracking data connection mode: passes the mode followed by the sensor ID
    // on uint32_t
    SPATIALIZER_PARAM_HEADTRACKING_CONNECTION,
    // Set/get the actual input channel mask of the track being spatialized. This is required to
    // support stereo spatialization (b/303920722) where number of active input channels would be
    // different from the channel mask received during effect configuration.
    // Stereo channels will be packed into 5.1 stream where the other 4 channels will contain
    // silence. The effect can configure spatialization settings accordingly when this parameter is
    // received.
    SPATIALIZER_PARAM_INPUT_CHANNEL_MASK,
} t_virtualizer_stage_params;

// See SpatializationLevel.aidl
typedef enum {
    SPATIALIZATION_LEVEL_NONE = 0,
    SPATIALIZATION_LEVEL_MULTICHANNEL = 1,
    SPATIALIZATION_LEVEL_MCHAN_BED_PLUS_OBJECTS = 2,
} spatializer_level_t;

typedef enum {
    SPATIALIZATION_MODE_BINAURAL = 0,
    SPATIALIZATION_MODE_TRANSAURAL = 1,
} spatialization_mode_t;

//TODO b/273373363: use AIDL enum when available
typedef enum {
    HEADTRACKING_CONNECTION_FRAMEWORK_PROCESSED = 0,
    HEADTRACKING_CONNECTION_DIRECT_TO_SENSOR_SW = 1,
    HEADTRACKING_CONNECTION_DIRECT_TO_SENSOR_TUNNEL = 2,
} headtracking_connection_t;

#if __cplusplus
}  // extern "C"
#endif


#endif /*ANDROID_EFFECT_SPATIALIZER_CORE_H_*/
