'use client';

import { useRouter, useSearchParams } from 'next/navigation';

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const callbackUrl = searchParams.get('callbackUrl') || '/';
  const next = searchParams.get('next');

  async function onSubmit() {
    await fetch('/api/login', { method: 'POST' });
    router.push(callbackUrl); // Noncompliant
    router.replace(next ?? '/'); // Noncompliant
  }

  function onSso() {
    window.location.href = callbackUrl; // Noncompliant
  }

  function onRegister() {
    // compliant: a fixed path, the value only in the query string
    router.push(`/register?callbackUrl=${encodeURIComponent(callbackUrl)}`);
    window.location.href = `/sso?next=${encodeURIComponent(callbackUrl)}`;
  }

  return { onSubmit, onSso, onRegister };
}

export function SafeLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get('returnTo') ?? '/';

  function done() {
    // compliant: checked to be a local path
    router.push(returnTo.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/');
  }

  return done;
}
