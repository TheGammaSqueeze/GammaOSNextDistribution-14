#!/bin/bash

# $1 Path to the new version.
# $2 Path to the old version.

cp -a -n $2/config.h $1/
cp -a -n $2/cups/tls-boringssl.c $1/cups/
