// Call Page Logic

/**
 * Make Call
 */
async function makeCall() {
    const comPort = document.getElementById('callComPort').value;
    const targetPhone = document.getElementById('callTargetPhone').value;
    const record = document.getElementById('recordCall').checked;
    const maxDuration = parseInt(document.getElementById('maxDuration').value) || 0;

    if (!comPort) {
        alert('Please select a SIM card first from the Scan page');
        return;
    }

    if (!targetPhone) {
        alert('Please enter phone number');
        return;
    }

    showStatus('📞 Initiating call...', 'info');

    try {
        const response = await fetch(`/api/modem-call/make-call?comPort=${comPort}&targetPhone=${targetPhone}&record=${record}&maxDurationSeconds=${maxDuration}`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.success) {
            showStatus('✅ Call initiated! OrderID: ' + data.orderId.substring(0, 8) + '...', 'success');
            clearCallForm();
            setTimeout(() => loadCallHistory(), 2000);
        } else {
            showStatus('❌ Failed: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (error) {
        showStatus('❌ Error: ' + error.message, 'error');
    }
}

/**
 * Load Call History
 */
async function loadCallHistory() {
    const grid = document.getElementById('callDataGrid');
    grid.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 1.5rem; font-size: 13px;">Loading...</td></tr>';

    try {
        const response = await fetch('/api/modem-call/call-history?page=1&size=100');
        const data = await response.json();

        if (data.success && data.calls && data.calls.length > 0) {
            grid.innerHTML = data.calls.map(call => `
                <tr>
                    <td>${formatTime(call.createdAt)}</td>
                    <td>${call.simPhone || '-'}</td>
                    <td>${call.fromNumber || '-'}</td>
                    <td>${call.comPort || '-'}</td>
                    <td>${call.status || '-'}</td>
                    <td>${call.recordFile ? 'Yes' : 'No'}</td>
                    <td>${calculateDuration(call.callStartTime, call.callEndTime)}</td>
                </tr>
            `).join('');
        } else {
            grid.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 2rem; color: #808080; font-size: 13px;">No call records</td></tr>';
        }
    } catch (error) {
        grid.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 2rem; color: red; font-size: 13px;">Error loading data</td></tr>';
    }
}

/**
 * Clear Call form
 */
function clearCallForm() {
    document.getElementById('callTargetPhone').value = '';
    document.getElementById('recordCall').checked = false;
    document.getElementById('maxDuration').value = '30';
}

// Load history on page load
loadCallHistory();
