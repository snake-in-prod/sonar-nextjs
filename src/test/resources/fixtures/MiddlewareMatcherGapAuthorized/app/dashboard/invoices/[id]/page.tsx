import { db } from '../../../../lib/db';

export default async function Page() {
  const rows = await db.user.findMany();
  return <pre>{JSON.stringify(rows)}</pre>;
}
