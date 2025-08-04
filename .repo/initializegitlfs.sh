#!/bin/bash

# Initialize Git LFS
git lfs install

# Find and track files over 50MB
find . -size +50M -exec git lfs track '{}' \;

echo "All files over 50MB are now being tracked by Git LFS."
