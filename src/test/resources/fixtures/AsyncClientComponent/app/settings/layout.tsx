'use client';

export default async function layout({ children }: { children: React.ReactNode }) { // Noncompliant
  return <section>{children}</section>;
}
