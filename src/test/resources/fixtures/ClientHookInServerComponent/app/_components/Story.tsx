import { useEffect } from 'react';

export default function Story({ v }: { v: number }) {
  useEffect(() => {}, [v]);
  return <em>{v}</em>;
}
