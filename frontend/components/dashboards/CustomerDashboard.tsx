'use client';

import { useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';

interface OrderSummary {
  orderId: string;
  status: string;
  totalAmount: number;
}

export default function CustomerDashboard() {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient
      .get('/api/v1/orders/mine')
      .then((res) => setOrders(res.data))
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <h2 className="font-display text-xl font-bold text-ink">Your orders</h2>
      {loading ? (
        <p className="mt-4 text-muted">Loading…</p>
      ) : orders.length === 0 ? (
        <p className="mt-4 text-muted">No orders yet.</p>
      ) : (
        <div className="mt-4 divide-y divide-ink/10 rounded-lg border border-ink/10 bg-paper-raised">
          {orders.map((o) => (
            <div key={o.orderId} className="flex justify-between p-4">
              <span className="font-mono text-sm text-muted">{o.orderId.slice(0, 8)}</span>
              <span className="text-sm font-semibold text-ink">{o.status}</span>
              <span className="tabular-nums font-mono text-ink">${o.totalAmount.toFixed(2)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
