use clap::{Args, Parser, Subcommand};

#[derive(Parser)]
#[command(
    about = "Tool to push your rebuilt modules to your device.\nSet ANDROID_SERIAL to choose your device if there is more than one."
)]
#[command(version = "0.4")]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
    #[clap(flatten)]
    pub global_options: GlobalOptions,
}

#[derive(Subcommand)]
pub enum Commands {
    /// Shows the file differences between build tree and host.
    /// Show the actions that would be run.
    Status,
    /// Updates the device (via adb push) with files from $ANDROID_PRODUCT_OUT.
    /// Only pushes files listed on --partitions.
    /// This does not work well when $ANDROID_PRODUCT_OUT and the device image
    /// are vastly different.  You should reimage the device in that case.
    Update,
    /// Adds module name to the list of tracked modules.
    /// If an installed file under $ANDROID_PRODUCT_OUT is not
    /// part of a tracked module or the base image, then it will
    /// not be pushed to the device.
    Track(ModuleNames),
    /// Change the base module we are tracking from `droid` to something else.
    TrackBase(BaseModule),
    /// Removes module name from list of tracked modules.
    /// See `track` for more details.
    Untrack(ModuleNames),
    /// Removes untracked files from the device.
    Clean {
        #[clap(long, short)]
        force: bool,
    },
}

#[derive(Debug, Args)]
pub struct ModuleNames {
    /// List one or modules, space separated.
    /// Use the module name in Android.bp
    pub modules: Vec<String>,
}

#[derive(Debug, Args)]
pub struct BaseModule {
    /// The module name the system image is built from like 'droid' or 'sync'.
    /// It can also be an unbundled mainline module name.
    pub base: String,
}

#[derive(Args, Debug)]
pub struct GlobalOptions {
    // TODO(rbraunstein): Revisit all the command name descriptions.
    // TODO(rbraunstein): Add system_other to the default list, but deal gracefully
    // with it not being on the device.
    /// Partitions in the product tree to sync. Repeat arg or comma-separate.
    #[clap(long, short, global = true, value_delimiter = ',')]
    pub partitions: Option<Vec<String>>,
    // TODO(rbraunstein): Validate relative, not absolute paths.
    /// If unset defaults to ANDROID_PRODUCT_OUT env variable.
    #[clap(long = "product_out", global = true)]
    pub product_out: Option<String>,
    /// Do not make any modification if more than this many are needed
    #[clap(long, short, default_value_t = 400, global = true)]
    pub max_allowed_changes: usize,
    /// If passed, use the device, otherwise use the only connected device or ANDROID_SERIAL env value.
    #[clap(long, short, global = true)]
    pub serial: Option<String>,
    /// Override the type of restart that happens after an update.
    #[clap(long = "restart", short, global = true, value_enum, default_value_t=RestartChoice::Auto)]
    pub restart_choice: RestartChoice,
    /// Path to config file.  Uses $HOME/.config/asuite/adevice-tracking.json if unset.
    #[clap(long = "config", global = true)]
    pub config_path: Option<String>,
    // Don't wait for device to become available after restarting it.
    #[clap(long = "nowait", global = true, alias = "no_wait", alias = "no-wait")]
    pub nowait: bool,
}

#[derive(clap::ValueEnum, Clone, Debug)]
pub enum Verbosity {
    /// Only show minimal information.
    None,
    /// Show all adb operations.
    Details,
    /// For debugging internals of tool and timings.
    Debug,
}

/// Allows you to choose how to reboot or to not reboot.
#[derive(clap::ValueEnum, Clone, Debug)]
pub enum RestartChoice {
    /// Let the system choose the restart based on the files changed.
    Auto,
    /// Don't restart.
    None,
    /// Always do a full system reboot after updates.
    Reboot,
    /// Always do a framework restart restart after updates.
    Restart,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Wait {
    Yes,
    No,
}

impl From<Wait> for bool {
    fn from(w: Wait) -> bool {
        match w {
            Wait::Yes => true,
            Wait::No => false,
        }
    }
}

impl Cli {
    /// Decide if the options indicate that we should wait for the device.
    /// Exists in case the cli options get more complicated like --wait=false
    pub fn should_wait(&self) -> Wait {
        match self.global_options.nowait {
            true => Wait::No,
            false => Wait::Yes,
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::cli::Wait;

    use super::Cli;
    use clap::Parser;

    #[test]
    fn nowait_works() {
        let cli = Cli::parse_from(["fake_prog", "update", "--nowait"]);
        assert!(cli.global_options.nowait);
    }

    #[test]
    fn no_wait_alias_works() {
        let cli = Cli::parse_from(["fake_prog", "update", "--no_wait"]);
        assert!(cli.global_options.nowait);
    }

    #[test]
    fn unset_nowait_is_none() {
        let cli = Cli::parse_from(["fake_prog", "update"]);
        assert!(!cli.global_options.nowait);
    }

    #[test]
    fn it_should_wait() {
        let cli = Cli::parse_from(["fake_prog", "update"]);
        assert_eq!(Wait::Yes, cli.should_wait());
        let should_wait: bool = cli.should_wait().into();
        assert!(should_wait);
    }

    #[test]
    fn it_should_not_wait() {
        let cli = Cli::parse_from(["fake_prog", "update", "--nowait"]);
        assert_eq!(Wait::No, cli.should_wait());
        let should_wait: bool = cli.should_wait().into();
        assert!(!should_wait);
    }
}
