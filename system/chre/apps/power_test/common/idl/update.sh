#!/bin/bash

# NOTE: Ensure you use flatc version 1.6.0 here!
if [[ $(flatc --version | grep -Po "(?<=flatc version )([0-9]|\.)*(?=\s|$)") != "1.6.0" ]]; then
echo "[ERROR] flatc version must be 1.6.0"
exit
fi

# Generate the CHRE-side header file
flatc --cpp -o ../include/generated/ --scoped-enums \
  --cpp-ptr-type chre::UniquePtr chre_power_test.fbs

# Generate the AP-side header file with some extra goodies
flatc --cpp -o ./ --scoped-enums --gen-mutable \
  --gen-object-api chre_power_test.fbs
