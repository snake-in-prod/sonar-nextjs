'use client';

import { secret } from '../lib/secret'; // Noncompliant {{server-only}}
import { label } from '../lib/label';

export default function Widget({ value }: { value: string }) {
  return <span>{label}{value}{secret}</span>;
}
