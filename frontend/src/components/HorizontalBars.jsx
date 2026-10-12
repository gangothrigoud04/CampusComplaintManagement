function HorizontalBars({ items, emptyMessage = 'No data is available yet.' }) {
  const max = Math.max(...items.map((item) => item.value), 0);
  if (!items.length) return <div className="chart-empty">{emptyMessage}</div>;

  return (
    <div className="horizontal-bars">
      {items.map((item) => (
        <div className="bar-row" key={item.label}>
          <div className="bar-caption"><span>{item.label}</span><strong>{item.value}</strong></div>
          <div className="bar-track"><span style={{ width: `${max ? Math.max((item.value / max) * 100, item.value ? 4 : 0) : 0}%` }} /></div>
        </div>
      ))}
    </div>
  );
}

export default HorizontalBars;
