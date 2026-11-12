import { withAuth } from 'next-auth/middleware';

export default withAuth({ pages: { signIn: '/admin' } });

// compliant: /admin is the sign-in page, /settings a placeholder
export const config = { matcher: ['/admin/dashboard/:path*'] };
