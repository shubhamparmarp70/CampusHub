import { Card, Note } from './ui';

/** Shown to an owner until the platform verifies the shop. The page re-checks every 15 seconds, so it unlocks by itself after approval. */
export default function ApprovalGate({ shop, what }: { shop: any; what: string }) {
  if (shop.approval === 'DISABLED') return <Card className="grid gap-2"><h2 className="font-bold">Your shop is disabled</h2><Note>The platform team has disabled this shop, so {what.toLowerCase()} is switched off. Please contact support.</Note></Card>;
  const rows: [string, boolean][] = [['Account created', true], ['Mobile number verified', true], ['Platform team verifies your shop', false], [what, false]];
  return (
    <Card className="grid gap-3">
      <h2 className="font-bold">Waiting for verification</h2>
      <ul className="grid gap-2 text-sm">{rows.map(([label, done]) => (
        <li key={label} className="flex items-center gap-2"><span className={done ? 'text-ok' : 'text-mute'}>{done ? '✓' : '○'}</span><span className={done ? '' : 'text-mute'}>{label}</span></li>))}</ul>
      <p className="text-sm text-mute">{what} unlocks by itself the moment the platform team approves your shop. This page checks every 15 seconds and you'll get a notification, so there is nothing else to do.</p>
    </Card>
  );
}
