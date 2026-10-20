/* Session state stays in the server cookie, never in localStorage. */
(() => {
  'use strict';
  const roles = Object.freeze({
    ADMIN: 'Quản trị viên', WAREHOUSE_MANAGER: 'Quản lý kho',
    WAREHOUSE_STAFF: 'Nhân viên kho', DIRECTOR: 'Quản lý/Ban giám đốc'
  });
  const permissions = Object.freeze({
    reports: ['WAREHOUSE_MANAGER', 'DIRECTOR'], audit: ['ADMIN'], accounts: ['ADMIN'],
    management: ['ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DIRECTOR'],
    receipts: ['ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DIRECTOR']
  });
  let account = null, revision = 0;
  function can(feature, user = account) {
    return !!user && !!permissions[feature]?.includes(user.role);
  }
  function label(user = account) { return user ? roles[user.role] || 'Chưa được cấp vai trò' : 'Chưa đăng nhập'; }
  function publish(user, status = 'ready') {
    account = user && typeof user.username === 'string' && typeof user.role === 'string'
      ? {username: user.username, role: user.role} : null;
    document.querySelectorAll('[data-permission]').forEach(node => {
      node.hidden = !can(node.dataset.permission);
    });
    document.querySelectorAll('[data-guest]').forEach(node => { node.hidden = !!account; });
    document.querySelectorAll('[data-member]').forEach(node => { node.hidden = !account; });
    document.querySelectorAll('[data-account-name]').forEach(node => { node.textContent = account?.username || ''; });
    document.querySelectorAll('[data-account-role]').forEach(node => { node.textContent = label(); });
    document.dispatchEvent(new CustomEvent('session:updated', {detail: {account, status}}));
    return account;
  }
  async function refresh() {
    const requestRevision = ++revision;
    try {
      const response = await fetch('/api/auth/me', {
        credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}
      });
      if (requestRevision !== revision) return account;
      if (response.status === 401) return publish(null);
      if (!response.ok) throw new Error('session');
      const user = await response.json();
      if (requestRevision !== revision) return account;
      if (!user || typeof user.username !== 'string' || typeof user.role !== 'string') throw new Error('session');
      return publish(user);
    } catch (_) {
      if (requestRevision === revision) publish(null, 'unavailable');
      return null;
    }
  }
  async function write(url, payload, method = 'POST') {
    const tokenResponse = await fetch('/api/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
    if (!tokenResponse.ok) throw new Error('Không kết nối được máy chủ. Vui lòng thử lại.');
    const csrf = await tokenResponse.json();
    if (typeof csrf.headerName !== 'string' || typeof csrf.token !== 'string' || !csrf.token) {
      throw new Error('Không xác nhận được yêu cầu. Hãy tải lại trang.');
    }
    return fetch(url, {
      method, credentials: 'same-origin', cache: 'no-store',
      headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
      ...(payload === undefined ? {} : {body: JSON.stringify(payload)})
    });
  }
  function destination(next, user = account) {
    // Exact allowlist prevents external redirects and role-inappropriate landings.
    if (next === '/accounts.html' && can('accounts', user)) return next;
    if (next === '/receipts.html' && can('receipts', user)) return next;
    if (next === '/management.html' && can('management', user)) return next;
    if (next === '/reports.html' && can('reports', user)) return next;
    if (next === '/audit-logs.html' && can('audit', user)) return next;
    return '/';
  }
  document.addEventListener('session:changed', () => { refresh(); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refresh();
  });
  window.addEventListener('pageshow', event => { if (event.persisted) refresh(); });
  document.querySelectorAll('[data-logout]').forEach(button=>{
    button.addEventListener('click',async()=>{
      if(button.disabled)return;
      const error=button.parentElement.querySelector('[data-logout-error]');error.textContent='';button.disabled=true;
      try{
        const response=await write('/api/auth/logout');
        if(!response.ok)throw new Error('logout');
        await refresh();location.assign('/');
      }catch(_){error.textContent='Chưa đăng xuất được. Vui lòng thử lại.';button.disabled=false;}
    });
  });
  window.AppSession = Object.freeze({refresh, write, can, label, destination, get account() { return account; }});
  window.AppSessionReady = refresh();
})();
