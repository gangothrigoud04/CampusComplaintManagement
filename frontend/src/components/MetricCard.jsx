import AppIcon from './AppIcon';

function MetricCard({ label, value, detail, icon, tone = 'blue' }) {
  return (
    <article className={`metric-card metric-${tone}`}>
      <div className="metric-copy">
        <span>{label}</span>
        <strong>{value}</strong>
        {detail && <small>{detail}</small>}
      </div>
      <span className="metric-icon"><AppIcon name={icon} size={21} /></span>
    </article>
  );
}

export default MetricCard;
