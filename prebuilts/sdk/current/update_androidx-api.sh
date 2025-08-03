#!/usr/bin/env bash
rm androidx-api.txt; find $1/frameworks/support/ -name current.txt | grep /api/ | grep -v /ktx/ | xargs cat>>androidx-api.txt

