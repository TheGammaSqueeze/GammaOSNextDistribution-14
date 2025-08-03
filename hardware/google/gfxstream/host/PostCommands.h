#pragma once

#include <GLES2/gl2.h>

#include <functional>
#include <future>
#include <memory>
#include <vector>

#include "Handle.h"
#include "render-utils/Renderer.h"

namespace gfxstream {

class ColorBuffer;

// Posting
enum class PostCmd {
    Post = 0,
    Viewport = 1,
    Compose = 2,
    Clear = 3,
    Screenshot = 4,
    Exit = 5,
    Block = 6,
};

struct Post {
    struct Block {
        // schduledSignal will be set when the block task is scheduled.
        std::promise<void> scheduledSignal;
        // The block task won't stop until continueSignal is ready.
        std::future<void> continueSignal;
    };
    using CompletionCallback =
        std::function<void(std::shared_future<void> waitForGpu)>;
    PostCmd cmd;
    int composeVersion;
    std::vector<char> composeBuffer;
    std::unique_ptr<CompletionCallback> completionCallback = nullptr;
    std::unique_ptr<Block> block = nullptr;
    HandleType cbHandle = 0;
    union {
        ColorBuffer* cb;
        struct {
            int width;
            int height;
        } viewport;
        struct {
            ColorBuffer* cb;
            int screenwidth;
            int screenheight;
            GLenum format;
            GLenum type;
            int rotation;
            void* pixels;
            Rect rect;
        } screenshot;
    };
};

}  // namespace gfxstream