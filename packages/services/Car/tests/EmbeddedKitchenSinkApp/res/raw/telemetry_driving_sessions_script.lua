KITCHENSINK_PROCESS_NAME = 'com.google.android.car.kitchensink'
MIN_NETSTATS_DATA_RECEIVED = 3


function onWifiStatsForDrivingSessions(published_data, state)
    if state['received wifi'] == nil then
        state['received wifi'] = 0
    end
    state['received wifi'] = state['received wifi'] + 1
    local session_id = published_data['session.sessionId']
    for i = 1, published_data['conn.size'] do
        if string.match(published_data['conn.packages'][i], KITCHENSINK_PROCESS_NAME) then
            local key = 'kitchensink_traffic_in_session_' .. session_id .. '_' .. published_data['session.createdAtMillis']
            state[key] = published_data['conn.rxBytes'][i] + published_data['conn.txBytes'][i]
            break
        end
    end
    if state['received wifi'] >= MIN_NETSTATS_DATA_RECEIVED then
        on_script_finished(state)
    else
        on_success(state)
    end
end