'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import apiClient from '@/lib/apiClient';
import { useCartStore } from '@/stores/cartStore';
import CheckoutTimer from '@/components/CheckoutTimer';

interface LineItem {
  productId: string;
  quantity: number;
  unitPrice: number;
}

interface PlaceOrderResponse {
  orderId: string;
  status: string;
  totalAmount: number;
  items: LineItem[];
  holdExpiresAt: string;
}

export default function CheckoutPage() {
  const router = useRouter();
  const clearCart = useCartStore((s) => s.clear);
  const [order, setOrder] = useState<PlaceOrderResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .post<PlaceOrderResponse>('/api/v1/orders')
      .then((res) => {
        if (!cancelled) {
          setOrder(res.data);
          clearCart(); // server now holds the authoritative pending order
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(
            e.response?.data?.message ||
              'Some items in your cart just sold out. Head back to your cart to review.'
          );
        }
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [clearCart]);

  const handleCancel = async () => {
    if (!order) return;
    try {
      await apiClient.post(`/api/v1/orders/${order.orderId}/cancel`);
    } finally {
      router.push('/cart');
    }
  };

  if (loading) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24 text-center text-muted">
        Placing your hold…
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24 text-center">
        <h1 className="font-display text-2xl font-bold text-danger">Checkout couldn&apos;t start</h1>
        <p className="mt-3 text-muted">{error}</p>
        <button
          onClick={() => router.push('/cart')}
          className="mt-6 rounded bg-ink px-6 py-3 font-semibold text-paper hover:bg-signal hover:text-ink"
        >
          Back to cart
        </button>
      </div>
    );
  }

  if (!order) return null;

  return (
    <div className="mx-auto max-w-xl px-6 py-12">
      <h1 className="font-display text-3xl font-bold text-ink">Complete your order</h1>

      <div className="mt-6">
        <CheckoutTimer expiresAt={order.holdExpiresAt} onExpire={() => setExpired(true)} />
      </div>

      {expired ? (
        <div className="mt-6 rounded-lg border border-danger bg-danger/5 p-5">
          <p className="font-semibold text-danger">Your hold expired</p>
          <p className="mt-1 text-sm text-muted">
            Stock has been released back to the floor. You&apos;ll need to check out again.
          </p>
          <button
            onClick={() => router.push('/products')}
            className="mt-4 rounded bg-ink px-5 py-2.5 font-semibold text-paper hover:bg-signal hover:text-ink"
          >
            Browse products
          </button>
        </div>
      ) : (
        <>
          <div className="mt-6 divide-y divide-ink/10 rounded-lg border border-ink/10 bg-paper-raised">
            {order.items.map((item) => (
              <div key={item.productId} className="flex justify-between p-4 text-sm">
                <span className="text-ink">Qty {item.quantity}</span>
                <span className="tabular-nums font-mono text-ink">
                  ${(item.unitPrice * item.quantity).toFixed(2)}
                </span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex justify-between border-t border-ink/10 pt-4">
            <span className="font-display font-bold text-ink">Total</span>
            <span className="tabular-nums font-mono text-xl font-bold text-ink">
              ${order.totalAmount.toFixed(2)}
            </span>
          </div>

          {/* A real payment form (card element, etc.) would go here; this
              simplified flow assumes Payment Service confirms out-of-band
              via its webhook once a provider payment succeeds. */}
          <div className="mt-8 flex gap-4">
            <button
              onClick={handleCancel}
              className="flex-1 rounded border border-ink/20 px-6 py-3 font-semibold text-ink hover:border-danger hover:text-danger"
            >
              Cancel hold
            </button>
            <button
              disabled
              className="flex-1 rounded bg-signal px-6 py-3 font-semibold text-ink opacity-70"
              title="Payment gateway integration is a follow-up; see payment-service webhook"
            >
              Awaiting payment…
            </button>
          </div>
        </>
      )}
    </div>
  );
}
