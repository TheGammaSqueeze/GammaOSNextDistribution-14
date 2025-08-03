#include <cstdint>
#include <fuzzer/FuzzedDataProvider.h>

extern "C" {
#include "libufdt_sysdeps.h"
#include "libufdt.h"
#include "ufdt_node_pool.h"
}

constexpr uint32_t kMaxData = 1024 * 512;
constexpr size_t kMinDataSize = 10;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  if (size < kMinDataSize || size > kMaxData) {
    return 0;
  }

  FuzzedDataProvider stream(data, size);

  // Initialize node pool
  struct ufdt_node_pool pool; // Allocate the structure.
  ufdt_node_pool_construct(&pool); // Initialize it.

  // Consume bytes and ensure they persist for the required lifetime
  auto bytes = stream.ConsumeBytes<uint8_t>(stream.remaining_bytes() / 2);
  void *fdtp = bytes.data();

  fdt32_t *fdt_tag_ptr = (fdt32_t *)fdt_offset_ptr(fdtp, 0, sizeof(fdt32_t));
  struct ufdt_node *node = ufdt_node_construct(fdtp, fdt_tag_ptr, &pool);

  int depth = stream.ConsumeIntegral<int>();

  if (node) {
    ufdt_node_print(node, depth);
  }

  ufdt_node_destruct(node, &pool);

  return 0;
}
