#!/bin/bash

echo "#pragma once"
echo
echo "namespace bluetooth::test::headless {"
echo "constexpr char kBuildTime[]=\""$(date -Iseconds)"\";"
echo " }  // namespace bluetooth::test::headless"
