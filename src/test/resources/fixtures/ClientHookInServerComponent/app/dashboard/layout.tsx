import { useRouter } from 'next/navigation';

export default function Layout({ children }: { children: React.ReactNode }) {
  const router = useRouter(); // Noncompliant {{useRouter}}
  return <div onClick={() => router.back()}>{children}</div>;
}
