/*
 * Copyright 2023 The Android Open Source Project
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

#ifndef MMC_CODEC_CLIENT_CODEC_CLIENT_H_
#define MMC_CODEC_CLIENT_CODEC_CLIENT_H_

#include <dbus/object_proxy.h>
#include <stdint.h>

#include <memory>

#include "mmc/metrics/mmc_rtt_logger.h"
#include "mmc/mmc_interface/mmc_interface.h"
#include "mmc/proto/mmc_service.pb.h"

namespace mmc {

// Implementation of MmcInterface.
// CodecClient serves as proxy of MMC codec service.
class CodecClient : public MmcInterface {
 public:
  // Connects to DBus.
  explicit CodecClient();

  // Calls |cleanup|.
  ~CodecClient();

  // CodecClient is neither copyable nor movable.
  CodecClient(const CodecClient&) = delete;
  CodecClient& operator=(const CodecClient&) = delete;

  // Calls MMC DBus method |CodecInit| with |CodecInitRequest|, opens the socket
  // for transcoding.
  //
  // Returns:
  //   Input frame size accepted by the transcoder, if init succeeded.
  //   Negative errno on error, otherwise.
  int init(const ConfigParam config) override;

  // Closes the socket, and calls MMC DBus method |CodecCleanUp|.
  void cleanup() override;

  // Transfers PCM data between caller and the MMC codec service.
  //
  // Returns:
  //   Transcoded data length, if transcode succeeded.
  //   Negative errno on error, otherwise.
  int transcode(uint8_t* i_buf, int i_len, uint8_t* o_buf, int o_len) override;

 private:
  int skt_fd_;
  dbus::ObjectProxy* codec_manager_;  // Owned by the Bus object.
  scoped_refptr<dbus::Bus> bus_;
  std::unique_ptr<MmcRttLogger> record_logger_;
};

}  // namespace mmc

#endif  // MMC_CODEC_CLIENT_CODEC_CLIENT_H_
