import Counter from '@/components/Counter';
import { db } from '@/lib/db';

export default async function Home() {
  const users = await db.user.findMany();
  return <Counter start={users.length} />;
}
