export function SecurityStrip() {
  return (
    <section id="security" className="px-5 sm:px-8 py-20">
      <div className="mx-auto max-w-5xl">
        <div className="rounded-lg border border-[var(--edge)] bg-[var(--panel)] p-8 sm:p-10">
          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-white">
            Security
          </h2>
          <p className="mt-3 text-[var(--muted)] max-w-lg">
            PINs are stored as bcrypt hashes, never plaintext. Sessions use JWT with
            configurable expiry. Login is rate-limited.
          </p>

          <div className="mt-8 grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              { v: 'bcrypt', k: 'PIN hashing' },
              { v: 'JWT', k: 'Session tokens' },
              { v: 'Rate limit', k: '5 attempts / 15 min' },
              { v: 'Atomic', k: 'DB transactions' },
            ].map((s) => (
              <div key={s.k} className="border-t border-[var(--edge)] pt-4">
                <p className="text-lg font-bold text-white">{s.v}</p>
                <p className="mt-1 text-[13px] text-[var(--muted)]">{s.k}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
