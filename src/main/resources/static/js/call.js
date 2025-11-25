// Call Page Logic with WebSocket Real-time Updates

let stompClient = null;
let currentComPort = null;

/**
 * Initialize WebSocket connection
 */
function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    stompClient.connect({}, function(frame) {
        console.log('✅ WebSocket Connected:', frame);
        
        // Subscribe to call status updates
        stompClient.subscribe('/topic/call-status', function(message) {
            const status = JSON.parse(message.body);
            console.log('📡 Received call status:', status);
            updateCallStatusDisplay(status);
        });
    }, function(error) {
        console.error('❌ WebSocket Error:', error);
        // Retry connection after 5 seconds
        setTimeout(connectWebSocket, 5000);
    });
}

/**
 * Update call status display
 */
function updateCallStatusDisplay(status) {
    // Show call status panel
    document.getElementById('callStatus').style.display = 'block';
    
    // Update call state
    const callStateEl = document.getElementById('callState');
    callStateEl.textContent = status.callState;
    callStateEl.className = 'status-badge ' + getCallStateClass(status.callState);
    
    // Update recording state
    const recordingStateEl = document.getElementById('recordingState');
    recordingStateEl.textContent = status.recordingState;
    recordingStateEl.className = 'status-badge ' + getRecordingStateClass(status.recordingState);
    
    // Update target number
    document.getElementById('targetNumber').textContent = status.targetNumber || '-';
    
    // Update duration
    document.getElementById('duration').textContent = status.durationSeconds + 's';
    
    // If call ended, reload history and hide status
    if (status.callState === 'ENDED') {
        setTimeout(() => {
            document.getElementById('callStatus').style.display = 'none';
            loadCallHistory();
        }, 3000);
    }
}

/**
 * Get CSS class for call state
 */
function getCallStateClass(state) {
    switch(state) {
        case 'IDLE': return 'status-idle';
        case 'DIALING': return 'status-recording';
        case 'RINGING': return 'status-recording';
        case 'ACTIVE': return 'status-on';
        case 'INCOMING': return 'status-recording';
        case 'ENDED': return 'status-off';
        default: return 'status-idle';
    }
}

/**
 * Get CSS class for recording state
 */
function getRecordingStateClass(state) {
    switch(state) {
        case 'IDLE': return 'status-idle';
        case 'RECORDING': return 'status-recording';
        case 'DOWNLOADING': return 'status-recording';
        case 'COMPLETED': return 'status-on';
        case 'FAILED': return 'status-off';
        default: return 'status-idle';
    }
}

/**
 * Make a call - TỰ ĐỘNG xử lý toàn bộ flow
 */
async function makeCall() {
    const comPort = document.getElementById('comPort').value;
    const phoneNumber = document.getElementById('phoneNumber').value;
    const enableRecording = document.getElementById('enableRecording').checked;
    const maxDuration = document.getElementById('maxDuration').value;
    
    if (!comPort) {
        alert('Vui lòng chọn COM Port');
        return;
    }
    
    if (!phoneNumber) {
        alert('Vui lòng nhập số điện thoại');
        return;
    }
    
    try {
        const response = await fetch(`/api/call/start?comPort=${encodeURIComponent(comPort)}&phoneNumber=${encodeURIComponent(phoneNumber)}&record=${enableRecording}&maxDurationSeconds=${maxDuration}`, {
            method: 'POST'
        });
        
        const result = await response.json();
        
        if (result.success) {
            currentComPort = comPort;
            console.log('✅ Call started:', result);
            // WebSocket sẽ tự động update status
        } else {
            alert('Lỗi: ' + result.message);
        }
    } catch (error) {
        console.error('Error making call:', error);
        alert('Lỗi khi gọi điện: ' + error.message);
    }
}

/**
 * End call
 */
async function endCall() {
    if (!currentComPort) {
        alert('Không có cuộc gọi đang hoạt động');
        return;
    }
    
    try {
        const response = await fetch(`/api/call/end?comPort=${encodeURIComponent(currentComPort)}`, {
            method: 'POST'
        });
        
        const result = await response.json();
        
        if (result.success) {
            console.log('✅ Call ended');
            currentComPort = null;
        } else {
            alert('Lỗi: ' + result.message);
        }
    } catch (error) {
        console.error('Error ending call:', error);
        alert('Lỗi khi kết thúc cuộc gọi: ' + error.message);
    }
}

/**
 * Load call history
 */
async function loadCallHistory() {
    try {
        const response = await fetch('/api/modem-call/call-history?page=1&size=50');
        const result = await response.json();
        
        if (result.success && result.calls && result.calls.length > 0) {
            renderCallHistory(result.calls);
        } else {
            document.getElementById('callHistoryList').innerHTML = 
                '<tr><td colspan="6" style="text-align: center; padding: 3rem; color: #808080;">Chưa có lịch sử cuộc gọi</td></tr>';
        }
    } catch (error) {
        console.error('Error loading call history:', error);
    }
}

/**
 * Render call history table
 */
function renderCallHistory(calls) {
    const html = calls.map(call => {
        const startTime = call.startTime ? new Date(call.startTime).toLocaleString('vi-VN') : '-';
        const duration = call.durationSeconds || 0;
        const recordingFile = call.recordingFileName;
        
        return `
            <tr>
                <td>${startTime}</td>
                <td><strong>${call.comPort}</strong></td>
                <td>${call.targetNumber || '-'}</td>
                <td>${duration}s</td>
                <td><span class="status-badge ${getCallEndStateClass(call.callState)}">${call.callState}</span></td>
                <td>
                    ${recordingFile ? 
                        `<button class="classic-button" onclick="openRecording('${recordingFile}')" style="padding: 0.3rem 0.6rem; font-size: 11px;">
                            🎧 Mở file
                        </button>` : 
                        '<span style="color: #999;">Không có</span>'}
                </td>
            </tr>
        `;
    }).join('');
    
    document.getElementById('callHistoryList').innerHTML = html;
}

/**
 * Get CSS class for call end state
 */
function getCallEndStateClass(state) {
    if (state === 'COMPLETED' || state === 'AUTO_HANGUP') return 'status-on';
    if (state === 'MANUAL_HANGUP') return 'status-idle';
    return 'status-off';
}

/**
 * Open recording file
 */
function openRecording(fileName) {
    const url = `/api/call/recording/download?comPort=${encodeURIComponent(currentComPort || 'unknown')}&fileName=${encodeURIComponent(fileName)}`;
    window.open(url, '_blank');
}

/**
 * Load available COM ports
 */
async function loadComPorts() {
    try {
        const response = await fetch('/api/modem-call/scan-ports');
        const result = await response.json();
        
        if (result.success && result.ports && result.ports.length > 0) {
            const select = document.getElementById('comPort');
            select.innerHTML = '<option value="">-- Chọn COM Port --</option>';
            
            result.ports.forEach(port => {
                const option = document.createElement('option');
                option.value = port.comPort;
                option.textContent = `${port.comPort} - ${port.phoneNumber || 'N/A'} (${port.carrier || 'Unknown'})`;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading COM ports:', error);
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    console.log('📞 Call page loaded');
    
    // Connect WebSocket
    connectWebSocket();
    
    // Load COM ports
    loadComPorts();
    
    // Load call history
    loadCallHistory();
    
    // Auto-refresh call history every 30 seconds
    setInterval(loadCallHistory, 30000);
});
