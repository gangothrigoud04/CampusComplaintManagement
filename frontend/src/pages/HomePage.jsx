import { Link } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import ConnectionStatus from '../components/ConnectionStatus';

const portalCards = [
  {
    role: 'Student',
    title: 'Make campus better',
    description: 'Report a campus issue, attach a photo, and keep track of your own submissions.',
    icon: 'student',
    route: '/student/login',
    tone: 'student',
  },
  {
    role: 'Faculty',
    title: 'Faculty access',
    description: 'Sign in to access your verified faculty account and current role-specific services.',
    icon: 'graduation',
    route: '/faculty/login',
    tone: 'faculty',
  },
  {
    role: 'Staff',
    title: 'Support campus services',
    description: 'Open your staff workspace and review complaints assigned to your account.',
    icon: 'staff',
    route: '/staff/login',
    tone: 'staff',
  },
  {
    role: 'Admin',
    title: 'Campus operations',
    description: 'Review the complaint register, manage account provisioning, and set up departments.',
    icon: 'admin',
    route: '/admin/login',
    tone: 'admin',
  },
];

const workflow = [
  { title: 'Submit', detail: 'Student report and optional photo', live: true },
  { title: 'Review', detail: 'Record is saved for review', live: true },
  { title: 'Assign', detail: 'Assignment tools planned', live: false },
  { title: 'Resolve', detail: 'Resolution workflow planned', live: false },
  { title: 'Close', detail: 'Closure workflow planned', live: false },
];

function HomePage() {
  return (
    <div className="home-page">
      <section className="hero-wrap">
        <div className="hero-shell">
          <div className="hero-copy">
            <div className="hero-kicker"><span /> Campus, heard and cared for</div>
            <h1>Small issues deserve a <span>clear path forward.</span></h1>
            <p className="hero-description">
              A shared campus space to report facility concerns, follow your submissions, and help campus teams see what needs attention.
            </p>
            <div className="hero-actions">
              <Link className="button button-primary button-large" to="/student/register">
                Register as a student <AppIcon name="arrow" size={18} />
              </Link>
              <a className="button button-secondary button-large" href="#portals">Explore portals</a>
            </div>
            <div className="hero-trust">
              <span className="trust-check"><AppIcon name="check" size={14} /></span>
              Private, role-based access to campus reports
            </div>
          </div>
          <div className="hero-visual" aria-label="Complaint reporting workflow overview">
            <div className="visual-glow" />
            <div className="visual-card visual-card-back">
              <div className="visual-card-top"><span className="visual-dot" /> Campus services</div>
              <div className="visual-skeleton wide" /><div className="visual-skeleton" />
            </div>
            <div className="visual-card visual-card-front">
              <div className="report-icon"><AppIcon name="clipboard" size={22} /></div>
              <div className="report-caption">PRIVATE TO YOUR ACCOUNT</div>
              <div className="report-title">Complaint tracking</div>
              <div className="report-meta">Your reports in one secure place</div>
              <div className="report-divider" />
              <div className="report-status"><span /> Status shown when available</div>
            </div>
            <div className="visual-orbit orbit-one" />
            <div className="visual-orbit orbit-two" />
            <div className="visual-stamp"><AppIcon name="check" size={17} /> Tracked securely</div>
          </div>
        </div>
        <div className="hero-health"><ConnectionStatus /></div>
      </section>

      <section className="workflow-section">
        <div className="section-heading">
          <p className="eyebrow">A simple process</p>
          <h2>From concern to care</h2>
          <p>Every report has a place. Current capabilities are clearly marked.</p>
        </div>
        <div className="workflow-track">
          {workflow.map((step, index) => (
            <div className={`workflow-step${step.live ? ' workflow-live' : ''}`} key={step.title}>
              <div className="workflow-number">{step.live ? <AppIcon name="check" size={17} /> : `0${index + 1}`}</div>
              <div className="workflow-step-title">{step.title}</div>
              <div className="workflow-detail">{step.detail}</div>
              {index < workflow.length - 1 && <span className="workflow-connector"><AppIcon name="arrow" size={16} /></span>}
            </div>
          ))}
        </div>
      </section>

      <section className="portals-section" id="portals">
        <div className="section-heading section-heading-row">
          <div>
            <p className="eyebrow">One campus, connected</p>
            <h2>Choose your workspace</h2>
          </div>
          <p>Role-specific access keeps each workspace focused and protected.</p>
        </div>
        <div className="portal-grid">
          {portalCards.map((portal) => (
            <article className={`portal-card portal-${portal.tone}`} key={portal.role}>
              <div className="portal-card-head">
                <span className="portal-icon"><AppIcon name={portal.icon} size={23} /></span>
                <span className="portal-role">{portal.role}</span>
              </div>
              <h3>{portal.title}</h3>
              <p>{portal.description}</p>
              <Link to={portal.route} className="portal-link">Enter {portal.role.toLowerCase()} portal <AppIcon name="arrow" size={16} /></Link>
            </article>
          ))}
        </div>
      </section>

      <section className="home-cta">
        <div>
          <p className="eyebrow">Start with one report</p>
          <h2>Help make your campus work better.</h2>
          <p>Student accounts can submit a concern and see its current status.</p>
        </div>
        <Link className="button button-white button-large" to="/student/register">Create student account <AppIcon name="arrow" size={18} /></Link>
      </section>
    </div>
  );
}

export default HomePage;
