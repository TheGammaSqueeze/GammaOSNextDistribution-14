# CarWatchdog's Performance Analysis Tools

## Performance Stats Parser
The `parser/` directory contains the scripts to parse CarWatchdog daemon's dumps
and generate a protobuf binary with the performance stats. The proto format is
available in `parser/performancestats.proto`.

### Make the parser

```bash
m perf_stats_parser
```

### Generate protobuf binary
To generate the proto binary for the CarWatchdog performance stats, run:
```bash
perf_stats_parser -f <file-with-dump>.txt -o ~/perf_stats_out.pb
```

The proto binary will be saved to `~/perf_stats_out.pb`.

### Read the protobuf binary
To read the protobuf binary, one could use:

```bash
perf_stats_parser -r <proto-file> -D -j
```

`-j` flags specify to output in JSON format.