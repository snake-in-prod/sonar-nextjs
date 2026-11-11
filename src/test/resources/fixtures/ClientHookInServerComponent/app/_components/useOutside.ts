import { useEffect } from 'react';

export function useOutside(handler: () => void) {
  useEffect(() => {
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [handler]);
}
