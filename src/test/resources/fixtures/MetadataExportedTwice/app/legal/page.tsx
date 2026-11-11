import type { Metadata } from 'next';
import { generateMetadata as seo } from '@/lib/seo';

// compliant: generateMetadata is imported and called, not exported
export type { Metadata };

export const metadata: Metadata = seo();

// export async function generateMetadata() {}

export default function Page() {
  return <p>Legal</p>;
}
