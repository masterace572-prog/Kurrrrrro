type Props = {
  m?: string;
  t?: string;
};

/** Renders ?m=<message>&t=success|danger|warning from any redirect. */
export default function Flash({ m, t }: Props) {
  if (!m) return null;
  const tone =
    t === 'danger'
      ? 'border-red-200 bg-red-50 text-red-800'
      : t === 'warning'
        ? 'border-amber-200 bg-amber-50 text-amber-800'
        : 'border-emerald-200 bg-emerald-50 text-emerald-800';
  return (
    <div className={`mb-5 rounded-xl border px-4 py-3 text-sm ${tone}`} role="status">
      {m}
    </div>
  );
}
