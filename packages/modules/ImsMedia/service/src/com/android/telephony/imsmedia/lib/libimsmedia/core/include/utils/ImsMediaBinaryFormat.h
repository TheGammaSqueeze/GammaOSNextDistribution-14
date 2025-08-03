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

#ifndef BINARYFORMAT_H_INCLUDED
#define BINARYFORMAT_H_INCLUDED

#include <stdint.h>

enum eBinaryFormat
{
    BINARY_FORMAT_BASE16 = 0,  // mpeg4
    BINARY_FORMAT_BASE32,      // not used
    BINARY_FORMAT_BASE64,      // avc/hevc
    BINARY_FORMAT_MAX
};  // RFC 3548

class ImsMediaBinaryFormat
{
public:
    static bool BinaryToBase00(char* pszDst, uint32_t nDstBuffSize, uint8_t* pbSrc,
            uint32_t nSrcSize, uint32_t eFormat);
    static bool Base00ToBinary(uint8_t* pbDst, uint32_t* pnDstSize, uint32_t nDstBuffSize,
            char* pszSrc, uint32_t eFormat);
};

#endif  // BINARYFORMAT_H_INCLUDED
