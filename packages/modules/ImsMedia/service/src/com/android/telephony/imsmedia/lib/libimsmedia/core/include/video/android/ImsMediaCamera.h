/**
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef IMS_MEDIA_CAMERA_H_INCLUDED
#define IMS_MEDIA_CAMERA_H_INCLUDED

#include <ImsMediaDefine.h>
#include <ImsMediaCondition.h>
#include <string>
#include <vector>
#include <map>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraMetadataTags.h>

enum class CaptureSessionState : int32_t
{
    kStateReady = 0,  // session is ready
    kStateActive,     // session is busy
    kStateClosed,     // session is closed(by itself or a new session evicts)
    kStateMax
};

class CameraId
{
public:
    ACameraDevice* mDevice;
    std::string mId;
    int32_t mFacing;
    bool mAvailable;
    bool mOwner;
    explicit CameraId(const char* id = nullptr) :
            mDevice(nullptr),
            mFacing(ACAMERA_LENS_FACING_FRONT),
            mAvailable(false),
            mOwner(false)
    {
        mId = (id != nullptr) ? id : "";
    }

    CameraId& operator=(const CameraId& camera)
    {
        if (this != &camera)
        {
            mDevice = camera.mDevice;
            mId = camera.mId;
            mFacing = camera.mFacing;
            mAvailable = camera.mAvailable;
            mOwner = camera.mOwner;
        }

        return *this;
    }
};

template <typename T>
class RangeValue
{
public:
    T min, max;
    /**
     * return absolute value from relative value
     * value: in percent (50 for 50%)
     */
    T value(int percent) { return static_cast<T>(min + (max - min) * percent / 100); }
    RangeValue() { min = max = static_cast<T>(0); }
    RangeValue& operator=(const RangeValue& rangeValue)
    {
        if (this != &rangeValue)
        {
            min = rangeValue.min;
            max = rangeValue.max;
        }

        return *this;
    }

    bool Supported(void) const { return (min != max); }
};

struct CaptureRequestInfo
{
public:
    CaptureRequestInfo()
    {
        outputNativeWindows.clear();
        sessionOutputs.clear();
        targets.clear();
        request = nullptr;
    }
    std::vector<ANativeWindow*> outputNativeWindows;
    std::vector<ACaptureSessionOutput*> sessionOutputs;
    std::vector<ACameraOutputTarget*> targets;
    ACaptureRequest* request;
    ACameraDevice_request_template requestTemplate;
};

enum kCameraMode
{
    kCameraModePreview = 0,
    kCameraModeRecord,
    kCameraModeCount,
};

class ImsMediaCamera
{
private:
    ImsMediaCamera();
    virtual ~ImsMediaCamera();

public:
    static ImsMediaCamera* getInstance();

    /**
     * @brief Creates camera manager and register the valid camera device to the list
     */
    void Initialize();

    /**
     * @brief Deletes camera manager and clear the camera list
     */
    void DeInitialize();

    /**
     * @brief Opens camera with pre configured camera configuration by SetCameraConfig.
     *
     * @return true Returns when open camera successfully
     * @return false Returns when there is error during opening camera
     */
    bool OpenCamera();

    /**
     * @brief Sets the Camera Config
     *
     * @param cameraId active camera id
     * @param cameraZoom camera zoom level
     * @param framerate framerate
     */
    void SetCameraConfig(int32_t cameraId, int32_t cameraZoom, int32_t framerate);

    /**
     * @brief Creates a Session object
     *
     * @param preview The mandatory prameter to run the camera
     * @param recording The optional parameter to run camera, it is mandatory if you want to run
     * camera as recording mode
     *
     * @return true Returns when create camera session successfully
     * @return false Returns when there is error during create camera session
     */
    bool CreateSession(ANativeWindow* preview, ANativeWindow* recording);

    /**
     * @brief Delete the camera capture session, request and release the target surfaces and
     * resources.
     *
     * @return true Deletes camera session and release the resources succeed
     * @return false Failed when there is error during the release the resources
     */
    bool DeleteSession();

    /**
     * @brief Starts camera preview or recording session
     *
     * @param bRecording Sets true to run recording session, it would be failed when the surface or
     * camera id is not valid.
     * @return true Starts camera session without error
     * @return false Failed when there is any error during starting camera session
     */
    bool StartSession(bool bRecording);

    /**
     * @brief Stops the running camera capture session.
     *
     * @return true Stop camera session succeed
     * @return false Failed when there is any error during stopping camera session
     */
    bool StopSession();

    /**
     * @brief Handles callback from ACameraManager
     *
     * @param id The camera id to update
     * @param available The state of camera, set true when the camera is available
     */
    virtual void OnCameraStatusChanged(const char* id, bool available);

    /**
     * @brief Handle Camera DeviceStateChanges msg, notify device is disconnected
     * simply close the camera
     *
     * @param dev device instance to get state
     */
    virtual void OnDeviceState(ACameraDevice* dev);

    /**
     * @brief Handles Camera's deviceErrorChanges message, no action; mainly debugging purpose
     *
     * @param dev The camera device object has error
     * @param err The error code
     */
    virtual void OnDeviceError(ACameraDevice* dev, int err);

    /**
     * @brief  Handles capture session state changes. Update into internal session state.
     *
     * @param ses The camera capture session object to change the state
     * @param state The camera state to changes
     */
    virtual void OnSessionState(ACameraCaptureSession* ses, CaptureSessionState state);

    /**
     * @brief Retrieve the camera facing and sensor orientation of requested camera id;
     *
     * @param cameraId the camera id to get the facing and sensor orientation
     * @param facing retrieved camera facing, rear or front.
     * @param angle retrieved camera sensor orientation in degree units.
     * @return true true when it is succeeded to retrieve values.
     * @return false fail in accessing camera instances.
     */
    bool GetSensorOrientation(const int cameraId, int32_t* facing, int32_t* angle);

private:
    /**
     * @brief Loop through cameras on the system, store camera informations
     */
    void EnumerateCamera();

    /**
     * Retrieve Camera Exposure adjustable range.
     *
     * @param min Camera minimium exposure time in nanoseconds
     * @param max Camera maximum exposure time in nanoseconds
     *
     * @return true  min and max are loaded with the camera's exposure values
     *         false camera has not initialized, no value available
     */
    bool GetExposureRange(int64_t* min, int64_t* max, int64_t* curVal);
    /**

     * Retrieve Camera sensitivity range.
     *
     * @param min Camera minimium sensitivity
     * @param max Camera maximum sensitivity
     *
     * @return true  min and max are loaded with the camera's sensitivity values
     *         false camera has not initialized, no value available
     */
    bool GetSensitivityRange(int64_t* min, int64_t* max, int64_t* curVal);

    /**
     * @brief Construct a camera manager listener on the fly and return to caller
     *
     * @return ACameraManager_AvailabilityCallback
     */
    ACameraManager_AvailabilityCallbacks* GetManagerListener();
    ACameraDevice_stateCallbacks* GetDeviceListener();
    ACameraCaptureSession_stateCallbacks* GetSessionListener();
    bool MatchCaptureSizeRequest(ANativeWindow* window);

    static ImsMediaCamera gCamera;
    static std::map<std::string, CameraId> gCameraIds;
    static ImsMediaCondition gCondition;
    ACameraManager* mManager;
    CaptureRequestInfo mCaptureRequest;
    ACaptureSessionOutputContainer* mSessionOutputContainer;
    ACameraCaptureSession* mCaptureSession;
    CaptureSessionState mCaptureSessionState;

    // set up exposure control
    int64_t mExposureTime;
    RangeValue<int64_t> mExposureRange;
    int32_t mSensitivity;
    RangeValue<int32_t> mSensitivityRange;
    uint32_t mCameraMode;
    uint32_t mCameraFacing;
    uint32_t mCameraOrientation;
    std::string mActiveCameraId;
    int32_t mCameraZoom;
    int32_t mFramerate;
};

#endif