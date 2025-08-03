#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_5_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_5_TYPES_H

#include <android/hardware/camera/metadata/3.2/types.h>
#include <android/hardware/camera/metadata/3.3/types.h>
#include <android/hardware/camera/metadata/3.4/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace metadata {
namespace V3_5 {

// Forward declaration for forward reference support:
enum class CameraMetadataTag : uint32_t;
enum class CameraMetadataEnumAndroidControlMode : uint32_t;
enum class CameraMetadataEnumAndroidControlExtendedSceneMode : uint32_t;
enum class CameraMetadataEnumAndroidLensPoseReference : uint32_t;
enum class CameraMetadataEnumAndroidRequestAvailableCapabilities : uint32_t;
enum class CameraMetadataEnumAndroidScalerRotateAndCrop : uint32_t;

/**
 * Main enumeration for defining camera metadata tags added in this revision
 *
 * <p>Partial documentation is included for each tag; for complete documentation, reference
 * '/system/media/camera/docs/docs.html' in the corresponding Android source tree.</p>
 */
enum class CameraMetadataTag : uint32_t {
    /**
     * android.colorCorrection.mode [dynamic, enum, public]
     *
     * <p>The mode control selects how the image data is converted from the
     * sensor's native color into linear sRGB color.</p>
     */
    ANDROID_COLOR_CORRECTION_MODE = 0u /* CameraMetadataSectionStart:ANDROID_COLOR_CORRECTION_START */,
    /**
     * android.colorCorrection.transform [dynamic, rational[], public]
     *
     * <p>A color transform matrix to use to transform
     * from sensor RGB color space to output linear sRGB color space.</p>
     */
    ANDROID_COLOR_CORRECTION_TRANSFORM = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_COLOR_CORRECTION_MODE implicitly + 1 */,
    /**
     * android.colorCorrection.gains [dynamic, float[], public]
     *
     * <p>Gains applying to Bayer raw color channels for
     * white-balance.</p>
     */
    ANDROID_COLOR_CORRECTION_GAINS = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_COLOR_CORRECTION_TRANSFORM implicitly + 1 */,
    /**
     * android.colorCorrection.aberrationMode [dynamic, enum, public]
     *
     * <p>Mode of operation for the chromatic aberration correction algorithm.</p>
     */
    ANDROID_COLOR_CORRECTION_ABERRATION_MODE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_COLOR_CORRECTION_GAINS implicitly + 1 */,
    /**
     * android.colorCorrection.availableAberrationModes [static, byte[], public]
     *
     * <p>List of aberration correction modes for ANDROID_COLOR_CORRECTION_ABERRATION_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_COLOR_CORRECTION_ABERRATION_MODE
     */
    ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_COLOR_CORRECTION_ABERRATION_MODE implicitly + 1 */,
    ANDROID_COLOR_CORRECTION_END = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES implicitly + 1 */,
    /**
     * android.control.aeAntibandingMode [dynamic, enum, public]
     *
     * <p>The desired setting for the camera device's auto-exposure
     * algorithm's antibanding compensation.</p>
     */
    ANDROID_CONTROL_AE_ANTIBANDING_MODE = 65536u /* CameraMetadataSectionStart:ANDROID_CONTROL_START */,
    /**
     * android.control.aeExposureCompensation [dynamic, int32, public]
     *
     * <p>Adjustment to auto-exposure (AE) target image
     * brightness.</p>
     */
    ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION = 65537u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_ANTIBANDING_MODE implicitly + 1 */,
    /**
     * android.control.aeLock [dynamic, enum, public]
     *
     * <p>Whether auto-exposure (AE) is currently locked to its latest
     * calculated values.</p>
     */
    ANDROID_CONTROL_AE_LOCK = 65538u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION implicitly + 1 */,
    /**
     * android.control.aeMode [dynamic, enum, public]
     *
     * <p>The desired mode for the camera device's
     * auto-exposure routine.</p>
     */
    ANDROID_CONTROL_AE_MODE = 65539u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_LOCK implicitly + 1 */,
    /**
     * android.control.aeRegions [dynamic, int32[], public]
     *
     * <p>List of metering areas to use for auto-exposure adjustment.</p>
     */
    ANDROID_CONTROL_AE_REGIONS = 65540u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_MODE implicitly + 1 */,
    /**
     * android.control.aeTargetFpsRange [dynamic, int32[], public]
     *
     * <p>Range over which the auto-exposure routine can
     * adjust the capture frame rate to maintain good
     * exposure.</p>
     */
    ANDROID_CONTROL_AE_TARGET_FPS_RANGE = 65541u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_REGIONS implicitly + 1 */,
    /**
     * android.control.aePrecaptureTrigger [dynamic, enum, public]
     *
     * <p>Whether the camera device will trigger a precapture
     * metering sequence when it processes this request.</p>
     */
    ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER = 65542u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_TARGET_FPS_RANGE implicitly + 1 */,
    /**
     * android.control.afMode [dynamic, enum, public]
     *
     * <p>Whether auto-focus (AF) is currently enabled, and what
     * mode it is set to.</p>
     */
    ANDROID_CONTROL_AF_MODE = 65543u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER implicitly + 1 */,
    /**
     * android.control.afRegions [dynamic, int32[], public]
     *
     * <p>List of metering areas to use for auto-focus.</p>
     */
    ANDROID_CONTROL_AF_REGIONS = 65544u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_MODE implicitly + 1 */,
    /**
     * android.control.afTrigger [dynamic, enum, public]
     *
     * <p>Whether the camera device will trigger autofocus for this request.</p>
     */
    ANDROID_CONTROL_AF_TRIGGER = 65545u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_REGIONS implicitly + 1 */,
    /**
     * android.control.awbLock [dynamic, enum, public]
     *
     * <p>Whether auto-white balance (AWB) is currently locked to its
     * latest calculated values.</p>
     */
    ANDROID_CONTROL_AWB_LOCK = 65546u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_TRIGGER implicitly + 1 */,
    /**
     * android.control.awbMode [dynamic, enum, public]
     *
     * <p>Whether auto-white balance (AWB) is currently setting the color
     * transform fields, and what its illumination target
     * is.</p>
     */
    ANDROID_CONTROL_AWB_MODE = 65547u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_LOCK implicitly + 1 */,
    /**
     * android.control.awbRegions [dynamic, int32[], public]
     *
     * <p>List of metering areas to use for auto-white-balance illuminant
     * estimation.</p>
     */
    ANDROID_CONTROL_AWB_REGIONS = 65548u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_MODE implicitly + 1 */,
    /**
     * android.control.captureIntent [dynamic, enum, public]
     *
     * <p>Information to the camera device 3A (auto-exposure,
     * auto-focus, auto-white balance) routines about the purpose
     * of this capture, to help the camera device to decide optimal 3A
     * strategy.</p>
     */
    ANDROID_CONTROL_CAPTURE_INTENT = 65549u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_REGIONS implicitly + 1 */,
    /**
     * android.control.effectMode [dynamic, enum, public]
     *
     * <p>A special color effect to apply.</p>
     */
    ANDROID_CONTROL_EFFECT_MODE = 65550u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_CAPTURE_INTENT implicitly + 1 */,
    /**
     * android.control.mode [dynamic, enum, public]
     *
     * <p>Overall mode of 3A (auto-exposure, auto-white-balance, auto-focus) control
     * routines.</p>
     */
    ANDROID_CONTROL_MODE = 65551u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_EFFECT_MODE implicitly + 1 */,
    /**
     * android.control.sceneMode [dynamic, enum, public]
     *
     * <p>Control for which scene mode is currently active.</p>
     */
    ANDROID_CONTROL_SCENE_MODE = 65552u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_MODE implicitly + 1 */,
    /**
     * android.control.videoStabilizationMode [dynamic, enum, public]
     *
     * <p>Whether video stabilization is
     * active.</p>
     */
    ANDROID_CONTROL_VIDEO_STABILIZATION_MODE = 65553u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_SCENE_MODE implicitly + 1 */,
    /**
     * android.control.aeAvailableAntibandingModes [static, byte[], public]
     *
     * <p>List of auto-exposure antibanding modes for ANDROID_CONTROL_AE_ANTIBANDING_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_CONTROL_AE_ANTIBANDING_MODE
     */
    ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES = 65554u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_VIDEO_STABILIZATION_MODE implicitly + 1 */,
    /**
     * android.control.aeAvailableModes [static, byte[], public]
     *
     * <p>List of auto-exposure modes for ANDROID_CONTROL_AE_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_CONTROL_AE_MODE
     */
    ANDROID_CONTROL_AE_AVAILABLE_MODES = 65555u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES implicitly + 1 */,
    /**
     * android.control.aeAvailableTargetFpsRanges [static, int32[], public]
     *
     * <p>List of frame rate ranges for ANDROID_CONTROL_AE_TARGET_FPS_RANGE supported by
     * this camera device.</p>
     *
     * @see ANDROID_CONTROL_AE_TARGET_FPS_RANGE
     */
    ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES = 65556u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.control.aeCompensationRange [static, int32[], public]
     *
     * <p>Maximum and minimum exposure compensation values for
     * ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION, in counts of ANDROID_CONTROL_AE_COMPENSATION_STEP,
     * that are supported by this camera device.</p>
     *
     * @see ANDROID_CONTROL_AE_COMPENSATION_STEP
     * @see ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION
     */
    ANDROID_CONTROL_AE_COMPENSATION_RANGE = 65557u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES implicitly + 1 */,
    /**
     * android.control.aeCompensationStep [static, rational, public]
     *
     * <p>Smallest step by which the exposure compensation
     * can be changed.</p>
     */
    ANDROID_CONTROL_AE_COMPENSATION_STEP = 65558u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_COMPENSATION_RANGE implicitly + 1 */,
    /**
     * android.control.afAvailableModes [static, byte[], public]
     *
     * <p>List of auto-focus (AF) modes for ANDROID_CONTROL_AF_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_CONTROL_AF_MODE
     */
    ANDROID_CONTROL_AF_AVAILABLE_MODES = 65559u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_COMPENSATION_STEP implicitly + 1 */,
    /**
     * android.control.availableEffects [static, byte[], public]
     *
     * <p>List of color effects for ANDROID_CONTROL_EFFECT_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_CONTROL_EFFECT_MODE
     */
    ANDROID_CONTROL_AVAILABLE_EFFECTS = 65560u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.control.availableSceneModes [static, byte[], public]
     *
     * <p>List of scene modes for ANDROID_CONTROL_SCENE_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_CONTROL_SCENE_MODE
     */
    ANDROID_CONTROL_AVAILABLE_SCENE_MODES = 65561u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_EFFECTS implicitly + 1 */,
    /**
     * android.control.availableVideoStabilizationModes [static, byte[], public]
     *
     * <p>List of video stabilization modes for ANDROID_CONTROL_VIDEO_STABILIZATION_MODE
     * that are supported by this camera device.</p>
     *
     * @see ANDROID_CONTROL_VIDEO_STABILIZATION_MODE
     */
    ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES = 65562u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_SCENE_MODES implicitly + 1 */,
    /**
     * android.control.awbAvailableModes [static, byte[], public]
     *
     * <p>List of auto-white-balance modes for ANDROID_CONTROL_AWB_MODE that are supported by this
     * camera device.</p>
     *
     * @see ANDROID_CONTROL_AWB_MODE
     */
    ANDROID_CONTROL_AWB_AVAILABLE_MODES = 65563u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES implicitly + 1 */,
    /**
     * android.control.maxRegions [static, int32[], ndk_public]
     *
     * <p>List of the maximum number of regions that can be used for metering in
     * auto-exposure (AE), auto-white balance (AWB), and auto-focus (AF);
     * this corresponds to the maximum number of elements in
     * ANDROID_CONTROL_AE_REGIONS, ANDROID_CONTROL_AWB_REGIONS,
     * and ANDROID_CONTROL_AF_REGIONS.</p>
     *
     * @see ANDROID_CONTROL_AE_REGIONS
     * @see ANDROID_CONTROL_AF_REGIONS
     * @see ANDROID_CONTROL_AWB_REGIONS
     */
    ANDROID_CONTROL_MAX_REGIONS = 65564u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.control.sceneModeOverrides [static, byte[], system]
     *
     * <p>Ordered list of auto-exposure, auto-white balance, and auto-focus
     * settings to use with each available scene mode.</p>
     */
    ANDROID_CONTROL_SCENE_MODE_OVERRIDES = 65565u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_MAX_REGIONS implicitly + 1 */,
    /**
     * android.control.aePrecaptureId [dynamic, int32, system]
     *
     * <p>The ID sent with the latest
     * CAMERA2_TRIGGER_PRECAPTURE_METERING call</p>
     */
    ANDROID_CONTROL_AE_PRECAPTURE_ID = 65566u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_SCENE_MODE_OVERRIDES implicitly + 1 */,
    /**
     * android.control.aeState [dynamic, enum, public]
     *
     * <p>Current state of the auto-exposure (AE) algorithm.</p>
     */
    ANDROID_CONTROL_AE_STATE = 65567u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_PRECAPTURE_ID implicitly + 1 */,
    /**
     * android.control.afState [dynamic, enum, public]
     *
     * <p>Current state of auto-focus (AF) algorithm.</p>
     */
    ANDROID_CONTROL_AF_STATE = 65568u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_STATE implicitly + 1 */,
    /**
     * android.control.afTriggerId [dynamic, int32, system]
     *
     * <p>The ID sent with the latest
     * CAMERA2_TRIGGER_AUTOFOCUS call</p>
     */
    ANDROID_CONTROL_AF_TRIGGER_ID = 65569u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_STATE implicitly + 1 */,
    /**
     * android.control.awbState [dynamic, enum, public]
     *
     * <p>Current state of auto-white balance (AWB) algorithm.</p>
     */
    ANDROID_CONTROL_AWB_STATE = 65570u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AF_TRIGGER_ID implicitly + 1 */,
    /**
     * android.control.availableHighSpeedVideoConfigurations [static, int32[], hidden]
     *
     * <p>List of available high speed video size, fps range and max batch size configurations
     * supported by the camera device, in the format of (width, height, fps_min, fps_max, batch_size_max).</p>
     */
    ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS = 65571u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_STATE implicitly + 1 */,
    /**
     * android.control.aeLockAvailable [static, enum, public]
     *
     * <p>Whether the camera device supports ANDROID_CONTROL_AE_LOCK</p>
     *
     * @see ANDROID_CONTROL_AE_LOCK
     */
    ANDROID_CONTROL_AE_LOCK_AVAILABLE = 65572u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.control.awbLockAvailable [static, enum, public]
     *
     * <p>Whether the camera device supports ANDROID_CONTROL_AWB_LOCK</p>
     *
     * @see ANDROID_CONTROL_AWB_LOCK
     */
    ANDROID_CONTROL_AWB_LOCK_AVAILABLE = 65573u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AE_LOCK_AVAILABLE implicitly + 1 */,
    /**
     * android.control.availableModes [static, byte[], public]
     *
     * <p>List of control modes for ANDROID_CONTROL_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_CONTROL_MODE
     */
    ANDROID_CONTROL_AVAILABLE_MODES = 65574u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AWB_LOCK_AVAILABLE implicitly + 1 */,
    /**
     * android.control.postRawSensitivityBoostRange [static, int32[], public]
     *
     * <p>Range of boosts for ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST supported
     * by this camera device.</p>
     *
     * @see ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST
     */
    ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE = 65575u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.control.postRawSensitivityBoost [dynamic, int32, public]
     *
     * <p>The amount of additional sensitivity boost applied to output images
     * after RAW sensor data is captured.</p>
     */
    ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST = 65576u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE implicitly + 1 */,
    /**
     * android.control.enableZsl [dynamic, enum, public]
     *
     * <p>Allow camera device to enable zero-shutter-lag mode for requests with
     * ANDROID_CONTROL_CAPTURE_INTENT == STILL_CAPTURE.</p>
     *
     * @see ANDROID_CONTROL_CAPTURE_INTENT
     */
    ANDROID_CONTROL_ENABLE_ZSL = 65577u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST implicitly + 1 */,
    ANDROID_CONTROL_END = 65578u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_CONTROL_ENABLE_ZSL implicitly + 1 */,
    /**
     * android.demosaic.mode [controls, enum, system]
     *
     * <p>Controls the quality of the demosaicing
     * processing.</p>
     */
    ANDROID_DEMOSAIC_MODE = 131072u /* CameraMetadataSectionStart:ANDROID_DEMOSAIC_START */,
    ANDROID_DEMOSAIC_END = 131073u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEMOSAIC_MODE implicitly + 1 */,
    /**
     * android.edge.mode [dynamic, enum, public]
     *
     * <p>Operation mode for edge
     * enhancement.</p>
     */
    ANDROID_EDGE_MODE = 196608u /* CameraMetadataSectionStart:ANDROID_EDGE_START */,
    /**
     * android.edge.strength [controls, byte, system]
     *
     * <p>Control the amount of edge enhancement
     * applied to the images</p>
     */
    ANDROID_EDGE_STRENGTH = 196609u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_EDGE_MODE implicitly + 1 */,
    /**
     * android.edge.availableEdgeModes [static, byte[], public]
     *
     * <p>List of edge enhancement modes for ANDROID_EDGE_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_EDGE_MODE
     */
    ANDROID_EDGE_AVAILABLE_EDGE_MODES = 196610u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_EDGE_STRENGTH implicitly + 1 */,
    ANDROID_EDGE_END = 196611u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_EDGE_AVAILABLE_EDGE_MODES implicitly + 1 */,
    /**
     * android.flash.firingPower [dynamic, byte, system]
     *
     * <p>Power for flash firing/torch</p>
     */
    ANDROID_FLASH_FIRING_POWER = 262144u /* CameraMetadataSectionStart:ANDROID_FLASH_START */,
    /**
     * android.flash.firingTime [dynamic, int64, system]
     *
     * <p>Firing time of flash relative to start of
     * exposure</p>
     */
    ANDROID_FLASH_FIRING_TIME = 262145u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_FIRING_POWER implicitly + 1 */,
    /**
     * android.flash.mode [dynamic, enum, public]
     *
     * <p>The desired mode for for the camera device's flash control.</p>
     */
    ANDROID_FLASH_MODE = 262146u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_FIRING_TIME implicitly + 1 */,
    /**
     * android.flash.colorTemperature [static, byte, system]
     *
     * <p>The x,y whitepoint of the
     * flash</p>
     */
    ANDROID_FLASH_COLOR_TEMPERATURE = 262147u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_MODE implicitly + 1 */,
    /**
     * android.flash.maxEnergy [static, byte, system]
     *
     * <p>Max energy output of the flash for a full
     * power single flash</p>
     */
    ANDROID_FLASH_MAX_ENERGY = 262148u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_COLOR_TEMPERATURE implicitly + 1 */,
    /**
     * android.flash.state [dynamic, enum, public]
     *
     * <p>Current state of the flash
     * unit.</p>
     */
    ANDROID_FLASH_STATE = 262149u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_MAX_ENERGY implicitly + 1 */,
    ANDROID_FLASH_END = 262150u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_STATE implicitly + 1 */,
    /**
     * android.flash.info.available [static, enum, public]
     *
     * <p>Whether this camera device has a
     * flash unit.</p>
     */
    ANDROID_FLASH_INFO_AVAILABLE = 327680u /* CameraMetadataSectionStart:ANDROID_FLASH_INFO_START */,
    /**
     * android.flash.info.chargeDuration [static, int64, system]
     *
     * <p>Time taken before flash can fire
     * again</p>
     */
    ANDROID_FLASH_INFO_CHARGE_DURATION = 327681u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_INFO_AVAILABLE implicitly + 1 */,
    ANDROID_FLASH_INFO_END = 327682u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_FLASH_INFO_CHARGE_DURATION implicitly + 1 */,
    /**
     * android.hotPixel.mode [dynamic, enum, public]
     *
     * <p>Operational mode for hot pixel correction.</p>
     */
    ANDROID_HOT_PIXEL_MODE = 393216u /* CameraMetadataSectionStart:ANDROID_HOT_PIXEL_START */,
    /**
     * android.hotPixel.availableHotPixelModes [static, byte[], public]
     *
     * <p>List of hot pixel correction modes for ANDROID_HOT_PIXEL_MODE that are supported by this
     * camera device.</p>
     *
     * @see ANDROID_HOT_PIXEL_MODE
     */
    ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES = 393217u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_HOT_PIXEL_MODE implicitly + 1 */,
    ANDROID_HOT_PIXEL_END = 393218u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES implicitly + 1 */,
    /**
     * android.jpeg.gpsCoordinates [dynamic, double[], ndk_public]
     *
     * <p>GPS coordinates to include in output JPEG
     * EXIF.</p>
     */
    ANDROID_JPEG_GPS_COORDINATES = 458752u /* CameraMetadataSectionStart:ANDROID_JPEG_START */,
    /**
     * android.jpeg.gpsProcessingMethod [dynamic, byte, ndk_public]
     *
     * <p>32 characters describing GPS algorithm to
     * include in EXIF.</p>
     */
    ANDROID_JPEG_GPS_PROCESSING_METHOD = 458753u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_GPS_COORDINATES implicitly + 1 */,
    /**
     * android.jpeg.gpsTimestamp [dynamic, int64, ndk_public]
     *
     * <p>Time GPS fix was made to include in
     * EXIF.</p>
     */
    ANDROID_JPEG_GPS_TIMESTAMP = 458754u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_GPS_PROCESSING_METHOD implicitly + 1 */,
    /**
     * android.jpeg.orientation [dynamic, int32, public]
     *
     * <p>The orientation for a JPEG image.</p>
     */
    ANDROID_JPEG_ORIENTATION = 458755u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_GPS_TIMESTAMP implicitly + 1 */,
    /**
     * android.jpeg.quality [dynamic, byte, public]
     *
     * <p>Compression quality of the final JPEG
     * image.</p>
     */
    ANDROID_JPEG_QUALITY = 458756u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_ORIENTATION implicitly + 1 */,
    /**
     * android.jpeg.thumbnailQuality [dynamic, byte, public]
     *
     * <p>Compression quality of JPEG
     * thumbnail.</p>
     */
    ANDROID_JPEG_THUMBNAIL_QUALITY = 458757u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_QUALITY implicitly + 1 */,
    /**
     * android.jpeg.thumbnailSize [dynamic, int32[], public]
     *
     * <p>Resolution of embedded JPEG thumbnail.</p>
     */
    ANDROID_JPEG_THUMBNAIL_SIZE = 458758u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_THUMBNAIL_QUALITY implicitly + 1 */,
    /**
     * android.jpeg.availableThumbnailSizes [static, int32[], public]
     *
     * <p>List of JPEG thumbnail sizes for ANDROID_JPEG_THUMBNAIL_SIZE supported by this
     * camera device.</p>
     *
     * @see ANDROID_JPEG_THUMBNAIL_SIZE
     */
    ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES = 458759u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_THUMBNAIL_SIZE implicitly + 1 */,
    /**
     * android.jpeg.maxSize [static, int32, system]
     *
     * <p>Maximum size in bytes for the compressed
     * JPEG buffer</p>
     */
    ANDROID_JPEG_MAX_SIZE = 458760u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES implicitly + 1 */,
    /**
     * android.jpeg.size [dynamic, int32, system]
     *
     * <p>The size of the compressed JPEG image, in
     * bytes</p>
     */
    ANDROID_JPEG_SIZE = 458761u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_MAX_SIZE implicitly + 1 */,
    ANDROID_JPEG_END = 458762u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_JPEG_SIZE implicitly + 1 */,
    /**
     * android.lens.aperture [dynamic, float, public]
     *
     * <p>The desired lens aperture size, as a ratio of lens focal length to the
     * effective aperture diameter.</p>
     */
    ANDROID_LENS_APERTURE = 524288u /* CameraMetadataSectionStart:ANDROID_LENS_START */,
    /**
     * android.lens.filterDensity [dynamic, float, public]
     *
     * <p>The desired setting for the lens neutral density filter(s).</p>
     */
    ANDROID_LENS_FILTER_DENSITY = 524289u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_APERTURE implicitly + 1 */,
    /**
     * android.lens.focalLength [dynamic, float, public]
     *
     * <p>The desired lens focal length; used for optical zoom.</p>
     */
    ANDROID_LENS_FOCAL_LENGTH = 524290u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_FILTER_DENSITY implicitly + 1 */,
    /**
     * android.lens.focusDistance [dynamic, float, public]
     *
     * <p>Desired distance to plane of sharpest focus,
     * measured from frontmost surface of the lens.</p>
     */
    ANDROID_LENS_FOCUS_DISTANCE = 524291u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_FOCAL_LENGTH implicitly + 1 */,
    /**
     * android.lens.opticalStabilizationMode [dynamic, enum, public]
     *
     * <p>Sets whether the camera device uses optical image stabilization (OIS)
     * when capturing images.</p>
     */
    ANDROID_LENS_OPTICAL_STABILIZATION_MODE = 524292u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_FOCUS_DISTANCE implicitly + 1 */,
    /**
     * android.lens.facing [static, enum, public]
     *
     * <p>Direction the camera faces relative to
     * device screen.</p>
     */
    ANDROID_LENS_FACING = 524293u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_OPTICAL_STABILIZATION_MODE implicitly + 1 */,
    /**
     * android.lens.poseRotation [dynamic, float[], public]
     *
     * <p>The orientation of the camera relative to the sensor
     * coordinate system.</p>
     */
    ANDROID_LENS_POSE_ROTATION = 524294u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_FACING implicitly + 1 */,
    /**
     * android.lens.poseTranslation [dynamic, float[], public]
     *
     * <p>Position of the camera optical center.</p>
     */
    ANDROID_LENS_POSE_TRANSLATION = 524295u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_POSE_ROTATION implicitly + 1 */,
    /**
     * android.lens.focusRange [dynamic, float[], public]
     *
     * <p>The range of scene distances that are in
     * sharp focus (depth of field).</p>
     */
    ANDROID_LENS_FOCUS_RANGE = 524296u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_POSE_TRANSLATION implicitly + 1 */,
    /**
     * android.lens.state [dynamic, enum, public]
     *
     * <p>Current lens status.</p>
     */
    ANDROID_LENS_STATE = 524297u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_FOCUS_RANGE implicitly + 1 */,
    /**
     * android.lens.intrinsicCalibration [dynamic, float[], public]
     *
     * <p>The parameters for this camera device's intrinsic
     * calibration.</p>
     */
    ANDROID_LENS_INTRINSIC_CALIBRATION = 524298u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_STATE implicitly + 1 */,
    /**
     * android.lens.radialDistortion [dynamic, float[], public]
     *
     * <p>The correction coefficients to correct for this camera device's
     * radial and tangential lens distortion.</p>
     */
    ANDROID_LENS_RADIAL_DISTORTION = 524299u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INTRINSIC_CALIBRATION implicitly + 1 */,
    ANDROID_LENS_END = 524300u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_RADIAL_DISTORTION implicitly + 1 */,
    /**
     * android.lens.info.availableApertures [static, float[], public]
     *
     * <p>List of aperture size values for ANDROID_LENS_APERTURE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_LENS_APERTURE
     */
    ANDROID_LENS_INFO_AVAILABLE_APERTURES = 589824u /* CameraMetadataSectionStart:ANDROID_LENS_INFO_START */,
    /**
     * android.lens.info.availableFilterDensities [static, float[], public]
     *
     * <p>List of neutral density filter values for
     * ANDROID_LENS_FILTER_DENSITY that are supported by this camera device.</p>
     *
     * @see ANDROID_LENS_FILTER_DENSITY
     */
    ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES = 589825u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_AVAILABLE_APERTURES implicitly + 1 */,
    /**
     * android.lens.info.availableFocalLengths [static, float[], public]
     *
     * <p>List of focal lengths for ANDROID_LENS_FOCAL_LENGTH that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_LENS_FOCAL_LENGTH
     */
    ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS = 589826u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES implicitly + 1 */,
    /**
     * android.lens.info.availableOpticalStabilization [static, byte[], public]
     *
     * <p>List of optical image stabilization (OIS) modes for
     * ANDROID_LENS_OPTICAL_STABILIZATION_MODE that are supported by this camera device.</p>
     *
     * @see ANDROID_LENS_OPTICAL_STABILIZATION_MODE
     */
    ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION = 589827u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS implicitly + 1 */,
    /**
     * android.lens.info.hyperfocalDistance [static, float, public]
     *
     * <p>Hyperfocal distance for this lens.</p>
     */
    ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE = 589828u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION implicitly + 1 */,
    /**
     * android.lens.info.minimumFocusDistance [static, float, public]
     *
     * <p>Shortest distance from frontmost surface
     * of the lens that can be brought into sharp focus.</p>
     */
    ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE = 589829u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE implicitly + 1 */,
    /**
     * android.lens.info.shadingMapSize [static, int32[], ndk_public]
     *
     * <p>Dimensions of lens shading map.</p>
     */
    ANDROID_LENS_INFO_SHADING_MAP_SIZE = 589830u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE implicitly + 1 */,
    /**
     * android.lens.info.focusDistanceCalibration [static, enum, public]
     *
     * <p>The lens focus distance calibration quality.</p>
     */
    ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION = 589831u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_SHADING_MAP_SIZE implicitly + 1 */,
    ANDROID_LENS_INFO_END = 589832u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION implicitly + 1 */,
    /**
     * android.noiseReduction.mode [dynamic, enum, public]
     *
     * <p>Mode of operation for the noise reduction algorithm.</p>
     */
    ANDROID_NOISE_REDUCTION_MODE = 655360u /* CameraMetadataSectionStart:ANDROID_NOISE_REDUCTION_START */,
    /**
     * android.noiseReduction.strength [controls, byte, system]
     *
     * <p>Control the amount of noise reduction
     * applied to the images</p>
     */
    ANDROID_NOISE_REDUCTION_STRENGTH = 655361u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_NOISE_REDUCTION_MODE implicitly + 1 */,
    /**
     * android.noiseReduction.availableNoiseReductionModes [static, byte[], public]
     *
     * <p>List of noise reduction modes for ANDROID_NOISE_REDUCTION_MODE that are supported
     * by this camera device.</p>
     *
     * @see ANDROID_NOISE_REDUCTION_MODE
     */
    ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES = 655362u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_NOISE_REDUCTION_STRENGTH implicitly + 1 */,
    ANDROID_NOISE_REDUCTION_END = 655363u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES implicitly + 1 */,
    /**
     * android.quirks.meteringCropRegion [static, byte, system]
     *
     * <p>If set to 1, the camera service does not
     * scale 'normalized' coordinates with respect to the crop
     * region. This applies to metering input (a{e,f,wb}Region
     * and output (face rectangles).</p>
     */
    ANDROID_QUIRKS_METERING_CROP_REGION = 720896u /* CameraMetadataSectionStart:ANDROID_QUIRKS_START */,
    /**
     * android.quirks.triggerAfWithAuto [static, byte, system]
     *
     * <p>If set to 1, then the camera service always
     * switches to FOCUS_MODE_AUTO before issuing a AF
     * trigger.</p>
     */
    ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO = 720897u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_QUIRKS_METERING_CROP_REGION implicitly + 1 */,
    /**
     * android.quirks.useZslFormat [static, byte, system]
     *
     * <p>If set to 1, the camera service uses
     * CAMERA2_PIXEL_FORMAT_ZSL instead of
     * HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED for the zero
     * shutter lag stream</p>
     */
    ANDROID_QUIRKS_USE_ZSL_FORMAT = 720898u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO implicitly + 1 */,
    /**
     * android.quirks.usePartialResult [static, byte, hidden]
     *
     * <p>If set to 1, the HAL will always split result
     * metadata for a single capture into multiple buffers,
     * returned using multiple process_capture_result calls.</p>
     */
    ANDROID_QUIRKS_USE_PARTIAL_RESULT = 720899u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_QUIRKS_USE_ZSL_FORMAT implicitly + 1 */,
    /**
     * android.quirks.partialResult [dynamic, enum, hidden]
     *
     * <p>Whether a result given to the framework is the
     * final one for the capture, or only a partial that contains a
     * subset of the full set of dynamic metadata
     * values.</p>
     */
    ANDROID_QUIRKS_PARTIAL_RESULT = 720900u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_QUIRKS_USE_PARTIAL_RESULT implicitly + 1 */,
    ANDROID_QUIRKS_END = 720901u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_QUIRKS_PARTIAL_RESULT implicitly + 1 */,
    /**
     * android.request.frameCount [dynamic, int32, hidden]
     *
     * <p>A frame counter set by the framework. This value monotonically
     * increases with every new result (that is, each new result has a unique
     * frameCount value).</p>
     */
    ANDROID_REQUEST_FRAME_COUNT = 786432u /* CameraMetadataSectionStart:ANDROID_REQUEST_START */,
    /**
     * android.request.id [dynamic, int32, hidden]
     *
     * <p>An application-specified ID for the current
     * request. Must be maintained unchanged in output
     * frame</p>
     */
    ANDROID_REQUEST_ID = 786433u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_FRAME_COUNT implicitly + 1 */,
    /**
     * android.request.inputStreams [controls, int32[], system]
     *
     * <p>List which camera reprocess stream is used
     * for the source of reprocessing data.</p>
     */
    ANDROID_REQUEST_INPUT_STREAMS = 786434u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_ID implicitly + 1 */,
    /**
     * android.request.metadataMode [dynamic, enum, system]
     *
     * <p>How much metadata to produce on
     * output</p>
     */
    ANDROID_REQUEST_METADATA_MODE = 786435u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_INPUT_STREAMS implicitly + 1 */,
    /**
     * android.request.outputStreams [dynamic, int32[], system]
     *
     * <p>Lists which camera output streams image data
     * from this capture must be sent to</p>
     */
    ANDROID_REQUEST_OUTPUT_STREAMS = 786436u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_METADATA_MODE implicitly + 1 */,
    /**
     * android.request.type [controls, enum, system]
     *
     * <p>The type of the request; either CAPTURE or
     * REPROCESS. For legacy HAL3, this tag is redundant.</p>
     */
    ANDROID_REQUEST_TYPE = 786437u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_OUTPUT_STREAMS implicitly + 1 */,
    /**
     * android.request.maxNumOutputStreams [static, int32[], ndk_public]
     *
     * <p>The maximum numbers of different types of output streams
     * that can be configured and used simultaneously by a camera device.</p>
     */
    ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS = 786438u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_TYPE implicitly + 1 */,
    /**
     * android.request.maxNumReprocessStreams [static, int32[], system]
     *
     * <p>How many reprocessing streams of any type
     * can be allocated at the same time.</p>
     */
    ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS = 786439u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS implicitly + 1 */,
    /**
     * android.request.maxNumInputStreams [static, int32, java_public]
     *
     * <p>The maximum numbers of any type of input streams
     * that can be configured and used simultaneously by a camera device.</p>
     */
    ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS = 786440u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS implicitly + 1 */,
    /**
     * android.request.pipelineDepth [dynamic, byte, public]
     *
     * <p>Specifies the number of pipeline stages the frame went
     * through from when it was exposed to when the final completed result
     * was available to the framework.</p>
     */
    ANDROID_REQUEST_PIPELINE_DEPTH = 786441u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS implicitly + 1 */,
    /**
     * android.request.pipelineMaxDepth [static, byte, public]
     *
     * <p>Specifies the number of maximum pipeline stages a frame
     * has to go through from when it's exposed to when it's available
     * to the framework.</p>
     */
    ANDROID_REQUEST_PIPELINE_MAX_DEPTH = 786442u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_PIPELINE_DEPTH implicitly + 1 */,
    /**
     * android.request.partialResultCount [static, int32, public]
     *
     * <p>Defines how many sub-components
     * a result will be composed of.</p>
     */
    ANDROID_REQUEST_PARTIAL_RESULT_COUNT = 786443u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_PIPELINE_MAX_DEPTH implicitly + 1 */,
    /**
     * android.request.availableCapabilities [static, enum[], public]
     *
     * <p>List of capabilities that this camera device
     * advertises as fully supporting.</p>
     */
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES = 786444u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_PARTIAL_RESULT_COUNT implicitly + 1 */,
    /**
     * android.request.availableRequestKeys [static, int32[], ndk_public]
     *
     * <p>A list of all keys that the camera device has available
     * to use with {@link ACaptureRequest }.</p>
     */
    ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS = 786445u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_CAPABILITIES implicitly + 1 */,
    /**
     * android.request.availableResultKeys [static, int32[], ndk_public]
     *
     * <p>A list of all keys that the camera device has available to use with {@link ACameraCaptureSession_captureCallback_result }.</p>
     */
    ANDROID_REQUEST_AVAILABLE_RESULT_KEYS = 786446u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS implicitly + 1 */,
    /**
     * android.request.availableCharacteristicsKeys [static, int32[], ndk_public]
     *
     * <p>A list of all keys that the camera device has available to use with {@link ACameraManager_getCameraCharacteristics }.</p>
     */
    ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS = 786447u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_RESULT_KEYS implicitly + 1 */,
    ANDROID_REQUEST_END = 786448u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS implicitly + 1 */,
    /**
     * android.scaler.cropRegion [dynamic, int32[], public]
     *
     * <p>The desired region of the sensor to read out for this capture.</p>
     */
    ANDROID_SCALER_CROP_REGION = 851968u /* CameraMetadataSectionStart:ANDROID_SCALER_START */,
    /**
     * android.scaler.availableFormats [static, enum[], hidden]
     *
     * <p>The list of image formats that are supported by this
     * camera device for output streams.</p>
     */
    ANDROID_SCALER_AVAILABLE_FORMATS = 851969u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_CROP_REGION implicitly + 1 */,
    /**
     * android.scaler.availableJpegMinDurations [static, int64[], hidden]
     *
     * <p>The minimum frame duration that is supported
     * for each resolution in ANDROID_SCALER_AVAILABLE_JPEG_SIZES.</p>
     *
     * @see ANDROID_SCALER_AVAILABLE_JPEG_SIZES
     */
    ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS = 851970u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_FORMATS implicitly + 1 */,
    /**
     * android.scaler.availableJpegSizes [static, int32[], hidden]
     *
     * <p>The JPEG resolutions that are supported by this camera device.</p>
     */
    ANDROID_SCALER_AVAILABLE_JPEG_SIZES = 851971u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS implicitly + 1 */,
    /**
     * android.scaler.availableMaxDigitalZoom [static, float, public]
     *
     * <p>The maximum ratio between both active area width
     * and crop region width, and active area height and
     * crop region height, for ANDROID_SCALER_CROP_REGION.</p>
     *
     * @see ANDROID_SCALER_CROP_REGION
     */
    ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM = 851972u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_JPEG_SIZES implicitly + 1 */,
    /**
     * android.scaler.availableProcessedMinDurations [static, int64[], hidden]
     *
     * <p>For each available processed output size (defined in
     * ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES), this property lists the
     * minimum supportable frame duration for that size.</p>
     *
     * @see ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES
     */
    ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS = 851973u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM implicitly + 1 */,
    /**
     * android.scaler.availableProcessedSizes [static, int32[], hidden]
     *
     * <p>The resolutions available for use with
     * processed output streams, such as YV12, NV12, and
     * platform opaque YUV/RGB streams to the GPU or video
     * encoders.</p>
     */
    ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES = 851974u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS implicitly + 1 */,
    /**
     * android.scaler.availableRawMinDurations [static, int64[], system]
     *
     * <p>For each available raw output size (defined in
     * ANDROID_SCALER_AVAILABLE_RAW_SIZES), this property lists the minimum
     * supportable frame duration for that size.</p>
     *
     * @see ANDROID_SCALER_AVAILABLE_RAW_SIZES
     */
    ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS = 851975u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES implicitly + 1 */,
    /**
     * android.scaler.availableRawSizes [static, int32[], system]
     *
     * <p>The resolutions available for use with raw
     * sensor output streams, listed as width,
     * height</p>
     */
    ANDROID_SCALER_AVAILABLE_RAW_SIZES = 851976u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS implicitly + 1 */,
    /**
     * android.scaler.availableInputOutputFormatsMap [static, int32, hidden]
     *
     * <p>The mapping of image formats that are supported by this
     * camera device for input streams, to their corresponding output formats.</p>
     */
    ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP = 851977u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_RAW_SIZES implicitly + 1 */,
    /**
     * android.scaler.availableStreamConfigurations [static, enum[], ndk_public]
     *
     * <p>The available stream configurations that this
     * camera device supports
     * (i.e. format, width, height, output/input stream).</p>
     */
    ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS = 851978u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP implicitly + 1 */,
    /**
     * android.scaler.availableMinFrameDurations [static, int64[], ndk_public]
     *
     * <p>This lists the minimum frame duration for each
     * format/size combination.</p>
     */
    ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS = 851979u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.scaler.availableStallDurations [static, int64[], ndk_public]
     *
     * <p>This lists the maximum stall duration for each
     * output format/size combination.</p>
     */
    ANDROID_SCALER_AVAILABLE_STALL_DURATIONS = 851980u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS implicitly + 1 */,
    /**
     * android.scaler.croppingType [static, enum, public]
     *
     * <p>The crop type that this camera device supports.</p>
     */
    ANDROID_SCALER_CROPPING_TYPE = 851981u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_STALL_DURATIONS implicitly + 1 */,
    ANDROID_SCALER_END = 851982u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SCALER_CROPPING_TYPE implicitly + 1 */,
    /**
     * android.sensor.exposureTime [dynamic, int64, public]
     *
     * <p>Duration each pixel is exposed to
     * light.</p>
     */
    ANDROID_SENSOR_EXPOSURE_TIME = 917504u /* CameraMetadataSectionStart:ANDROID_SENSOR_START */,
    /**
     * android.sensor.frameDuration [dynamic, int64, public]
     *
     * <p>Duration from start of frame exposure to
     * start of next frame exposure.</p>
     */
    ANDROID_SENSOR_FRAME_DURATION = 917505u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_EXPOSURE_TIME implicitly + 1 */,
    /**
     * android.sensor.sensitivity [dynamic, int32, public]
     *
     * <p>The amount of gain applied to sensor data
     * before processing.</p>
     */
    ANDROID_SENSOR_SENSITIVITY = 917506u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_FRAME_DURATION implicitly + 1 */,
    /**
     * android.sensor.referenceIlluminant1 [static, enum, public]
     *
     * <p>The standard reference illuminant used as the scene light source when
     * calculating the ANDROID_SENSOR_COLOR_TRANSFORM1,
     * ANDROID_SENSOR_CALIBRATION_TRANSFORM1, and
     * ANDROID_SENSOR_FORWARD_MATRIX1 matrices.</p>
     *
     * @see ANDROID_SENSOR_CALIBRATION_TRANSFORM1
     * @see ANDROID_SENSOR_COLOR_TRANSFORM1
     * @see ANDROID_SENSOR_FORWARD_MATRIX1
     */
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1 = 917507u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_SENSITIVITY implicitly + 1 */,
    /**
     * android.sensor.referenceIlluminant2 [static, byte, public]
     *
     * <p>The standard reference illuminant used as the scene light source when
     * calculating the ANDROID_SENSOR_COLOR_TRANSFORM2,
     * ANDROID_SENSOR_CALIBRATION_TRANSFORM2, and
     * ANDROID_SENSOR_FORWARD_MATRIX2 matrices.</p>
     *
     * @see ANDROID_SENSOR_CALIBRATION_TRANSFORM2
     * @see ANDROID_SENSOR_COLOR_TRANSFORM2
     * @see ANDROID_SENSOR_FORWARD_MATRIX2
     */
    ANDROID_SENSOR_REFERENCE_ILLUMINANT2 = 917508u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_REFERENCE_ILLUMINANT1 implicitly + 1 */,
    /**
     * android.sensor.calibrationTransform1 [static, rational[], public]
     *
     * <p>A per-device calibration transform matrix that maps from the
     * reference sensor colorspace to the actual device sensor colorspace.</p>
     */
    ANDROID_SENSOR_CALIBRATION_TRANSFORM1 = 917509u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_REFERENCE_ILLUMINANT2 implicitly + 1 */,
    /**
     * android.sensor.calibrationTransform2 [static, rational[], public]
     *
     * <p>A per-device calibration transform matrix that maps from the
     * reference sensor colorspace to the actual device sensor colorspace
     * (this is the colorspace of the raw buffer data).</p>
     */
    ANDROID_SENSOR_CALIBRATION_TRANSFORM2 = 917510u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_CALIBRATION_TRANSFORM1 implicitly + 1 */,
    /**
     * android.sensor.colorTransform1 [static, rational[], public]
     *
     * <p>A matrix that transforms color values from CIE XYZ color space to
     * reference sensor color space.</p>
     */
    ANDROID_SENSOR_COLOR_TRANSFORM1 = 917511u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_CALIBRATION_TRANSFORM2 implicitly + 1 */,
    /**
     * android.sensor.colorTransform2 [static, rational[], public]
     *
     * <p>A matrix that transforms color values from CIE XYZ color space to
     * reference sensor color space.</p>
     */
    ANDROID_SENSOR_COLOR_TRANSFORM2 = 917512u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_COLOR_TRANSFORM1 implicitly + 1 */,
    /**
     * android.sensor.forwardMatrix1 [static, rational[], public]
     *
     * <p>A matrix that transforms white balanced camera colors from the reference
     * sensor colorspace to the CIE XYZ colorspace with a D50 whitepoint.</p>
     */
    ANDROID_SENSOR_FORWARD_MATRIX1 = 917513u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_COLOR_TRANSFORM2 implicitly + 1 */,
    /**
     * android.sensor.forwardMatrix2 [static, rational[], public]
     *
     * <p>A matrix that transforms white balanced camera colors from the reference
     * sensor colorspace to the CIE XYZ colorspace with a D50 whitepoint.</p>
     */
    ANDROID_SENSOR_FORWARD_MATRIX2 = 917514u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_FORWARD_MATRIX1 implicitly + 1 */,
    /**
     * android.sensor.baseGainFactor [static, rational, system]
     *
     * <p>Gain factor from electrons to raw units when
     * ISO=100</p>
     */
    ANDROID_SENSOR_BASE_GAIN_FACTOR = 917515u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_FORWARD_MATRIX2 implicitly + 1 */,
    /**
     * android.sensor.blackLevelPattern [static, int32[], public]
     *
     * <p>A fixed black level offset for each of the color filter arrangement
     * (CFA) mosaic channels.</p>
     */
    ANDROID_SENSOR_BLACK_LEVEL_PATTERN = 917516u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_BASE_GAIN_FACTOR implicitly + 1 */,
    /**
     * android.sensor.maxAnalogSensitivity [static, int32, public]
     *
     * <p>Maximum sensitivity that is implemented
     * purely through analog gain.</p>
     */
    ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY = 917517u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_BLACK_LEVEL_PATTERN implicitly + 1 */,
    /**
     * android.sensor.orientation [static, int32, public]
     *
     * <p>Clockwise angle through which the output image needs to be rotated to be
     * upright on the device screen in its native orientation.</p>
     */
    ANDROID_SENSOR_ORIENTATION = 917518u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY implicitly + 1 */,
    /**
     * android.sensor.profileHueSatMapDimensions [static, int32[], system]
     *
     * <p>The number of input samples for each dimension of
     * ANDROID_SENSOR_PROFILE_HUE_SAT_MAP.</p>
     *
     * @see ANDROID_SENSOR_PROFILE_HUE_SAT_MAP
     */
    ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS = 917519u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_ORIENTATION implicitly + 1 */,
    /**
     * android.sensor.timestamp [dynamic, int64, public]
     *
     * <p>Time at start of exposure of first
     * row of the image sensor active array, in nanoseconds.</p>
     */
    ANDROID_SENSOR_TIMESTAMP = 917520u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS implicitly + 1 */,
    /**
     * android.sensor.temperature [dynamic, float, system]
     *
     * <p>The temperature of the sensor, sampled at the time
     * exposure began for this frame.</p>
     * <p>The thermal diode being queried should be inside the sensor PCB, or
     * somewhere close to it.</p>
     */
    ANDROID_SENSOR_TEMPERATURE = 917521u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_TIMESTAMP implicitly + 1 */,
    /**
     * android.sensor.neutralColorPoint [dynamic, rational[], public]
     *
     * <p>The estimated camera neutral color in the native sensor colorspace at
     * the time of capture.</p>
     */
    ANDROID_SENSOR_NEUTRAL_COLOR_POINT = 917522u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_TEMPERATURE implicitly + 1 */,
    /**
     * android.sensor.noiseProfile [dynamic, double[], public]
     *
     * <p>Noise model coefficients for each CFA mosaic channel.</p>
     */
    ANDROID_SENSOR_NOISE_PROFILE = 917523u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_NEUTRAL_COLOR_POINT implicitly + 1 */,
    /**
     * android.sensor.profileHueSatMap [dynamic, float[], system]
     *
     * <p>A mapping containing a hue shift, saturation scale, and value scale
     * for each pixel.</p>
     */
    ANDROID_SENSOR_PROFILE_HUE_SAT_MAP = 917524u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_NOISE_PROFILE implicitly + 1 */,
    /**
     * android.sensor.profileToneCurve [dynamic, float[], system]
     *
     * <p>A list of x,y samples defining a tone-mapping curve for gamma adjustment.</p>
     */
    ANDROID_SENSOR_PROFILE_TONE_CURVE = 917525u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_PROFILE_HUE_SAT_MAP implicitly + 1 */,
    /**
     * android.sensor.greenSplit [dynamic, float, public]
     *
     * <p>The worst-case divergence between Bayer green channels.</p>
     */
    ANDROID_SENSOR_GREEN_SPLIT = 917526u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_PROFILE_TONE_CURVE implicitly + 1 */,
    /**
     * android.sensor.testPatternData [dynamic, int32[], public]
     *
     * <p>A pixel <code>[R, G_even, G_odd, B]</code> that supplies the test pattern
     * when ANDROID_SENSOR_TEST_PATTERN_MODE is SOLID_COLOR.</p>
     *
     * @see ANDROID_SENSOR_TEST_PATTERN_MODE
     */
    ANDROID_SENSOR_TEST_PATTERN_DATA = 917527u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_GREEN_SPLIT implicitly + 1 */,
    /**
     * android.sensor.testPatternMode [dynamic, enum, public]
     *
     * <p>When enabled, the sensor sends a test pattern instead of
     * doing a real exposure from the camera.</p>
     */
    ANDROID_SENSOR_TEST_PATTERN_MODE = 917528u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_TEST_PATTERN_DATA implicitly + 1 */,
    /**
     * android.sensor.availableTestPatternModes [static, int32[], public]
     *
     * <p>List of sensor test pattern modes for ANDROID_SENSOR_TEST_PATTERN_MODE
     * supported by this camera device.</p>
     *
     * @see ANDROID_SENSOR_TEST_PATTERN_MODE
     */
    ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES = 917529u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_TEST_PATTERN_MODE implicitly + 1 */,
    /**
     * android.sensor.rollingShutterSkew [dynamic, int64, public]
     *
     * <p>Duration between the start of exposure for the first row of the image sensor,
     * and the start of exposure for one past the last row of the image sensor.</p>
     */
    ANDROID_SENSOR_ROLLING_SHUTTER_SKEW = 917530u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES implicitly + 1 */,
    /**
     * android.sensor.opticalBlackRegions [static, int32[], public]
     *
     * <p>List of disjoint rectangles indicating the sensor
     * optically shielded black pixel regions.</p>
     */
    ANDROID_SENSOR_OPTICAL_BLACK_REGIONS = 917531u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_ROLLING_SHUTTER_SKEW implicitly + 1 */,
    /**
     * android.sensor.dynamicBlackLevel [dynamic, float[], public]
     *
     * <p>A per-frame dynamic black level offset for each of the color filter
     * arrangement (CFA) mosaic channels.</p>
     */
    ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL = 917532u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_OPTICAL_BLACK_REGIONS implicitly + 1 */,
    /**
     * android.sensor.dynamicWhiteLevel [dynamic, int32, public]
     *
     * <p>Maximum raw value output by sensor for this frame.</p>
     */
    ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL = 917533u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL implicitly + 1 */,
    /**
     * android.sensor.opaqueRawSize [static, int32[], system]
     *
     * <p>Size in bytes for all the listed opaque RAW buffer sizes</p>
     */
    ANDROID_SENSOR_OPAQUE_RAW_SIZE = 917534u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL implicitly + 1 */,
    ANDROID_SENSOR_END = 917535u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_OPAQUE_RAW_SIZE implicitly + 1 */,
    /**
     * android.sensor.info.activeArraySize [static, int32[], public]
     *
     * <p>The area of the image sensor which corresponds to active pixels after any geometric
     * distortion correction has been applied.</p>
     */
    ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE = 983040u /* CameraMetadataSectionStart:ANDROID_SENSOR_INFO_START */,
    /**
     * android.sensor.info.sensitivityRange [static, int32[], public]
     *
     * <p>Range of sensitivities for ANDROID_SENSOR_SENSITIVITY supported by this
     * camera device.</p>
     *
     * @see ANDROID_SENSOR_SENSITIVITY
     */
    ANDROID_SENSOR_INFO_SENSITIVITY_RANGE = 983041u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE implicitly + 1 */,
    /**
     * android.sensor.info.colorFilterArrangement [static, enum, public]
     *
     * <p>The arrangement of color filters on sensor;
     * represents the colors in the top-left 2x2 section of
     * the sensor, in reading order, for a Bayer camera, or the
     * light spectrum it captures for MONOCHROME camera.</p>
     */
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT = 983042u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_SENSITIVITY_RANGE implicitly + 1 */,
    /**
     * android.sensor.info.exposureTimeRange [static, int64[], public]
     *
     * <p>The range of image exposure times for ANDROID_SENSOR_EXPOSURE_TIME supported
     * by this camera device.</p>
     *
     * @see ANDROID_SENSOR_EXPOSURE_TIME
     */
    ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE = 983043u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT implicitly + 1 */,
    /**
     * android.sensor.info.maxFrameDuration [static, int64, public]
     *
     * <p>The maximum possible frame duration (minimum frame rate) for
     * ANDROID_SENSOR_FRAME_DURATION that is supported this camera device.</p>
     *
     * @see ANDROID_SENSOR_FRAME_DURATION
     */
    ANDROID_SENSOR_INFO_MAX_FRAME_DURATION = 983044u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE implicitly + 1 */,
    /**
     * android.sensor.info.physicalSize [static, float[], public]
     *
     * <p>The physical dimensions of the full pixel
     * array.</p>
     */
    ANDROID_SENSOR_INFO_PHYSICAL_SIZE = 983045u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_MAX_FRAME_DURATION implicitly + 1 */,
    /**
     * android.sensor.info.pixelArraySize [static, int32[], public]
     *
     * <p>Dimensions of the full pixel array, possibly
     * including black calibration pixels.</p>
     */
    ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE = 983046u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_PHYSICAL_SIZE implicitly + 1 */,
    /**
     * android.sensor.info.whiteLevel [static, int32, public]
     *
     * <p>Maximum raw value output by sensor.</p>
     */
    ANDROID_SENSOR_INFO_WHITE_LEVEL = 983047u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE implicitly + 1 */,
    /**
     * android.sensor.info.timestampSource [static, enum, public]
     *
     * <p>The time base source for sensor capture start timestamps.</p>
     */
    ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE = 983048u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_WHITE_LEVEL implicitly + 1 */,
    /**
     * android.sensor.info.lensShadingApplied [static, enum, public]
     *
     * <p>Whether the RAW images output from this camera device are subject to
     * lens shading correction.</p>
     */
    ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED = 983049u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE implicitly + 1 */,
    /**
     * android.sensor.info.preCorrectionActiveArraySize [static, int32[], public]
     *
     * <p>The area of the image sensor which corresponds to active pixels prior to the
     * application of any geometric distortion correction.</p>
     */
    ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE = 983050u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED implicitly + 1 */,
    ANDROID_SENSOR_INFO_END = 983051u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE implicitly + 1 */,
    /**
     * android.shading.mode [dynamic, enum, public]
     *
     * <p>Quality of lens shading correction applied
     * to the image data.</p>
     */
    ANDROID_SHADING_MODE = 1048576u /* CameraMetadataSectionStart:ANDROID_SHADING_START */,
    /**
     * android.shading.strength [controls, byte, system]
     *
     * <p>Control the amount of shading correction
     * applied to the images</p>
     */
    ANDROID_SHADING_STRENGTH = 1048577u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SHADING_MODE implicitly + 1 */,
    /**
     * android.shading.availableModes [static, byte[], public]
     *
     * <p>List of lens shading modes for ANDROID_SHADING_MODE that are supported by this camera device.</p>
     *
     * @see ANDROID_SHADING_MODE
     */
    ANDROID_SHADING_AVAILABLE_MODES = 1048578u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SHADING_STRENGTH implicitly + 1 */,
    ANDROID_SHADING_END = 1048579u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SHADING_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.statistics.faceDetectMode [dynamic, enum, public]
     *
     * <p>Operating mode for the face detector
     * unit.</p>
     */
    ANDROID_STATISTICS_FACE_DETECT_MODE = 1114112u /* CameraMetadataSectionStart:ANDROID_STATISTICS_START */,
    /**
     * android.statistics.histogramMode [dynamic, enum, system]
     *
     * <p>Operating mode for histogram
     * generation</p>
     */
    ANDROID_STATISTICS_HISTOGRAM_MODE = 1114113u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_FACE_DETECT_MODE implicitly + 1 */,
    /**
     * android.statistics.sharpnessMapMode [dynamic, enum, system]
     *
     * <p>Operating mode for sharpness map
     * generation</p>
     */
    ANDROID_STATISTICS_SHARPNESS_MAP_MODE = 1114114u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_HISTOGRAM_MODE implicitly + 1 */,
    /**
     * android.statistics.hotPixelMapMode [dynamic, enum, public]
     *
     * <p>Operating mode for hot pixel map generation.</p>
     */
    ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE = 1114115u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_SHARPNESS_MAP_MODE implicitly + 1 */,
    /**
     * android.statistics.faceIds [dynamic, int32[], ndk_public]
     *
     * <p>List of unique IDs for detected faces.</p>
     */
    ANDROID_STATISTICS_FACE_IDS = 1114116u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE implicitly + 1 */,
    /**
     * android.statistics.faceLandmarks [dynamic, int32[], ndk_public]
     *
     * <p>List of landmarks for detected
     * faces.</p>
     */
    ANDROID_STATISTICS_FACE_LANDMARKS = 1114117u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_FACE_IDS implicitly + 1 */,
    /**
     * android.statistics.faceRectangles [dynamic, int32[], ndk_public]
     *
     * <p>List of the bounding rectangles for detected
     * faces.</p>
     */
    ANDROID_STATISTICS_FACE_RECTANGLES = 1114118u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_FACE_LANDMARKS implicitly + 1 */,
    /**
     * android.statistics.faceScores [dynamic, byte[], ndk_public]
     *
     * <p>List of the face confidence scores for
     * detected faces</p>
     */
    ANDROID_STATISTICS_FACE_SCORES = 1114119u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_FACE_RECTANGLES implicitly + 1 */,
    /**
     * android.statistics.histogram [dynamic, int32[], system]
     *
     * <p>A 3-channel histogram based on the raw
     * sensor data</p>
     */
    ANDROID_STATISTICS_HISTOGRAM = 1114120u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_FACE_SCORES implicitly + 1 */,
    /**
     * android.statistics.sharpnessMap [dynamic, int32[], system]
     *
     * <p>A 3-channel sharpness map, based on the raw
     * sensor data</p>
     */
    ANDROID_STATISTICS_SHARPNESS_MAP = 1114121u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_HISTOGRAM implicitly + 1 */,
    /**
     * android.statistics.lensShadingCorrectionMap [dynamic, byte, java_public]
     *
     * <p>The shading map is a low-resolution floating-point map
     * that lists the coefficients used to correct for vignetting, for each
     * Bayer color channel.</p>
     */
    ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP = 1114122u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_SHARPNESS_MAP implicitly + 1 */,
    /**
     * android.statistics.lensShadingMap [dynamic, float[], ndk_public]
     *
     * <p>The shading map is a low-resolution floating-point map
     * that lists the coefficients used to correct for vignetting and color shading,
     * for each Bayer color channel of RAW image data.</p>
     */
    ANDROID_STATISTICS_LENS_SHADING_MAP = 1114123u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP implicitly + 1 */,
    /**
     * android.statistics.predictedColorGains [dynamic, float[], hidden]
     *
     * <p>The best-fit color channel gains calculated
     * by the camera device's statistics units for the current output frame.</p>
     */
    ANDROID_STATISTICS_PREDICTED_COLOR_GAINS = 1114124u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_LENS_SHADING_MAP implicitly + 1 */,
    /**
     * android.statistics.predictedColorTransform [dynamic, rational[], hidden]
     *
     * <p>The best-fit color transform matrix estimate
     * calculated by the camera device's statistics units for the current
     * output frame.</p>
     */
    ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM = 1114125u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_PREDICTED_COLOR_GAINS implicitly + 1 */,
    /**
     * android.statistics.sceneFlicker [dynamic, enum, public]
     *
     * <p>The camera device estimated scene illumination lighting
     * frequency.</p>
     */
    ANDROID_STATISTICS_SCENE_FLICKER = 1114126u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM implicitly + 1 */,
    /**
     * android.statistics.hotPixelMap [dynamic, int32[], public]
     *
     * <p>List of <code>(x, y)</code> coordinates of hot/defective pixels on the sensor.</p>
     */
    ANDROID_STATISTICS_HOT_PIXEL_MAP = 1114127u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_SCENE_FLICKER implicitly + 1 */,
    /**
     * android.statistics.lensShadingMapMode [dynamic, enum, public]
     *
     * <p>Whether the camera device will output the lens
     * shading map in output result metadata.</p>
     */
    ANDROID_STATISTICS_LENS_SHADING_MAP_MODE = 1114128u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_HOT_PIXEL_MAP implicitly + 1 */,
    ANDROID_STATISTICS_END = 1114129u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_LENS_SHADING_MAP_MODE implicitly + 1 */,
    /**
     * android.statistics.info.availableFaceDetectModes [static, byte[], public]
     *
     * <p>List of face detection modes for ANDROID_STATISTICS_FACE_DETECT_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_STATISTICS_FACE_DETECT_MODE
     */
    ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES = 1179648u /* CameraMetadataSectionStart:ANDROID_STATISTICS_INFO_START */,
    /**
     * android.statistics.info.histogramBucketCount [static, int32, system]
     *
     * <p>Number of histogram buckets
     * supported</p>
     */
    ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT = 1179649u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES implicitly + 1 */,
    /**
     * android.statistics.info.maxFaceCount [static, int32, public]
     *
     * <p>The maximum number of simultaneously detectable
     * faces.</p>
     */
    ANDROID_STATISTICS_INFO_MAX_FACE_COUNT = 1179650u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT implicitly + 1 */,
    /**
     * android.statistics.info.maxHistogramCount [static, int32, system]
     *
     * <p>Maximum value possible for a histogram
     * bucket</p>
     */
    ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT = 1179651u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_MAX_FACE_COUNT implicitly + 1 */,
    /**
     * android.statistics.info.maxSharpnessMapValue [static, int32, system]
     *
     * <p>Maximum value possible for a sharpness map
     * region.</p>
     */
    ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE = 1179652u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT implicitly + 1 */,
    /**
     * android.statistics.info.sharpnessMapSize [static, int32[], system]
     *
     * <p>Dimensions of the sharpness
     * map</p>
     */
    ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE = 1179653u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE implicitly + 1 */,
    /**
     * android.statistics.info.availableHotPixelMapModes [static, byte[], public]
     *
     * <p>List of hot pixel map output modes for ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE
     */
    ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES = 1179654u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE implicitly + 1 */,
    /**
     * android.statistics.info.availableLensShadingMapModes [static, byte[], public]
     *
     * <p>List of lens shading map output modes for ANDROID_STATISTICS_LENS_SHADING_MAP_MODE that
     * are supported by this camera device.</p>
     *
     * @see ANDROID_STATISTICS_LENS_SHADING_MAP_MODE
     */
    ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES = 1179655u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES implicitly + 1 */,
    ANDROID_STATISTICS_INFO_END = 1179656u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES implicitly + 1 */,
    /**
     * android.tonemap.curveBlue [dynamic, float[], ndk_public]
     *
     * <p>Tonemapping / contrast / gamma curve for the blue
     * channel, to use when ANDROID_TONEMAP_MODE is
     * CONTRAST_CURVE.</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_CURVE_BLUE = 1245184u /* CameraMetadataSectionStart:ANDROID_TONEMAP_START */,
    /**
     * android.tonemap.curveGreen [dynamic, float[], ndk_public]
     *
     * <p>Tonemapping / contrast / gamma curve for the green
     * channel, to use when ANDROID_TONEMAP_MODE is
     * CONTRAST_CURVE.</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_CURVE_GREEN = 1245185u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_CURVE_BLUE implicitly + 1 */,
    /**
     * android.tonemap.curveRed [dynamic, float[], ndk_public]
     *
     * <p>Tonemapping / contrast / gamma curve for the red
     * channel, to use when ANDROID_TONEMAP_MODE is
     * CONTRAST_CURVE.</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_CURVE_RED = 1245186u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_CURVE_GREEN implicitly + 1 */,
    /**
     * android.tonemap.mode [dynamic, enum, public]
     *
     * <p>High-level global contrast/gamma/tonemapping control.</p>
     */
    ANDROID_TONEMAP_MODE = 1245187u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_CURVE_RED implicitly + 1 */,
    /**
     * android.tonemap.maxCurvePoints [static, int32, public]
     *
     * <p>Maximum number of supported points in the
     * tonemap curve that can be used for ANDROID_TONEMAP_CURVE.</p>
     *
     * @see ANDROID_TONEMAP_CURVE
     */
    ANDROID_TONEMAP_MAX_CURVE_POINTS = 1245188u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_MODE implicitly + 1 */,
    /**
     * android.tonemap.availableToneMapModes [static, byte[], public]
     *
     * <p>List of tonemapping modes for ANDROID_TONEMAP_MODE that are supported by this camera
     * device.</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES = 1245189u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_MAX_CURVE_POINTS implicitly + 1 */,
    /**
     * android.tonemap.gamma [dynamic, float, public]
     *
     * <p>Tonemapping curve to use when ANDROID_TONEMAP_MODE is
     * GAMMA_VALUE</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_GAMMA = 1245190u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES implicitly + 1 */,
    /**
     * android.tonemap.presetCurve [dynamic, enum, public]
     *
     * <p>Tonemapping curve to use when ANDROID_TONEMAP_MODE is
     * PRESET_CURVE</p>
     *
     * @see ANDROID_TONEMAP_MODE
     */
    ANDROID_TONEMAP_PRESET_CURVE = 1245191u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_GAMMA implicitly + 1 */,
    ANDROID_TONEMAP_END = 1245192u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_TONEMAP_PRESET_CURVE implicitly + 1 */,
    /**
     * android.led.transmit [dynamic, enum, hidden]
     *
     * <p>This LED is nominally used to indicate to the user
     * that the camera is powered on and may be streaming images back to the
     * Application Processor. In certain rare circumstances, the OS may
     * disable this when video is processed locally and not transmitted to
     * any untrusted applications.</p>
     * <p>In particular, the LED <em>must</em> always be on when the data could be
     * transmitted off the device. The LED <em>should</em> always be on whenever
     * data is stored locally on the device.</p>
     * <p>The LED <em>may</em> be off if a trusted application is using the data that
     * doesn't violate the above rules.</p>
     */
    ANDROID_LED_TRANSMIT = 1310720u /* CameraMetadataSectionStart:ANDROID_LED_START */,
    /**
     * android.led.availableLeds [static, enum[], hidden]
     *
     * <p>A list of camera LEDs that are available on this system.</p>
     */
    ANDROID_LED_AVAILABLE_LEDS = 1310721u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LED_TRANSMIT implicitly + 1 */,
    ANDROID_LED_END = 1310722u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_LED_AVAILABLE_LEDS implicitly + 1 */,
    /**
     * android.info.supportedHardwareLevel [static, enum, public]
     *
     * <p>Generally classifies the overall set of the camera device functionality.</p>
     */
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL = 1376256u /* CameraMetadataSectionStart:ANDROID_INFO_START */,
    ANDROID_INFO_END = 1376257u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL implicitly + 1 */,
    /**
     * android.blackLevel.lock [dynamic, enum, public]
     *
     * <p>Whether black-level compensation is locked
     * to its current values, or is free to vary.</p>
     */
    ANDROID_BLACK_LEVEL_LOCK = 1441792u /* CameraMetadataSectionStart:ANDROID_BLACK_LEVEL_START */,
    ANDROID_BLACK_LEVEL_END = 1441793u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_BLACK_LEVEL_LOCK implicitly + 1 */,
    /**
     * android.sync.frameNumber [dynamic, enum, ndk_public]
     *
     * <p>The frame number corresponding to the last request
     * with which the output result (metadata + buffers) has been fully
     * synchronized.</p>
     */
    ANDROID_SYNC_FRAME_NUMBER = 1507328u /* CameraMetadataSectionStart:ANDROID_SYNC_START */,
    /**
     * android.sync.maxLatency [static, enum, public]
     *
     * <p>The maximum number of frames that can occur after a request
     * (different than the previous) has been submitted, and before the
     * result's state becomes synchronized.</p>
     */
    ANDROID_SYNC_MAX_LATENCY = 1507329u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SYNC_FRAME_NUMBER implicitly + 1 */,
    ANDROID_SYNC_END = 1507330u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_SYNC_MAX_LATENCY implicitly + 1 */,
    /**
     * android.reprocess.effectiveExposureFactor [dynamic, float, java_public]
     *
     * <p>The amount of exposure time increase factor applied to the original output
     * frame by the application processing before sending for reprocessing.</p>
     */
    ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR = 1572864u /* CameraMetadataSectionStart:ANDROID_REPROCESS_START */,
    /**
     * android.reprocess.maxCaptureStall [static, int32, java_public]
     *
     * <p>The maximal camera capture pipeline stall (in unit of frame count) introduced by a
     * reprocess capture request.</p>
     */
    ANDROID_REPROCESS_MAX_CAPTURE_STALL = 1572865u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR implicitly + 1 */,
    ANDROID_REPROCESS_END = 1572866u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_REPROCESS_MAX_CAPTURE_STALL implicitly + 1 */,
    /**
     * android.depth.maxDepthSamples [static, int32, system]
     *
     * <p>Maximum number of points that a depth point cloud may contain.</p>
     */
    ANDROID_DEPTH_MAX_DEPTH_SAMPLES = 1638400u /* CameraMetadataSectionStart:ANDROID_DEPTH_START */,
    /**
     * android.depth.availableDepthStreamConfigurations [static, enum[], ndk_public]
     *
     * <p>The available depth dataspace stream
     * configurations that this camera device supports
     * (i.e. format, width, height, output/input stream).</p>
     */
    ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS = 1638401u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEPTH_MAX_DEPTH_SAMPLES implicitly + 1 */,
    /**
     * android.depth.availableDepthMinFrameDurations [static, int64[], ndk_public]
     *
     * <p>This lists the minimum frame duration for each
     * format/size combination for depth output formats.</p>
     */
    ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS = 1638402u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.depth.availableDepthStallDurations [static, int64[], ndk_public]
     *
     * <p>This lists the maximum stall duration for each
     * output format/size combination for depth streams.</p>
     */
    ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS = 1638403u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS implicitly + 1 */,
    /**
     * android.depth.depthIsExclusive [static, enum, public]
     *
     * <p>Indicates whether a capture request may target both a
     * DEPTH16 / DEPTH_POINT_CLOUD output, and normal color outputs (such as
     * YUV_420_888, JPEG, or RAW) simultaneously.</p>
     */
    ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE = 1638404u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS implicitly + 1 */,
    ANDROID_DEPTH_END = 1638405u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataTag.ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE implicitly + 1 */,
    /**
     * android.control.afSceneChange [dynamic, enum, public]
     *
     * <p>Whether a significant scene change is detected within the currently-set AF
     * region(s).</p>
     */
    ANDROID_CONTROL_AF_SCENE_CHANGE = 65578u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_CONTROL_END */,
    ANDROID_CONTROL_END_3_3 = 65579u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_CONTROL_AF_SCENE_CHANGE implicitly + 1 */,
    /**
     * android.lens.poseReference [static, enum, public]
     *
     * <p>The origin for ANDROID_LENS_POSE_TRANSLATION, and the accuracy of
     * ANDROID_LENS_POSE_TRANSLATION and ANDROID_LENS_POSE_ROTATION.</p>
     *
     * @see ANDROID_LENS_POSE_ROTATION
     * @see ANDROID_LENS_POSE_TRANSLATION
     */
    ANDROID_LENS_POSE_REFERENCE = 524300u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_LENS_END */,
    /**
     * android.lens.distortion [dynamic, float[], public]
     *
     * <p>The correction coefficients to correct for this camera device's
     * radial and tangential lens distortion.</p>
     * <p>Replaces the deprecated ANDROID_LENS_RADIAL_DISTORTION field, which was
     * inconsistently defined.</p>
     *
     * @see ANDROID_LENS_RADIAL_DISTORTION
     */
    ANDROID_LENS_DISTORTION = 524301u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_LENS_POSE_REFERENCE implicitly + 1 */,
    ANDROID_LENS_END_3_3 = 524302u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_LENS_DISTORTION implicitly + 1 */,
    /**
     * android.request.availableSessionKeys [static, int32[], ndk_public]
     *
     * <p>A subset of the available request keys that the camera device
     * can pass as part of the capture session initialization.</p>
     */
    ANDROID_REQUEST_AVAILABLE_SESSION_KEYS = 786448u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_REQUEST_END */,
    /**
     * android.request.availablePhysicalCameraRequestKeys [static, int32[], ndk_public]
     *
     * <p>A subset of the available request keys that can be overridden for
     * physical devices backing a logical multi-camera.</p>
     */
    ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS = 786449u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_SESSION_KEYS implicitly + 1 */,
    ANDROID_REQUEST_END_3_3 = 786450u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS implicitly + 1 */,
    /**
     * android.statistics.oisDataMode [dynamic, enum, public]
     *
     * <p>A control for selecting whether optical stabilization (OIS) position
     * information is included in output result metadata.</p>
     */
    ANDROID_STATISTICS_OIS_DATA_MODE = 1114129u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_STATISTICS_END */,
    /**
     * android.statistics.oisTimestamps [dynamic, int64[], ndk_public]
     *
     * <p>An array of timestamps of OIS samples, in nanoseconds.</p>
     */
    ANDROID_STATISTICS_OIS_TIMESTAMPS = 1114130u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_STATISTICS_OIS_DATA_MODE implicitly + 1 */,
    /**
     * android.statistics.oisXShifts [dynamic, float[], ndk_public]
     *
     * <p>An array of shifts of OIS samples, in x direction.</p>
     */
    ANDROID_STATISTICS_OIS_X_SHIFTS = 1114131u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_STATISTICS_OIS_TIMESTAMPS implicitly + 1 */,
    /**
     * android.statistics.oisYShifts [dynamic, float[], ndk_public]
     *
     * <p>An array of shifts of OIS samples, in y direction.</p>
     */
    ANDROID_STATISTICS_OIS_Y_SHIFTS = 1114132u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_STATISTICS_OIS_X_SHIFTS implicitly + 1 */,
    ANDROID_STATISTICS_END_3_3 = 1114133u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_STATISTICS_OIS_Y_SHIFTS implicitly + 1 */,
    /**
     * android.statistics.info.availableOisDataModes [static, byte[], public]
     *
     * <p>List of OIS data output modes for ANDROID_STATISTICS_OIS_DATA_MODE that
     * are supported by this camera device.</p>
     *
     * @see ANDROID_STATISTICS_OIS_DATA_MODE
     */
    ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES = 1179656u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_STATISTICS_INFO_END */,
    ANDROID_STATISTICS_INFO_END_3_3 = 1179657u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES implicitly + 1 */,
    /**
     * android.info.version [static, byte, public]
     *
     * <p>A short string for manufacturer version information about the camera device, such as
     * ISP hardware, sensors, etc.</p>
     */
    ANDROID_INFO_VERSION = 1376257u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_INFO_END */,
    ANDROID_INFO_END_3_3 = 1376258u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_INFO_VERSION implicitly + 1 */,
    /**
     * android.logicalMultiCamera.physicalIds [static, byte[], ndk_public]
     *
     * <p>String containing the ids of the underlying physical cameras.</p>
     */
    ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS = 1703936u /* CameraMetadataSectionStart:ANDROID_LOGICAL_MULTI_CAMERA_START */,
    /**
     * android.logicalMultiCamera.sensorSyncType [static, enum, public]
     *
     * <p>The accuracy of frame timestamp synchronization between physical cameras</p>
     */
    ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE = 1703937u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS implicitly + 1 */,
    ANDROID_LOGICAL_MULTI_CAMERA_END_3_3 = 1703938u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE implicitly + 1 */,
    /**
     * android.distortionCorrection.mode [dynamic, enum, public]
     *
     * <p>Mode of operation for the lens distortion correction block.</p>
     */
    ANDROID_DISTORTION_CORRECTION_MODE = 1769472u /* CameraMetadataSectionStart:ANDROID_DISTORTION_CORRECTION_START */,
    /**
     * android.distortionCorrection.availableModes [static, byte[], public]
     *
     * <p>List of distortion correction modes for ANDROID_DISTORTION_CORRECTION_MODE that are
     * supported by this camera device.</p>
     *
     * @see ANDROID_DISTORTION_CORRECTION_MODE
     */
    ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES = 1769473u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_DISTORTION_CORRECTION_MODE implicitly + 1 */,
    ANDROID_DISTORTION_CORRECTION_END_3_3 = 1769474u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataTag.ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES implicitly + 1 */,
    /**
     * android.request.characteristicKeysNeedingPermission [static, int32[], hidden]
     *
     * <p>A list of camera characteristics keys that are only available
     * in case the camera client has camera permission.</p>
     */
    ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION = 786450u /* android.hardware.camera.metadata@3.3::CameraMetadataTag:ANDROID_REQUEST_END_3_3 */,
    ANDROID_REQUEST_END_3_4 = 786451u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION implicitly + 1 */,
    /**
     * android.scaler.availableRecommendedStreamConfigurations [static, enum[], ndk_public]
     *
     * <p>Recommended stream configurations for common client use cases.</p>
     */
    ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS = 851982u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_SCALER_END */,
    /**
     * android.scaler.availableRecommendedInputOutputFormatsMap [static, int32, ndk_public]
     *
     * <p>Recommended mappings of image formats that are supported by this
     * camera device for input streams, to their corresponding output formats.</p>
     */
    ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP = 851983u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS implicitly + 1 */,
    ANDROID_SCALER_END_3_4 = 851984u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP implicitly + 1 */,
    /**
     * android.info.supportedBufferManagementVersion [static, enum, system]
     *
     * <p>The version of buffer management API this camera device supports and opts into.</p>
     */
    ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION = 1376258u /* android.hardware.camera.metadata@3.3::CameraMetadataTag:ANDROID_INFO_END_3_3 */,
    ANDROID_INFO_END_3_4 = 1376259u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION implicitly + 1 */,
    /**
     * android.depth.availableRecommendedDepthStreamConfigurations [static, int32[], ndk_public]
     *
     * <p>Recommended depth stream configurations for common client use cases.</p>
     */
    ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS = 1638405u /* android.hardware.camera.metadata@3.2::CameraMetadataTag:ANDROID_DEPTH_END */,
    /**
     * android.depth.availableDynamicDepthStreamConfigurations [static, enum[], ndk_public]
     *
     * <p>The available dynamic depth dataspace stream
     * configurations that this camera device supports
     * (i.e. format, width, height, output/input stream).</p>
     */
    ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS = 1638406u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.depth.availableDynamicDepthMinFrameDurations [static, int64[], ndk_public]
     *
     * <p>This lists the minimum frame duration for each
     * format/size combination for dynamic depth output streams.</p>
     */
    ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS = 1638407u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.depth.availableDynamicDepthStallDurations [static, int64[], ndk_public]
     *
     * <p>This lists the maximum stall duration for each
     * output format/size combination for dynamic depth streams.</p>
     */
    ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS = 1638408u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS implicitly + 1 */,
    ANDROID_DEPTH_END_3_4 = 1638409u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS implicitly + 1 */,
    /**
     * android.logicalMultiCamera.activePhysicalId [dynamic, byte, public]
     *
     * <p>String containing the ID of the underlying active physical camera.</p>
     */
    ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID = 1703938u /* android.hardware.camera.metadata@3.3::CameraMetadataTag:ANDROID_LOGICAL_MULTI_CAMERA_END_3_3 */,
    ANDROID_LOGICAL_MULTI_CAMERA_END_3_4 = 1703939u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID implicitly + 1 */,
    /**
     * android.heic.availableHeicStreamConfigurations [static, enum[], ndk_public]
     *
     * <p>The available HEIC (ISO/IEC 23008-12) stream
     * configurations that this camera device supports
     * (i.e. format, width, height, output/input stream).</p>
     */
    ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS = 1703936u /* CameraMetadataSectionStart:ANDROID_HEIC_START */,
    /**
     * android.heic.availableHeicMinFrameDurations [static, int64[], ndk_public]
     *
     * <p>This lists the minimum frame duration for each
     * format/size combination for HEIC output formats.</p>
     */
    ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS = 1703937u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS implicitly + 1 */,
    /**
     * android.heic.availableHeicStallDurations [static, int64[], ndk_public]
     *
     * <p>This lists the maximum stall duration for each
     * output format/size combination for HEIC streams.</p>
     */
    ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS = 1703938u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS implicitly + 1 */,
    ANDROID_HEIC_END_3_4 = 1703939u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS implicitly + 1 */,
    /**
     * android.heic.info.supported [static, enum, system]
     *
     * <p>Whether this camera device can support identical set of stream combinations
     * involving HEIC image format, compared to the
     * <a href="https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession">table of combinations</a>
     * involving JPEG image format required for the device's hardware level and capabilities.</p>
     */
    ANDROID_HEIC_INFO_SUPPORTED = 1769472u /* CameraMetadataSectionStart:ANDROID_HEIC_INFO_START */,
    /**
     * android.heic.info.maxJpegAppSegmentsCount [static, byte, system]
     *
     * <p>The maximum number of Jpeg APP segments supported by the camera HAL device.</p>
     */
    ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT = 1769473u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_HEIC_INFO_SUPPORTED implicitly + 1 */,
    ANDROID_HEIC_INFO_END_3_4 = 1769474u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataTag.ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT implicitly + 1 */,
    /**
     * android.control.availableExtendedSceneModeMaxSizes [static, int32[], ndk_public]
     *
     * <p>The list of extended scene modes for ANDROID_CONTROL_EXTENDED_SCENE_MODE that are supported
     * by this camera device, and each extended scene mode's maximum streaming (non-stall) size
     * with  effect.</p>
     *
     * @see ANDROID_CONTROL_EXTENDED_SCENE_MODE
     */
    ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES = 65579u /* android.hardware.camera.metadata@3.3::CameraMetadataTag:ANDROID_CONTROL_END_3_3 */,
    /**
     * android.control.availableExtendedSceneModeZoomRatioRanges [static, float[], ndk_public]
     *
     * <p>The ranges of supported zoom ratio for non-DISABLED ANDROID_CONTROL_EXTENDED_SCENE_MODE.</p>
     *
     * @see ANDROID_CONTROL_EXTENDED_SCENE_MODE
     */
    ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES = 65580u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES implicitly + 1 */,
    /**
     * android.control.extendedSceneMode [dynamic, enum, public]
     *
     * <p>Whether extended scene mode is enabled for a particular capture request.</p>
     */
    ANDROID_CONTROL_EXTENDED_SCENE_MODE = 65581u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES implicitly + 1 */,
    /**
     * android.control.zoomRatioRange [static, float[], public]
     *
     * <p>Minimum and maximum zoom ratios supported by this camera device.</p>
     */
    ANDROID_CONTROL_ZOOM_RATIO_RANGE = 65582u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_CONTROL_EXTENDED_SCENE_MODE implicitly + 1 */,
    /**
     * android.control.zoomRatio [dynamic, float, public]
     *
     * <p>The desired zoom ratio</p>
     */
    ANDROID_CONTROL_ZOOM_RATIO = 65583u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_CONTROL_ZOOM_RATIO_RANGE implicitly + 1 */,
    ANDROID_CONTROL_END_3_5 = 65584u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_CONTROL_ZOOM_RATIO implicitly + 1 */,
    /**
     * android.scaler.availableRotateAndCropModes [static, byte[], hidden]
     *
     * <p>List of rotate-and-crop modes for ANDROID_SCALER_ROTATE_AND_CROP that are supported by this camera device.</p>
     *
     * @see ANDROID_SCALER_ROTATE_AND_CROP
     */
    ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES = 851984u /* android.hardware.camera.metadata@3.4::CameraMetadataTag:ANDROID_SCALER_END_3_4 */,
    /**
     * android.scaler.rotateAndCrop [dynamic, enum, hidden]
     *
     * <p>Whether a rotation-and-crop operation is applied to processed
     * outputs from the camera.</p>
     */
    ANDROID_SCALER_ROTATE_AND_CROP = 851985u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES implicitly + 1 */,
    ANDROID_SCALER_END_3_5 = 851986u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataTag.ANDROID_SCALER_ROTATE_AND_CROP implicitly + 1 */,
};

/*
 * Enumeration definitions for the various entries that need them
 *
 *
 * android.control.mode enumeration values added since v3.2
 * @see ANDROID_CONTROL_MODE
 */
enum class CameraMetadataEnumAndroidControlMode : uint32_t {
    ANDROID_CONTROL_MODE_OFF = 0u,
    ANDROID_CONTROL_MODE_AUTO = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_MODE_USE_SCENE_MODE = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_AUTO implicitly + 1 */,
    ANDROID_CONTROL_MODE_OFF_KEEP_STATE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_USE_SCENE_MODE implicitly + 1 */,
    ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_OFF_KEEP_STATE implicitly + 1 */,
};

/**
 * android.control.extendedSceneMode enumeration values
 * @see ANDROID_CONTROL_EXTENDED_SCENE_MODE
 */
enum class CameraMetadataEnumAndroidControlExtendedSceneMode : uint32_t {
    ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED = 0u,
    ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE = 1u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode.ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED implicitly + 1 */,
    ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS = 2u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode.ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE implicitly + 1 */,
    ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START = 64u /* 0x40 */,
};

/**
 * android.lens.poseReference enumeration values added since v3.3
 * @see ANDROID_LENS_POSE_REFERENCE
 */
enum class CameraMetadataEnumAndroidLensPoseReference : uint32_t {
    ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA = 0u,
    ANDROID_LENS_POSE_REFERENCE_GYROSCOPE = 1u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataEnumAndroidLensPoseReference.ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA implicitly + 1 */,
    ANDROID_LENS_POSE_REFERENCE_UNDEFINED = 2u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataEnumAndroidLensPoseReference.ANDROID_LENS_POSE_REFERENCE_GYROSCOPE implicitly + 1 */,
};

/**
 * android.request.availableCapabilities enumeration values added since v3.4
 * @see ANDROID_REQUEST_AVAILABLE_CAPABILITIES
 */
enum class CameraMetadataEnumAndroidRequestAvailableCapabilities : uint32_t {
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE = 0u,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING = 7u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT = 8u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO = 9u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING = 10u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA = 11u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME = 12u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA = 13u /* ::android::hardware::camera::metadata::V3_3::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA = 14u /* ::android::hardware::camera::metadata::V3_4::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA implicitly + 1 */,
    ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING = 15u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities.ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA implicitly + 1 */,
};

/**
 * android.scaler.rotateAndCrop enumeration values
 * @see ANDROID_SCALER_ROTATE_AND_CROP
 */
enum class CameraMetadataEnumAndroidScalerRotateAndCrop : uint32_t {
    ANDROID_SCALER_ROTATE_AND_CROP_NONE = 0u,
    ANDROID_SCALER_ROTATE_AND_CROP_90 = 1u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop.ANDROID_SCALER_ROTATE_AND_CROP_NONE implicitly + 1 */,
    ANDROID_SCALER_ROTATE_AND_CROP_180 = 2u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop.ANDROID_SCALER_ROTATE_AND_CROP_90 implicitly + 1 */,
    ANDROID_SCALER_ROTATE_AND_CROP_270 = 3u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop.ANDROID_SCALER_ROTATE_AND_CROP_180 implicitly + 1 */,
    ANDROID_SCALER_ROTATE_AND_CROP_AUTO = 4u /* ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop.ANDROID_SCALER_ROTATE_AND_CROP_270 implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataTag o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataTag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataTag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataTag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataTag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_TRANSFORM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_GAINS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_ABERRATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_TARGET_FPS_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_COMPENSATION_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_COMPENSATION_STEP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_EFFECTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_SCENE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MAX_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_OVERRIDES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ENABLE_ZSL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_AVAILABLE_EDGE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_FIRING_POWER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_FIRING_TIME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_COLOR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MAX_ENERGY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_CHARGE_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_COORDINATES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_PROCESSING_METHOD";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_TIMESTAMP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_ORIENTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_ORIENTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_THUMBNAIL_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_THUMBNAIL_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_MAX_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_APERTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_APERTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_APERTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_APERTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FILTER_DENSITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCAL_LENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCUS_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_OPTICAL_STABILIZATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FACING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FACING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FACING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FACING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_ROTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_TRANSLATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCUS_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INTRINSIC_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_RADIAL_DISTORTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_APERTURES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_SHADING_MAP_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_METERING_CROP_REGION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_USE_ZSL_FORMAT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_USE_PARTIAL_RESULT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_PARTIAL_RESULT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_FRAME_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_INPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_METADATA_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_OUTPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_TYPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_TYPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_TYPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PIPELINE_DEPTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PIPELINE_MAX_DEPTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PARTIAL_RESULT_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_RESULT_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROP_REGION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROP_REGION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROP_REGION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROP_REGION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_JPEG_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RAW_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROPPING_TYPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_EXPOSURE_TIME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FRAME_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_SENSITIVITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_CALIBRATION_TRANSFORM1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_CALIBRATION_TRANSFORM2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_COLOR_TRANSFORM1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_COLOR_TRANSFORM2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FORWARD_MATRIX1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FORWARD_MATRIX2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_BASE_GAIN_FACTOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_BLACK_LEVEL_PATTERN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TIMESTAMP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_NEUTRAL_COLOR_POINT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_NOISE_PROFILE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_TONE_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_GREEN_SPLIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_DATA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_ROLLING_SHUTTER_SKEW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_OPTICAL_BLACK_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_OPAQUE_RAW_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_SENSITIVITY_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_MAX_FRAME_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PHYSICAL_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_WHITE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_DETECT_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_IDS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_LANDMARKS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_RECTANGLES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_SCORES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_PREDICTED_COLOR_GAINS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SCENE_FLICKER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_FACE_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_BLUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_GREEN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_RED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MAX_CURVE_POINTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_GAMMA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_GAMMA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_GAMMA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_GAMMA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_PRESET_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_TRANSMIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_TRANSMIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_TRANSMIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_TRANSMIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_AVAILABLE_LEDS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_FRAME_NUMBER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_MAX_LATENCY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_MAX_CAPTURE_STALL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_MAX_DEPTH_SAMPLES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_SCENE_CHANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_SCENE_CHANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_SCENE_CHANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_SCENE_CHANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_REFERENCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_REFERENCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_REFERENCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_REFERENCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_DISTORTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_DISTORTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_DISTORTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_DISTORTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_SESSION_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_SESSION_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_SESSION_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_SESSION_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_DATA_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_DATA_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_OIS_DATA_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_DATA_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_TIMESTAMPS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_TIMESTAMPS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_OIS_TIMESTAMPS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_TIMESTAMPS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_X_SHIFTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_X_SHIFTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_OIS_X_SHIFTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_X_SHIFTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_Y_SHIFTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_Y_SHIFTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_OIS_Y_SHIFTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_Y_SHIFTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_VERSION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_VERSION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_VERSION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_VERSION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LOGICAL_MULTI_CAMERA_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DISTORTION_CORRECTION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_END_3_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_END_3_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DISTORTION_CORRECTION_END_3_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_END_3_3;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LOGICAL_MULTI_CAMERA_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_SUPPORTED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_INFO_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_SUPPORTED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_END_3_4) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_END_3_4)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HEIC_INFO_END_3_4";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_END_3_4;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EXTENDED_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EXTENDED_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EXTENDED_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EXTENDED_SCENE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ZOOM_RATIO_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ZOOM_RATIO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_5) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_5)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_END_3_5";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_5;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_ROTATE_AND_CROP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_ROTATE_AND_CROP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_ROTATE_AND_CROP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_5) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_5)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_END_3_5";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_5;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataTag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE) {
        return "ANDROID_COLOR_CORRECTION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM) {
        return "ANDROID_COLOR_CORRECTION_TRANSFORM";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS) {
        return "ANDROID_COLOR_CORRECTION_GAINS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE) {
        return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) {
        return "ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END) {
        return "ANDROID_COLOR_CORRECTION_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) {
        return "ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK) {
        return "ANDROID_CONTROL_AE_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_MODE) {
        return "ANDROID_CONTROL_AE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS) {
        return "ANDROID_CONTROL_AE_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE) {
        return "ANDROID_CONTROL_AE_TARGET_FPS_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_MODE) {
        return "ANDROID_CONTROL_AF_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS) {
        return "ANDROID_CONTROL_AF_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER) {
        return "ANDROID_CONTROL_AF_TRIGGER";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK) {
        return "ANDROID_CONTROL_AWB_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE) {
        return "ANDROID_CONTROL_AWB_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS) {
        return "ANDROID_CONTROL_AWB_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT) {
        return "ANDROID_CONTROL_CAPTURE_INTENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE) {
        return "ANDROID_CONTROL_EFFECT_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MODE) {
        return "ANDROID_CONTROL_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE) {
        return "ANDROID_CONTROL_SCENE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE) {
        return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE) {
        return "ANDROID_CONTROL_AE_COMPENSATION_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP) {
        return "ANDROID_CONTROL_AE_COMPENSATION_STEP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AF_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS) {
        return "ANDROID_CONTROL_AVAILABLE_EFFECTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_SCENE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AWB_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS) {
        return "ANDROID_CONTROL_MAX_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES) {
        return "ANDROID_CONTROL_SCENE_MODE_OVERRIDES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_STATE) {
        return "ANDROID_CONTROL_AE_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_STATE) {
        return "ANDROID_CONTROL_AF_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID) {
        return "ANDROID_CONTROL_AF_TRIGGER_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE) {
        return "ANDROID_CONTROL_AWB_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS) {
        return "ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE) {
        return "ANDROID_CONTROL_AE_LOCK_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE) {
        return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE) {
        return "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST) {
        return "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL) {
        return "ANDROID_CONTROL_ENABLE_ZSL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END) {
        return "ANDROID_CONTROL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_MODE) {
        return "ANDROID_DEMOSAIC_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_END) {
        return "ANDROID_DEMOSAIC_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_MODE) {
        return "ANDROID_EDGE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_STRENGTH) {
        return "ANDROID_EDGE_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES) {
        return "ANDROID_EDGE_AVAILABLE_EDGE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_END) {
        return "ANDROID_EDGE_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER) {
        return "ANDROID_FLASH_FIRING_POWER";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME) {
        return "ANDROID_FLASH_FIRING_TIME";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MODE) {
        return "ANDROID_FLASH_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE) {
        return "ANDROID_FLASH_COLOR_TEMPERATURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY) {
        return "ANDROID_FLASH_MAX_ENERGY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_STATE) {
        return "ANDROID_FLASH_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_END) {
        return "ANDROID_FLASH_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE) {
        return "ANDROID_FLASH_INFO_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION) {
        return "ANDROID_FLASH_INFO_CHARGE_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_END) {
        return "ANDROID_FLASH_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE) {
        return "ANDROID_HOT_PIXEL_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES) {
        return "ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_END) {
        return "ANDROID_HOT_PIXEL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES) {
        return "ANDROID_JPEG_GPS_COORDINATES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD) {
        return "ANDROID_JPEG_GPS_PROCESSING_METHOD";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP) {
        return "ANDROID_JPEG_GPS_TIMESTAMP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_ORIENTATION) {
        return "ANDROID_JPEG_ORIENTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_QUALITY) {
        return "ANDROID_JPEG_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY) {
        return "ANDROID_JPEG_THUMBNAIL_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE) {
        return "ANDROID_JPEG_THUMBNAIL_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES) {
        return "ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE) {
        return "ANDROID_JPEG_MAX_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_SIZE) {
        return "ANDROID_JPEG_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_END) {
        return "ANDROID_JPEG_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_APERTURE) {
        return "ANDROID_LENS_APERTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY) {
        return "ANDROID_LENS_FILTER_DENSITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH) {
        return "ANDROID_LENS_FOCAL_LENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE) {
        return "ANDROID_LENS_FOCUS_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE) {
        return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FACING) {
        return "ANDROID_LENS_FACING";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION) {
        return "ANDROID_LENS_POSE_ROTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION) {
        return "ANDROID_LENS_POSE_TRANSLATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE) {
        return "ANDROID_LENS_FOCUS_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_STATE) {
        return "ANDROID_LENS_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION) {
        return "ANDROID_LENS_INTRINSIC_CALIBRATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION) {
        return "ANDROID_LENS_RADIAL_DISTORTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END) {
        return "ANDROID_LENS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES) {
        return "ANDROID_LENS_INFO_AVAILABLE_APERTURES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES) {
        return "ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS) {
        return "ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) {
        return "ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE) {
        return "ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE) {
        return "ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE) {
        return "ANDROID_LENS_INFO_SHADING_MAP_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION) {
        return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_END) {
        return "ANDROID_LENS_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE) {
        return "ANDROID_NOISE_REDUCTION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH) {
        return "ANDROID_NOISE_REDUCTION_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) {
        return "ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END) {
        return "ANDROID_NOISE_REDUCTION_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION) {
        return "ANDROID_QUIRKS_METERING_CROP_REGION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO) {
        return "ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT) {
        return "ANDROID_QUIRKS_USE_ZSL_FORMAT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT) {
        return "ANDROID_QUIRKS_USE_PARTIAL_RESULT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT) {
        return "ANDROID_QUIRKS_PARTIAL_RESULT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_END) {
        return "ANDROID_QUIRKS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT) {
        return "ANDROID_REQUEST_FRAME_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_ID) {
        return "ANDROID_REQUEST_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS) {
        return "ANDROID_REQUEST_INPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE) {
        return "ANDROID_REQUEST_METADATA_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS) {
        return "ANDROID_REQUEST_OUTPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_TYPE) {
        return "ANDROID_REQUEST_TYPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH) {
        return "ANDROID_REQUEST_PIPELINE_DEPTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH) {
        return "ANDROID_REQUEST_PIPELINE_MAX_DEPTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT) {
        return "ANDROID_REQUEST_PARTIAL_RESULT_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_RESULT_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END) {
        return "ANDROID_REQUEST_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROP_REGION) {
        return "ANDROID_SCALER_CROP_REGION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_JPEG_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) {
        return "ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_RAW_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP) {
        return "ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE) {
        return "ANDROID_SCALER_CROPPING_TYPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END) {
        return "ANDROID_SCALER_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME) {
        return "ANDROID_SENSOR_EXPOSURE_TIME";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION) {
        return "ANDROID_SENSOR_FRAME_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY) {
        return "ANDROID_SENSOR_SENSITIVITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT2";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1) {
        return "ANDROID_SENSOR_CALIBRATION_TRANSFORM1";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2) {
        return "ANDROID_SENSOR_CALIBRATION_TRANSFORM2";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1) {
        return "ANDROID_SENSOR_COLOR_TRANSFORM1";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2) {
        return "ANDROID_SENSOR_COLOR_TRANSFORM2";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1) {
        return "ANDROID_SENSOR_FORWARD_MATRIX1";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2) {
        return "ANDROID_SENSOR_FORWARD_MATRIX2";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR) {
        return "ANDROID_SENSOR_BASE_GAIN_FACTOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN) {
        return "ANDROID_SENSOR_BLACK_LEVEL_PATTERN";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY) {
        return "ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION) {
        return "ANDROID_SENSOR_ORIENTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS) {
        return "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP) {
        return "ANDROID_SENSOR_TIMESTAMP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE) {
        return "ANDROID_SENSOR_TEMPERATURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT) {
        return "ANDROID_SENSOR_NEUTRAL_COLOR_POINT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE) {
        return "ANDROID_SENSOR_NOISE_PROFILE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP) {
        return "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE) {
        return "ANDROID_SENSOR_PROFILE_TONE_CURVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT) {
        return "ANDROID_SENSOR_GREEN_SPLIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA) {
        return "ANDROID_SENSOR_TEST_PATTERN_DATA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES) {
        return "ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW) {
        return "ANDROID_SENSOR_ROLLING_SHUTTER_SKEW";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS) {
        return "ANDROID_SENSOR_OPTICAL_BLACK_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL) {
        return "ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL) {
        return "ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE) {
        return "ANDROID_SENSOR_OPAQUE_RAW_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_END) {
        return "ANDROID_SENSOR_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE) {
        return "ANDROID_SENSOR_INFO_SENSITIVITY_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE) {
        return "ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION) {
        return "ANDROID_SENSOR_INFO_MAX_FRAME_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE) {
        return "ANDROID_SENSOR_INFO_PHYSICAL_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL) {
        return "ANDROID_SENSOR_INFO_WHITE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE) {
        return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED) {
        return "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_END) {
        return "ANDROID_SENSOR_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_MODE) {
        return "ANDROID_SHADING_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_STRENGTH) {
        return "ANDROID_SHADING_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES) {
        return "ANDROID_SHADING_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_END) {
        return "ANDROID_SHADING_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE) {
        return "ANDROID_STATISTICS_FACE_DETECT_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE) {
        return "ANDROID_STATISTICS_HISTOGRAM_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS) {
        return "ANDROID_STATISTICS_FACE_IDS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS) {
        return "ANDROID_STATISTICS_FACE_LANDMARKS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES) {
        return "ANDROID_STATISTICS_FACE_RECTANGLES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES) {
        return "ANDROID_STATISTICS_FACE_SCORES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM) {
        return "ANDROID_STATISTICS_HISTOGRAM";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP) {
        return "ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS) {
        return "ANDROID_STATISTICS_PREDICTED_COLOR_GAINS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM) {
        return "ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER) {
        return "ANDROID_STATISTICS_SCENE_FLICKER";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END) {
        return "ANDROID_STATISTICS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT) {
        return "ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT) {
        return "ANDROID_STATISTICS_INFO_MAX_FACE_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT) {
        return "ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE) {
        return "ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE) {
        return "ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END) {
        return "ANDROID_STATISTICS_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE) {
        return "ANDROID_TONEMAP_CURVE_BLUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN) {
        return "ANDROID_TONEMAP_CURVE_GREEN";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED) {
        return "ANDROID_TONEMAP_CURVE_RED";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MODE) {
        return "ANDROID_TONEMAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS) {
        return "ANDROID_TONEMAP_MAX_CURVE_POINTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES) {
        return "ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_GAMMA) {
        return "ANDROID_TONEMAP_GAMMA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE) {
        return "ANDROID_TONEMAP_PRESET_CURVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_END) {
        return "ANDROID_TONEMAP_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_TRANSMIT) {
        return "ANDROID_LED_TRANSMIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS) {
        return "ANDROID_LED_AVAILABLE_LEDS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_END) {
        return "ANDROID_LED_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END) {
        return "ANDROID_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK) {
        return "ANDROID_BLACK_LEVEL_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_END) {
        return "ANDROID_BLACK_LEVEL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER) {
        return "ANDROID_SYNC_FRAME_NUMBER";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY) {
        return "ANDROID_SYNC_MAX_LATENCY";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_END) {
        return "ANDROID_SYNC_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR) {
        return "ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL) {
        return "ANDROID_REPROCESS_MAX_CAPTURE_STALL";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_END) {
        return "ANDROID_REPROCESS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES) {
        return "ANDROID_DEPTH_MAX_DEPTH_SAMPLES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE) {
        return "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END) {
        return "ANDROID_DEPTH_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_SCENE_CHANGE) {
        return "ANDROID_CONTROL_AF_SCENE_CHANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_3) {
        return "ANDROID_CONTROL_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_REFERENCE) {
        return "ANDROID_LENS_POSE_REFERENCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_DISTORTION) {
        return "ANDROID_LENS_DISTORTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END_3_3) {
        return "ANDROID_LENS_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_SESSION_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_SESSION_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_3) {
        return "ANDROID_REQUEST_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_DATA_MODE) {
        return "ANDROID_STATISTICS_OIS_DATA_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_TIMESTAMPS) {
        return "ANDROID_STATISTICS_OIS_TIMESTAMPS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_X_SHIFTS) {
        return "ANDROID_STATISTICS_OIS_X_SHIFTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_Y_SHIFTS) {
        return "ANDROID_STATISTICS_OIS_Y_SHIFTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END_3_3) {
        return "ANDROID_STATISTICS_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END_3_3) {
        return "ANDROID_STATISTICS_INFO_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_VERSION) {
        return "ANDROID_INFO_VERSION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_3) {
        return "ANDROID_INFO_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS) {
        return "ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE) {
        return "ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_3) {
        return "ANDROID_LOGICAL_MULTI_CAMERA_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_MODE) {
        return "ANDROID_DISTORTION_CORRECTION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES) {
        return "ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_END_3_3) {
        return "ANDROID_DISTORTION_CORRECTION_END_3_3";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION) {
        return "ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_4) {
        return "ANDROID_REQUEST_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP) {
        return "ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_4) {
        return "ANDROID_SCALER_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION) {
        return "ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_4) {
        return "ANDROID_INFO_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END_3_4) {
        return "ANDROID_DEPTH_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) {
        return "ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_4) {
        return "ANDROID_LOGICAL_MULTI_CAMERA_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS) {
        return "ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS) {
        return "ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS) {
        return "ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_END_3_4) {
        return "ANDROID_HEIC_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_SUPPORTED) {
        return "ANDROID_HEIC_INFO_SUPPORTED";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT) {
        return "ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_END_3_4) {
        return "ANDROID_HEIC_INFO_END_3_4";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES) {
        return "ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES) {
        return "ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EXTENDED_SCENE_MODE) {
        return "ANDROID_CONTROL_EXTENDED_SCENE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO_RANGE) {
        return "ANDROID_CONTROL_ZOOM_RATIO_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO) {
        return "ANDROID_CONTROL_ZOOM_RATIO";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_5) {
        return "ANDROID_CONTROL_END_3_5";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES) {
        return "ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_ROTATE_AND_CROP) {
        return "ANDROID_SCALER_ROTATE_AND_CROP";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_5) {
        return "ANDROID_SCALER_END_3_5";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataTag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_USE_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_OFF_KEEP_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF) {
        return "ANDROID_CONTROL_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO) {
        return "ANDROID_CONTROL_MODE_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE) {
        return "ANDROID_CONTROL_MODE_USE_SCENE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE) {
        return "ANDROID_CONTROL_MODE_OFF_KEEP_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE) {
        return "ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED) {
        return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE) {
        return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS) {
        return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START) {
        return "ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_REFERENCE_GYROSCOPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_REFERENCE_UNDEFINED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA) {
        return "ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE) {
        return "ANDROID_LENS_POSE_REFERENCE_GYROSCOPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED) {
        return "ANDROID_LENS_POSE_REFERENCE_UNDEFINED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP_NONE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP_90";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP_180";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP_270";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270;
    }
    if ((o & ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_ROTATE_AND_CROP_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE) {
        return "ANDROID_SCALER_ROTATE_AND_CROP_NONE";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90) {
        return "ANDROID_SCALER_ROTATE_AND_CROP_90";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180) {
        return "ANDROID_SCALER_ROTATE_AND_CROP_180";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270) {
        return "ANDROID_SCALER_ROTATE_AND_CROP_270";
    }
    if (o == ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO) {
        return "ANDROID_SCALER_ROTATE_AND_CROP_AUTO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V3_5
}  // namespace metadata
}  // namespace camera
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataTag, 299> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataTag> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEMOSAIC_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_STRENGTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_EDGE_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_FLASH_INFO_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HOT_PIXEL_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_ORIENTATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_QUALITY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_JPEG_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_APERTURE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FACING,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_INFO_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_QUIRKS_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_ID,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_TYPE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROP_REGION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SENSOR_INFO_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_STRENGTH,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SHADING_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_GAMMA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_TONEMAP_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_TRANSMIT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LED_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_BLACK_LEVEL_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SYNC_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REPROCESS_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AF_SCENE_CHANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_POSE_REFERENCE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_DISTORTION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LENS_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_SESSION_KEYS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_PHYSICAL_CAMERA_REQUEST_KEYS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_DATA_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_TIMESTAMPS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_X_SHIFTS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_OIS_Y_SHIFTS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_STATISTICS_INFO_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_VERSION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DISTORTION_CORRECTION_END_3_3,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_REQUEST_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RECOMMENDED_INPUT_OUTPUT_FORMATS_MAP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_INFO_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_RECOMMENDED_DEPTH_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_DEPTH_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_LOGICAL_MULTI_CAMERA_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_SUPPORTED,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_MAX_JPEG_APP_SEGMENTS_COUNT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_HEIC_INFO_END_3_4,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_MAX_SIZES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_ZOOM_RATIO_RANGES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_EXTENDED_SCENE_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO_RANGE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_ZOOM_RATIO,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_CONTROL_END_3_5,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_ROTATE_AND_CROP_MODES,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_ROTATE_AND_CROP,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataTag::ANDROID_SCALER_END_3_5,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_EXTENDED_SCENE_MODE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_DISABLED,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidControlExtendedSceneMode::ANDROID_CONTROL_EXTENDED_SCENE_MODE_VENDOR_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidLensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities, 16> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop> = {
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270,
    ::android::hardware::camera::metadata::V3_5::CameraMetadataEnumAndroidScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_5_TYPES_H
