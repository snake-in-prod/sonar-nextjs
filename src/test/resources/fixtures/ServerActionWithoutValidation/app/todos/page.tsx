import { db } from '@/lib/db';

export default function Page() {
  async function addTodo(formData: FormData) {
    'use server';
    await db.todo.create({ data: { title: formData.get('title') as string } }); // Noncompliant
  }
  return <form action={addTodo}><input name="title" /></form>;
}
