import { Link } from 'react-router-dom'

export function Hero() {
  return (
    <section id="top" className="relative pt-28 lg:pt-36 pb-16 px-5 sm:px-8">
      <div className="mx-auto max-w-4xl text-center">
        <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-tight">
          <span className="text-gradient">Straightforward money management,</span>
          <br />
          <span className="text-gradient-brand">built with Java and React.</span>
        </h1>

        <p className="mx-auto mt-6 max-w-xl text-[var(--muted)] leading-relaxed">
          A mobile payment system with a Spring Boot backend, JWT auth,
          and a clean web interface for transfers, deposits, and transaction history.
        </p>

        <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg bg-[var(--primary)] text-white font-semibold text-[14px] px-6 py-3"
          >
            Open App
          </Link>
          <a
            href="#features"
            className="inline-flex items-center gap-2 rounded-lg border border-[var(--edge)] bg-transparent px-6 py-3 text-[14px] font-semibold text-[var(--muted)] hover:text-white hover:border-[#555] transition-colors"
          >
            Learn more
          </a>
        </div>
      </div>
    </section>
  )
}
