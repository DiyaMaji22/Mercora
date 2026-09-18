'use client';

import Link from 'next/link';
import { useAuthStore } from '@/stores/authStore';
import { useCartStore } from '@/stores/cartStore';

export default function NavBar() {
  const { email, role, logout } = useAuthStore();
  const totalItems = useCartStore((s) => s.totalItems());

  return (
    <header className="sticky top-0 z-50 border-b border-ink/10 bg-ink text-paper">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href="/" className="font-display text-xl font-bold tracking-tightest">
          FLASH<span className="text-signal">MARKET</span>
        </Link>

        <nav className="flex items-center gap-6 text-sm">
          <Link href="/products" className="hover:text-signal">Browse</Link>
          {email ? (
            <>
              {role && (
                <Link href={`/dashboard/${role.toLowerCase().replace('_', '-')}`} className="hover:text-signal">
                  Dashboard
                </Link>
              )}
              <Link href="/cart" className="relative hover:text-signal">
                Cart
                {totalItems > 0 && (
                  <span className="tabular-nums ml-1 rounded-full bg-signal px-1.5 py-0.5 text-xs font-bold text-ink">
                    {totalItems}
                  </span>
                )}
              </Link>
              <button onClick={logout} className="text-muted hover:text-signal">
                Sign out
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="hover:text-signal">Sign in</Link>
              <Link href="/register" className="rounded bg-signal px-3 py-1.5 font-semibold text-ink hover:bg-signal-dim">
                Get started
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
