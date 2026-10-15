#!/bin/sh
# Builds the host harness (tools/ctrhost) against Mesa EGL/GLES2.
set -e
cd "$(dirname "$0")"
mkdir -p out
for f in stbimpl.cpp; do [ out/stbimpl.o -nt $f ] || g++ -O1 -c $f -o out/stbimpl.o; done
g++ -std=c++17 -O1 -g -Wall -Wno-unused-function -DGL_GLEXT_PROTOTYPES -I. -I../.. \
  ctrhost.cpp ../../NanoCtrPack.cpp ../../NanoCtrLayout.cpp ../../NanoCtrRender.cpp out/stbimpl.o \
  -I/usr/include/freetype2 -lfreetype -lEGL -lGLESv2 -o out/ctrhost
g++ -std=c++17 -O1 -g -Wall -Wno-unused-function -DGL_GLEXT_PROTOTYPES -DCTRHOST_NO_MAIN -I. -I../.. \
  homehost.cpp ctrhost.cpp ../../NanoCtrPack.cpp ../../NanoCtrLayout.cpp ../../NanoCtrRender.cpp ../../NanoCtrHome.cpp ../../NanoCtrTop.cpp ../../NanoCtr3d.cpp ../../NanoCtrPanel.cpp ../../NanoCtrTheme.cpp ../../NanoCtrArt.cpp ../../NanoCtrRom.cpp out/stbimpl.o \
  -I/usr/include/freetype2 -lfreetype -lEGL -lGLESv2 -lz -o out/ctrhome
