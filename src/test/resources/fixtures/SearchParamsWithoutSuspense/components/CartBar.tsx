'use client';

import { useSearchParams } from 'next/navigation';

export default function CartBar() {
  const coupon = useSearchParams().get('coupon');
  return <p>{coupon}</p>;
}
