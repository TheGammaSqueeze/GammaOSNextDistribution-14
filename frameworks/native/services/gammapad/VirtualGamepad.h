#pragma once

#include <linux/input.h>
#include <linux/uinput.h>
#include <set>
#include <string>
#include <vector>

namespace gammapad {

class VirtualGamepad {
public:
    struct AxisSetup {
        int code;
        int min;
        int max;
        int fuzz;
        int flat;
    };

    VirtualGamepad();
    ~VirtualGamepad();

    // Create the virtual uinput device with default button/axis sets. Returns true on success.
    bool create();

    // Create with explicit button and axis sets (default ranges).
    bool create(const std::set<int>& buttons, const std::set<int>& axes);

    // Create with explicit button set, per-axis setup, and custom device identity.
    bool create(const std::set<int>& buttons,
                const std::vector<AxisSetup>& axes,
                const std::string& name,
                uint16_t vendor, uint16_t product);

    // Destroy the virtual device.
    void destroy();

    // Write an input event to the virtual device.
    bool writeEvent(const struct input_event& ev);

    // Write a SYN_REPORT.
    bool writeSyn();

    // Get the uinput fd (for epoll monitoring of FF events).
    int fd() const { return mFd; }

    bool isValid() const { return mFd >= 0; }

    // Get the button and axis sets this device was created with.
    const std::set<int>& getButtons() const { return mButtons; }
    const std::set<int>& getAxes() const { return mAxes; }

private:
    bool setupButtons(const std::set<int>& buttons);
    bool setupAxes(const std::set<int>& axes);
    bool setupAxes(const std::vector<AxisSetup>& axes);
    bool setupForceFeedback();
    bool createDevice(const std::set<int>& buttons, const std::set<int>& axes);
    bool createDevice(const std::set<int>& buttons,
                      const std::vector<AxisSetup>& axes,
                      const std::string& name,
                      uint16_t vendor, uint16_t product);

    int mFd;
    std::set<int> mButtons;
    std::set<int> mAxes;
};

} // namespace gammapad
