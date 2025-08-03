use aes::cipher::{generic_array::GenericArray, BlockEncrypt, KeyInit};
use aes::Aes128;
use clap::Parser;
use rand::Rng;

#[derive(Parser, Debug)]
#[clap(author, version, about, long_about = None)]
struct Args {
    /// The command task to perform [verify|generate]
    /// e.g.
    ///     irk-calculator -c verify -i "..." -a "..."
    #[clap(short, long)]
    command: String,
    /// The Identity Resolving Key
    #[clap(short, long)]
    irk: String,
    /// The addres to verify if verifying
    #[clap(short, long, required(false), default_value = "00:00:00:00:00:00")]
    address: String,
}

fn e(key: [u8; 16], plaintext_data: [u8; 16]) -> [u8; 16] {
    println!("=====[ e ]=====");
    let mut key_reversed = key.clone();
    key_reversed.reverse();
    println!("Reversed Key: {:02X?}", key_reversed);
    let cipher = Aes128::new(&GenericArray::from(key_reversed));
    let mut block = GenericArray::from(plaintext_data);
    println!("Data: {:02X?}", block);
    cipher.encrypt_block(&mut block);
    println!("Encrypted data: {:02X?}", block);
    let mut ret = [0u8; 16];
    ret.clone_from_slice(block.iter().as_slice());
    println!("=====[ /e ]=====");
    ret
}

/// ah function as defined in BT Spec Core v5.2 pg 1626
///
/// k is 128 bits
/// r is 24 bits
/// padding is 104 bits
///
/// returns 3 byte array
fn _ah(k: [u8; 16], r: [u8; 3], padding: [u8; 13]) -> [u8; 3] {
    println!("=====[ ah ]=====");
    let mut padded_r: [u8; 16] = [0u8; 16];
    // Pad the r to become r'
    padded_r[..13].clone_from_slice(&padding);
    padded_r[13..].clone_from_slice(&r);
    println!("K: {:02X?}", k);
    println!("R: {:02X?}", r);
    println!("R': {:02X?}", padded_r);

    // Create data
    let encrypted_data = e(k, padded_r);
    // Mod 2^24 (Only take last 3 bytes)
    let mut ret = [0u8; 3];
    let mut i = 0;
    for b in &encrypted_data[13..] {
        ret[i] = *b;
        i += 1;
    }
    println!("Mod 2^24 data: {:02X?}", ret);
    println!("=====[ /ah ]=====");
    ret
}

fn ah(k: [u8; 16], r: [u8; 3]) -> [u8; 3] {
    let padding = [0u8; 13];
    _ah(k, r, padding)
}

fn to_hex_string(bytes: Vec<u8>) -> String {
    let s: Vec<String> = bytes.iter().map(|b| format!("{:02X}", b)).collect();

    s.join(":")
}

fn parse_hex(hexstr: &str) -> Vec<u8> {
    let mut hex_bytes = hexstr
        .as_bytes()
        .iter()
        .filter_map(|b| match b {
            b'0'..=b'9' => Some(b - b'0'),
            b'a'..=b'f' => Some(b - b'a' + 10),
            b'A'..=b'F' => Some(b - b'A' + 10),
            _ => None,
        })
        .fuse();

    let mut bytes = Vec::new();
    while let (Some(h), Some(l)) = (hex_bytes.next(), hex_bytes.next()) {
        bytes.push(h << 4 | l)
    }
    bytes
}

fn parse_irk(irk: String) -> Vec<u8> {
    let irk_byte_array = parse_hex(irk.as_str());
    assert_eq!(16, irk_byte_array.len(), "IRK '{:02X?}' must be 16 octets!", irk_byte_array);
    irk_byte_array
}

fn parse_address(address: String) -> Vec<u8> {
    let address_byte_array = parse_hex(address.as_str());
    assert_eq!(
        6,
        address_byte_array.len(),
        "Address '{:02X?}' must be 6 octets!",
        address_byte_array
    );
    address_byte_array
}

// TODO(optedoblivion): Verify address is RPA
fn verify_irk_address(irk: String, address: String) -> bool {
    println!("Verifying '{}' matches '{}'", irk, address);

    // IRK
    let irk_byte_array = parse_irk(irk);
    println!("IRK Byte Array: {:02X?}", irk_byte_array);

    // Address
    let address_byte_array = parse_address(address);
    println!("Address Byte Array: {:02X?}", address_byte_array);

    // prand
    let mut prand = [0u8; 3];
    prand.clone_from_slice(&address_byte_array[..=2]);
    println!("prand: {:02X?}", prand);

    // Hash
    let given_hash = &address_byte_array[3..];
    println!("Given hash: {:02X?}", given_hash);

    let mut irk_slice = [0u8; 16];
    irk_slice.clone_from_slice(&irk_byte_array[..]);
    let hash = ah(irk_slice, prand);
    println!("Given hash: {:02X?}", given_hash);
    println!("Calculated hash: {:02X?}", hash);
    println!("IRK + Address combination is valid: {}", given_hash == hash);
    given_hash == hash
}

fn generate_irk_address(irk: String) -> String {
    println!("Generating new address with '{}'", irk);

    // IRK
    let irk_byte_array = parse_irk(irk);
    println!("IRK Byte Array: {:02X?}", irk_byte_array);

    // prand
    let prand = rand::thread_rng().gen::<[u8; 3]>();
    println!("prand: {:02X?}", prand);

    let mut irk_slice = [0u8; 16];
    irk_slice.clone_from_slice(&irk_byte_array[..]);
    let hash = ah(irk_slice, prand);
    println!("Calculated hash: {:02X?}", hash);

    let mut calculated_address = [0u8; 6];
    println!("len: {}", prand.len());
    calculated_address[0] = prand[0];
    calculated_address[1] = prand[1];
    calculated_address[2] = prand[2];
    calculated_address[3] = hash[0];
    calculated_address[4] = hash[1];
    calculated_address[5] = hash[2];
    println!("Calculated Address: {}", to_hex_string(calculated_address.to_vec()));
    to_hex_string(calculated_address.to_vec())
}

fn main() {
    let args = Args::parse();

    match args.command.as_str() {
        "verify" => {
            verify_irk_address(args.irk, args.address);
        }
        "generate" => {
            generate_irk_address(args.irk);
        }
        _ => {
            println!("Invalid command!");
        }
    }
}

#[test]
fn test_verify_good_combos() {
    assert_eq!(
        true,
        verify_irk_address(
            String::from("0102030405060708090a0b0c0d0e0f10"),
            String::from("5B:89:68:1E:4E:19"),
        )
    );
    assert_eq!(
        true,
        verify_irk_address(
            String::from("0102030405060708090a0b0c0d0e0f10"),
            String::from("79:CB:92:70:BE:B3"),
        )
    );
    assert_eq!(
        true,
        verify_irk_address(
            String::from("0102030405060708090a0b0c0d0e0f10"),
            String::from("5D:EC:DA:8C:33:AE"),
        )
    );
}

#[test]
fn test_verify_bad_combos() {
    assert_eq!(
        false,
        verify_irk_address(
            String::from("0102030405060708090a0b0c0d0e0f10"),
            String::from("60:89:68:1E:4E:19"),
        )
    );
}

fn _validate_address_byte(i: usize, address: &String) {
    println!("address: {:?}", address);
    let vs: Vec<String> = vec![
        address.chars().nth(i).unwrap().to_string(),
        address.chars().nth(i + 1).unwrap().to_string(),
    ];
    let byte_string = vs.join("");
    match parse_hex(&byte_string.as_str()) {
        a => {
            println!("herp: {:?}", a);
            assert_eq!(1, a.len());
        }
    }
}

#[test]
fn test_validate_good_address_byte() {
    let good_address = String::from("1A:34:AF:78:98:76");
    println!("{:?}", good_address.chars().nth(0).unwrap().to_string().as_str());
    _validate_address_byte(0, &good_address);
}

#[test]
#[should_panic]
fn test_validate_bad_address_byte() {
    let bad_address = String::from("ZX:12:34:56:78:90");
    _validate_address_byte(0, &bad_address);
}

#[test]
fn test_generate_rpa() {
    let address = generate_irk_address(String::from("0102030405060708090a0b0c0d0e0f10"));
    _validate_address_byte(0, &address);
    assert_eq!(address.chars().nth(2).unwrap(), ':');
    _validate_address_byte(3, &address);
    assert_eq!(address.chars().nth(5).unwrap(), ':');
    _validate_address_byte(6, &address);
    assert_eq!(address.chars().nth(8).unwrap(), ':');
    _validate_address_byte(9, &address);
    assert_eq!(address.chars().nth(11).unwrap(), ':');
    _validate_address_byte(12, &address);
}
