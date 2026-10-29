export default function PageHeader({
  title,
  subtitle,
  action,
  breadcrumb,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  breadcrumb?: { label: string; href?: string }[];
}) {
  return (
    <div className="mb-5 sm:mb-6">
      {breadcrumb?.length ? (
        <nav className="mb-2 flex items-center gap-1.5 text-xs text-slate-500" aria-label="Breadcrumb">
          {breadcrumb.map((b, i) => (
            <span key={`${b.label}-${i}`} className="flex items-center gap-1.5">
              {i > 0 ? <span className="text-slate-700">/</span> : null}
              {b.href ? (
                <a href={b.href} className="transition hover:text-slate-300">
                  {b.label}
                </a>
              ) : (
                <span className="text-slate-400">{b.label}</span>
              )}
            </span>
          ))}
        </nav>
      ) : null}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-lg font-bold leading-tight tracking-tight text-slate-100 sm:text-2xl">{title}</h1>
          {subtitle ? <p className="mt-1 text-xs text-slate-500 sm:text-sm">{subtitle}</p> : null}
        </div>
        {action}
      </div>
    </div>
  );
}
