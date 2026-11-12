'use client';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

// checks the session in the browser and sends visitors to the sign-in page
export default function Upload() {
  const router = useRouter();
  useEffect(() => {
    fetch('/api/me').then((res) => {
      if (res.status === 401) {
        router.replace('/login');
      }
    });
  }, [router]);
  return <input type="file" />;
}
