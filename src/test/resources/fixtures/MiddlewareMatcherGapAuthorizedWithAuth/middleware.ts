import { NextResponse } from 'next/server';
import { withAuth } from 'next-auth/middleware';
import { protectedRoutes } from './routes';

const API = '/api';

export default withAuth({ // Noncompliant {{leave out 1 route that checks nothing themselves (/settings)}}
  callbacks: {
    authorized: ({ token, req }) => {
      const { pathname } = req.nextUrl;
      if (pathname === '/admin/login') return true;
      if (!token && pathname.startsWith('/account')) {
        const url: URL = new URL(`/login?next=${encodeURIComponent(pathname)}`, req.url);
        url.searchParams.set('from', 'mw');
        return NextResponse.redirect(url);
      }
      if (!token && pathname.startsWith(`${API}/dashboard`)) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
      }
      return protectedRoutes.some((route) => pathname.startsWith(route)) ? !!token : true;
    },
  },
});
