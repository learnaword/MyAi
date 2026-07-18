(() => {
  const AUTH_KEYS = {
    token: 'ia_token',
    userId: 'ia_userId',
    username: 'ia_username',
    role: 'ia_role'
  };

  function saveAuth(res) {
    localStorage.setItem(AUTH_KEYS.token, res.token);
    localStorage.setItem(AUTH_KEYS.userId, String(res.userId));
    localStorage.setItem(AUTH_KEYS.username, res.username || '');
    localStorage.setItem(AUTH_KEYS.role, res.role || 'USER');
  }

  function clearAuth() {
    Object.values(AUTH_KEYS).forEach((k) => localStorage.removeItem(k));
  }

  function getToken() {
    return localStorage.getItem(AUTH_KEYS.token) || '';
  }

  function getRole() {
    return localStorage.getItem(AUTH_KEYS.role) || '';
  }

  function getUsername() {
    return localStorage.getItem(AUTH_KEYS.username) || '';
  }

  function redirectByRole(role) {
    location.replace(role === 'ADMIN' ? '/obs.html' : '/index.html');
  }

  function guardPage(expectedRole) {
    const token = getToken();
    const role = getRole();
    if (!token) {
      location.replace('/login.html');
      return false;
    }
    if (role !== expectedRole) {
      redirectByRole(role);
      return false;
    }
    return true;
  }

  function authHeaders(json) {
    const headers = {};
    if (json) headers['Content-Type'] = 'application/json';
    const token = getToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    return headers;
  }

  async function parseError(res) {
    const body = await res.json().catch(() => ({}));
    if (res.status === 429) return '尝试过于频繁，请稍后再试';
    if (body.error === 'USER_NOT_FOUND') return '用户不存在';
    if (body.error === 'BAD_CREDENTIALS') return '密码错误';
    if (body.error === 'EMAIL_NOT_FOUND') return '邮箱未注册';
    if (body.error === 'CONFLICT') return body.message === 'email exists' ? '邮箱已存在' : '用户名已存在';
    if (body.error === 'MAIL_NOT_CONFIGURED') return '邮件服务未配置';
    if (body.error === 'INVALID_CODE') return '验证码无效或已过期';
    return body.message || ('请求失败 ' + res.status);
  }

  function logout() {
    clearAuth();
    location.replace('/login.html');
  }

  window.IAAuth = {
    AUTH_KEYS,
    saveAuth,
    clearAuth,
    getToken,
    getRole,
    getUsername,
    redirectByRole,
    guardPage,
    authHeaders,
    parseError,
    logout
  };
})();
