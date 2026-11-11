import dynamic from 'next/dynamic';

// compliant: next/dynamic loads the component behind its own boundary
const LazyMap = dynamic(() => import('@/components/LazyMap'));

export default function Lazy() {
  return <LazyMap />;
}
