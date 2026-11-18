function getApiBaseUrl(): string {
  const envUrl = import.meta.env.VITE_API_BASE_URL;
  if (typeof window !== 'undefined' && window.location) {
    if (envUrl) {
      try {
        const parsed = new URL(envUrl);
        if (['localhost', '127.0.0.1'].includes(window.location.hostname)) {
          parsed.hostname = window.location.hostname;
          return parsed.origin;
        }
      } catch { /* Preserve the configured URL when it is not a valid URL object. */ }
      return envUrl;
    }
    return `http://${window.location.hostname}:8080`;
  }
  return envUrl ?? 'http://localhost:8080';
}

const apiBaseUrl = getApiBaseUrl();

export type SafeUser = { id: string; email: string; displayName: string }
export type SafeCompany = { id: string; name: string; legalName: string | null; businessEmail: string | null; membershipId: string; role: string }
export type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number; user: SafeUser; activeCompany: SafeCompany }
export type RegisterResponse = { userId: string | null; companyId: string | null; status: string; message: string; developmentVerificationToken: string | null }
export type SystemInfo = { service: string; version: string; apiVersion: string }

let csrfToken: string | null = null
let accessToken: string | null = null
export function setAccessToken(value: string | null) { accessToken = value }

async function csrf(): Promise<string> {
  if (csrfToken) return csrfToken
  const response = await fetch(`${apiBaseUrl}/api/v1/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw new Error('Unable to initialize a secure session')
  const body = await response.json() as { token: string }
  csrfToken = body.token
  return body.token
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-XSRF-TOKEN', await csrf())
  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, method, headers, credentials: 'include' })
  if (response.status === 401 && retry && !path.endsWith('/auth/refresh') && !path.endsWith('/auth/login')) {
    const refreshed = await refreshAccessToken()
    if (refreshed) return apiRequest<T>(path, init, false)
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('mediacheck:session-expired'))
  }
  if (response.status === 204) return undefined as T
  const body = await response.json().catch(() => ({})) as { error?: string; message?: string; [key: string]: unknown }
  if (!response.ok) {
    const error = new Error(body.error ?? body.message ?? `Request failed (${response.status})`) as Error & { status?: number; details?: typeof body }
    error.status = response.status
    error.details = body
    throw error
  }
  return body as T
}

export async function apiBlob(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const headers = new Headers()
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(`${apiBaseUrl}${path}`, { headers, credentials: 'include' })
  if (!response.ok) throw new Error(`Request failed (${response.status})`)
  const disposition = response.headers.get('Content-Disposition')
  const filename = disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? null
  return { blob: await response.blob(), filename }
}

export async function refreshAccessToken(): Promise<TokenResponse | null> {
  try {
    const token = await csrf()
    const response = await fetch(`${apiBaseUrl}/api/v1/auth/refresh`, {
      method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': token },
    })
    if (!response.ok) { setAccessToken(null); if(typeof window!=='undefined')window.dispatchEvent(new Event('mediacheck:session-expired')); return null }
    const data = await response.json() as TokenResponse
    setAccessToken(data.accessToken)
    if(typeof window!=='undefined')window.dispatchEvent(new CustomEvent<TokenResponse>('mediacheck:token-refreshed',{detail:data}))
    return data
  } catch { setAccessToken(null); return null }
}

export async function getSystemInfo() {
  return apiRequest<SystemInfo>('/api/v1/system/info')
}

export type RegistryAsset = { id: string; title: string; assetType: string; description: string | null; rightsHolder: string | null; status: string; originalFilename: string | null; mimeType: string | null; fileSize: number | null; sha256Checksum: string | null; hasFile: boolean; createdAt: string }
export type RegistryLicence = { id: string; licenceReference: string; name: string | null; description: string | null; licenceType: string | null; licensorName: string; licenseeName: string | null; validFrom: string | null; validUntil: string | null; status: string; effectiveStatus: string; assetCount: number; documentCount: number; createdAt: string }
export type RegistryDashboard = { totalAssets: number; activeAssets: number; archivedAssets: number; totalLicences: number; activeLicences: number; expiredLicences: number; expiringWithin30Days: number; draftLicences: number; revokedLicences: number; recentAssets: Array<{ id: string; title: string; assetType: string; status: string }>; recentLicences: Array<{ id: string; name: string | null; licenceReference: string; status: string; validUntil: string | null }> }
export type Paged<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export function registryDashboard() { return apiRequest<RegistryDashboard>('/api/v1/rights-registry/dashboard') }
export function listRegistryAssets(search = '') { return apiRequest<Paged<RegistryAsset>>(`/api/v1/rights-assets?size=50${search ? `&search=${encodeURIComponent(search)}` : ''}`) }
export function createRegistryAsset(body: Record<string, unknown>) { return apiRequest<RegistryAsset>('/api/v1/rights-assets', { method: 'POST', body: JSON.stringify(body) }) }
export function uploadRegistryAsset(id: string, file: File) { const body = new FormData(); body.append('file', file); return apiRequest<RegistryAsset>(`/api/v1/rights-assets/${id}/file`, { method: 'POST', body }) }
export function listRegistryLicences(search = '') { return apiRequest<Paged<RegistryLicence>>(`/api/v1/licences?size=50${search ? `&search=${encodeURIComponent(search)}` : ''}`) }
export function createRegistryLicence(body: Record<string, unknown>) { return apiRequest<RegistryLicence>('/api/v1/licences', { method: 'POST', body: JSON.stringify(body) }) }
export function getRegistryAsset(id: string) { return apiRequest<RegistryAsset>(`/api/v1/rights-assets/${id}`) }
export function updateRegistryAsset(id: string, body: Record<string, unknown>) { return apiRequest<RegistryAsset>(`/api/v1/rights-assets/${id}`, { method: 'PUT', body: JSON.stringify(body) }) }
export function archiveRegistryAsset(id: string) { return apiRequest<void>(`/api/v1/rights-assets/${id}`, { method: 'DELETE' }) }
export function downloadRegistryAsset(id: string) { return apiBlob(`/api/v1/rights-assets/${id}/file`) }
export function getRegistryLicence(id: string) { return apiRequest<RegistryLicence>(`/api/v1/licences/${id}`) }
export function updateRegistryLicence(id: string, body: Record<string, unknown>) { return apiRequest<RegistryLicence>(`/api/v1/licences/${id}`, { method: 'PUT', body: JSON.stringify(body) }) }
export function changeRegistryLicenceStatus(id: string, status: string) { return apiRequest<RegistryLicence>(`/api/v1/licences/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }) }
export type RegistryDocument = { id: string; originalFilename: string; mimeType: string; fileSize: number; sha256Checksum: string; createdAt: string }
export function listLicenceDocuments(id: string) { return apiRequest<RegistryDocument[]>(`/api/v1/licences/${id}/documents`) }
export function uploadLicenceDocument(id: string, file: File) { const body = new FormData(); body.append('file', file); return apiRequest<RegistryDocument>(`/api/v1/licences/${id}/documents`, { method: 'POST', body }) }
export function downloadLicenceDocument(licenceId: string, documentId: string) { return apiBlob(`/api/v1/licences/${licenceId}/documents/${documentId}/file`) }
export function deleteLicenceDocument(licenceId: string, documentId: string) { return apiRequest<void>(`/api/v1/licences/${licenceId}/documents/${documentId}`, { method: 'DELETE' }) }
