// Scan Page Logic

let scannedSims = [];
let isScanning = false;

/**
 * Reload scan - wrapper for scanPorts with animation
 */
async function reloadScan() {
    console.log('🔄 Reloading scan...');
    await scanPorts();
}

/**
 * Set scanning state
 */
function setScanningState(scanning) {
    isScanning = scanning;
    const scanBtn = document.getElementById('scanBtn');
    const reloadBtn = document.getElementById('reloadBtn');
    
    if (scanBtn) {
        scanBtn.disabled = scanning;
        scanBtn.style.opacity = scanning ? '0.6' : '1';
        scanBtn.style.cursor = scanning ? 'not-allowed' : 'pointer';
        scanBtn.innerHTML = scanning ? '⏳ Scanning...' : '🔍 Scan Ports';
    }
    
    if (reloadBtn) {
        reloadBtn.disabled = scanning;
        reloadBtn.style.opacity = scanning ? '0.6' : '1';
        reloadBtn.style.cursor = scanning ? 'not-allowed' : 'pointer';
        reloadBtn.innerHTML = scanning ? '⏳ Loading...' : '🔄 Reload';
    }
}

/**
 * Scan ports - Simple version using regular API
 */
async function scanPorts() {
    if (isScanning) {
        console.log('⚠️ Scan already in progress');
        return;
    }
    
    const simList = document.getElementById('simList');
    const statusText = document.getElementById('statusText');
    const simCount = document.getElementById('simCount');
    
    // Set scanning state
    setScanningState(true);
    
    // Reset state
    simList.innerHTML = '<tr class="empty-state"><td colspan="10" style="text-align: center; padding: 2rem; color: #808080; font-size: 14px;">🔍 Scanning ports...</td></tr>';
    statusText.textContent = 'Scanning...';
    
    scannedSims = [];
    const startTime = Date.now();

    try {
        const response = await fetch('/api/modem-call/scan-ports');
        const result = await response.json();
        
        if (result.success && result.ports) {
            scannedSims = result.ports;
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            
            statusText.textContent = `✅ Scan complete: ${scannedSims.length} SIM(s) in ${elapsed}s`;
            simCount.textContent = `SIMs: ${scannedSims.length}`;
            
            if (scannedSims.length === 0) {
                simList.innerHTML = '<tr class="empty-state"><td colspan="10" style="text-align: center; padding: 3rem; color: #808080; font-size: 14px;">❌ No SIM cards found</td></tr>';
            } else {
                renderSimsTable(scannedSims);
            }
        } else {
            statusText.textContent = '❌ Error: ' + (result.message || 'Unknown error');
            simList.innerHTML = '<tr class="empty-state"><td colspan="10" style="text-align: center; padding: 2rem; color: red; font-size: 14px;">Error scanning ports</td></tr>';
        }
    } catch (error) {
        console.error('Scan error:', error);
        statusText.textContent = '❌ Error scanning';
        simList.innerHTML = '<tr class="empty-state"><td colspan="10" style="text-align: center; padding: 2rem; color: red; font-size: 14px;">Error: ' + error.message + '</td></tr>';
    } finally {
        // Reset scanning state
        setScanningState(false);
    }
}

/**
 * Render SIMs as table rows
 */
function renderSimsTable(sims) {
    if (!sims || sims.length === 0) {
        document.getElementById('simList').innerHTML = '<tr class="empty-state"><td colspan="10" style="text-align: center; padding: 3rem; color: #808080; font-size: 14px;">No SIMs found</td></tr>';
        return;
    }

    const html = sims.map((sim, index) => {
        const carrierClass = getCarrierClass(sim.carrier);
        const carrierIcon = getCarrierIcon(sim.carrier);
        const signalQuality = parseSignalStrength(sim.signalStrength);
        const signalClass = getSignalClass(signalQuality);
        
        // Extract signal strength in dB format
        const signalDb = extractSignalDb(sim.signalStrength);
        const dbClass = getDbClass(signalDb);
        
        // Get current timestamp
        const currentTime = new Date().toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        
        // Model name (extract from sim data or use default)
        const model = sim.model || 'Quectel EC25';
        
        return `
            <tr class="${carrierClass ? 'row-' + carrierClass.replace('carrier-', '') : ''}" onclick="toggleRowSelection(this, ${index})">
                <td>
                    <input type="checkbox" id="sim_${index}" onchange="selectSim('${sim.comPort}', this)" onclick="event.stopPropagation()">
                </td>
                <td>${model}</td>
                <td>
                    <div class="operator-cell">
                        <span class="operator-icon">${carrierIcon}</span>
                        <span>${sim.carrier || 'N/A'}</span>
                    </div>
                </td>
                <td>${sim.phoneNumber || 'N/A'}</td>
                <td><strong>${sim.comPort}</strong></td>
                <td><span class="signal-db ${dbClass}">${signalDb}</span></td>
                <td><span class="status-badge status-on">on</span></td>
                <td>${currentTime}</td>
                <td>${signalQuality || 'N/A'}</td>
                <td><span class="status-badge status-idle">${sim.status || 'Idle'}</span></td>
            </tr>
        `;
    }).join('');

    document.getElementById('simList').innerHTML = html;
}

// Auto-scan on page load
setTimeout(() => {
    console.log('🔍 Auto-scanning ports on startup...');
    scanPorts();
}, 1000);

/**
 * Extract signal strength in dB format
 */
function extractSignalDb(signalStr) {
    if (!signalStr || signalStr === 'N/A') return 'N/A';
    
    // Try to extract dB value from signal strength string
    // Example formats: "+CSQ: 25,99" or "(-73 dBm)"
    const dbMatch = signalStr.match(/-?\d+\s*dB/i);
    if (dbMatch) return dbMatch[0];
    
    // Try to extract CSQ value and convert to approximate dB
    const csqMatch = signalStr.match(/\+CSQ:\s*(\d+)/);
    if (csqMatch) {
        const csq = parseInt(csqMatch[1]);
        if (csq === 99) return 'N/A';
        // Convert CSQ to approximate dBm: -113 + (csq * 2)
        const dbm = -113 + (csq * 2);
        return `${dbm} dB`;
    }
    
    return 'N/A';
}

/**
 * Get CSS class for signal dB value
 */
function getDbClass(dbStr) {
    if (dbStr === 'N/A') return 'poor';
    
    const dbValue = parseInt(dbStr);
    if (isNaN(dbValue)) return 'poor';
    
    // Signal quality thresholds (dBm)
    if (dbValue >= -70) return 'excellent';
    if (dbValue >= -85) return 'good';
    if (dbValue >= -100) return 'fair';
    return 'poor';
}

/**
 * Toggle row selection
 */
function toggleRowSelection(row, index) {
    const checkbox = document.getElementById(`sim_${index}`);
    if (checkbox) {
        checkbox.checked = !checkbox.checked;
        checkbox.dispatchEvent(new Event('change'));
    }
}

/**
 * Toggle select all checkboxes
 */
function toggleSelectAll(selectAllCheckbox) {
    const checkboxes = document.querySelectorAll('#simList input[type="checkbox"]');
    checkboxes.forEach(cb => {
        cb.checked = selectAllCheckbox.checked;
        cb.dispatchEvent(new Event('change'));
    });
}
