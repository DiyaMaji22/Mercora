import Link from 'next/link';

export default function HomePage() {
  return (
    <div>
      <section className="border-b border-ink/10 bg-ink text-paper">
        <div className="mx-auto max-w-6xl px-6 py-24">
          <p className="mb-4 font-mono text-sm uppercase tracking-widest text-signal">
            Live inventory · Real-time holds
          </p>
          <h1 className="max-w-3xl font-display text-5xl font-bold leading-[1.05] tracking-tightest md:text-7xl">
            Stock moves fast.
            <br />
            <span className="text-signal">So do we.</span>
          </h1>
          <p className="mt-6 max-w-xl text-lg text-paper/70">
            Every item you see is checked against live inventory the instant you look at it.
            When you add to cart, we hold your stock for five minutes flat — no oversells,
            no surprises at checkout.
          </p>
          <div className="mt-10 flex gap-4">
            <Link
              href="/products"
              className="rounded bg-signal px-6 py-3 font-semibold text-ink transition hover:bg-signal-dim"
            >
              Browse live stock
            </Link>
            <Link
              href="/register"
              className="rounded border border-paper/30 px-6 py-3 font-semibold text-paper transition hover:border-signal hover:text-signal"
            >
              Create an account
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 py-16">
        <h2 className="font-display text-2xl font-bold text-ink">How a hold works</h2>
        <div className="mt-8 grid gap-6 md:grid-cols-3">
          <HowItWorksCard
            step="Add to cart"
            body="Nothing is reserved yet — browsing and cart-building never touches live stock."
          />
          <HowItWorksCard
            step="Check out"
            body="We atomically deduct stock and start a 5-minute hold the instant you place the order."
          />
          <HowItWorksCard
            step="Confirm or release"
            body="Complete payment within the window and it's yours. Miss it, and stock returns to the floor automatically."
          />
        </div>
      </section>
    </div>
  );
}

function HowItWorksCard({ step, body }: { step: string; body: string }) {
  return (
    <div className="rounded-lg border border-ink/10 bg-paper-raised p-6">
      <h3 className="font-display text-lg font-bold text-ink">{step}</h3>
      <p className="mt-2 text-sm text-muted">{body}</p>
    </div>
  );
}
