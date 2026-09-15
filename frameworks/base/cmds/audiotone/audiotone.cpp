// Plays a clean synthetic tone through a plain AudioTrack so the platform's
// output path (AudioFlinger mixer/resampler + HAL) can be judged with a
// microphone independently of any emulator. Usage: audiotone [rate] [seconds] [chunkFrames]
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>
#include <android/content/AttributionSourceState.h>
#include <binder/ProcessState.h>
#include <media/AudioTrack.h>
#include <utils/Log.h>

using namespace android;

int main(int argc, char** argv) {
    const uint32_t rate = argc > 1 ? (uint32_t)atoi(argv[1]) : 43971;
    const int secs = argc > 2 ? atoi(argv[2]) : 120;
    const size_t chunk = argc > 3 ? (size_t)atoi(argv[3]) : 2940;
    ProcessState::self()->startThreadPool();
    sp<AudioTrack> t = new AudioTrack();
    audio_attributes_t attr = {};
    attr.content_type = AUDIO_CONTENT_TYPE_MUSIC;
    attr.usage = AUDIO_USAGE_MEDIA;
    status_t st = t->set(AUDIO_STREAM_DEFAULT, rate, AUDIO_FORMAT_PCM_16_BIT, AUDIO_CHANNEL_OUT_STEREO,
                         0 /*frameCount*/, AUDIO_OUTPUT_FLAG_NONE, nullptr /*cb*/, 0 /*notif*/,
                         nullptr /*sharedBuffer*/, false /*threadCanCallJava*/, AUDIO_SESSION_ALLOCATE,
                         AudioTrack::TRANSFER_SYNC, nullptr, android::content::AttributionSourceState(), &attr, false,
                         1.0f, AUDIO_PORT_HANDLE_NONE);
    if (st != NO_ERROR) { fprintf(stderr, "AudioTrack::set failed %d\n", st); return 1; }
    printf("rate=%u frameCount=%zu latency=%u chunk=%zu\n", rate, t->frameCount(), t->latency(), chunk);
    t->start();
    std::vector<int16_t> buf(chunk * 2);
    double ph = 0;
    const size_t total = (size_t)rate * (size_t)secs;
    for (size_t done = 0; done < total; done += chunk) {
        for (size_t i = 0; i < chunk; i++) {
            const double v = 0.15 * (sin(ph) + 0.5 * sin(ph * 1.5) + 0.3 * sin(ph * 2.0));
            buf[2 * i] = buf[2 * i + 1] = (int16_t)(v * 32767.0);
            ph += 2.0 * M_PI * 220.0 / 44100.0;
            if (ph > 2.0 * M_PI * 1000.0) ph -= 2.0 * M_PI * 1000.0;
        }
        const ssize_t w = t->write(buf.data(), chunk * 4, true);
        if (w < 0) { fprintf(stderr, "write failed %zd\n", w); break; }
    }
    t->stop();
    printf("done, underruns=%u\n", t->getUnderrunCount());
    return 0;
}
