// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

use std::collections::{BTreeMap, BTreeSet};
use std::fs::{copy, read_to_string, File};
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

/// JSON serde struct.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct PatchDictSchema {
    pub metadata: Option<BTreeMap<String, serde_json::Value>>,
    #[serde(default, skip_serializing_if = "BTreeSet::is_empty")]
    pub platforms: BTreeSet<String>,
    pub rel_patch_path: String,
    pub version_range: Option<VersionRange>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct VersionRange {
    pub from: Option<u64>,
    pub until: Option<u64>,
}

impl PatchDictSchema {
    /// Return the first version this patch applies to.
    pub fn get_from_version(&self) -> Option<u64> {
        self.version_range.and_then(|x| x.from)
    }

    /// Return the version after the last version this patch
    /// applies to.
    pub fn get_until_version(&self) -> Option<u64> {
        self.version_range.and_then(|x| x.until)
    }
}

/// Struct to keep track of patches and their relative paths.
#[derive(Debug, Clone)]
pub struct PatchCollection {
    pub patches: Vec<PatchDictSchema>,
    pub workdir: PathBuf,
    pub indent_len: usize,
}

impl PatchCollection {
    /// Create a `PatchCollection` from a PATCHES.
    pub fn parse_from_file(json_file: &Path) -> Result<Self> {
        // We can't just use a file reader because we
        // need to know what the original indent_len is.
        let contents = read_to_string(json_file)?;
        Self::parse_from_str(
            json_file
                .parent()
                .ok_or_else(|| anyhow!("failed to get json_file parent"))?
                .to_path_buf(),
            &contents,
        )
    }

    /// Create a `PatchCollection` from a string literal and a workdir.
    pub fn parse_from_str(workdir: PathBuf, contents: &str) -> Result<Self> {
        Ok(Self {
            patches: serde_json::from_str(contents).context("parsing from str")?,
            workdir,
            indent_len: predict_indent(contents),
        })
    }

    /// Copy this collection with patches filtered by given criterion.
    pub fn filter_patches(&self, f: impl FnMut(&PatchDictSchema) -> bool) -> Self {
        Self {
            patches: self.patches.iter().cloned().filter(f).collect(),
            workdir: self.workdir.clone(),
            indent_len: self.indent_len,
        }
    }

    /// Map over the patches.
    pub fn map_patches(&self, f: impl FnMut(&PatchDictSchema) -> PatchDictSchema) -> Self {
        Self {
            patches: self.patches.iter().map(f).collect(),
            workdir: self.workdir.clone(),
            indent_len: self.indent_len,
        }
    }

    /// Return true if the collection is tracking any patches.
    pub fn is_empty(&self) -> bool {
        self.patches.is_empty()
    }

    /// Compute the set-set subtraction, returning a new `PatchCollection` which
    /// keeps the minuend's workdir and indent level.
    pub fn subtract(&self, subtrahend: &Self) -> Result<Self> {
        let mut new_patches = Vec::new();
        // This is O(n^2) when it could be much faster, but n is always going to be less
        // than 1k and speed is not important here.
        for our_patch in &self.patches {
            let found_in_sub = subtrahend.patches.iter().any(|sub_patch| {
                let hash1 = subtrahend
                    .hash_from_rel_patch(sub_patch)
                    .expect("getting hash from subtrahend patch");
                let hash2 = self
                    .hash_from_rel_patch(our_patch)
                    .expect("getting hash from our patch");
                hash1 == hash2
            });
            if !found_in_sub {
                new_patches.push(our_patch.clone());
            }
        }
        Ok(Self {
            patches: new_patches,
            workdir: self.workdir.clone(),
            indent_len: self.indent_len,
        })
    }

    pub fn union(&self, other: &Self) -> Result<Self> {
        self.union_helper(
            other,
            |p| self.hash_from_rel_patch(p),
            |p| other.hash_from_rel_patch(p),
        )
    }

    /// Vec of every PatchDictSchema with differing
    /// version ranges but the same rel_patch_paths.
    fn version_range_diffs(&self, other: &Self) -> Vec<(String, Option<VersionRange>)> {
        let other_map: BTreeMap<_, _> = other
            .patches
            .iter()
            .map(|p| (p.rel_patch_path.clone(), p))
            .collect();
        self.patches
            .iter()
            .filter_map(|ours| match other_map.get(&ours.rel_patch_path) {
                Some(theirs) => {
                    if ours.get_from_version() != theirs.get_from_version()
                        || ours.get_until_version() != theirs.get_until_version()
                    {
                        Some((ours.rel_patch_path.clone(), ours.version_range))
                    } else {
                        None
                    }
                }
                _ => None,
            })
            .collect()
    }

    /// Given a vector of tuples with (rel_patch_path, Option<VersionRange>), replace
    /// all version ranges in this collection with a matching one in the new_versions parameter.
    pub fn update_version_ranges(&self, new_versions: &[(String, Option<VersionRange>)]) -> Self {
        // new_versions should be really tiny (len() <= 2 for the most part), so
        // the overhead of O(1) lookups is not worth it.
        let get_updated_version = |rel_patch_path: &str| -> Option<Option<VersionRange>> {
            // The first Option indicates whether we are updating it at all.
            // The second Option indicates we can update it with None.
            new_versions
                .iter()
                .find(|i| i.0 == rel_patch_path)
                .map(|x| x.1)
        };
        let cloned_patches = self
            .patches
            .iter()
            .map(|p| match get_updated_version(&p.rel_patch_path) {
                Some(version_range) => PatchDictSchema {
                    version_range,
                    ..p.clone()
                },
                _ => p.clone(),
            })
            .collect();
        Self {
            workdir: self.workdir.clone(),
            indent_len: self.indent_len,
            patches: cloned_patches,
        }
    }

    fn union_helper(
        &self,
        other: &Self,
        our_hash_f: impl Fn(&PatchDictSchema) -> Result<String>,
        their_hash_f: impl Fn(&PatchDictSchema) -> Result<String>,
    ) -> Result<Self> {
        // 1. For all our patches:
        //   a. If there exists a matching patch hash from `other`:
        //     i. Create a new patch with merged platform info,
        //     ii. add the new patch to our new collection.
        //     iii. Mark the other patch as "merged"
        //   b. Otherwise, copy our patch to the new collection
        // 2. For all unmerged patches from the `other`
        //   a. Copy their patch into the new collection
        let mut combined_patches = Vec::new();
        let mut other_merged = vec![false; other.patches.len()];

        // 1.
        for p in &self.patches {
            let our_hash = our_hash_f(p)?;
            let mut found = false;
            // a.
            for (idx, merged) in other_merged.iter_mut().enumerate() {
                if !*merged {
                    let other_p = &other.patches[idx];
                    let their_hash = their_hash_f(other_p)?;
                    if our_hash == their_hash {
                        // i.
                        let new_platforms =
                            p.platforms.union(&other_p.platforms).cloned().collect();
                        // ii.
                        combined_patches.push(PatchDictSchema {
                            rel_patch_path: p.rel_patch_path.clone(),
                            platforms: new_platforms,
                            metadata: p.metadata.clone(),
                            version_range: p.version_range,
                        });
                        // iii.
                        *merged = true;
                        found = true;
                        break;
                    }
                }
            }
            // b.
            if !found {
                combined_patches.push(p.clone());
            }
        }
        // 2.
        // Add any remaining, other-only patches.
        for (idx, merged) in other_merged.iter().enumerate() {
            if !*merged {
                combined_patches.push(other.patches[idx].clone());
            }
        }

        Ok(Self {
            workdir: self.workdir.clone(),
            indent_len: self.indent_len,
            patches: combined_patches,
        })
    }

    /// Copy all patches from this collection into another existing collection, and write that
    /// to the existing collection's file.
    pub fn transpose_write(&self, existing_collection: &mut Self) -> Result<()> {
        for p in &self.patches {
            let original_file_path = self.workdir.join(&p.rel_patch_path);
            let copy_file_path = existing_collection.workdir.join(&p.rel_patch_path);
            copy_create_parents(&original_file_path, &copy_file_path)?;
            existing_collection.patches.push(p.clone());
        }
        existing_collection.write_patches_json("PATCHES.json")
    }

    /// Write out the patch collection contents to a PATCHES.json file.
    fn write_patches_json(&self, filename: &str) -> Result<()> {
        let write_path = self.workdir.join(filename);
        let mut new_patches_file = File::create(&write_path)
            .with_context(|| format!("writing to {}", write_path.display()))?;
        new_patches_file.write_all(self.serialize_patches()?.as_bytes())?;
        Ok(())
    }

    pub fn serialize_patches(&self) -> Result<String> {
        let indent_str = " ".repeat(self.indent_len);
        let mut serialization_buffer = Vec::<u8>::new();
        // Four spaces to indent json serialization.
        let mut serializer = serde_json::Serializer::with_formatter(
            &mut serialization_buffer,
            serde_json::ser::PrettyFormatter::with_indent(indent_str.as_bytes()),
        );
        self.patches
            .serialize(&mut serializer)
            .context("serializing patches to JSON")?;
        // Append a newline at the end if not present. This is necessary to get
        // past some pre-upload hooks.
        if serialization_buffer.last() != Some(&b'\n') {
            serialization_buffer.push(b'\n');
        }
        Ok(std::str::from_utf8(&serialization_buffer)?.to_string())
    }

    /// Return whether a given patch actually exists on the file system.
    pub fn patch_exists(&self, patch: &PatchDictSchema) -> bool {
        self.workdir.join(&patch.rel_patch_path).exists()
    }

    fn hash_from_rel_patch(&self, patch: &PatchDictSchema) -> Result<String> {
        hash_from_patch_path(&self.workdir.join(&patch.rel_patch_path))
    }
}

impl std::fmt::Display for PatchCollection {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        for (i, p) in self.patches.iter().enumerate() {
            let title = p
                .metadata
                .as_ref()
                .and_then(|x| x.get("title"))
                .and_then(serde_json::Value::as_str)
                .unwrap_or("[No Title]");
            let path = self.workdir.join(&p.rel_patch_path);
            writeln!(f, "* {}", title)?;
            if i == self.patches.len() - 1 {
                write!(f, "  {}", path.display())?;
            } else {
                writeln!(f, "  {}", path.display())?;
            }
        }
        Ok(())
    }
}

/// Represents information which changed between now and an old version of a PATCHES.json file.
pub struct PatchTemporalDiff {
    pub cur_collection: PatchCollection,
    pub new_patches: PatchCollection,
    // Store version_updates as a vec, not a map, as it's likely to be very small (<=2),
    // and the overhead of using a O(1) look up structure isn't worth it.
    pub version_updates: Vec<(String, Option<VersionRange>)>,
}

/// Generate a PatchCollection incorporating only the diff between current patches and old patch
/// contents.
pub fn new_patches(
    patches_path: &Path,
    old_patch_contents: &str,
    platform: &str,
) -> Result<PatchTemporalDiff> {
    // Set up the current patch collection.
    let cur_collection = PatchCollection::parse_from_file(patches_path)
        .with_context(|| format!("parsing {} PATCHES.json", platform))?;
    validate_patches(&cur_collection, platform)?;

    // Set up the old patch collection.
    let old_collection = PatchCollection::parse_from_str(
        patches_path.parent().unwrap().to_path_buf(),
        old_patch_contents,
    )?;
    let old_collection = old_collection.filter_patches(|p| old_collection.patch_exists(p));

    // Set up the differential values
    let version_updates = cur_collection.version_range_diffs(&old_collection);
    let new_patches: PatchCollection = cur_collection.subtract(&old_collection)?;
    let new_patches = new_patches.map_patches(|p| {
        let mut platforms = BTreeSet::new();
        platforms.extend(["android".to_string(), "chromiumos".to_string()]);
        PatchDictSchema {
            platforms: platforms.union(&p.platforms).cloned().collect(),
            ..p.to_owned()
        }
    });
    Ok(PatchTemporalDiff {
        cur_collection,
        new_patches,
        version_updates,
    })
}

/// Create a new collection with only the patches that apply to the
/// given platform.
///
/// If there's no platform listed, the patch should still apply if the patch file exists.
pub fn filter_patches_by_platform(collection: &PatchCollection, platform: &str) -> PatchCollection {
    collection.filter_patches(|p| {
        p.platforms.contains(platform) || (p.platforms.is_empty() && collection.patch_exists(p))
    })
}

/// Verify the patches all exist and apply to the given platform.
///
/// If all good, return Unit. Otherwise, return an Err.
pub fn validate_patches(collection: &PatchCollection, platform: &str) -> Result<()> {
    for p in &collection.patches {
        if !collection.patch_exists(p) {
            bail!("Patch {} does not exist", p.rel_patch_path);
        }
        if !p.platforms.is_empty() && !p.platforms.contains(platform) {
            bail!(
                "Patch {} did not apply to platform {}",
                p.rel_patch_path,
                platform
            );
        }
    }
    Ok(())
}

/// Get the hash from the patch file contents.
///
/// Not every patch file actually contains its own hash,
/// we must compute the hash ourselves when it's not found.
fn hash_from_patch(patch_contents: impl Read) -> Result<String> {
    let mut reader = BufReader::new(patch_contents);
    let mut buf = String::new();
    reader.read_line(&mut buf)?;
    let mut first_line_iter = buf.trim().split(' ').fuse();
    let (fst_word, snd_word) = (first_line_iter.next(), first_line_iter.next());
    if let (Some("commit" | "From"), Some(hash_str)) = (fst_word, snd_word) {
        // If the first line starts with either "commit" or "From", the following
        // text is almost certainly a commit hash.
        Ok(hash_str.to_string())
    } else {
        // This is an annoying case where the patch isn't actually a commit.
        // So we'll hash the entire file, and hope that's sufficient.
        let mut hasher = Sha256::new();
        hasher.update(&buf); // Have to hash the first line.
        reader.read_to_string(&mut buf)?;
        hasher.update(buf); // Hash the rest of the file.
        let sha = hasher.finalize();
        Ok(format!("{:x}", &sha))
    }
}

fn hash_from_patch_path(patch: &Path) -> Result<String> {
    let f = File::open(patch).with_context(|| format!("opening patch file {}", patch.display()))?;
    hash_from_patch(f)
}

/// Copy a file from one path to another, and create any parent
/// directories along the way.
fn copy_create_parents(from: &Path, to: &Path) -> Result<()> {
    let to_parent = to
        .parent()
        .with_context(|| format!("getting parent of {}", to.display()))?;
    if !to_parent.exists() {
        std::fs::create_dir_all(to_parent)?;
    }

    copy(&from, &to)
        .with_context(|| format!("copying file from {} to {}", &from.display(), &to.display()))?;
    Ok(())
}

/// Given a json string, predict and return the maximum indentation unit the json string uses.
pub fn predict_indent(json: &str) -> usize {
    let indents = json
        .split('\n')
        .map(|line| line.len() - line.trim_start_matches(' ').len())
        .collect::<Vec<usize>>();
    if indents.iter().all(|x| x % 4 == 0) {
        return 4;
    }
    if indents.iter().all(|x| x % 2 == 0) {
        return 2;
    }
    if indents.iter().all(|x| *x == 0) {
        return 0;
    }
    1
}

#[cfg(test)]
mod test {

    use super::*;

    /// Test we can extract the hash from patch files.
    #[test]
    fn test_hash_from_patch() {
        // Example git patch from Gerrit
        let desired_hash = "004be4037e1e9c6092323c5c9268acb3ecf9176c";
        let test_file_contents = "commit 004be4037e1e9c6092323c5c9268acb3ecf9176c\n\
            Author: An Author <some_email>\n\
            Date:   Thu Aug 6 12:34:16 2020 -0700";
        assert_eq!(
            &hash_from_patch(test_file_contents.as_bytes()).unwrap(),
            desired_hash
        );

        // Example git patch from upstream
        let desired_hash = "6f85225ef3791357f9b1aa097b575b0a2b0dff48";
        let test_file_contents = "From 6f85225ef3791357f9b1aa097b575b0a2b0dff48\n\
            Mon Sep 17 00:00:00 2001\n\
            From: Another Author <another_email>\n\
            Date: Wed, 18 Aug 2021 15:03:03 -0700";
        assert_eq!(
            &hash_from_patch(test_file_contents.as_bytes()).unwrap(),
            desired_hash
        );
    }

    #[test]
    fn test_keep_indent2() {
        let example_json = "\
[
  {
    \"rel_patch_path\": \"some_patch.\",
    \"metadata\": null,
    \"platforms\": []
  }
]
";
        let collection1 = PatchCollection::parse_from_str(PathBuf::new(), example_json)
            .expect("could not parse example_json");
        assert_eq!(collection1.indent_len, 2);
        let collection2 = PatchCollection::parse_from_str(
            PathBuf::new(),
            &collection1
                .serialize_patches()
                .expect("expected to serialize patches"),
        )
        .expect("could not parse from serialization");
        assert_eq!(collection2.indent_len, 2);
        let mut collection3 = collection1;
        collection3.indent_len = 4;
        let collection4 = PatchCollection::parse_from_str(
            PathBuf::new(),
            &collection3
                .serialize_patches()
                .expect("expected to serialize patches"),
        )
        .expect("could not parse from serialization");
        assert_eq!(collection4.indent_len, 4)
    }

    #[test]
    fn test_union() {
        let patch1 = PatchDictSchema {
            rel_patch_path: "a".into(),
            metadata: None,
            platforms: BTreeSet::from(["x".into()]),
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(1),
            }),
        };
        let patch2 = PatchDictSchema {
            rel_patch_path: "b".into(),
            platforms: BTreeSet::from(["x".into(), "y".into()]),
            ..patch1.clone()
        };
        let patch3 = PatchDictSchema {
            platforms: BTreeSet::from(["z".into(), "x".into()]),
            ..patch1.clone()
        };
        let collection1 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1, patch2],
            indent_len: 0,
        };
        let collection2 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch3],
            indent_len: 0,
        };
        let union = collection1
            .union_helper(
                &collection2,
                |p| Ok(p.rel_patch_path.to_string()),
                |p| Ok(p.rel_patch_path.to_string()),
            )
            .expect("could not create union");
        assert_eq!(union.patches.len(), 2);
        assert_eq!(
            union.patches[0].platforms.iter().collect::<Vec<&String>>(),
            vec!["x", "z"]
        );
        assert_eq!(
            union.patches[1].platforms.iter().collect::<Vec<&String>>(),
            vec!["x", "y"]
        );
    }

    #[test]
    fn test_union_empties() {
        let patch1 = PatchDictSchema {
            rel_patch_path: "a".into(),
            metadata: None,
            platforms: Default::default(),
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(1),
            }),
        };
        let collection1 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1.clone()],
            indent_len: 4,
        };
        let collection2 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1],
            indent_len: 4,
        };
        let union = collection1
            .union_helper(
                &collection2,
                |p| Ok(p.rel_patch_path.to_string()),
                |p| Ok(p.rel_patch_path.to_string()),
            )
            .expect("could not create union");
        assert_eq!(union.patches.len(), 1);
        assert_eq!(union.patches[0].platforms.len(), 0);
    }

    #[test]
    fn test_version_differentials() {
        let fixture = version_range_fixture();
        let diff = fixture[0].version_range_diffs(&fixture[1]);
        assert_eq!(diff.len(), 1);
        assert_eq!(
            &diff,
            &[(
                "a".to_string(),
                Some(VersionRange {
                    from: Some(0),
                    until: Some(1)
                })
            )]
        );
        let diff = fixture[1].version_range_diffs(&fixture[2]);
        assert_eq!(diff.len(), 0);
    }

    #[test]
    fn test_version_updates() {
        let fixture = version_range_fixture();
        let collection = fixture[0].update_version_ranges(&[("a".into(), None)]);
        assert_eq!(collection.patches[0].version_range, None);
        assert_eq!(collection.patches[1], fixture[1].patches[1]);
        let new_version_range = Some(VersionRange {
            from: Some(42),
            until: Some(43),
        });
        let collection = fixture[0].update_version_ranges(&[("a".into(), new_version_range)]);
        assert_eq!(collection.patches[0].version_range, new_version_range);
        assert_eq!(collection.patches[1], fixture[1].patches[1]);
    }

    fn version_range_fixture() -> Vec<PatchCollection> {
        let patch1 = PatchDictSchema {
            rel_patch_path: "a".into(),
            metadata: None,
            platforms: Default::default(),
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(1),
            }),
        };
        let patch1_updated = PatchDictSchema {
            version_range: Some(VersionRange {
                from: Some(0),
                until: Some(3),
            }),
            ..patch1.clone()
        };
        let patch2 = PatchDictSchema {
            rel_patch_path: "b".into(),
            ..patch1.clone()
        };
        let collection1 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1, patch2.clone()],
            indent_len: 0,
        };
        let collection2 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch1_updated, patch2.clone()],
            indent_len: 0,
        };
        let collection3 = PatchCollection {
            workdir: PathBuf::new(),
            patches: vec![patch2],
            indent_len: 0,
        };
        vec![collection1, collection2, collection3]
    }
}
