/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  eslint: { ignoreDuringBuilds: true },
  experimental: {
    // lib zips are uploaded through a server action; the default 1 MB limit is too small
    serverActions: { bodySizeLimit: '6mb' },
  },
  async headers() {
    const noStore = { key: 'Cache-Control', value: 'no-store, no-cache, must-revalidate, max-age=0' };
    const cors = [
      { key: 'Access-Control-Allow-Origin', value: '*' },
      { key: 'Access-Control-Allow-Headers', value: 'Content-Type, Accept' },
      { key: 'Access-Control-Allow-Methods', value: 'GET, POST, OPTIONS' },
    ];
    return [
      // The loader hits these with libcurl from Android.
      { source: '/server', headers: [noStore, ...cors] },
      { source: '/api/server', headers: [noStore, ...cors] },
      { source: '/api/version', headers: [noStore, ...cors] },
      { source: '/api/download', headers: [noStore, ...cors] },
      { source: '/api/health', headers: [noStore] },
    ];
  },
};

export default nextConfig;
