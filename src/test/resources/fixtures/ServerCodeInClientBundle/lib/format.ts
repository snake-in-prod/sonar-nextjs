// two hops away from the client: tree shaking may drop sign() and stripe, so neither counts here
import { sign } from './sign';
import { stripe } from './stripe';

export const format = (v: string) => v.trim();
export const signed = (v: string) => sign(v) + stripe.id;
