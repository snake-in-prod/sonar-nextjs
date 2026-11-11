import { auth } from '../auth';

// reads the session for the header only: guards nothing
export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const session = await auth();
  return <html><body><header>{session?.user?.name}</header>{children}</body></html>;
}
