use std::io::{self, Write};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use lazy_static::lazy_static;

lazy_static! {
    static ref PROGRESS: Progress = Progress {
        message: Arc::new(Mutex::new("".to_string())),
        is_complete: Arc::new(Mutex::new(false))
    };
}

pub struct Progress {
    message: Arc<Mutex<String>>,
    is_complete: Arc<Mutex<bool>>,
}

impl Progress {
    fn start(&self) {
        let is_complete = self.is_complete.clone();
        let message_ref = self.message.clone();
        thread::spawn(move || {
            let start = Instant::now();
            while !*is_complete.lock().unwrap() {
                let minutes = start.elapsed().as_secs() / 60;
                let seconds = start.elapsed().as_secs() % 60;
                let mut message =
                    format!("     {:01}:{:02} {}", minutes, seconds, message_ref.lock().unwrap());
                if message.len() > 80 {
                    message.truncate(77);
                    message.push('…');
                }
                print!("\x1B[2K"); // clear the line
                print!("\r{} ", message);
                io::stdout().flush().unwrap();
                thread::sleep(Duration::from_millis(100));
            }
            let mut complete = PROGRESS.is_complete.lock().unwrap();
            *complete = false;
        });
    }

    fn stop(&self) {
        let mut is_complete = self.is_complete.lock().unwrap();
        *is_complete = true;
        print!("\x1B[2K"); // clear the line
        print!("\r");
        io::stdout().flush().unwrap();
    }
}

pub fn update(message: &str) {
    let mut new_message = PROGRESS.message.lock().unwrap();
    *new_message = message.to_string();
}

pub fn start(message: &str) {
    update(message);
    PROGRESS.start();
}

pub fn stop() {
    PROGRESS.stop();
}
