#!/bin/bash

# Copyright 2012 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Script to run the gesture regression test and check if there is any
# regression for each submit.

# Set current directory to the project one and load the common script.
pushd . >/dev/null
cd "$(dirname "$(readlink -f "$0")")/.."
. "../../scripts/common.sh" || exit 1

update_chroot_library() {
  library=$1
  version=$2
  project=$3
  info "Check chroot $library version ${version}..."

  if ! grep -q ${version} /usr/lib/${library} ; then
    info "Update the library ${library} under chroot.."
    sudo emerge -q ${project}
    info grep ${version} /usr/lib/${library}
    if ! grep -q ${version} /usr/lib/${library} ; then
      die_notrace "Can not install ${library} successfully"
    fi
  fi
}

install_regression_test_suite() {
  info "Install regression test suite first..."
  sudo emerge -q gestures chromeos-base/libevdev utouch-evemu -j3
  pushd ~/trunk/src/platform/touchpad-tests >/dev/null
  make -j${NUM_JOBS} -s all
  sudo make -s local-install
  popd >/dev/null
}

run_regression_tests() {
  info "Running regression tests..."
  if ! type touchtests > /dev/null; then
    die_notrace \
      "The touchtests aren't installed in your chroot. Please install them: "\
      "https://chromium.googlesource.com/chromiumos/platform/touchpad-tests/+/HEAD/README.md#Setting-up"
  fi
  if ! touchtests --presubmit --ref tools/touchtests-report.json; then
    die_notrace "Regression tests failed."
  fi
}

check_test_setup() {
  if [[ ! -e  /usr/lib/libgestures.so ]]; then
    install_regression_test_suite
  else
    update_chroot_library libgestures.so ${libgestures_head_hash} gestures
    update_chroot_library libevdev-cros.so ${libevdev_head_hash} \
      chromeos-base/libevdev
  fi
}

libevdev_head_hash=`cd ../libevdev; git rev-parse HEAD`
libgestures_head_hash=`git rev-parse HEAD`
if [[ ${INSIDE_CHROOT} -ne 1 ]]; then
  if [[ "${PRESUBMIT_COMMIT}" == "${libgestures_head_hash}" ]]; then
    popd >/dev/null
    restart_in_chroot_if_needed "$@"
  fi
else
  cros_workon --host start gestures chromeos-base/libevdev
  check_test_setup
  run_regression_tests
  popd >/dev/null
fi
exit 0
