#include <stdlib.h>
#include <stddef.h>
#include <stdint.h>
#include <algorithm>
#include <audio_utils/limiter.h>
#include <system/audio.h>
#include <audio_utils/mono_blend.h>
#include <fuzzer/FuzzedDataProvider.h>

audio_format_t decideFormat(uint32_t num) {

 switch (num % 3) {
    case 0:
      return AUDIO_FORMAT_PCM_16_BIT; //  1st switch case in mono_blend()
    case 1:
      return AUDIO_FORMAT_PCM_FLOAT;  //  2nd switch case in mono_blend()
    default:
      return (audio_format_t) num;  //  default switch case in mono_blend()
 }

}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {

  /* Minimum size == 9 bytes
   * 4 bytes: uint32_t audio_format_t
   * 4 bytes: uint32_t channelCount
   * 1 byte : bool limit
   * Extra  : float *buf
   */

  if (data == nullptr || size < 9) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  // calculate size of buffer in floats
  size_t samples = (size - 9) / sizeof(float);

  // initialize the 5 parameters
  float *buf = (float *) malloc(samples * sizeof(float));
  audio_format_t format = decideFormat(fuzzed_data.ConsumeIntegral<uint32_t>());
  size_t channelCount = samples / std::max(fuzzed_data.ConsumeIntegral<uint32_t>(), (uint32_t)1);
  size_t frames = samples / std::max(channelCount, (size_t)1);
  bool limit = fuzzed_data.ConsumeBool();

  // fill buffer with floats
  for (size_t i = 0; i < frames * channelCount; ++i) {
    buf[i] = fuzzed_data.ConsumeFloatingPoint<float>();
  }

  mono_blend((void *) buf, format, channelCount, frames, limit);

  free(buf);
  return 0;
}
