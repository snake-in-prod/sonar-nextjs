import { withAuth } from 'next-auth/middleware';

// compliant: a visitor without a session is turned away everywhere; /admin needs a role on top
export default withAuth({
  callbacks: {
    authorized: ({ token, req }) => (req.nextUrl.pathname.startsWith('/admin') ? token?.role === 'admin' : !!token),
  },
});
