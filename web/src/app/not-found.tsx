import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="grid min-h-screen place-items-center p-6">
      <div className="text-center">
        <p className="text-6xl font-black text-brand-500">404</p>
        <h1 className="mt-2 text-xl font-bold text-slate-100">This page does not exist</h1>
        <p className="mt-1 text-sm text-slate-400">The link may be old, or the key was deleted.</p>
        <div className="mt-6 flex justify-center gap-2">
          <Link href="/dashboard" className="btn-primary">
            Dashboard
          </Link>
          <Link href="/keys" className="btn-ghost">
            Keys
          </Link>
        </div>
      </div>
    </main>
  );
}
