#ifndef VIRTGPU_GFXSTREAM_RENDERER_UNSTABLE_H
#define VIRTGPU_GFXSTREAM_RENDERER_UNSTABLE_H

#include "gfxstream/virtio-gpu-gfxstream-renderer.h"

#ifdef __cplusplus
extern "C" {
#endif

// Enables the host to control which memory types the guest will be allowed to map. For types not
// in the mask, the bits HOST_VISIBLE and HOST_COHERENT will be removed.
#define STREAM_RENDERER_PARAM_HOST_VISIBLE_MEMORY_MASK 8

// Skip android opengles initiation. Used by aemu to skip android opengles initiation.
// aemu does its own initialization in qemu/android/android/android-emu/android/opengles.cpp.
// TODO(joshuaduong): Migrate aemu to use stream_renderer_init without this hack. This will
// require adding more options to customize the feature flags, etc.
#define STREAM_RENDERER_SKIP_OPENGLES_INIT 10

// Information about one device's memory mask.
struct stream_renderer_param_host_visible_memory_mask_entry {
    // Which device the mask applies to.
    struct stream_renderer_device_id device_id;
    // Memory types allowed to be host visible are 1, otherwise 0.
    uint32_t memory_type_mask;
};

// Information about the devices in the system with host visible memory type constraints.
struct stream_renderer_param_host_visible_memory_mask {
    // Points to a stream_renderer_param_host_visible_memory_mask_entry array.
    uint64_t entries;
    // Length of the entries array.
    uint64_t num_entries;
};

// Enables the host to control which GPU is used for rendering.
#define STREAM_RENDERER_PARAM_RENDERING_GPU 9

// External callbacks for tracking metrics.
// Separating each function to a parameter allows new functions to be added later.
#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_ADD_INSTANT_EVENT 1024
typedef void (*stream_renderer_param_metrics_callback_add_instant_event)(int64_t event_code);

#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_ADD_INSTANT_EVENT_WITH_DESCRIPTOR 1025
typedef void (*stream_renderer_param_metrics_callback_add_instant_event_with_descriptor)(
    int64_t event_code, int64_t descriptor);

#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_ADD_INSTANT_EVENT_WITH_METRIC 1026
typedef void (*stream_renderer_param_metrics_callback_add_instant_event_with_metric)(
    int64_t event_code, int64_t metric_value);

#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_ADD_VULKAN_OUT_OF_MEMORY_EVENT 1027
typedef void (*stream_renderer_param_metrics_callback_add_vulkan_out_of_memory_event)(
    int64_t result_code, uint32_t op_code, const char* function, uint32_t line,
    uint64_t allocation_size, bool is_host_side_result, bool is_allocation);

// STREAM_RENDERER_PARAM_RENDERER_FEATURES: stream_renderer_param::value is a pointer to a null
// terminated string of the form "<feature1 name>:[enabled|disabled],<feature 2 ...>".
#define STREAM_RENDERER_PARAM_RENDERER_FEATURES 11

#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_SET_ANNOTATION 1028
typedef void (*stream_renderer_param_metrics_callback_set_annotation)(const char* key,
                                                                      const char* value);

#define STREAM_RENDERER_PARAM_METRICS_CALLBACK_ABORT 1029
typedef void (*stream_renderer_param_metrics_callback_abort)();

VG_EXPORT void gfxstream_backend_setup_window(void* native_window_handle, int32_t window_x,
                                              int32_t window_y, int32_t window_width,
                                              int32_t window_height, int32_t fb_width,
                                              int32_t fb_height);

VG_EXPORT void stream_renderer_flush(uint32_t res_handle);

// Platform resources and contexts support
#define STREAM_RENDERER_PLATFORM_RESOURCE_USE_MASK 0xF0
#define STREAM_RENDERER_PLATFORM_RESOURCE_TYPE_MASK 0x0F

// types
#define STREAM_RENDERER_PLATFORM_RESOURCE_TYPE_EGL_NATIVE_PIXMAP 0x01
#define STREAM_RENDERER_PLATFORM_RESOURCE_TYPE_EGL_IMAGE 0x02
#define STREAM_RENDERER_PLATFORM_RESOURCE_TYPE_VK_EXT_MEMORY_HANDLE 0x03

// uses
#define STREAM_RENDERER_PLATFORM_RESOURCE_USE_PRESERVE 0x10

VG_EXPORT int stream_renderer_platform_import_resource(int res_handle, int res_info,
                                                       void* resource);
VG_EXPORT int stream_renderer_platform_resource_info(int res_handle, int* width, int* height,
                                                     int* internal_format);
VG_EXPORT void* stream_renderer_platform_create_shared_egl_context(void);
VG_EXPORT int stream_renderer_platform_destroy_shared_egl_context(void*);

struct stream_renderer_resource_info {
    uint32_t handle;
    uint32_t virgl_format;
    uint32_t width;
    uint32_t height;
    uint32_t depth;
    uint32_t flags;
    uint32_t tex_id;
    uint32_t stride;
    int drm_fourcc;
};

VG_EXPORT int stream_renderer_resource_get_info(int res_handle,
                                                struct stream_renderer_resource_info* info);

VG_EXPORT int stream_renderer_snapshot(const char* dir);

VG_EXPORT int stream_renderer_restore(const char* dir);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif
