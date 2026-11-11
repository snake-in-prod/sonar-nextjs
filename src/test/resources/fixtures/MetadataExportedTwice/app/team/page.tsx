import type { Metadata } from 'next';

// compliant: generateStaticParams is not generateMetadata
export const metadata: Metadata = { title: 'Team' };

export function generateStaticParams() {
  return [];
}

export default function Page() {
  return <p>Team</p>;
}
