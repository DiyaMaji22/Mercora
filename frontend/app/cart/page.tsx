'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCartStore } from '@/stores/cartStore';
import { useAuthStore } from '@/stores/authStore';
import apiClient from '@/lib/apiClient';
import { useState } from 'react';

export default function CartPage() {
  const { items, removeItem, updateQuantity, totalPrice } = useCartStore();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const router = useRouter();
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCheckout = async () => {
    if (!isAuthenticated) {
      router.push('/login?redirect=/cart');
      return;
    }
    setSyncing(true);
    setError(null);
    try {
      // Sync the locally-held cart to the server-side Redis cart before
      // checkout, since placeOrder() reads from CartService (Redis), not
      // this client-side store.
      for (const item of items) {
        await apiClient.post('/api/v1/cart/items', {
          productId: item.productId,
          quantity: item.quantity,
        });
      }
      router.push('/checkout');
    } catch (e: any) {
      setError(e.response?.data?.message || 'Could not start checkout. Please try again.');
    } finally {
      setSyncing(false);
    }
  };

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-24 text-center">
        <h1 className="font-display text-2xl font-bold text-ink">Your cart is empty</h1>
        <p className="mt-2 text-muted">Nothing held yet — browse the live catalog to find something.</p>
        <Link href="/products" className="mt-6 inline-block rounded bg-ink px-6 py-3 font-semibold text-paper hover:bg-signal hover:text-ink">
          Browse products
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-6 py-12">
      <h1 className="font-display text-3xl font-bold text-ink">Your cart</h1>
      <p className="mt-2 text-sm text-muted">
        Nothing is reserved yet. Stock is only held once you start checkout.
      </p>

      <div className="mt-8 divide-y divide-ink/10 rounded-lg border border-ink/10 bg-paper-raised">
        {items.map((item) => (
          <div key={item.productId} className="flex items-center justify-between p-5">
            <div>
              <p className="font-display font-bold text-ink">{item.name}</p>
              <p className="tabular-nums text-sm text-muted">${item.price.toFixed(2)} each</p>
            </div>
            <div className="flex items-center gap-4">
              <div className="flex items-center rounded border border-ink/20">
                <button
                  onClick={() => updateQuantity(item.productId, Math.max(1, item.quantity - 1))}
                  className="px-2 py-1 hover:bg-ink/5"
                >
                  −
                </button>
                <span className="tabular-nums w-8 text-center">{item.quantity}</span>
                <button
                  onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                  className="px-2 py-1 hover:bg-ink/5"
                >
                  +
                </button>
              </div>
              <p className="tabular-nums w-20 text-right font-mono font-bold text-ink">
                ${(item.price * item.quantity).toFixed(2)}
              </p>
              <button
                onClick={() => removeItem(item.productId)}
                className="text-sm text-danger hover:underline"
              >
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 flex items-center justify-between border-t border-ink/10 pt-6">
        <span className="font-display text-lg font-bold text-ink">Total</span>
        <span className="tabular-nums font-mono text-2xl font-bold text-ink">
          ${totalPrice().toFixed(2)}
        </span>
      </div>

      {error && <p className="mt-4 text-sm text-danger">{error}</p>}

      <button
        onClick={handleCheckout}
        disabled={syncing}
        className="mt-6 w-full rounded bg-signal px-6 py-4 font-semibold text-ink transition hover:bg-signal-dim disabled:opacity-60"
      >
        {syncing ? 'Placing your hold…' : 'Start checkout — hold my stock'}
      </button>
    </div>
  );
}
