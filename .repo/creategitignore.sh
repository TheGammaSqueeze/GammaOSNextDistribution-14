#!/bin/bash

# Find all directories within the current directory (recursively)
# that are empty, and for each one, create a .gitignore file

find . -type d -empty -exec sh -c 'echo "# Ignore everything in this directory" > {}/.gitignore' \;

echo "Created .gitignore files in all empty subdirectories."
