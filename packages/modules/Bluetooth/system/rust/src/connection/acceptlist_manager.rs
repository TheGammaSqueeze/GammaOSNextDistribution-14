//! This module takes the set of attempts from the AttemptManager, determines
//! the target state of the LE manager, and drives it to this target state

use std::collections::HashSet;

use log::info;

use crate::core::address::AddressWithType;

use super::{
    attempt_manager::{ConnectionAttempt, ConnectionMode},
    le_manager::LeAclManager,
};

/// This struct represents the target state of the LeManager based on the
/// set of all active connection attempts
pub struct TargetState {
    /// These addresses should go to the LE background connect list
    pub background_list: HashSet<AddressWithType>,
    /// These addresses should go to the direct list (we are not connected to any of them)
    pub direct_list: HashSet<AddressWithType>,
}

/// Takes a list of connection attempts, and determines the target state of the LE ACL manager
pub fn determine_target_state(attempts: &[ConnectionAttempt]) -> TargetState {
    let background_list = attempts
        .iter()
        .filter(|attempt| attempt.mode == ConnectionMode::Background)
        .map(|attempt| attempt.remote_address)
        .collect();

    let direct_list = attempts
        .iter()
        .filter(|attempt| attempt.mode == ConnectionMode::Direct)
        .map(|attempt| attempt.remote_address)
        .collect();

    TargetState { background_list, direct_list }
}

/// This struct monitors the state of the LE connect list,
/// and drives it to the target state.
#[derive(Debug)]
pub struct LeAcceptlistManager {
    /// The connect list in the ACL manager
    direct_list: HashSet<AddressWithType>,
    /// The background connect list in the ACL manager
    background_list: HashSet<AddressWithType>,
    /// An interface into the LE ACL manager (le_impl.h)
    le_manager: Box<dyn LeAclManager>,
}

impl LeAcceptlistManager {
    /// Constructor
    pub fn new(le_manager: impl LeAclManager + 'static) -> Self {
        Self {
            direct_list: HashSet::new(),
            background_list: HashSet::new(),
            le_manager: Box::new(le_manager),
        }
    }

    /// The state of the LE connect list (as per le_impl.h) updates on a completed connection
    pub fn on_connect_complete(&mut self, address: AddressWithType) {
        if address == AddressWithType::EMPTY {
            return;
        }
        // le_impl pulls the device out of the direct connect list (but not the background list) on connection (regardless of status)
        self.direct_list.remove(&address);
    }

    /// Drive the state of the connect list to the target state
    pub fn drive_to_state(&mut self, target: TargetState) {
        // First, pull out anything in the ACL manager that we don't need
        // recall that cancel_connect() removes addresses from *both* lists (!)
        for address in self.direct_list.difference(&target.direct_list) {
            info!("Cancelling connection attempt to {address:?}");
            self.le_manager.remove_from_all_lists(*address);
            self.background_list.remove(address);
        }
        self.direct_list = self.direct_list.intersection(&target.direct_list).copied().collect();

        for address in self.background_list.difference(&target.background_list) {
            info!("Cancelling connection attempt to {address:?}");
            self.le_manager.remove_from_all_lists(*address);
            self.direct_list.remove(address);
        }
        self.background_list =
            self.background_list.intersection(&target.background_list).copied().collect();

        // now everything extra has been removed, we can put things back in
        for address in target.direct_list.difference(&self.direct_list) {
            info!("Starting direct connection to {address:?}");
            self.le_manager.add_to_direct_list(*address);
        }
        for address in target.background_list.difference(&self.background_list) {
            info!("Starting background connection to {address:?}");
            self.le_manager.add_to_background_list(*address);
        }

        // we should now be in a consistent state!
        self.direct_list = target.direct_list;
        self.background_list = target.background_list;
    }
}

#[cfg(test)]
mod test {
    use crate::{
        connection::{
            le_manager::ErrorCode, mocks::mock_le_manager::MockActiveLeAclManager,
            ConnectionManagerClient,
        },
        core::address::AddressType,
    };

    use super::*;

    const CLIENT: ConnectionManagerClient = ConnectionManagerClient::GattClient(1);

    const ADDRESS_1: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 6], address_type: AddressType::Public };
    const ADDRESS_2: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 6], address_type: AddressType::Random };
    const ADDRESS_3: AddressWithType =
        AddressWithType { address: [1, 2, 3, 4, 5, 7], address_type: AddressType::Random };

    #[test]
    fn test_determine_target_state() {
        let target = determine_target_state(&[
            ConnectionAttempt {
                client: CLIENT,
                mode: ConnectionMode::Background,
                remote_address: ADDRESS_1,
            },
            ConnectionAttempt {
                client: CLIENT,
                mode: ConnectionMode::Background,
                remote_address: ADDRESS_1,
            },
            ConnectionAttempt {
                client: CLIENT,
                mode: ConnectionMode::Background,
                remote_address: ADDRESS_2,
            },
            ConnectionAttempt {
                client: CLIENT,
                mode: ConnectionMode::Direct,
                remote_address: ADDRESS_2,
            },
            ConnectionAttempt {
                client: CLIENT,
                mode: ConnectionMode::Direct,
                remote_address: ADDRESS_3,
            },
        ]);

        assert_eq!(target.background_list.len(), 2);
        assert!(target.background_list.contains(&ADDRESS_1));
        assert!(target.background_list.contains(&ADDRESS_2));
        assert_eq!(target.direct_list.len(), 2);
        assert!(target.direct_list.contains(&ADDRESS_2));
        assert!(target.direct_list.contains(&ADDRESS_3));
    }

    #[test]
    fn test_add_to_direct_list() {
        // arrange
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());

        // act: request a device to be present in the direct list
        manager.drive_to_state(TargetState {
            background_list: [].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // assert: that the device has been added
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
        assert_eq!(mock_le_manager.current_acceptlist().len(), 1);
        assert!(mock_le_manager.current_acceptlist().contains(&ADDRESS_1));
    }

    #[test]
    fn test_add_to_background_list() {
        // arrange
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());

        // act: request a device to be present in the direct list
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // assert: that the device has been added
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Background));
        assert_eq!(mock_le_manager.current_acceptlist().len(), 1);
        assert!(mock_le_manager.current_acceptlist().contains(&ADDRESS_1));
    }

    #[test]
    fn test_background_connection_upgrade_to_direct() {
        // arrange: a pending background connection
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // act: initiate a direct connection to the same device
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // assert: we are now doing a direct connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
    }

    #[test]
    fn test_direct_connection_cancel_while_background() {
        // arrange: a pending background connection
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // act: initiate a direct connection to the same device, then remove it
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // assert: we have returned to a background connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Background));
    }

    #[test]
    fn test_direct_connection_cancel_then_resume_while_background() {
        // arrange: a pending background connection
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // act: initiate a direct connection to the same device, cancel it, then resume
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // assert: we have returned to a direct connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
    }

    #[test]
    fn test_remove_background_connection_then_add() {
        // arrange
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());

        // act: add then remove a background connection
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });
        manager.drive_to_state(TargetState { background_list: [].into(), direct_list: [].into() });

        // assert: we have stopped our connection
        assert_eq!(mock_le_manager.current_connection_mode(), None);
    }

    #[test]
    fn test_background_connection_remove_then_add() {
        // arrange
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());

        // act: add, remove, then re-add a background connection
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });
        manager.drive_to_state(TargetState { background_list: [].into(), direct_list: [].into() });
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [].into(),
        });

        // assert: we resume our background connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Background));
    }
    #[test]
    fn test_retry_direct_connection_after_disconnect() {
        // arrange
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());

        // act: initiate a direct connection
        manager.drive_to_state(TargetState {
            background_list: [].into(),
            direct_list: [ADDRESS_1].into(),
        });
        // act: the connection succeeds (and later disconnects)
        mock_le_manager.on_le_connect(ADDRESS_1, ErrorCode::SUCCESS);
        manager.on_connect_complete(ADDRESS_1);
        // the peer later disconnects
        mock_le_manager.on_le_disconnect(ADDRESS_1);
        // act: retry the direct connection
        manager.drive_to_state(TargetState {
            background_list: [].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // assert: we have resumed the direct connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
        assert_eq!(mock_le_manager.current_acceptlist().len(), 1);
        assert!(mock_le_manager.current_acceptlist().contains(&ADDRESS_1));
    }

    #[test]
    fn test_background_connection_remove_then_add_while_direct() {
        // arrange: a pending direct connection
        let mock_le_manager = MockActiveLeAclManager::new();
        let mut manager = LeAcceptlistManager::new(mock_le_manager.clone());
        manager.drive_to_state(TargetState {
            background_list: [].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // act: add, remove, then re-add a background connection
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });
        manager.drive_to_state(TargetState {
            background_list: [].into(),
            direct_list: [ADDRESS_1].into(),
        });
        manager.drive_to_state(TargetState {
            background_list: [ADDRESS_1].into(),
            direct_list: [ADDRESS_1].into(),
        });

        // assert: we remain doing our direct connection
        assert_eq!(mock_le_manager.current_connection_mode(), Some(ConnectionMode::Direct));
    }
}
