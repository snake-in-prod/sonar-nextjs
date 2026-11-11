export const config = {
  a: process.env.NEXT_PUBLIC_API_SECRET, // Noncompliant {{NEXT_PUBLIC_API_SECRET is inlined into the browser bundle}}
  b: process.env.NEXT_PUBLIC_DB_PASSWORD, // Noncompliant
  c: process.env.NEXT_PUBLIC_FIREBASE_PRIVATE_KEY, // Noncompliant
  d: process.env.NEXT_PUBLIC_SUPABASE_SERVICE_ROLE_KEY, // Noncompliant
  e: process.env.NEXT_PUBLIC_POSTMARK_SERVER_TOKEN, // Noncompliant
  f: process.env['NEXT_PUBLIC_STRIPE_SECRET_KEY'], // Noncompliant
  g: process.env.NEXT_PUBLIC_GITHUB_CLIENT_SECRET, // Noncompliant
  h: process.env.NEXT_PUBLIC_AWS_SECRET_ACCESS_KEY, // Noncompliant

  // publishable, anon and public keys and ids are meant for the browser
  i: process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY,
  j: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
  k: process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY,
  l: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,
  m: process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY,
  n: process.env.NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN,
  // the secret word qualifies something else
  o: process.env.NEXT_PUBLIC_WEBHOOK_SECRET_HEADER,
  p: process.env.NEXT_PUBLIC_PASSWORD_MIN_LENGTH,
  q: process.env.NEXT_PUBLIC_PRIVATE_KEY_ID,
  s: process.env.NEXT_PUBLIC_DISABLE_EMAIL_PASSWORD_SIGNUP,
  // not public at all
  r: process.env.STRIPE_SECRET_KEY,
};
