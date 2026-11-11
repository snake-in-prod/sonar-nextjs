import { redirect } from 'next/navigation';
import { auth } from '../../auth';

// guards every page under /account
export default async function AccountLayout({ children }: { children: React.ReactNode }) {
  const session = await auth();
  if (!session) {
    redirect('/login');
  }
  return <section>{children}</section>;
}
