#pragma once

#include "VirtualGamepad.h"
#include "InputTransformer.h"
#include "ForceFeedback.h"
#include "KeyLayoutParser.h"
#include "MouseMode.h"

#include <linux/input.h>
#include <set>
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include <utility>

namespace gammapad {

struct PhysicalDevice {
    int fd;
    std::string path;
    std::string name;
    uint16_t vendor = 0;
    uint16_t product = 0;
    bool hasFF;
    // Map virtual FF effect IDs -> physical effect IDs
    std::unordered_map<int, int> ffEffects;
    // Per-device axis info (scancode -> absinfo) for correct normalization
    std::unordered_map<int, AxisInfo> absInfo;
    // Per-device discovered axis scancodes
    std::set<int> discoveredAxes;
    // Per-device discovered key codes
    std::set<int> discoveredKeys;
    // Whether we unlinked the /dev node to hide it from games
    bool nodeHidden = false;
    // major/minor saved before unlink for mknod restore
    dev_t devNumber = 0;
    // File permissions saved before unlink
    mode_t devMode = 0660;
};

class GamepadManager {
public:
    GamepadManager();
    ~GamepadManager();

    // Initialize: scan devices, create virtual gamepad, set up epoll.
    bool init();

    // Run the main event loop. Blocks until shutdown.
    void run();

    // Signal shutdown.
    void shutdown();

    // Emergency restore of all hidden device nodes (async-signal-safe).
    void restoreAllHiddenNodes();

private:
    void loadConfig();
    void scanDevices();
    bool grabDevice(const std::string& path);
    void releaseDevice(int fd);
    void releaseAllDevices();

    void handleInputEvent(int fd);
    void handleInotifyEvent();
    void handleUinputEvent();
    void checkConfigChange();
    void checkForegroundApp();
    void drainMouseFlushEvents();

    bool shouldGrabDevice(const std::string& name);

    // Compute the set of button and axis codes required by the
    // current remap configuration (base set + remap targets + discovered).
    std::pair<std::set<int>, std::set<int>> computeRequiredCodes() const;

    // Recreate the virtual gamepad with new button/axis sets.
    bool recreateVirtualGamepad(const std::set<int>& buttons, const std::set<int>& axes);

    // Create or recreate the virtual gamepad using discovered device info.
    void createVirtualGamepadFromDiscovery();

    // Discover capabilities (axes, keys, absinfo) for a single physical device.
    // Only reads hardware state, does NOT apply .kl or role mappings.
    void discoverDeviceCapabilities(int fd, PhysicalDevice& dev);

    // Rebuild global maps (mAbsMap, mKeyMap, mAbsInfo, etc.) from all
    // currently grabbed devices, then apply .kl, collisions, and role mappings.
    void rebuildGlobalMaps();

    // Apply user-configured role overrides (e.g., persist.gammaos.gamepad.role_rx=9)
    // to mAbsMap after .kl parsing.
    void applyRoleMappings();

    int mEpollFd;
    int mInotifyFd;
    int mInotifyWd;
    bool mRunning;

    // Config
    bool mMerge;
    std::vector<std::string> mDeviceNames;
    int mConfigVersion;
    std::set<int> mBlacklistVpad;
    bool mHideSourceNodes;

    // Per-app profile: package -> {btnRemap, comboMap}
    struct PerAppProfile {
        std::string btnRemap;
        std::string comboMap;
    };
    std::unordered_map<std::string, PerAppProfile> mPerAppProfiles;
    std::string mCurrentFgPkg;  // currently active foreground package
    // All combo emit codes across all per-app profiles (for virtual device caps)
    std::set<int> mPerAppComboCodes;

    std::unique_ptr<VirtualGamepad> mVirtualGamepad;
    std::unique_ptr<InputTransformer> mTransformer;
    std::unique_ptr<ForceFeedback> mForceFeedback;
    std::unique_ptr<MouseMode> mMouseMode;

    // Collect all EV_KEY bits from a physical device
    void discoverDeviceKeys(int fd, std::set<int>& keys);

    // The union of all EV_KEY bits discovered from all grabbed physical devices
    std::set<int> mDiscoveredKeys;

    // .kl-based scancode->final code mappings (global, merged from all devices)
    std::unordered_map<int, int> mAbsMap;   // physical axis scancode -> mapped axis code
    std::unordered_map<int, int> mKeyMap;   // physical key scancode -> mapped key code

    // Physical axis info (scancode -> absinfo) for virtual device creation
    std::unordered_map<int, AxisInfo> mAbsInfo;

    // All discovered axis scancodes from physical devices
    std::set<int> mDiscoveredAxes;

    bool hideDeviceNode(PhysicalDevice& dev);
    bool restoreDeviceNode(PhysicalDevice& dev);
    void writeHiddenNodesState();
    void recoverHiddenNodes();

    // fd -> PhysicalDevice
    std::unordered_map<int, PhysicalDevice> mDevices;

    // Paths of devices released due to ENODEV that need a deferred rescan
    // (the replacement device may already exist but inotify IN_CREATE was
    // discarded because the old fd was still in mDevices at the time)
    std::vector<std::string> mPendingRescanPaths;
};

} // namespace gammapad
