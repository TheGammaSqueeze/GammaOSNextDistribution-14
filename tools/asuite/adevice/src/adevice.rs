use crate::cli;
use crate::commands;
use crate::device;
use crate::fingerprint;
use crate::metrics;
use crate::progress;
use crate::restart_chooser;
use crate::tracking::Config;
use anyhow::{anyhow, bail, Context, Result};
use fingerprint::{DiffMode, FileMetadata};
use itertools::Itertools;
use lazy_static::lazy_static;
use metrics::MetricSender;
use rayon::prelude::*;
use regex::Regex;
use restart_chooser::RestartChooser;
use tracing::{debug, Level};

use std::collections::{HashMap, HashSet};
use std::ffi::OsString;
use std::fs;
use std::fs::File;
use std::io::{stdin, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;

/// Methods that interact with the host, like fingerprinting and calling ninja to get deps.
pub trait Host {
    /// Return all files in the given partitions at the partition_root along with metadata for those files.
    /// The keys in the returned hashmap will be relative to partition_root.
    fn fingerprint(
        &self,
        partition_root: &Path,
        partitions: &[PathBuf],
    ) -> Result<HashMap<PathBuf, FileMetadata>>;

    /// Return a list of all files that compose `droid` or whatever base and tracked
    /// modules are listed in `config`.
    /// Result strings are device relative. (i.e. start with system)
    fn tracked_files(&self, config: &Config) -> Result<Vec<String>>;
}

/// Methods to interact with the device, like adb, rebooting, and fingerprinting.
pub trait Device {
    /// Run the `commands` and return the stdout as a string.  If there is non-zero return code
    /// or output on stderr, then the result is an Err.
    fn run_adb_command(&self, args: &commands::AdbCommand) -> Result<String>;

    fn run_raw_adb_command(&self, args: &[String]) -> Result<String>;

    /// Send commands to reboot device.
    fn reboot(&self) -> Result<String>;
    /// Send commands to do a soft restart.
    fn soft_restart(&self) -> Result<String>;

    /// Call the fingerprint program on the device.
    fn fingerprint(&self, partitions: &[String]) -> Result<HashMap<PathBuf, FileMetadata>>;

    /// Return the list apks that are currently installed, i.e. `adb install`
    /// which live on the /data partition.
    /// Returns the package name, i.e. "com.android.shell".
    fn get_installed_apks(&self) -> Result<HashSet<String>>;

    /// Wait for the device to be ready after reboots/restarts.
    /// Returns any relevant output from waiting.
    fn wait(&self, profiler: &mut Profiler) -> Result<String>;

    /// Run the commands needed to prep a userdebug device after a flash.
    fn prep_after_flash(&self) -> Result<()>;
}

pub struct RealHost {}

impl Default for RealHost {
    fn default() -> Self {
        Self::new()
    }
}

impl RealHost {
    pub fn new() -> RealHost {
        RealHost {}
    }
}

impl Host for RealHost {
    fn fingerprint(
        &self,
        partition_root: &Path,
        partitions: &[PathBuf],
    ) -> Result<HashMap<PathBuf, FileMetadata>> {
        fingerprint::fingerprint_partitions(partition_root, partitions)
    }

    fn tracked_files(&self, config: &Config) -> Result<Vec<String>> {
        config.tracked_files()
    }
}

/// Time how long it takes to run the function and store the
/// result in the given profiler field.
// TODO(rbraunstein): Ideally, use tracing or flamegraph crate or
// use Map rather than name all the fields.
// See: https://docs.rs/tracing/latest/tracing/index.html#using-the-macros and span!
#[macro_export]
macro_rules! time {
    ($fn:expr, $ident:expr) => {{
        let start = std::time::Instant::now();
        let result = $fn;
        $ident = start.elapsed();
        result
    }};
}

pub fn adevice(
    host: &impl Host,
    device: &impl Device,
    cli: &cli::Cli,
    stdout: &mut impl Write,
    metrics: &mut impl MetricSender,
    opt_log_file: Option<File>,
    profiler: &mut Profiler,
) -> Result<()> {
    // If we can initialize a log file, then setup the tracing/log subscriber to write there.
    // Otherwise, logs will be dropped.
    if let Some(log_file) = opt_log_file {
        let subscriber = tracing_subscriber::fmt()
            .with_max_level(Level::DEBUG)
            .with_writer(Mutex::new(log_file))
            .finish();
        tracing::subscriber::set_global_default(subscriber)?;
    }

    let command_line = std::env::args().collect::<Vec<String>>().join(" ");
    metrics.add_start_event(&command_line);
    let restart_choice = cli.global_options.restart_choice.clone();

    let product_out = match &cli.global_options.product_out {
        Some(po) => PathBuf::from(po),
        None => get_product_out_from_env().ok_or(anyhow!(
            "ANDROID_PRODUCT_OUT is not set. Please run source build/envsetup.sh and lunch."
        ))?,
    };

    let track_time = std::time::Instant::now();

    let mut config = Config::load(&cli.global_options.config_path)?;

    // Early return for track/untrack commands.
    match &cli.command {
        cli::Commands::Track(names) => return config.track(&names.modules),
        cli::Commands::TrackBase(base) => return config.trackbase(&base.base),
        cli::Commands::Untrack(names) => return config.untrack(&names.modules),
        _ => (),
    }
    config.print();

    writeln!(stdout, " * Checking for files to push to device")?;

    progress::start("Checking ninja installed files");
    let mut ninja_installed_files =
        time!(host.tracked_files(&config)?, profiler.ninja_deps_computer);
    let partitions =
        &validate_partitions(&product_out, &ninja_installed_files, &cli.global_options.partitions)?;
    // Filter to paths on any partitions.
    ninja_installed_files
        .retain(|nif| partitions.iter().any(|p| PathBuf::from(nif).starts_with(p)));
    debug!("Stale file tracking took {} millis", track_time.elapsed().as_millis());
    progress::update("Checking files on device");
    let mut device_tree: HashMap<PathBuf, FileMetadata> =
        time!(device.fingerprint(partitions)?, profiler.device_fingerprint);
    // We expect the device to create lost+found dirs when mounting
    // new partitions.  Filter them out as if they don't exist.
    // However, if there are file inside of them, don't filter the
    // inner files.
    for p in partitions {
        device_tree.remove(&PathBuf::from(p).join("lost+found"));
    }
    progress::update("Checking files on host");
    let partition_paths: Vec<PathBuf> = partitions.iter().map(PathBuf::from).collect();
    let host_tree =
        time!(host.fingerprint(&product_out, &partition_paths)?, profiler.host_fingerprint);
    progress::update("Calculating diffs");
    // For now ignore diffs in permissions.  This will allow us to have a new adevice host tool
    // still working with an older adevice_fingerprint device tool.
    // [It also works on windows hosts]
    // Version 0.2 of the device tool will support permission mode.
    // We can check for that version of the tool or check to see if the metadata
    // on a well-known file (like system/bin/adevice_fingerprint) contains permission
    // bits before we change this to UsePermissions.
    let diff_mode = fingerprint::DiffMode::IgnorePermissions;

    let commands = &get_update_commands(
        &device_tree,
        &host_tree,
        &ninja_installed_files,
        product_out.clone(),
        &device.get_installed_apks()?,
        diff_mode,
        &partition_paths,
        stdout,
    )?;
    progress::stop();
    #[allow(clippy::collapsible_if)]
    if matches!(cli.command, cli::Commands::Status) {
        if commands.is_empty() {
            println!("   Device already up to date.");
        }
    }

    let max_changes = cli.global_options.max_allowed_changes;
    if matches!(cli.command, cli::Commands::Clean { .. }) {
        let deletes = &commands.deletes;
        if deletes.is_empty() {
            println!("   Nothing to clean.");
            return Ok(());
        }
        if deletes.len() > max_changes {
            bail!("There are {} files to be deleted which exceeds the configured limit of {}.\n  It is recommended that you reimage your device instead.  For small increases in the limit, you can run `adevice clean --max-allowed-changes={}.", deletes.len(), max_changes, deletes.len());
        }
        if matches!(cli.command, cli::Commands::Clean { force } if !force) {
            println!(
                "You are about to delete {} [untracked pushed] files. Are you sure? y/N",
                deletes.len()
            );
            let mut should_delete = String::new();
            stdin().read_line(&mut should_delete)?;
            if should_delete.trim().to_lowercase() != "y" {
                bail!("Not deleting");
            }
        }

        // Consider always reboot instead of soft restart after a clean.
        let restart_chooser = &RestartChooser::new(&restart_choice);
        device::update(restart_chooser, deletes, profiler, device, cli.should_wait())?;
    }

    if matches!(cli.command, cli::Commands::Update) {
        // Status
        if commands.is_empty() {
            println!("   Device already up to date.");
            return Ok(());
        }
        let all_cmds: HashMap<PathBuf, commands::AdbCommand> =
            commands.upserts.clone().into_iter().chain(commands.deletes.clone()).collect();

        if all_cmds.len() > max_changes {
            bail!("There are {} files out of date on the device, which exceeds the configured limit of {}.\n  It is recommended to reimage your device.  For small increases in the limit, you can run `adevice update --max-allowed-changes={}.", all_cmds.len(), max_changes, all_cmds.len());
        }
        writeln!(stdout, " * Updating {} files on device.", all_cmds.len())?;

        let changed_files = all_cmds.iter().map(|cmd| format!("{:?}", cmd.1.file)).collect();
        metrics.add_action_event_with_files_changed(
            "file_updates",
            Duration::new(0, 0),
            changed_files,
        );

        // Send the update commands, but retry once if we need to remount rw an extra time after a flash.
        for retry in 0..=1 {
            let update_result = device::update(
                &RestartChooser::new(&restart_choice),
                &all_cmds,
                profiler,
                device,
                cli.should_wait(),
            );
            if update_result.is_ok() {
                break;
            }

            if let Err(problem) = update_result {
                if retry == 1 {
                    bail!(" !! Not expecting errors after a retry.|n  Error:{:?}", problem);
                }
                // TODO(rbraunstein): Avoid string checks. Either check mounts directly for this case
                // or return json with the error message and code from adevice_fingerprint.
                if problem.root_cause().to_string().contains("Read-only file system") {
                    println!(" !! The device has a read-only file system.\n !! After a fresh image, the device needs an extra `remount` and `reboot` to adb push files.  Performing the remount and reboot now.");
                }
                time!(device.prep_after_flash()?, profiler.first_remount_rw);
            }
            println!("\n * Trying update again after remount and reboot.");
        }
    }
    metrics.display_survey();
    Ok(())
}

/// Returns the commands to update the device for every file that should be updated.
/// If there are errors, like some files in the staging set have not been built, then
/// an error result is returned.
#[allow(clippy::too_many_arguments)]
fn get_update_commands(
    device_tree: &HashMap<PathBuf, FileMetadata>,
    host_tree: &HashMap<PathBuf, FileMetadata>,
    ninja_installed_files: &[String],
    product_out: PathBuf,
    installed_packages: &HashSet<String>,
    diff_mode: DiffMode,
    partitions: &[PathBuf],
    stdout: &mut impl Write,
) -> Result<commands::Commands> {
    // NOTE: The Ninja deps list can be _ahead_of_ the product tree output list.
    //      i.e. m `nothing` will update our ninja list even before someone
    //      does a build to populate product out.
    //      We don't have a way to know if we are in this case or if the user
    //      ever did a `m droid`

    // We add implicit dirs up to the partition name to the tracked set so the set matches the staging set.
    let mut ninja_installed_dirs: HashSet<PathBuf> =
        ninja_installed_files.iter().flat_map(|p| parents(p, partitions)).collect();
    for p in partitions {
        ninja_installed_dirs.insert(PathBuf::from(p));
    }

    let tracked_set: HashSet<PathBuf> =
        ninja_installed_files.iter().map(PathBuf::from).chain(ninja_installed_dirs).collect();
    let host_set: HashSet<PathBuf> = host_tree.keys().map(PathBuf::clone).collect();

    // Files that are in the tracked set but NOT in the build directory. These need
    // to be built.
    let needs_building: HashSet<&PathBuf> = tracked_set.difference(&host_set).collect();
    let status_per_file = &collect_status_per_file(
        &tracked_set,
        host_tree,
        device_tree,
        &product_out,
        installed_packages,
        diff_mode,
    )?;
    progress::stop();
    print_status(stdout, status_per_file)?;
    // Shadowing apks are apks that are installed outside the system partition with `adb install`
    // If they exist, we should not push the apk that would be shadowed.
    let shadowing_apks: HashSet<&PathBuf> = status_per_file
        .iter()
        .filter_map(
            |(path, push_state)| {
                if *push_state == PushState::ApkInstalled {
                    Some(path)
                } else {
                    None
                }
            },
        )
        .collect();

    #[allow(clippy::len_zero)]
    if needs_building.len() > 0 {
        println!("WARNING: Please build needed [unbuilt] modules before updating.");
    }

    // Restrict the host set down to the ones that are in the tracked set and not installed in the data partition.
    let filtered_host_set: HashMap<PathBuf, FileMetadata> = host_tree
        .iter()
        .filter_map(|(key, value)| {
            if tracked_set.contains(key) && !shadowing_apks.contains(key) {
                Some((key.clone(), value.clone()))
            } else {
                None
            }
        })
        .collect();

    let filtered_changes = fingerprint::diff(&filtered_host_set, device_tree, diff_mode);
    Ok(commands::compose(&filtered_changes, &product_out))
}

// These are the partitions we will try to install to.
// ADB sync also has data, oem and vendor.
// There are some partition images (like boot.img) that we don't have a good way of determining
// the changed status of. (i.e. did they touch files that forces a flash/reimage).
// By default we will clean all the default partitions of stale files.
const DEFAULT_PARTITIONS: &[&str] = &["system", "system_ext", "odm", "product"];

/// If a user explicitly passes a partition, but that doesn't exist in the tracked files,
/// then bail.
/// Otherwise, if one of the default partitions does not exist (like system_ext), then
/// just remove it from the default.
fn validate_partitions(
    partition_root: &Path,
    tracked_files: &[String],
    cli_partitions: &Option<Vec<String>>,
) -> Result<Vec<String>> {
    // NOTE: We use PathBuf instead of String so starts_with matches path components.
    // Use the partitions the user passed in or default to system and system_ext
    if let Some(partitions) = cli_partitions {
        for partition in partitions {
            if !tracked_files.iter().any(|t| PathBuf::from(t).starts_with(partition)) {
                bail!("{partition:?} is not a valid partition for current lunch target.");
            }
        }
        for partition in partitions {
            if fs::read_dir(partition_root.join(partition)).is_err() {
                bail!("{partition:?} partition does not exist on host. Try rebuilding with m");
            }
        }
        return Ok(partitions.clone());
    }
    let found_partitions: Vec<String> = DEFAULT_PARTITIONS
        .iter()
        .filter_map(|part| match tracked_files.iter().any(|t| PathBuf::from(t).starts_with(part)) {
            true => Some(part.to_string()),
            false => None,
        })
        .collect();
    for partition in &found_partitions {
        if fs::read_dir(partition_root.join(partition)).is_err() {
            bail!("{partition:?} partition does not exist on host. Try rebuilding with m");
        }
    }

    Ok(found_partitions)
}

#[derive(Clone, PartialEq)]
enum PushState {
    Push,
    /// File is tracked and the device and host fingerprints match.
    UpToDate,
    /// File is not tracked but exists on device and host.
    TrackOrClean,
    /// File is on the device, but not host and not tracked.
    TrackAndBuildOrClean,
    /// File is tracked and on host but not on device.
    //PushNew,
    /// File is on host, but not tracked and not on device.
    TrackOrMakeClean,
    /// File is tracked and on the device, but is not in the build tree.
    /// `m` the module to build it.
    UntrackOrBuild,
    /// The apk was `installed` on top of the system image.  It will shadow any push
    /// we make to the system partitions.  It should be explicitly installed or uninstalled, not pushed.
    // TODO(rbraunstein): Store package name and path to file on disk so we can print a better
    // message to the user.
    ApkInstalled,
}

impl PushState {
    /// Message to print indicating what actions the user should take based on the
    /// state of the file.
    pub fn get_action_msg(self) -> String {
        match self {
	    PushState::Push => "Ready to push:\n  (These files are out of date on the device and will be pushed when you run `adevice update`)".to_string(),
	    // Note: we don't print up to date files.
	    PushState::UpToDate => "Up to date:  (These files are up to date on the device. There is nothing to do.)".to_string(),
	    PushState::TrackOrClean => "Untracked pushed files:\n  (These files are not tracked but exist on the device and host.)\n  (Use `adevice track` for the appropriate module to have them pushed.)".to_string(),
	    PushState::TrackAndBuildOrClean => "Stale device files:\n  (These files are on the device, but not built or tracked.)\n  (They will be cleaned with `adevice update` or `adevice clean`.)".to_string(),
	    PushState::TrackOrMakeClean => "Untracked built files:\n  (These files are in the build tree but not tracked or on the device.)\n  (You might want to `adevice track` the module.  It is safe to do nothing.)".to_string(),
	    PushState::UntrackOrBuild => "Unbuilt files:\n  (These files should be built so the device can be updated.)\n  (Rebuild and `adevice update`)".to_string(),
	    PushState::ApkInstalled => format!("ADB Installed files:\n{RED_WARNING_LINE}  (These files were installed with `adb install` or similar.  Pushing to the system partition will not make them available.)\n  (Either `adb uninstall` the package or `adb install` by hand.`)"),
	}
    }
}

// TODO(rbraunstein): Create a struct for each of the sections above for better formatting.
const RED_WARNING_LINE: &str = "  \x1b[1;31m!! Warning: !!\x1b[0m\n";

/// Group each file by state and print the state message followed by the files in that state.
fn print_status(stdout: &mut impl Write, files: &HashMap<PathBuf, PushState>) -> Result<()> {
    for state in [
        PushState::Push,
        // Skip UpToDate and TrackOrMakeClean, don't print those.
        PushState::TrackOrClean,
        PushState::TrackAndBuildOrClean,
        PushState::UntrackOrBuild,
        PushState::ApkInstalled,
    ] {
        print_files_in_state(stdout, files, state)?;
    }
    Ok(())
}

/// Determine if file is an apk and decide if we need to give a warning
/// about pushing to a system directory because it is already installed in /data
/// and will shadow a system apk if we push it.
fn installed_apk_action(
    file: &Path,
    product_out: &Path,
    installed_packages: &HashSet<String>,
) -> Result<PushState> {
    if file.extension() != Some(OsString::from("apk").as_os_str()) {
        return Ok(PushState::Push);
    }
    // See if this file was installed.
    if is_apk_installed(&product_out.join(file), installed_packages)? {
        Ok(PushState::ApkInstalled)
    } else {
        Ok(PushState::Push)
    }
}

/// Determine if the given apk has been installed via `adb install`.
/// This will allow us to decide if pushing to /system will cause problems because the
/// version we push would be shadowed by the `installed` version.
/// Run PackageManager commands from the shell to check if something is installed.
/// If this is a problem, we can build something in to adevice_fingerprint that
/// calls PackageManager#getInstalledApplications.
/// adb exec-out pm list packages  -s -f
fn is_apk_installed(host_path: &Path, installed_packages: &HashSet<String>) -> Result<bool> {
    let host_apk_path = host_path.as_os_str().to_str().unwrap();
    let aapt_output = std::process::Command::new("aapt2")
        .args(["dump", "permissions", host_apk_path])
        .output()
        .context(format!("Running aapt2 on host to see if apk installed: {}", host_apk_path))?;

    if !aapt_output.status.success() {
        let stderr = String::from_utf8(aapt_output.stderr)?;
        bail!("Unable to run aapt2 to get installed packages {:?}", stderr);
    }

    match package_from_aapt_dump_output(aapt_output.stdout) {
        Ok(package) => {
            debug!("AAPT dump found package: {package}");
            Ok(installed_packages.contains(&package))
        }
        Err(e) => bail!("Unable to run aapt2 to get package information {e:?}"),
    }
}

lazy_static! {
    static ref AAPT_PACKAGE_MATCHER: Regex =
        Regex::new(r"^package: (.+)$").expect("regex does not compile");
}

/// Filter aapt2 dump output to parse out the package name for the apk.
fn package_from_aapt_dump_output(stdout: Vec<u8>) -> Result<String> {
    let package_match = String::from_utf8(stdout)?
        .lines()
        .filter_map(|line| AAPT_PACKAGE_MATCHER.captures(line).map(|x| x[1].to_string()))
        .collect();
    Ok(package_match)
}

/// Go through all files that exist on the host, device, and tracking set.
/// Ignore any file that is in all three and has the same fingerprint on the host and device.
/// States where the user should take action:
///   Build
///   Clean
///   Track
///   Untrack
fn collect_status_per_file(
    tracked_set: &HashSet<PathBuf>,
    host_tree: &HashMap<PathBuf, FileMetadata>,
    device_tree: &HashMap<PathBuf, FileMetadata>,
    product_out: &Path,
    installed_packages: &HashSet<String>,
    diff_mode: DiffMode,
) -> Result<HashMap<PathBuf, PushState>> {
    let mut all_files: Vec<&PathBuf> =
        host_tree.keys().chain(device_tree.keys()).chain(tracked_set.iter()).collect();
    all_files.dedup();

    let states: HashMap<PathBuf, PushState> = all_files
    .par_iter()
    .map(|f| {
        let on_device = device_tree.contains_key(*f);
        let on_host = host_tree.contains_key(*f);
        let tracked = tracked_set.contains(*f);

        // I think keeping tracked/untracked else is clearer than collapsing.
        #[allow(clippy::collapsible_else_if)]
        let push_state = if tracked {
            if on_device && on_host {
                if fingerprint::is_metadata_diff(
                    device_tree.get(*f).unwrap(),
                    host_tree.get(*f).unwrap(),
                    diff_mode,
                ) {
                    // PushDiff
                    installed_apk_action(f, product_out, installed_packages).expect("checking if apk installed")
                } else {
                    // Else normal case, do nothing.
                    // TODO(rbraunstein): Do we need to check for installed apk and warn.
                    // 1) User updates apk
                    // 2) User adb install
                    // 3) User reverts code and builds
                    //   (host and device match but installed apk shadows system version).
                    // For now, don't look for extra problems.
                    PushState::UpToDate
                }
            } else if !on_host {
                // We don't care if it is on the device or not, it has to built if it isn't
                // on the host.
                PushState::UntrackOrBuild
            } else {
                assert!(
                    !on_device && on_host,
                    "Unexpected state for file: {f:?}, tracked: {tracked} on_device: {on_device}, on_host: {on_host}"
                );
                // TODO(rbraunstein): Is it possible for an apk to be adb installed, but not in the system image?
                // I guess so, but seems weird.  Add check InstalledApk here too.
                // PushNew
                PushState::Push
            }
        } else {
            if on_device && on_host {
                PushState::TrackOrClean
            } else if on_device && !on_host {
                PushState::TrackAndBuildOrClean
            } else {
                // Note: case of !tracked, !on_host, !on_device is not possible.
                // So only one case left.
                assert!(
                    !on_device && on_host,
                    "Unexpected state for file: {f:?}, tracked: {tracked} on_device: {on_device}, on_host: {on_host}"
                );
                PushState::TrackOrMakeClean
            }
        };
        (PathBuf::from(f), push_state)
    })
    .collect();
    Ok(states)
}

/// Find all files in a given state, and if that file list is not empty, print the
/// state message and all the files (sorted).
/// Only prints stages that files in that stage.
fn print_files_in_state(
    stdout: &mut impl Write,
    files: &HashMap<PathBuf, PushState>,
    push_state: PushState,
) -> Result<()> {
    let filtered_files: HashMap<&PathBuf, &PushState> =
        files.iter().filter(|(_, state)| *state == &push_state).collect();

    if filtered_files.is_empty() {
        return Ok(());
    }
    writeln!(stdout, "{}", &push_state.get_action_msg())?;
    let file_list_output = filtered_files
        .keys()
        .sorted()
        .map(|path| format!("\t{}", path.display()))
        .collect::<Vec<String>>()
        .join("\n");
    writeln!(stdout, "{}", file_list_output)?;
    Ok(())
}

fn get_product_out_from_env() -> Option<PathBuf> {
    match std::env::var("ANDROID_PRODUCT_OUT") {
        Ok(x) if !x.is_empty() => Some(PathBuf::from(x)),
        _ => None,
    }
}

/// Return all path components of file_path up to a passed partition.
/// Given system/bin/logd and partition "system",
/// return ["system/bin/logd", "system/bin"], not "system" or ""

fn parents(file_path: &str, partitions: &[PathBuf]) -> Vec<PathBuf> {
    PathBuf::from(file_path)
        .ancestors()
        .map(|p| p.to_path_buf())
        .take_while(|p| !partitions.contains(p))
        .collect()
}

#[allow(missing_docs)]
#[derive(Default)]
pub struct Profiler {
    pub device_fingerprint: Duration,
    pub host_fingerprint: Duration,
    pub ninja_deps_computer: Duration,
    /// Time to run all the "adb push" or "adb rm" commands.
    pub adb_cmds: Duration,
    /// Time to run "adb reboot" or "exec-out start".
    pub restart: Duration,
    pub restart_type: String,
    /// Time for device to respond to "wait-for-device".
    pub wait_for_device: Duration,
    /// Time for sys.boot_completed to be 1 after wait-for-device.
    pub wait_for_boot_completed: Duration,
    /// The first time after a userdebug build is flashed/created, we need
    /// to mount rw and reboot.
    pub first_remount_rw: Duration,
    pub total: Duration,
}

impl std::string::ToString for Profiler {
    fn to_string(&self) -> String {
        [
            " Operation profile: (secs)".to_string(),
            format!("Device Fingerprint - {}", self.device_fingerprint.as_secs()),
            format!("Host fingerprint - {}", self.host_fingerprint.as_secs()),
            format!("Ninja - {}", self.ninja_deps_computer.as_secs()),
            format!("Adb Cmds - {}", self.adb_cmds.as_secs()),
            format!("Restart({})- {}", self.restart_type, self.restart.as_secs()),
            format!("Wait For device connected - {}", self.wait_for_device.as_secs()),
            format!("Wait For boot completed - {}", self.wait_for_boot_completed.as_secs()),
            format!("First remount RW - {}", self.first_remount_rw.as_secs()),
            format!("TOTAL - {}", self.total.as_secs()),
        ]
        .join("\n\t")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::fingerprint::{self, DiffMode};
    use std::path::PathBuf;
    use tempfile::TempDir;

    // TODO(rbraunstein): Capture/test stdout and logging.
    //  Test stdout: https://users.rust-lang.org/t/how-to-test-functions-that-use-println/67188/5
    #[test]
    fn empty_inputs() -> Result<()> {
        let device_files: HashMap<PathBuf, FileMetadata> = HashMap::new();
        let host_files: HashMap<PathBuf, FileMetadata> = HashMap::new();
        let ninja_deps: Vec<String> = vec![];
        let product_out = PathBuf::from("");
        let installed_apks = HashSet::<String>::new();
        let partitions = Vec::new();
        let mut stdout = Vec::new();

        let results = get_update_commands(
            &device_files,
            &host_files,
            &ninja_deps,
            product_out,
            &installed_apks,
            DiffMode::UsePermissions,
            &partitions,
            &mut stdout,
        )?;
        assert_eq!(results.upserts.values().len(), 0);
        Ok(())
    }

    #[test]
    fn host_and_ninja_file_not_on_device() -> Result<()> {
        // Relative to product out?
        let product_out = PathBuf::from("");
        let installed_apks = HashSet::<String>::new();
        let partitions = Vec::new();
        let mut stdout = Vec::new();

        let results = get_update_commands(
            // Device files
            &HashMap::new(),
            // Host files
            &HashMap::from([
                (PathBuf::from("system/myfile"), file_metadata("digest1")),
                (PathBuf::from("system"), dir_metadata()),
            ]),
            // Ninja deps
            &["system".to_string(), "system/myfile".to_string()],
            product_out,
            &installed_apks,
            DiffMode::UsePermissions,
            &partitions,
            &mut stdout,
        )?;
        assert_eq!(results.upserts.values().len(), 2);
        Ok(())
    }

    #[test]
    fn on_host_not_in_tracked_on_device() -> Result<()> {
        let results = call_update(&FakeState {
            device_data: &["system/f1"],
            host_data: &["system/f1"],
            tracked_set: &[],
        })?
        .upserts;
        assert_eq!(0, results.values().len());
        Ok(())
    }

    #[test]
    fn in_host_not_in_tracked_not_on_device() -> Result<()> {
        let results = call_update(&FakeState {
            device_data: &[""],
            host_data: &["system/f1"],
            tracked_set: &[],
        })?
        .upserts;
        assert_eq!(0, results.values().len());
        Ok(())
    }

    #[test]
    fn test_parents_stops_at_partition() {
        assert_eq!(
            vec![
                PathBuf::from("some/long/path/file"),
                PathBuf::from("some/long/path"),
                PathBuf::from("some/long"),
            ],
            parents("some/long/path/file", &[PathBuf::from("some")]),
        );
    }

    #[test]
    fn validate_partition_removes_unused_default_partition() -> Result<()> {
        let tmp_root = TempDir::new().unwrap();
        fs::create_dir_all(tmp_root.path().join("system")).unwrap();

        // No system_ext here, so remove from default partitions
        let ninja_deps = vec![
            "system/file1".to_string(),
            "file3".to_string(),
            "system/dir2/file1".to_string(),
            "data/sys/file4".to_string(),
        ];
        assert_eq!(
            vec!["system".to_string(),],
            validate_partitions(tmp_root.path(), &ninja_deps, &None)?
        );
        Ok(())
    }

    #[test]
    fn validate_partition_bails_on_bad_partition_name() {
        let tmp_root = TempDir::new().unwrap();
        fs::create_dir_all(tmp_root.path().join("system")).unwrap();
        fs::create_dir_all(tmp_root.path().join("sys")).unwrap();

        let ninja_deps = vec![
            "system/file1".to_string(),
            "file3".to_string(),
            "system/dir2/file1".to_string(),
            "data/sys/file4".to_string(),
        ];
        // "sys" isn't a valid partition name, but it matches a prefix of "system".
        // Should bail.
        match validate_partitions(tmp_root.path(), &ninja_deps, &Some(vec!["sys".to_string()])) {
            Ok(_) => panic!("Expected error"),
            Err(e) => {
                assert!(
                    e.to_string().contains("\"sys\" is not a valid partition"),
                    "{}",
                    e.to_string()
                )
            }
        }
    }

    #[test]
    fn validate_partition_bails_on_no_partition_on_host() {
        let tmp_root = TempDir::new().unwrap();

        let ninja_deps = vec!["system/file1".to_string()];
        match validate_partitions(tmp_root.path(), &ninja_deps, &Some(vec!["system".to_string()])) {
            Ok(_) => panic!("Expected error"),
            Err(e) => {
                assert!(
                    e.to_string().contains("\"system\" partition does not exist on host"),
                    "{}",
                    e.to_string()
                )
            }
        }
    }

    // TODO(rbraunstein): Test case where on device and up to date, but not tracked.

    struct FakeState {
        device_data: &'static [&'static str],
        host_data: &'static [&'static str],
        tracked_set: &'static [&'static str],
    }

    // Helper to call update.
    // Uses filename for the digest in the fingerprint
    // Add directories for every file on the host like walkdir would do.
    // `update` adds the directories for the tracked set so we don't do that here.
    fn call_update(fake_state: &FakeState) -> Result<commands::Commands> {
        let product_out = PathBuf::from("");
        let installed_apks = HashSet::<String>::new();
        let partitions = Vec::new();

        let mut device_files: HashMap<PathBuf, FileMetadata> = HashMap::new();
        let mut host_files: HashMap<PathBuf, FileMetadata> = HashMap::new();
        for d in fake_state.device_data {
            // Set the digest to the filename for now.
            device_files.insert(PathBuf::from(d), file_metadata(d));
        }
        for h in fake_state.host_data {
            // Set the digest to the filename for now.
            host_files.insert(PathBuf::from(h), file_metadata(h));
            // Add the dir too.
        }

        let tracked_set: Vec<String> =
            fake_state.tracked_set.iter().map(|s| s.to_string()).collect();

        let mut stdout = Vec::new();
        get_update_commands(
            &device_files,
            &host_files,
            &tracked_set,
            product_out,
            &installed_apks,
            DiffMode::UsePermissions,
            &partitions,
            &mut stdout,
        )
    }

    fn file_metadata(digest: &str) -> FileMetadata {
        FileMetadata {
            file_type: fingerprint::FileType::File,
            digest: digest.to_string(),
            ..Default::default()
        }
    }

    fn dir_metadata() -> FileMetadata {
        FileMetadata { file_type: fingerprint::FileType::Directory, ..Default::default() }
    }
    // TODO(rbraunstein): Add tests for collect_status_per_file after we decide on output.
    // TODO(rbraunstein): Add tests for shadowing apks and ensure we don't install the system version.
}
