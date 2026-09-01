import type { Collection } from '../api/types';

const money = (v: number | string) => `Rs.${Number(v ?? 0).toFixed(2)}`;
const rate = (v: number | string) => `Rs.${Number(v ?? 0).toFixed(4)}`;
const qty = (v: number | string) => Number(v ?? 0).toFixed(2);

const PRINT_CSS = `
@media screen { #sasi-receipt { display: none; } }
@media print {
  body * { visibility: hidden !important; }
  #sasi-receipt, #sasi-receipt * { visibility: visible !important; }
  #sasi-receipt {
    position: absolute; left: 0; top: 0; width: 76mm;
    font-family: "Courier New", monospace; font-size: 12px; color: #000;
  }
  #sasi-receipt h1 { font-size: 14px; text-align: center; margin: 0; letter-spacing: 1px; }
  #sasi-receipt .sub { text-align: center; font-size: 11px; margin: 2px 0 6px; }
  #sasi-receipt .hr { border-top: 1px dashed #000; margin: 6px 0; }
  #sasi-receipt .row { display: flex; justify-content: space-between; gap: 8px; line-height: 1.6; }
  #sasi-receipt .row span:last-child { text-align: right; white-space: nowrap; }
  #sasi-receipt .total { font-weight: bold; font-size: 13px; }
  #sasi-receipt .foot { text-align: center; font-size: 10px; margin-top: 8px; }
  @page { margin: 4mm; }
}`;

function R({ k, v }: { k: string; v: string }) {
  return (
    <div className="row">
      <span>{k}</span>
      <span>{v}</span>
    </div>
  );
}

/** Hidden on screen; the only thing printed when the user hits print / window.print(). */
export function PrintableReceipt({
  collection,
  centerName,
  centerAddress,
}: {
  collection: Collection | null;
  centerName: string;
  centerAddress?: string;
}) {
  return (
    <>
      <style>{PRINT_CSS}</style>
      <div id="sasi-receipt" aria-hidden>
        {collection && (
          <>
            <h1>{centerName}</h1>
            {centerAddress ? <div className="sub">{centerAddress}</div> : null}
            <div className="sub">MILK COLLECTION RECEIPT</div>
            <div className="hr" />
            <R k="Collection No" v={collection.collectionNumber} />
            <R k="Date / Shift" v={`${collection.collectionDate}  ${collection.shift}`} />
            <R
              k="Producer"
              v={`${collection.producerNumber ?? ''} ${collection.producerName ?? '#' + collection.producerId}`.trim()}
            />
            <div className="hr" />
            <R k="Milk (litres)" v={qty(collection.litres)} />
            <R k="FAT %" v={qty(collection.fat)} />
            <R k="SNF %" v={qty(collection.snf)} />
            <R k="Rate / litre" v={rate(collection.breakdown.ratePerLitre)} />
            <div className="hr" />
            <R k="Milk amount" v={money(collection.breakdown.baseAmount)} />
            <R k="Incentive" v={money(collection.breakdown.incentiveAmount)} />
            <R k="Deduction" v={money(collection.breakdown.deductionAmount)} />
            <div className="hr" />
            <div className="row total">
              <span>NET AMOUNT</span>
              <span>{money(collection.breakdown.netAmount)}</span>
            </div>
            <div className="hr" />
            <div className="foot">
              Printed {new Date().toLocaleString('en-IN')}
              <br />
              Thank you
            </div>
          </>
        )}
      </div>
    </>
  );
}
