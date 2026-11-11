import type { Metadata } from 'next';

export const metadata: Metadata = { title: 'Blog' };

export async function generateMetadata(): Promise<Metadata> { // Noncompliant {{Export either "metadata" or "generateMetadata"}}
  return { title: 'Blog' };
}

export default function Page() {
  return <h1>Blog</h1>;
}
