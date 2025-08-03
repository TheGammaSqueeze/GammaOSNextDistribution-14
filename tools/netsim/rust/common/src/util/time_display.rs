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

//! # Time Display class

use std::time::{SystemTime, UNIX_EPOCH};

use chrono::{DateTime, Datelike, Local, NaiveDateTime, TimeZone as _, Timelike, Utc};

/// A enum for different time zones. This is used for displaying
/// times in a desired time zone format.
pub enum TimeZone {
    /// Coordinated Universal Time zone
    Utc,
    /// Local Time zone
    Local,
}

/// A simple class that contains information required to display time
pub struct TimeDisplay {
    /// seconds since std::time::UNIX_EPOCH
    secs: i64,
    /// nano sub seconds since std::time::UNIX_EPOCH
    nsecs: u32,
}

impl TimeDisplay {
    /// Creates a new TimeDisplay with given secs and nsecs
    ///
    /// # Arguments
    ///
    /// * `secs` - seconds since std::time::UNIX_EPOCH
    /// * `nsecs` - nano sub seconds since std::time::UNIX_EPOCH
    pub fn new(secs: i64, nsecs: u32) -> TimeDisplay {
        TimeDisplay { secs, nsecs }
    }

    /// Displays date & time in UTC with a format YYYY-MM-DD-HH:MM:SS
    ///
    /// # Returns
    ///
    /// `String` display of utc time.
    pub fn utc_display(&self) -> String {
        if let Some(datetime) = NaiveDateTime::from_timestamp_opt(self.secs, self.nsecs) {
            let current_datetime = DateTime::<Utc>::from_utc(datetime, Utc);
            return format!(
                "{}-{:02}-{:02}-{:02}-{:02}-{:02}",
                current_datetime.year(),
                current_datetime.month(),
                current_datetime.day(),
                current_datetime.hour(),
                current_datetime.minute(),
                current_datetime.second()
            );
        }
        "INVALID-TIMESTAMP".to_string()
    }

    /// Displays date & time in local with a format YYYY-MM-DD-HH:MM:SS
    ///
    /// # Returns
    ///
    /// `String` display of local time
    pub fn local_time_display(&self) -> String {
        if let Some(current_datetime) = Local.timestamp_opt(self.secs, self.nsecs).single() {
            return format!(
                "{}-{:02}-{:02}-{:02}-{:02}-{:02}",
                current_datetime.year(),
                current_datetime.month(),
                current_datetime.day(),
                current_datetime.hour(),
                current_datetime.minute(),
                current_datetime.second()
            );
        }
        "INVALID-TIMESTAMP".to_string()
    }

    /// Displays time in UTC without date with a format HH:MM:SS
    ///
    /// # Returns
    ///
    /// `Ok(String)` if the display was successful, `Error` otherwise.
    pub fn utc_display_hms(&self) -> String {
        if let Some(datetime) = NaiveDateTime::from_timestamp_opt(self.secs, self.nsecs) {
            let current_datetime = DateTime::<Utc>::from_utc(datetime, Utc);
            return format!(
                "{:02}:{:02}:{:02}",
                current_datetime.hour(),
                current_datetime.minute(),
                current_datetime.second()
            );
        }
        "INVALID".to_string()
    }

    /// Displays time in UTC for logs
    fn utc_display_log(&self) -> String {
        if let Some(datetime) = NaiveDateTime::from_timestamp_opt(self.secs, self.nsecs) {
            let current_datetime = DateTime::<Utc>::from_utc(datetime, Utc);
            return format!(
                "{:02}-{:02} {:02}:{:02}:{:02}.{:.3}",
                current_datetime.month(),
                current_datetime.day(),
                current_datetime.hour(),
                current_datetime.minute(),
                current_datetime.second(),
                current_datetime.timestamp_subsec_nanos().to_string(),
            );
        }
        "INVALID-TIMESTAMP".to_string()
    }
}

// Get TimeDisplay of current_time
fn get_current_time() -> TimeDisplay {
    let since_epoch = SystemTime::now().duration_since(UNIX_EPOCH).expect("Time went backwards");
    TimeDisplay::new(since_epoch.as_secs() as i64, since_epoch.subsec_nanos())
}

/// Return the timestamp of the current time for logs
pub fn log_current_time() -> String {
    get_current_time().utc_display_log()
}

/// Return the timestamp of the current time for files
pub fn file_current_time(timezone: TimeZone) -> String {
    match timezone {
        TimeZone::Utc => get_current_time().utc_display(),
        TimeZone::Local => get_current_time().local_time_display(),
    }
}

#[cfg(test)]
mod tests {

    use chrono::Local;

    use super::TimeDisplay;

    #[test]
    fn test_utc_display_ok() {
        let epoch_time = TimeDisplay::new(0, 0);
        let utc_epoch = epoch_time.utc_display();
        assert_eq!(utc_epoch, "1970-01-01-00-00-00");
        let twok_time = TimeDisplay::new(946684900, 0);
        let utc_twok = twok_time.utc_display();
        assert_eq!(utc_twok, "2000-01-01-00-01-40");
    }

    #[test]
    fn test_utc_display_err() {
        let max_seconds = TimeDisplay::new(i64::MAX, 0);
        assert_eq!("INVALID-TIMESTAMP", max_seconds.utc_display());
        let max_nanos = TimeDisplay::new(0, 2_000_000_000);
        assert_eq!("INVALID-TIMESTAMP", max_nanos.utc_display());
    }

    #[test]
    fn test_utc_display_hms() {
        let epoch_time = TimeDisplay::new(0, 0);
        let utc_epoch = epoch_time.utc_display_hms();
        assert_eq!(utc_epoch, "00:00:00");
        let twok_time = TimeDisplay::new(946684900, 0);
        let utc_twok = twok_time.utc_display_hms();
        assert_eq!(utc_twok, "00:01:40");
    }

    #[test]
    fn test_utc_display_log() {
        let epoch_time = TimeDisplay::new(0, 0);
        let utc_epoch = epoch_time.utc_display_log();
        assert_eq!(utc_epoch, "01-01 00:00:00.0");
        let twok_time = TimeDisplay::new(946684900, 200);
        let utc_twok = twok_time.utc_display_log();
        assert_eq!(utc_twok, "01-01 00:01:40.200");
    }

    #[test]
    fn test_local_time_display() {
        // TODO: Need to have expected value based on the testing environment's
        // local time zone. Currently the local_time_display is tested when local time
        // zone is UTC.
        if Local::now().offset().to_string() == "+00:00" {
            let epoch_time = TimeDisplay::new(0, 0);
            let local_time_epoch = epoch_time.local_time_display();
            assert_eq!(local_time_epoch, "1970-01-01-00-00-00");
            let twok_time = TimeDisplay::new(946684900, 0);
            let local_time_twok = twok_time.local_time_display();
            assert_eq!(local_time_twok, "2000-01-01-00-01-40");
        }
        let max_seconds = TimeDisplay::new(i64::MAX, 0);
        assert_eq!("INVALID-TIMESTAMP", max_seconds.local_time_display());
        let max_nanos = TimeDisplay::new(0, 2_000_000_000);
        assert_eq!("INVALID-TIMESTAMP", max_nanos.local_time_display());
    }
}
