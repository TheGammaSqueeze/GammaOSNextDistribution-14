#pragma once
#include <lib/tidl/tidl.h>
class IBootDone {
public:
    virtual ~IBootDone() {}
    virtual int set_boot_done() = 0;
    static constexpr char PORT[] = "com.android.trusty.boot_done.tidl";
    enum : uint32_t {
        REQ_SHIFT = 1,
        RESP_BIT = 1,
        CMD_set_boot_done = (0 << REQ_SHIFT),
    };
    struct TIDL_PACKED_ATTR Request_set_boot_done {
        static constexpr uint32_t num_handles = 0U;
        void send_handles(::tidl::Handle*& ptr) {}
        void recv_handles(::tidl::Handle*& ptr) {}
    };
    virtual void destroy() {}
};
