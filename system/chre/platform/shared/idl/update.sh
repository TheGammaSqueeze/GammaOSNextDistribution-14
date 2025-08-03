#!/bin/bash

# Check flatc version
if [[ $(flatc --version | grep -Po "(?<=flatc version )([0-9]|\.)*(?=\s|$)") != "1.12.0" ]]; then
echo "[ERROR] flatc version must be 1.12.0"
exit
fi

# Generate the CHRE-side header file
flatc --cpp -o ../include/chre/platform/shared/generated/ --scoped-enums \
  --cpp-ptr-type chre::UniquePtr host_messages.fbs

# Generate the AP-side header file with some extra goodies
flatc --cpp -o ../../../host/common/include/chre_host/generated/ --scoped-enums \
  --gen-mutable --gen-object-api host_messages.fbs
