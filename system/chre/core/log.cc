/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifdef CHRE_TOKENIZED_LOGGING_ENABLED
#include "chre/platform/log.h"
#include "pw_log_tokenized/config.h"
#include "pw_tokenizer/encode_args.h"
#include "pw_tokenizer/tokenize.h"

// The callback function that must be defined to handle an encoded
// tokenizer message.

void EncodeTokenizedMessage(uint32_t level, pw_tokenizer_Token token,
                            pw_tokenizer_ArgTypes types, ...) {
  va_list args;
  va_start(args, types);
  pw::tokenizer::EncodedMessage<pw::log_tokenized::kEncodingBufferSizeBytes>
      encodedMessage(token, types, args);
  va_end(args);

  chrePlatformEncodedLogToBuffer(static_cast<chreLogLevel>(level),
                                 encodedMessage.data_as_uint8(),
                                 encodedMessage.size());
}

#endif  // CHRE_TOKENIZED_LOGGING_ENABLED
