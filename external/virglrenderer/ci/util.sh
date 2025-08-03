#!/bin/bash

# Try to find locally defined paths for external resources
CI_CONFIG="$HOME/.virgl-ci.conf"
if [ -f "$CI_CONFIG" ]; then
   . "$CI_CONFIG"
fi

# Test paths for existence, if found assign to variable
# try_paths() MESA_PATH ../mesa /mesa
try_paths()
{
   VARIABLE_NAME=$1
   shift
   for VARIABLE_PATH in "$@"
   do
      if [ -d "$VARIABLE_PATH" ]; then
         VARIABLE_PATH="$(realpath $VARIABLE_PATH)"
         eval "export $VARIABLE_NAME=\"$VARIABLE_PATH\""
         return
      fi
   done
}

PROJECT_PATH="$(dirname $(readlink -f "$0"))/../.."

try_paths VIRGL_PATH \
   "$VIRGL_PATH" \
   "/virglrenderer" \
   "${PROJECT_PATH}/virglrenderer"

try_paths CTS_PATH \
   "$CTS_PATH" \
   "/VK-GL-CTS" \
   "${PROJECT_PATH}/VK-GL-CTS"

try_paths MESA_PATH \
   "$MESA_PATH" \
   "/local_mesa" \
   "${PROJECT_PATH}/mesa"