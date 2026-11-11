import { redirect } from './router';

export function go(searchParams: URLSearchParams) {
  // compliant: not the Next.js redirect
  redirect(searchParams.get('next'));
}
