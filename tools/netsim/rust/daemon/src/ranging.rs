// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Ranging library

use glam::{EulerRot, Quat, Vec3};

/// The Free Space Path Loss (FSPL) model is considered as the standard
/// under the ideal scenario.

/// (dBm) PATH_LOSS at 1m for isotropic antenna transmitting BLE.
const PATH_LOSS_AT_1M: f32 = 40.20;

/// Convert distance to RSSI using the free space path loss equation.
/// See [Free-space_path_loss][1].
///
/// [1]: http://en.wikipedia.org/wiki/Free-space_path_loss
///
/// # Parameters
///
/// * `distance`: distance in meters (m).
/// * `tx_power`: transmitted power (dBm) calibrated to 1 meter.
///
/// # Returns
///
/// The rssi that would be measured at that distance, in the
/// range -120..20 dBm,
pub fn distance_to_rssi(tx_power: i8, distance: f32) -> i8 {
    // TODO(b/285634913)
    // Rootcanal reporting tx_power of 0 or 1 during Nearby Share
    let new_tx_power = match tx_power {
        0 | 1 => -49,
        _ => tx_power,
    };
    match distance == 0.0 {
        true => (new_tx_power as f32 + PATH_LOSS_AT_1M).clamp(-120.0, 20.0) as i8,
        false => (new_tx_power as f32 - 20.0 * distance.log10()).clamp(-120.0, 20.0) as i8,
    }
}

// helper function for performing division with
// zero division check
#[allow(unused)]
fn checked_div(num: f32, den: f32) -> Option<f32> {
    (den != 0.).then_some(num / den)
}

// helper function for calculating azimuth angle
// from a given 3D delta vector.
#[allow(unused)]
fn azimuth(delta: Vec3) -> f32 {
    checked_div(delta.x, delta.z).map_or(
        match delta.x == 0. {
            true => 0.,
            false => delta.x.signum() * std::f32::consts::FRAC_2_PI,
        },
        f32::atan,
    ) + if delta.z >= 0. { 0. } else { delta.x.signum() * std::f32::consts::PI }
}

// helper function for calculating elevation angle
// from a given 3D delta vector.
#[allow(unused)]
fn elevation(delta: Vec3) -> f32 {
    checked_div(delta.y, f32::sqrt(delta.x.powi(2) + delta.z.powi(2)))
        .map_or(delta.y.signum() * std::f32::consts::FRAC_PI_2, f32::atan)
}

/// Pose struct
///
/// This struct allows for a mathematical representation of
/// position and orientation values from the protobufs, which
/// would enable to compute range, azimuth, and elevation.
#[allow(unused)]
pub struct Pose {
    position: Vec3,
    orientation: Quat,
}

impl Pose {
    #[allow(unused)]
    fn new(x: f32, y: f32, z: f32, yaw: f32, pitch: f32, roll: f32) -> Self {
        Pose {
            position: Vec3::new(x, y, z),
            orientation: Quat::from_euler(
                EulerRot::ZXY,
                roll.to_radians(),
                pitch.to_radians(),
                yaw.to_radians(),
            ),
        }
    }
}

/// UWB Ranging Model for computing range, azimuth, and elevation
/// The raning model brought from https://github.com/google/pica
#[allow(unused)]
pub fn compute_range_azimuth_elevation(
    a: &Pose,
    b: &Pose,
) -> std::result::Result<(u16, i16, i8), String> {
    let delta = b.position - a.position;
    let distance = delta.length();
    let direction = a.orientation.mul_vec3(delta);
    let azimuth = azimuth(direction).to_degrees().round();
    let elevation = elevation(direction).to_degrees().round();

    if !(-180. ..=180.).contains(&azimuth) {
        return Err(format!("azimuth is not between -180 and 180. value: {azimuth}"));
    }
    if !(-90. ..=90.).contains(&elevation) {
        return Err(format!("elevation is not between -90 and 90. value: {elevation}"));
    }
    Ok((f32::min(distance, u16::MAX as f32) as u16, azimuth as i16, elevation as i8))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rssi_at_0m() {
        let rssi_at_0m = distance_to_rssi(-120, 0.0);
        assert_eq!(rssi_at_0m, -79);
    }

    #[test]
    fn rssi_at_1m() {
        // With transmit power at 0 dBm verify a reasonable rssi at 1m
        let rssi_at_1m = distance_to_rssi(0, 1.0);
        assert!(rssi_at_1m < -35 && rssi_at_1m > -55);
    }

    #[test]
    fn rssi_saturate_inf() {
        // Verify that the rssi saturates at -120 for very large distances.
        let rssi_inf = distance_to_rssi(-120, 1000.0);
        assert_eq!(rssi_inf, -120);
    }

    #[test]
    fn rssi_saturate_sup() {
        // Verify that the rssi saturates at +20 for the largest tx power
        // and nearest distance.
        let rssi_sup = distance_to_rssi(20, 0.0);
        assert_eq!(rssi_sup, 20);
    }

    #[test]
    fn range() {
        let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        {
            let b_pose = Pose::new(10.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            let (range, _, _) = compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(range, 10);
        }
        {
            let b_pose = Pose::new(-10.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            let (range, _, _) = compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(range, 10);
        }
        {
            let b_pose = Pose::new(10.0, 10.0, 0.0, 0.0, 0.0, 0.0);
            let (range, _, _) = compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(range, f32::sqrt(10. * 10. + 10. * 10.).round() as u16);
        }
        {
            let b_pose = Pose::new(-10.0, -10.0, -10.0, 0.0, 0.0, 0.0);
            let (range, _, _) = compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(range, f32::sqrt(10. * 10. + 10. * 10. + 10. * 10.).round() as u16);
        }
    }

    #[test]
    fn azimuth_without_rotation() {
        let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        {
            let b_pose = Pose::new(10.0, 0.0, 10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 45);
            assert_eq!(elevation, 0);
        }
        {
            let b_pose = Pose::new(-10.0, 0.0, 10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, -45);
            assert_eq!(elevation, 0);
        }
        {
            let b_pose = Pose::new(10.0, 0.0, -10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 135);
            assert_eq!(elevation, 0);
        }
        {
            let b_pose = Pose::new(-10.0, 0.0, -10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, -135);
            assert_eq!(elevation, 0);
        }
    }

    #[test]
    fn elevation_without_rotation() {
        let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        {
            let b_pose = Pose::new(0.0, 10.0, 10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, 45);
        }
        {
            let b_pose = Pose::new(0.0, -10.0, 10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, -45);
        }
        {
            let b_pose = Pose::new(0.0, 10.0, -10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert!(azimuth == 180 || azimuth == -180);
            assert_eq!(elevation, 45);
        }
        {
            let b_pose = Pose::new(0.0, -10.0, -10.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert!(azimuth == 180 || azimuth == -180);
            assert_eq!(elevation, -45);
        }
    }

    #[test]
    fn rotation_only() {
        let b_pose = Pose::new(0.0, 0.0, 10.0, 0.0, 0.0, 0.0);
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, 0);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 45.0, 0.0, 0.0); // <=> azimuth = -45deg
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 45);
            assert_eq!(elevation, 0);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 45.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, -45);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 45.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, 0);
        }
    }

    #[test]
    fn rotation_only_complex_position() {
        let b_pose = Pose::new(10.0, 10.0, 10.0, 0.0, 0.0, 0.0);
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 45);
            assert_eq!(elevation, 35);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 90.0, 0.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 135);
            assert_eq!(elevation, 35);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 90.0, 0.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 45);
            assert_eq!(elevation, -35);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, 0.0, 0.0, 90.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, -45);
            assert_eq!(elevation, 35);
        }
        {
            let a_pose = Pose::new(0.0, 0.0, 0.0, -45.0, 35.0, 42.0);
            let (_, azimuth, elevation) =
                compute_range_azimuth_elevation(&a_pose, &b_pose).unwrap();
            assert_eq!(azimuth, 0);
            assert_eq!(elevation, 0);
        }
    }
}
