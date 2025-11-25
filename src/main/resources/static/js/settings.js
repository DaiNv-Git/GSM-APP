// Settings Page Logic

// Language translations
const translations = {
    en: {
        scan: 'Scan',
        sms: 'SMS',
        call: 'Call',
        settings: 'Settings',
        ready: 'Ready',
        selected: 'Selected',
        none: 'None'
    },
    vi: {
        scan: 'Quét',
        sms: 'Tin nhắn',
        call: 'Cuộc gọi',
        settings: 'Cài đặt',
        ready: 'Sẵn sàng',
        selected: 'Đã chọn',
        none: 'Không'
    },
    zh: {
        scan: '扫描',
        sms: '短信',
        call: '通话',
        settings: '设置',
        ready: '就绪',
        selected: '已选择',
        none: '无'
    },
    ja: {
        scan: 'スキャン',
        sms: 'SMS',
        call: '通話',
        settings: '設定',
        ready: '準備完了',
        selected: '選択済み',
        none: 'なし'
    }
};

/**
 * Switch theme
 */
function switchTheme(theme) {
    applyTheme(theme);
    updateThemeButtons(theme);
    showStatus(`Theme changed to ${theme}`, 'success');
}

/**
 * Update theme button states
 */
function updateThemeButtons(theme) {
    document.getElementById('btnLight').style.background = theme === 'light' ? '#a0c5dd' : '#c3d9e8';
    document.getElementById('btnDark').style.background = theme === 'dark' ? '#a0c5dd' : '#c3d9e8';
}

/**
 * Change language
 */
function changeLanguage() {
    const lang = document.getElementById('languageSelect').value;
    localStorage.setItem('gsm-language', lang);
    showStatus(`Language changed to ${lang.toUpperCase()}`, 'success');
    
    // Note: Full translation would require reloading pages or updating all text
    alert('Language preference saved. Please refresh the page to apply changes.');
}

/**
 * Apply window size
 */
function applyWindowSize() {
    const width = parseInt(document.getElementById('windowWidth').value);
    const height = parseInt(document.getElementById('windowHeight').value);
    
    if (width < 800 || height < 600) {
        alert('Minimum size is 800x600');
        return;
    }
    
    setWindowSize(width, height);
    localStorage.setItem('gsm-window-width', width);
    localStorage.setItem('gsm-window-height', height);
    
    showStatus(`Window size set to ${width}x${height}`, 'success');
}

/**
 * Update recording path
 */
async function updateRecordingPath() {
    const path = document.getElementById('recordingPath').value;
    
    try {
        const response = await fetch(`/api/modem-call/recording-config?savePath=${encodeURIComponent(path)}`, {
            method: 'POST'
        });
        const data = await response.json();
        
        if (data.success) {
            showStatus('Recording path updated', 'success');
        } else {
            showStatus('Failed to update path', 'error');
        }
    } catch (error) {
        showStatus('Error: ' + error.message, 'error');
    }
}

/**
 * Save all settings
 */
function saveAllSettings() {
    // Save theme
    const currentTheme = localStorage.getItem('gsm-theme') || 'light';
    
    // Save language
    const lang = document.getElementById('languageSelect').value;
    localStorage.setItem('gsm-language', lang);
    
    // Save window size
    const width = document.getElementById('windowWidth').value;
    const height = document.getElementById('windowHeight').value;
    localStorage.setItem('gsm-window-width', width);
    localStorage.setItem('gsm-window-height', height);
    
    // Save scan settings
    const autoScan = document.getElementById('autoScan').checked;
    const scanTimeout = document.getElementById('scanTimeout').value;
    const carrierPriority = document.getElementById('carrierPriority').value;
    localStorage.setItem('gsm-auto-scan', autoScan);
    localStorage.setItem('gsm-scan-timeout', scanTimeout);
    localStorage.setItem('gsm-carrier-priority', carrierPriority);
    
    // Save recording settings
    const autoRecord = document.getElementById('autoRecord').checked;
    localStorage.setItem('gsm-auto-record', autoRecord);
    
    showStatus('✅ All settings saved!', 'success');
    alert('Settings saved successfully!');
}

/**
 * Reset settings to defaults
 */
function resetSettings() {
    if (!confirm('Reset all settings to defaults?')) return;
    
    // Clear all settings
    localStorage.removeItem('gsm-theme');
    localStorage.removeItem('gsm-language');
    localStorage.removeItem('gsm-window-width');
    localStorage.removeItem('gsm-window-height');
    localStorage.removeItem('gsm-auto-scan');
    localStorage.removeItem('gsm-scan-timeout');
    localStorage.removeItem('gsm-carrier-priority');
    localStorage.removeItem('gsm-auto-record');
    
    showStatus('Settings reset to defaults', 'success');
    alert('Settings reset! Please refresh the page.');
}

/**
 * Load saved settings
 */
function loadSettings() {
    // Load theme
    const theme = localStorage.getItem('gsm-theme') || 'light';
    updateThemeButtons(theme);
    
    // Load language
    const lang = localStorage.getItem('gsm-language') || 'en';
    document.getElementById('languageSelect').value = lang;
    
    // Load window size
    const width = localStorage.getItem('gsm-window-width') || '1200';
    const height = localStorage.getItem('gsm-window-height') || '800';
    document.getElementById('windowWidth').value = width;
    document.getElementById('windowHeight').value = height;
    
    // Load scan settings
    const autoScan = localStorage.getItem('gsm-auto-scan') !== 'false';
    const scanTimeout = localStorage.getItem('gsm-scan-timeout') || '10';
    const carrierPriority = localStorage.getItem('gsm-carrier-priority') || 'jp';
    document.getElementById('autoScan').checked = autoScan;
    document.getElementById('scanTimeout').value = scanTimeout;
    document.getElementById('carrierPriority').value = carrierPriority;
    
    // Load recording settings
    const autoRecord = localStorage.getItem('gsm-auto-record') === 'true';
    document.getElementById('autoRecord').checked = autoRecord;
    
    // Load recording path from API
    loadRecordingPath();
}

/**
 * Load recording path from API
 */
async function loadRecordingPath() {
    try {
        const response = await fetch('/api/modem-call/recording-config');
        const data = await response.json();
        
        if (data.success && data.recordingSavePath) {
            document.getElementById('recordingPath').value = data.recordingSavePath;
        }
    } catch (error) {
        console.error('Error loading recording path:', error);
    }
}

// Load settings on page load
document.addEventListener('DOMContentLoaded', () => {
    loadSettings();
});
