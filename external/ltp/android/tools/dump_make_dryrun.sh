#!/usr/bin/env bash
#
# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

[ -z $TOOLS_DIR ] && TOOLS_DIR=$(realpath $(dirname $0))
[ -z $LTP_ROOT ] && LTP_ROOT=$(realpath $TOOLS_DIR/../..)
[ -z $TOOLS_DUMP_DIR ] && TOOLS_DUMP_DIR=$(realpath $TOOLS_DIR/dump)

mkdir -p $TOOLS_DUMP_DIR

if ! [ -f $LTP_ROOT/include/config.h ]; then
  echo ""
  echo "LTP has not been configured."
  echo ""
  echo "Executing \"cd $LTP_ROOT; make autotools\""
  (cd $LTP_ROOT; make autotools > /dev/null)
  echo ""
  echo "Executing \"cd $LTP_ROOT; ./configure\""
  (cd $LTP_ROOT; ./configure > /dev/null)

  OUTPUT=$TOOLS_DUMP_DIR/config.h.dump
  cat $LTP_ROOT/include/config.h > $OUTPUT
fi

MAKE_FLAGS="-j1 --dry-run V=1"

OUTPUT=$TOOLS_DUMP_DIR/make_dry_run.dump
CMD="make $MAKE_FLAGS -C $LTP_ROOT/testcases"
echo ""
echo "Dumping output to $OUTPUT from command '$CMD'"
$CMD > $OUTPUT

OUTPUT=$TOOLS_DUMP_DIR/make_install_dry_run.dump
CMD="make $MAKE_FLAGS -C $LTP_ROOT/testcases install"
echo ""
echo "Dumping output to $OUTPUT from command '$CMD'"
$CMD > $OUTPUT

echo ""
echo "Distclean $LTP_ROOT ..."
make -C $LTP_ROOT distclean > /dev/null

