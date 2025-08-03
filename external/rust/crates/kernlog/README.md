# Kernel logger for Rust

Logger implementation for low level kernel log (using `/dev/kmsg`)

Usually intended for low level implementations, like [systemd generators][1],
which have to use `/dev/kmsg`:

> Since syslog is not available (see above) write log messages to /dev/kmsg instead.

[Full documentation.][2]

[1]: http://www.freedesktop.org/wiki/Software/systemd/Generators/
[2]: http://kstep.me/kernlog.rs/kernlog/index.html

## Usage

```toml
[dependencies]
log = "0.4"
kernlog = "0.3"
```

```rust
#[macro_use]
extern crate log;
extern crate kernlog;

fn main() {
    kernlog::init().unwrap();
    warn!("something strange happened");
}
```

Note you have to have permissions to write to `/dev/kmsg`,
which normal users (not root) usually don't.
