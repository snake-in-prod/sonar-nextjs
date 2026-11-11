"use server";

import { db } from "./db";

// a byte order mark before the directive
export async function signOut() {
  await db.query("delete");
}
