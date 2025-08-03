#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_2_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_2_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace metadata {
namespace V3_2 {

// Forward declaration for forward reference support:
enum class CameraMetadataSection : uint32_t;
enum class CameraMetadataSectionStart : uint32_t;
enum class CameraMetadataTag : uint32_t;
enum class CameraMetadataEnumAndroidColorCorrectionMode : uint32_t;
enum class CameraMetadataEnumAndroidColorCorrectionAberrationMode : uint32_t;
enum class CameraMetadataEnumAndroidControlAeAntibandingMode : uint32_t;
enum class CameraMetadataEnumAndroidControlAeLock : uint32_t;
enum class CameraMetadataEnumAndroidControlAeMode : uint32_t;
enum class CameraMetadataEnumAndroidControlAePrecaptureTrigger : uint32_t;
enum class CameraMetadataEnumAndroidControlAfMode : uint32_t;
enum class CameraMetadataEnumAndroidControlAfTrigger : uint32_t;
enum class CameraMetadataEnumAndroidControlAwbLock : uint32_t;
enum class CameraMetadataEnumAndroidControlAwbMode : uint32_t;
enum class CameraMetadataEnumAndroidControlCaptureIntent : uint32_t;
enum class CameraMetadataEnumAndroidControlEffectMode : uint32_t;
enum class CameraMetadataEnumAndroidControlMode : uint32_t;
enum class CameraMetadataEnumAndroidControlSceneMode : uint32_t;
enum class CameraMetadataEnumAndroidControlVideoStabilizationMode : uint32_t;
enum class CameraMetadataEnumAndroidControlAeState : uint32_t;
enum class CameraMetadataEnumAndroidControlAfState : uint32_t;
enum class CameraMetadataEnumAndroidControlAwbState : uint32_t;
enum class CameraMetadataEnumAndroidControlAeLockAvailable : uint32_t;
enum class CameraMetadataEnumAndroidControlAwbLockAvailable : uint32_t;
enum class CameraMetadataEnumAndroidControlEnableZsl : uint32_t;
enum class CameraMetadataEnumAndroidDemosaicMode : uint32_t;
enum class CameraMetadataEnumAndroidEdgeMode : uint32_t;
enum class CameraMetadataEnumAndroidFlashMode : uint32_t;
enum class CameraMetadataEnumAndroidFlashState : uint32_t;
enum class CameraMetadataEnumAndroidFlashInfoAvailable : uint32_t;
enum class CameraMetadataEnumAndroidHotPixelMode : uint32_t;
enum class CameraMetadataEnumAndroidLensOpticalStabilizationMode : uint32_t;
enum class CameraMetadataEnumAndroidLensFacing : uint32_t;
enum class CameraMetadataEnumAndroidLensState : uint32_t;
enum class CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration : uint32_t;
enum class CameraMetadataEnumAndroidNoiseReductionMode : uint32_t;
enum class CameraMetadataEnumAndroidQuirksPartialResult : uint32_t;
enum class CameraMetadataEnumAndroidRequestMetadataMode : uint32_t;
enum class CameraMetadataEnumAndroidRequestType : uint32_t;
enum class CameraMetadataEnumAndroidRequestAvailableCapabilities : uint32_t;
enum class CameraMetadataEnumAndroidScalerAvailableFormats : uint32_t;
enum class CameraMetadataEnumAndroidScalerAvailableStreamConfigurations : uint32_t;
enum class CameraMetadataEnumAndroidScalerCroppingType : uint32_t;
enum class CameraMetadataEnumAndroidSensorReferenceIlluminant1 : uint32_t;
enum class CameraMetadataEnumAndroidSensorTestPatternMode : uint32_t;
enum class CameraMetadataEnumAndroidSensorInfoColorFilterArrangement : uint32_t;
enum class CameraMetadataEnumAndroidSensorInfoTimestampSource : uint32_t;
enum class CameraMetadataEnumAndroidSensorInfoLensShadingApplied : uint32_t;
enum class CameraMetadataEnumAndroidShadingMode : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsFaceDetectMode : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsHistogramMode : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsSharpnessMapMode : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsHotPixelMapMode : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsSceneFlicker : uint32_t;
enum class CameraMetadataEnumAndroidStatisticsLensShadingMapMode : uint32_t;
enum class CameraMetadataEnumAndroidTonemapMode : uint32_t;
enum class CameraMetadataEnumAndroidTonemapPresetCurve : uint32_t;
enum class CameraMetadataEnumAndroidLedTransmit : uint32_t;
enum class CameraMetadataEnumAndroidLedAvailableLeds : uint32_t;
enum class CameraMetadataEnumAndroidInfoSupportedHardwareLevel : uint32_t;
enum class CameraMetadataEnumAndroidBlackLevelLock : uint32_t;
enum class CameraMetadataEnumAndroidSyncFrameNumber : uint32_t;
enum class CameraMetadataEnumAndroidSyncMaxLatency : uint32_t;
enum class CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations : uint32_t;
enum class CameraMetadataEnumAndroidDepthDepthIsExclusive : uint32_t;

/**
 * Top level hierarchy definitions for camera metadata. *_INFO sections are for
 * the static metadata that can be retrived without opening the camera device.
 */
enum class CameraMetadataSection : uint32_t {
    ANDROID_COLOR_CORRECTION = 0u,
    ANDROID_CONTROL = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_COLOR_CORRECTION implicitly + 1 */,
    ANDROID_DEMOSAIC = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_CONTROL implicitly + 1 */,
    ANDROID_EDGE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_DEMOSAIC implicitly + 1 */,
    ANDROID_FLASH = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_EDGE implicitly + 1 */,
    ANDROID_FLASH_INFO = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_FLASH implicitly + 1 */,
    ANDROID_HOT_PIXEL = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_FLASH_INFO implicitly + 1 */,
    ANDROID_JPEG = 7u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_HOT_PIXEL implicitly + 1 */,
    ANDROID_LENS = 8u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_JPEG implicitly + 1 */,
    ANDROID_LENS_INFO = 9u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_LENS implicitly + 1 */,
    ANDROID_NOISE_REDUCTION = 10u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_LENS_INFO implicitly + 1 */,
    ANDROID_QUIRKS = 11u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_NOISE_REDUCTION implicitly + 1 */,
    ANDROID_REQUEST = 12u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_QUIRKS implicitly + 1 */,
    ANDROID_SCALER = 13u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_REQUEST implicitly + 1 */,
    ANDROID_SENSOR = 14u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_SCALER implicitly + 1 */,
    ANDROID_SENSOR_INFO = 15u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_SENSOR implicitly + 1 */,
    ANDROID_SHADING = 16u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_SENSOR_INFO implicitly + 1 */,
    ANDROID_STATISTICS = 17u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_SHADING implicitly + 1 */,
    ANDROID_STATISTICS_INFO = 18u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_STATISTICS implicitly + 1 */,
    ANDROID_TONEMAP = 19u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_STATISTICS_INFO implicitly + 1 */,
    ANDROID_LED = 20u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_TONEMAP implicitly + 1 */,
    ANDROID_INFO = 21u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_LED implicitly + 1 */,
    ANDROID_BLACK_LEVEL = 22u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_INFO implicitly + 1 */,
    ANDROID_SYNC = 23u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_BLACK_LEVEL implicitly + 1 */,
    ANDROID_REPROCESS = 24u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_SYNC implicitly + 1 */,
    ANDROID_DEPTH = 25u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_REPROCESS implicitly + 1 */,
    ANDROID_SECTION_COUNT = 26u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataSection.ANDROID_DEPTH implicitly + 1 */,
    VENDOR_SECTION = 32768u /* 0x8000 */,
};

/**
 * Hierarchy positions in enum space. All vendor extension sections must be
 * defined with tag >= VENDOR_SECTION_START
 */
enum class CameraMetadataSectionStart : uint32_t {
    ANDROID_COLOR_CORRECTION_START = 0u /* CameraMetadataSection:ANDROID_COLOR_CORRECTION << 16 */,
    ANDROID_CONTROL_START = 65536u /* CameraMetadataSection:ANDROID_CONTROL << 16 */,
    ANDROID_DEMOSAIC_START = 131072u /* CameraMetadataSection:ANDROID_DEMOSAIC << 16 */,
    ANDROID_EDGE_START = 196608u /* CameraMetadataSection:ANDROID_EDGE << 16 */,
    ANDROID_FLASH_START = 262144u /* CameraMetadataSection:ANDROID_FLASH << 16 */,
    ANDROID_FLASH_INFO_START = 327680u /* CameraMetadataSection:ANDROID_FLASH_INFO << 16 */,
    ANDROID_HOT_PIXEL_START = 393216u /* CameraMetadataSection:ANDROID_HOT_PIXEL << 16 */,
    ANDROID_JPEG_START = 458752u /* CameraMetadataSection:ANDROID_JPEG << 16 */,
    ANDROID_LENS_START = 524288u /* CameraMetadataSection:ANDROID_LENS << 16 */,
    ANDROID_LENS_INFO_START = 589824u /* CameraMetadataSection:ANDROID_LENS_INFO << 16 */,
    ANDROID_NOISE_REDUCTION_START = 655360u /* CameraMetadataSection:ANDROID_NOISE_REDUCTION << 16 */,
    ANDROID_QUIRKS_START = 720896u /* CameraMetadataSection:ANDROID_QUIRKS << 16 */,
    ANDROID_REQUEST_START = 786432u /* CameraMetadataSection:ANDROID_REQUEST << 16 */,
    ANDROID_SCALER_START = 851968u /* CameraMetadataSection:ANDROID_SCALER << 16 */,
    ANDROID_SENSOR_START = 917504u /* CameraMetadataSection:ANDROID_SENSOR << 16 */,
    ANDROID_SENSOR_INFO_START = 983040u /* CameraMetadataSection:ANDROID_SENSOR_INFO << 16 */,
    ANDROID_SHADING_START = 1048576u /* CameraMetadataSection:ANDROID_SHADING << 16 */,
    ANDROID_STATISTICS_START = 1114112u /* CameraMetadataSection:ANDROID_STATISTICS << 16 */,
    ANDROID_STATISTICS_INFO_START = 1179648u /* CameraMetadataSection:ANDROID_STATISTICS_INFO << 16 */,
    ANDROID_TONEMAP_START = 1245184u /* CameraMetadataSection:ANDROID_TONEMAP << 16 */,
    ANDROID_LED_START = 1310720u /* CameraMetadataSection:ANDROID_LED << 16 */,
    ANDROID_INFO_START = 1376256u /* CameraMetadataSection:ANDROID_INFO << 16 */,
    ANDROID_BLACK_LEVEL_START = 1441792u /* CameraMetadataSection:ANDROID_BLACK_LEVEL << 16 */,
    ANDROID_SYNC_START = 1507328u /* CameraMetadataSection:ANDROID_SYNC << 16 */,
    ANDROID_REPROCESS_START = 1572864u /* CameraMetadataSection:ANDROID_REPROCESS << 16 */,
    ANDROID_DEPTH_START = 1638400u /* CameraMetadataSection:ANDROID_DEPTH << 16 */,
    VENDOR_SECTION_START = 2147483648u /* CameraMetadataSection:VENDOR_SECTION << 16 */,
};

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
};

/*
 * Enumeration definitions for the various entries that need them
 *
 *
 * android.colorCorrection.mode enumeration values
 * @see ANDROID_COLOR_CORRECTION_MODE
 */
enum class CameraMetadataEnumAndroidColorCorrectionMode : uint32_t {
    ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX = 0u,
    ANDROID_COLOR_CORRECTION_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode.ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX implicitly + 1 */,
    ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode.ANDROID_COLOR_CORRECTION_MODE_FAST implicitly + 1 */,
};

/**
 * android.colorCorrection.aberrationMode enumeration values
 * @see ANDROID_COLOR_CORRECTION_ABERRATION_MODE
 */
enum class CameraMetadataEnumAndroidColorCorrectionAberrationMode : uint32_t {
    ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF = 0u,
    ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode.ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF implicitly + 1 */,
    ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode.ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST implicitly + 1 */,
};

/**
 * android.control.aeAntibandingMode enumeration values
 * @see ANDROID_CONTROL_AE_ANTIBANDING_MODE
 */
enum class CameraMetadataEnumAndroidControlAeAntibandingMode : uint32_t {
    ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF = 0u,
    ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode.ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode.ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ implicitly + 1 */,
    ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode.ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ implicitly + 1 */,
};

/**
 * android.control.aeLock enumeration values
 * @see ANDROID_CONTROL_AE_LOCK
 */
enum class CameraMetadataEnumAndroidControlAeLock : uint32_t {
    ANDROID_CONTROL_AE_LOCK_OFF = 0u,
    ANDROID_CONTROL_AE_LOCK_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock.ANDROID_CONTROL_AE_LOCK_OFF implicitly + 1 */,
};

/**
 * android.control.aeMode enumeration values
 * @see ANDROID_CONTROL_AE_MODE
 */
enum class CameraMetadataEnumAndroidControlAeMode : uint32_t {
    ANDROID_CONTROL_AE_MODE_OFF = 0u,
    ANDROID_CONTROL_AE_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode.ANDROID_CONTROL_AE_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode.ANDROID_CONTROL_AE_MODE_ON implicitly + 1 */,
    ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode.ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH implicitly + 1 */,
    ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode.ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH implicitly + 1 */,
};

/**
 * android.control.aePrecaptureTrigger enumeration values
 * @see ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER
 */
enum class CameraMetadataEnumAndroidControlAePrecaptureTrigger : uint32_t {
    ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE = 0u,
    ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger.ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE implicitly + 1 */,
    ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger.ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START implicitly + 1 */,
};

/**
 * android.control.afMode enumeration values
 * @see ANDROID_CONTROL_AF_MODE
 */
enum class CameraMetadataEnumAndroidControlAfMode : uint32_t {
    ANDROID_CONTROL_AF_MODE_OFF = 0u,
    ANDROID_CONTROL_AF_MODE_AUTO = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode.ANDROID_CONTROL_AF_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_AF_MODE_MACRO = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode.ANDROID_CONTROL_AF_MODE_AUTO implicitly + 1 */,
    ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode.ANDROID_CONTROL_AF_MODE_MACRO implicitly + 1 */,
    ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode.ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO implicitly + 1 */,
    ANDROID_CONTROL_AF_MODE_EDOF = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode.ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE implicitly + 1 */,
};

/**
 * android.control.afTrigger enumeration values
 * @see ANDROID_CONTROL_AF_TRIGGER
 */
enum class CameraMetadataEnumAndroidControlAfTrigger : uint32_t {
    ANDROID_CONTROL_AF_TRIGGER_IDLE = 0u,
    ANDROID_CONTROL_AF_TRIGGER_START = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger.ANDROID_CONTROL_AF_TRIGGER_IDLE implicitly + 1 */,
    ANDROID_CONTROL_AF_TRIGGER_CANCEL = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger.ANDROID_CONTROL_AF_TRIGGER_START implicitly + 1 */,
};

/**
 * android.control.awbLock enumeration values
 * @see ANDROID_CONTROL_AWB_LOCK
 */
enum class CameraMetadataEnumAndroidControlAwbLock : uint32_t {
    ANDROID_CONTROL_AWB_LOCK_OFF = 0u,
    ANDROID_CONTROL_AWB_LOCK_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock.ANDROID_CONTROL_AWB_LOCK_OFF implicitly + 1 */,
};

/**
 * android.control.awbMode enumeration values
 * @see ANDROID_CONTROL_AWB_MODE
 */
enum class CameraMetadataEnumAndroidControlAwbMode : uint32_t {
    ANDROID_CONTROL_AWB_MODE_OFF = 0u,
    ANDROID_CONTROL_AWB_MODE_AUTO = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_INCANDESCENT = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_AUTO implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_FLUORESCENT = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_INCANDESCENT implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_FLUORESCENT implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_DAYLIGHT = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_DAYLIGHT implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_TWILIGHT = 7u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT implicitly + 1 */,
    ANDROID_CONTROL_AWB_MODE_SHADE = 8u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode.ANDROID_CONTROL_AWB_MODE_TWILIGHT implicitly + 1 */,
};

/**
 * android.control.captureIntent enumeration values
 * @see ANDROID_CONTROL_CAPTURE_INTENT
 */
enum class CameraMetadataEnumAndroidControlCaptureIntent : uint32_t {
    ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM = 0u,
    ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM implicitly + 1 */,
    ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW implicitly + 1 */,
    ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE implicitly + 1 */,
    ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD implicitly + 1 */,
    ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT implicitly + 1 */,
    ANDROID_CONTROL_CAPTURE_INTENT_MANUAL = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent.ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG implicitly + 1 */,
};

/**
 * android.control.effectMode enumeration values
 * @see ANDROID_CONTROL_EFFECT_MODE
 */
enum class CameraMetadataEnumAndroidControlEffectMode : uint32_t {
    ANDROID_CONTROL_EFFECT_MODE_OFF = 0u,
    ANDROID_CONTROL_EFFECT_MODE_MONO = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_NEGATIVE = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_MONO implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_SOLARIZE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_NEGATIVE implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_SEPIA = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_SOLARIZE implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_POSTERIZE = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_SEPIA implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_POSTERIZE implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD = 7u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD implicitly + 1 */,
    ANDROID_CONTROL_EFFECT_MODE_AQUA = 8u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode.ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD implicitly + 1 */,
};

/**
 * android.control.mode enumeration values
 * @see ANDROID_CONTROL_MODE
 */
enum class CameraMetadataEnumAndroidControlMode : uint32_t {
    ANDROID_CONTROL_MODE_OFF = 0u,
    ANDROID_CONTROL_MODE_AUTO = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_OFF implicitly + 1 */,
    ANDROID_CONTROL_MODE_USE_SCENE_MODE = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_AUTO implicitly + 1 */,
    ANDROID_CONTROL_MODE_OFF_KEEP_STATE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode.ANDROID_CONTROL_MODE_USE_SCENE_MODE implicitly + 1 */,
};

/**
 * android.control.sceneMode enumeration values
 * @see ANDROID_CONTROL_SCENE_MODE
 */
enum class CameraMetadataEnumAndroidControlSceneMode : uint32_t {
    ANDROID_CONTROL_SCENE_MODE_DISABLED = 0u,
    ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_DISABLED implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_ACTION = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_PORTRAIT = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_ACTION implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_LANDSCAPE = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_PORTRAIT implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_NIGHT = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_LANDSCAPE implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_NIGHT implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_THEATRE = 7u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_BEACH = 8u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_THEATRE implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_SNOW = 9u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_BEACH implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_SUNSET = 10u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_SNOW implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO = 11u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_SUNSET implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_FIREWORKS = 12u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_SPORTS = 13u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_FIREWORKS implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_PARTY = 14u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_SPORTS implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT = 15u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_PARTY implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_BARCODE = 16u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO = 17u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_BARCODE implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_HDR = 18u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT = 19u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode.ANDROID_CONTROL_SCENE_MODE_HDR implicitly + 1 */,
    ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START = 100u,
    ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END = 127u,
};

/**
 * android.control.videoStabilizationMode enumeration values
 * @see ANDROID_CONTROL_VIDEO_STABILIZATION_MODE
 */
enum class CameraMetadataEnumAndroidControlVideoStabilizationMode : uint32_t {
    ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF = 0u,
    ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode.ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF implicitly + 1 */,
};

/**
 * android.control.aeState enumeration values
 * @see ANDROID_CONTROL_AE_STATE
 */
enum class CameraMetadataEnumAndroidControlAeState : uint32_t {
    ANDROID_CONTROL_AE_STATE_INACTIVE = 0u,
    ANDROID_CONTROL_AE_STATE_SEARCHING = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState.ANDROID_CONTROL_AE_STATE_INACTIVE implicitly + 1 */,
    ANDROID_CONTROL_AE_STATE_CONVERGED = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState.ANDROID_CONTROL_AE_STATE_SEARCHING implicitly + 1 */,
    ANDROID_CONTROL_AE_STATE_LOCKED = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState.ANDROID_CONTROL_AE_STATE_CONVERGED implicitly + 1 */,
    ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState.ANDROID_CONTROL_AE_STATE_LOCKED implicitly + 1 */,
    ANDROID_CONTROL_AE_STATE_PRECAPTURE = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState.ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED implicitly + 1 */,
};

/**
 * android.control.afState enumeration values
 * @see ANDROID_CONTROL_AF_STATE
 */
enum class CameraMetadataEnumAndroidControlAfState : uint32_t {
    ANDROID_CONTROL_AF_STATE_INACTIVE = 0u,
    ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_INACTIVE implicitly + 1 */,
    ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN implicitly + 1 */,
    ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED implicitly + 1 */,
    ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN implicitly + 1 */,
    ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED = 5u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED implicitly + 1 */,
    ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED = 6u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState.ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED implicitly + 1 */,
};

/**
 * android.control.awbState enumeration values
 * @see ANDROID_CONTROL_AWB_STATE
 */
enum class CameraMetadataEnumAndroidControlAwbState : uint32_t {
    ANDROID_CONTROL_AWB_STATE_INACTIVE = 0u,
    ANDROID_CONTROL_AWB_STATE_SEARCHING = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState.ANDROID_CONTROL_AWB_STATE_INACTIVE implicitly + 1 */,
    ANDROID_CONTROL_AWB_STATE_CONVERGED = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState.ANDROID_CONTROL_AWB_STATE_SEARCHING implicitly + 1 */,
    ANDROID_CONTROL_AWB_STATE_LOCKED = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState.ANDROID_CONTROL_AWB_STATE_CONVERGED implicitly + 1 */,
};

/**
 * android.control.aeLockAvailable enumeration values
 * @see ANDROID_CONTROL_AE_LOCK_AVAILABLE
 */
enum class CameraMetadataEnumAndroidControlAeLockAvailable : uint32_t {
    ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE = 0u,
    ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable.ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE implicitly + 1 */,
};

/**
 * android.control.awbLockAvailable enumeration values
 * @see ANDROID_CONTROL_AWB_LOCK_AVAILABLE
 */
enum class CameraMetadataEnumAndroidControlAwbLockAvailable : uint32_t {
    ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE = 0u,
    ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable.ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE implicitly + 1 */,
};

/**
 * android.control.enableZsl enumeration values
 * @see ANDROID_CONTROL_ENABLE_ZSL
 */
enum class CameraMetadataEnumAndroidControlEnableZsl : uint32_t {
    ANDROID_CONTROL_ENABLE_ZSL_FALSE = 0u,
    ANDROID_CONTROL_ENABLE_ZSL_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl.ANDROID_CONTROL_ENABLE_ZSL_FALSE implicitly + 1 */,
};

/**
 * android.demosaic.mode enumeration values
 * @see ANDROID_DEMOSAIC_MODE
 */
enum class CameraMetadataEnumAndroidDemosaicMode : uint32_t {
    ANDROID_DEMOSAIC_MODE_FAST = 0u,
    ANDROID_DEMOSAIC_MODE_HIGH_QUALITY = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode.ANDROID_DEMOSAIC_MODE_FAST implicitly + 1 */,
};

/**
 * android.edge.mode enumeration values
 * @see ANDROID_EDGE_MODE
 */
enum class CameraMetadataEnumAndroidEdgeMode : uint32_t {
    ANDROID_EDGE_MODE_OFF = 0u,
    ANDROID_EDGE_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode.ANDROID_EDGE_MODE_OFF implicitly + 1 */,
    ANDROID_EDGE_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode.ANDROID_EDGE_MODE_FAST implicitly + 1 */,
    ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode.ANDROID_EDGE_MODE_HIGH_QUALITY implicitly + 1 */,
};

/**
 * android.flash.mode enumeration values
 * @see ANDROID_FLASH_MODE
 */
enum class CameraMetadataEnumAndroidFlashMode : uint32_t {
    ANDROID_FLASH_MODE_OFF = 0u,
    ANDROID_FLASH_MODE_SINGLE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode.ANDROID_FLASH_MODE_OFF implicitly + 1 */,
    ANDROID_FLASH_MODE_TORCH = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode.ANDROID_FLASH_MODE_SINGLE implicitly + 1 */,
};

/**
 * android.flash.state enumeration values
 * @see ANDROID_FLASH_STATE
 */
enum class CameraMetadataEnumAndroidFlashState : uint32_t {
    ANDROID_FLASH_STATE_UNAVAILABLE = 0u,
    ANDROID_FLASH_STATE_CHARGING = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState.ANDROID_FLASH_STATE_UNAVAILABLE implicitly + 1 */,
    ANDROID_FLASH_STATE_READY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState.ANDROID_FLASH_STATE_CHARGING implicitly + 1 */,
    ANDROID_FLASH_STATE_FIRED = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState.ANDROID_FLASH_STATE_READY implicitly + 1 */,
    ANDROID_FLASH_STATE_PARTIAL = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState.ANDROID_FLASH_STATE_FIRED implicitly + 1 */,
};

/**
 * android.flash.info.available enumeration values
 * @see ANDROID_FLASH_INFO_AVAILABLE
 */
enum class CameraMetadataEnumAndroidFlashInfoAvailable : uint32_t {
    ANDROID_FLASH_INFO_AVAILABLE_FALSE = 0u,
    ANDROID_FLASH_INFO_AVAILABLE_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable.ANDROID_FLASH_INFO_AVAILABLE_FALSE implicitly + 1 */,
};

/**
 * android.hotPixel.mode enumeration values
 * @see ANDROID_HOT_PIXEL_MODE
 */
enum class CameraMetadataEnumAndroidHotPixelMode : uint32_t {
    ANDROID_HOT_PIXEL_MODE_OFF = 0u,
    ANDROID_HOT_PIXEL_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode.ANDROID_HOT_PIXEL_MODE_OFF implicitly + 1 */,
    ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode.ANDROID_HOT_PIXEL_MODE_FAST implicitly + 1 */,
};

/**
 * android.lens.opticalStabilizationMode enumeration values
 * @see ANDROID_LENS_OPTICAL_STABILIZATION_MODE
 */
enum class CameraMetadataEnumAndroidLensOpticalStabilizationMode : uint32_t {
    ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF = 0u,
    ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode.ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF implicitly + 1 */,
};

/**
 * android.lens.facing enumeration values
 * @see ANDROID_LENS_FACING
 */
enum class CameraMetadataEnumAndroidLensFacing : uint32_t {
    ANDROID_LENS_FACING_FRONT = 0u,
    ANDROID_LENS_FACING_BACK = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing.ANDROID_LENS_FACING_FRONT implicitly + 1 */,
    ANDROID_LENS_FACING_EXTERNAL = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing.ANDROID_LENS_FACING_BACK implicitly + 1 */,
};

/**
 * android.lens.state enumeration values
 * @see ANDROID_LENS_STATE
 */
enum class CameraMetadataEnumAndroidLensState : uint32_t {
    ANDROID_LENS_STATE_STATIONARY = 0u,
    ANDROID_LENS_STATE_MOVING = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState.ANDROID_LENS_STATE_STATIONARY implicitly + 1 */,
};

/**
 * android.lens.info.focusDistanceCalibration enumeration values
 * @see ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION
 */
enum class CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration : uint32_t {
    ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED = 0u,
    ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration.ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED implicitly + 1 */,
    ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration.ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE implicitly + 1 */,
};

/**
 * android.noiseReduction.mode enumeration values
 * @see ANDROID_NOISE_REDUCTION_MODE
 */
enum class CameraMetadataEnumAndroidNoiseReductionMode : uint32_t {
    ANDROID_NOISE_REDUCTION_MODE_OFF = 0u,
    ANDROID_NOISE_REDUCTION_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode.ANDROID_NOISE_REDUCTION_MODE_OFF implicitly + 1 */,
    ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode.ANDROID_NOISE_REDUCTION_MODE_FAST implicitly + 1 */,
    ANDROID_NOISE_REDUCTION_MODE_MINIMAL = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode.ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY implicitly + 1 */,
    ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode.ANDROID_NOISE_REDUCTION_MODE_MINIMAL implicitly + 1 */,
};

/**
 * android.quirks.partialResult enumeration values
 * @see ANDROID_QUIRKS_PARTIAL_RESULT
 */
enum class CameraMetadataEnumAndroidQuirksPartialResult : uint32_t {
    ANDROID_QUIRKS_PARTIAL_RESULT_FINAL = 0u,
    ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult.ANDROID_QUIRKS_PARTIAL_RESULT_FINAL implicitly + 1 */,
};

/**
 * android.request.metadataMode enumeration values
 * @see ANDROID_REQUEST_METADATA_MODE
 */
enum class CameraMetadataEnumAndroidRequestMetadataMode : uint32_t {
    ANDROID_REQUEST_METADATA_MODE_NONE = 0u,
    ANDROID_REQUEST_METADATA_MODE_FULL = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode.ANDROID_REQUEST_METADATA_MODE_NONE implicitly + 1 */,
};

/**
 * android.request.type enumeration values
 * @see ANDROID_REQUEST_TYPE
 */
enum class CameraMetadataEnumAndroidRequestType : uint32_t {
    ANDROID_REQUEST_TYPE_CAPTURE = 0u,
    ANDROID_REQUEST_TYPE_REPROCESS = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType.ANDROID_REQUEST_TYPE_CAPTURE implicitly + 1 */,
};

/**
 * android.request.availableCapabilities enumeration values
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
};

/**
 * android.scaler.availableFormats enumeration values
 * @see ANDROID_SCALER_AVAILABLE_FORMATS
 */
enum class CameraMetadataEnumAndroidScalerAvailableFormats : uint32_t {
    ANDROID_SCALER_AVAILABLE_FORMATS_RAW16 = 32u /* 0x20 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE = 36u /* 0x24 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_YV12 = 842094169u /* 0x32315659 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP = 17u /* 0x11 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED = 34u /* 0x22 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888 = 35u /* 0x23 */,
    ANDROID_SCALER_AVAILABLE_FORMATS_BLOB = 33u /* 0x21 */,
};

/**
 * android.scaler.availableStreamConfigurations enumeration values
 * @see ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS
 */
enum class CameraMetadataEnumAndroidScalerAvailableStreamConfigurations : uint32_t {
    ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT = 0u,
    ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations.ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT implicitly + 1 */,
};

/**
 * android.scaler.croppingType enumeration values
 * @see ANDROID_SCALER_CROPPING_TYPE
 */
enum class CameraMetadataEnumAndroidScalerCroppingType : uint32_t {
    ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY = 0u,
    ANDROID_SCALER_CROPPING_TYPE_FREEFORM = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType.ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY implicitly + 1 */,
};

/**
 * android.sensor.referenceIlluminant1 enumeration values
 * @see ANDROID_SENSOR_REFERENCE_ILLUMINANT1
 */
enum class CameraMetadataEnumAndroidSensorReferenceIlluminant1 : uint32_t {
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT = 1u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT = 2u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN = 3u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH = 4u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER = 9u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER = 10u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE = 11u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT = 12u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT = 13u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT = 14u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT = 15u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A = 17u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B = 18u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C = 19u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55 = 20u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65 = 21u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75 = 22u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50 = 23u,
    ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN = 24u,
};

/**
 * android.sensor.testPatternMode enumeration values
 * @see ANDROID_SENSOR_TEST_PATTERN_MODE
 */
enum class CameraMetadataEnumAndroidSensorTestPatternMode : uint32_t {
    ANDROID_SENSOR_TEST_PATTERN_MODE_OFF = 0u,
    ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode.ANDROID_SENSOR_TEST_PATTERN_MODE_OFF implicitly + 1 */,
    ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode.ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR implicitly + 1 */,
    ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode.ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS implicitly + 1 */,
    ANDROID_SENSOR_TEST_PATTERN_MODE_PN9 = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode.ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY implicitly + 1 */,
    ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1 = 256u,
};

/**
 * android.sensor.info.colorFilterArrangement enumeration values
 * @see ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
 */
enum class CameraMetadataEnumAndroidSensorInfoColorFilterArrangement : uint32_t {
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB = 0u,
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement.ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB implicitly + 1 */,
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement.ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG implicitly + 1 */,
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement.ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG implicitly + 1 */,
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement.ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR implicitly + 1 */,
};

/**
 * android.sensor.info.timestampSource enumeration values
 * @see ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE
 */
enum class CameraMetadataEnumAndroidSensorInfoTimestampSource : uint32_t {
    ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN = 0u,
    ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource.ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN implicitly + 1 */,
};

/**
 * android.sensor.info.lensShadingApplied enumeration values
 * @see ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED
 */
enum class CameraMetadataEnumAndroidSensorInfoLensShadingApplied : uint32_t {
    ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE = 0u,
    ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied.ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE implicitly + 1 */,
};

/**
 * android.shading.mode enumeration values
 * @see ANDROID_SHADING_MODE
 */
enum class CameraMetadataEnumAndroidShadingMode : uint32_t {
    ANDROID_SHADING_MODE_OFF = 0u,
    ANDROID_SHADING_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode.ANDROID_SHADING_MODE_OFF implicitly + 1 */,
    ANDROID_SHADING_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode.ANDROID_SHADING_MODE_FAST implicitly + 1 */,
};

/**
 * android.statistics.faceDetectMode enumeration values
 * @see ANDROID_STATISTICS_FACE_DETECT_MODE
 */
enum class CameraMetadataEnumAndroidStatisticsFaceDetectMode : uint32_t {
    ANDROID_STATISTICS_FACE_DETECT_MODE_OFF = 0u,
    ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode.ANDROID_STATISTICS_FACE_DETECT_MODE_OFF implicitly + 1 */,
    ANDROID_STATISTICS_FACE_DETECT_MODE_FULL = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode.ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE implicitly + 1 */,
};

/**
 * android.statistics.histogramMode enumeration values
 * @see ANDROID_STATISTICS_HISTOGRAM_MODE
 */
enum class CameraMetadataEnumAndroidStatisticsHistogramMode : uint32_t {
    ANDROID_STATISTICS_HISTOGRAM_MODE_OFF = 0u,
    ANDROID_STATISTICS_HISTOGRAM_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode.ANDROID_STATISTICS_HISTOGRAM_MODE_OFF implicitly + 1 */,
};

/**
 * android.statistics.sharpnessMapMode enumeration values
 * @see ANDROID_STATISTICS_SHARPNESS_MAP_MODE
 */
enum class CameraMetadataEnumAndroidStatisticsSharpnessMapMode : uint32_t {
    ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF = 0u,
    ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode.ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF implicitly + 1 */,
};

/**
 * android.statistics.hotPixelMapMode enumeration values
 * @see ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE
 */
enum class CameraMetadataEnumAndroidStatisticsHotPixelMapMode : uint32_t {
    ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF = 0u,
    ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode.ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF implicitly + 1 */,
};

/**
 * android.statistics.sceneFlicker enumeration values
 * @see ANDROID_STATISTICS_SCENE_FLICKER
 */
enum class CameraMetadataEnumAndroidStatisticsSceneFlicker : uint32_t {
    ANDROID_STATISTICS_SCENE_FLICKER_NONE = 0u,
    ANDROID_STATISTICS_SCENE_FLICKER_50HZ = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker.ANDROID_STATISTICS_SCENE_FLICKER_NONE implicitly + 1 */,
    ANDROID_STATISTICS_SCENE_FLICKER_60HZ = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker.ANDROID_STATISTICS_SCENE_FLICKER_50HZ implicitly + 1 */,
};

/**
 * android.statistics.lensShadingMapMode enumeration values
 * @see ANDROID_STATISTICS_LENS_SHADING_MAP_MODE
 */
enum class CameraMetadataEnumAndroidStatisticsLensShadingMapMode : uint32_t {
    ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF = 0u,
    ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode.ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF implicitly + 1 */,
};

/**
 * android.tonemap.mode enumeration values
 * @see ANDROID_TONEMAP_MODE
 */
enum class CameraMetadataEnumAndroidTonemapMode : uint32_t {
    ANDROID_TONEMAP_MODE_CONTRAST_CURVE = 0u,
    ANDROID_TONEMAP_MODE_FAST = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode.ANDROID_TONEMAP_MODE_CONTRAST_CURVE implicitly + 1 */,
    ANDROID_TONEMAP_MODE_HIGH_QUALITY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode.ANDROID_TONEMAP_MODE_FAST implicitly + 1 */,
    ANDROID_TONEMAP_MODE_GAMMA_VALUE = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode.ANDROID_TONEMAP_MODE_HIGH_QUALITY implicitly + 1 */,
    ANDROID_TONEMAP_MODE_PRESET_CURVE = 4u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode.ANDROID_TONEMAP_MODE_GAMMA_VALUE implicitly + 1 */,
};

/**
 * android.tonemap.presetCurve enumeration values
 * @see ANDROID_TONEMAP_PRESET_CURVE
 */
enum class CameraMetadataEnumAndroidTonemapPresetCurve : uint32_t {
    ANDROID_TONEMAP_PRESET_CURVE_SRGB = 0u,
    ANDROID_TONEMAP_PRESET_CURVE_REC709 = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve.ANDROID_TONEMAP_PRESET_CURVE_SRGB implicitly + 1 */,
};

/**
 * android.led.transmit enumeration values
 * @see ANDROID_LED_TRANSMIT
 */
enum class CameraMetadataEnumAndroidLedTransmit : uint32_t {
    ANDROID_LED_TRANSMIT_OFF = 0u,
    ANDROID_LED_TRANSMIT_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit.ANDROID_LED_TRANSMIT_OFF implicitly + 1 */,
};

/**
 * android.led.availableLeds enumeration values
 * @see ANDROID_LED_AVAILABLE_LEDS
 */
enum class CameraMetadataEnumAndroidLedAvailableLeds : uint32_t {
    ANDROID_LED_AVAILABLE_LEDS_TRANSMIT = 0u,
};

/**
 * android.info.supportedHardwareLevel enumeration values
 * @see ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL
 */
enum class CameraMetadataEnumAndroidInfoSupportedHardwareLevel : uint32_t {
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0u,
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel.ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED implicitly + 1 */,
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY = 2u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel.ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL implicitly + 1 */,
    ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3 = 3u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel.ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY implicitly + 1 */,
};

/**
 * android.blackLevel.lock enumeration values
 * @see ANDROID_BLACK_LEVEL_LOCK
 */
enum class CameraMetadataEnumAndroidBlackLevelLock : uint32_t {
    ANDROID_BLACK_LEVEL_LOCK_OFF = 0u,
    ANDROID_BLACK_LEVEL_LOCK_ON = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock.ANDROID_BLACK_LEVEL_LOCK_OFF implicitly + 1 */,
};

/**
 * android.sync.frameNumber enumeration values
 * @see ANDROID_SYNC_FRAME_NUMBER
 */
enum class CameraMetadataEnumAndroidSyncFrameNumber : uint32_t {
    ANDROID_SYNC_FRAME_NUMBER_CONVERGING = 4294967295u /* -1 */,
    ANDROID_SYNC_FRAME_NUMBER_UNKNOWN = 4294967294u /* -2 */,
};

/**
 * android.sync.maxLatency enumeration values
 * @see ANDROID_SYNC_MAX_LATENCY
 */
enum class CameraMetadataEnumAndroidSyncMaxLatency : uint32_t {
    ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL = 0u,
    ANDROID_SYNC_MAX_LATENCY_UNKNOWN = 4294967295u /* -1 */,
};

/**
 * android.depth.availableDepthStreamConfigurations enumeration values
 * @see ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS
 */
enum class CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations : uint32_t {
    ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT = 0u,
    ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations.ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT implicitly + 1 */,
};

/**
 * android.depth.depthIsExclusive enumeration values
 * @see ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE
 */
enum class CameraMetadataEnumAndroidDepthDepthIsExclusive : uint32_t {
    ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE = 0u,
    ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE = 1u /* ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive.ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE implicitly + 1 */,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataSection o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataSection o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSection e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataTag o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataTag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataTag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive o);
static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataSection>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataSection> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_COLOR_CORRECTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_COLOR_CORRECTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_COLOR_CORRECTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_CONTROL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_CONTROL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_CONTROL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEMOSAIC) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEMOSAIC)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEMOSAIC;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_EDGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_EDGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_EDGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH_INFO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH_INFO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH_INFO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_HOT_PIXEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_HOT_PIXEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_HOT_PIXEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_JPEG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_JPEG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_JPEG;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS_INFO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS_INFO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS_INFO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_NOISE_REDUCTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_NOISE_REDUCTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_NOISE_REDUCTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_QUIRKS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_QUIRKS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_QUIRKS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REQUEST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REQUEST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REQUEST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SCALER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SCALER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SCALER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR_INFO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR_INFO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR_INFO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SHADING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SHADING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SHADING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS_INFO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS_INFO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS_INFO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_TONEMAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_TONEMAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_TONEMAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_INFO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_INFO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_INFO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_BLACK_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_BLACK_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_BLACK_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SYNC) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SYNC)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SYNC;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REPROCESS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REPROCESS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REPROCESS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEPTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEPTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEPTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SECTION_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SECTION_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SECTION_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SECTION_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::VENDOR_SECTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSection::VENDOR_SECTION)) {
        os += (first ? "" : " | ");
        os += "VENDOR_SECTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::VENDOR_SECTION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataSection o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_COLOR_CORRECTION) {
        return "ANDROID_COLOR_CORRECTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_CONTROL) {
        return "ANDROID_CONTROL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEMOSAIC) {
        return "ANDROID_DEMOSAIC";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_EDGE) {
        return "ANDROID_EDGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH) {
        return "ANDROID_FLASH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH_INFO) {
        return "ANDROID_FLASH_INFO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_HOT_PIXEL) {
        return "ANDROID_HOT_PIXEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_JPEG) {
        return "ANDROID_JPEG";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS) {
        return "ANDROID_LENS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS_INFO) {
        return "ANDROID_LENS_INFO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_NOISE_REDUCTION) {
        return "ANDROID_NOISE_REDUCTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_QUIRKS) {
        return "ANDROID_QUIRKS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REQUEST) {
        return "ANDROID_REQUEST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SCALER) {
        return "ANDROID_SCALER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR) {
        return "ANDROID_SENSOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR_INFO) {
        return "ANDROID_SENSOR_INFO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SHADING) {
        return "ANDROID_SHADING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS) {
        return "ANDROID_STATISTICS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS_INFO) {
        return "ANDROID_STATISTICS_INFO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_TONEMAP) {
        return "ANDROID_TONEMAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LED) {
        return "ANDROID_LED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_INFO) {
        return "ANDROID_INFO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_BLACK_LEVEL) {
        return "ANDROID_BLACK_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SYNC) {
        return "ANDROID_SYNC";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REPROCESS) {
        return "ANDROID_REPROCESS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEPTH) {
        return "ANDROID_DEPTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SECTION_COUNT) {
        return "ANDROID_SECTION_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::VENDOR_SECTION) {
        return "VENDOR_SECTION";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataSection o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_CONTROL_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_CONTROL_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_CONTROL_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_EDGE_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_EDGE_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_EDGE_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_JPEG_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_JPEG_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_JPEG_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_INFO_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_INFO_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_INFO_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_QUIRKS_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_QUIRKS_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_QUIRKS_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REQUEST_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REQUEST_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REQUEST_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SCALER_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SCALER_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SCALER_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SHADING_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SHADING_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SHADING_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_TONEMAP_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_TONEMAP_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_TONEMAP_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LED_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LED_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LED_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_INFO_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_INFO_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_INFO_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SYNC_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SYNC_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SYNC_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REPROCESS_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REPROCESS_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REPROCESS_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEPTH_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEPTH_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEPTH_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::VENDOR_SECTION_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::VENDOR_SECTION_START)) {
        os += (first ? "" : " | ");
        os += "VENDOR_SECTION_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::VENDOR_SECTION_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START) {
        return "ANDROID_COLOR_CORRECTION_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_CONTROL_START) {
        return "ANDROID_CONTROL_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START) {
        return "ANDROID_DEMOSAIC_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_EDGE_START) {
        return "ANDROID_EDGE_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_START) {
        return "ANDROID_FLASH_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START) {
        return "ANDROID_FLASH_INFO_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START) {
        return "ANDROID_HOT_PIXEL_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_JPEG_START) {
        return "ANDROID_JPEG_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_START) {
        return "ANDROID_LENS_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_INFO_START) {
        return "ANDROID_LENS_INFO_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START) {
        return "ANDROID_NOISE_REDUCTION_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_QUIRKS_START) {
        return "ANDROID_QUIRKS_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REQUEST_START) {
        return "ANDROID_REQUEST_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SCALER_START) {
        return "ANDROID_SCALER_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_START) {
        return "ANDROID_SENSOR_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START) {
        return "ANDROID_SENSOR_INFO_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SHADING_START) {
        return "ANDROID_SHADING_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_START) {
        return "ANDROID_STATISTICS_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START) {
        return "ANDROID_STATISTICS_INFO_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_TONEMAP_START) {
        return "ANDROID_TONEMAP_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LED_START) {
        return "ANDROID_LED_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_INFO_START) {
        return "ANDROID_INFO_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START) {
        return "ANDROID_BLACK_LEVEL_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SYNC_START) {
        return "ANDROID_SYNC_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REPROCESS_START) {
        return "ANDROID_REPROCESS_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEPTH_START) {
        return "ANDROID_DEPTH_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::VENDOR_SECTION_START) {
        return "VENDOR_SECTION_START";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataTag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataTag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_TRANSFORM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_GAINS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_ABERRATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_TARGET_FPS_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_COMPENSATION_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_COMPENSATION_STEP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_EFFECTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_SCENE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MAX_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_OVERRIDES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ENABLE_ZSL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_AVAILABLE_EDGE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_FIRING_POWER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_FIRING_TIME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_COLOR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MAX_ENERGY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_CHARGE_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_COORDINATES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_PROCESSING_METHOD";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_GPS_TIMESTAMP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_ORIENTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_ORIENTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_THUMBNAIL_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_THUMBNAIL_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_MAX_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_JPEG_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_APERTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_APERTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_APERTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_APERTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FILTER_DENSITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCAL_LENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCUS_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_OPTICAL_STABILIZATION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FACING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FACING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FACING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FACING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_ROTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_POSE_TRANSLATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FOCUS_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_STATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INTRINSIC_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_RADIAL_DISTORTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_APERTURES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_SHADING_MAP_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_METERING_CROP_REGION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_USE_ZSL_FORMAT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_USE_PARTIAL_RESULT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_PARTIAL_RESULT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_FRAME_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_ID) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_ID)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_ID";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_ID;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_INPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_METADATA_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_OUTPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_TYPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_TYPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_TYPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PIPELINE_DEPTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PIPELINE_MAX_DEPTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_PARTIAL_RESULT_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_RESULT_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROP_REGION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROP_REGION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROP_REGION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROP_REGION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_JPEG_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_RAW_SIZES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROPPING_TYPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_EXPOSURE_TIME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FRAME_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_SENSITIVITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_CALIBRATION_TRANSFORM1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_CALIBRATION_TRANSFORM2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_COLOR_TRANSFORM1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_COLOR_TRANSFORM2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FORWARD_MATRIX1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_FORWARD_MATRIX2";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_BASE_GAIN_FACTOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_BLACK_LEVEL_PATTERN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TIMESTAMP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_NEUTRAL_COLOR_POINT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_NOISE_PROFILE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_PROFILE_TONE_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_GREEN_SPLIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_DATA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_ROLLING_SHUTTER_SKEW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_OPTICAL_BLACK_REGIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_OPAQUE_RAW_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_SENSITIVITY_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_MAX_FRAME_DURATION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PHYSICAL_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_WHITE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_STRENGTH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_STRENGTH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_STRENGTH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_AVAILABLE_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_DETECT_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_IDS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_LANDMARKS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_RECTANGLES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_SCORES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_PREDICTED_COLOR_GAINS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SCENE_FLICKER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_FACE_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_BLUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_GREEN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_CURVE_RED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MAX_CURVE_POINTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_GAMMA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_GAMMA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_GAMMA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_GAMMA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_PRESET_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_TRANSMIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_TRANSMIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_TRANSMIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_TRANSMIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_AVAILABLE_LEDS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_LOCK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_FRAME_NUMBER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_MAX_LATENCY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_MAX_CAPTURE_STALL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REPROCESS_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_END;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_MAX_DEPTH_SAMPLES";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_END;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataTag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE) {
        return "ANDROID_COLOR_CORRECTION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM) {
        return "ANDROID_COLOR_CORRECTION_TRANSFORM";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS) {
        return "ANDROID_COLOR_CORRECTION_GAINS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE) {
        return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) {
        return "ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END) {
        return "ANDROID_COLOR_CORRECTION_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION) {
        return "ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK) {
        return "ANDROID_CONTROL_AE_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_MODE) {
        return "ANDROID_CONTROL_AE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS) {
        return "ANDROID_CONTROL_AE_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE) {
        return "ANDROID_CONTROL_AE_TARGET_FPS_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_MODE) {
        return "ANDROID_CONTROL_AF_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS) {
        return "ANDROID_CONTROL_AF_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER) {
        return "ANDROID_CONTROL_AF_TRIGGER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK) {
        return "ANDROID_CONTROL_AWB_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE) {
        return "ANDROID_CONTROL_AWB_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS) {
        return "ANDROID_CONTROL_AWB_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT) {
        return "ANDROID_CONTROL_CAPTURE_INTENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE) {
        return "ANDROID_CONTROL_EFFECT_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MODE) {
        return "ANDROID_CONTROL_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE) {
        return "ANDROID_CONTROL_SCENE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE) {
        return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) {
        return "ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE) {
        return "ANDROID_CONTROL_AE_COMPENSATION_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP) {
        return "ANDROID_CONTROL_AE_COMPENSATION_STEP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AF_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS) {
        return "ANDROID_CONTROL_AVAILABLE_EFFECTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_SCENE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AWB_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS) {
        return "ANDROID_CONTROL_MAX_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES) {
        return "ANDROID_CONTROL_SCENE_MODE_OVERRIDES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_STATE) {
        return "ANDROID_CONTROL_AE_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_STATE) {
        return "ANDROID_CONTROL_AF_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID) {
        return "ANDROID_CONTROL_AF_TRIGGER_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE) {
        return "ANDROID_CONTROL_AWB_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS) {
        return "ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE) {
        return "ANDROID_CONTROL_AE_LOCK_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE) {
        return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES) {
        return "ANDROID_CONTROL_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE) {
        return "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST) {
        return "ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL) {
        return "ANDROID_CONTROL_ENABLE_ZSL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_END) {
        return "ANDROID_CONTROL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_MODE) {
        return "ANDROID_DEMOSAIC_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_END) {
        return "ANDROID_DEMOSAIC_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_MODE) {
        return "ANDROID_EDGE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_STRENGTH) {
        return "ANDROID_EDGE_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES) {
        return "ANDROID_EDGE_AVAILABLE_EDGE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_END) {
        return "ANDROID_EDGE_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER) {
        return "ANDROID_FLASH_FIRING_POWER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME) {
        return "ANDROID_FLASH_FIRING_TIME";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MODE) {
        return "ANDROID_FLASH_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE) {
        return "ANDROID_FLASH_COLOR_TEMPERATURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY) {
        return "ANDROID_FLASH_MAX_ENERGY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_STATE) {
        return "ANDROID_FLASH_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_END) {
        return "ANDROID_FLASH_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE) {
        return "ANDROID_FLASH_INFO_AVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION) {
        return "ANDROID_FLASH_INFO_CHARGE_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_END) {
        return "ANDROID_FLASH_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE) {
        return "ANDROID_HOT_PIXEL_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES) {
        return "ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_END) {
        return "ANDROID_HOT_PIXEL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES) {
        return "ANDROID_JPEG_GPS_COORDINATES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD) {
        return "ANDROID_JPEG_GPS_PROCESSING_METHOD";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP) {
        return "ANDROID_JPEG_GPS_TIMESTAMP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_ORIENTATION) {
        return "ANDROID_JPEG_ORIENTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_QUALITY) {
        return "ANDROID_JPEG_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY) {
        return "ANDROID_JPEG_THUMBNAIL_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE) {
        return "ANDROID_JPEG_THUMBNAIL_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES) {
        return "ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE) {
        return "ANDROID_JPEG_MAX_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_SIZE) {
        return "ANDROID_JPEG_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_END) {
        return "ANDROID_JPEG_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_APERTURE) {
        return "ANDROID_LENS_APERTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY) {
        return "ANDROID_LENS_FILTER_DENSITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH) {
        return "ANDROID_LENS_FOCAL_LENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE) {
        return "ANDROID_LENS_FOCUS_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE) {
        return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FACING) {
        return "ANDROID_LENS_FACING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION) {
        return "ANDROID_LENS_POSE_ROTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION) {
        return "ANDROID_LENS_POSE_TRANSLATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE) {
        return "ANDROID_LENS_FOCUS_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_STATE) {
        return "ANDROID_LENS_STATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION) {
        return "ANDROID_LENS_INTRINSIC_CALIBRATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION) {
        return "ANDROID_LENS_RADIAL_DISTORTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_END) {
        return "ANDROID_LENS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES) {
        return "ANDROID_LENS_INFO_AVAILABLE_APERTURES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES) {
        return "ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS) {
        return "ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) {
        return "ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE) {
        return "ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE) {
        return "ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE) {
        return "ANDROID_LENS_INFO_SHADING_MAP_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION) {
        return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_END) {
        return "ANDROID_LENS_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE) {
        return "ANDROID_NOISE_REDUCTION_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH) {
        return "ANDROID_NOISE_REDUCTION_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) {
        return "ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END) {
        return "ANDROID_NOISE_REDUCTION_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION) {
        return "ANDROID_QUIRKS_METERING_CROP_REGION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO) {
        return "ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT) {
        return "ANDROID_QUIRKS_USE_ZSL_FORMAT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT) {
        return "ANDROID_QUIRKS_USE_PARTIAL_RESULT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT) {
        return "ANDROID_QUIRKS_PARTIAL_RESULT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_END) {
        return "ANDROID_QUIRKS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT) {
        return "ANDROID_REQUEST_FRAME_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_ID) {
        return "ANDROID_REQUEST_ID";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS) {
        return "ANDROID_REQUEST_INPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE) {
        return "ANDROID_REQUEST_METADATA_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS) {
        return "ANDROID_REQUEST_OUTPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_TYPE) {
        return "ANDROID_REQUEST_TYPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS) {
        return "ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH) {
        return "ANDROID_REQUEST_PIPELINE_DEPTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH) {
        return "ANDROID_REQUEST_PIPELINE_MAX_DEPTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT) {
        return "ANDROID_REQUEST_PARTIAL_RESULT_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_RESULT_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS) {
        return "ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_END) {
        return "ANDROID_REQUEST_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROP_REGION) {
        return "ANDROID_SCALER_CROP_REGION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_JPEG_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) {
        return "ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES) {
        return "ANDROID_SCALER_AVAILABLE_RAW_SIZES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP) {
        return "ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS) {
        return "ANDROID_SCALER_AVAILABLE_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE) {
        return "ANDROID_SCALER_CROPPING_TYPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_END) {
        return "ANDROID_SCALER_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME) {
        return "ANDROID_SENSOR_EXPOSURE_TIME";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION) {
        return "ANDROID_SENSOR_FRAME_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY) {
        return "ANDROID_SENSOR_SENSITIVITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT2";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1) {
        return "ANDROID_SENSOR_CALIBRATION_TRANSFORM1";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2) {
        return "ANDROID_SENSOR_CALIBRATION_TRANSFORM2";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1) {
        return "ANDROID_SENSOR_COLOR_TRANSFORM1";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2) {
        return "ANDROID_SENSOR_COLOR_TRANSFORM2";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1) {
        return "ANDROID_SENSOR_FORWARD_MATRIX1";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2) {
        return "ANDROID_SENSOR_FORWARD_MATRIX2";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR) {
        return "ANDROID_SENSOR_BASE_GAIN_FACTOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN) {
        return "ANDROID_SENSOR_BLACK_LEVEL_PATTERN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY) {
        return "ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION) {
        return "ANDROID_SENSOR_ORIENTATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS) {
        return "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP) {
        return "ANDROID_SENSOR_TIMESTAMP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE) {
        return "ANDROID_SENSOR_TEMPERATURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT) {
        return "ANDROID_SENSOR_NEUTRAL_COLOR_POINT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE) {
        return "ANDROID_SENSOR_NOISE_PROFILE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP) {
        return "ANDROID_SENSOR_PROFILE_HUE_SAT_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE) {
        return "ANDROID_SENSOR_PROFILE_TONE_CURVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT) {
        return "ANDROID_SENSOR_GREEN_SPLIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA) {
        return "ANDROID_SENSOR_TEST_PATTERN_DATA";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES) {
        return "ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW) {
        return "ANDROID_SENSOR_ROLLING_SHUTTER_SKEW";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS) {
        return "ANDROID_SENSOR_OPTICAL_BLACK_REGIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL) {
        return "ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL) {
        return "ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE) {
        return "ANDROID_SENSOR_OPAQUE_RAW_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_END) {
        return "ANDROID_SENSOR_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE) {
        return "ANDROID_SENSOR_INFO_SENSITIVITY_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE) {
        return "ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION) {
        return "ANDROID_SENSOR_INFO_MAX_FRAME_DURATION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE) {
        return "ANDROID_SENSOR_INFO_PHYSICAL_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL) {
        return "ANDROID_SENSOR_INFO_WHITE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE) {
        return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED) {
        return "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE) {
        return "ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_END) {
        return "ANDROID_SENSOR_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_MODE) {
        return "ANDROID_SHADING_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_STRENGTH) {
        return "ANDROID_SHADING_STRENGTH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES) {
        return "ANDROID_SHADING_AVAILABLE_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_END) {
        return "ANDROID_SHADING_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE) {
        return "ANDROID_STATISTICS_FACE_DETECT_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE) {
        return "ANDROID_STATISTICS_HISTOGRAM_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS) {
        return "ANDROID_STATISTICS_FACE_IDS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS) {
        return "ANDROID_STATISTICS_FACE_LANDMARKS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES) {
        return "ANDROID_STATISTICS_FACE_RECTANGLES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES) {
        return "ANDROID_STATISTICS_FACE_SCORES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM) {
        return "ANDROID_STATISTICS_HISTOGRAM";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP) {
        return "ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS) {
        return "ANDROID_STATISTICS_PREDICTED_COLOR_GAINS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM) {
        return "ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER) {
        return "ANDROID_STATISTICS_SCENE_FLICKER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_END) {
        return "ANDROID_STATISTICS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT) {
        return "ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT) {
        return "ANDROID_STATISTICS_INFO_MAX_FACE_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT) {
        return "ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE) {
        return "ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE) {
        return "ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES) {
        return "ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_END) {
        return "ANDROID_STATISTICS_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE) {
        return "ANDROID_TONEMAP_CURVE_BLUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN) {
        return "ANDROID_TONEMAP_CURVE_GREEN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED) {
        return "ANDROID_TONEMAP_CURVE_RED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MODE) {
        return "ANDROID_TONEMAP_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS) {
        return "ANDROID_TONEMAP_MAX_CURVE_POINTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES) {
        return "ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_GAMMA) {
        return "ANDROID_TONEMAP_GAMMA";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE) {
        return "ANDROID_TONEMAP_PRESET_CURVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_END) {
        return "ANDROID_TONEMAP_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_TRANSMIT) {
        return "ANDROID_LED_TRANSMIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS) {
        return "ANDROID_LED_AVAILABLE_LEDS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_END) {
        return "ANDROID_LED_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_END) {
        return "ANDROID_INFO_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK) {
        return "ANDROID_BLACK_LEVEL_LOCK";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_END) {
        return "ANDROID_BLACK_LEVEL_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER) {
        return "ANDROID_SYNC_FRAME_NUMBER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY) {
        return "ANDROID_SYNC_MAX_LATENCY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_END) {
        return "ANDROID_SYNC_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR) {
        return "ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL) {
        return "ANDROID_REPROCESS_MAX_CAPTURE_STALL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_END) {
        return "ANDROID_REPROCESS_END";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES) {
        return "ANDROID_DEPTH_MAX_DEPTH_SAMPLES";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE) {
        return "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_END) {
        return "ANDROID_DEPTH_END";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataTag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX) {
        return "ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST) {
        return "ANDROID_COLOR_CORRECTION_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY) {
        return "ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF) {
        return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST) {
        return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) {
        return "ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO) {
        return "ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_OFF) {
        return "ANDROID_CONTROL_AE_LOCK_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_ON) {
        return "ANDROID_CONTROL_AE_LOCK_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_OFF) {
        return "ANDROID_CONTROL_AE_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON) {
        return "ANDROID_CONTROL_AE_MODE_ON";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH) {
        return "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH) {
        return "ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
        return "ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL) {
        return "ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_MACRO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_MODE_EDOF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_OFF) {
        return "ANDROID_CONTROL_AF_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO) {
        return "ANDROID_CONTROL_AF_MODE_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO) {
        return "ANDROID_CONTROL_AF_MODE_MACRO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
        return "ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
        return "ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF) {
        return "ANDROID_CONTROL_AF_MODE_EDOF";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER_IDLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_TRIGGER_CANCEL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE) {
        return "ANDROID_CONTROL_AF_TRIGGER_IDLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START) {
        return "ANDROID_CONTROL_AF_TRIGGER_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL) {
        return "ANDROID_CONTROL_AF_TRIGGER_CANCEL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_OFF) {
        return "ANDROID_CONTROL_AWB_LOCK_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_ON) {
        return "ANDROID_CONTROL_AWB_LOCK_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_INCANDESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_DAYLIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_TWILIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_MODE_SHADE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF) {
        return "ANDROID_CONTROL_AWB_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO) {
        return "ANDROID_CONTROL_AWB_MODE_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT) {
        return "ANDROID_CONTROL_AWB_MODE_INCANDESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT) {
        return "ANDROID_CONTROL_AWB_MODE_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT) {
        return "ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT) {
        return "ANDROID_CONTROL_AWB_MODE_DAYLIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) {
        return "ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT) {
        return "ANDROID_CONTROL_AWB_MODE_TWILIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE) {
        return "ANDROID_CONTROL_AWB_MODE_SHADE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_CAPTURE_INTENT_MANUAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL) {
        return "ANDROID_CONTROL_CAPTURE_INTENT_MANUAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_MONO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_NEGATIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_SOLARIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_SEPIA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_POSTERIZE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_EFFECT_MODE_AQUA";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF) {
        return "ANDROID_CONTROL_EFFECT_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO) {
        return "ANDROID_CONTROL_EFFECT_MODE_MONO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE) {
        return "ANDROID_CONTROL_EFFECT_MODE_NEGATIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE) {
        return "ANDROID_CONTROL_EFFECT_MODE_SOLARIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA) {
        return "ANDROID_CONTROL_EFFECT_MODE_SEPIA";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE) {
        return "ANDROID_CONTROL_EFFECT_MODE_POSTERIZE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD) {
        return "ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD) {
        return "ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA) {
        return "ANDROID_CONTROL_EFFECT_MODE_AQUA";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_AUTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_USE_SCENE_MODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_MODE_OFF_KEEP_STATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF) {
        return "ANDROID_CONTROL_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO) {
        return "ANDROID_CONTROL_MODE_AUTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE) {
        return "ANDROID_CONTROL_MODE_USE_SCENE_MODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE) {
        return "ANDROID_CONTROL_MODE_OFF_KEEP_STATE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_DISABLED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_ACTION";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_PORTRAIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_LANDSCAPE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_NIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_THEATRE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_BEACH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_SNOW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_SUNSET";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_FIREWORKS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_SPORTS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_PARTY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_BARCODE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_HDR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED) {
        return "ANDROID_CONTROL_SCENE_MODE_DISABLED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY) {
        return "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION) {
        return "ANDROID_CONTROL_SCENE_MODE_ACTION";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT) {
        return "ANDROID_CONTROL_SCENE_MODE_PORTRAIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE) {
        return "ANDROID_CONTROL_SCENE_MODE_LANDSCAPE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT) {
        return "ANDROID_CONTROL_SCENE_MODE_NIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT) {
        return "ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE) {
        return "ANDROID_CONTROL_SCENE_MODE_THEATRE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH) {
        return "ANDROID_CONTROL_SCENE_MODE_BEACH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW) {
        return "ANDROID_CONTROL_SCENE_MODE_SNOW";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET) {
        return "ANDROID_CONTROL_SCENE_MODE_SUNSET";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO) {
        return "ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS) {
        return "ANDROID_CONTROL_SCENE_MODE_FIREWORKS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS) {
        return "ANDROID_CONTROL_SCENE_MODE_SPORTS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY) {
        return "ANDROID_CONTROL_SCENE_MODE_PARTY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT) {
        return "ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE) {
        return "ANDROID_CONTROL_SCENE_MODE_BARCODE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO) {
        return "ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR) {
        return "ANDROID_CONTROL_SCENE_MODE_HDR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT) {
        return "ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START) {
        return "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END) {
        return "ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF) {
        return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON) {
        return "ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_INACTIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_SEARCHING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_CONVERGED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_LOCKED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_STATE_PRECAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE) {
        return "ANDROID_CONTROL_AE_STATE_INACTIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING) {
        return "ANDROID_CONTROL_AE_STATE_SEARCHING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED) {
        return "ANDROID_CONTROL_AE_STATE_CONVERGED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED) {
        return "ANDROID_CONTROL_AE_STATE_LOCKED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED) {
        return "ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE) {
        return "ANDROID_CONTROL_AE_STATE_PRECAPTURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_INACTIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE) {
        return "ANDROID_CONTROL_AF_STATE_INACTIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN) {
        return "ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED) {
        return "ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN) {
        return "ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED) {
        return "ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
        return "ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED) {
        return "ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE_INACTIVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE_SEARCHING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE_CONVERGED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_STATE_LOCKED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE) {
        return "ANDROID_CONTROL_AWB_STATE_INACTIVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING) {
        return "ANDROID_CONTROL_AWB_STATE_SEARCHING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED) {
        return "ANDROID_CONTROL_AWB_STATE_CONVERGED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED) {
        return "ANDROID_CONTROL_AWB_STATE_LOCKED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE) {
        return "ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE) {
        return "ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE) {
        return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE) {
        return "ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ENABLE_ZSL_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_CONTROL_ENABLE_ZSL_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_FALSE) {
        return "ANDROID_CONTROL_ENABLE_ZSL_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_TRUE) {
        return "ANDROID_CONTROL_ENABLE_ZSL_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEMOSAIC_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_FAST) {
        return "ANDROID_DEMOSAIC_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY) {
        return "ANDROID_DEMOSAIC_MODE_HIGH_QUALITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_HIGH_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_OFF) {
        return "ANDROID_EDGE_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_FAST) {
        return "ANDROID_EDGE_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_HIGH_QUALITY) {
        return "ANDROID_EDGE_MODE_HIGH_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG) {
        return "ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_SINGLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_SINGLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MODE_SINGLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_SINGLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_TORCH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_TORCH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_MODE_TORCH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_TORCH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_OFF) {
        return "ANDROID_FLASH_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_SINGLE) {
        return "ANDROID_FLASH_MODE_SINGLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_TORCH) {
        return "ANDROID_FLASH_MODE_TORCH";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_UNAVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_UNAVAILABLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_CHARGING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_CHARGING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE_CHARGING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_CHARGING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_READY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_READY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE_READY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_READY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_FIRED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_FIRED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE_FIRED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_FIRED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_PARTIAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_PARTIAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_STATE_PARTIAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_PARTIAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_UNAVAILABLE) {
        return "ANDROID_FLASH_STATE_UNAVAILABLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_CHARGING) {
        return "ANDROID_FLASH_STATE_CHARGING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_READY) {
        return "ANDROID_FLASH_STATE_READY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_FIRED) {
        return "ANDROID_FLASH_STATE_FIRED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_PARTIAL) {
        return "ANDROID_FLASH_STATE_PARTIAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_AVAILABLE_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_FLASH_INFO_AVAILABLE_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_FALSE) {
        return "ANDROID_FLASH_INFO_AVAILABLE_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_TRUE) {
        return "ANDROID_FLASH_INFO_AVAILABLE_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF) {
        return "ANDROID_HOT_PIXEL_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST) {
        return "ANDROID_HOT_PIXEL_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY) {
        return "ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF) {
        return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON) {
        return "ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_FRONT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_FRONT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FACING_FRONT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_FRONT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_BACK) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_BACK)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FACING_BACK";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_BACK;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_EXTERNAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_EXTERNAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_FACING_EXTERNAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_EXTERNAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_FRONT) {
        return "ANDROID_LENS_FACING_FRONT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_BACK) {
        return "ANDROID_LENS_FACING_BACK";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_EXTERNAL) {
        return "ANDROID_LENS_FACING_EXTERNAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_STATIONARY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_STATIONARY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_STATE_STATIONARY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_STATIONARY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_MOVING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_MOVING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_STATE_MOVING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_MOVING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_STATIONARY) {
        return "ANDROID_LENS_STATE_STATIONARY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_MOVING) {
        return "ANDROID_LENS_STATE_MOVING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED) {
        return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE) {
        return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED) {
        return "ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE_MINIMAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF) {
        return "ANDROID_NOISE_REDUCTION_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST) {
        return "ANDROID_NOISE_REDUCTION_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY) {
        return "ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL) {
        return "ANDROID_NOISE_REDUCTION_MODE_MINIMAL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG) {
        return "ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_PARTIAL_RESULT_FINAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL) {
        return "ANDROID_QUIRKS_PARTIAL_RESULT_FINAL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL) {
        return "ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_METADATA_MODE_NONE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_METADATA_MODE_FULL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE) {
        return "ANDROID_REQUEST_METADATA_MODE_NONE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL) {
        return "ANDROID_REQUEST_METADATA_MODE_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_TYPE_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_CAPTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_REPROCESS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_REPROCESS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_TYPE_REPROCESS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_REPROCESS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_CAPTURE) {
        return "ANDROID_REQUEST_TYPE_CAPTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_REPROCESS) {
        return "ANDROID_REQUEST_TYPE_REPROCESS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
        os += (first ? "" : " | ");
        os += "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) {
        return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_RAW16";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_YV12";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_FORMATS_BLOB";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW16";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_YV12";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB) {
        return "ANDROID_SCALER_AVAILABLE_FORMATS_BLOB";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT) {
        return "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT) {
        return "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SCALER_CROPPING_TYPE_FREEFORM";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY) {
        return "ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM) {
        return "ANDROID_SCALER_CROPPING_TYPE_FREEFORM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN) {
        return "ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1 o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_PN9";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_PN9";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1) {
        return "ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB) {
        return "ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) {
        return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
        return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE) {
        return "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE) {
        return "ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SHADING_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_HIGH_QUALITY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_OFF) {
        return "ANDROID_SHADING_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_FAST) {
        return "ANDROID_SHADING_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_HIGH_QUALITY) {
        return "ANDROID_SHADING_MODE_HIGH_QUALITY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_DETECT_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_FACE_DETECT_MODE_FULL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF) {
        return "ANDROID_STATISTICS_FACE_DETECT_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE) {
        return "ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL) {
        return "ANDROID_STATISTICS_FACE_DETECT_MODE_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HISTOGRAM_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF) {
        return "ANDROID_STATISTICS_HISTOGRAM_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON) {
        return "ANDROID_STATISTICS_HISTOGRAM_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON) {
        return "ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON) {
        return "ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SCENE_FLICKER_NONE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SCENE_FLICKER_50HZ";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_SCENE_FLICKER_60HZ";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE) {
        return "ANDROID_STATISTICS_SCENE_FLICKER_NONE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ) {
        return "ANDROID_STATISTICS_SCENE_FLICKER_50HZ";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ) {
        return "ANDROID_STATISTICS_SCENE_FLICKER_60HZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON) {
        return "ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE_CONTRAST_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_FAST) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_FAST)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE_FAST";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_FAST;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE_HIGH_QUALITY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE_GAMMA_VALUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_MODE_PRESET_CURVE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE) {
        return "ANDROID_TONEMAP_MODE_CONTRAST_CURVE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_FAST) {
        return "ANDROID_TONEMAP_MODE_FAST";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY) {
        return "ANDROID_TONEMAP_MODE_HIGH_QUALITY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE) {
        return "ANDROID_TONEMAP_MODE_GAMMA_VALUE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE) {
        return "ANDROID_TONEMAP_MODE_PRESET_CURVE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_PRESET_CURVE_SRGB";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709)) {
        os += (first ? "" : " | ");
        os += "ANDROID_TONEMAP_PRESET_CURVE_REC709";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB) {
        return "ANDROID_TONEMAP_PRESET_CURVE_SRGB";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709) {
        return "ANDROID_TONEMAP_PRESET_CURVE_REC709";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_TRANSMIT_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_TRANSMIT_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_OFF) {
        return "ANDROID_LED_TRANSMIT_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_ON) {
        return "ANDROID_LED_TRANSMIT_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds::ANDROID_LED_AVAILABLE_LEDS_TRANSMIT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds::ANDROID_LED_AVAILABLE_LEDS_TRANSMIT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_LED_AVAILABLE_LEDS_TRANSMIT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds::ANDROID_LED_AVAILABLE_LEDS_TRANSMIT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds::ANDROID_LED_AVAILABLE_LEDS_TRANSMIT) {
        return "ANDROID_LED_AVAILABLE_LEDS_TRANSMIT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
        os += (first ? "" : " | ");
        os += "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3) {
        return "ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_OFF) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_OFF)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_LOCK_OFF";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_OFF;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_ON) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "ANDROID_BLACK_LEVEL_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_OFF) {
        return "ANDROID_BLACK_LEVEL_LOCK_OFF";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_ON) {
        return "ANDROID_BLACK_LEVEL_LOCK_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_FRAME_NUMBER_CONVERGING";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_FRAME_NUMBER_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING) {
        return "ANDROID_SYNC_FRAME_NUMBER_CONVERGING";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN) {
        return "ANDROID_SYNC_FRAME_NUMBER_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ANDROID_SYNC_MAX_LATENCY_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL) {
        return "ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN) {
        return "ANDROID_SYNC_MAX_LATENCY_UNKNOWN";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT) {
        return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE;
    }
    if ((o & ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE) == static_cast<uint32_t>(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE)) {
        os += (first ? "" : " | ");
        os += "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE";
        first = false;
        flipped |= ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE) {
        return "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE";
    }
    if (o == ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE) {
        return "ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V3_2
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataSection, 28> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataSection> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_COLOR_CORRECTION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_CONTROL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEMOSAIC,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_EDGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_FLASH_INFO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_HOT_PIXEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_JPEG,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LENS_INFO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_NOISE_REDUCTION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_QUIRKS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REQUEST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SCALER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SENSOR_INFO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SHADING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_STATISTICS_INFO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_TONEMAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_LED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_INFO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_BLACK_LEVEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SYNC,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_REPROCESS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_DEPTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::ANDROID_SECTION_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSection::VENDOR_SECTION,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart, 27> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_CONTROL_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_EDGE_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_JPEG_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LENS_INFO_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_QUIRKS_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REQUEST_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SCALER_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SHADING_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_TONEMAP_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_LED_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_INFO_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_SYNC_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_REPROCESS_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::ANDROID_DEPTH_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataSectionStart::VENDOR_SECTION_START,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataTag, 246> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataTag> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_TRANSFORM,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_GAINS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_ABERRATION_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_COLOR_CORRECTION_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_ANTIBANDING_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_EXPOSURE_COMPENSATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_REGIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_TARGET_FPS_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_REGIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_REGIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_CAPTURE_INTENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_EFFECT_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_COMPENSATION_STEP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_EFFECTS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_SCENE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_MAX_REGIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_SCENE_MODE_OVERRIDES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_PRECAPTURE_ID,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_STATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_STATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AF_TRIGGER_ID,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_STATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_HIGH_SPEED_VIDEO_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AE_LOCK_AVAILABLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AWB_LOCK_AVAILABLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_POST_RAW_SENSITIVITY_BOOST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_ENABLE_ZSL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_CONTROL_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEMOSAIC_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_STRENGTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_AVAILABLE_EDGE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_EDGE_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_POWER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_FIRING_TIME,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_COLOR_TEMPERATURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_MAX_ENERGY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_STATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_AVAILABLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_CHARGE_DURATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_FLASH_INFO_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_HOT_PIXEL_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_COORDINATES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_PROCESSING_METHOD,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_GPS_TIMESTAMP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_ORIENTATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_QUALITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_QUALITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_THUMBNAIL_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_AVAILABLE_THUMBNAIL_SIZES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_MAX_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_JPEG_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_APERTURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FILTER_DENSITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCAL_LENGTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_DISTANCE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_OPTICAL_STABILIZATION_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FACING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_ROTATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_POSE_TRANSLATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_FOCUS_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_STATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INTRINSIC_CALIBRATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_RADIAL_DISTORTION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_APERTURES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FILTER_DENSITIES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_HYPERFOCAL_DISTANCE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_MINIMUM_FOCUS_DISTANCE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_SHADING_MAP_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LENS_INFO_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_STRENGTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_NOISE_REDUCTION_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_METERING_CROP_REGION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_TRIGGER_AF_WITH_AUTO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_ZSL_FORMAT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_USE_PARTIAL_RESULT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_PARTIAL_RESULT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_QUIRKS_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_FRAME_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_ID,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_INPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_METADATA_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_OUTPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_TYPE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_OUTPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_REPROCESS_STREAMS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_MAX_NUM_INPUT_STREAMS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_DEPTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PIPELINE_MAX_DEPTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_PARTIAL_RESULT_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CAPABILITIES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_REQUEST_KEYS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_RESULT_KEYS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_AVAILABLE_CHARACTERISTICS_KEYS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REQUEST_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROP_REGION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_FORMATS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_JPEG_SIZES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_PROCESSED_SIZES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_MIN_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_RAW_SIZES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_INPUT_OUTPUT_FORMATS_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_AVAILABLE_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_CROPPING_TYPE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SCALER_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_EXPOSURE_TIME,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FRAME_DURATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_SENSITIVITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT1,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_REFERENCE_ILLUMINANT2,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM1,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_CALIBRATION_TRANSFORM2,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM1,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_COLOR_TRANSFORM2,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX1,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_FORWARD_MATRIX2,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BASE_GAIN_FACTOR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_BLACK_LEVEL_PATTERN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_MAX_ANALOG_SENSITIVITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ORIENTATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP_DIMENSIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TIMESTAMP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEMPERATURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NEUTRAL_COLOR_POINT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_NOISE_PROFILE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_HUE_SAT_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_PROFILE_TONE_CURVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_GREEN_SPLIT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_DATA,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_TEST_PATTERN_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_AVAILABLE_TEST_PATTERN_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_ROLLING_SHUTTER_SKEW,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPTICAL_BLACK_REGIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_BLACK_LEVEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_DYNAMIC_WHITE_LEVEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_OPAQUE_RAW_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_ACTIVE_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_SENSITIVITY_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_EXPOSURE_TIME_RANGE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_MAX_FRAME_DURATION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PHYSICAL_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_WHITE_LEVEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SENSOR_INFO_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_STRENGTH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_AVAILABLE_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SHADING_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_DETECT_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_IDS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_LANDMARKS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_RECTANGLES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_FACE_SCORES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HISTOGRAM,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SHARPNESS_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_CORRECTION_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_GAINS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_PREDICTED_COLOR_TRANSFORM,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_SCENE_FLICKER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_HOT_PIXEL_MAP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_HISTOGRAM_BUCKET_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_FACE_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_HISTOGRAM_COUNT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_MAX_SHARPNESS_MAP_VALUE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_SHARPNESS_MAP_SIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_STATISTICS_INFO_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_BLUE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_GREEN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_CURVE_RED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_MAX_CURVE_POINTS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_AVAILABLE_TONE_MAP_MODES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_GAMMA,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_PRESET_CURVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_TONEMAP_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_TRANSMIT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_AVAILABLE_LEDS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_LED_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_INFO_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_LOCK,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_BLACK_LEVEL_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_FRAME_NUMBER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_MAX_LATENCY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_SYNC_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_EFFECTIVE_EXPOSURE_FACTOR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_MAX_CAPTURE_STALL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_REPROCESS_END,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_MAX_DEPTH_SAMPLES,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataTag::ANDROID_DEPTH_END,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionMode::ANDROID_COLOR_CORRECTION_MODE_HIGH_QUALITY,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidColorCorrectionAberrationMode::ANDROID_COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_50HZ,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_60HZ,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeAntibandingMode::ANDROID_CONTROL_AE_ANTIBANDING_MODE_AUTO,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLock::ANDROID_CONTROL_AE_LOCK_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_ALWAYS_FLASH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeMode::ANDROID_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_IDLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAePrecaptureTrigger::ANDROID_CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode, 6> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_AUTO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_MACRO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_VIDEO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfMode::ANDROID_CONTROL_AF_MODE_EDOF,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_IDLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfTrigger::ANDROID_CONTROL_AF_TRIGGER_CANCEL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLock::ANDROID_CONTROL_AWB_LOCK_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode, 9> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_AUTO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_INCANDESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_WARM_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_DAYLIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_TWILIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbMode::ANDROID_CONTROL_AWB_MODE_SHADE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent, 7> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_CUSTOM,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_RECORD,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlCaptureIntent::ANDROID_CONTROL_CAPTURE_INTENT_MANUAL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode, 9> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_MONO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_NEGATIVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SOLARIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_SEPIA,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_POSTERIZE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_WHITEBOARD,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_BLACKBOARD,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEffectMode::ANDROID_CONTROL_EFFECT_MODE_AQUA,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_AUTO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_USE_SCENE_MODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlMode::ANDROID_CONTROL_MODE_OFF_KEEP_STATE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode, 22> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DISABLED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_ACTION,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PORTRAIT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_LANDSCAPE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_NIGHT_PORTRAIT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_THEATRE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BEACH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SNOW,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SUNSET,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_STEADYPHOTO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FIREWORKS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_SPORTS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_PARTY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_CANDLELIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_BARCODE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_HDR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_FACE_PRIORITY_LOW_LIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_START,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlSceneMode::ANDROID_CONTROL_SCENE_MODE_DEVICE_CUSTOM_END,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlVideoStabilizationMode::ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState, 6> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_INACTIVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_SEARCHING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_CONVERGED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_LOCKED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_FLASH_REQUIRED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeState::ANDROID_CONTROL_AE_STATE_PRECAPTURE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState, 7> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_INACTIVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_SCAN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_FOCUSED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_ACTIVE_SCAN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_FOCUSED_LOCKED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAfState::ANDROID_CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_INACTIVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_SEARCHING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_CONVERGED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbState::ANDROID_CONTROL_AWB_STATE_LOCKED,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAeLockAvailable::ANDROID_CONTROL_AE_LOCK_AVAILABLE_TRUE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlAwbLockAvailable::ANDROID_CONTROL_AWB_LOCK_AVAILABLE_TRUE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidControlEnableZsl::ANDROID_CONTROL_ENABLE_ZSL_TRUE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_HIGH_QUALITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidEdgeMode::ANDROID_EDGE_MODE_ZERO_SHUTTER_LAG,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_SINGLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashMode::ANDROID_FLASH_MODE_TORCH,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_UNAVAILABLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_CHARGING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_READY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_FIRED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashState::ANDROID_FLASH_STATE_PARTIAL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidFlashInfoAvailable::ANDROID_FLASH_INFO_AVAILABLE_TRUE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidHotPixelMode::ANDROID_HOT_PIXEL_MODE_HIGH_QUALITY,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensOpticalStabilizationMode::ANDROID_LENS_OPTICAL_STABILIZATION_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_FRONT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_BACK,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensFacing::ANDROID_LENS_FACING_EXTERNAL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_STATIONARY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensState::ANDROID_LENS_STATE_MOVING,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLensInfoFocusDistanceCalibration::ANDROID_LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_MINIMAL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidNoiseReductionMode::ANDROID_NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_FINAL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidQuirksPartialResult::ANDROID_QUIRKS_PARTIAL_RESULT_PARTIAL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_CAPTURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestType::ANDROID_REQUEST_TYPE_REPROCESS,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities, 10> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidRequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats, 7> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerAvailableStreamConfigurations::ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1, 19> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FLASH,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_SHADE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D55,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D65,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D75,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_D50,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorReferenceIlluminant1::ANDROID_SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode, 6> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_PN9,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorTestPatternMode::ANDROID_SENSOR_TEST_PATTERN_MODE_CUSTOM1,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoColorFilterArrangement::ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSensorInfoLensShadingApplied::ANDROID_SENSOR_INFO_LENS_SHADING_APPLIED_TRUE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidShadingMode::ANDROID_SHADING_MODE_HIGH_QUALITY,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSharpnessMapMode::ANDROID_STATISTICS_SHARPNESS_MAP_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsHotPixelMapMode::ANDROID_STATISTICS_HOT_PIXEL_MAP_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker, 3> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidStatisticsLensShadingMapMode::ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode, 5> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_CONTRAST_CURVE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_FAST,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_HIGH_QUALITY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_GAMMA_VALUE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapMode::ANDROID_TONEMAP_MODE_PRESET_CURVE,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_SRGB,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidTonemapPresetCurve::ANDROID_TONEMAP_PRESET_CURVE_REC709,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedTransmit::ANDROID_LED_TRANSMIT_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds, 1> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidLedAvailableLeds::ANDROID_LED_AVAILABLE_LEDS_TRANSMIT,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel, 4> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidInfoSupportedHardwareLevel::ANDROID_INFO_SUPPORTED_HARDWARE_LEVEL_3,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_OFF,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidBlackLevelLock::ANDROID_BLACK_LEVEL_LOCK_ON,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_CONVERGING,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncFrameNumber::ANDROID_SYNC_FRAME_NUMBER_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_PER_FRAME_CONTROL,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidSyncMaxLatency::ANDROID_SYNC_MAX_LATENCY_UNKNOWN,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_OUTPUT,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthAvailableDepthStreamConfigurations::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_INPUT,
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
template<> inline constexpr std::array<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive, 2> hidl_enum_values<::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive> = {
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_FALSE,
    ::android::hardware::camera::metadata::V3_2::CameraMetadataEnumAndroidDepthDepthIsExclusive::ANDROID_DEPTH_DEPTH_IS_EXCLUSIVE_TRUE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_METADATA_V3_2_TYPES_H
