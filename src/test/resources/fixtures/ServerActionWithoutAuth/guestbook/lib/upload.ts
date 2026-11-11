// neither is a sign-in: a signed upload URL and a React ref callback
export async function uploadUrl(client: any, cmd: any, ref: any) {
  ref.current();
  return getSignedUrl(client, cmd);
}

declare function getSignedUrl(client: any, cmd: any): Promise<string>;
