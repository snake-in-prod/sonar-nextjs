type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ sort?: string }> };

export default async function Page(props: Props) {
  const { id } = props.params; // Noncompliant {{"params"}}
  const sort = props.searchParams?.sort ?? 'asc'; // Noncompliant {{"searchParams"}}
  return <p>{id}{sort}</p>;
}
