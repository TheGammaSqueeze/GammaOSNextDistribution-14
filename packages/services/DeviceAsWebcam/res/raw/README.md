The JSON files in this directory can be overridden by vendors using Runtime
Resource Overlays. These serve as an optional mechanism for vendors to modify
DeviceAsWebcam behavior if needed.

The details of the overridable files are as follows:

- `physical_camera_mapping.json`:

  This file contains a mapping of the preferred physical streams that are to be
  used by DeviceAsWebcam service instead of logical streams, in order to save
  power during webcam streaming. This mapping is optional. It must be overridden
  through a static resource overlay by vendors if needed. The format of the
  mapping is :

  ```json
  {
    "<logical-camera-id-1>" : {
      "<physical-camera-id-1>" : "<camera-label-1>",
      "<physical-camera-id-2>" : "<camera-label-2>",
      ...
    },
    "<logical-camera-id-2>" : {
      "<physical-camera-id-3>" : "<camera-label-3>",
      "<physical-camera-id-4>" : "<camera-label-4>",
      ...
    },
    ...
  }
  ```

  The 'camera-label' field here helps DeviceAsWebcam label these physical camera
  ids in the UI elements of the service. The mapping containing physical camera
  ids is in order of preference of the physical streams that must be used by
  DeviceAsWebcam service. Available camera-labels are:

  - `UW`: Ultra-wide
  - `W`: Wide
  - `T`: Telephoto
  - `S`: Standard
  - `O`: Other

  For example, if a vendor would like to advertise 2 possible physical streams
  with camera ids 3(Ultra-wide) and 4(Wide) for the back logical camera the
  mapping could be:
  ```json
  {
    "0" : {
      "3" : "UW",
      "4" : "W"
    }
  }
  ```

- `physical_camera_zoom_ratio_ranges.json`:

  This file contains the mapping of physical cameras to their zoom ratio ranges.
  When provided, DeviceAsWebcam will use this zoom ratio range instead of that
  retrieved from camera characteristics. This is useful when the zoom ratio of the
  physical camera listed in `physical_camera_mapping.json` is different from the
  logical camera it belongs to. Providing this mapping is optional, and only used
  if `physical_camera_mapping,json` contains a corresponding entry. The format of
  the mapping is:

  ```json
  {
    "<logical-camera-id-1>" : {
      "<physical-camera-id-1>" : ["<zoom-ratio-range-lower-1>", "<zoom-ratio-range-upper-1>"],
      "<physical-camera-id-2>" : ["<zoom-ratio-range-lower-2>", "<zoom-ratio-range-upper-2>"],
      ...
    },
    "<logical-camera-id-2>" : {
      "<physical-camera-id-3>" : ["<zoom-ratio-range-lower-3>", "<zoom-ratio-range-upper-3>"],
      "<physical-camera-id-4>" : ["<zoom-ratio-range-lower-4>", "<zoom-ratio-range-upper-4>"],
      ...
    },
    ...
  }
  ```

  For example, if a vendor would like to custom 2 possible physical streams'
  supported zoom ratio ranges with camera ids 3 and 4 for the back logical
  camera the mapping could be:
  ```json
  {
    "0" : {
      "3" : ["1.0", "5.0"],
      "4" : ["1.0", "8.0"]
    }
  }
  ```

- `ignored_cameras.json`:

  By default, DeviceAsWebcam exposes all backward compatible cameras listed in
  `CameraManager#getCameraIdList()` as supported cameras to stream webcam frames
  from. `ignored_cameras.json` provides a way to ignore a predetermined set of
  cameras if they are not expected to be used with DeviceAsWebcam.

  For example, if a vendor would like to ignore camera ids 22 and 66, the ignored
  cameras array could be:

  ```json
  [
      "22",
      "66",
      ...
  ]
  ```

- `ignored_v4l2_nodes.json`:

  Linux UVC gadget driver mounts a V4L2 node that DeviceAsWebcam service
  interacts with. To determine this node, DeviceAsWebcam service looks through
  all `/dev/video*` nodes, looking for the first V4L2 node that advertises
  `V4L2_CAP_VIDEO_OUTPUT` capability.

  This will run into issues if a device has other V4L2 nodes mounted at
  `/dev/video*` with `V4L2_CAP_VIDEO_OUTPUT` capability advertised for other
  processing purposes.

  `ignored_v4l2_nodes.json` provides a way for DeviceAsWebcam service to
  ignore a predetermined set of V4L2 nodes.

  The file format is as follows:

  ```json
  [
      "/dev/video10",
      "/dev/video12",
      ...
  ]
  ```

  Note that wildcard patterns are _not_ supported in `ignored_v4l2_nodes.json`.
