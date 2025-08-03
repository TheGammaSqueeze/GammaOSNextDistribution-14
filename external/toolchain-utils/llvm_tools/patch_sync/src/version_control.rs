// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

use anyhow::{anyhow, bail, ensure, Context, Result};
use regex::Regex;
use std::ffi::OsStr;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

const CHROMIUMOS_OVERLAY_REL_PATH: &str = "src/third_party/chromiumos-overlay";
const ANDROID_LLVM_REL_PATH: &str = "toolchain/llvm_android";

// Need to checkout the upstream, rather than the local clone.
const CROS_MAIN_BRANCH: &str = "cros/main";
const ANDROID_MAIN_BRANCH: &str = "aosp/main";
const WORK_BRANCH_NAME: &str = "__patch_sync_tmp";

/// Context struct to keep track of both ChromiumOS and Android checkouts.
#[derive(Debug)]
pub struct RepoSetupContext {
    pub cros_checkout: PathBuf,
    pub android_checkout: PathBuf,
    /// Run `repo sync` before doing any comparisons.
    pub sync_before: bool,
    pub wip_mode: bool,
    pub enable_cq: bool,
    /// Generally LLVM ebuilds are now 9999 LIVE ebuilds, so only uprev if this is set.
    pub uprev_ebuilds: bool,
}

impl RepoSetupContext {
    pub fn setup(&self) -> Result<()> {
        if self.sync_before {
            {
                let crpp = self.cros_patches_path();
                let cros_git = crpp.parent().unwrap();
                git_cd_cmd(cros_git, ["checkout", CROS_MAIN_BRANCH])?;
            }
            {
                let anpp = self.android_patches_path();
                let android_git = anpp.parent().unwrap();
                git_cd_cmd(android_git, ["checkout", ANDROID_MAIN_BRANCH])?;
            }
            repo_cd_cmd(&self.cros_checkout, &["sync", CHROMIUMOS_OVERLAY_REL_PATH])?;
            repo_cd_cmd(&self.android_checkout, &["sync", ANDROID_LLVM_REL_PATH])?;
        }
        Ok(())
    }

    pub fn cros_repo_upload<S: AsRef<str>>(&self, reviewers: &[S]) -> Result<()> {
        let llvm_dir = self
            .cros_checkout
            .join(&CHROMIUMOS_OVERLAY_REL_PATH)
            .join("sys-devel/llvm");
        ensure!(
            llvm_dir.is_dir(),
            "CrOS LLVM dir {} is not a directory",
            llvm_dir.display()
        );

        if self.uprev_ebuilds {
            Self::rev_bump_llvm(&llvm_dir)?;
        }

        let mut extra_args = Vec::new();
        for reviewer in reviewers {
            extra_args.push("--re");
            extra_args.push(reviewer.as_ref());
        }
        if self.wip_mode {
            extra_args.push("--wip");
            extra_args.push("--no-emails");
        }
        if self.enable_cq {
            extra_args.push("--label=Commit-Queue+1");
        }
        Self::repo_upload(
            &self.cros_checkout,
            CHROMIUMOS_OVERLAY_REL_PATH,
            &Self::build_commit_msg(
                "llvm: Synchronize patches from android",
                "android",
                "chromiumos",
                "BUG=None\nTEST=CQ",
            ),
            extra_args,
        )
    }

    pub fn android_repo_upload<S: AsRef<str>>(&self, reviewers: &[S]) -> Result<()> {
        let mut extra_args = Vec::new();
        for reviewer in reviewers {
            extra_args.push("--re");
            extra_args.push(reviewer.as_ref());
        }
        if self.wip_mode {
            extra_args.push("--wip");
            extra_args.push("--no-emails");
        }
        if self.enable_cq {
            extra_args.push("--label=Presubmit-Ready+1");
        }
        Self::repo_upload(
            &self.android_checkout,
            ANDROID_LLVM_REL_PATH,
            &Self::build_commit_msg(
                "Synchronize patches from chromiumos",
                "chromiumos",
                "android",
                "Test: N/A",
            ),
            extra_args,
        )
    }

    fn cros_cleanup(&self) -> Result<()> {
        let git_path = self.cros_checkout.join(CHROMIUMOS_OVERLAY_REL_PATH);
        Self::cleanup_branch(&git_path, CROS_MAIN_BRANCH, WORK_BRANCH_NAME)
            .with_context(|| format!("cleaning up branch {}", WORK_BRANCH_NAME))?;
        Ok(())
    }

    fn android_cleanup(&self) -> Result<()> {
        let git_path = self.android_checkout.join(ANDROID_LLVM_REL_PATH);
        Self::cleanup_branch(&git_path, ANDROID_MAIN_BRANCH, WORK_BRANCH_NAME)
            .with_context(|| format!("cleaning up branch {}", WORK_BRANCH_NAME))?;
        Ok(())
    }

    /// Wrapper around cleanups to ensure both get run, even if errors appear.
    pub fn cleanup(&self) {
        if let Err(e) = self.cros_cleanup() {
            eprintln!("Failed to clean up chromiumos, continuing: {}", e);
        }
        if let Err(e) = self.android_cleanup() {
            eprintln!("Failed to clean up android, continuing: {}", e);
        }
    }

    /// Get the Android path to the PATCHES.json file
    pub fn android_patches_path(&self) -> PathBuf {
        self.android_checkout
            .join(&ANDROID_LLVM_REL_PATH)
            .join("patches/PATCHES.json")
    }

    /// Get the ChromiumOS path to the PATCHES.json file
    pub fn cros_patches_path(&self) -> PathBuf {
        self.cros_checkout
            .join(&CHROMIUMOS_OVERLAY_REL_PATH)
            .join("sys-devel/llvm/files/PATCHES.json")
    }

    /// Return the contents of the old PATCHES.json from ChromiumOS
    pub fn old_cros_patch_contents(&self, hash: &str) -> Result<String> {
        Self::old_file_contents(
            hash,
            &self.cros_checkout.join(CHROMIUMOS_OVERLAY_REL_PATH),
            Path::new("sys-devel/llvm/files/PATCHES.json"),
        )
    }

    /// Return the contents of the old PATCHES.json from android
    pub fn old_android_patch_contents(&self, hash: &str) -> Result<String> {
        Self::old_file_contents(
            hash,
            &self.android_checkout.join(ANDROID_LLVM_REL_PATH),
            Path::new("patches/PATCHES.json"),
        )
    }

    fn repo_upload<'a, I: IntoIterator<Item = &'a str>>(
        checkout_path: &Path,
        subproject_git_wd: &'a str,
        commit_msg: &str,
        extra_flags: I,
    ) -> Result<()> {
        let git_path = &checkout_path.join(&subproject_git_wd);
        ensure!(
            git_path.is_dir(),
            "git_path {} is not a directory",
            git_path.display()
        );
        repo_cd_cmd(
            checkout_path,
            &["start", WORK_BRANCH_NAME, subproject_git_wd],
        )?;
        let base_args = ["upload", "--br", WORK_BRANCH_NAME, "-y", "--verify"];
        let new_args = base_args
            .iter()
            .copied()
            .chain(extra_flags)
            .chain(["--", subproject_git_wd]);
        git_cd_cmd(git_path, &["add", "."])
            .and_then(|_| git_cd_cmd(git_path, &["commit", "-m", commit_msg]))
            .and_then(|_| repo_cd_cmd(checkout_path, new_args))?;
        Ok(())
    }

    /// Clean up the git repo after we're done with it.
    fn cleanup_branch(git_path: &Path, base_branch: &str, rm_branch: &str) -> Result<()> {
        git_cd_cmd(git_path, ["restore", "."])?;
        git_cd_cmd(git_path, ["clean", "-fd"])?;
        git_cd_cmd(git_path, ["checkout", base_branch])?;
        // It's acceptable to be able to not delete the branch. This may be
        // because the branch does not exist, which is an expected result.
        // Since this is a very common case, we won't report any failures related
        // to this command failure as it'll pollute the stderr logs.
        let _ = git_cd_cmd(git_path, ["branch", "-D", rm_branch]);
        Ok(())
    }

    /// Increment LLVM's revision number
    fn rev_bump_llvm(llvm_dir: &Path) -> Result<PathBuf> {
        let ebuild = find_ebuild(llvm_dir)
            .with_context(|| format!("finding ebuild in {} to rev bump", llvm_dir.display()))?;
        let ebuild_dir = ebuild.parent().unwrap();
        let suffix_matcher = Regex::new(r"-r([0-9]+)\.ebuild").unwrap();
        let ebuild_name = ebuild
            .file_name()
            .unwrap()
            .to_str()
            .ok_or_else(|| anyhow!("converting ebuild filename to utf-8"))?;
        let new_path = if let Some(captures) = suffix_matcher.captures(ebuild_name) {
            let full_suffix = captures.get(0).unwrap().as_str();
            let cur_version = captures.get(1).unwrap().as_str().parse::<u32>().unwrap();
            let new_filename =
                ebuild_name.replace(full_suffix, &format!("-r{}.ebuild", cur_version + 1_u32));
            let new_path = ebuild_dir.join(new_filename);
            fs::rename(&ebuild, &new_path)?;
            new_path
        } else {
            // File did not end in a revision. We should append -r1 to the end.
            let new_filename = ebuild.file_stem().unwrap().to_string_lossy() + "-r1.ebuild";
            let new_path = ebuild_dir.join(new_filename.as_ref());
            fs::rename(&ebuild, &new_path)?;
            new_path
        };
        Ok(new_path)
    }

    /// Return the contents of an old file in git
    fn old_file_contents(hash: &str, pwd: &Path, file: &Path) -> Result<String> {
        let git_ref = format!(
            "{}:{}",
            hash,
            file.to_str()
                .ok_or_else(|| anyhow!("failed to convert filepath to str"))?
        );
        let output = git_cd_cmd(pwd, &["show", &git_ref])?;
        if !output.status.success() {
            bail!("could not get old file contents for {}", &git_ref)
        }
        String::from_utf8(output.stdout)
            .with_context(|| format!("converting {} file contents to UTF-8", &git_ref))
    }

    /// Create the commit message
    fn build_commit_msg(subj: &str, from: &str, to: &str, footer: &str) -> String {
        format!(
            "[patch_sync] {subj}\n\n\
Copies new PATCHES.json changes from {from} to {to}.\n
For questions about this job, contact chromeos-toolchain@google.com\n
This change is generated automatically by the script go/llvm-patch-sync\n\n
{footer}",
        )
    }
}

/// Return the path of an ebuild located within the given directory.
fn find_ebuild(dir: &Path) -> Result<PathBuf> {
    // The logic here is that we create an iterator over all file paths to ebuilds
    // with _pre in the name. Then we sort those ebuilds based on their revision numbers.
    // Then we return the highest revisioned one.

    let ebuild_rev_matcher = Regex::new(r"-r([0-9]+)\.ebuild").unwrap();
    // For LLVM ebuilds, we only want to check for ebuilds that have this in their file name.
    let per_heuristic = "_pre";
    // Get an iterator over all ebuilds with a _per in the file name.
    let ebuild_candidates = fs::read_dir(dir)?.filter_map(|entry| {
        let entry = entry.ok()?;
        let path = entry.path();
        if path.extension()? != "ebuild" {
            // Not an ebuild, ignore.
            return None;
        }
        let stem = path.file_stem()?.to_str()?;
        if stem.contains(per_heuristic) {
            return Some(path);
        }
        None
    });
    let try_parse_ebuild_rev = |path: PathBuf| -> Option<(u64, PathBuf)> {
        let name = path.file_name()?;
        if let Some(rev_match) = ebuild_rev_matcher.captures(name.to_str()?) {
            let rev_str = rev_match.get(1)?;
            let rev_num = rev_str.as_str().parse::<u64>().ok()?;
            return Some((rev_num, path));
        }
        // If it doesn't have a revision, then it's revision 0.
        Some((0, path))
    };
    let mut sorted_candidates: Vec<_> =
        ebuild_candidates.filter_map(try_parse_ebuild_rev).collect();
    sorted_candidates.sort_unstable_by_key(|x| x.0);
    let highest_rev_ebuild = sorted_candidates
        .pop()
        .ok_or_else(|| anyhow!("could not find ebuild"))?;
    Ok(highest_rev_ebuild.1)
}

/// Run a given git command from inside a specified git dir.
pub fn git_cd_cmd<I, S>(pwd: &Path, args: I) -> Result<Output>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let mut command = Command::new("git");
    command.current_dir(&pwd).args(args);
    let output = command.output()?;
    if !output.status.success() {
        bail!(
            "git command failed:\n  {:?}\nstdout --\n{}\nstderr --\n{}",
            command,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr),
        );
    }
    Ok(output)
}

pub fn repo_cd_cmd<I, S>(pwd: &Path, args: I) -> Result<()>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let mut command = Command::new("repo");
    command.current_dir(&pwd).args(args);
    let status = command.status()?;
    if !status.success() {
        bail!("repo command failed:\n  {:?}  \n", command)
    }
    Ok(())
}

#[cfg(test)]
mod test {
    use super::*;
    use rand::prelude::Rng;
    use std::env;
    use std::fs::File;

    #[test]
    fn test_revbump_ebuild() {
        // Random number to append at the end of the test folder to prevent conflicts.
        let rng: u32 = rand::thread_rng().gen();
        let llvm_dir = env::temp_dir().join(format!("patch_sync_test_{}", rng));
        fs::create_dir(&llvm_dir).expect("creating llvm dir in temp directory");

        {
            // With revision
            let ebuild_name = "llvm-13.0_pre433403_p20211019-r10.ebuild";
            let ebuild_path = llvm_dir.join(ebuild_name);
            File::create(&ebuild_path).expect("creating test ebuild file");
            let new_ebuild_path =
                RepoSetupContext::rev_bump_llvm(&llvm_dir).expect("rev bumping the ebuild");
            assert!(
                new_ebuild_path.ends_with("llvm-13.0_pre433403_p20211019-r11.ebuild"),
                "{}",
                new_ebuild_path.display()
            );
            fs::remove_file(new_ebuild_path).expect("removing renamed ebuild file");
        }
        {
            // Without revision
            let ebuild_name = "llvm-13.0_pre433403_p20211019.ebuild";
            let ebuild_path = llvm_dir.join(ebuild_name);
            File::create(&ebuild_path).expect("creating test ebuild file");
            let new_ebuild_path =
                RepoSetupContext::rev_bump_llvm(&llvm_dir).expect("rev bumping the ebuild");
            assert!(
                new_ebuild_path.ends_with("llvm-13.0_pre433403_p20211019-r1.ebuild"),
                "{}",
                new_ebuild_path.display()
            );
            fs::remove_file(new_ebuild_path).expect("removing renamed ebuild file");
        }
        {
            // With both
            let ebuild_name = "llvm-13.0_pre433403_p20211019.ebuild";
            let ebuild_path = llvm_dir.join(ebuild_name);
            File::create(&ebuild_path).expect("creating test ebuild file");
            let ebuild_link_name = "llvm-13.0_pre433403_p20211019-r2.ebuild";
            let ebuild_link_path = llvm_dir.join(ebuild_link_name);
            File::create(&ebuild_link_path).expect("creating test ebuild link file");
            let new_ebuild_path =
                RepoSetupContext::rev_bump_llvm(&llvm_dir).expect("rev bumping the ebuild");
            assert!(
                new_ebuild_path.ends_with("llvm-13.0_pre433403_p20211019-r3.ebuild"),
                "{}",
                new_ebuild_path.display()
            );
            fs::remove_file(new_ebuild_path).expect("removing renamed ebuild link file");
            fs::remove_file(ebuild_path).expect("removing renamed ebuild file");
        }

        fs::remove_dir(&llvm_dir).expect("removing temp test dir");
    }
}
