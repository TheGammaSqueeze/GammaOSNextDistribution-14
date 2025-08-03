add_library(
    OpenGLESDispatch
    EGLDispatch.cpp
    gles1_stubbed_in_translator_namespace.cpp
    gles2_stubbed_in_translator_namespace.cpp
    GLESv1Dispatch.cpp
    GLESv2Dispatch.cpp
    OpenGLDispatchLoader.cpp
    StaticDispatch.cpp)
if (NOT MSVC)
    target_compile_options(OpenGLESDispatch PRIVATE -fvisibility=default)
endif()
target_link_libraries(OpenGLESDispatch PRIVATE aemu-base.headers)
target_link_libraries(OpenGLESDispatch PUBLIC gles1_dec gles2_dec apigen-codec-common)
target_link_libraries(OpenGLESDispatch PRIVATE EGL_translator_static)
