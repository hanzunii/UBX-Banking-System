const SNIPPETS = {
  curl: {
    label: 'cURL',
    code: `curl -X POST http://localhost:8080/api/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"accountNumber":"10000001","pin":"1234"}'`,
  },
  java: {
    label: 'Java',
    code: `// POST /api/account/transfer
// Header: Authorization: Bearer <jwt>
{
  "toAccountNumber": "10000002",
  "amount": "50.00",
  "reference": "lunch money"
}`,
  },
}

export function CodeSection() {
  const current = SNIPPETS.curl

  return (
    <section id="api" className="px-5 sm:px-8 py-20">
      <div className="mx-auto max-w-4xl">
        <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-white">
          REST API
        </h2>
        <p className="mt-3 text-[var(--muted)] max-w-lg">
          All features are available through a simple REST API. The server runs on port 8080 with JWT bearer token auth.
        </p>

        <div className="mt-8 overflow-hidden rounded-lg border border-[var(--edge)] bg-[#111]">
          <div className="flex items-center gap-2 px-4 py-2.5 border-b border-[var(--edge)]">
            <span className="text-xs font-medium text-[var(--muted)]">Example: Login request</span>
          </div>
          <pre className="p-5 overflow-x-auto text-[13px] leading-relaxed font-mono text-[var(--text)]">
            <code>{current.code}</code>
          </pre>
        </div>

        <div className="mt-6 grid sm:grid-cols-3 gap-3">
          {[
            { t: 'JWT Auth', d: 'Bearer token sessions with configurable expiry.' },
            { t: 'Rate Limiting', d: '5 failed logins triggers a 15-minute lockout.' },
            { t: 'Atomic Transfers', d: 'Both sides of a transfer commit in one transaction.' },
          ].map((f) => (
            <div key={f.t} className="rounded-lg border border-[var(--edge)] bg-[var(--panel)] p-4">
              <h3 className="text-[13px] font-semibold text-white">{f.t}</h3>
              <p className="mt-1 text-[12px] text-[var(--muted)] leading-relaxed">{f.d}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
