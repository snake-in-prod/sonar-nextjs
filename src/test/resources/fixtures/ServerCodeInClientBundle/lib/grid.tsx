import { ObjectId } from 'mongoose';

type Props = {
  getId: (item: { id: string }) => ObjectId;
};

export function Grid({ getId }: Props) {
  return <ul>{String(getId)}</ul>;
}
