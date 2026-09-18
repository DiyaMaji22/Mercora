'use client';

import { useEffect, useState } from 'react';

interface CheckoutTimerProps {
  /** ISO timestamp when the hold expires (order.holdExpiresAt from the API). */
  expiresAt: string;
  onExpire?: () => void;
}

/**
 * The signature UI element of this app: a ticker-style countdown that
 * mirrors the 5-minute Redis hold TTL on Inventory Service. It's built to
 * feel like a stock-ticker / auction clock - tabular numerals so digits
 * don't jitter, a pulsing dot while time remains, and a hard color flip to
 * danger red in the final 60 seconds so the urgency is unmistakable.
 */
export default function CheckoutTimer({ expiresAt, onExpire }: CheckoutTimerProps) {
  const [remainingMs, setRemainingMs] = useState<number>(() =>
    new Date(expiresAt).getTime() - Date.now()
  );

  useEffect(() => {
    const interval = setInterval(() => {
      const remaining = new Date(expiresAt).getTime() - Date.now();
      setRemainingMs(remaining);
      if (remaining <= 0) {
        clearInterval(interval);
        onExpire?.();
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);

  const clamped = Math.max(0, remainingMs);
  const totalSeconds = Math.floor(clamped / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const isUrgent = totalSeconds <= 60;
  const isExpired = clamped <= 0;

  return (
    <div
      className={`flex items-center gap-3 rounded-lg border px-4 py-3 transition-colors ${
        isExpired
          ? 'border-danger bg-danger/10'
          : isUrgent
          ? 'border-danger bg-danger/5'
          : 'border-signal/40 bg-ink text-paper'
      }`}
      role="timer"
      aria-live="polite"
    >
      <span
        className={`h-2.5 w-2.5 rounded-full ${
          isExpired ? 'bg-danger' : 'bg-signal ticker-dot'
        }`}
      />
      <div>
        <p className={`text-xs uppercase tracking-wide ${isUrgent ? 'text-danger' : 'text-muted'}`}>
          {isExpired ? 'Hold expired' : 'Checkout hold expires in'}
        </p>
        <p
          className={`tabular-nums font-mono text-2xl font-bold ${
            isExpired ? 'text-danger' : isUrgent ? 'text-danger' : 'text-signal'
          }`}
        >
          {isExpired ? '00:00' : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`}
        </p>
      </div>
    </div>
  );
}
