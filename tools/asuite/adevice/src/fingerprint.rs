//! Recursively hash the contents of a directory
use anyhow::Result;
use hex::encode;
use rayon::iter::IntoParallelIterator;
use rayon::iter::ParallelIterator;
use ring::digest::{self, SHA256};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::io::{self, Read, Write};
use std::os::unix::fs::FileTypeExt;
use std::os::unix::fs::MetadataExt;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

#[allow(missing_docs)]
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub enum FileType {
    #[default]
    File,
    Symlink,
    Directory,
}

#[derive(Clone, Copy, PartialEq)]
#[allow(dead_code)]
pub enum DiffMode {
    IgnorePermissions,
    UsePermissions,
}

/// Represents a file, directory, or symlink.
/// We need enough information to be able to tell if:
///   1) A regular file changes to a directory or symlink.
///   2) A symlink's target file path changes.
#[derive(Debug, Default, Clone, Serialize, Deserialize, PartialEq)]
pub struct FileMetadata {
    /// Is this a file, dir or symlink?
    pub file_type: FileType,

    /// Path that this symlinks to or ""
    #[serde(skip_serializing_if = "String::is_empty", default)]
    pub symlink: String,

    /// Sha256 of contents for regular files.
    #[serde(skip_serializing_if = "String::is_empty", default)]
    pub digest: String,

    /// Permission bits.
    #[serde(default, skip_serializing_if = "is_default")]
    pub permission_bits: u32,

    // A unique string used only to determine if digest should be recomputed
    // or can be used from cache.
    // The key includes: path, size, mtime, and ctime
    //
    // The cache_key will be different for the  host/device and is not used to compare
    // if the files are different
    #[serde(skip)]
    pub cache_key: String,
}

fn is_default<T: Default + PartialEq>(t: &T) -> bool {
    t == &T::default()
}

impl FileMetadata {
    pub fn from_path(file_path: &Path, cache: &Cache) -> Result<Self> {
        let metadata = fs::symlink_metadata(file_path)?;

        if metadata.is_dir() {
            Ok(FileMetadata::from_dir())
        } else if metadata.is_symlink() {
            FileMetadata::from_symlink(file_path, &metadata)
        } else {
            Ok(FileMetadata::from_file(file_path, &metadata, cache)?)
        }
    }

    pub fn from_dir() -> Self {
        FileMetadata { file_type: FileType::Directory, ..Default::default() }
    }
    pub fn from_symlink(file_path: &Path, metadata: &fs::Metadata) -> Result<Self> {
        let link = fs::read_link(file_path)?;
        let target_path_string =
            link.into_os_string().into_string().expect("Expected valid file name");
        let mut perms = 0;

        // Getting the permissions doesn't work on windows, so don't try and don't compare them.
        if !cfg!(windows) {
            perms = metadata.permissions().mode();
        }
        Ok(FileMetadata {
            file_type: FileType::Symlink,
            symlink: target_path_string,
            permission_bits: perms,
            ..Default::default()
        })
    }
    pub fn from_file(file_path: &Path, metadata: &fs::Metadata, cache: &Cache) -> Result<Self> {
        // Getting the permissions doesn't work on windows, so don't try and don't compare them.
        let mut perms = 0;
        if !cfg!(windows) {
            perms = metadata.permissions().mode();
        }

        let (digest, cache_key) = get_or_compute_digest(file_path, metadata, cache)?;
        Ok(FileMetadata {
            file_type: FileType::File,
            digest,
            cache_key,
            permission_bits: perms,
            ..Default::default()
        })
    }
}

/// A description of the differences on the filesystems between the host
/// and device. Each file that is different will be a key in one of
/// three maps with the value indicating the difference.
#[derive(Debug, Default, PartialEq)]
pub struct Diffs {
    /// Files on host, but not on device
    pub device_needs: HashMap<PathBuf, FileMetadata>,
    /// Files on device, but not host.
    pub device_extra: HashMap<PathBuf, FileMetadata>,
    /// Files that are different between host and device.
    pub device_diffs: HashMap<PathBuf, FileMetadata>,
}

/// Compute the files that need to be added, removed or updated on the device.
/// Each file should land in of the three categories (i.e. updated, not
/// removed and added);
/// TODO(rbraunstein): Fix allow(unused) by breaking out methods not
/// needed by adevice_fingerprint.
#[allow(unused)]
pub fn diff(
    host_files: &HashMap<PathBuf, FileMetadata>,
    device_files: &HashMap<PathBuf, FileMetadata>,
    diff_mode: DiffMode,
) -> Diffs {
    let mut diffs = Diffs {
        device_needs: HashMap::new(),
        device_extra: HashMap::new(),
        device_diffs: HashMap::new(),
    };

    // Insert diffs files that are on the host, but not on the device or
    // file on the host that are different on the device.
    for (file_name, host_metadata) in host_files {
        match device_files.get(file_name) {
            // File on host and device, but the metadata is different.
            Some(device_metadata)
                if is_metadata_diff(device_metadata, host_metadata, diff_mode) =>
            {
                diffs.device_diffs.insert(file_name.clone(), host_metadata.clone())
            }
            // If the device metadata == host metadata there is nothing to do.
            Some(_) => None,
            // Not on the device yet, insert it
            None => diffs.device_needs.insert(file_name.clone(), host_metadata.clone()),
        };
    }

    // Files on the device, but not one the host.
    for (file_name, metadata) in device_files {
        if host_files.get(file_name).is_none() {
            diffs.device_extra.insert(file_name.clone(), metadata.clone());
        }
    }
    diffs
}

/// Return true if left != right ignoring cachekey since that include last_modifed
/// When useing DiffMode::IgnorePermissions, clear the permission bits before doing the comparison
pub fn is_metadata_diff(left: &FileMetadata, right: &FileMetadata, diff_mode: DiffMode) -> bool {
    let mut cleared_left = left.clone();
    let mut cleared_right = right.clone();
    cleared_left.cache_key = "".to_string();
    cleared_right.cache_key = "".to_string();

    if diff_mode == DiffMode::UsePermissions {
        return cleared_left != cleared_right;
    }
    cleared_left.permission_bits = 0;
    cleared_right.permission_bits = 0;
    cleared_left != cleared_right
}

/// Given a `partition_root`, traverse all files under the named |partitions|
/// at the root.  Typically, ["system", "apex"] are partition_names.
/// The keys will be rooted at the `partition root`, ie. if system contains
/// a file named FILE and system is the `partition_root`, the key wil be
/// system/FILE.
/// Cache is used only to speed up computing digests; if the cache_key is the same
/// as an earlier fingerprint; then we reuse it rather than recomputing.
pub fn fingerprint_partitions(
    partition_root: &Path,
    partition_names: &[PathBuf],
) -> Result<HashMap<PathBuf, FileMetadata>> {
    let cache = Cache::read().unwrap_or_default();
    let filenames: Vec<PathBuf> = partition_names
        .iter()
        .flat_map(|p| WalkDir::new(partition_root.join(p)).follow_links(false))
        .map(|result| result.expect("Walking directory").path().to_path_buf())
        .collect();

    // Compute digest for each file.
    let results = filenames
        .into_par_iter()
        // Walking the /data partition quickly leads to sockets, filter those out.
        .filter(|file_path| !is_special_file(file_path))
        .map(|file_path| {
            (
                file_path.strip_prefix(partition_root).unwrap().to_owned(),
                FileMetadata::from_path(&file_path, &cache).unwrap(),
            )
        })
        .collect();
    cache.write(&results)?;
    Ok(results)
}

/// Return true for special files like sockets that would be incorrect
/// to digest and we that we can skip when comparing the device
/// to the build tree.
fn is_special_file(file_path: &Path) -> bool {
    // `symlink_metadata` doesn't follow links. We don't want to follow symlinks here.
    // The stat costs much less than the digest operations we are about to perform.
    let file_metadata = fs::symlink_metadata(file_path).expect("no metadata");
    file_metadata.file_type().is_block_device()
        || file_metadata.file_type().is_char_device()
        || file_metadata.file_type().is_fifo()
        || file_metadata.file_type().is_socket()
}

/// Compute the sha256 and return it as a lowercase hex string.
fn compute_digest(file_path: &Path) -> Result<String> {
    let input = fs::File::open(file_path)?;
    let mut reader = io::BufReader::new(input);
    let mut context = digest::Context::new(&SHA256);
    let mut buffer = [0; 4096];

    loop {
        let num_bytes_read = reader.read(&mut buffer)?;
        if num_bytes_read == 0 {
            break;
        }
        context.update(&buffer[..num_bytes_read]);
    }

    Ok(encode(context.finish().as_ref()))
}

/// Get digest from cache or compute digest and return a cache_key
fn get_or_compute_digest(
    file_path: &Path,
    metadata: &fs::Metadata,
    cache: &Cache,
) -> Result<(String, String)> {
    let cache_key = cache.cache_key(file_path, metadata)?;
    let digest;
    if let Some(cached_digest) = cache.get(&cache_key) {
        digest = cached_digest.to_string();
    } else {
        digest = compute_digest(file_path)?;
    }
    Ok((digest, cache_key))
}

// The cache is intended to be used to skip computing digests - it relies on the assumption that unchanged
// file stats imply unchanged content (and therefore, unchanged digest).
//
// The host stores the cache file in $OUT/ by default.  Acloud/physical devices don't have $OUT so will attempt to store
// the cache in /cache/. Any file modification (add/delete/change) triggers a cache key recomputation for that specific file
// and the cache file will be updated.  The cache should persist across reboots but may be deleted between flashes
#[derive(Default)]
pub struct Cache {
    pub data: HashMap<String, String>,
}
impl Cache {
    pub fn get(&self, key: &str) -> Option<&String> {
        self.data.get(key)
    }

    // Generate cache key from file metadata
    pub fn cache_key(&self, file_path: &Path, metadata: &fs::Metadata) -> Result<String> {
        Ok(format!(
            "{}#{}#{}.{}#{}.{}",
            file_path.display(),
            metadata.len(),
            metadata.mtime(),
            metadata.mtime_nsec(),
            metadata.ctime(),
            metadata.ctime_nsec()
        ))
    }

    /// Reads cache from a file
    pub fn read_from_file(file_path: &Path) -> Result<Self> {
        let mut file = fs::File::open(file_path)?;
        let mut contents = String::new();
        file.read_to_string(&mut contents)?;
        match serde_json::from_str(&contents) {
            Ok(data) => Ok(Cache { data }),
            Err(_error) => Err(_error.into()),
        }
    }

    pub fn read() -> Result<Self> {
        let cache_file_path = Cache::default_cache_path();
        Cache::read_from_file(&cache_file_path)
    }

    /// Writes cache to a file
    pub fn write_to_file(
        self,
        results: &HashMap<PathBuf, FileMetadata>,
        file_path: &Path,
    ) -> Result<()> {
        let mut new_cache: HashMap<String, String> = HashMap::new();
        for meta in results.values() {
            if !meta.cache_key.is_empty() {
                new_cache.insert(meta.cache_key.clone(), meta.digest.clone());
            }
        }
        if new_cache == self.data {
            // cache did not change - skip write.
            return Ok(());
        }
        let cache_str = serde_json::to_string(&new_cache)?;
        let mut file = fs::File::create(file_path)?;
        file.write_all(cache_str.as_bytes())?;
        Ok(())
    }

    pub fn write(self, results: &HashMap<PathBuf, FileMetadata>) -> Result<()> {
        let cache_file_path = Cache::default_cache_path();
        self.write_to_file(results, &cache_file_path)
    }

    pub fn default_cache_path() -> PathBuf {
        // Attempt to use $OUT, then /cache and finally fall back to /tmp
        // /tmp is deleted on reboot on acloud devices
        let mut cache_dir = std::env::var("OUT").unwrap_or_else(|_| "/cache".to_string());
        if !Path::new(&cache_dir).is_dir() {
            cache_dir = "/tmp".to_string();
        }

        PathBuf::from(cache_dir).join("adevice_digest_cache.json")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::fingerprint::DiffMode::UsePermissions;
    use std::collections::BTreeSet;
    use std::path::PathBuf;
    use tempfile::TempDir;

    #[test]
    fn empty_inputs() {
        assert_eq!(diff(&HashMap::new(), &HashMap::new(), UsePermissions), Diffs::default());
    }

    #[test]
    fn same_inputs() {
        let file_entry = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                ..Default::default()
            },
        )]);
        assert_eq!(diff(&file_entry, &file_entry.clone(), UsePermissions), Diffs::default());
    }

    #[test]
    fn same_inputs_with_permissions() {
        let file_entry = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                permission_bits: 0o644,
                ..Default::default()
            },
        )]);
        assert_eq!(diff(&file_entry, &file_entry.clone(), UsePermissions), Diffs::default());
    }

    #[test]
    fn same_inputs_with_different_permissions_are_not_equal() {
        let orig = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                permission_bits: 0o644,
                ..Default::default()
            },
        )]);
        let mut copy = orig.clone();
        copy.entry(PathBuf::from("a/b/foo.so")).and_modify(|v| v.permission_bits = 0);

        // Not equal
        assert_ne!(diff(&orig, &copy, UsePermissions), Diffs::default());
    }

    #[test]
    fn same_inputs_ignoring_permissions() {
        let orig = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                permission_bits: 0o644,
                ..Default::default()
            },
        )]);
        let mut copy = orig.clone();
        copy.entry(PathBuf::from("a/b/foo.so")).and_modify(|v| v.permission_bits = 0);

        // Equal when we ignore the different permission bits.
        assert_eq!(diff(&orig, &copy, DiffMode::IgnorePermissions), Diffs::default());
    }

    #[test]
    fn different_file_type() {
        let host_map_with_filename_as_file = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                ..Default::default()
            },
        )]);

        let device_map_with_filename_as_dir = HashMap::from([(
            PathBuf::from("a/b/foo.so"),
            FileMetadata { file_type: FileType::Directory, ..Default::default() },
        )]);

        let diffs =
            diff(&host_map_with_filename_as_file, &device_map_with_filename_as_dir, UsePermissions);
        assert_eq!(
            diffs.device_diffs.get(&PathBuf::from("a/b/foo.so")).expect("Missing file"),
            // `diff` returns FileMetadata for host, but we really only care that the
            // file name was found.
            &FileMetadata {
                file_type: FileType::File,
                digest: "deadbeef".to_string(),
                ..Default::default()
            },
        );
    }

    #[test]
    fn diff_simple_trees() {
        let host_map = HashMap::from([
            (PathBuf::from("matching_file"), file_metadata("digest_matching_file")),
            (PathBuf::from("path/to/diff_file"), file_metadata("digest_file2")),
            (PathBuf::from("path/to/new_file"), file_metadata("digest_new_file")),
            (PathBuf::from("same_link"), link_metadata("matching_file")),
            (PathBuf::from("diff_link"), link_metadata("targetxx")),
            (PathBuf::from("new_link"), link_metadata("new_target")),
            (PathBuf::from("matching dir"), dir_metadata()),
            (PathBuf::from("new_dir"), dir_metadata()),
        ]);

        let device_map = HashMap::from([
            (PathBuf::from("matching_file"), file_metadata("digest_matching_file")),
            (PathBuf::from("path/to/diff_file"), file_metadata("digest_file2_DIFF")),
            (PathBuf::from("path/to/deleted_file"), file_metadata("digest_deleted_file")),
            (PathBuf::from("same_link"), link_metadata("matching_file")),
            (PathBuf::from("diff_link"), link_metadata("targetxx_DIFF")),
            (PathBuf::from("deleted_link"), link_metadata("new_target")),
            (PathBuf::from("matching dir"), dir_metadata()),
            (PathBuf::from("deleted_dir"), dir_metadata()),
        ]);

        let diffs = diff(&host_map, &device_map, UsePermissions);
        assert_eq!(
            BTreeSet::from_iter(diffs.device_diffs.keys()),
            BTreeSet::from([&PathBuf::from("diff_link"), &PathBuf::from("path/to/diff_file")])
        );
        assert_eq!(
            BTreeSet::from_iter(diffs.device_needs.keys()),
            BTreeSet::from([
                &PathBuf::from("path/to/new_file"),
                &PathBuf::from("new_link"),
                &PathBuf::from("new_dir")
            ])
        );
        assert_eq!(
            BTreeSet::from_iter(diffs.device_extra.keys()),
            BTreeSet::from([
                &PathBuf::from("path/to/deleted_file"),
                &PathBuf::from("deleted_link"),
                &PathBuf::from("deleted_dir")
            ])
        );
    }

    #[test]
    fn compute_digest_empty_file() {
        let tmpdir = TempDir::new().unwrap();
        let file_path = tmpdir.path().join("empty_file");
        fs::write(&file_path, "").unwrap();
        assert_eq!(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".to_string(),
            compute_digest(&file_path).unwrap()
        );
    }

    #[test]
    fn compute_digest_small_file() {
        let tmpdir = TempDir::new().unwrap();
        let file_path = tmpdir.path().join("small_file");
        fs::write(&file_path, "This is a test\nof a small file.\n").unwrap();
        assert_eq!(
            "a519d054afdf2abfbdd90a738d248f606685d6c187e96390bde22e958240449e".to_string(),
            compute_digest(&file_path).unwrap()
        );
    }

    #[test]
    fn get_or_compute_digest_small_file() {
        let tmpdir = TempDir::new().unwrap();
        let file_path = tmpdir.path().join("small_file");
        fs::write(&file_path, "This is a test\nof a small file.\n").unwrap();

        let metadata = fs::metadata(&file_path).expect("file metadata");
        let mut cache = Cache::default();
        let (digest, cache_key) = get_or_compute_digest(&file_path, &metadata, &cache).unwrap();
        assert_eq!(
            "a519d054afdf2abfbdd90a738d248f606685d6c187e96390bde22e958240449e".to_string(),
            digest
        );
        assert_eq!(
            cache_key,
            format!(
                "{}#{}#{}.{}#{}.{}",
                file_path.display(),
                metadata.len(),
                metadata.mtime(),
                metadata.mtime_nsec(),
                metadata.ctime(),
                metadata.ctime_nsec()
            )
        );

        // if cache entry exists; than that is used instead of recomputing
        cache.data.insert(cache_key, "test-saved-cache-digest".to_string());
        let (digest, _) = get_or_compute_digest(&file_path, &metadata, &cache).unwrap();
        assert_eq!("test-saved-cache-digest".to_string(), digest);
    }

    // Generate some files near the buffer size to check for off-by-one errors
    // and compute the digest and store here.
    // We can't check these into testdata and read from testdata unless we serialize
    // all the tests. Some tests to `cd` to create relative symlinks and that affects
    // any tests that want to read from testdata.
    #[test]
    fn verify_edge_case_digests() {
        let tmpdir = TempDir::new().unwrap();
        // We could use a RNG with a seed, but lets just create simple files of bytes.
        let raw_bytes: &[u8; 10] = &[0, 1, 17, 200, 11, 8, 0, 32, 9, 10];
        let mut boring_buff = Vec::new();
        for _ in 1..1000 {
            boring_buff.extend_from_slice(raw_bytes);
        }

        for (num_bytes, digest) in
            &[(4095, "a0e88b2743"), (4096, "b2e324aac3"), (4097, "70fcbe6a8d")]
        {
            let file_path = tmpdir.path().join(num_bytes.to_string());
            fs::write(&file_path, &boring_buff[0..*num_bytes]).unwrap();
            assert!(
                compute_digest(&file_path).unwrap().starts_with(digest),
                "Expected file {:?} to have a digest starting with {:?}",
                file_path,
                digest
            );
        }
    }

    #[test]
    fn fingerprint_file_for_file() {
        let partition_root = TempDir::new().unwrap();
        let file_path = partition_root.path().join("small_file");
        fs::write(&file_path, "This is a test\nof a small file.\n").unwrap();

        // NOTE: files are 0x644 on the host tests and 0x655 on device tests
        // for me and CI so changing the file to always be 655 during the test.
        let mut perms = fs::metadata(&file_path).expect("Getting permissions").permissions();
        perms.set_mode(0o100655);
        assert!(fs::set_permissions(&file_path, perms).is_ok());
        let cache = Cache::default();
        let entry = FileMetadata::from_path(&file_path, &cache).unwrap();
        assert_eq!(
            FileMetadata {
                file_type: FileType::File,
                digest: "a519d054afdf2abfbdd90a738d248f606685d6c187e96390bde22e958240449e"
                    .to_string(),
                permission_bits: 0o100655,
                cache_key: entry.cache_key.clone(),
                ..Default::default()
            },
            entry
        )
    }

    #[test]
    fn fingerprint_file_for_relative_symlink() {
        let partition_root = TempDir::new().unwrap();
        let file_path = partition_root.path().join("small_file");
        fs::write(file_path, "This is a test\nof a small file.\n").unwrap();

        let link = create_symlink(
            &PathBuf::from("small_file"),
            "link_to_small_file",
            partition_root.path(),
        );
        let cache = Cache::default();
        let entry = FileMetadata::from_path(&link, &cache).unwrap();
        assert_eq!(
            FileMetadata {
                file_type: FileType::Symlink,
                symlink: "small_file".to_string(),
                permission_bits: 0o120777,
                ..Default::default()
            },
            entry
        )
    }

    #[test]
    fn fingerprint_file_for_absolute_symlink() {
        let partition_root = TempDir::new().unwrap();
        let link = create_symlink(&PathBuf::from("/tmp"), "link_to_tmp", partition_root.path());
        let cache = Cache::default();
        let entry = FileMetadata::from_path(&link, &cache).unwrap();
        assert_eq!(
            FileMetadata {
                file_type: FileType::Symlink,
                symlink: "/tmp".to_string(),
                permission_bits: 0o120777,
                ..Default::default()
            },
            entry
        )
    }

    #[test]
    fn fingerprint_file_for_directory() {
        let partition_root = TempDir::new().unwrap();
        let newdir_path = partition_root.path().join("some_dir");
        fs::create_dir(&newdir_path).expect("Should have create 'some_dir' in temp dir");
        let cache = Cache::default();
        let entry = FileMetadata::from_path(&newdir_path, &cache).unwrap();
        assert_eq!(FileMetadata { file_type: FileType::Directory, ..Default::default() }, entry)
    }

    #[test]
    fn fingerprint_file_on_bad_path_reports_err() {
        let cache = Cache::default();
        if FileMetadata::from_path(Path::new("testdata/not_exist"), &cache).is_ok() {
            panic!("Should have failed on invalid path")
        }
    }

    /// /tmp/.tmpxO0pRC/system
    /// % tree
    /// .
    /// ├── cycle1 -> cycle2
    /// ├── cycle2 -> cycle1
    /// ├── danglers
    /// │   ├── d1 -> nowhere
    /// │   └── d2 -> /not/existing
    /// ├── dir1
    /// │   ├── dir2
    /// │   │   ├── nested
    /// │   │   └── nested2
    /// │   ├── dir4
    /// │   └── f1.txt
    /// ├── dir3
    /// │   ├── to_tmp -> /tmp
    /// │   └── to_tmp2 -> /system/cycle1
    /// ├── file1.so
    /// ├── file2.so
    /// ├── link1 -> file1.so
    /// └── link2 -> link1
    #[test]

    fn fingerprint_simple_partition() {
        let tmp_root = TempDir::new().unwrap();
        // TODO(rbraunstein): Change make_partition to look more like `expected` variable below.
        // i.e. use file_type rather than pass files, dirs, and symlinks in different arrays.
        // Or use a struct with named fields as the args.
        make_partition(
            tmp_root.path(),
            "system",
            &[
                ("file1.so", "some text"),
                ("file2.so", "more text"),
                ("dir1/f1.txt", ""),
                ("dir1/dir2/nested", "some more text"),
                ("dir1/dir2/nested2", "some more text"),
            ],
            // Empty directories/
            &["dir3", "dir1/dir4", "danglers"],
            // Symlinks:
            //   Linkname, target.
            &[
                ("link1", "file1.so"),
                ("link2", "link1"),
                ("cycle1", "cycle2"),
                ("cycle2", "cycle1"),
                ("dir3/to_tmp", "/tmp"),
                ("dir3/to_tmp2", "/system/cycle1"),
                ("danglers/d1", "nowhere"),
                ("danglers/d2", "/not/existing"),
            ],
        );
        let result = fingerprint_partitions(tmp_root.path(), &[PathBuf::from("system")]).unwrap();
        println!("RESULTS\n");
        for x in &result {
            println!("{:?}", x);
        }
        let expected = &[
            ("system/file1.so", FileType::File, "b94f"),
            ("system/file2.so", FileType::File, "c0dc"),
            ("system/dir1/f1.txt", FileType::File, "e3b0c"),
            ("system/dir1/dir2/nested", FileType::File, "bde27b"),
            ("system/dir1/dir2/nested2", FileType::File, "bde27b"),
            ("system/dir3", FileType::Directory, ""),
            ("system/danglers", FileType::Directory, ""),
            ("system/dir1", FileType::Directory, ""),
            ("system/dir1/dir2", FileType::Directory, ""),
            ("system/dir1/dir4", FileType::Directory, ""),
            ("system/link1", FileType::Symlink, "file1.so"),
            ("system/link2", FileType::Symlink, "link1"),
            ("system/cycle1", FileType::Symlink, "cycle2"),
            ("system/cycle2", FileType::Symlink, "cycle1"),
            ("system/dir3/to_tmp", FileType::Symlink, "/tmp"),
            ("system/dir3/to_tmp2", FileType::Symlink, "/system/cycle1"),
            ("system/danglers/d1", FileType::Symlink, "nowhere"),
            ("system/danglers/d2", FileType::Symlink, "/not/existing"),
            ("system", FileType::Directory, ""),
        ];

        assert_eq!(
            expected.len(),
            result.len(),
            "expected: {}, result {}",
            expected.len(),
            result.len()
        );

        for (file_name, file_type, data) in expected {
            match file_type {
                FileType::File => assert!(
                    matching_file_fingerprint(file_name, data, &result),
                    "mismatch on {:?} {:?}",
                    file_name,
                    data
                ),
                FileType::Directory => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|d| d.file_type == FileType::Directory)),
                FileType::Symlink => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|s| s.file_type == FileType::Symlink && &s.symlink == data)),
            };
        }
    }

    #[test]
    fn fingerprint_multiple_partitions() {
        let tmp_root = TempDir::new().unwrap();
        // Use same file name, with and without same contents in two different partitions.
        make_partition(
            tmp_root.path(),
            "system",
            &[("file1.so", "some text"), ("file2", "system part")],
            // Empty directories/
            &[],
            // Symlinks
            &[],
        );
        make_partition(
            tmp_root.path(),
            "data",
            &[("file1.so", "some text"), ("file2", "data part")],
            // Empty directories/
            &[],
            // Symlinks
            &[],
        );

        let result = fingerprint_partitions(
            tmp_root.path(),
            &[PathBuf::from("system"), PathBuf::from("data")],
        )
        .unwrap();
        println!("RESULTS\n");
        for x in &result {
            println!("{:?}", x);
        }
        let expected = &[
            ("system/file1.so", FileType::File, "b94f"),
            ("data/file1.so", FileType::File, "b94f"),
            ("system/file2", FileType::File, "ae7c6c"),
            ("data/file2", FileType::File, "4ae46d"),
            ("data", FileType::Directory, ""),
            ("system", FileType::Directory, ""),
        ];

        assert_eq!(
            expected.len(),
            result.len(),
            "expected: {}, result {}",
            expected.len(),
            result.len()
        );

        for (file_name, file_type, data) in expected {
            match file_type {
                FileType::File => assert!(
                    matching_file_fingerprint(file_name, data, &result),
                    "mismatch on {:?} {:?}",
                    file_name,
                    data
                ),
                FileType::Directory => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|d| d.file_type == FileType::Directory)),
                _ => (),
            };
        }
    }

    #[test]
    fn fingerprint_partition_with_interesting_file_names() {
        let tmp_dir = TempDir::new().unwrap();
        let tmp_root = tmp_dir.path().to_owned();
        println!("DEBUG: {tmp_root:?}");
        make_partition(
            &tmp_root,
            "funky",
            &[("안녕하세요", "hello\n")],
            // Empty directories/
            &[
                // TODO(rbraunstein): This invalid file name (embedded newlind and Nil) breaks tests.
                // Need to fix the code to remove `unwraps` and propagate errors.
                // "d\ni\0r3"
                ],
            // symlinks
            // linkname, target
            &[("שלום", "안녕하세요")],
        );
        let result = fingerprint_partitions(&tmp_root, &[PathBuf::from("funky")]).unwrap();
        println!("RESULTS\n");
        for x in &result {
            println!("{:?}", x);
        }
        let expected = &[
            ("funky/안녕하세요", FileType::File, "5891b"),
            // ("funky/d\ni\0r3", FileType::Directory, ""),
            ("funky/שלום", FileType::Symlink, "안녕하세요"),
            ("funky", FileType::Directory, ""),
        ];

        assert_eq!(
            expected.len(),
            result.len(),
            "expected: {}, result {}",
            expected.len(),
            result.len()
        );

        for (file_name, file_type, data) in expected {
            match file_type {
                FileType::File => assert!(
                    matching_file_fingerprint(file_name, data, &result),
                    "mismatch on {:?} {:?}",
                    file_name,
                    data
                ),
                FileType::Directory => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|d| d.file_type == FileType::Directory)),
                FileType::Symlink => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|s| s.file_type == FileType::Symlink && &s.symlink == data)),
            };
        }
    }

    #[test]
    fn fingerprint_partition_cache_mismatch_test() {
        // test to assure that when a file is modified; the cache doesn't return the same digest
        let tmp_root = TempDir::new().unwrap();
        make_partition(
            tmp_root.path(),
            "system",
            &[("file1.so", "some text"), ("file2.so", "more text")],
            // Empty directories/
            &[],
            // Symlinks
            &[("link1.so", "file1.so")],
        );
        let result = fingerprint_partitions(tmp_root.path(), &[PathBuf::from("system")]).unwrap();
        let expected = &[
            ("system/file1.so", FileType::File, "b94f"),
            ("system/file2.so", FileType::File, "c0dc"),
            ("system/link1.so", FileType::Symlink, "file1.so"),
        ];
        for (file_name, file_type, data) in expected {
            match file_type {
                FileType::File => assert!(
                    matching_file_fingerprint(file_name, data, &result),
                    "mismatch on {:?} {:?}",
                    file_name,
                    data
                ),
                FileType::Directory => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|d| d.file_type == FileType::Directory)),
                FileType::Symlink => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|s| s.file_type == FileType::Symlink && &s.symlink == data)),
            }
        }

        // modify file
        let file_path = tmp_root.path().join("system/file1.so");
        fs::write(file_path, "modified file.").unwrap();
        let result2 = fingerprint_partitions(tmp_root.path(), &[PathBuf::from("system")]).unwrap();
        let expected2 = &[
            ("system/file1.so", FileType::File, "047c5"),
            ("system/file2.so", FileType::File, "c0dc"),
        ];
        for (file_name, file_type, data) in expected2 {
            match file_type {
                FileType::File => assert!(
                    matching_file_fingerprint(file_name, data, &result2),
                    "mismatch on {:?} {:?}",
                    file_name,
                    data
                ),
                FileType::Directory => assert!(result2
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|d| d.file_type == FileType::Directory)),
                FileType::Symlink => assert!(result
                    .get(&PathBuf::from(file_name))
                    .is_some_and(|s| s.file_type == FileType::Symlink && &s.symlink == data)),
            }
        }
    }

    #[test]
    fn test_write_and_read_cache_file() {
        let root = TempDir::new().unwrap();
        let file_path = root.path().join("cache.json");
        let results = HashMap::from([
            (
                PathBuf::from("path1"),
                FileMetadata {
                    cache_key: "key1".to_string(),
                    digest: "value1".to_string(),
                    ..Default::default()
                },
            ),
            (
                PathBuf::from("path2"),
                FileMetadata {
                    cache_key: "key2".to_string(),
                    digest: "value2".to_string(),
                    ..Default::default()
                },
            ),
        ]);

        let cache = Cache::default();
        let write_result = cache.write_to_file(&results, &file_path);
        assert!(write_result.is_ok());

        let cache = Cache::read_from_file(&file_path).unwrap();
        assert_eq!(cache.get("key1"), Some(&"value1".to_string()));
        assert_eq!(cache.get("key2"), Some(&"value2".to_string()));
    }

    #[test]
    fn test_read_cache_file_no_file() {
        let bad_path = Path::new("/tmp/fake/non/existing/path");
        let cache = Cache::read_from_file(bad_path).unwrap_or_default();
        assert!(cache.data.is_empty());
    }

    #[test]
    fn test_read_cache_file_invalid_file() {
        let root = TempDir::new().unwrap();
        let file_path = root.path().join("cache.json");
        fs::write(file_path.clone(), "invalid cache data").unwrap();

        let cache = Cache::read_from_file(&file_path).unwrap_or_default();
        assert!(cache.data.is_empty());
    }

    // Ensure the FileMetadata for the given file matches the prefix of the digest.
    // We don't require whole digests as that just muddys up the test code and
    // other methods tests full digests.
    fn matching_file_fingerprint(
        file_name: &str,
        digest_prefix: &str,
        fingerprints: &HashMap<PathBuf, FileMetadata>,
    ) -> bool {
        match fingerprints.get(&PathBuf::from(file_name)) {
            None => false,
            Some(metadata) => {
                metadata.file_type == FileType::File
                    && metadata.symlink.is_empty()
                    && metadata.digest.starts_with(digest_prefix)
            }
        }
    }

    // Create a temporary folder and create files, directories and symlinks under it.
    fn make_partition(
        tmp_root: &Path,
        partition_name: &str,
        files: &[(&str, &str)],
        directories: &[&str],
        symlinks: &[(&str, &str)],
    ) {
        let partition_dir = tmp_root.join(partition_name);
        fs::create_dir(&partition_dir).expect("should have created directory partition_dir");
        // First create all empty directories.
        for dir in directories {
            fs::create_dir_all(partition_dir.join(dir))
                .unwrap_or_else(|_| panic!("Should have created {dir} in {tmp_root:?}"));
        }
        for (file_name, file_content) in files {
            // Create parent dirs, in case they are needed.
            fs::create_dir_all(partition_dir.join(file_name).parent().unwrap()).unwrap();
            fs::write(partition_dir.join(file_name), file_content).expect("Trouble writing file");
        }
        for (symlink_name, target) in symlinks {
            fs::create_dir_all(partition_dir.join(symlink_name).parent().unwrap()).unwrap();
            create_symlink(&PathBuf::from(target), symlink_name, &partition_dir);
        }
    }

    // Create a symlink in `directory` named `link_name` that points to `target`.
    // Returns the absolute path to the created symlink.
    fn create_symlink(target: &Path, link_name: &str, directory: &Path) -> PathBuf {
        fs::soft_link(target, directory.join(link_name))
            .unwrap_or_else(|e| println!("Could not symlink to {:?} {:?}", directory, e));

        directory.join(Path::new(link_name))
    }

    fn file_metadata(digest: &str) -> FileMetadata {
        FileMetadata { file_type: FileType::File, digest: digest.to_string(), ..Default::default() }
    }

    fn link_metadata(target: &str) -> FileMetadata {
        FileMetadata {
            file_type: FileType::Symlink,
            digest: target.to_string(),
            ..Default::default()
        }
    }

    fn dir_metadata() -> FileMetadata {
        FileMetadata { file_type: FileType::Directory, ..Default::default() }
    }

    // TODO(rbraunstein): a bunch more tests:
}
