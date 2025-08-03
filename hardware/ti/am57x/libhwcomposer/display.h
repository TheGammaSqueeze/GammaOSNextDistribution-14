/*
 * Copyright (C) Texas Instruments Incorporated - http://www.ti.com/
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

#pragma once

#include <condition_variable>
#include <mutex>

#include <cstdbool>
#include <cstdint>

#include <hardware/hwcomposer.h>
#include <kms++/kms++.h>

#include <xf86drm.h>
#include <xf86drmMode.h>

#include "drmfb.h"

#define MAX_DISPLAYS 4
#define DSS_AVAILABLE_PIPES 4

typedef struct display_config {
    unsigned int xres;
    unsigned int yres;
    unsigned int fps;
    unsigned int xdpi;
    unsigned int ydpi;
} display_config_t;

enum disp_role {
    DISP_ROLE_PRIMARY = 0,
    DISP_ROLE_SECONDARY,
};

typedef struct drm_plane_props {
    hwc_layer_1_t* layer;

    kms::Plane* plane;
    uint64_t crtc_id;

    uint64_t crtc_x;
    uint64_t crtc_y;
    uint64_t crtc_w;
    uint64_t crtc_h;
    uint64_t src_x;
    uint64_t src_y;
    uint64_t src_w;
    uint64_t src_h;

    DRMFramebuffer* fb_info;
} drm_plane_props_t;

class KMSDisplay {
public:
    KMSDisplay() :
        card(NULL),
        con(NULL),
        crtc(NULL),
        mode() {}

    kms::Card* card;
    kms::Connector* con;
    kms::Crtc* crtc;
    kms::Videomode mode;
};

class HWCDisplay
{
public:
    HWCDisplay(enum disp_role role);
    ~HWCDisplay(){};

    void setup_composition_pipes();

    int update_display(drm_plane_props_t* planeProp);

    std::vector<display_config_t> configs;
    uint32_t active_config;

    enum disp_role role;

    drm_plane_props_t planeProps[DSS_AVAILABLE_PIPES];

    KMSDisplay disp_link;

    const hwc_procs_t* cb_procs;

    bool is_dummy;

    int set_vsync_state(bool state);
    void blank(int blank);
    int get_display_configs(uint32_t* configs, size_t* numConfigs);
    int get_display_attributes(uint32_t cfg, const uint32_t* attributes, int32_t* values);

    static void page_flip_handler(int fd, unsigned int frame, unsigned int sec, unsigned int usec, void* data);
    static void vblank_handler(int fd, unsigned int frame, unsigned int sec, unsigned int usec, void* data);

private:
    std::vector<DRMFramebuffer*> pending_fb_infos;
    std::vector<DRMFramebuffer*> current_fb_infos;

    bool vsync_on;
    bool blanked;

    std::mutex mutex;
    std::condition_variable cond_flip;
    volatile bool is_flip_pending;
};
