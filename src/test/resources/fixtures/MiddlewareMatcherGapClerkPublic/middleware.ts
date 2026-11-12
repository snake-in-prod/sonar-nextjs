import { clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';

// compliant: the route matcher lists the public routes, every other route is protected
const isPublicRoute = createRouteMatcher(['/', '/dashboard/demo(.*)', '/sign-in(.*)']);

export default clerkMiddleware(async (auth, req) => {
  if (!isPublicRoute(req)) {
    await auth.protect();
  }
});

export const config = {
  matcher: ['/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ico)).*)', '/(api|trpc)(.*)'],
};
