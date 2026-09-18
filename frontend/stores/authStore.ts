import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { jwtDecode } from 'jwt-decode';

export type Role = 'ADMIN' | 'CUSTOMER' | 'RETAILER' | 'BULK_BUYER';

interface DecodedToken {
  sub: string;
  userId: string;
  roles: Role[];
  exp: number;
}

interface AuthState {
  accessToken: string | null;
  email: string | null;
  userId: string | null;
  role: Role | null;
  login: (accessToken: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      email: null,
      userId: null,
      role: null,

      login: (accessToken: string) => {
        const decoded = jwtDecode<DecodedToken>(accessToken);
        set({
          accessToken,
          email: decoded.sub,
          userId: decoded.userId,
          role: decoded.roles?.[0] ?? null,
        });
      },

      logout: () => set({ accessToken: null, email: null, userId: null, role: null }),

      isAuthenticated: () => {
        const token = get().accessToken;
        if (!token) return false;
        try {
          const decoded = jwtDecode<DecodedToken>(token);
          return decoded.exp * 1000 > Date.now();
        } catch {
          return false;
        }
      },
    }),
    { name: 'ecommerce-auth' }
  )
);
