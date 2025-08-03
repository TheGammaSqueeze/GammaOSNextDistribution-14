#!/bin/bash

UPSTREAM_COMMIT=$(git merge-base HEAD aosp/upstream-main)

git diff HEAD..$UPSTREAM_COMMIT --stat -- $(find . -name "*.rs" -o -name "*.toml")

function crosvm_take_theirs() {
    git checkout $UPSTREAM_COMMIT -- $(find . -name "*.rs" -o -name "*.toml" | grep -v "/out/")
}
