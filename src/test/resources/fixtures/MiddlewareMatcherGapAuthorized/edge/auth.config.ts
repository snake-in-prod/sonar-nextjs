const PROTECTED = ['/dashboard'];

export const authConfig = {
  providers: [],
  callbacks: {
    authorized: ({ auth, request }) => {
      const { pathname } = request.nextUrl;
      return PROTECTED.includes(pathname) ? !!auth : true;
    },
  },
};
