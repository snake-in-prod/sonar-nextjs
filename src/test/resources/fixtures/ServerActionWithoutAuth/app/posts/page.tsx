import { db } from '@/lib/db';

export default function Page() {
  // an inline action: not an exported Server Action, left out
  async function quickAdd(formData: FormData) {
    'use server';
    await db.post.create({ data: { title: String(formData.get('title')) } });
  }
  return <form action={quickAdd}><button>Add</button></form>;
}
