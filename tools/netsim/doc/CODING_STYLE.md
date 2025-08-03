# Coding Style Guide

## Introduction
This project follows [Google C++ style](https://google.github.io/styleguide/cppguide.html).

If there is any inconsistency in this repository and it's not mentioned in Google C++ style guide:
1. Add a rule with reason or reference here and send a code review to the team.
2. Once approved, apply the rule to the repository in a follow up change.

## Rules
1. Use `#pragma once` instead of #define guards. Netsim uses compiler tool chain from external/qemu, and the project uses `#pragma once` too.
