import { useEffect, useRef, useState } from 'react'
import { EmptyState } from '../components/ui'
import {
  archiveMedia,
  downloadMediaFile,
  getMediaReport,
  listMedia,
  uploadMedia,
  type MediaSummary,
  type ReviewReport,
} from '../services/api'

const extensions = ['jpg', 'jpeg', 'png', 'webp']

export function MediaScansPage() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [notice, setNotice] = useState('JPEG, PNG, and WebP files are validated by the server before processing.')
  const [media, setMedia] = useState<MediaSummary[]>([])
  const [report, setReport] = useState<ReviewReport | null>(null)
  const [busy, setBusy] = useState(false)
  const [activeTab, setActiveTab] = useState<'ALL' | 'PROVENANCE' | 'RIGHTS' | 'DEPLOYMENT_READINESS'>('ALL')
  const [context, setContext] = useState({
    intendedChannel: 'SOCIAL',
    intendedTerritory: 'GLOBAL',
    intendedUse: 'COMMERCIAL',
    plannedPublicationAt: '',
  })

  const load = async () => {
    try {
      const paged = await listMedia()
      setMedia(paged.content)
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Unable to load media')
    }
  }

  useEffect(() => {
    void load()
    const timer = setInterval(() => {
      // Poll if any media is still processing
      if (media.some((m) => m.scanStatus === 'QUEUED' || m.scanStatus === 'PROCESSING')) {
        void load()
      }
    }, 4000)
    return () => clearInterval(timer)
  }, [media])

  const validate = async (file: File | undefined) => {
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!extension || !extensions.includes(extension)) {
      setNotice('Unsupported format. Choose a JPEG, PNG, or WebP image.')
      return
    }
    setBusy(true)
    setNotice(`Uploading ${file.name}…`)
    try {
      await uploadMedia(file, {
        intendedChannel: context.intendedChannel || undefined,
        intendedTerritory: context.intendedTerritory || undefined,
        intendedUse: context.intendedUse || undefined,
        plannedPublicationAt: context.plannedPublicationAt ? new Date(context.plannedPublicationAt).toISOString() : undefined,
      })
      setNotice(`${file.name} uploaded successfully. Analysis running asynchronously.`)
      await load()
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  const handleDownload = async (id: string, filename: string) => {
    try {
      const blob = await downloadMediaFile(id)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      window.URL.revokeObjectURL(url)
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Download failed')
    }
  }

  const handleArchive = async (id: string) => {
    if (!confirm('Are you sure you want to archive this media asset?')) return
    try {
      await archiveMedia(id)
      setNotice('Media asset archived.')
      await load()
      if (report?.mediaId === id) setReport(null)
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Archive failed')
    }
  }

  const getDecisionBadgeClass = (decision: string | null | undefined) => {
    switch (decision) {
      case 'APPROVE':
        return 'badge-success'
      case 'BLOCK':
        return 'badge-danger'
      case 'REVIEW':
        return 'badge-warning'
      default:
        return 'badge-neutral'
    }
  }

  const getStatusBadgeClass = (status: string | null | undefined) => {
    switch (status) {
      case 'PASS':
        return 'badge-success'
      case 'FAIL':
        return 'badge-danger'
      case 'ADVISORY_FLAG':
      case 'REVIEW':
        return 'badge-warning'
      case 'UNAVAILABLE':
      case 'UNKNOWN':
        return 'badge-subtle'
      default:
        return 'badge-neutral'
    }
  }

  return (
    <div className="scans-page">
      <p className="breadcrumb">Media Review &amp; Compliance</p>
      <h1>Media Upload &amp; Review Queue</h1>

      {/* Publication Context Section */}
      <section className="scan-section">
        <h2>1. Intended Publication Context</h2>
        <p className="section-help">Specify release parameters to evaluate licence scope and policy deployment readiness.</p>
        <div className="toolbar context-inputs">
          <label>
            <span>Channel:</span>
            <select
              value={context.intendedChannel}
              onChange={(e) => setContext({ ...context, intendedChannel: e.target.value })}
            >
              <option value="SOCIAL">Social Media</option>
              <option value="WEB">Web / Digital</option>
              <option value="BROADCAST">Television / Broadcast</option>
              <option value="PRINT">Print / Out-of-Home</option>
              <option value="INTERNAL">Internal Use Only</option>
            </select>
          </label>

          <label>
            <span>Territory:</span>
            <select
              value={context.intendedTerritory}
              onChange={(e) => setContext({ ...context, intendedTerritory: e.target.value })}
            >
              <option value="GLOBAL">Global / Worldwide</option>
              <option value="US">North America (US/CA)</option>
              <option value="EU">European Union</option>
              <option value="UK">United Kingdom</option>
              <option value="APAC">Asia-Pacific</option>
            </select>
          </label>

          <label>
            <span>Intended Use:</span>
            <select
              value={context.intendedUse}
              onChange={(e) => setContext({ ...context, intendedUse: e.target.value })}
            >
              <option value="COMMERCIAL">Commercial / Advertising</option>
              <option value="EDITORIAL">Editorial / News</option>
              <option value="INTERNAL">Internal / Non-Commercial</option>
            </select>
          </label>

          <label>
            <span>Planned Date:</span>
            <input
              type="date"
              value={context.plannedPublicationAt}
              onChange={(e) => setContext({ ...context, plannedPublicationAt: e.target.value })}
            />
          </label>
        </div>
      </section>

      {/* Upload Drop Zone */}
      <section
        className={dragging ? 'upload-zone is-dragging' : 'upload-zone'}
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          void validate(event.dataTransfer.files[0])
        }}
      >
        <div className="media-icons" aria-hidden="true">
          <span>▧</span>
          <span>▣</span>
          <span>▤</span>
        </div>
        <p>
          Drag and drop an image to review or{' '}
          <button type="button" className="browse-link" onClick={() => inputRef.current?.click()} disabled={busy}>
            browse files
          </button>
          .
        </p>
        <input
          ref={inputRef}
          className="sr-only"
          type="file"
          accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          onChange={(event) => void validate(event.target.files?.[0])}
        />
        <small>JPEG, PNG, and WebP supported · Filesystem limits and content signatures validated server-side.</small>
        <p className="upload-notice" role="status">
          {notice}
        </p>
      </section>

      {/* Active Processing Queue Cards */}
      {media.some((x) => x.scanStatus === 'QUEUED' || x.scanStatus === 'PROCESSING') && (
        <section className="scan-section">
          <h2>Active Processing Queue</h2>
          <div className="media-cards">
            {media
              .filter((x) => x.scanStatus === 'QUEUED' || x.scanStatus === 'PROCESSING')
              .map((x) => (
                <article className="scan-card" key={x.id}>
                  <strong>{x.originalFilename}</strong>
                  <span className={`status-pill ${getStatusBadgeClass(x.scanStatus)}`}>{x.scanStatus}</span>
                  <small>
                    {x.mimeType} · {Math.round(x.fileSizeBytes / 1024)} KB · SHA: {x.sha256Checksum.slice(0, 10)}…
                  </small>
                </article>
              ))}
          </div>
        </section>
      )}

      {/* Scanned Assets & Outcomes Table */}
      <section className="scan-section">
        <div className="section-row results-heading">
          <h2>Scanned Assets &amp; Compliance Outcomes</h2>
          <button type="button" className="btn-secondary" onClick={() => void load()}>
            Refresh
          </button>
        </div>

        <div className="results-table-wrap">
          <table>
            <thead>
              <tr>
                <th>File Name</th>
                <th>Type</th>
                <th>Size</th>
                <th>Checksum</th>
                <th>Processing</th>
                <th>Decision</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {media.map((x) => (
                <tr key={x.id}>
                  <td>
                    <strong>{x.originalFilename}</strong>
                  </td>
                  <td>{x.mimeType}</td>
                  <td>{Math.round(x.fileSizeBytes / 1024)} KB</td>
                  <td>
                    <code>{x.sha256Checksum.slice(0, 12)}…</code>
                  </td>
                  <td>
                    <span className={`status-pill ${getStatusBadgeClass(x.scanStatus)}`}>{x.scanStatus}</span>
                  </td>
                  <td>
                    <span className={`status-pill ${getDecisionBadgeClass(x.decision)}`}>
                      {x.decision || 'Pending'}
                    </span>
                  </td>
                  <td>
                    <div className="table-actions">
                      <button
                        type="button"
                        className="btn-action"
                        onClick={async () => {
                          try {
                            setReport(await getMediaReport(x.id))
                          } catch (e) {
                            setNotice(e instanceof Error ? e.message : 'Report not ready')
                          }
                        }}
                      >
                        View Report
                      </button>
                      <button
                        type="button"
                        className="btn-action-secondary"
                        onClick={() => void handleDownload(x.id, x.originalFilename)}
                      >
                        Download
                      </button>
                      <button
                        type="button"
                        className="btn-action-danger"
                        onClick={() => void handleArchive(x.id)}
                      >
                        Archive
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {media.length === 0 && (
            <EmptyState title="No scanned assets available">
              Uploaded media assets and their compliance verification results will appear here.
            </EmptyState>
          )}
        </div>
      </section>

      {/* Comprehensive Report View */}
      {report && (
        <section className="scan-section report-modal">
          <div className="section-row report-header">
            <div>
              <h2>Review Report: {report.originalFilename}</h2>
              <p className="report-subhead">
                Decision Authority: <strong>Deterministic Engine ({report.policyVersion})</strong> · Generated:{' '}
                {new Date(report.generatedAt).toLocaleString()}
              </p>
            </div>
            <button type="button" className="btn-close" onClick={() => setReport(null)}>
              ✕ Close
            </button>
          </div>

          <div className="decision-banner">
            <div className={`decision-badge-large ${getDecisionBadgeClass(report.decision)}`}>
              {report.decision}
            </div>
            <div className="decision-summary-text">
              <p>
                <strong>Status:</strong> {report.processingStatus} ·{' '}
                <strong>Explanation Status:</strong> {report.explanationStatus}
              </p>
              <ul>
                {report.reasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          </div>

          {/* Context Details */}
          <div className="report-meta-grid">
            <div>
              <strong>Resolution:</strong>{' '}
              {report.widthPixels && report.heightPixels
                ? `${report.widthPixels} × ${report.heightPixels} px`
                : 'Not available'}
            </div>
            <div>
              <strong>File Size:</strong> {Math.round(report.fileSizeBytes / 1024)} KB
            </div>
            <div>
              <strong>Intended Channel:</strong> {report.intendedChannel || 'Unspecified'}
            </div>
            <div>
              <strong>Intended Territory:</strong> {report.intendedTerritory || 'Unspecified'}
            </div>
            <div>
              <strong>Intended Use:</strong> {report.intendedUse || 'Unspecified'}
            </div>
            <div>
              <strong>Planned Date:</strong>{' '}
              {report.plannedPublicationAt
                ? new Date(report.plannedPublicationAt).toLocaleDateString()
                : 'Immediate'}
            </div>
          </div>

          {/* AI Explanation / Deterministic Fallback Box */}
          <div className="explanation-box">
            <h3>Compliance Analysis &amp; Explanation</h3>
            <p className="explanation-content">{report.explanation}</p>
            <small className="explanation-disclaimer">
              Deterministic rule authority remains final. Explanations never alter decision outcomes.
            </small>
          </div>

          {/* Evidence Category Filter Tabs */}
          <div className="evidence-section">
            <div className="evidence-header-row">
              <h3>Detailed Evidence &amp; Findings ({report.evidence.length})</h3>
              <div className="tab-group">
                <button
                  type="button"
                  className={activeTab === 'ALL' ? 'tab-btn active' : 'tab-btn'}
                  onClick={() => setActiveTab('ALL')}
                >
                  All ({report.evidence.length})
                </button>
                <button
                  type="button"
                  className={activeTab === 'PROVENANCE' ? 'tab-btn active' : 'tab-btn'}
                  onClick={() => setActiveTab('PROVENANCE')}
                >
                  Provenance (
                  {report.evidence.filter((e) => e.category.toUpperCase().includes('PROVENANCE')).length}
                  )
                </button>
                <button
                  type="button"
                  className={activeTab === 'RIGHTS' ? 'tab-btn active' : 'tab-btn'}
                  onClick={() => setActiveTab('RIGHTS')}
                >
                  Rights &amp; Licences (
                  {report.evidence.filter((e) => e.category.toUpperCase().includes('RIGHTS')).length}
                  )
                </button>
                <button
                  type="button"
                  className={activeTab === 'DEPLOYMENT_READINESS' ? 'tab-btn active' : 'tab-btn'}
                  onClick={() => setActiveTab('DEPLOYMENT_READINESS')}
                >
                  Policy Readiness (
                  {
                    report.evidence.filter(
                      (e) =>
                        e.category.toUpperCase().includes('DEPLOYMENT') ||
                        e.category.toUpperCase().includes('POLICY')
                    ).length
                  }
                  )
                </button>
              </div>
            </div>

            <ul className="evidence-list">
              {report.evidence
                .filter((f) => {
                  if (activeTab === 'ALL') return true
                  if (activeTab === 'PROVENANCE') return f.category.toUpperCase().includes('PROVENANCE')
                  if (activeTab === 'RIGHTS') return f.category.toUpperCase().includes('RIGHTS')
                  if (activeTab === 'DEPLOYMENT_READINESS')
                    return f.category.toUpperCase().includes('DEPLOYMENT') || f.category.toUpperCase().includes('POLICY')
                  return true
                })
                .map((f) => (
                  <li key={f.id} className="evidence-item">
                    <div className="evidence-title-row">
                      <span className={`status-pill ${getStatusBadgeClass(f.status)}`}>{f.status}</span>
                      <strong>
                        [{f.category}] {f.checkName}
                      </strong>
                    </div>
                    <p className="evidence-summary">{f.summary}</p>
                    {f.details && f.details !== '{}' && (
                      <details className="evidence-details">
                        <summary>View Technical Data</summary>
                        <pre>{JSON.stringify(JSON.parse(f.details), null, 2)}</pre>
                      </details>
                    )}
                  </li>
                ))}
            </ul>
          </div>
        </section>
      )}
    </div>
  )
}
