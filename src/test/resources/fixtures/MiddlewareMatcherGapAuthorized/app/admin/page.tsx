import { redirect } from 'next/navigation';
import { auth } from '../../auth';

export default async function Admin() {
  const session = await auth();
  if (!session) redirect('/login');
  return <h1>Admin</h1>;
}
