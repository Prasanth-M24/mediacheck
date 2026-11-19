import { useEffect, useState } from 'react'
import { EmptyState } from '../components/ui'
import {
  createPolicy,
  createPolicyVersion,
  listPolicies,
  type CompanyPolicy,
} from '../services/api'

export function PoliciesPage() {
  const [policies, setPolicies] = useState<CompanyPolicy[]>([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(false)

  // New Policy form state
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [policyName, setPolicyName] = useState('')
  const [requireAiDisclosure, setRequireAiDisclosure] = useState(true)
  const [blockAiGenerated, setBlockAiGenerated] = useState(false)
  const [requireValidC2pa, setRequireValidC2pa] = useState(false)
  const [requireMandatoryMetadata, setRequireMandatoryMetadata] = useState(false)
  const [allowedChannels, setAllowedChannels] = useState('SOCIAL,WEB')
  const [allowedTerritories, setAllowedTerritories] = useState('GLOBAL,US,EU')

  // New Version form state
  const [selectedPolicyId, setSelectedPolicyId] = useState<string | null>(null)
  const [versionRulesJson, setVersionRulesJson] = useState('{\n  "requireAiDisclosure": true\n}')

  const load = async () => {
    try {
      setLoading(true)
      const data = await listPolicies()
      setPolicies(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to load policies')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const handleCreatePolicy = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!policyName.trim()) {
      setError('Policy name is required.')
      return
    }

    const rules = {
      requireAiDisclosure,
      blockAiGenerated,
      requireValidC2pa,
      requireMandatoryMetadata,
      allowedChannels: allowedChannels
        .split(',')
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean),
      allowedTerritories: allowedTerritories
        .split(',')
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean),
    }

    try {
      await createPolicy(policyName.trim(), JSON.stringify(rules))
      setNotice(`Policy '${policyName}' created successfully.`)
      setShowCreateModal(false)
      setPolicyName('')
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create policy')
    }
  }

  const handleCreateVersion = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPolicyId) return
    try {
      // Validate JSON syntax
      JSON.parse(versionRulesJson)
      await createPolicyVersion(selectedPolicyId, versionRulesJson)
      setNotice('New policy version published successfully.')
      setSelectedPolicyId(null)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid JSON or version creation failed')
    }
  }

  return (
    <div className="scans-page">
      <p className="breadcrumb">Governance &amp; Rules</p>
      <h1>Company Deployment Policies</h1>

      <section className="scan-section">
        <div className="section-row">
          <h2>Configured Review Policies ({policies.length})</h2>
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              setError('')
              setNotice('')
              setShowCreateModal(true)
            }}
          >
            + Create New Policy
          </button>
        </div>

        {error && <p className="upload-notice error-notice" role="alert">{error}</p>}
        {notice && <p className="upload-notice success-notice" role="status">{notice}</p>}

        {policies.length === 0 ? (
          <EmptyState title="No policies configured">
            Deployment policies define deterministic rules for channel restrictions, territory rules, AI disclosure, and C2PA credentials.
          </EmptyState>
        ) : (
          <div className="results-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Policy Name</th>
                  <th>Status</th>
                  <th>Created At</th>
                  <th>Last Updated</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {policies.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.name}</strong>
                    </td>
                    <td>
                      <span className="status-pill badge-success">{p.status}</span>
                    </td>
                    <td>{new Date(p.created_at).toLocaleDateString()}</td>
                    <td>{new Date(p.updated_at).toLocaleDateString()}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-action"
                        onClick={() => {
                          setSelectedPolicyId(p.id)
                          setError('')
                        }}
                      >
                        Publish Version
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Create Policy Modal */}
      {showCreateModal && (
        <section className="scan-section report-modal">
          <div className="section-row report-header">
            <h2>Create New Company Policy</h2>
            <button type="button" className="btn-close" onClick={() => setShowCreateModal(false)}>
              ✕ Close
            </button>
          </div>

          <form onSubmit={(e) => void handleCreatePolicy(e)} className="policy-form">
            <div className="form-group">
              <label htmlFor="pname">Policy Name:</label>
              <input
                id="pname"
                type="text"
                value={policyName}
                placeholder="e.g., Global Commercial Release Policy"
                onChange={(e) => setPolicyName(e.target.value)}
                required
              />
            </div>

            <fieldset className="form-fieldset">
              <legend>Compliance &amp; Verification Rules</legend>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={requireAiDisclosure}
                  onChange={(e) => setRequireAiDisclosure(e.target.checked)}
                />
                <span>Require AI Disclosure (flag advisory if synthetic patterns detected)</span>
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={blockAiGenerated}
                  onChange={(e) => setBlockAiGenerated(e.target.checked)}
                />
                <span>Block Synthetic Media (strict zero-tolerance for AI generation)</span>
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={requireValidC2pa}
                  onChange={(e) => setRequireValidC2pa(e.target.checked)}
                />
                <span>Require Valid C2PA Content Credentials</span>
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={requireMandatoryMetadata}
                  onChange={(e) => setRequireMandatoryMetadata(e.target.checked)}
                />
                <span>Require Mandatory Publication Metadata (Channel, Territory, Use)</span>
              </label>
            </fieldset>

            <div className="form-group">
              <label htmlFor="channels">Permitted Channels (comma-separated):</label>
              <input
                id="channels"
                type="text"
                value={allowedChannels}
                placeholder="SOCIAL, WEB, BROADCAST, PRINT"
                onChange={(e) => setAllowedChannels(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="territories">Permitted Territories (comma-separated):</label>
              <input
                id="territories"
                type="text"
                value={allowedTerritories}
                placeholder="GLOBAL, US, EU, UK, APAC"
                onChange={(e) => setAllowedTerritories(e.target.value)}
              />
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary" disabled={loading}>
                Save &amp; Activate Policy
              </button>
              <button type="button" className="btn-secondary" onClick={() => setShowCreateModal(false)}>
                Cancel
              </button>
            </div>
          </form>
        </section>
      )}

      {/* Publish Version Modal */}
      {selectedPolicyId && (
        <section className="scan-section report-modal">
          <div className="section-row report-header">
            <h2>Publish New Policy Version</h2>
            <button type="button" className="btn-close" onClick={() => setSelectedPolicyId(null)}>
              ✕ Close
            </button>
          </div>

          <form onSubmit={(e) => void handleCreateVersion(e)} className="policy-form">
            <p>Define updated deterministic rules for this policy version in standard JSON format:</p>
            <div className="form-group">
              <textarea
                rows={8}
                value={versionRulesJson}
                onChange={(e) => setVersionRulesJson(e.target.value)}
                className="code-textarea"
                required
              />
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary" disabled={loading}>
                Publish Version
              </button>
              <button type="button" className="btn-secondary" onClick={() => setSelectedPolicyId(null)}>
                Cancel
              </button>
            </div>
          </form>
        </section>
      )}
    </div>
  )
}
