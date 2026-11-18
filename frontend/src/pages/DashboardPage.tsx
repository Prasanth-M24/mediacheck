import { useEffect, useState } from 'react'
import { EmptyState, ErrorState, LoadingState, MetricCard, PageHeader, SectionCard } from '../components/ui'
import { getSystemInfo, registryDashboard, type RegistryDashboard, type SystemInfo } from '../services/api'

export function DashboardPage() {
  const [info, setInfo] = useState<SystemInfo | null>(null)
  const [registry, setRegistry] = useState<RegistryDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  useEffect(() => { Promise.all([getSystemInfo(), registryDashboard()]).then(([i, r]) => { setInfo(i); setRegistry(r) }).catch(() => setError(true)).finally(() => setLoading(false)) }, [])
  return <>
    <PageHeader eyebrow="DASHBOARD" title="Overview">Monitor scans, rights checks, and licence readiness from one place.</PageHeader>
    <div className="metrics-grid">
      <MetricCard label="Total scans" value="Not available" detail="Scan processing is planned for a later phase." icon="◈" />
      <MetricCard label="Rights assets" value={registry ? String(registry.totalAssets) : '—'} detail="Company-owned rights records" icon="◆" />
      <MetricCard label="Active licences" value={registry ? String(registry.activeLicences) : '—'} detail={registry ? `${registry.expiringWithin30Days} expiring within 30 days` : 'Loading registry data'} icon="□" />
    </div>
    <div className="dashboard-grid">
      <SectionCard title="Recent rights assets">{registry?.recentAssets.length ? registry.recentAssets.map(a => <p key={a.id}><strong>{a.title}</strong> · {a.status}</p>) : <EmptyState title="No rights assets">Create the first rights asset in the Rights Registry.</EmptyState>}</SectionCard>
      <SectionCard title="Recent licences">{registry?.recentLicences.length ? registry.recentLicences.map(l => <p key={l.id}><strong>{l.name || l.licenceReference}</strong> · {l.status}</p>) : <EmptyState title="No licences">Create the first licence in the Rights Registry.</EmptyState>}</SectionCard>
    </div>
    <SectionCard title="Service connection">{loading && <LoadingState />}{error && <ErrorState>The backend or registry endpoint is unavailable.</ErrorState>}{info && <div className="service-info"><span className="service-dot" aria-hidden="true" /><div><strong>Backend and registry reachable</strong><p>{info.service} · version {info.version} · API {info.apiVersion}</p></div></div>}</SectionCard>
  </>
}
