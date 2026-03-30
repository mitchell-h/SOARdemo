// SOAR Command Center - Frontend SPA
// All API calls go to the Javalin backend (same origin) which proxies to downstream services.

const API = '';  // same origin

let currentTab = 'dashboard';
let investigateTarget = null;  // alert being investigated
let decisionTarget = null;     // wfRunId for analyst decision
let dashInterval = null;
let alertsCache = {};          // data cache for modals

// ========== TAB NAVIGATION ==========

function showTab(tab) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    document.getElementById('tab-' + tab).classList.add('active');
    document.getElementById('nav-' + tab).classList.add('active');
    currentTab = tab;

    if (tab === 'alerts')     loadAlerts();
    if (tab === 'logs')       { /* wait for user search */ }
    if (tab === 'accounts')   searchAccounts();
    if (tab === 'cases')      loadCases();
    if (tab === 'workflows')  loadWorkflows();
    if (tab === 'dashboard')  refreshDashboard();
}

// ========== DASHBOARD ==========

async function refreshDashboard() {
    document.getElementById('last-updated-dash').textContent = 'Updated ' + new Date().toLocaleTimeString();

    // Stat: open alerts
    try {
        const alerts = await fetch(API + '/api/alerts?status=OPEN').then(r => r.json());
        document.getElementById('stat-open-alerts').textContent = alerts.length;
        document.getElementById('badge-alerts').textContent = alerts.length;
        const critCount = alerts.filter(a => a.severity === 'CRITICAL').length;
        document.getElementById('stat-alerts-severity').textContent = critCount + ' critical';
    } catch(e) { document.getElementById('stat-open-alerts').textContent = '?'; }

    // Stat: log count
    try {
        const cnt = await fetch(API + '/api/logs/count').then(r => r.json());
        document.getElementById('stat-log-count').textContent = cnt.count?.toLocaleString() || '?';
    } catch(e) {}

    // Stat: frozen accounts
    try {
        const frozen = await fetch(API + '/api/accounts?frozen=true&limit=5000').then(r => r.json());
        document.getElementById('stat-frozen').textContent = frozen.length;
    } catch(e) {}

    // Stat: active WfRuns
    try {
        const wfs = await fetch(API + '/api/workflows').then(r => r.json());
        const running = wfs.filter(w => w.status === 'RUNNING').length;
        document.getElementById('stat-wf-running').textContent = running;
        document.getElementById('badge-workflows').textContent = running;
    } catch(e) {}

    // Recent critical alerts table
    try {
        const alerts = await fetch(API + '/api/alerts?severity=CRITICAL&status=OPEN').then(r => r.json());
        const top = alerts.slice(0, 8);
        document.getElementById('dash-alerts-table').innerHTML = top.length
            ? renderAlertsTable(top, false)
            : '<div class="empty-state">No critical open alerts</div>';
    } catch(e) { document.getElementById('dash-alerts-table').innerHTML = '<div class="loading">Unavailable</div>'; }

    // Recent log feed (live events)
    try {
        const logs = await fetch(API + '/api/logs?limit=20').then(r => r.json());
        const feed = document.getElementById('dash-log-feed');
        feed.innerHTML = '';
        logs.slice(0, 15).forEach(l => {
            const div = document.createElement('div');
            div.className = 'log-line ' + (l.severity || 'low').toLowerCase();
            div.textContent = `[${l.timestamp?.slice(11,19)||'?'}] ${l.username||l.userId||'?'} | ${l.event} | ${l.ipAddress||''} | ${l.country||''}${l.amount ? ' | $'+l.amount : ''}`;
            feed.appendChild(div);
        });
    } catch(e) {}
}

// ========== ALERTS ==========

async function loadAlerts() {
    const status   = document.getElementById('alert-status-filter')?.value || '';
    const severity = document.getElementById('alert-severity-filter')?.value || '';
    const container = document.getElementById('alerts-container');
    container.innerHTML = '<div class="loading">Loading alerts...</div>';

    let url = API + '/api/alerts?';
    if (status)   url += 'status='   + status   + '&';
    if (severity) url += 'severity=' + severity + '&';

    try {
        const alerts = await fetch(url).then(r => r.json());
        alerts.forEach(a => { if(a.id) alertsCache[a.id] = a; });
        container.innerHTML = alerts.length
            ? renderAlertsTable(alerts, true)
            : '<div class="empty-state">No alerts found</div>';
    } catch(e) {
        container.innerHTML = '<div class="loading">Error loading alerts</div>';
    }
}

function renderAlertsTable(alerts, showActions) {
    let html = '<table><thead><tr>';
    html += '<th>Alert ID</th><th>Time</th><th>Username</th><th>Type</th><th>Severity</th><th>Score</th><th>Status</th>';
    if (showActions) html += '<th>Actions</th>';
    html += '</tr></thead><tbody>';
    for (const a of alerts) {
        html += '<tr>';
        html += `<td class="monospace">${a.id || '-'}</td>`;
        html += `<td>${fmtTime(a.timestamp)}</td>`;
        html += `<td class="monospace">${a.username || a.userId || '-'}</td>`;
        html += `<td>${a.alertType || '-'}</td>`;
        html += `<td>${pill(a.severity)}</td>`;
        html += `<td>${a.fraudScore != null ? (a.fraudScore * 100).toFixed(0) + '%' : '-'}</td>`;
        html += `<td>${pill(a.status)}</td>`;
        if (showActions) {
            html += `<td style="display:flex;gap:6px;flex-wrap:wrap">`;
            if (a.status === 'OPEN') {
                html += `<button class="btn btn-sm btn-primary" onclick="openInvestigateModalById('${a.id}')">Investigate</button>`;
            }
            if (a.wfRunId) {
                html += `<button class="btn btn-sm btn-secondary" onclick="openDecisionModal('${a.wfRunId}')">Decide</button>`;
            }
            html += `<button class="btn btn-sm btn-ghost" onclick="closeAlert('${a.id}')">Close</button>`;
            html += '</td>';
        }
        html += '</tr>';
    }
    html += '</tbody></table>';
    return html;
}

function openInvestigateModalById(id) {
    investigateTarget = alertsCache[id];
    if (!investigateTarget) return;
    document.getElementById('modal-alert-detail').innerHTML =
        `<div>Alert: <strong>${investigateTarget.id}</strong></div>
         <div>User: <strong>${investigateTarget.username || investigateTarget.userId}</strong></div>
         <div>Score: <strong>${investigateTarget.fraudScore != null ? (investigateTarget.fraudScore * 100).toFixed(0) + '%' : 'N/A'}</strong></div>`;
    document.getElementById('investigate-modal').classList.remove('hidden');
}

async function launchInvestigation() {
    if (!investigateTarget) return;
    const btn = document.getElementById('btn-launch-investigation');
    btn.disabled = true; btn.textContent = 'Launching...';
    try {
        const resp = await fetch(API + '/api/workflows/investigate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                alertId:    investigateTarget.id,
                username:   investigateTarget.username || investigateTarget.userId,
                fraudScore: investigateTarget.fraudScore || 0
            })
        }).then(r => r.json());

        // Update alert status
        await fetch(API + '/api/alerts/' + investigateTarget.id + '/status', {
            method: 'PATCH',
            headers: {'Content-Type':'application/json'},
            body: JSON.stringify({ status: 'INVESTIGATING' })
        });

        closeModal('investigate-modal');
        alert('Investigation workflow launched!\nWfRun ID: ' + resp.wfRunId);
        loadAlerts();
    } catch(e) {
        alert('Failed to launch investigation: ' + e.message);
    } finally {
        btn.disabled = false; btn.textContent = 'Launch Workflow';
    }
}

async function openDecisionModal(wfRunId) {
    decisionTarget = wfRunId;
    document.getElementById('decision-wfrun-id').textContent = wfRunId;
    const intelDiv = document.getElementById('decision-intelligence');
    intelDiv.innerHTML = '<div class="loading">Gathering intelligence...</div>';
    intelDiv.classList.remove('hidden');
    document.getElementById('decision-modal').classList.remove('hidden');

    try {
        // Fetch runs to find the one with this ID and its variables
        const runs = await fetch(API + '/api/workflows').then(r => r.json());
        const run = runs.find(r => r.id === wfRunId);
        
        if (run && run.variables) {
            renderIntelligence(run.variables);
        } else {
            intelDiv.innerHTML = '<div class="modal-hint">Intelligence gathering in progress or unavailable for this run.</div>';
        }
    } catch(e) {
        intelDiv.innerHTML = '<div class="modal-hint">Error fetching intelligence.</div>';
    }
}

function renderIntelligence(vars) {
    const intelDiv = document.getElementById('decision-intelligence');
    let html = '';

    // 1. Fraud Score
    const score = vars.fraudScore || 0;
    const scoreClass = score > 0.6 ? 'high' : (score > 0.35 ? 'med' : '');
    html += `<div class="intel-section">
        <div class="intel-header">Risk Intelligence</div>
        <div class="intel-score ${scoreClass}">${(score * 100).toFixed(1)}% <span style="font-size:11px; font-weight:400; color:var(--text-dim)">fraud probability</span></div>
    </div>`;

    // 2. Account Info
    if (vars.accountData) {
        try {
            const acc = JSON.parse(vars.accountData);
            html += `<div class="intel-section">
                <div class="intel-header">Account Context</div>
                <div class="intel-grid">
                    <div class="intel-item"><span class="intel-label">Balance</span><span class="intel-value">$${acc.balance?.toFixed(2)||'0.00'}</span></div>
                    <div class="intel-item"><span class="intel-label">Home Country</span><span class="intel-value">${acc.previousCountryOfOrigin||'Unknown'}</span></div>
                    <div class="intel-item"><span class="intel-label">Address</span><span class="intel-value" style="font-size:9px">${acc.address||'-'}</span></div>
                    <div class="intel-item"><span class="intel-label">Status</span><span class="intel-value">${acc.frozen ? 'FROZEN' : 'ACTIVE'}</span></div>
                </div>
            </div>`;
        } catch(e) {}
    }

    // 3. Recent Logs
    if (vars.userLogs) {
        try {
            const logs = JSON.parse(vars.userLogs);
            html += `<div class="intel-section">
                <div class="intel-header">Recent Activity Timeline</div>
                <div class="intel-logs">`;
            logs.slice(0, 5).forEach(l => {
                html += `[${l.timestamp?.slice(11,16)}] ${l.event} - ${l.status}\n`;
            });
            html += `</div></div>`;
        } catch(e) {}
    }

    intelDiv.innerHTML = html;
}

async function sendDecision(decision) {
    if (!decisionTarget) return;
    try {
        const resp = await fetch(API + '/api/workflows/analyst-decision', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ wfRunId: decisionTarget, decision })
        }).then(r => r.json());
        closeModal('decision-modal');
        alert(resp.result);
        loadAlerts();
    } catch(e) {
        alert('Failed to send decision: ' + e.message);
    }
}

async function closeAlert(id) {
    await fetch(API + '/api/alerts/' + id + '/status', {
        method: 'PATCH',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ status: 'CLOSED' })
    });
    loadAlerts();
}

// ========== LOG SEARCH ==========

async function searchLogs() {
    const container = document.getElementById('logs-result');
    container.innerHTML = '<div class="loading">Searching...</div>';

    const params = new URLSearchParams();
    const username = document.getElementById('log-username').value.trim();
    const event    = document.getElementById('log-event').value;
    const severity = document.getElementById('log-severity').value;
    const country  = document.getElementById('log-country').value.trim();
    const ip       = document.getElementById('log-ip').value.trim();
    const limit    = document.getElementById('log-limit').value;

    if (username) params.set('username', username);
    if (event)    params.set('event', event);
    if (severity) params.set('severity', severity);
    if (country)  params.set('country', country.toUpperCase());
    if (ip)       params.set('ipAddress', ip);
    params.set('limit', limit);

    try {
        const logs = await fetch(API + '/api/logs?' + params.toString()).then(r => r.json());
        if (!logs.length) { container.innerHTML = '<div class="empty-state">No logs found</div>'; return; }

        let html = '<table><thead><tr><th>Time</th><th>Username</th><th>Event</th><th>IP Address</th><th>Country</th><th>Status</th><th>Severity</th><th>Amount</th></tr></thead><tbody>';
        for (const l of logs) {
            html += `<tr>
                <td class="monospace">${fmtTime(l.timestamp)}</td>
                <td class="monospace">${l.username || l.userId || '-'}</td>
                <td>${l.event || '-'}</td>
                <td class="monospace">${l.ipAddress || '-'}</td>
                <td>${l.country || '-'}</td>
                <td>${l.status || '-'}</td>
                <td>${pill(l.severity)}</td>
                <td>${l.amount != null ? '$' + l.amount.toFixed(2) + ' ' + (l.currency || '') : '-'}</td>
            </tr>`;
        }
        html += '</tbody></table>';
        container.innerHTML = html;
    } catch(e) {
        container.innerHTML = '<div class="loading">Error: ' + e.message + '</div>';
    }
}

// ========== ACCOUNTS ==========

async function searchAccounts() {
    const search = document.getElementById('acc-search')?.value?.trim() || '';
    const frozen = document.getElementById('acc-frozen-filter')?.value || '';
    const container = document.getElementById('accounts-result');
    if (!search && !frozen) { container.innerHTML = '<div class="empty-state">Enter a username or select a filter</div>'; return; }

    container.innerHTML = '<div class="loading">Searching...</div>';
    const params = new URLSearchParams({ limit: '50' });
    if (search) params.set('username', search);
    if (frozen) params.set('frozen', frozen);

    try {
        const accounts = await fetch(API + '/api/accounts?' + params).then(r => r.json());
        if (!accounts.length) { container.innerHTML = '<div class="empty-state">No accounts found</div>'; return; }

        let html = '<table><thead><tr><th>Username</th><th>Account #</th><th>Balance</th><th>Country</th><th>Status</th><th>Email</th><th>Actions</th></tr></thead><tbody>';
        for (const a of accounts) {
            html += `<tr>
                <td class="monospace">${a.username}</td>
                <td class="monospace">${a.accountNumber || '-'}</td>
                <td>$${a.balance?.toFixed(2) || '0.00'}</td>
                <td>${a.previousCountryOfOrigin || '-'}</td>
                <td>${a.frozen ? '<span class="pill pill-frozen">FROZEN</span>' : '<span class="pill pill-active">ACTIVE</span>'}</td>
                <td>${a.email || '-'}</td>
                <td style="display:flex;gap:6px">
                    ${!a.frozen
                        ? `<button class="btn btn-sm btn-danger" onclick="freezeAccount('${a.username}')">Freeze</button>`
                        : `<button class="btn btn-sm btn-success" onclick="unfreezeAccount('${a.username}')">Unfreeze</button>`}
                    <button class="btn btn-sm btn-ghost" onclick="triggerFreezeWorkflow('${a.username}')">Workflow Freeze</button>
                </td>
            </tr>`;
        }
        html += '</tbody></table>';
        container.innerHTML = html;
    } catch(e) {
        container.innerHTML = '<div class="loading">Error: ' + e.message + '</div>';
    }
}

async function freezeAccount(username) {
    await fetch(API + '/api/accounts/' + username + '/freeze', { method: 'POST' });
    searchAccounts();
}

async function unfreezeAccount(username) {
    await fetch(API + '/api/accounts/' + username + '/unfreeze', { method: 'POST' });
    searchAccounts();
}

async function triggerFreezeWorkflow(username) {
    try {
        const resp = await fetch(API + '/api/workflows/investigate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ alertId: 'manual-' + Date.now(), username, fraudScore: 0.9 })
        }).then(r => r.json());
        alert('account-freeze-workflow triggered!\nWfRun: ' + resp.wfRunId);
    } catch(e) { alert('Error: ' + e.message); }
}

// ========== CASES ==========

async function loadCases() {
    const status = document.getElementById('case-status-filter')?.value || '';
    const container = document.getElementById('cases-container');
    container.innerHTML = '<div class="loading">Loading cases...</div>';

    let url = API + '/api/cases?';
    if (status) url += 'status=' + status;

    try {
        const cases = await fetch(url).then(r => r.json());
        container.innerHTML = cases.length
            ? renderCasesTable(cases)
            : '<div class="empty-state">No cases found</div>';
    } catch(e) {
        container.innerHTML = '<div class="loading">Error loading cases</div>';
    }
}

function renderCasesTable(cases) {
    let html = '<table><thead><tr>';
    html += '<th>Case ID</th><th>Created</th><th>Username</th><th>Reason</th><th>Source IP</th><th>Status</th><th>Resolution</th><th>Actions</th>';
    html += '</tr></thead><tbody>';
    for (const c of cases) {
        html += '<tr>';
        html += `<td class="monospace">${c.id}</td>`;
        html += `<td style="font-size:11px">${fmtTime(c.createdAt)}</td>`;
        html += `<td class="monospace">${c.username}</td>`;
        html += `<td>${c.reason}</td>`;
        html += `<td class="monospace">${c.sourceIp || '-'}</td>`;
        html += `<td>${pill(c.status)}</td>`;
        html += `<td>${c.resolution || '-'}</td>`;
        html += `<td>`;
        if (c.status === 'OPEN') {
            html += `<button class="btn btn-sm btn-ghost" onclick="closeCasePrompt('${c.id}')">Close Case</button>`;
        } else {
            html += '-';
        }
        html += `</td>`;
        html += '</tr>';
        if (c.details) {
            html += `<tr class="details-row"><td colspan="8" class="details-td">Details: ${c.details}</td></tr>`;
        }
    }
    html += '</tbody></table>';
    return html;
}

async function closeCasePrompt(id) {
    const resolution = prompt("Enter resolution for case " + id, "RESOLVED_BY_ANALYST");
    if (!resolution) return;
    try {
        await fetch(API + '/api/cases/' + id + '/close', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ resolution })
        });
        loadCases();
    } catch(e) { alert('Error closing case: ' + e.message); }
}

// ========== WORKFLOWS ==========

async function loadWorkflows() {
    const container = document.getElementById('workflows-container');
    container.innerHTML = '<div class="loading">Fetching LittleHorse WfRuns...</div>';
    try {
        const runs = await fetch(API + '/api/workflows').then(r => r.json());
        document.getElementById('badge-workflows').textContent = runs.filter(w => w.status === 'RUNNING').length;

        if (!runs.length) { container.innerHTML = '<div class="empty-state">No workflow runs found yet. Trigger a fraud event or launch an investigation.</div>'; return; }

        let html = '<table><thead><tr><th>WfRun ID</th><th>Workflow</th><th>Status</th><th>Started</th><th>Actions</th></tr></thead><tbody>';
        for (const w of runs) {
            const wfClass = w.wfSpecName.includes('fraud') ? 'fraud'
                : w.wfSpecName.includes('invest') ? 'invest'
                : w.wfSpecName.includes('verify') ? 'verify' : 'freeze';
            html += `<tr>
                <td class="monospace" style="font-size:11px">${w.id.slice(0,20)}...</td>
                <td><span class="wf-spec-badge ${wfClass}">${w.wfSpecName}</span></td>
                <td>${pill(w.status)}</td>
                <td>${fmtTime(w.startTime)}</td>
                <td>
                    ${w.status === 'RUNNING' && w.wfSpecName === 'alert-investigation-workflow'
                        ? `<button class="btn btn-sm btn-secondary" onclick="openDecisionModal('${w.id}')">Send Decision</button>`
                        : '-'}
                </td>
            </tr>`;
        }
        html += '</tbody></table>';
        container.innerHTML = html;
    } catch(e) {
        container.innerHTML = '<div class="empty-state">LittleHorse not connected (workflows unavailable)</div>';
    }
}

// ========== LH STATUS ==========

async function checkLhStatus() {
    const dot  = document.getElementById('lh-status-dot');
    const text = document.getElementById('lh-status-text');
    try {
        const wfs = await fetch(API + '/api/workflows').then(r => { if(!r.ok) throw new Error(); return r.json(); });
        dot.className = 'status-dot connected';
        text.textContent = 'LH Connected';
    } catch(e) {
        dot.className = 'status-dot error';
        text.textContent = 'LH Offline';
    }
}

// ========== HELPERS ==========

function closeModal(id) { document.getElementById(id).classList.add('hidden'); }

function pill(value) {
    if (!value) return '-';
    const cls = 'pill-' + value.toLowerCase().replace(/_/g, '-');
    return `<span class="pill ${cls}">${value}</span>`;
}

function fmtTime(ts) {
    if (!ts) return '-';
    try {
        const d = new Date(ts);
        return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
    } catch(e) { return ts; }
}

// ========== INIT ==========

document.addEventListener('DOMContentLoaded', () => {
    refreshDashboard();
    checkLhStatus();
    // Auto-refresh dashboard every 10 seconds
    dashInterval = setInterval(() => {
        if (currentTab === 'dashboard') refreshDashboard();
    }, 10000);
    // LH status check every 15s
    setInterval(checkLhStatus, 15000);
});
