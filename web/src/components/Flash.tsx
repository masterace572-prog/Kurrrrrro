type Props = {
  m?: string;
  t?: string;
};

/** Renders ?m=<message>&t=success|danger|warning from any redirect. */
export default function Flash({ m, t }: Props) {
  if (!m) return null;
  const tone =
    t === 'danger'
      ? 'border-red-500/40 bg-red-500/10 text-red-200'
      : t === 'warning'
        ? 'border-amber-500/40 bg-amber-500/10 text-amber-200'
        : 'border-emerald-500/40 bg-emerald-500/10 text-emerald-200';
  return (
    <div className={`mb-5 rounded-xl border px-4 py-3 text-sm ${tone}`} role="status">
      {m}
    </div>
  );
}
