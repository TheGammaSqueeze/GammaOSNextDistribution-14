/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cstdint>

#include <fuzzer/FuzzedDataProvider.h>

extern "C" {
#include "libufdt_sysdeps.h"
#include "ufdt_overlay.h"
}

/* Count split value, plus 1 byte for dto and overlay each */
constexpr uint32_t kMinData = sizeof(uint32_t) + 2;

constexpr uint32_t kMaxData = 1024 * 512;

/* libFuzzer driver.
 * We need two dtb's to test merging, so split the input data block, using
 * the first 4 bytes to give the dtb length, the rest being overlay.
 * The mkcorpus helper program can construct these files.
 */
extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  /* Bound input size */
  if (size < kMinData || size > kMaxData) {
    return 0;
  }

  FuzzedDataProvider fdp(data, size);

  /* Read fixed length header */
  auto hdr = fdp.ConsumeBytes<uint8_t>(4);

  /* Extract the length, network byte order */
  const uint32_t dtb_len = hdr[0] << 24 | hdr[1] << 16 | hdr[2] << 8 | hdr[3];

  /* Ensure the dtb and overlay are non-zero length */
  if (dtb_len == 0 || dtb_len >= size - 1) {
    return 0;
  }

  auto dtb = fdp.ConsumeBytes<uint8_t>(dtb_len);
  auto overlay = fdp.ConsumeRemainingBytes<uint8_t>();

  /* Check headers */
  auto fdt_dtb = ufdt_install_blob(dtb.data(), dtb.size());
  auto fdt_overlay = ufdt_install_blob(overlay.data(), overlay.size());

  if (!fdt_dtb || !fdt_overlay) {
    return 0;
  }

  struct fdt_header *res =
      ufdt_apply_overlay(fdt_dtb, dtb.size(), fdt_overlay, overlay.size());

  if (res) {
    dto_free(res);
  }

  return 0;
}
