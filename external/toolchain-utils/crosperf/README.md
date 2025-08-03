# experiment_files

To use these experiment files, replace the board, remote and images
placeholders and run crosperf on them.

Further information about crosperf: https://goto.google.com/crostc-crosperf

The final experiment file should look something like the following (but with
different actual values for the fields):

```
board: lumpy
remote: 123.45.67.089

# Add images you want to test:

my_image {
  chromeos_image: /usr/local/chromeos/src/build/images/lumpy/chromiumos_test_image.bin
}

vanilla_image {
   chromeos_root: /usr/local/chromeos
   build: lumpy-release/R35-5672.0.0
}

# Paste experiment benchmarks here. Example, I pasted
# `page_cycler_v2.morejs` here.

# This experiment just runs a short autotest which measures the performance
# of Telemetry's `page_cycler_v2.morejs`. In addition, it profiles cycles.

perf_args: record -e cycles

benchmark: page_cycler_v2.morejs {
   suite: telemetry_Crosperf
   iterations: 1
}
```

# default_remotes

This is the list of machines allocated for toolchain team.
This should be kept in sync with:
https://chromeos-swarming.appspot.com/botlist?c=id&c=task&c=label-board&c=label-pool&c=os&c=status&d=asc&f=label-pool%3Atoolchain&k=label-pool&s=id
