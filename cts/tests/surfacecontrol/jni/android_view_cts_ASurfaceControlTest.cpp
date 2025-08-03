/*
 * Copyright 2018 The Android Open Source Project
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

#define LOG_TAG "ASurfaceControlTest"

#include <ChoreographerTestUtils.h>
#include <android/choreographer.h>
#include <android/data_space.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/log.h>
#include <android/looper.h>
#include <android/native_window_jni.h>
#include <android/surface_control.h>
#include <android/surface_control_jni.h>
#include <android/sync.h>
#include <android/trace.h>
#include <errno.h>
#include <jni.h>
#include <poll.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <array>
#include <cinttypes>
#include <string>

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

static struct {
    jclass clazz;
    jmethodID onTransactionComplete;
} gTransactionCompleteListenerClassInfo;

static AHardwareBuffer* allocateBuffer(int32_t width, int32_t height) {
    AHardwareBuffer* buffer = nullptr;
    AHardwareBuffer_Desc desc = {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
            AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    AHardwareBuffer_allocate(&desc, &buffer);

    return buffer;
}

static void fillRegion(void* data, int32_t left, int32_t top, int32_t right,
                       int32_t bottom, uint32_t color, uint32_t stride) {
    uint32_t* ptr = static_cast<uint32_t*>(data);

    ptr += stride * top;

    // Convert color from incoming ARGB format to ABGR
    uint32_t alpha = (color >> 24);
    uint32_t red = (color >> 16) & 0xff;
    uint32_t green = (color >> 8) & 0xff;
    uint32_t blue = color & 0xff;
    color = (alpha << 24) | (blue << 16) | (green << 8) | red;

    for (uint32_t y = top; y < bottom; y++) {
        for (uint32_t x = left; x < right; x++) {
            ptr[x] = color;
        }
        ptr += stride;
    }
}

static bool getSolidBuffer(int32_t width, int32_t height, uint32_t color,
                           AHardwareBuffer** outHardwareBuffer,
                           int* outFence) {
    AHardwareBuffer* buffer = allocateBuffer(width, height);
    if (!buffer) {
        return true;
    }

    AHardwareBuffer_Desc desc = {};
    AHardwareBuffer_describe(buffer, &desc);

    void* data = nullptr;
    const ARect rect{0, 0, width, height};
    AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, &rect,
                                             &data);
    if (!data) {
        AHardwareBuffer_release(buffer);
        return true;
    }

    fillRegion(data, 0, 0, width, height, color, desc.stride);

    AHardwareBuffer_unlock(buffer, outFence);

    *outHardwareBuffer = buffer;
    return false;
}

jobject Utils_getSolidBuffer(JNIEnv* env, jobject /*clazz*/, jint width, jint height, jint color) {
    AHardwareBuffer* buffer;
    if (getSolidBuffer(width, height, static_cast<uint32_t>(color), &buffer, nullptr)) {
        return nullptr;
    }
    jobject result = AHardwareBuffer_toHardwareBuffer(env, buffer);
    AHardwareBuffer_release(buffer);
    return result;
}

static bool getQuadrantBuffer(int32_t width, int32_t height, jint colorTopLeft,
                              jint colorTopRight, jint colorBottomRight,
                              jint colorBottomLeft,
                              AHardwareBuffer** outHardwareBuffer,
                              int* outFence) {
    AHardwareBuffer* buffer = allocateBuffer(width, height);
    if (!buffer) {
        return true;
    }

    AHardwareBuffer_Desc desc = {};
    AHardwareBuffer_describe(buffer, &desc);

    void* data = nullptr;
    const ARect rect{0, 0, width, height};
    AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, &rect,
                                             &data);
    if (!data) {
        return true;
    }

    fillRegion(data, 0, 0, width / 2, height / 2, colorTopLeft, desc.stride);
    fillRegion(data, width / 2, 0, width, height / 2, colorTopRight, desc.stride);
    fillRegion(data, 0, height / 2, width / 2, height, colorBottomLeft,
                         desc.stride);
    fillRegion(data, width / 2, height / 2, width, height, colorBottomRight,
                         desc.stride);

    AHardwareBuffer_unlock(buffer, outFence);

    *outHardwareBuffer = buffer;
    return false;
}

jobject Utils_getQuadrantBuffer(JNIEnv* env, jobject /*clazz*/, jint width, jint height,
                                jint colorTopLeft, jint colorTopRight, jint colorBottomRight,
                                jint colorBottomLeft) {
    AHardwareBuffer* buffer;
    if (getQuadrantBuffer(width, height, colorTopLeft, colorTopRight, colorBottomRight,
                          colorBottomLeft, &buffer, nullptr)) {
        return nullptr;
    }
    jobject result = AHardwareBuffer_toHardwareBuffer(env, buffer);
    AHardwareBuffer_release(buffer);
    return result;
}

jlong Utils_getBufferId(JNIEnv* env, jobject /*clazz*/, jobject jHardwareBuffer) {
    AHardwareBuffer* buffer = AHardwareBuffer_fromHardwareBuffer(env, jHardwareBuffer);
    uint64_t id = 0;
    AHardwareBuffer_getId(buffer, &id);
    return id;
}

jlong SurfaceTransaction_create(JNIEnv* /*env*/, jclass) {
    return reinterpret_cast<jlong>(ASurfaceTransaction_create());
}

void SurfaceTransaction_delete(JNIEnv* /*env*/, jclass, jlong surfaceTransaction) {
    ASurfaceTransaction_delete(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction));
}

jlong SurfaceTransaction_fromJava(JNIEnv* env, jclass, jobject transactionObj) {
    return reinterpret_cast<jlong>(ASurfaceTransaction_fromJava(env, transactionObj));
}

void SurfaceTransaction_apply(JNIEnv* /*env*/, jclass, jlong surfaceTransaction) {
    ASurfaceTransaction_apply(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction));
}

long SurfaceControl_createFromWindow(JNIEnv* env, jclass, jobject jSurface) {
    if (!jSurface) {
        return 0;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        return 0;
    }

    const std::string debugName = "SurfaceControl_createFromWindowLayer";
    ASurfaceControl* surfaceControl =
            ASurfaceControl_createFromWindow(window, debugName.c_str());
    if (!surfaceControl) {
        return 0;
    }

    ANativeWindow_release(window);

    return reinterpret_cast<jlong>(surfaceControl);
}

jlong SurfaceControl_create(JNIEnv* /*env*/, jclass, jlong parentSurfaceControlId) {
    ASurfaceControl* surfaceControl = nullptr;
    const std::string debugName = "SurfaceControl_create";

    surfaceControl = ASurfaceControl_create(
            reinterpret_cast<ASurfaceControl*>(parentSurfaceControlId),
            debugName.c_str());

    return reinterpret_cast<jlong>(surfaceControl);
}

void SurfaceControl_acquire(JNIEnv* /*env*/, jclass, jlong surfaceControl) {
    ASurfaceControl_acquire(reinterpret_cast<ASurfaceControl*>(surfaceControl));
}

jlong SurfaceControl_fromJava(JNIEnv* env, jclass, jobject surfaceControlObj) {
    return reinterpret_cast<jlong>(ASurfaceControl_fromJava(env, surfaceControlObj));
}

void SurfaceControl_release(JNIEnv* /*env*/, jclass, jlong surfaceControl) {
    ASurfaceControl_release(reinterpret_cast<ASurfaceControl*>(surfaceControl));
}

jlong SurfaceTransaction_setSolidBuffer(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction, jint width,
                                        jint height, jint color) {
    AHardwareBuffer* buffer = nullptr;
    int fence = -1;

    bool err = getSolidBuffer(width, height, color, &buffer, &fence);
    if (err) {
        return 0;
    }

    ASurfaceTransaction_setBuffer(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), buffer, fence);

    ASurfaceTransaction_setBufferDataSpace(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), ADATASPACE_UNKNOWN);

    return reinterpret_cast<jlong>(buffer);
}

void SurfaceTransaction_setBuffer(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                  jlong surfaceTransaction, jlong buffer) {
    ASurfaceTransaction_setBuffer(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                  reinterpret_cast<ASurfaceControl*>(surfaceControl),
                                  reinterpret_cast<AHardwareBuffer*>(buffer), -1 /* fence */);

    ASurfaceTransaction_setBufferDataSpace(reinterpret_cast<ASurfaceTransaction*>(
                                                   surfaceTransaction),
                                           reinterpret_cast<ASurfaceControl*>(surfaceControl),
                                           ADATASPACE_UNKNOWN);
}

jlong SurfaceTransaction_setQuadrantBuffer(
        JNIEnv* /*env*/, jclass, jlong surfaceControl, jlong surfaceTransaction,
        jint width, jint height, jint colorTopLeft, jint colorTopRight,
        jint colorBottomRight, jint colorBottomLeft) {
    AHardwareBuffer* buffer = nullptr;
    int fence = -1;

    bool err =
            getQuadrantBuffer(width, height, colorTopLeft, colorTopRight,
                              colorBottomRight, colorBottomLeft, &buffer, &fence);
    if (err) {
        return 0;
    }

    ASurfaceTransaction_setBuffer(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), buffer, fence);

    ASurfaceTransaction_setBufferDataSpace(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), ADATASPACE_UNKNOWN);

    return reinterpret_cast<jlong>(buffer);
}

void SurfaceTransaction_releaseBuffer(JNIEnv* /*env*/, jclass, jlong buffer) {
    AHardwareBuffer_release(reinterpret_cast<AHardwareBuffer*>(buffer));
}

void SurfaceTransaction_setVisibility(JNIEnv* /*env*/, jclass,
                                      jlong surfaceControl,
                                      jlong surfaceTransaction, jboolean show) {
    int8_t visibility = (show) ? ASURFACE_TRANSACTION_VISIBILITY_SHOW :
                                 ASURFACE_TRANSACTION_VISIBILITY_HIDE;
    ASurfaceTransaction_setVisibility(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), visibility);
}

void SurfaceTransaction_setBufferOpaque(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction,
                                        jboolean opaque) {
    int8_t transparency = (opaque) ? ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE :
                                   ASURFACE_TRANSACTION_TRANSPARENCY_TRANSPARENT;
    ASurfaceTransaction_setBufferTransparency(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), transparency);
}

void SurfaceTransaction_setGeometry(JNIEnv* /*env*/, jclass,
                                    jlong surfaceControl,
                                    jlong surfaceTransaction,
                                    jint srcLeft, jint srcTop, jint srcRight, jint srcBottom,
                                    jint dstLeft, jint dstTop, jint dstRight, jint dstBottom,
                                    jint transform) {
    const ARect src{srcLeft, srcTop, srcRight, srcBottom};
    const ARect dst{dstLeft, dstTop, dstRight, dstBottom};
    ASurfaceTransaction_setGeometry(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), src, dst, transform);
}

void SurfaceTransaction_setCrop(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                jlong surfaceTransaction, jint left, jint top, jint right,
                                jint bottom) {
    const ARect crop{left, top, right, bottom};
    ASurfaceTransaction_setCrop(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                reinterpret_cast<ASurfaceControl*>(surfaceControl), crop);
}

void SurfaceTransaction_setPosition(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                    jlong surfaceTransaction, jint x, jint y) {
    ASurfaceTransaction_setPosition(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                    reinterpret_cast<ASurfaceControl*>(surfaceControl), x, y);
}

void SurfaceTransaction_setBufferTransform(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                           jlong surfaceTransaction, jint transform) {
    ASurfaceTransaction_setBufferTransform(reinterpret_cast<ASurfaceTransaction*>(
                                                   surfaceTransaction),
                                           reinterpret_cast<ASurfaceControl*>(surfaceControl),
                                           transform);
}

void SurfaceTransaction_setScale(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                 jlong surfaceTransaction, jfloat xScale, jfloat yScale) {
    ASurfaceTransaction_setScale(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                 reinterpret_cast<ASurfaceControl*>(surfaceControl), xScale,
                                 yScale);
}

void SurfaceTransaction_setDamageRegion(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction, jint left,
                                        jint top, jint right, jint bottom) {
    const ARect rect[] = {{left, top, right, bottom}};
    ASurfaceTransaction_setDamageRegion(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), rect, 1);
}

void SurfaceTransaction_setZOrder(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                  jlong surfaceTransaction, jint z) {
    ASurfaceTransaction_setZOrder(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), z);
}

class CallbackListenerWrapper {
public:
    explicit CallbackListenerWrapper(JNIEnv* env, jobject object, bool waitForFence) {
        env->GetJavaVM(&mVm);
        mCallbackListenerObject = env->NewGlobalRef(object);
        mWaitForFence = waitForFence;
        if (!mCallbackListenerObject) {
            ALOGE("Failed to make global ref");
        }
    }

    ~CallbackListenerWrapper() { getenv()->DeleteGlobalRef(mCallbackListenerObject); }

    /**
     * This is duplicate code from sync.c, but the sync_wait function is not exposed to the ndk.
     * The documentation recommends using poll instead of exposing sync_wait, but the sync_wait
     * also handles errors and retries so copied the code here.
     */
    static int sync_wait(int fd, int timeout) {
        struct pollfd fds;
        int ret;

        if (fd < 0) {
            errno = EINVAL;
            return -1;
        }

        fds.fd = fd;
        fds.events = POLLIN;

        do {
            ret = poll(&fds, 1, timeout);
            if (ret > 0) {
                if (fds.revents & (POLLERR | POLLNVAL)) {
                    errno = EINVAL;
                    return -1;
                }
                return 0;
            } else if (ret == 0) {
                errno = ETIME;
                return -1;
            }
        } while (ret == -1 && (errno == EINTR || errno == EAGAIN));

        return ret;
    }

    static uint64_t getPresentTime(int presentFence) {
        uint64_t presentTime = 0;
        int error = sync_wait(presentFence, 500);
        if (error < 0) {
            ALOGE("Failed to sync fence error=%d", error);
            return 0;
        }

        struct sync_file_info* syncFileInfo = sync_file_info(presentFence);
        if (!syncFileInfo) {
            ALOGE("invalid fence");
            sync_file_info_free(syncFileInfo);
            return 0;
        }

        if (syncFileInfo->status != 1) {
            ALOGE("fence did not signal status=%d", syncFileInfo->status);
            return 0;
        }

        struct sync_fence_info* syncFenceInfo = sync_get_fence_info(syncFileInfo);
        for (size_t i = 0; i < syncFileInfo->num_fences; i++) {
            if (syncFenceInfo[i].timestamp_ns > presentTime) {
                presentTime = syncFenceInfo[i].timestamp_ns;
            }
        }

        sync_file_info_free(syncFileInfo);
        close(presentFence);
        return presentTime;
    }

    void callback(ASurfaceTransactionStats* stats) {
        JNIEnv* env = getenv();
        int64_t latchTime = ASurfaceTransactionStats_getLatchTime(stats);
        uint64_t presentTime = systemTime();
        if (mWaitForFence) {
            int presentFence = ASurfaceTransactionStats_getPresentFenceFd(stats);
            if (presentFence >= 0) {
                presentTime = getPresentTime(presentFence);
            }
        }
        env->CallVoidMethod(mCallbackListenerObject,
                            gTransactionCompleteListenerClassInfo.onTransactionComplete, latchTime,
                            presentTime);
    }

    static void transactionCallbackThunk(void* context, ASurfaceTransactionStats* stats) {
        CallbackListenerWrapper* listener = reinterpret_cast<CallbackListenerWrapper*>(context);
        listener->callback(stats);
        delete listener;
    }

private:
    jobject mCallbackListenerObject;
    JavaVM* mVm;
    bool mWaitForFence;

    JNIEnv* getenv() {
        JNIEnv* env;
        mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        return env;
    }
};

jlong SurfaceTransaction_setDesiredPresentTime(JNIEnv* /*env*/, jclass, jlong surfaceTransaction,
                                              jlong desiredPresentTimeOffset) {
    struct timespec t;
    t.tv_sec = t.tv_nsec = 0;
    clock_gettime(CLOCK_MONOTONIC, &t);
    int64_t currentTime = ((int64_t) t.tv_sec)*1000000000LL + t.tv_nsec;

    int64_t desiredPresentTime = currentTime + desiredPresentTimeOffset;

    ASurfaceTransaction_setDesiredPresentTime(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction), desiredPresentTime);

    return desiredPresentTime;
}

void SurfaceTransaction_setBufferAlpha(JNIEnv* /*env*/, jclass,
                                      jlong surfaceControl,
                                      jlong surfaceTransaction, jdouble alpha) {
    ASurfaceTransaction_setBufferAlpha(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), alpha);
}

void SurfaceTransaction_reparent(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                 jlong newParentSurfaceControl, jlong surfaceTransaction) {
    ASurfaceTransaction_reparent(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl),
            reinterpret_cast<ASurfaceControl*>(newParentSurfaceControl));
}

void SurfaceTransaction_setColor(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                 jlong surfaceTransaction, jfloat r,
                                 jfloat g, jfloat b, jfloat alpha) {
    ASurfaceTransaction_setColor(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl),
            r, g, b, alpha, ADATASPACE_UNKNOWN);
}

void SurfaceTransaction_setEnableBackPressure(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                              jlong surfaceTransaction,
                                              jboolean enableBackPressure) {
    ASurfaceTransaction_setEnableBackPressure(reinterpret_cast<ASurfaceTransaction*>(
                                                      surfaceTransaction),
                                              reinterpret_cast<ASurfaceControl*>(surfaceControl),
                                              enableBackPressure);
}

void SurfaceTransaction_setOnCompleteCallback(JNIEnv* env, jclass, jlong surfaceTransaction,
                                              jboolean waitForFence, jobject callback) {
    void* context = new CallbackListenerWrapper(env, callback, waitForFence);
    ASurfaceTransaction_setOnComplete(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                      reinterpret_cast<void*>(context),
                                      CallbackListenerWrapper::transactionCallbackThunk);
}

void SurfaceTransaction_setOnCommitCallback(JNIEnv* env, jclass, jlong surfaceTransaction,
                                            jobject callback) {
    void* context = new CallbackListenerWrapper(env, callback, false /* waitForFence */);
    ASurfaceTransaction_setOnCommit(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                    reinterpret_cast<void*>(context),
                                    CallbackListenerWrapper::transactionCallbackThunk);
}

// Save context so we can test callbacks without a context provided.
static CallbackListenerWrapper* listener = nullptr;
static void transactionCallbackWithoutContextThunk(void* /* context */,
                                                   ASurfaceTransactionStats* stats) {
    CallbackListenerWrapper::transactionCallbackThunk(listener, stats);
    listener = nullptr;
}

void SurfaceTransaction_setOnCompleteCallbackWithoutContext(JNIEnv* env, jclass,
                                                            jlong surfaceTransaction,
                                                            jboolean waitForFence,
                                                            jobject callback) {
    listener = new CallbackListenerWrapper(env, callback, waitForFence);
    ASurfaceTransaction_setOnComplete(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                      nullptr, transactionCallbackWithoutContextThunk);
}

void SurfaceTransaction_setOnCommitCallbackWithoutContext(JNIEnv* env, jclass,
                                                          jlong surfaceTransaction,
                                                          jobject callback) {
    listener = new CallbackListenerWrapper(env, callback, false /* waitForFence */);
    ASurfaceTransaction_setOnCommit(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                    nullptr, transactionCallbackWithoutContextThunk);
}

void SurfaceTransaction_setFrameTimeline(JNIEnv* /*env*/, jclass, jlong surfaceTransaction,
                                         jlong vsyncId) {
    ASurfaceTransaction_setFrameTimeline(reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
                                         vsyncId);
}

void SurfaceTransaction_setExtendedRangeBrightness(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                                   jlong surfaceTransaction, jfloat currentRatio,
                                                   jfloat desiredRatio) {
    ASurfaceTransaction_setExtendedRangeBrightness(reinterpret_cast<ASurfaceTransaction*>(
                                                           surfaceTransaction),
                                                   reinterpret_cast<ASurfaceControl*>(
                                                           surfaceControl),
                                                   currentRatio, desiredRatio);
}

void SurfaceTransaction_setDesiredHdrHeadroom(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                                   jlong surfaceTransaction, jfloat desiredRatio) {
    ASurfaceTransaction_setDesiredHdrHeadroom(reinterpret_cast<ASurfaceTransaction*>(
                                                           surfaceTransaction),
                                                   reinterpret_cast<ASurfaceControl*>(
                                                           surfaceControl),
                                                   desiredRatio);
}

void SurfaceTransaction_setDataSpace(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                     jlong surfaceTransaction, jint dataspace) {
    ASurfaceTransaction_setBufferDataSpace(reinterpret_cast<ASurfaceTransaction*>(
                                                   surfaceTransaction),
                                           reinterpret_cast<ASurfaceControl*>(surfaceControl),
                                           static_cast<ADataSpace>(dataspace));
}

static struct {
    jclass clazz;
    jmethodID constructor;
} gFrameTimelineClassInfo;

static struct {
    jclass clazz;
    jmethodID constructor;
} gFrameCallbackDataClassInfo;

static void verifyChoreographer(JNIEnv* env, AChoreographer* choreographer) {
    ASSERT(choreographer != nullptr, "Choreographer setup unsuccessful");
}

static void verifyPollCallback(JNIEnv* env, int result) {
    ASSERT(result == ALOOPER_POLL_CALLBACK, "Callback failed with error: %d", result);
}

/** Gets VSync information from Choreographer, including a collection of frame timelines and
 * platform-preferred index using Choreographer. */
jobject SurfaceControlTest_getFrameTimelines(JNIEnv* env, jclass) {
    ALooper_prepare(0);
    ATrace_beginSection("Getting Choreographer instance");
    AChoreographer* choreographer = AChoreographer_getInstance();
    ATrace_endSection();
    verifyChoreographer(env, choreographer);

    VsyncCallback cb1("cb1", env);
    auto start = now();
    ATrace_beginSection("postVsyncCallback");
    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    ATrace_endSection();
    auto delayPeriod = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    ATrace_beginSection("ALooper_pollOnce");
    int result = ALooper_pollOnce(delayPeriod * 5, nullptr, nullptr, nullptr);
    ATrace_endSection();
    verifyPollCallback(env, result);
    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);

    jobjectArray frameTimelineObjs =
            env->NewObjectArray(cb1.getTimeline().size(), gFrameTimelineClassInfo.clazz,
                                /*initial element*/ NULL);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return NULL;
    }
    if (frameTimelineObjs == NULL) {
        jniThrowRuntimeException(env, "Failed to create FrameTimeline array");
        return NULL;
    }
    for (int i = 0; i < cb1.getTimeline().size(); i++) {
        VsyncCallback::FrameTime frameTimeline = cb1.getTimeline()[i];
        jobject frameTimelineObj =
                env->NewObject(gFrameTimelineClassInfo.clazz, gFrameTimelineClassInfo.constructor,
                               frameTimeline.vsyncId, frameTimeline.expectedPresentTime,
                               frameTimeline.deadline);
        env->SetObjectArrayElement(frameTimelineObjs, i, frameTimelineObj);
    }

    return env->NewObject(gFrameCallbackDataClassInfo.clazz,
                          gFrameCallbackDataClassInfo.constructor, frameTimelineObjs,
                          cb1.getPreferredFrameTimelineIndex());
}

static const JNINativeMethod JNI_METHODS[] = {
        {"nSurfaceTransaction_create", "()J", (void*)SurfaceTransaction_create},
        {"nSurfaceTransaction_delete", "(J)V", (void*)SurfaceTransaction_delete},
        {"nSurfaceTransaction_fromJava", "(Landroid/view/SurfaceControl$Transaction;)J",
         (void*)SurfaceTransaction_fromJava},
        {"nSurfaceTransaction_apply", "(J)V", (void*)SurfaceTransaction_apply},
        {"nSurfaceControl_createFromWindow", "(Landroid/view/Surface;)J",
         (void*)SurfaceControl_createFromWindow},
        {"nSurfaceControl_create", "(J)J", (void*)SurfaceControl_create},
        {"nSurfaceControl_acquire", "(J)V", (void*)SurfaceControl_acquire},
        {"nSurfaceControl_release", "(J)V", (void*)SurfaceControl_release},
        {"nSurfaceControl_fromJava", "(Landroid/view/SurfaceControl;)J",
         (void*)SurfaceControl_fromJava},
        {"nSurfaceTransaction_setSolidBuffer", "(JJIII)J",
         (void*)SurfaceTransaction_setSolidBuffer},
        {"nSurfaceTransaction_setBuffer", "(JJJ)V", (void*)SurfaceTransaction_setBuffer},
        {"nSurfaceTransaction_setQuadrantBuffer", "(JJIIIIII)J",
         (void*)SurfaceTransaction_setQuadrantBuffer},
        {"nSurfaceTransaction_releaseBuffer", "(J)V", (void*)SurfaceTransaction_releaseBuffer},
        {"nSurfaceTransaction_setVisibility", "(JJZ)V", (void*)SurfaceTransaction_setVisibility},
        {"nSurfaceTransaction_setBufferOpaque", "(JJZ)V",
         (void*)SurfaceTransaction_setBufferOpaque},
        {"nSurfaceTransaction_setGeometry", "(JJIIIIIIIII)V",
         (void*)SurfaceTransaction_setGeometry},
        {"nSurfaceTransaction_setDamageRegion", "(JJIIII)V",
         (void*)SurfaceTransaction_setDamageRegion},
        {"nSurfaceTransaction_setZOrder", "(JJI)V", (void*)SurfaceTransaction_setZOrder},
        {"nSurfaceTransaction_setDesiredPresentTime", "(JJ)J",
         (void*)SurfaceTransaction_setDesiredPresentTime},
        {"nSurfaceTransaction_setBufferAlpha", "(JJD)V", (void*)SurfaceTransaction_setBufferAlpha},
        {"nSurfaceTransaction_reparent", "(JJJ)V", (void*)SurfaceTransaction_reparent},
        {"nSurfaceTransaction_setColor", "(JJFFFF)V", (void*)SurfaceTransaction_setColor},
        {"nSurfaceTransaction_setEnableBackPressure", "(JJZ)V",
         (void*)SurfaceTransaction_setEnableBackPressure},
        {"nSurfaceTransaction_setOnCompleteCallback",
         "(JZLandroid/view/cts/util/ASurfaceControlTestUtils$TransactionCompleteListener;)V",
         (void*)SurfaceTransaction_setOnCompleteCallback},
        {"nSurfaceTransaction_setOnCommitCallback",
         "(JLandroid/view/cts/util/ASurfaceControlTestUtils$TransactionCompleteListener;)V",
         (void*)SurfaceTransaction_setOnCommitCallback},
        {"nSurfaceTransaction_setCrop", "(JJIIII)V", (void*)SurfaceTransaction_setCrop},
        {"nSurfaceTransaction_setPosition", "(JJII)V", (void*)SurfaceTransaction_setPosition},
        {"nSurfaceTransaction_setBufferTransform", "(JJI)V",
         (void*)SurfaceTransaction_setBufferTransform},
        {"nSurfaceTransaction_setScale", "(JJFF)V", (void*)SurfaceTransaction_setScale},
        {"nSurfaceTransaction_setOnCompleteCallbackWithoutContext",
         "(JZLandroid/view/cts/util/ASurfaceControlTestUtils$TransactionCompleteListener;)V",
         (void*)SurfaceTransaction_setOnCompleteCallbackWithoutContext},
        {"nSurfaceTransaction_setOnCommitCallbackWithoutContext",
         "(JLandroid/view/cts/util/ASurfaceControlTestUtils$TransactionCompleteListener;)V",
         (void*)SurfaceTransaction_setOnCommitCallbackWithoutContext},
        {"nSurfaceTransaction_setFrameTimeline", "(JJ)V",
         (void*)SurfaceTransaction_setFrameTimeline},
        {"nSurfaceTransaction_setExtendedRangeBrightness", "(JJFF)V",
         (void*)SurfaceTransaction_setExtendedRangeBrightness},
        {"nSurfaceTransaction_setDesiredHdrHeadroom", "(JJF)V",
         (void*)SurfaceTransaction_setDesiredHdrHeadroom},
        {"nSurfaceTransaction_setDataSpace", "(JJI)V", (void*)SurfaceTransaction_setDataSpace},
        {"getSolidBuffer", "(III)Landroid/hardware/HardwareBuffer;", (void*)Utils_getSolidBuffer},
        {"getQuadrantBuffer", "(IIIIII)Landroid/hardware/HardwareBuffer;",
         (void*)Utils_getQuadrantBuffer},
        {"getBufferId", "(Landroid/hardware/HardwareBuffer;)J", (void*)Utils_getBufferId},
};

static const JNINativeMethod FRAME_TIMELINE_JNI_METHODS[] = {
        {"nGetFrameTimelines", "()Landroid/view/cts/util/FrameCallbackData;",
         (void*)SurfaceControlTest_getFrameTimelines},
};

}  // anonymous namespace

jint register_android_view_cts_ASurfaceControlTest(JNIEnv* env) {
    jclass transactionCompleteListenerClazz = env->FindClass(
            "android/view/cts/util/ASurfaceControlTestUtils$TransactionCompleteListener");
    gTransactionCompleteListenerClassInfo.clazz =
            static_cast<jclass>(env->NewGlobalRef(transactionCompleteListenerClazz));
    gTransactionCompleteListenerClassInfo.onTransactionComplete =
            env->GetMethodID(transactionCompleteListenerClazz, "onTransactionComplete", "(JJ)V");

    gFrameTimelineClassInfo.clazz = static_cast<jclass>(env->NewGlobalRef(
            env->FindClass("android/view/cts/util/FrameCallbackData$FrameTimeline")));
    gFrameTimelineClassInfo.constructor =
            env->GetMethodID(gFrameTimelineClassInfo.clazz, "<init>", "(JJJ)V");

    gFrameCallbackDataClassInfo.clazz = static_cast<jclass>(
            env->NewGlobalRef(env->FindClass("android/view/cts/util/FrameCallbackData")));
    gFrameCallbackDataClassInfo.constructor =
            env->GetMethodID(gFrameCallbackDataClassInfo.clazz, "<init>",
                             "([Landroid/view/cts/util/FrameCallbackData$FrameTimeline;I)V");

    jclass frameCallbackDataClass = env->FindClass("android/view/cts/util/FrameCallbackData");
    env->RegisterNatives(frameCallbackDataClass, FRAME_TIMELINE_JNI_METHODS,
                         sizeof(FRAME_TIMELINE_JNI_METHODS) / sizeof(JNINativeMethod));

    jclass clazz = env->FindClass("android/view/cts/util/ASurfaceControlTestUtils");
    return env->RegisterNatives(clazz, JNI_METHODS, sizeof(JNI_METHODS) / sizeof(JNINativeMethod));
}
