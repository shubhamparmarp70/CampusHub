import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
export type Vertical = 'FOOD' | 'DESK';
const Ctx = createContext<{ vertical: Vertical; setVertical(v: Vertical): void }>({ vertical: 'FOOD', setVertical() {} });
export const useService = () => useContext(Ctx);
export function ServiceProvider({ children }: { children: ReactNode }) {
  const [vertical, setV] = useState<Vertical>((localStorage.getItem('cd_vertical') as Vertical) || 'FOOD');
  useEffect(() => { document.documentElement.dataset.vertical = vertical; localStorage.setItem('cd_vertical', vertical); }, [vertical]);
  return <Ctx.Provider value={{ vertical, setVertical: setV }}>{children}</Ctx.Provider>;
}
