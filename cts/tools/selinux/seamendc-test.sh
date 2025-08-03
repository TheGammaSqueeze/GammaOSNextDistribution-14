#!/bin/bash
set -e

# Generate the amend policy in cil format.
echo "(type foo)" > test_sepolicy.cil
echo "(typeattribute bar)" >> test_sepolicy.cil
echo "(typeattributeset bar (foo))" >> test_sepolicy.cil
echo "(allow foo bar (file (read)))" >> test_sepolicy.cil

# Generate the definitions file containing (re)definitions of existing types/classes/attributes, and
# of preliminary symbols. This file is needed by seamendc to successfully parse the CIL policy.
echo "(sid test)" > definitions.cil
echo "(sidorder (test))" >> definitions.cil
echo "(class file (read))" >> definitions.cil
echo "(classorder (file))" >> definitions.cil

# Compile binary and amend policies using secilc.
./secilc -m -M true -G -N -c 30 \
  -o sepolicy+test-secilc.binary \
  plat_sepolicy.cil \
  plat_pub_versioned.cil \
  system_ext_sepolicy.cil \
  product_sepolicy.cil \
  vendor_sepolicy.cil \
  odm_sepolicy.cil \
  test_sepolicy.cil

# Compile binary policy and use seamendc to amend the binary file.
./secilc -m -M true -G -N -c 30 \
  -o sepolicy.binary \
  plat_sepolicy.cil \
  plat_pub_versioned.cil \
  system_ext_sepolicy.cil \
  product_sepolicy.cil \
  vendor_sepolicy.cil \
  odm_sepolicy.cil

./seamendc -vv \
  -o sepolicy+test-seamendc.binary \
  -b sepolicy.binary \
  test_sepolicy.cil definitions.cil

# Diff the generated binary policies.
./searchpolicy --allow --libpath libsepolwrap.so sepolicy+test-secilc.binary \
  -s foo > secilc.diff
./searchpolicy --allow --libpath libsepolwrap.so sepolicy+test-seamendc.binary \
  -s foo > seamendc.diff
diff secilc.diff seamendc.diff

./searchpolicy --allow --libpath libsepolwrap.so sepolicy+test-secilc.binary \
  -t foo > secilc.diff
./searchpolicy --allow --libpath libsepolwrap.so sepolicy+test-seamendc.binary \
  -t foo > seamendc.diff
diff secilc.diff seamendc.diff
