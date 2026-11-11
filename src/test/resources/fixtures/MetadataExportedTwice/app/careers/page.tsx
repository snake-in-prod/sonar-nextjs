import type { Metadata } from 'next';

// compliant: the metadata object is local, only generateMetadata is exported
const metadata: Metadata = { title: 'Careers' };

export async function generateMetadata(): Promise<Metadata> {
  return { ...metadata, description: 'Open roles' };
}

export default function Page() {
  return <p>Careers</p>;
}
