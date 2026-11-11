'use server';

export default async function Save(data: FormData) {
  return data.get('name');
}
