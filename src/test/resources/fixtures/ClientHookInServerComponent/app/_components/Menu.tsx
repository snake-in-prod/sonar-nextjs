import { useEffect } from 'react';

export default function Menu({ open, onToggle }: { open: boolean; onToggle: () => void }) {
  useEffect(() => {}, [open]);
  return <nav onClick={onToggle} />;
}
