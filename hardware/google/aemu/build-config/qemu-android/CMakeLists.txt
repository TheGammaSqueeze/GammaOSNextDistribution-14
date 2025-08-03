# aemu-base overrides:
#   - aemu-base-srcs: aemu-base source files
#   - aemu-base-test-srcs: aemu-base test source files
set(BUILD_STANDALONE ON PARENT_SCOPE)
set(GFXSTREAM_BASE_LIB aemu-base PARENT_SCOPE)
set(GFXSTREAM_HOST_COMMON_LIB aemu-host-common PARENT_SCOPE)
add_library(lz4_static ALIAS lz4)
