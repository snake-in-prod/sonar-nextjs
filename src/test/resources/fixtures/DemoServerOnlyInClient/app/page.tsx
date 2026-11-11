import Widget from './Widget';
import { secret } from '../lib/secret';

export default function Page() {
  return <Widget value={secret} />;
}
