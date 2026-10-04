export function Footer() {
  return (
    <footer className="border-t border-[var(--edge)] px-5 sm:px-8 pt-10 pb-6">
      <div className="mx-auto max-w-5xl">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-[13px] text-[var(--faint)]">
            Unified Banking Experience (UBX) — Spring Boot + React demo
          </p>
          <div className="flex items-center gap-4 text-[12px] text-[var(--faint)]">
            <span>Java 17</span>
            <span>React 18</span>
            <span>H2 Database</span>
          </div>
        </div>
      </div>
    </footer>
  )
}
