#!/bin/sh

# Known Working configs for AOSP
sed -i 's|/\* #undef CAN_HARDLINK_SPECIAL \*/|#define CAN_HARDLINK_SPECIAL 1|g;' config.h
sed -i 's|/\* #undef CAN_HARDLINK_SYMLINK \*/|#define CAN_HARDLINK_SYMLINK 1|g;' config.h
sed -i 's|/\* #undef HAVE_SOCKETPAIR \*/|#define HAVE_SOCKETPAIR 1|g;' config.h
sed -i 's|/\* #undef HAVE_XXHASH_H \*/|#define HAVE_XXHASH_H 1|g;' config.h
sed -i 's|/\* #undef MKNOD_CREATES_FIFOS \*/|#define MKNOD_CREATES_FIFOS 1|g;' config.h
sed -i 's|/\* #undef SUPPORT_LZ4 \*/|#define SUPPORT_LZ4 1|g;' config.h
sed -i 's|/\* #undef SUPPORT_XXHASH \*/|#define SUPPORT_XXHASH 1|g;' config.h
sed -i 's|/\* #undef SUPPORT_ZSTD \*/|#define SUPPORT_ZSTD 1|g;' config.h
sed -i 's|/\* #undef USE_OPENSSL \*/|#define USE_OPENSSL 1|g;' config.h
