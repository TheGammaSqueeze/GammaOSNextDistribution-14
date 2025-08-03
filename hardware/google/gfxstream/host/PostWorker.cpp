/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#include "PostWorker.h"

#include <string.h>

#include <chrono>

#include "ColorBuffer.h"
#include "FrameBuffer.h"
#include "RenderThreadInfo.h"
#include "aemu/base/Tracing.h"
#include "host-common/GfxstreamFatalError.h"
#include "host-common/logging.h"
#include "host-common/misc.h"
#include "vulkan/VkCommonOperations.h"

#define POST_DEBUG 0
#if POST_DEBUG >= 1
#define DD(fmt, ...) \
    fprintf(stderr, "%s:%d| " fmt, __func__, __LINE__, ##__VA_ARGS__)
#else
#define DD(fmt, ...) (void)0
#endif

#define POST_ERROR(fmt, ...)                                                  \
    do {                                                                      \
        fprintf(stderr, "%s(%s:%d): " fmt "\n", __func__, __FILE__, __LINE__, \
                ##__VA_ARGS__);                                               \
        fflush(stderr);                                                       \
    } while (0)

static void sDefaultRunOnUiThread(UiUpdateFunc f, void* data, bool wait) {
    (void)f;
    (void)data;
    (void)wait;
}

namespace gfxstream {
namespace {

using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;

hwc_transform_t getTransformFromRotation(int rotation) {
    switch (static_cast<int>(rotation / 90)) {
        case 1:
            return HWC_TRANSFORM_ROT_270;
        case 2:
            return HWC_TRANSFORM_ROT_180;
        case 3:
            return HWC_TRANSFORM_ROT_90;
        default:
            return HWC_TRANSFORM_NONE;
    }
}

}  // namespace

PostWorker::PostWorker(bool mainThreadPostingOnly, FrameBuffer* fb, Compositor* compositor)
    : mFb(fb),
      m_mainThreadPostingOnly(mainThreadPostingOnly),
      m_runOnUiThread(m_mainThreadPostingOnly ? emugl::get_emugl_window_operations().runOnUiThread
                                              : sDefaultRunOnUiThread),
      m_compositor(compositor) {}

std::shared_future<void> PostWorker::composeImpl(const FlatComposeRequest& composeRequest) {
    std::shared_future<void> completedFuture =
        std::async(std::launch::deferred, [] {}).share();
    completedFuture.wait();

    if (!isComposeTargetReady(composeRequest.targetHandle)) {
        ERR("The last composition on the target buffer hasn't completed.");
    }

    Compositor::CompositionRequest compositorRequest = {};
    compositorRequest.target = mFb->borrowColorBufferForComposition(composeRequest.targetHandle,
                                                                    /*colorBufferIsTarget=*/true);
    if (!compositorRequest.target) {
        ERR("Compose target is null (cb=0x%x).", composeRequest.targetHandle);
        return completedFuture;
    }

    for (const ComposeLayer& guestLayer : composeRequest.layers) {
        if (guestLayer.composeMode == HWC2_COMPOSITION_SOLID_COLOR) {
            // HWC2_COMPOSITION_SOLID_COLOR has no colorbuffer backing.
            auto& compositorLayer = compositorRequest.layers.emplace_back();
            compositorLayer.props = guestLayer;
        } else {
            auto source = mFb->borrowColorBufferForComposition(guestLayer.cbHandle,
                                                            /*colorBufferIsTarget=*/false);
            if (!source) {
                continue;
            }

            auto& compositorLayer = compositorRequest.layers.emplace_back();
            compositorLayer.props = guestLayer;
            compositorLayer.source = std::move(source);
        }
    }

    return m_compositor->compose(compositorRequest);
}

void PostWorker::block(std::promise<void> scheduledSignal, std::future<void> continueSignal) {
    // Do not block mainthread.
    if (m_mainThreadPostingOnly) {
        return;
    }
    // MSVC STL doesn't support not copyable std::packaged_task. As a workaround, we use the
    // copyable std::shared_ptr here.
    auto block = std::make_shared<Post::Block>(Post::Block{
        .scheduledSignal = std::move(scheduledSignal),
        .continueSignal = std::move(continueSignal),
    });
    runTask(std::packaged_task<void()>([block] {
        block->scheduledSignal.set_value();
        block->continueSignal.wait();
    }));
}

PostWorker::~PostWorker() {}

void PostWorker::post(ColorBuffer* cb, std::unique_ptr<Post::CompletionCallback> postCallback) {
    auto packagedPostCallback = std::shared_ptr<Post::CompletionCallback>(std::move(postCallback));
    runTask(
        std::packaged_task<void()>([cb, packagedPostCallback, this] {
            auto completedFuture = postImpl(cb);
            (*packagedPostCallback)(completedFuture);
        }));
}

void PostWorker::exit() {
    runTask(std::packaged_task<void()>([this] { exitImpl(); }));
}

void PostWorker::viewport(int width, int height) {
    runTask(std::packaged_task<void()>(
        [width, height, this] { viewportImpl(width, height); }));
}

void PostWorker::compose(std::unique_ptr<FlatComposeRequest> composeRequest,
                         std::unique_ptr<Post::CompletionCallback> composeCallback) {
    // std::shared_ptr(std::move(...)) is WA for MSFT STL implementation bug:
    // https://developercommunity.visualstudio.com/t/unable-to-move-stdpackaged-task-into-any-stl-conta/108672
    auto packagedComposeCallback =
        std::shared_ptr<Post::CompletionCallback>(std::move(composeCallback));
    auto packagedComposeRequest = std::shared_ptr<FlatComposeRequest>(std::move(composeRequest));
    runTask(
        std::packaged_task<void()>([packagedComposeCallback, packagedComposeRequest, this] {
        auto completedFuture = composeImpl(*packagedComposeRequest);
        m_composeTargetToComposeFuture.emplace(packagedComposeRequest->targetHandle,
                                               completedFuture);
        (*packagedComposeCallback)(completedFuture);
        }));
}

void PostWorker::clear() {
    runTask(std::packaged_task<void()>([this] { clearImpl(); }));
}

void PostWorker::runTask(std::packaged_task<void()> task) {
    using Task = std::packaged_task<void()>;
    auto taskPtr = std::make_unique<Task>(std::move(task));
    if (m_mainThreadPostingOnly) {
        m_runOnUiThread(
            [](void* data) {
                std::unique_ptr<Task> taskPtr(reinterpret_cast<Task*>(data));
                (*taskPtr)();
            },
            taskPtr.release(), false);
    } else {
        (*taskPtr)();
    }
}

bool PostWorker::isComposeTargetReady(uint32_t targetHandle) {
    // Even if the target ColorBuffer has already been destroyed, the compose future should have
    // been waited and set to the ready state.
    for (auto i = m_composeTargetToComposeFuture.begin();
         i != m_composeTargetToComposeFuture.end();) {
        auto& composeFuture = i->second;
        if (composeFuture.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
            i = m_composeTargetToComposeFuture.erase(i);
        } else {
            i++;
        }
    }
    if (m_composeTargetToComposeFuture.find(targetHandle) == m_composeTargetToComposeFuture.end()) {
        return true;
    }
    return false;
}

}  // namespace gfxstream
