// compliant: a route handler, not a page or layout
export const metadata = { title: 'Feed' };

export function generateMetadata() {
  return metadata;
}

export async function GET() {
  return Response.json([]);
}
