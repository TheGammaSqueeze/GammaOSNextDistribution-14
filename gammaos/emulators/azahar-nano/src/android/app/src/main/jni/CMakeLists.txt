add_library(citra-android SHARED
    android_common/android_common.cpp
    android_common/android_common.h
    applets/mii_selector.cpp
    applets/mii_selector.h
    applets/swkbd.cpp
    applets/swkbd.h
    input_manager.cpp
    input_manager.h
    camera/ndk_camera.cpp
    camera/ndk_camera.h
    camera/still_image_camera.cpp
    camera/still_image_camera.h
    cheats/cheat.cpp
    cheats/cheat.h
    cheats/cheat_engine.cpp
    config.cpp
    config.h
    default_ini.h
    emu_window/emu_window.cpp
    emu_window/emu_window.h
    game_info.cpp
    id_cache.cpp
    id_cache.h
    jni_setting_keys.cpp
    multiplayer.cpp
    multiplayer.h
    native.cpp
    ndk_motion.cpp
    ndk_motion.h
    system_save_game.cpp
    native_log.cpp
)

target_link_libraries(citra-android PRIVATE audio_core citra_common citra_core input_common network)
target_link_libraries(citra-android PRIVATE android camera2ndk inih jnigraphics log mediandk yuv)

if (ENABLE_OPENGL)
    target_sources(citra-android PRIVATE
        emu_window/emu_window_gl.cpp
        emu_window/emu_window_gl.h
    )
    target_link_libraries(citra-android PRIVATE EGL glad)
endif()
if (ENABLE_VULKAN)
    target_sources(citra-android PRIVATE
        emu_window/emu_window_vk.cpp
        emu_window/emu_window_vk.h
    )
    if ("arm64" IN_LIST ARCHITECTURE)
        target_link_libraries(citra-android PRIVATE adrenotools)
    endif()
endif()

set(CPACK_PACKAGE_EXECUTABLES ${CPACK_PACKAGE_EXECUTABLES} citra-android)

# azahar-nano: the same native layer without the JVM, exposed through a C ABI
# (nano/azahar_nano.h) for the GammaOS Nano frontend.
add_library(azahar-nano-core SHARED
    nano/azahar_nano.cpp
    nano/azahar_nano.h
    config.cpp
    config.h
    default_ini.h
    emu_window/emu_window.cpp
    emu_window/emu_window.h
    input_manager.cpp
    input_manager.h
    ndk_motion.cpp
    ndk_motion.h
)
target_include_directories(azahar-nano-core PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(azahar-nano-core PRIVATE audio_core citra_common citra_core input_common network)
target_link_libraries(azahar-nano-core PRIVATE android inih log mediandk)
if (ENABLE_OPENGL)
    target_sources(azahar-nano-core PRIVATE emu_window/emu_window_gl.cpp emu_window/emu_window_gl.h)
    target_link_libraries(azahar-nano-core PRIVATE EGL glad)
endif()
if (ENABLE_VULKAN)
    target_sources(azahar-nano-core PRIVATE emu_window/emu_window_vk.cpp emu_window/emu_window_vk.h)
    if ("arm64" IN_LIST ARCHITECTURE)
        target_link_libraries(azahar-nano-core PRIVATE adrenotools)
    endif()
endif()
