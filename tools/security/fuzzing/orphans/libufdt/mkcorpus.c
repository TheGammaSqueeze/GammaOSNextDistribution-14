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

#include <arpa/inet.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

static uint32_t get_file_length(const char *filename) {
  struct stat sb;

  if (stat(filename, &sb) == -1) {
    fprintf(stderr, "stat(%s) failed: %m\n", filename);
    exit(EXIT_FAILURE);
  }

  return sb.st_size;
}

static void append_file(FILE *out, const char *filename) {
  FILE *f = fopen(filename, "rbe");
  uint8_t buf[1024 * 8];

  if (!f) {
    fprintf(stderr, "fopen(%s) failed: %m\n", filename);
    exit(EXIT_FAILURE);
  }

  while (!feof(f)) {
    size_t n = fread(buf, 1, sizeof(buf), f);

    if (fwrite(buf, n, 1, out) != 1) {
      fprintf(stderr, "fwrite() failed: %m\n");
      exit(EXIT_FAILURE);
    }
  }

  fclose(f);
}

int main(int argc, char *argv[]) {
  FILE *out;

  if (argc != 4) {
    fprintf(stderr,
            "Usage: mkcorpus <dtb> <dto> <output>\n"
            "\n"
            "  This concatenates base and overlay file and adds a header to "
            "create an\n"
            "  input that can be used for fuzzing.\n");
    exit(EXIT_FAILURE);
  }

  if (strcmp(argv[3], "-") == 0) {
    out = stdout;
  } else {
    out = fopen(argv[3], "wbe");
    if (!out) {
      fprintf(stderr, "fopen(%s) failed: %m\n", argv[1]);
      exit(EXIT_FAILURE);
    }
  }

  uint32_t len = htonl(get_file_length(argv[1]));

  if (fwrite(&len, sizeof(uint32_t), 1, out) != 1) {
    fprintf(stderr, "fwrite() failed: %m\n");
    exit(EXIT_FAILURE);
  }

  append_file(out, argv[1]);
  append_file(out, argv[2]);

  if (out != stdout) {
    fclose(out);
  }

  return EXIT_SUCCESS;
}

/* END OF FILE */
