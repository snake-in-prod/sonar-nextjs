import { Content } from './content';

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<{ page?: string }> };

export async function generateMetadata(props: Props) {
  const params = await props.params;
  return { title: `Post ${params.slug}` };
}

export async function generateStaticParams({ params: { lang } }: { params: { lang: string } }) {
  return [{ slug: lang }];
}

export default async function Page({ params, searchParams }: Props) {
  const { slug } = await params;
  const page = (await searchParams).page;
  const items = [{ params: 1 }].map((params) => params.params);
  params.then((p) => console.log(p.slug));
  const hint = `params.slug is ${slug}`;
  return <Content params={params} slug={slug} page={page} items={items} hint={hint} />;
}
