(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    token: localStorage.getItem("token"),
    authMode: "login",
    quiz: null,
    generated: null,
    interview: null,
    voiceRecorder: null,
    voiceStream: null,
    voiceChunks: [],
    voiceCaptureCancelled: false,
  };

  const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (char) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));

  function icon(name, className = "") {
    const paths = {
      book: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21z"/><path d="M4 5.5v13A2.5 2.5 0 0 1 6.5 16H20M8 7h8M8 10h6"/>',
      grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
      sparkle: '<path d="m12 3 1.9 5.8L20 11l-6.1 2.1L12 19l-1.9-5.9L4 11l6.1-2.2z"/><path d="m19 14 1 2.5 2.5 1-2.5 1L19 21l-1-2.5-2.5-1 2.5-1z"/>',
      upload: '<path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5"/><path d="M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/>',
      arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
      check: '<path d="m5 12 4 4L19 6"/>',
      chart: '<path d="M4 19V5m0 14h17M8 15l4-4 3 2 5-6"/>',
      shield: '<path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11"/><path d="m9 12 2 2 4-4"/>',
      microphone: '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8"/>',
      back: '<path d="m15 18-6-6 6-6"/>',
      close: '<path d="m18 6-12 12M6 6l12 12"/>',
      history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 2"/>',
      scan: '<path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3"/><path d="M8 9h8M8 12h8M8 15h5"/>',
    };
    return `<svg class="icon ${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || ""}</svg>`;
  }

  function showMsg(text, kind = "error") {
    const message = $("msg");
    message.className = text ? `notice ${kind}` : "notice";
    message.textContent = text;
    message.hidden = !text;
  }

  const QUESTION_MODES = { multiple_choice: "Multiple choice", subjective: "Subjective", mixed: "Both" };

  function formatDate(value) {
    return value ? new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
      : "Date not recorded";
  }

  const percent = (value) => `${Math.round(value * 100)}%`;

  // One wording for AI-writing estimates across quiz feedback, test reviews and authenticity checks.
  // 60% matches the threshold the detector uses to flag text.
  function aiBand(probability) {
    if (probability >= 0.6) return { tone: "high", label: "Likely AI-written" };
    if (probability >= 0.35) return { tone: "mid", label: "Uncertain: mixed signals" };
    return { tone: "low", label: "Likely human-written" };
  }

  function aiMeter(probability, { title = "AI-WRITING CHECK", feedback = "", note = "" } = {}) {
    const band = aiBand(probability);
    return `<div class="ai-writing-result ai-${band.tone}">
      <span class="eyebrow">${title}</span>
      <div class="probability-row"><span class="ai-badge ai-badge-${band.tone}">${band.label}</span>
        <strong>${percent(probability)} AI probability</strong></div>
      <div class="probability-track"><span style="width:${percent(probability)}"></span></div>
      ${feedback ? `<p>${esc(feedback)}</p>` : ""}
      ${note ? `<small>${esc(note)}</small>` : ""}
    </div>`;
  }

  async function api(path, { method = "GET", body, form, auth = true } = {}) {
    const headers = {};
    if (auth && state.token) headers.Authorization = "Bearer " + state.token;
    let payload;
    if (form) payload = form;
    else if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      payload = JSON.stringify(body);
    }

    const response = await fetch("/api" + path, { method, headers, body: payload });
    const raw = await response.text();
    let data = raw;
    try { data = raw ? JSON.parse(raw) : null; } catch { /* Keep plain text errors readable. */ }

    if (auth && (response.status === 401 || (response.status === 403 && !(data && data.error)))) {
      logout();
      throw new Error("Your session expired. Please sign in again.");
    }
    if (!response.ok) {
      throw new Error((typeof data === "string" ? data : data && data.error)
        || `Request failed (${response.status})`);
    }
    return data;
  }

  function logout() {
    stopVoiceRecognition();
    window.speechSynthesis?.cancel();
    state.token = null;
    state.quiz = null;
    state.generated = null;
    localStorage.removeItem("token");
    location.hash = "#/login";
    route();
  }

  async function signIn(credentials) {
    const response = await api("/auth/login", { method: "POST", body: credentials, auth: false });
    state.token = response.token;
    localStorage.setItem("token", response.token);
    location.hash = "#/subjects";
  }

  function renderNav(active = "subjects") {
    $("nav").innerHTML = `
      <a class="brand" href="#/subjects" aria-label="Smart Study dashboard">
        <span class="brand-mark">${icon("book")}</span>
        <span>smart<span class="brand-light">study</span></span>
      </a>
      <div class="nav-links">
        <a class="${active === "subjects" ? "active" : ""}" href="#/subjects" title="My subjects">${icon("grid")} My subjects</a>
        <a class="${active === "tests" ? "active" : ""}" href="#/tests" title="My tests">${icon("history")} My tests</a>
        <a class="${active === "authenticity" ? "active" : ""}" href="#/authenticity" title="Authenticity check">${icon("scan")} Authenticity check</a>
        <a class="${active === "interview" ? "active" : ""}" href="#/interview" title="AI Interview">${icon("sparkle")} AI Interview</a>
      </div>
      <button class="nav-profile" data-action="logout" aria-label="Log out" title="Log out">
        <span class="avatar">S</span><span class="profile-label">Sign out</span>
      </button>`;
  }

  function renderAuth() {
    $("nav").innerHTML = `<a class="brand" href="#/login"><span class="brand-mark">${icon("book")}</span>
      <span>smart<span class="brand-light">study</span></span></a>`;
    const isLogin = state.authMode === "login";
    $("view").innerHTML = `
      <section class="auth-layout">
        <div class="auth-story">
          <span class="eyebrow eyebrow-light">YOUR PERSONAL STUDY SPACE</span>
          <h1>Study smarter.<br><span>Know what to expect.</span></h1>
          <p>Turn previous exam papers into focused practice. Follow the topics, learn as you go,
            and build confidence one question at a time.</p>
          <div class="auth-points">
            <span>${icon("upload")} Start with your papers</span>
            <span>${icon("sparkle")} Find topics and make fresh questions</span>
            <span>${icon("chart")} Practice at your own level</span>
          </div>
          <div class="auth-decoration" aria-hidden="true"><span></span><span></span><span></span></div>
        </div>
        <div class="auth-panel">
          <span class="eyebrow">${isLogin ? "WELCOME BACK" : "GET STARTED"}</span>
          <h2>${isLogin ? "Sign in to continue" : "Create your account"}</h2>
          <p class="muted">${isLogin ? "Your next focused study session starts here." : "Set up your study space in a few seconds."}</p>
          ${isLogin ? `
            <form data-form="login" class="auth-form">
              <label>Email address<input name="email" type="email" placeholder="you@example.com" autocomplete="email" required></label>
              <label>Password<input name="password" type="password" placeholder="Your password" autocomplete="current-password" required></label>
              <button class="button button-primary button-wide">Sign in ${icon("arrow")}</button>
            </form>` : `
            <form data-form="register" class="auth-form">
              <label>Your name<input name="fullName" placeholder="Alex Morgan" autocomplete="name" required></label>
              <label>Email address<input name="email" type="email" placeholder="you@example.com" autocomplete="email" required></label>
              <label>Password<input name="password" type="password" placeholder="At least 6 characters" minlength="6" autocomplete="new-password" required></label>
              <button class="button button-primary button-wide">Create account ${icon("arrow")}</button>
            </form>`}
          <p class="auth-switch">${isLogin ? "New to Smart Study?" : "Already have an account?"}
            <button class="text-button" data-action="toggleAuth">${isLogin ? "Create an account" : "Sign in"}</button>
          </p>
        </div>
      </section>`;
  }

  async function renderSubjects() {
    renderNav("subjects");
    const subjects = await api("/subjects");
    const subjectCards = subjects.map((subject, index) => `
      <a class="subject-card" href="#/subjects/${subject.id}">
        <span class="subject-icon subject-icon-${index % 4}">${icon("book")}</span>
        <span class="subject-card-copy"><strong>${esc(subject.name)}</strong>
          <small>Open your study workspace</small></span>
        <span class="subject-arrow">${icon("arrow")}</span>
      </a>`).join("");

    $("view").innerHTML = `
      <section class="welcome-row">
        <div><span class="eyebrow">YOUR STUDY DASHBOARD</span>
          <h1>Make every paper <span class="text-accent">count.</span></h1>
          <p class="lead">Bring your past papers. We’ll find the patterns and turn them into practice.</p>
        </div>
        <div class="welcome-art" aria-hidden="true"><div class="art-orbit"></div><span>◎</span><i>✳</i></div>
      </section>
      <section class="dashboard-stats" aria-label="How Smart Study works">
        <div class="stat-card"><span class="stat-icon stat-blue">${icon("upload")}</span><span><strong>Upload</strong><small>Past papers, PDF</small></span><b>01</b></div>
        <div class="stat-card"><span class="stat-icon stat-violet">${icon("chart")}</span><span><strong>Discover</strong><small>Topics found for you</small></span><b>02</b></div>
        <div class="stat-card"><span class="stat-icon stat-green">${icon("sparkle")}</span><span><strong>Practice</strong><small>Adaptive questions</small></span><b>03</b></div>
      </section>
      <section class="section-heading">
        <div><span class="eyebrow">YOUR WORKSPACES</span><h2>Subjects</h2></div>
        <span class="count-pill">${subjects.length} ${subjects.length === 1 ? "subject" : "subjects"}</span>
      </section>
      <section class="subject-grid">
        ${subjectCards || `<div class="empty-card"><span class="empty-icon">${icon("book")}</span>
          <h3>Your first subject starts here</h3><p>Create a subject, then upload a previous paper to build a practice set.</p></div>`}
        <form class="subject-create" data-form="subject">
          <label for="subject-name">${icon("sparkle")} Add a subject</label>
          <div class="create-row"><input id="subject-name" name="name" placeholder="e.g. Data Structures" required>
            <button class="button button-dark" aria-label="Add subject">${icon("arrow")}</button></div>
        </form>
      </section>
      <p class="privacy-note">${icon("shield")} Your papers are used to prepare your study set for your account.</p>`;
  }

  async function renderInterviewSetup() {
    state.interview = null;
    renderNav("interview");
    const subjects = await api("/subjects");
    $("view").innerHTML = `
      <a class="back-link" href="#/subjects">${icon("back")} Dashboard</a>
      <section class="subject-hero"><div><span class="eyebrow">VOICE-ENABLED PRACTICE</span>
        <h1>Practice an interview</h1>
        <p class="lead">Choose a subject or engineering role. The interviewer adapts follow-ups to your answers.</p>
      </div><div class="subject-hero-mark">${icon("sparkle")}</div></section>
      <section class="panel interview-setup">
        <span class="eyebrow">SET UP YOUR SESSION</span>
        <form data-form="startInterview" class="interview-setup-form">
          <label>Interview focus
            <select name="targetType" id="interview-target-type">
              <option value="subject">Academic subject</option>
              <option value="engineering_role">Engineering role</option>
            </select>
          </label>
          <label id="interview-subject-field">Choose a subject
            <select name="subjectTarget">
              ${subjects.map((subject) => `<option value="${esc(subject.name)}">${esc(subject.name)}</option>`).join("")}
            </select>
          </label>
          <label id="interview-role-field" hidden>Engineering role
            <input name="roleTarget" maxlength="120" placeholder="e.g. Backend Engineer" disabled>
          </label>
          <label>Experience level
            <select name="level">
              <option value="beginner">Beginner</option>
              <option value="intermediate" selected>Intermediate</option>
              <option value="advanced">Advanced</option>
            </select>
          </label>
          <label>Number of questions
            <input name="questionCount" type="number" min="1" max="20" value="5" required>
          </label>
          <p class="interview-privacy">Voice recordings are transcribed by the local Smart Study service and are not sent to an external speech provider.
            Your answer text is sent to the configured AI service for feedback. You can type your answer instead.</p>
          <button class="button button-primary" data-busy="Preparing your interview…">
            Start interview ${icon("arrow")}
          </button>
        </form>
        ${subjects.length ? "" : `<p class="muted">Create a subject first, or select an engineering role above.</p>`}
      </section>`;
    const subjectField = $("interview-subject-field");
    subjectField.hidden = !subjects.length;
    const subjectSelect = subjectField.querySelector("select");
    subjectSelect.disabled = !subjects.length;
    const mode = $("interview-target-type");
    mode.addEventListener("change", () => {
      const isSubject = mode.value === "subject";
      subjectField.hidden = !isSubject;
      subjectSelect.disabled = !isSubject;
      const roleField = $("interview-role-field");
      roleField.hidden = isSubject;
      roleField.querySelector("input").disabled = isSubject;
      roleField.querySelector("input").required = !isSubject;
    });
  }

  function renderInterviewSession() {
    const interview = state.interview;
    renderNav("interview");
    const historyHtml = interview.history.map((turn) => `
      <article class="interview-transcript-turn ${turn.speaker}">
        <strong>${turn.speaker === "interviewer" ? "Interviewer" : "You"}</strong>
        <p>${esc(turn.content)}</p>
      </article>`).join("");
    $("view").innerHTML = `
      <div class="interview-topline">
        <div><span class="eyebrow">LIVE PRACTICE SESSION</span>
          <h1>${esc(interview.target)}</h1>
          <p class="muted">${interview.level[0].toUpperCase() + interview.level.slice(1)} level · ${esc(interview.targetType === "subject" ? "Subject interview" : "Engineering-role interview")}</p>
        </div>
        <button class="text-button" data-action="endInterview">End interview</button>
      </div>
      <div class="quiz-progress-label"><span>Question ${interview.questionNumber} of ${interview.questionCount}</span>
        <span>${Math.round((interview.questionNumber - 1) / interview.questionCount * 100)}% complete</span></div>
      <div class="progress-track"><span style="width:${Math.max(6, (interview.questionNumber - 1) / interview.questionCount * 100)}%"></span></div>
      <section class="interview-live">
        <div class="interviewer-question">
          <span class="eyebrow">YOUR QUESTION</span>
          <p>${esc(interview.currentQuestion)}</p>
          <button class="button button-secondary" data-action="speakQuestion">${icon("sparkle")} Read question aloud</button>
        </div>
        ${interview.lastFeedback ? `<div class="interview-feedback">
          <span class="eyebrow">FEEDBACK ON YOUR LAST ANSWER · ${Math.round(interview.lastScore * 100)}%</span>
          <p>${esc(interview.lastFeedback)}</p></div>` : ""}
        <form data-form="submitInterviewAnswer" class="interview-answer-form">
          <label for="interview-answer">Your answer</label>
          <textarea id="interview-answer" name="answer" rows="6" maxlength="12000"
            placeholder="Type your answer, or use the microphone to dictate…" required></textarea>
          <div class="interview-controls">
            <button type="button" class="button button-secondary" data-action="listenAnswer">
              ${icon("microphone")} Record answer
            </button>
            <button class="button button-primary" data-busy="Listening to your answer…">
              Submit answer ${icon("arrow")}
            </button>
          </div>
        </form>
        <details class="interview-transcript"><summary>Interview transcript (${interview.history.length} turns)</summary>
          <div class="interview-transcript-list">${historyHtml || `<p class="muted">Your interview transcript will appear here.</p>`}</div>
        </details>
      </section>`;
  }

  function renderInterviewComplete(result) {
    const interview = state.interview;
    interview.complete = true;
    interview.lastFeedback = result.feedback;
    interview.lastScore = result.score;
    interview.history.push({ speaker: "interviewer", content: result.feedback });
    renderNav("interview");
    const candidateScores = interview.scores;
    const average = candidateScores.length
      ? Math.round(candidateScores.reduce((total, score) => total + score, 0) / candidateScores.length * 100)
      : 0;
    $("view").innerHTML = `
      <section class="summary-card interview-summary">
        <span class="summary-icon">${icon("chart")}</span>
        <span class="eyebrow eyebrow-green">INTERVIEW COMPLETE</span>
        <h2>Session review</h2>
        <p>${esc(result.feedback)}</p>
        <div class="summary-score"><strong>${average}%</strong><span>average answer score</span></div>
        <p class="muted">Scores reflect estimated technical content only—not accent, grammar, or speaking speed.</p>
        <details class="interview-transcript" open>
          <summary>Review transcript (${interview.history.length} turns)</summary>
          <div class="interview-transcript-list">${interview.history.map((turn) => `
            <article class="interview-transcript-turn ${turn.speaker}">
              <strong>${turn.speaker === "interviewer" ? "Interviewer" : "You"}</strong>
              <p>${esc(turn.content)}</p>
            </article>`).join("")}</div>
        </details>
        <button class="button button-primary" data-action="newInterview">${icon("back")} New interview</button>
      </section>`;
  }

  async function renderSubject(id) {
    renderNav("subjects");
    const [subject, papers, bank] = await Promise.all([
      api(`/subjects/${id}`), api(`/subjects/${id}/papers`), api(`/subjects/${id}/question-bank`),
    ]);
    state.generated = null;
    state.quiz = null;
    $("view").innerHTML = `
      <a class="back-link" href="#/subjects">${icon("back")} All subjects</a>
      <section class="subject-hero">
        <div><span class="eyebrow">STUDY WORKSPACE</span><h1>${esc(subject.name)}</h1>
          <p class="lead">One upload starts the whole pipeline. Your topic map and practice set are created together.</p>
        </div>
        <div class="subject-hero-mark">${icon("book")}</div>
      </section>
      <section class="pipeline" aria-label="Study pipeline">
        <div class="pipeline-step"><span>1</span><div><strong>Upload papers</strong><small>PDF past exams</small></div></div>
        <i class="pipeline-line"></i>
        <div class="pipeline-step"><span>2</span><div><strong>Find topics</strong><small>Patterns from papers</small></div></div>
        <i class="pipeline-line"></i>
        <div class="pipeline-step"><span>3</span><div><strong>Practice adaptively</strong><small>Difficulty adjusts to you</small></div></div>
      </section>
      <section class="workspace-grid">
        <div class="panel upload-panel">
          <div class="panel-heading"><span class="panel-icon">${icon("upload")}</span>
            <div><span class="eyebrow">START YOUR PIPELINE</span><h2>Build a practice set</h2></div>
          </div>
          <p class="muted">Choose 1–5 previous exam papers and how many new questions you want.</p>
          <form data-form="generateFromPapers" data-subject="${id}" class="generation-form">
            <label class="dropzone" for="paper-files">
              <input id="paper-files" type="file" name="files" accept="application/pdf,.pdf" multiple required>
              <span class="dropzone-icon">${icon("upload")}</span>
              <strong>Choose your past papers</strong>
              <span class="dropzone-copy">Click to browse · PDF files · 1 to 5 papers</span>
              <span id="file-names" class="file-names">No files selected</span>
            </label>
            <div class="form-bottom">
              <label class="count-input" for="question-count"><span>Questions to generate</span>
                <span class="count-input-control"><input id="question-count" type="number" name="count" min="1" max="50" value="10" required>
                  <small>1–50</small></span>
              </label>
              <label class="format-input" for="question-format"><span>Question format</span>
                <select id="question-format" name="format">
                  <option value="multiple_choice">Multiple choice</option>
                  <option value="subjective">Subjective</option>
                  <option value="mixed">Both (MCQ + subjective)</option>
                </select>
              </label>
              <button class="button button-primary generate-button" data-busy="Preparing your study set…">
                ${icon("sparkle")} Generate my questions
              </button>
            </div>
            <div class="pipeline-progress" id="pipeline-progress" hidden aria-live="polite">
              <span class="spinner"></span><span><strong>Pipeline in progress</strong>
                <small>Reading papers (including OCR for scans), finding topics and preparing your questions. Large scans can take several minutes; keep this page open.</small></span>
            </div>
          </form>
        </div>
        <aside class="side-panel">
          <span class="side-panel-icon">${icon("chart")}</span><span class="eyebrow">WHAT HAPPENS NEXT</span>
          <h3>From old papers to fresh practice</h3>
          <ul class="process-list">
            <li><span>01</span><div><strong>Read</strong><small>Text is extracted; scanned pages use OCR.</small></div></li>
            <li><span>02</span><div><strong>Understand</strong><small>Recurring topics and exam patterns are identified.</small></div></li>
            <li><span>03</span><div><strong>Create</strong><small>New questions are generated across those topics.</small></div></li>
          </ul>
          <p class="side-note">Scanned pages are read with OCR. Clear, upright scans work best. Review your questions before starting a quiz.</p>
        </aside>
      </section>
      ${practicePanel(id, bank)}
      <section id="generated-section" class="generated-section" hidden></section>
      <section id="quiz-section" class="quiz-section" hidden></section>
      <section class="papers-section">
        <div class="section-heading"><div><span class="eyebrow">YOUR SOURCE MATERIAL</span><h2>Uploaded papers</h2></div>
          <span class="count-pill">${papers.length} ${papers.length === 1 ? "paper" : "papers"}</span></div>
        <div class="paper-list">${papers.map((paper) => `
          <div class="paper-row"><span class="paper-icon">PDF</span>
            <span class="paper-name"><strong>${esc(paper.originalFilename)}</strong>
              <small>${paper.textLength.toLocaleString()} characters extracted</small></span>
            <span class="paper-date">${paper.examYear ? esc(paper.examYear) : "Past paper"}</span></div>`).join("")
          || `<p class="empty-papers">Your uploaded papers will show here.</p>`}</div>
      </section>`;
  }

  function practicePanel(subjectId, bank) {
    const total = bank.multipleChoice + bank.subjective;
    if (!total) return "";
    const counts = { multiple_choice: bank.multipleChoice, subjective: bank.subjective,
      mixed: bank.multipleChoice && bank.subjective ? total : 0 };
    const preferred = counts.mixed ? "mixed" : counts.subjective ? "subjective" : "multiple_choice";
    const options = Object.entries(counts).map(([value, count]) => `
      <option value="${value}" ${count ? "" : "disabled"} ${value === preferred ? "selected" : ""}>
        ${QUESTION_MODES[value]} (${count})</option>`).join("");
    return `<section class="panel practice-panel">
      <div class="panel-heading"><span class="panel-icon">${icon("chart")}</span>
        <div><span class="eyebrow">PRACTICE ANYTIME</span><h2>Take a test from your question bank</h2></div></div>
      <p class="muted">${bank.multipleChoice} multiple-choice and ${bank.subjective} subjective questions are saved
        for this subject. Choose what to practise; every test is saved in My tests for later review.</p>
      <form data-form="practiceBank" data-subject="${subjectId}" class="practice-form">
        <label class="format-input">Question type<select name="questionType">${options}</select></label>
        <label class="count-input"><span>Questions</span>
          <span class="count-input-control"><input type="number" name="count" min="1"
            max="${Math.min(50, total)}" value="${Math.min(10, total)}" required></span></label>
        <button class="button button-primary" data-busy="Starting your test…">Start test ${icon("arrow")}</button>
      </form>
    </section>`;
  }

  function showGenerated(result) {
    state.generated = result;
    const section = $("generated-section");
    section.hidden = false;
    section.innerHTML = `
      <div class="section-heading generated-heading"><div><span class="eyebrow eyebrow-green">YOUR STUDY SET IS READY</span>
        <h2>${result.questions.length} questions, made for this subject</h2></div>
        <span class="ready-badge">${icon("check")} Ready to practice</span></div>
      <div class="topic-tags"><strong>Topics identified</strong>
        ${result.topics.map((topic) => `<span class="topic-tag">${esc(topic)}</span>`).join("")}</div>
      <div class="question-preview-list">${result.questions.map((question, index) => `
        <article class="question-preview"><span class="question-number">${String(index + 1).padStart(2, "0")}</span>
          <div class="question-preview-body"><div class="question-meta"><span>${esc(question.topic)}</span>
            <span>${question.questionType === "SUBJECTIVE" ? "Subjective" : "Multiple choice"}</span></div>
            <h3>${esc(question.prompt)}</h3>
            ${question.questionType === "SUBJECTIVE" ? "" : `
              <ol type="A">${question.options.map((option) => `<li>${esc(option)}</li>`).join("")}</ol>`}</div>
        </article>`).join("")}</div>
      <div class="start-quiz-row"><p>${icon("chart")} Your quiz will adjust question difficulty as you answer.</p>
        <button class="button button-primary" data-action="startGeneratedQuiz" data-subject="${esc(location.hash.match(/\d+/)?.[0] || "")}">
          Start adaptive practice ${icon("arrow")}
        </button></div>`;
    section.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function renderQuestion(question) {
    const quizSection = $("quiz-section");
    quizSection.hidden = false;
    const subjective = question.questionType === "SUBJECTIVE";
    quizSection.innerHTML = `
      <div class="quiz-topline"><div><span class="eyebrow">ADAPTIVE PRACTICE</span>
        <h2>Your quiz</h2></div><button class="text-button" data-action="backToQuestions">Exit quiz</button></div>
      <div class="quiz-progress-label"><span>Question ${question.questionNumber} of ${question.totalQuestions}</span>
        <span>${Math.round((question.questionNumber - 1) / question.totalQuestions * 100)}% complete</span></div>
      <div class="progress-track"><span style="width:${Math.max(6, (question.questionNumber - 1) / question.totalQuestions * 100)}%"></span></div>
      <article class="quiz-card"><div class="question-meta"><span>Question ${String(question.questionNumber).padStart(2, "0")}</span>
        <span>${icon("sparkle")} Adaptive difficulty</span></div>
        <h3>${esc(question.prompt)}</h3>
        ${subjective ? `
          <form class="subjective-answer-form" data-form="subjectiveAnswer">
            <label for="subjective-answer">Your answer</label>
            <textarea id="subjective-answer" name="answerText" rows="7" maxlength="12000"
              placeholder="Write your explanation or solution here…"></textarea>
            <p class="answer-input-note">Enter at least 50 characters, or attach a text-based PDF answer.</p>
            <label class="answer-upload" for="subjective-answer-pdf">
              <span>Or upload your written answer as a PDF</span>
              <input id="subjective-answer-pdf" type="file" name="answerPdf" accept="application/pdf,.pdf">
            </label>
            <button class="button button-primary" data-busy="Grading and checking writing…">
              Submit answer ${icon("arrow")}
            </button>
          </form>` : `
          <div class="quiz-options">${question.options.map((option, index) => `
          <button class="quiz-option" data-action="answer" data-index="${index}">
            <span class="option-letter">${String.fromCharCode(65 + index)}</span><span>${esc(option)}</span>
          </button>`).join("")}</div>
          <p class="quiz-hint">Choose the best answer. Your next question adapts to your progress.</p>`}
      </article>
      <div id="quiz-feedback"></div>`;
    state.quiz.current = question;
    quizSection.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function renderResult(result, currentQuestion) {
    const feedback = $("quiz-feedback");
    const options = document.querySelectorAll(".quiz-option");
    options.forEach((option, index) => {
      option.disabled = true;
      if (index === result.correctIndex) option.classList.add("answer-correct");
      else if (index === Number(document.querySelector(".quiz-option.selected")?.dataset.index)) {
        option.classList.add("answer-wrong");
      }
    });
    const subjective = currentQuestion.questionType === "SUBJECTIVE";
    const aiAssessment = result.aiWritingAssessment;
    feedback.innerHTML = `
      <div class="answer-feedback ${result.correct ? "feedback-correct" : "feedback-wrong"}">
        <span class="feedback-icon">${icon(result.correct ? "check" : "close")}</span>
        <div><strong>${subjective
          ? (result.correct ? "Your answer met the grading threshold" : "More practice recommended")
          : (result.correct ? "That’s right!" : "Not quite this time")}</strong>
          <p>${subjective
            ? `Estimated answer score: <b>${Math.round(result.score * 100)}%</b>. ${esc(result.gradingFeedback)}`
            : `The correct answer is <b>${String.fromCharCode(65 + result.correctIndex)}</b>.
              ${esc(result.explanation || "Keep going—your next question will adapt to your progress.")}`}</p></div>
      </div>
      ${aiAssessment ? aiMeter(aiAssessment.aiProbability, {
        feedback: aiAssessment.feedback,
        note: "This estimate is not proof of authorship and does not affect your answer grade or quiz difficulty.",
      }) : subjective ? `<p class="ai-unavailable">${icon("scan")} The AI-writing check was unavailable for this answer,
        so only the grade was recorded. Try again in a few minutes if the service is still starting.</p>` : ""}
      <div class="quiz-result-footer"><div><span class="eyebrow">YOUR PROGRESS</span>
        <strong>${result.correctCount} <small>correct</small> / ${result.answeredCount} <small>answered</small></strong>
        <span class="ability-label">Ability estimate ${result.abilityEstimate.toFixed(2)}</span></div>
        ${result.finished
          ? `<button class="button button-primary" data-action="finishQuiz">${icon("check")} See your results</button>`
          : `<button class="button button-primary" data-action="nextQuestion">Next question ${icon("arrow")}</button>`}
      </div>`;
    state.quiz.next = result.nextQuestion;
    state.quiz.finished = result.finished;
    state.quiz.summary = result;
  }

  function showQuizSummary() {
    const summary = state.quiz.summary;
    $("quiz-section").innerHTML = `
      <div class="summary-card"><span class="summary-icon">${icon("chart")}</span>
        <span class="eyebrow eyebrow-green">PRACTICE COMPLETE</span>
        <h2>Nice work. You showed up.</h2>
        <p>You answered <strong>${summary.answeredCount}</strong> questions and got
          <strong>${summary.correctCount}</strong> right.</p>
        <div class="summary-score"><strong>${Math.round(summary.correctCount / summary.answeredCount * 100)}%</strong>
          <span>correct answers</span></div>
        <p class="muted">This test is saved in My tests, with your answers, grades and feedback.</p>
        <div class="summary-actions">
          <a class="button button-primary" href="#/tests/${state.quiz.sessionId}">${icon("history")} Review your answers</a>
          <button class="button button-secondary" data-action="backToQuestions">${icon("back")} Back to subject</button>
        </div>
      </div>`;
    $("quiz-section").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function testScore(test) {
    const parts = [`${test.correctCount}/${test.answeredCount} correct`];
    if (test.averageWrittenScore != null) parts.push(`written answers ${percent(test.averageWrittenScore)}`);
    return parts.join(" · ");
  }

  async function renderTests() {
    renderNav("tests");
    const tests = await api("/quizzes");
    $("view").innerHTML = `
      <section class="subject-hero"><div><span class="eyebrow">SELF-EVALUATION</span>
        <h1>My tests</h1>
        <p class="lead">Every test you take is saved here with your answers, grades and feedback, so you can see what to revise.</p>
      </div><div class="subject-hero-mark">${icon("history")}</div></section>
      <section class="test-list">${tests.map((test) => `
        <a class="test-row" href="#/tests/${test.sessionId}">
          <span class="test-score">${test.answeredCount ? percent(test.correctCount / test.answeredCount) : "–"}</span>
          <span class="test-copy"><strong>${esc(test.subjectName)}</strong>
            <small>${formatDate(test.startedAt)} · ${esc(QUESTION_MODES[test.questionMode] || "Practice")}
              · ${esc(testScore(test))}</small></span>
          <span class="test-tags">
            ${test.status === "COMPLETED" ? "" : `<span class="tag tag-muted">Not finished</span>`}
            ${test.aiFlaggedCount ? `<span class="tag tag-warn">${test.aiFlaggedCount} flagged as likely AI-written</span>` : ""}
          </span>
          <span class="subject-arrow">${icon("arrow")}</span>
        </a>`).join("") || `<div class="empty-card"><span class="empty-icon">${icon("history")}</span>
          <h3>No tests yet</h3><p>Open a subject and start a test. It will appear here when you answer your first question.</p></div>`}
      </section>`;
  }

  function reviewAnswer(answer) {
    const subjective = answer.questionType === "SUBJECTIVE";
    const result = subjective
      ? `<span class="tag ${answer.correct ? "tag-good" : "tag-warn"}">Score ${answer.score != null ? percent(answer.score) : "–"}</span>`
      : `<span class="tag ${answer.correct ? "tag-good" : "tag-bad"}">${answer.correct ? "Correct" : "Incorrect"}</span>`;
    const body = subjective ? `
      <div class="review-block"><span class="eyebrow">YOUR ANSWER</span>
        <p class="written-answer">${esc(answer.answerText || "No answer text was saved for this older test.")}</p></div>
      ${answer.feedback ? `<div class="review-block"><span class="eyebrow">FEEDBACK</span><p>${esc(answer.feedback)}</p></div>` : ""}
      ${answer.aiProbability != null ? aiMeter(answer.aiProbability)
        : `<p class="ai-unavailable">${icon("scan")} No AI-writing check was recorded for this answer.</p>`}
      <details class="review-details"><summary>Model answer and marking guide</summary>
        ${answer.referenceAnswer ? `<p><strong>Model answer.</strong> ${esc(answer.referenceAnswer)}</p>` : ""}
        ${answer.explanation ? `<p><strong>Marking guide.</strong> ${esc(answer.explanation)}</p>` : ""}
      </details>` : `
      <ol class="review-options" type="A">${answer.options.map((option, index) => {
        const mark = index === answer.correctIndex ? "option-correct"
          : index === answer.selectedIndex ? "option-wrong" : "";
        const note = index === answer.correctIndex ? " (correct answer)"
          : index === answer.selectedIndex ? " (your answer)" : "";
        return `<li class="${mark}">${esc(option)}<span class="option-note">${note}</span></li>`;
      }).join("")}</ol>
      ${answer.explanation ? `<div class="review-block"><span class="eyebrow">EXPLANATION</span><p>${esc(answer.explanation)}</p></div>` : ""}`;
    return `<article class="review-card">
      <div class="question-meta"><span>Question ${String(answer.number).padStart(2, "0")} · ${esc(answer.topic)}</span>
        <span>${subjective ? "Subjective" : "Multiple choice"}</span></div>
      <div class="review-heading"><h3>${esc(answer.prompt)}</h3>${result}</div>
      ${body}
    </article>`;
  }

  async function renderTestReview(id) {
    renderNav("tests");
    const { summary, answers } = await api(`/quizzes/${id}`);
    const written = answers.filter((answer) => answer.questionType === "SUBJECTIVE");
    $("view").innerHTML = `
      <a class="back-link" href="#/tests">${icon("back")} All tests</a>
      <section class="subject-hero"><div><span class="eyebrow">TEST REVIEW</span>
        <h1>${esc(summary.subjectName)}</h1>
        <p class="lead">${formatDate(summary.startedAt)} · ${esc(QUESTION_MODES[summary.questionMode] || "Practice")}
          ${summary.status === "COMPLETED" ? "" : " · Not finished"}</p>
      </div><div class="subject-hero-mark">${icon("chart")}</div></section>
      <section class="dashboard-stats review-stats">
        <div class="stat-card"><span class="stat-icon stat-green">${icon("check")}</span>
          <span><strong>${summary.correctCount} / ${summary.answeredCount}</strong><small>answers correct</small></span></div>
        <div class="stat-card"><span class="stat-icon stat-violet">${icon("book")}</span>
          <span><strong>${summary.averageWrittenScore != null ? percent(summary.averageWrittenScore) : "–"}</strong>
            <small>average written score (${written.length} answers)</small></span></div>
        <div class="stat-card"><span class="stat-icon stat-blue">${icon("scan")}</span>
          <span><strong>${summary.aiFlaggedCount}</strong><small>answers flagged as likely AI-written</small></span></div>
      </section>
      <section class="review-list">${answers.map(reviewAnswer).join("")}</section>`;
  }

  function authenticityResult(result) {
    const flaggedPassages = result.passages.length > 1 ? `
      <div class="passage-list"><span class="eyebrow">SECTION BY SECTION</span>
        ${result.passages.map((passage, index) => {
          const band = aiBand(passage.aiProbability);
          return `<div class="passage-row ai-${band.tone}">
            <div class="probability-row"><span>Section ${index + 1} · <span class="ai-badge ai-badge-${band.tone}">${band.label}</span></span>
              <strong>${percent(passage.aiProbability)}</strong></div>
            <div class="probability-track"><span style="width:${percent(passage.aiProbability)}"></span></div>
            <p>${esc(passage.excerpt)}</p></div>`;
        }).join("")}</div>` : "";
    return `<section class="panel authenticity-result">
      <div class="panel-heading"><span class="panel-icon">${icon("scan")}</span>
        <div><span class="eyebrow">RESULT</span><h2>${esc(result.sourceName)}</h2></div></div>
      <p class="muted">${result.characterCount.toLocaleString()} characters checked
        ${result.passageCount > 1 ? ` in ${result.passageCount} sections, ${result.flaggedPassageCount} flagged` : ""}.</p>
      ${aiMeter(result.aiProbability, { title: "OVERALL ESTIMATE", feedback: result.feedback })}
      ${flaggedPassages}
    </section>`;
  }

  function recentChecks(checks) {
    if (!checks.length) return `<p class="empty-papers">Your checks will be listed here.</p>`;
    return checks.map((check) => {
      const band = aiBand(check.aiProbability);
      return `<div class="paper-row"><span class="paper-icon">${icon("scan")}</span>
        <span class="paper-name"><strong>${esc(check.sourceName)}</strong>
          <small>${formatDate(check.createdAt)} · ${check.characterCount.toLocaleString()} characters
            ${check.passageCount > 1 ? ` · ${check.flaggedPassageCount}/${check.passageCount} sections flagged` : ""}</small></span>
        <span class="ai-badge ai-badge-${band.tone}">${band.label} · ${percent(check.aiProbability)}</span></div>`;
    }).join("");
  }

  async function renderAuthenticity() {
    renderNav("authenticity");
    const checks = await api("/authenticity/checks");
    $("view").innerHTML = `
      <section class="subject-hero"><div><span class="eyebrow">CONTENT AUTHENTICITY</span>
        <h1>Check your material</h1>
        <p class="lead">Upload notes, an essay or an assignment to estimate how likely it is that parts were written by AI.</p>
      </div><div class="subject-hero-mark">${icon("scan")}</div></section>
      <section class="panel authenticity-panel">
        <form data-form="authenticity" class="authenticity-form">
          <label for="authenticity-text">Paste your text</label>
          <textarea id="authenticity-text" name="text" rows="9" maxlength="60000"
            placeholder="Paste at least 50 characters…"></textarea>
          <label class="answer-upload" for="authenticity-file">
            <span>Or upload a PDF, .txt or .md file (one or the other, not both)</span>
            <input id="authenticity-file" type="file" name="file" accept="application/pdf,.pdf,.txt,.md,text/plain,text/markdown">
          </label>
          <p class="answer-input-note">Up to 60,000 characters per check. Your text is analysed by the local Smart Study
            detector and is not stored; only the result is saved to your history.</p>
          <button class="button button-primary" data-busy="Checking your material…">${icon("scan")} Check authenticity</button>
        </form>
        <p class="detector-disclaimer">${icon("shield")} AI detection is an estimate and can be wrong in both directions,
          especially for short, edited or non-native-English text. Use it to reflect on your work, never as proof.</p>
      </section>
      <div id="authenticity-result"></div>
      <section class="papers-section">
        <div class="section-heading"><div><span class="eyebrow">YOUR HISTORY</span><h2>Recent checks</h2></div>
          <span class="count-pill" id="checks-count">${checks.length} ${checks.length === 1 ? "check" : "checks"}</span></div>
        <div class="paper-list" id="recent-checks">${recentChecks(checks)}</div>
      </section>`;
  }

  const forms = {
    async login(form) {
      await signIn(Object.fromEntries(new FormData(form)));
    },
    async register(form) {
      const data = Object.fromEntries(new FormData(form));
      await api("/auth/register", { method: "POST", body: data, auth: false });
      await signIn({ email: data.email, password: data.password });
    },
    async subject(form) {
      await api("/subjects", { method: "POST", body: Object.fromEntries(new FormData(form)) });
      form.reset();
      await renderSubjects();
      showMsg("Subject created. Open it when you’re ready to upload a past paper.", "success");
    },
    async startInterview(form) {
      const data = new FormData(form);
      const targetType = data.get("targetType");
      const target = targetType === "subject" ? data.get("subjectTarget") : data.get("roleTarget").trim();
      const questionCount = Number(data.get("questionCount"));
      if (!target) throw new Error("Enter an engineering role or create a subject first.");
      if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 20) {
        throw new Error("Choose between 1 and 20 questions.");
      }
      const settings = {
        targetType, target, level: data.get("level"), questionCount,
        questionNumber: 0, history: [], answer: null,
      };
      const firstTurn = await api("/interview/turn", { method: "POST", body: settings });
      if (!firstTurn.question || firstTurn.complete) {
        throw new Error("The interviewer could not prepare an opening question.");
      }
      state.interview = {
        ...settings, questionNumber: 1, currentQuestion: firstTurn.question,
        lastFeedback: "", lastScore: 0, scores: [], complete: false,
      };
      renderInterviewSession();
      speakText(firstTurn.question);
    },
    async submitInterviewAnswer(form) {
      const answer = form.elements.answer.value.trim();
      if (answer.length < 10) throw new Error("Please give a little more detail before submitting.");
      const interview = state.interview;
      const result = await api("/interview/turn", {
        method: "POST",
        body: {
          targetType: interview.targetType,
          target: interview.target,
          level: interview.level,
          questionNumber: interview.questionNumber,
          questionCount: interview.questionCount,
          history: interview.history,
          answer,
        },
      });
      interview.history.push({ speaker: "interviewer", content: interview.currentQuestion });
      interview.history.push({ speaker: "candidate", content: answer });
      interview.scores.push(result.score);
      if (result.complete) {
        renderInterviewComplete(result);
        speakText(result.feedback);
        return;
      }
      if (!result.question) throw new Error("The interviewer did not provide the next question.");
      interview.lastFeedback = result.feedback;
      interview.lastScore = result.score;
      interview.currentQuestion = result.question;
      interview.questionNumber += 1;
      renderInterviewSession();
      speakText(result.question);
    },
    async generateFromPapers(form) {
      const files = Array.from(form.elements.files.files);
      if (files.length < 1 || files.length > 5) {
        throw new Error("Choose between 1 and 5 past-paper PDFs.");
      }
      if (files.some((file) => file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf"))) {
        throw new Error("Please choose PDF files only.");
      }
      const count = Number(form.elements.count.value);
      if (!Number.isInteger(count) || count < 1 || count > 50) {
        throw new Error("Choose between 1 and 50 questions.");
      }
      if (form.elements.format.value === "mixed" && count < 2) {
        throw new Error("Choose at least 2 questions for a mixed-format set.");
      }
      $("pipeline-progress").hidden = false;
      const result = await api(`/subjects/${form.dataset.subject}/questions/generate-from-papers`, {
        method: "POST",
        form: new FormData(form),
      });
      const subjectId = form.dataset.subject;
      renderNav("subjects");
      await renderSubject(subjectId);
      showGenerated(result);
      showMsg("Your new questions are ready. Review them, then start adaptive practice.", "success");
    },
    async authenticity(form) {
      const text = form.elements.text.value.trim();
      const file = form.elements.file.files[0];
      if (Boolean(text) === Boolean(file)) throw new Error("Paste your text or choose one file, not both.");
      if (text && text.length < 50) throw new Error("Paste at least 50 characters to check.");
      const data = new FormData();
      if (text) data.append("text", text);
      if (file) data.append("file", file);
      const result = await api("/authenticity/check", { method: "POST", form: data });
      $("authenticity-result").innerHTML = authenticityResult(result);
      $("authenticity-result").scrollIntoView({ behavior: "smooth", block: "start" });
      form.reset();
      const checks = await api("/authenticity/checks");
      $("recent-checks").innerHTML = recentChecks(checks);
      $("checks-count").textContent = `${checks.length} ${checks.length === 1 ? "check" : "checks"}`;
    },
    async practiceBank(form) {
      const data = new FormData(form);
      const count = Number(data.get("count"));
      if (!Number.isInteger(count) || count < 1 || count > 50) {
        throw new Error("Choose between 1 and 50 questions.");
      }
      const response = await api(`/subjects/${form.dataset.subject}/quiz`, {
        method: "POST", body: { maxQuestions: count, questionType: data.get("questionType") },
      });
      state.quiz = { sessionId: response.sessionId, correctCount: 0 };
      renderQuestion(response.question);
    },
    async subjectiveAnswer(form) {
      const answerText = form.elements.answerText.value.trim();
      const answerPdf = form.elements.answerPdf.files[0];
      if (Boolean(answerText) === Boolean(answerPdf)) {
        throw new Error("Enter your answer as text or choose one PDF, not both.");
      }
      if (answerText && (answerText.length < 50 || answerText.length > 12000)) {
        throw new Error("Text answers must be between 50 and 12,000 characters.");
      }
      if (answerPdf && answerPdf.type !== "application/pdf"
          && !answerPdf.name.toLowerCase().endsWith(".pdf")) {
        throw new Error("Please choose a PDF file for your answer.");
      }
      const formData = new FormData();
      if (answerText) formData.append("answerText", answerText);
      if (answerPdf) formData.append("answerPdf", answerPdf);
      const result = await api(`/quiz/${state.quiz.sessionId}/subjective-answer`, {
        method: "POST", form: formData,
      });
      renderResult(result, state.quiz.current);
    },
  };

  const actions = {
    logout,
    toggleAuth() {
      state.authMode = state.authMode === "login" ? "register" : "login";
      renderAuth();
    },
    endInterview() {
      stopVoiceRecognition();
      window.speechSynthesis?.cancel();
      state.interview = null;
      renderInterviewSetup().catch((error) => showMsg(error.message));
    },
    newInterview() {
      stopVoiceRecognition();
      window.speechSynthesis?.cancel();
      state.interview = null;
      renderInterviewSetup().catch((error) => showMsg(error.message));
    },
    speakQuestion() {
      if (!state.interview?.currentQuestion) return;
      speakText(state.interview.currentQuestion);
    },
    async listenAnswer() {
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
        throw new Error("Audio recording is not supported in this browser. Type your answer instead.");
      }
      let stream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      } catch (error) {
        if (error.name === "NotAllowedError" || error.name === "PermissionDeniedError") {
          throw new Error("Microphone access was denied. Allow it in your browser and try again.");
        }
        if (error.name === "NotFoundError" || error.name === "DevicesNotFoundError") {
          throw new Error("No microphone was detected. Connect a microphone or type your answer.");
        }
        throw new Error(`Could not access the microphone (${error.message}).`);
      }

      const mimeType = [
        "audio/webm;codecs=opus",
        "audio/ogg;codecs=opus",
        "audio/mp4",
      ].find((type) => MediaRecorder.isTypeSupported(type));
      let recorder;
      try {
        recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      } catch (error) {
        stream.getTracks().forEach((track) => track.stop());
        throw new Error(`Could not start audio recording (${error.message}).`);
      }

      const button = document.querySelector('[data-action="listenAnswer"]');
      const chunks = [];
      state.voiceCaptureCancelled = false;
      state.voiceStream = stream;
      state.voiceRecorder = recorder;
      state.voiceChunks = chunks;
      recorder.ondataavailable = (event) => {
        if (event.data.size) chunks.push(event.data);
      };
      recorder.onerror = () => {
        state.voiceCaptureCancelled = true;
        stream.getTracks().forEach((track) => track.stop());
        const currentButton = document.querySelector('[data-action="stopVoiceAnswer"]');
        if (currentButton) {
          currentButton.dataset.action = "listenAnswer";
          currentButton.innerHTML = `${icon("microphone")} Record answer`;
        }
        showMsg("Audio recording failed. Check your microphone and try again.");
      };
      recorder.onstop = async () => {
        stream.getTracks().forEach((track) => track.stop());
        state.voiceRecorder = null;
        state.voiceStream = null;
        state.voiceChunks = [];
        if (state.voiceCaptureCancelled || !state.interview) return;

        const answerField = $("interview-answer");
        const listenButton = document.querySelector('[data-action="listenAnswer"]');
        if (!chunks.length) {
          showMsg("No audio was recorded. Try again or type your answer.");
          return;
        }
        listenButton.disabled = true;
        listenButton.innerHTML = '<span class="spinner spinner-small"></span>Transcribing on device…';
        try {
          const audioType = recorder.mimeType || "audio/webm";
          const extension = audioType.includes("ogg") ? "ogg"
            : audioType.includes("mp4") ? "mp4" : "webm";
          const audio = new Blob(chunks, { type: audioType });
          const form = new FormData();
          form.append("file", audio, `interview-answer.${extension}`);
          form.append("language", navigator.language || "en-US");
          const transcript = await api("/interview/transcribe", { method: "POST", form });
          answerField.value = [answerField.value.trim(), transcript.text]
            .filter(Boolean).join("\n");
          showMsg("Your recording was transcribed locally.", "success");
        } catch (error) {
          showMsg(error.message || "Could not transcribe the recording. Type your answer instead.");
        } finally {
          const currentButton = document.querySelector('[data-action="listenAnswer"]');
          if (currentButton) {
            currentButton.disabled = false;
            currentButton.innerHTML = `${icon("microphone")} Record answer`;
          }
        }
      };
      recorder.start();
      button.dataset.action = "stopVoiceAnswer";
      button.innerHTML = `${icon("close")} Stop recording`;
    },
    stopVoiceAnswer() {
      if (!state.voiceRecorder || state.voiceRecorder.state === "inactive") return;
      state.voiceCaptureCancelled = false;
      const button = document.querySelector('[data-action="stopVoiceAnswer"]');
      button.dataset.action = "listenAnswer";
      state.voiceRecorder.stop();
    },
    async startGeneratedQuiz(button) {
      if (!state.generated?.questions?.length) throw new Error("Generate a question set first.");
      const questionIds = state.generated.questions.map((question) => question.id);
      const response = await api(`/subjects/${button.dataset.subject}/quiz`, {
        method: "POST",
        body: { maxQuestions: questionIds.length, questionIds },
      });
      state.quiz = { sessionId: response.sessionId, correctCount: 0 };
      renderQuestion(response.question);
    },
    async answer(button) {
      const selectedOption = button;
      selectedOption.classList.add("selected");
      const options = document.querySelectorAll(".quiz-option");
      options.forEach((option) => { option.disabled = true; });
      try {
        const result = await api(`/quiz/${state.quiz.sessionId}/answer`, {
          method: "POST", body: { selectedIndex: Number(selectedOption.dataset.index) },
        });
        renderResult(result, state.quiz.current);
      } catch (error) {
        options.forEach((option) => { option.disabled = false; });
        selectedOption.classList.remove("selected");
        throw error;
      }
    },
    nextQuestion() {
      renderQuestion(state.quiz.next);
    },
    finishQuiz: showQuizSummary,
    backToQuestions() {
      const id = location.hash.match(/\d+/)?.[0];
      if (id) renderSubject(id).catch((error) => showMsg(error.message));
    },
  };

  function speakText(text) {
    if (!("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = navigator.language || "en-US";
    window.speechSynthesis.speak(utterance);
  }

  function stopVoiceRecognition() {
    state.voiceCaptureCancelled = true;
    if (state.voiceRecorder && state.voiceRecorder.state !== "inactive") {
      state.voiceRecorder.stop();
    }
    state.voiceStream?.getTracks().forEach((track) => track.stop());
    state.voiceRecorder = null;
    state.voiceStream = null;
    state.voiceChunks = [];
  }

  function setBusy(button, busy, label) {
    if (!button) return () => {};
    const original = button.innerHTML;
    button.disabled = busy;
    if (busy && label) button.innerHTML = `<span class="spinner spinner-small"></span>${esc(label)}`;
    return () => {
      button.disabled = false;
      button.innerHTML = original;
    };
  }

  document.addEventListener("change", (event) => {
    if (event.target.id === "paper-files") {
      const files = Array.from(event.target.files);
      $("file-names").textContent = files.length
        ? files.map((file) => file.name).join(" · ")
        : "No files selected";
      event.target.closest(".dropzone").classList.toggle("has-files", files.length > 0);
    }
  });

  document.addEventListener("submit", async (event) => {
    const form = event.target;
    const name = form.dataset.form;
    if (!name || !forms[name]) return;
    event.preventDefault();
    showMsg("");
    const button = form.querySelector('button[type="submit"], button:not([type])');
    const restore = setBusy(button, true, button?.dataset.busy || "Working…");
    try {
      await forms[name](form);
    } catch (error) {
      showMsg(error.message || "Something went wrong. Please try again.");
      const progress = $("pipeline-progress");
      if (progress) progress.hidden = true;
    } finally {
      restore();
    }
  });

  document.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-action]");
    const action = button && actions[button.dataset.action];
    if (!action) return;
    event.preventDefault();
    showMsg("");
    const restore = setBusy(button, true, button.dataset.busy);
    try {
      await action(button);
    } catch (error) {
      showMsg(error.message || "Something went wrong. Please try again.");
    } finally {
      restore();
      if (button.dataset.action === "stopVoiceAnswer") {
        button.innerHTML = `${icon("close")} Stop recording`;
      }
    }
  });

  async function route() {
    const hash = location.hash || "#/subjects";
    if (hash !== "#/interview") stopVoiceRecognition();
    if (!state.token && hash !== "#/login") {
      location.hash = "#/login";
      return;
    }
    if (state.token && hash === "#/login") {
      location.hash = "#/subjects";
      return;
    }
    showMsg("");
    try {
      if (hash === "#/login") renderAuth();
      else if (hash === "#/subjects") await renderSubjects();
      else if (hash === "#/interview") await renderInterviewSetup();
      else if (hash === "#/tests") await renderTests();
      else if (/^#\/tests\/\d+$/.test(hash)) await renderTestReview(hash.match(/\d+/)[0]);
      else if (hash === "#/authenticity") await renderAuthenticity();
      else if (/^#\/subjects\/\d+$/.test(hash)) await renderSubject(hash.match(/\d+/)[0]);
      else location.hash = "#/subjects";
    } catch (error) {
      showMsg(error.message || "Could not load this page.");
    }
  }

  window.addEventListener("hashchange", route);
  route();
})();
