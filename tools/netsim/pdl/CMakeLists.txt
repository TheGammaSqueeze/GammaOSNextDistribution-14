message(STATUS "PDL netsim packets module.")

get_property(pdlc_EXECUTABLE TARGET pdlc PROPERTY EXECUTABLE_PATH)

pdl_gen(
  NAME
  NetlinkPackets_rs
  INPUT
  ${CMAKE_CURRENT_LIST_DIR}/netlink.pdl
  OUTPUT
  netlink_packets.rs
  LANG
  rust)

pdl_gen(
  NAME
  Mac80211HwsimPackets_rs
  INPUT
  ${CMAKE_CURRENT_LIST_DIR}/mac80211_hwsim.pdl
  OUTPUT
  mac80211_hwsim_packets.rs
  LANG
  rust)

pdl_gen(
  NAME
  Ieee80211Packets_rs
  INPUT
  ${CMAKE_CURRENT_LIST_DIR}/ieee80211.pdl
  OUTPUT
  ieee80211_packets.rs
  LANG
  rust)
