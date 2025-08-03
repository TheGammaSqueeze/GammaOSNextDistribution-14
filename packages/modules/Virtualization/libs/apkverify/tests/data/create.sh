#!/bin/bash

sizes="512 4K 1M 10000000 272629760"
for size in $sizes; do
  echo $size
  dd if=/dev/random of=input.$size bs=$size count=1
  fsverity digest input.$size \
    --hash-alg=sha256 \
    --salt=010203040506 \
    --block-size=4096 \
    --out-merkle-tree input.$size.hash \
    --out-descriptor input.$size.descriptor
done
