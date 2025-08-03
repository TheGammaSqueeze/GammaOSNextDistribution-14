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
#ifndef __HARDWARE_SAMSUNG_SLSI_EXYNOS_APPMARKER_WRITER_H__
#define __HARDWARE_SAMSUNG_SLSI_EXYNOS_APPMARKER_WRITER_H__

#include <ExynosExif.h>

#include "hwjpeg-internal.h"
#include "include/hardware/exynos/ExynosExif.h"

#define JPEG_MAX_SEGMENT_SIZE ((1 << 16) - 1)
#define JPEG_MARKER_SIZE 2
#define JPEG_SEGMENT_LENFIELD_SIZE 2
#define JPEG_APP1_OEM_RESERVED 200

#define IFD_FIELDCOUNT_SIZE 2
#define IFD_NEXTIFDOFFSET_SIZE 4

#define IFD_TAG_SIZE 2
#define IFD_TYPE_SIZE 2
#define IFD_COUNT_SIZE 4
#define IFD_VALOFF_SIZE 4

#define IFD_FIELD_SIZE (IFD_TAG_SIZE + IFD_TYPE_SIZE + IFD_COUNT_SIZE + IFD_VALOFF_SIZE)

#define EXTRA_APPMARKER_MIN 1
#define EXTRA_APPMARKER_LIMIT 10

#define MAX_GPS_PROCESSINGMETHOD_SIZE 108

#define EXIF_SUBSECTIME_LENGTH 5
#define EXIF_DATETIME_LENGTH 20
#define EXIF_GPSDATESTAMP_LENGTH 11

class CAppMarkerWriter {
    char *m_pAppBase;
    char *m_pApp1End;
    size_t m_szMaxThumbSize; // Maximum available thumbnail stream size minus JPEG_MARKER_SIZE
    uint16_t m_szApp1;       // The size of APP1 segment without marker
    uint16_t m_szApp11;      // The size of APP11 segment without marker
    uint16_t m_n0thIFDFields;
    uint16_t m_n1stIFDFields;
    uint16_t m_nExifIFDFields;
    uint16_t m_nGPSIFDFields;
    exif_attribute_t *m_pExif;
    extra_appinfo_t *m_pExtra;

    uint32_t m_szMake;
    uint32_t m_szSoftware;
    uint32_t m_szModel;
    uint32_t m_szUniqueID;

    char *m_pMainBase;
    // The address to write compressed stream of the thumbnail image
    char *m_pThumbBase;
    // points to the "ValueOffset" field of JPEGInterchangeFormatLen tag of 1st IFD
    // This should be updated after compression of thumbnail image.
    // Note that the address may not be aligned by 32-bit.
    char *m_pThumbSizePlaceholder;

    void Init();

    char *WriteAPP1(char *base, bool reserve_thumbnail_space, bool updating = false);
    char *WriteAPPX(char *base, bool just_reserve);
    char *WriteAPP11(char *current, size_t dummy, size_t align);

public:
    // dummy: number of dummy bytes written by the compressor of the main image
    // this dummy size should be added to the APP1 length. Howerver, this dummy area
    // for the main image stream should not be written by neither of appwriter nor
    // thumbnail compressor.
    CAppMarkerWriter();
    CAppMarkerWriter(char *base, exif_attribute_t *exif, debug_attribute_t *debug);

    ~CAppMarkerWriter() {}

    void PrepareAppWriter(char *base, exif_attribute_t *exif, extra_appinfo_t *info);

    char *GetMainStreamBase() { return m_pMainBase; }
    char *GetThumbStreamBase() { return m_pThumbBase; }
    char *GetThumbStreamSizeAddr() {
        char *p = m_pThumbSizePlaceholder;
        m_pThumbSizePlaceholder = NULL;
        return p;
    }
    size_t GetMaxThumbnailSize() { return m_szMaxThumbSize; }
    size_t GetAPP1ResrevedSize() { return JPEG_APP1_OEM_RESERVED; }
    // CalculateAPPSize() is valid after Write() is successful.
    size_t CalculateAPPSize(size_t thumblen = JPEG_MAX_SEGMENT_SIZE) {
        size_t appsize = 0;
        if (m_szApp1 > 0) appsize += m_szApp1 + JPEG_MARKER_SIZE;
        if (m_pExtra) {
            for (int idx = 0; idx < m_pExtra->num_of_appmarker; idx++)
                appsize += m_pExtra->appInfo[idx].dataSize + +JPEG_MARKER_SIZE +
                        JPEG_SEGMENT_LENFIELD_SIZE;
        }
        if (IsThumbSpaceReserved())
            appsize += m_szMaxThumbSize + JPEG_APP1_OEM_RESERVED;
        else
            appsize += min(m_szMaxThumbSize, thumblen);

        return appsize + m_szApp11;
    }

    char *GetApp1End() { return m_pApp1End; }

    void Write(bool reserve_thumbnail_space, size_t dummy, size_t align,
               bool reserve_debug = false) {
        m_pApp1End = WriteAPP1(m_pAppBase, reserve_thumbnail_space);
        char *appXend = WriteAPPX(m_pApp1End, reserve_debug);
        char *app11end = WriteAPP11(appXend, dummy, align);
        m_szApp11 = PTR_DIFF(appXend, app11end);
        m_pMainBase = app11end - dummy;
    }

    void Update() { WriteAPP1(m_pAppBase, false, true); }

    bool IsThumbSpaceReserved() {
        return PTR_DIFF(m_pAppBase, m_pApp1End) ==
                (m_szApp1 + m_szMaxThumbSize + JPEG_APP1_OEM_RESERVED + JPEG_MARKER_SIZE);
    }

    void Finalize(size_t thumbsize);

    void UpdateApp1Size(size_t amount);
};
#endif //__HARDWARE_SAMSUNG_SLSI_EXYNOS_APPMARKER_WRITER_H__
