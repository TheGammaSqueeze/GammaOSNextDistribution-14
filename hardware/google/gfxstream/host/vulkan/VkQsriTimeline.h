#ifndef VK_QSRI_TIMELINE_H
#define VK_QSRI_TIMELINE_H

#include <cstdint>
#include <functional>
#include <map>
#include <mutex>
#include <sstream>

#include "host-common/logging.h"

namespace gfxstream {
namespace vk {

class VkQsriTimeline {
   public:
    using Callback = std::function<void()>;

    void signalNextPresentAndPoll() {
        std::lock_guard<std::mutex> guard(mLock);
        mPresentCount++;
        pollLocked();
    }

    void registerCallbackForNextPresentAndPoll(Callback callback) {
        std::lock_guard<std::mutex> guard(mLock);
        uint64_t requestPresentCount = mRequestPresentCount;
        mRequestPresentCount++;
        mPendingCallbacks.emplace(requestPresentCount, std::move(callback));
        pollLocked();
    }

    VkQsriTimeline() : mPresentCount(0), mRequestPresentCount(0) {}
    ~VkQsriTimeline() {
        std::lock_guard<std::mutex> guard(mLock);
        if (mPendingCallbacks.empty()) {
            return;
        }
        std::stringstream ss;
        ss << mPendingCallbacks.size()
           << " pending QSRI callbacks found when destroying the timeline, waiting for: ";
        for (auto& [requiredPresentCount, callback] : mPendingCallbacks) {
            callback();
            ss << requiredPresentCount << ", ";
        }
        ss << "just call all of callbacks.";
        ERR("%s", ss.str().c_str());
    }

   private:
    std::map<uint64_t, Callback> mPendingCallbacks;
    std::mutex mLock;
    uint64_t mPresentCount;
    uint64_t mRequestPresentCount;

    void pollLocked() {
        auto firstPendingCallback = mPendingCallbacks.lower_bound(mPresentCount);
        for (auto readyCallback = mPendingCallbacks.begin(); readyCallback != firstPendingCallback;
             readyCallback++) {
            readyCallback->second();
        }
        mPendingCallbacks.erase(mPendingCallbacks.begin(), firstPendingCallback);
    }
};

}  // namespace vk
}  // namespace gfxstream

#endif  // VK_QSRI_TIMELINE_H