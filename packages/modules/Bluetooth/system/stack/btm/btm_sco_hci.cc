/*
 * Copyright 2021 The Android Open Source Project
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

#define LOG_TAG "sco_hci"

#include <bluetooth/log.h>
#include <grp.h>
#include <math.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cfloat>
#include <memory>

#include "btif/include/core_callbacks.h"
#include "btif/include/stack_manager_t.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_sco.h"
#include "udrv/include/uipc.h"

#define SCO_DATA_READ_POLL_MS 10
#define SCO_HOST_DATA_PATH "/var/run/bluetooth/audio/.sco_data"
// TODO(b/198260375): Make SCO data owner group configurable.
#define SCO_HOST_DATA_GROUP "bluetooth-audio"

/* Per Bluetooth Core v5.0 and HFP 1.9 specification. */
#define BTM_MSBC_H2_HEADER_0 0x01
#define BTM_MSBC_H2_HEADER_LEN 2
#define BTM_MSBC_PKT_LEN 60
#define BTM_MSBC_PKT_FRAME_LEN 57 /* Packet length without the header */
#define BTM_MSBC_SYNC_WORD 0xAD

/* Used by PLC */
#define BTM_MSBC_SAMPLE_SIZE 2 /* 2 bytes*/
#define BTM_MSBC_FS 120        /* Frame Size */

#define BTM_PLC_WL 256 /* 16ms - Window Length for pattern matching */
#define BTM_PLC_TL 64  /* 4ms - Template Length for matching */
#define BTM_PLC_HL \
  (BTM_PLC_WL + BTM_MSBC_FS - 1) /* Length of History buffer required */
#define BTM_PLC_SBCRL 36         /* SBC Reconvergence sample Length */
#define BTM_PLC_OLAL 16          /* OverLap-Add Length */

/* Disable the PLC when there are more than threshold of lost packets in the
 * window */
#define BTM_PLC_WINDOW_SIZE 5
#define BTM_PLC_PL_THRESHOLD 1

/* LC3 definitions */
#define BTM_LC3_H2_HEADER_0 0x01
#define BTM_LC3_H2_HEADER_LEN 2
#define BTM_LC3_PKT_LEN 60
#define BTM_LC3_FS 240 /* Frame Size */

namespace {

std::unique_ptr<tUIPC_STATE> sco_uipc = nullptr;

void sco_data_cb(tUIPC_CH_ID, tUIPC_EVENT event) {
  switch (event) {
    case UIPC_OPEN_EVT:
      /*
       * Read directly from media task from here on (keep callback for
       * connection events.
       */
      UIPC_Ioctl(*sco_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_REG_REMOVE_ACTIVE_READSET,
                 NULL);
      UIPC_Ioctl(*sco_uipc, UIPC_CH_ID_AV_AUDIO, UIPC_SET_READ_POLL_TMO,
                 reinterpret_cast<void*>(SCO_DATA_READ_POLL_MS));
      break;
    default:
      break;
  }
}

}  // namespace

namespace bluetooth {
namespace audio {
namespace sco {

void open() {
  if (sco_uipc != nullptr) {
    log::warn("Re-opening UIPC that is already running");
  }

  sco_uipc = UIPC_Init();
  if (sco_uipc == nullptr) {
    log::error("failed to init UIPC");
    return;
  }

  UIPC_Open(*sco_uipc, UIPC_CH_ID_AV_AUDIO, sco_data_cb, SCO_HOST_DATA_PATH);
  struct group* grp = getgrnam(SCO_HOST_DATA_GROUP);
  chmod(SCO_HOST_DATA_PATH, 0770);
  if (grp) {
    int res = chown(SCO_HOST_DATA_PATH, -1, grp->gr_gid);
    if (res == -1) {
      log::error("failed: {}", strerror(errno));
    }
  }
}

void cleanup() {
  if (sco_uipc == nullptr) {
    return;
  }
  UIPC_Close(*sco_uipc, UIPC_CH_ID_ALL);
  sco_uipc = nullptr;
}

size_t read(uint8_t* p_buf, uint32_t len) {
  if (sco_uipc == nullptr) {
    log::warn("Read from uninitialized or closed UIPC");
    return 0;
  }
  return UIPC_Read(*sco_uipc, UIPC_CH_ID_AV_AUDIO, p_buf, len);
}

size_t write(const uint8_t* p_buf, uint32_t len) {
  if (sco_uipc == nullptr) {
    log::warn("Write to uninitialized or closed UIPC");
    return 0;
  }
  return UIPC_Send(*sco_uipc, UIPC_CH_ID_AV_AUDIO, 0, p_buf, len) ? len : 0;
}

namespace wbs {

/* Second octet of H2 header is composed by 4 bits fixed 0x8 and 4 bits
 * sequence number 0000, 0011, 1100, 1111. */
static const uint8_t btm_h2_header_frames_count[] = {0x08, 0x38, 0xc8, 0xf8};

/* Supported SCO packet sizes for mSBC. The wideband speech mSBC frame parsing
 * code ties to limited packet size values. Specifically list them out
 * to check against when setting packet size. The first entry is the default
 * value as a fallback. */
constexpr size_t btm_wbs_supported_pkt_size[] = {BTM_MSBC_PKT_LEN, 72, 0};
/* Buffer size should be set to least common multiple of SCO packet size and
 * BTM_MSBC_PKT_LEN for optimizing buffer copy. */
constexpr size_t btm_wbs_msbc_buffer_size[] = {BTM_MSBC_PKT_LEN, 360, 0};

/* The pre-computed SCO packet per HFP 1.7 spec. This mSBC packet will be
 * decoded into all-zero input PCM. */
static const uint8_t btm_msbc_zero_packet[] = {
    0x01, 0x08, /* Mock H2 header */
    0xad, 0x00, 0x00, 0xc5, 0x00, 0x00, 0x00, 0x00, 0x77, 0x6d, 0xb6, 0xdd,
    0xdb, 0x6d, 0xb7, 0x76, 0xdb, 0x6d, 0xdd, 0xb6, 0xdb, 0x77, 0x6d, 0xb6,
    0xdd, 0xdb, 0x6d, 0xb7, 0x76, 0xdb, 0x6d, 0xdd, 0xb6, 0xdb, 0x77, 0x6d,
    0xb6, 0xdd, 0xdb, 0x6d, 0xb7, 0x76, 0xdb, 0x6d, 0xdd, 0xb6, 0xdb, 0x77,
    0x6d, 0xb6, 0xdd, 0xdb, 0x6d, 0xb7, 0x76, 0xdb, 0x6c,
    /* End of Audio Samples */
    0x00 /* A padding byte defined by mSBC */};

/* Raised Cosine table for OLA */
static const float rcos[BTM_PLC_OLAL] = {
    0.99148655f, 0.96623611f, 0.92510857f, 0.86950446f,
    0.80131732f, 0.72286918f, 0.63683150f, 0.54613418f,
    0.45386582f, 0.36316850f, 0.27713082f, 0.19868268f,
    0.13049554f, 0.07489143f, 0.03376389f, 0.00851345f};

static int16_t f_to_s16(float input) {
  return input > INT16_MAX   ? INT16_MAX
         : input < INT16_MIN ? INT16_MIN
                             : (int16_t)input;
}
/* This structure tracks the packet loss for last PLC_WINDOW_SIZE of packets */
struct tBTM_MSBC_BTM_PLC_WINDOW {
  bool loss_hist[BTM_PLC_WINDOW_SIZE]; /* The packet loss history of receiving
                                      packets.*/
  unsigned int idx;   /* The index of the to be updated packet loss status. */
  unsigned int count; /* The count of lost packets in the window. */

 public:
  void update_plc_state(bool is_packet_loss) {
    bool* curr = &loss_hist[idx];
    if (is_packet_loss != *curr) {
      count += (is_packet_loss - *curr);
      *curr = is_packet_loss;
    }
    idx = (idx + 1) % BTM_PLC_WINDOW_SIZE;
  }

  bool is_packet_loss_too_high() {
    /* The packet loss count comes from a time window and we use it as an
     * indicator of our confidence of the PLC algorithm. It is known to
     * generate poorer and robotic feeling sounds, when the majority of
     * samples in the PLC history buffer are from the concealment results.
     */
    return count > BTM_PLC_PL_THRESHOLD;
  }
};

/* The PLC is specifically designed for mSBC. The algorithm searches the
 * history of receiving samples to find the best match samples and constructs
 * substitutions for the lost samples. The selection is based on pattern
 * matching a template, composed of a length of samples preceding to the lost
 * samples. It then uses the following samples after the best match as the
 * replacement samples and applies Overlap-Add to reduce the audible
 * distortion.
 *
 * This structure holds related info needed to conduct the PLC algorithm.
 */
struct tBTM_MSBC_PLC {
  int16_t hist[BTM_PLC_HL + BTM_MSBC_FS + BTM_PLC_SBCRL +
               BTM_PLC_OLAL]; /* The history buffer for receiving samples, we
                                 also use it to buffer the processed
                                 replacement samples */
  unsigned best_lag;      /* The index of the best substitution samples in the
                             sample history */
  int handled_bad_frames; /* Number of bad frames handled since the last good
                             frame */
  int16_t decoded_buffer[BTM_MSBC_FS]; /* Used for storing the samples from
                                      decoding the mSBC zero frame packet and
                                      also constructed frames */
  tBTM_MSBC_BTM_PLC_WINDOW*
      pl_window; /* Used to monitor how many packets are bad within the recent
                    BTM_PLC_WINDOW_SIZE of packets. We use this to determine if
                    we want to disable the PLC temporarily */

  int num_decoded_frames; /* Number of total read mSBC frames. */
  int num_lost_frames;    /* Number of total lost mSBC frames. */

  void overlap_add(int16_t* output, float scaler_d, const int16_t* desc,
                   float scaler_a, const int16_t* asc) {
    for (int i = 0; i < BTM_PLC_OLAL; i++) {
      output[i] = f_to_s16(scaler_d * desc[i] * rcos[i] +
                           scaler_a * asc[i] * rcos[BTM_PLC_OLAL - 1 - i]);
    }
  }

  float cross_correlation(int16_t* x, int16_t* y) {
    float sum = 0, x2 = 0, y2 = 0;

    for (int i = 0; i < BTM_PLC_TL; i++) {
      sum += ((float)x[i]) * y[i];
      x2 += ((float)x[i]) * x[i];
      y2 += ((float)y[i]) * y[i];
    }
    return sum / sqrtf(x2 * y2);
  }

  int pattern_match(int16_t* hist) {
    int best = 0;
    float cn, max_cn = FLT_MIN;

    for (int i = 0; i < BTM_PLC_WL; i++) {
      cn = cross_correlation(&hist[BTM_PLC_HL - BTM_PLC_TL], &hist[i]);
      if (cn > max_cn) {
        best = i;
        max_cn = cn;
      }
    }
    return best;
  }

  float amplitude_match(int16_t* x, int16_t* y) {
    uint32_t sum_x = 0, sum_y = 0;
    float scaler;
    for (int i = 0; i < BTM_MSBC_FS; i++) {
      sum_x += abs(x[i]);
      sum_y += abs(y[i]);
    }

    if (sum_y == 0) return 1.2f;

    scaler = (float)sum_x / sum_y;
    return scaler > 1.2f ? 1.2f : scaler < 0.75f ? 0.75f : scaler;
  }

 public:
  void init() {
    if (pl_window) osi_free(pl_window);
    pl_window = (tBTM_MSBC_BTM_PLC_WINDOW*)osi_calloc(sizeof(*pl_window));
  }

  void deinit() {
    if (pl_window) osi_free_and_reset((void**)&pl_window);
  }

  int get_num_decoded_frames() { return num_decoded_frames; }

  int get_num_lost_frames() { return num_lost_frames; }

  void handle_bad_frames(const uint8_t** output) {
    float scaler;
    int16_t* best_match_hist;
    int16_t* frame_head = &hist[BTM_PLC_HL];

    num_decoded_frames++;
    num_lost_frames++;

    /* mSBC codec is stateful, the history of signal would contribute to the
     * decode result decoded_buffer. This should never fail. */
    GetInterfaceToProfiles()->msbcCodec->decodePacket(
        btm_msbc_zero_packet, decoded_buffer, sizeof(decoded_buffer));

    /* The PLC algorithm is more likely to generate bad results that sound
     * robotic after severe packet losses happened. Only applying it when
     * we are confident. */
    if (!pl_window->is_packet_loss_too_high()) {
      if (handled_bad_frames == 0) {
        /* Finds the best matching samples and amplitude */
        best_lag = pattern_match(hist) + BTM_PLC_TL;
        best_match_hist = &hist[best_lag];
        scaler =
            amplitude_match(&hist[BTM_PLC_HL - BTM_MSBC_FS], best_match_hist);

        /* Constructs the substitution samples */
        overlap_add(frame_head, 1.0, decoded_buffer, scaler, best_match_hist);
        for (int i = BTM_PLC_OLAL; i < BTM_MSBC_FS; i++)
          hist[BTM_PLC_HL + i] = f_to_s16(scaler * best_match_hist[i]);
        overlap_add(&frame_head[BTM_MSBC_FS], scaler,
                    &best_match_hist[BTM_MSBC_FS], 1.0,
                    &best_match_hist[BTM_MSBC_FS]);

        memmove(&frame_head[BTM_MSBC_FS + BTM_PLC_OLAL],
                &best_match_hist[BTM_MSBC_FS + BTM_PLC_OLAL],
                BTM_PLC_SBCRL * BTM_MSBC_SAMPLE_SIZE);
      } else {
        /* Using the existing best lag and copy the following frames */
        memmove(frame_head, &hist[best_lag],
                (BTM_MSBC_FS + BTM_PLC_SBCRL + BTM_PLC_OLAL) *
                    BTM_MSBC_SAMPLE_SIZE);
      }
      /* Copy the constructed frames to decoded buffer for caller to use */
      std::copy(frame_head, &frame_head[BTM_MSBC_FS], decoded_buffer);

      handled_bad_frames++;
    } else {
      /* This is a case similar to receiving a good frame with all zeros, we set
       * handled_bad_frames to zero to prevent the following good frame from
       * being concealed to reconverge with the zero frames we fill in. The
       * concealment result sounds more artificial and weird than simply writing
       * zeros and following samples.
       */
      std::copy(std::begin(decoded_buffer), std::end(decoded_buffer),
                frame_head);
      std::fill(&frame_head[BTM_MSBC_FS],
                &frame_head[BTM_MSBC_FS + BTM_PLC_SBCRL + BTM_PLC_OLAL], 0);
      /* No need to copy the frames as we'll use the decoded zero frames in the
       * decoded buffer as our concealment frames */

      handled_bad_frames = 0;
    }

    *output = (const uint8_t*)decoded_buffer;

    /* Shift the frames to update the history window */
    memmove(hist, &hist[BTM_MSBC_FS],
            (BTM_PLC_HL + BTM_PLC_SBCRL + BTM_PLC_OLAL) * BTM_MSBC_SAMPLE_SIZE);
    pl_window->update_plc_state(1);
  }

  void handle_good_frames(int16_t* input) {
    int16_t* frame_head;
    num_decoded_frames++;
    if (handled_bad_frames != 0) {
      /* If there was a packet concealment before this good frame, we need to
       * reconverge the input frames */
      frame_head = &hist[BTM_PLC_HL];

      /* For the first good frame after packet loss, we need to conceal the
       * received samples to have it reconverge with the true output */
      std::copy(frame_head, &frame_head[BTM_PLC_SBCRL], input);
      /* Overlap the input frame with the previous output frame */
      overlap_add(&input[BTM_PLC_SBCRL], 1.0, &frame_head[BTM_PLC_SBCRL], 1.0,
                  &input[BTM_PLC_SBCRL]);
      handled_bad_frames = 0;
    }

    /* Shift the history and update the good frame to the end of it */
    memmove(hist, &hist[BTM_MSBC_FS],
            (BTM_PLC_HL - BTM_MSBC_FS) * BTM_MSBC_SAMPLE_SIZE);
    std::copy(input, &input[BTM_MSBC_FS], &hist[BTM_PLC_HL - BTM_MSBC_FS]);
    pl_window->update_plc_state(0);
  }
};

/* Define the structure that contains mSBC data */
struct tBTM_MSBC_INFO {
  size_t packet_size; /* SCO mSBC packet size supported by lower layer */
  size_t buf_size; /* The size of the buffer, determined by the packet_size. */

  enum decode_buf_state {
    DECODE_BUF_EMPTY,
    DECODE_BUF_FULL,

    // Neither empty nor full.
    DECODE_BUF_HALFFULL,
  };

  uint8_t* packet_buf;      /* Temporary buffer to store the data */
  uint8_t* msbc_decode_buf; /* Buffer to store mSBC packets to decode */
  size_t decode_buf_wo;     /* Write offset of the decode buffer */
  size_t decode_buf_ro;     /* Read offset of the decode buffer */

  /* Within the circular buffer, which can be visualized as having
     two halves, mirror indicators track the pointer's location,
     signaling whether it resides in the first or second segment:

              [buf_size-1] ┼ - - -─┼ [0]
                           │       │
                           │       │
     wo = x, wo_mirror = 0 ^       v ro = x, ro_mirror = 1
                           │       │
                           │       │
                       [0] ┼ - - - ┼ [buf_size-1]
              (First Half)           (Second Half)
  */
  bool decode_buf_wo_mirror; /* The mirror indicator specifies whether
                                the write pointer is currently located
                                in the first or second half of the
                                circular buffer */
  bool decode_buf_ro_mirror; /* The mirror indicator specifies whether
                                the read pointer is currently located
                                in the first or second half of the
                                circular buffer */
  bool read_corrupted;      /* If the current mSBC packet read is corrupted */

  uint8_t* msbc_encode_buf; /* Buffer to store the encoded SCO packets */
  size_t encode_buf_wo;     /* Write offset of the encode buffer */
  size_t encode_buf_ro;     /* Read offset of the encode buffer */

  int16_t decoded_pcm_buf[BTM_MSBC_FS]; /* Buffer to store decoded PCM */

  uint8_t num_encoded_msbc_pkts; /* Number of the encoded mSBC packets */

  tBTM_MSBC_PLC* plc; /* PLC component to handle the packet loss of input */
  tBTM_SCO_PKT_STATUS* pkt_status; /* Record of mSBC packet status */
  static size_t get_supported_packet_size(size_t pkt_size,
                                          size_t* buffer_size) {
    int i;
    for (i = 0; btm_wbs_supported_pkt_size[i] != 0 &&
                btm_wbs_supported_pkt_size[i] != pkt_size;
         i++)
      ;
    /* In case of unsupported value, error log and fallback to
     * BTM_MSBC_PKT_LEN(60). */
    if (btm_wbs_supported_pkt_size[i] == 0) {
      log::warn("Unsupported packet size {}", (unsigned long)pkt_size);
      i = 0;
    }

    if (buffer_size) {
      *buffer_size = btm_wbs_msbc_buffer_size[i];
    }
    return btm_wbs_supported_pkt_size[i];
  }

  bool verify_h2_header_seq_num(const uint8_t num) {
    for (int i = 0; i < 4; i++) {
      if (num == btm_h2_header_frames_count[i]) {
        return true;
      }
    }
    return false;
  }

 public:
  size_t init(size_t pkt_size) {
    decode_buf_wo = 0;
    decode_buf_ro = 0;
    decode_buf_wo_mirror = false;
    decode_buf_ro_mirror = false;

    encode_buf_wo = 0;
    encode_buf_ro = 0;

    pkt_size = get_supported_packet_size(pkt_size, &buf_size);
    if (pkt_size == packet_size) return packet_size;
    packet_size = pkt_size;

    if (packet_buf) osi_free(packet_buf);
    packet_buf = (uint8_t*)osi_calloc(packet_size);

    if (msbc_decode_buf) osi_free(msbc_decode_buf);
    msbc_decode_buf = (uint8_t*)osi_calloc(buf_size);

    if (msbc_encode_buf) osi_free(msbc_encode_buf);
    msbc_encode_buf = (uint8_t*)osi_calloc(buf_size);

    if (plc) {
      plc->deinit();
      osi_free(plc);
    }
    plc = (tBTM_MSBC_PLC*)osi_calloc(sizeof(*plc));
    plc->init();

    if (pkt_status) osi_free(pkt_status);
    pkt_status = (tBTM_SCO_PKT_STATUS*)osi_calloc(sizeof(*pkt_status));
    pkt_status->init();

    return packet_size;
  }

  void deinit() {
    if (msbc_decode_buf) osi_free(msbc_decode_buf);
    if (packet_buf) osi_free(packet_buf);
    if (msbc_encode_buf) osi_free(msbc_encode_buf);
    if (plc) {
      plc->deinit();
      osi_free_and_reset((void**)&plc);
    }
    if (pkt_status) osi_free_and_reset((void**)&pkt_status);
  }

  void incr_buf_offset(size_t& offset, bool& mirror, size_t bsize,
                       size_t amount) {
    if (bsize - offset > amount) {
      offset += amount;
      return;
    }

    mirror = !mirror;
    offset = amount - (bsize - offset);
  }

  decode_buf_state decode_buf_status() {
    if (decode_buf_ro == decode_buf_wo) {
      if (decode_buf_ro_mirror == decode_buf_wo_mirror) return DECODE_BUF_EMPTY;
      return DECODE_BUF_FULL;
    }
    return DECODE_BUF_HALFFULL;
  }

  size_t decode_buf_data_len() {
    switch (decode_buf_status()) {
      case DECODE_BUF_EMPTY:
        return 0;
      case DECODE_BUF_FULL:
        return buf_size;
      case DECODE_BUF_HALFFULL:
      default:
        if (decode_buf_wo > decode_buf_ro) return decode_buf_wo - decode_buf_ro;
        return buf_size - (decode_buf_ro - decode_buf_wo);
    };
  }

  size_t decode_buf_avail_len() { return buf_size - decode_buf_data_len(); }

  void mark_pkt_decoded() {
    if (decode_buf_data_len() < BTM_MSBC_PKT_LEN) {
      log::error("Trying to mark read offset beyond write offset.");
      return;
    }

    incr_buf_offset(decode_buf_ro, decode_buf_ro_mirror, buf_size,
                    BTM_MSBC_PKT_LEN);
  }

  size_t write(const std::vector<uint8_t>& input) {
    if (input.size() > decode_buf_avail_len()) {
      return 0;
    }

    if (buf_size - decode_buf_wo > input.size()) {
      std::copy(input.begin(), input.end(), msbc_decode_buf + decode_buf_wo);
    } else {
      std::copy(input.begin(), input.begin() + buf_size - decode_buf_wo,
                msbc_decode_buf + decode_buf_wo);
      std::copy(input.begin() + buf_size - decode_buf_wo, input.end(),
                msbc_decode_buf);
    }

    incr_buf_offset(decode_buf_wo, decode_buf_wo_mirror, buf_size,
                    input.size());
    return input.size();
  }

  const uint8_t* find_msbc_pkt_head() {
    if (read_corrupted) {
      read_corrupted = false;
      return nullptr;
    }

    size_t rp = 0;
    size_t data_len = decode_buf_data_len();
    while (rp < BTM_MSBC_PKT_LEN && data_len - rp >= BTM_MSBC_PKT_LEN) {
      if ((msbc_decode_buf[(decode_buf_ro + rp) % buf_size] !=
           BTM_MSBC_H2_HEADER_0) ||
          (!verify_h2_header_seq_num(
              msbc_decode_buf[(decode_buf_ro + rp + 1) % buf_size])) ||
          (msbc_decode_buf[(decode_buf_ro + rp + 2) % buf_size] !=
           BTM_MSBC_SYNC_WORD)) {
        rp++;
        continue;
      }

      if (rp != 0) {
        log::warn("Skipped {} bytes of mSBC data ahead of a valid mSBC frame",
                  (unsigned long)rp);
        incr_buf_offset(decode_buf_ro, decode_buf_ro_mirror, buf_size, rp);
      }

      // Get the frame head.
      if (buf_size - decode_buf_ro >= BTM_MSBC_PKT_LEN) {
        return &msbc_decode_buf[decode_buf_ro];
      }

      std::copy(msbc_decode_buf + decode_buf_ro, msbc_decode_buf + buf_size,
                packet_buf);
      std::copy(msbc_decode_buf,
                msbc_decode_buf + BTM_MSBC_PKT_LEN - (buf_size - decode_buf_ro),
                packet_buf + (buf_size - decode_buf_ro));
      return packet_buf;
    }

    return nullptr;
  }

  /* Fill in the mSBC header and update the buffer's write offset to guard the
   * buffer space to be written. Return a pointer to the start of mSBC packet's
   * body for the caller to fill the encoded mSBC data if there is enough space
   * in the buffer to fill in a new packet, otherwise return a nullptr. */
  uint8_t* fill_msbc_pkt_template() {
    uint8_t* wp = &msbc_encode_buf[encode_buf_wo];
    if (buf_size - encode_buf_wo < BTM_MSBC_PKT_LEN) {
      log::debug("Packet queue can't accommodate more packets.");
      return nullptr;
    }

    wp[0] = BTM_MSBC_H2_HEADER_0;
    wp[1] = btm_h2_header_frames_count[num_encoded_msbc_pkts % 4];
    encode_buf_wo += BTM_MSBC_PKT_LEN;

    num_encoded_msbc_pkts++;
    return wp + BTM_MSBC_H2_HEADER_LEN;
  }

  size_t mark_pkt_dequeued() {
    if (encode_buf_wo - encode_buf_ro < packet_size) return 0;

    encode_buf_ro += packet_size;
    if (encode_buf_ro == encode_buf_wo) {
      encode_buf_ro = 0;
      encode_buf_wo = 0;
    }

    return packet_size;
  }

  const uint8_t* sco_pkt_read_ptr() {
    if (encode_buf_wo - encode_buf_ro < packet_size) {
      log::debug("Insufficient data to dequeue. buf_wo:{}, buf_ro:{}",
                 encode_buf_wo, encode_buf_ro);
      return nullptr;
    }

    return &msbc_encode_buf[encode_buf_ro];
  }
};

static tBTM_MSBC_INFO* msbc_info = nullptr;

size_t init(size_t pkt_size) {
  GetInterfaceToProfiles()->msbcCodec->initialize();

  if (msbc_info) {
    log::warn("Re-initiating mSBC buffer that is active or not cleaned");
    msbc_info->deinit();
    osi_free(msbc_info);
  }

  msbc_info = (tBTM_MSBC_INFO*)osi_calloc(sizeof(*msbc_info));
  return msbc_info->init(pkt_size);
}

void cleanup() {
  GetInterfaceToProfiles()->msbcCodec->cleanup();

  if (msbc_info == nullptr) return;

  msbc_info->deinit();
  osi_free_and_reset((void**)&msbc_info);
}

bool fill_plc_stats(int* num_decoded_frames, double* packet_loss_ratio) {
  if (msbc_info == NULL || num_decoded_frames == NULL ||
      packet_loss_ratio == NULL)
    return false;

  int decoded_frames = msbc_info->plc->get_num_decoded_frames();
  int lost_frames = msbc_info->plc->get_num_lost_frames();
  if (decoded_frames <= 0 || lost_frames < 0 || lost_frames > decoded_frames)
    return false;

  *num_decoded_frames = decoded_frames;
  *packet_loss_ratio = (double)lost_frames / decoded_frames;
  return true;
}

bool enqueue_packet(const std::vector<uint8_t>& data, bool corrupted) {
  if (msbc_info == nullptr) {
    log::warn("mSBC buffer uninitialized or cleaned");
    return false;
  }

  if (data.size() != msbc_info->packet_size) {
    log::warn(
        "Ignoring the coming packet with size {} that is inconsistent with the "
        "HAL reported packet size {}",
        (unsigned long)data.size(), (unsigned long)msbc_info->packet_size);
    return false;
  }

  msbc_info->read_corrupted |= corrupted;
  if (msbc_info->write(data) != data.size()) {
    return false;
  }

  return true;
}

size_t decode(const uint8_t** out_data) {
  const uint8_t* frame_head = nullptr;

  if (msbc_info == nullptr) {
    log::warn("mSBC buffer uninitialized or cleaned");
    return 0;
  }

  if (out_data == nullptr) {
    log::warn("Invalid output pointer");
    return 0;
  }

  if (msbc_info->decode_buf_data_len() < BTM_MSBC_PKT_LEN) {
    return 0;
  }

  frame_head = msbc_info->find_msbc_pkt_head();
  if (frame_head == nullptr) {
    /* Done with parsing the raw bytes just read. If we couldn't find a valid
     * mSBC frame head, we shall treat the existing BTM_MSBC_PKT_LEN length
     * of mSBC data as a corrupted packet and conduct the PLC. */
    goto packet_loss;
  }

  if (!GetInterfaceToProfiles()->msbcCodec->decodePacket(
          frame_head, msbc_info->decoded_pcm_buf,
          sizeof(msbc_info->decoded_pcm_buf))) {
    goto packet_loss;
  }

  msbc_info->plc->handle_good_frames(msbc_info->decoded_pcm_buf);
  msbc_info->pkt_status->update(false);
  *out_data = (const uint8_t*)msbc_info->decoded_pcm_buf;
  msbc_info->mark_pkt_decoded();
  return BTM_MSBC_CODE_SIZE;

packet_loss:
  msbc_info->plc->handle_bad_frames(out_data);
  msbc_info->pkt_status->update(true);
  msbc_info->mark_pkt_decoded();
  return BTM_MSBC_CODE_SIZE;
}

size_t encode(int16_t* data, size_t len) {
  uint8_t* pkt_body = nullptr;
  uint32_t encoded_size = 0;
  if (msbc_info == nullptr) {
    log::warn("mSBC buffer uninitialized or cleaned");
    return 0;
  }

  if (data == nullptr) {
    log::warn("Invalid data to encode");
    return 0;
  }

  if (len < BTM_MSBC_CODE_SIZE) {
    return 0;
  }

  pkt_body = msbc_info->fill_msbc_pkt_template();
  if (pkt_body == nullptr) {
    return 0;
  }

  encoded_size =
      GetInterfaceToProfiles()->msbcCodec->encodePacket(data, pkt_body);
  if (encoded_size != BTM_MSBC_PKT_FRAME_LEN) {
    log::warn("Encoding invalid packet size: {}", (unsigned long)encoded_size);
    std::copy(&btm_msbc_zero_packet[BTM_MSBC_H2_HEADER_LEN],
              std::end(btm_msbc_zero_packet), pkt_body);
  }

  return BTM_MSBC_CODE_SIZE;
}

size_t dequeue_packet(const uint8_t** output) {
  if (msbc_info == nullptr) {
    log::warn("mSBC buffer uninitialized or cleaned");
    return 0;
  }

  if (output == nullptr) {
    log::warn("Invalid output pointer");
    return 0;
  }

  *output = msbc_info->sco_pkt_read_ptr();
  if (*output == nullptr) {
    return 0;
  }

  return msbc_info->mark_pkt_dequeued();
}

tBTM_SCO_PKT_STATUS* get_pkt_status() {
  if (msbc_info == nullptr) {
    return nullptr;
  }
  return msbc_info->pkt_status;
}

}  // namespace wbs

// TODO(b/269970706): fill `pkt_status` and allow `debug_dump`
namespace swb {

/* Second octet of H2 header is composed by 4 bits fixed 0x8 and 4 bits
 * sequence number 0000, 0011, 1100, 1111. */
constexpr uint8_t btm_h2_header_frames_count[] = {0x08, 0x38, 0xc8, 0xf8};

/* Supported SCO packet sizes for LC3. The SWB-LC3 frame parsing
 * code ties to limited packet size values. Specifically list them out
 * to check against when setting packet size. The first entry is the default
 * value as a fallback. */
constexpr size_t btm_swb_supported_pkt_size[] = {BTM_LC3_PKT_LEN, 72, 0};

/* Buffer size should be set to least common multiple of SCO packet size and
 * BTM_LC3_PKT_LEN for optimizing buffer copy. */
constexpr size_t btm_swb_lc3_buffer_size[] = {BTM_LC3_PKT_LEN, 360, 0};

/* Define the structure that contains LC3 data */
struct tBTM_LC3_INFO {
  size_t packet_size; /* SCO LC3 packet size supported by lower layer */
  size_t buf_size; /* The size of the buffer, determined by the packet_size. */

  uint8_t* lc3_decode_buf; /* Buffer to store LC3 packets to decode */
  size_t decode_buf_wo;    /* Write offset of the decode buffer */
  size_t decode_buf_ro;    /* Read offset of the decode buffer */
  bool read_corrupted;     /* If the current LC3 packet read is corrupted */

  uint8_t* lc3_encode_buf; /* Buffer to store the encoded SCO packets */
  size_t encode_buf_wo;    /* Write offset of the encode buffer */
  size_t encode_buf_ro;    /* Read offset of the encode buffer */

  int16_t decoded_pcm_buf[BTM_LC3_FS]; /* Buffer to store decoded PCM */

  uint8_t num_encoded_lc3_pkts; /* Number of the encoded LC3 packets */

  tBTM_SCO_PKT_STATUS* pkt_status; /* Record of LC3 packet status */

  static size_t get_supported_packet_size(size_t pkt_size,
                                          size_t* buffer_size) {
    int i;
    for (i = 0; btm_swb_supported_pkt_size[i] != 0 &&
                btm_swb_supported_pkt_size[i] != pkt_size;
         i++)
      ;
    /* In case of unsupported value, error log and fallback to
     * BTM_LC3_PKT_LEN(60). */
    if (btm_swb_supported_pkt_size[i] == 0) {
      log::warn("Unsupported packet size {}", (unsigned long)pkt_size);
      i = 0;
    }

    if (buffer_size) {
      *buffer_size = btm_swb_lc3_buffer_size[i];
    }
    return btm_swb_supported_pkt_size[i];
  }

  bool verify_h2_header_seq_num(const uint8_t num) {
    for (int i = 0; i < 4; i++) {
      if (num == btm_h2_header_frames_count[i]) {
        return true;
      }
    }
    return false;
  }

 public:
  size_t init(size_t pkt_size) {
    decode_buf_wo = 0;
    decode_buf_ro = 0;
    encode_buf_wo = 0;
    encode_buf_ro = 0;

    pkt_size = get_supported_packet_size(pkt_size, &buf_size);
    if (pkt_size == packet_size) return packet_size;
    packet_size = pkt_size;

    if (lc3_decode_buf) osi_free(lc3_decode_buf);
    lc3_decode_buf = (uint8_t*)osi_calloc(buf_size);

    if (lc3_encode_buf) osi_free(lc3_encode_buf);
    lc3_encode_buf = (uint8_t*)osi_calloc(buf_size);

    if (pkt_status) osi_free(pkt_status);
    pkt_status = (tBTM_SCO_PKT_STATUS*)osi_calloc(sizeof(*pkt_status));
    pkt_status->init();

    return packet_size;
  }

  void deinit() {
    if (lc3_decode_buf) osi_free(lc3_decode_buf);
    if (lc3_encode_buf) osi_free(lc3_encode_buf);
    if (pkt_status) osi_free_and_reset((void**)&pkt_status);
  }

  size_t decodable() { return decode_buf_wo - decode_buf_ro; }

  uint8_t* fill_lc3_pkt_template() {
    uint8_t* wp = &lc3_encode_buf[encode_buf_wo];
    if (buf_size - encode_buf_wo < BTM_LC3_PKT_LEN) {
      log::debug("Packet queue can't accommodate more packets.");
      return nullptr;
    }

    wp[0] = BTM_LC3_H2_HEADER_0;
    wp[1] = btm_h2_header_frames_count[num_encoded_lc3_pkts % 4];
    encode_buf_wo += BTM_LC3_PKT_LEN;

    num_encoded_lc3_pkts++;
    return wp + BTM_LC3_H2_HEADER_LEN;
  }

  void mark_pkt_decoded() {
    if (decode_buf_ro + BTM_LC3_PKT_LEN > decode_buf_wo) {
      log::error("Trying to mark read offset beyond write offset.");
      return;
    }

    decode_buf_ro += BTM_LC3_PKT_LEN;
    if (decode_buf_ro == decode_buf_wo) {
      decode_buf_ro = 0;
      decode_buf_wo = 0;
    }
  }

  size_t write(const std::vector<uint8_t>& input) {
    if (input.size() > buf_size - decode_buf_wo) {
      return 0;
    }

    std::copy(input.begin(), input.end(), lc3_decode_buf + decode_buf_wo);
    decode_buf_wo += input.size();
    return input.size();
  }

  const uint8_t* find_lc3_pkt_head() {
    if (read_corrupted) {
      read_corrupted = false;
      return nullptr;
    }

    size_t rp = 0;
    while (rp < BTM_LC3_PKT_LEN &&
           decode_buf_wo - (decode_buf_ro + rp) >= BTM_LC3_PKT_LEN) {
      if ((lc3_decode_buf[decode_buf_ro + rp] != BTM_LC3_H2_HEADER_0) ||
          !verify_h2_header_seq_num(lc3_decode_buf[decode_buf_ro + rp + 1])) {
        rp++;
        continue;
      }

      if (rp != 0) {
        log::warn("Skipped {} bytes of LC3 data ahead of a valid LC3 frame",
                  (unsigned long)rp);
        decode_buf_ro += rp;
      }
      return &lc3_decode_buf[decode_buf_ro];
    }

    return nullptr;
  }

  size_t mark_pkt_dequeued() {
    if (encode_buf_wo - encode_buf_ro < packet_size) return 0;

    encode_buf_ro += packet_size;
    if (encode_buf_ro == encode_buf_wo) {
      encode_buf_ro = 0;
      encode_buf_wo = 0;
    }

    return packet_size;
  }

  const uint8_t* sco_pkt_read_ptr() {
    if (encode_buf_wo - encode_buf_ro < packet_size) {
      log::debug("Insufficient data to dequeue. buf_wo:{}, buf_ro:{}",
                 encode_buf_wo, encode_buf_ro);
      return nullptr;
    }

    return &lc3_encode_buf[encode_buf_ro];
  }
};

static tBTM_LC3_INFO* lc3_info;
static int decoded_frames;
static int lost_frames;

size_t init(size_t pkt_size) {
  GetInterfaceToProfiles()->lc3Codec->initialize();

  decoded_frames = 0;
  lost_frames = 0;

  if (lc3_info) {
    log::warn("Re-initiating LC3 buffer that is active or not cleaned");
    lc3_info->deinit();
    osi_free(lc3_info);
  }

  lc3_info = (tBTM_LC3_INFO*)osi_calloc(sizeof(*lc3_info));
  return lc3_info->init(pkt_size);
}

void cleanup() {
  GetInterfaceToProfiles()->lc3Codec->cleanup();

  decoded_frames = 0;
  lost_frames = 0;

  if (lc3_info == nullptr) return;

  lc3_info->deinit();
  osi_free_and_reset((void**)&lc3_info);
}

bool fill_plc_stats(int* num_decoded_frames, double* packet_loss_ratio) {
  if (lc3_info == NULL || num_decoded_frames == NULL ||
      packet_loss_ratio == NULL)
    return false;

  if (decoded_frames <= 0 || lost_frames < 0 || lost_frames > decoded_frames)
    return false;

  *num_decoded_frames = decoded_frames;
  *packet_loss_ratio = (double)lost_frames / decoded_frames;
  return true;
}

bool enqueue_packet(const std::vector<uint8_t>& data, bool corrupted) {
  if (lc3_info == nullptr) {
    log::warn("LC3 buffer uninitialized or cleaned");
    return false;
  }

  if (data.size() != lc3_info->packet_size) {
    log::warn(
        "Ignoring the coming packet with size {} that is inconsistent with the "
        "HAL reported packet size {}",
        (unsigned long)data.size(), (unsigned long)lc3_info->packet_size);
    return false;
  }

  lc3_info->read_corrupted |= corrupted;
  if (lc3_info->write(data) != data.size()) {
    return false;
  }

  return true;
}

size_t decode(const uint8_t** out_data) {
  const uint8_t* frame_head = nullptr;

  if (lc3_info == nullptr) {
    log::warn("LC3 buffer uninitialized or cleaned");
    return 0;
  }

  if (out_data == nullptr) {
    log::warn("Invalid output pointer");
    return 0;
  }

  if (lc3_info->decodable() < BTM_LC3_PKT_LEN) {
    return 0;
  }

  frame_head = lc3_info->find_lc3_pkt_head();

  bool plc_conducted = !GetInterfaceToProfiles()->lc3Codec->decodePacket(
      frame_head, lc3_info->decoded_pcm_buf, sizeof(lc3_info->decoded_pcm_buf));

  lc3_info->pkt_status->update(plc_conducted);

  ++decoded_frames;
  lost_frames += plc_conducted;

  *out_data = (const uint8_t*)lc3_info->decoded_pcm_buf;
  lc3_info->mark_pkt_decoded();

  return BTM_LC3_CODE_SIZE;
}

size_t encode(int16_t* data, size_t len) {
  uint8_t* pkt_body = nullptr;
  if (lc3_info == nullptr) {
    log::warn("LC3 buffer uninitialized or cleaned");
    return 0;
  }

  if (data == nullptr) {
    log::warn("Invalid data to encode");
    return 0;
  }

  if (len < BTM_LC3_CODE_SIZE) {
    return 0;
  }

  pkt_body = lc3_info->fill_lc3_pkt_template();
  if (pkt_body == nullptr) {
    return 0;
  }

  return GetInterfaceToProfiles()->lc3Codec->encodePacket(data, pkt_body);
}

size_t dequeue_packet(const uint8_t** output) {
  if (lc3_info == nullptr) {
    log::warn("LC3 buffer uninitialized or cleaned");
    return 0;
  }

  if (output == nullptr) {
    log::warn("Invalid output pointer");
    return 0;
  }

  *output = lc3_info->sco_pkt_read_ptr();
  if (*output == nullptr) {
    return 0;
  }

  return lc3_info->mark_pkt_dequeued();
}

tBTM_SCO_PKT_STATUS* get_pkt_status() {
  if (lc3_info == nullptr) {
    return nullptr;
  }
  return lc3_info->pkt_status;
}
}  // namespace swb

}  // namespace sco
}  // namespace audio
}  // namespace bluetooth
