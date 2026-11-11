'use client';

import { useEffect, useState } from 'react';
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

export function StateLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [target, setTarget] = useState('/');
  const [home, setHome] = useState('/');
  useEffect(() => {
    setTarget(searchParams.get('next') ?? '/');
    setHome('/home');
  }, [searchParams]);
  return () => {
    router.push(target); // Noncompliant
    // compliant: only fixed paths are set
    router.push(home);
  };
}

export function HintedLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const callbackUrl = searchParams.get('callbackUrl') || '/';
  const onDone = () => router.push(callbackUrl); // Noncompliant
  // the comparison after the redirect only picks a hint, it guards nothing
  return { onDone, hint: callbackUrl !== '/' && callbackUrl };
}
