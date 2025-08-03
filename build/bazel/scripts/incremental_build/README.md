# How to Use

The most basic invocation, e.g. `incremental_build.sh --cujs "modify Android.bp$" -- libc`, is logically
equivalent to

1. running `m --skip-soong-tests libc` and then
2. parsing `$OUTDIR/soong_metrics`, `$OUTDIR/bp2build_metrics.pb` etc
3. Adding timing-related metrics from step 2 into `out/timing_logs/metrics.csv`
4. Now it's "warmed-up", for each cuj:
   1. apply changes associate with the cuj
   1. repeat steps 1 through 3

CUJs are defined in `cuj_catalog.py`
Each row in `metrics.csv` has the timings of various "phases" of a build.

Try `incremental_build.sh --help` and `canoncial_perf.sh --help` for help on
usage.

## CUJ groups

Since most CUJs involve making changes to the source code, we group a number of cujs together such that when any of them is specified, all CUJs