/*--------------------------------------------------------------------------
Copyright (c) 2018-2020, 2021 The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

--------------------------------------------------------------------------*/
/*============================================================================
                            O p e n M A X   w r a p p e r s
                             O p e n  M A X   C o r e

 This module contains the registry table for the OpenMAX core.

*//*========================================================================*/

#include "qc_omx_core.h"

omx_core_cb_type core[] =
{
    //Common entries
    OMX_REGISTRY_ENTRY("OMX.qcom.video.decoder.avc", "libOmxVdec.so", "video_decoder.avc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.decoder.avc.dsmode", "libOmxVideoDSMode.so", "video_decoder.avc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.decoder.hevc", "libOmxVdec.so", "video_decoder.hevc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.decoder.vp9", "libOmxVdec.so", "video_decoder.vp9"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.encoder.avc", "libOmxVenc.so", "video_encoder.avc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.encoder.hevc", "libOmxVenc.so", "video_encoder.hevc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.encoder.hevc.cq", "libOmxVenc.so", "video_encoder.hevc"),
    OMX_REGISTRY_ENTRY("OMX.qcom.video.encoder.heic", "libOmxVenc.so", "image_encoder.heic"),
};

const unsigned int SIZE_OF_CORE = sizeof(core) / sizeof(omx_core_cb_type);
omx_core_cb_type component[SIZE_OF_CORE];

