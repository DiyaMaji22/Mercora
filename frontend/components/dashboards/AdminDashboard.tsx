'use client';

import { useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/stores/authStore';

interface BulkOrder {
  id: string;
  userId: string;
  productId: string;
  quantity: number;
  status: string;
  requestedAt: string;
}

export default function AdminDashboard() {
  const [pending, setPending] = useState<BulkOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [decidingId, setDecidingId] = useState<string | null>(null);

  const load = () => {
    apiClient
      .get('/api/v1/bulk-orders', { params: { status: 'PENDING_APPROVAL' } })
      .then((res) => setPending(res.data))
      .catch(() => setPending([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const decide = async (id: string, approve: boolean) => {
    setDecidingId(id);
    try {
      await apiClient.post(`/api/v1/bulk-orders/${id}/${approve ? 'approve' : 'reject'}`);
      setPending((prev) => prev.filter((b) => b.id !== id));
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <div className="rounded-lg border border-ink/10 bg-paper-raised p-6">
        <h2 className="font-display text-lg font-bold text-ink">Bulk order approvals</h2>
        <p className="mt-1 text-sm text-muted">
          Requests hold a 2-hour lock on stock while awaiting your decision.
        </p>

        <div className="mt-4 space-y-3">
          {loading ? (
            <p className="text-sm text-muted">Loading…</p>
          ) : pending.length === 0 ? (
            <p className="text-sm text-muted">Nothing pending right now.</p>
          ) : (
            pending.map((b) => (
              <div key={b.id} className="rounded border border-ink/10 p-4">
                <p className="text-sm text-ink">
                  Product <span className="font-mono text-xs">{b.productId.slice(0, 8)}</span> ·{' '}
                  Qty {b.quantity}
                </p>
                <p className="mt-1 text-xs text-muted">
                  Requested {new Date(b.requestedAt).toLocaleString()}
                </p>
                <div className="mt-3 flex gap-2">
                  <button
                    onClick={() => decide(b.id, true)}
                    disabled={decidingId === b.id}
                    className="rounded bg-success px-3 py-1.5 text-sm font-semibold text-paper hover:bg-success-dim disabled:opacity-60"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => decide(b.id, false)}
                    disabled={decidingId === b.id}
                    className="rounded border border-danger px-3 py-1.5 text-sm font-semibold text-danger hover:bg-danger/5 disabled:opacity-60"
                  >
                    Reject
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="rounded-lg border border-ink/10 bg-paper-raised p-6">
        <h2 className="font-display text-lg font-bold text-ink">Platform health</h2>
        <p className="mt-2 text-sm text-muted">
          Live service status, Kafka consumer lag, and Redis hit rate are best surfaced via
          Grafana rather than duplicated here — see the ops dashboard link once monitoring
          is deployed (devops/monitoring).
        </p>
      </div>
    </div>
  );
}
