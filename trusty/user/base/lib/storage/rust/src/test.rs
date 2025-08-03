use crate::{Error, ErrorCode, FileState, OpenMode, Port, Session};
use test::assert_eq;

/// Tests that connecting to the service works.
#[test]
fn connect() {
    let session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();
    session.close();
}

/// Tests successfully connecting to a port when not waiting for the port to be
/// available.
#[test]
fn connect_no_wait() {
    let session = Session::new(Port::TamperDetectEarlyAccess, false).unwrap();
    session.close();
}

/// Tests that `Session::new` will return an error instead of blocking when
/// trying to connect to a port that doesn't exist if not waiting on the port to
/// be available.
#[test]
fn connect_fail_no_wait() {
    let result = Session::new(Port::TestPortNonExistent, false);
    assert_eq!(Err(Error::Code(ErrorCode::NotFound)), result);
}

/// Tests that reading/writing to a file with the basic file access APIs works.
#[test]
fn read_write() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "read_write.txt";
    let file_contents = "Hello, world!";

    // Write the initial contents of the file.
    session.write(file_name, file_contents.as_bytes()).unwrap();

    // Read the contents of the file.
    let data = &mut [0; 32];
    let data = session.read(file_name, data).unwrap();

    // Verify that the contents are as expected.
    assert_eq!(data.len(), file_contents.len(), "Incorrect number of bytes read");
    assert_eq!(data, file_contents.as_bytes(), "Incorrect file contents returned");
}

/// Tests that trying to read a full file with a buffer that's too small returns
/// an error.
#[test]
fn read_all_buf_too_small() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "read_all_buf_too_small.txt";
    let file_contents = "Hello, world!";

    // Write the initial contents of the file.
    session.write(file_name, file_contents.as_bytes()).unwrap();

    // Try to read the contents of the file with a buffer that is not large enough
    // to hold the full file.
    let data = &mut [0; 5];
    let result = session.read(file_name, data);

    assert_eq!(Err(Error::Code(ErrorCode::NotEnoughBuffer)), result);
}

/// Tests `read_at`, verifying that it can partially read a file at different
/// offsets, and that it does not read past the end of the file.
#[test]
fn read_at() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "read_at.txt";
    let file_contents = b"Hello, world!";

    // Write the initial contents of the file.
    session.write(file_name, file_contents).unwrap();

    let mut file = session.open_file(file_name, OpenMode::Open).unwrap();

    // Read 5 bytes of the file starting at the 4th byte.
    let data = &mut [0; 5];
    let result = session.read_at(&mut file, 3, data);

    // Verify that the correct chunk of the file was read.
    assert_eq!(Ok(&file_contents[3..8]), result);

    // Read past the end of the file to verify that the buffer is only partially
    // filled.
    data.fill(0);
    let result = session.read_at(&mut file, 10, data);

    // Verify that the remaining portion of the file is returned.
    assert_eq!(Ok(&file_contents[10..]), result);

    // Verify that only a prefix of the buffer was overwritten.
    let expected = &[file_contents[10], file_contents[11], file_contents[12], 0, 0][..];
    assert_eq!(expected, data, "Data buffer was not overwritten in expected way");
}

#[test]
fn write_at() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "write_at.txt";
    let file_contents = "Hello, world!";

    // Write the initial contents of the file.
    let mut file = session.open_file(file_name, OpenMode::Create).unwrap();
    session.write_all(&mut file, file_contents.as_bytes()).unwrap();

    // Overwrite a portion of the file.
    let result = session.write_at(&mut file, 7, b"Trusty");
    assert_eq!(Ok(()), result);

    // Verify that the contents are as expected.
    let data = &mut [0; 32];
    let result = session.read_all(&file, data);
    let expected = b"Hello, Trusty".as_slice();
    assert_eq!(Ok(expected), result, "Incorrect bytes read");

    // Use `write_at` in a transaction.
    let mut transaction = session.begin_transaction();
    assert_eq!(Ok(()), transaction.write_at(&mut file, 7, b"C"));
    assert_eq!(Ok(()), transaction.commit());

    let result = session.read_all(&file, data);
    let expected = b"Hello, Crusty".as_slice();
    assert_eq!(Ok(expected), result);

    // Verify that attempting to write past the end of the file succeeds and expands
    // the file to fit the new data.
    let result = session.write_at(&mut file, 7, b"too long data");
    assert_eq!(Ok(()), result, "Writing past end of file failed");

    let result = session.read_all(&file, data);
    let expected = b"Hello, too long data".as_slice();
    assert_eq!(Ok(expected), result);
}

/// Tests that file sizes are reported correctly, and that setting file size
/// works both when increasing and decreasing a file's size.
#[test]
fn get_set_size() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "get_set_size.txt";
    let initial_contents = "Hello, world!";
    let mut buf = [0; 32];

    // Create the file and set its initial contents.
    let mut file = session.open_file(file_name, OpenMode::Create).unwrap();
    session.write_all(&mut file, initial_contents.as_bytes()).unwrap();

    // Verify that the reported size is correct after writing to the file.
    let size = session.get_size(&file).unwrap();
    assert_eq!(initial_contents.len(), size, "File has incorrect size after initial creation");

    // Decrease the file's size and verify that the contents are truncated as
    // expected.
    session.set_size(&mut file, 5).unwrap();
    let contents = session.read_all(&file, buf.as_mut_slice()).unwrap();
    assert_eq!("Hello".as_bytes(), contents, "File has incorrect contents after truncating");

    // Increase the file's size and verify that the contents are 0-extended as
    // expected.
    session.set_size(&mut file, 10).unwrap();
    let contents = session.read_all(&file, buf.as_mut_slice()).unwrap();
    assert_eq!(
        "Hello\0\0\0\0\0".as_bytes(),
        contents,
        "File has incorrect contents after extending",
    );
}

/// Tests that files can be renamed and deleted.
#[test]
fn rename_delete() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let before_name = "before.txt";
    let after_name = "after.txt";
    let file_contents = "Hello, world!";
    let mut buf = [0; 32];

    // Verify that neither of the test files exist before the test runs.
    session.open_file(before_name, OpenMode::Open).unwrap_err();
    session.open_file(after_name, OpenMode::Open).unwrap_err();

    // Create the initial file and then rename it.
    session.write(before_name, file_contents.as_bytes()).unwrap();
    session.rename(before_name, after_name).unwrap();

    // Verify that the file no longer exists at the original name, and that the new
    // file has the same contents as the original file.
    session.open_file(before_name, OpenMode::Open).unwrap_err();
    let contents = session.read(after_name, buf.as_mut_slice()).unwrap();
    assert_eq!(file_contents.as_bytes(), contents, "File has incorrect contents after renaming");

    // Delete the file and then verify it no longer exists
    session.remove(after_name).unwrap();
    session.open_file(after_name, OpenMode::Open).unwrap_err();
}

/// Tests that a file that is open as a handle cannot be renamed.
#[test]
fn cannot_rename_open_file() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    // Clear any leftover files from a previous test run.
    remove_all(&mut session);

    let file_name = "cannot_rename_or_delete_open_file.txt";

    // Create the file and open a handle for it.
    let file = session.open_file(file_name, OpenMode::CreateExclusive).unwrap();

    // Verify that renaming the file fails while the handle is open.
    assert_eq!(
        Err(Error::Code(ErrorCode::NotAllowed)),
        session.rename(file_name, "different_file.txt"),
        "Unexpected result when renaming open file",
    );

    // Verify that the file can be renamed once the handle is closed.
    file.close();
    session.rename(file_name, "different_file.txt").unwrap();
}

/// Tests that multiple files can be modified in a single transaction, and that
/// file handles opened as part of a transaction can still be used after the
/// transaction is committed.
#[test]
fn multiple_files_in_transaction() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_a = "file_a.txt";
    let file_b = "file_b.txt";
    let file_contents = "multiple_files_in_transaction";
    let mut buf = [0; 32];

    // Clear any leftover files from a previous test run.
    remove_all(&mut session);

    // Start a transaction, create two files, and then write the contents of those
    // files before committing the transaction.

    let mut transaction = session.begin_transaction();

    let mut file_a = transaction.open_file(file_a, OpenMode::CreateExclusive).unwrap();
    let mut file_b = transaction.open_file(file_b, OpenMode::CreateExclusive).unwrap();

    transaction.write_all(&mut file_a, file_contents.as_bytes()).unwrap();
    transaction.write_all(&mut file_b, file_contents.as_bytes()).unwrap();

    transaction.commit().unwrap();

    // Verify that we can observe the updated file contents. Note that we reuse the
    // existing file handles to verify that file handles opened in a transaction
    // remain valid after the transaction is committed.

    let actual_contents = session.read_all(&file_a, &mut buf).unwrap();
    assert_eq!(
        file_contents.as_bytes(),
        actual_contents,
        "Changes from transaction were not written",
    );

    let actual_contents = session.read_all(&file_b, &mut buf).unwrap();
    assert_eq!(
        file_contents.as_bytes(),
        actual_contents,
        "Changes from transaction were not written",
    );
}

/// Tests that file contents can be read while using a `Transaction`.
#[test]
fn read_in_transaction() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "read_in_transaction.txt";
    let file_contents = "Hello, world!";

    // Write the initial contents of the file.
    let mut file = session.open_file(file_name, OpenMode::Create).unwrap();
    session.write_all(&mut file, file_contents.as_bytes()).unwrap();

    // Overwrite a portion of the file in a transaction.
    let mut transaction = session.begin_transaction();
    let result = transaction.write_at(&mut file, 7, b"Trusty");
    assert_eq!(Ok(()), result);

    // Verify that the contents are as expected.
    let data = &mut [0; 32];
    let result = transaction.read_all(&file, data);
    let expected = b"Hello, Trusty".as_slice();
    assert_eq!(Ok(expected), result, "Incorrect bytes read");

    // Change the contents of the file then verify that we can get the updated length.
    let long_contents = b"Now for a much much longer file";
    assert_eq!(Ok(()), transaction.write_all(&mut file, long_contents));
    assert_eq!(Ok(long_contents.len()), transaction.get_size(&file));

    transaction.discard().unwrap();
}

/// Tests that pending changes in a transaction are not committed if the
/// transaction is discarded.
#[test]
fn discard_transaction() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "discard_transaction.txt";
    let file_contents = "discard_transaction";
    let mut buf = [0; 32];

    // Clear any leftover files from a previous test run.
    remove_all(&mut session);

    // Begin to make changes in a transaction, then discard the transaction without
    // committing the pending changes.
    {
        let mut transaction = session.begin_transaction();

        let mut file = transaction.open_file(file_name, OpenMode::CreateExclusive).unwrap();
        transaction.write_all(&mut file, file_contents.as_bytes()).unwrap();

        transaction.discard().unwrap();
    }

    // Verify that the file was never created or written to.
    assert_eq!(
        Err(Error::Code(ErrorCode::NotFound)),
        session.read(file_name, &mut buf),
        "Unexpected result when renaming open file",
    );
}

/// Tests that pending changes in a transaction are not committed if the
/// transaction is discarded.
#[test]
fn drop_transaction() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    let file_name = "discard_transaction_on_drop.txt";
    let file_contents = "discard_transaction_on_drop";
    let mut buf = [0; 32];

    // Clear any leftover files from a previous test run.
    remove_all(&mut session);

    // Begin to make changes in a transaction, then drop it without explicitly
    // committing or discarding it. This should discard any pending changes.
    {
        let mut transaction = session.begin_transaction();

        let mut file = transaction.open_file(file_name, OpenMode::CreateExclusive).unwrap();
        transaction.write_all(&mut file, file_contents.as_bytes()).unwrap();
    }

    // Verify that the file was never created or written to.
    assert_eq!(
        Err(Error::Code(ErrorCode::NotFound)),
        session.read(file_name, &mut buf),
        "Unexpected result when renaming open file",
    );
}

/// Tests directory enumeration in an empty directory.
#[test]
fn list_empty_dir() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    // Reset the storage dir so that we can test listing files in an empty dir.
    remove_all(&mut session);

    // Verify that listing files in an empty dir yields no elements.
    for entry in session.list_files().unwrap() {
        let entry = entry.unwrap();
        panic!("Unexpected file: {:?}", entry);
    }
}

/// Tests that directory enumeration correctly returns errors if the session is
/// closed while directory enumeration is still in progress.
#[test]
fn drop_session_while_listing() {
    // Open a session, start listing files, and then drop the `Session` object
    // while keeping the `DirIter` object alive.
    let mut dir_iter = {
        let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();
        session.list_files().unwrap()
    };

    // Verify that attempting to use the iterator after the session is closed
    // generates an error and then only yields `None`.
    assert_eq!(Some(Err(Error::Code(ErrorCode::NotFound))), dir_iter.next());
    assert_eq!(None, dir_iter.next());
    assert_eq!(None, dir_iter.next());
}

/// Verifies that directory enumeration lists files correctly, and that that
/// states of files are reported correctly.
#[test]
fn list_during_transaction() {
    let mut session = Session::new(Port::TamperDetectEarlyAccess, true).unwrap();

    // Clear any leftover files from a previous test run.
    remove_all(&mut session);

    let file_a = "file_a.txt";
    let file_b = "file_b.txt";
    let file_contents = "Hello, world!";

    // Create a file and write to it such that it is fully committed.
    session.write(file_a, file_contents.as_bytes()).unwrap();

    let mut transaction = session.begin_transaction();

    // Create a file as part of `transaction` that will be in the "added" state.
    let mut file = transaction.open_file(file_b, OpenMode::CreateExclusive).unwrap();
    transaction.write_all(&mut file, file_contents.as_bytes()).unwrap();

    // Verify that listing files while a transaction is active correctly reports all
    // expected files and in the correct states.
    let mut dir_iter = transaction.list_files().unwrap();
    assert_eq!(Some(Ok((file_a.into(), FileState::Committed))), dir_iter.next());
    assert_eq!(Some(Ok((file_b.into(), FileState::Added))), dir_iter.next());
    assert_eq!(None, dir_iter.next());

    transaction.discard().unwrap();
}

/// Removes all files in the storage for `session`.
///
/// Useful for resetting the state of the storage for tests that need to iterate
/// the contents of storage and so need things to be in a known starting state.
fn remove_all(session: &mut Session) {
    for entry in session.list_files().unwrap() {
        let (name, _state) = entry.unwrap();
        session.remove(&name).unwrap();
    }
}

test::init!();
