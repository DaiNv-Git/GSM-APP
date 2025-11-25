// Common JavaScript utilities for GSM Manager

// ============================================
// Japanese Carrier Detection & Icons
// ============================================

const JAPANESE_CARRIERS = {
    'docomo': { name: 'NTT Docomo', icon: '🟠', color: '#ff6600' },
    'au': { name: 'AU', icon: '🟡', color: '#ff9900' },
    'softbank': { name: 'SoftBank', icon: '⚪', color: '#888888' },
    'rakuten': { name: 'Rakuten Mobile', icon: '🔴', color: '#bf0000' },
    'ymobile': { name: 'Y!mobile', icon: '🔴', color: '#e60012' }
};

const VIETNAMESE_CARRIERS = {
    'viettel': { name: 'Viettel', icon: '🔴', color: '#e60012' },
    'vinaphone': { name: 'Vinaphone', icon: '🔵', color: '#0066cc' },
    'mobifone': { name: 'Mobifone', icon: '🟢', color: '#0099ff' }
};

/**
 * Get carrier icon based on carrier name
 */
function getCarrierIcon(carrier) {
    if (!carrier) return '📡';
    const carrierLower = carrier.toLowerCase();
    
    // Check Japanese carriers first
    for (const [key, value] of Object.entries(JAPANESE_CARRIERS)) {
        if (carrierLower.includes(key)) return value.icon;
    }
    
    // Check Vietnamese carriers
    for (const [key, value] of Object.entries(VIETNAMESE_CARRIERS)) {
        if (carrierLower.includes(key)) return value.icon;
    }
    
    return '📡';
}

/**
 * Get carrier CSS class
 */
function getCarrierClass(carrier) {
    if (!carrier) return '';
    const carrierLower = carrier.toLowerCase();
    
    // Check Japanese carriers
    for (const key of Object.keys(JAPANESE_CARRIERS)) {
        if (carrierLower.includes(key)) return `carrier-${key}`;
    }
    
    // Check Vietnamese carriers
    for (const key of Object.keys(VIETNAMESE_CARRIERS)) {
        if (carrierLower.includes(key)) return `carrier-${key}`;
    }
    
    return '';
}

// ============================================
// Signal Strength Parsing
// ============================================

/**
 * Parse signal strength from AT+CSQ response
 */
function parseSignalStrength(signalStr) {
    if (!signalStr || signalStr === 'N/A') return null;
    const match = signalStr.match(/\((.*?)\)/);
    return match ? match[1] : null;
}

/**
 * Get signal quality CSS class
 */
function getSignalClass(quality) {
    if (!quality) return 'signal-poor';
    const q = quality.toLowerCase();
    if (q.includes('excellent')) return 'signal-excellent';
    if (q.includes('good')) return 'signal-good';
    if (q.includes('fair')) return 'signal-fair';
    return 'signal-poor';
}

// ============================================
// Date/Time Formatting
// ============================================

/**
 * Format date/time for display
 */
function formatTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('ja-JP', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

/**
 * Calculate duration between two timestamps
 */
function calculateDuration(start, end) {
    if (!start || !end) return '-';
    const ms = new Date(end) - new Date(start);
    const sec = Math.floor(ms / 1000);
    if (sec >= 60) {
        const min = Math.floor(sec / 60);
        const remainingSec = sec % 60;
        return `${min}m ${remainingSec}s`;
    }
    return `${sec}s`;
}

// ============================================
// API Helpers
// ============================================

/**
 * Make API call with error handling
 */
async function apiCall(url, options = {}) {
    try {
        const response = await fetch(url, options);
        const data = await response.json();
        return { success: true, data };
    } catch (error) {
        console.error('API Error:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Show status message
 */
function showStatus(message, type = 'info') {
    const statusText = document.getElementById('statusText');
    if (statusText) {
        statusText.textContent = message;
        
        // Add color based on type
        if (type === 'success') statusText.style.color = '#155724';
        else if (type === 'error') statusText.style.color = '#721c24';
        else statusText.style.color = '#000';
    }
}

// ============================================
// Theme Management (Dark/Light Mode)
// ============================================

const THEMES = {
    light: {
        '--bg-main': '#c3d9e8',
        '--bg-panel': '#e8f3f9',
        '--bg-header': '#d8e9f7',
        '--text-primary': '#000',
        '--text-secondary': '#333',
        '--border-color': '#6b9bc4'
    },
    dark: {
        '--bg-main': '#1a1a2e',
        '--bg-panel': '#16213e',
        '--bg-header': '#0f3460',
        '--text-primary': '#eee',
        '--text-secondary': '#ccc',
        '--border-color': '#4a5568'
    }
};

/**
 * Apply theme
 */
function applyTheme(themeName) {
    const theme = THEMES[themeName];
    if (!theme) return;
    
    const root = document.documentElement;
    for (const [property, value] of Object.entries(theme)) {
        root.style.setProperty(property, value);
    }
    
    // Save preference
    localStorage.setItem('gsm-theme', themeName);
}

/**
 * Load saved theme
 */
function loadTheme() {
    const savedTheme = localStorage.getItem('gsm-theme') || 'light';
    applyTheme(savedTheme);
    return savedTheme;
}

/**
 * Toggle theme
 */
function toggleTheme() {
    const currentTheme = localStorage.getItem('gsm-theme') || 'light';
    const newTheme = currentTheme === 'light' ? 'dark' : 'light';
    applyTheme(newTheme);
    return newTheme;
}

// ============================================
// Navigation
// ============================================

/**
 * Navigate to page
 */
function navigateTo(page) {
    window.location.href = page;
}

/**
 * Get current page name
 */
function getCurrentPage() {
    const path = window.location.pathname;
    const page = path.substring(path.lastIndexOf('/') + 1);
    return page || 'index.html';
}

// ============================================
// SIM Selection Management
// ============================================

let selectedSim = null;

/**
 * Select SIM card
 */
function selectSim(comPort, checkbox) {
    if (checkbox.checked) {
        // Uncheck all other checkboxes
        document.querySelectorAll('input[type="checkbox"][id^="sim_"]').forEach(cb => {
            if (cb !== checkbox) cb.checked = false;
        });
        
        selectedSim = comPort;
        
        // Update hidden fields if they exist
        const smsComPort = document.getElementById('smsComPort');
        const callComPort = document.getElementById('callComPort');
        if (smsComPort) smsComPort.value = comPort;
        if (callComPort) callComPort.value = comPort;
        
        // Update status
        const selectedSimStatus = document.getElementById('selectedSim');
        if (selectedSimStatus) {
            selectedSimStatus.textContent = `Selected: ${comPort}`;
        }
        
        showStatus(`Selected SIM: ${comPort}`, 'success');
    } else {
        selectedSim = null;
        
        const smsComPort = document.getElementById('smsComPort');
        const callComPort = document.getElementById('callComPort');
        if (smsComPort) smsComPort.value = '';
        if (callComPort) callComPort.value = '';
        
        const selectedSimStatus = document.getElementById('selectedSim');
        if (selectedSimStatus) {
            selectedSimStatus.textContent = 'Selected: None';
        }
        
        showStatus('No SIM selected');
    }
}

/**
 * Get selected SIM
 */
function getSelectedSim() {
    return selectedSim;
}

// ============================================
// Window Size Management
// ============================================

/**
 * Set window size (for desktop app)
 */
function setWindowSize(width, height) {
    if (window.resizeTo) {
        window.resizeTo(width, height);
    }
}

/**
 * Load saved window size
 */
function loadWindowSize() {
    const savedWidth = localStorage.getItem('gsm-window-width');
    const savedHeight = localStorage.getItem('gsm-window-height');
    
    if (savedWidth && savedHeight) {
        setWindowSize(parseInt(savedWidth), parseInt(savedHeight));
    }
}

/**
 * Save window size
 */
function saveWindowSize() {
    localStorage.setItem('gsm-window-width', window.innerWidth);
    localStorage.setItem('gsm-window-height', window.innerHeight);
}

// ============================================
// Initialization
// ============================================

// Load theme on page load
document.addEventListener('DOMContentLoaded', () => {
    loadTheme();
    loadWindowSize();
    
    // Save window size on resize
    window.addEventListener('resize', () => {
        saveWindowSize();
    });
});
