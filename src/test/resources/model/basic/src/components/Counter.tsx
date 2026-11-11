'use client';

import { useState } from 'react';
import type { User } from '@/lib/types';
import { format } from './format';
import { save } from '@/actions/save';

export default function Counter({ start }: { start: number }) {
  const [n, setN] = useState(start);
  const flag = process.env.NEXT_PUBLIC_FLAG;
  return <button onClick={() => save(n)}>{format(n)}</button>;
}
