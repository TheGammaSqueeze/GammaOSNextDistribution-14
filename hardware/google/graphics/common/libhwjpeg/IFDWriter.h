/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef __HARDWARE_SAMSUNG_SLSI_EXYNOS_IFDWRITER_H__
#define __HARDWARE_SAMSUNG_SLSI_EXYNOS_IFDWRITER_H__

#include "hwjpeg-internal.h"

class CEndianessChecker {
    bool __little;

public:
    CEndianessChecker();
    operator bool() { return __little; }
};
#ifndef __LITTLE_ENDIAN__
extern CEndianessChecker __LITTLE_ENDIAN__;
#endif

template <typename T>
char *WriteDataInBig(char *p, T val) {
    if (sizeof(val) == 1) {
        *p++ = val;
    } else if (__LITTLE_ENDIAN__) {
        switch (sizeof(val)) {
            case 2:
                *p++ = static_cast<char>((val >> 8) & 0xFF);
                *p++ = static_cast<char>(val & 0xFF);
                break;
            case 4:
                *p++ = static_cast<char>((val >> 24) & 0xFF);
                *p++ = static_cast<char>((val >> 16) & 0xFF);
                *p++ = static_cast<char>((val >> 8) & 0xFF);
                *p++ = static_cast<char>(val & 0xFF);
                break;
        }
    } else {
        switch (sizeof(val)) {
            case 2:
                *p++ = static_cast<char>(val & 0xFF);
                *p++ = static_cast<char>((val >> 8) & 0xFF);
                break;
            case 4:
                *p++ = static_cast<char>(val & 0xFF);
                *p++ = static_cast<char>((val >> 8) & 0xFF);
                *p++ = static_cast<char>((val >> 16) & 0xFF);
                *p++ = static_cast<char>((val >> 24) & 0xFF);
                break;
        }
    }

    return p;
}

template <typename T>
char *WriteData(char *p, T val) {
    const char *pt = reinterpret_cast<char *>(&val);
    for (size_t i = 0; i < sizeof(val); i++) *p++ = *pt++;
    return p;
}

class CIFDWriter {
    char *m_pBase;
    char *m_pIFDBase;
    char *m_pValue;
    unsigned int m_nTags;

    char *WriteOffset(char *target, char *addr) {
        uint32_t val = Offset(addr);
        const char *p = reinterpret_cast<char *>(&val);
        *target++ = *p++;
        *target++ = *p++;
        *target++ = *p++;
        *target++ = *p++;
        return target;
    }

    void WriteTagTypeCount(uint16_t tag, uint16_t type, uint32_t count) {
        const char *p = reinterpret_cast<char *>(&tag);
        *m_pIFDBase++ = *p++;
        *m_pIFDBase++ = *p++;

        p = reinterpret_cast<char *>(&type);
        *m_pIFDBase++ = *p++;
        *m_pIFDBase++ = *p++;

        p = reinterpret_cast<char *>(&count);
        *m_pIFDBase++ = *p++;
        *m_pIFDBase++ = *p++;
        *m_pIFDBase++ = *p++;
        *m_pIFDBase++ = *p++;

        m_nTags--;
    }

public:
    CIFDWriter(char *offset_base, char *ifdbase, uint16_t tagcount) {
        m_nTags = tagcount;
        m_pBase = offset_base;
        m_pIFDBase = ifdbase;
        m_pValue = m_pIFDBase + IFD_FIELDCOUNT_SIZE + IFD_FIELD_SIZE * tagcount +
                IFD_NEXTIFDOFFSET_SIZE;

        // COUNT field of IFD
        const char *pval = reinterpret_cast<char *>(&m_nTags);
        *m_pIFDBase++ = *pval++;
        *m_pIFDBase++ = *pval++;
    }

    uint32_t Offset(char *p) {
        return static_cast<uint32_t>(PTR_TO_ULONG(p) - PTR_TO_ULONG(m_pBase));
    }

    void WriteByte(uint16_t tag, uint32_t count, const uint8_t value[]) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_BYTE, count);

        if (count > IFD_VALOFF_SIZE) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            for (uint32_t i = 0; i < count; i++) {
                *m_pValue++ = static_cast<char>(value[i]);
            }
        } else {
            for (uint32_t i = 0; i < count; i++) *m_pIFDBase++ = static_cast<char>(value[i]);
            m_pIFDBase += IFD_VALOFF_SIZE - count;
        }
    }

    void WriteShort(uint16_t tag, uint32_t count, const uint16_t value[]) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_SHORT, count);

        const char *p = reinterpret_cast<const char *>(&value[0]);

        if (count > (IFD_VALOFF_SIZE / sizeof(value[0]))) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            for (uint32_t i = 0; i < count; i++) {
                *m_pValue++ = *p++;
                *m_pValue++ = *p++;
            }
        } else {
            for (uint32_t i = 0; i < count; i++) {
                *m_pIFDBase++ = *p++;
                *m_pIFDBase++ = *p++;
            }
            m_pIFDBase += IFD_VALOFF_SIZE - count * sizeof(value[0]);
        }
    }

    void WriteLong(uint16_t tag, uint32_t count, const uint32_t value[]) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_LONG, count);

        const char *p = reinterpret_cast<const char *>(&value[0]);
        if (count > (IFD_VALOFF_SIZE / sizeof(value[0]))) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            *m_pValue++ = *p++;
        } else {
            *m_pIFDBase++ = *p++;
            *m_pIFDBase++ = *p++;
            *m_pIFDBase++ = *p++;
            *m_pIFDBase++ = *p++;
        }
    }

    void WriteASCII(uint16_t tag, uint32_t count, const char *value) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_ASCII, count);

        if (count > IFD_VALOFF_SIZE) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            memcpy(m_pValue, value, count);
            m_pValue[count - 1] = '\0';
            m_pValue += count;
        } else {
            for (uint32_t i = 0; i < count; i++) *m_pIFDBase++ = value[i];
            *(m_pIFDBase - 1) = '\0';
            m_pIFDBase += IFD_VALOFF_SIZE - count;
        }
    }

    void WriteCString(uint16_t tag, uint32_t count, const char *string) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_ASCII, count);

        if (count > IFD_VALOFF_SIZE) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            strncpy(m_pValue, string, count);
            m_pValue[count - 1] = '\0';
            m_pValue += count;
        } else {
            uint32_t i;

            for (i = 0; (i < (count - 1)) && (string[i] != '\0'); i++) *m_pIFDBase++ = string[i];

            while (i++ < count) *m_pIFDBase++ = '\0';

            m_pIFDBase += IFD_VALOFF_SIZE - count;
        }
    }

    void WriteRational(uint16_t tag, uint32_t count, const rational_t value[]) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_RATIONAL, count);
        m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);

        for (uint32_t i = 0; i < count; i++) {
            const char *pt;
            pt = reinterpret_cast<const char *>(&value[i].num);
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
            pt = reinterpret_cast<const char *>(&value[i].den);
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
            *m_pValue++ = *pt++;
        }
    }

    void WriteSRational(uint16_t tag, uint32_t count, const srational_t value[]) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_SRATIONAL, count);
        m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);

        const char *pt = reinterpret_cast<const char *>(value);
        for (uint32_t i = 0; i < sizeof(srational_t) * count; i++) *m_pValue++ = *pt++;
    }

    void WriteUndef(uint16_t tag, uint32_t count, const unsigned char *value) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_UNDEFINED, count);
        if (count > IFD_VALOFF_SIZE) {
            m_pIFDBase = WriteOffset(m_pIFDBase, m_pValue);
            memcpy(m_pValue, value, count);
            m_pValue += count;
        } else {
            for (uint32_t i = 0; i < count; i++) *m_pIFDBase++ = static_cast<char>(value[i]);
            m_pIFDBase += IFD_VALOFF_SIZE - count;
        }
    }

    char *BeginSubIFD(uint16_t tag) {
        ALOG_ASSERT(m_nTags == 0);

        WriteTagTypeCount(tag, EXIF_TYPE_LONG, 1);

        uint32_t offset = Offset(m_pValue);
        const char *poff = reinterpret_cast<char *>(&offset);
        *m_pIFDBase++ = *poff++;
        *m_pIFDBase++ = *poff++;
        *m_pIFDBase++ = *poff++;
        *m_pIFDBase++ = *poff++;

        return m_pValue;
    }

    void EndSubIFD(char *end_of_subIFD) { m_pValue = end_of_subIFD; }
    void CancelSubIFD() { m_pIFDBase -= IFD_FIELD_SIZE; }

    void Finish(bool last) {
        ALOG_ASSERT(m_nTags > 0);

        uint32_t offset = last ? 0 : Offset(m_pValue);
        const char *pv = reinterpret_cast<char *>(&offset);
        *m_pIFDBase++ = *pv++;
        *m_pIFDBase++ = *pv++;
        *m_pIFDBase++ = *pv++;
        *m_pIFDBase++ = *pv++;
    }

    char *GetNextIFDBase() { return m_pValue; }
    char *GetNextTagAddress() { return m_pIFDBase; }
};

#endif //__HARDWARE_SAMSUNG_SLSI_EXYNOS_IFDWRITER_H__
