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

#ifndef IMS_MEDIA_BITREADER_H
#define IMS_MEDIA_BITREADER_H

#include <stdint.h>

class ImsMediaBitReader
{
public:
    ImsMediaBitReader();
    ~ImsMediaBitReader();
    void SetBuffer(uint8_t* pbBuffer, uint32_t nBufferSize);
    uint32_t Read(uint32_t nSize);
    void ReadByteBuffer(uint8_t* pbDst, uint32_t nBitSize);
    uint32_t ReadByUEMode();

private:
    uint8_t* mBuffer;
    uint32_t mMaxBufferSize;
    uint32_t mBytePos;
    uint32_t mBitPos;  // start bit position of valid data in mBitBuffer
    uint32_t mBitBuffer;
    bool mBufferEOF;
};

#endif