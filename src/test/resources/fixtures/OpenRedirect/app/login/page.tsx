import { redirect, permanentRedirect } from 'next/navigation';
import { auth } from '@/lib/auth';
import { safeTarget } from '@/lib/targets';

export default async function LoginPage(props: { searchParams: Promise<{ next?: string; old?: string; to?: string }> }) {
  const session = await auth();
  const { next } = await props.searchParams;
  if (session) {
    redirect(next ?? '/'); // Noncompliant {{Check this redirect target}}
  }
  if ((await props.searchParams).old) {
    permanentRedirect((await props.searchParams).old as string); // Noncompliant
  }
  // compliant: the value goes through a helper the rule cannot see
  redirect(safeTarget(await props.searchParams));
}

export async function Elsewhere({ searchParams }: { searchParams: Promise<{ to?: string }> }) {
  const { to } = await searchParams;
  // compliant: a fixed path, the value only in the query string
  redirect(`/login?next=${encodeURIComponent(to ?? '')}`);
}

export async function Checked({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const sp = await searchParams;
  const next = sp.next;
  // compliant: checked to be a local path
  if (next && next.startsWith('/') && !next.startsWith('//')) {
    redirect(next);
  }
  // compliant: a ternary picks the target
  redirect(next === '/home' ? next : '/');
}
