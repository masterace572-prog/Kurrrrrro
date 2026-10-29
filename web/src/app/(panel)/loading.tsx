export default function PanelLoading() {
  return (
    <div className="animate-pulse space-y-5">
      <div className="h-7 w-56 rounded-lg bg-ink-800" />
      <div className="h-4 w-80 max-w-full rounded bg-ink-850" />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="card h-24" />
        ))}
      </div>
      <div className="card h-64" />
    </div>
  );
}
