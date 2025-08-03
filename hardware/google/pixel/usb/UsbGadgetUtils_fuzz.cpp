#include <stddef.h>
#include <stdint.h>

#include "fuzzer/FuzzedDataProvider.h"
#include "hidl/include/pixelusb/UsbGadgetCommon.h"

using ::android::hardware::google::pixel::usb::setVidPid;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider fdp(data, size);

    std::string vid_str = fdp.ConsumeRandomLengthString(size);
    std::string pid_str = fdp.ConsumeRemainingBytesAsString();
    setVidPid(vid_str.c_str(), pid_str.c_str());
    return 0;
}
