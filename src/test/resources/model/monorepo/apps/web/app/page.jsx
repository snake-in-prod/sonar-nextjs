import { Button } from '@repo/ui/button';
import { Card } from '@repo/ui';
import { title } from 'lib/title';
const Chart = dynamic(() => import('../lib/chart'));

export default function Page() {
  return <Card><Button>{title}</Button><Chart /></Card>;
}
