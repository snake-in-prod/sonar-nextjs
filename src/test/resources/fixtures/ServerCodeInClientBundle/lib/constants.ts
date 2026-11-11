// constants shared by client and server: tree shaking drops the exports a client does not import
export const STOREFRONT_KEY = process.env.STOREFRONT_SECRET_KEY || '';
export const DEFAULT_OPTION = 'Default';

export const apiClient = {
  get: async (path: string) => fetch(`${process.env.API_URL}${path}?key=${process.env.API_KEY}`),
};

export function signed(value: string) {
  return value + process.env.SIGNING_SECRET;
}

// private helpers: kept only when an imported export uses them
function hmacSecret() {
  return process.env.HMAC_SECRET;
}

function relaySecret() {
  return process.env.RELAY_SECRET;
}

export function signedHeader(value: string) {
  return value + hmacSecret();
}

export function relay(value: string) {
  return value + relaySecret();
}

// any endpoint, not a secret
export const gateway = process.env.GATEWAY_URI;
export const mongoUrl = process.env.MONGODB_URI;

export function imageUrl(path: string) {
  return `/images/${path}`;
}
