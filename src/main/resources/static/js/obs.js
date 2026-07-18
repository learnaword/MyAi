(function () {
  const tokenInput = document.getElementById('obsToken');
  const traceInput = document.getElementById('traceIdInput');
  const fromInput = document.getElementById('fromInput');
  const toInput = document.getElementById('toInput');
  const traceView = document.getElementById('traceView');
  const statsBody = document.querySelector('#statsTable tbody');
  const errEl = document.getElementById('err');

  let obsToken = sessionStorage.getItem('obsAdminToken') || '';
  tokenInput.value = obsToken;

  tokenInput.addEventListener('change', () => {
    obsToken = tokenInput.value.trim();
    sessionStorage.setItem('obsAdminToken', obsToken);
  });

  function headers() {
    return { 'X-Obs-Admin-Token': obsToken || tokenInput.value.trim() };
  }

  function showErr(e) {
    errEl.textContent = e && e.message ? e.message : String(e);
  }

  function clearErr() {
    errEl.textContent = '';
  }

  function toIso(localValue) {
    if (!localValue) throw new Error('请填写 From/To');
    const d = new Date(localValue);
    if (Number.isNaN(d.getTime())) throw new Error('时间格式无效');
    return d.toISOString();
  }

  async function fetchJson(url) {
    const res = await fetch(url, { headers: headers() });
    const text = await res.text();
    let body;
    try { body = text ? JSON.parse(text) : {}; } catch { body = { message: text }; }
    if (!res.ok) {
      throw new Error((body.error || res.status) + ': ' + (body.message || text));
    }
    return body;
  }

  document.getElementById('btnLoadTrace').addEventListener('click', async () => {
    clearErr();
    try {
      const id = traceInput.value.trim();
      if (!id) throw new Error('请填写 TraceId');
      const data = await fetchJson('/api/observability/traces/' + encodeURIComponent(id) + '?includeAttributes=true');
      traceView.textContent = JSON.stringify(data, null, 2);
    } catch (e) {
      showErr(e);
    }
  });

  document.getElementById('btnLoadStats').addEventListener('click', async () => {
    clearErr();
    try {
      const from = toIso(fromInput.value);
      const to = toIso(toInput.value);
      const q = new URLSearchParams({ from, to });
      const [tokens, rag, tools, agents] = await Promise.all([
        fetchJson('/api/observability/stats/tokens?' + q),
        fetchJson('/api/observability/stats/rag?' + q),
        fetchJson('/api/observability/stats/tools?' + q),
        fetchJson('/api/observability/stats/agents?' + q)
      ]);
      const rows = [
        ['Token llmCalls', tokens.totals && tokens.totals.llmCalls],
        ['Token sumCost', tokens.totals && tokens.totals.sumCostAmount],
        ['RAG retrieves', rag.retrieves],
        ['RAG emptyRate', rag.emptyRate],
        ['RAG hitRate', rag.hitRate],
        ['Tools rows', (tools.rows || []).length],
        ['Agents rows', (agents.rows || []).length]
      ];
      statsBody.innerHTML = rows.map(([k, v]) => '<tr><td>' + k + '</td><td>' + v + '</td></tr>').join('');
      traceView.textContent = JSON.stringify({ tokens, rag, tools, agents }, null, 2);
    } catch (e) {
      showErr(e);
    }
  });

  document.getElementById('btnStatus').addEventListener('click', async () => {
    clearErr();
    try {
      const data = await fetchJson('/api/observability/status');
      traceView.textContent = JSON.stringify(data, null, 2);
    } catch (e) {
      showErr(e);
    }
  });

  // default last 24h
  const now = new Date();
  const from = new Date(now.getTime() - 24 * 3600 * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  const fmt = (d) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
    + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  fromInput.value = fmt(from);
  toInput.value = fmt(now);
})();
