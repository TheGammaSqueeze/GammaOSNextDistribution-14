#!/bin/bash -u
# -*- coding: utf-8 -*-
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# llvm_bisection_template.sh
#
# This script is meant to be run inside a `git bisect` process, like so:
#
#   $ cd <your llvm-project dir>
#   $ git bisect start
#   $ git bisect bad <your bad ref>
#   $ git bisect good <your good ref>
#   $ git bisect run ~/chromimuos/src/scripts/llvm_bisection_template.sh
#
# This template exists as a "batteries included" LLVM bisection script,
# which will modify the LLVM_NEXT hash to help the mage track down issues
# locally.
#
# Modify the fixme sections below to customize to your bisection use-case.

# FIXME: Replace this for the location of your llvm clone within the chroot.
# We need this for the git history.
LLVM_CLONE_PATH="${HOME}/chromiumos/src/third_party/llvm-project"

main () {
  # Note this builds with USE="llvm-next debug -thinlto -llvm_pgo_use continue-on-patch-failure"
  build_llvm || exit

  # FIXME: Write your actual bisection command here which uses
  # LLVM_NEXT here.
  #
  # Example bisection command:
  #
  #   build_pkg efitools || exit 1
  #
  # You can use build_pkg if you want to emerge a package and print
  # out diagnostics along the way
  #
  #   Fail Example: build_pkg "${MY_PACKAGE}" || exit 1
  #   Skip Example: build_pkg "${MY_PACKAGE}" || exit 125
  #
}

# ---------------------------------------------------------------------

# Current LLVM_NEXT_HASH we're using. Does not need to be set.
CURRENT='UNKNOWN'

logdo () {
  local cmd="${1}"
  shift
  printf '%1 $ %2' "$(date '+%T')" "${cmd}"
  for i in "$@"; do
    printf "'%1'" "${i}"
  done
  printf "\n"
  "${cmd}" "$@"
}

log () {
  echo "$(date '+%T') | $*"
}

build_llvm () {
  cd "${LLVM_CLONE_PATH}" || exit 2  # Exit with error
  local llvm_ebuild_path
  llvm_ebuild_path="$(readlink -f "$(equery which llvm)")"
  CURRENT="$(git rev-parse --short HEAD)"
  log "Current hash=${CURRENT}"
  NEW_LINE="LLVM_NEXT_HASH=\"${CURRENT}\""
  sed -i "s/^LLVM_NEXT_HASH=\".*\"/${NEW_LINE}/" "${llvm_ebuild_path}"

  local logfile="/tmp/build-llvm.${CURRENT}.out"
  log "Writing logs to ${logfile}"
  log "sudo USE='llvm-next debug -thinlto -llvm_use_pgo continue-on-patch-failure'" \
      " emerge sys-devel/llvm"
  logdo sudo USE='llvm-next debug -thinlto -llvm_use_pgo continue-on-patch-failure' emerge \
    sys-devel/llvm \
    &> "${logfile}"
  local emerge_exit_code="$?"
  if [[ "${emerge_exit_code}" -ne 0 ]]; then
    log "FAILED to build llvm with hash=${CURRENT}"
    log 'Skipping this hash'
    return 125  # 125 is the "skip" exit code.
  fi
  log "Succesfully built LLVM with hash=${CURRENT}"
  return 0  # Explicitly returning 0 for "good" even if a command errors out
}

build_pkg () {
  local pkg="${1}"

  local logfile="/tmp/build-${pkg//\//_}.${CURRENT}.out"
  log "Writing logs to ${logfile}"
  log "sudo emerge ${pkg}"
  logdo sudo emerge "${pkg}" \
    &> "${logfile}"
  local emerge_exit_code="$?"
  if [[ "${emerge_exit_code}" -ne 0 ]]; then
    log "FAILED to build ${pkg} with hash=${CURRENT}"
    return 1  # 1 here isn't for bisection, but for chaining with `||`
  fi
  log "Successfully built ${pkg} with hash=${CURRENT}"
  return 0  # Explicitly returning 0 for "good" even if a command errors out
}

main
