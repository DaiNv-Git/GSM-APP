// Scan Page Logic

let scannedSims = [];

/**
 * Scan ports with SSE progressive loading
 */
async function scanPorts() {
    const simList = document.getElementById('simList');
    const statusText = document.getElementById('statusText');
    const simCount = document.getElementById('simCount');
    
    // Reset state
    simList.innerHTML = '<div style="text-align: center; padding: 2rem; color: #808080; font-size: 14px;">🔍 Scanning ports...</div>';
    statusText.textContent = 'Scanning...';
    
    scannedSims = [];
    let scannedCount = 0;
    const startTime = Date.now();

    try {
        const eventSource = new EventSource('/api/modem-call/scan-ports-stream');

        eventSource.addEventListener('scan-start', (e) => {
            const data = JSON.parse(e.data);
            simList.innerHTML = '<div style="text-align: center; padding: 1.5rem; color: #0066cc; font-size: 14px;">⏳ ' + data.message + '</div>';
        });

        eventSource.addEventListener('port-found', (e) => {
            const portInfo = JSON.parse(e.data);
            scannedSims.push(portInfo);
            scannedCount++;
            
            // Update status
            statusText.textContent = `Scanning... Found ${scannedCount} SIM(s)`;
            simCount.textContent = `SIMs: ${scannedCount}`;
            
            // Render progressively
            renderSimsGrouped(scannedSims);
        });

        eventSource.addEventListener('scan-complete', (e) => {
            const data = JSON.parse(e.data);
            const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
            
            statusText.textContent = `✅ Scan complete: ${data.totalPorts} SIM(s) in ${elapsed}s`;
            simCount.textContent = `SIMs: ${data.totalPorts}`;
            
            if (data.totalPorts === 0) {
                simList.innerHTML = '<div style="text-align: center; padding: 3rem; color: #808080; font-size: 14px;">❌ No SIM cards found</div>';
            }
            
            eventSource.close();
        });

        eventSource.addEventListener('scan-error', (e) => {
            const data = JSON.parse(e.data);
            statusText.textContent = '❌ Error: ' + data.error;
            simList.innerHTML = '<div style="text-align: center; padding: 2rem; color: red; font-size: 14px;">Error scanning ports</div>';
            eventSource.close();
        });

        eventSource.onerror = (error) => {
            console.error('SSE Error:', error);
            statusText.textContent = 'Connection error';
            eventSource.close();
        };

    } catch (error) {
        console.error('Scan error:', error);
        statusText.textContent = 'Error scanning';
        simList.innerHTML = '<div style="text-align: center; padding: 2rem; color: red; font-size: 14px;">Error</div>';
    }
}

/**
 * Render SIMs grouped by carrier
 */
function renderSimsGrouped(sims) {
    // Group by carrier
    const grouped = {};
    sims.forEach(sim => {
        const carrier = sim.carrier || 'Unknown';
        if (!grouped[carrier]) {
            grouped[carrier] = [];
        }
        grouped[carrier].push(sim);
    });

    // Sort carriers (prioritize Japanese carriers)
    const carrierPriority = ['DOCOMO', 'AU', 'SOFTBANK', 'RAKUTEN', 'YMOBILE', 'VIETTEL', 'VINAPHONE', 'MOBIFONE'];
    const sortedCarriers = Object.keys(grouped).sort((a, b) => {
        const aIndex = carrierPriority.findIndex(c => a.toUpperCase().includes(c));
        const bIndex = carrierPriority.findIndex(c => b.toUpperCase().includes(c));
        if (aIndex !== -1 && bIndex !== -1) return aIndex - bIndex;
        if (aIndex !== -1) return -1;
        if (bIndex !== -1) return 1;
        return a.localeCompare(b);
    });

    // Render
    const html = sortedCarriers.map(carrier => {
        const carrierSims = grouped[carrier];
        const carrierIcon = getCarrierIcon(carrier);
        const simCards = carrierSims.map((sim, idx) => {
            const globalIndex = sims.indexOf(sim);
            const carrierClass = getCarrierClass(sim.carrier);
            const signalQuality = parseSignalStrength(sim.signalStrength);
            const signalClass = getSignalClass(signalQuality);
            
            return `
                <div class="sim-card ${carrierClass}" onclick="document.getElementById('sim_${globalIndex}').click()">
                    <input type="checkbox" id="sim_${globalIndex}" onchange="selectSim('${sim.comPort}', this)" onclick="event.stopPropagation()">
                    <div class="sim-card-content">
                        <div class="sim-card-header">
                            <span>💳 ${sim.comPort}</span>
                        </div>
                        <div class="sim-card-info">
                            <div class="sim-info-row">
                                <span class="sim-info-label">📞 Phone:</span>
                                <span class="sim-info-value">${sim.phoneNumber || 'N/A'}</span>
                            </div>
                            <div class="sim-info-row">
                                <span class="sim-info-label">📡 Carrier:</span>
                                <span class="sim-info-value">${carrierIcon} ${sim.carrier || 'N/A'}</span>
                            </div>
                            <div class="sim-info-row">
                                <span class="sim-info-label">📶 Signal:</span>
                                <span class="signal-indicator ${signalClass}">${signalQuality || 'N/A'}</span>
                            </div>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        return `
            <div class="carrier-group">
                <div class="carrier-header">
                    <span class="carrier-name">
                        <span class="carrier-icon">${carrierIcon}</span>
                        ${carrier}
                    </span>
                    <span class="carrier-count">${carrierSims.length} SIM(s)</span>
                </div>
                ${simCards}
            </div>
        `;
    }).join('');

    document.getElementById('simList').innerHTML = html || '<div style="text-align: center; padding: 3rem; color: #808080; font-size: 14px;">No SIMs found</div>';
}

// Auto-scan on page load
setTimeout(() => {
    console.log('🔍 Auto-scanning ports on startup...');
    scanPorts();
}, 1000);
