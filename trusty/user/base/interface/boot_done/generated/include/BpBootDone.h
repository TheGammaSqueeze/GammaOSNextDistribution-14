#pragma once
#include <IBootDone.h>
class BpBootDone : public IBootDone {
public:
    BpBootDone() : mChan() {}
    int set_boot_done() override;
    int connect(const char*, uint32_t);
    bool is_connected();
    void reset();

private:
#if !defined(__QL_TIPC__)
    ::android::base::unique_fd mChan;
#else
    ::tidl::handle mChan;
#endif  // #if !defined(__QL_TIPC__)
};
