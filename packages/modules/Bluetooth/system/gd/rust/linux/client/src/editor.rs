//! Tools to work with rustyline readline() library.

use futures::Future;

use rustyline::completion::Completer;
use rustyline::error::ReadlineError;
use rustyline::highlight::Highlighter;
use rustyline::hint::Hinter;
use rustyline::validate::Validator;
use rustyline::{CompletionType, Config, Editor};
use rustyline_derive::Helper;

use std::collections::HashSet;
use std::pin::Pin;
use std::sync::{Arc, Mutex};
use std::task::{Context, Poll};

use crate::console_blue;
use crate::ClientContext;

#[derive(Helper)]
struct BtHelper {
    // Command rules must follow below format:
    // cmd arg1 arg2 arg3 ...
    // where each argument could have multiple options separated by a single '|'
    //
    // It is not required to put an argument in angle brackets.
    //
    // "address" in options is a keyword, which will be matched by any of the founded
    // and bonded devices.
    //
    // Example:
    // list <found|bonded> <address>
    // This will match
    //     list found any-cached-address
    // and
    //     list bond any-cached-address
    command_rules: Vec<String>,
    client_context: Arc<Mutex<ClientContext>>,
}

#[derive(Hash, Eq, PartialEq)]
struct CommandCandidate {
    suggest_word: String,
    matched_len: usize,
}

impl Completer for BtHelper {
    type Candidate = String;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        _ctx: &rustyline::Context<'_>,
    ) -> Result<(usize, Vec<String>), ReadlineError> {
        let slice = &line[..pos];
        let candidates = self.get_candidates(slice.to_string().clone());
        let mut completions =
            candidates.iter().map(|c| c.suggest_word.clone() + " ").collect::<Vec<String>>();

        completions.sort();

        // |start| points to the starting position of the current token
        let start = match slice.rfind(' ') {
            Some(x) => x + 1,
            None => 0,
        };

        Ok((start, completions))
    }
}

impl Hinter for BtHelper {
    type Hint = String;
}

impl Highlighter for BtHelper {}

impl Validator for BtHelper {}

impl BtHelper {
    fn get_candidates(&self, cmd: String) -> HashSet<CommandCandidate> {
        let mut result = HashSet::<CommandCandidate>::new();

        for rule in self.command_rules.iter() {
            let n_splits = cmd.split(" ").count();
            // The tokens should have empty strings removed from them, except the last one.
            let tokens = cmd
                .split(" ")
                .enumerate()
                .filter_map(|(i, token)| (i == n_splits - 1 || token != "").then(|| token));

            let n_cmd = tokens.clone().count();
            for (i, (rule_token, cmd_token)) in rule.split(" ").zip(tokens).enumerate() {
                let mut candidates = Vec::<String>::new();
                let mut match_some = false;

                for opt in rule_token.replace("<", "").replace(">", "").split("|") {
                    if opt.eq("address") {
                        let devices = self.client_context.lock().unwrap().get_devices();
                        candidates.extend(devices);
                    } else {
                        candidates.push(opt.to_string());
                    }
                }

                if cmd_token.len() == 0 {
                    candidates.iter().for_each(|s| {
                        result.insert(CommandCandidate { suggest_word: s.clone(), matched_len: 0 });
                    });
                    break;
                }

                for opt in candidates {
                    if opt.starts_with(cmd_token) {
                        match_some = true;
                        if i == n_cmd - 1 {
                            // we add candidates only if it's the last word
                            result.insert(CommandCandidate {
                                suggest_word: opt.clone(),
                                matched_len: cmd_token.len(),
                            });
                        }
                    }
                }

                if !match_some {
                    break;
                }
            }
        }
        result
    }
}

/// A future that does async readline().
///
/// async readline() is implemented by spawning a thread for the blocking readline(). While this
/// readline() thread is blocked, it yields back to executor and will wake the executor up when the
/// blocked thread has proceeded and got input from readline().
pub struct AsyncReadline {
    rl: Arc<Mutex<Editor<BtHelper>>>,
    result: Arc<Mutex<Option<rustyline::Result<String>>>>,
}

impl Future for AsyncReadline {
    type Output = rustyline::Result<String>;

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<rustyline::Result<String>> {
        let option = self.result.lock().unwrap().take();
        if let Some(res) = option {
            return Poll::Ready(res);
        }

        let waker = cx.waker().clone();
        let result_clone = self.result.clone();
        let rl = self.rl.clone();
        std::thread::spawn(move || {
            let readline = rl.lock().unwrap().readline(console_blue!("bluetooth> "));
            *result_clone.lock().unwrap() = Some(readline);
            waker.wake();
        });

        Poll::Pending
    }
}

/// Wrapper of rustyline editor that supports async readline().
pub struct AsyncEditor {
    rl: Arc<Mutex<Editor<BtHelper>>>,
}

impl AsyncEditor {
    /// Creates new async rustyline editor.
    ///
    /// * `commands` - List of commands for autocomplete.
    pub(crate) fn new(
        command_rules: Vec<String>,
        client_context: Arc<Mutex<ClientContext>>,
    ) -> rustyline::Result<AsyncEditor> {
        let builder = Config::builder()
            .auto_add_history(true)
            .history_ignore_dups(true)
            .completion_type(CompletionType::List);
        let config = builder.build();
        let mut rl = rustyline::Editor::with_config(config)?;
        let helper = BtHelper { command_rules, client_context };
        rl.set_helper(Some(helper));
        Ok(AsyncEditor { rl: Arc::new(Mutex::new(rl)) })
    }

    /// Does async readline().
    ///
    /// Returns a future that will do the readline() when await-ed. This does not block the thread
    /// but rather yields to the executor while waiting for a command to be entered.
    pub fn readline(&self) -> AsyncReadline {
        AsyncReadline { rl: self.rl.clone(), result: Arc::new(Mutex::new(None)) }
    }
}
