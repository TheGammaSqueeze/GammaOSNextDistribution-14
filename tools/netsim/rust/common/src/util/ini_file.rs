//
//  Copyright 2023 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

//! # IniFile class

use std::collections::HashMap;
use std::error::Error;
use std::fs::File;
use std::io::prelude::*;
use std::io::BufReader;
use std::path::PathBuf;

/// A simple class to process init file. Based on
/// external/qemu/android/android-emu-base/android/base/files/IniFile.h
pub struct IniFile {
    /// The data stored in the ini file.
    data: HashMap<String, String>,
    /// The path to the ini file.
    filepath: PathBuf,
}

impl IniFile {
    /// Creates a new IniFile with the given filepath.
    ///
    /// # Arguments
    ///
    /// * `filepath` - The path to the ini file.
    pub fn new(filepath: PathBuf) -> IniFile {
        IniFile { data: HashMap::new(), filepath }
    }

    /// Reads data into IniFile from the backing file, overwriting any
    /// existing data.
    ///
    /// # Returns
    ///
    /// `Ok` if the write was successful, `Error` otherwise.
    pub fn read(&mut self) -> Result<(), Box<dyn Error>> {
        self.data.clear();

        let mut f = File::open(self.filepath.clone())?;
        let reader = BufReader::new(&mut f);

        for line in reader.lines() {
            let line = line?;
            let parts = line.split_once('=');
            if parts.is_none() {
                continue;
            }
            let key = parts.unwrap().0.trim();
            let value = parts.unwrap().1.trim();
            self.data.insert(key.to_owned(), value.to_owned());
        }

        Ok(())
    }

    /// Writes the current IniFile to the backing file.
    ///
    /// # Returns
    ///
    /// `Ok` if the write was successful, `Error` otherwise.
    pub fn write(&self) -> Result<(), Box<dyn Error>> {
        let mut f = File::create(self.filepath.clone())?;
        for (key, value) in &self.data {
            writeln!(&mut f, "{}={}", key, value)?;
        }
        f.flush()?;
        Ok(())
    }

    /// Checks if a certain key exists in the file.
    ///
    /// # Arguments
    ///
    /// * `key` - The key to check.
    ///
    /// # Returns
    ///
    /// `true` if the key exists, `false` otherwise.
    pub fn contains_key(&self, key: &str) -> bool {
        self.data.contains_key(key)
    }

    /// Gets value.
    ///
    /// # Arguments
    ///
    /// * `key` - The key to get the value for.
    ///
    /// # Returns
    ///
    /// An `Option` containing the value if it exists, `None` otherwise.
    pub fn get(&self, key: &str) -> Option<&str> {
        self.data.get(key).map(|v| v.as_str())
    }

    /// Inserts a key-value pair.
    ///
    /// # Arguments
    ///
    /// * `key` - The key to set the value for.
    /// * `value` - The value to set.
    pub fn insert(&mut self, key: &str, value: &str) {
        self.data.insert(key.to_owned(), value.to_owned());
    }
}

#[cfg(test)]
mod tests {
    use rand::{distributions::Alphanumeric, Rng};
    use std::env;
    use std::fs::File;
    use std::io::{Read, Write};
    use std::path::PathBuf;

    use super::IniFile;

    fn get_temp_ini_filepath(prefix: &str) -> PathBuf {
        env::temp_dir().join(format!(
            "{prefix}_{}.ini",
            rand::thread_rng()
                .sample_iter(&Alphanumeric)
                .take(8)
                .map(char::from)
                .collect::<String>()
        ))
    }

    // NOTE: ctest run a test at least twice tests in parallel, so we need to use unique temp file
    // to prevent tests from accessing the same file simultaneously.
    #[test]
    fn test_read() {
        for test_case in ["port=123", "port= 123", "port =123", " port = 123 "] {
            let filepath = get_temp_ini_filepath("test_read");

            {
                let mut tmpfile = match File::create(&filepath) {
                    Ok(f) => f,
                    Err(_) => return,
                };
                writeln!(tmpfile, "{test_case}").unwrap();
            }

            let mut inifile = IniFile::new(filepath.clone());
            inifile.read().unwrap();

            assert!(!inifile.contains_key("unknown-key"));
            assert!(inifile.contains_key("port"), "Fail in test case: {test_case}");
            assert_eq!(inifile.get("port").unwrap(), "123");
            assert_eq!(inifile.get("unknown-key"), None);

            // Note that there is no guarantee that the file is immediately deleted (e.g.,
            // depending on platform, other open file descriptors may prevent immediate removal).
            // https://doc.rust-lang.org/std/fs/fn.remove_file.html.
            std::fs::remove_file(filepath).unwrap();
        }
    }

    #[test]
    fn test_read_no_newline() {
        let filepath = get_temp_ini_filepath("test_read_no_newline");

        {
            let mut tmpfile = match File::create(&filepath) {
                Ok(f) => f,
                Err(_) => return,
            };
            write!(tmpfile, "port=123").unwrap();
        }

        let mut inifile = IniFile::new(filepath.clone());
        inifile.read().unwrap();

        assert!(!inifile.contains_key("unknown-key"));
        assert!(inifile.contains_key("port"));
        assert_eq!(inifile.get("port").unwrap(), "123");
        assert_eq!(inifile.get("unknown-key"), None);

        std::fs::remove_file(filepath).unwrap();
    }

    #[test]
    fn test_read_no_file() {
        let filepath = get_temp_ini_filepath("test_read_no_file");
        let mut inifile = IniFile::new(filepath.clone());
        assert!(inifile.read().is_err());
    }

    #[test]
    fn test_read_multiple_lines() {
        let filepath = get_temp_ini_filepath("test_read_multiple_lines");

        {
            let mut tmpfile = match File::create(&filepath) {
                Ok(f) => f,
                Err(_) => return,
            };
            write!(tmpfile, "port=123\nport2=456\n").unwrap();
        }

        let mut inifile = IniFile::new(filepath.clone());
        inifile.read().unwrap();

        assert!(!inifile.contains_key("unknown-key"));
        assert!(inifile.contains_key("port"));
        assert!(inifile.contains_key("port2"));
        assert_eq!(inifile.get("port").unwrap(), "123");
        assert_eq!(inifile.get("port2").unwrap(), "456");
        assert_eq!(inifile.get("unknown-key"), None);

        std::fs::remove_file(filepath).unwrap();
    }

    #[test]
    fn test_insert_and_contains_key() {
        let filepath = get_temp_ini_filepath("test_insert_and_contains_key");

        let mut inifile = IniFile::new(filepath);

        assert!(!inifile.contains_key("port"));
        assert!(!inifile.contains_key("unknown-key"));

        inifile.insert("port", "123");

        assert!(inifile.contains_key("port"));
        assert!(!inifile.contains_key("unknown-key"));
        assert_eq!(inifile.get("port").unwrap(), "123");
        assert_eq!(inifile.get("unknown-key"), None);

        // Update the value of an existing key.
        inifile.insert("port", "234");

        assert!(inifile.contains_key("port"));
        assert!(!inifile.contains_key("unknown-key"));
        assert_eq!(inifile.get("port").unwrap(), "234");
        assert_eq!(inifile.get("unknown-key"), None);
    }

    #[test]
    fn test_write() {
        let filepath = get_temp_ini_filepath("test_write");

        let mut inifile = IniFile::new(filepath.clone());

        assert!(!inifile.contains_key("port"));
        assert!(!inifile.contains_key("unknown-key"));

        inifile.insert("port", "123");

        assert!(inifile.contains_key("port"));
        assert!(!inifile.contains_key("unknown-key"));
        assert_eq!(inifile.get("port").unwrap(), "123");
        assert_eq!(inifile.get("unknown-key"), None);

        if inifile.write().is_err() {
            return;
        }
        let mut file = File::open(&filepath).unwrap();
        let mut contents = String::new();
        file.read_to_string(&mut contents).unwrap();

        assert_eq!(contents, "port=123\n");

        std::fs::remove_file(filepath).unwrap();
    }

    #[test]
    fn test_write_and_read() {
        let filepath = get_temp_ini_filepath("test_write_and_read");

        {
            let mut inifile = IniFile::new(filepath.clone());

            assert!(!inifile.contains_key("port"));
            assert!(!inifile.contains_key("port2"));
            assert!(!inifile.contains_key("unknown-key"));

            inifile.insert("port", "123");
            inifile.insert("port2", "456");

            assert!(inifile.contains_key("port"));
            assert!(!inifile.contains_key("unknown-key"));
            assert_eq!(inifile.get("port").unwrap(), "123");
            assert_eq!(inifile.get("unknown-key"), None);

            if inifile.write().is_err() {
                return;
            }
        }

        let mut inifile = IniFile::new(filepath.clone());
        inifile.read().unwrap();

        assert!(!inifile.contains_key("unknown-key"));
        assert!(inifile.contains_key("port"));
        assert!(inifile.contains_key("port2"));
        assert_eq!(inifile.get("port").unwrap(), "123");
        assert_eq!(inifile.get("port2").unwrap(), "456");
        assert_eq!(inifile.get("unknown-key"), None);

        std::fs::remove_file(filepath).unwrap();
    }

    #[test]
    fn test_overwrite() {
        let filepath = get_temp_ini_filepath("test_overwrite");
        {
            let mut tmpfile = match File::create(&filepath) {
                Ok(f) => f,
                Err(_) => return,
            };
            write!(tmpfile, "port=123\nport2=456\n").unwrap();
        }

        let mut inifile = IniFile::new(filepath.clone());
        inifile.insert("port3", "789");

        inifile.write().unwrap();
        let mut file = File::open(&filepath).unwrap();
        let mut contents = String::new();
        file.read_to_string(&mut contents).unwrap();

        assert_eq!(contents, "port3=789\n");
    }
}
