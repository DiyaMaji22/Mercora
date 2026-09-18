'use client';

import { useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';

interface BulkOrder {
  id: string;
  productId: string;
  quantity: number;
  status: string;
  requestedAt: string;
}

export default function BulkBuyerDashboard() {
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const [history, setHistory] = useState<BulkOrder[]>([]);

  const loadHistory = () => {
    apiClient
      .get('/api/v1/bulk-orders/mine')
      .then((res) => setHistory(res.data))
      .catch(() => setHistory([]));
  };

  useEffect(loadHistory, []);

  const requestLock = async () => {
    if (!productId || !quantity) return;
    try {
      await apiClient.post('/api/v1/bulk-orders', {
        productId,
        quantity: Number(quantity),
      });
      setResult('Bulk lock placed — pending admin approval.');
      setProductId('');
      setQuantity('');
      loadHistory();
    } catch (e: any) {
      setResult(e.response?.data?.message || 'Could not place bulk lock.');
    }
  };

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <div className="rounded-lg border border-ink/10 bg-paper-raised p-6">
        <h2 className="font-display text-lg font-bold text-ink">Request a bulk order</h2>
        <p className="mt-2 text-sm text-muted">
          Bulk locks hold stock for 2 hours while an admin reviews your request.
        </p>

        <div className="mt-4 space-y-3">
          <input
            placeholder="Product ID"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            className="w-full rounded border border-ink/20 px-3 py-2 text-sm focus:border-signal focus:outline-none"
          />
          <input
            placeholder="Quantity"
            type="number"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            className="w-full rounded border border-ink/20 px-3 py-2 text-sm focus:border-signal focus:outline-none"
          />
          <button
            onClick={requestLock}
            className="w-full rounded bg-signal px-4 py-2.5 font-semibold text-ink hover:bg-signal-dim"
          >
            Request bulk lock
          </button>
          {result && <p className="text-sm text-muted">{result}</p>}
        </div>
      </div>

      <div className="rounded-lg border border-ink/10 bg-paper-raised p-6">
        <h2 className="font-display text-lg font-bold text-ink">Your requests</h2>
        <div className="mt-4 space-y-2">
          {history.length === 0 ? (
            <p className="text-sm text-muted">No bulk orders yet.</p>
          ) : (
            history.map((b) => (
              <div key={b.id} className="flex items-center justify-between rounded border border-ink/10 p-3 text-sm">
                <span className="font-mono text-xs text-muted">{b.productId.slice(0, 8)}</span>
                <span>Qty {b.quantity}</span>
                <span
                  className={
                    b.status === 'APPROVED'
                      ? 'font-semibold text-success'
                      : b.status === 'REJECTED'
                      ? 'font-semibold text-danger'
                      : 'font-semibold text-signal-dim'
                  }
                >
                  {b.status}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
