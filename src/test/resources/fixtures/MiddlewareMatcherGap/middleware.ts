export { auth as middleware } from './auth';

export const config = {
  matcher: ['/dashboard/:path*', '/api/projects/:path*'], // Noncompliant {{leaves out 3 routes that check nothing themselves (/admin/users/[id], /api/admin/stats, /settings)}}
};
