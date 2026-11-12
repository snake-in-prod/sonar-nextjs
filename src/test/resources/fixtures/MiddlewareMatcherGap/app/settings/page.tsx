import { db } from '../../lib/db';

export default async function Settings() {
  const user = await db.user.findFirst();
  return <h1>Settings of {user.email}</h1>;
}
