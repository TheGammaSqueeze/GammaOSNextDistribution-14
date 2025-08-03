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

#include <algorithm>

#include <cstdint>

#include <log/log.h>
#include <cutils/properties.h>

#include "display.h"
#include "format.h"
#include "hwc_dev.h"

HWCDisplay::HWCDisplay(enum disp_role role) :
    active_config(0),
    role(role),
    cb_procs(NULL),
    is_dummy(false),
    vsync_on(false),
    blanked(true),
    is_flip_pending(false)
{
}

void HWCDisplay::setup_composition_pipes()
{
    std::unique_lock<std::mutex> lock(this->mutex);

    KMSDisplay* kdisp = &this->disp_link;

    int count = 0;
    for (auto plane : kdisp->card->get_planes()) {
        auto possible_crtcs = plane->get_possible_crtcs();
        if (std::find(possible_crtcs.begin(), possible_crtcs.end(), kdisp->crtc) != possible_crtcs.end()) {
            planeProps[count].plane = plane;
            ALOGI("Overlay %d: plane_id: %d", count, planeProps[count].plane->id());
            count++;
        }
    }
}

/* Page flip handler callback */
void HWCDisplay::page_flip_handler(int fd, unsigned int frame, unsigned int sec, unsigned int usec, void* data)
{
    HWCDisplay* display = static_cast<HWCDisplay*>(data);

    std::unique_lock<std::mutex> lock(display->mutex);

    if (display->is_flip_pending == false) {
        ALOGW("Spurious page flip?");
        return;
    }

    /* release the old buffers */
    for (auto current_fb_info : display->current_fb_infos)
        delete current_fb_info;
    display->current_fb_infos.clear();

    /* pending are now current */
    for (auto pending_fb_info : display->pending_fb_infos)
        display->current_fb_infos.push_back(pending_fb_info);
    display->pending_fb_infos.clear();

    display->is_flip_pending = false;
    lock.unlock();
    display->cond_flip.notify_one();
}

static int vblank_kick(HWCDisplay* display)
{
    drmVBlank vblank;
    memset(&vblank, 0, sizeof(vblank));
    vblank.request.type = (drmVBlankSeqType)(DRM_VBLANK_RELATIVE | DRM_VBLANK_EVENT |
                display->role == DISP_ROLE_SECONDARY ? DRM_VBLANK_SECONDARY : 0);
    vblank.request.sequence = 1;
    vblank.request.signal = (unsigned long)display;
    int err = drmWaitVBlank(display->disp_link.card->fd(), &vblank);
    if (err < 0) {
        /* FIXME: error in drmWaitVBlank() use SW vsync instead? */
        ALOGE("drmWaitVBlank error %d (%s)", err, strerror(errno));
        return -1;
    }

    return 0;
}

/* Callback function that gets triggered on vsync */
void HWCDisplay::vblank_handler(int fd, unsigned int frame, unsigned int sec, unsigned int usec, void* data)
{
    HWCDisplay* display = static_cast<HWCDisplay*>(data);

    std::unique_lock<std::mutex> lock(display->mutex);

    if (display->vsync_on) {
        int64_t ts = sec * (int64_t)1000000000 + usec * (int64_t)1000;

//        ALOGD("Sending VBLANK at %lld for display %d", ts, display->role);
        display->cb_procs->vsync(display->cb_procs, display->role, ts);

        vblank_kick(display);
    }
}

int HWCDisplay::set_vsync_state(bool state)
{
    std::unique_lock<std::mutex> lock(this->mutex);

    if (this->is_dummy)
        return 0;

    this->vsync_on = state;

    if (this->vsync_on)
        return vblank_kick(this);

    return 0;
}

static void set_plane_properties(kms::AtomicReq& req, drm_plane_props_t* plane_props)
{
    kms::Plane* plane = plane_props->plane;

    req.add(plane, "FB_ID", plane_props->fb_info->fb_id);

    req.add(plane, "IN_FENCE_FD", plane_props->layer->acquireFenceFd);

    req.add(plane, {
            { "CRTC_ID", plane_props->crtc_id },
            { "SRC_X", (plane_props->src_x) << 16 },
            { "SRC_Y", (plane_props->src_y) << 16 },
            { "SRC_W", (plane_props->src_w) << 16 },
            { "SRC_H", (plane_props->src_h) << 16 },
            { "CRTC_X", plane_props->crtc_x },
            { "CRTC_Y", plane_props->crtc_y },
            { "CRTC_W", plane_props->crtc_w },
            { "CRTC_H", plane_props->crtc_h },
    });
}

int HWCDisplay::update_display(drm_plane_props_t* planeProp)
{
    std::unique_lock<std::mutex> lock(this->mutex);

    this->cond_flip.wait(lock, [this]{return !this->is_flip_pending;});

    buffer_handle_t handle = planeProp->layer->handle;
    if (!handle) {
        ALOGW("Got empty handle, nothing to display");
        return 0;
    }

    KMSDisplay* kdisp = &this->disp_link;

    planeProp->fb_info = new DRMFramebuffer(kdisp->card->fd(), handle, false);
    this->pending_fb_infos.push_back(planeProp->fb_info);

    planeProp->crtc_id = kdisp->crtc->id();

    // Atomic display update
    kms::AtomicReq req(*kdisp->card);
    set_plane_properties(req, planeProp);
    int ret = req.commit(this, true);
    if (ret) {
        ALOGE("cannot do atomic commit/page flip: %d (%s)", ret, strerror(errno));
        for (auto pending_fb_info : this->pending_fb_infos)
            delete pending_fb_info;
        this->pending_fb_infos.clear();
        return ret;
    }

//    ALOGD("Scheduled page flip on display %d", this->role);
    this->is_flip_pending = true;

    return 0;
}

void HWCDisplay::blank(int blank)
{
    std::unique_lock<std::mutex> lock(this->mutex);

    if (this->is_dummy)
        return;

    KMSDisplay* kdisp = &this->disp_link;

    ALOGI("Linking connector %d to crtc %d", kdisp->con->id(), kdisp->crtc->id());

    int ret = kdisp->crtc->set_mode(kdisp->con, kdisp->mode);
    if (ret) {
        ALOGE("Failed to set crtc mode (%s)", strerror(-ret));
        return;
    }

    // FIXME: This should actually blank the screen
    this->blanked = blank;
}

int HWCDisplay::get_display_configs(uint32_t* configs, size_t* numConfigs)
{
    if (!configs || !numConfigs)
        return -EINVAL;

    if (*numConfigs == 0)
        return 0;

    std::unique_lock<std::mutex> lock(this->mutex);

    size_t num = this->configs.size();
    if (num > *numConfigs)
        num = *numConfigs;

    for (size_t i = 0; i < num; i++)
        configs[i] = i;

    *numConfigs = num;

    return 0;
}

int HWCDisplay::get_display_attributes(uint32_t cfg, const uint32_t* attributes, int32_t* values)
{
    if (!attributes || !values)
        return 0;

    std::unique_lock<std::mutex> lock(this->mutex);

    if (cfg >= this->configs.size())
        return -EINVAL;

    display_config_t* config = &this->configs[cfg];

    for (size_t i = 0; attributes[i] != HWC_DISPLAY_NO_ATTRIBUTE; i++) {
        switch (attributes[i]) {
        case HWC_DISPLAY_VSYNC_PERIOD:
            values[i] = 1000000000 / config->fps;
            break;
        case HWC_DISPLAY_WIDTH:
            values[i] = config->xres;
            break;
        case HWC_DISPLAY_HEIGHT:
            values[i] = config->yres;
            break;
        case HWC_DISPLAY_DPI_X:
            values[i] = 1000 * config->xdpi;
            break;
        case HWC_DISPLAY_DPI_Y:
            values[i] = 1000 * config->ydpi;
            break;
        }
    }

    return 0;
}
