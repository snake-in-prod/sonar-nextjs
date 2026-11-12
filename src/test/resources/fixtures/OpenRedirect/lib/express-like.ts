// compliant: not a Next.js API route nor typed as one, the rule does not know this res
export function finish(req: any, res: any) {
  res.redirect(req.query.callbackUrl);
}
