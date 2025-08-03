# Statsd Configs

This directory hosts binary statsd config protos.

## What they do

A config tells statsd what metrics to collect from the device during a test. For example,
`app-start` will instruct statsd to collect app startup metrics.

## Checking in a config

To check in config(s) for a new set of metrics, follow these steps:

1. Create a directory under this directory for the new metrics (e.g. `app-start`).
2. Put the new config(s) in the subdirectory using the directory name and optionally with additional
suffixes if there are multiple configs related to the overarching metrics, with `.pb` extension.
This ensures that each config has a unique name.
3. Write a README file explaining what the config(s) in the new subdirectory does and put it under
the new subdirectory.

## (Google Internal only) Creating a config

Please follow go/android-crystalball/features/metric/statsdlistener#creating-a-config.
