import { Link } from 'react-router-dom';

function PortalPage({ title, role, responsibilities, pageLabel }) {
  return (
    <div className="container py-5">
      <div className="card border-0 shadow-sm">
        <div className="card-body p-4 p-lg-5">
          <div className="d-flex justify-content-between align-items-center flex-wrap gap-3 mb-4">
            <div>
              <p className="text-uppercase text-primary fw-semibold mb-2">{pageLabel}</p>
              <h1 className="mb-0">{title}</h1>
            </div>
            <Link className="btn btn-outline-primary" to="/">
              Back to homepage
            </Link>
          </div>

          <div className="row g-4">
            <div className="col-lg-6">
              <div className="bg-light border rounded-4 p-4 h-100">
                <h2 className="h4 mb-3">Role overview</h2>
                <p className="mb-0">{role}</p>
              </div>
            </div>
            <div className="col-lg-6">
              <div className="bg-primary-subtle border rounded-4 p-4 h-100">
                <h2 className="h4 mb-3">Intended responsibilities</h2>
                <ul className="mb-0 ps-3">
                  {responsibilities.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>

          <div className="alert alert-warning mt-4 mb-0">
            This page includes only the features currently implemented for this role. Additional dashboards and workflows are planned for later phases.
          </div>
        </div>
      </div>
    </div>
  );
}

export default PortalPage;
