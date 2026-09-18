import Link from 'next/link';

interface Product {
  id: string;
  name: string;
  price: number;
  category: string;
  sku: string;
}

async function getProducts(): Promise<Product[]> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
  try {
    const res = await fetch(`${base}/api/v1/products/search`, { cache: 'no-store' });
    if (!res.ok) return [];
    return res.json();
  } catch {
    return [];
  }
}

export default async function ProductsPage() {
  const products = await getProducts();

  return (
    <div className="mx-auto max-w-6xl px-6 py-12">
      <h1 className="font-display text-3xl font-bold text-ink">Live catalog</h1>
      <p className="mt-2 text-muted">Stock levels shown here refresh in real time on each product page.</p>

      {products.length === 0 ? (
        <p className="mt-12 text-muted">
          No products to show yet — once the catalog is seeded, they&apos;ll appear here.
        </p>
      ) : (
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((p) => (
            <Link
              key={p.id}
              href={`/products/${p.id}`}
              className="group rounded-lg border border-ink/10 bg-paper-raised p-5 transition hover:border-signal"
            >
              <img
                src={`/images/products/${p.sku}.jpg`}
                alt={p.name}
                className="w-full h-48 object-cover rounded bg-paper mb-4"
              />
              <p className="text-xs uppercase tracking-wide text-muted">{p.category}</p>
              <h2 className="mt-1 font-display text-lg font-bold text-ink group-hover:text-signal-dim">
                {p.name}
              </h2>
              <p className="tabular-nums mt-3 font-mono text-xl font-bold text-ink">
                ${p.price.toFixed(2)}
              </p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
