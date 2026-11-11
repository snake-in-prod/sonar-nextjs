import Router from 'next/router';

export async function signIn() {
  await fetch('/api/login', { method: 'POST' });
  const returnUrl = Router.query.returnUrl || '/';
  Router.push(returnUrl as string); // Noncompliant
}
