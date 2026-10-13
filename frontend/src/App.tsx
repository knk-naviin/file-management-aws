import {
  ArrowDownToLine,
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronDown,
  CircleHelp,
  FileArchive,
  FileImage,
  FileText,
  Fingerprint,
  FolderClosed,
  LockKeyhole,
  LogOut,
  MoreHorizontal,
  Plus,
  Search,
  ShieldCheck,
  ShieldEllipsis,
  ShieldPlus,
  ShieldX,
  Trash2,
  UploadCloud,
  UserRound,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ApiError,
  api,
  fileExtension,
  formatBytes,
  type DocumentRecord,
  type Session,
  type UserProfile,
} from "./api";

const LAST_SESSION_KEY = "vault-session";

function readSavedSession(): Session | null {
  try {
    const serialized = sessionStorage.getItem(LAST_SESSION_KEY);
    if (!serialized) return null;
    const session = JSON.parse(serialized) as Session;
    return session.accessToken && session.refreshToken ? session : null;
  } catch {
    return null;
  }
}

function documentIcon(fileName: string) {
  const extension = fileExtension(fileName);
  if (["png", "jpg", "jpeg", "webp"].includes(extension)) return FileImage;
  if (["zip", "7z", "tar", "gz"].includes(extension)) return FileArchive;
  return FileText;
}

function displayDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, { month: "short", day: "2-digit", year: "numeric" }).format(date);
}

export default function App() {
  const [session, setSession] = useState<Session | null>(readSavedSession);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [activeDocument, setActiveDocument] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [showAuth, setShowAuth] = useState(false);

  const refresh = useCallback(async (currentSession: Session) => {
    try {
      const [currentProfile, records] = await Promise.all([
        api.profile(currentSession.accessToken),
        api.listDocuments(currentSession.accessToken),
      ]);
      setProfile(currentProfile);
      setDocuments(records);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        try {
          const nextSession = await api.refresh(currentSession.refreshToken);
          sessionStorage.setItem(LAST_SESSION_KEY, JSON.stringify(nextSession));
          setSession(nextSession);
          const [currentProfile, records] = await Promise.all([
            api.profile(nextSession.accessToken),
            api.listDocuments(nextSession.accessToken),
          ]);
          setProfile(currentProfile);
          setDocuments(records);
          return;
        } catch {
          sessionStorage.removeItem(LAST_SESSION_KEY);
          setSession(null);
          setProfile(null);
          setDocuments([]);
          setError("Your session expired. Sign in again to continue.");
          return;
        }
      }
      setError(caught instanceof Error ? caught.message : "Could not load your documents.");
    }
  }, []);

  useEffect(() => {
    if (session) void refresh(session);
  }, [session, refresh]);

  const visibleDocuments = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return documents;
    return documents.filter((document) =>
      document.fileName.toLowerCase().includes(query),
    );
  }, [documents, search]);

  async function signOut() {
    if (session) {
      try {
        await api.logout(session.accessToken, session.refreshToken);
      } catch {
        setNotice("You were signed out locally; server token revocation could not be confirmed.");
      }
    }
    sessionStorage.removeItem(LAST_SESSION_KEY);
    setSession(null);
    setProfile(null);
    setDocuments([]);
  }

  async function uploadFile(file: File) {
    if (!session) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await api.upload(session.accessToken, file);
      await refresh(session);
      setNotice(`${file.name} is protected and saved.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Upload failed.");
    } finally {
      setBusy(false);
    }
  }

  async function downloadFile(document: DocumentRecord) {
    if (!session) return;
    setActiveDocument(document.id);
    setError("");
    try {
      const blob = await api.download(session.accessToken, document.id);
      const url = URL.createObjectURL(blob);
      const anchor = window.document.createElement("a");
      anchor.href = url;
      anchor.download = document.fileName;
      anchor.rel = "noopener";
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Download failed.");
    } finally {
      setActiveDocument(null);
    }
  }

  async function removeDocument(document: DocumentRecord) {
    if (!session || !window.confirm(`Delete “${document.fileName}”? This cannot be undone.`)) return;
    setActiveDocument(document.id);
    setError("");
    try {
      await api.delete(session.accessToken, document.id);
      setDocuments((current) => current.filter((item) => item.id !== document.id));
      setNotice(`${document.fileName} was deleted.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Delete failed.");
    } finally {
      setActiveDocument(null);
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Vault home">
          <span className="brand-mark"><LockKeyhole size={17} strokeWidth={2.2} /></span>
          <span>vault<span className="brand-period">.</span></span>
        </a>
        <div className="topbar-right">
          <div className="trust-note"><span className="pulse-dot" /> All systems operational</div>
          <button className="icon-button help-button" aria-label="Security information" title="Your files are encrypted before they reach cloud storage">
            <CircleHelp size={17} />
          </button>
          {profile ? (
            <button className="account-button" onClick={() => void signOut()}>
              <span className="avatar">{profile.username.slice(0, 1).toUpperCase()}</span>
              <span className="account-name">{profile.username}</span>
              <LogOut size={15} />
            </button>
          ) : (
            <button className="sign-in-link" onClick={() => setShowAuth(true)}>Sign in <ArrowRight size={15} /></button>
          )}
        </div>
      </header>

      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow"><span className="eyebrow-line" /> PRIVATE BY DESIGN</div>
          <h1>Your documents.<br /><span>Your control.</span></h1>
          <p>A quieter, safer place for the files that matter.<br className="desktop-break" /> Encrypted before they reach cloud storage.</p>
          {profile ? (
            <div className="hero-actions">
              <label className={`primary-button ${busy ? "is-disabled" : ""}`}>
                <UploadCloud size={16} /> {busy ? "Uploading…" : "Upload a document"}
                <input
                  type="file"
                  className="visually-hidden"
                  disabled={busy}
                  onChange={(event) => {
                    const file = event.currentTarget.files?.[0];
                    if (file) void uploadFile(file);
                    event.currentTarget.value = "";
                  }}
                />
              </label>
              <span className="upload-hint">Up to 8 MB per file</span>
            </div>
          ) : (
            <button className="primary-button" onClick={() => setShowAuth(true)}>
              <LockKeyhole size={16} /> Open your vault <ArrowRight size={15} />
            </button>
          )}
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="art-orbit orbit-one" />
          <div className="art-orbit orbit-two" />
          <div className="art-glow" />
          <div className="art-card card-back"><div className="card-line short" /><div className="card-line" /><div className="card-line mid" /></div>
          <div className="art-card card-front">
            <div className="art-lock"><LockKeyhole size={29} strokeWidth={1.45} /></div>
            <span className="art-card-caption">AES-256 · KMS PROTECTED</span>
            <span className="art-card-id">KEY ID&nbsp;&nbsp;• • • •  8F2A</span>
          </div>
          <div className="float-tag tag-encrypted"><ShieldCheck size={13} /> KMS-encrypted storage</div>
          <div className="float-tag tag-key"><span className="key-spark">✳</span> Customer-managed key</div>
        </div>
        <div className="hero-footer"><span>01 / YOUR PERSONAL ARCHIVE</span><span>SCROLL TO EXPLORE &nbsp; ↓</span></div>
      </section>

      <section className="content-section" id="documents">
        <div className="section-head">
          <div>
            <div className="eyebrow"><span className="eyebrow-line" /> YOUR PRIVATE SPACE</div>
            <h2>{profile ? "Your documents" : "A little peace of mind."}</h2>
            <p className="section-subtitle">
              {profile ? `${documents.length} ${documents.length === 1 ? "document" : "documents"} · only you can access what you upload` : "Sign in to open your encrypted archive."}
            </p>
          </div>
          {profile && (
            <div className="list-controls">
              <label className="search-box">
                <Search size={15} />
                <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Find a document" aria-label="Find a document" />
                {search && <button className="clear-search" onClick={() => setSearch("")} aria-label="Clear search"><X size={13} /></button>}
              </label>
              <button className="filter-button" disabled><span>All files</span><ChevronDown size={14} /></button>
            </div>
          )}
        </div>

        {(error || notice) && (
          <div className={`feedback ${error ? "feedback-error" : "feedback-success"}`} role={error ? "alert" : "status"}>
            {error ? <ShieldX size={16} /> : <Check size={16} />}
            <span>{error || notice}</span>
            {error && <button onClick={() => setError("")} aria-label="Dismiss error"><X size={15} /></button>}
          </div>
        )}

        {profile ? (
          <div className="document-table">
            <div className="table-labels"><span>NAME</span><span>DATE ADDED</span><span>SIZE</span><span>STATUS</span><span /></div>
            {visibleDocuments.length ? visibleDocuments.map((document) => {
              const Icon = documentIcon(document.fileName);
              const loading = activeDocument === document.id;
              return (
                <article className="document-row" key={document.id}>
                  <div className="document-name">
                    <div className={`file-icon file-${fileExtension(document.fileName)}`}><Icon size={18} strokeWidth={1.7} /></div>
                    <div className="file-label"><span title={document.fileName}>{document.fileName}</span><small>{document.contentType}</small></div>
                  </div>
                  <div className="date-value">{displayDate(document.uploadedDate)}</div>
                  <div className="size-value">{formatBytes(document.size)}</div>
                  <div><span className="status-pill"><span /> Protected</span></div>
                  <div className="row-actions">
                    <button aria-label={`Download ${document.fileName}`} title="Download" disabled={loading} onClick={() => void downloadFile(document)}><ArrowDownToLine size={16} /></button>
                    <button aria-label={`Delete ${document.fileName}`} title="Delete" disabled={loading} onClick={() => void removeDocument(document)}><Trash2 size={16} /></button>
                    <button aria-label={`More actions for ${document.fileName}`} title="More" disabled><MoreHorizontal size={17} /></button>
                  </div>
                </article>
              );
            }) : (
              <div className="empty-state">
                <div className="empty-icon"><FolderClosed size={24} /></div>
                <strong>{search ? "No matching documents" : "Your archive is ready"}</strong>
                <p>{search ? "Try a different search." : "Upload your first file to keep it safe."}</p>
                {!search && <label className="text-button">Add a document <Plus size={14} /><input type="file" className="visually-hidden" onChange={(event) => { const file = event.currentTarget.files?.[0]; if (file) void uploadFile(file); event.currentTarget.value = ""; }} /></label>}
              </div>
            )}
            {documents.length > 0 && (
              <div className="table-footer"><span>Showing {visibleDocuments.length} of {documents.length} documents</span><span className="pagination"><button aria-label="Previous page" disabled><ArrowLeft size={14} /></button><span>1</span><button aria-label="Next page" disabled><ArrowRight size={14} /></button></span></div>
            )}
          </div>
        ) : (
          <div className="signed-out-panel">
            <div className="signed-out-icon"><LockKeyhole size={22} /></div>
            <div><strong>Your files are private. Always.</strong><p>Sign in or create an account to upload, manage, and retrieve your documents.</p></div>
            <button className="text-button" onClick={() => setShowAuth(true)}>Get started <ArrowRight size={14} /></button>
          </div>
        )}
      </section>

      <section className="security-strip">
        <div className="security-copy"><div className="eyebrow"><span className="eyebrow-line" /> BUILT FOR THE PRIVATE THINGS</div><h2>Security that<br />stays out of the way.</h2><p>Thoughtful protection at every step. No complexity, no compromises.</p></div>
        <div className="security-points">
          <div className="security-point"><div className="point-icon"><ShieldEllipsis size={18} /></div><div><strong>Encrypted at rest</strong><span>AES-256 with your own dedicated key.</span></div></div>
          <div className="security-point"><div className="point-icon"><Fingerprint size={18} /></div><div><strong>Just yours</strong><span>Private by default. You choose what happens next.</span></div></div>
          <div className="security-point"><div className="point-icon"><ShieldPlus size={18} /></div><div><strong>Protected on arrival</strong><span>Files are encrypted before they are stored.</span></div></div>
        </div>
        <div className="security-stamp"><span>DESIGNED<br />TO PROTECT</span><ShieldCheck size={20} /></div>
      </section>

      <footer className="footer"><a className="brand footer-brand" href="/"><span className="brand-mark"><LockKeyhole size={14} /></span><span>vault<span className="brand-period">.</span></span></a><span className="footer-note">A little more peace of mind.</span><span className="footer-copyright">PRIVATE BY DESIGN &nbsp;·&nbsp; 2026</span></footer>

      {showAuth && <AuthModal onClose={() => setShowAuth(false)} onAuthenticated={(nextSession) => {
        sessionStorage.setItem(LAST_SESSION_KEY, JSON.stringify(nextSession));
        setSession(nextSession);
        setProfile(null);
        setShowAuth(false);
        setError("");
      }} />}
    </main>
  );
}

function AuthModal({ onClose, onAuthenticated }: {
  onClose: () => void;
  onAuthenticated: (session: Session) => void;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const usernameRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    usernameRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !pending) onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose, pending]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      if (mode === "register") await api.register(username, email, password);
      const nextSession = await api.login(username, password);
      onAuthenticated(nextSession);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Authentication failed.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !pending) onClose(); }}>
      <section className="auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <button className="modal-close" onClick={onClose} aria-label="Close sign in"><X size={18} /></button>
        <div className="modal-mark"><LockKeyhole size={21} /></div>
        <div className="eyebrow"><span className="eyebrow-line" /> YOUR PRIVATE SPACE</div>
        <h2 id="auth-title">{mode === "login" ? "Welcome back." : "Start your vault."}</h2>
        <p className="modal-description">{mode === "login" ? "Sign in to access your documents." : "A safe place for the files that matter."}</p>
        {error && <div className="modal-error" role="alert"><ShieldX size={15} />{error}</div>}
        <form onSubmit={(event) => void submit(event)}>
          <label className="field-label" htmlFor="username">Username</label>
          <div className="input-wrap"><UserRound size={16} /><input ref={usernameRef} id="username" name="username" autoComplete="username" minLength={3} maxLength={80} required value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Your username" /></div>
          {mode === "register" && <><label className="field-label" htmlFor="email">Email address</label><div className="input-wrap"><span className="email-at">@</span><input id="email" name="email" type="email" autoComplete="email" maxLength={254} required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" /></div></>}
          <label className="field-label" htmlFor="password">Password</label>
          <div className="input-wrap"><LockKeyhole size={16} /><input id="password" name="password" type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} minLength={mode === "register" ? 12 : 1} maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} placeholder={mode === "register" ? "At least 12 characters" : "Your password"} /></div>
          <button className="primary-button modal-submit" type="submit" disabled={pending}>
            {pending ? "One moment…" : mode === "login" ? "Sign in securely" : "Create your vault"}
            {!pending && <ArrowRight size={15} />}
          </button>
        </form>
        <div className="auth-switch">{mode === "login" ? "New to Vault?" : "Already have an account?"}<button onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}> {mode === "login" ? "Create an account" : "Sign in"}</button></div>
        <div className="modal-secure"><ShieldCheck size={14} /> Your connection is private and encrypted.</div>
      </section>
    </div>
  );
}
