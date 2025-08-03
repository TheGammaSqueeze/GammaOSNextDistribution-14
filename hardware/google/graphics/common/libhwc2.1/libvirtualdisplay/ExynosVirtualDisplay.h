/*
 * Copyright (C) 2012 The Android Open Source Project
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
#ifndef EXYNOS_VIRTUAL_DISPLAY_DISPLAY_H
#define EXYNOS_VIRTUAL_DISPLAY_DISPLAY_H

#include "ExynosHWCDebug.h"
#include "../libdevice/ExynosDisplay.h"

#define VIRTUAL_DISLAY_SKIP_LAYER   0x00000100

enum WFDState {
    DISABLE_WFD,
    GOOGLEWFD,
    LLWFD,
    GOOGLEWFD_TO_LLWFD,
    LLWFD_TO_GOOGLEWFD,
};

enum WFDCommand {
    SET_WFD_MODE,
    SET_TARGET_DISPLAY_LUMINANCE,
    SET_TARGET_DISPLAY_DEVICE,
};

class ExynosVirtualDisplay : public ExynosDisplay {
public:
    enum CompositionType {
        COMPOSITION_UNKNOWN = 0,
        COMPOSITION_GLES    = 1,
        COMPOSITION_HWC     = 2,
        COMPOSITION_MIXED   = COMPOSITION_GLES | COMPOSITION_HWC
    };

    ExynosVirtualDisplay(uint32_t index, ExynosDevice* device, const std::string& displayName);
    ~ExynosVirtualDisplay();

    void createVirtualDisplay(uint32_t width, uint32_t height, int32_t* format);
    void destroyVirtualDisplay();

    int setWFDMode(unsigned int mode);
    int getWFDMode();
    int sendWFDCommand(int32_t cmd, int32_t ext1, int32_t ext2);
    int setSecureVDSMode(unsigned int mode);
    int setWFDOutputResolution(unsigned int width, unsigned int height);
    void getWFDOutputResolution(unsigned int *width, unsigned int *height);
    void setPresentationMode(bool use);
    int getPresentationMode(void);
    int setVDSGlesFormat(int format);

    /* setOutputBuffer(..., buffer, releaseFence)
     * Descriptor: HWC2_FUNCTION_SET_OUTPUT_BUFFER
     * HWC2_PFN_SET_OUTPUT_BUFFER
     * releaseFence is actually acquireFence
     * output buffer reader should wait releaseFence
     */
    virtual int32_t setOutputBuffer(
            buffer_handle_t buffer,
            int32_t releaseFence);

    virtual int clearDisplay(bool needModeClear = false);

    /* validateDisplay(..., outNumTypes, outNumRequests)
     * Descriptor: HWC2_FUNCTION_VALIDATE_DISPLAY
     * HWC2_PFN_VALIDATE_DISPLAY
     */
    virtual int32_t validateDisplay(uint32_t* outNumTypes, uint32_t* outNumRequests);

    virtual int32_t canSkipValidate();

    /* presentDisplay(..., outRetireFence)
     * Descriptor: HWC2_FUNCTION_PRESENT_DISPLAY
     * HWC2_PFN_PRESENT_DISPLAY
     */
    virtual int32_t presentDisplay(int32_t* outRetireFence);

    /**
     * set config related DPU window
     * Only DRM video playback scenario use it.
     */
    virtual int setWinConfigData();

    /**
     * set config related with DPU WB
     * Only DRM video playback scenario use it.
     */
    virtual int setDisplayWinConfigData();

    /**
     * check validation of DPU config
     * Only DRM video playback scenario use it.
     */
    virtual int32_t validateWinConfigData();

    /**
     * call ioctl for DPU
     * Only DRM video playback scenario use it.
     */
    virtual int deliverWinConfigData();

    /**
     * set release fence of DPU to layers
     * Only DRM video playback scenario use it.
     */
    virtual int setReleaseFences();

    /**
     * check output buffer validation
     * If it is invalid, don't presetnDisplay
     * And function handle fence of DEVICE composition Layer.
     */
    virtual bool checkFrameValidation();

    virtual int32_t getHdrCapabilities(uint32_t* outNumTypes,
            int32_t* outTypes, float* outMaxLuminance,
            float* outMaxAverageLuminance, float* outMinLuminance);

    /**
     * If mIsWFDState is true, VirtualDisplaySurface use HWC
     */
    int         mIsWFDState;

protected:
    void setSinkBufferUsage();

    void setCompositionType();

    void setDrmMode();

    void initPerFrameData();

    bool checkSkipFrame();

    void handleSkipFrame();

    void handleAcquireFence();

    /**
     * Display width, height information set by surfaceflinger
     */
    unsigned int mDisplayWidth;
    unsigned int mDisplayHeight;

    /**
     * output buffer and fence are set by setOutputBuffer()
     */
    buffer_handle_t mOutputBuffer;
    int32_t mOutputBufferAcquireFenceFd;
    int32_t mOutputBufferReleaseFenceFd;

    /**
     * If mIsSecureVDSState is true, HWC doesn't change the color format of SINK BQ
     */
    bool        mIsSecureVDSState;

    /**
     * If mIsSkipFrame is true, TV should not be updated.
     */
    bool        mIsSkipFrame;

    bool        mPresentationMode;

    /**
     * If mIsRotationState is true,
     * VurtualDisplaySurface don't queue graphic buffer
     */
    int32_t     mCompositionType;

    /**
     * G3D pixel format
     */
    int32_t     mGLESFormat;

    /**
     * output buffer usage
     */
    int32_t     mSinkUsage;

    /**
     * If mIsSecureDRM is true, DPU composition is used.
     * Otherwise, G2D composition is used.
     */
    bool mIsSecureDRM;
    bool mIsNormalDRM;

    /**
     * reload the G2D instance to remove the shared buffer for HWFC
     */
    bool mNeedReloadResourceForHWFC;

    /**
     * the minimum luminance and maximum luminance of the SINK device's display
     * WFD engine will set this values.
     */
    uint16_t mMaxTargetLuminance;
    uint16_t mMinTargetLuminance;

    /**
     * the type of the SINK device
     * WFD engine will set this values.
     */
    int32_t mSinkDeviceType;
};

#endif
