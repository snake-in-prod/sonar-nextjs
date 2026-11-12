import { loadContext } from "../session";

export async function authenticated<TResponse>(url: string, init: RequestInit = {}): Promise<TResponse> {
  const session = await loadContext();
  if (!session) {
    throw new Error("Not authenticated");
  }
  const response = await fetch(url, init);
  return (await response.json()) as TResponse;
}

export async function updateCategory(id: number, name: string) {
  return await authenticated<{ category: string }>(`/categories/${id}`, { method: "PATCH", body: name });
}

