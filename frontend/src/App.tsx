import { useState } from 'react'
import { Link, NavLink, Navigate, Outlet, Route, Routes, useNavigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { DashboardPage } from './pages/DashboardPage'
import { MediaScansPage } from './pages/MediaScansPage'
import { RightsRegistryPage } from './pages/RightsRegistryPage'
import { PlaceholderPage } from './pages/PlaceholderPage'
import { AccessDeniedPage, ForgotPasswordPage, InvitationPage, LoginPage, RegisterPage, VerifyEmailPage } from './pages/AuthPages'
import { LoadingState } from './components/ui'
import { ReportsPage } from './pages/ReportsPage'
import { PoliciesPage } from './pages/PoliciesPage'

const navigation = [{ label: 'Dashboard', path: '/' }, { label: 'Media Scans', path: '/media-scans' }, { label: 'Rights Registry', path: '/rights-registry' }, { label: 'Policies', path: '/policies' }, { label: 'Reports', path: '/reports' }, { label: 'Audit History', path: '/audit-history' }]

function RequireAuth(){const {loading,user}=useAuth();if(loading)return <main className="page-content"><LoadingState label="Restoring secure session…"/></main>;return user?<Outlet/>:<Navigate to="/login" replace/>}
function ApplicationLayout(){
  const [menuOpen,setMenuOpen]=useState(false);const {user,company,companies,switchCompany,logout}=useAuth();const navigate=useNavigate()
  async function changeCompany(id:string){try{await switchCompany(id)}catch{await logout();navigate('/login',{replace:true})}}
  async function signOut(){await logout();navigate('/login',{replace:true})}
  return <div className="app-shell"><header className="top-nav"><NavLink className="brand" to="/" aria-label="MEDIACHECK dashboard"><span className="brand-mark" aria-hidden="true">✓</span><span><b>MEDIA</b><em>CHECK</em></span></NavLink><button className="nav-toggle" type="button" aria-expanded={menuOpen} aria-controls="primary-navigation" onClick={()=>setMenuOpen(v=>!v)}><span className="sr-only">Toggle navigation</span><span aria-hidden="true">☰</span></button><nav id="primary-navigation" className={menuOpen?'nav-links is-open':'nav-links'} aria-label="Primary navigation">{navigation.map(item=><NavLink key={item.path} to={item.path} end={item.path==='/'} onClick={()=>setMenuOpen(false)}>{item.label}</NavLink>)}</nav><div className="account-actions"><label className="company-switcher"><span className="sr-only">Active company</span><select aria-label="Active company" value={company?.id??''} onChange={e=>void changeCompany(e.target.value)}>{companies.map(c=><option value={c.id} key={c.id}>{c.name}</option>)}</select></label><div className="account-menu"><span className="profile-icon" aria-hidden="true">{user?.displayName.slice(0,1).toUpperCase()}</span><span className="account-name">{user?.displayName}</span><button type="button" className="quiet-button" onClick={()=>void signOut()}>Sign out</button></div></div></header><main className="page-content"><Outlet/></main></div>
}

function AppRoutes(){return <Routes><Route path="/login" element={<LoginPage/>}/><Route path="/register" element={<RegisterPage/>}/><Route path="/forgot-password" element={<ForgotPasswordPage/>}/><Route path="/verify-email" element={<VerifyEmailPage/>}/><Route path="/access-denied" element={<AccessDeniedPage/>}/><Route path="/auth/accept-invitation" element={<InvitationPage/>}/><Route element={<RequireAuth/>}><Route element={<ApplicationLayout/>}><Route index element={<DashboardPage/>}/><Route path="media-scans" element={<MediaScansPage/>}/><Route path="rights-registry" element={<RightsRegistryPage/>}/><Route path="policies" element={<PoliciesPage/>}/><Route path="reports" element={<ReportsPage/>}/>{navigation.slice(3).filter(item=>!['/reports','/policies'].includes(item.path)).map(item=><Route key={item.path} path={item.path.slice(1)} element={<PlaceholderPage title={item.label}/>}/>)}</Route></Route><Route path="*" element={<main className="auth-page"><section className="auth-card"><h1>Page not found</h1><Link to="/">Return to MEDIACHECK</Link></section></main>}/></Routes>}
export default function App(){return <AuthProvider><AppRoutes/></AuthProvider>}
