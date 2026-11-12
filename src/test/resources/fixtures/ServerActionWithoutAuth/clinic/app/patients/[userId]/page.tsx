import { getUser } from '@/clinic/lib/actions';

// a record lookup, not a sign-in
export default async function Patient({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return getUser(userId);
}
