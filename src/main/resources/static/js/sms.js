// SMS Page Logic

/**
 * Send SMS
 */
async function sendSms() {
    const comPort = document.getElementById('smsComPort').value;
    const targetPhone = document.getElementById('smsTargetPhone').value;
    const message = document.getElementById('smsMessage').value;

    if (!comPort) {
        alert('Please select a SIM card first from the Scan page');
        return;
    }

    if (!targetPhone || !message) {
        alert('Please enter phone number and message');
        return;
    }

    showStatus('📡 Sending SMS...', 'info');

    try {
        const response = await fetch(`/api/modem-call/send-sms?comPort=${comPort}&targetPhone=${targetPhone}&message=${encodeURIComponent(message)}`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.success) {
            showStatus('✅ SMS sent successfully!', 'success');
            clearSmsForm();
            setTimeout(() => loadSmsHistory(), 1000);
        } else {
            showStatus('❌ Failed: ' + (data.error || 'Unknown error'), 'error');
        }
    } catch (error) {
        showStatus('❌ Error: ' + error.message, 'error');
    }
}

/**
 * Load SMS History
 */
async function loadSmsHistory() {
    const grid = document.getElementById('smsDataGrid');
    grid.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 1.5rem; font-size: 13px;">Loading...</td></tr>';

    // Placeholder - would integrate with real SMS API
    setTimeout(() => {
        grid.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: #808080; font-size: 13px;">No SMS records (Feature coming soon)</td></tr>';
    }, 500);
}

/**
 * Clear SMS form
 */
function clearSmsForm() {
    document.getElementById('smsTargetPhone').value = '';
    document.getElementById('smsMessage').value = '';
}

// Load history on page load
loadSmsHistory();
