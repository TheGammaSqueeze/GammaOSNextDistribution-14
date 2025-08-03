/*
 *  libacryl_plugins/plugin/g2d_hdr.h
 *
 *   Copyright 2018 Samsung Electronics Co., Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#ifndef __LIBACRYL_PLUGIN_G2D_HDR_H__
#define __LIBACRYL_PLUGIN_G2D_HDR_H__

#include <uapi/g2d.h>

struct g2d_commandlist {
    struct g2d_reg *layer_hdr_mode;
    struct g2d_reg *commands;
    unsigned int layer_count;
    unsigned int command_count;
};

class IG2DHdr10CommandWriter {
public:
    virtual ~IG2DHdr10CommandWriter() { };
    static IG2DHdr10CommandWriter *createInstance();
    virtual bool setLayerStaticMetadata(int layer_index, int dataspace, unsigned int min_luminance, unsigned int max_luminance) = 0;
    virtual bool setLayerOpaqueData(int __unused layer_index, void __unused *data, size_t __unused len) { return true; }
    virtual bool setLayerImageInfo(int __unused layer_index, unsigned int __unused pixfmt, bool __unused alpha_premult) { return true; }
    virtual bool setTargetInfo(int dataspace, void *data) = 0;
    virtual void setTargetDisplayLuminance(unsigned int __unused min, unsigned int __unused max) { };
    virtual struct g2d_commandlist *getCommands() = 0;
    virtual void putCommands(struct g2d_commandlist __unused *commands) { };
    virtual bool hasColorFillLayer(void) { return false; }
};

#endif/* __LIBACRYL_PLUGIN_G2D_HDR_H__ */
