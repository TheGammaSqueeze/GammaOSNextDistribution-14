#!/bin/bash

# Find and delete files larger than 1GB in the current directory
find . -type f -size +1G -exec rm {} \;
