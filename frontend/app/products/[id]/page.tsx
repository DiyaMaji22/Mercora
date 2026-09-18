import AddToCartButton from '@/components/AddToCartButton';
import StockIndicator from '@/components/StockIndicator';

interface ProductDetail {
  id: string;
  name: string;
  description: string;
  category: string;
  price: number;
  sku: string;
  retailerId: string;
}

async function getProduct(id: string): Promise<ProductDetail | null> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
  try {
    const res = await fetch(`${base}/api/v1/products/${id}`, { cache: 'no-store' });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export default async function ProductDetailPage({ params }: { params: { id: string } }) {
  const product = await getProduct(params.id);

  if (!product) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-24 text-center">
        <h1 className="font-display text-2xl font-bold text-ink">Product not found</h1>
        <p className="mt-2 text-muted">This listing may have been removed or is no longer active.</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-6 py-12">
      <img
        src={`/images/products/${product.sku}.jpg`}
        alt={product.name}
        className="w-full max-h-96 object-contain rounded-lg bg-paper-raised mb-8"
      />
      <p className="text-xs uppercase tracking-wide text-muted">{product.category}</p>
      <h1 className="mt-1 font-display text-3xl font-bold text-ink">{product.name}</h1>

      <div className="mt-4">
        <StockIndicator productId={product.id} initialStock={10} />
      </div>

      <p className="tabular-nums mt-6 font-mono text-4xl font-bold text-ink">
        ${product.price.toFixed(2)}
      </p>

      <p className="mt-2 text-sm text-muted">Seller ID: {product.retailerId}</p>

      <p className="mt-6 max-w-xl text-muted">{product.description}</p>

      <div className="mt-8">
        <AddToCartButton
          productId={product.id}
          name={product.name}
          price={product.price}
        />
      </div>
    </div>
  );
}
