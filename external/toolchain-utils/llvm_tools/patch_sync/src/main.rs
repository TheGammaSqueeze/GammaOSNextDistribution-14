// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

mod android_utils;
mod patch_parsing;
mod version_control;

use std::borrow::ToOwned;
use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use structopt::StructOpt;

use patch_parsing::{filter_patches_by_platform, PatchCollection, PatchDictSchema, VersionRange};
use version_control::RepoSetupContext;

fn main() -> Result<()> {
    match Opt::from_args() {
        Opt::Show {
            cros_checkout_path,
            android_checkout_path,
            sync,
            keep_unmerged,
        } => show_subcmd(ShowOpt {
            cros_checkout_path,
            android_checkout_path,
            sync,
            keep_unmerged,
        }),
        Opt::Transpose {
            cros_checkout_path,
            cros_reviewers,
            old_cros_ref,
            android_checkout_path,
            android_reviewers,
            old_android_ref,
            sync,
            verbose,
            dry_run,
            no_commit,
            wip,
            disable_cq,
            uprev,
        } => transpose_subcmd(TransposeOpt {
            cros_checkout_path,
            cros_reviewers: cros_reviewers
                .map(|r| r.split(',').map(ToOwned::to_owned).collect())
                .unwrap_or_default(),
            old_cros_ref,
            android_checkout_path,
            android_reviewers: android_reviewers
                .map(|r| r.split(',').map(ToOwned::to_owned).collect())
                .unwrap_or_default(),
            old_android_ref,
            sync,
            verbose,
            dry_run,
            no_commit,
            wip,
            disable_cq,
            uprev_ebuilds: uprev,
        }),
    }
}

struct ShowOpt {
    cros_checkout_path: PathBuf,
    android_checkout_path: PathBuf,
    keep_unmerged: bool,
    sync: bool,
}

fn show_subcmd(args: ShowOpt) -> Result<()> {
    let ShowOpt {
        cros_checkout_path,
        android_checkout_path,
        keep_unmerged,
        sync,
    } = args;
    let ctx = RepoSetupContext {
        cros_checkout: cros_checkout_path,
        android_checkout: android_checkout_path,
        sync_before: sync,
        wip_mode: true,   // Has no effect, as we're not making changes
        enable_cq: false, // Has no effect, as we're not uploading anything
        uprev_ebuilds: false,
    };
    ctx.setup()?;
    let make_collection = |platform: &str, patches_fp: &Path| -> Result<PatchCollection> {
        let parsed_collection = PatchCollection::parse_from_file(patches_fp)
            .with_context(|| format!("could not parse {} PATCHES.json", platform))?;
        Ok(if keep_unmerged {
            parsed_collection
        } else {
            filter_patches_by_platform(&parsed_collection, platform).map_patches(|p| {
                // Need to do this platforms creation as Rust 1.55 cannot use "from".
                let mut platforms = BTreeSet::new();
                platforms.insert(platform.to_string());
                PatchDictSchema {
                    platforms,
                    ..p.clone()
                }
            })
        })
    };
    let cur_cros_collection = make_collection("chromiumos", &ctx.cros_patches_path())?;
    let cur_android_collection = make_collection("android", &ctx.android_patches_path())?;
    let merged = cur_cros_collection.union(&cur_android_collection)?;
    println!("{}", merged.serialize_patches()?);
    Ok(())
}

struct TransposeOpt {
    cros_checkout_path: PathBuf,
    old_cros_ref: String,
    android_checkout_path: PathBuf,
    old_android_ref: String,
    sync: bool,
    verbose: bool,
    dry_run: bool,
    no_commit: bool,
    cros_reviewers: Vec<String>,
    android_reviewers: Vec<String>,
    wip: bool,
    disable_cq: bool,
    uprev_ebuilds: bool,
}

fn transpose_subcmd(args: TransposeOpt) -> Result<()> {
    let ctx = RepoSetupContext {
        cros_checkout: args.cros_checkout_path,
        android_checkout: args.android_checkout_path,
        sync_before: args.sync,
        wip_mode: args.wip,
        enable_cq: !args.disable_cq,
        uprev_ebuilds: args.uprev_ebuilds,
    };
    ctx.setup()?;
    let cros_patches_path = ctx.cros_patches_path();
    let android_patches_path = ctx.android_patches_path();

    // Get new Patches -------------------------------------------------------
    let patch_parsing::PatchTemporalDiff {
        cur_collection: cur_cros_collection,
        new_patches: new_cros_patches,
        version_updates: cros_version_updates,
    } = patch_parsing::new_patches(
        &cros_patches_path,
        &ctx.old_cros_patch_contents(&args.old_cros_ref)?,
        "chromiumos",
    )
    .context("finding new patches for chromiumos")?;
    let patch_parsing::PatchTemporalDiff {
        cur_collection: cur_android_collection,
        new_patches: new_android_patches,
        version_updates: android_version_updates,
    } = patch_parsing::new_patches(
        &android_patches_path,
        &ctx.old_android_patch_contents(&args.old_android_ref)?,
        "android",
    )
    .context("finding new patches for android")?;

    // Have to ignore patches that are already at the destination, even if
    // the patches are new.
    let new_cros_patches = new_cros_patches.subtract(&cur_android_collection)?;
    let new_android_patches = new_android_patches.subtract(&cur_cros_collection)?;

    // Need to do an extra filtering step for Android, as AOSP doesn't
    // want patches outside of the start/end bounds.
    let android_llvm_version: u64 = {
        let android_llvm_version_str =
            android_utils::get_android_llvm_version(&ctx.android_checkout)?;
        android_llvm_version_str.parse::<u64>().with_context(|| {
            format!(
                "converting llvm version to u64: '{}'",
                android_llvm_version_str
            )
        })?
    };
    let new_android_patches = new_android_patches.filter_patches(|p| {
        match (p.get_from_version(), p.get_until_version()) {
            (Some(start), Some(end)) => start <= android_llvm_version && android_llvm_version < end,
            (Some(start), None) => start <= android_llvm_version,
            (None, Some(end)) => android_llvm_version < end,
            (None, None) => true,
        }
    });

    // Need to filter version updates to only existing patches to the other platform.
    let cros_version_updates =
        filter_version_changes(cros_version_updates, &cur_android_collection);
    let android_version_updates =
        filter_version_changes(android_version_updates, &cur_cros_collection);

    if args.verbose {
        display_patches("New patches from ChromiumOS", &new_cros_patches);
        display_version_updates("Version updates from ChromiumOS", &cros_version_updates);
        display_patches("New patches from Android", &new_android_patches);
        display_version_updates("Version updates from Android", &android_version_updates);
    }

    if args.dry_run {
        println!("--dry-run specified; skipping modifications");
        return Ok(());
    }

    modify_repos(
        &ctx,
        args.no_commit,
        ModifyOpt {
            new_cros_patches,
            cur_cros_collection,
            cros_version_updates,
            cros_reviewers: args.cros_reviewers,
            new_android_patches,
            cur_android_collection,
            android_version_updates,
            android_reviewers: args.android_reviewers,
        },
    )
}

struct ModifyOpt {
    new_cros_patches: PatchCollection,
    cur_cros_collection: PatchCollection,
    cros_version_updates: Vec<(String, Option<VersionRange>)>,
    cros_reviewers: Vec<String>,
    new_android_patches: PatchCollection,
    cur_android_collection: PatchCollection,
    android_version_updates: Vec<(String, Option<VersionRange>)>,
    android_reviewers: Vec<String>,
}

fn modify_repos(ctx: &RepoSetupContext, no_commit: bool, opt: ModifyOpt) -> Result<()> {
    // Cleanup on scope exit.
    scopeguard::defer! {
        ctx.cleanup();
    }
    // Transpose Patches -----------------------------------------------------
    let mut cur_android_collection = opt.cur_android_collection;
    let mut cur_cros_collection = opt.cur_cros_collection;
    // Apply any version ranges and new patches, then write out.
    if !opt.new_cros_patches.is_empty() || !opt.cros_version_updates.is_empty() {
        cur_android_collection =
            cur_android_collection.update_version_ranges(&opt.cros_version_updates);
        opt.new_cros_patches
            .transpose_write(&mut cur_android_collection)?;
    }
    if !opt.new_android_patches.is_empty() || !opt.android_version_updates.is_empty() {
        cur_cros_collection =
            cur_cros_collection.update_version_ranges(&opt.android_version_updates);
        opt.new_android_patches
            .transpose_write(&mut cur_cros_collection)?;
    }

    if no_commit {
        println!("--no-commit specified; not committing or uploading");
        return Ok(());
    }
    // Commit and upload for review ------------------------------------------
    // Note we want to check if the android patches are empty for CrOS, and
    // vice versa. This is a little counterintuitive.
    if !opt.new_android_patches.is_empty() {
        ctx.cros_repo_upload(&opt.cros_reviewers)
            .context("uploading chromiumos changes")?;
    }
    if !opt.new_cros_patches.is_empty() {
        if let Err(e) = android_utils::sort_android_patches(&ctx.android_checkout) {
            eprintln!(
                "Couldn't sort Android patches; continuing. Caused by: {}",
                e
            );
        }
        ctx.android_repo_upload(&opt.android_reviewers)
            .context("uploading android changes")?;
    }
    Ok(())
}

/// Filter version changes that can't apply to a given collection.
fn filter_version_changes<T>(
    version_updates: T,
    other_platform_collection: &PatchCollection,
) -> Vec<(String, Option<VersionRange>)>
where
    T: IntoIterator<Item = (String, Option<VersionRange>)>,
{
    version_updates
        .into_iter()
        .filter(|(rel_patch_path, _)| {
            other_platform_collection
                .patches
                .iter()
                .any(|p| &p.rel_patch_path == rel_patch_path)
        })
        .collect()
}

fn display_patches(prelude: &str, collection: &PatchCollection) {
    println!("{}", prelude);
    if collection.patches.is_empty() {
        println!("  [No Patches]");
        return;
    }
    println!("{}", collection);
}

fn display_version_updates(prelude: &str, version_updates: &[(String, Option<VersionRange>)]) {
    println!("{}", prelude);
    if version_updates.is_empty() {
        println!("  [No Version Changes]");
        return;
    }
    for (rel_patch_path, _) in version_updates {
        println!("*  {}", rel_patch_path);
    }
}

#[derive(Debug, structopt::StructOpt)]
#[structopt(name = "patch_sync", about = "A pipeline for syncing the patch code")]
enum Opt {
    /// Show a combined view of the PATCHES.json file, without making any changes.
    #[allow(dead_code)]
    Show {
        #[structopt(parse(from_os_str))]
        cros_checkout_path: PathBuf,
        #[structopt(parse(from_os_str))]
        android_checkout_path: PathBuf,

        /// Keep a patch's platform field even if it's not merged at that platform.
        #[structopt(long)]
        keep_unmerged: bool,

        /// Run repo sync before transposing.
        #[structopt(short, long)]
        sync: bool,
    },
    /// Transpose patches from two PATCHES.json files
    /// to each other.
    Transpose {
        /// Path to the ChromiumOS source repo checkout.
        #[structopt(long = "cros-checkout", parse(from_os_str))]
        cros_checkout_path: PathBuf,

        /// Emails to send review requests to during ChromiumOS upload.
        /// Comma separated.
        #[structopt(long = "cros-rev")]
        cros_reviewers: Option<String>,

        /// Git ref (e.g. hash) for the ChromiumOS overlay to use as the base.
        #[structopt(long = "overlay-base-ref")]
        old_cros_ref: String,

        /// Path to the Android Open Source Project source repo checkout.
        #[structopt(long = "aosp-checkout", parse(from_os_str))]
        android_checkout_path: PathBuf,

        /// Emails to send review requests to during Android upload.
        /// Comma separated.
        #[structopt(long = "aosp-rev")]
        android_reviewers: Option<String>,

        /// Git ref (e.g. hash) for the llvm_android repo to use as the base.
        #[structopt(long = "aosp-base-ref")]
        old_android_ref: String,

        /// Run repo sync before transposing.
        #[structopt(short, long)]
        sync: bool,

        /// Revbump/uprev ebuilds during transposing.
        #[structopt(long)]
        uprev: bool,

        /// Print information to stdout
        #[structopt(short, long)]
        verbose: bool,

        /// Do not change any files. Useful in combination with `--verbose`
        /// Implies `--no-commit`.
        #[structopt(long)]
        dry_run: bool,

        /// Do not commit or upload any changes made.
        #[structopt(long)]
        no_commit: bool,

        /// Upload and send things for review, but mark as WIP and send no
        /// emails.
        #[structopt(long)]
        wip: bool,

        /// Don't run CQ if set. Only has an effect if uploading.
        #[structopt(long)]
        disable_cq: bool,
    },
}
