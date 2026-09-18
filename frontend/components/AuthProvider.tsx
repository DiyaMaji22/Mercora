'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/stores/authStore';

/**
 * Zustand's persist middleware writes to localStorage, which Next.js
 * middleware (edge runtime) cannot read. This component mirrors the token
 * into a cookie on every change so /middleware.ts can enforce route
 * protection before a protected page ever renders server-side.
 */
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (accessToken) {
      document.cookie = `ecommerce_access_token=${accessToken}; path=/; max-age=3600; SameSite=Lax`;
    } else {
      document.cookie = 'ecommerce_access_token=; path=/; max-age=0';
    }
  }, [accessToken]);

  return <>{children}</>;
}
