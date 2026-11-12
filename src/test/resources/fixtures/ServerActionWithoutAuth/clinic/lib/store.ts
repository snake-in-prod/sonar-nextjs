export const databases = {
  async createDocument(collection: string, data: unknown) { return { collection, data }; },
  async updateDocument(collection: string, id: string, data: unknown) { return { collection, id, data }; },
  async getDocument(collection: string, id: string) { return { collection, id }; },
};
