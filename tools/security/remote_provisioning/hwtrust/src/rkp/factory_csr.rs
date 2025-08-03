use crate::rkp::Csr;
use crate::session::Session;
use anyhow::{bail, Result};
use serde_json::{Map, Value};

/// Represents a "Factory CSR", which is a JSON value captured for each device on the factory
/// line. This JSON is uploaded to the RKP backend to register the device. We reuse the CSR
/// (Certificate Signing Request) format for this as an implementation convenience. The CSR
/// actually contains an empty set of keys for which certificates are needed.
#[non_exhaustive]
#[derive(Debug, Eq, PartialEq)]
pub struct FactoryCsr {
    /// The CSR, as created by an IRemotelyProvisionedComponent HAL.
    pub csr: Csr,
    /// The name of the HAL that generated the CSR.
    pub name: String,
}

fn get_string_from_map(fields: &Map<String, Value>, key: &str) -> Result<String> {
    match fields.get(key) {
        Some(Value::String(s)) => Ok(s.to_string()),
        Some(v) => bail!("Unexpected type for '{key}'. Expected String, found '{v:?}'"),
        None => bail!("Unable to locate '{key}' in input"),
    }
}

impl FactoryCsr {
    /// Parse the input JSON string into a CSR that was captured on the factory line. The
    /// format of the JSON data is defined by rkp_factory_extraction_tool.
    pub fn from_json(session: &Session, json: &str) -> Result<Self> {
        match serde_json::from_str(json) {
            Ok(Value::Object(map)) => Self::from_map(session, map),
            Ok(unexpected) => bail!("Expected a map, got some other type: {unexpected}"),
            Err(e) => bail!("Error parsing input json: {e}"),
        }
    }

    fn from_map(session: &Session, fields: Map<String, Value>) -> Result<Self> {
        let base64 = get_string_from_map(&fields, "csr")?;
        let name = get_string_from_map(&fields, "name")?;
        let csr = Csr::from_base64_cbor(session, &base64)?;
        Ok(Self { csr, name })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cbor::rkp::csr::testutil::{parse_pem_public_key_or_panic, test_device_info};
    use crate::dice::{ChainForm, DegenerateChain};
    use crate::rkp::device_info::DeviceInfoVersion;
    use crate::rkp::factory_csr::FactoryCsr;
    use crate::rkp::{ProtectedData, UdsCerts, UdsCertsEntry};
    use anyhow::anyhow;
    use itertools::Itertools;
    use openssl::{pkey::PKey, x509::X509};
    use std::fs;
    use std::fs::File;

    fn json_map_from_file(path: &str) -> Result<Map<String, Value>> {
        let input = File::open(path)?;
        match serde_json::from_reader(input)? {
            Value::Object(map) => Ok(map),
            other => Err(anyhow!("Unexpected JSON. Wanted a map, found {other:?}")),
        }
    }

    #[test]
    fn from_json_valid_v2_ed25519() {
        let json = fs::read_to_string("testdata/factory_csr/v2_ed25519_valid.json").unwrap();
        let csr = FactoryCsr::from_json(&Session::default(), &json).unwrap();
        let subject_public_key = parse_pem_public_key_or_panic(
            "-----BEGIN PUBLIC KEY-----\n\
            MCowBQYDK2VwAyEAOhWsfxcBLgUfLLdqpb8cLUWutkkPtfIqDRfJC3LUihI=\n\
            -----END PUBLIC KEY-----\n",
        );
        let degenerate = ChainForm::Degenerate(
            DegenerateChain::new("self-signed", "self-signed", subject_public_key).unwrap(),
        );
        let chain = [
            "-----BEGIN CERTIFICATE-----\n\
             MIIBaDCCARqgAwIBAgIBezAFBgMrZXAwKzEVMBMGA1UEChMMRmFrZSBDb21wYW55\n\
             MRIwEAYDVQQDEwlGYWtlIFJvb3QwHhcNMjMwODAxMjI1MTM0WhcNMjMwODMxMjI1\n\
             MTM0WjArMRUwEwYDVQQKEwxGYWtlIENvbXBhbnkxEjAQBgNVBAMTCUZha2UgUm9v\n\
             dDAqMAUGAytlcAMhAJYhPNIeQXe6+GPFiQAg4WtK+D8HuWaF6Es4X3HgDzq7o2Mw\n\
             YTAdBgNVHQ4EFgQUDR0DF3abDeR3WXSlIhpN07R049owHwYDVR0jBBgwFoAUDR0D\n\
             F3abDeR3WXSlIhpN07R049owDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC\n\
             AgQwBQYDK2VwA0EAWNEhXrATWj4MXT/n38OwbngUm/n/5+vGFqV0aXuJPX/8d6Yx\n\
             BbX/LFv6m8/VuPuItSqK4AudgwZJoupR/lknDg==\n\
             -----END CERTIFICATE-----\n",
            "-----BEGIN CERTIFICATE-----\n\
             MIIBbDCCAR6gAwIBAgICAcgwBQYDK2VwMCsxFTATBgNVBAoTDEZha2UgQ29tcGFu\n\
             eTESMBAGA1UEAxMJRmFrZSBSb290MB4XDTIzMDgwMTIyNTEzNFoXDTIzMDgzMTIy\n\
             NTEzNFowLjEVMBMGA1UEChMMRmFrZSBDb21wYW55MRUwEwYDVQQDEwxGYWtlIENo\n\
             aXBzZXQwKjAFBgMrZXADIQA6Fax/FwEuBR8st2qlvxwtRa62SQ+18ioNF8kLctSK\n\
             EqNjMGEwHQYDVR0OBBYEFEbOrkgBL2SUCLJayyvpc+oR7m6/MB8GA1UdIwQYMBaA\n\
             FA0dAxd2mw3kd1l0pSIaTdO0dOPaMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/\n\
             BAQDAgIEMAUGAytlcANBANJmKGgiZP3tqtjnHpbmR3ypkXtvuqmo6KTBIHKsGKAO\n\
             mH8qlMQPLmNSdxTs3OnVrlxQwZqXxHjVHS/6OpkkFgo=\n\
             -----END CERTIFICATE-----\n",
        ];
        let chain = chain
            .iter()
            .map(|pem| X509::from_pem(pem.as_bytes()).unwrap().to_der().unwrap())
            .collect_vec();

        let mut uds_certs = UdsCerts::new();
        uds_certs
            .0
            .insert("google-test".to_string(), UdsCertsEntry::new_x509_chain(chain).unwrap());
        assert_eq!(
            csr,
            FactoryCsr {
                csr: Csr::V2 {
                    device_info: test_device_info(DeviceInfoVersion::V2),
                    challenge: b"\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10"
                        .to_vec(),
                    protected_data: ProtectedData::new(vec![0; 32], degenerate, Some(uds_certs)),
                },
                name: "default".to_string(),
            }
        );
    }

    #[test]
    fn from_json_valid_v3_ed25519() {
        let json = fs::read_to_string("testdata/factory_csr/v3_ed25519_valid.json").unwrap();
        let csr = FactoryCsr::from_json(&Session::default(), &json).unwrap();
        if let Csr::V3 { device_info, dice_chain } = csr.csr {
            assert_eq!(device_info, test_device_info(DeviceInfoVersion::V3));
            let root_public_key = parse_pem_public_key_or_panic(
                "-----BEGIN PUBLIC KEY-----\n\
                MCowBQYDK2VwAyEA3FEn/nhqoGOKNok1AJaLfTKI+aFXHf4TfC42vUyPU6s=\n\
                -----END PUBLIC KEY-----\n",
            );
            assert_eq!(dice_chain.root_public_key(), &root_public_key);
            assert_eq!(dice_chain.payloads().len(), 1);
        } else {
            panic!("Parsed CSR was not V3: {:?}", csr);
        }
    }

    #[test]
    fn from_json_valid_v2_p256() {
        let json = fs::read_to_string("testdata/factory_csr/v2_p256_valid.json").unwrap();
        let csr = FactoryCsr::from_json(&Session::default(), &json).unwrap();
        let pem = "-----BEGIN PUBLIC KEY-----\n\
                   MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERd9pHZbUJ/b4IleUGDN8fs8+LDxE\n\
                   vG6VX1dkw0sClFs4imbzfXGbocEq74S7TQiyZkd1LhY6HRZnTC51KoGDIA==\n\
                   -----END PUBLIC KEY-----\n";
        let subject_public_key =
            PKey::public_key_from_pem(pem.as_bytes()).unwrap().try_into().unwrap();
        let degenerate = ChainForm::Degenerate(
            DegenerateChain::new("self-signed", "self-signed", subject_public_key).unwrap(),
        );
        assert_eq!(
            csr,
            FactoryCsr {
                csr: Csr::V2 {
                    device_info: test_device_info(DeviceInfoVersion::V2),
                    challenge: b"\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10"
                        .to_vec(),
                    protected_data: ProtectedData::new(vec![0; 32], degenerate, None),
                },
                name: "default".to_string(),
            }
        );
    }

    #[test]
    fn from_json_valid_v3_p256() {
        let json = fs::read_to_string("testdata/factory_csr/v3_p256_valid.json").unwrap();
        let csr = FactoryCsr::from_json(&Session::default(), &json).unwrap();
        if let Csr::V3 { device_info, dice_chain } = csr.csr {
            assert_eq!(device_info, test_device_info(DeviceInfoVersion::V3));
            let root_public_key = parse_pem_public_key_or_panic(
                "-----BEGIN PUBLIC KEY-----\n\
                MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEqT6ujVegwBbVWtsZeZmvN4WO3THx\n\
                zpPPnt2rAOdqL9DSDZcIBbLas5xh9psaEaD0o/0KxlUVZplO/BPmRf3Ycg==\n\
                -----END PUBLIC KEY-----\n",
            );
            assert_eq!(dice_chain.root_public_key(), &root_public_key);
            assert_eq!(dice_chain.payloads().len(), 1);
        } else {
            panic!("Parsed CSR was not V3: {:?}", csr);
        }
    }

    #[test]
    fn from_json_name_is_missing() {
        let mut value = json_map_from_file("testdata/factory_csr/v2_ed25519_valid.json").unwrap();
        value.remove_entry("name");
        let json = serde_json::to_string(&value).unwrap();
        let err = FactoryCsr::from_json(&Session::default(), &json).unwrap_err();
        assert!(err.to_string().contains("Unable to locate 'name'"));
    }

    #[test]
    fn from_json_name_is_wrong_type() {
        let mut value = json_map_from_file("testdata/factory_csr/v2_ed25519_valid.json").unwrap();
        value.insert("name".to_string(), Value::Object(Map::default()));
        let json = serde_json::to_string(&value).unwrap();
        let err = FactoryCsr::from_json(&Session::default(), &json).unwrap_err();
        assert!(err.to_string().contains("Unexpected type for 'name'"));
    }

    #[test]
    fn from_json_csr_is_missing() {
        let json = r#"{ "name": "default" }"#;
        let err = FactoryCsr::from_json(&Session::default(), json).unwrap_err();
        assert!(err.to_string().contains("Unable to locate 'csr'"));
    }

    #[test]
    fn from_json_csr_is_wrong_type() {
        let json = r#"{ "csr": 3.1415, "name": "default" }"#;
        let err = FactoryCsr::from_json(&Session::default(), json).unwrap_err();
        assert!(err.to_string().contains("Unexpected type for 'csr'"));
    }

    #[test]
    fn from_json_extra_tag_is_ignored() {
        let mut value = json_map_from_file("testdata/factory_csr/v2_ed25519_valid.json").unwrap();
        value.insert("extra".to_string(), Value::Bool(true));
        let json = serde_json::to_string(&value).unwrap();
        let csr = FactoryCsr::from_json(&Session::default(), &json).unwrap();
        assert_eq!(csr.name, "default");
    }
}
