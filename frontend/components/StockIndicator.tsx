'use client';

import { useEffect, useState } from 'react';

interface StockIndicatorProps {
  productId: string;
  initialStock: number;
}

/**
 * Subscribes to the SSE stream fronting `inventory-events` (see spec's
 * "Retailer Update" flow: Postgres write -> Kafka -> Redis resync -> SSE
 * push). The gateway is expected to expose
 * GET /api/v1/inventory/stream/{productId} as a Server-Sent Events endpoint
 * that relays InventoryUpdateEvent messages; this component just renders
 * whatever the stream reports, falling back to the page's initial value
 * until the first event arrives.
 */
export default function StockIndicator({ productId, initialStock }: StockIndicatorProps) {
  const [stock, setStock] = useState(initialStock);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const base = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
    const source = new EventSource(`${base}/api/v1/inventory/stream/${productId}`);

    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (typeof payload.newAvailableStock === 'number') {
          setStock(payload.newAvailableStock);
        }
      } catch {
        // ignore malformed frames
      }
    };

    return () => source.close();
  }, [productId]);

  const level = stock === 0 ? 'out' : stock <= 5 ? 'low' : 'in';

  const label = {
    out: 'Sold out',
    low: `Only ${stock} left`,
    in: `${stock} in stock`,
  }[level];

  const dotColor = { out: 'bg-danger', low: 'bg-signal', in: 'bg-success' }[level];
  const textColor = { out: 'text-danger', low: 'text-signal-dim', in: 'text-success' }[level];

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className={`h-2 w-2 rounded-full ${dotColor} ${connected ? 'ticker-dot' : ''}`} />
      <span className={`font-semibold ${textColor}`}>{label}</span>
      {!connected && <span className="text-xs text-muted">(reconnecting…)</span>}
    </div>
  );
}
