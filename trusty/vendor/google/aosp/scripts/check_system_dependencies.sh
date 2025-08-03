#!/bin/bash

set -e
set -u

DEPS=(
  libglib2.0-dev
  libpixman-1-dev
  libssl-dev
  libusb-1.0-0-dev
  mypy
  pkg-config
  pylint
  xxd
)

if !(echo ${DEPS[@]} | tr " " "\n" | sort --check); then
  echo
  echo "WARNING DEPS is not sorted:"
  echo ${DEPS[@]}
  echo
fi

if dpkg -V ${DEPS[@]}; then
  echo "System dependencies appear to be installed."
else
  echo
  echo "There appear to be missing system dependencies. Please run:"
  echo
  echo "sudo apt-get install ${DEPS[@]}"
  echo
  exit 1
fi
