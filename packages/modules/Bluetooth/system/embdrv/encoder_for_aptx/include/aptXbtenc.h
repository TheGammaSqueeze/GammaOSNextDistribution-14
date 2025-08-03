/**
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
/*------------------------------------------------------------------------------
 *
 *  This file exposes a public interface to allow clients to invoke aptX
 *  encoding on 4 new PCM samples, generating 2 new codeword (one for the
 *  left channel and one for the right channel).
 *
 *----------------------------------------------------------------------------*/

#ifndef APTXBTENC_H
#define APTXBTENC_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifdef _DLLEXPORT
#define APTXBTENCEXPORT __declspec(dllexport)
#else
#define APTXBTENCEXPORT
#endif

/* SizeofAptxbtenc returns the size (in byte) of the memory
 * allocation required to store the state of the encoder */
APTXBTENCEXPORT int SizeofAptxbtenc(void);

/* aptxbtenc_version can be used to extract the version number
 * of the aptX encoder */
APTXBTENCEXPORT const char* aptxbtenc_version(void);

/* aptxbtenc_init is used to initialise the encoder structure.
 * _state should be a pointer to the encoder structure (stereo).
 * endian represent the endianness of the output data
 * (0=little endian. Big endian otherwise)
 * The function returns 1 if an error occurred during the initialisation.
 * The function returns 0 if no error occurred during the initialisation. */
APTXBTENCEXPORT int aptxbtenc_init(void* _state, short endian);

/* aptxbtenc_setsync_mode is used to initialise the sync mode in the encoder
 * state structure. _state should be a pointer to the encoder structure (stereo,
 * though strictly-speaking it is dual channel). 'sync_mode' is an enumerated
 * type  {stereo=0, dualmono=1, no_sync=2} The function returns 0 if no error
 * occurred during the initialisation. */
APTXBTENCEXPORT int aptxbtenc_setsync_mode(void* _state, int32_t sync_mode);

/* StereoEncode will take 8 audio samples (16-bit per sample)
 * and generate one 32-bit codeword with autosync inserted. */
APTXBTENCEXPORT int aptxbtenc_encodestereo(void* _state, void* _pcmL,
                                           void* _pcmR, void* _buffer);

#ifdef __cplusplus
}  //  /extern "C"
#endif

#endif  // APTXBTENC_H
