import { Nav } from './Nav'
import { Hero } from './Hero'
import { BentoSuite } from './BentoSuite'
import { CodeSection } from './CodeSection'
import { SecurityStrip } from './SecurityStrip'
import { Footer } from './Footer'

export default function Landing() {
  return (
    <div className="relative min-h-screen bg-[var(--bg)] text-[var(--text)]">
      <Nav />
      <main>
        <Hero />
        <BentoSuite />
        <CodeSection />
        <SecurityStrip />
      </main>
      <Footer />
    </div>
  )
}
