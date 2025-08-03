#!/bin/bash

pushd $ANDROID_BUILD_TOP

llvm-cov show --format=html --summary-only --show-line-counts-or-regions --show-instantiation-summary --instr-profile=/tmp/logs/HostOnlyCert/latest/GdDevice_dut_backing_process_coverage.profdata --output-dir=/tmp/logs/HostOnlyCert/latest/GdDevice_dut_backing_process_coverage/ out/dist/bluetooth_venv/lib/python3.8/site-packages/bluetooth_stack_with_facade

popd

echo "point your browser to file:///tmp/logs/HostOnlyCert/latest/GdDevice_dut_backing_process_coverage/index.html"

