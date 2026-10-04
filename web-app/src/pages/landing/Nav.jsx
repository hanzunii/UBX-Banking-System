import { useState } from 'react'
import { Link } from 'react-router-dom'

const NAV_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'Security', href: '#security' },
]

export function Nav() {
  const [open, setOpen] = useState(false)

  return (
    <header className="fixed top-0 inset-x-0 z-50 glass-nav">
      <nav className="mx-auto max-w-6xl px-5 h-14 flex items-center justify-between">
        <a href="#top" className="flex items-center gap-2 text-white font-semibold">
          <span className="grid place-items-center w-8 h-8 rounded-lg bg-[var(--primary)] text-white text-sm font-bold">
            U
          </span>
          <span className="text-[14px]">UBX</span>
        </a>

        <div className="hidden md:flex items-center gap-6 text-sm text-[var(--muted)]">
          {NAV_LINKS.map((l) => (
            <a key={l.label} href={l.href} className="hover:text-white transition-colors">
              {l.label}
            </a>
          ))}
        </div>

        <div className="hidden md:flex items-center gap-3">
          <Link to="/login" className="text-sm text-[var(--muted)] hover:text-white transition-colors px-3 py-2">
            Sign In
          </Link>
          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg bg-[var(--primary)] text-white text-sm font-semibold px-4 py-2"
          >
            Open App
          </Link>
        </div>

        <button
          className="md:hidden grid place-items-center w-9 h-9 rounded-lg border border-[var(--edge)] text-[var(--muted)]"
          onClick={() => setOpen((v) => !v)}
          aria-label="Toggle menu"
        >
          <svg viewBox="0 0 24 24" fill="none" className="w-5 h-5">
            {open ? (
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            ) : (
              <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            )}
          </svg>
        </button>
      </nav>

      {open && (
        <div className="md:hidden border-t border-[var(--edge)] bg-[#111] px-5 py-4 flex flex-col gap-1">
          {NAV_LINKS.map((l) => (
            <a
              key={l.label}
              href={l.href}
              onClick={() => setOpen(false)}
              className="py-2.5 text-sm text-[var(--muted)] border-b border-[var(--edge)] last:border-0"
            >
              {l.label}
            </a>
          ))}
          <Link
            to="/login"
            onClick={() => setOpen(false)}
            className="mt-3 inline-flex items-center justify-center gap-2 rounded-lg bg-[var(--primary)] text-white text-sm font-semibold px-4 py-2.5"
          >
            Open App
          </Link>
        </div>
      )}
    </header>
  )
}
