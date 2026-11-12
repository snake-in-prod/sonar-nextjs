"use server";

import { updateCategory } from "@/lib/requests/fetcher";

// the session is checked two calls down, behind a call with type arguments
export async function renameCategory(id: number, name: string) {
  await updateCategory(id, name);
  await fetch(`/categories/${id}/log`, { method: "POST", body: name });
}

export async function removeCategory(id: number) { // Noncompliant
  await fetch(`/categories/${id}`, { method: "DELETE" });
}
