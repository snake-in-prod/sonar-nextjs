import { useState } from 'react';
import Counter from './_components/Counter';
import Toggle from './_components/Toggle';
import Bom from './_components/Bom';

export default function Page() {
  const [open, setOpen] = useState(false); // Noncompliant {{Add "use client" to this module or move useState}}
  const [n] = useState<number>(0);
  return <main><Counter /><Toggle /><Bom /></main>;
}
