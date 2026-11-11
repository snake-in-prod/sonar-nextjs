import { useState } from 'react';
import { useOutside } from './useOutside';

// compliant: nobody imports this module, so its hook helper is not in the server graph either
export default function Dropdown() {
  const [open, setOpen] = useState(false);
  useOutside(() => setOpen(false));
  return <div>{open}</div>;
}
