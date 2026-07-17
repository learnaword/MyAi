(() => {
  const $ = (id) => document.getElementById(id);
  const messages = $("messages");
  const wsStatus = $("wsStatus");
  let ws;
  let interviewSessionId = null;
  let awaitingAnswer = false;

  function addMsg(role, type, content) {
    const el = document.createElement("div");
    el.className = `msg ${role}`;
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent = type || role;
    el.appendChild(meta);
    el.appendChild(document.createTextNode(content || ""));
    messages.appendChild(el);
    messages.scrollTop = messages.scrollHeight;
  }

  function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    ws = new WebSocket(`${proto}://${location.host}/ws`);
    ws.onopen = () => {
      wsStatus.textContent = "已连接";
      wsStatus.classList.add("ok");
    };
    ws.onclose = () => {
      wsStatus.textContent = "已断开，重连中…";
      wsStatus.classList.remove("ok");
      setTimeout(connect, 1500);
    };
    ws.onerror = () => wsStatus.textContent = "连接异常";
    ws.onmessage = (ev) => {
      let msg;
      try { msg = JSON.parse(ev.data); } catch { return; }
      const type = msg.type || "message";
      const content = msg.content || msg.error || JSON.stringify(msg.data || {}, null, 2);
      addMsg("bot", type, content);
      if (msg.sessionId) interviewSessionId = msg.sessionId;
      if (type === "question" || type === "followup") awaitingAnswer = true;
      if (type === "done" || type === "evaluation" || type === "quit") awaitingAnswer = false;
    };
  }

  function send(payload) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      addMsg("bot", "error", "WebSocket 未连接");
      return;
    }
    ws.send(JSON.stringify(payload));
  }

  function fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result || "";
        const base64 = String(result).split(",")[1] || "";
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  $("btnSend").onclick = async () => {
    const text = $("input").value.trim();
    if (!text) return;
    addMsg("user", awaitingAnswer ? "answer" : "chat", text);
    $("input").value = "";
    if (awaitingAnswer) {
      send({ type: "answer", answer: text, sessionId: interviewSessionId });
      awaitingAnswer = false;
    } else {
      send({ type: "chat", content: text });
    }
  };

  $("input").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      $("btnSend").click();
    }
  });

  $("btnStart").onclick = async () => {
    const payload = {
      type: "start_interview",
      jd: $("jdText").value.trim(),
      jdUrl: $("jdUrl").value.trim(),
      resumeText: $("resumeText").value.trim(),
    };
    const file = $("resumeFile").files[0];
    if (file) {
      payload.resumeFilename = file.name;
      payload.resumeBase64 = await fileToBase64(file);
    }
    addMsg("user", "start_interview", "请求开始面试");
    send(payload);
  };

  $("btnQuit").onclick = () => send({ type: "quit" });

  $("btnUpload").onclick = async () => {
    const file = $("bankFile").files[0];
    if (!file) {
      addMsg("bot", "error", "请先选择题库文件");
      return;
    }
    const fileBase64 = await fileToBase64(file);
    addMsg("user", "upload_questions", `上传 ${file.name}`);
    send({ type: "upload_questions", filename: file.name, fileBase64 });
  };

  connect();
})();
