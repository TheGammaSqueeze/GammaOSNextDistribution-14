/*
 * Copyright 2022 The Android Open Source Project
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

#ifndef GOLDFISH_HEVC_HELPER_H_
#define GOLDFISH_HEVC_HELPER_H_

#include <inttypes.h>
#include "ihevc_typedefs.h"
#include "ihevcd_cxa.h"


namespace android {

// this class is just to provide some functions to decode header
// so that we know w/h of each sps
class GoldfishHevcHelper {
  public:
    GoldfishHevcHelper(int w, int h);
    ~GoldfishHevcHelper();

    // check whether the frame is vps; typical hevc will have
    // a frame that is vps/sps/pps together
    static bool isVpsFrame(const uint8_t* frame, int inSize);
  public:
    // return true if decoding finds out w/h changed;
    // otherwise false
   bool decodeHeader(const uint8_t *frame, int inSize, bool &status);
   int getWidth() const { return mWidth; }
   int getHeight() const { return mHeight; }

  private:
    void createDecoder();
    void destroyDecoder();
    void resetDecoder();
    void setNumCores();
    void setParams(size_t stride, IVD_VIDEO_DECODE_MODE_T dec_mode);
    bool setDecodeArgs(ivd_video_decode_ip_t *ps_decode_ip,
                       ivd_video_decode_op_t *ps_decode_op,
                       const uint8_t *inBuffer, uint32_t displayStride,
                       size_t inOffset, size_t inSize, uint32_t tsMarker);

  private:
    iv_obj_t *mDecHandle = nullptr;
    int mWidth = 320;
    int mHeight = 240;
    int mNumCores = 1;
    int mStride = 16;
    int mOutputDelay = 8; // default
    IV_COLOR_FORMAT_T mIvColorformat = IV_YUV_420P;
};

} // namespace android
#endif
