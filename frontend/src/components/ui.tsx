import type { ReactNode } from 'react'

export function PageHeader({ eyebrow, title, children }: { eyebrow?: string; title: string; children: ReactNode }) { return <header className="page-header">{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1><p>{children}</p></header> }
export function MetricCard({ label, value, detail, icon }: { label: string; value: string; detail: string; icon: string }) { return <section className="metric-card"><div><p className="metric-label">{label}</p><strong className="metric-value">{value}</strong><p className="metric-detail">{detail}</p></div><span className="metric-icon" aria-hidden="true">{icon}</span></section> }
export function EmptyState({ title, children }: { title: string; children: ReactNode }) { return <div className="empty-state"><span aria-hidden="true">○</span><div><strong>{title}</strong><p>{children}</p></div></div> }
export function LoadingState({ label = 'Loading available information…' }: { label?: string }) { return <div className="inline-state" role="status"><span className="spinner" aria-hidden="true" />{label}</div> }
export function ErrorState({ children }: { children: ReactNode }) { return <div className="inline-state error-state" role="status"><span aria-hidden="true">!</span>{children}</div> }
export function SectionCard({ title, children }: { title: string; children: ReactNode }) { return <section className="section-card"><div className="section-heading"><h2>{title}</h2></div>{children}</section> }
