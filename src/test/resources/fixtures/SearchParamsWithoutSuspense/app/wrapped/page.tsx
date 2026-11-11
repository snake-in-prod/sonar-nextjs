import { Suspense } from 'react';
import SearchBox from '@/components/SearchBox';

// compliant: a Suspense boundary around the component
export default function Wrapped() {
  return (
    <Suspense fallback={null}>
      <SearchBox />
    </Suspense>
  );
}
