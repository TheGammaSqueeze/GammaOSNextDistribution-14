/*
 * Copyright (C) 2020 The Android Open Source Project
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

// Separate test for Metadata C API called from C, as gtest doesn't support that

#include <stdlib.h>
#include <audio_utils/Metadata.h>

int main(int argc, char **argv)
{
    (void) argc;
    (void) argv;

    audio_metadata_t *metadata = audio_metadata_create();
    audio_metadata_put(metadata, "i32", (int32_t)1);
    audio_metadata_put(metadata, "i64", (int64_t)2);
    audio_metadata_put(metadata, "float", (float)3.1f);
    audio_metadata_put(metadata, "double", (double)4.11);
    audio_metadata_t *data = audio_metadata_create();
    audio_metadata_put(data, "string", "hello");
    audio_metadata_put(metadata, "data", data);
#if 0   // change to 1 for compile error: use of undeclared identifier 'audio_metadata_put_unknown'
    {
        static const struct complex {
            float re;
            float im;
        } prime = { -5.0, -4.0 };
        audio_metadata_put(metadata, "complex", prime);
    }
#endif
    audio_metadata_destroy(data);

    // TODO Test audio_metadata_get()

    audio_metadata_destroy(metadata);
    return EXIT_SUCCESS;
}
