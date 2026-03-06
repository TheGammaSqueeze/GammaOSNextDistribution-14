#define LOG_TAG "gammapad"

#include <android-base/logging.h>
#include <signal.h>

#include "GamepadManager.h"

static gammapad::GamepadManager* gManager = nullptr;

static void signalHandler(int sig) {
    if (gManager) {
        gManager->restoreAllHiddenNodes();
        gManager->shutdown();
    }
}

int main(int argc, char** argv) {
    android::base::InitLogging(argv, android::base::LogdLogger());
    LOG(INFO) << "GammaPad daemon starting";

    struct sigaction sa = {};
    sa.sa_handler = signalHandler;
    sigaction(SIGTERM, &sa, nullptr);
    sigaction(SIGINT, &sa, nullptr);

    gammapad::GamepadManager manager;
    gManager = &manager;

    if (!manager.init()) {
        LOG(ERROR) << "Failed to initialize GamepadManager";
        return 1;
    }

    manager.run();

    LOG(INFO) << "GammaPad daemon exiting";
    return 0;
}
