// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "host-common/opengles.h"

#include "aemu/base/GLObjectCounter.h"
#include "aemu/base/files/PathUtils.h"
#include "aemu/base/files/Stream.h"
#include "aemu/base/memory/MemoryTracker.h"
#include "aemu/base/SharedLibrary.h"
#include "aemu/base/system/System.h"
#include "host-common/address_space_device.h"
#include "host-common/address_space_graphics.h"
#include "host-common/address_space_graphics_types.h"
#include "host-common/GfxstreamFatalError.h"
#include "host-common/GoldfishDma.h"
#include "host-common/RefcountPipe.h"
#include "host-common/FeatureControl.h"
#include "host-common/globals.h"
#include "host-common/opengl/emugl_config.h"
#include "host-common/opengl/GLProcessPipe.h"
#include "host-common/opengl/logger.h"
#include "host-common/opengl/gpuinfo.h"

#include "render-utils/render_api_functions.h"
#include "OpenGLESDispatch/EGLDispatch.h"
#include "OpenGLESDispatch/GLESv2Dispatch.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <optional>

#define D(...)
#define DD(...)
#define E(...)

// #define D(...) do { \
//     VERBOSE_PRINT(init,__VA_ARGS__); \
//     android_opengl_logger_write(__VA_ARGS__); \
// } while(0);
//
// #define DD(...) do { \
//     VERBOSE_PRINT(gles,__VA_ARGS__); \
//     android_opengl_logger_write(__VA_ARGS__); \
// } while(0);
//
// #define E(fmt,...) do { \
//     derror(fmt, ##__VA_ARGS__); \
//     android_opengl_logger_write(fmt "\n", ##__VA_ARGS__); \
// } while(0);

using android::base::pj;
using android::base::SharedLibrary;
using android::emulation::asg::AddressSpaceGraphicsContext;
using android::emulation::asg::ConsumerCallbacks;
using android::emulation::asg::ConsumerInterface;
using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;
using gfxstream::gl::EGLDispatch;
using gfxstream::gl::GLESv2Dispatch;

/* Name of the GLES rendering library we're going to use */
#define RENDERER_LIB_NAME "libOpenglRender"

/* Declared in "android/globals.h" */
int  android_gles_fast_pipes = 1;

// Define the Render API function pointers.
#define FUNCTION_(ret, name, sig, params) \
        inline ret (*name) sig = NULL;
LIST_RENDER_API_FUNCTIONS(FUNCTION_)
#undef FUNCTION_

static bool sOpenglLoggerInitialized = false;
static bool sRendererUsesSubWindow = false;
static bool sEgl2egl = false;
static gfxstream::RenderLib* sRenderLib = nullptr;
static gfxstream::RendererPtr sRenderer = nullptr;

int android_prepareOpenglesEmulation() {
    android_init_opengl_logger();

    bool glFineLogging = android::base::getEnvironmentVariable("ANDROID_EMUGL_FINE_LOG") == "1";
    bool glLogPrinting = android::base::getEnvironmentVariable("ANDROID_EMUGL_LOG_PRINT") == "1";

    AndroidOpenglLoggerFlags loggerFlags =
        static_cast<AndroidOpenglLoggerFlags>(
        (glFineLogging ? OPENGL_LOGGER_DO_FINE_LOGGING : 0) |
        (glLogPrinting ? OPENGL_LOGGER_PRINT_TO_STDOUT : 0));

    android_opengl_logger_set_flags(loggerFlags);

    sOpenglLoggerInitialized = true;
    sRendererUsesSubWindow = true;

    sEgl2egl = false;
    if (android::base::getEnvironmentVariable("ANDROID_EGL_ON_EGL") == "1") {
        sEgl2egl = true;
    }

    return 0;
}

int android_setOpenglesEmulation(void* renderLib, void* eglDispatch, void* glesv2Dispatch) {
    sRenderLib = (gfxstream::RenderLib*)renderLib;
    (void)eglDispatch;
    (void)glesv2Dispatch;
    sEgl2egl = android::base::getEnvironmentVariable("ANDROID_EGL_ON_EGL") == "1";
    return 0;
}

int android_initOpenglesEmulation() {
    GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
        << "Not meant to call android_initOpenglesEmulation in the new build.";
}

int
android_startOpenglesRenderer(int width, int height, bool guestPhoneApi, int guestApiLevel,
                              const QAndroidVmOperations *vm_operations,
                              const QAndroidEmulatorWindowAgent *window_agent,
                              const QAndroidMultiDisplayAgent *multi_display_agent,
                              int* glesMajorVersion_out,
                              int* glesMinorVersion_out)
{
    if (!sRenderLib) {
        D("Can't start OpenGLES renderer without support libraries");
        return -1;
    }

    if (sRenderer) {
        return 0;
    }

    const GpuInfoList& gpuList = globalGpuInfoList();
    std::string gpuInfoAsString = gpuList.dump();
    android_opengl_logger_write("%s: gpu info", __func__);
    android_opengl_logger_write("%s", gpuInfoAsString.c_str());

    sRenderLib->setRenderer(emuglConfig_get_current_renderer());
    sRenderLib->setAvdInfo(guestPhoneApi, guestApiLevel);
    // sRenderLib->setCrashReporter(&crashhandler_die_format);
    // sRenderLib->setFeatureController(&android::featurecontrol::isEnabled);
    sRenderLib->setSyncDevice(goldfish_sync_create_timeline,
            goldfish_sync_create_fence,
            goldfish_sync_timeline_inc,
            goldfish_sync_destroy_timeline,
            goldfish_sync_register_trigger_wait,
            goldfish_sync_device_exists);

    emugl_logger_struct logfuncs;
    logfuncs.coarse = android_opengl_logger_write;
    logfuncs.fine = android_opengl_cxt_logger_write;
    sRenderLib->setLogger(logfuncs);
    sRenderLib->setGLObjectCounter(android::base::GLObjectCounter::get());
    emugl_dma_ops dma_ops;
    dma_ops.get_host_addr = android_goldfish_dma_ops.get_host_addr;
    dma_ops.unlock = android_goldfish_dma_ops.unlock;
    sRenderLib->setDmaOps(dma_ops);
    sRenderLib->setVmOps(*vm_operations);
    sRenderLib->setAddressSpaceDeviceControlOps(get_address_space_device_control_ops());
    sRenderLib->setWindowOps(*window_agent, *multi_display_agent);
    // sRenderLib->setUsageTracker(android::base::CpuUsage::get(),
    //                             android::base::MemoryTracker::get());

    sRenderer = sRenderLib->initRenderer(width, height, sRendererUsesSubWindow, sEgl2egl);
    android_setOpenglesRenderer(&sRenderer);

    // android::snapshot::Snapshotter::get().addOperationCallback(
    //         [](android::snapshot::Snapshotter::Operation op,
    //            android::snapshot::Snapshotter::Stage stage) {
    //             sRenderer->snapshotOperationCallback(op, stage);
    //         });

    android::emulation::registerOnLastRefCallback(
            sRenderLib->getOnLastColorBufferRef());

    ConsumerInterface iface = {
        // create
        [](struct asg_context context,
           android::base::Stream* loadStream, ConsumerCallbacks callbacks,
           uint32_t contextId, uint32_t capsetId,
           std::optional<std::string> nameOpt) {
           return sRenderer->addressSpaceGraphicsConsumerCreate(
               context, loadStream, callbacks, contextId, capsetId, std::move(nameOpt));
        },
        // destroy
        [](void* consumer) {
           sRenderer->addressSpaceGraphicsConsumerDestroy(consumer);
        },
        // pre save
        [](void* consumer) {
           sRenderer->addressSpaceGraphicsConsumerPreSave(consumer);
        },
        // global presave
        []() {
           sRenderer->pauseAllPreSave();
        },
        // save
        [](void* consumer, android::base::Stream* stream) {
           sRenderer->addressSpaceGraphicsConsumerSave(consumer, stream);
        },
        // global postsave
        []() {
           sRenderer->resumeAll();
        },
        // postSave
        [](void* consumer) {
           sRenderer->addressSpaceGraphicsConsumerPostSave(consumer);
        },
        // postLoad
        [](void* consumer) {
           sRenderer->addressSpaceGraphicsConsumerRegisterPostLoadRenderThread(consumer);
        },
        // global preload
        []() {
            // This wants to address that when using asg, pipe wants to clean
            // up all render threads and wait for gl objects, but framebuffer
            // notices that there is a render thread info that is still not
            // cleaned up because these render threads come from asg.
            android::opengl::forEachProcessPipeIdRunAndErase([](uint64_t id) {
                android_cleanupProcGLObjects(id);
            });
            android_waitForOpenglesProcessCleanup();
        },
    };
    AddressSpaceGraphicsContext::setConsumer(iface);

    if (!sRenderer) {
        D("Can't start OpenGLES renderer?");
        return -1;
    }

    // after initRenderer is a success, the maximum GLES API is calculated depending
    // on feature control and host GPU support. Set the obtained GLES version here.
    if (glesMajorVersion_out && glesMinorVersion_out)
        sRenderLib->getGlesVersion(glesMajorVersion_out, glesMinorVersion_out);
    return 0;
}

bool
android_asyncReadbackSupported() {
    if (sRenderer) {
        return sRenderer->asyncReadbackSupported();
    } else {
        D("tried to query async readback support "
          "before renderer initialized. Likely guest rendering");
        return false;
    }
}

void
android_setPostCallback(OnPostFunc onPost, void* onPostContext, bool useBgraReadback, uint32_t displayId)
{
    if (sRenderer) {
        sRenderer->setPostCallback(onPost, onPostContext, useBgraReadback, displayId);
    }
}

ReadPixelsFunc android_getReadPixelsFunc() {
    if (sRenderer) {
        return sRenderer->getReadPixelsCallback();
    } else {
        return nullptr;
    }
}

FlushReadPixelPipeline android_getFlushReadPixelPipeline() {
    if (sRenderer) {
        return sRenderer->getFlushReadPixelPipeline();
    } else {
        return nullptr;
    }
}


static char* strdupBaseString(const char* src) {
    const char* begin = strchr(src, '(');
    if (!begin) {
        return strdup(src);
    }

    const char* end = strrchr(begin + 1, ')');
    if (!end) {
        return strdup(src);
    }

    // src is of the form:
    // "foo (barzzzzzzzzzz)"
    //       ^            ^
    //       (b+1)        e
    //     = 5            18
    int len;
    begin += 1;
    len = end - begin;

    char* result;
    result = (char*)malloc(len + 1);
    memcpy(result, begin, len);
    result[len] = '\0';
    return result;
}

void android_getOpenglesHardwareStrings(char** vendor,
                                        char** renderer,
                                        char** version) {
    assert(vendor != NULL && renderer != NULL && version != NULL);
    assert(*vendor == NULL && *renderer == NULL && *version == NULL);
    if (!sRenderer) {
        D("Can't get OpenGL ES hardware strings when renderer not started");
        return;
    }

    const gfxstream::Renderer::HardwareStrings strings = sRenderer->getHardwareStrings();
    D("OpenGL Vendor=[%s]", strings.vendor.c_str());
    D("OpenGL Renderer=[%s]", strings.renderer.c_str());
    D("OpenGL Version=[%s]", strings.version.c_str());

    /* Special case for the default ES to GL translators: extract the strings
     * of the underlying OpenGL implementation. */
    if (strncmp(strings.vendor.c_str(), "Google", 6) == 0 &&
            strncmp(strings.renderer.c_str(), "Android Emulator OpenGL ES Translator", 37) == 0) {
        *vendor = strdupBaseString(strings.vendor.c_str());
        *renderer = strdupBaseString(strings.renderer.c_str());
        *version = strdupBaseString(strings.version.c_str());
    } else {
        *vendor = strdup(strings.vendor.c_str());
        *renderer = strdup(strings.renderer.c_str());
        *version = strdup(strings.version.c_str());
    }
}

void android_getOpenglesVersion(int* maj, int* min) {
    sRenderLib->getGlesVersion(maj, min);
    fprintf(stderr, "%s: maj min %d %d\n", __func__, *maj, *min);
}

void
android_stopOpenglesRenderer(bool wait)
{
    if (sRenderer) {
        sRenderer->stop(wait);
        if (wait) {
            sRenderer.reset();
            android_stop_opengl_logger();
        }
    }
}

void
android_finishOpenglesRenderer()
{
    if (sRenderer) {
        sRenderer->finish();
    }
}

static gfxstream::RenderOpt sOpt;
static int sWidth, sHeight;
static int sNewWidth, sNewHeight;

int android_showOpenglesWindow(void* window,
                               int wx,
                               int wy,
                               int ww,
                               int wh,
                               int fbw,
                               int fbh,
                               float dpr,
                               float rotation,
                               bool deleteExisting,
                               bool hideWindow) {
    if (!sRenderer) {
        return -1;
    }
    FBNativeWindowType win = (FBNativeWindowType)(uintptr_t)window;
    bool success = sRenderer->showOpenGLSubwindow(win, wx, wy, ww, wh, fbw, fbh,
                                                  dpr, rotation, deleteExisting,
                                                  hideWindow);
    sNewWidth = ww * dpr;
    sNewHeight = wh * dpr;
    return success ? 0 : -1;
}

void
android_setOpenglesTranslation(float px, float py)
{
    if (sRenderer) {
        sRenderer->setOpenGLDisplayTranslation(px, py);
    }
}

void
android_setOpenglesScreenMask(int width, int height, const unsigned char* rgbaData)
{
    if (sRenderer) {
        sRenderer->setScreenMask(width, height, rgbaData);
    }
}

int
android_hideOpenglesWindow(void)
{
    if (!sRenderer) {
        return -1;
    }
    bool success = sRenderer->destroyOpenGLSubwindow();
    return success ? 0 : -1;
}

void
android_redrawOpenglesWindow(void)
{
    if (sRenderer) {
        sRenderer->repaintOpenGLDisplay();
    }
}

bool
android_hasGuestPostedAFrame(void)
{
    if (sRenderer) {
        return sRenderer->hasGuestPostedAFrame();
    }
    return false;
}

void
android_resetGuestPostedAFrame(void)
{
    if (sRenderer) {
        sRenderer->resetGuestPostedAFrame();
    }
}

static ScreenshotFunc sScreenshotFunc = nullptr;

void android_registerScreenshotFunc(ScreenshotFunc f)
{
    sScreenshotFunc = f;
}

bool android_screenShot(const char* dirname, uint32_t displayId)
{
    if (sScreenshotFunc) {
        return sScreenshotFunc(dirname, displayId);
    }
    return false;
}

const gfxstream::RendererPtr& android_getOpenglesRenderer() { return sRenderer; }

void android_setOpenglesRenderer(gfxstream::RendererPtr* renderer) {
    sRenderer = *renderer;
}

void android_onGuestGraphicsProcessCreate(uint64_t puid) {
    if (sRenderer) {
        sRenderer->onGuestGraphicsProcessCreate(puid);
    }
}

void android_cleanupProcGLObjects(uint64_t puid) {
    if (sRenderer) {
        sRenderer->cleanupProcGLObjects(puid);
    }
}

void android_cleanupProcGLObjectsAndWaitFinished(uint64_t puid) {
    if (sRenderer) {
        sRenderer->cleanupProcGLObjects(puid);
    }
}

void android_waitForOpenglesProcessCleanup() {
    if (sRenderer) {
        sRenderer->waitForProcessCleanup();
    }
}

struct AndroidVirtioGpuOps* android_getVirtioGpuOps() {
    if (sRenderer) {
        return sRenderer->getVirtioGpuOps();
    }
    return nullptr;
}

const void* android_getEGLDispatch() {
    if (sRenderer) {
        return sRenderer->getEglDispatch();
    }
    return nullptr;
}

const void* android_getGLESv2Dispatch() {
    if (sRenderer) {
        return sRenderer->getGles2Dispatch();
    }
    return nullptr;
}

void android_setVsyncHz(int vsyncHz) {
    if (sRenderer) {
        sRenderer->setVsyncHz(vsyncHz);
    }
}

void android_setOpenglesDisplayConfigs(int configId, int w, int h, int dpiX,
                                       int dpiY) {
    if (sRenderer) {
        sRenderer->setDisplayConfigs(configId, w, h, dpiX, dpiY);
    }
}

void android_setOpenglesDisplayActiveConfig(int configId) {
    if (sRenderer) {
        sRenderer->setDisplayActiveConfig(configId);
    }
}
