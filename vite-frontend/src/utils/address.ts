export const splitAddresses = (value?: string | null): string[] =>
  Array.from(new Set(
    (value ?? '').split(/[\r\n,]+/).map(address => address.trim()).filter(Boolean)
  ));

export const formatAddressPort = (address: string, port: number): string =>
  address.includes(':') && !address.startsWith('[')
    ? `[${address}]:${port}`
    : `${address}:${port}`;
