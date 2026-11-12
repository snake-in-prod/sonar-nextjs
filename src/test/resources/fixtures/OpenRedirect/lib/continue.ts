import { redirect } from 'next/navigation';

// compliant: not an endpoint nor a Server Action, the rule does not know this request
export async function finish(request: Request) {
  const { next } = await request.json();
  redirect(next);
}
