
#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODE_COMPONENT_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODE_COMPONENT_H

#include <v4l2_codec2/components/DecodeComponent.h>

namespace android {
class V4L2DecodeComponent : public DecodeComponent {
public:
    static std::shared_ptr<C2Component> create(const std::string& name, c2_node_id_t id,
                                               std::shared_ptr<DecodeInterface> intfImpl,
                                               C2ComponentFactory::ComponentDeleter deleter);

    V4L2DecodeComponent(uint32_t debugStreamId, const std::string& name, c2_node_id_t id,
                        std::shared_ptr<DecodeInterface> intfImpl);

    ~V4L2DecodeComponent() override;

    void startTask(c2_status_t* status, ::base::WaitableEvent* done) override;

private:
    static std::atomic<int32_t> sConcurrentInstances;
    static std::atomic<uint32_t> sNextDebugStreamId;
};

};  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODE_COMPONENT_H