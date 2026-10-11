import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="landing">
      <header class="land-header">
        <a class="land-brand" routerLink="/" aria-label="GENOMETRICS home">
          <span class="land-mark" aria-hidden="true"></span>
          GENOMETRICS
        </a>
        <nav class="land-nav" aria-label="Landing">
          <a href="#product">Product</a>
          <a href="#how">How it works</a>
          <a [routerLink]="enterLink">Enter</a>
        </nav>
      </header>

      <section class="hero" id="product">
        <div class="hero-copy">
          <h1>Meet Genometrics</h1>
          <p class="accent">Local-first analytics for FASTQ and VCF.</p>
          <p class="lead">Upload a sequencing file, catalogue the metrics, and inspect the result in one workspace.</p>
          <div class="actions">
            <a class="enter" [routerLink]="enterLink">Enter</a>
            <a class="learn" href="#how">Learn more</a>
          </div>
          <p class="choose">Choose your workspace</p>
          <div class="pills">
            <a routerLink="/overview">Panel</a>
            <a routerLink="/projects">Projects</a>
          </div>
        </div>

        <aside class="shot" aria-label="Sample quality output">
          <div class="shot-bar">
            <strong>GENOMETRICS</strong>
            <span>sample.fastq</span>
          </div>
          <div class="shot-body">
            <h2>Sequencing quality</h2>
            <p>Mean quality by read position</p>
            <div class="bars" aria-hidden="true">
              <span style="height: 46%"></span>
              <span style="height: 58%"></span>
              <span style="height: 70%"></span>
              <span style="height: 78%"></span>
              <span style="height: 86%"></span>
              <span style="height: 82%"></span>
              <span style="height: 74%"></span>
              <span style="height: 68%"></span>
              <span style="height: 64%"></span>
              <span style="height: 58%"></span>
              <span style="height: 52%"></span>
              <span class="late" style="height: 48%"></span>
              <span class="late" style="height: 44%"></span>
              <span class="late" style="height: 40%"></span>
              <span class="late" style="height: 36%"></span>
            </div>
            <dl class="kpis">
              <div><dt>Mean quality</dt><dd>32.4</dd></div>
              <div><dt>GC content</dt><dd>48%</dd></div>
              <div><dt>Reads</dt><dd>1,240</dd></div>
            </dl>
          </div>
        </aside>
      </section>

      <section class="stats" aria-label="Highlights">
        <div><strong>FASTQ + VCF</strong><span>One upload flow</span></div>
        <div><strong>Grounded</strong><span>Catalogued metrics</span></div>
        <div><strong>Local</strong><span>Optional Ollama analyst</span></div>
        <div><strong>Postgres</strong><span>Ready with Docker</span></div>
      </section>

      <section class="pillars" id="pillars">
        <h2>Data-intensive sequencing for the bench</h2>
        <p class="section-lead">A public front door, then the workspace you already use.</p>
        <div class="cards">
          <article>
            <h3>Accessible</h3>
            <p>Open the workspace from one Enter action. The same screens stay available after sign-in.</p>
          </article>
          <article>
            <h3>Reproducible</h3>
            <p>Each file becomes a job with stored metrics, so a result can be opened again later.</p>
          </article>
          <article>
            <h3>Transparent</h3>
            <p>Queries and comparisons show the numbers behind the chart, not only a summary.</p>
          </article>
        </div>
      </section>

      <section class="how" id="how">
        <h2>How Genometrics works</h2>
        <p class="section-lead">From a raw file to a result you can inspect.</p>
        <ol>
          <li><strong>Bring your data</strong><span>Choose a FASTQ or VCF and attach it to a sample.</span></li>
          <li><strong>Run the analysis</strong><span>The parser streams the file and stores quality metrics.</span></li>
          <li><strong>Read the panel</strong><span>Open named charts for the metrics, and compare a second sample on the same page.</span></li>
        </ol>
      </section>

      <footer>
        <div class="foot-cols">
          <div>
            <strong>Genometrics</strong>
            <span>by Bruno Omena</span>
          </div>
          <div>
            <strong>Workspace</strong>
            <a routerLink="/overview">Panel</a>
            <a routerLink="/projects">Projects</a>
          </div>
          <div>
            <strong>Enter</strong>
            <a [routerLink]="enterLink">Open the workspace</a>
            <span>dev&#64;dev.com</span>
          </div>
        </div>
        <p>© 2026 Genometrics. Layout inspired by the Galaxy Community Hub.</p>
      </footer>
    </div>
  `,
  styles: [`
    :host { display: block; color: #d5e8dc; }
    .landing {
      min-height: 100vh;
      background-color: #073b2a;
      background-image:
        linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
      background-size: 32px 32px;
    }
    .land-header, .hero, .stats, .pillars, .how, footer {
      width: min(1200px, calc(100% - 3rem));
      margin: 0 auto;
    }
    .land-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 1.4rem 0;
    }
    .land-brand {
      display: inline-flex;
      align-items: center;
      gap: 0.7rem;
      color: #fff;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-decoration: none;
    }
    .land-mark {
      width: 22px;
      height: 14px;
      background:
        linear-gradient(#c6f26d, #c6f26d) 0 2px / 100% 2px no-repeat,
        linear-gradient(#c6f26d, #c6f26d) 0 6px / 70% 2px no-repeat,
        linear-gradient(#c6f26d, #c6f26d) 0 10px / 40% 2px no-repeat;
    }
    .land-nav { display: flex; gap: 1.25rem; }
    .land-nav a { color: #d5e8dc; text-decoration: none; }
    .land-nav a:hover { color: #fff; }
    .hero {
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(280px, 520px);
      gap: 2.5rem;
      align-items: center;
      padding: 2.5rem 0 3.5rem;
    }
    h1, h2, h3 { font-family: var(--display); color: #fff; margin: 0; }
    h1 { font-size: clamp(2.4rem, 5vw, 3.4rem); line-height: 1.05; }
    .accent { margin: 0.8rem 0 0; color: #c6f26d; font-size: 1.35rem; }
    .lead { max-width: 38rem; color: #d5e8dc; font-size: 1.05rem; }
    .actions { display: flex; gap: 0.75rem; margin: 1.4rem 0 1.6rem; }
    .enter, .learn {
      display: inline-flex;
      align-items: center;
      min-height: 46px;
      padding: 0 1.1rem;
      border: 1px solid transparent;
      text-decoration: none;
      font-weight: 600;
    }
    .enter { background: #c6f26d; color: #073b2a; }
    .learn { border-color: #8fb5a0; color: #fff; }
    .choose {
      margin: 0 0 0.6rem;
      color: #8fb5a0;
      font-size: 0.75rem;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .pills { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .pills a {
      padding: 0.4rem 0.7rem;
      border: 1px solid rgba(143, 181, 160, 0.45);
      color: #fff;
      text-decoration: none;
    }
    .shot { background: #f4f7f5; color: #17231d; border: 1px solid #8fb5a0; }
    .shot-bar {
      display: flex;
      justify-content: space-between;
      padding: 0.75rem 1rem;
      background: #073b2a;
      color: #fff;
    }
    .shot-bar span { color: #d5e8dc; font-size: 0.85rem; }
    .shot-body { padding: 1rem; }
    .shot-body h2 { color: #17231d; font-size: 1.15rem; }
    .shot-body p { color: #637169; margin: 0.25rem 0 0.8rem; }
    .bars {
      display: flex;
      align-items: flex-end;
      gap: 6px;
      height: 120px;
      padding: 8px;
      background: #fff;
      border: 1px solid #d8e2dc;
    }
    .bars span { flex: 1; background: #177148; }
    .bars span.late { background: #c6f26d; }
    .kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; margin: 0.75rem 0 0; }
    .kpis div { background: #fff; border: 1px solid #d8e2dc; padding: 0.65rem; }
    .kpis dt { color: #637169; font-size: 0.75rem; }
    .kpis dd { margin: 0.15rem 0 0; font-weight: 700; font-size: 1.15rem; }
    .stats {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
      padding: 1.5rem 0 2rem;
      border-top: 1px solid rgba(143, 181, 160, 0.25);
    }
    .stats strong { display: block; color: #fff; font-size: 1.25rem; }
    .stats span { color: #8fb5a0; }
    .pillars, .how { padding: 2.5rem 0; }
    .section-lead { color: #d5e8dc; }
    .cards, ol {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1rem;
      margin: 1.25rem 0 0;
      padding: 0;
    }
    .cards article, ol li {
      padding: 1.1rem;
      background: #0e4a36;
      border: 1px solid rgba(143, 181, 160, 0.3);
    }
    .cards p, ol span { color: #d5e8dc; margin: 0.45rem 0 0; }
    ol { list-style: none; counter-reset: step; }
    ol li { display: grid; }
    ol strong::before {
      counter-increment: step;
      content: counter(step) ". ";
      color: #c6f26d;
    }
    footer { padding: 2rem 0 2.5rem; border-top: 1px solid rgba(143, 181, 160, 0.25); }
    .foot-cols { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
    .foot-cols strong, .foot-cols a, .foot-cols span { display: block; }
    .foot-cols a { color: #d5e8dc; text-decoration: none; margin-top: 0.35rem; }
    footer p { color: #8fb5a0; font-size: 0.85rem; }
    @media (max-width: 900px) {
      .hero, .stats, .cards, ol, .foot-cols { grid-template-columns: 1fr; }
      .land-header { align-items: flex-start; flex-direction: column; }
    }
  `]
})
export class LandingComponent {
  readonly enterLink = environment.authDisabled ? '/overview' : '/login';
}
