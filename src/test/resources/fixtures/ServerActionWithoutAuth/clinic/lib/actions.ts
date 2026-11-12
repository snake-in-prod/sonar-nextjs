'use server';

import { revalidatePath } from 'next/cache';
import { databases } from './store';

// compliant: no sign-in in this app, booking is what visitors do
export async function createBooking(booking: unknown) {
  const created = await databases.createDocument('bookings', booking);
  revalidatePath('/admin');
  return created;
}

// the admin page changes existing bookings: without sign-in anyone can
export async function updateBooking(id: string, booking: unknown) { // Noncompliant
  const updated = await databases.updateDocument('bookings', id, booking);
  revalidatePath('/admin');
  return updated;
}

export const getUser = async (userId: string) => databases.getDocument('users', userId);
