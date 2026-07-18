(() => {
  if (!window.IAAuth || !window.IAAuth.guardPage('USER')) {
    return;
  }

  const { getToken, getUsername, authHeaders, parseError, logout } = window.IAAuth;
  const $ = (id) => document.getElementById(id);
  const messages = $('messages');
  const wsStatus = $('wsStatus');
  $('currentUser').textContent = getUsername() || '用户';
  $('btnLogout').onclick = () => logout();

  let ws;
  let interviewSessionId = null;
  let awaitingAnswer = false;
  let reconnectTimer = null;

  async function refreshMe() {
    try {
      const res = await fetch('/api/me', { headers: authHeaders(false) });
      if (!res.ok) return;
      const me = await res.json();
      $('currentUser').textContent = me.username || getUsername();
      const banner = $('bindEmailBanner');
      if (me.emailBound === false) {
        banner.hidden = false;
      } else {
        banner.hidden = true;
      }
    } catch (_) { /* ignore */ }
  }

  $('btnBindEmail').onclick = async () => {
    const err = $('bindErr');
    err.textContent = '';
    const res = await fetch('/api/me/bind-email', {
      method: 'POST',
      headers: authHeaders(true),
      body: JSON.stringify({ email: $('bindEmail').value.trim() })
    });
    if (!res.ok) {
      err.textContent = await parseError(res);
      return;
    }
    $('bindEmailBanner').hidden = true;
  };

  function addMsg(role, type, content) {
    const el = document.createElement('div');
    el.className = `msg ${role}`;
    const meta = document.createElement('span');
    meta.className = 'meta';
    meta.textContent = type || role;
    el.appendChild(meta);
    el.appendChild(document.createTextNode(content || ''));
    messages.appendChild(el);
    messages.scrollTop = messages.scrollHeight;
  }

  function connect() {
    const token = getToken();
    if (!token) {
      location.replace('/login.html');
      return;
    }
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`);
    ws.onopen = () => {
      wsStatus.textContent = '已连接';
      wsStatus.classList.add('ok');
    };
    ws.onclose = () => {
      wsStatus.textContent = '已断开';
      wsStatus.classList.remove('ok');
      if (!reconnectTimer) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          if (!getToken()) {
            addMsg('bot', 'error', '用户未登录');
            location.replace('/login.html');
            return;
          }
          wsStatus.textContent = '重连中…';
          connect();
        }, 1500);
      }
    };
    ws.onerror = () => {
      wsStatus.textContent = '连接异常（若刚登录失败，请确认账号为普通用户）';
    };
    ws.onmessage = (ev) => {
      let msg;
      try { msg = JSON.parse(ev.data); } catch { return; }
      const type = msg.type || 'message';
      const content = msg.content || msg.error || JSON.stringify(msg.data || {}, null, 2);
      addMsg('bot', type, content);
      if (msg.sessionId) interviewSessionId = msg.sessionId;
      if (type === 'question' || type === 'followup') awaitingAnswer = true;
      if (type === 'done' || type === 'evaluation' || type === 'quit') awaitingAnswer = false;
    };
  }

  function send(payload) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      addMsg('bot', 'error', 'WebSocket 未连接');
      return;
    }
    ws.send(JSON.stringify(payload));
  }

  function fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result || '';
        const base64 = String(result).split(',')[1] || '';
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  $('btnSend').onclick = async () => {
    const text = $('input').value.trim();
    if (!text) return;
    addMsg('user', awaitingAnswer ? 'answer' : 'chat', text);
    $('input').value = '';
    if (awaitingAnswer) {
      send({ type: 'answer', answer: text, sessionId: interviewSessionId });
      awaitingAnswer = false;
    } else {
      send({ type: 'chat', content: text });
    }
  };

  // IME（中文等输入法）组字时：Enter 只确认拼音，不发送
  $('input').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' || e.shiftKey) return;
    if (e.isComposing || e.keyCode === 229) return;
    e.preventDefault();
    $('btnSend').click();
  });

  $('btnStart').onclick = async () => {
    const payload = {
      type: 'start_interview',
      jd: $('jdText').value.trim(),
      jdUrl: $('jdUrl').value.trim(),
      resumeText: $('resumeText').value.trim(),
    };
    const file = $('resumeFile').files[0];
    if (file) {
      payload.resumeFilename = file.name;
      payload.resumeBase64 = await fileToBase64(file);
    }
    addMsg('user', 'start_interview', '请求开始面试');
    send(payload);
  };

  $('btnQuit').onclick = () => send({ type: 'quit' });

  $('btnUpload').onclick = async () => {
    const file = $('bankFile').files[0];
    if (!file) {
      addMsg('bot', 'error', '请先选择题库文件');
      return;
    }
    const fileBase64 = await fileToBase64(file);
    addMsg('user', 'upload_questions', `上传 ${file.name}`);
    send({ type: 'upload_questions', filename: file.name, fileBase64 });
  };

  refreshMe();
  connect();
})();
