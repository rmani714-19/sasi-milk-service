const money = (v: number | string) => `Rs.${Number(v ?? 0).toFixed(2)}`;

const PRINT_CSS = `
@media screen { #sasi-payment-receipt { display: none; } }
@media print {
  body * { visibility: hidden !important; }
  #sasi-payment-receipt, #sasi-payment-receipt * { visibility: visible !important; }
  #sasi-payment-receipt {
    position: absolute; left: 0; top: 0; width: 80mm;
    font-family: "Courier New", monospace; font-size: 12px; color: #000;
  }
  #sasi-payment-receipt h1 { font-size: 14px; text-align: center; margin: 0; letter-spacing: 1px; }
  #sasi-payment-receipt .sub { text-align: center; font-size: 11px; margin: 2px 0 6px; }
  #sasi-payment-receipt .hr { border-top: 1px dashed #000; margin: 6px 0; }
  #sasi-payment-receipt .row { display: flex; justify-content: space-between; gap: 8px; line-height: 1.6; }
  #sasi-payment-receipt .row span:last-child { text-align: right; white-space: nowrap; }
  #sasi-payment-receipt .total { font-weight: bold; font-size: 13px; }
  #sasi-payment-receipt .foot { text-align: center; font-size: 10px; margin-top: 8px; }
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

export interface PaymentReceiptData {
  kindLabel: string; // "Farmer Settlement Payout" | "Driver Salary" | "Business Expense"
  reference: string; // settlement/batch/expense number
  recipient: string;
  period: string;
  amount: number;
  method: string;
  txnRef: string;
  status: string;
  paidAt?: string;
}

/** Hidden on screen; printed (Print → Save as PDF) as a keepable payment receipt. */
export function PaymentReceipt({
  data,
  centerName,
  centerAddress,
}: {
  data: PaymentReceiptData | null;
  centerName: string;
  centerAddress?: string;
}) {
  return (
    <>
      <style>{PRINT_CSS}</style>
      <div id="sasi-payment-receipt" aria-hidden>
        {data && (
          <>
            <h1>{centerName}</h1>
            {centerAddress ? <div className="sub">{centerAddress}</div> : null}
            <div className="sub">{data.kindLabel.toUpperCase()} — PAYMENT RECEIPT</div>
            <div className="hr" />
            <R k="Reference" v={data.reference} />
            <R k="Recipient" v={data.recipient} />
            <R k="Period / Date" v={data.period} />
            <div className="hr" />
            <R k="Method" v={data.method.replace(/_/g, ' ')} />
            <R k="Txn / UTR ref" v={data.txnRef} />
            <R k="Status" v={data.status.replace(/_/g, ' ')} />
            {data.paidAt && <R k="Paid at" v={new Date(data.paidAt).toLocaleString('en-IN')} />}
            <div className="hr" />
            <div className="row total">
              <span>AMOUNT</span>
              <span>{money(data.amount)}</span>
            </div>
            <div className="hr" />
            <div className="foot">
              Printed {new Date().toLocaleString('en-IN')}
              <br />
              This is a system-generated payment receipt.
            </div>
          </>
        )}
      </div>
    </>
  );
}
