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
#include <string.h>
#include <ImsMediaBinaryFormat.h>
#include <ImsMediaTrace.h>

// Default value for non-printable characters
#define NP         0xFF
// Carriage-Return (\r)
#define CR         0x0D
// Line-Feed (\n)
#define LF         0x0A
// Padding character for Base64
#define BASE64_PAD '='

// Constant table for Base64 value encoding / decoding
static const char BASE64_ENCODING_TABLE[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static const uint8_t BASE64_DECODING_TABLE[] = {
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 0 ~ 9
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 10 ~ 19
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 20 ~ 29
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 30 ~ 39
        NP, NP, NP, 62, NP, NP, NP, 63, 52, 53,  // 40 ~ 49
        54, 55, 56, 57, 58, 59, 60, 61, NP, NP,  // 50 ~ 59
        NP, NP, NP, NP, NP, 0, 1, 2, 3, 4,       // 60 ~ 69
        5, 6, 7, 8, 9, 10, 11, 12, 13, 14,       // 70 ~ 79
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 80 ~ 89
        25, NP, NP, NP, NP, NP, NP, 26, 27, 28,  // 90 ~ 99
        29, 30, 31, 32, 33, 34, 35, 36, 37, 38,  // 100 ~ 109
        39, 40, 41, 42, 43, 44, 45, 46, 47, 48,  // 110 ~ 119
        49, 50, 51, NP, NP, NP, NP, NP, NP, NP,  // 120 ~ 129
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 130 ~ 139
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 140 ~ 149
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 150 ~ 159
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 160 ~ 169
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 170 ~ 179
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 180 ~ 189
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 190 ~ 199
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 200 ~ 209
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 210 ~ 219
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 220 ~ 229
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 230 ~ 239
        NP, NP, NP, NP, NP, NP, NP, NP, NP, NP,  // 240 ~ 249
        NP, NP, NP, NP, NP, NP                   // 250 ~ 256
};

static bool BinaryToBase16(char* pszDst, uint32_t nDstBuffSize, uint8_t* pbSrc, uint32_t nSrcSize)
{
    if (((nDstBuffSize - 1) >> 1) < nSrcSize)
        return false;

    if (nSrcSize > 0)
    {
        uint32_t m, n;

        for (m = 0, n = 0; m < nSrcSize; m++)
        {
            int8_t c, h, l;

            c = (int8_t)pbSrc[m];

            h = (c >> 4) & 0x0F;
            l = c & 0x0F;

            if (h < 10)
                h += '0';
            else
                h += 'A' - 10;
            if (l < 10)
                l += '0';
            else
                l += 'A' - 10;

            pszDst[n++] = h;
            pszDst[n++] = l;
        }
        pszDst[n] = 0;
    }

    return true;
}

static bool Base16ToBinary(uint8_t* pbDst, uint32_t* pnDstSize, uint32_t nDstBuffSize, char* pszSrc)
{
    uint32_t nSrcLen;
    uint32_t src_pos, dst_pos;

    nSrcLen = strlen(pszSrc);
    if (nSrcLen & 0x1)
        nSrcLen++;
    if (nDstBuffSize < (nSrcLen >> 1))
        return false;
    for (src_pos = 0, dst_pos = 0; src_pos < nSrcLen; src_pos += 2, dst_pos++)
    {
        char h, l;

        h = pszSrc[src_pos];
        l = pszSrc[src_pos + 1];

        if (h >= '0' && h <= '9')
            h = h - '0';
        else if (h >= 'a' && h <= 'f')
            h = h - 'a' + 10;
        else if (h >= 'A' && h <= 'F')
            h = h - 'A' + 10;

        if (l >= '0' && l <= '9')
            l = l - '0';
        else if (l >= 'a' && l <= 'f')
            l = l - 'a' + 10;
        else if (l >= 'A' && l <= 'F')
            l = l - 'A' + 10;

        pbDst[dst_pos] = (h << 4) | l;
    }

    *pnDstSize = dst_pos;

    return true;
}

static bool BinaryToBase64(
        char* pszDst, uint32_t nDstBuffSize, const uint8_t* pbSrc, uint32_t nSrcSize)
{
    char* pEncBuffer = pszDst;

    if ((nDstBuffSize - 1) < ((nSrcSize + 2) / 3 * 4))
        return false;

    for (int32_t nPos = 0; nPos < nSrcSize; ++nPos)
    {
        uint8_t c6bit = (pbSrc[nPos] >> 2) & 0x3F;
        (*pEncBuffer) = BASE64_ENCODING_TABLE[(uint8_t)c6bit];
        pEncBuffer++;

        c6bit = (pbSrc[nPos] << 4) & 0x3F;

        if (++nPos < nSrcSize)
            c6bit |= (pbSrc[nPos] >> 4) & 0x0F;

        (*pEncBuffer) = BASE64_ENCODING_TABLE[(uint8_t)c6bit];
        pEncBuffer++;

        if (nPos < nSrcSize)
        {
            c6bit = (pbSrc[nPos] << 2) & 0x3F;

            if (++nPos < nSrcSize)
                c6bit |= (pbSrc[nPos] >> 6) & 0x03;

            (*pEncBuffer) = BASE64_ENCODING_TABLE[(uint8_t)c6bit];
            pEncBuffer++;
        }
        else
        {
            ++nPos;
            (*pEncBuffer) = BASE64_PAD;
            pEncBuffer++;
        }

        if (nPos < nSrcSize)
        {
            c6bit = pbSrc[nPos] & 0x3F;
            (*pEncBuffer) = BASE64_ENCODING_TABLE[(uint8_t)c6bit];
            pEncBuffer++;
        }
        else
        {
            (*pEncBuffer) = BASE64_PAD;
            pEncBuffer++;
        }
    }

    (*pEncBuffer) = 0x00;

    return true;
}

static bool Base64ToBinary(uint8_t* pbDst, uint32_t* pnDstSize, uint32_t nDstBuffSize, char* pszSrc)
{
    uint8_t* pDecBuffer = pbDst;
    uint32_t nSrcLen;

    nSrcLen = strlen(pszSrc);

    if (nDstBuffSize < ((nSrcLen >> 2) * 3 + (nSrcLen & 0x3)))
        return false;

    for (int32_t nPos = 0; nPos < nSrcLen; ++nPos)
    {
        if (pszSrc[nPos] == LF)
            nPos += 1;
        if (pszSrc[nPos] == CR)
            nPos += 2;

        uint8_t c8bit = BASE64_DECODING_TABLE[(uint8_t)pszSrc[nPos]];
        ++nPos;

        if (pszSrc[nPos] == LF)
            nPos += 1;
        if (pszSrc[nPos] == CR)
            nPos += 2;

        uint8_t c8bit1 = BASE64_DECODING_TABLE[(uint8_t)pszSrc[nPos]];
        c8bit = (c8bit << 2) | ((c8bit1 >> 4) & 0x03);
        (*pDecBuffer) = c8bit;
        pDecBuffer++;

        if (++nPos < nSrcLen)
        {
            if (pszSrc[nPos] == LF)
                nPos += 1;
            if (pszSrc[nPos] == CR)
                nPos += 2;

            c8bit = pszSrc[nPos];

            if (c8bit == BASE64_PAD)
                break;

            c8bit = BASE64_DECODING_TABLE[(uint8_t)pszSrc[nPos]];
            c8bit1 = ((c8bit1 << 4) & 0xF0) | ((c8bit >> 2) & 0x0F);
            (*pDecBuffer) = c8bit1;
            pDecBuffer++;
        }

        if (++nPos < nSrcLen)
        {
            if (pszSrc[nPos] == LF)
                nPos += 1;
            if (pszSrc[nPos] == CR)
                nPos += 2;

            c8bit1 = pszSrc[nPos];

            if (c8bit1 == BASE64_PAD)
                break;

            c8bit1 = BASE64_DECODING_TABLE[(uint8_t)pszSrc[nPos]];
            c8bit = ((c8bit << 6) & 0xC0) | c8bit1;
            (*pDecBuffer) = c8bit;
            pDecBuffer++;
        }
    }

    *pnDstSize = (uint32_t)(pDecBuffer - pbDst);

    return true;
}

bool ImsMediaBinaryFormat::BinaryToBase00(
        char* pszDst, uint32_t nDstBuffSize, uint8_t* pbSrc, uint32_t nSrcSize, uint32_t eFormat)
{
    switch (eFormat)
    {
        case BINARY_FORMAT_BASE16:
            return BinaryToBase16(pszDst, nDstBuffSize, pbSrc, nSrcSize);
        case BINARY_FORMAT_BASE64:
            return BinaryToBase64(pszDst, nDstBuffSize, pbSrc, nSrcSize);
        case BINARY_FORMAT_BASE32:
        default:
            IMLOGE1("[BinaryToBase00] not supported binary format %d", eFormat);
            return false;
    }
}

bool ImsMediaBinaryFormat::Base00ToBinary(
        uint8_t* pbDst, uint32_t* pnDstSize, uint32_t nDstBuffSize, char* pszSrc, uint32_t eFormat)
{
    switch (eFormat)
    {
        case BINARY_FORMAT_BASE16:
            return Base16ToBinary(pbDst, pnDstSize, nDstBuffSize, pszSrc);
        case BINARY_FORMAT_BASE64:
            return Base64ToBinary(pbDst, pnDstSize, nDstBuffSize, pszSrc);
        case BINARY_FORMAT_BASE32:
        default:
            IMLOGE1("[Base00ToBinary] not supported binary format %d", eFormat);
            return false;
    }
}
