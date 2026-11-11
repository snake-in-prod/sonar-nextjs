export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const next = searchParams.get('next');
  if (next) {
    return Response.redirect(next); // Noncompliant
  }
  const target = new URL(origin);
  target.pathname = searchParams.get('path') ?? '/';
  // compliant: the value only sets the path of a same-origin URL
  return Response.redirect(target);
}
