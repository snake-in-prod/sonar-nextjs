'use server';

import { revalidateTag } from 'next/cache';
import { addLine } from '@/lib/writer';

// compliant: a guest cart is public by design
export async function addItem(variantId: string) {
  await addLine(variantId);
  revalidateTag('cart');
}
