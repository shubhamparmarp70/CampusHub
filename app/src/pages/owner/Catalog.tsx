import { useState } from 'react';
import { useMyShop } from '../../lib/hooks';
import { Card, Note, Spinner } from '../../components/ui';
import Products from './Products';
import Stock from './Stock';
import PrintPrices from './PrintPrices';
import ApprovalGate from '../../components/ApprovalGate';

export default function Catalog() {
  const shop = useMyShop(); const [tab, setTab] = useState('products');
  if (shop.isLoading) return <Spinner />;
  if (!shop.data) return <Card><Note tone="mute">Create your shop first on the Orders tab.</Note></Card>;
  const s = shop.data;
  if (s.approval !== 'APPROVED') return <><h1 className="text-xl font-bold">Catalog</h1><ApprovalGate shop={s} what="Adding products and setting prices" /></>;
  const tabs: [string, string][] = [['products', 'Products'], ['stock', 'Stock'], ...(s.vertical === 'DESK' ? [['prices', 'Printing prices'] as [string, string]] : [])];
  return (
    <>
      <h1 className="text-xl font-bold">Catalog</h1>
      <div className="flex gap-1 rounded-xl bg-surface p-1">
        {tabs.map(([k, l]) => <button key={k} onClick={() => setTab(k)} className={`flex-1 rounded-lg py-2 text-sm font-semibold ${tab === k ? 'bg-accent text-white' : 'text-mute'}`}>{l}</button>)}
      </div>
      {tab === 'products' && <Products shop={s} />}
      {tab === 'stock' && <Stock />}
      {tab === 'prices' && <PrintPrices />}
    </>
  );
}
