/******************************************************************************
 *
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *****************************************************************************
 * Originally developed and contributed by Ittiam Systems Pvt. Ltd, Bangalore
 */

#include <stdint.h>
#include <string.h>
extern "C" {
#include <Tremolo/codec_internal.h>

int _vorbis_unpack_books(vorbis_info *vi, oggpack_buffer *opb);
int _vorbis_unpack_info(vorbis_info *vi, oggpack_buffer *opb);
int _vorbis_unpack_comment(vorbis_comment *vc, oggpack_buffer *opb);
}

constexpr int16_t kMaxNumSamplesPerChannel = 8192;
constexpr size_t kVorbisHeaderlength = 7;

class Codec {
 public:
  Codec() = default;
  ~Codec() { deInitDecoder(); }
  bool initDecoder();
  void decodeFrames(const uint8_t *data, size_t size);
  void deInitDecoder();

 private:
  bool mInfoUnpacked = false;
  bool mBooksUnpacked = false;
  int32_t mNumFramesLeftOnPage = -1;
  vorbis_dsp_state *mState = nullptr;
  vorbis_info *mVi = nullptr;
};

bool Codec::initDecoder() {
  mVi = new vorbis_info{};
  if (!mVi) {
    return false;
  }
  vorbis_info_clear(mVi);

  mState = new vorbis_dsp_state{};
  if (!mState) {
    return false;
  }
  vorbis_dsp_clear(mState);

  mNumFramesLeftOnPage = -1;
  mInfoUnpacked = false;
  mBooksUnpacked = false;

  return true;
}

static void makeBitReader(const uint8_t *data, size_t size, ogg_buffer *buf, ogg_reference *ref,
                          oggpack_buffer *bits) {
  buf->data = const_cast<uint8_t *>(data);
  buf->size = size;
  buf->refcount = 1;
  buf->ptr.owner = nullptr;

  ref->buffer = buf;
  ref->begin = 0;
  ref->length = size;
  ref->next = nullptr;

  oggpack_readinit(bits, ref);
}

void Codec::decodeFrames(const uint8_t *data, size_t size) {
  /* Decode vorbis headers only once */
  while (size > 0) {
    if (size > kVorbisHeaderlength && (!memcmp(&data[1], "vorbis", 6)) &&
        (!mInfoUnpacked || !mBooksUnpacked)) {
      if ((data[0] == 1) || (data[0] == 5)) {
        ogg_buffer buf;
        ogg_reference ref;
        oggpack_buffer bits;
        /* skip kVorbisHeaderlength <type + "vorbis"> bytes */
        makeBitReader(data + kVorbisHeaderlength, size - kVorbisHeaderlength, &buf, &ref, &bits);
        if (data[0] == 1) {
          // release any memory that vorbis_info_init will blindly overwrite
          vorbis_info_clear(mVi);
          vorbis_info_init(mVi);
          if (0 != _vorbis_unpack_info(mVi, &bits)) {
            return;
          }
          mInfoUnpacked = true;
        } else { /* data[0] == 5*/
          if (!mInfoUnpacked) {
            return;
          }
          if (0 != _vorbis_unpack_books(mVi, &bits)) {
            return;
          }
          // release any memory that vorbis_dsp_init will blindly overwrite
          vorbis_dsp_clear(mState);
          if (0 != vorbis_dsp_init(mState, mVi)) {
            return;
          }
          mBooksUnpacked = true;
          data += kVorbisHeaderlength;
          size -= kVorbisHeaderlength;
          break;
        }
      }
    }
    ++data;
    --size;
  }

  if (!mInfoUnpacked || !mBooksUnpacked) {
    return;
  }

  int32_t numPageFrames = 0;
  if (size < sizeof(numPageFrames)) {
    return;
  }
  memcpy(&numPageFrames, data + size - sizeof(numPageFrames), sizeof(numPageFrames));
  size -= sizeof(numPageFrames);
  if (numPageFrames >= 0) {
    mNumFramesLeftOnPage = numPageFrames;
  }

  ogg_buffer buf;
  buf.data = const_cast<unsigned char *>(data);
  buf.size = size;
  buf.refcount = 1;
  buf.ptr.owner = nullptr;

  ogg_reference ref;
  ref.buffer = &buf;
  ref.begin = 0;
  ref.length = buf.size;
  ref.next = nullptr;

  ogg_packet pack;
  pack.packet = &ref;
  pack.bytes = ref.length;
  pack.b_o_s = 0;
  pack.e_o_s = 0;
  pack.granulepos = 0;
  pack.packetno = 0;

  int ret = vorbis_dsp_synthesis(mState, &pack, 1);
  if (0 == ret) {
    size_t maxSamplesInBuffer = kMaxNumSamplesPerChannel * mVi->channels;
    size_t outCapacity = maxSamplesInBuffer * sizeof(int16_t);
    int16_t outputBuf[outCapacity];
    vorbis_dsp_pcmout(mState, outputBuf, kMaxNumSamplesPerChannel);
  }
}

void Codec::deInitDecoder() {
  if (mState) {
    vorbis_dsp_clear(mState);
    delete mState;
    mState = nullptr;
  }

  if (mVi) {
    vorbis_info_clear(mVi);
    delete mVi;
    mVi = nullptr;
  }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  if (size < kVorbisHeaderlength + 1) { /* 7 bytes for header , at least 1 byte for data */
    return 0;
  }
  Codec *codec = new Codec();
  if (!codec) {
    return 0;
  }
  if (codec->initDecoder()) {
    codec->decodeFrames(data, size);
  }
  delete codec;
  return 0;
}
