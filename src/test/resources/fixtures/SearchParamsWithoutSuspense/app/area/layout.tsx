import { Suspense, type ReactNode } from 'react';

export default function AreaLayout({ children }: { children: ReactNode }) {
  return <Suspense>{children}</Suspense>;
}
