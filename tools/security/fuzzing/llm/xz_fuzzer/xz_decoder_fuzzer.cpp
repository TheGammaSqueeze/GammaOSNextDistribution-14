#include <fuzzer/FuzzedDataProvider.h>
#include "xz.h"

// Function to initialize xz_dec structure using xz_dec_init
struct xz_dec *init_xz_dec(FuzzedDataProvider& stream) {
    // Randomly select a mode from the xz_mode enum
    const std::array<enum xz_mode, 3> modes = {XZ_SINGLE, XZ_PREALLOC, XZ_DYNALLOC};
    enum xz_mode mode = stream.PickValueInArray(modes);

    // Generate a random dict_max value
    uint32_t dict_max = stream.ConsumeIntegral<uint32_t>();

    // Initialize the xz_dec structure
    struct xz_dec *s = xz_dec_init(mode, dict_max);

    return s;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider stream(data, size);

    // Initialize xz_dec structure
    struct xz_dec *s = init_xz_dec(stream);

    // Initialize xz_buf structure
    struct xz_buf b;
    size_t in_buffer_size = stream.ConsumeIntegralInRange<size_t>(0, size);
    std::vector<uint8_t> in_buffer(in_buffer_size);
    for (size_t i = 0; i < in_buffer_size; ++i) {
        in_buffer[i] = stream.ConsumeIntegral<uint8_t>();
    }
    b.in = in_buffer.data();
    b.in_pos = 0;
    b.in_size = in_buffer_size;

    size_t out_buffer_size = stream.ConsumeIntegralInRange<size_t>(0, size);
    std::vector<uint8_t> out_buffer(out_buffer_size);
    b.out = out_buffer.data();
    b.out_pos = 0;
    b.out_size = out_buffer_size;

    // Call the function under test
    xz_ret result = xz_dec_run(s, &b);

    return 0;  // Non-zero return values are usually reserved for fatal errors
}
