'use client';

import { useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/stores/authStore';

interface Product {
  id: string;
  sku: string;
  name: string;
  price: number;
}

export default function RetailerDashboard() {
  const userId = useAuthStore((s) => s.userId);
  const [products, setProducts] = useState<Product[]>([]);
  const [stockUpdates, setStockUpdates] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) return;
    apiClient
      .get(`/api/v1/products/search`)
      .then((res) => setProducts(res.data))
      .catch(() => setProducts([]));
  }, [userId]);

  const handleStockUpdate = async (productId: string) => {
    const value = stockUpdates[productId];
    if (!value) return;
    setSavingId(productId);
    try {
      await apiClient.put(`/api/v1/inventory/${productId}/stock`, null, {
        params: { quantity: Number(value) },
      });
    } finally {
      setSavingId(null);
    }
  };

  return (
    <div>
      <h2 className="font-display text-xl font-bold text-ink">Your listings</h2>
      <p className="mt-1 text-sm text-muted">
        Stock changes here push live to shoppers via Kafka + SSE within seconds.
      </p>

      <div className="mt-6 space-y-3">
        {products.map((p) => (
          <div key={p.id} className="flex items-center justify-between rounded-lg border border-ink/10 bg-paper-raised p-4">
            <div>
              <p className="font-semibold text-ink">{p.name}</p>
              <p className="text-xs text-muted">{p.sku}</p>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="number"
                placeholder="New stock"
                className="w-28 rounded border border-ink/20 px-2 py-1.5 text-sm focus:border-signal focus:outline-none"
                value={stockUpdates[p.id] ?? ''}
                onChange={(e) => setStockUpdates((s) => ({ ...s, [p.id]: e.target.value }))}
              />
              <button
                onClick={() => handleStockUpdate(p.id)}
                disabled={savingId === p.id}
                className="rounded bg-ink px-4 py-1.5 text-sm font-semibold text-paper hover:bg-signal hover:text-ink disabled:opacity-60"
              >
                {savingId === p.id ? 'Saving…' : 'Update'}
              </button>
            </div>
          </div>
        ))}
        {products.length === 0 && <p className="text-muted">No listings yet.</p>}
      </div>
    </div>
  );
}
