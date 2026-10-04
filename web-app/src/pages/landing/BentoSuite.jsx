import { Link } from 'react-router-dom'

export function BentoSuite() {
  return (
    <section id="features" className="px-5 sm:px-8 py-20">
      <div className="mx-auto max-w-5xl">
        <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-white">
          What it does
        </h2>
        <p className="mt-3 text-[var(--muted)] max-w-lg">
          A working demo of core banking features — login, transfers, deposits, and an admin panel for approving accounts.
        </p>

        <div className="mt-10 grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[
            { title: 'User Login & Registration', desc: 'PIN-based auth with bcrypt hashing and JWT sessions. Rate limiting on failed attempts.' },
            { title: 'Transfers & Deposits', desc: 'Send money between accounts with atomic transactions and full history tracking.' },
            { title: 'QR Code Receiving', desc: 'Generate a QR code with your account number so others can easily send you money.' },
            { title: 'Transaction History', desc: 'Scroll through paginated history with real-time balance updates via WebSocket.' },
            { title: 'Admin Panel', desc: 'Approve pending registrations and credit accounts with deposited cash amounts.' },
            { title: 'H2 Embedded Database', desc: 'Zero setup — the server creates the database on first run and seeds demo accounts.' },
          ].map((f) => (
            <div key={f.title} className="rounded-lg border border-[var(--edge)] bg-[var(--panel)] p-5">
              <h3 className="text-[14px] font-semibold text-white">{f.title}</h3>
              <p className="mt-2 text-[13px] text-[var(--muted)] leading-relaxed">{f.desc}</p>
            </div>
          ))}
        </div>

        <div className="mt-12 flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg bg-[var(--primary)] text-white font-semibold text-[14px] px-6 py-3"
          >
            Try it out
          </Link>
          <Link
            to="/register"
            className="inline-flex items-center gap-2 rounded-lg border border-[var(--edge)] px-6 py-3 text-[14px] font-semibold text-[var(--muted)] hover:text-white hover:border-[#555] transition-colors"
          >
            Create an account
          </Link>
        </div>
      </div>
    </section>
  )
}
