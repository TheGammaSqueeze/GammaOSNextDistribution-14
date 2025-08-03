/// Module to keep track of which files should be pushed to a device.
/// Composed of:
///  1) A tracking config that lets user specify modules to
///     augment a base image (droid).
///  2) Integration with ninja to derive "installed" files from
///     this module set.
use anyhow::{bail, Context, Result};
use lazy_static::lazy_static;
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::BufReader;
use std::path::PathBuf;
use std::process;
use tracing::{debug, warn};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Config {
    pub base: String,
    pub modules: Vec<String>,
    #[serde(default, skip_serializing, skip_deserializing)]
    config_path: String,
}

/// Object representing the files that are _tracked_. These are files that the
/// build system indicates should be on the device.  Sometimes stale files
/// get left in the Product Out tree or extra modules get built into the Product Out tree.
/// This tracking config helps us call ninja to distinguish declared depdencies for
/// `droid` and what has been built.
/// TODO(rbraunstein): Rewrite above clearer.
impl Config {
    /// Load set of tracked modules from User's homedir or return a default one.
    /// If the user passes a config path, use it. Otherwise use the
    /// default path in their home dir.
    pub fn load(config_path: &Option<String>) -> Result<Self> {
        match &config_path {
            Some(path) => Self::from_json_file(path),
            None => match std::env::var("HOME") {
                Ok(home) if !home.is_empty() => Self::load(&Some(Self::default_path(&home)?)),
                _ => Ok(Self::default()),
            },
        }
    }

    /// Load set of tracked modules from the given path or return a default one.
    fn from_json_file(path: &String) -> Result<Self> {
        if let Ok(file) = fs::File::open(path) {
            let mut config: Config = serde_json::from_reader(BufReader::new(file))
                .context(format!("Parsing config {path:?}"))?;
            config.config_path = path.clone();
            return Ok(config);
        }
        // Lets not create a default config file until they actually track a module.
        Ok(Config { base: "droid".to_string(), modules: Vec::new(), config_path: path.clone() })
    }

    fn default() -> Self {
        Config { base: "droid".to_string(), modules: Vec::new(), config_path: String::new() }
    }

    pub fn print(&self) {
        debug!("Tracking base: `{}` and modules {:?}", self.base, self.modules);
    }

    /// Returns the full path to the serialized config file.
    fn default_path(home: &str) -> Result<String> {
        fs::create_dir_all(format!("{home}/.config/asuite"))?;
        Ok(format!("{home}/.config/asuite/adevice-tracking.json"))
    }

    /// Adds the module name to the config and saves it.
    pub fn track(&mut self, module_names: &[String]) -> Result<()> {
        // TODO(rbraunstein): Validate the module names and warn on bad names.
        self.modules.extend_from_slice(module_names);
        self.modules.sort();
        self.modules.dedup();
        self.print();
        self.clear_cache();
        Self::save(self)
    }

    /// Update the base module and saves it.
    pub fn trackbase(&mut self, base: &str) -> Result<()> {
        // TODO(rbraunstein): Validate the module names and warn on bad names.
        self.base = base.to_string();
        self.print();
        self.clear_cache();
        Self::save(self)
    }

    /// Removes the module name from the config and saves it.
    pub fn untrack(&mut self, module_names: &[String]) -> Result<()> {
        // TODO(rbraunstein): Report if not found?
        self.modules.retain(|m| !module_names.contains(m));
        self.print();
        self.clear_cache();
        Self::save(self)
    }

    // Store the config as json at the config_path.
    fn save(&self) -> Result<()> {
        if self.config_path.is_empty() {
            bail!("Can not save config file when HOME is not set and --config not set.")
        }
        let mut file = fs::File::create(&self.config_path)
            .context(format!("Creating config file {:?}", self.config_path))?;
        serde_json::to_writer_pretty(&mut file, &self).context("Writing config file")?;
        debug!("Wrote config file {:?}", &self.config_path);
        Ok(())
    }

    /// Return all files that are part of the tracked set under ANDROID_PRODUCT_OUT.
    /// Implementation:
    ///   Runs `ninja` to get all transitive intermediate targets for `droid`.
    ///   These intermediate targets contain all the apks and .sos, etc that
    ///   that get packaged for flashing.
    ///   Filter all the inputs returned by ninja to just those under
    ///   ANDROID_PRODUCT_OUT and explicitly ask for modules in our tracking set.
    ///   Extra or stale files in ANDROID_PRODUCT_OUT from builds will not be part
    ///   of the result.
    ///   The combined.ninja file will be found under:
    ///        ${ANDROID_BUILD_TOP}/${OUT_DIR}/combined-${TARGET_PRODUCT}.ninja
    ///   Tracked files inside that file are relative to $OUT_DIR/target/product/*/
    ///   The final element of the path can be derived from the final element of ANDROID_PRODUCT_OUT,
    ///   but matching against */target/product/* is enough.
    /// Store all ninja deps in the cache.
    pub fn tracked_files(&self) -> Result<Vec<String>> {
        if let Ok(cache) = self.read_cache() {
            Ok(cache)
        } else {
            let ninja_output = self.ninja_output(
                &self.src_root()?,
                &self.ninja_args(&self.target_product()?, &self.out_dir()),
            )?;
            if !ninja_output.status.success() {
                let stderr = String::from_utf8(ninja_output.stderr.clone()).unwrap();
                anyhow::bail!("{}", self.ninja_failure_msg(&stderr));
            }
            let unfiltered_tracked_files = tracked_files(&ninja_output)?;
            self.write_cache(&unfiltered_tracked_files)
                .unwrap_or_else(|e| warn!("Error writing tracked file cache: {e}"));
            Ok(unfiltered_tracked_files)
        }
    }

    fn src_root(&self) -> Result<String> {
        std::env::var("ANDROID_BUILD_TOP")
            .context("ANDROID_BUILD_TOP must be set. Be sure to run lunch.")
    }

    fn target_product(&self) -> Result<String> {
        std::env::var("TARGET_PRODUCT").context("TARGET_PRODUCT must be set. Be sure to run lunch.")
    }

    fn out_dir(&self) -> String {
        std::env::var("OUT_DIR").unwrap_or("out".to_string())
    }

    // Prepare the ninja command line args, creating the right ninja file name and
    // appending all the modules.
    fn ninja_args(&self, target_product: &str, out_dir: &str) -> Vec<String> {
        // Create `ninja -f combined.ninja -t input -i BASE MOD1 MOD2 ....`
        // The `-i` for intermediary is what gives the PRODUCT_OUT files.
        let mut args = vec![
            "-f".to_string(),
            format!("{out_dir}/combined-{target_product}.ninja"),
            "-t".to_string(),
            "inputs".to_string(),
            "-i".to_string(),
            self.base.clone(),
        ];
        for module in self.modules.clone() {
            args.push(module);
        }
        args
    }

    // Call ninja.
    fn ninja_output(&self, src_root: &str, args: &[String]) -> Result<process::Output> {
        // TODO(rbraunstein): Deal with non-linux-x86.
        let path = "prebuilts/build-tools/linux-x86/bin/ninja";
        debug!("Running {path} {args:?}");
        process::Command::new(path)
            .current_dir(src_root)
            .args(args)
            .output()
            .context("Running ninja to get base files")
    }

    /// Check to see if the output from running ninja mentions a module we are tracking.
    /// If a user tracks a module, but then removes it from the codebase, they should be notified.
    /// Return origina ninja error and possibly a statement suggesting they `untrack` a module.
    fn ninja_failure_msg(&self, stderr: &str) -> String {
        // A stale tracked target will look something like this:
        //   unknown target 'SomeStaleModule'
        let mut msg = String::new();
        for tracked_module in &self.modules {
            if stderr.contains(tracked_module) {
                msg = format!("You may need to `adevice untrack {}`", tracked_module);
            }
        }
        if stderr.contains(&self.base) {
            msg = format!(
                "You may need to `adevice track-base` something other than `{}`",
                &self.base
            );
        }
        format!("{}{}", stderr, msg)
    }

    pub fn clear_cache(&self) {
        let path = self.cache_path();
        if path.is_err() {
            warn!("Error getting the cache path {:?}", path.err().unwrap());
            return;
        }
        match std::fs::remove_file(path.unwrap()) {
            Ok(_) => (),
            Err(e) => {
                // Probably the cache has already been cleared and we can't remove it again.
                debug!("Error clearing the cache {e}");
            }
        }
    }

    // If our cache (in the out_dir) is newer than the ninja file, then use it rather
    // than rerun ninja.  Saves about 2 secs.
    // Returns Err if cache not found or if cache is stale.
    // Otherwise returns the stdout from the ninja command.
    // TODO(rbraunstein): I don't think the cache is effective.  I think the combined
    // ninja file gets touched after every `m`.  Either use the subninja or just turn off caching.
    fn read_cache(&self) -> Result<Vec<String>> {
        let cache_path = self.cache_path()?;
        let ninja_file_path = PathBuf::from(&self.src_root()?)
            .join(self.out_dir())
            .join(format!("combined-{}.ninja", self.target_product()?));
        // cache file is too old.
        // TODO(rbraunstein): Need integration tests for this.
        // Adding and removing tracked modules affects the cache too.
        debug!("Reading cache {cache_path}");
        let cache_time = fs::metadata(&cache_path)?.modified()?;
        debug!("Reading ninja  {ninja_file_path:?}");
        let ninja_file_time = fs::metadata(ninja_file_path)?.modified()?;
        if cache_time.lt(&ninja_file_time) {
            debug!("Cache is too old: {cache_time:?}, ninja file time {ninja_file_time:?}");
            anyhow::bail!("cache is stale");
        }
        debug!("Using ninja file cache");
        Ok(fs::read_to_string(&cache_path)?.split('\n').map(|s| s.to_string()).collect())
    }

    fn cache_path(&self) -> Result<String> {
        Ok([
            self.src_root()?,
            self.out_dir(),
            format!("adevice-ninja-deps-{}.cache", self.target_product()?),
        ]
        // TODO(rbraunstein): Fix OS separator.
        .join("/"))
    }

    // Unconditionally write the given byte stream to the cache file
    // overwriting whatever is there.
    fn write_cache(&self, data: &[String]) -> Result<()> {
        let cache_path = self.cache_path()?;
        debug!("Wrote cache file: {cache_path:?}");
        fs::write(cache_path, data.join("\n"))?;
        Ok(())
    }
}

/// Iterate through the `ninja -t input -i MOD...` output
/// to find files in the PRODUCT_OUT directory.
fn tracked_files(output: &process::Output) -> Result<Vec<String>> {
    let stdout = &output.stdout;
    let stderr = &output.stderr;
    debug!("NINJA calculated deps: {}", stdout.len());
    if output.status.code().unwrap() > 0 || !stderr.is_empty() {
        warn!("code: {} {:?}", output.status, String::from_utf8(stderr.to_owned()));
    }
    Ok(String::from_utf8(stdout.to_owned())?
        .lines()
        .filter_map(|line| {
            if let Some(device_path) = strip_product_prefix(line) {
                return Some(device_path);
            }
            None
        })
        .collect())
}

// The ninja output for the files we are interested in will look like this:
//     % OUT_DIR=innie m nothing
//     % (cd $ANDROID_BUILD_TOP;prebuilts/build-tools/linux-x86/bin/ninja -f innie/combined-aosp_cf_x86_64_phone.ninja -t inputs -i droid | grep innie/target/product/vsoc_x86_64/system) | grep apk | head
//     innie/target/product/vsoc_x86_64/system/app/BasicDreams/BasicDreams.apk
//     innie/target/product/vsoc_x86_64/system/app/BluetoothMidiService/BluetoothMidiService.apk
//     innie/target/product/vsoc_x86_64/system/app/BookmarkProvider/BookmarkProvider.apk
//     innie/target/product/vsoc_x86_64/system/app/CameraExtensionsProxy/CameraExtensionsProxy.apk
// Match any files with target/product as the second and third dir paths and capture
// everything from 5th path element to the end.
lazy_static! {
    static ref NINJA_OUT_PATH_MATCHER: Regex =
        Regex::new(r"^[^/]+/target/product/[^/]+/(.+)$").expect("regex does not compile");
}

fn strip_product_prefix(path: &str) -> Option<String> {
    NINJA_OUT_PATH_MATCHER.captures(path).map(|x| x[1].to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn load_creates_new_config_with_droid() -> Result<()> {
        let home_dir = TempDir::new()?;
        let config_path = home_dir.path().join("config.json").display().to_string();
        let config = Config::load(&Some(config_path));
        assert_eq!("droid", config?.base);
        Ok(())
    }

    #[test]
    fn track_updates_config_file() -> Result<()> {
        let home_dir = TempDir::new()?;
        let config_path = home_dir.path().join("config.json").display().to_string();
        let mut config = Config::load(&Some(config_path.clone()))?;
        config.track(&["supermod".to_string()])?;
        config.track(&["another".to_string()])?;
        // Updates in-memory version, which gets sorted and deduped.
        assert_eq!(vec!["another".to_string(), "supermod".to_string()], config.modules);

        // Check the disk version too.
        let config2 = Config::load(&Some(config_path))?;
        assert_eq!(config, config2);
        Ok(())
    }

    #[test]
    fn untrack_updates_config() -> Result<()> {
        let home_dir = TempDir::new()?;
        let config_path = Config::default_path(&path(&home_dir)).context("Writing config")?;
        std::fs::write(
            config_path.clone(),
            r#"{"base": "droid",  "modules": [ "mod_one", "mod_two" ]}"#,
        )?;
        let mut config = Config::load(&Some(config_path.clone())).context("LOAD")?;
        assert_eq!(2, config.modules.len());
        // Updates in-memory version.
        config.untrack(&["mod_two".to_string()]).context("UNTRACK")?;
        assert_eq!(vec!["mod_one"], config.modules);
        // Updates on-disk version.
        Ok(())
    }

    #[test]
    fn ninja_args_updated_based_on_config() {
        let config =
            Config { base: s("DROID"), modules: vec![s("ADEVICE_FP")], config_path: s("") };
        assert_eq!(
            crate::commands::split_string(
                "-f outdir/combined-lynx.ninja -t inputs -i DROID ADEVICE_FP"
            ),
            config.ninja_args("lynx", "outdir")
        );
        // Find the args passed to ninja
    }

    #[test]
    fn ninja_output_filtered_to_android_product_out() -> Result<()> {
        // Ensure only paths matching */target/product/ remain
        let fake_out = vec![
            // 2 good ones
            "innie/target/product/vsoc_x86_64/system/app/BasicDreams/BasicDreams.apk\n",
            "innie/target/product/vsoc_x86_64/system/app/BookmarkProvider/BookmarkProvider.apk\n",
            // Target/product not at right position
            "innie/nested/target/product/vsoc_x86_64/system/NOT_FOUND\n",
            // Different partition
            "innie/target/product/vsoc_x86_64/OTHER_PARTITION/app/BasicDreams/BasicDreams2.apk\n",
            // Good again.
            "innie/target/product/vsoc_x86_64/system_ext/ok_file\n",
        ];

        let output = process::Command::new("echo")
            .args(&fake_out)
            .output()
            .context("Running ECHO to generate output")?;

        assert_eq!(
            vec![
                "system/app/BasicDreams/BasicDreams.apk",
                "system/app/BookmarkProvider/BookmarkProvider.apk",
                "OTHER_PARTITION/app/BasicDreams/BasicDreams2.apk",
                "system_ext/ok_file",
            ],
            tracked_files(&output)?
        );
        Ok(())
    }

    #[test]
    fn check_ninja_failure_msg_for_tracked_module() {
        // User tracks 'fish', which isn't a real module.
        let config = Config { base: s("DROID"), modules: vec![s("fish")], config_path: s("") };
        let msg = config.ninja_failure_msg(" error: unknown target 'fish', did you mean 'sh'");

        assert!(msg.contains("adevice untrack fish"), "Actual: {msg}")
    }

    #[test]
    fn check_ninja_failure_msg_for_special_base() {
        let config = Config { base: s("R2D2_DROID"), modules: Vec::new(), config_path: s("") };
        let msg = config.ninja_failure_msg(" error: unknown target 'R2D2_DROID'");

        assert!(msg.contains("adevice track-base"), "Actual: {msg}")
    }

    #[test]
    fn check_ninja_failure_msg_unrelated() {
        // User tracks 'bait', which is a real module, but gets some other error message.
        let config = Config { base: s("DROID"), modules: vec![s("bait")], config_path: s("") };

        // There should be no untrack command.
        assert!(!config
            .ninja_failure_msg(" error: unknown target 'fish', did you mean 'sh'")
            .contains("untrack"))
    }

    /*
    // Ensure we match the whole path component, i.e. "sys" should not match system.
    #[test]
    fn test_partition_filtering_partition_name_matches_path_component() {
        let ninja_deps = vec![
            "system/file1".to_string(),
            "system_ext/file2".to_string(),
            "file3".to_string(),
            "data/sys/file4".to_string(),
        ];
        assert_eq!(
            Vec::<String>::new(),
            crate::tracking::filter_partitions(&ninja_deps, &[PathBuf::from("sys")])
        );
    }*/

    // Convert TempDir to string we can use for fs::write/read.
    fn path(dir: &TempDir) -> String {
        dir.path().display().to_string()
    }

    // Tired of typing to_string()
    fn s(str: &str) -> String {
        str.to_string()
    }
}
