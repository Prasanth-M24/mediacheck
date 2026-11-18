import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiRequest, refreshAccessToken, setAccessToken, type SafeCompany, type SafeUser, type TokenResponse } from '../services/api'
import { AuthContext } from './authContextStore'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loading,setLoading] = useState(true)
  const [user,setUser] = useState<SafeUser|null>(null)
  const [company,setCompany] = useState<SafeCompany|null>(null)
  const [companies,setCompanies] = useState<SafeCompany[]>([])
  const [expiresIn,setExpiresIn] = useState(0)
  const accept = useCallback((token: TokenResponse) => { setAccessToken(token.accessToken); setUser(token.user); setCompany(token.activeCompany); setExpiresIn(token.expiresIn) }, [])
  const reloadCompanies = useCallback(async () => { const data = await apiRequest<{ companies: SafeCompany[] }>('/api/v1/auth/companies'); setCompanies(data.companies) }, [])

  useEffect(() => { let active = true; refreshAccessToken().then(async token => { if (!active || !token) return; accept(token); try { const data = await apiRequest<{ companies: SafeCompany[] }>('/api/v1/auth/companies'); if(active)setCompanies(data.companies) } catch { setAccessToken(null) } }).finally(() => { if(active)setLoading(false) }); return () => { active=false } }, [accept])
  useEffect(() => { const refreshed=(event:Event)=>accept((event as CustomEvent<TokenResponse>).detail); const expired=()=>{setAccessToken(null);setUser(null);setCompany(null);setCompanies([])};window.addEventListener('mediacheck:token-refreshed',refreshed);window.addEventListener('mediacheck:session-expired',expired);return()=>{window.removeEventListener('mediacheck:token-refreshed',refreshed);window.removeEventListener('mediacheck:session-expired',expired)} },[accept])
  useEffect(() => { if(!user || expiresIn<30)return; const timer=window.setTimeout(async()=>{const token=await refreshAccessToken();if(token)accept(token);else{setUser(null);setCompany(null);setCompanies([])}},Math.max(15,expiresIn-30)*1000);return()=>window.clearTimeout(timer) },[user,expiresIn,accept])

  const login = useCallback(async (email:string,password:string,companyId?:string) => {
    const result=await apiRequest<TokenResponse>('/api/v1/auth/login',{method:'POST',body:JSON.stringify({email,password,...(companyId?{companyId}:{})})},false)
    accept(result); const data=await apiRequest<{companies:SafeCompany[]}>('/api/v1/auth/companies');setCompanies(data.companies);return result
  },[accept])
  const logout = useCallback(async()=>{try{await apiRequest<void>('/api/v1/auth/logout',{method:'POST'},false)}finally{setAccessToken(null);setUser(null);setCompany(null);setCompanies([])}},[])
  const switchCompany = useCallback(async(companyId:string)=>{const token=await apiRequest<TokenResponse>('/api/v1/auth/switch-company',{method:'POST',body:JSON.stringify({companyId})},false);accept(token)},[accept])
  const value=useMemo(()=>({loading,user,company,companies,login,logout,switchCompany,reloadCompanies}),[loading,user,company,companies,login,logout,switchCompany,reloadCompanies])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

