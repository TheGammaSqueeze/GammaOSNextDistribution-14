#!/bin/bash
[[ ! $(command -v buildifier) ]] || buildifier -mode=check -lint=warn -warnings="out-of-order-load,load-on-top,load,unused-variable,list-append" `printf "%s\n" $@ | grep -E "^(.*/)?(BUILD|BUILD.bazel|bazel.WORKSPACE|.*\\.bzl)$"` < /dev/null
