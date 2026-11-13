import './style.css';

const API = (import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? 'http://localhost:8080' : '')).replace(/\/$/, '');
let authHeader = '';

const state = {
  cases: [],
  selectedCaseId: '',
  evidence: [],
  selectedEvidenceId: '',
  selectedEvidence: null,
  analysis: null,
  metadata: {},
  dashboard: {},
  page: 'dashboard',
  evidenceSearch: '',
  sort: 'newest',
  file: null,
  busy: false
};

const root = document.querySelector('#root');

root.innerHTML = `
  <div class="login-gate" id="loginGate">
    <form class="login-card" id="loginForm">
      <div class="brand-mark">F</div>
      <p class="eyebrow">FORENSIGHT WORKSPACE</p>
      <h1>Investigator sign in</h1>
      <p class="login-copy">Use the credentials configured for the ForenSight backend.</p>
      <label for="loginUsername">Username</label>
      <input id="loginUsername" name="username" autocomplete="username" value="investigator" required>
      <label for="loginPassword">Password</label>
      <input id="loginPassword" name="password" type="password" autocomplete="current-password" required>
      <p id="loginError" class="login-error" role="alert"></p>
      <button class="primary-button" type="submit" id="loginButton">Sign in</button>
    </form>
  </div>
  <div class="app-shell locked" id="appShell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">F</div>
        <div>
          <strong>ForenSight</strong>
          <span>Digital Forensics</span>
        </div>
      </div>

      <div class="sidebar-section-label">WORKSPACE</div>
      <nav class="nav-list">
        <button class="nav-item active" data-page="dashboard">
          <span class="nav-icon">⌂</span><span>Dashboard</span>
        </button>
        <button class="nav-item" data-page="investigations">
          <span class="nav-icon">▣</span><span>Investigations</span>
        </button>
        <button class="nav-item" data-page="analysis">
          <span class="nav-icon">◉</span><span>Evidence Analysis</span>
        </button>
      </nav>

      <div class="sidebar-section-label">SELECTED CASE</div>
      <div id="sidebarCase" class="sidebar-case empty">No case selected</div>

      <div class="sidebar-spacer"></div>
      <div class="backend-status-card">
        <span class="status-dot" id="backendDot"></span>
        <div>
          <strong id="backendLabel">Checking backend</strong>
          <small id="backendHint">localhost:8080</small>
        </div>
      </div>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <div>
          <div class="eyebrow">FORENSIC INVESTIGATION WORKSPACE</div>
          <h1 id="pageTitle">Forensic Dashboard</h1>
        </div>
        <div class="topbar-actions">
          <button class="icon-button" id="globalRefresh" title="Refresh">↻</button>
          <button class="icon-button" id="logoutButton" title="Sign out" aria-label="Sign out">⇥</button>
          <button class="primary-button compact" id="topNewCase">+ New Case</button>
        </div>
      </header>

      <main class="content">
        <section class="page active" data-page-view="dashboard">
          <div class="hero-grid">
            <div class="hero-card">
              <div class="hero-kicker">FORENSIGHT AI</div>
              <h2>Digital evidence, organized.</h2>
              <p>Manage investigations, review image metadata, run ELA screening on JPEG evidence, and generate forensic reports from one workspace.</p>
              <div class="hero-actions">
                <button class="primary-button" id="heroNewCase">Create Investigation</button>
                <button class="secondary-button" id="heroGoCases">Open Investigations</button>
              </div>
            </div>
            <div class="hero-side-card">
              <div class="signal-orb"></div>
              <div>
                <span class="muted-label">ANALYSIS WORKFLOW</span>
                <strong>TruFor AI + ELA</strong>
                <small>TruFor primary signal · 75% | ELA supporting signal · 25%</small>
              </div>
            </div>
          </div>

          <div class="section-head">
            <div><span class="section-kicker">OVERVIEW</span><h2>Investigation activity</h2></div>
            <button class="text-button" id="dashboardRefresh">Refresh data</button>
          </div>

          <div class="stats-grid">
            <article class="stat-card">
              <div class="stat-icon blue">C</div><span>Total Cases</span><strong id="statCases">—</strong><small>Investigations in MongoDB</small>
            </article>
            <article class="stat-card">
              <div class="stat-icon purple">E</div><span>Total Evidence</span><strong id="statEvidence">—</strong><small>Uploaded evidence items</small>
            </article>
            <article class="stat-card">
              <div class="stat-icon cyan">A</div><span>Analyzed</span><strong id="statAnalyzed">—</strong><small>Valid forensic assessments</small>
            </article>
            <article class="stat-card positive">
              <div class="stat-icon green">✓</div><span>Authentic</span><strong id="statAuthentic">—</strong><small>Latest valid result</small>
            </article>
            <article class="stat-card danger">
              <div class="stat-icon red">!</div><span>Manipulated</span><strong id="statManipulated">—</strong><small>Latest valid result</small>
            </article>
            <article class="stat-card warning">
              <div class="stat-icon amber">R</div><span>High Risk</span><strong id="statHighRisk">—</strong><small>High-risk assessments</small>
            </article>
          </div>

          <div class="two-column">
            <section class="panel">
              <div class="panel-head"><div><h3>Recent investigations</h3><p>Quick access to your cases.</p></div><button class="text-button" id="dashboardCases">View all</button></div>
              <div id="recentCases" class="case-list compact-list"></div>
            </section>
            <section class="panel">
              <div class="panel-head"><div><h3>System health</h3><p>Live application connectivity.</p></div></div>
              <div class="health-list">
                <div class="health-row"><span><i class="health-dot" id="healthApiDot"></i>API</span><strong id="healthApi">Checking</strong></div>
                <div class="health-row"><span><i class="health-dot" id="healthDbDot"></i>Database</span><strong id="healthDb">Checking</strong></div>
                <div class="health-row"><span><i class="health-dot" id="healthAiDot"></i>TruFor engine</span><strong id="healthAi">Checking</strong></div>
              </div>
            </section>
          </div>
        </section>

        <section class="page" data-page-view="investigations">
          <div class="section-head page-head"><div><span class="section-kicker">CASE WORKSPACE</span><h2>Investigations</h2><p>Create, inspect, and manage forensic cases.</p></div><button class="primary-button" id="newCaseFromCases">+ New Investigation</button></div>
          <div class="investigation-layout">
            <section class="panel case-browser">
              <div class="panel-head"><div><h3>All cases</h3><p id="caseCountLabel">Loading…</p></div><button class="text-button" id="refreshCases">Refresh</button></div>
              <div id="allCases" class="case-list"></div>
            </section>
            <section class="panel case-detail-panel">
              <div id="caseDetailEmpty" class="empty-state"><div class="empty-icon">▣</div><h3>Select an investigation</h3><p>Choose a case from the list to view its evidence and run analysis.</p></div>
              <div id="caseDetail" class="hidden"></div>
            </section>
          </div>
        </section>

        <section class="page" data-page-view="analysis">
          <div class="section-head page-head"><div><span class="section-kicker">FORENSIC ANALYSIS</span><h2>Evidence Analysis</h2><p>Upload new evidence or inspect an existing assessment.</p></div></div>

          <div class="analysis-layout">
            <section class="panel upload-panel">
              <div class="panel-head"><div><h3>Analyze new evidence</h3><p>Evidence is attached to the selected investigation.</p></div></div>
              <div id="analysisCaseBanner" class="case-banner"></div>
              <label class="field-label" for="fileInput">Evidence image</label>
              <div id="dropZone" class="drop-zone">
                <input id="fileInput" type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" hidden />
                <div class="drop-icon">↑</div>
                <strong>Drop an image here</strong>
                <span>JPG, JPEG and PNG images · ELA currently supports JPEG</span>
                <button type="button" class="secondary-button" id="chooseFile">Choose file</button>
                <small id="selectedFile">No file selected</small>
              </div>
              <div id="uploadPreview" class="upload-preview hidden"></div>
              <button id="analyzeButton" class="primary-button full-width">Analyze Evidence</button>
              <div id="analysisProgress" class="progress-message hidden"></div>
            </section>

            <section class="panel analysis-panel">
              <div id="analysisEmpty" class="empty-state"><div class="empty-icon">◉</div><h3>No evidence selected</h3><p>Choose evidence from an investigation or analyze a new image.</p></div>
              <div id="analysisView" class="hidden"></div>
            </section>
          </div>
        </section>
      </main>
    </div>
  </div>

  <div id="toast" class="toast"></div>

  <div id="caseModal" class="modal-backdrop hidden">
    <div class="modal-card">
      <div class="modal-head"><div><span class="section-kicker">NEW INVESTIGATION</span><h3>Create a case</h3></div><button class="icon-button" data-close-modal="caseModal">×</button></div>
      <form id="caseForm">
        <label class="field-label">Case title<input id="caseTitle" required maxlength="120" placeholder="e.g. Certificate verification"></label>
        <label class="field-label">Description<textarea id="caseDescription" rows="4" maxlength="500" placeholder="Brief description of the investigation"></textarea></label>
        <label class="field-label">Status<select id="caseStatus"><option value="OPEN">OPEN</option><option value="IN_PROGRESS">IN PROGRESS</option><option value="CLOSED">CLOSED</option></select></label>
        <div class="modal-actions"><button type="button" class="secondary-button" data-close-modal="caseModal">Cancel</button><button type="submit" class="primary-button" id="saveCase">Create Case</button></div>
      </form>
    </div>
  </div>

  <div id="confirmModal" class="modal-backdrop hidden">
    <div class="modal-card confirm-card">
      <div class="modal-head"><div><span class="section-kicker">CONFIRM ACTION</span><h3 id="confirmTitle">Are you sure?</h3></div><button class="icon-button" data-close-modal="confirmModal">×</button></div>
      <p id="confirmMessage"></p>
      <div class="modal-actions"><button class="secondary-button" data-close-modal="confirmModal">Cancel</button><button class="danger-button" id="confirmAction">Delete</button></div>
    </div>
  </div>
`;

const qs = (selector, scope = document) => scope.querySelector(selector);
const qsa = (selector, scope = document) => [...scope.querySelectorAll(selector)];

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function formatPercent(value) {
  if (value === null || value === undefined || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return `${(n <= 1 ? n * 100 : n).toFixed(2)}%`;
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') return '—';
  const n = Number(value);
  return Number.isFinite(n) ? `${n.toFixed(2)}%` : '—';
}

function formatBytes(bytes) {
  const n = Number(bytes);
  if (!Number.isFinite(n) || n < 0) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(2)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function normalizeResult(result) {
  const value = String(result ?? 'UNKNOWN').trim().toUpperCase();
  if (value === 'LIKELY_AUTHENTIC') return 'AUTHENTIC';
  if (value === 'LIKELY_MANIPULATED' || value === 'SUSPICIOUS') return 'MANIPULATED';
  return value;
}

function elaFindingLabel(value) {
  const finding = String(value || '').toUpperCase();
  if (finding === 'POSSIBLE_COMPRESSION_ANOMALY' || finding === 'SUSPICIOUS') return 'Possible compression anomaly';
  if (finding === 'NO_CLEAR_COMPRESSION_ANOMALY' || finding === 'LIKELY_AUTHENTIC') return 'No clear anomaly flagged';
  if (finding === 'NOT_SUPPORTED' || finding === 'UNSUPPORTED') return 'Not supported for this format';
  if (finding === 'ERROR') return 'Screening error';
  return 'Not available';
}

function resultClass(result) {
  const value = normalizeResult(result);
  if (value === 'AUTHENTIC') return 'authentic';
  if (value === 'MANIPULATED') return 'manipulated';
  if (value === 'SUSPICIOUS') return 'suspicious';
  if (value === 'ERROR' || value === 'FAILED') return 'failed';
  return 'unknown';
}

function showToast(message, type = 'info') {
  const toast = qs('#toast');
  toast.textContent = message;
  toast.className = `toast show ${type}`;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove('show'), 3400);
}

function setBackendStatus(online, text) {
  qs('#backendDot').className = `status-dot ${online ? 'online' : 'offline'}`;
  qs('#backendLabel').textContent = online ? 'Backend connected' : 'Backend offline';
  qs('#backendHint').textContent = text || 'localhost:8080';
}

async function request(path, options = {}) {
  const response = await authorizedFetch(path, options);
  if (response.status === 401) {
    authHeader = '';
    showLoginGate('Your sign-in expired. Please sign in again.');
  }
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json().catch(() => null)
    : await response.text().catch(() => '');

  if (!response.ok) {
    const message = body && typeof body === 'object' && body.message
      ? body.message
      : `${options.method || 'GET'} ${path} failed (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return body;
}

async function authorizedFetch(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (authHeader) headers.set('Authorization', authHeader);
  return fetch(`${API}${path}`, { ...options, headers });
}

function showLoginGate(message = '') {
  qs('#appShell').classList.add('locked');
  qs('#loginGate').classList.remove('hidden');
  qs('#loginError').textContent = message;
  qs('#loginPassword').value = '';
}

function showWorkspace() {
  qs('#loginGate').classList.add('hidden');
  qs('#appShell').classList.remove('locked');
  updateCaseBanner();
  renderCaseDetail();
  refreshAll();
}

async function signIn(event) {
  event.preventDefault();
  const username = qs('#loginUsername').value.trim();
  const password = qs('#loginPassword').value;
  const button = qs('#loginButton');
  button.disabled = true;
  qs('#loginError').textContent = 'Connecting…';

  try {
    const candidate = `Basic ${btoa(`${username}:${password}`)}`;
    const response = await fetch(`${API}/api/auth/check`, {
      headers: { Authorization: candidate }
    });
    if (!response.ok) {
      qs('#loginError').textContent = response.status === 401
        ? 'That username or password is not correct.'
        : `The backend returned ${response.status}.`;
      return;
    }
    authHeader = candidate;
    qs('#loginPassword').value = '';
    showWorkspace();
  } catch {
    qs('#loginError').textContent = 'Could not reach the backend. Check the backend URL and try again.';
  } finally {
    button.disabled = false;
  }
}

function signOut() {
  authHeader = '';
  state.cases = [];
  state.evidence = [];
  state.selectedCaseId = '';
  state.selectedEvidenceId = '';
  state.selectedEvidence = null;
  state.analysis = null;
  state.metadata = {};
  state.dashboard = {};
  showLoginGate();
}

function hydrateProtectedAssets() {
  if (!authHeader) return;

  root.querySelectorAll('img[data-api-asset]:not([data-api-loading])').forEach(async (image) => {
    image.dataset.apiLoading = 'true';
    try {
      const response = await authorizedFetch(image.dataset.apiAsset);
      if (response.status === 401) {
        authHeader = '';
        showLoginGate('Your sign-in expired. Please sign in again.');
        return;
      }
      if (!response.ok) throw new Error('Asset request failed');
      image.src = URL.createObjectURL(await response.blob());
    } catch {
      image.classList.add('image-error');
    }
  });

  root.querySelectorAll('a[data-api-asset-link]:not([data-api-loading])').forEach(async (link) => {
    link.dataset.apiLoading = 'true';
    try {
      const response = await authorizedFetch(link.dataset.apiAssetLink);
      if (response.status === 401) {
        authHeader = '';
        showLoginGate('Your sign-in expired. Please sign in again.');
        return;
      }
      if (!response.ok) throw new Error('Asset request failed');
      link.href = URL.createObjectURL(await response.blob());
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    } catch {
      link.removeAttribute('href');
      link.setAttribute('aria-disabled', 'true');
    }
  });
}

const protectedAssetObserver = new MutationObserver(() => hydrateProtectedAssets());

function navigate(page) {
  state.page = page;
  qsa('.nav-item').forEach((button) => {
    button.classList.toggle('active', button.dataset.page === page);
  });
  qsa('.page').forEach((section) => {
    section.classList.toggle('active', section.dataset.pageView === page);
  });

  const titles = {
    dashboard: 'Forensic Dashboard',
    investigations: 'Investigations',
    analysis: 'Evidence Analysis'
  };
  qs('#pageTitle').textContent = titles[page] || 'ForenSight';
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function openModal(id) {
  qs(`#${id}`)?.classList.remove('hidden');
}

function closeModal(id) {
  qs(`#${id}`)?.classList.add('hidden');
}

function openConfirm(title, message, onConfirm) {
  qs('#confirmTitle').textContent = title;
  qs('#confirmMessage').textContent = message;
  const button = qs('#confirmAction');
  button.onclick = async () => {
    button.disabled = true;
    button.textContent = 'Working…';
    try {
      await onConfirm();
      closeModal('confirmModal');
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      button.disabled = false;
      button.textContent = 'Delete';
    }
  };
  openModal('confirmModal');
}

async function checkHealth() {
  try {
    await request('/api/health');
    setBackendStatus(true, 'API reachable');
    qs('#healthApi').textContent = 'Online';
    qs('#healthApiDot').className = 'health-dot online';
  } catch {
    setBackendStatus(false, 'Start Spring Boot on :8080');
    qs('#healthApi').textContent = 'Offline';
    qs('#healthApiDot').className = 'health-dot offline';
  }

  try {
    await request('/api/db-health');
    qs('#healthDb').textContent = 'Connected';
    qs('#healthDbDot').className = 'health-dot online';
  } catch {
    qs('#healthDb').textContent = 'Unavailable';
    qs('#healthDbDot').className = 'health-dot offline';
  }
  qs('#healthAi').textContent = 'Optional';
  qs('#healthAiDot').className = 'health-dot';
}

async function loadDashboard() {
  try {
    const stats = await request('/api/dashboard/stats');
    state.dashboard = stats || {};
    qs('#statCases').textContent = stats.totalCases ?? 0;
    qs('#statEvidence').textContent = stats.totalEvidence ?? 0;
    qs('#statAnalyzed').textContent = stats.analyzedCount ?? 0;
    qs('#statAuthentic').textContent = stats.authenticCount ?? 0;
    qs('#statManipulated').textContent = stats.manipulatedCount ?? 0;
    qs('#statHighRisk').textContent = stats.highRiskCount ?? 0;
  } catch (error) {
    showToast('Dashboard statistics could not be loaded.', 'error');
  }
}

function renderRecentCases() {
  const container = qs('#recentCases');
  if (!state.cases.length) {
    container.innerHTML = '<div class="empty-inline">No investigations yet.</div>';
    return;
  }

  container.innerHTML = state.cases.slice(0, 5).map((item) => `
    <button class="case-row" data-case-id="${escapeHtml(item.id)}">
      <div class="case-row-icon">▣</div>
      <div class="case-row-main"><strong>${escapeHtml(item.title || 'Untitled case')}</strong><span>${escapeHtml(item.description || 'No description')}</span></div>
      <span class="status-pill ${String(item.status || 'OPEN').toLowerCase()}">${escapeHtml(item.status || 'OPEN')}</span>
    </button>
  `).join('');

  qsa('[data-case-id]', container).forEach((button) => {
    button.addEventListener('click', async () => {
      await selectCase(button.dataset.caseId);
      navigate('investigations');
    });
  });
}

async function loadCases() {
  try {
    state.cases = await request('/api/cases');
    if (!Array.isArray(state.cases)) state.cases = [];

    renderRecentCases();
    renderCaseBrowser();

    if (state.selectedCaseId && !state.cases.some((c) => c.id === state.selectedCaseId)) {
      state.selectedCaseId = '';
      state.evidence = [];
      state.selectedEvidenceId = '';
      state.analysis = null;
      renderCaseDetail();
    }

    qs('#caseCountLabel').textContent = `${state.cases.length} investigation${state.cases.length === 1 ? '' : 's'}`;
    qs('#sidebarCase').textContent = state.selectedCaseId
      ? (state.cases.find((c) => c.id === state.selectedCaseId)?.title || 'Selected case')
      : 'No case selected';
    qs('#sidebarCase').classList.toggle('empty', !state.selectedCaseId);
  } catch (error) {
    showToast('Investigations could not be loaded.', 'error');
    qs('#allCases').innerHTML = '<div class="empty-state small"><h4>Unable to load cases</h4><p>Make sure Spring Boot is running on port 8080.</p></div>';
  }
}

function renderCaseBrowser() {
  const container = qs('#allCases');
  if (!state.cases.length) {
    container.innerHTML = '<div class="empty-state small"><div class="empty-icon">+</div><h4>No investigations</h4><p>Create your first case to begin.</p></div>';
    return;
  }

  container.innerHTML = state.cases.map((item) => `
    <button class="case-browser-row ${item.id === state.selectedCaseId ? 'selected' : ''}" data-case-id="${escapeHtml(item.id)}">
      <div class="case-row-icon">▣</div>
      <div class="case-row-main"><strong>${escapeHtml(item.title || 'Untitled case')}</strong><span>${escapeHtml(item.description || 'No description')}</span></div>
      <span class="status-pill ${String(item.status || 'OPEN').toLowerCase()}">${escapeHtml(item.status || 'OPEN')}</span>
    </button>
  `).join('');

  qsa('[data-case-id]', container).forEach((button) => {
    button.addEventListener('click', () => selectCase(button.dataset.caseId));
  });
}

async function selectCase(caseId) {
  state.selectedCaseId = caseId;
  state.selectedEvidenceId = '';
  state.selectedEvidence = null;
  state.analysis = null;
  state.metadata = {};
  state.evidence = [];
  renderCaseBrowser();
  renderCaseDetail();
  updateCaseBanner();

  const chosen = state.cases.find((item) => item.id === caseId);
  qs('#sidebarCase').textContent = chosen?.title || 'Selected case';
  qs('#sidebarCase').classList.remove('empty');

  try {
    state.evidence = await request(`/api/evidence/case/${encodeURIComponent(caseId)}`);
    if (!Array.isArray(state.evidence)) state.evidence = [];
    renderCaseDetail();
    if (state.evidence.length) {
      const preferred = state.evidence[0];
      await selectEvidence(preferred.id, false);
    } else {
      navigate('analysis');
    }
  } catch (error) {
    showToast('Evidence history could not be loaded.', 'error');
    renderCaseDetail();
  }
}

function updateCaseBanner() {
  const chosen = state.cases.find((item) => item.id === state.selectedCaseId);
  const banner = qs('#analysisCaseBanner');
  if (!chosen) {
    banner.innerHTML = '<div class="case-banner-empty">Select an investigation before uploading evidence.</div>';
    return;
  }
  banner.innerHTML = `<div class="case-banner-icon">▣</div><div><span>ATTACHED TO CASE</span><strong>${escapeHtml(chosen.title || 'Untitled case')}</strong></div><span class="status-pill ${String(chosen.status || 'OPEN').toLowerCase()}">${escapeHtml(chosen.status || 'OPEN')}</span>`;
}

function renderCaseDetail() {
  const empty = qs('#caseDetailEmpty');
  const detail = qs('#caseDetail');
  const chosen = state.cases.find((item) => item.id === state.selectedCaseId);
  if (!chosen) {
    empty.classList.remove('hidden');
    detail.classList.add('hidden');
    return;
  }
  empty.classList.add('hidden');
  detail.classList.remove('hidden');

  const filtered = getFilteredEvidence();
  detail.innerHTML = `
    <div class="detail-head">
      <div><span class="section-kicker">INVESTIGATION</span><h3>${escapeHtml(chosen.title || 'Untitled case')}</h3><p>${escapeHtml(chosen.description || 'No description provided.')}</p></div>
      <div class="detail-actions"><span class="status-pill ${String(chosen.status || 'OPEN').toLowerCase()}">${escapeHtml(chosen.status || 'OPEN')}</span><button class="danger-button small" id="deleteCaseInline">Delete Case</button></div>
    </div>
    <div class="detail-stats"><div><span>Evidence</span><strong>${state.evidence.length}</strong></div><div><span>Case ID</span><strong class="mono">${escapeHtml(chosen.id)}</strong></div></div>
    <div class="evidence-toolbar"><div class="search-wrap"><span>⌕</span><input id="evidenceSearch" value="${escapeHtml(state.evidenceSearch)}" placeholder="Search evidence…"></div><select id="evidenceSort"><option value="newest" ${state.sort === 'newest' ? 'selected' : ''}>Newest first</option><option value="oldest" ${state.sort === 'oldest' ? 'selected' : ''}>Oldest first</option><option value="name" ${state.sort === 'name' ? 'selected' : ''}>File name</option></select></div>
    <div id="evidenceList" class="evidence-list"></div>
  `;
  const list = qs('#evidenceList', detail);
  if (!filtered.length) {
    list.innerHTML = '<div class="empty-state small"><div class="empty-icon">⌕</div><h4>No matching evidence</h4><p>Upload an image or change the search.</p></div>';
  } else {
    list.innerHTML = filtered.map((item) => renderEvidenceRow(item)).join('');
  }

  qs('#evidenceSearch', detail)?.addEventListener('input', (event) => {
    state.evidenceSearch = event.target.value;
    renderCaseDetail();
  });
  qs('#evidenceSort', detail)?.addEventListener('change', (event) => {
    state.sort = event.target.value;
    renderCaseDetail();
  });
  qs('#deleteCaseInline', detail)?.addEventListener('click', deleteSelectedCase);
  qsa('[data-evidence-id]', detail).forEach((button) => {
    button.addEventListener('click', () => selectEvidence(button.dataset.evidenceId, true));
  });
  qsa('[data-delete-evidence]', detail).forEach((button) => {
    button.addEventListener('click', (event) => {
      event.stopPropagation();
      const item = state.evidence.find((e) => e.id === button.dataset.deleteEvidence);
      if (item) deleteEvidence(item);
    });
  });
}

function getFilteredEvidence() {
  const query = state.evidenceSearch.trim().toLowerCase();
  let items = state.evidence.filter((item) => !query || String(item.fileName || '').toLowerCase().includes(query) || String(item.id || '').toLowerCase().includes(query));
  if (state.sort === 'name') items.sort((a, b) => String(a.fileName).localeCompare(String(b.fileName)));
  else if (state.sort === 'oldest') items.sort((a, b) => String(a.uploadedAt || '').localeCompare(String(b.uploadedAt || '')));
  else items.sort((a, b) => String(b.uploadedAt || '').localeCompare(String(a.uploadedAt || '')));
  return items;
}

function renderEvidenceRow(item) {
  const cached = state.analysis && state.analysis.evidenceId === item.id ? state.analysis : null;
  const result = cached ? normalizeResult(cached.result) : 'NOT ANALYZED';
  const cls = cached ? resultClass(result) : 'pending';
  return `
    <article class="evidence-row ${state.selectedEvidenceId === item.id ? 'selected' : ''}" data-evidence-id="${escapeHtml(item.id)}">
      <div class="evidence-thumb">${fileTypeBadge(item.fileType || item.fileName)}</div>
      <div class="evidence-main"><strong>${escapeHtml(item.fileName || 'Unnamed evidence')}</strong><span>${escapeHtml(item.fileType || 'Unknown type')} · ${formatBytes(item.fileSize)}</span><small>${escapeHtml(item.uploadedAt || 'Upload time unavailable')}</small></div>
      <div class="evidence-result ${cls}"><strong>${escapeHtml(result)}</strong><span>${cached ? formatScore(cached.finalScore) : '—'}</span></div>
      <div class="evidence-actions"><button class="icon-action" title="Open analysis">→</button><button class="icon-action danger" title="Delete evidence" data-delete-evidence="${escapeHtml(item.id)}">×</button></div>
    </article>
  `;
}

function fileTypeBadge(value) {
  const text = String(value || '').toLowerCase();
  if (text.includes('png')) return 'PNG';
  if (text.includes('jpeg') || text.includes('jpg')) return 'JPG';
  if (text.includes('webp')) return 'WEBP';
  return 'IMG';
}

async function selectEvidence(evidenceId, navigateToAnalysis = true) {
  state.selectedEvidenceId = evidenceId;
  state.selectedEvidence = state.evidence.find((item) => item.id === evidenceId) || null;
  state.analysis = null;
  state.metadata = {};
  renderCaseDetail();
  renderAnalysisLoading();
  if (navigateToAnalysis) navigate('analysis');
  try {
    const analysis = await request(`/api/analysis/${encodeURIComponent(evidenceId)}`);
    state.analysis = analysis;
  } catch {
    state.analysis = { evidenceId, result: 'NOT ANALYZED', finalScore: null };
  }
  renderAnalysis();
  loadMetadata(evidenceId);
}

function renderAnalysisLoading() {
  qs('#analysisEmpty').classList.add('hidden');
  const view = qs('#analysisView');
  view.classList.remove('hidden');
  view.innerHTML = '<div class="loading-card"><div class="spinner"></div><h3>Loading forensic assessment</h3><p>Retrieving analysis, metadata, and evidence artifacts…</p></div>';
}

function renderAnalysis() {
  const view = qs('#analysisView');
  if (!state.selectedEvidence) {
    qs('#analysisEmpty').classList.remove('hidden');
    view.classList.add('hidden');
    return;
  }
  qs('#analysisEmpty').classList.add('hidden');
  view.classList.remove('hidden');
  const evidence = state.selectedEvidence;
  const analysis = state.analysis || {};
  const hasAnalysis = Boolean(analysis.result && analysis.result !== 'NOT ANALYZED');
  const modelAvailable = analysis.truForAvailable === true;
  // Legacy database rows may say LIKELY_AUTHENTIC based on ELA only. Never
  // show that as an authenticity verdict unless a model actually ran.
  const result = !hasAnalysis ? 'NOT ANALYZED' : (modelAvailable ? normalizeResult(analysis.result) : 'INCONCLUSIVE');
  const risk = modelAvailable ? String(analysis.riskLevel || 'UNKNOWN').toUpperCase() : 'UNKNOWN';
  const cls = resultClass(result);
  const modelScore = modelAvailable ? formatScore(analysis.finalScore) : 'N/A';
  const truforAuthenticity = modelAvailable ? formatScore(analysis.truForAuthenticity) : 'N/A';
  const elaFinding = elaFindingLabel(analysis.elaFinding);

  view.innerHTML = `
    <div class="analysis-header">
      <div><span class="section-kicker">SELECTED EVIDENCE</span><h3>${escapeHtml(evidence.fileName || 'Unnamed evidence')}</h3><p class="mono">${escapeHtml(evidence.id || '')}</p></div>
      <div class="analysis-header-actions"><span class="risk-badge ${risk.toLowerCase()}">${escapeHtml(risk)}</span><button class="primary-button" id="reanalyzeButton">${analysis.result && analysis.result !== 'NOT ANALYZED' ? 'Re-run Analysis' : 'Run Analysis'}</button><button class="secondary-button" id="reportButton">Download PDF</button><button class="danger-button small" id="deleteEvidenceTop">Delete Evidence</button></div>
    </div>

    <div class="result-hero ${cls}">
      <div><span class="muted-label">${modelAvailable ? 'MODEL-ASSISTED FORENSIC ASSESSMENT' : 'PRELIMINARY FORENSIC SCREENING'}</span><strong>${escapeHtml(result)}</strong><p>${escapeHtml(analysis.explanation || 'No explanation available yet.')}</p></div>
      <div class="hero-score"><span>Final Authenticity · TruFor 75% + ELA 25%</span><strong>${modelScore}</strong></div>
    </div>

    <div class="metric-grid">
      <div class="metric-card"><span>TruFor Authenticity · 75%</span><strong>${truforAuthenticity}</strong><small>Primary AI signal · (1 − native TruFor score) × 100</small></div>
      <div class="metric-card"><span>ELA Supporting Score · 25%</span><strong>${formatScore(analysis.elaScore)}</strong><small>${escapeHtml(elaFinding)} · supporting signal</small></div>
      <div class="metric-card"><span>Metadata Context</span><strong>Not scored</strong><small>Reported separately; excluded from final authenticity score</small></div>
      <div class="metric-card"><span>TruFor confidence</span><strong>${formatPercent(analysis.truForConfidence)}</strong><small>${escapeHtml(analysis.truForStatus || 'TruFor not available')}</small></div>
    </div>

    <div class="tab-bar">
      <button class="tab active" data-tab="overview">Overview</button>
      <button class="tab" data-tab="metadata">Metadata</button>
      <button class="tab" data-tab="ela">ELA</button>
      <button class="tab" data-tab="trufor">TruFor</button>
      <button class="tab" data-tab="report">Report</button>
    </div>

    <div class="tab-panel active" data-tab-panel="overview">
      <div class="overview-grid">
        <div class="artifact-card"><div class="artifact-top"><span>Evidence</span><span class="tag">SOURCE</span></div><div class="artifact-body"><div class="data-line"><span>File type</span><strong>${escapeHtml(evidence.fileType || 'Unknown')}</strong></div><div class="data-line"><span>Size</span><strong>${formatBytes(evidence.fileSize)}</strong></div><div class="data-line"><span>Uploaded</span><strong>${escapeHtml(evidence.uploadedAt || '—')}</strong></div><div class="data-line"><span>Case</span><strong>${escapeHtml((state.cases.find((c) => c.id === evidence.caseId)?.title) || evidence.caseId || '—')}</strong></div></div></div>
        <div class="artifact-card original-evidence-card"><div class="artifact-top"><span>Original evidence image</span><span class="tag">SOURCE FILE</span></div><div class="artifact-body original-image-body"><div class="original-image-preview-frame"><img class="evidence-original-preview" data-api-asset="/api/evidence/original/${encodeURIComponent(evidence.id)}?t=${Date.now()}" alt="Original evidence — full image" loading="eager" decoding="async" style="display:block!important;width:100%!important;height:auto!important;max-width:100%!important;max-height:520px!important;object-fit:contain!important;object-position:center center!important;aspect-ratio:auto!important;" onload="const d=this.closest('.original-image-body')?.querySelector('.original-image-dimensions');if(d)d.textContent=this.naturalWidth+' × '+this.naturalHeight+' px'" onerror="this.classList.add('image-error')"></div><div class="original-image-caption">Complete source image · <span class="original-image-dimensions">dimensions shown after loading</span> · <a data-api-asset-link="/api/evidence/original/${encodeURIComponent(evidence.id)}">Open original file</a></div></div></div>
        <div class="artifact-card"><div class="artifact-top"><span>Assessment notes</span><span class="tag">INTERPRETATION</span></div><div class="artifact-body"><p class="long-text">${escapeHtml(analysis.explanation || 'No explanation available.')}</p></div></div>
      </div>
    </div>

    <div class="tab-panel" data-tab-panel="metadata"><div id="metadataPanel" class="metadata-grid-large"><div class="loading-card small"><div class="spinner"></div><span>Loading metadata…</span></div></div></div>

    <div class="tab-panel" data-tab-panel="ela">
      ${analysis.elaSupported ? `<div class="visual-card"><div class="visual-copy"><span class="section-kicker">ERROR LEVEL ANALYSIS</span><h3>ELA supporting visualization</h3><p>ELA highlights JPEG recompression differences only. A clean ELA result does not mean the image is authentic, and an anomaly does not prove editing.</p></div><img data-api-asset="/api/evidence/ela/${encodeURIComponent(evidence.id)}?t=${Date.now()}" alt="ELA visualization" onerror="this.classList.add('image-error')"></div>` : `<div class="empty-state small"><h4>ELA is not available for this image</h4><p>The current ELA implementation supports JPEG files. Metadata extraction and the optional TruFor integration can still be reviewed.</p></div>`}
    </div>

    <div class="tab-panel" data-tab-panel="trufor">
      <div class="artifact-card"><div class="artifact-top"><span>TruFor model status</span><span class="tag">EXISTING MODEL</span></div><div class="artifact-body"><strong>${analysis.truForAvailable ? 'TruFor ran successfully' : 'TruFor did not produce a result'}</strong><p class="long-text">${escapeHtml(analysis.truForStatus || 'Run analysis to generate the TruFor localization map.')}</p>${analysis.truForAvailable ? `<div class="data-line"><span>Native TruFor score</span><strong>${formatScore(100 - Number(analysis.truForAuthenticity))}</strong></div><div class="data-line"><span>TruFor authenticity indicator</span><strong>${formatScore(analysis.truForAuthenticity)}</strong></div><div class="data-line"><span>Model confidence</span><strong>${formatPercent(analysis.truForConfidence)}</strong></div>` : ''}</div></div>
      ${analysis.truForAvailable ? `
        <section class="trufor-preview-section" aria-label="TruFor model visual outputs">
          <div class="trufor-preview-heading">
            <div>
              <span class="section-kicker">MODEL VISUALIZATION</span>
              <h3>TruFor heatmap preview</h3>
              <p>These previews are generated from the existing TruFor model output for this evidence item.</p>
            </div>
            <span class="tag">NATIVE MODEL OUTPUT</span>
          </div>
          <div class="trufor-preview-grid">
            <article class="trufor-preview-card">
              <div class="trufor-preview-card-heading">
                <div><strong>Forgery localization heatmap</strong><small>Native TruFor map · RdBu_r colour scale</small></div>
                <a data-api-asset-link="/api/evidence/trufor/${encodeURIComponent(evidence.id)}?t=${Date.now()}">Open full size ↗</a>
              </div>
              <a class="trufor-preview-image-link" data-api-asset-link="/api/evidence/trufor/${encodeURIComponent(evidence.id)}?t=${Date.now()}" aria-label="Open full-size TruFor localization heatmap">
                <img class="trufor-preview-image" loading="eager" decoding="async" data-api-asset="/api/evidence/trufor/${encodeURIComponent(evidence.id)}?t=${Date.now()}" alt="TruFor forgery localization heatmap" onerror="this.hidden=true; this.closest('.trufor-preview-card').classList.add('preview-failed')">
              </a>
              <div class="trufor-preview-fallback" role="status">The localization image could not be loaded. Check that the evidence has a saved TruFor heatmap and that the image endpoint returns PNG.</div>
            </article>
            <article class="trufor-preview-card">
              <div class="trufor-preview-card-heading">
                <div><strong>TruFor confidence map</strong><small>Pixel-level model confidence · grayscale</small></div>
                <a data-api-asset-link="/api/evidence/trufor-confidence/${encodeURIComponent(evidence.id)}?t=${Date.now()}">Open full size ↗</a>
              </div>
              <a class="trufor-preview-image-link" data-api-asset-link="/api/evidence/trufor-confidence/${encodeURIComponent(evidence.id)}?t=${Date.now()}" aria-label="Open full-size TruFor confidence map">
                <img class="trufor-preview-image confidence-preview" loading="eager" decoding="async" data-api-asset="/api/evidence/trufor-confidence/${encodeURIComponent(evidence.id)}?t=${Date.now()}" alt="TruFor pixel-level confidence map" onerror="this.hidden=true; this.closest('.trufor-preview-card').classList.add('preview-failed')">
              </a>
              <div class="trufor-preview-fallback" role="status">The confidence image could not be loaded. Check that the evidence has a saved TruFor confidence map and that the image endpoint returns PNG.</div>
            </article>
          </div>
          <p class="trufor-preview-note">Interpret the localization map as supporting forensic evidence. It is not, by itself, proof that an image was manipulated.</p>
        </section>
      ` : `<div class="empty-state small"><h4>TruFor heatmap not available</h4><p>TruFor has not produced saved visualization files for this evidence. Re-run analysis and check the backend log if the images are still unavailable.</p></div>`}
    </div>

    <div class="tab-panel" data-tab-panel="report"><div class="report-callout"><div><span class="section-kicker">FORMAL REPORT</span><h3>Generate a complete forensic PDF</h3><p>The PDF includes evidence details, metadata as context, the original image, ELA supporting visualization, the TruFor localization heatmap, confidence map, and the original 75% TruFor + 25% ELA calculation.</p></div><button class="primary-button" id="reportButtonSecondary">Generate PDF</button></div></div>
  `;

  bindAnalysisTabs();
  qs('#reportButton')?.addEventListener('click', downloadReport);
  qs('#reportButtonSecondary')?.addEventListener('click', downloadReport);
  qs('#reanalyzeButton')?.addEventListener('click', () => runAnalysis(evidence.id));
  qs('#deleteEvidenceTop')?.addEventListener('click', () => deleteEvidence(evidence));
}

function bindAnalysisTabs() {
  qsa('.tab').forEach((button) => {
    button.addEventListener('click', () => {
      qsa('.tab').forEach((tab) => tab.classList.remove('active'));
      qsa('.tab-panel').forEach((panel) => panel.classList.remove('active'));
      button.classList.add('active');
      qs(`[data-tab-panel="${button.dataset.tab}"]`)?.classList.add('active');
    });
  });
}

async function loadMetadata(evidenceId) {
  try {
    const metadata = await request(`/api/evidence/metadata/${encodeURIComponent(evidenceId)}`);
    state.metadata = metadata && typeof metadata === 'object' ? metadata : {};
    renderMetadataPanel();
  } catch {
    state.metadata = {};
    renderMetadataPanel('Metadata unavailable.');
  }
}

function renderMetadataPanel(message = '') {
  const container = qs('#metadataPanel');
  if (!container) return;
  const entries = Object.entries(state.metadata || {});
  if (message || !entries.length) {
    container.innerHTML = `<div class="empty-state small"><div class="empty-icon">M</div><h4>${escapeHtml(message || 'No readable metadata')}</h4><p>The uploaded image did not expose readable metadata fields.</p></div>`;
    return;
  }
  container.innerHTML = entries.map(([key, value]) => `<div class="metadata-item"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value || 'N/A')}</strong></div>`).join('');
}

async function createCase(event) {
  event.preventDefault();
  const title = qs('#caseTitle').value.trim();
  const description = qs('#caseDescription').value.trim();
  const status = qs('#caseStatus').value;
  if (!title) return;
  const button = qs('#saveCase');
  button.disabled = true;
  button.textContent = 'Creating…';
  try {
    const created = await request('/api/cases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, description, status })
    });
    closeModal('caseModal');
    qs('#caseForm').reset();
    await loadCases();
    await loadDashboard();
    if (created?.id) await selectCase(created.id);
    navigate('investigations');
    showToast('Investigation created successfully.', 'success');
  } catch (error) {
    showToast(error.message, 'error');
  } finally {
    button.disabled = false;
    button.textContent = 'Create Case';
  }
}

async function deleteEvidence(item) {
  openConfirm(
    'Delete evidence?',
    `This permanently removes “${item.fileName || 'Unnamed evidence'}”, its analysis results and generated ELA output.`,
    async () => {
      await request(`/api/evidence/${encodeURIComponent(item.id)}`, { method: 'DELETE' });
      state.evidence = state.evidence.filter((entry) => entry.id !== item.id);
      if (state.selectedEvidenceId === item.id) {
        state.selectedEvidenceId = '';
        state.selectedEvidence = null;
        state.analysis = null;
        qs('#analysisView').classList.add('hidden');
        qs('#analysisEmpty').classList.remove('hidden');
      }
      renderCaseDetail();
      await loadDashboard();
      showToast('Evidence deleted.', 'success');
    }
  );
}

async function deleteSelectedCase() {
  const chosen = state.cases.find((item) => item.id === state.selectedCaseId);
  if (!chosen) return;
  openConfirm(
    'Delete investigation?',
    `This permanently deletes “${chosen.title || 'Untitled case'}”, all associated evidence, analysis records and generated image files.`,
    async () => {
      await request(`/api/cases/${encodeURIComponent(chosen.id)}`, { method: 'DELETE' });
      state.selectedCaseId = '';
      state.evidence = [];
      state.selectedEvidenceId = '';
      state.selectedEvidence = null;
      state.analysis = null;
      await loadCases();
      await loadDashboard();
      navigate('investigations');
      showToast('Investigation deleted.', 'success');
    }
  );
}

async function uploadEvidence() {
  if (!state.selectedCaseId) {
    showToast('Select an investigation before uploading evidence.', 'error');
    navigate('investigations');
    return;
  }
  if (!state.file) {
    showToast('Choose an image first.', 'error');
    return;
  }
  if (state.file.size > 4 * 1024 * 1024) {
    showToast('For the free Vercel deployment, choose an image smaller than 4 MB.', 'error');
    return;
  }

  const button = qs('#analyzeButton');
  const progress = qs('#analysisProgress');
  button.disabled = true;
  progress.classList.remove('hidden');
  progress.textContent = 'Uploading evidence…';

  try {
    const form = new FormData();
    form.append('caseId', state.selectedCaseId);
    form.append('file', state.file);
    const evidence = await request('/api/evidence/upload', { method: 'POST', body: form });
    if (!evidence?.id) throw new Error('Upload succeeded but no evidence ID was returned.');

    progress.textContent = 'Running image screening and metadata extraction…';
    const analysis = await request(`/api/analysis/${encodeURIComponent(evidence.id)}`, { method: 'POST' });

    state.evidence.unshift(evidence);
    state.selectedEvidenceId = evidence.id;
    state.selectedEvidence = evidence;
    state.analysis = analysis;
    state.file = null;
    qs('#fileInput').value = '';
    qs('#selectedFile').textContent = 'No file selected';
    qs('#uploadPreview').innerHTML = '';
    qs('#uploadPreview').classList.add('hidden');
    progress.textContent = 'Analysis completed successfully.';

    renderCaseDetail();
    renderAnalysis();
    loadMetadata(evidence.id);
    await loadDashboard();
    showToast('Evidence analyzed successfully.', 'success');
  } catch (error) {
    progress.textContent = error.message;
    showToast(error.message, 'error');
  } finally {
    button.disabled = false;
  }
}

async function runAnalysis(evidenceId) {
  const button = qs('#reanalyzeButton');
  if (button) {
    button.disabled = true;
    button.textContent = 'Analyzing…';
  }
  try {
    state.analysis = await request(`/api/analysis/${encodeURIComponent(evidenceId)}`, { method: 'POST' });
    renderAnalysis();
    await loadMetadata(evidenceId);
    await Promise.allSettled([loadDashboard(), loadCases()]);
    showToast('Evidence analysis completed.', 'success');
  } catch (error) {
    showToast(error.message || 'Evidence analysis failed.', 'error');
  } finally {
    if (button) {
      button.disabled = false;
      button.textContent = 'Re-run Analysis';
    }
  }
}

async function downloadReport() {
  if (!state.selectedEvidenceId) {
    showToast('Select analyzed evidence first.', 'error');
    return;
  }
  try {
    showToast('Generating forensic PDF…');
    const report = await request(`/api/report/${encodeURIComponent(state.selectedEvidenceId)}`);
    const response = await authorizedFetch('/api/pdf/report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(report)
    });
    if (!response.ok) throw new Error(`PDF generation failed (${response.status})`);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ForenSight_Forensic_Report_${state.selectedEvidenceId}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showToast('Forensic PDF downloaded.', 'success');
  } catch (error) {
    showToast(error.message, 'error');
  }
}

function showFile(file) {
  state.file = file;
  qs('#selectedFile').textContent = file ? `${file.name} · ${formatBytes(file.size)}` : 'No file selected';
  const preview = qs('#uploadPreview');
  preview.innerHTML = '';
  if (!file || !file.type.startsWith('image/')) {
    preview.classList.add('hidden');
    return;
  }
  const url = URL.createObjectURL(file);
  preview.innerHTML = `<img src="${url}" alt="Selected evidence preview"><button type="button" class="preview-clear" id="clearPreview">Remove</button>`;
  preview.classList.remove('hidden');
  qs('#clearPreview').addEventListener('click', () => {
    state.file = null;
    qs('#fileInput').value = '';
    showFile(null);
    URL.revokeObjectURL(url);
  });
}

function bindEvents() {
  qs('#loginForm').addEventListener('submit', signIn);
  qs('#logoutButton').addEventListener('click', signOut);
  qsa('.nav-item').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.page)));
  qs('#globalRefresh').addEventListener('click', refreshAll);
  qs('#dashboardRefresh').addEventListener('click', refreshAll);
  qs('#dashboardCases').addEventListener('click', () => navigate('investigations'));
  qs('#heroGoCases').addEventListener('click', () => navigate('investigations'));
  [qs('#heroNewCase'), qs('#topNewCase'), qs('#newCaseFromCases')].forEach((button) => button?.addEventListener('click', () => openModal('caseModal')));
  qs('#refreshCases').addEventListener('click', loadCases);
  qs('#chooseFile').addEventListener('click', () => qs('#fileInput').click());
  qs('#fileInput').addEventListener('change', () => showFile(qs('#fileInput').files[0] || null));
  qs('#analyzeButton').addEventListener('click', uploadEvidence);
  qs('#caseForm').addEventListener('submit', createCase);

  const dropZone = qs('#dropZone');
  dropZone.addEventListener('dragover', (event) => { event.preventDefault(); dropZone.classList.add('dragging'); });
  dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragging'));
  dropZone.addEventListener('drop', (event) => {
    event.preventDefault();
    dropZone.classList.remove('dragging');
    const file = event.dataTransfer.files?.[0];
    if (file) showFile(file);
  });

  qsa('[data-close-modal]').forEach((button) => button.addEventListener('click', () => closeModal(button.dataset.closeModal)));
  qsa('.modal-backdrop').forEach((backdrop) => backdrop.addEventListener('click', (event) => { if (event.target === backdrop) backdrop.classList.add('hidden'); }));
}

async function refreshAll() {
  await Promise.allSettled([checkHealth(), loadDashboard(), loadCases()]);
  if (state.selectedCaseId) {
    await selectCase(state.selectedCaseId);
  }
}

function boot() {
  bindEvents();
  protectedAssetObserver.observe(root, { childList: true, subtree: true });
  showLoginGate();
}

boot();

