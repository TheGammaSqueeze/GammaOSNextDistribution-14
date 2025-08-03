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

#include <ImsMediaBitReader.h>
#include <ImsMediaTrace.h>
#include <string.h>

ImsMediaBitReader::ImsMediaBitReader()
{
    mBuffer = nullptr;
    mMaxBufferSize = 0;
    mBytePos = 0;
    mBitPos = 0;
    mBitBuffer = 0;
    mBufferEOF = false;
}

ImsMediaBitReader::~ImsMediaBitReader() {}

void ImsMediaBitReader::SetBuffer(uint8_t* pbBuffer, uint32_t nBufferSize)
{
    mBytePos = 0;
    mBitPos = 32;
    mBitBuffer = 0;
    mBufferEOF = false;
    mBuffer = pbBuffer;
    mMaxBufferSize = nBufferSize;
}

uint32_t ImsMediaBitReader::Read(uint32_t nSize)
{
    uint32_t value;
    if (nSize == 0)
        return 0;
    if (mBuffer == nullptr || nSize > 24 || mBufferEOF)
    {
        IMLOGE2("[Read] nSize[%d], bBufferEOF[%d]", nSize, mBufferEOF);
        return 0;
    }

    // read from byte buffer
    while ((32 - mBitPos) < nSize)
    {
        if (mBytePos >= mMaxBufferSize)
        {
            mBufferEOF = true;
            IMLOGE2("[Read] End of Buffer : nBytePos[%d], nMaxBufferSize[%d]", mBytePos,
                    mMaxBufferSize);
            return 0;
        }

        mBitPos -= 8;
        mBitBuffer <<= 8;
        mBitBuffer += mBuffer[mBytePos++];
    }

    // read from bit buffer
    value = mBitBuffer << mBitPos >> (32 - nSize);
    mBitPos += nSize;
    return value;
}

void ImsMediaBitReader::ReadByteBuffer(uint8_t* pbDst, uint32_t nBitSize)
{
    uint32_t dst_pos = 0;
    uint32_t nByteSize;
    uint32_t nRemainBitSize;
    nByteSize = nBitSize >> 3;
    nRemainBitSize = nBitSize & 0x07;

    if (mBitPos == 32)
    {
        memcpy(pbDst, mBuffer + mBytePos, nByteSize);
        mBytePos += nByteSize;
        dst_pos += nByteSize;
    }
    else
    {
        for (dst_pos = 0; dst_pos < nByteSize; dst_pos++)
        {
            pbDst[dst_pos] = Read(8);
        }
    }

    if (nRemainBitSize > 0)
    {
        uint32_t v;
        v = Read(nRemainBitSize);
        v <<= (8 - nRemainBitSize);
        pbDst[dst_pos] = (unsigned char)v;
    }
}

uint32_t ImsMediaBitReader::ReadByUEMode()
{
    uint32_t i = 0;
    uint32_t j = 0;
    uint32_t k = 1;
    uint32_t result = 0;

    while (Read(1) == 0 && mBufferEOF == false)
    {
        i++;
    }

    j = Read(i);
    result = j - 1 + (k << i);
    return result;
}