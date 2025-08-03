add_library(
    gfxstream_etc_headers
    INTERFACE)
target_include_directories(
    gfxstream_etc_headers
    INTERFACE
    include)

add_library(
    gfxstream_etc
    STATIC
    etc.cpp)
target_link_libraries(
    gfxstream_etc
    PUBLIC
    gfxstream_etc_headers)