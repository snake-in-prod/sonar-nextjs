import Client from '../components/Client';
import { shared } from '../lib/shared';

export default async function Page() {
  const data = await fetch(process.env.API_URL + '/items');
  return <Client items={data} label={shared} />;
}
