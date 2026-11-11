'use client';

export default async function Page() { // Noncompliant {{Remove "async" from this client component}}
  const res = await fetch('/api/me');
  return <h1>{(await res.json()).name}</h1>;
}
