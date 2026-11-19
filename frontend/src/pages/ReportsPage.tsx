import { useEffect, useState } from 'react'
import { EmptyState } from '../components/ui'
import { getMediaReport, listReports, type ReportSummary, type ReviewReport } from '../services/api'

export function ReportsPage() {
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [selectedReport, setSelectedReport] = useState<ReviewReport | null>(null)
  const [filterDecision, setFilterDecision] = useState<string>('ALL')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async () => {
    try {
      setLoading(true)
      const data = await listReports()
      setReports(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to load reports')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const filtered = reports.filter((r) => {
    if (filterDecision === 'ALL') return true
    return r.decision === filterDecision
  })

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

  return (
    <div className="scans-page">
      <p className="breadcrumb">Compliance History</p>
      <h1>Review Reports &amp; Audit Records</h1>

      <section className="scan-section">
        <div className="section-row">
          <h2>Persisted Reports History ({filtered.length})</h2>
          <div className="toolbar-controls">
            <label>
              <span>Filter Decision:</span>
              <select value={filterDecision} onChange={(e) => setFilterDecision(e.target.value)}>
                <option value="ALL">All Decisions</option>
                <option value="APPROVE">Approved</option>
                <option value="REVIEW">Needs Review</option>
                <option value="BLOCK">Blocked</option>
              </select>
            </label>
            <button type="button" className="btn-secondary" onClick={() => void load()} disabled={loading}>
              Refresh
            </button>
          </div>
        </div>

        {error && <p className="upload-notice error-notice" role="alert">{error}</p>}

        {filtered.length === 0 ? (
          <EmptyState title="No review reports found">
            Completed compliance reviews are permanently archived here with their deterministic decisions and evidence records.
          </EmptyState>
        ) : (
          <div className="results-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>File Name</th>
                  <th>Final Decision</th>
                  <th>Report Status</th>
                  <th>Timestamp</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => (
                  <tr key={r.reportId}>
                    <td>
                      <strong>{r.filename}</strong>
                    </td>
                    <td>
                      <span className={`status-pill ${getDecisionBadgeClass(r.decision)}`}>
                        {r.decision}
                      </span>
                    </td>
                    <td>
                      <span className="status-pill badge-neutral">{r.reportStatus}</span>
                    </td>
                    <td>{new Date(r.createdAt).toLocaleString()}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-action"
                        onClick={async () => {
                          try {
                            setSelectedReport(await getMediaReport(r.mediaId))
                          } catch (e) {
                            setError(e instanceof Error ? e.message : 'Report details unavailable')
                          }
                        }}
                      >
                        Inspect Report
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Selected Report Inspection View */}
      {selectedReport && (
        <section className="scan-section report-modal">
          <div className="section-row report-header">
            <div>
              <h2>Archived Report: {selectedReport.originalFilename}</h2>
              <p className="report-subhead">
                Decision: <strong>{selectedReport.decision}</strong> ({selectedReport.policyVersion}) · Generated:{' '}
                {new Date(selectedReport.generatedAt).toLocaleString()}
              </p>
            </div>
            <button type="button" className="btn-close" onClick={() => setSelectedReport(null)}>
              ✕ Close
            </button>
          </div>

          <div className="decision-banner">
            <div className={`decision-badge-large ${getDecisionBadgeClass(selectedReport.decision)}`}>
              {selectedReport.decision}
            </div>
            <div className="decision-summary-text">
              <p>
                <strong>Status:</strong> {selectedReport.processingStatus} ·{' '}
                <strong>Checksum:</strong> <code>{selectedReport.sha256Checksum}</code>
              </p>
              <ul>
                {selectedReport.reasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          </div>

          <div className="explanation-box">
            <h3>Persisted Compliance Explanation</h3>
            <p className="explanation-content">{selectedReport.explanation}</p>
          </div>

          <div className="evidence-section">
            <h3>Evidence Findings ({selectedReport.evidence.length})</h3>
            <ul className="evidence-list">
              {selectedReport.evidence.map((f) => (
                <li key={f.id} className="evidence-item">
                  <div className="evidence-title-row">
                    <span className={`status-pill ${getDecisionBadgeClass(f.status)}`}>{f.status}</span>
                    <strong>
                      [{f.category}] {f.checkName}
                    </strong>
                  </div>
                  <p className="evidence-summary">{f.summary}</p>
                  {f.details && f.details !== '{}' && (
                    <details className="evidence-details">
                      <summary>Technical Details</summary>
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
