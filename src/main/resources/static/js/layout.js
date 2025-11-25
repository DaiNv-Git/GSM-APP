// Navigation Layout Generator

/**
 * Generate common menu bar for all pages
 */
function generateMenuBar() {
    const currentPage = getCurrentPage();
    
    const menuItems = [
        { name: 'Scan', page: 'index.html', icon: '🔍' },
        { name: 'SMS', page: 'sms.html', icon: '📨' },
        { name: 'Call', page: 'call.html', icon: '📞' },
        { name: 'Settings', page: 'settings.html', icon: '⚙️' }
    ];
    
    const menuHTML = menuItems.map(item => {
        const activeClass = currentPage === item.page || (currentPage === '' && item.page === 'index.html') ? 'active' : '';
        return `<div class="menu-item ${activeClass}" onclick="navigateTo('${item.page}')">${item.icon} ${item.name}</div>`;
    }).join('');
    
    return `
        <div class="menu-bar">
            <div class="menu-items">
                ${menuHTML}
            </div>
        </div>
    `;
}

/**
 * Generate status bar
 */
function generateStatusBar() {
    return `
        <div class="status-bar">
            <div class="status-panel" id="statusText">Ready</div>
            <div class="status-panel" id="simCount">SIMs: 0</div>
            <div class="status-panel" id="selectedSim">Selected: None</div>
        </div>
    `;
}

/**
 * Initialize page layout
 */
function initializeLayout() {
    // Insert menu bar at the beginning of body
    const menuBar = generateMenuBar();
    document.body.insertAdjacentHTML('afterbegin', menuBar);
    
    // Insert status bar at the end of body
    const statusBar = generateStatusBar();
    document.body.insertAdjacentHTML('beforeend', statusBar);
}

// Auto-initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    initializeLayout();
});
