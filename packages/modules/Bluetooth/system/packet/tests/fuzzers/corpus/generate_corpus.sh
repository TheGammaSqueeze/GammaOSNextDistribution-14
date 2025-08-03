mkdir avrcp_browse_packet_corpus
mkdir change_path_req_corpus
mkdir get_capabilities_req_corpus
mkdir get_item_attributes_req_corpus
mkdir get_play_status_req_corpus
mkdir get_total_number_of_items_req_corpus
mkdir pass_through_packet_corpus
mkdir play_item_packet_corpus
mkdir register_notification_packet_corpus
mkdir set_absolute_volume_packet_corpus
mkdir set_addressed_player_packet_corpus
mkdir set_browsed_player_req_corpus
mkdir vendor_packet_corpus
mkdir avrcp_packet_corpus
mkdir reject_packet_corpus
#New ones
mkdir change_path_res_corpus
mkdir get_element_attributes_req_packet_corpus
mkdir get_element_attributes_res_packet_corpus
mkdir get_folder_items_res_corpus
mkdir get_folder_items_req_corpus
mkdir get_item_attributes_res_corpus
mkdir get_play_status_res_corpus
mkdir get_total_number_of_items_res_corpus
mkdir set_browsed_player_res_corpus

echo -n -e '\x71,\x00,\x0a,\x00,\x00,\x00,\x00,\x00,\x00,\x00,\x00,\x03,\x00' > avrcp_browse_packet_corpus/validpacket
echo -n -e '\x72\x00\x0b\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x02' > change_path_packet_corpus/validpacket
echo -n -e '\x72\x00\x0b\x00\x00\x01\x00\x00\x00\x00\x00\x00\x00\x02' > get_capabilities_req_corpus/validpacket
echo -n -e '\x73\x00\x28\x03\x00\x00\x00\x00\x00\x00\x00\x01\x00\x00\x07\x00\x00\x00\x01\x00\x00\x00\x02\x00\x00\x00\x03\x00\x00\x00\x04\x00\x00\x00\x05\x00\x00\x00\x06\x00\x00\x00\x07' > get_item_attributes_req_corpus/validpacket 
echo -n -e '\x01\x48\x00\x00\x19\x58\x30\x00\x00\x00' > get_play_status_req_corpus/validpacket
echo -n -e '\x75\x00\x00' > get_total_number_of_items_req_corpus/validpacket
echo -n -e '\x00\x48\x7c\x44\x00' > pass_through_packet_corpus/validpacket
echo -n -e '\x00\x48\x00\x00\x19\x58\x74\x00\x00\x0b\x03\x00\x00\x00\x00\x00\x00\x00\x03\x00\x00' > play_item_req_corpus/validpacket
echo -n -e '\x03\x48\x00\x00\x19\x58\x31\x00\x00\x04\x00\x00\x00\x00' > register_notification_packet_corpus/validpacket
echo -n -e '\x00\x48\x00\x00\x19\x58\x50\x00\x00\x01\x48' > set_absolute_volume_packet_corpus/validpacket
echo -n -e '\x00\x48\x00\x00\x19\x58\x60\x00\x00\x00' > set_addressed_player_packet_corpus/validpacket
echo -n -e '\x70\x00\x02\x00\x02' > set_browsed_player_req_corpus/validpacket
echo -n -e '\x01\x48\x00\x00\x19\x58\x10\x00\x00\x01' > vendor_packet_corpus/validpacket
echo -n -e '\x01\x48\x00\x00\x19\x58\x10\x00\x00\x00' > avrcp_packet_corpus/validpacket

#new ones 

echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_element_attributes_req_packet_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_element_attributes_res_packet_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_folder_items_res_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_folder_items_req_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_item_attributes_res_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_play_status_res_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > get_total_number_of_items_res_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > set_browsed_player_res_corpus/validpacket
echo -n -e '0x01, 0x48, 0x00, 0x00, 0x19, 0x58, 0x20, 0x00, 0x00, 0x00' > change_path_res_corpus/validpacket