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

#include <sstream>

#include <cutils/trace.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/resource.h>

#include <log/log.h>
#include <cutils/properties.h>
#define HWC_REMOVE_DEPRECATED_VERSIONS 1
#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>
#include <hardware_legacy/uevent.h>

#include <kms++/kms++.h>

#include "display.h"
#include "format.h"
#include "hwc_dev.h"

#define LCD_DISPLAY_DEFAULT_HRES 1920
#define LCD_DISPLAY_DEFAULT_VRES 1080
#define LCD_DISPLAY_DEFAULT_FPS 60

#define LCD_DISPLAY_DEFAULT_DPI 120
#define HDMI_DISPLAY_DEFAULT_DPI 75

#define HWC_PRIORITY_LOW_DISPLAY (19)

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))
#endif

#define WIDTH(rect) ((rect).right - (rect).left)
#define HEIGHT(rect) ((rect).bottom - (rect).top)

static bool is_valid_display(omap_hwc_device_t* hwc_dev, int disp)
{
    if (disp < 0 || disp >= MAX_DISPLAYS || !hwc_dev->displays[disp])
        return false;

    return true;
}

struct drm_connector_type {
    int type;
    char name[64];
};

#define CONNECTOR_TYPE_STR(type) { DRM_MODE_CONNECTOR_ ## type, #type }

static const struct drm_connector_type connector_type_list[] = {
    CONNECTOR_TYPE_STR(Unknown),
    CONNECTOR_TYPE_STR(VGA),
    CONNECTOR_TYPE_STR(DVII),
    CONNECTOR_TYPE_STR(DVID),
    CONNECTOR_TYPE_STR(DVIA),
    CONNECTOR_TYPE_STR(Composite),
    CONNECTOR_TYPE_STR(SVIDEO),
    CONNECTOR_TYPE_STR(LVDS),
    CONNECTOR_TYPE_STR(Component),
    CONNECTOR_TYPE_STR(9PinDIN),
    CONNECTOR_TYPE_STR(DisplayPort),
    CONNECTOR_TYPE_STR(HDMIA),
    CONNECTOR_TYPE_STR(HDMIB),
    CONNECTOR_TYPE_STR(TV),
    CONNECTOR_TYPE_STR(eDP),
    CONNECTOR_TYPE_STR(VIRTUAL),
    CONNECTOR_TYPE_STR(DSI),
    CONNECTOR_TYPE_STR(DPI),
};

/* The connectors for primary and external displays can be controlled
 * by the below system properties
 *   - ro.hwc.primary.conn
 *   - ro.hwc.external.conn
 * If these are not set (default), `DRM_CONNECTOR_TYPE_Unknown` will be
 * considered as primary display connector, and `DRM_CONNECTOR_TYPE_HDMIA`
 * will be considered as secondary display.
 *
 * The values are <string> constants, with acceptable values being as defined
 * by the DRM interface `DRM_CONNECTOR_TYPE_<string>`.
 *
 * If nothing is set, external is `HDMIA` connector.
 */
static int display_get_connector_type(int disp)
{
    char prop_val[PROPERTY_VALUE_MAX];

    if (disp == HWC_DISPLAY_PRIMARY)
        property_get("ro.hwc.primary.conn", prop_val, "");
    else
        property_get("ro.hwc.external.conn", prop_val, "HDMIA");

    for (size_t i = 0; i < ARRAY_SIZE(connector_type_list); i++) {
        if (!strncasecmp(prop_val, connector_type_list[i].name, 64))
            return connector_type_list[i].type;
    }

    return -1;
}

static void get_connectors(omap_hwc_device_t* hwc_dev)
{
    int primary_connector_type = display_get_connector_type(HWC_DISPLAY_PRIMARY);
    int external_connector_type = display_get_connector_type(HWC_DISPLAY_EXTERNAL);

    // Find primary connector
    for (auto connector : hwc_dev->card->get_connectors()) {
        if (primary_connector_type != -1) {
            if (connector->connector_type() == (uint32_t)primary_connector_type) {
                hwc_dev->primaryConector = connector;
                break;
            }
        } else {
            /* If connector type is not specified use the first
             * connector that is not our HWC_DISPLAY_EXTERNAL connector
             */
            if (connector->connector_type() != (uint32_t)external_connector_type) {
                hwc_dev->primaryConector = connector;
                break;
            }
        }
    }

    // Find external connector
    for (auto connector : hwc_dev->card->get_connectors()) {
        if (connector->connector_type() == (uint32_t)external_connector_type) {
            hwc_dev->externalConector = connector;
            break;
        }
    }
}

static int init_primary_display(omap_hwc_device_t* hwc_dev)
{
    if (hwc_dev->displays[HWC_DISPLAY_PRIMARY]) {
        ALOGE("Display %d is already connected", HWC_DISPLAY_PRIMARY);
        return -EBUSY;
    }

    kms::Connector* connector = hwc_dev->primaryConector;

    HWCDisplay* display = new HWCDisplay(DISP_ROLE_PRIMARY);
    hwc_dev->displays[HWC_DISPLAY_PRIMARY] = display;

    if (!connector) {
        ALOGW("No connector found for primary display");
        ALOGW("Using dummy primary display");

        display->is_dummy = true;

        display_config_t config;
        config.xres = LCD_DISPLAY_DEFAULT_HRES;
        config.yres = LCD_DISPLAY_DEFAULT_VRES;
        config.fps = LCD_DISPLAY_DEFAULT_FPS;
        config.xdpi = LCD_DISPLAY_DEFAULT_DPI;
        config.ydpi = LCD_DISPLAY_DEFAULT_DPI;
        display->configs.push_back(config);

        return 0;
    }

    /* Always use default mode for now */
    kms::Videomode mode = connector->get_default_mode();

    display_config_t config;
    config.xres = mode.hdisplay;
    config.yres = mode.vdisplay;
    config.fps = mode.vrefresh;
    config.xdpi = LCD_DISPLAY_DEFAULT_DPI;
    config.ydpi = LCD_DISPLAY_DEFAULT_DPI;
    display->configs.push_back(config);

    display->disp_link.card = hwc_dev->card;
    display->disp_link.con = connector;
    display->disp_link.crtc = connector->get_current_crtc();
    // FIXME: user resource manager
    if (!display->disp_link.crtc)
        display->disp_link.crtc = connector->get_possible_crtcs()[0];
    display->disp_link.mode = mode;

    display->setup_composition_pipes();

    return 0;
}

static int add_external_hdmi_display(omap_hwc_device_t* hwc_dev)
{
    if (hwc_dev->displays[HWC_DISPLAY_EXTERNAL]) {
        ALOGE("Display %d is already connected", HWC_DISPLAY_EXTERNAL);
        return 0;
    }

    kms::Connector* connector = hwc_dev->externalConector;

    if (!connector) {
        ALOGE("No connector for external display");
        return -1;
    }

    /* wait until EDID read finishes */
    do {
        connector->refresh();
    } while (connector->get_modes().size() == 0);

    // FIXME: Allow selecting other modes until HWC 1.4 support is added
    kms::Videomode mode = connector->get_default_mode();

    HWCDisplay* display = new HWCDisplay(DISP_ROLE_SECONDARY);
    hwc_dev->displays[HWC_DISPLAY_EXTERNAL] = display;

    display_config_t config;
    config.xres = mode.hdisplay;
    config.yres = mode.vdisplay;
    config.fps = mode.vrefresh;
    config.xdpi = HDMI_DISPLAY_DEFAULT_DPI;
    config.ydpi = HDMI_DISPLAY_DEFAULT_DPI;
    display->configs.push_back(config);

    display->disp_link.card = hwc_dev->card;
    display->disp_link.con = connector;
    display->disp_link.crtc = connector->get_current_crtc();
    // FIXME: user resource manager
    if (!display->disp_link.crtc)
        display->disp_link.crtc = connector->get_possible_crtcs()[0];
    display->disp_link.mode = mode;

    display->setup_composition_pipes();

    return 0;
}

static void remove_external_hdmi_display(omap_hwc_device_t* hwc_dev)
{
    HWCDisplay* display = hwc_dev->displays[HWC_DISPLAY_EXTERNAL];
    if (!display) {
        ALOGW("Failed to remove non-existent display %d", HWC_DISPLAY_EXTERNAL);
        return;
    }

    delete hwc_dev->displays[HWC_DISPLAY_EXTERNAL];
    hwc_dev->displays[HWC_DISPLAY_EXTERNAL] = NULL;
}

static void handle_hotplug(omap_hwc_device_t* hwc_dev, bool state)
{
    if (state) {
        int err = add_external_hdmi_display(hwc_dev);
        if (err) {
            remove_external_hdmi_display(hwc_dev);
            return;
        }
        ALOGI("Added external display");
    } else {
        remove_external_hdmi_display(hwc_dev);
        ALOGI("Removed external display");
    }
}

static int find_hdmi_connector_status(omap_hwc_device_t* hwc_dev)
{
    auto connector = hwc_dev->externalConector;
    if (!connector)
        return false;

    bool old_state = connector->connected();
    connector->refresh();
    bool cur_state = connector->connected();

    if (cur_state != old_state)
        ALOGI("%s event for connector %u\n",
              cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
              connector->id());

    return cur_state == DRM_MODE_CONNECTED;
}

static bool check_hotplug_status(omap_hwc_device_t* hwc_dev, bool old_state)
{
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    bool state = find_hdmi_connector_status(hwc_dev);
    if (state != old_state)
        handle_hotplug(hwc_dev, state);

    lock.unlock();

    if (hwc_dev->cb_procs) {
        if (hwc_dev->cb_procs->hotplug)
            hwc_dev->cb_procs->hotplug(hwc_dev->cb_procs, HWC_DISPLAY_EXTERNAL, state);
        if (hwc_dev->cb_procs->invalidate)
            hwc_dev->cb_procs->invalidate(hwc_dev->cb_procs);
    }

    return state;
}

static void hwc_hdmi_thread(void* data)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)data;

    setpriority(PRIO_PROCESS, 0, HWC_PRIORITY_LOW_DISPLAY);

    uevent_init();

    struct pollfd pfds[1] = {
        {
            pfds[0].fd = uevent_get_fd(),
            pfds[0].events = POLLIN,
            pfds[0].revents = 0,
        }
    };

    const char* hdmi_uevent_path = "change@/devices/platform/omapdrm.0/drm/card0";
    static char uevent_desc[4096];
    memset(uevent_desc, 0, sizeof(uevent_desc));

    /* Check outside of uevent loop in-case already plugged in */
    bool state = check_hotplug_status(hwc_dev, false);

    while (true) {
        int err = poll(pfds, ARRAY_SIZE(pfds), -1);
        if (err < 0) {
            ALOGE("received hdmi_thread poll() error event %d", err);
            break;
        }

        if (pfds[0].revents & POLLIN) {
            /* keep last 2 zeroes to ensure double 0 termination */
            uevent_next_event(uevent_desc, sizeof(uevent_desc) - 2);
            if (strlen(hdmi_uevent_path) <= 0 || strcmp(uevent_desc, hdmi_uevent_path))
                continue; /* event not for us */

            state = check_hotplug_status(hwc_dev, state);
        }
    }

    ALOGE("HDMI polling thread exiting\n");
}

/*
 * DRM event polling thread
 *   We poll for DRM events in this thread. DRM events can be vblank and/or
 *   page-flip events both occurring on Vsync.
 */
static void hwc_drm_event_thread(void* data)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)data;

    setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

    struct pollfd pfds = {
        pfds.fd = hwc_dev->card->fd(),
        pfds.events = POLLIN,
        pfds.revents = 0,
    };

    drmEventContext evctx = {
        evctx.version = 2,
        evctx.vblank_handler = HWCDisplay::vblank_handler,
        evctx.page_flip_handler = HWCDisplay::page_flip_handler,
    };

    while (true) {
        int ret = poll(&pfds, 1, 60000);
        if (ret < 0) {
            ALOGE("Event poll error %d", errno);
            break;
        } else if (ret == 0) {
            ALOGI("Event poll timeout");
            continue;
        }
        if (pfds.revents & POLLIN)
            drmHandleEvent(pfds.fd, &evctx);
    }

    ALOGE("DRM event polling thread exiting\n");
}

static void adjust_drm_plane_to_layer(hwc_layer_1_t* layer, drm_plane_props_t* plane)
{
    if (!layer || !plane) {
        ALOGE("Bad layer or plane");
        return;
    }

    /* display position */
    plane->crtc_x = layer->displayFrame.left;
    plane->crtc_y = layer->displayFrame.top;
    plane->crtc_w = WIDTH(layer->displayFrame);
    plane->crtc_h = HEIGHT(layer->displayFrame);

    /* crop */
    plane->src_x = layer->sourceCrop.left;
    plane->src_y = layer->sourceCrop.top;
    plane->src_w = WIDTH(layer->sourceCrop);
    plane->src_h = HEIGHT(layer->sourceCrop);

    plane->layer = layer;
}

static int hwc_prepare_for_display(omap_hwc_device_t* hwc_dev, int disp, hwc_display_contents_1_t* content)
{
    if (!is_valid_display(hwc_dev, disp))
        return -ENODEV;

    HWCDisplay* display = hwc_dev->displays[disp];

    if (display->is_dummy) {
        for (size_t i = 0; i < content->numHwLayers - 1; i++) {
            hwc_layer_1_t* layer = &content->hwLayers[i];
            layer->compositionType = HWC_OVERLAY;
        }
        return 0;
    }

    /* Set the FB_TARGET layer */
    adjust_drm_plane_to_layer(&content->hwLayers[content->numHwLayers - 1], &display->planeProps[disp]);

    return 0;
}

static int hwc_prepare(struct hwc_composer_device_1* dev, size_t numDisplays, hwc_display_contents_1_t** displays)
{
    atrace_begin(ATRACE_TAG_HAL, "am57xx_hwc_prepare");
    if (!numDisplays || !displays)
        return 0;

    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    int err = 0;

    for (size_t i = 0; i < numDisplays; i++) {
        hwc_display_contents_1_t* contents = displays[i];

        if (!contents)
            continue;

        if (contents->numHwLayers == 0) {
            ALOGW("Prepare given no content for display %zu", i);
            continue;
        }

        int disp_err = hwc_prepare_for_display(hwc_dev, i, contents);
        if (!err && disp_err)
            err = disp_err;
    }

    atrace_end(ATRACE_TAG_HAL);
    return err;
}

static int hwc_set_for_display(omap_hwc_device_t* hwc_dev, int disp, hwc_display_contents_1_t* content)
{
    if (!is_valid_display(hwc_dev, disp))
        return -ENODEV;

    HWCDisplay* display = hwc_dev->displays[disp];
    drm_plane_props_t* planeProp = &display->planeProps[disp];

    int err = 0;

    /*
     * clear release and retire fence fd's here in case we do
     * not set them in update_display()
     */
    for (size_t i = 0; i < content->numHwLayers; i++) {
        hwc_layer_1_t* layer = &content->hwLayers[i];
        layer->releaseFenceFd = -1;
    }
    content->retireFenceFd = -1;

    if (!display->is_dummy) {
        err = display->update_display(planeProp);
        if (err)
            ALOGE("Failed to update display %d\n", disp);
    }

    /* clear any remaining acquire fences */
    for (size_t i = 0; i < content->numHwLayers; i++) {
        hwc_layer_1_t* layer = &content->hwLayers[i];
        if (layer->acquireFenceFd >= 0) {
            close(layer->acquireFenceFd);
            layer->acquireFenceFd = -1;
        }
    }

    return err;
}

static int hwc_set(struct hwc_composer_device_1* dev, size_t numDisplays, hwc_display_contents_1_t** displays)
{
    atrace_begin(ATRACE_TAG_HAL, "am57xx_hwc_set");
    if (!numDisplays || !displays)
        return 0;

    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    int err = 0;

    for (size_t i = 0; i < numDisplays; i++) {
        hwc_display_contents_1_t* contents = displays[i];

        if (!contents)
            continue;

        if (contents->numHwLayers == 0) {
            ALOGE("Set given no content for display %zu", i);
            continue;
        }

        int disp_err = hwc_set_for_display(hwc_dev, i, contents);
        if (!err && disp_err)
            err = disp_err;
    }

    atrace_end(ATRACE_TAG_HAL);
    return err;
}

static int hwc_eventControl(struct hwc_composer_device_1* dev, int disp, int event, int enabled)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    if (!is_valid_display(hwc_dev, disp))
        return -EINVAL;

    switch (event) {
    case HWC_EVENT_VSYNC:
        // FIXME: This is a hack
        hwc_dev->displays[disp]->cb_procs = hwc_dev->cb_procs;

        ALOGD("%s vsync for display %d", enabled ? "Enabling" : "Disabling", disp);
        return hwc_dev->displays[disp]->set_vsync_state(enabled);

    default:
        return -EINVAL;
    }

    return 0;
}

static int hwc_blank(struct hwc_composer_device_1* dev, int disp, int blank)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    ALOGD("%s display %d", blank ? "Blanking" : "Unblanking", disp);

    if (!is_valid_display(hwc_dev, disp))
        return -EINVAL;

    hwc_dev->displays[disp]->blank(blank);

    return 0;
}

static int hwc_query(struct hwc_composer_device_1* dev, int what, int* value)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    switch (what) {
    case HWC_BACKGROUND_LAYER_SUPPORTED:
        // we don't support the background layer yet
        value[0] = 0;
        break;
    case HWC_VSYNC_PERIOD:
        ALOGW("Query for deprecated vsync value, returning 60Hz");
        *value = 1000 * 1000 * 1000 / 60;
        break;
    case HWC_DISPLAY_TYPES_SUPPORTED:
        *value = HWC_DISPLAY_PRIMARY_BIT | HWC_DISPLAY_EXTERNAL_BIT;
        break;
    default:
        // unsupported query
        return -EINVAL;
    }

    return 0;
}

static void hwc_registerProcs(struct hwc_composer_device_1* dev, hwc_procs_t const* procs)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    hwc_dev->cb_procs = (typeof(hwc_dev->cb_procs))procs;

    /* now that cb_procs->hotplug is valid */
    try {
        hwc_dev->hdmi_thread = new std::thread(hwc_hdmi_thread, hwc_dev);
    } catch (...) {
        ALOGE("Failed to create HDMI listening thread (%s)", strerror(errno));
    }
}

static int hwc_getDisplayConfigs(struct hwc_composer_device_1* dev, int disp, uint32_t* configs, size_t* numConfigs)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    if (!is_valid_display(hwc_dev, disp))
        return -EINVAL;

    HWCDisplay* display = hwc_dev->displays[disp];

    return display->get_display_configs(configs, numConfigs);
}

static int hwc_getDisplayAttributes(struct hwc_composer_device_1* dev, int disp, uint32_t config, const uint32_t* attributes, int32_t* values)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)dev;
    std::unique_lock<std::mutex> lock(hwc_dev->mutex);

    if (!is_valid_display(hwc_dev, disp))
        return -EINVAL;

    HWCDisplay* display = hwc_dev->displays[disp];

    return display->get_display_attributes(config, attributes, values);
}

static int hwc_device_close(hw_device_t* device)
{
    omap_hwc_device_t* hwc_dev = (omap_hwc_device_t*)device;

    if (hwc_dev) {
        if (hwc_dev->event_thread)
            delete hwc_dev->event_thread;

        for (size_t i = 0; i < MAX_DISPLAYS; i++)
            delete hwc_dev->displays[i];

        delete hwc_dev;
    }

    return 0;
}

static int hwc_device_open(const hw_module_t* module, const char* name, hw_device_t** device)
{
    if (strcmp(name, HWC_HARDWARE_COMPOSER))
        return -EINVAL;

    omap_hwc_device_t* hwc_dev = new omap_hwc_device_t;
    memset(hwc_dev, 0, sizeof(*hwc_dev));

    /* Open DRM device */
    hwc_dev->card = new kms::Card();

    /* Find primary and external connectors */
    get_connectors(hwc_dev);

    int ret = init_primary_display(hwc_dev);
    if (ret) {
        ALOGE("Could not initialize primary display");
        return -1;
    }

    hwc_dev->event_thread = new std::thread(hwc_drm_event_thread, hwc_dev);

    hwc_dev->device.common.tag = HARDWARE_DEVICE_TAG;
    hwc_dev->device.common.version = HWC_DEVICE_API_VERSION_1_1;
    hwc_dev->device.common.module = (hw_module_t*)module;
    hwc_dev->device.common.close = hwc_device_close;
    hwc_dev->device.prepare = hwc_prepare;
    hwc_dev->device.set = hwc_set;
    hwc_dev->device.eventControl = hwc_eventControl;
    hwc_dev->device.blank = hwc_blank;
    hwc_dev->device.query = hwc_query;
    hwc_dev->device.registerProcs = hwc_registerProcs;
    hwc_dev->device.getDisplayConfigs = hwc_getDisplayConfigs;
    hwc_dev->device.getDisplayAttributes = hwc_getDisplayAttributes;

    *device = &hwc_dev->device.common;

    return 0;
}

static struct hw_module_methods_t module_methods = {
    .open = hwc_device_open,
};

omap_hwc_module_t HAL_MODULE_INFO_SYM = {
    .base = {
        .common = {
            .tag = HARDWARE_MODULE_TAG,
            .module_api_version = HWC_MODULE_API_VERSION_0_1,
            .hal_api_version = HARDWARE_HAL_API_VERSION,
            .id = HWC_HARDWARE_MODULE_ID,
            .name = "AM57xx Hardware Composer HAL",
            .author = "Texas Instruments",
            .methods = &module_methods,
        },
    },
};
