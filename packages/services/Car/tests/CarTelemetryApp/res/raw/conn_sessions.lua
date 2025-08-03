-- Accumulates txBytes and rxBytes per package group, includes session data
function onConnectivityDataWithSession(published_data, state)
    log("entering onConnectivityDataWithSession");
    res = {}
    -- Save session data which unlike normal fields of published_data is not array type and exists
    -- for every session
    -- sessionId: integer that starts from 1 and increases for each new session to uniquely
    -- identify each session. It's reset to 1 upon reboot.
    res['sessionId'] = published_data['session.sessionId']
    -- sessionState: either 1 (STATE_EXIT_DRIVING_SESSION) meaning currently outside a session or
    -- 2 (STATE_ENTER_DRIVING_SESSION) meaning currently in a session (device is on). For
    -- connectivity this is always 1 because data is calculated at session end.
    res['sessionState'] = published_data['session.sessionState']
    -- createdAtSinceBootMillis: milliseconds since boot
    res['createdAtSinceBootMillis'] = published_data['session.createdAtSinceBootMillis']
    -- createdAtMillis: current time in milliseconds unix time
    res['createdAtMillis'] = published_data['session.createdAtMillis']
    -- bootReason: last boot reason
    res['bootReason'] = published_data['session.bootReason']
    res['bootCount'] = published_data['session.bootCount']

    -- If we don't have metrics data then exit with only sessions data right now
    if published_data['conn.packages'] == nil then
        -- on_metrics_report(r) sends r as finished result table
        -- on_metrics_report(r, s) sends r as finished result table while also sending
        -- s as intermediate result that will be received next time as 'state' param
        log("packages is nil, only sessions data available.")
        on_metrics_report(res)
        do return end
    end

    -- Accumulate rxBytes and txBytes for each package group
    rx = {}
    tx = {}
    uids = {}
    -- Go through the arrays (all same length as packages array) and accumulate rx and tx for each
    -- package name group. In the packages array an entry can be a conglomeration of multiple package
    -- names (eg. ["com.example.abc", "com.example.cdf,com.vending.xyz"] the 2nd entry has 2
    -- package names because it's not distinguishable which made the data transfers)
    for i, ps in ipairs(published_data['conn.packages']) do
        if rx[ps] == nil then
            rx[ps] = 0
            tx[ps] = 0
        end
        -- For each package group accumulate the rx and tx separately
        rx[ps] = rx[ps] + published_data['conn.rxBytes'][i]
        tx[ps] = tx[ps] + published_data['conn.txBytes'][i]
        uids[ps] = published_data['conn.uid'][i]
    end
    -- For each package group name combine rx and tx into one string for print
    for p, v in pairs(rx) do
        res[p] = 'rx: ' .. rx[p] .. ', tx: ' .. tx[p] .. ', uid: ' .. uids[p]
    end
    on_metrics_report(res)
end
