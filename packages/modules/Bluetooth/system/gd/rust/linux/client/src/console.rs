//! Convenient functions to print messages to console.

#[macro_export]
macro_rules! console_blue {
    ( $text:expr ) => {
        format!("\x1b[1;34m{}\x1b[0m", $text).as_str()
    };
}

#[macro_export]
macro_rules! console_yellow {
    ( $text:expr ) => {
        format!("\x1b[1;33m{}\x1b[0m", $text).as_str()
    };
}

#[macro_export]
macro_rules! console_red {
    ( $text:expr ) => {
        format!("\x1b[1;31m{}\x1b[0m", $text).as_str()
    };
}

#[macro_export]
macro_rules! print_info {
    ( $($arg:tt)* ) => {
        {
            print!("{}: ", console_yellow!("btclient:info"));
            println!($($arg)*);
        }
    };
}

#[macro_export]
macro_rules! print_error {
    ( $($arg:tt)* ) => {
        {
            print!("{}: ", console_red!("btclient:error"));
            println!($($arg)*);
        }
    };
}
