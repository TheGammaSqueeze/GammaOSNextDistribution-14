/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <chrono>
#include <ctime>
#include <iomanip>
#include <fcntl.h>
#include <fstream>
#include <log/log.h>
#include <memory>
#include <sstream>
#include <sys/epoll.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <tuple>
#include <unistd.h>
#include <unordered_map>
#include <vector>

#include <drm/msm_drm.h>
#include <drm/msm_drm_pp.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include "histogram_collector.h"
#include "ringbuffer.h"

namespace {

class ManagedFd
{
public:
    static std::unique_ptr<ManagedFd> create(int fd) {
        if (fd < 0)
            return nullptr;
        return std::unique_ptr<ManagedFd>(new ManagedFd(fd));
    }

    ~ManagedFd() {
        close(drmfd_);
    }

    operator int() const {
        return drmfd_;
    }

private:
    ManagedFd(ManagedFd const&) = delete;
    ManagedFd& operator=(ManagedFd const&) = delete;

    ManagedFd(int fd) : drmfd_(fd) {
    }
    int const drmfd_ = -1;
};

class DrmResources
{
public:
    static std::unique_ptr<DrmResources> create(int drm_fd) {
        auto resources = drmModeGetResources(drm_fd);
        if (!resources || !resources->connectors || !resources->crtcs || !resources->encoders) {
            return nullptr;
        }
        return std::unique_ptr<DrmResources>(new DrmResources(drm_fd, resources));
    }

    ~DrmResources() {
        for (auto encoder : encoders_)
            drmModeFreeEncoder(encoder.second);
        for (auto crtc : crtcs_ )
            drmModeFreeCrtc(crtc.second);
        for (auto connector : connectors_)
            drmModeFreeConnector(connector.second);
        drmModeFreeResources(resources_);
    }

    drmModeConnectorPtr find_first_connector_of_type(uint32_t type) {
        auto connector = std::find_if(connectors_.begin(), connectors_.end(),
            [type] (auto const& c) { return c.second->connector_type == type; });
        if (connector != connectors_.end()) {
            return connector->second;
        }
        return nullptr;
    }

    drmModeEncoderPtr find_encoder_by_connector_and_type(drmModeConnectorPtr con, uint32_t type) {
        for (auto i = 0; i < con->count_encoders; i++) {
            auto enc = encoders_.find(con->encoders[i]);
            if (enc != encoders_.end() && (enc->second->encoder_type == type)) {
                return enc->second;
            }
        }
        return nullptr;
    }

    bool find_histogram_supporting_crtc(int fd, drmModeEncoderPtr encoder,
        drmModeCrtcPtr* crtc, int* histogram_ctrl, int* histogram_irq) {

        for (auto i = 0; i < resources_->count_crtcs; i++) {
            if (!(encoder->possible_crtcs & (1 << i)))
                continue;

            auto it = crtcs_.find(resources_->crtcs[i]);
            if (it == crtcs_.end()) {
                ALOGW("Could not find CRTC %i reported as possible by encoder %i",
                    resources_->crtcs[i], encoder->encoder_id);
                continue;
            }
            *crtc = it->second;

            int hist_ctl_found = -1;
            int hist_irq_found = -1;
            auto props = drmModeObjectGetProperties(fd, (*crtc)->crtc_id, DRM_MODE_OBJECT_CRTC);
            for (auto j = 0u; j < props->count_props; j++) {
                auto info = drmModeGetProperty(fd, props->props[j]);
                if (std::string(info->name) == "SDE_DSPP_HIST_CTRL_V1") {
                    hist_ctl_found = props->props[j];
                }
                if (std::string(info->name) == "SDE_DSPP_HIST_IRQ_V1") {
                    hist_irq_found = props->props[j];
                }
                drmModeFreeProperty(info);
            }
            drmModeFreeObjectProperties(props);
            if ((hist_ctl_found != -1 ) && (hist_irq_found != -1)) {
                *histogram_ctrl = hist_ctl_found;
                *histogram_irq = hist_irq_found;
                return true;
            }
        }
        return false;
    }

private:
    DrmResources(DrmResources const&) = delete;
    DrmResources& operator=(DrmResources const&) = delete;

    DrmResources(int drm_fd, drmModeResPtr resources) :
        resources_(resources),
        crtcs_(resources_->count_crtcs),
        connectors_(resources_->count_connectors),
        encoders_(resources_->count_encoders) {

        for (auto i = 0; i < resources_->count_connectors; i++) {
            auto connector = drmModeGetConnector(drm_fd, resources_->connectors[i]);
            connectors_[connector->connector_id] = connector;
        }

        for (auto i = 0; i < resources_->count_crtcs; i++) {
            auto crtc = drmModeGetCrtc(drm_fd, resources_->crtcs[i]);
            crtcs_[crtc->crtc_id] = crtc;
        }

        for (auto i = 0; i < resources_->count_encoders; i++) {
            auto encoder = drmModeGetEncoder(drm_fd, resources_->encoders[i]);
            encoders_[encoder->encoder_id] = encoder;
        }
    }

    drmModeResPtr resources_;
    std::unordered_map<int, drmModeCrtcPtr> crtcs_;
    std::unordered_map<int, drmModeConnectorPtr> connectors_;
    std::unordered_map<int, drmModeEncoderPtr> encoders_;
};

// Registering DRM_EVENT_CRTC_POWER does not trigger a notification on the DRM fd.
struct PowerEventRegistration
{
    static std::unique_ptr<PowerEventRegistration> create(int drm_fd, int crtc_id) {
        auto r = std::unique_ptr<PowerEventRegistration>(new PowerEventRegistration(drm_fd, crtc_id));
        if (drmIoctl(drm_fd, DRM_IOCTL_MSM_REGISTER_EVENT, &r->req))
           return nullptr;
        return r;
    }

    ~PowerEventRegistration() {
        drmIoctl(fd, DRM_IOCTL_MSM_DEREGISTER_EVENT, &req);
    }
private:
    PowerEventRegistration(PowerEventRegistration const&) = delete;
    PowerEventRegistration operator=(PowerEventRegistration const&) = delete;

    PowerEventRegistration(int drm_fd, int crtc_id) :
        fd(drm_fd) {
       req.object_id = crtc_id;
       req.object_type = DRM_MODE_OBJECT_CRTC;
       req.event = DRM_EVENT_CRTC_POWER;
    }

    int const fd; //non-owning.
    struct drm_msm_event_req req = {};
};

struct HistogramRAIIEnabler
{
    static std::unique_ptr<HistogramRAIIEnabler> create(int fd, int crtc_id, int histogram_prop) {
        auto hist = std::unique_ptr<HistogramRAIIEnabler>(
            new HistogramRAIIEnabler(fd, crtc_id, histogram_prop));
        if (drmModeObjectSetProperty(fd, crtc_id, DRM_MODE_OBJECT_CRTC, histogram_prop, 1))
           return nullptr;
        return hist;
    }

    ~HistogramRAIIEnabler() {
       drmModeObjectSetProperty(fd, crtc_id, DRM_MODE_OBJECT_CRTC, histogram_property, 0);
    }

private:
    HistogramRAIIEnabler(HistogramRAIIEnabler const&) = delete;
    HistogramRAIIEnabler& operator=(HistogramRAIIEnabler const&) = delete;

    HistogramRAIIEnabler(int fd, int crtc_id, int histogram_property) :
        fd(fd),
        crtc_id(crtc_id),
        histogram_property(histogram_property) {
    }

    int fd;
    int crtc_id;
    int histogram_property;
};

struct EventRegistration
{
    static std::unique_ptr<EventRegistration> create(
        int drm_fd, int crtc_id, int histogram_property) {
        auto reg = std::unique_ptr<EventRegistration>(
            new EventRegistration(drm_fd, crtc_id, histogram_property));
        if (!reg->property_registration ||
                drmIoctl(drm_fd, DRM_IOCTL_MSM_REGISTER_EVENT, &reg->req))
           return nullptr;
        return reg;
    }

    ~EventRegistration() {
        drmIoctl(fd, DRM_IOCTL_MSM_DEREGISTER_EVENT, &req);
    }

private:
    EventRegistration(int drm_fd, int crtc_id, int histogram_property) :
        property_registration(HistogramRAIIEnabler::create(drm_fd, crtc_id, histogram_property)),
        fd(drm_fd) {
       req.object_id = crtc_id;
       req.object_type = DRM_MODE_OBJECT_CRTC;
       req.event = DRM_EVENT_HISTOGRAM;
    }
    EventRegistration(EventRegistration const&) = delete;
    EventRegistration operator&(EventRegistration const&) = delete;

    //SDE_DSPP_HIST_CTRL_V1 must be turned on before receiving events
    std::unique_ptr<HistogramRAIIEnabler> property_registration;
    int const fd; //non-owning.
    struct drm_msm_event_req req = {};
};

//These are not the DPMS enum encodings.
enum class CrtcPowerState
{
    OFF,
    ON,
    UNKNOWN
};

constexpr static auto implementation_defined_max_frame_ringbuffer = 300;
}

histogram::HistogramCollector::HistogramCollector() :
    histogram(histogram::Ringbuffer::create(
        implementation_defined_max_frame_ringbuffer, std::make_unique<histogram::DefaultTimeKeeper>())) {
}

histogram::HistogramCollector::~HistogramCollector() {
    stop();
}

namespace {
static constexpr size_t numBuckets = 8;
static_assert((HIST_V_SIZE % numBuckets) == 0,
           "histogram cannot be rebucketed to smaller number of buckets");
static constexpr int bucket_compression = HIST_V_SIZE / numBuckets;

std::array<uint64_t, numBuckets> rebucketTo8Buckets(std::array<uint64_t, HIST_V_SIZE> const& frame) {
    std::array<uint64_t, numBuckets> bins;
    bins.fill(0);
    for (auto i = 0u; i < HIST_V_SIZE; i++)
        bins[i / bucket_compression] += frame[i];
    return bins;
}
}

std::string histogram::HistogramCollector::Dump() const {
    uint64_t num_frames;
    std::array<uint64_t, HIST_V_SIZE> all_sample_buckets;
    std::tie(num_frames, all_sample_buckets) = histogram->collect_cumulative();
    std::array<uint64_t, numBuckets> samples = rebucketTo8Buckets(all_sample_buckets);

    std::stringstream ss;
    ss << "Color Sampling, dark (0.0) to light (1.0): sampled frames: " << num_frames << '\n';
    if (num_frames == 0) {
        ss << "\tno color statistics collected\n";
        return ss.str();
    }

    ss << std::fixed << std::setprecision(3);
    ss << "\tbucket\t\t: # of displayed pixels at bucket value\n";
    for (auto i = 0u; i < samples.size(); i++) {
        ss << "\t" << i / static_cast<float>(samples.size()) <<
              " to " << ( i + 1 ) / static_cast<float>(samples.size()) << "\t: " <<
              samples[i] << '\n';
    }

    return ss.str();
}

HWC2::Error histogram::HistogramCollector::collect(
    uint64_t max_frames,
    uint64_t timestamp,
    int32_t out_samples_size[NUM_HISTOGRAM_COLOR_COMPONENTS],
    uint64_t* out_samples[NUM_HISTOGRAM_COLOR_COMPONENTS],
    uint64_t* out_num_frames) const {

    if (!out_samples_size || !out_num_frames)
        return HWC2::Error::BadParameter;

    out_samples_size[0] = 0;
    out_samples_size[1] = 0;
    out_samples_size[2] = numBuckets;
    out_samples_size[3] = 0;

    uint64_t num_frames;
    std::array<uint64_t, HIST_V_SIZE> samples;

    if (max_frames == 0 && timestamp == 0) {
        std::tie(num_frames, samples) = histogram->collect_cumulative();
    } else if (max_frames == 0) {
        std::tie(num_frames, samples) = histogram->collect_after(timestamp);
    } else if (timestamp == 0) {
        std::tie(num_frames, samples) = histogram->collect_max(max_frames);
    } else {
        std::tie(num_frames, samples) = histogram->collect_max_after(timestamp, max_frames);
    }

    auto samples_rebucketed = rebucketTo8Buckets(samples);
    *out_num_frames = num_frames;
    if (out_samples && out_samples[2])
        memcpy(out_samples[2], samples_rebucketed.data(), sizeof(uint64_t) * samples_rebucketed.size());

    return HWC2::Error::None;
}

HWC2::Error histogram::HistogramCollector::getAttributes(int32_t* format,
                                                         int32_t* dataspace,
                                                         uint8_t* supported_components) const {
    if (!format || !dataspace || !supported_components)
        return HWC2::Error::BadParameter;

    *format = HAL_PIXEL_FORMAT_HSV_888;
    *dataspace = HAL_DATASPACE_UNKNOWN;
    *supported_components = HWC2_FORMAT_COMPONENT_2;
    return HWC2::Error::None;
}

void histogram::HistogramCollector::start() {
    start(implementation_defined_max_frame_ringbuffer);
}

void histogram::HistogramCollector::start(uint64_t max_frames) {
    std::unique_lock<decltype(thread_control)> lk(thread_control);
    if (started) {
        return;
    }

    if (pipe2(selfpipe, O_CLOEXEC | O_NONBLOCK )) {
        ALOGE("histogram thread not started, could not create control pipe.");
        return;
    }
    histogram = histogram::Ringbuffer::create(max_frames, std::make_unique<histogram::DefaultTimeKeeper>());
    monitoring_thread = std::thread(&HistogramCollector::collecting_thread, this, selfpipe[0]);
    started = true;
}

void histogram::HistogramCollector::stop() {
    std::unique_lock<decltype(thread_control)> lk(thread_control);
    if (!started) {
        return;
    }

    char dummy = 's';
    write(selfpipe[1], &dummy, 1);
    if (monitoring_thread.joinable())
        monitoring_thread.join();
    close(selfpipe[0]);
    close(selfpipe[1]);
    started = false;
}

void histogram::HistogramCollector::collecting_thread(int selfpipe) {
    if (prctl(PR_SET_NAME, "histogram-collector", 0, 0, 0))
        ALOGW("could not set thread name for histogram collector.");

    int const control_minor_version { 64 };
    auto drm = ManagedFd::create(drmOpenControl(control_minor_version));
    if (!drm) {
        ALOGW("could not find DRM control node. Histogram collection disabled.");
        return;
    }
    auto drm_resources = DrmResources::create(*drm);
    if (!drm_resources) {
        ALOGW("could not get DRM resources. Histogram collection disabled.");
        return;
    }

    //Find the connector and encoder on the DSI. Check the possible CRTCs for support
    //for the histogram property.
    auto connector = drm_resources->find_first_connector_of_type(DRM_MODE_CONNECTOR_DSI);
    if (!connector) {
        ALOGE("Could not find connector. Histogram collection disabled.");
        return;
    }

    auto encoder = drm_resources->find_encoder_by_connector_and_type(
        connector, DRM_MODE_ENCODER_DSI);
    if (!encoder) {
        ALOGE("Could not find encoder. Histogram collection disabled.");
        return;
    }

    auto histogram_property = -1;
    auto histogram_irq = -1;
    drmModeCrtcPtr crtc = nullptr;
    if (!drm_resources->find_histogram_supporting_crtc(
        *drm, encoder, &crtc, &histogram_property, &histogram_irq)) {
        ALOGE("Could not find CRTC that supports color sampling. Histogram collection disabled.");
        return;
    }

    // Set up event loop.
    // Event loop will listen to 1) FD that exposes color sampling events (1 per displayed frame),
    // and 2) a self-pipe that will indicate when this thread should shut down.
    enum class EventType
    {
        DRM,
        CTL,
        NUM_EVENT_TYPES
    };

    struct epoll_event ev, events[static_cast<int>(EventType::NUM_EVENT_TYPES)];
    auto epollfd = ManagedFd::create(epoll_create1(EPOLL_CLOEXEC));
    if (!epollfd) {
        ALOGE("Error creating epoll loop. Histogram collection disabled.");
        return;
    }

    ev.events = EPOLLIN;
    ev.data.u32 = static_cast<uint32_t>(EventType::DRM);
    if (epoll_ctl(*epollfd, EPOLL_CTL_ADD, *drm, &ev) == -1) {
        ALOGE("Error adding drm fd to epoll. Histogram collection disabled.");
        return;
    }

    ev.events = EPOLLIN;
    ev.data.u32 = static_cast<uint32_t>(EventType::CTL);
    if (epoll_ctl(*epollfd, EPOLL_CTL_ADD, selfpipe, &ev) == -1) {
        ALOGE("Error adding control fd to epoll. Histogram collection disabled.");
        return;
    }

    if (fcntl(*drm, F_SETFL, fcntl(*drm, F_GETFL) | O_NONBLOCK)) {
        ALOGE("Error making drm read nonblocking. Histogram collection disabled.");
        return;
    }

    /* Attempting to set SDE_DSPP_HIST_CTRL_V1, SDE_DSPP_HIST_IRQ_V1, or DRM_EVENT_HISTOGRAM
     * while the screen is off will result in an error.
     *
     * Since we have to wait on events (or poll the connector for power state), and then issue
     * based on that info, there's no 100% certain way to know if enabling those histogram events
     * are done when the screen is actually on. We work around this by retrying when those
     * events fail, and not trying to enable those when we know the screen is off.
     */
    std::unique_ptr<EventRegistration> hist_registration = nullptr;
    CrtcPowerState state = CrtcPowerState::UNKNOWN;
    bool collecting = true;

    auto power_registration = PowerEventRegistration::create(*drm, crtc->crtc_id);
    if (!power_registration) {
        ALOGE("could not register event to monitor power events. Histogram collection disabled.");
        return;
    }

    while (collecting) {
        if (state != CrtcPowerState::OFF) {
            if (!hist_registration) {
                hist_registration = EventRegistration::create(
                    *drm, crtc->crtc_id, histogram_property);
            }

            if (drmModeObjectSetProperty(*drm,
                    crtc->crtc_id, DRM_MODE_OBJECT_CRTC, histogram_irq, 1)) {
                ALOGI("Failed to enable histogram property on crtc, will retry");
                state = CrtcPowerState::OFF;
                hist_registration = nullptr;
            }
        }

        int nfds = epoll_wait(*epollfd, events, static_cast<int>(EventType::NUM_EVENT_TYPES), -1);
        if (nfds == -1) {
            if (errno != EINTR)
                collecting = false;
            continue;
        }

        for (auto i = 0; i < nfds; i++) {
            if (events[i].data.u32 == static_cast<uint32_t>(EventType::CTL)) {
                collecting = false;
            } else if (events[i].data.u32 == static_cast<uint32_t>(EventType::DRM)) {
                //VLA has a single int as blob id, or power mode
                char buffer[sizeof(drm_msm_event_resp) + sizeof(uint32_t)];
                auto size_read = read(*drm, buffer, sizeof(buffer));
                if (size_read != sizeof(buffer)) {
                    ALOGW("Histogram event wrong size (%zu bytes, errno: %X). Skipping event.",
                        size_read, errno);
                    continue;
                }

                struct drm_msm_event_resp* response =
                    reinterpret_cast<struct drm_msm_event_resp*>(buffer);
                if (response->base.type == DRM_EVENT_HISTOGRAM) {
                    uint32_t blob_id = *reinterpret_cast<uint32_t*>(response->data);
                    drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(*drm, blob_id);
                    histogram->insert(*static_cast<struct drm_msm_hist*>(blob->data));
                    drmModeFreePropertyBlob(blob);
                }

                if (response->base.type == DRM_EVENT_CRTC_POWER) {
                    uint32_t state_raw = *reinterpret_cast<uint32_t*>(response->data);
                    state = (state_raw) ? CrtcPowerState::ON : CrtcPowerState::OFF;
                }
            }
        }
    }
}
