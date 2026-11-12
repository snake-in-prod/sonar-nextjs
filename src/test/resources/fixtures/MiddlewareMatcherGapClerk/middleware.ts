import { clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';

const isProtectedRoute = createRouteMatcher(['/dashboard(.*)']); // Noncompliant {{leaves out 1 route that checks nothing themselves (/settings)}}
const isAuthRoute = createRouteMatcher(['/sign-in(.*)']);

export default clerkMiddleware(async (auth, req) => {
  if (isAuthRoute(req)) {
    return;
  }
  if (isProtectedRoute(req)) await auth.protect();
});

export const config = {
  matcher: ['/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ico)).*)', '/(api|trpc)(.*)'],
};
