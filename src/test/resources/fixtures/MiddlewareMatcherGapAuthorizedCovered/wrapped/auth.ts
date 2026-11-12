import NextAuth from 'next-auth';

export const { auth } = NextAuth({
  providers: [],
  callbacks: {
    authorized({ auth, request }) {
      if (request.nextUrl.pathname.startsWith('/dashboard')) return !!auth;
      return true;
    },
  },
});
