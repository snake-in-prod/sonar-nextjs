import Stripe from 'stripe';

// a route handler keeps the value on the server, but the name invites client code to read it
const stripe = new Stripe(process.env.NEXT_PUBLIC_STRIPE_SECRET_KEY!); // Noncompliant {{is named for the browser, so any client code reading it ships it to every visitor}}

export async function POST() {
  return Response.json(await stripe.checkout.sessions.create({ mode: 'payment' }));
}
