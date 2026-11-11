import { cookies } from 'next/headers';
import Tabs from '@/components/Tabs';

// compliant: cookies() makes the page dynamic
export default async function Account() {
  const store = await cookies();
  return <Tabs theme={store.get('theme')?.value} />;
}
