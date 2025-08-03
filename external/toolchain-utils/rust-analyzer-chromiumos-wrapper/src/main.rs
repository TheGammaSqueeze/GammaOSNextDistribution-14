// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

use std::collections::HashMap;
use std::env;
use std::fs::File;
use std::io::{self, BufRead, BufReader, BufWriter, Write};
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{self, Child};
use std::str::from_utf8;
use std::thread;

use anyhow::{anyhow, bail, Context, Result};
use lazy_static::lazy_static;
use log::trace;

use regex::Regex;

use simplelog::{Config, LevelFilter, WriteLogger};

use serde_json::{from_slice, to_writer, Value};

const CHROOT_SERVER_PATH: &str = "/usr/sbin/rust-analyzer";

fn main() -> Result<()> {
    let args = env::args().skip(1);

    let d = env::current_dir()?;
    let chromiumos_root = match find_chromiumos_root(&d) {
        Some(x) => x,
        None => {
            // It doesn't appear that we're in a chroot. Run the
            // regular rust-analyzer.
            bail!(process::Command::new("rust-analyzer").args(args).exec());
        }
    };

    let args: Vec<String> = args.collect();
    if !args.is_empty() {
        // We've received command line arguments, and there are 3 possibilities:
        // * We just forward the arguments to rust-analyzer and exit.
        // * We don't support the arguments, so we bail.
        // * We still need to do our path translation in the LSP protocol.
        fn run(args: &[String]) -> Result<()> {
            bail!(process::Command::new("cros_sdk")
                .args(["--", "rust-analyzer"])
                .args(args)
                .exec());
        }

        if args.iter().any(|x| {
            matches!(
                x.as_str(),
                "--version" | "--help" | "-h" | "--print-config-schema"
            )
        }) {
            // With any of these options rust-analyzer will just print something and exit.
            return run(&args);
        }

        if !args[0].starts_with('-') {
            // It's a subcommand, and seemingly none of these need the path translation
            // rust-analyzer-chromiumos-wrapper provides.
            return run(&args);
        }

        if args.iter().any(|x| x == "--log-file") {
            bail!("rust-analyzer-chromiums_wrapper doesn't support --log-file");
        }

        // Otherwise it seems we're probably OK to proceed.
    }

    init_log()?;

    // Get the rust sysroot, this is needed to translate filepaths to sysroot
    // related files, e.g. crate sources.
    let outside_rust_sysroot = {
        let output = process::Command::new("rustc")
            .arg("--print")
            .arg("sysroot")
            .output()?;
        if !output.status.success() {
            bail!("Unable to find rustc installation outside of sysroot");
        }
        std::str::from_utf8(&output.stdout)?.to_owned()
    };
    let outside_rust_sysroot = outside_rust_sysroot.trim();

    // The /home path inside the chroot is visible outside through "<chromiumos-root>/out/home".
    let outside_home: &'static str =
        Box::leak(format!("{}/out/home", chromiumos_root.display()).into_boxed_str());

    let outside_prefix: &'static str = {
        let mut path = chromiumos_root
            .to_str()
            .ok_or_else(|| anyhow!("Path is not valid UTF-8"))?
            .to_owned();

        if Some(&b'/') == path.as_bytes().last() {
            let _ = path.pop();
        }

        // No need to ever free this memory, so let's get a static reference.
        Box::leak(path.into_boxed_str())
    };

    trace!("Found chromiumos root {}", outside_prefix);

    let outside_sysroot_prefix: &'static str =
        Box::leak(format!("{outside_rust_sysroot}/lib/rustlib").into_boxed_str());
    let inside_prefix: &'static str = "/mnt/host/source";

    let cmd = "cros_sdk";
    let all_args = ["--", CHROOT_SERVER_PATH]
        .into_iter()
        .chain(args.iter().map(|x| x.as_str()));
    let mut child = KillOnDrop(run_command(cmd, all_args)?);

    let mut child_stdin = BufWriter::new(child.0.stdin.take().unwrap());
    let mut child_stdout = BufReader::new(child.0.stdout.take().unwrap());

    let replacement_map = {
        let mut m = HashMap::new();
        m.insert(outside_prefix, inside_prefix);
        m.insert(outside_sysroot_prefix, "/usr/lib/rustlib");
        m.insert(outside_home, "/home");
        m
    };

    let join_handle = {
        let rm = replacement_map.clone();
        thread::spawn(move || {
            let mut stdin = io::stdin().lock();
            stream_with_replacement(&mut stdin, &mut child_stdin, &rm)
                .context("Streaming from stdin into rust-analyzer")
        })
    };

    // For the mapping between inside to outside, we just reverse the map.
    let replacement_map_rev = replacement_map.iter().map(|(k, v)| (*v, *k)).collect();
    let mut stdout = BufWriter::new(io::stdout().lock());
    stream_with_replacement(&mut child_stdout, &mut stdout, &replacement_map_rev)
        .context("Streaming from rust-analyzer into stdout")?;

    join_handle.join().unwrap()?;

    let code = child.0.wait().context("Running rust-analyzer")?.code();
    std::process::exit(code.unwrap_or(127));
}

fn init_log() -> Result<()> {
    if !cfg!(feature = "no_debug_log") {
        let filename = env::var("RUST_ANALYZER_CHROMIUMOS_WRAPPER_LOG")
            .context("Obtaining RUST_ANALYZER_CHROMIUMOS_WRAPPER_LOG environment variable")?;
        let file = File::create(&filename).with_context(|| {
            format!(
                "Opening log file `{}` (value of RUST_ANALYZER_WRAPPER_LOG)",
                filename
            )
        })?;
        WriteLogger::init(LevelFilter::Trace, Config::default(), file)
            .with_context(|| format!("Creating WriteLogger with log file `{}`", filename))?;
    }
    Ok(())
}

#[derive(Debug, Default)]
struct Header {
    length: Option<usize>,
    other_fields: Vec<u8>,
}

/// Read the `Content-Length` (if present) into `header.length`, and the text of every other header
/// field into `header.other_fields`.
fn read_header<R: BufRead>(r: &mut R, header: &mut Header) -> Result<()> {
    header.length = None;
    header.other_fields.clear();
    const CONTENT_LENGTH: &[u8] = b"Content-Length:";
    let slen = CONTENT_LENGTH.len();
    loop {
        let index = header.other_fields.len();

        // HTTP header spec says line endings are supposed to be '\r\n' but recommends
        // implementations accept just '\n', so let's not worry whether a '\r' is present.
        r.read_until(b'\n', &mut header.other_fields)
            .context("Reading a header")?;

        let new_len = header.other_fields.len();

        if new_len <= index + 2 {
            // Either we've just received EOF, or just a newline, indicating end of the header.
            return Ok(());
        }
        if header
            .other_fields
            .get(index..index + slen)
            .map_or(false, |v| v == CONTENT_LENGTH)
        {
            let s = from_utf8(&header.other_fields[index + slen..])
                .context("Parsing Content-Length")?;
            header.length = Some(s.trim().parse().context("Parsing Content-Length")?);
            header.other_fields.truncate(index);
        }
    }
}

/// Extend `dest` with `contents`, replacing any occurrence of patterns in a json string in
/// `contents` with a replacement.
fn replace(
    contents: &[u8],
    replacement_map: &HashMap<&str, &str>,
    dest: &mut Vec<u8>,
) -> Result<()> {
    fn map_value(val: Value, replacement_map: &HashMap<&str, &str>) -> Value {
        match val {
            Value::String(s) =>
            // `s.replace` is very likely doing more work than necessary. Probably we only need
            // to look for the pattern at the beginning of the string.
            {
                lazy_static! {
                    static ref SERVER_PATH_REGEX: Regex =
                        Regex::new(r".*/rust-analyzer-chromiumos-wrapper$").unwrap();
                }
                // Always replace the server path everywhere.
                let mut s = SERVER_PATH_REGEX
                    .replace_all(&s, CHROOT_SERVER_PATH)
                    .to_string();
                // Then replace all mappings we get.
                for (pattern, replacement) in replacement_map {
                    s = s.replace(pattern, replacement);
                }
                Value::String(s.to_string())
            }
            Value::Array(mut v) => {
                for val_ref in v.iter_mut() {
                    let value = std::mem::replace(val_ref, Value::Null);
                    *val_ref = map_value(value, replacement_map);
                }
                Value::Array(v)
            }
            Value::Object(mut map) => {
                // Surely keys can't be paths.
                for val_ref in map.values_mut() {
                    let value = std::mem::replace(val_ref, Value::Null);
                    *val_ref = map_value(value, replacement_map);
                }
                Value::Object(map)
            }
            x => x,
        }
    }

    let init_val: Value = from_slice(contents).with_context(|| match from_utf8(contents) {
        Err(_) => format!(
            "JSON parsing content of length {} that's not valid UTF-8",
            contents.len()
        ),
        Ok(s) => format!("JSON parsing content of length {}:\n{}", contents.len(), s),
    })?;
    let mapped_val = map_value(init_val, replacement_map);
    to_writer(dest, &mapped_val)?;
    Ok(())
}

/// Read LSP messages from `r`, replacing each occurrence of patterns in a json string in the
/// payload with replacements, adjusting the `Content-Length` in the header to match, and writing
/// the result to `w`.
fn stream_with_replacement<R: BufRead, W: Write>(
    r: &mut R,
    w: &mut W,
    replacement_map: &HashMap<&str, &str>,
) -> Result<()> {
    let mut head = Header::default();
    let mut buf = Vec::with_capacity(1024);
    let mut buf2 = Vec::with_capacity(1024);
    loop {
        read_header(r, &mut head)?;
        if head.length.is_none() && head.other_fields.is_empty() {
            // No content in the header means we're apparently done.
            return Ok(());
        }
        let len = head
            .length
            .ok_or_else(|| anyhow!("No Content-Length in header"))?;

        trace!("Received header with length {}", head.length.unwrap());
        trace!(
            "Received header with contents\n{}",
            from_utf8(&head.other_fields)?
        );

        buf.resize(len, 0);
        r.read_exact(&mut buf)
            .with_context(|| format!("Reading payload expecting size {}", len))?;

        trace!("Received payload\n{}", from_utf8(&buf)?);

        buf2.clear();
        replace(&buf, replacement_map, &mut buf2)?;

        trace!("After replacements payload\n{}", from_utf8(&buf2)?);

        write!(w, "Content-Length: {}\r\n", buf2.len())?;
        w.write_all(&head.other_fields)?;
        w.write_all(&buf2)?;
        w.flush()?;
    }
}

fn run_command<'a, I>(cmd: &'a str, args: I) -> Result<process::Child>
where
    I: IntoIterator<Item = &'a str>,
{
    Ok(process::Command::new(cmd)
        .args(args)
        .stdin(process::Stdio::piped())
        .stdout(process::Stdio::piped())
        .spawn()?)
}

fn find_chromiumos_root(start: &Path) -> Option<PathBuf> {
    let mut buf = start.to_path_buf();
    loop {
        buf.push(".chroot_lock");
        if buf.exists() {
            buf.pop();
            return Some(buf);
        }
        buf.pop();
        if !buf.pop() {
            return None;
        }
    }
}

struct KillOnDrop(Child);

impl Drop for KillOnDrop {
    fn drop(&mut self) {
        let _ = self.0.kill();
    }
}

#[cfg(test)]
mod test {
    use super::*;

    fn test_stream_with_replacement(
        read: &str,
        pattern: &str,
        replacement: &str,
        json_expected: &str,
    ) -> Result<()> {
        let mut w = Vec::<u8>::with_capacity(read.len());
        let replacement_map = {
            let mut m = HashMap::new();
            m.insert(pattern, replacement);
            m
        };
        stream_with_replacement(&mut read.as_bytes(), &mut w, &replacement_map)?;

        // serde_json may not format the json output the same as we do, so we can't just compare
        // as strings or slices.

        let (w1, w2) = {
            let mut split = w.rsplitn(2, |&c| c == b'\n');
            let w2 = split.next().unwrap();
            (split.next().unwrap(), w2)
        };

        assert_eq!(
            from_utf8(w1)?,
            format!("Content-Length: {}\r\n\r", w2.len())
        );

        let v1: Value = from_slice(w2)?;
        let v2: Value = serde_json::from_str(json_expected)?;
        assert_eq!(v1, v2);

        Ok(())
    }

    #[test]
    fn test_stream_with_replacement_1() -> Result<()> {
        test_stream_with_replacement(
            // read
            "Content-Length: 93\r\n\r\n{\"somekey\": {\"somepath\": \"XYZXYZabc\",\
            \"anotherpath\": \"somestring\"}, \"anotherkey\": \"XYZXYZdef\"}",
            // pattern
            "XYZXYZ",
            // replacement
            "REPLACE",
            // json_expected
            "{\"somekey\": {\"somepath\": \"REPLACEabc\", \"anotherpath\": \"somestring\"},\
            \"anotherkey\": \"REPLACEdef\"}",
        )
    }

    #[test]
    fn test_stream_with_replacement_2() -> Result<()> {
        test_stream_with_replacement(
            // read
            "Content-Length: 83\r\n\r\n{\"key0\": \"sometextABCDEF\",\
            \"key1\": {\"key2\": 5, \"key3\": \"moreABCDEFtext\"}, \"key4\": 1}",
            // pattern
            "ABCDEF",
            // replacement
            "replacement",
            // json_expected
            "{\"key0\": \"sometextreplacement\", \"key1\": {\"key2\": 5,\
            \"key3\": \"morereplacementtext\"}, \"key4\": 1}",
        )
    }

    #[test]
    fn test_stream_with_replacement_3() -> Result<()> {
        test_stream_with_replacement(
            // read
            "Content-Length: 55\r\n\r\n{\"path\": \"/my_folder/rust-analyzer-chromiumos-wrapper\"}",
            // pattern
            "",
            // replacement
            "",
            // json_expected
            "{\"path\": \"/usr/sbin/rust-analyzer\"}",
        )
    }
}
