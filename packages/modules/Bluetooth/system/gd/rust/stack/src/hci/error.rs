//! Defines the Result type and HCI errors

use futures::channel::oneshot;
use std::fmt::Debug;
use thiserror::Error;
use tokio::sync::mpsc::error::SendError;
use tokio::sync::oneshot::error::RecvError;

/// Result type
pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

/// HCI errors
#[derive(Error, Debug)]
pub enum HciError<T: Debug + 'static> {
    /// Error when sending on a bounded channel
    #[error("Error sending: {0}")]
    BoundedSendError(#[from] SendError<T>),
    /// Error when sending on a oneshot channel
    #[error("Error sending: {0}")]
    OneshotSendError(#[from] oneshot::Canceled),
    /// Error receiving from a channel
    #[error("Error receiving: {0}")]
    ChannelRecvError(#[from] RecvError),
}
