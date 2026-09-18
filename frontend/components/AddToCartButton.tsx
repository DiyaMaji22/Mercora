'use client';

import { useState } from 'react';
import { useCartStore } from '@/stores/cartStore';

interface AddToCartButtonProps {
  productId: string;
  name: string;
  price: number;
}

export default function AddToCartButton({ productId, name, price }: AddToCartButtonProps) {
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const addItem = useCartStore((s) => s.addItem);

  const handleAdd = () => {
    addItem({ productId, name, price, quantity });
    setAdded(true);
    setTimeout(() => setAdded(false), 1500);
  };

  return (
    <div className="flex items-center gap-4">
      <div className="flex items-center rounded border border-ink/20">
        <button
          onClick={() => setQuantity((q) => Math.max(1, q - 1))}
          className="px-3 py-2 text-ink hover:bg-ink/5"
          aria-label="Decrease quantity"
        >
          −
        </button>
        <span className="tabular-nums w-10 text-center font-mono">{quantity}</span>
        <button
          onClick={() => setQuantity((q) => q + 1)}
          className="px-3 py-2 text-ink hover:bg-ink/5"
          aria-label="Increase quantity"
        >
          +
        </button>
      </div>

      <button
        onClick={handleAdd}
        className="rounded bg-ink px-6 py-3 font-semibold text-paper transition hover:bg-signal hover:text-ink"
      >
        {added ? 'Added ✓' : 'Add to cart'}
      </button>
    </div>
  );
}
