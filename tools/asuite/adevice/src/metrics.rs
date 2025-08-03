//! Metrics client

use crate::adevice::Profiler;
use adevice_proto::clientanalytics::LogEvent;
use adevice_proto::clientanalytics::LogRequest;
use adevice_proto::user_log::adevice_log_event::AdeviceActionEvent;
use adevice_proto::user_log::adevice_log_event::AdeviceExitEvent;
use adevice_proto::user_log::adevice_log_event::AdeviceStartEvent;
use adevice_proto::user_log::AdeviceLogEvent;
use adevice_proto::user_log::Duration;

use anyhow::{anyhow, Result};
use std::env;
use std::fs;
use std::process::{Command, Stdio};
use std::time::UNIX_EPOCH;
use tracing::debug;

const ENV_OUT: &str = "OUT";
const ENV_USER: &str = "USER";
const ENV_TARGET: &str = "TARGET_PRODUCT";
const ENV_SURVEY_BANNER: &str = "ADEVICE_SURVEY_BANNER";
const METRICS_UPLOADER: &str = "/google/bin/releases/adevice-dev/metrics_uploader";
const ADEVICE_LOG_SOURCE: i32 = 2265;

pub trait MetricSender {
    fn add_start_event(&mut self, command_line: &str);
    fn add_action_event(&mut self, action: &str, duration: std::time::Duration);
    fn add_action_event_with_files_changed(
        &mut self,
        action: &str,
        duration: std::time::Duration,
        files_changed: std::vec::Vec<String>,
    );
    fn add_profiler_events(&mut self, profiler: &Profiler);
    fn add_exit_event(&mut self, output: &str, exit_code: i32);
    fn display_survey(&mut self);
}

#[derive(Debug, Clone)]
pub struct Metrics {
    events: Vec<LogEvent>,
    user: String,
}

impl MetricSender for Metrics {
    fn add_start_event(&mut self, command_line: &str) {
        let mut start_event = AdeviceStartEvent::default();
        start_event.set_command_line(command_line.to_string());
        start_event.set_target(env::var(ENV_TARGET).unwrap_or("".to_string()));

        let mut event = self.default_log_event();
        event.set_adevice_start_event(start_event);
        self.events.push(LogEvent {
            event_time_ms: Some(UNIX_EPOCH.elapsed().unwrap().as_millis() as i64),
            source_extension: Some(protobuf::Message::write_to_bytes(&event).unwrap()),
            ..Default::default()
        });
    }

    fn add_action_event(&mut self, action: &str, duration: std::time::Duration) {
        self.add_action_event_with_files_changed(action, duration, Vec::new())
    }

    fn add_action_event_with_files_changed(
        &mut self,
        action: &str,
        duration: std::time::Duration,
        files_changed: std::vec::Vec<String>,
    ) {
        let action_event = AdeviceActionEvent {
            action: Some(action.to_string()),
            outcome: ::std::option::Option::None,
            file_changed: files_changed,
            duration: protobuf::MessageField::some(Duration {
                seconds: Some(duration.as_secs() as i64),
                nanos: Some(duration.as_nanos() as i32),
                ..Default::default()
            }),
            ..Default::default()
        };

        let mut event: AdeviceLogEvent = self.default_log_event();
        event.set_adevice_action_event(action_event);
        self.events.push(LogEvent {
            event_time_ms: Some(UNIX_EPOCH.elapsed().unwrap().as_millis() as i64),
            source_extension: Some(protobuf::Message::write_to_bytes(&event).unwrap()),
            ..Default::default()
        });
    }

    fn add_exit_event(&mut self, output: &str, exit_code: i32) {
        let mut exit_event = AdeviceExitEvent::default();
        exit_event.set_logs(output.to_string());
        exit_event.set_exit_code(exit_code);

        let mut event = self.default_log_event();
        event.set_adevice_exit_event(exit_event);
        self.events.push(LogEvent {
            event_time_ms: Some(UNIX_EPOCH.elapsed().unwrap().as_millis() as i64),
            source_extension: Some(protobuf::Message::write_to_bytes(&event).unwrap()),
            ..Default::default()
        });
    }

    fn add_profiler_events(&mut self, profiler: &Profiler) {
        self.add_action_event("device_fingerprint", profiler.device_fingerprint);
        self.add_action_event("host_fingerprint", profiler.host_fingerprint);
        self.add_action_event("ninja_deps_computer", profiler.ninja_deps_computer);
        self.add_action_event("adb_cmds", profiler.adb_cmds);
        self.add_action_event(&profiler.restart_type, profiler.restart);
        self.add_action_event("wait_for_device", profiler.wait_for_device);
        self.add_action_event("wait_for_boot_completed", profiler.wait_for_boot_completed);
        self.add_action_event("first_remount_rw", profiler.first_remount_rw);
        self.add_action_event("total", profiler.total);
        // Compute the time we aren't capturing in a category.
        // We could graph total, but sometimes it is easier to just graph this
        // to see if we are missing significant chunks.
        self.add_action_event(
            "other",
            profiler.total
                - profiler.device_fingerprint
                - profiler.host_fingerprint
                - profiler.ninja_deps_computer
                - profiler.adb_cmds
                - profiler.restart
                - profiler.wait_for_device
                - profiler.wait_for_boot_completed
                - profiler.first_remount_rw,
        );
    }

    fn display_survey(&mut self) {
        let survey = env::var(ENV_SURVEY_BANNER).unwrap_or("".to_string());
        if !survey.is_empty() {
            println!("\n{}", survey);
        }
    }
}

impl Default for Metrics {
    fn default() -> Self {
        Metrics { events: Vec::new(), user: env::var(ENV_USER).unwrap_or("".to_string()) }
    }
}

impl Metrics {
    fn send(&self) -> Result<()> {
        // Only send for internal users, check for metrics_uploader
        if fs::metadata(METRICS_UPLOADER).is_err() {
            return Err(anyhow!("Not internal user: Metrics not sent since uploader not found"));
        }

        // Serialize
        let body = {
            let mut log_request = LogRequest::default();
            log_request.set_log_source(ADEVICE_LOG_SOURCE);

            for e in &*self.events {
                log_request.log_event.push(e.clone());
            }
            let res: Vec<u8> = protobuf::Message::write_to_bytes(&log_request).unwrap();
            res
        };

        let out = env::var(ENV_OUT).unwrap_or("/tmp".to_string());
        let temp_dir = format!("{}/adevice", out);
        let temp_file_path = format!("{}/adevice/adevice.bin", out);
        fs::create_dir_all(temp_dir).expect("Failed to create folder for metrics");
        fs::write(temp_file_path.clone(), body).expect("Failed to write to metrics file");
        Command::new(METRICS_UPLOADER)
            .args([&temp_file_path])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .expect("Failed to send metrics");

        // TODO implement next_request_wait_millis that comes back in response

        Ok(())
    }

    fn default_log_event(&self) -> AdeviceLogEvent {
        let mut event = AdeviceLogEvent::default();
        event.set_user_key(self.user.to_string());
        event
    }
}

impl Drop for Metrics {
    fn drop(&mut self) {
        match self.send() {
            Ok(_) => (),
            Err(e) => debug!("Failed to send metrics: {}", e),
        };
    }
}

#[cfg(test)]
#[allow(unused)]
mod tests {
    use super::*;

    #[test]
    fn test_print_events() {
        let mut metrics = Metrics::default();
        metrics.user = "test_user".to_string();
        metrics.add_start_event("adevice status");
        metrics.add_start_event("adevice track SomeModule");

        assert_eq!(metrics.events.len(), 2);
        metrics.send();
        metrics.events.clear();
        assert_eq!(metrics.events.len(), 0);
    }
}
