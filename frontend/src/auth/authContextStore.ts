import { createContext } from 'react'
import type { SafeCompany, SafeUser, TokenResponse } from '../services/api'
export type AuthState = { loading: boolean; user: SafeUser | null; company: SafeCompany | null; companies: SafeCompany[]; login: (email: string,password: string,companyId?: string) => Promise<TokenResponse>; logout: () => Promise<void>; switchCompany: (companyId: string) => Promise<void>; reloadCompanies: () => Promise<void> }
export const AuthContext = createContext<AuthState | null>(null)
