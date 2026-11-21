import { useQuery } from '@tanstack/react-query';
import { get } from './api';
export const useMyShop = () => useQuery({ queryKey: ['my-shop'], queryFn: () => get<any>('/shop-owner/shop'), retry: false,
  refetchInterval: (q) => (q.state.data?.approval === 'APPROVED' ? false : 15000) });   // keep checking until the admin approves, then unlock by itself
/** The API stores times in UTC without a zone. datetime-local inputs are local time. */
export const localToUtc = (local: string) => new Date(local).toISOString().slice(0, 19);
export const utcToLocalInput = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

import { useEffect, useState } from 'react';
export function useDebounced<T>(value: T, ms = 400): T {
  const [v, setV] = useState(value);
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t); }, [value, ms]);
  return v;
}
