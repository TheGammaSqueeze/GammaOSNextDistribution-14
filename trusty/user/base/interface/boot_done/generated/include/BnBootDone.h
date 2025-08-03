#pragma once
#include <IBootDone.h>
class BnBootDone : public ::tidl::Service, public IBootDone {
public:
    BnBootDone() = delete;

protected:
    BnBootDone(const char*,
               const ::tidl::Service::PortAcl* acl,
               uint32_t maximum_payload_size);
    virtual int get_instance(IBootDone*&, const struct uuid*);

private:
    static int on_connect(const ::tidl::Service::Port* port,
                          ::tidl::Handle chan,
                          const struct uuid* peer,
                          void** ctx_p);
    static void on_channel_cleanup(void* ctx);
    static int on_message(const ::tidl::Service::Port* port,
                          ::tidl::Handle chan,
                          void* ctx);
    static ::tidl::Service::Ops kOps;
};
