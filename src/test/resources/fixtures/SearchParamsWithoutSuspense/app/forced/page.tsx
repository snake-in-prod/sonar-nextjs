import Sorter from '@/components/Sorter';

// compliant: rendered on every request
export const dynamic = 'force-dynamic';

export default function Forced() {
  return <Sorter />;
}
