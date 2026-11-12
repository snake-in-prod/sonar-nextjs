export const authConfig = {
  providers: [],
  callbacks: {
    authorized({ auth, request: { nextUrl } }) {
      // not followed: a regular expression decides
      if (/^\/(dashboard|settings)/.test(nextUrl.pathname)) return !!auth?.user;
      return true;
    },
  },
};
