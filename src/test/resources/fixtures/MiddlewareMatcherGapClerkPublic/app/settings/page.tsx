import { db } from '../../lib/db';

export default async function Dashboard() {
  const users = await db.user.findMany();
  return <pre>{JSON.stringify(users)}</pre>;
}
