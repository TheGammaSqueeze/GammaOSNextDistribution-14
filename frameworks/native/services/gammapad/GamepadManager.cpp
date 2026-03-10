#define LOG_TAG "gammapad"

#include "GamepadManager.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>

#include <chrono>
#include <fstream>
#include <sstream>
#include <algorithm>

namespace gammapad {

// Proper bit-test for kernel bitmask arrays (works on both 32-bit and 64-bit)
#define BITS_PER_LONG (sizeof(unsigned long) * 8)
#define BIT_WORD(nr) ((nr) / BITS_PER_LONG)
#define BIT_MASK(nr) (1UL << ((nr) % BITS_PER_LONG))
#define test_bit(nr, addr) (((addr)[BIT_WORD(nr)] & BIT_MASK(nr)) != 0)

static constexpr const char* DEV_INPUT_PATH = "/dev/input";
static constexpr const char* HIDDEN_NODES_DIR = "/data/misc/gammapad";
static constexpr const char* HIDDEN_NODES_FILE = "/data/misc/gammapad/hidden_nodes";
static constexpr int MAX_EPOLL_EVENTS = 16;
static constexpr int CONFIG_CHECK_INTERVAL_MS = 1000;
static constexpr int HOTPLUG_SETTLE_MS = 100;

// epoll data tags to distinguish event sources
enum EpollTag : uint32_t {
    TAG_INOTIFY     = 0xFFFF0001,
    TAG_UINPUT      = 0xFFFF0002,
    TAG_MOUSE_TIMER = 0xFFFF0003,
    // Physical device fds use the fd value directly
};

// Xbox controller baseline button set.
// Additional buttons come from:
//  - mDiscoveredKeys (inherited from physical controller)
//  - remap targets (added dynamically, triggers virtual device recreation)
static const std::set<int> kDefaultButtons = {
    BTN_A, BTN_B, BTN_X, BTN_Y,
    BTN_TL, BTN_TR, BTN_TL2, BTN_TR2,
    BTN_SELECT, BTN_START, BTN_MODE,
    BTN_THUMBL, BTN_THUMBR,
};

// Default axis set — matches Xbox Wireless Controller (BT) layout:
// right stick on Z/RZ, triggers on GAS/BRAKE
static const std::set<int> kDefaultAxes = {
    ABS_X, ABS_Y, ABS_Z, ABS_RZ,
    ABS_GAS, ABS_BRAKE,
    ABS_HAT0X, ABS_HAT0Y,
};

GamepadManager::GamepadManager()
    : mEpollFd(-1),
      mInotifyFd(-1),
      mInotifyWd(-1),
      mRunning(false),
      mMerge(true),
      mConfigVersion(0),
      mHideSourceNodes(true) {
}

GamepadManager::~GamepadManager() {
    releaseAllDevices();
    if (mInotifyFd >= 0) {
        if (mInotifyWd >= 0) {
            inotify_rm_watch(mInotifyFd, mInotifyWd);
        }
        close(mInotifyFd);
    }
    if (mEpollFd >= 0) {
        close(mEpollFd);
    }
}

bool GamepadManager::init() {
    mTransformer = std::make_unique<InputTransformer>();
    mForceFeedback = std::make_unique<ForceFeedback>();
    mVirtualGamepad = std::make_unique<VirtualGamepad>();
    mMouseMode = std::make_unique<MouseMode>();

    loadConfig();

    // Create epoll
    mEpollFd = epoll_create1(0);
    if (mEpollFd < 0) {
        LOG(ERROR) << "epoll_create1 failed: " << strerror(errno);
        return false;
    }

    // Create initial virtual gamepad with default codes
    // (will be recreated after device discovery with proper axis ranges)
    auto [reqButtons, reqAxes] = computeRequiredCodes();
    if (!mVirtualGamepad->create(reqButtons, reqAxes)) {
        LOG(ERROR) << "Failed to create virtual gamepad";
        return false;
    }

    // Monitor uinput fd for FF events
    struct epoll_event ev = {};
    ev.events = EPOLLIN;
    ev.data.u32 = TAG_UINPUT;
    if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mVirtualGamepad->fd(), &ev) < 0) {
        LOG(ERROR) << "Failed to add uinput to epoll: " << strerror(errno);
        return false;
    }

    // Initialize mouse mode (creates timerfd)
    int mouseTimerFd = mMouseMode->init();
    if (mouseTimerFd >= 0) {
        ev = {};
        ev.events = EPOLLIN;
        ev.data.u32 = TAG_MOUSE_TIMER;
        if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mouseTimerFd, &ev) < 0) {
            LOG(ERROR) << "Failed to add mouse timer to epoll: " << strerror(errno);
        }
    }

    // Wire up mouse mode toast callback through the vibration bridge
    mMouseMode->setToastCallback([this](const std::string& msg) {
        mForceFeedback->sendToast(msg);
    });

    // Set up inotify for hotplug
    mInotifyFd = inotify_init1(IN_NONBLOCK);
    if (mInotifyFd < 0) {
        LOG(ERROR) << "inotify_init1 failed: " << strerror(errno);
        return false;
    }

    mInotifyWd = inotify_add_watch(mInotifyFd, DEV_INPUT_PATH,
                                    IN_CREATE | IN_DELETE);
    if (mInotifyWd < 0) {
        LOG(ERROR) << "inotify_add_watch failed: " << strerror(errno);
        return false;
    }

    ev = {};
    ev.events = EPOLLIN;
    ev.data.u32 = TAG_INOTIFY;
    if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mInotifyFd, &ev) < 0) {
        LOG(ERROR) << "Failed to add inotify to epoll: " << strerror(errno);
        return false;
    }

    // Recover any hidden nodes from a previous crash
    recoverHiddenNodes();

    // Scan and grab existing devices
    scanDevices();

    // After all devices discovered, rebuild global maps and recreate virtual device
    if (!mDevices.empty()) {
        rebuildGlobalMaps();
        createVirtualGamepadFromDiscovery();
    }

    // Connect to vibration bridge
    mForceFeedback->connectBridge();

    LOG(INFO) << "GamepadManager initialized with " << mDevices.size() << " devices"
              << " absMap=" << mAbsMap.size() << " keyMap=" << mKeyMap.size();
    return true;
}

void GamepadManager::loadConfig() {
    using android::base::GetProperty;
    using android::base::GetIntProperty;

    mMerge = GetIntProperty("persist.gammaos.gamepad.merge", 1) != 0;
    mConfigVersion = GetIntProperty("persist.gammaos.gamepad.config_version", 0);
    mHideSourceNodes = GetIntProperty("persist.gammaos.gamepad.hide_source", 1) != 0;

    // Parse device names (semicolon-separated)
    mDeviceNames.clear();
    std::string devNames = GetProperty("persist.gammaos.gamepad.devices", "");

    // Support continuation properties for long values
    for (int i = 1; !devNames.empty() || i == 1; i++) {
        if (i > 1) {
            std::string contKey = "persist.gammaos.gamepad.devices_" + std::to_string(i);
            std::string cont = GetProperty(contKey, "");
            if (cont.empty()) break;
            devNames += cont;
        }

        std::istringstream ss(devNames);
        std::string name;
        while (std::getline(ss, name, ';')) {
            if (!name.empty()) {
                mDeviceNames.push_back(name);
            }
        }

        if (i == 1 && devNames.empty()) break;
        devNames.clear();
    }

    // Parse virtual pad button blacklist: comma-separated hex scan codes
    mBlacklistVpad.clear();
    std::string blacklistVpadStr = GetProperty("persist.gammaos.gamepad.blacklist_vpad", "");
    if (!blacklistVpadStr.empty()) {
        std::istringstream bvs(blacklistVpadStr);
        std::string tok;
        while (std::getline(bvs, tok, ',')) {
            if (!tok.empty()) {
                int code = (int)strtol(tok.c_str(), nullptr, 0);
                if (code > 0) mBlacklistVpad.insert(code);
            }
        }
    }

    mTransformer->loadConfig();
    mForceFeedback->loadConfig();
    if (mMouseMode) mMouseMode->loadConfig();

    // Load per-app profiles
    mPerAppProfiles.clear();
    mPerAppComboCodes.clear();
    int paCount = GetIntProperty("persist.gammaos.gamepad.pa_count", 0);
    for (int i = 0; i < paCount && i < 20; i++) {
        std::string prefix = "persist.gammaos.gamepad.pa" + std::to_string(i);
        std::string pkg = GetProperty(prefix + "_pkg", "");
        if (pkg.empty()) continue;

        PerAppProfile profile;
        profile.btnRemap = GetProperty(prefix + "_btn", "");
        profile.comboMap = GetProperty(prefix + "_combo", "");
        mPerAppProfiles[pkg] = profile;

        // Collect combo emit codes for virtual device creation
        if (!profile.comboMap.empty()) {
            std::istringstream ss(profile.comboMap);
            std::string entry;
            while (std::getline(ss, entry, ',')) {
                size_t eq = entry.find('=');
                if (eq != std::string::npos) {
                    int emit = std::atoi(entry.substr(eq + 1).c_str());
                    if (emit > 0) mPerAppComboCodes.insert(emit);
                }
            }
        }

        LOG(INFO) << "Per-app profile: " << pkg
                  << " btn=[" << profile.btnRemap << "]"
                  << " combo=[" << profile.comboMap << "]";
    }

    // Apply per-app overrides if a foreground app is already tracked
    if (!mCurrentFgPkg.empty()) {
        auto it = mPerAppProfiles.find(mCurrentFgPkg);
        if (it != mPerAppProfiles.end()) {
            mTransformer->applyPerAppOverrides(it->second.btnRemap,
                                                it->second.comboMap);
        }
    }

    LOG(INFO) << "Config loaded: merge=" << mMerge
              << " devices=" << mDeviceNames.size()
              << " blacklistVpad=" << mBlacklistVpad.size()
              << " perAppProfiles=" << mPerAppProfiles.size()
              << " version=" << mConfigVersion;
}

void GamepadManager::run() {
    mRunning = true;
    struct epoll_event events[MAX_EPOLL_EVENTS];
    auto lastConfigCheck = std::chrono::steady_clock::now();

    while (mRunning) {
        int nfds = epoll_wait(mEpollFd, events, MAX_EPOLL_EVENTS,
                              CONFIG_CHECK_INTERVAL_MS);

        if (nfds < 0) {
            if (errno == EINTR) continue;
            LOG(ERROR) << "epoll_wait failed: " << strerror(errno);
            break;
        }

        for (int i = 0; i < nfds; i++) {
            uint32_t tag = events[i].data.u32;

            if (tag == TAG_INOTIFY) {
                handleInotifyEvent();
            } else if (tag == TAG_UINPUT) {
                handleUinputEvent();
            } else if (tag == TAG_MOUSE_TIMER) {
                if (mMouseMode) mMouseMode->tick();
            } else {
                // Physical device input — tag holds the fd
                handleInputEvent(static_cast<int>(tag));
            }
        }

        // Re-grab devices that were released due to ENODEV (firmware re-enumeration).
        // A replacement device at the same path may already exist but its inotify
        // IN_CREATE was skipped because the old fd was still in mDevices.
        if (!mPendingRescanPaths.empty()) {
            bool rescanGrabbed = false;
            for (const auto& path : mPendingRescanPaths) {
                // Check if not already grabbed (could have been re-grabbed by inotify)
                bool alreadyGrabbed = false;
                for (const auto& [fd, dev] : mDevices) {
                    if (dev.path == path) { alreadyGrabbed = true; break; }
                }
                if (!alreadyGrabbed) {
                    usleep(HOTPLUG_SETTLE_MS * 1000);
                    if (grabDevice(path)) {
                        rescanGrabbed = true;
                    }
                }
            }
            mPendingRescanPaths.clear();
            if (rescanGrabbed) {
                rebuildGlobalMaps();
                createVirtualGamepadFromDiscovery();
            }
        }

        // Check for config changes periodically even when events are flowing
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                now - lastConfigCheck).count();
        if (elapsed >= CONFIG_CHECK_INTERVAL_MS) {
            checkConfigChange();
            checkForegroundApp();
            // Check for external mouse mode toggle (QS tile) and combo timeout
            if (mMouseMode) {
                if (mMouseMode->checkExternalToggle()) {
                    drainMouseFlushEvents();
                }
                mMouseMode->checkComboTimeout();
            }
            lastConfigCheck = now;
        }
    }

    releaseAllDevices();
}

void GamepadManager::shutdown() {
    mRunning = false;
}

void GamepadManager::restoreAllHiddenNodes() {
    // Async-signal-safe: only uses mknod, chown, unlink — no malloc, no logging
    for (auto& [fd, dev] : mDevices) {
        if (dev.nodeHidden) {
            mknod(dev.path.c_str(), S_IFCHR | (dev.devMode & 07777), dev.devNumber);
            chown(dev.path.c_str(), 1000, 1004);
            dev.nodeHidden = false;
        }
    }
    // Remove state file since we restored everything
    unlink(HIDDEN_NODES_FILE);
}

void GamepadManager::scanDevices() {
    DIR* dir = opendir(DEV_INPUT_PATH);
    if (!dir) {
        LOG(ERROR) << "Failed to open " << DEV_INPUT_PATH;
        return;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;

        std::string path = std::string(DEV_INPUT_PATH) + "/" + entry->d_name;
        grabDevice(path);
    }

    closedir(dir);
}

bool GamepadManager::shouldGrabDevice(const std::string& name) {
    // If no device filter configured, grab all gamepads
    if (mDeviceNames.empty()) return true;

    for (const auto& pattern : mDeviceNames) {
        if (name.find(pattern) != std::string::npos) {
            return true;
        }
    }
    return false;
}

bool GamepadManager::grabDevice(const std::string& path) {
    int fd = open(path.c_str(), O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        LOG(WARNING) << "Failed to open " << path << ": " << strerror(errno);
        return false;
    }

    // Get device name
    char name[256] = {};
    if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name) < 0) {
        LOG(WARNING) << "EVIOCGNAME failed on " << path << ": " << strerror(errno);
        close(fd);
        return false;
    }

    LOG(INFO) << "Checking device: " << path << " (" << name << ")";

    // Skip our own virtual devices by checking phys identifier prefix
    char phys[256] = {};
    if (ioctl(fd, EVIOCGPHYS(sizeof(phys) - 1), phys) >= 0) {
        if (strncmp(phys, "gammapad-", 9) == 0) {
            LOG(INFO) << "Skipping own virtual device: " << path
                      << " (phys=" << phys << ")";
            close(fd);
            return false;
        }
    }

    // Check if this is a gamepad/joystick
    unsigned long evBits[(EV_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(0, sizeof(evBits)), evBits) < 0) {
        LOG(WARNING) << "EVIOCGBIT(0) failed on " << path;
        close(fd);
        return false;
    }

    bool hasAbs = test_bit(EV_ABS, evBits);
    bool hasKey = test_bit(EV_KEY, evBits);

    if (!hasAbs || !hasKey) {
        LOG(INFO) << "Skipping " << name << ": hasAbs=" << hasAbs << " hasKey=" << hasKey;
        close(fd);
        return false;
    }

    // Check for joystick-like axes (ABS_X or ABS_HAT0X)
    unsigned long absBits[(ABS_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits) < 0) {
        close(fd);
        return false;
    }

    bool hasStick = test_bit(ABS_X, absBits) || test_bit(ABS_HAT0X, absBits);
    if (!hasStick) {
        LOG(INFO) << "Skipping " << name << ": no stick axes";
        close(fd);
        return false;
    }

    // Check for gamepad buttons (BTN_A or BTN_GAMEPAD range)
    unsigned long keyBits[(KEY_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBits)), keyBits) < 0) {
        close(fd);
        return false;
    }

    bool hasGamepadBtn = test_bit(BTN_A, keyBits) || test_bit(BTN_GAMEPAD, keyBits);
    if (!hasGamepadBtn) {
        LOG(INFO) << "Skipping " << name << ": no gamepad buttons (BTN_A/BTN_GAMEPAD)";
        close(fd);
        return false;
    }

    // Check name filter
    std::string nameStr(name);
    if (!shouldGrabDevice(nameStr)) {
        LOG(INFO) << "Skipping " << name << ": not in device filter";
        close(fd);
        return false;
    }

    // Get vendor/product ID
    struct input_id devId = {};
    uint16_t vendor = 0, product = 0;
    if (ioctl(fd, EVIOCGID, &devId) == 0) {
        vendor = devId.vendor;
        product = devId.product;
        LOG(INFO) << "Device ID: vendor=0x" << std::hex << vendor
                  << " product=0x" << product << std::dec;
    }

    // Check FF capability
    bool hasFF = false;
    unsigned long ffBits[(FF_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_FF, sizeof(ffBits)), ffBits) >= 0) {
        hasFF = test_bit(FF_RUMBLE, ffBits);
    }

    // Grab the device (hide from other consumers)
    if (ioctl(fd, EVIOCGRAB, 1) < 0) {
        LOG(WARNING) << "Failed to grab " << path << " (" << name << "): "
                     << strerror(errno);
        close(fd);
        return false;
    }

    // Add to epoll — use fd as the tag
    struct epoll_event ev = {};
    ev.events = EPOLLIN;
    ev.data.u32 = static_cast<uint32_t>(fd);
    if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, fd, &ev) < 0) {
        LOG(ERROR) << "epoll_ctl ADD failed for " << path;
        ioctl(fd, EVIOCGRAB, 0);
        close(fd);
        return false;
    }

    PhysicalDevice dev;
    dev.fd = fd;
    dev.path = path;
    dev.name = nameStr;
    dev.vendor = vendor;
    dev.product = product;
    dev.hasFF = hasFF;

    // Discover this device's capabilities (keys, axes, absinfo)
    discoverDeviceCapabilities(fd, dev);

    mDevices[fd] = std::move(dev);

    // Hide the physical device node so games can't see it
    if (mHideSourceNodes) {
        hideDeviceNode(mDevices[fd]);
    }

    LOG(INFO) << "Grabbed device: " << path << " (" << name << ")"
              << (hasFF ? " [FF]" : "")
              << " keys=" << mDevices[fd].discoveredKeys.size()
              << " axes=" << mDevices[fd].discoveredAxes.size();

    return true;
}

void GamepadManager::discoverDeviceCapabilities(int fd, PhysicalDevice& dev) {
    // Discover all EV_ABS capabilities with absinfo
    unsigned long absBits[(ABS_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits) >= 0) {
        for (int code = 0; code <= ABS_MAX; code++) {
            if (!test_bit(code, absBits)) continue;

            dev.discoveredAxes.insert(code);

            struct input_absinfo info = {};
            if (ioctl(fd, EVIOCGABS(code), &info) == 0) {
                AxisInfo ai;
                ai.min = info.minimum;
                ai.max = info.maximum;
                ai.fuzz = info.fuzz;
                ai.flat = info.flat;
                dev.absInfo[code] = ai;
                LOG(INFO) << "  Axis sc=" << code << " min=" << ai.min
                          << " max=" << ai.max << " fuzz=" << ai.fuzz
                          << " flat=" << ai.flat;
            }
        }
    }

    // Discover all EV_KEY codes
    unsigned long keyBits[(KEY_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBits)), keyBits) >= 0) {
        for (int i = 0; i <= KEY_MAX; i++) {
            if (test_bit(i, keyBits)) {
                dev.discoveredKeys.insert(i);
            }
        }
    }

    LOG(INFO) << "Device capabilities: " << dev.discoveredAxes.size() << " axes, "
              << dev.discoveredKeys.size() << " keys";
}

void GamepadManager::rebuildGlobalMaps() {
    // Clear all global maps
    mAbsMap.clear();
    mKeyMap.clear();
    mAbsInfo.clear();
    mDiscoveredAxes.clear();
    mDiscoveredKeys.clear();

    // Merge capabilities from all grabbed devices
    for (auto& [fd, dev] : mDevices) {
        // Merge discovered axes and keys
        mDiscoveredAxes.insert(dev.discoveredAxes.begin(), dev.discoveredAxes.end());
        mDiscoveredKeys.insert(dev.discoveredKeys.begin(), dev.discoveredKeys.end());

        // Set identity mappings for all discovered axes/keys
        for (int code : dev.discoveredAxes) {
            if (mAbsMap.find(code) == mAbsMap.end()) {
                mAbsMap[code] = code;
            }
        }
        for (int code : dev.discoveredKeys) {
            if (mKeyMap.find(code) == mKeyMap.end()) {
                mKeyMap[code] = code;
            }
        }

        // Merge absinfo (last device wins for overlapping scancodes)
        for (const auto& [code, info] : dev.absInfo) {
            mAbsInfo[code] = info;
        }
    }

    // Parse .kl files for each device (using their VID/PID)
    // Use the first device's VID/PID for .kl lookup (devices in merge mode
    // should generally be the same type)
    if (!mDevices.empty()) {
        auto& firstDev = mDevices.begin()->second;
        KeyLayoutParser::parse(firstDev.vendor, firstDev.product, mAbsMap, mKeyMap);
    }

    // Prune absMap entries for scancodes not present on any physical device.
    // The .kl file may add mappings for standard scancodes (e.g., ABS_RX, ABS_RY)
    // that the physical device doesn't actually have.
    {
        std::vector<int> toErase;
        for (const auto& [sc, fc] : mAbsMap) {
            if (!mDiscoveredAxes.count(sc)) {
                toErase.push_back(sc);
            }
        }
        for (int sc : toErase) {
            mAbsMap.erase(sc);
        }
    }

    // Apply heuristic axis remapping for controllers without proper .kl mappings.
    // Skip the heuristic when the virtual device PID matches the physical
    // controller's native layout (right stick on Z/RZ, triggers on GAS/BRAKE).
    // PID 0x0b13 (Xbox Wireless Controller BT) already uses this layout natively,
    // so remapping would put axes in the wrong places for apps expecting that PID.
    {
        int presetPid = android::base::GetIntProperty(
                "persist.gammaos.gamepad.device_pid", 0x0b13);
        if (presetPid == 0x02fd) {
            // PID 0x02fd expects standard layout (RX/RY=right stick, Z/RZ=triggers),
            // so apply heuristic to remap from native to standard
            KeyLayoutParser::applyHeuristicMapping(mAbsMap, mAbsInfo);
        } else {
            LOG(INFO) << "Skipping heuristic: preset PID 0x"
                      << std::hex << presetPid << std::dec
                      << " uses native axis layout";
        }
    }

    // Resolve axis collisions
    KeyLayoutParser::resolveAxisCollisions(mAbsMap, mAbsInfo);

    // Apply user-configured role overrides
    applyRoleMappings();

    // Apply user axis remaps at the mAbsMap level (not in transform pipeline).
    // This ensures remaps work regardless of which physical scancode the axis
    // arrives on, since mAbsMap already resolved .kl and role mappings.
    const auto& axisRemaps = mTransformer->getAxisRemaps();
    if (!axisRemaps.empty()) {
        // Build completed permutation (auto-swap like roles)
        std::unordered_map<int, int> remapPerm = axisRemaps;
        for (const auto& [from, to] : axisRemaps) {
            if (remapPerm.find(to) == remapPerm.end()) {
                remapPerm[to] = from;
            }
        }
        for (auto& [sc, mappedCode] : mAbsMap) {
            auto it = remapPerm.find(mappedCode);
            if (it != remapPerm.end()) {
                LOG(INFO) << "Axis remap applied: sc=" << sc
                          << " " << mappedCode << " -> " << it->second;
                mappedCode = it->second;
            }
        }
    }

    // Push the merged maps to the transformer
    mTransformer->setDeviceMaps(mAbsMap, mKeyMap);

    LOG(INFO) << "Global maps rebuilt: "
              << mDiscoveredAxes.size() << " axes, "
              << mDiscoveredKeys.size() << " keys, "
              << mAbsMap.size() << " absMap, "
              << mKeyMap.size() << " keyMap";
}

void GamepadManager::applyRoleMappings() {
    using android::base::GetProperty;

    // Each role property stores the current mapped code that should assume a target role.
    // E.g., role_lt=9 means "the axis currently mapped to code 9 should output as ABS_Z".
    static const struct {
        const char* prop;
        int targetCode;
    } roles[] = {
        { "persist.gammaos.gamepad.role_lx", ABS_X },
        { "persist.gammaos.gamepad.role_ly", ABS_Y },
        { "persist.gammaos.gamepad.role_rx", ABS_RX },
        { "persist.gammaos.gamepad.role_ry", ABS_RY },
        { "persist.gammaos.gamepad.role_lt", ABS_Z },
        { "persist.gammaos.gamepad.role_rt", ABS_RZ },
    };

    // Build permutation: currentCode → desiredCode
    std::unordered_map<int, int> perm;
    for (const auto& role : roles) {
        std::string val = GetProperty(role.prop, "");
        if (val.empty()) continue;
        int sourceCode = std::atoi(val.c_str());
        if (sourceCode < 0 || sourceCode > ABS_MAX) continue;
        if (sourceCode == role.targetCode) continue;
        perm[sourceCode] = role.targetCode;
        LOG(INFO) << "Role: " << role.prop << "=" << sourceCode
                  << " -> " << role.targetCode;
    }

    if (perm.empty()) return;

    // Auto-complete swaps: if A→B exists but B has no mapping, add B→A
    std::unordered_map<int, int> completed = perm;
    for (const auto& [from, to] : perm) {
        if (completed.find(to) == completed.end()) {
            completed[to] = from;
            LOG(INFO) << "Role auto-swap: " << to << " -> " << from;
        }
    }

    // Apply permutation atomically to all mAbsMap values
    for (auto& [sc, mappedCode] : mAbsMap) {
        auto it = completed.find(mappedCode);
        if (it != completed.end()) {
            LOG(INFO) << "Role applied: sc=" << sc
                      << " " << mappedCode << " -> " << it->second;
            mappedCode = it->second;
        }
    }
}

void GamepadManager::createVirtualGamepadFromDiscovery() {
    using android::base::GetProperty;
    using android::base::GetIntProperty;

    // Build axis setup list from discovered+mapped axes
    std::set<int> finalAxes;
    std::vector<VirtualGamepad::AxisSetup> axisSetups;

    // Collect all final axis codes from the absMap
    for (const auto& [sc, finalCode] : mAbsMap) {
        if (finalCode < 0 || finalCode > ABS_MAX) continue;
        if (!mDiscoveredAxes.count(sc)) continue;
        if (finalAxes.count(finalCode)) continue; // already added

        // Skip HAT1X/HAT1Y — these are non-standard axes from some source
        // controllers that real Xbox controllers don't have.
        // Forwarding them confuses games that enumerate all axes.
        if (finalCode == ABS_HAT1X || finalCode == ABS_HAT1Y) continue;

        finalAxes.insert(finalCode);

        VirtualGamepad::AxisSetup setup;
        setup.code = finalCode;

        // Use standardized ranges for the virtual device since the daemon
        // normalizes all raw values to signed 16-bit range before processing.
        // This ensures calibration and Android MotionEvent work consistently.
        if (finalCode == ABS_HAT0X || finalCode == ABS_HAT0Y ||
            finalCode == ABS_HAT1X || finalCode == ABS_HAT1Y) {
            setup.min = -1; setup.max = 1;
            setup.fuzz = 0; setup.flat = 0;
        } else {
            setup.min = -32768; setup.max = 32767;
            setup.fuzz = 16; setup.flat = 128;
        }

        // Detect trigger axes: set virtual device range to 0..32767 for triggers.
        // Two cases:
        //   1. Bipolar trigger: .kl remapped a bipolar axis to trigger code
        //      (e.g., Xbox 360: sc=2→ABS_BRAKE, bipolar source)
        //   2. Unipolar trigger: heuristic/kl remapped a unipolar axis to trigger code
        //      (e.g., sc=9(0..255)→ABS_RZ)
        // Identity-mapped axes on Z/RZ with large unsigned range (>4096) are STICKS,
        // not triggers (e.g., Xbox BT right stick on Z/RZ with 0..65535).
        if (finalCode == ABS_Z || finalCode == ABS_RZ ||
            finalCode == ABS_GAS || finalCode == ABS_BRAKE) {
            for (const auto& [sc2, fc2] : mAbsMap) {
                if (fc2 == finalCode && mDiscoveredAxes.count(sc2)) {
                    auto infoIt = mAbsInfo.find(sc2);
                    if (infoIt != mAbsInfo.end()) {
                        bool wasRemapped = (sc2 != finalCode);
                        int pRange = infoIt->second.max - infoIt->second.min;
                        LOG(INFO) << "Trigger check: finalCode=" << finalCode
                                  << " sc=" << sc2 << " pMin=" << infoIt->second.min
                                  << " pRange=" << pRange << " remapped=" << wasRemapped;
                        if (wasRemapped && infoIt->second.min < 0) {
                            // Bipolar source remapped to trigger code
                            setup.min = 0; setup.max = 32767;
                            setup.fuzz = 0; setup.flat = 0;
                            LOG(INFO) << "  -> bipolar trigger range 0..32767";
                            break;
                        } else if (infoIt->second.min >= 0 && pRange > 2) {
                            if (!wasRemapped && pRange > 4096) {
                                // Large unsigned range identity-mapped = stick axis
                                LOG(INFO) << "  -> unsigned stick (range " << pRange << "), keeping as stick";
                            } else {
                                // Unipolar trigger (identity with small range, or remapped)
                                setup.min = 0; setup.max = 32767;
                                setup.fuzz = 0; setup.flat = 0;
                                LOG(INFO) << "  -> unipolar trigger range 0..32767";
                            }
                            break;
                        }
                        // else: identity-mapped bipolar = stick, keep default range
                    }
                }
            }
        }

        axisSetups.push_back(setup);
    }

    // Ensure default axes are present even if not discovered
    for (int code : kDefaultAxes) {
        if (finalAxes.count(code)) continue;
        finalAxes.insert(code);

        VirtualGamepad::AxisSetup setup;
        setup.code = code;
        if (code == ABS_HAT0X || code == ABS_HAT0Y) {
            setup.min = -1; setup.max = 1;
            setup.fuzz = 0; setup.flat = 0;
        } else if (code == ABS_GAS || code == ABS_BRAKE) {
            setup.min = 0; setup.max = 32767;
            setup.fuzz = 0; setup.flat = 0;
        } else {
            // Stick axes (X, Y, Z, RZ)
            setup.min = -32768; setup.max = 32767;
            setup.fuzz = 16; setup.flat = 128;
        }
        axisSetups.push_back(setup);
    }

    // Add remap target axes
    for (const auto& [from, to] : mTransformer->getAxisRemaps()) {
        if (finalAxes.count(to)) continue;
        finalAxes.insert(to);

        VirtualGamepad::AxisSetup setup;
        setup.code = to;
        setup.min = -32768; setup.max = 32767;
        setup.fuzz = 16; setup.flat = 128;
        axisSetups.push_back(setup);
    }

    // Also add axis-to-button target button codes
    for (int btn : mTransformer->getAxisButtonCodes()) {
        mDiscoveredKeys.insert(btn);
    }

    // Build button set
    std::set<int> buttons(kDefaultButtons);
    buttons.insert(mDiscoveredKeys.begin(), mDiscoveredKeys.end());
    for (const auto& [from, to] : mTransformer->getButtonRemaps()) {
        buttons.insert(to);
    }

    // Remove blacklisted buttons from virtual pad
    for (int code : mBlacklistVpad) {
        buttons.erase(code);
    }

    // Read custom device identity from properties
    std::string deviceName = GetProperty("persist.gammaos.gamepad.device_name",
                                         "Xbox Wireless Controller");
    int vid = GetIntProperty("persist.gammaos.gamepad.device_vid", 0x045e);
    int pid = GetIntProperty("persist.gammaos.gamepad.device_pid", 0x0b13);

    // Remove old uinput fd from epoll
    if (mVirtualGamepad->isValid()) {
        epoll_ctl(mEpollFd, EPOLL_CTL_DEL, mVirtualGamepad->fd(), nullptr);
        mVirtualGamepad->destroy();
    }

    // Create with discovered capabilities and custom identity
    if (!mVirtualGamepad->create(buttons, axisSetups, deviceName,
                                 static_cast<uint16_t>(vid),
                                 static_cast<uint16_t>(pid))) {
        LOG(ERROR) << "Failed to create virtual gamepad from discovery";
        // Fallback to basic create
        auto [reqButtons, reqAxes] = computeRequiredCodes();
        mVirtualGamepad->create(reqButtons, reqAxes);
    }

    // Re-add to epoll
    struct epoll_event ev = {};
    ev.events = EPOLLIN;
    ev.data.u32 = TAG_UINPUT;
    if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mVirtualGamepad->fd(), &ev) < 0) {
        LOG(ERROR) << "Failed to re-add uinput to epoll: " << strerror(errno);
    }

    LOG(INFO) << "Virtual gamepad created from discovery: "
              << buttons.size() << " buttons, "
              << axisSetups.size() << " axes, "
              << "name=\"" << deviceName << "\" "
              << "vid=0x" << std::hex << vid << " pid=0x" << pid << std::dec;
}

void GamepadManager::releaseDevice(int fd) {
    auto it = mDevices.find(fd);
    if (it == mDevices.end()) return;

    mForceFeedback->cancelDevice(fd);

    // Restore hidden device node before closing
    restoreDeviceNode(it->second);

    epoll_ctl(mEpollFd, EPOLL_CTL_DEL, fd, nullptr);
    ioctl(fd, EVIOCGRAB, 0);
    close(fd);

    LOG(INFO) << "Released device: " << it->second.path
              << " (" << it->second.name << ")";
    mDevices.erase(it);
}

void GamepadManager::releaseAllDevices() {
    std::vector<int> fds;
    for (auto& [fd, dev] : mDevices) {
        fds.push_back(fd);
    }
    for (int fd : fds) {
        releaseDevice(fd);
    }
}

void GamepadManager::handleInputEvent(int fd) {
    struct input_event ev;

    auto devIt = mDevices.find(fd);
    if (devIt == mDevices.end()) return;

    // Get per-device absinfo for normalization
    const auto& deviceAbsInfo = devIt->second.absInfo;

    bool mouseActive = mMouseMode && mMouseMode->isActive();

    while (true) {
        ssize_t n = read(fd, &ev, sizeof(ev));
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) break;
            if (errno == ENODEV) {
                // Device re-enumerated or unplugged.
                // Save path for deferred rescan — a replacement device may
                // already exist but its inotify IN_CREATE was discarded
                // because this fd was still in mDevices at the time.
                std::string path = devIt->second.path;
                devIt->second.nodeHidden = false;
                releaseDevice(fd);
                mPendingRescanPaths.push_back(std::move(path));
                return;
            }
            break;
        }
        if (n != sizeof(ev)) break;

        // SYN events: forward to virtual gamepad only when mouse mode is off
        if (ev.type == EV_SYN) {
            if (mMouseMode && mMouseMode->processEvent(ev)) {
                // Consumed by mouse mode (combo or active)
            } else {
                mVirtualGamepad->writeSyn();
            }
            // Flush any pending events from mouse mode toggle
            drainMouseFlushEvents();
            continue;
        }

        // Apply input transformation pipeline with per-device absinfo.
        // In mouse mode, skip analog/DPAD conversions so MouseMode gets
        // clean stick and DPAD values separately.
        mTransformer->setMouseMode(mouseActive);

        if (mTransformer->transform(ev, deviceAbsInfo)) {
            // Check if mouse mode wants this event
            if (mMouseMode && mMouseMode->processEvent(ev)) {
                // Event consumed by mouse mode
                drainMouseFlushEvents();
                // Update mouseActive flag in case mode just toggled
                mouseActive = mMouseMode->isActive();
                mTransformer->setMouseMode(mouseActive);
            } else {
                mVirtualGamepad->writeEvent(ev);
            }
        }

        // Write any extra events generated by axis-to-button
        const auto& extras = mTransformer->getExtraEvents();
        for (const auto& extra : extras) {
            if (mMouseMode && mMouseMode->isActive()) {
                mMouseMode->processEvent(extra);
            } else {
                mVirtualGamepad->writeEvent(extra);
            }
        }
        mTransformer->clearExtraEvents();
    }
}

void GamepadManager::drainMouseFlushEvents() {
    if (!mMouseMode) return;
    const auto& flushEvents = mMouseMode->getFlushEvents();
    for (const auto& fe : flushEvents) {
        mVirtualGamepad->writeEvent(fe);
    }
    mMouseMode->clearFlushEvents();
}

void GamepadManager::handleInotifyEvent() {
    char buf[4096] __attribute__((aligned(__alignof__(struct inotify_event))));
    ssize_t len = read(mInotifyFd, buf, sizeof(buf));
    if (len <= 0) return;

    bool needRebuild = false;

    for (char* ptr = buf; ptr < buf + len; ) {
        auto* event = reinterpret_cast<struct inotify_event*>(ptr);

        if (event->len > 0 && strncmp(event->name, "event", 5) == 0) {
            std::string path = std::string(DEV_INPUT_PATH) + "/" + event->name;

            if (event->mask & IN_CREATE) {
                // Skip if we already have this path grabbed (self-triggered by mknod restore)
                bool alreadyGrabbed = false;
                for (const auto& [fd, dev] : mDevices) {
                    if (dev.path == path) { alreadyGrabbed = true; break; }
                }
                if (!alreadyGrabbed) {
                    // Wait for device node to settle
                    usleep(HOTPLUG_SETTLE_MS * 1000);
                    if (grabDevice(path)) {
                        needRebuild = true;
                    }
                }
            } else if (event->mask & IN_DELETE) {
                // Find and release by path, but skip if we intentionally hid this node
                for (auto& [fd, dev] : mDevices) {
                    if (dev.path == path) {
                        if (!dev.nodeHidden) {
                            releaseDevice(fd);
                            needRebuild = true;
                        }
                        break;
                    }
                }
            }
        }

        ptr += sizeof(struct inotify_event) + event->len;
    }

    // Rebuild global maps and recreate virtual device when devices changed
    if (needRebuild) {
        if (mDevices.empty()) {
            // All devices gone — clear state
            mAbsMap.clear();
            mKeyMap.clear();
            mAbsInfo.clear();
            mDiscoveredAxes.clear();
            mDiscoveredKeys.clear();
        } else {
            // Rebuild from all current devices for consistency
            rebuildGlobalMaps();
            createVirtualGamepadFromDiscovery();
        }
    }
}

void GamepadManager::handleUinputEvent() {
    struct input_event ev;

    while (true) {
        ssize_t n = read(mVirtualGamepad->fd(), &ev, sizeof(ev));
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) break;
            LOG(ERROR) << "uinput read error: " << strerror(errno);
            break;
        }
        if (n != sizeof(ev)) break;

        if (ev.type == EV_UINPUT) {
            LOG(INFO) << "FF upload/erase: code=" << ev.code << " request_id=" << ev.value;
            if (ev.code == UI_FF_UPLOAD) {
                mForceFeedback->handleUpload(mVirtualGamepad->fd(), mDevices, ev.value);
            } else if (ev.code == UI_FF_ERASE) {
                mForceFeedback->handleErase(mVirtualGamepad->fd(), mDevices, ev.value);
            }
        } else if (ev.type == EV_FF) {
            LOG(INFO) << "FF play/stop: effect=" << ev.code << " value=" << ev.value;
            mForceFeedback->handlePlayStop(ev, mDevices);
        }
    }
}

void GamepadManager::checkConfigChange() {
    int version = android::base::GetIntProperty(
        "persist.gammaos.gamepad.config_version", 0);
    if (version != mConfigVersion) {
        LOG(INFO) << "Config version changed " << mConfigVersion
                  << " -> " << version << ", reloading";

        // Release all devices and reload config
        releaseAllDevices();

        loadConfig();

        scanDevices();

        // Rebuild and recreate with updated config + discovered capabilities
        if (!mDevices.empty()) {
            rebuildGlobalMaps();
            createVirtualGamepadFromDiscovery();
        } else {
            auto [reqButtons, reqAxes] = computeRequiredCodes();
            if (reqButtons != mVirtualGamepad->getButtons()
                    || reqAxes != mVirtualGamepad->getAxes()) {
                LOG(INFO) << "Code change detected, recreating virtual device";
                recreateVirtualGamepad(reqButtons, reqAxes);
            }
        }

        mForceFeedback->connectBridge();
    }
}

void GamepadManager::checkForegroundApp() {
    using android::base::GetProperty;

    std::string fgPkg = GetProperty("sys.gammaos.gamepad.fg_pkg", "");
    if (fgPkg == mCurrentFgPkg) return;

    LOG(INFO) << "Foreground app changed: " << mCurrentFgPkg << " -> " << fgPkg;
    mCurrentFgPkg = fgPkg;

    // Reload base config from properties (resets to global mappings)
    mTransformer->loadConfig();

    // Also refresh per-app profiles from properties (in case they were
    // changed via Settings since the last full loadConfig)
    mPerAppProfiles.clear();
    mPerAppComboCodes.clear();
    int paCount = android::base::GetIntProperty("persist.gammaos.gamepad.pa_count", 0);
    for (int i = 0; i < paCount && i < 20; i++) {
        std::string prefix = "persist.gammaos.gamepad.pa" + std::to_string(i);
        std::string pkg = GetProperty(prefix + "_pkg", "");
        if (pkg.empty()) continue;

        PerAppProfile profile;
        profile.btnRemap = GetProperty(prefix + "_btn", "");
        profile.comboMap = GetProperty(prefix + "_combo", "");
        mPerAppProfiles[pkg] = profile;

        if (!profile.comboMap.empty()) {
            std::istringstream ss(profile.comboMap);
            std::string entry;
            while (std::getline(ss, entry, ',')) {
                size_t eq = entry.find('=');
                if (eq != std::string::npos) {
                    int emit = std::atoi(entry.substr(eq + 1).c_str());
                    if (emit > 0) mPerAppComboCodes.insert(emit);
                }
            }
        }
    }

    // Apply per-app overrides if a profile exists for this package
    if (!fgPkg.empty()) {
        auto it = mPerAppProfiles.find(fgPkg);
        if (it != mPerAppProfiles.end()) {
            mTransformer->applyPerAppOverrides(it->second.btnRemap,
                                                it->second.comboMap);
            LOG(INFO) << "Applied per-app profile for: " << fgPkg
                      << " btn=[" << it->second.btnRemap << "]"
                      << " combo=[" << it->second.comboMap << "]";
        } else {
            LOG(INFO) << "No per-app profile for: " << fgPkg;
        }
    }
}

void GamepadManager::discoverDeviceKeys(int fd, std::set<int>& keys) {
    unsigned long keyBits[(KEY_MAX / BITS_PER_LONG) + 1] = {};
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keyBits)), keyBits) < 0) return;

    for (int i = 0; i <= KEY_MAX; i++) {
        if (test_bit(i, keyBits)) {
            keys.insert(i);
        }
    }
}

std::pair<std::set<int>, std::set<int>> GamepadManager::computeRequiredCodes() const {
    // Start with default sets
    std::set<int> buttons(kDefaultButtons);
    std::set<int> axes(kDefaultAxes);

    // Include all EV_KEY codes discovered from physical devices
    buttons.insert(mDiscoveredKeys.begin(), mDiscoveredKeys.end());

    // Add any remap target codes that aren't in the base set
    for (const auto& [from, to] : mTransformer->getButtonRemaps()) {
        buttons.insert(to);
    }
    for (const auto& [from, to] : mTransformer->getAxisRemaps()) {
        axes.insert(to);
    }

    // Add combo emit target buttons
    for (int btn : mTransformer->getComboEmitCodes()) {
        buttons.insert(btn);
    }

    // Add per-app combo emit buttons so the virtual device supports them
    buttons.insert(mPerAppComboCodes.begin(), mPerAppComboCodes.end());

    // Add discovered axis final codes
    for (const auto& [sc, finalCode] : mAbsMap) {
        if (finalCode >= 0 && finalCode <= ABS_MAX) {
            axes.insert(finalCode);
        }
    }

    return {buttons, axes};
}

bool GamepadManager::recreateVirtualGamepad(const std::set<int>& buttons,
                                             const std::set<int>& axes) {
    // Remove old uinput fd from epoll
    epoll_ctl(mEpollFd, EPOLL_CTL_DEL, mVirtualGamepad->fd(), nullptr);

    // Destroy old virtual device
    mVirtualGamepad->destroy();

    // Create new virtual device with updated codes
    if (!mVirtualGamepad->create(buttons, axes)) {
        LOG(ERROR) << "Failed to recreate virtual gamepad";
        return false;
    }

    // Re-add to epoll
    struct epoll_event ev = {};
    ev.events = EPOLLIN;
    ev.data.u32 = TAG_UINPUT;
    if (epoll_ctl(mEpollFd, EPOLL_CTL_ADD, mVirtualGamepad->fd(), &ev) < 0) {
        LOG(ERROR) << "Failed to re-add uinput to epoll: " << strerror(errno);
        return false;
    }

    LOG(INFO) << "Virtual gamepad recreated with " << buttons.size()
              << " buttons and " << axes.size() << " axes";
    return true;
}

bool GamepadManager::hideDeviceNode(PhysicalDevice& dev) {
    if (dev.nodeHidden) return true;

    struct stat st;
    if (stat(dev.path.c_str(), &st) < 0) {
        LOG(WARNING) << "hideDeviceNode: stat failed for " << dev.path
                     << ": " << strerror(errno);
        return false;
    }

    dev.devNumber = st.st_rdev;
    dev.devMode = st.st_mode;

    if (unlink(dev.path.c_str()) < 0) {
        LOG(WARNING) << "hideDeviceNode: unlink failed for " << dev.path
                     << ": " << strerror(errno);
        return false;
    }

    dev.nodeHidden = true;
    writeHiddenNodesState();

    LOG(INFO) << "Hidden device node: " << dev.path
              << " (major=" << major(dev.devNumber)
              << " minor=" << minor(dev.devNumber) << ")";
    return true;
}

bool GamepadManager::restoreDeviceNode(PhysicalDevice& dev) {
    if (!dev.nodeHidden) return true;

    if (mknod(dev.path.c_str(), S_IFCHR | (dev.devMode & 07777), dev.devNumber) < 0) {
        LOG(WARNING) << "restoreDeviceNode: mknod failed for " << dev.path
                     << ": " << strerror(errno);
        dev.nodeHidden = false;
        writeHiddenNodesState();
        return false;
    }

    // Restore ownership: system:input (1000:1004)
    chown(dev.path.c_str(), 1000, 1004);

    dev.nodeHidden = false;
    writeHiddenNodesState();

    LOG(INFO) << "Restored device node: " << dev.path;
    return true;
}

void GamepadManager::writeHiddenNodesState() {
    // Ensure directory exists
    mkdir(HIDDEN_NODES_DIR, 0755);

    std::ofstream ofs(HIDDEN_NODES_FILE, std::ios::trunc);
    if (!ofs) {
        LOG(WARNING) << "writeHiddenNodesState: failed to open " << HIDDEN_NODES_FILE;
        return;
    }

    for (const auto& [fd, dev] : mDevices) {
        if (dev.nodeHidden) {
            ofs << dev.path << " "
                << major(dev.devNumber) << " "
                << minor(dev.devNumber) << " "
                << std::oct << (dev.devMode & 07777) << std::dec << "\n";
        }
    }
}

void GamepadManager::recoverHiddenNodes() {
    std::ifstream ifs(HIDDEN_NODES_FILE);
    if (!ifs) return;  // No state file — nothing to recover

    LOG(INFO) << "Recovering hidden device nodes from previous session";

    std::string path;
    unsigned int maj, min;
    unsigned int mode;
    while (ifs >> path >> maj >> min >> std::oct >> mode >> std::dec) {
        // Check if the node is already present (ueventd may have recreated it)
        struct stat st;
        if (stat(path.c_str(), &st) == 0) {
            LOG(INFO) << "  Node already exists: " << path << " (skipping)";
            continue;
        }

        dev_t devNum = makedev(maj, min);
        if (mknod(path.c_str(), S_IFCHR | (mode & 07777), devNum) < 0) {
            LOG(WARNING) << "  Failed to restore " << path << ": " << strerror(errno);
        } else {
            chown(path.c_str(), 1000, 1004);
            LOG(INFO) << "  Restored " << path;
        }
    }

    // Clean up state file
    unlink(HIDDEN_NODES_FILE);
}

} // namespace gammapad
